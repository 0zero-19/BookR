package com.bookr.reader.data.model;

import java.io.Serializable;

public class MangaItem implements Serializable {
    private long id;
    private String mangaId;
    private String title;
    private String altTitle;
    private String author;
    private String description;
    private String coverUrl;
    private String localCoverPath;
    private String status;
    private String genres;
    private String lastChapterId;
    private String lastChapterTitle;
    private int lastPage;
    private boolean isFavorite;
    private boolean isDownloaded;
    private int downloadedChaptersCount;
    private long updatedAt;

    public MangaItem() {
    }

    public MangaItem(String mangaId, String title, String author, String description,
                     String coverUrl, String status, String genres) {
        this.mangaId = mangaId;
        this.title = title;
        this.author = author;
        this.description = description;
        this.coverUrl = coverUrl;
        this.status = status;
        this.genres = genres;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMangaId() {
        return mangaId != null ? mangaId : "";
    }

    public void setMangaId(String mangaId) {
        this.mangaId = mangaId;
    }

    public String getTitle() {
        return title != null && !title.isEmpty() ? title : "Без названия";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAltTitle() {
        return altTitle != null ? altTitle : "";
    }

    public void setAltTitle(String altTitle) {
        this.altTitle = altTitle;
    }

    public String getAuthor() {
        return author != null && !author.isEmpty() ? author : "Мангака";
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

    public String getLocalCoverPath() {
        return localCoverPath;
    }

    public void setLocalCoverPath(String localCoverPath) {
        this.localCoverPath = localCoverPath;
    }

    public String getStatus() {
        return status != null && !status.isEmpty() ? status : "Выходит";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGenres() {
        return genres != null ? genres : "";
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public String getLastChapterId() {
        return lastChapterId;
    }

    public void setLastChapterId(String lastChapterId) {
        this.lastChapterId = lastChapterId;
    }

    public String getLastChapterTitle() {
        return lastChapterTitle;
    }

    public void setLastChapterTitle(String lastChapterTitle) {
        this.lastChapterTitle = lastChapterTitle;
    }

    public int getLastPage() {
        return lastPage;
    }

    public void setLastPage(int lastPage) {
        this.lastPage = lastPage;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public boolean isDownloaded() {
        return isDownloaded;
    }

    public void setDownloaded(boolean downloaded) {
        isDownloaded = downloaded;
    }

    public int getDownloadedChaptersCount() {
        return downloadedChaptersCount;
    }

    public void setDownloadedChaptersCount(int downloadedChaptersCount) {
        this.downloadedChaptersCount = downloadedChaptersCount;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}

