package com.bookr.reader.ui.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.PageViewHolder> {
    private static final String TAG = "BookR.PdfAdapter";

    public interface OnPageClickListener {
        void onPageClick(int pageIndex);
    }

    private final Context context;
    private final ParcelFileDescriptor pfd;
    private PdfRenderer pdfRenderer;
    private final int pageCount;
    private final OnPageClickListener listener;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final LruCache<Integer, Bitmap> memoryCache;

    public PdfPageAdapter(Context context, ParcelFileDescriptor pfd, OnPageClickListener listener) {
        this.context = context;
        this.pfd = pfd;
        this.listener = listener;

        int count = 0;
        try {
            this.pdfRenderer = new PdfRenderer(pfd);
            count = pdfRenderer.getPageCount();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing PdfRenderer", e);
        }
        this.pageCount = count;

        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        this.memoryCache = new LruCache<Integer, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(Integer key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pdf_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.tvPageNumber.setText(String.format(Locale.getDefault(), "Стр. %d из %d", position + 1, pageCount));
        holder.position = position;

        Bitmap cached = memoryCache.get(position);
        if (cached != null && !cached.isRecycled()) {
            holder.ivPage.setImageBitmap(cached);
        } else {
            holder.ivPage.setImageBitmap(null);
            holder.ivPage.setBackgroundColor(Color.parseColor("#1C1C1E"));

            final int pageIdx = position;
            executor.execute(() -> {
                Bitmap bmp = renderPageBitmap(pageIdx);
                if (bmp != null) {
                    memoryCache.put(pageIdx, bmp);
                    mainHandler.post(() -> {
                        if (holder.position == pageIdx) {
                            holder.ivPage.setImageBitmap(bmp);
                        }
                    });
                }
            });
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPageClick(position);
        });
    }

    private synchronized Bitmap renderPageBitmap(int pageIndex) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pageCount) return null;
        try {
            PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);
            int displayWidth = context.getResources().getDisplayMetrics().widthPixels;
            int targetWidth = Math.min(1440, Math.max(displayWidth, page.getWidth()));
            int targetHeight = (int) (targetWidth * ((float) page.getHeight() / page.getWidth()));

            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error rendering page " + pageIndex, e);
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return pageCount;
    }

    public synchronized void release() {
        executor.shutdownNow();
        memoryCache.evictAll();
        if (pdfRenderer != null) {
            try {
                pdfRenderer.close();
            } catch (Exception ignored) {}
            pdfRenderer = null;
        }
        if (pfd != null) {
            try {
                pfd.close();
            } catch (Exception ignored) {}
        }
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPage;
        TextView tvPageNumber;
        int position;

        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPage = itemView.findViewById(R.id.ivPdfPage);
            tvPageNumber = itemView.findViewById(R.id.tvPdfPageNumber);
        }
    }
}
