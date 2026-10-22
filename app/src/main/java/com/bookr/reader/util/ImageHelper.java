package com.bookr.reader.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

public class ImageHelper {
    private static final String TAG = "BookR.ImageHelper";

    /**
     * Copies chosen image or GIF from content URI into private app storage (files/themes/)
     * so access remains persistent even after reboot or permission revocation.
     */
    public static String copyMediaToThemesDir(Context context, Uri uri, String preferredExtension) {
        if (uri == null) return null;
        try {
            File dir = new File(context.getFilesDir(), "themes");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String ext = preferredExtension != null ? preferredExtension : ".jpg";
            if (!ext.startsWith(".")) ext = "." + ext;

            // Check if URI is already a local file inside themes
            if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                File source = new File(uri.getPath());
                if (source.getAbsolutePath().startsWith(dir.getAbsolutePath())) {
                    return source.getAbsolutePath();
                }
            }

            // Detect if content is GIF
            try (InputStream testIs = context.getContentResolver().openInputStream(uri)) {
                if (testIs != null) {
                    byte[] header = new byte[6];
                    int read = testIs.read(header);
                    if (read >= 6) {
                        String hStr = new String(header);
                        if (hStr.startsWith("GIF87a") || hStr.startsWith("GIF89a")) {
                            ext = ".gif";
                        }
                    }
                }
            } catch (Exception ignored) {}

            String filename = "theme_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            File dest = new File(dir, filename);

            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) return null;
                byte[] buf = new byte[16 * 1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy media to themes directory", e);
            return null;
        }
    }

    /**
     * Checks if given file path is an animated GIF by inspecting header.
     */
    public static boolean isGifFile(String path) {
        if (path == null) return false;
        if (path.toLowerCase().endsWith(".gif")) return true;
        try (InputStream is = new FileInputStream(path)) {
            byte[] h = new byte[6];
            int read = is.read(h);
            if (read >= 6) {
                String str = new String(h);
                return str.startsWith("GIF87a") || str.startsWith("GIF89a");
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Efficiently loads and downsamples bitmap to fit target dimensions without OOM.
     */
    public static Bitmap loadSampledBitmap(String filePath, int reqWidth, int reqHeight) {
        if (filePath == null) return null;
        File file = new File(filePath);
        if (!file.exists()) return null;

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);

            int width = options.outWidth;
            int height = options.outHeight;
            if (width <= 0 || height <= 0) return null;

            int targetW = reqWidth > 0 ? reqWidth : 1080;
            int targetH = reqHeight > 0 ? reqHeight : 1920;

            int inSampleSize = 1;
            while ((width / inSampleSize) > targetW * 1.2 || (height / inSampleSize) > targetH * 1.2) {
                inSampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = Math.max(1, inSampleSize);
            options.inPreferredConfig = Bitmap.Config.RGB_565; // Saves 50% memory

            return BitmapFactory.decodeFile(filePath, options);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to decode bitmap: " + filePath, t);
            return null;
        }
    }

    /**
     * Creates a ColorMatrix incorporating brightness, contrast, saturation, dimming, and tint.
     */
    public static ColorMatrix createFilterMatrix(float brightness, float contrast, float saturation, float dim) {
        ColorMatrix cm = new ColorMatrix();

        // 1. Saturation (0.0 = grayscale, 1.0 = normal, 2.0 = extra vivid)
        if (saturation != 1.0f) {
            cm.setSaturation(Math.max(0f, saturation));
        }

        // 2. Contrast & Brightness
        // contrast scale: c (e.g. 1.0 is neutral)
        // brightness translate: b * 255 (-1.0 to 1.0 -> -255 to 255)
        float c = Math.max(0f, contrast);
        float b = brightness * 128f;
        // contrast transform formula: factor*(color - 128) + 128 + brightness
        float translate = (1.0f - c) * 128f + b;

        ColorMatrix cbMatrix = new ColorMatrix(new float[]{
                c, 0, 0, 0, translate,
                0, c, 0, 0, translate,
                0, 0, c, 0, translate,
                0, 0, 0, 1, 0
        });
        cm.postConcat(cbMatrix);

        // 3. Dimming overlay (reduces lightness toward black)
        if (dim > 0.01f) {
            float d = 1.0f - Math.min(1.0f, dim);
            ColorMatrix dimMatrix = new ColorMatrix(new float[]{
                    d, 0, 0, 0, 0,
                    0, d, 0, 0, 0,
                    0, 0, d, 0, 0,
                    0, 0, 0, 1, 0
            });
            cm.postConcat(dimMatrix);
        }

        return cm;
    }

    /**
     * Fast pure-Java StackBlur implementation (Mario Klingemann).
     * Works on any Android version without RenderScript or native libraries.
     */
    public static Bitmap fastBlur(Bitmap sentBitmap, int radius) {
        if (radius < 1 || sentBitmap == null) return sentBitmap;

        try {
            Bitmap bitmap = sentBitmap.copy(sentBitmap.getConfig() != null ? sentBitmap.getConfig() : Bitmap.Config.ARGB_8888, true);
            if (bitmap == null) return sentBitmap;

            int w = bitmap.getWidth();
            int h = bitmap.getHeight();

            int[] pix = new int[w * h];
            bitmap.getPixels(pix, 0, w, 0, 0, w, h);

            int wm = w - 1;
            int hm = h - 1;
            int wh = w * h;
            int div = radius + radius + 1;

            int[] r = new int[wh];
            int[] g = new int[wh];
            int[] b = new int[wh];
            int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
            int[] vmin = new int[Math.max(w, h)];

            int divsum = (div + 1) >> 1;
            divsum *= divsum;
            int[] dv = new int[256 * divsum];
            for (i = 0; i < 256 * divsum; i++) {
                dv[i] = (i / divsum);
            }

            yw = yi = 0;

            int[][] stack = new int[div][3];
            int stackpointer;
            int stackstart;
            int[] sir;
            int rbs;
            int r1 = radius + 1;
            int routsum, goutsum, boutsum;
            int rinsum, ginsum, binsum;

            for (y = 0; y < h; y++) {
                rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
                for (i = -radius; i <= radius; i++) {
                    p = pix[yi + Math.min(wm, Math.max(i, 0))];
                    sir = stack[i + radius];
                    sir[0] = (p & 0xff0000) >> 16;
                    sir[1] = (p & 0x00ff00) >> 8;
                    sir[2] = (p & 0x0000ff);
                    rbs = r1 - Math.abs(i);
                    rsum += sir[0] * rbs;
                    gsum += sir[1] * rbs;
                    bsum += sir[2] * rbs;
                    if (i > 0) {
                        rinsum += sir[0];
                        ginsum += sir[1];
                        binsum += sir[2];
                    } else {
                        routsum += sir[0];
                        goutsum += sir[1];
                        boutsum += sir[2];
                    }
                }
                stackpointer = radius;

                for (x = 0; x < w; x++) {
                    r[yi] = dv[rsum];
                    g[yi] = dv[gsum];
                    b[yi] = dv[bsum];

                    rsum -= routsum;
                    gsum -= goutsum;
                    bsum -= boutsum;

                    stackstart = stackpointer - radius + div;
                    sir = stack[stackstart % div];

                    routsum -= sir[0];
                    goutsum -= sir[1];
                    boutsum -= sir[2];

                    if (y == 0) {
                        vmin[x] = Math.min(x + radius + 1, wm);
                    }
                    p = pix[yw + vmin[x]];

                    sir[0] = (p & 0xff0000) >> 16;
                    sir[1] = (p & 0x00ff00) >> 8;
                    sir[2] = (p & 0x0000ff);

                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];

                    rsum += rinsum;
                    gsum += ginsum;
                    bsum += binsum;

                    stackpointer = (stackpointer + 1) % div;
                    sir = stack[(stackpointer) % div];

                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];

                    rinsum -= sir[0];
                    ginsum -= sir[1];
                    binsum -= sir[2];

                    yi++;
                }
                yw += w;
            }
            for (x = 0; x < w; x++) {
                rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
                yp = -radius * w;
                for (i = -radius; i <= radius; i++) {
                    yi = Math.max(0, yp) + x;

                    sir = stack[i + radius];

                    sir[0] = r[yi];
                    sir[1] = g[yi];
                    sir[2] = b[yi];

                    rbs = r1 - Math.abs(i);

                    rsum += r[yi] * rbs;
                    gsum += g[yi] * rbs;
                    bsum += b[yi] * rbs;

                    if (i > 0) {
                        rinsum += sir[0];
                        ginsum += sir[1];
                        binsum += sir[2];
                    } else {
                        routsum += sir[0];
                        goutsum += sir[1];
                        boutsum += sir[2];
                    }

                    if (i < hm) {
                        yp += w;
                    }
                }
                yi = x;
                stackpointer = radius;
                for (y = 0; y < h; y++) {
                    // Preserve full alpha
                    pix[yi] = (0xff000000) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                    rsum -= routsum;
                    gsum -= goutsum;
                    bsum -= boutsum;

                    stackstart = stackpointer - radius + div;
                    sir = stack[stackstart % div];

                    routsum -= sir[0];
                    goutsum -= sir[1];
                    boutsum -= sir[2];

                    if (x == 0) {
                        vmin[y] = Math.min(y + r1, hm) * w;
                    }
                    p = x + vmin[y];

                    sir[0] = r[p];
                    sir[1] = g[p];
                    sir[2] = b[p];

                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];

                    rsum += rinsum;
                    gsum += ginsum;
                    bsum += binsum;

                    stackpointer = (stackpointer + 1) % div;
                    sir = stack[stackpointer % div];

                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];

                    rinsum -= sir[0];
                    ginsum -= sir[1];
                    binsum -= sir[2];

                    yi += w;
                }
            }

            bitmap.setPixels(pix, 0, w, 0, 0, w, h);
            return bitmap;
        } catch (Throwable t) {
            Log.e(TAG, "Fast blur failed", t);
            return sentBitmap;
        }
    }
}
