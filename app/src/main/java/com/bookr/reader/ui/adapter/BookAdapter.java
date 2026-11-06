package com.bookr.reader.ui.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.model.BookItem;
import com.bookr.reader.util.FileUtils;
import com.bookr.reader.util.ImageHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {

    public interface OnBookClickListener {
        void onBookClick(BookItem book);
        void onBookOptionsClick(BookItem book);
    }

    private final Context context;
    private final List<BookItem> books = new ArrayList<>();
    private final OnBookClickListener listener;
    private final LruCache<String, Bitmap> coverCache = new LruCache<String, Bitmap>(16 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    public BookAdapter(Context context, OnBookClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setBooks(List<BookItem> newBooks) {
        this.books.clear();
        if (newBooks != null) {
            this.books.addAll(newBooks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_book, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        BookItem book = books.get(position);
        holder.tvTitle.setText(book.getTitle());
        String authorLine = book.getAuthor();
        if (book.getSeries() != null && !book.getSeries().trim().isEmpty()) {
            String seriesStr = book.getSeries().trim();
            if (book.getSeriesNumber() > 0) {
                seriesStr += " #" + book.getSeriesNumber();
            }
            authorLine = authorLine + " • " + seriesStr;
        }
        holder.tvAuthor.setText(authorLine);

        // Progress badge
        if (book.getProgressPercent() > 0) {
            holder.tvProgressBadge.setVisibility(View.VISIBLE);
            holder.tvProgressBadge.setText(String.format(Locale.getDefault(), "%.0f%%", book.getProgressPercent()));
        } else {
            holder.tvProgressBadge.setVisibility(View.GONE);
        }

        // Last read or date added
        if (book.getLastOpened() > 0) {
            holder.tvLastRead.setText(FileUtils.formatDateTime(book.getLastOpened()));
        } else {
            holder.tvLastRead.setText(FileUtils.formatFileSize(book.getFileSize()));
        }

        // Cover
        String coverPath = book.getCoverPath();
        if (coverPath != null && new File(coverPath).exists()) {
            try {
                Bitmap bitmap = coverCache.get(coverPath);
                if (bitmap == null || bitmap.isRecycled()) {
                    bitmap = ImageHelper.loadSampledBitmap(coverPath, 160, 220);
                    if (bitmap != null) {
                        coverCache.put(coverPath, bitmap);
                    }
                }
                if (bitmap != null) {
                    holder.ivCover.setImageBitmap(bitmap);
                    holder.ivCover.setVisibility(View.VISIBLE);
                    holder.ivPlaceholder.setVisibility(View.GONE);
                } else {
                    holder.ivCover.setVisibility(View.GONE);
                    holder.ivPlaceholder.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                holder.ivCover.setVisibility(View.GONE);
                holder.ivPlaceholder.setVisibility(View.VISIBLE);
            }
        } else {
            holder.ivCover.setVisibility(View.GONE);
            holder.ivPlaceholder.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBookClick(book);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onBookOptionsClick(book);
            return true;
        });

        holder.btnOptions.setOnClickListener(v -> {
            if (listener != null) listener.onBookOptionsClick(book);
        });
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    static class BookViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        ImageView ivPlaceholder;
        TextView tvTitle;
        TextView tvAuthor;
        TextView tvProgressBadge;
        TextView tvLastRead;
        FrameLayout btnOptions;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivCover);
            ivPlaceholder = itemView.findViewById(R.id.ivCoverPlaceholder);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvAuthor = itemView.findViewById(R.id.tvAuthor);
            tvProgressBadge = itemView.findViewById(R.id.tvProgressBadge);
            tvLastRead = itemView.findViewById(R.id.tvLastRead);
            btnOptions = itemView.findViewById(R.id.btnOptions);
        }
    }
}
