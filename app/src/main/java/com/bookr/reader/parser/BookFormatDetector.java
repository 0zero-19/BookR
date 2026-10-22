package com.bookr.reader.parser;

import android.content.Context;
import android.net.Uri;

import com.bookr.reader.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BookFormatDetector {

    public static BookFormat detectFormat(Context context, Uri uri) {
        String fileName = FileUtils.getFileName(context, uri).toLowerCase();

        if (fileName.endsWith(".fb2.zip")) return BookFormat.FB2_ZIP;
        if (fileName.endsWith(".fb2")) return BookFormat.FB2;
        if (fileName.endsWith(".epub")) return BookFormat.EPUB;
        if (fileName.endsWith(".pdf")) return BookFormat.PDF;
        if (fileName.endsWith(".txt") || fileName.endsWith(".text")) return BookFormat.TXT;
        if (fileName.endsWith(".html") || fileName.endsWith(".htm") || fileName.endsWith(".xhtml")) return BookFormat.HTML;
        if (fileName.endsWith(".rtf")) return BookFormat.RTF;
        if (fileName.endsWith(".mobi") || fileName.endsWith(".azw") || fileName.endsWith(".azw3")) return BookFormat.MOBI;

        // Fallback: detect by magic bytes
        try (InputStream is = FileUtils.openInputStreamSafely(context, uri)) {
            if (is == null) return BookFormat.UNKNOWN;
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
                    return detectZipFormat(bis);
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
        } catch (Exception ignored) {}

        return BookFormat.TXT;
    }

    private static BookFormat detectZipFormat(InputStream is) {
        try {
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry;
            boolean hasMimetype = false;
            boolean hasContainer = false;
            boolean hasFb2 = false;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (name.equals("mimetype") || name.equals("meta-inf/container.xml")) {
                    hasContainer = true;
                }
                if (name.endsWith(".fb2") || name.endsWith(".xml")) {
                    hasFb2 = true;
                }
            }

            if (hasContainer) return BookFormat.EPUB;
            if (hasFb2) return BookFormat.FB2_ZIP;
        } catch (Exception ignored) {}
        return BookFormat.EPUB; // Default for zip e-books
    }
}
