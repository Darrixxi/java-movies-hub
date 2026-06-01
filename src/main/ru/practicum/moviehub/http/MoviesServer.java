package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;
    private final MoviesStore store;

    public MoviesServer(MoviesStore store, int port) {
        this.store = store;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/movies", new MoviesHandler(store));

            server.createContext("/", ex -> {
                String errorJson = "{\"error\":\"Not Found\"}";
                byte[] bytes = errorJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                ex.sendResponseHeaders(404, bytes.length);
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