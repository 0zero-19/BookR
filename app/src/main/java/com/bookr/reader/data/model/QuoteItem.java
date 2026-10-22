package com.bookr.reader.data.model;

import java.io.Serializable;

public class QuoteItem implements Serializable {
    private long id;
    private String bookUuid;
    private String bookTitle;
    private String bookAuthor;
    private String chapterTitle;
    private String quoteText;
    private String colorLabel = "#FFFFFF";
    private long createdAt;

    public QuoteItem() {
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

    public String getChapterTitle() {
        return chapterTitle;
    }

    public void setChapterTitle(String chapterTitle) {
        this.chapterTitle = chapterTitle;
    }

    public String getQuoteText() {
        return quoteText != null ? quoteText : "";
    }

    public void setQuoteText(String quoteText) {
        this.quoteText = quoteText;
    }

    public String getColorLabel() {
        return colorLabel != null ? colorLabel : "#FFFFFF";
    }

    public void setColorLabel(String colorLabel) {
        this.colorLabel = colorLabel;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
