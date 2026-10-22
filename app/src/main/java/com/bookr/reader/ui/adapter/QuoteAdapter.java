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
import com.bookr.reader.data.model.QuoteItem;
import com.bookr.reader.util.FileUtils;

import java.util.ArrayList;
import java.util.List;

public class QuoteAdapter extends RecyclerView.Adapter<QuoteAdapter.QuoteViewHolder> {

    public interface OnQuoteActionListener {
        void onCopyQuote(QuoteItem quote);
        void onShareQuote(QuoteItem quote);
        void onDeleteQuote(QuoteItem quote);
    }

    private final Context context;
    private final List<QuoteItem> items = new ArrayList<>();
    private final OnQuoteActionListener listener;

    public QuoteAdapter(Context context, OnQuoteActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<QuoteItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QuoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_quote, parent, false);
        return new QuoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuoteViewHolder holder, int position) {
        QuoteItem quote = items.get(position);

        holder.tvBookTitle.setText(quote.getBookTitle());
        holder.tvAuthor.setText(quote.getBookAuthor());
        holder.tvQuoteText.setText("«" + quote.getQuoteText() + "»");
        holder.tvChapter.setText(quote.getChapterTitle());
        holder.tvDate.setText(FileUtils.formatDateTime(quote.getCreatedAt()));

        holder.btnCopy.setOnClickListener(v -> {
            if (listener != null) listener.onCopyQuote(quote);
        });

        holder.btnShare.setOnClickListener(v -> {
            if (listener != null) listener.onShareQuote(quote);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteQuote(quote);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class QuoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvBookTitle, tvAuthor, tvQuoteText, tvChapter, tvDate;
        FrameLayout btnCopy, btnShare, btnDelete;

        public QuoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBookTitle = itemView.findViewById(R.id.tvQuoteBookTitle);
            tvAuthor = itemView.findViewById(R.id.tvQuoteAuthor);
            tvQuoteText = itemView.findViewById(R.id.tvQuoteText);
            tvChapter = itemView.findViewById(R.id.tvQuoteChapter);
            tvDate = itemView.findViewById(R.id.tvQuoteDate);
            btnCopy = itemView.findViewById(R.id.btnCopyQuote);
            btnShare = itemView.findViewById(R.id.btnShareQuote);
            btnDelete = itemView.findViewById(R.id.btnDeleteQuote);
        }
    }
}
