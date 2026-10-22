package com.bookr.reader.data.model;

import java.io.Serializable;

public class ShelfItem implements Serializable {
    private long id;
    private String name;
    private long createdAt;
    private int bookCount;

    public ShelfItem() {
    }

    public ShelfItem(String name) {
        this.name = name;
        this.createdAt = System.currentTimeMillis();
    }

    public ShelfItem(String name, long createdAt) {
        this.name = name;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "Полка";
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getBookCount() {
        return bookCount;
    }

    public void setBookCount(int bookCount) {
        this.bookCount = bookCount;
    }
}
