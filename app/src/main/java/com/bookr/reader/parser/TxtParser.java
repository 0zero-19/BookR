package com.bookr.reader.parser;

import android.content.Context;
import android.net.Uri;

import com.bookr.reader.data.model.TocItem;
import com.bookr.reader.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

public class TxtParser {

    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^(глава|часть|раздел|том|книга|act|chapter|section|part|book|prologue|epilogue|пролог|эпилог)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    public static Fb2Metadata parseMetadata(Context context, Uri uri) {
        Fb2Metadata meta = new Fb2Metadata();
        String fileName = FileUtils.getFileName(context, uri);
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        meta.setTitle(fileName);

        try (InputStream is = FileUtils.openInputStreamSafely(context, uri)) {
            if (is != null) {
                BufferedInputStream bis = new BufferedInputStream(is, 8192);
                Charset charset = EncodingDetector.detectTextCharset(bis);
                BufferedReader reader = new BufferedReader(new InputStreamReader(bis, charset));
                String firstLine = reader.readLine();
                if (firstLine != null && !firstLine.trim().isEmpty() && firstLine.length() < 100) {
                    meta.setTitle(firstLine.trim());
                }
            }
        } catch (Exception ignored) {}
        return meta;
    }

    public static Fb2Book parseBook(Context context, Uri uri) throws Exception {
        Fb2Book book = new Fb2Book();
        Fb2Metadata meta = parseMetadata(context, uri);
        book.setMetadata(meta);

        try (InputStream is = FileUtils.openInputStreamSafely(context, uri)) {
            if (is == null) throw new Exception("Cannot open TXT stream: " + uri);

            BufferedInputStream bis = new BufferedInputStream(is, 64 * 1024);
            Charset charset = EncodingDetector.detectTextCharset(bis);
            BufferedReader reader = new BufferedReader(new InputStreamReader(bis, charset));

            Fb2Section currentSection = new Fb2Section();
            currentSection.setTitle(meta.getTitle());
            currentSection.setLevel(1);
            book.addSection(currentSection);
            book.getToc().add(new TocItem(0, meta.getTitle(), "chapter_0", 1));

            String line;
            StringBuilder paragraph = new StringBuilder();
            int chapterIdx = 1;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    if (paragraph.length() > 0) {
                        currentSection.appendContent("<p>" + escapeHtml(paragraph.toString()) + "</p>");
                        paragraph.setLength(0);
                    }
                    continue;
                }

                // Check for chapter heading
                if (trimmed.length() <= 80 && (CHAPTER_PATTERN.matcher(trimmed).matches() || trimmed.startsWith("***"))) {
                    if (paragraph.length() > 0) {
                        currentSection.appendContent("<p>" + escapeHtml(paragraph.toString()) + "</p>");
                        paragraph.setLength(0);
                    }

                    Fb2Section nextSec = new Fb2Section();
                    nextSec.setTitle(trimmed);
                    nextSec.setLevel(1);
                    book.addSection(nextSec);
                    book.getToc().add(new TocItem(chapterIdx, trimmed, "chapter_" + chapterIdx, 1));
                    currentSection = nextSec;
                    chapterIdx++;
                    continue;
                }

                if (paragraph.length() > 0) paragraph.append(" ");
                paragraph.append(trimmed);
            }

            if (paragraph.length() > 0) {
                currentSection.appendContent("<p>" + escapeHtml(paragraph.toString()) + "</p>");
            }
        }
        return book;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
