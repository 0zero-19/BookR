package com.bookr.reader.parser;

import android.content.Context;
import android.net.Uri;

import com.bookr.reader.data.model.TocItem;
import com.bookr.reader.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlParser {

    public static Fb2Metadata parseMetadata(Context context, Uri uri) {
        Fb2Metadata meta = new Fb2Metadata();
        String fileName = FileUtils.getFileName(context, uri);
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        meta.setTitle(fileName);

        try (InputStream is = FileUtils.openInputStreamSafely(context, uri)) {
            if (is != null) {
                BufferedInputStream bis = new BufferedInputStream(is, 16384);
                Charset cs = EncodingDetector.detectXmlCharset(bis);
                byte[] buf = new byte[16384];
                int read = bis.read(buf);
                if (read > 0) {
                    String str = new String(buf, 0, read, cs);
                    Pattern p = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(str);
                    if (m.find()) {
                        meta.setTitle(m.group(1).trim());
                    }
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
            if (is == null) throw new Exception("Cannot open HTML stream: " + uri);

            BufferedInputStream bis = new BufferedInputStream(is, 64 * 1024);
            Charset cs = EncodingDetector.detectXmlCharset(bis);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = bis.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            String rawHtml = new String(baos.toByteArray(), cs);

            // Strip scripts and styles for security and consistent reading style
            String cleanHtml = rawHtml.replaceAll("(?is)<script.*?>.*?</script>", "")
                    .replaceAll("(?is)<style.*?>.*?</style>", "");

            // Extract body content
            int bodyStart = cleanHtml.toLowerCase().indexOf("<body");
            String bodyContent = cleanHtml;
            if (bodyStart != -1) {
                int tagClose = cleanHtml.indexOf('>', bodyStart);
                if (tagClose != -1) {
                    int bodyEnd = cleanHtml.toLowerCase().indexOf("</body>", tagClose);
                    if (bodyEnd != -1) {
                        bodyContent = cleanHtml.substring(tagClose + 1, bodyEnd);
                    } else {
                        bodyContent = cleanHtml.substring(tagClose + 1);
                    }
                }
            }

            Fb2Section section = new Fb2Section();
            section.setTitle(meta.getTitle());
            section.setLevel(1);
            section.appendContent(bodyContent);
            book.addSection(section);

            // Find headers for TOC
            Pattern hPattern = Pattern.compile("(?is)<h([1-3])[^>]*>(.*?)</h\\1>");
            Matcher hMatcher = hPattern.matcher(bodyContent);
            int idx = 0;
            while (hMatcher.find()) {
                int lvl = Integer.parseInt(hMatcher.group(1));
                String text = hMatcher.group(2).replaceAll("<[^>]+>", "").trim();
                if (!text.isEmpty()) {
                    book.getToc().add(new TocItem(idx, text, "chapter_" + idx, lvl));
                    idx++;
                }
            }

            if (book.getToc().isEmpty()) {
                book.getToc().add(new TocItem(0, meta.getTitle(), "chapter_0", 1));
            }
        }
        return book;
    }
}
