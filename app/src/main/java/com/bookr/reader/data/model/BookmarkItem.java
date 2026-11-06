package com.bookr.reader.data.model;

import java.io.Serializable;

public class BookmarkItem implements Serializable {
    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_IMPORTANT = 1;
    public static final int TYPE_QUOTE = 2;

    private long id;
    private String bookUuid;
    private String bookTitle;
    private String bookAuthor;
    private String seriesInfo;
    private String chapterTitle;
    private String positionStr;
    private int pageNumber;
    private String snippet;
    private String comment;
    private int bookmarkType = TYPE_NORMAL;
    private long createdAt;

    public BookmarkItem() {
    }

    public BookmarkItem(long bookId, int position, String chapterTitle, String snippet, long createdAt) {
        this.positionStr = String.valueOf(position / 10f);
        this.pageNumber = position;
        this.chapterTitle = chapterTitle;
        this.snippet = snippet;
        this.createdAt = createdAt;
    }

    public int getPosition() {
        if (positionStr != null && !positionStr.trim().isEmpty()) {
            try {
                float pct = Float.parseFloat(positionStr.trim().replace(',', '.'));
                return Math.round(pct * 10f);
            } catch (Exception ignored) {}
        }
        if (pageNumber > 0) return pageNumber;
        return 0;
    }

    public float getProgressPercent() {
        if (positionStr != null && !positionStr.trim().isEmpty()) {
            try {
                return Float.parseFloat(positionStr.trim().replace(',', '.'));
            } catch (Exception ignored) {}
        }
        return pageNumber > 0 ? (pageNumber / 10f) : 0f;
    }

    public void setPosition(int position) {
        this.pageNumber = position;
        this.positionStr = String.valueOf(position / 10f);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getBookUuid() {
        return bookUuid;
    }

    public void setBookUuid(String bookUuid) {
        this.bookUuid = bookUuid;
    }

    public String getBookTitle() {
        return (bookTitle == null || bookTitle.trim().isEmpty()) ? "Без названия" : bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getBookAuthor() {
        return (bookAuthor == null || bookAuthor.trim().isEmpty()) ? "Неизвестный автор" : bookAuthor;
    }

    public void setBookAuthor(String bookAuthor) {
        this.bookAuthor = bookAuthor;
    }

    public String getSeriesInfo() {
        return seriesInfo;
    }

    public void setSeriesInfo(String seriesInfo) {
        this.seriesInfo = seriesInfo;
    }

    public String getChapterTitle() {
        return (chapterTitle == null || chapterTitle.isEmpty()) ? "Глава" : chapterTitle;
    }

    public void setChapterTitle(String chapterTitle) {
        this.chapterTitle = chapterTitle;
    }

    public String getPositionStr() {
        return positionStr;
    }

    public void setPositionStr(String positionStr) {
        this.positionStr = positionStr;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getBookmarkType() {
        return bookmarkType;
    }

    public void setBookmarkType(int bookmarkType) {
        this.bookmarkType = bookmarkType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getTypeLabel() {
        switch (bookmarkType) {
            case TYPE_IMPORTANT:
                return "Важная";
            case TYPE_QUOTE:
                return "Цитата";
            case TYPE_NORMAL:
            default:
                return "Обычная";
        }
    }
}
