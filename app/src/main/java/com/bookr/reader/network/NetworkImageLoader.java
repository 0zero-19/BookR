package com.bookr.reader.network;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkImageLoader {
    private static NetworkImageLoader instance;
    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final File diskCacheDir;

    public interface BitmapCallback {
        void onLoaded(Bitmap bitmap);
    }

    public static synchronized NetworkImageLoader getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkImageLoader(context.getApplicationContext());
        }
        return instance;
    }

    private NetworkImageLoader(Context context) {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = Math.max(4 * 1024, maxMemory / 6);
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
        executor = Executors.newFixedThreadPool(4);
        mainHandler = new Handler(Looper.getMainLooper());
        diskCacheDir = new File(context.getCacheDir(), "net_images");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
    }

    public void loadImage(final String url, final ImageView target, final String fallbackTitle, final String fallbackSub) {
        if (target == null) return;
        target.setTag(url);

        if (url == null || url.trim().isEmpty()) {
            Bitmap generated = generateMonochromeCover(fallbackTitle, fallbackSub);
            target.setImageBitmap(generated);
            return;
        }

        Bitmap cached = memoryCache.get(url);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }

        // Show procedural cover immediately while loading
        target.setImageBitmap(generateMonochromeCover(fallbackTitle, fallbackSub));

        executor.execute(() -> {
            Bitmap bmp = loadBitmapSync(url, fallbackTitle, fallbackSub);
            if (bmp != null) {
                memoryCache.put(url, bmp);
                mainHandler.post(() -> {
                    if (url.equals(target.getTag())) {
                        target.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    public void loadMangaPage(final String urlOrPath, final int pageNum, final int totalPages,
                              final String mangaTitle, final String chapterTitle,
                              final BitmapCallback callback) {
        if (urlOrPath == null || urlOrPath.isEmpty()) {
            Bitmap fallback = generateMangaPageBitmap(mangaTitle, chapterTitle, pageNum, totalPages);
            if (callback != null) callback.onLoaded(fallback);
            return;
        }

        Bitmap cached = memoryCache.get(urlOrPath);
        if (cached != null && !cached.isRecycled()) {
            if (callback != null) callback.onLoaded(cached);
            return;
        }

        executor.execute(() -> {
            Bitmap bmp = loadMangaPageSync(urlOrPath, pageNum, totalPages, mangaTitle, chapterTitle);
            if (bmp != null) {
                memoryCache.put(urlOrPath, bmp);
            }
            mainHandler.post(() -> {
                if (callback != null) callback.onLoaded(bmp);
            });
        });
    }

    public Bitmap loadMangaPageSync(String urlOrPath, int pageNum, int totalPages, String mangaTitle, String chapterTitle) {
        try {
            if (urlOrPath.startsWith("builtin://manga_page/")) {
                return generateMangaPageBitmap(mangaTitle, chapterTitle, pageNum, totalPages);
            }

            File localFile = new File(urlOrPath);
            if (localFile.exists() && localFile.isFile()) {
                Bitmap b = decodeSampledFile(localFile, 1400, 2200);
                if (b != null) return b;
            }

            String hash = md5(urlOrPath);
            File cacheFile = new File(diskCacheDir, "mp_" + hash + ".jpg");
            if (cacheFile.exists() && cacheFile.length() > 256) {
                Bitmap b = decodeSampledFile(cacheFile, 1400, 2200);
                if (b != null) return b;
            }

            if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
                if (downloadUrlToFile(urlOrPath, cacheFile)) {
                    Bitmap b = decodeSampledFile(cacheFile, 1400, 2200);
                    if (b != null) return b;
                }
            }
        } catch (Exception ignored) {
        }
        return generateMangaPageBitmap(mangaTitle, chapterTitle, pageNum, totalPages);
    }

    public Bitmap loadBitmapSync(String url, String fallbackTitle, String fallbackSub) {
        try {
            if (url.startsWith("builtin://cover/")) {
                return generateMonochromeCover(fallbackTitle, fallbackSub);
            }
            File directFile = new File(url);
            if (directFile.exists() && directFile.isFile()) {
                Bitmap b = decodeSampledFile(directFile, 500, 750);
                if (b != null) return b;
            }

            String hash = md5(url);
            File cacheFile = new File(diskCacheDir, "cov_" + hash + ".jpg");
            if (cacheFile.exists() && cacheFile.length() > 128) {
                Bitmap b = decodeSampledFile(cacheFile, 500, 750);
                if (b != null) return b;
            }

            if (url.startsWith("http://") || url.startsWith("https://")) {
                if (downloadUrlToFile(url, cacheFile)) {
                    Bitmap b = decodeSampledFile(cacheFile, 500, 750);
                    if (b != null) return b;
                }
            }
        } catch (Exception ignored) {
        }
        return generateMonochromeCover(fallbackTitle, fallbackSub);
    }

    public static boolean downloadUrlToFile(String urlStr, File destFile) {
        boolean success = downloadUrlToFileInternal(urlStr, destFile);
        if (!success && urlStr != null) {
            if (urlStr.contains("img3.cdnlibs.org")) {
                String fallback = urlStr.replace("img3.cdnlibs.org", "img2.imglib.info");
                success = downloadUrlToFileInternal(fallback, destFile);
            } else if (urlStr.contains("img2.imglib.info")) {
                String fallback = urlStr.replace("img2.imglib.info", "img3.cdnlibs.org");
                success = downloadUrlToFileInternal(fallback, destFile);
            }
        }
        return success;
    }

    private static boolean downloadUrlToFileInternal(String urlStr, File destFile) {
        int redirects = 0;
        String currentUrl = urlStr;
        while (redirects < 5) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(currentUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
                conn.setRequestProperty("Referer", "https://mangalib.me/");
                conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM
                        || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == HttpURLConnection.HTTP_SEE_OTHER
                        || code == 307 || code == 308) {
                    String loc = conn.getHeaderField("Location");
                    if (loc != null && !loc.isEmpty()) {
                        if (!loc.startsWith("http://") && !loc.startsWith("https://")) {
                            loc = new URL(url, loc).toString();
                        }
                        currentUrl = loc;
                        redirects++;
                        continue;
                    }
                }
                if (code >= 200 && code < 300) {
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(destFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                        }
                        out.flush();
                    }
                    return destFile.exists() && destFile.length() > 0;
                }
                return false;
            } catch (Exception e) {
                return false;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return false;
    }

    public static Bitmap generateMonochromeCover(String title, String author) {
        int width = 360;
        int height = 520;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        String safeTitle = title != null && !title.isEmpty() ? title : "BookR";
        String safeAuthor = author != null && !author.isEmpty() ? author : "";
        int seed = Math.abs(safeTitle.hashCode());

        int bgTop = Color.rgb(22 + (seed % 18), 22 + (seed % 18), 25 + (seed % 18));
        int bgBottom = Color.rgb(10, 10, 12);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setShader(new LinearGradient(0, 0, width, height, bgTop, bgBottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, bgPaint);

        // Subtle geometric lines
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(Color.argb(35, 255, 255, 255));
        linePaint.setStrokeWidth(1.5f);
        canvas.drawRect(18, 18, width - 18, height - 18, linePaint);
        canvas.drawRect(24, 24, width - 24, height - 24, linePaint);

        // Decorative emblem circle
        linePaint.setColor(Color.argb(24, 255, 255, 255));
        float cx = width / 2f;
        float cy = height * 0.34f;
        canvas.drawCircle(cx, cy, 62f + (seed % 20), linePaint);
        canvas.drawCircle(cx, cy, 42f, linePaint);

        // Initials inside emblem
        Paint initPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        initPaint.setColor(Color.argb(180, 240, 240, 240));
        initPaint.setTextSize(42f);
        initPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        initPaint.setTextAlign(Paint.Align.CENTER);
        String initial = safeTitle.substring(0, 1).toUpperCase();
        canvas.drawText(initial, cx, cy + 15f, initPaint);

        // Title wrapped
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.rgb(238, 238, 238));
        titlePaint.setTextSize(24f);
        titlePaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        titlePaint.setTextAlign(Paint.Align.CENTER);

        String[] words = safeTitle.split("\\s+");
        StringBuilder line = new StringBuilder();
        float y = height * 0.60f;
        int linesDrawn = 0;
        for (String w : words) {
            if (line.length() + w.length() > 16 && line.length() > 0) {
                canvas.drawText(line.toString().trim(), cx, y, titlePaint);
                y += 30f;
                line.setLength(0);
                linesDrawn++;
                if (linesDrawn >= 3) break;
            }
            line.append(w).append(" ");
        }
        if (line.length() > 0 && linesDrawn < 3) {
            String rem = line.toString().trim();
            if (rem.length() > 18) rem = rem.substring(0, 16) + "…";
            canvas.drawText(rem, cx, y, titlePaint);
        }

        // Author at bottom
        if (!safeAuthor.isEmpty()) {
            Paint authorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            authorPaint.setColor(Color.rgb(160, 160, 165));
            authorPaint.setTextSize(17f);
            authorPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            authorPaint.setTextAlign(Paint.Align.CENTER);
            String dispAuthor = safeAuthor.length() > 24 ? safeAuthor.substring(0, 22) + "…" : safeAuthor;
            canvas.drawText(dispAuthor, cx, height - 42f, authorPaint);
        }

        return bmp;
    }

    public static Bitmap generateMangaPageBitmap(String mangaTitle, String chapterTitle, int pageNum, int totalPages) {
        int width = 900;
        int height = 1350;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // Minimalist deep dark canvas
        canvas.drawColor(Color.rgb(13, 13, 17));

        // Subtle gradient underlay
        Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradPaint.setShader(new android.graphics.LinearGradient(
                0, 0, 0, height,
                Color.rgb(18, 18, 24),
                Color.rgb(9, 9, 12),
                android.graphics.Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, width, height, gradPaint);

        // Thin outer glass border
        Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setColor(Color.argb(32, 255, 255, 255));
        framePaint.setStrokeWidth(2f);
        RectF outerFrame = new RectF(40, 40, width - 40, height - 40);
        canvas.drawRoundRect(outerFrame, 28f, 28f, framePaint);

        // Corner accents
        framePaint.setColor(Color.argb(80, 255, 255, 255));
        framePaint.setStrokeWidth(3f);
        float accLen = 32f;
        canvas.drawLine(40, 40 + accLen, 40, 40, framePaint);
        canvas.drawLine(40, 40, 40 + accLen, 40, framePaint);
        canvas.drawLine(width - 40 - accLen, 40, width - 40, 40, framePaint);
        canvas.drawLine(width - 40, 40, width - 40, 40 + accLen, framePaint);
        canvas.drawLine(40, height - 40 - accLen, 40, height - 40, framePaint);
        canvas.drawLine(40, height - 40, 40 + accLen, height - 40, framePaint);
        canvas.drawLine(width - 40 - accLen, height - 40, width - 40, height - 40, framePaint);
        canvas.drawLine(width - 40, height - 40 - accLen, width - 40, height - 40, framePaint);

        // Center Minimalist Glass Card
        float cardLeft = 90f;
        float cardRight = width - 90f;
        float cardTop = height * 0.26f;
        float cardBottom = height * 0.74f;
        RectF glassCard = new RectF(cardLeft, cardTop, cardRight, cardBottom);

        Paint glassFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassFill.setStyle(Paint.Style.FILL);
        glassFill.setColor(Color.argb(16, 255, 255, 255));
        canvas.drawRoundRect(glassCard, 32f, 32f, glassFill);

        Paint glassBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassBorder.setStyle(Paint.Style.STROKE);
        glassBorder.setStrokeWidth(1.5f);
        glassBorder.setColor(Color.argb(40, 255, 255, 255));
        canvas.drawRoundRect(glassCard, 32f, 32f, glassBorder);

        float cx = width / 2f;
        float ringCy = cardTop + 140f;

        // Elegant loading ring
        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3.5f);
        ringPaint.setColor(Color.argb(45, 255, 255, 255));
        canvas.drawCircle(cx, ringCy, 50f, ringPaint);

        ringPaint.setColor(Color.WHITE);
        ringPaint.setStrokeWidth(4f);
        RectF ringRect = new RectF(cx - 50f, ringCy - 50f, cx + 50f, ringCy + 50f);
        int angleOffset = (pageNum * 47) % 360;
        canvas.drawArc(ringRect, angleOffset, 110f, false, ringPaint);

        // Center dot
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.argb(160, 255, 255, 255));
        canvas.drawCircle(cx, ringCy, 6f, dotPaint);

        // Delicate page indicator
        Paint pagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pagePaint.setColor(Color.argb(220, 255, 255, 255));
        pagePaint.setTextSize(20f);
        pagePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        pagePaint.setTextAlign(Paint.Align.CENTER);
        int total = Math.max(pageNum, totalPages);
        canvas.drawText("С Т Р А Н И Ц А   " + pageNum + "   /   " + total, cx, ringCy + 96f, pagePaint);

        // Manga Title
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(28f);
        titlePaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        titlePaint.setTextAlign(Paint.Align.CENTER);
        String safeManga = (mangaTitle != null && !mangaTitle.isEmpty()) ? mangaTitle : "Манга";
        if (safeManga.length() > 32) safeManga = safeManga.substring(0, 30) + "…";
        canvas.drawText(safeManga, cx, ringCy + 154f, titlePaint);

        // Chapter title
        Paint chapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        chapPaint.setColor(Color.argb(180, 160, 160, 168));
        chapPaint.setTextSize(20f);
        chapPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        chapPaint.setTextAlign(Paint.Align.CENTER);
        String safeChap = (chapterTitle != null && !chapterTitle.isEmpty()) ? chapterTitle : "Глава 1";
        if (safeChap.length() > 36) safeChap = safeChap.substring(0, 34) + "…";
        canvas.drawText(safeChap, cx, ringCy + 196f, chapPaint);

        // Progress line track
        float progY = ringCy + 250f;
        float progWidth = (cardRight - cardLeft) * 0.65f;
        float progStartX = cx - progWidth / 2f;
        float progEndX = cx + progWidth / 2f;

        Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(3f);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.argb(35, 255, 255, 255));
        canvas.drawLine(progStartX, progY, progEndX, progY, trackPaint);

        // Progress line active
        float fraction = total > 0 ? ((float) pageNum / (float) total) : 0f;
        float activeEndX = progStartX + (progWidth * Math.min(1f, Math.max(0.08f, fraction)));
        Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setStyle(Paint.Style.STROKE);
        activePaint.setStrokeWidth(3.5f);
        activePaint.setStrokeCap(Paint.Cap.ROUND);
        activePaint.setColor(Color.WHITE);
        canvas.drawLine(progStartX, progY, activeEndX, progY, activePaint);

        // Loading subtitle
        Paint loadSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        loadSubPaint.setColor(Color.argb(130, 255, 255, 255));
        loadSubPaint.setTextSize(15f);
        loadSubPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        loadSubPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("З А Г Р У З К А   С Т Р А Н И Ц Ы . . .", cx, progY + 44f, loadSubPaint);

        // Minimalist footer branding
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(Color.argb(80, 255, 255, 255));
        footerPaint.setTextSize(14f);
        footerPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        footerPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("BOOKR • МИНИМАЛИЗМ • МОНОХРОМ", cx, height - 72f, footerPaint);

        return bmp;
    }

    private static void drawMultilineText(Canvas canvas, String text, float cx, float startY, Paint paint, float lineHeight) {
        String[] lines = text.split("\n");
        float y = startY;
        for (String line : lines) {
            canvas.drawText(line, cx, y, paint);
            y += lineHeight;
        }
    }

    private static Bitmap decodeSampledFile(File file, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception e) {
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(Math.abs(input.hashCode()));
        }
    }

    public long getCacheSizeBytes() {
        long total = 0;
        if (diskCacheDir != null && diskCacheDir.exists()) {
            File[] files = diskCacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) total += f.length();
                }
            }
        }
        return total;
    }

    public void clearCache() {
        memoryCache.evictAll();
        if (diskCacheDir != null && diskCacheDir.exists()) {
            File[] files = diskCacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) f.delete();
                }
            }
        }
    }
}

