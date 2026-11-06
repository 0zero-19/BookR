package com.bookr.reader.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class MangaPageView extends AppCompatImageView {

    public interface OnTapZoneListener {
        void onLeftTap();
        void onCenterTap();
        void onRightTap();
    }

    private final Matrix baseMatrix = new Matrix();
    private final Matrix suppMatrix = new Matrix();
    private final Matrix drawMatrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OnTapZoneListener tapZoneListener;

    private float minScale = 1.0f;
    private float maxScale = 4.0f;

    public MangaPageView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public MangaPageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MangaPageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                float currentScale = getCurrentScale();
                if ((currentScale < maxScale && scaleFactor > 1f) || (currentScale > minScale && scaleFactor < 1f)) {
                    if (currentScale * scaleFactor < minScale) {
                        scaleFactor = minScale / currentScale;
                    }
                    if (currentScale * scaleFactor > maxScale) {
                        scaleFactor = maxScale / currentScale;
                    }
                    suppMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                    checkAndDisplayMatrix();
                }
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float current = getCurrentScale();
                if (current > 1.15f) {
                    suppMatrix.reset();
                } else {
                    suppMatrix.postScale(2.2f, 2.2f, e.getX(), e.getY());
                }
                checkAndDisplayMatrix();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (getCurrentScale() > 1.05f) {
                    suppMatrix.postTranslate(-distanceX, -distanceY);
                    checkAndDisplayMatrix();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (tapZoneListener != null) {
                    float w = getWidth();
                    float x = e.getX();
                    if (x < w * 0.32f) {
                        tapZoneListener.onLeftTap();
                    } else if (x > w * 0.68f) {
                        tapZoneListener.onRightTap();
                    } else {
                        tapZoneListener.onCenterTap();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    public void setOnTapZoneListener(OnTapZoneListener listener) {
        this.tapZoneListener = listener;
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        suppMatrix.reset();
        updateBaseMatrix();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateBaseMatrix();
    }

    private void updateBaseMatrix() {
        if (getDrawable() == null) return;
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        int drawableWidth = getDrawable().getIntrinsicWidth();
        int drawableHeight = getDrawable().getIntrinsicHeight();
        if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) return;

        baseMatrix.reset();
        float widthScale = viewWidth / drawableWidth;
        float heightScale = viewHeight / drawableHeight;
        float scale = Math.min(widthScale, heightScale);

        baseMatrix.postScale(scale, scale);
        baseMatrix.postTranslate((viewWidth - drawableWidth * scale) / 2f,
                (viewHeight - drawableHeight * scale) / 2f);
        checkAndDisplayMatrix();
    }

    private void checkAndDisplayMatrix() {
        drawMatrix.set(baseMatrix);
        drawMatrix.postConcat(suppMatrix);

        if (getDrawable() != null) {
            RectF rect = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
            drawMatrix.mapRect(rect);

            float height = rect.height();
            float width = rect.width();
            float deltaX = 0, deltaY = 0;
            int viewHeight = getHeight();
            int viewWidth = getWidth();

            if (height <= viewHeight) {
                deltaY = (viewHeight - height) / 2f - rect.top;
            } else if (rect.top > 0) {
                deltaY = -rect.top;
            } else if (rect.bottom < viewHeight) {
                deltaY = viewHeight - rect.bottom;
            }

            if (width <= viewWidth) {
                deltaX = (viewWidth - width) / 2f - rect.left;
            } else if (rect.left > 0) {
                deltaX = -rect.left;
            } else if (rect.right < viewWidth) {
                deltaX = viewWidth - rect.right;
            }

            suppMatrix.postTranslate(deltaX, deltaY);
            drawMatrix.set(baseMatrix);
            drawMatrix.postConcat(suppMatrix);
        }

        setImageMatrix(drawMatrix);
    }

    private float getCurrentScale() {
        suppMatrix.getValues(matrixValues);
        return (float) Math.sqrt(Math.pow(matrixValues[Matrix.MSCALE_X], 2)
                + Math.pow(matrixValues[Matrix.MSKEW_Y], 2));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }
}

