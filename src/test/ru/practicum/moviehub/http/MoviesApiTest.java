package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;

    private static final Gson gson = new Gson();

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(new MoviesStore(), 8080);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        server.clearStore();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        return client.send(HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPost(String path, Object body, String contentType) throws Exception {
        String json = body != null ? gson.toJson(body) : "";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendDelete(String path) throws Exception {
        return client.send(HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .DELETE()
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Movie createMovie(String title, int year) throws Exception {
        Movie input = new Movie(0, title, year);

        HttpResponse<String> response = sendPost("/movies", input, "application/json");

        assertEquals(201, response.statusCode(),
                "Ожидается статус 201 Created при успешном создании фильма");

        return parseMovie(response.body());
    }

    private HttpResponse<String> attemptCreateMovie(Object body, String contentType) throws Exception {
        return sendPost("/movies", body, contentType);
    }

    private HttpResponse<String> sendPostWithoutContentType(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                // Намеренно НЕ добавляем заголовок Content-Type
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertJsonContentType(HttpResponse<String> resp) {
        String ct = resp.headers().firstValue("Content-Type").orElse("");
        assertTrue(ct.toLowerCase().contains("application/json"),
                "Content-Type должен содержать application/json, было: " + ct);
        assertTrue(ct.toLowerCase().contains("charset=utf-8"),
                "Content-Type должен содержать charset=UTF-8, было: " + ct);
    }

    private List<Movie> parseMoviesList(String json) {
        return gson.fromJson(json, ListOfMoviesTypeToken.TYPE);
    }

    private Movie parseMovie(String json) {
        return gson.fromJson(json, Movie.class);
    }

    private ErrorResponse parseError(String json) {
        return gson.fromJson(json, ErrorResponse.class);
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovies_withMovies_returnsListOfMovies() throws Exception {
        createMovie("Inception", 2010);
        createMovie("Interstellar", 2014);

        HttpResponse<String> resp = sendGet("/movies");
        assertEquals(200, resp.statusCode());

        List<Movie> movies = parseMoviesList(resp.body());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().anyMatch(m -> "Inception".equals(m.getTitle())));
        assertTrue(movies.stream().anyMatch(m -> m.getYear() == 2014));
    }

    @Test
    void postMovie_validData_returns201WithId() throws Exception {
        Movie created = createMovie("The Matrix", 1999);

        assertTrue(created.getId() > 0, "Сервер должен присвоить положительный ID");
        assertEquals("The Matrix", created.getTitle());
        assertEquals(1999, created.getYear());
    }

    @Test
    void postMovie_emptyTitle_returns422() throws Exception {
        Movie badMovie = new Movie(0, "   ", 2020);

        HttpResponse<String> resp = attemptCreateMovie(badMovie, "application/json");

        assertEquals(422, resp.statusCode());

        ErrorResponse err = parseError(resp.body());
        assertTrue(err.getDetails().stream()
                .anyMatch(d -> d.contains("пустым")));
    }

    @Test
    void getMovieById_found_returnsMovie() throws Exception {
        Movie created = createMovie("Dune", 2021);

        HttpResponse<String> getResp = sendGet("/movies/" + created.getId());
        assertEquals(200, getResp.statusCode());

        Movie found = parseMovie(getResp.body());
        assertEquals(created.getId(), found.getId());
        assertEquals("Dune", found.getTitle());
        assertEquals(2021, found.getYear());
    }

    @Test
    void deleteMovie_existing_returns204() throws Exception {
        Movie created = createMovie("Oppenheimer", 2023);

        HttpResponse<String> deleteResp = sendDelete("/movies/" + created.getId());
        assertEquals(204, deleteResp.statusCode());

        HttpResponse<String> checkResp = sendGet("/movies/" + created.getId());
        assertEquals(404, checkResp.statusCode(), "Удалённый фильм не должен находиться");
    }

    // -------

    @Test
    void returnsOnlyMoviesFromSpecifiedYear() throws Exception {
        // Подготовка: фильмы разных годов
        createMovie("Titanic", 1997);
        createMovie("Avatar", 2009);
        createMovie("Inception", 2010);
        createMovie("Interstellar", 2014);

        HttpResponse<String> resp = sendGet("/movies?year=2010");
        assertEquals(200, resp.statusCode());

        List<Movie> movies = parseMoviesList(resp.body());
        assertEquals(1, movies.size());
        assertEquals("Inception", movies.get(0).getTitle());
        assertEquals(2010, movies.get(0).getYear());
    }

    @Test
    void returnsEmptyArrayWhenNoMoviesForYear() throws Exception {
        createMovie("Inception", 2010);

        HttpResponse<String> resp = sendGet("/movies?year=1999");
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    void returns400ForNonNumericYearParam() throws Exception {
        HttpResponse<String> resp = sendGet("/movies?year=abc");
        assertEquals(400, resp.statusCode());

        ErrorResponse err = parseError(resp.body());
        assertNotNull(err.getError());
        assertTrue(err.getError().contains("year"));
    }

    @Test
    void returns400ForNegativeYearParam() throws Exception {
        HttpResponse<String> resp = sendGet("/movies?year=-100");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void errorResponse_serializesCorrectly() {
        ErrorResponse err = new ErrorResponse("Ошибка валидации",
                List.of("деталь 1", "деталь 2"));
        String json = err.toJson();

        assertTrue(json.contains("\"error\":\"Ошибка валидации\""));
        assertTrue(json.contains("\"details\""));
        assertTrue(json.contains("\"деталь 1\""));
        assertTrue(json.contains("\"деталь 2\""));
    }

    // ------

    @Test
    void returns422ForTitleOver100Chars() throws Exception {
        String longTitle = "A".repeat(101);
        Movie input = new Movie(0, longTitle, 2020);

        HttpResponse<String> resp = attemptCreateMovie(input, "application/json");

        assertEquals(422, resp.statusCode(), "Слишком длинное название → 422");

        ErrorResponse err = parseError(resp.body());
        assertTrue(err.getDetails().stream()
                        .anyMatch(d -> d.contains("100")),
                "Сообщение должно упоминать лимит в 100 символов");
    }

    @Test
    void returns422ForYearBefore1888() throws Exception {
        Movie input = new Movie(0, "Silent Film", 1887);

        HttpResponse<String> resp = attemptCreateMovie(input, "application/json");

        assertEquals(422, resp.statusCode(), "Год раньше 1888 → 422");

        ErrorResponse err = parseError(resp.body());
        assertTrue(err.getDetails().stream()
                        .anyMatch(d -> d.contains("1888")),
                "Сообщение должно упоминать минимальный год 1888");
    }

    @Test
    void returns422ForYearInFarFuture() throws Exception {
        int currentYear = java.time.Year.now().getValue();
        Movie input = new Movie(0, "Future Film", currentYear + 2);

        HttpResponse<String> resp = attemptCreateMovie(input, "application/json");

        assertEquals(422, resp.statusCode(),
                "Год больше текущего + 1 → 422");

        ErrorResponse err = parseError(resp.body());
        assertTrue(err.getDetails().stream()
                        .anyMatch(d -> d.contains(String.valueOf(currentYear + 1))),
                "Сообщение должно упоминать максимально допустимый год");
    }

    @Test
    void returns415ForMissingContentType() throws Exception {
        String json = "{\"title\":\"Test\",\"year\":2020}";

        HttpResponse<String> resp = sendPostWithoutContentType("/movies", json);

        assertEquals(415, resp.statusCode(), "Отсутствие Content-Type → 415");
    }

    @Test
    void returns422ForInvalidJson() throws Exception {
        String invalidJson = "{title: 'Broken', year: 2020}";

        HttpResponse<String> resp = sendPost("/movies", invalidJson, "application/json");

        assertTrue(resp.statusCode() == 400 || resp.statusCode() == 422,
                "Некорректный JSON должен вернуть ошибку клиента");
    }

    @Test
    void handlesSpecialCharsInTitle() throws Exception {
        Movie created = createMovie("Film: \"Quote\" & <Tag>", 2020);

        assertEquals("Film: \"Quote\" & <Tag>", created.getTitle(),
                "Спецсимволы в названии должны экранироваться корректно");
    }

    //-------

    @Test
    void getMovieWithNonNumericId_returns400() throws Exception {
        HttpResponse<String> resp = sendGet("/movies/abc");

        assertEquals(400, resp.statusCode(),
                "Некорректный ID (не число) должен вернуть 400");

        ErrorResponse err = parseError(resp.body());
        assertNotNull(err.getError(), "Ответ должен содержать поле 'error'");
        assertTrue(err.getError().toLowerCase().contains("id") ||
                        err.getError().toLowerCase().contains("некоррект"),
                "Сообщение об ошибке должно упоминать проблему с ID");
    }

    @Test
    void getMovieWithNegativeId_returnsError() throws Exception {
        HttpResponse<String> resp = sendGet("/movies/-5");
        assertTrue(resp.statusCode() == 400 || resp.statusCode() == 404,
                "Отрицательный ID должен вернуть 400 (некорректный формат) или 404 (не найден)");
    }

    @Test
    void deleteMovieWithNonNumericId_returns400() throws Exception {
        HttpResponse<String> resp = sendDelete("/movies/xyz");

        assertEquals(400, resp.statusCode(),
                "Некорректный ID при удалении должен вернуть 400");

        ErrorResponse err = parseError(resp.body());
        assertNotNull(err.getError());
        assertTrue(err.getError().toLowerCase().contains("id"));
    }

    @Test
    void getNonExistentLargeId_returns404() throws Exception {
        HttpResponse<String> resp = sendGet("/movies/999999");

        assertEquals(404, resp.statusCode(),
                "Запрос несуществующего ID должен вернуть 404");

        ErrorResponse err = parseError(resp.body());
        assertNotNull(err.getError());
        assertTrue(err.getError().toLowerCase().contains("не найден") ||
                        err.getError().toLowerCase().contains("not found"),
                "Сообщение должно указывать, что фильм не найден");
    }

    //------

    @Test
    void putToMovies_returns405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, resp.statusCode(),
                "Метод PUT не поддерживается для /movies → 405");

        assertJsonContentType(resp);
        ErrorResponse err = parseError(resp.body());
        assertNotNull(err.getError());
    }

    @Test
    void patchToMovies_returns405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(405, resp.statusCode());
    }

    @Test
    void getUnknownPath_returns404() throws Exception {
        HttpResponse<String> resp = sendGet("/unknown");
        assertEquals(404, resp.statusCode());
        assertJsonContentType(resp);
        ErrorResponse err = parseError(resp.body());
        assertNotNull(err.getError());
    }

    //-----

    @Test
    void successfulResponsesHaveCorrectContentType() throws Exception {
        HttpResponse<String> resp1 = sendGet("/movies");
        assertJsonContentType(resp1);

        Movie created = createMovie("Test", 2020);
        HttpResponse<String> resp2 = sendGet("/movies/" + created.getId());
        assertJsonContentType(resp2);
    }

    @Test
    void errorResponsesHaveJsonContentType() throws Exception {
        // 404
        HttpResponse<String> resp404 = sendGet("/movies/999");
        assertJsonContentType(resp404);

        // 400
        HttpResponse<String> resp400 = sendGet("/movies?year=abc");
        assertJsonContentType(resp400);

        // 422
        Movie bad = new Movie(0, "", 2020);
        HttpResponse<String> resp422 = sendPost("/movies", bad, "application/json");
        assertJsonContentType(resp422);
    }

    //-----

    @Test
    void deleteMovieTwice_secondReturns404() throws Exception {
        Movie created = createMovie("ToDelete", 2020);
        HttpResponse<String> resp1 = sendDelete("/movies/" + created.getId());
        assertEquals(204, resp1.statusCode());

        HttpResponse<String> resp2 = sendDelete("/movies/" + created.getId());
        assertEquals(404, resp2.statusCode());
    }
}
