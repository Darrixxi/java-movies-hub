package ru.practicum.moviehub.model;

import com.google.gson.Gson;

public class Movie {

    private int id;
    private String title;
    private int year;

    public Movie(int id, String title, int year) {
        this.id = id;
        this.title = title;
        this.year = year;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getYear() {
        return year;
    }

    @Override
    public String toString() {
        return "Movie{id=" + id + ", title='" + title + "', year=" + year + "}";
    }

    private static final Gson gson = new Gson();

    public String toJson() {
        return gson.toJson(this);
    }

    public static Movie fromJson(String json) {
        return gson.fromJson(json, Movie.class);
    }
}
