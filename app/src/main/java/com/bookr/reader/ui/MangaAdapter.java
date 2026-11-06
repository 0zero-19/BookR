package com.bookr.reader.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.model.MangaItem;
import com.bookr.reader.network.NetworkImageLoader;

import java.util.ArrayList;
import java.util.List;

public class MangaAdapter extends RecyclerView.Adapter<MangaAdapter.ViewHolder> {

    public interface OnMangaClickListener {
        void onMangaClick(MangaItem manga);
        void onMangaLongClick(MangaItem manga);
    }

    private List<MangaItem> items = new ArrayList<>();
    private final OnMangaClickListener listener;

    public MangaAdapter(OnMangaClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<MangaItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manga, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MangaItem item = items.get(position);

        String titlePrefix = item.isFavorite() ? "★ " : "";
        holder.tvTitle.setText(titlePrefix + item.getTitle());
        holder.tvAuthor.setText(item.getAuthor() + " • " + item.getStatus());

        String genresAndDesc = item.getGenres();
        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            genresAndDesc = (genresAndDesc.isEmpty() ? "" : genresAndDesc + "\n") + item.getDescription();
        }
        holder.tvGenres.setText(genresAndDesc);

        StringBuilder badgeSb = new StringBuilder();
        if (item.getDownloadedChaptersCount() > 0 || item.isDownloaded()) {
            badgeSb.append("Офлайн: ")
                    .append(Math.max(1, item.getDownloadedChaptersCount()))
                    .append(" гл.");
        } else {
            badgeSb.append("ОНЛАЙН");
        }
        if (item.getLastChapterTitle() != null && !item.getLastChapterTitle().isEmpty()) {
            badgeSb.append(" • Стр. ").append(item.getLastPage() + 1);
            holder.btnOpen.setText("Продолжить");
        } else {
            holder.btnOpen.setText("Главы");
        }
        holder.tvBadge.setText(badgeSb.toString());

        String cover = item.getLocalCoverPath() != null && !item.getLocalCoverPath().isEmpty()
                ? item.getLocalCoverPath() : item.getCoverUrl();
        NetworkImageLoader.getInstance(holder.itemView.getContext())
                .loadImage(cover, holder.ivCover, item.getTitle(), item.getAuthor());

        View.OnClickListener openClick = v -> {
            if (listener != null) listener.onMangaClick(item);
        };
        holder.itemView.setOnClickListener(openClick);
        holder.btnOpen.setOnClickListener(openClick);

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onMangaLongClick(item);
            return true;
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
        final TextView tvGenres;
        final TextView tvBadge;
        final TextView btnOpen;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivMangaCover);
            tvTitle = itemView.findViewById(R.id.tvMangaTitle);
            tvAuthor = itemView.findViewById(R.id.tvMangaAuthor);
            tvGenres = itemView.findViewById(R.id.tvMangaGenres);
            tvBadge = itemView.findViewById(R.id.tvMangaBadge);
            btnOpen = itemView.findViewById(R.id.btnOpenManga);
        }
    }
}

