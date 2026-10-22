package com.bookr.reader.parser;

public enum BookFormat {
    FB2("FB2"),
    FB2_ZIP("FB2.ZIP"),
    EPUB("EPUB"),
    PDF("PDF"),
    TXT("TXT"),
    HTML("HTML"),
    RTF("RTF"),
    MOBI("MOBI"),
    UNKNOWN("UNKNOWN");

    private final String displayName;

    BookFormat(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
