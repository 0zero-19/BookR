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
import com.bookr.reader.data.model.NoteItem;
import com.bookr.reader.util.FileUtils;

import java.util.ArrayList;
import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    public interface OnNoteActionListener {
        void onEditNote(NoteItem note);
        void onShareNote(NoteItem note);
        void onDeleteNote(NoteItem note);
    }

    private final Context context;
    private final List<NoteItem> items = new ArrayList<>();
    private final OnNoteActionListener listener;

    public NoteAdapter(Context context, OnNoteActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<NoteItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        NoteItem note = items.get(position);

        holder.tvBookTitle.setText(note.getBookTitle());
        holder.tvNoteText.setText(note.getNoteText());
        holder.tvChapter.setText(note.getChapterTitle());
        holder.tvDate.setText(FileUtils.formatDateTime(note.getUpdatedAt() > 0 ? note.getUpdatedAt() : note.getCreatedAt()));

        if (note.getSelectedText() != null && !note.getSelectedText().trim().isEmpty()) {
            holder.tvSelectedText.setVisibility(View.VISIBLE);
            holder.tvSelectedText.setText("«" + note.getSelectedText().trim() + "»");
        } else {
            holder.tvSelectedText.setVisibility(View.GONE);
        }

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEditNote(note);
        });

        holder.btnShare.setOnClickListener(v -> {
            if (listener != null) listener.onShareNote(note);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteNote(note);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEditNote(note);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvBookTitle, tvSelectedText, tvNoteText, tvChapter, tvDate;
        FrameLayout btnEdit, btnShare, btnDelete;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBookTitle = itemView.findViewById(R.id.tvNoteBookTitle);
            tvSelectedText = itemView.findViewById(R.id.tvNoteSelectedText);
            tvNoteText = itemView.findViewById(R.id.tvNoteText);
            tvChapter = itemView.findViewById(R.id.tvNoteChapter);
            tvDate = itemView.findViewById(R.id.tvNoteDate);
            btnEdit = itemView.findViewById(R.id.btnEditNote);
            btnShare = itemView.findViewById(R.id.btnShareNote);
            btnDelete = itemView.findViewById(R.id.btnDeleteNote);
        }
    }
}
