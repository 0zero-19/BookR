package com.bookr.reader.parser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.bookr.reader.data.model.TocItem;
import com.bookr.reader.util.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class BookParserManager {
    private static final String TAG = "BookR.ParserManager";

    public static Fb2Metadata parseMetadata(Context context, Uri uri) {
        BookFormat format = BookFormatDetector.detectFormat(context, uri);
        Log.d(TAG, "Parsing metadata for " + uri + " detected format: " + format);

        switch (format) {
            case EPUB:
                return EpubParser.parseMetadata(context, uri);
            case TXT:
                return TxtParser.parseMetadata(context, uri);
            case HTML:
                return HtmlParser.parseMetadata(context, uri);
            case RTF:
                return RtfParser.parseMetadata(context, uri);
            case MOBI:
                return MobiParser.parseMetadata(context, uri);
            case PDF:
                return parsePdfMetadata(context, uri);
            case FB2:
            case FB2_ZIP:
            default:
                return Fb2Parser.parseMetadata(context, uri);
        }
    }

    public static Fb2Book parseBook(Context context, Uri uri) throws Exception {
        BookFormat format = BookFormatDetector.detectFormat(context, uri);
        Log.d(TAG, "Parsing full book for " + uri + " detected format: " + format);

        switch (format) {
            case EPUB:
                return EpubParser.parseBook(context, uri);
            case TXT:
                return TxtParser.parseBook(context, uri);
            case HTML:
                return HtmlParser.parseBook(context, uri);
            case RTF:
                return RtfParser.parseBook(context, uri);
            case MOBI:
                return MobiParser.parseBook(context, uri);
            case PDF:
                return parsePdfBook(context, uri);
            case FB2:
            case FB2_ZIP:
            default:
                return Fb2Parser.parseBook(context, uri);
        }
    }

    private static Fb2Metadata parsePdfMetadata(Context context, Uri uri) {
        Fb2Metadata meta = new Fb2Metadata();
        String name = FileUtils.getFileName(context, uri);
        if (name.contains(".")) name = name.substring(0, name.lastIndexOf('.'));
        meta.setTitle(name);

        File tempFile = null;
        try {
            ParcelFileDescriptor pfd = null;
            if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                pfd = ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                try {
                    pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                } catch (Exception e) {
                    tempFile = FileUtils.copyToLocalBookCache(context, uri, "pdf_meta.pdf");
                    pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
                }
            }

            if (pfd != null) {
                PdfRenderer renderer = new PdfRenderer(pfd);
                if (renderer.getPageCount() > 0) {
                    PdfRenderer.Page page = renderer.openPage(0);
                    int w = Math.min(600, page.getWidth());
                    int h = (int) (w * ((float) page.getHeight() / page.getWidth()));
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                    meta.setCoverImageBytes(baos.toByteArray());
                    bmp.recycle();
                }
                renderer.close();
                pfd.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to render PDF thumbnail", e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
        return meta;
    }

    private static Fb2Book parsePdfBook(Context context, Uri uri) throws Exception {
        Fb2Book book = new Fb2Book();
        Fb2Metadata meta = parsePdfMetadata(context, uri);
        book.setMetadata(meta);

        // For PDF, we create a book section marked as PDF
        Fb2Section sec = new Fb2Section();
        sec.setTitle(meta.getTitle());
        sec.setLevel(1);
        sec.appendContent("<div id=\"pdf-placeholder\">PDF Document</div>");
        book.addSection(sec);

        // Count pages
        ParcelFileDescriptor pfd = null;
        File tempFile = null;
        try {
            if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                pfd = ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                try {
                    pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                } catch (Exception e) {
                    tempFile = FileUtils.copyToLocalBookCache(context, uri, "pdf_read.pdf");
                    pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
                }
            }
            if (pfd != null) {
                PdfRenderer renderer = new PdfRenderer(pfd);
                int count = renderer.getPageCount();
                for (int i = 0; i < count; i++) {
                    book.getToc().add(new TocItem(i, "Страница " + (i + 1), "page_" + i, 1));
                }
                renderer.close();
                pfd.close();
            }
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        return book;
    }
}
