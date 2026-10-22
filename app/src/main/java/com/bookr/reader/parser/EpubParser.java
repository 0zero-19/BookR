package com.bookr.reader.parser;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;

import com.bookr.reader.data.model.TocItem;
import com.bookr.reader.util.FileUtils;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EpubParser {
    private static final String TAG = "BookR.EpubParser";

    public static Fb2Metadata parseMetadata(Context context, Uri uri) {
        Fb2Metadata meta = new Fb2Metadata();
        try (InputStream is = FileUtils.openInputStreamSafely(context, uri)) {
            if (is == null) return meta;

            Map<String, byte[]> memoryFiles = readEpubKeyFiles(is, true);
            String opfPath = findOpfPath(memoryFiles);
            if (opfPath != null && memoryFiles.containsKey(opfPath)) {
                parseOpfMetadata(memoryFiles.get(opfPath), meta, memoryFiles, opfPath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse EPUB metadata for " + uri, e);
        }
        return meta;
    }

    public static Fb2Book parseBook(Context context, Uri uri) throws Exception {
        Fb2Book book = new Fb2Book();
        try (InputStream is = FileUtils.openInputStreamSafely(context, uri)) {
            if (is == null) throw new IOException("Cannot open EPUB stream: " + uri);

            Map<String, byte[]> memoryFiles = readEpubKeyFiles(is, false);
            String opfPath = findOpfPath(memoryFiles);
            if (opfPath == null || !memoryFiles.containsKey(opfPath)) {
                throw new IOException("Invalid EPUB: META-INF/container.xml or OPF file missing");
            }

            String opfDir = "";
            int slashIdx = opfPath.lastIndexOf('/');
            if (slashIdx != -1) {
                opfDir = opfPath.substring(0, slashIdx + 1);
            }

            OpfPackage opf = parseOpfFull(memoryFiles.get(opfPath), opfDir);
            book.setMetadata(opf.metadata);

            // Cover
            if (opf.coverHref != null && memoryFiles.containsKey(opf.coverHref)) {
                byte[] coverBytes = memoryFiles.get(opf.coverHref);
                book.getMetadata().setCoverImageBytes(coverBytes);
            }

            // Parse chapters in spine order
            int chapterIdx = 0;
            for (String spineHref : opf.spineHrefs) {
                if (memoryFiles.containsKey(spineHref)) {
                    byte[] chapterBytes = memoryFiles.get(spineHref);
                    String chapterHtml = new String(chapterBytes, StandardCharsets.UTF_8);

                    // Rewrite images to data URIs
                    chapterHtml = inlineImages(chapterHtml, spineHref, memoryFiles, opf.manifestMediaTypes);

                    // Extract chapter body
                    String bodyContent = extractHtmlBody(chapterHtml);
                    String title = extractHtmlTitle(chapterHtml);
                    if (title.isEmpty()) {
                        title = "Глава " + (chapterIdx + 1);
                    }

                    Fb2Section section = new Fb2Section();
                    section.setTitle(title);
                    section.setLevel(1);
                    section.appendContent(bodyContent);
                    book.addSection(section);

                    book.getToc().add(new TocItem(chapterIdx, title, "chapter_" + chapterIdx, 1));
                    chapterIdx++;
                }
            }

            // If TOC was present in NCX
            if (opf.ncxHref != null && memoryFiles.containsKey(opf.ncxHref)) {
                List<TocItem> ncxToc = parseNcxToc(memoryFiles.get(opf.ncxHref));
                if (!ncxToc.isEmpty()) {
                    book.setToc(ncxToc);
                }
            }
        }
        return book;
    }

    private static Map<String, byte[]> readEpubKeyFiles(InputStream is, boolean metadataOnly) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is, 64 * 1024));
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName().replace('\\', '/');
            if (entry.isDirectory()) continue;

            String lower = name.toLowerCase();
            boolean isKeyFile = lower.equals("meta-inf/container.xml")
                    || lower.endsWith(".opf")
                    || lower.endsWith(".ncx")
                    || (!metadataOnly && (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")));

            if (metadataOnly) {
                // For metadata only read container, opf and possible cover images
                isKeyFile = lower.equals("meta-inf/container.xml") || lower.endsWith(".opf") || lower.contains("cover");
            }

            if (isKeyFile) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                files.put(name, baos.toByteArray());
            }
        }
        return files;
    }

    private static String findOpfPath(Map<String, byte[]> files) {
        byte[] containerData = files.get("META-INF/container.xml");
        if (containerData == null) {
            for (String key : files.keySet()) {
                if (key.equalsIgnoreCase("meta-inf/container.xml")) {
                    containerData = files.get(key);
                    break;
                }
            }
        }
        if (containerData != null) {
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(new StringReader(new String(containerData, StandardCharsets.UTF_8)));
                int event = parser.getEventType();
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && "rootfile".equalsIgnoreCase(parser.getName())) {
                        String fullPath = parser.getAttributeValue(null, "full-path");
                        if (fullPath != null) return fullPath.replace('\\', '/');
                    }
                    event = parser.next();
                }
            } catch (Exception ignored) {}
        }

        // Fallback: search for any .opf
        for (String k : files.keySet()) {
            if (k.toLowerCase().endsWith(".opf")) return k;
        }
        return null;
    }

    private static void parseOpfMetadata(byte[] opfBytes, Fb2Metadata meta, Map<String, byte[]> files, String opfPath) {
        try {
            String opfDir = "";
            int slash = opfPath.lastIndexOf('/');
            if (slash != -1) opfDir = opfPath.substring(0, slash + 1);

            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new EntityFilterReader(new StringReader(new String(opfBytes, StandardCharsets.UTF_8))));

            int event = parser.getEventType();
            String coverId = null;
            Map<String, String> manifestHref = new HashMap<>();

            while (event != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                if (event == XmlPullParser.START_TAG) {
                    if ("title".equalsIgnoreCase(name) || "dc:title".equalsIgnoreCase(name)) {
                        meta.setTitle(Fb2Parser.safeGetText(parser));
                    } else if ("creator".equalsIgnoreCase(name) || "dc:creator".equalsIgnoreCase(name)) {
                        meta.setAuthor(Fb2Parser.safeGetText(parser));
                    } else if ("description".equalsIgnoreCase(name) || "dc:description".equalsIgnoreCase(name)) {
                        meta.setAnnotation(Fb2Parser.safeGetText(parser));
                    } else if ("meta".equalsIgnoreCase(name)) {
                        String mName = parser.getAttributeValue(null, "name");
                        if ("cover".equalsIgnoreCase(mName)) {
                            coverId = parser.getAttributeValue(null, "content");
                        }
                    } else if ("item".equalsIgnoreCase(name)) {
                        String id = parser.getAttributeValue(null, "id");
                        String href = parser.getAttributeValue(null, "href");
                        String props = parser.getAttributeValue(null, "properties");
                        if (id != null && href != null) {
                            manifestHref.put(id, opfDir + href);
                            if (props != null && props.contains("cover-image")) {
                                coverId = id;
                            }
                        }
                    }
                }
                event = parser.next();
            }

            if (coverId != null && manifestHref.containsKey(coverId)) {
                String coverPath = manifestHref.get(coverId);
                if (files.containsKey(coverPath)) {
                    meta.setCoverImageBytes(files.get(coverPath));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in parseOpfMetadata", e);
        }
    }

    private static OpfPackage parseOpfFull(byte[] opfBytes, String opfDir) throws Exception {
        OpfPackage opf = new OpfPackage();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new EntityFilterReader(new StringReader(new String(opfBytes, StandardCharsets.UTF_8))));

        int event = parser.getEventType();
        String coverId = null;
        String ncxId = null;
        Map<String, String> manifestIdToHref = new LinkedHashMap<>();

        while (event != XmlPullParser.END_DOCUMENT) {
            String name = parser.getName();
            if (event == XmlPullParser.START_TAG) {
                if ("title".equalsIgnoreCase(name) || "dc:title".equalsIgnoreCase(name)) {
                    opf.metadata.setTitle(Fb2Parser.safeGetText(parser));
                } else if ("creator".equalsIgnoreCase(name) || "dc:creator".equalsIgnoreCase(name)) {
                    opf.metadata.setAuthor(Fb2Parser.safeGetText(parser));
                } else if ("description".equalsIgnoreCase(name) || "dc:description".equalsIgnoreCase(name)) {
                    opf.metadata.setAnnotation(Fb2Parser.safeGetText(parser));
                } else if ("meta".equalsIgnoreCase(name)) {
                    String mName = parser.getAttributeValue(null, "name");
                    if ("cover".equalsIgnoreCase(mName)) {
                        coverId = parser.getAttributeValue(null, "content");
                    }
                } else if ("item".equalsIgnoreCase(name)) {
                    String id = parser.getAttributeValue(null, "id");
                    String href = parser.getAttributeValue(null, "href");
                    String mediaType = parser.getAttributeValue(null, "media-type");
                    String props = parser.getAttributeValue(null, "properties");
                    if (id != null && href != null) {
                        String fullHref = resolvePath(opfDir, href);
                        manifestIdToHref.put(id, fullHref);
                        if (mediaType != null) {
                            opf.manifestMediaTypes.put(fullHref, mediaType);
                        }
                        if (props != null && props.contains("cover-image")) {
                            coverId = id;
                        }
                        if ("application/x-dtbncx+xml".equalsIgnoreCase(mediaType)) {
                            ncxId = id;
                        }
                    }
                } else if ("spine".equalsIgnoreCase(name)) {
                    String tocAttr = parser.getAttributeValue(null, "toc");
                    if (tocAttr != null) ncxId = tocAttr;
                } else if ("itemref".equalsIgnoreCase(name)) {
                    String idref = parser.getAttributeValue(null, "idref");
                    if (idref != null && manifestIdToHref.containsKey(idref)) {
                        opf.spineHrefs.add(manifestIdToHref.get(idref));
                    }
                }
            }
            event = parser.next();
        }

        if (coverId != null) opf.coverHref = manifestIdToHref.get(coverId);
        if (ncxId != null) opf.ncxHref = manifestIdToHref.get(ncxId);

        return opf;
    }

    private static String resolvePath(String baseDir, String relativePath) {
        if (relativePath == null) return "";
        if (relativePath.startsWith("/")) return relativePath.substring(1);
        String combined = baseDir + relativePath;
        // Normalize ../ and ./
        String[] parts = combined.split("/");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            if (p.equals(".") || p.isEmpty()) continue;
            if (p.equals("..")) {
                if (!list.isEmpty()) list.remove(list.size() - 1);
            } else {
                list.add(p);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static String inlineImages(String html, String chapterPath, Map<String, byte[]> files, Map<String, String> mediaTypes) {
        int slash = chapterPath.lastIndexOf('/');
        String chapterDir = slash != -1 ? chapterPath.substring(0, slash + 1) : "";

        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String filePath = entry.getKey();
            String lower = filePath.toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".webp")) {
                String fileNameOnly = filePath.substring(filePath.lastIndexOf('/') + 1);
                String mediaType = mediaTypes.get(filePath);
                if (mediaType == null) {
                    mediaType = lower.endsWith(".png") ? "image/png" : "image/jpeg";
                }
                String base64 = Base64.encodeToString(entry.getValue(), Base64.NO_WRAP);
                String dataUri = "data:" + mediaType + ";base64," + base64;

                html = html.replace("\"" + fileNameOnly + "\"", "\"" + dataUri + "\"");
                html = html.replace("'" + fileNameOnly + "'", "'" + dataUri + "'");

                String relPath = filePath.startsWith(chapterDir) ? filePath.substring(chapterDir.length()) : filePath;
                html = html.replace("\"" + relPath + "\"", "\"" + dataUri + "\"");
                html = html.replace("'" + relPath + "'", "'" + dataUri + "'");
            }
        }
        return html;
    }

    private static String extractHtmlBody(String html) {
        int bodyStart = html.toLowerCase().indexOf("<body");
        if (bodyStart != -1) {
            int tagClose = html.indexOf('>', bodyStart);
            if (tagClose != -1) {
                int bodyEnd = html.toLowerCase().indexOf("</body>", tagClose);
                if (bodyEnd != -1) {
                    return html.substring(tagClose + 1, bodyEnd);
                }
                return html.substring(tagClose + 1);
            }
        }
        return html;
    }

    private static String extractHtmlTitle(String html) {
        int titleStart = html.toLowerCase().indexOf("<title>");
        if (titleStart != -1) {
            int titleEnd = html.toLowerCase().indexOf("</title>", titleStart);
            if (titleEnd != -1) {
                return html.substring(titleStart + 7, titleEnd).trim();
            }
        }
        // Try h1
        int h1Start = html.toLowerCase().indexOf("<h1");
        if (h1Start != -1) {
            int tagClose = html.indexOf('>', h1Start);
            int h1End = html.toLowerCase().indexOf("</h1>", tagClose);
            if (tagClose != -1 && h1End != -1) {
                return html.substring(tagClose + 1, h1End).replaceAll("<[^>]+>", "").trim();
            }
        }
        return "";
    }

    private static List<TocItem> parseNcxToc(byte[] ncxBytes) {
        List<TocItem> toc = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new EntityFilterReader(new StringReader(new String(ncxBytes, StandardCharsets.UTF_8))));

            int event = parser.getEventType();
            int idx = 0;
            String currentTitle = null;
            int level = 1;

            while (event != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                if (event == XmlPullParser.START_TAG) {
                    if ("navPoint".equalsIgnoreCase(name)) {
                        level++;
                    } else if ("text".equalsIgnoreCase(name)) {
                        currentTitle = Fb2Parser.safeGetText(parser);
                        if (currentTitle != null && !currentTitle.isEmpty()) {
                            toc.add(new TocItem(idx, currentTitle, "chapter_" + idx, Math.max(1, level - 1)));
                            idx++;
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if ("navPoint".equalsIgnoreCase(name)) {
                        level = Math.max(1, level - 1);
                    }
                }
                event = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in parseNcxToc", e);
        }
        return toc;
    }

    private static class OpfPackage {
        Fb2Metadata metadata = new Fb2Metadata();
        List<String> spineHrefs = new ArrayList<>();
        Map<String, String> manifestMediaTypes = new HashMap<>();
        String coverHref;
        String ncxHref;
    }
}
