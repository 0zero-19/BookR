package com.bookr.reader.parser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EncodingDetector {
    private static final Pattern XML_ENCODING_PATTERN = Pattern.compile("<\\?xml[^>]*encoding=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_CHARSET_PATTERN = Pattern.compile("charset=[\"']?([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);

    public static Charset detectXmlCharset(BufferedInputStream bis) throws IOException {
        int readLimit = 4096;
        bis.mark(readLimit);
        byte[] buffer = new byte[readLimit];
        int bytesRead = bis.read(buffer, 0, readLimit);
        bis.reset();

        if (bytesRead <= 0) {
            return StandardCharsets.UTF_8;
        }

        // Check for UTF-8 BOM
        if (bytesRead >= 3 && (buffer[0] & 0xFF) == 0xEF && (buffer[1] & 0xFF) == 0xBB && (buffer[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }

        // Check for UTF-16 BOM
        if (bytesRead >= 2) {
            if ((buffer[0] & 0xFF) == 0xFE && (buffer[1] & 0xFF) == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
            if ((buffer[0] & 0xFF) == 0xFF && (buffer[1] & 0xFF) == 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
        }

        // Check XML declaration
        String preview = new String(buffer, 0, bytesRead, StandardCharsets.ISO_8859_1);
        Matcher matcher = XML_ENCODING_PATTERN.matcher(preview);
        if (matcher.find()) {
            String charsetName = matcher.group(1);
            Charset cs = resolveCharset(charsetName);
            if (cs != null) return cs;
        }

        // Check HTML meta charset
        Matcher htmlMatcher = HTML_CHARSET_PATTERN.matcher(preview);
        if (htmlMatcher.find()) {
            String charsetName = htmlMatcher.group(1);
            Charset cs = resolveCharset(charsetName);
            if (cs != null) return cs;
        }

        // If no declaration, check whether it is valid UTF-8
        if (isValidUtf8(buffer, bytesRead)) {
            return StandardCharsets.UTF_8;
        }

        // Russian text default when not valid UTF-8
        try {
            return Charset.forName("windows-1251");
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    public static Charset detectTextCharset(BufferedInputStream bis) throws IOException {
        int readLimit = 8192;
        bis.mark(readLimit);
        byte[] buffer = new byte[readLimit];
        int bytesRead = bis.read(buffer, 0, readLimit);
        bis.reset();

        if (bytesRead <= 0) return StandardCharsets.UTF_8;

        if (bytesRead >= 3 && (buffer[0] & 0xFF) == 0xEF && (buffer[1] & 0xFF) == 0xBB && (buffer[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }

        if (isValidUtf8(buffer, bytesRead)) {
            return StandardCharsets.UTF_8;
        }

        try {
            return Charset.forName("windows-1251");
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    public static Charset resolveCharset(String charsetName) {
        if (charsetName == null) return null;
        charsetName = charsetName.trim().toLowerCase();
        try {
            if (charsetName.equals("windows-1251") || charsetName.equals("cp1251") || charsetName.equals("cp-1251") || charsetName.equals("win-1251")) {
                return Charset.forName("windows-1251");
            }
            if (charsetName.equals("koi8-r") || charsetName.equals("koi8r")) {
                return Charset.forName("KOI8-R");
            }
            if (charsetName.equals("iso-8859-5")) {
                return Charset.forName("ISO-8859-5");
            }
            if (charsetName.equals("utf-8") || charsetName.equals("utf8")) {
                return StandardCharsets.UTF_8;
            }
            if (Charset.isSupported(charsetName)) {
                return Charset.forName(charsetName);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isValidUtf8(byte[] bytes, int length) {
        int i = 0;
        int nonAsciiCount = 0;
        while (i < length) {
            int b = bytes[i] & 0xFF;
            if (b <= 0x7F) {
                i++;
            } else if ((b & 0xE0) == 0xC0) {
                if (i + 1 >= length || (bytes[i + 1] & 0xC0) != 0x80) return false;
                nonAsciiCount++;
                i += 2;
            } else if ((b & 0xF0) == 0xE0) {
                if (i + 2 >= length || (bytes[i + 1] & 0xC0) != 0x80 || (bytes[i + 2] & 0xC0) != 0x80) return false;
                nonAsciiCount++;
                i += 3;
            } else if ((b & 0xF8) == 0xF0) {
                if (i + 3 >= length || (bytes[i + 1] & 0xC0) != 0x80 || (bytes[i + 2] & 0xC0) != 0x80 || (bytes[i + 3] & 0xC0) != 0x80) return false;
                nonAsciiCount++;
                i += 4;
            } else {
                return false;
            }
        }
        return nonAsciiCount > 0;
    }
}
