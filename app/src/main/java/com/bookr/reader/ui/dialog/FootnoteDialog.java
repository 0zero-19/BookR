package com.bookr.reader.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bookr.reader.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class FootnoteDialog extends BottomSheetDialogFragment {

    private static final String ARG_NOTE_TEXT = "arg_note_text";

    public static FootnoteDialog newInstance(String noteText) {
        FootnoteDialog dialog = new FootnoteDialog();
        Bundle args = new Bundle();
        args.putString(ARG_NOTE_TEXT, noteText);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_footnote, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String noteText = getArguments() != null ? getArguments().getString(ARG_NOTE_TEXT) : "";

        TextView tvContent = view.findViewById(R.id.tvFootnoteContent);
        tvContent.setText(noteText != null && !noteText.isEmpty() ? noteText : "Сноска пуста");

        view.findViewById(R.id.btnCloseFootnote).setOnClickListener(v -> dismiss());
    }
}
