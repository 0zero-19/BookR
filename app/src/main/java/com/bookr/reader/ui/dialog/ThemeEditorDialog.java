package com.bookr.reader.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.CustomThemeItem;
import com.bookr.reader.data.model.ReaderSettings;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ThemeEditorDialog extends BottomSheetDialogFragment {

    public interface OnThemeAppliedListener {
        void onThemeApplied(ReaderSettings settings);
    }

    private ReaderSettings settings;
    private BookRepository repository;
    private OnThemeAppliedListener listener;

    private LinearLayout previewContainer;
    private TextView tvPreviewTitle, tvPreviewText;
    private TextView presetObsidian, presetGraphite, presetWarmGray, presetOled, presetPaper;
    private EditText etCustomThemeName;
    private TextView btnPickBg, btnPickText, btnResetTheme;

    private String currentBg = "#0A0A0A";
    private String currentText = "#E6E6E6";
    private int selectedPreset = ReaderSettings.THEME_OBSIDIAN;

    public static ThemeEditorDialog newInstance() {
        return new ThemeEditorDialog();
    }

    public void setListener(OnThemeAppliedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_theme_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        settings = ReaderSettings.load(context);
        repository = new BookRepository(context);

        previewContainer = view.findViewById(R.id.previewContainer);
        tvPreviewTitle = view.findViewById(R.id.tvPreviewTitle);
        tvPreviewText = view.findViewById(R.id.tvPreviewText);

        presetObsidian = view.findViewById(R.id.presetObsidian);
        presetGraphite = view.findViewById(R.id.presetGraphite);
        presetWarmGray = view.findViewById(R.id.presetWarmGray);
        presetOled = view.findViewById(R.id.presetOled);
        presetPaper = view.findViewById(R.id.presetPaper);

        etCustomThemeName = view.findViewById(R.id.etCustomThemeName);
        btnPickBg = view.findViewById(R.id.btnPickBg);
        btnPickText = view.findViewById(R.id.btnPickText);
        btnResetTheme = view.findViewById(R.id.btnResetTheme);

        currentBg = settings.getCssBgColor();
        currentText = settings.getCssTextColor();
        selectedPreset = settings.getThemeScheme();
        updatePreview();

        presetObsidian.setOnClickListener(v -> applyPreset(ReaderSettings.THEME_OBSIDIAN, "#0A0A0A", "#E6E6E6"));
        presetGraphite.setOnClickListener(v -> applyPreset(ReaderSettings.THEME_GRAPHITE, "#181818", "#D5D5D5"));
        presetWarmGray.setOnClickListener(v -> applyPreset(ReaderSettings.THEME_WARM_GRAY, "#22201E", "#DCD6CD"));
        presetOled.setOnClickListener(v -> applyPreset(ReaderSettings.THEME_OLED_BLACK, "#000000", "#D0D0D0"));
        presetPaper.setOnClickListener(v -> applyPreset(ReaderSettings.THEME_PAPER, "#F4F4F4", "#1A1A1A"));

        btnPickBg.setOnClickListener(v -> showColorPickerDialog("Выберите цвет фона", new String[]{
                "#000000", "#0A0A0A", "#121212", "#181818", "#202020", "#242220", "#F4F4F4", "#E8E4DF"
        }, color -> {
            currentBg = color;
            selectedPreset = ReaderSettings.THEME_CUSTOM;
            updatePreview();
        }));

        btnPickText.setOnClickListener(v -> showColorPickerDialog("Выберите цвет текста", new String[]{
                "#FFFFFF", "#E6E6E6", "#D5D5D5", "#C0C0C0", "#DCD6CD", "#8E8E93", "#222222", "#000000"
        }, color -> {
            currentText = color;
            selectedPreset = ReaderSettings.THEME_CUSTOM;
            updatePreview();
        }));

        btnResetTheme.setOnClickListener(v -> {
            applyPreset(ReaderSettings.THEME_OBSIDIAN, "#0A0A0A", "#E6E6E6");
            etCustomThemeName.setText("");
        });

        view.findViewById(R.id.btnSaveTheme).setOnClickListener(v -> {
            settings.setThemeScheme(selectedPreset);
            settings.setCustomBgColor(currentBg);
            settings.setCustomTextColor(currentText);
            settings.save(context);

            String name = etCustomThemeName.getText().toString().trim();
            if (!name.isEmpty()) {
                CustomThemeItem item = new CustomThemeItem();
                item.setName(name);
                item.setBgColor(currentBg);
                item.setTextColor(currentText);
                item.setSurfaceColor("#1A1A1A");
                item.setStrokeColor("#333333");
                item.setCreatedAt(System.currentTimeMillis());
                repository.addCustomTheme(item);
                Toast.makeText(context, "Тема «" + name + "» сохранена", Toast.LENGTH_SHORT).show();
            }

            if (listener != null) {
                listener.onThemeApplied(settings);
            }
            dismiss();
        });
    }

    private void applyPreset(int scheme, String bg, String text) {
        selectedPreset = scheme;
        currentBg = bg;
        currentText = text;
        updatePreview();
    }

    private void updatePreview() {
        try {
            previewContainer.setBackgroundColor(Color.parseColor(currentBg));
            tvPreviewTitle.setTextColor(Color.parseColor(currentText));
            tvPreviewText.setTextColor(Color.parseColor(currentText));
        } catch (Exception ignored) {}

        setPillState(presetObsidian, selectedPreset == ReaderSettings.THEME_OBSIDIAN);
        setPillState(presetGraphite, selectedPreset == ReaderSettings.THEME_GRAPHITE);
        setPillState(presetWarmGray, selectedPreset == ReaderSettings.THEME_WARM_GRAY);
        setPillState(presetOled, selectedPreset == ReaderSettings.THEME_OLED_BLACK);
        setPillState(presetPaper, selectedPreset == ReaderSettings.THEME_PAPER);
    }

    private void setPillState(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
        tv.setTextColor(active ? Color.WHITE : Color.parseColor("#8E8E93"));
    }

    private void showColorPickerDialog(String title, String[] colors, OnColorSelectedListener colorListener) {
        new AlertDialog.Builder(requireContext(), R.style.Theme_BookR_Dialog)
                .setTitle(title)
                .setItems(colors, (d, which) -> colorListener.onColor(colors[which]))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    interface OnColorSelectedListener {
        void onColor(String hex);
    }
}
