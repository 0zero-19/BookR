package com.bookr.reader.data.model;

import java.io.Serializable;

public class OnlineBookItem implements Serializable {
    private String id;
    private String title;
    private String author;
    private String description;
    private String coverUrl;
    private String downloadUrl;
    private String format; // FB2, EPUB, TXT, PDF
    private String language;
    private String sourceName; // Gutenberg, OpenLibrary, OPDS, Classic
    private String genres;
    private int downloadsCount;
    private long localBookId; // >0 if already downloaded in local library
    private String localFilePath;
    private String fallbackContent; // For instant built-in or full-text books

    public OnlineBookItem() {
    }

    public OnlineBookItem(String id, String title, String author, String description,
                          String coverUrl, String downloadUrl, String format,
                          String language, String sourceName, String genres, int downloadsCount) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.description = description;
        this.coverUrl = coverUrl;
        this.downloadUrl = downloadUrl;
        this.format = format;
        this.language = language;
        this.sourceName = sourceName;
        this.genres = genres;
        this.downloadsCount = downloadsCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title != null && !title.isEmpty() ? title : "Без названия";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author != null && !author.isEmpty() ? author : "Неизвестный автор";
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getFormat() {
        return format != null && !format.isEmpty() ? format : "FB2";
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getLanguage() {
        return language != null && !language.isEmpty() ? language : "RU";
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceName() {
        return sourceName != null && !sourceName.isEmpty() ? sourceName : "Каталог";
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getGenres() {
        return genres != null ? genres : "";
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public int getDownloadsCount() {
        return downloadsCount;
    }

    public void setDownloadsCount(int downloadsCount) {
        this.downloadsCount = downloadsCount;
    }

    public long getLocalBookId() {
        return localBookId;
    }

    public void setLocalBookId(long localBookId) {
        this.localBookId = localBookId;
    }

    public String getLocalFilePath() {
        return localFilePath;
    }

    public void setLocalFilePath(String localFilePath) {
        this.localFilePath = localFilePath;
    }

    public String getFallbackContent() {
        return fallbackContent;
    }

    public void setFallbackContent(String fallbackContent) {
        this.fallbackContent = fallbackContent;
    }

    public boolean isDownloaded() {
        return localBookId > 0;
    }
}

