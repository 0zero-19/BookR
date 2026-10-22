package com.bookr.reader.data.model;

import java.io.Serializable;

public class TocItem implements Serializable {
    private int index;
    private String title;
    private String elementId;
    private int level;

    public TocItem(int index, String title, String elementId, int level) {
        this.index = index;
        this.title = title;
        this.elementId = elementId;
        this.level = level;
    }

    public int getIndex() {
        return index;
    }

    public String getTitle() {
        return (title == null || title.trim().isEmpty()) ? "Раздел " + (index + 1) : title;
    }

    public String getElementId() {
        return elementId;
    }

    public int getLevel() {
        return level;
    }
}
