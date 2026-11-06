package com.bookr.reader.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.model.MangaBookmarkItem;
import com.bookr.reader.network.NetworkImageLoader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MangaBookmarkAdapter extends RecyclerView.Adapter<MangaBookmarkAdapter.ViewHolder> {

    public interface OnMangaBookmarkListener {
        void onOpenBookmark(MangaBookmarkItem item);
        void onDeleteBookmark(MangaBookmarkItem item, int position);
    }

    private List<MangaBookmarkItem> items = new ArrayList<>();
    private final OnMangaBookmarkListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public MangaBookmarkAdapter(OnMangaBookmarkListener listener) {
        this.listener = listener;
    }

    public void setItems(List<MangaBookmarkItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_online_book, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MangaBookmarkItem bm = items.get(position);
        holder.tvTitle.setText(bm.getMangaTitle());
        holder.tvAuthor.setText(bm.getChapterTitle() + " • Страница " + (bm.getPageIndex() + 1));

        String note = bm.getNote();
        if (note == null || note.trim().isEmpty()) {
            note = "Сохранено: " + sdf.format(new Date(bm.getCreatedAt()));
        } else {
            note = "«" + note + "» • " + sdf.format(new Date(bm.getCreatedAt()));
        }
        holder.tvDesc.setText(note);
        holder.tvBadge.setText("ЗАКЛАДКА МАНГИ • СТР. " + (bm.getPageIndex() + 1));

        holder.btnRead.setText("Открыть стр.");
        holder.btnDownload.setText("Удалить");

        NetworkImageLoader.getInstance(holder.itemView.getContext())
                .loadImage(bm.getCoverUrl(), holder.ivCover, bm.getMangaTitle(), bm.getChapterTitle());

        View.OnClickListener openClick = v -> {
            if (listener != null) listener.onOpenBookmark(bm);
        };
        holder.itemView.setOnClickListener(openClick);
        holder.btnRead.setOnClickListener(openClick);

        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteBookmark(bm, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivCover;
        final TextView tvTitle;
        final TextView tvAuthor;
        final TextView tvDesc;
        final TextView tvBadge;
        final TextView btnRead;
        final TextView btnDownload;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivOnlineCover);
            tvTitle = itemView.findViewById(R.id.tvOnlineTitle);
            tvAuthor = itemView.findViewById(R.id.tvOnlineAuthor);
            tvDesc = itemView.findViewById(R.id.tvOnlineDesc);
            tvBadge = itemView.findViewById(R.id.tvOnlineBadge);
            btnRead = itemView.findViewById(R.id.btnReadOnline);
            btnDownload = itemView.findViewById(R.id.btnDownloadBook);
        }
    }
}

