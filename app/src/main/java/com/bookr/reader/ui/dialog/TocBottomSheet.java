package com.bookr.reader.ui.dialog;

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
import com.bookr.reader.data.model.TocItem;
import com.bookr.reader.ui.adapter.TocAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TocBottomSheet extends BottomSheetDialogFragment {

    public interface OnTocItemSelectedListener {
        void onTocItemSelected(TocItem item);
    }

    private static final String ARG_TOC_LIST = "arg_toc_list";
    private List<TocItem> tocItems = new ArrayList<>();
    private OnTocItemSelectedListener listener;

    public static TocBottomSheet newInstance(List<TocItem> items) {
        TocBottomSheet fragment = new TocBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TOC_LIST, (Serializable) items);
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(OnTocItemSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            List<TocItem> list = (List<TocItem>) getArguments().getSerializable(ARG_TOC_LIST);
            if (list != null) tocItems.addAll(list);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_toc, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rvToc = view.findViewById(R.id.rvToc);
        TextView tvEmpty = view.findViewById(R.id.tvEmptyToc);

        if (tocItems.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvToc.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvToc.setVisibility(View.VISIBLE);
            rvToc.setLayoutManager(new LinearLayoutManager(getContext()));
            rvToc.setAdapter(new TocAdapter(requireContext(), tocItems, item -> {
                dismiss();
                if (listener != null) listener.onTocItemSelected(item);
            }));
        }
    }
}
