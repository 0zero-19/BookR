package com.bookr.reader.parser;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.bookr.reader.data.model.TocItem;
import com.bookr.reader.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class MobiParser {
    private static final String TAG = "BookR.MobiParser";

    public static Fb2Metadata parseMetadata(Context context, Uri uri) {
        Fb2Metadata meta = new Fb2Metadata();
        String fileName = FileUtils.getFileName(context, uri);
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        meta.setTitle(fileName);

        try (InputStream is = FileUtils.openInputStreamSafely(context, uri)) {
            if (is == null) return meta;
            byte[] fileBytes = readFully(is, 16384);
            if (fileBytes.length >= 78) {
                String magic = new String(fileBytes, 60, 8, StandardCharsets.ISO_8859_1);
                if ("BOOKMOBI".equals(magic)) {
                    int rec0Offset = readInt32(fileBytes, 78);
                    if (rec0Offset + 84 <= fileBytes.length) {
                        int titleOffsetInRec0 = readInt32(fileBytes, rec0Offset + 16 + 68);
                        int titleLen = readInt32(fileBytes, rec0Offset + 16 + 72);
                        int absTitleOffset = rec0Offset + titleOffsetInRec0;
                        if (absTitleOffset + titleLen <= fileBytes.length && titleLen > 0 && titleLen < 256) {
                            String title = new String(fileBytes, absTitleOffset, titleLen, StandardCharsets.UTF_8);
                            meta.setTitle(title.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse MOBI metadata", e);
        }
        return meta;
    }

    public static Fb2Book parseBook(Context context, Uri uri) throws Exception {
        Fb2Book book = new Fb2Book();
        Fb2Metadata meta = parseMetadata(context, uri);
        book.setMetadata(meta);

        try (InputStream is = FileUtils.openInputStreamSafely(context, uri)) {
            if (is == null) throw new Exception("Cannot open MOBI stream: " + uri);

            byte[] fileBytes = readFully(is, 30 * 1024 * 1024);
            if (fileBytes.length < 78) throw new Exception("File too small to be a valid MOBI");

            String magic = new String(fileBytes, 60, 8, StandardCharsets.ISO_8859_1);
            if (!"BOOKMOBI".equals(magic)) {
                throw new Exception("Not a valid Mobipocket file");
            }

            int numRecords = readInt16(fileBytes, 76);
            int[] recordOffsets = new int[numRecords];
            for (int r = 0; r < numRecords; r++) {
                recordOffsets[r] = readInt32(fileBytes, 78 + r * 8);
            }

            int rec0 = recordOffsets[0];
            int compression = readInt16(fileBytes, rec0);
            int textRecordCount = readInt16(fileBytes, rec0 + 8);
            int encryption = readInt16(fileBytes, rec0 + 16 + 12);

            if (encryption != 0) {
                throw new Exception("Файл MOBI защищён DRM-шифрованием и не может быть прочитан без снятия защиты.");
            }

            ByteArrayOutputStream textBytes = new ByteArrayOutputStream();
            int countToRead = Math.min(textRecordCount, numRecords - 1);

            for (int r = 1; r <= countToRead; r++) {
                int start = recordOffsets[r];
                int end = (r + 1 < numRecords) ? recordOffsets[r + 1] : fileBytes.length;
                int len = end - start;
                if (len <= 0 || start + len > fileBytes.length) continue;

                if (compression == 1) {
                    textBytes.write(fileBytes, start, len);
                } else if (compression == 2) {
                    // PalmDOC LZ77
                    byte[] decompressed = decompressPalmDoc(fileBytes, start, len);
                    textBytes.write(decompressed);
                } else {
                    throw new Exception("Неподдерживаемый тип сжатия MOBI (Huff/CDIC: " + compression + ")");
                }
            }

            byte[] fullTextBytes = textBytes.toByteArray();
            Charset cs = StandardCharsets.UTF_8;
            String rawText = new String(fullTextBytes, cs);

            // Clean up text / HTML
            String htmlContent;
            if (rawText.contains("<html") || rawText.contains("<p>") || rawText.contains("<div>")) {
                htmlContent = rawText;
            } else {
                htmlContent = "<p>" + rawText.replace("\n\n", "</p><p>").replace("\n", "<br/>") + "</p>";
            }

            Fb2Section section = new Fb2Section();
            section.setTitle(meta.getTitle());
            section.setLevel(1);
            section.appendContent(htmlContent);
            book.addSection(section);
            book.getToc().add(new TocItem(0, meta.getTitle(), "chapter_0", 1));
        }
        return book;
    }

    private static byte[] decompressPalmDoc(byte[] src, int offset, int length) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(length * 2);
        int i = offset;
        int end = offset + length;

        while (i < end) {
            int b = src[i++] & 0xFF;

            if (b >= 1 && b <= 8) {
                // Next b bytes are literal
                while (b > 0 && i < end) {
                    out.write(src[i++]);
                    b--;
                }
            } else if (b <= 0x7F) {
                // Literal byte
                out.write(b);
            } else if (b >= 0xC0) {
                // Space + (b ^ 0x80)
                out.write(' ');
                out.write(b ^ 0x80);
            } else {
                // Distance / length pair (0x80 to 0xBF)
                if (i >= end) break;
                int b2 = src[i++] & 0xFF;
                int distance = ((b & 0x3F) << 3) | (b2 >> 5);
                int count = (b2 & 0x1F) + 3;

                byte[] current = out.toByteArray();
                int srcPos = current.length - distance;
                if (srcPos >= 0) {
                    for (int k = 0; k < count; k++) {
                        if (srcPos + k < current.length) {
                            out.write(current[srcPos + k]);
                        } else {
                            // Run-length overflow: repeat written character
                            out.write(current[srcPos]);
                        }
                    }
                }
            }
        }
        return out.toByteArray();
    }

    private static int readInt16(byte[] data, int offset) {
        if (offset + 1 >= data.length) return 0;
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int readInt32(byte[] data, int offset) {
        if (offset + 3 >= data.length) return 0;
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static byte[] readFully(InputStream is, int maxBytes) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
            if (baos.size() >= maxBytes) break;
        }
        return baos.toByteArray();
    }
}
