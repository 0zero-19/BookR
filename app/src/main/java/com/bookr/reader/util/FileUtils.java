package com.bookr.reader.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtils {
    private static final String TAG = "FileUtils";

    public static InputStream openInputStreamSafely(Context context, Uri uri) throws IOException {
        if (uri == null) return null;
        String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (path != null) {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    return new FileInputStream(f);
                }
            }
        }
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is != null) return is;
        } catch (SecurityException se) {
            Log.w(TAG, "ContentResolver security exception for URI: " + uri + ", attempting file fallback");
            String path = uri.getPath();
            if (path != null) {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    return new FileInputStream(f);
                }
            }
            throw se;
        }

        // Direct file path fallback
        String path = uri.getPath();
        if (path != null) {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                return new FileInputStream(f);
            }
        }

        return null;
    }

    public static File copyToLocalBookCache(Context context, Uri uri, String preferredName) throws IOException {
        File booksDir = new File(context.getFilesDir(), "books");
        if (!booksDir.exists()) {
            booksDir.mkdirs();
        }
        String safeName = (preferredName == null ? "book" : preferredName.replaceAll("[^a-zA-Z0-9._-]", "_"));
        File targetFile = new File(booksDir, System.currentTimeMillis() + "_" + safeName);
        try (InputStream in = openInputStreamSafely(context, uri);
             FileOutputStream out = new FileOutputStream(targetFile)) {
            if (in == null) throw new IOException("Cannot open input stream for: " + uri);
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        }
        return targetFile;
    }

    public static String saveCoverToCache(Context context, byte[] imageBytes, String prefix) {
        if (imageBytes == null || imageBytes.length == 0) return null;
        try {
            File coversDir = new File(context.getFilesDir(), "covers");
            if (!coversDir.exists()) {
                coversDir.mkdirs();
            }
            String fileName = "cover_" + prefix.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + System.currentTimeMillis() + ".jpg";
            File targetFile = new File(coversDir, fileName);
            FileOutputStream fos = new FileOutputStream(targetFile);
            fos.write(imageBytes);
            fos.flush();
            fos.close();
            return targetFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save cover", e);
            return null;
        }
    }

    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 Б";
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f КБ", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0));
    }

    public static String formatDateTime(long timestamp) {
        if (timestamp <= 0) return "";
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 60 * 1000) {
            return "Только что";
        } else if (diff < 60 * 60 * 1000) {
            return (diff / (60 * 1000)) + " мин. назад";
        } else if (diff < 24 * 60 * 60 * 1000) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return "Сегодня в " + timeFormat.format(new Date(timestamp));
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
            return dateFormat.format(new Date(timestamp));
        }
    }

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int colIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (colIndex != -1) {
                        result = cursor.getString(colIndex);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "book";
    }

    public static long getFileSize(Context context, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int colIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (colIndex != -1) {
                        return cursor.getLong(colIndex);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            File f = new File(uri.getPath());
            if (f.exists()) return f.length();
        }
        return 0;
    }
}
