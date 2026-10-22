package com.bookr.reader.parser;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * FilterReader that resolves HTML entities found in Russian FB2 and HTML documents
 * to valid Unicode characters before feeding to standard XML parsers.
 * This completely prevents "undefined entity: mdash / laquo / nbsp" crashes.
 */
public class EntityFilterReader extends FilterReader {

    private static final Map<String, String> ENTITIES = new HashMap<>();

    static {
        ENTITIES.put("laquo", "«");
        ENTITIES.put("raquo", "»");
        ENTITIES.put("mdash", "—");
        ENTITIES.put("ndash", "–");
        ENTITIES.put("nbsp", "\u00A0");
        ENTITIES.put("hellip", "…");
        ENTITIES.put("copy", "©");
        ENTITIES.put("reg", "®");
        ENTITIES.put("trade", "™");
        ENTITIES.put("deg", "°");
        ENTITIES.put("bull", "•");
        ENTITIES.put("shy", "");
        ENTITIES.put("prime", "′");
        ENTITIES.put("Prime", "″");
        ENTITIES.put("sect", "§");
        ENTITIES.put("para", "¶");
        ENTITIES.put("minus", "−");
        ENTITIES.put("times", "×");
        ENTITIES.put("divide", "÷");
        ENTITIES.put("plusmn", "±");
        ENTITIES.put("acute", "´");
        ENTITIES.put("cedil", "¸");
        ENTITIES.put("uml", "¨");
    }

    private final StringBuilder buffer = new StringBuilder();

    public EntityFilterReader(Reader in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        if (buffer.length() > 0) {
            char c = buffer.charAt(0);
            buffer.deleteCharAt(0);
            return c;
        }

        int c = in.read();
        if (c != '&') {
            return c;
        }

        // Potential entity
        StringBuilder entityName = new StringBuilder();
        int next;
        while ((next = in.read()) != -1) {
            char ch = (char) next;
            if (ch == ';') {
                String ent = entityName.toString();
                // Check if standard XML entity
                if ("amp".equals(ent) || "lt".equals(ent) || "gt".equals(ent) || "quot".equals(ent) || "apos".equals(ent)) {
                    buffer.append(ent).append(';');
                    return '&';
                }
                // Check if numeric entity &#...;
                if (ent.startsWith("#")) {
                    buffer.append(ent).append(';');
                    return '&';
                }
                // Resolve known HTML entity
                String replacement = ENTITIES.get(ent.toLowerCase());
                if (replacement != null) {
                    if (replacement.isEmpty()) {
                        return read(); // shy or empty
                    }
                    if (replacement.length() == 1) {
                        return replacement.charAt(0);
                    } else {
                        buffer.append(replacement.substring(1));
                        return replacement.charAt(0);
                    }
                } else {
                    // Unknown entity: emit space to prevent XML parser crash
                    return ' ';
                }
            } else if (Character.isLetterOrDigit(ch) || ch == '#') {
                entityName.append(ch);
                if (entityName.length() > 12) {
                    // Not a valid entity name, put back
                    buffer.append(entityName);
                    return '&';
                }
            } else {
                // Not an entity (e.g. "& " or "&<")
                buffer.append(entityName).append(ch);
                return '&';
            }
        }

        buffer.append(entityName);
        return '&';
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (len <= 0) return 0;
        int count = 0;
        while (count < len) {
            int c = read();
            if (c == -1) {
                return count == 0 ? -1 : count;
            }
            cbuf[off + count] = (char) c;
            count++;
            // If nothing in buffer and ready is false, break early
            if (buffer.length() == 0 && !in.ready()) {
                break;
            }
        }
        return count;
    }
}
