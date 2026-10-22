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
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.ShelfItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.ShelfViewHolder> {

    public interface OnShelfClickListener {
        void onShelfClick(ShelfItem shelf);
        void onShelfDelete(ShelfItem shelf);
    }

    private final Context context;
    private final BookRepository repository;
    private final List<ShelfItem> items = new ArrayList<>();
    private final OnShelfClickListener listener;

    public ShelfAdapter(Context context, BookRepository repository, OnShelfClickListener listener) {
        this.context = context;
        this.repository = repository;
        this.listener = listener;
    }

    public void setItems(List<ShelfItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ShelfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_shelf, parent, false);
        return new ShelfViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShelfViewHolder holder, int position) {
        ShelfItem shelf = items.get(position);
        holder.tvName.setText(shelf.getName());

        int count = repository.getShelfBooksCount(shelf.getId());
        holder.tvCount.setText(String.format(Locale.getDefault(), "%d %s", count, getBookCountNoun(count)));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onShelfClick(shelf);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onShelfDelete(shelf);
        });
    }

    private String getBookCountNoun(int count) {
        int rem100 = count % 100;
        int rem10 = count % 10;
        if (rem100 >= 11 && rem100 <= 19) return "книг";
        if (rem10 == 1) return "книга";
        if (rem10 >= 2 && rem10 <= 4) return "книги";
        return "книг";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ShelfViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCount;
        FrameLayout btnDelete;

        public ShelfViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvShelfName);
            tvCount = itemView.findViewById(R.id.tvShelfBooksCount);
            btnDelete = itemView.findViewById(R.id.btnDeleteShelf);
        }
    }
}
