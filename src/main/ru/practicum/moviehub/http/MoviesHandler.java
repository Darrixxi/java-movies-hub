package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;
import com.google.gson.Gson;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.net.HttpURLConnection;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;

    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MIN_YEAR = 1888;

    public static final int HTTP_METHOD_NOT_ALLOWED = 405;
    public static final int HTTP_UNPROCESSABLE_ENTITY = 422;

    private static final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        if ("/movies".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                handleGetAllMovies(ex);
            } else if ("POST".equalsIgnoreCase(method)) {
                handleCreateMovie(ex);
            } else {
                sendJson(ex, HTTP_METHOD_NOT_ALLOWED, new ErrorResponse("Method Not Allowed").toJson());
            }
            return;
        }

        if (path.startsWith("/movies/")) {
            if ("GET".equalsIgnoreCase(method)) {
                handleGetMovieById(ex, path);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleDeleteMovie(ex, path);
            } else {
                sendJson(ex, HTTP_METHOD_NOT_ALLOWED, new ErrorResponse("Method Not Allowed").toJson());
            }
            return;
        }

        sendJson(ex, HttpURLConnection.HTTP_NOT_FOUND, new ErrorResponse("Not Found").toJson());
    }

    private void handleGetAllMovies(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();

        if (query != null && query.startsWith("year=")) {
            try {
                int year = Integer.parseInt(query.substring(5));

                int currentYear = java.time.Year.now().getValue();
                if (year < MIN_YEAR || year > currentYear + 1) {
                    sendJson(ex, HttpURLConnection.HTTP_BAD_REQUEST,
                            new ErrorResponse("Некорректный параметр запроса — 'year'").toJson());
                    return;
                }

                List<Movie> filtered = store.getByYear(year);
                sendJson(ex, HttpURLConnection.HTTP_OK, gson.toJson(filtered));
            } catch (NumberFormatException e) {
                sendJson(ex, HttpURLConnection.HTTP_BAD_REQUEST, new ErrorResponse("Некорректный параметр запроса — 'year'").toJson());
            }
        } else {
            List<Movie> all = store.getAll();
            sendJson(ex, HttpURLConnection.HTTP_OK, gson.toJson(all));
        }
    }

    private void handleCreateMovie(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            sendJson(ex, HttpURLConnection.HTTP_UNSUPPORTED_TYPE, new ErrorResponse("Unsupported Media Type").toJson());
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        Movie newMovie;
        try {
            newMovie = Movie.fromJson(body);
        } catch (Exception e) {
            sendJson(ex, HTTP_UNPROCESSABLE_ENTITY, new ErrorResponse("Ошибка валидации",
                    List.of("Некорректный JSON")).toJson());
            return;
        }

        List<String> errors = validateMovie(newMovie);
        if (!errors.isEmpty()) {
            sendJson(ex, HTTP_UNPROCESSABLE_ENTITY, new ErrorResponse("Ошибка валидации", errors).toJson());
            return;
        }

        Movie created = store.add(newMovie.getTitle(), newMovie.getYear());

        sendJson(ex, HttpURLConnection.HTTP_CREATED, created.toJson());
    }

    private void handleGetMovieById(HttpExchange ex, String path) throws IOException {
        try {
            int id = Integer.parseInt(path.substring("/movies/".length()));
            Movie movie = store.getById(id);

            if (movie != null) {
                sendJson(ex, HttpURLConnection.HTTP_OK, movie.toJson());
            } else {
                sendJson(ex, HttpURLConnection.HTTP_NOT_FOUND, new ErrorResponse("Фильм не найден").toJson());
            }
        } catch (NumberFormatException e) {
            sendJson(ex, HttpURLConnection.HTTP_BAD_REQUEST, new ErrorResponse("Некорректный ID").toJson());
        }
    }

    private void handleDeleteMovie(HttpExchange ex, String path) throws IOException {
        try {
            int id = Integer.parseInt(path.substring("/movies/".length()));
            boolean deleted = store.delete(id);

            if (deleted) {
                sendNoContent(ex);
            } else {
                sendJson(ex, HttpURLConnection.HTTP_NOT_FOUND, new ErrorResponse("Фильм не найден").toJson());
            }
        } catch (NumberFormatException e) {
            sendJson(ex, HttpURLConnection.HTTP_BAD_REQUEST, new ErrorResponse("Некорректный ID").toJson());
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();
        int currentYear = java.time.Year.now().getValue();

        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            errors.add("название не должно быть пустым");
        }
        if (movie.getTitle() != null && movie.getTitle().length() > MAX_TITLE_LENGTH) {
            errors.add("название не должно превышать 100 символов");
        }
        if (movie.getYear() < MIN_YEAR || movie.getYear() > currentYear + 1) {
            errors.add("год должен быть между 1888 и " + (currentYear + 1));
        }
        return errors;
    }
}
