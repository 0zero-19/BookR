package com.bookr.reader.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class BackupRestoreDialog extends BottomSheetDialogFragment {

    public interface OnBackupActionListener {
        void onExportBackupRequested();
        void onImportBackupRequested();
    }

    private OnBackupActionListener listener;

    public static BackupRestoreDialog newInstance() {
        return new BackupRestoreDialog();
    }

    public void setListener(OnBackupActionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_backup_restore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnExportBackup).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onExportBackupRequested();
        });

        view.findViewById(R.id.btnImportBackup).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onImportBackupRequested();
        });

        view.findViewById(R.id.btnCloseBackup).setOnClickListener(v -> dismiss());
    }
}
