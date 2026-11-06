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
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "BookR/2.0 (Android Monochrome Reader)");
            int code = conn.getResponseCode();
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
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
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

        // Crisp paper background
        canvas.drawColor(Color.rgb(246, 246, 244));

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.rgb(20, 20, 20));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6f);

        Paint fillDark = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillDark.setColor(Color.rgb(28, 28, 32));
        fillDark.setStyle(Paint.Style.FILL);

        Paint fillTone = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillTone.setColor(Color.rgb(215, 215, 212));
        fillTone.setStyle(Paint.Style.FILL);

        // Header strip
        Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setColor(Color.rgb(90, 90, 90));
        headerPaint.setTextSize(20f);
        headerPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        String safeManga = mangaTitle != null ? mangaTitle : "MANGA";
        String safeChap = chapterTitle != null ? chapterTitle : "Глава 1";
        canvas.drawText(safeManga + "  •  " + safeChap, 42f, 36f, headerPaint);

        // Panel 1 (Top Wide Action/Establishing Panel)
        RectF panel1 = new RectF(40, 52, width - 40, 440);
        canvas.drawRect(panel1, fillTone);
        canvas.drawRect(panel1, borderPaint);

        // Speed lines in Panel 1
        Paint speedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        speedPaint.setColor(Color.argb(70, 20, 20, 20));
        speedPaint.setStrokeWidth(2f);
        int seed = Math.abs((safeManga + pageNum).hashCode());
        for (int i = 0; i < 36; i++) {
            float sx = 40 + ((i * 29 + seed) % (width - 80));
            canvas.drawLine(sx, 52, width / 2f, 260, speedPaint);
        }

        // Dramatic silhouette in Panel 1
        canvas.drawCircle(width * 0.35f, 230f, 75f, fillDark);
        Path cloak = new Path();
        cloak.moveTo(width * 0.22f, 440f);
        cloak.lineTo(width * 0.35f, 280f);
        cloak.lineTo(width * 0.50f, 440f);
        cloak.close();
        canvas.drawPath(cloak, fillDark);

        // Speech Bubble in Panel 1
        RectF bubble1 = new RectF(width * 0.52f, 88f, width - 68f, 260f);
        Paint bubbleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        bubbleFill.setColor(Color.WHITE);
        canvas.drawRoundRect(bubble1, 48f, 48f, bubbleFill);
        borderPaint.setStrokeWidth(3.5f);
        canvas.drawRoundRect(bubble1, 48f, 48f, borderPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.rgb(18, 18, 18));
        textPaint.setTextSize(23f);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        String[] dialogues1 = new String[]{
                "Мы наконец дошли\nдо врат старого\nгорода...",
                "Ты чувствуешь этот\nстранный резонанс\nв воздухе?!",
                "Не отступать!\nЭтот бой решит\nисход всей главы!",
                "Тень движется быстрее,\nчем мы предполагали..."
        };
        drawMultilineText(canvas, dialogues1[pageNum % dialogues1.length], bubble1.centerX(), bubble1.top + 56f, textPaint, 30f);

        // Panel 2 (Middle Left) & Panel 3 (Middle Right)
        borderPaint.setStrokeWidth(6f);
        RectF panel2 = new RectF(40, 464, width * 0.48f, 880);
        RectF panel3 = new RectF(width * 0.50f, 464, width - 40, 880);
        canvas.drawRect(panel2, fillDark);
        canvas.drawRect(panel2, borderPaint);
        canvas.drawRect(panel3, bubbleFill);
        canvas.drawRect(panel3, borderPaint);

        // Contrast eyes/glow in dark Panel 2
        Paint whiteInk = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteInk.setColor(Color.WHITE);
        canvas.drawCircle(panel2.centerX() - 35f, panel2.centerY() - 20f, 12f, whiteInk);
        canvas.drawCircle(panel2.centerX() + 35f, panel2.centerY() - 20f, 12f, whiteInk);
        whiteInk.setTextSize(34f);
        whiteInk.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        whiteInk.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("ВЖУХ!!", panel2.centerX(), panel2.bottom - 50f, whiteInk);

        // Panel 3 geometry & dialogue
        for (int r = 20; r < 160; r += 25) {
            speedPaint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(panel3.centerX(), panel3.centerY() + 40f, r, speedPaint);
        }
        RectF bubble2 = new RectF(panel3.left + 24f, panel3.top + 24f, panel3.right - 24f, panel3.top + 170f);
        canvas.drawRoundRect(bubble2, 36f, 36f, bubbleFill);
        borderPaint.setStrokeWidth(3f);
        canvas.drawRoundRect(bubble2, 36f, 36f, borderPaint);
        String[] dialogues2 = new String[]{
                "Стр. " + pageNum + ": Секретная\nтехника активирована!",
                "Невероятно!\nУровень силы растёт!",
                "Нам нужно открыть\nдревний свиток.",
                "Смотри внимательно\nна знаки!"
        };
        drawMultilineText(canvas, dialogues2[(pageNum + 1) % dialogues2.length], bubble2.centerX(), bubble2.top + 48f, textPaint, 28f);

        // Panel 4 (Bottom Wide Cinematic Panel)
        borderPaint.setStrokeWidth(6f);
        RectF panel4 = new RectF(40, 904, width - 40, height - 64);
        canvas.drawRect(panel4, fillTone);
        canvas.drawRect(panel4, borderPaint);

        // Horizon & cityscape in Panel 4
        canvas.drawRect(40, 1140, width - 40, height - 64, fillDark);
        for (int b = 60; b < width - 80; b += 75) {
            int bh = 980 + ((b * 17 + pageNum * 31) % 150);
            canvas.drawRect(b, bh, b + 52, 1140, fillDark);
        }

        RectF captionBox = new RectF(68, 932, 480, 1045);
        canvas.drawRect(captionBox, bubbleFill);
        borderPaint.setStrokeWidth(3f);
        canvas.drawRect(captionBox, borderPaint);
        drawMultilineText(canvas, safeChap + " • Эпизод " + pageNum + "\nПродолжение на след. странице →",
                captionBox.centerX(), captionBox.top + 45f, textPaint, 28f);

        // Footer Page Counter
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(Color.rgb(60, 60, 60));
        footerPaint.setTextSize(22f);
        footerPaint.setTextAlign(Paint.Align.CENTER);
        footerPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        canvas.drawText("— " + pageNum + " / " + Math.max(pageNum, totalPages) + " —", width / 2f, height - 22f, footerPaint);

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

