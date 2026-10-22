package com.bookr.reader.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.bookr.reader.data.model.ThemeConfig;
import com.bookr.reader.util.ImageHelper;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThemeBackgroundView extends View {
    private static final String TAG = "BookR.ThemeBgView";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint();
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    private ThemeConfig currentConfig;
    private boolean isReaderScreen = false;

    private Bitmap processedBitmap;
    private Movie movie;
    private long movieStartTime = 0;
    private int movieDuration = 1000;
    private boolean isPaused = false;
    private boolean isPlaying = false;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String loadedPath = null;

    public ThemeBackgroundView(Context context) {
        super(context);
        init();
    }

    public ThemeBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeBackgroundView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
    }

    public void setConfig(ThemeConfig config, boolean isReader) {
        this.currentConfig = config;
        this.isReaderScreen = isReader;
        reloadMedia();
    }

    public void reloadMedia() {
        if (currentConfig == null) {
            invalidate();
            return;
        }

        int bgType = isReaderScreen ? currentConfig.getReaderBgType() : currentConfig.getAppBgType();
        String path = isReaderScreen ? currentConfig.getReaderBgPath() : currentConfig.getAppBgPath();

        if (bgType == ThemeConfig.BG_COLOR || path == null || !(new File(path).exists())) {
            releaseMedia();
            invalidate();
            return;
        }

        if (bgType == ThemeConfig.BG_GIF && ImageHelper.isGifFile(path)) {
            loadGif(path);
        } else {
            loadStaticBitmap(path);
        }
    }

    private void loadStaticBitmap(String path) {
        movie = null;
        isPlaying = false;
        loadedPath = path;

        int reqW = getWidth() > 0 ? getWidth() : 1080;
        int reqH = getHeight() > 0 ? getHeight() : 1920;

        int blur = isReaderScreen ? currentConfig.getReaderBgBlur() : currentConfig.getAppBgBlur();

        executor.execute(() -> {
            Bitmap raw = ImageHelper.loadSampledBitmap(path, reqW, reqH);
            Bitmap finalBmp = raw;
            if (raw != null && blur > 0) {
                finalBmp = ImageHelper.fastBlur(raw, blur);
            }

            final Bitmap res = finalBmp;
            mainHandler.post(() -> {
                if (processedBitmap != null && processedBitmap != res && !processedBitmap.isRecycled()) {
                    processedBitmap.recycle();
                }
                processedBitmap = res;
                updatePaintFilters();
                invalidate();
            });
        });
    }

    private void loadGif(String path) {
        if (processedBitmap != null && !processedBitmap.isRecycled()) {
            processedBitmap.recycle();
            processedBitmap = null;
        }
        loadedPath = path;

        executor.execute(() -> {
            try {
                Movie m = Movie.decodeFile(path);
                mainHandler.post(() -> {
                    movie = m;
                    if (movie != null) {
                        movieDuration = movie.duration() > 0 ? movie.duration() : 1000;
                        movieStartTime = SystemClock.uptimeMillis();
                        isPlaying = true;
                        updatePaintFilters();
                        invalidate();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode movie from GIF", e);
            }
        });
    }

    private void updatePaintFilters() {
        if (currentConfig == null) return;

        float brightness = isReaderScreen ? currentConfig.getReaderBgBrightness() : currentConfig.getAppBgBrightness();
        float contrast = isReaderScreen ? currentConfig.getReaderBgContrast() : currentConfig.getAppBgContrast();
        float saturation = isReaderScreen ? currentConfig.getReaderBgSaturation() : currentConfig.getAppBgSaturation();
        float dim = isReaderScreen ? currentConfig.getReaderBgDim() : currentConfig.getAppBgDim();
        float opacity = isReaderScreen ? currentConfig.getReaderBgOpacity() : currentConfig.getAppBgOpacity();

        ColorMatrix cm = ImageHelper.createFilterMatrix(brightness, contrast, saturation, dim);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        paint.setAlpha((int) (Math.max(0f, Math.min(1f, opacity)) * 255));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && loadedPath != null && processedBitmap == null && movie == null) {
            reloadMedia();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        // 1. Draw base solid color
        String colorHex = isReaderScreen ?
                (currentConfig != null ? currentConfig.getReaderBgColor() : "#0A0A0A") :
                (currentConfig != null ? currentConfig.getAppBgColor() : "#0A0A0A");
        int baseColor = ThemeConfig.parseColorSafe(colorHex, Color.BLACK);
        canvas.drawColor(baseColor);

        if (currentConfig == null) return;

        int bgType = isReaderScreen ? currentConfig.getReaderBgType() : currentConfig.getAppBgType();
        int scaleMode = isReaderScreen ? currentConfig.getReaderBgScale() : currentConfig.getAppBgScale();
        float offX = isReaderScreen ? currentConfig.getReaderBgOffsetX() : currentConfig.getAppBgOffsetX();
        float offY = isReaderScreen ? currentConfig.getReaderBgOffsetY() : currentConfig.getAppBgOffsetY();
        String tintHex = isReaderScreen ? currentConfig.getReaderBgTintColor() : currentConfig.getAppBgTintColor();

        // 2. Render static image
        if (bgType == ThemeConfig.BG_PHOTO && processedBitmap != null && !processedBitmap.isRecycled()) {
            int bw = processedBitmap.getWidth();
            int bh = processedBitmap.getHeight();
            srcRect.set(0, 0, bw, bh);
            computeDestinationRect(dstRect, viewW, viewH, bw, bh, scaleMode, offX, offY);

            canvas.drawBitmap(processedBitmap, srcRect, dstRect, paint);
        }
        // 3. Render animated GIF via Movie
        else if (bgType == ThemeConfig.BG_GIF && movie != null) {
            int mw = movie.width() > 0 ? movie.width() : viewW;
            int mh = movie.height() > 0 ? movie.height() : viewH;
            computeDestinationRect(dstRect, viewW, viewH, mw, mh, scaleMode, offX, offY);

            long now = SystemClock.uptimeMillis();
            if (movieStartTime == 0) movieStartTime = now;

            float speed = Math.max(0.2f, currentConfig.getGifSpeed());
            boolean batterySaver = currentConfig.isBatterySaver();
            boolean paused = currentConfig.isGifPaused() || isPaused || batterySaver;

            int relTime;
            if (paused) {
                relTime = 0;
            } else {
                relTime = (int) (((now - movieStartTime) * speed) % movieDuration);
            }
            movie.setTime(relTime);

            canvas.save();
            canvas.translate(dstRect.left, dstRect.top);
            float sx = dstRect.width() / (float) mw;
            float sy = dstRect.height() / (float) mh;
            canvas.scale(sx, sy);
            movie.draw(canvas, 0, 0, paint);
            canvas.restore();

            if (!paused && isPlaying) {
                postInvalidateOnAnimation();
            }
        }

        // 4. Draw optional color tint overlay
        int tintColor = ThemeConfig.parseColorSafe(tintHex, Color.TRANSPARENT);
        if (Color.alpha(tintColor) > 0) {
            tintPaint.setColor(tintColor);
            canvas.drawRect(0, 0, viewW, viewH, tintPaint);
        }
    }

    private void computeDestinationRect(RectF out, int viewW, int viewH, int mediaW, int mediaH,
                                        int scaleMode, float offX, float offY) {
        float dx = offX * viewW * 0.5f;
        float dy = offY * viewH * 0.5f;

        switch (scaleMode) {
            case ThemeConfig.SCALE_FIT: {
                float s = Math.min((float) viewW / mediaW, (float) viewH / mediaH);
                float dw = mediaW * s;
                float dh = mediaH * s;
                float l = (viewW - dw) / 2f + dx;
                float t = (viewH - dh) / 2f + dy;
                out.set(l, t, l + dw, t + dh);
                break;
            }
            case ThemeConfig.SCALE_STRETCH: {
                out.set(dx, dy, viewW + dx, viewH + dy);
                break;
            }
            case ThemeConfig.SCALE_ORIGINAL: {
                float l = (viewW - mediaW) / 2f + dx;
                float t = (viewH - mediaH) / 2f + dy;
                out.set(l, t, l + mediaW, t + mediaH);
                break;
            }
            case ThemeConfig.SCALE_FILL:
            default: {
                float s = Math.max((float) viewW / mediaW, (float) viewH / mediaH);
                float dw = mediaW * s;
                float dh = mediaH * s;
                float l = (viewW - dw) / 2f + dx;
                float t = (viewH - dh) / 2f + dy;
                out.set(l, t, l + dw, t + dh);
                break;
            }
        }
    }

    public void pauseAnimation() {
        isPaused = true;
    }

    public void resumeAnimation() {
        isPaused = false;
        if (movie != null && isPlaying) {
            invalidate();
        }
    }

    public void releaseMedia() {
        isPlaying = false;
        movie = null;
        if (processedBitmap != null && !processedBitmap.isRecycled()) {
            processedBitmap.recycle();
            processedBitmap = null;
        }
        loadedPath = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        pauseAnimation();
    }
}
