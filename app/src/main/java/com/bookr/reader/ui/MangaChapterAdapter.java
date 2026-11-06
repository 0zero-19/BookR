package com.bookr.reader.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.model.MangaChapterItem;

import java.util.ArrayList;
import java.util.List;

public class MangaChapterAdapter extends RecyclerView.Adapter<MangaChapterAdapter.ViewHolder> {

    public interface OnChapterActionListener {
        void onReadChapter(MangaChapterItem chapter, int position);
        void onDownloadChapter(MangaChapterItem chapter, int position);
    }

    private List<MangaChapterItem> items = new ArrayList<>();
    private final OnChapterActionListener listener;

    public MangaChapterAdapter(OnChapterActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<MangaChapterItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<MangaChapterItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manga_chapter, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MangaChapterItem ch = items.get(position);
        holder.tvTitle.setText(ch.getDisplayTitle());

        StringBuilder meta = new StringBuilder();
        meta.append(ch.getLanguage());
        if (ch.getPagesCount() > 0) {
            meta.append(" • ").append(ch.getPagesCount()).append(" стр.");
        }
        if (ch.isCompleted()) {
            meta.append(" • Прочитано ✓");
        } else if (ch.getReadPage() > 0) {
            meta.append(" • Остановились на стр. ").append(ch.getReadPage() + 1);
        }
        holder.tvMeta.setText(meta.toString());

        if (ch.isDownloaded()) {
            holder.btnDownload.setText("Офлайн ✓");
            holder.btnDownload.setTextColor(holder.itemView.getContext().getColor(R.color.white));
        } else {
            holder.btnDownload.setText("Скачать");
            holder.btnDownload.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
        }

        View.OnClickListener readClick = v -> {
            if (listener != null) listener.onReadChapter(ch, holder.getAdapterPosition());
        };
        holder.itemView.setOnClickListener(readClick);
        holder.btnRead.setOnClickListener(readClick);

        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) listener.onDownloadChapter(ch, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvMeta;
        final TextView btnRead;
        final TextView btnDownload;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvChapterTitle);
            tvMeta = itemView.findViewById(R.id.tvChapterMeta);
            btnRead = itemView.findViewById(R.id.btnReadChapter);
            btnDownload = itemView.findViewById(R.id.btnDownloadChapter);
        }
    }
}

