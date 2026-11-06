package com.bookr.reader.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.model.BookmarkItem;
import com.bookr.reader.util.FileUtils;

import java.util.ArrayList;
import java.util.List;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder> {

    public interface OnBookmarkClickListener {
        void onBookmarkClick(BookmarkItem item);
        void onBookmarkDelete(BookmarkItem item);
    }

    private final Context context;
    private final List<BookmarkItem> items = new ArrayList<>();
    private final OnBookmarkClickListener listener;

    public BookmarkAdapter(Context context, List<BookmarkItem> items, OnBookmarkClickListener listener) {
        this.context = context;
        if (items != null) {
            this.items.addAll(items);
        }
        this.listener = listener;
    }

    public void setItems(List<BookmarkItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_bookmark, parent, false);
        return new BookmarkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        BookmarkItem item = items.get(position);
        String header = item.getChapterTitle();
        if (item.getBookTitle() != null && !item.getBookTitle().trim().isEmpty()
                && !"Без названия".equals(item.getBookTitle().trim())) {
            header = item.getBookTitle().trim() + " • " + header;
        }
        holder.tvChapter.setText(header);

        StringBuilder snippetBuilder = new StringBuilder();
        if (item.getSnippet() != null && !item.getSnippet().trim().isEmpty()) {
            snippetBuilder.append(item.getSnippet().trim());
        }
        if (item.getComment() != null && !item.getComment().trim().isEmpty()) {
            if (snippetBuilder.length() > 0) snippetBuilder.append("\n");
            snippetBuilder.append("💬 ").append(item.getComment().trim());
        }
        if (snippetBuilder.length() > 0) {
            holder.tvSnippet.setText(snippetBuilder.toString());
            holder.tvSnippet.setVisibility(View.VISIBLE);
        } else {
            holder.tvSnippet.setVisibility(View.GONE);
        }

        String dateLine = FileUtils.formatDateTime(item.getCreatedAt());
        float pct = item.getProgressPercent();
        if (pct > 0f) {
            dateLine = String.format(java.util.Locale.getDefault(), "%.1f%% • %s", pct, dateLine);
        }
        holder.tvDate.setText(dateLine);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBookmarkClick(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onBookmarkDelete(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class BookmarkViewHolder extends RecyclerView.ViewHolder {
        TextView tvChapter;
        TextView tvSnippet;
        TextView tvDate;
        FrameLayout btnDelete;

        public BookmarkViewHolder(@NonNull View itemView) {
            super(itemView);
            tvChapter = itemView.findViewById(R.id.tvBmChapter);
            tvSnippet = itemView.findViewById(R.id.tvBmSnippet);
            tvDate = itemView.findViewById(R.id.tvBmDate);
            btnDelete = itemView.findViewById(R.id.btnDeleteBm);
        }
    }
}
