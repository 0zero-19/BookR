package com.bookr.reader.data.model;

public class OpdsCatalogItem {
    private long id;
    private String title;
    private String url;
    private String description;
    private long createdAt;
    private boolean builtIn;

    public OpdsCatalogItem() {
    }

    public OpdsCatalogItem(String title, String url, String description, boolean builtIn) {
        this.title = title;
        this.url = url;
        this.description = description;
        this.builtIn = builtIn;
        this.createdAt = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title != null ? title : "OPDS Каталог";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url != null ? url : "";
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }
}

