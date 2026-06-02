package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;

public class MoviesServer {
    private final HttpServer server;
    private final MoviesStore store;

    private static final Gson GSON = new Gson();

    public MoviesServer(MoviesStore store, int port) {
        this.store = store;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/movies", new MoviesHandler(store));

            server.createContext("/", ex -> {
                String errorJson = GSON.toJson(new ErrorResponse("Not Found"));
                byte[] bytes = errorJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                ex.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, bytes.length);
                try (java.io.OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            });

        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер", e);
        }
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен!");
    }

    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }

    public void clearStore() {
        store.clear();
    }
}