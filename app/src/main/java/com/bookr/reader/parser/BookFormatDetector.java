package com.bookr.reader.parser;

import android.content.Context;
import android.net.Uri;

import com.bookr.reader.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BookFormatDetector {

    public static BookFormat detectFormat(Context context, Uri uri) {
        // 1. Inspect magic bytes first to avoid parsing binary zip as xml
        try (InputStream is = FileUtils.openInputStreamSafely(context, uri)) {
            if (is != null) {
                BufferedInputStream bis = new BufferedInputStream(is, 4096);
                bis.mark(1024);
                byte[] header = new byte[1024];
                int read = bis.read(header);
                bis.reset();

                if (read >= 4) {
                    // PDF magic: %PDF
                    if (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
                        return BookFormat.PDF;
                    }
                    // RTF magic: {\rtf
                    if (header[0] == 0x7B && header[1] == 0x5C && header[2] == 0x72 && header[3] == 0x74 && header[4] == 0x66) {
                        return BookFormat.RTF;
                    }
                    // ZIP magic: PK\x03\x04
                    if (header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04) {
                        return detectZipFormat(bis, FileUtils.getFileName(context, uri));
                    }
                    // MOBI check
                    if (read >= 68) {
                        String mobiHeader = new String(header, 60, 8, StandardCharsets.ISO_8859_1);
                        if ("BOOKMOBI".equals(mobiHeader)) {
                            return BookFormat.MOBI;
                        }
                    }

                    String preview = new String(header, 0, Math.min(read, 512), StandardCharsets.ISO_8859_1).toLowerCase();
                    if (preview.contains("<fictionbook")) {
                        return BookFormat.FB2;
                    }
                    if (preview.contains("<!doctype html") || preview.contains("<html")) {
                        return BookFormat.HTML;
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Check filename extension as fallback
        String fileName = FileUtils.getFileName(context, uri).toLowerCase();
        if (fileName.endsWith(".fb2.zip")) return BookFormat.FB2_ZIP;
        if (fileName.endsWith(".fb2")) return BookFormat.FB2;
        if (fileName.endsWith(".epub")) return BookFormat.EPUB;
        if (fileName.endsWith(".pdf")) return BookFormat.PDF;
        if (fileName.endsWith(".txt") || fileName.endsWith(".text")) return BookFormat.TXT;
        if (fileName.endsWith(".html") || fileName.endsWith(".htm") || fileName.endsWith(".xhtml")) return BookFormat.HTML;
        if (fileName.endsWith(".rtf")) return BookFormat.RTF;
        if (fileName.endsWith(".mobi") || fileName.endsWith(".azw") || fileName.endsWith(".azw3")) return BookFormat.MOBI;

        return BookFormat.TXT;
    }

    private static BookFormat detectZipFormat(InputStream is, String fileName) {
        String lowerFile = fileName != null ? fileName.toLowerCase() : "";
        try {
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry;
            boolean hasMimetype = false;
            boolean hasContainer = false;
            boolean hasFb2 = false;
            boolean hasOpfOrXhtml = false;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/').toLowerCase();
                if (name.equals("mimetype") || name.equals("meta-inf/container.xml")) {
                    hasContainer = true;
                } else if (name.endsWith(".opf") || name.endsWith(".xhtml")) {
                    hasOpfOrXhtml = true;
                } else if (name.endsWith(".fb2") || (name.endsWith(".xml") && !name.contains("container") && !name.contains("meta-inf"))) {
                    hasFb2 = true;
                }
            }

            if (hasContainer || hasOpfOrXhtml || lowerFile.endsWith(".epub")) {
                return BookFormat.EPUB;
            }
            if (hasFb2 || lowerFile.endsWith(".fb2.zip") || lowerFile.endsWith(".fb2")) {
                return BookFormat.FB2_ZIP;
            }
        } catch (Exception ignored) {}

        if (lowerFile.endsWith(".fb2.zip") || lowerFile.endsWith(".fb2")) {
            return BookFormat.FB2_ZIP;
        }
        return BookFormat.EPUB; // Standard zip e-book container default
    }
}
