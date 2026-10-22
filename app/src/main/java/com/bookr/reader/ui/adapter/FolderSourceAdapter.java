package com.bookr.reader.ui.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.model.FolderSourceItem;
import com.bookr.reader.util.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FolderSourceAdapter extends RecyclerView.Adapter<FolderSourceAdapter.SourceViewHolder> {

    public interface OnFolderSourceActionListener {
        void onRescanFolder(FolderSourceItem item);
        void onDeleteFolder(FolderSourceItem item);
        void onRestoreAccess(FolderSourceItem item);
    }

    private final Context context;
    private final List<FolderSourceItem> items = new ArrayList<>();
    private final OnFolderSourceActionListener listener;

    public FolderSourceAdapter(Context context, OnFolderSourceActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<FolderSourceItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_folder_source, parent, false);
        return new SourceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
        FolderSourceItem item = items.get(position);

        holder.tvName.setText(item.getDisplayName());
        holder.tvUri.setText(item.getTreeUri());

        String timeStr = item.getLastScanned() > 0 ? FileUtils.formatDateTime(item.getLastScanned()) : "Не сканировалась";
        holder.tvInfo.setText(String.format(Locale.getDefault(), "Книг: %d • Сканировано: %s", item.getBooksCount(), timeStr));

        if (item.isAccessible()) {
            holder.tvStatus.setText("Доступна");
            holder.tvStatus.setBackgroundResource(R.drawable.bg_glass_pill);
            holder.tvStatus.setTextColor(Color.WHITE);
        } else {
            holder.tvStatus.setText("Доступ утерян");
            holder.tvStatus.setBackgroundResource(R.drawable.bg_glass_pill_inactive);
            holder.tvStatus.setTextColor(Color.parseColor("#FF5252"));
        }

        holder.tvStatus.setOnClickListener(v -> {
            if (!item.isAccessible() && listener != null) {
                listener.onRestoreAccess(item);
            }
        });

        holder.btnRescan.setOnClickListener(v -> {
            if (listener != null) listener.onRescanFolder(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteFolder(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SourceViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvUri, tvStatus, tvInfo;
        FrameLayout btnRescan, btnDelete;

        public SourceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvFolderName);
            tvUri = itemView.findViewById(R.id.tvFolderUri);
            tvStatus = itemView.findViewById(R.id.tvFolderStatus);
            tvInfo = itemView.findViewById(R.id.tvFolderInfo);
            btnRescan = itemView.findViewById(R.id.btnFolderRescan);
            btnDelete = itemView.findViewById(R.id.btnFolderDelete);
        }
    }
}
