package com.bookr.reader.parser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Fb2Metadata implements Serializable {
    private String title = "";
    private String author = "";
    private String annotation = "";
    private byte[] coverImageBytes;
    private String coverContentType = "image/jpeg";
    private String language = "ru";
    private String series = "";
    private int seriesNumber = 0;
    private List<String> genres = new ArrayList<>();

    public String getTitle() {
        return (title == null || title.trim().isEmpty()) ? "Без названия" : title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return (author == null || author.trim().isEmpty()) ? "Неизвестный автор" : author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    public byte[] getCoverImageBytes() {
        return coverImageBytes;
    }

    public void setCoverImageBytes(byte[] coverImageBytes) {
        this.coverImageBytes = coverImageBytes;
    }

    public String getCoverContentType() {
        return coverContentType;
    }

    public void setCoverContentType(String coverContentType) {
        this.coverContentType = coverContentType;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public String getSeries() {
        return series != null ? series : "";
    }

    public void setSeries(String series) {
        this.series = series != null ? series : "";
    }

    public int getSeriesNumber() {
        return seriesNumber;
    }

    public void setSeriesNumber(int seriesNumber) {
        this.seriesNumber = seriesNumber;
    }
}
