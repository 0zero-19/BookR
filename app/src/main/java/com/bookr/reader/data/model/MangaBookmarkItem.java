package com.bookr.reader.data.model;

import java.io.Serializable;

public class MangaBookmarkItem implements Serializable {
    private long id;
    private String mangaId;
    private String mangaTitle;
    private String chapterId;
    private String chapterTitle;
    private int pageIndex;
    private String note;
    private String coverUrl;
    private long createdAt;

    public MangaBookmarkItem() {
    }

    public MangaBookmarkItem(String mangaId, String mangaTitle, String chapterId,
                             String chapterTitle, int pageIndex, String note, String coverUrl) {
        this.mangaId = mangaId;
        this.mangaTitle = mangaTitle;
        this.chapterId = chapterId;
        this.chapterTitle = chapterTitle;
        this.pageIndex = pageIndex;
        this.note = note;
        this.coverUrl = coverUrl;
        this.createdAt = System.currentTimeMillis();
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

    public String getMangaTitle() {
        return mangaTitle != null ? mangaTitle : "Манга";
    }

    public void setMangaTitle(String mangaTitle) {
        this.mangaTitle = mangaTitle;
    }

    public String getChapterId() {
        return chapterId != null ? chapterId : "";
    }

    public void setChapterId(String chapterId) {
        this.chapterId = chapterId;
    }

    public String getChapterTitle() {
        return chapterTitle != null ? chapterTitle : "Глава";
    }

    public void setChapterTitle(String chapterTitle) {
        this.chapterTitle = chapterTitle;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public String getNote() {
        return note != null ? note : "";
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}

