package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MoviesStore {

    private final Map<Integer, Movie> movies = new HashMap<>();

    private final AtomicInteger idGenerator = new AtomicInteger(1);

    public List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    public List<Movie> getByYear(int year) {
        return movies.values().stream()
                .filter(m -> m.getYear() == year)
                .collect(Collectors.toList());
    }

    public Movie getById(int id) {
        return movies.get(id);
    }

    public Movie add(String title, int year) {
        int id = idGenerator.getAndIncrement();
        Movie movie = new Movie(id, title, year);
        movies.put(id, movie);
        return movie;
    }

    public boolean delete(int id) {
        return movies.remove(id) != null;
    }

    public void clear() {
        movies.clear();
        idGenerator.set(1);
    }
}