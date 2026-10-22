package com.bookr.reader.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.FolderSourceItem;
import com.bookr.reader.ui.adapter.FolderSourceAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

public class FolderSourcesDialog extends BottomSheetDialogFragment {

    public interface OnFolderSourceInteractionListener {
        void onAddFolderRequested();
        void onRescanFolderRequested(FolderSourceItem item);
        void onRescanAllRequested();
        void onRestoreAccessRequested(FolderSourceItem item);
    }

    private BookRepository repository;
    private FolderSourceAdapter adapter;
    private RecyclerView rvSources;
    private TextView tvEmpty;
    private OnFolderSourceInteractionListener listener;

    public static FolderSourcesDialog newInstance() {
        return new FolderSourcesDialog();
    }

    public void setListener(OnFolderSourceInteractionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_folder_sources, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        repository = new BookRepository(context);

        rvSources = view.findViewById(R.id.rvFolderSources);
        tvEmpty = view.findViewById(R.id.tvEmptyFolderSources);

        rvSources.setLayoutManager(new LinearLayoutManager(context));
        adapter = new FolderSourceAdapter(context, new FolderSourceAdapter.OnFolderSourceActionListener() {
            @Override
            public void onRescanFolder(FolderSourceItem item) {
                if (listener != null) listener.onRescanFolderRequested(item);
                dismiss();
            }

            @Override
            public void onDeleteFolder(FolderSourceItem item) {
                repository.deleteFolderSource(item.getId());
                loadSources();
            }

            @Override
            public void onRestoreAccess(FolderSourceItem item) {
                if (listener != null) listener.onRestoreAccessRequested(item);
                dismiss();
            }
        });
        rvSources.setAdapter(adapter);

        view.findViewById(R.id.btnDialogAddFolder).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onAddFolderRequested();
        });

        view.findViewById(R.id.btnRescanAllFolders).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onRescanAllRequested();
        });

        view.findViewById(R.id.btnCloseFolderDialog).setOnClickListener(v -> dismiss());

        loadSources();
    }

    private void loadSources() {
        List<FolderSourceItem> list = repository.getAllFolderSources();
        adapter.setItems(list);
        if (list.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvSources.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvSources.setVisibility(View.VISIBLE);
        }
    }
}
