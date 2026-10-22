package com.bookr.reader.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.model.TocItem;

import java.util.ArrayList;
import java.util.List;

public class TocAdapter extends RecyclerView.Adapter<TocAdapter.TocViewHolder> {

    public interface OnTocClickListener {
        void onTocClick(TocItem item);
    }

    private final Context context;
    private final List<TocItem> items = new ArrayList<>();
    private final OnTocClickListener listener;

    public TocAdapter(Context context, List<TocItem> items, OnTocClickListener listener) {
        this.context = context;
        if (items != null) {
            this.items.addAll(items);
        }
        this.listener = listener;
    }

    @NonNull
    @Override
    public TocViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_toc, parent, false);
        return new TocViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TocViewHolder holder, int position) {
        TocItem item = items.get(position);
        holder.tvTitle.setText(item.getTitle());

        // Indent based on nesting level
        int indentDp = Math.min(32, item.getLevel() * 12);
        int indentPx = (int) (indentDp * context.getResources().getDisplayMetrics().density);
        holder.itemView.setPadding(indentPx + 16, holder.itemView.getPaddingTop(), 16, holder.itemView.getPaddingBottom());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTocClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TocViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;

        public TocViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTocTitle);
        }
    }
}
