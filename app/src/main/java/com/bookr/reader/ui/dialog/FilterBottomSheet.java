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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.Serializable;

public class FilterBottomSheet extends BottomSheetDialogFragment {

    public static class FilterCriteria implements Serializable {
        public String format = "Все";
        public String genre = "Все";
        public String status = "Все";
        public String language = "Все";
        public String sort = "По умолчанию";

        public boolean isFiltered() {
            return !"Все".equalsIgnoreCase(format)
                    || !"Все".equalsIgnoreCase(genre)
                    || !"Все".equalsIgnoreCase(status)
                    || !"Все".equalsIgnoreCase(language)
                    || !"По умолчанию".equalsIgnoreCase(sort);
        }

        public void reset() {
            format = "Все";
            genre = "Все";
            status = "Все";
            language = "Все";
            sort = "По умолчанию";
        }
    }

    public interface OnFilterAppliedListener {
        void onFilterApplied(FilterCriteria criteria);
    }

    private static final String ARG_CRITERIA = "arg_criteria";
    private FilterCriteria currentCriteria;
    private OnFilterAppliedListener listener;

    private ChipGroup chipGroupFormat, chipGroupGenre, chipGroupStatus, chipGroupLanguage, chipGroupSort;

    public static FilterBottomSheet newInstance(FilterCriteria criteria) {
        FilterBottomSheet fragment = new FilterBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_CRITERIA, criteria != null ? criteria : new FilterCriteria());
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(OnFilterAppliedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            currentCriteria = (FilterCriteria) getArguments().getSerializable(ARG_CRITERIA);
        }
        if (currentCriteria == null) currentCriteria = new FilterCriteria();

        chipGroupFormat = view.findViewById(R.id.chipGroupFormat);
        chipGroupGenre = view.findViewById(R.id.chipGroupGenre);
        chipGroupStatus = view.findViewById(R.id.chipGroupStatus);
        chipGroupLanguage = view.findViewById(R.id.chipGroupLanguage);
        chipGroupSort = view.findViewById(R.id.chipGroupSort);

        selectChipByText(chipGroupFormat, currentCriteria.format);
        selectChipByText(chipGroupGenre, currentCriteria.genre);
        selectChipByText(chipGroupStatus, currentCriteria.status);
        selectChipByText(chipGroupLanguage, currentCriteria.language);
        selectChipByText(chipGroupSort, currentCriteria.sort);

        TextView btnReset = view.findViewById(R.id.btnResetFilters);
        btnReset.setOnClickListener(v -> {
            currentCriteria.reset();
            selectChipByText(chipGroupFormat, "Все");
            selectChipByText(chipGroupGenre, "Все");
            selectChipByText(chipGroupStatus, "Все");
            selectChipByText(chipGroupLanguage, "Все");
            selectChipByText(chipGroupSort, "По умолчанию");
        });

        TextView btnApply = view.findViewById(R.id.btnApplyFilters);
        btnApply.setOnClickListener(v -> {
            FilterCriteria applied = new FilterCriteria();
            applied.format = getSelectedChipText(chipGroupFormat, "Все");
            applied.genre = getSelectedChipText(chipGroupGenre, "Все");
            applied.status = getSelectedChipText(chipGroupStatus, "Все");
            applied.language = getSelectedChipText(chipGroupLanguage, "Все");
            applied.sort = getSelectedChipText(chipGroupSort, "По умолчанию");

            if (listener != null) {
                listener.onFilterApplied(applied);
            }
            dismiss();
        });
    }

    private void selectChipByText(ChipGroup group, String text) {
        if (group == null || text == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                String chipText = chip.getText().toString();
                if (chipText.equalsIgnoreCase(text) || chipText.startsWith(text)) {
                    chip.setChecked(true);
                    return;
                }
            }
        }
    }

    private String getSelectedChipText(ChipGroup group, String defaultText) {
        if (group == null) return defaultText;
        int checkedId = group.getCheckedChipId();
        if (checkedId != View.NO_ID) {
            Chip chip = group.findViewById(checkedId);
            if (chip != null) {
                String t = chip.getText().toString();
                if (t.startsWith("Русский")) return "RU";
                if (t.startsWith("English")) return "EN";
                return t;
            }
        }
        return defaultText;
    }
}

