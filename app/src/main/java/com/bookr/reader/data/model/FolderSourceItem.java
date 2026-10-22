package com.bookr.reader.data.model;

import java.io.Serializable;

public class FolderSourceItem implements Serializable {
    private long id;
    private String treeUri;
    private String displayName;
    private long addedAt;
    private long lastScanned;
    private int booksCount;
    private boolean isAccessible = true;

    public FolderSourceItem() {
    }

    public FolderSourceItem(String treeUri, String displayName, long addedAt) {
        this.treeUri = treeUri;
        this.displayName = displayName;
        this.addedAt = addedAt;
        this.lastScanned = addedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTreeUri() {
        return treeUri;
    }

    public void setTreeUri(String treeUri) {
        this.treeUri = treeUri;
    }

    public String getDisplayName() {
        return (displayName == null || displayName.isEmpty()) ? "Папка" : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(long addedAt) {
        this.addedAt = addedAt;
    }

    public long getLastScanned() {
        return lastScanned;
    }

    public void setLastScanned(long lastScanned) {
        this.lastScanned = lastScanned;
    }

    public int getBooksCount() {
        return booksCount;
    }

    public void setBooksCount(int booksCount) {
        this.booksCount = booksCount;
    }

    public boolean isAccessible() {
        return isAccessible;
    }

    public void setAccessible(boolean accessible) {
        isAccessible = accessible;
    }
}
