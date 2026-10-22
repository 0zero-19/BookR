package com.bookr.reader.ui.dialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.NoteItem;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class NoteEditorDialog extends BottomSheetDialogFragment {

    private static final String PREF_DRAFT = "bookr_note_draft";
    private static final String ARG_NOTE = "arg_note";

    public interface OnNoteSavedListener {
        void onNoteSaved();
    }

    private NoteItem existingNote;
    private OnNoteSavedListener listener;
    private EditText etContent;

    public static NoteEditorDialog newInstance(@Nullable NoteItem note) {
        NoteEditorDialog dialog = new NoteEditorDialog();
        if (note != null) {
            Bundle args = new Bundle();
            args.putSerializable(ARG_NOTE, note);
            dialog.setArguments(args);
        }
        return dialog;
    }

    public void setListener(OnNoteSavedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_note_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        BookRepository repository = new BookRepository(context);

        if (getArguments() != null) {
            existingNote = (NoteItem) getArguments().getSerializable(ARG_NOTE);
        }

        TextView tvTitle = view.findViewById(R.id.tvNoteDialogTitle);
        TextView tvBook = view.findViewById(R.id.tvNoteDialogBook);
        etContent = view.findViewById(R.id.etNoteContent);

        SharedPreferences sp = context.getSharedPreferences(PREF_DRAFT, Context.MODE_PRIVATE);

        if (existingNote != null) {
            tvTitle.setText(R.string.action_edit);
            tvBook.setText(existingNote.getBookTitle() + " • " + existingNote.getChapterTitle());
            etContent.setText(existingNote.getNoteText());
        } else {
            tvTitle.setText(R.string.btn_new_note);
            tvBook.setText("Свободная заметка к библиотеке");
            String draft = sp.getString("draft_text", "");
            if (!draft.isEmpty()) {
                etContent.setText(draft);
            }
        }

        // Draft auto-save
        etContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (existingNote == null && s != null) {
                    sp.edit().putString("draft_text", s.toString()).apply();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        view.findViewById(R.id.btnNoteCancel).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btnNoteSave).setOnClickListener(v -> {
            String text = etContent.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(context, "Введите текст заметки", Toast.LENGTH_SHORT).show();
                return;
            }

            if (existingNote != null) {
                repository.updateNote(existingNote.getId(), text);
            } else {
                NoteItem note = new NoteItem();
                note.setBookTitle("Мои заметки");
                note.setChapterTitle("Общее");
                note.setNoteText(text);
                note.setCreatedAt(System.currentTimeMillis());
                note.setUpdatedAt(System.currentTimeMillis());
                repository.addNote(note);
                sp.edit().remove("draft_text").apply();
            }

            if (listener != null) listener.onNoteSaved();
            dismiss();
        });
    }
}
