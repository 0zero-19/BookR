package com.bookr.reader.data.model;

import java.io.Serializable;

public class CustomThemeItem implements Serializable {
    private long id;
    private String name;
    private String bgColor = "#000000";
    private String textColor = "#FFFFFF";
    private String surfaceColor = "#141414";
    private String strokeColor = "#26FFFFFF";
    private long createdAt;

    public CustomThemeItem() {
    }

    public CustomThemeItem(String name, String bgColor, String textColor, String surfaceColor, String strokeColor) {
        this.name = name;
        this.bgColor = bgColor;
        this.textColor = textColor;
        this.surfaceColor = surfaceColor;
        this.strokeColor = strokeColor;
        this.createdAt = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "Тема";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBgColor() {
        return bgColor;
    }

    public void setBgColor(String bgColor) {
        this.bgColor = bgColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public void setTextColor(String textColor) {
        this.textColor = textColor;
    }

    public String getSurfaceColor() {
        return surfaceColor;
    }

    public void setSurfaceColor(String surfaceColor) {
        this.surfaceColor = surfaceColor;
    }

    public String getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(String strokeColor) {
        this.strokeColor = strokeColor;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
