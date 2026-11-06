package com.bookr.reader.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.model.OnlineBookItem;
import com.bookr.reader.network.NetworkImageLoader;

import java.util.ArrayList;
import java.util.List;

public class OnlineBookAdapter extends RecyclerView.Adapter<OnlineBookAdapter.ViewHolder> {

    public interface OnOnlineBookActionListener {
        void onReadOnline(OnlineBookItem item);
        void onDownloadBook(OnlineBookItem item, int position);
        void onShowDetails(OnlineBookItem item, int position);
    }

    private List<OnlineBookItem> items = new ArrayList<>();
    private final OnOnlineBookActionListener listener;

    public OnlineBookAdapter(OnOnlineBookActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<OnlineBookItem> newItems) {
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
        OnlineBookItem item = items.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvAuthor.setText(item.getAuthor());

        String desc = item.getDescription();
        if (desc == null || desc.trim().isEmpty()) {
            desc = item.getGenres();
        }
        holder.tvDesc.setText(desc);

        String badge = item.getSourceName() + " • " + item.getFormat() + " • " + item.getLanguage();
        holder.tvBadge.setText(badge);

        if (item.isDownloaded()) {
            holder.btnDownload.setText("Офлайн ✓");
            holder.btnDownload.setTextColor(holder.itemView.getContext().getColor(R.color.white));
        } else {
            holder.btnDownload.setText("Скачать");
            holder.btnDownload.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
        }

        NetworkImageLoader.getInstance(holder.itemView.getContext())
                .loadImage(item.getCoverUrl(), holder.ivCover, item.getTitle(), item.getAuthor());

        holder.btnRead.setOnClickListener(v -> {
            if (listener != null) listener.onReadOnline(item);
        });

        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) listener.onDownloadBook(item, holder.getAdapterPosition());
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onShowDetails(item, holder.getAdapterPosition());
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

