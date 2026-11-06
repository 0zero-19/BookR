package com.bookr.reader.parser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Fb2Section implements Serializable {
    private String id;
    private String anchorId;
    private String title = "";
    private String epigraph = "";
    private String epigraphAuthor = "";
    private StringBuilder contentBuilder = new StringBuilder();
    private int level = 0;
    private List<Fb2Section> subSections = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAnchorId() {
        return anchorId;
    }

    public void setAnchorId(String anchorId) {
        this.anchorId = anchorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEpigraph() {
        return epigraph;
    }

    public void setEpigraph(String epigraph) {
        this.epigraph = epigraph;
    }

    public String getEpigraphAuthor() {
        return epigraphAuthor;
    }

    public void setEpigraphAuthor(String epigraphAuthor) {
        this.epigraphAuthor = epigraphAuthor;
    }

    public Fb2Section appendContent(String html) {
        if (contentBuilder == null) {
            contentBuilder = new StringBuilder();
        }
        contentBuilder.append(html);
        return this;
    }

    public void setContent(String html) {
        if (contentBuilder == null) {
            contentBuilder = new StringBuilder();
        } else {
            contentBuilder.setLength(0);
        }
        if (html != null) {
            contentBuilder.append(html);
        }
    }

    public String getContent() {
        return contentBuilder != null ? contentBuilder.toString() : "";
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public List<Fb2Section> getSubSections() {
        return subSections;
    }

    public void addSubSection(Fb2Section section) {
        this.subSections.add(section);
    }
}
