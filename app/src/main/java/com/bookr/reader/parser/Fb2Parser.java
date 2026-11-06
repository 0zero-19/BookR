package com.bookr.reader.parser;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;

import com.bookr.reader.data.model.TocItem;
import com.bookr.reader.util.FileUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Fb2Parser {
    private static final String TAG = "BookR.Fb2Parser";

    public static Fb2Metadata parseMetadata(Context context, Uri uri) {
        Fb2Metadata meta = new Fb2Metadata();
        InputStream rawStream = null;
        try {
            rawStream = openBookStream(context, uri);
            if (rawStream == null) {
                Log.w(TAG, "Cannot open raw stream for URI: " + uri);
                return meta;
            }

            BufferedInputStream bis = new BufferedInputStream(rawStream, 32 * 1024);
            Charset charset = EncodingDetector.detectXmlCharset(bis);
            Log.d(TAG, "Detected XML charset: " + charset.name() + " for " + uri);

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            Reader entityReader = new EntityFilterReader(new InputStreamReader(bis, charset));
            parser.setInput(entityReader);

            int eventType = parser.getEventType();
            boolean insideDescription = false;
            boolean insideTitleInfo = false;
            boolean insideAuthor = false;
            boolean insideCoverpage = false;
            boolean insideAnnotation = false;

            StringBuilder authorFirst = new StringBuilder();
            StringBuilder authorMiddle = new StringBuilder();
            StringBuilder authorLast = new StringBuilder();
            StringBuilder annotationBuilder = new StringBuilder();
            String coverImageId = null;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                if (eventType == XmlPullParser.START_TAG) {
                    if ("description".equalsIgnoreCase(name)) {
                        insideDescription = true;
                    } else if ("title-info".equalsIgnoreCase(name)) {
                        insideTitleInfo = true;
                    } else if (insideTitleInfo && "book-title".equalsIgnoreCase(name)) {
                        meta.setTitle(safeGetText(parser));
                    } else if (insideTitleInfo && "author".equalsIgnoreCase(name)) {
                        insideAuthor = true;
                        authorFirst.setLength(0);
                        authorMiddle.setLength(0);
                        authorLast.setLength(0);
                    } else if (insideAuthor && "first-name".equalsIgnoreCase(name)) {
                        authorFirst.append(safeGetText(parser));
                    } else if (insideAuthor && "middle-name".equalsIgnoreCase(name)) {
                        authorMiddle.append(safeGetText(parser));
                    } else if (insideAuthor && "last-name".equalsIgnoreCase(name)) {
                        authorLast.append(safeGetText(parser));
                    } else if (insideTitleInfo && "annotation".equalsIgnoreCase(name)) {
                        insideAnnotation = true;
                    } else if (insideAnnotation && "p".equalsIgnoreCase(name)) {
                        annotationBuilder.append("<p>").append(safeGetText(parser)).append("</p>");
                    } else if (insideTitleInfo && "coverpage".equalsIgnoreCase(name)) {
                        insideCoverpage = true;
                    } else if (insideCoverpage && "image".equalsIgnoreCase(name)) {
                        coverImageId = getHrefAttribute(parser);
                        if (coverImageId != null && coverImageId.startsWith("#")) {
                            coverImageId = coverImageId.substring(1);
                        }
                    } else if (insideTitleInfo && "lang".equalsIgnoreCase(name)) {
                        meta.setLanguage(safeGetText(parser));
                    } else if (insideTitleInfo && "genre".equalsIgnoreCase(name)) {
                        String genre = safeGetText(parser);
                        if (!genre.isEmpty()) {
                            meta.getGenres().add(genre);
                        }
                    } else if (insideTitleInfo && "sequence".equalsIgnoreCase(name)) {
                        String seqName = parser.getAttributeValue(null, "name");
                        String seqNum = parser.getAttributeValue(null, "number");
                        if (seqName != null && !seqName.trim().isEmpty()) {
                            meta.setSeries(seqName.trim());
                            if (seqNum != null) {
                                try {
                                    meta.setSeriesNumber(Integer.parseInt(seqNum.trim()));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    } else if ("binary".equalsIgnoreCase(name)) {
                        String binaryId = parser.getAttributeValue(null, "id");
                        String contentType = parser.getAttributeValue(null, "content-type");
                        if (coverImageId != null && coverImageId.equals(binaryId)) {
                            String base64Data = safeGetText(parser).replaceAll("\\s+", "");
                            try {
                                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                                meta.setCoverImageBytes(bytes);
                                if (contentType != null) {
                                    meta.setCoverContentType(contentType);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error decoding cover base64", e);
                            }
                            break;
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("author".equalsIgnoreCase(name) && insideAuthor) {
                        insideAuthor = false;
                        StringBuilder fullAuthor = new StringBuilder();
                        if (authorFirst.length() > 0) fullAuthor.append(authorFirst).append(" ");
                        if (authorMiddle.length() > 0) fullAuthor.append(authorMiddle).append(" ");
                        if (authorLast.length() > 0) fullAuthor.append(authorLast);
                        String aStr = fullAuthor.toString().trim();
                        if (!aStr.isEmpty()) {
                            if (meta.getAuthor().equals("Неизвестный автор")) {
                                meta.setAuthor(aStr);
                            } else {
                                meta.setAuthor(meta.getAuthor() + ", " + aStr);
                            }
                        }
                    } else if ("annotation".equalsIgnoreCase(name)) {
                        insideAnnotation = false;
                        meta.setAnnotation(annotationBuilder.toString());
                    } else if ("coverpage".equalsIgnoreCase(name)) {
                        insideCoverpage = false;
                    } else if ("title-info".equalsIgnoreCase(name)) {
                        insideTitleInfo = false;
                    } else if ("description".equalsIgnoreCase(name)) {
                        insideDescription = false;
                        if (coverImageId == null) {
                            break;
                        }
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse metadata for " + uri, e);
        } finally {
            if (rawStream != null) {
                try { rawStream.close(); } catch (IOException ignored) {}
            }
        }
        return meta;
    }

    public static Fb2Book parseBook(Context context, Uri uri) throws Exception {
        Log.d(TAG, "Starting full FB2 parse for URI: " + uri);
        Fb2Book book = new Fb2Book();
        InputStream rawStream = openBookStream(context, uri);
        if (rawStream == null) {
            throw new IOException("Cannot open book stream for: " + uri);
        }

        BufferedInputStream bis = new BufferedInputStream(rawStream, 64 * 1024);
        Charset charset = EncodingDetector.detectXmlCharset(bis);
        Log.d(TAG, "Detected XML charset: " + charset.name());

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        Reader entityReader = new EntityFilterReader(new InputStreamReader(bis, charset));
        parser.setInput(entityReader);

        int eventType = parser.getEventType();
        boolean insideDescription = false;
        boolean insideTitleInfo = false;
        boolean insideAuthor = false;
        boolean insideAnnotation = false;

        boolean insideBody = false;
        boolean insideNotesBody = false;
        boolean insideTitle = false;
        boolean insideEpigraph = false;
        boolean insideCite = false;
        boolean insidePoem = false;
        boolean insideStanza = false;

        StringBuilder authorFirst = new StringBuilder();
        StringBuilder authorMiddle = new StringBuilder();
        StringBuilder authorLast = new StringBuilder();
        StringBuilder annotationBuilder = new StringBuilder();

        Fb2Section currentSection = null;
        Deque<Fb2Section> sectionStack = new ArrayDeque<>();
        StringBuilder currentTitleBuilder = new StringBuilder();
        StringBuilder currentEpigraphBuilder = new StringBuilder();
        StringBuilder currentEpigraphAuthor = new StringBuilder();

        String currentNoteId = null;
        StringBuilder currentNoteText = new StringBuilder();
        String coverImageId = null;
        boolean insideCoverpage = false;

        int tocIndex = 0;
        int sectionCounter = 0;
        int sectionDepth = 0;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String name = parser.getName();

            if (eventType == XmlPullParser.START_TAG) {
                if ("description".equalsIgnoreCase(name)) {
                    insideDescription = true;
                } else if (insideDescription) {
                    if ("title-info".equalsIgnoreCase(name)) {
                        insideTitleInfo = true;
                    } else if (insideTitleInfo && "book-title".equalsIgnoreCase(name)) {
                        book.getMetadata().setTitle(safeGetText(parser));
                    } else if (insideTitleInfo && "author".equalsIgnoreCase(name)) {
                        insideAuthor = true;
                        authorFirst.setLength(0);
                        authorMiddle.setLength(0);
                        authorLast.setLength(0);
                    } else if (insideAuthor && "first-name".equalsIgnoreCase(name)) {
                        authorFirst.append(safeGetText(parser));
                    } else if (insideAuthor && "middle-name".equalsIgnoreCase(name)) {
                        authorMiddle.append(safeGetText(parser));
                    } else if (insideAuthor && "last-name".equalsIgnoreCase(name)) {
                        authorLast.append(safeGetText(parser));
                    } else if (insideTitleInfo && "annotation".equalsIgnoreCase(name)) {
                        insideAnnotation = true;
                    } else if (insideAnnotation && "p".equalsIgnoreCase(name)) {
                        annotationBuilder.append("<p>").append(safeGetText(parser)).append("</p>");
                    } else if (insideTitleInfo && "coverpage".equalsIgnoreCase(name)) {
                        insideCoverpage = true;
                    } else if (insideCoverpage && "image".equalsIgnoreCase(name)) {
                        coverImageId = getHrefAttribute(parser);
                        if (coverImageId != null && coverImageId.startsWith("#")) {
                            coverImageId = coverImageId.substring(1);
                        }
                    } else if (insideTitleInfo && "lang".equalsIgnoreCase(name)) {
                        book.getMetadata().setLanguage(safeGetText(parser));
                    } else if (insideTitleInfo && "genre".equalsIgnoreCase(name)) {
                        String genre = safeGetText(parser);
                        if (!genre.isEmpty()) {
                            book.getMetadata().getGenres().add(genre);
                        }
                    } else if (insideTitleInfo && "sequence".equalsIgnoreCase(name)) {
                        String seqName = parser.getAttributeValue(null, "name");
                        String seqNum = parser.getAttributeValue(null, "number");
                        if (seqName != null && !seqName.trim().isEmpty()) {
                            book.getMetadata().setSeries(seqName.trim());
                            if (seqNum != null) {
                                try {
                                    book.getMetadata().setSeriesNumber(Integer.parseInt(seqNum.trim()));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                } else if ("body".equalsIgnoreCase(name)) {
                    insideBody = true;
                    String bodyName = parser.getAttributeValue(null, "name");
                    insideNotesBody = (bodyName != null && (bodyName.equalsIgnoreCase("notes") || bodyName.equalsIgnoreCase("comments")));
                } else if (insideBody && !insideNotesBody) {
                    if ("section".equalsIgnoreCase(name)) {
                        sectionDepth++;
                        Fb2Section newSection = new Fb2Section();
                        newSection.setId(parser.getAttributeValue(null, "id"));
                        newSection.setAnchorId("chapter_" + (sectionCounter++));
                        newSection.setLevel(sectionDepth);

                        if (sectionStack.isEmpty()) {
                            book.addSection(newSection);
                        } else {
                            sectionStack.peek().addSubSection(newSection);
                        }
                        sectionStack.push(newSection);
                        currentSection = newSection;
                    } else if ("title".equalsIgnoreCase(name)) {
                        insideTitle = true;
                        currentTitleBuilder.setLength(0);
                    } else if ("epigraph".equalsIgnoreCase(name)) {
                        insideEpigraph = true;
                        currentEpigraphBuilder.setLength(0);
                        currentEpigraphAuthor.setLength(0);
                    } else if (insideEpigraph && "text-author".equalsIgnoreCase(name)) {
                        currentEpigraphAuthor.append(safeGetText(parser));
                    } else if ("cite".equalsIgnoreCase(name)) {
                        insideCite = true;
                        if (currentSection != null) currentSection.appendContent("<blockquote>");
                    } else if ("poem".equalsIgnoreCase(name)) {
                        insidePoem = true;
                        if (currentSection != null) currentSection.appendContent("<div class=\"poem\">");
                    } else if ("stanza".equalsIgnoreCase(name)) {
                        insideStanza = true;
                        if (currentSection != null) currentSection.appendContent("<div class=\"stanza\">");
                    } else if ("v".equalsIgnoreCase(name)) {
                        String verse = safeGetText(parser);
                        if (currentSection != null) {
                            currentSection.appendContent("<div class=\"v\">" + escapeHtml(verse) + "</div>");
                        }
                    } else if ("p".equalsIgnoreCase(name)) {
                        String paragraphContent = parseFormattedText(parser);
                        if (insideTitle) {
                            if (currentTitleBuilder.length() > 0) currentTitleBuilder.append(" ");
                            currentTitleBuilder.append(paragraphContent);
                        } else if (insideEpigraph) {
                            currentEpigraphBuilder.append("<p>").append(paragraphContent).append("</p>");
                        } else if (currentSection != null) {
                            currentSection.appendContent("<p>" + paragraphContent + "</p>");
                        } else {
                            if (book.getSections().isEmpty()) {
                                Fb2Section defaultSec = new Fb2Section();
                                defaultSec.setAnchorId("chapter_" + (sectionCounter++));
                                defaultSec.setLevel(1);
                                book.addSection(defaultSec);
                                sectionStack.push(defaultSec);
                                currentSection = defaultSec;
                            }
                            currentSection.appendContent("<p>" + paragraphContent + "</p>");
                        }
                    } else if ("subtitle".equalsIgnoreCase(name)) {
                        String sub = safeGetText(parser);
                        if (currentSection != null) {
                            currentSection.appendContent("<h3 class=\"section-subtitle\">" + escapeHtml(sub) + "</h3>");
                        }
                    } else if ("empty-line".equalsIgnoreCase(name)) {
                        if (currentSection != null) {
                            currentSection.appendContent("<div style=\"height: 1em;\"></div>");
                        }
                    } else if ("image".equalsIgnoreCase(name)) {
                        String imgHref = getHrefAttribute(parser);
                        if (imgHref != null && currentSection != null) {
                            if (imgHref.startsWith("#")) imgHref = imgHref.substring(1);
                            currentSection.appendContent("<img class=\"book-image\" data-img-id=\""
                                    + escapeHtml(imgHref)
                                    + "\" src=\"bookr://image/"
                                    + escapeHtml(imgHref)
                                    + "\" alt=\"Illustration\" />");
                        }
                    }
                } else if (insideBody && insideNotesBody) {
                    if ("section".equalsIgnoreCase(name)) {
                        currentNoteId = parser.getAttributeValue(null, "id");
                        currentNoteText.setLength(0);
                    } else if ("p".equalsIgnoreCase(name) && currentNoteId != null) {
                        String text = safeGetText(parser);
                        if (currentNoteText.length() > 0) currentNoteText.append(" ");
                        currentNoteText.append(text);
                    }
                } else if ("binary".equalsIgnoreCase(name)) {
                    String binaryId = parser.getAttributeValue(null, "id");
                    String contentType = parser.getAttributeValue(null, "content-type");
                    if (contentType == null || contentType.isEmpty()) contentType = "image/jpeg";
                    String base64Data = safeGetText(parser).replaceAll("\\s+", "");
                    if (binaryId != null && !base64Data.isEmpty()) {
                        String dataUri = "data:" + contentType + ";base64," + base64Data;
                        book.addImage(binaryId, dataUri);
                        if (coverImageId != null && coverImageId.equals(binaryId)) {
                            try {
                                book.getMetadata().setCoverImageBytes(Base64.decode(base64Data, Base64.DEFAULT));
                                book.getMetadata().setCoverContentType(contentType);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if ("description".equalsIgnoreCase(name)) {
                    insideDescription = false;
                } else if ("title-info".equalsIgnoreCase(name)) {
                    insideTitleInfo = false;
                } else if ("coverpage".equalsIgnoreCase(name)) {
                    insideCoverpage = false;
                } else if ("author".equalsIgnoreCase(name) && insideAuthor) {
                    insideAuthor = false;
                    StringBuilder fullAuthor = new StringBuilder();
                    if (authorFirst.length() > 0) fullAuthor.append(authorFirst).append(" ");
                    if (authorMiddle.length() > 0) fullAuthor.append(authorMiddle).append(" ");
                    if (authorLast.length() > 0) fullAuthor.append(authorLast);
                    String aStr = fullAuthor.toString().trim();
                    if (!aStr.isEmpty()) {
                        if (book.getMetadata().getAuthor().equals("Неизвестный автор")) {
                            book.getMetadata().setAuthor(aStr);
                        } else {
                            book.getMetadata().setAuthor(book.getMetadata().getAuthor() + ", " + aStr);
                        }
                    }
                } else if ("annotation".equalsIgnoreCase(name)) {
                    insideAnnotation = false;
                    book.getMetadata().setAnnotation(annotationBuilder.toString());
                } else if ("title".equalsIgnoreCase(name) && insideTitle) {
                    insideTitle = false;
                    if (currentSection != null && currentTitleBuilder.length() > 0) {
                        String titleStr = currentTitleBuilder.toString().trim();
                        if (insidePoem || insideCite) {
                            currentSection.appendContent("<div class=\"poem-title\"><strong>" + titleStr + "</strong></div>");
                        } else if (currentSection.getTitle() == null || currentSection.getTitle().isEmpty()) {
                            String plainTitle = stripHtmlTags(titleStr);
                            currentSection.setTitle(plainTitle);
                            String anchorId = currentSection.getAnchorId() != null
                                    ? currentSection.getAnchorId()
                                    : ("chapter_" + tocIndex);
                            book.getToc().add(new TocItem(tocIndex++, plainTitle, anchorId, currentSection.getLevel()));
                        }
                    }
                } else if ("epigraph".equalsIgnoreCase(name) && insideEpigraph) {
                    insideEpigraph = false;
                    if (currentSection != null) {
                        currentSection.setEpigraph(currentEpigraphBuilder.toString());
                        currentSection.setEpigraphAuthor(currentEpigraphAuthor.toString());
                    }
                } else if ("cite".equalsIgnoreCase(name) && insideCite) {
                    insideCite = false;
                    if (currentSection != null) currentSection.appendContent("</blockquote>");
                } else if ("poem".equalsIgnoreCase(name) && insidePoem) {
                    insidePoem = false;
                    if (currentSection != null) currentSection.appendContent("</div>");
                } else if ("stanza".equalsIgnoreCase(name) && insideStanza) {
                    insideStanza = false;
                    if (currentSection != null) currentSection.appendContent("</div>");
                } else if ("section".equalsIgnoreCase(name)) {
                    if (insideNotesBody && currentNoteId != null) {
                        book.addFootnote(currentNoteId, currentNoteText.toString());
                        currentNoteId = null;
                    } else if (insideBody && !insideNotesBody) {
                        sectionDepth = Math.max(0, sectionDepth - 1);
                        if (!sectionStack.isEmpty()) {
                            sectionStack.pop();
                        }
                        currentSection = sectionStack.peek();
                    }
                } else if ("body".equalsIgnoreCase(name)) {
                    insideBody = false;
                    insideNotesBody = false;
                    sectionStack.clear();
                    currentSection = null;
                    sectionDepth = 0;
                }
            }
            eventType = parser.next();
        }

        // Replace embedded image placeholders with data URIs
        Map<String, String> imgMap = book.getImages();
        if (!imgMap.isEmpty()) {
            for (Fb2Section sec : book.getSections()) {
                replaceImagesRecursively(sec, imgMap);
            }
        }

        // Fallback TOC if no explicit title tags were defined
        if (book.getToc().isEmpty() && !book.getSections().isEmpty()) {
            int idx = 0;
            for (Fb2Section sec : book.getSections()) {
                String title = (sec.getTitle() != null && !sec.getTitle().isEmpty()) ? sec.getTitle() : "Раздел " + (idx + 1);
                String anchorId = sec.getAnchorId() != null ? sec.getAnchorId() : ("chapter_" + idx);
                book.getToc().add(new TocItem(idx, title, anchorId, sec.getLevel()));
                idx++;
            }
        }

        rawStream.close();
        Log.d(TAG, "Successfully parsed FB2 book: " + book.getMetadata().getTitle() + " (" + book.getSections().size() + " sections)");
        return book;
    }

    private static void replaceImagesRecursively(Fb2Section sec, Map<String, String> imgMap) {
        String content = sec.getContent();
        if (content.contains("bookr://image/")) {
            for (Map.Entry<String, String> entry : imgMap.entrySet()) {
                String placeholder = "bookr://image/" + entry.getKey();
                if (content.contains(placeholder)) {
                    content = content.replace(placeholder, entry.getValue());
                }
            }
            sec.setContent(content);
        }
        for (Fb2Section sub : sec.getSubSections()) {
            replaceImagesRecursively(sub, imgMap);
        }
    }

    private static String parseFormattedText(XmlPullParser parser) throws IOException, XmlPullParserException {
        StringBuilder sb = new StringBuilder();
        int depth = 1;

        while (depth > 0) {
            int eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                depth++;
                String name = parser.getName();
                if ("emphasis".equalsIgnoreCase(name) || "style".equalsIgnoreCase(name)) {
                    sb.append("<em>");
                } else if ("strong".equalsIgnoreCase(name)) {
                    sb.append("<strong>");
                } else if ("strikethrough".equalsIgnoreCase(name)) {
                    sb.append("<s>");
                } else if ("sub".equalsIgnoreCase(name)) {
                    sb.append("<sub>");
                } else if ("sup".equalsIgnoreCase(name)) {
                    sb.append("<sup>");
                } else if ("a".equalsIgnoreCase(name)) {
                    String href = getHrefAttribute(parser);
                    String type = parser.getAttributeValue(null, "type");
                    String noteId = (href != null && href.startsWith("#")) ? href.substring(1) : href;
                    if ("note".equalsIgnoreCase(type) || (noteId != null && (noteId.startsWith("n") || noteId.startsWith("note")))) {
                        sb.append("<a class=\"footnote-link\" data-note-id=\"").append(escapeHtml(noteId)).append("\">");
                    } else {
                        sb.append("<a href=\"").append(escapeHtml(href != null ? href : "")).append("\">");
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                depth--;
                if (depth > 0) {
                    String name = parser.getName();
                    if ("emphasis".equalsIgnoreCase(name) || "style".equalsIgnoreCase(name)) {
                        sb.append("</em>");
                    } else if ("strong".equalsIgnoreCase(name)) {
                        sb.append("</strong>");
                    } else if ("strikethrough".equalsIgnoreCase(name)) {
                        sb.append("</s>");
                    } else if ("sub".equalsIgnoreCase(name)) {
                        sb.append("</sub>");
                    } else if ("sup".equalsIgnoreCase(name)) {
                        sb.append("</sup>");
                    } else if ("a".equalsIgnoreCase(name)) {
                        sb.append("</a>");
                    }
                }
            } else if (eventType == XmlPullParser.TEXT) {
                sb.append(escapeHtml(parser.getText()));
            }
        }
        return sb.toString();
    }

    public static String safeGetText(XmlPullParser parser) throws IOException, XmlPullParserException {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            int event = parser.next();
            if (event == XmlPullParser.START_TAG) {
                depth++;
            } else if (event == XmlPullParser.END_TAG) {
                depth--;
            } else if (event == XmlPullParser.TEXT) {
                sb.append(parser.getText());
            }
        }
        return sb.toString().trim();
    }

    private static String getHrefAttribute(XmlPullParser parser) {
        String href = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href");
        if (href == null) href = parser.getAttributeValue("http://www.gribuser.ru/xml/fictionbook/2.0", "href");
        if (href == null) href = parser.getAttributeValue(null, "href");
        if (href == null) href = parser.getAttributeValue(null, "l:href");
        return href;
    }

    public static InputStream openBookStream(Context context, Uri uri) throws IOException {
        InputStream is = FileUtils.openInputStreamSafely(context, uri);
        if (is == null) return null;

        BufferedInputStream bis = new BufferedInputStream(is, 64 * 1024);
        bis.mark(16);
        byte[] magic = new byte[4];
        int read = bis.read(magic);
        bis.reset();

        boolean isZip = (read == 4 && magic[0] == 0x50 && magic[1] == 0x4B && magic[2] == 0x03 && magic[3] == 0x04);
        if (isZip) {
            ZipInputStream zis = new ZipInputStream(bis);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase();
                if (entryName.endsWith(".fb2") || entryName.endsWith(".xml")) {
                    Log.d(TAG, "Found FB2 entry in zip: " + entry.getName());
                    return zis;
                }
            }
            zis.close();
            throw new IOException("No .fb2 or .xml found in zip archive: " + uri);
        }

        return bis;
    }

    private static String stripHtmlTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .trim();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
