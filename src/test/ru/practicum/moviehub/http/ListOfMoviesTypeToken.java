package ru.practicum.moviehub.http;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

import ru.practicum.moviehub.model.Movie;

public class ListOfMoviesTypeToken extends TypeToken<List<Movie>> {
    private ListOfMoviesTypeToken() {
    }

    public static final Type TYPE = new ListOfMoviesTypeToken().getType();
}