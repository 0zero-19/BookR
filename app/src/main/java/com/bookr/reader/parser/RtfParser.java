package com.bookr.reader.parser;

import android.content.Context;
import android.net.Uri;

import com.bookr.reader.data.model.TocItem;
import com.bookr.reader.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Stack;

public class RtfParser {

    public static Fb2Metadata parseMetadata(Context context, Uri uri) {
        Fb2Metadata meta = new Fb2Metadata();
        String fileName = FileUtils.getFileName(context, uri);
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        meta.setTitle(fileName);
        return meta;
    }

    public static Fb2Book parseBook(Context context, Uri uri) throws Exception {
        Fb2Book book = new Fb2Book();
        Fb2Metadata meta = parseMetadata(context, uri);
        book.setMetadata(meta);

        try (InputStream is = FileUtils.openInputStreamSafely(context, uri)) {
            if (is == null) throw new Exception("Cannot open RTF stream: " + uri);

            BufferedInputStream bis = new BufferedInputStream(is, 64 * 1024);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = bis.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            byte[] rtfBytes = baos.toByteArray();

            // Default charset for Cyrillic RTF
            Charset charset = Charset.forName("windows-1251");
            String rtfText = parseRtfToHtml(rtfBytes, charset);

            Fb2Section section = new Fb2Section();
            section.setTitle(meta.getTitle());
            section.setLevel(1);
            section.appendContent(rtfText);
            book.addSection(section);

            book.getToc().add(new TocItem(0, meta.getTitle(), "chapter_0", 1));
        }
        return book;
    }

    public static String parseRtfToHtml(byte[] bytes, Charset defaultCharset) {
        StringBuilder out = new StringBuilder(bytes.length / 2);
        out.append("<p>");

        int len = bytes.length;
        int i = 0;
        int groupDepth = 0;
        int skipGroupDepth = -1;
        boolean bold = false;
        boolean italic = false;
        int ucSkip = 1;
        int skipCharsAfterUnicode = 0;
        Stack<Integer> ucSkipStack = new Stack<>();

        ByteArrayOutputStream hexByteCollector = new ByteArrayOutputStream();

        while (i < len) {
            byte b = bytes[i];
            char c = (char) (b & 0xFF);

            if (c == '{') {
                flushHex(hexByteCollector, out, defaultCharset);
                skipCharsAfterUnicode = 0;
                ucSkipStack.push(ucSkip);
                groupDepth++;
                i++;
            } else if (c == '}') {
                flushHex(hexByteCollector, out, defaultCharset);
                skipCharsAfterUnicode = 0;
                if (skipGroupDepth == groupDepth) {
                    skipGroupDepth = -1;
                }
                if (!ucSkipStack.isEmpty()) {
                    ucSkip = ucSkipStack.pop();
                }
                groupDepth--;
                i++;
            } else if (skipGroupDepth != -1) {
                // Inside skipped metadata group (e.g. fonttbl, colortbl)
                i++;
            } else if (c == '\\') {
                flushHex(hexByteCollector, out, defaultCharset);
                i++;
                if (i >= len) break;
                char nextChar = (char) (bytes[i] & 0xFF);

                if (nextChar == '*') {
                    // Ignorable destination group {\*\...}
                    skipGroupDepth = groupDepth;
                    skipCharsAfterUnicode = 0;
                    i++;
                } else if (nextChar == '\\' || nextChar == '{' || nextChar == '}') {
                    if (skipCharsAfterUnicode > 0) {
                        skipCharsAfterUnicode--;
                    } else {
                        out.append(nextChar);
                    }
                    i++;
                } else if (nextChar == '\'') {
                    // Hex byte \'XX
                    i++;
                    if (i + 1 < len) {
                        int h1 = Character.digit((char) (bytes[i] & 0xFF), 16);
                        int h2 = Character.digit((char) (bytes[i + 1] & 0xFF), 16);
                        if (h1 != -1 && h2 != -1) {
                            if (skipCharsAfterUnicode > 0) {
                                skipCharsAfterUnicode--;
                            } else {
                                hexByteCollector.write((h1 << 4) | h2);
                            }
                            i += 2;
                        }
                    }
                } else {
                    skipCharsAfterUnicode = 0;
                    // Control word
                    StringBuilder word = new StringBuilder();
                    while (i < len && Character.isLetter((char) (bytes[i] & 0xFF))) {
                        word.append((char) (bytes[i] & 0xFF));
                        i++;
                    }
                    // Optional parameter
                    StringBuilder param = new StringBuilder();
                    if (i < len && (bytes[i] == '-' || Character.isDigit((char) (bytes[i] & 0xFF)))) {
                        while (i < len && (bytes[i] == '-' || Character.isDigit((char) (bytes[i] & 0xFF)))) {
                            param.append((char) (bytes[i] & 0xFF));
                            i++;
                        }
                    }
                    // Trailing space delimiter consumed
                    if (i < len && bytes[i] == ' ') {
                        i++;
                    }

                    String cw = word.toString();
                    if (cw.equals("fonttbl") || cw.equals("colortbl") || cw.equals("stylesheet") || cw.equals("info") || cw.equals("pict")) {
                        skipGroupDepth = groupDepth;
                    } else if (cw.equals("ansicpg")) {
                        try {
                            defaultCharset = Charset.forName("windows-" + param);
                        } catch (Exception ignored) {}
                    } else if (cw.equals("uc") && param.length() > 0) {
                        try {
                            ucSkip = Math.max(0, Integer.parseInt(param.toString()));
                        } catch (Exception ignored) {}
                    } else if (cw.equals("par")) {
                        if (bold) out.append("</strong>");
                        if (italic) out.append("</em>");
                        out.append("</p><p>");
                        if (bold) out.append("<strong>");
                        if (italic) out.append("<em>");
                    } else if (cw.equals("line")) {
                        out.append("<br/>");
                    } else if (cw.equals("b")) {
                        boolean turnOn = param.length() == 0 || !param.toString().equals("0");
                        if (turnOn && !bold) {
                            out.append("<strong>");
                            bold = true;
                        } else if (!turnOn && bold) {
                            out.append("</strong>");
                            bold = false;
                        }
                    } else if (cw.equals("i")) {
                        boolean turnOn = param.length() == 0 || !param.toString().equals("0");
                        if (turnOn && !italic) {
                            out.append("<em>");
                            italic = true;
                        } else if (!turnOn && italic) {
                            out.append("</em>");
                            italic = false;
                        }
                    } else if (cw.equals("u") && param.length() > 0) {
                        try {
                            int code = Integer.parseInt(param.toString());
                            if (code < 0) code += 65536;
                            char ch = (char) code;
                            if (ch == '<') out.append("&lt;");
                            else if (ch == '>') out.append("&gt;");
                            else if (ch == '&') out.append("&amp;");
                            else out.append(ch);
                            skipCharsAfterUnicode = ucSkip;
                        } catch (Exception ignored) {}
                    }
                }
            } else if (c == '\r' || c == '\n') {
                i++;
            } else {
                flushHex(hexByteCollector, out, defaultCharset);
                if (skipCharsAfterUnicode > 0) {
                    skipCharsAfterUnicode--;
                } else {
                    if (c == '<') out.append("&lt;");
                    else if (c == '>') out.append("&gt;");
                    else if (c == '&') out.append("&amp;");
                    else out.append(c);
                }
                i++;
            }
        }

        flushHex(hexByteCollector, out, defaultCharset);
        if (bold) out.append("</strong>");
        if (italic) out.append("</em>");
        out.append("</p>");
        return out.toString();
    }

    private static void flushHex(ByteArrayOutputStream collector, StringBuilder out, Charset charset) {
        if (collector.size() > 0) {
            byte[] bytes = collector.toByteArray();
            collector.reset();
            String decoded = new String(bytes, charset);
            for (int j = 0; j < decoded.length(); j++) {
                char ch = decoded.charAt(j);
                if (ch == '<') out.append("&lt;");
                else if (ch == '>') out.append("&gt;");
                else if (ch == '&') out.append("&amp;");
                else out.append(ch);
            }
        }
    }
}
