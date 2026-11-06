package com.bookr.reader.data.model;

import java.io.Serializable;

public class MangaChapterItem implements Serializable {
    private long id;
    private String chapterId;
    private String mangaId;
    private String chapterNumber;
    private String volumeNumber;
    private String title;
    private String language;
    private int pagesCount;
    private String scanlationGroup;
    private boolean isDownloaded;
    private String localDirPath;
    private int readPage;
    private boolean isCompleted;
    private long publishedAt;

    public MangaChapterItem() {
    }

    public MangaChapterItem(String chapterId, String mangaId, String chapterNumber,
                            String volumeNumber, String title, String language, int pagesCount) {
        this.chapterId = chapterId;
        this.mangaId = mangaId;
        this.chapterNumber = chapterNumber;
        this.volumeNumber = volumeNumber;
        this.title = title;
        this.language = language;
        this.pagesCount = pagesCount;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getChapterId() {
        return chapterId != null ? chapterId : "";
    }

    public void setChapterId(String chapterId) {
        this.chapterId = chapterId;
    }

    public String getMangaId() {
        return mangaId != null ? mangaId : "";
    }

    public void setMangaId(String mangaId) {
        this.mangaId = mangaId;
    }

    public String getChapterNumber() {
        return chapterNumber != null && !chapterNumber.isEmpty() ? chapterNumber : "1";
    }

    public void setChapterNumber(String chapterNumber) {
        this.chapterNumber = chapterNumber;
    }

    public String getVolumeNumber() {
        return volumeNumber != null ? volumeNumber : "";
    }

    public void setVolumeNumber(String volumeNumber) {
        this.volumeNumber = volumeNumber;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDisplayTitle() {
        StringBuilder sb = new StringBuilder();
        if (volumeNumber != null && !volumeNumber.isEmpty() && !"null".equalsIgnoreCase(volumeNumber)) {
            sb.append("Том ").append(volumeNumber).append(" ");
        }
        sb.append("Глава ").append(getChapterNumber());
        if (title != null && !title.trim().isEmpty() && !"null".equalsIgnoreCase(title.trim())) {
            sb.append(" — ").append(title.trim());
        }
        return sb.toString();
    }

    public String getLanguage() {
        return language != null && !language.isEmpty() ? language.toUpperCase() : "RU";
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getPagesCount() {
        return pagesCount;
    }

    public void setPagesCount(int pagesCount) {
        this.pagesCount = pagesCount;
    }

    public String getScanlationGroup() {
        return scanlationGroup != null ? scanlationGroup : "";
    }

    public void setScanlationGroup(String scanlationGroup) {
        this.scanlationGroup = scanlationGroup;
    }

    public boolean isDownloaded() {
        return isDownloaded;
    }

    public void setDownloaded(boolean downloaded) {
        isDownloaded = downloaded;
    }

    public String getLocalDirPath() {
        return localDirPath;
    }

    public void setLocalDirPath(String localDirPath) {
        this.localDirPath = localDirPath;
    }

    public int getReadPage() {
        return readPage;
    }

    public void setReadPage(int readPage) {
        this.readPage = readPage;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public long getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(long publishedAt) {
        this.publishedAt = publishedAt;
    }
}

