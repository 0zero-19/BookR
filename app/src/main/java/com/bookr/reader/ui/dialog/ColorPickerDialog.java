package com.bookr.reader.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bookr.reader.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

public class ColorPickerDialog extends BottomSheetDialogFragment {

    public interface OnColorPickedListener {
        void onColorPicked(String hexCode);
    }

    private String title = "Выбор цвета";
    private String initialHex = "#0A0A0A";
    private String defaultHex = "#0A0A0A";
    private OnColorPickedListener listener;

    private View viewColorPreview;
    private TextView tvPreviewHex, tvPreviewAlpha, tvHexStatus;
    private EditText etHexCode;
    private SeekBar seekAlpha, seekRed, seekGreen, seekBlue;
    private TextView tvAlphaValue, tvRedValue, tvGreenValue, tvBlueValue;
    private LinearLayout layoutSwatches;

    private int currentAlpha = 255;
    private int currentRed = 10;
    private int currentGreen = 10;
    private int currentBlue = 10;

    private boolean isUpdatingInternally = false;

    private static final String[] SWATCHES = new String[]{
            "#000000", "#0A0A0A", "#141414", "#1E1E1E", "#2A2A2A",
            "#404040", "#666666", "#8E8E93", "#B0B0B0", "#D5D5D5",
            "#F0F0F0", "#FFFFFF",
            "#80000000", "#80FFFFFF", "#40FFFFFF", "#20FFFFFF",
            "#E5A93C", "#4A90E2", "#34C759", "#E05656", "#AF52DE"
    };

    public static ColorPickerDialog newInstance(String title, String initialHex, String defaultHex, OnColorPickedListener listener) {
        ColorPickerDialog dialog = new ColorPickerDialog();
        dialog.title = title != null ? title : "Выбор цвета";
        dialog.initialHex = initialHex != null ? initialHex : "#0A0A0A";
        dialog.defaultHex = defaultHex != null ? defaultHex : "#0A0A0A";
        dialog.listener = listener;
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
                BottomSheetBehavior.from(bottomSheet).setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_color_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle = view.findViewById(R.id.tvColorDialogTitle);
        tvTitle.setText(title);

        viewColorPreview = view.findViewById(R.id.viewColorPreview);
        tvPreviewHex = view.findViewById(R.id.tvPreviewHex);
        tvPreviewAlpha = view.findViewById(R.id.tvPreviewAlpha);
        tvHexStatus = view.findViewById(R.id.tvHexStatus);
        etHexCode = view.findViewById(R.id.etHexCode);

        seekAlpha = view.findViewById(R.id.seekAlpha);
        seekRed = view.findViewById(R.id.seekRed);
        seekGreen = view.findViewById(R.id.seekGreen);
        seekBlue = view.findViewById(R.id.seekBlue);

        tvAlphaValue = view.findViewById(R.id.tvAlphaValue);
        tvRedValue = view.findViewById(R.id.tvRedValue);
        tvGreenValue = view.findViewById(R.id.tvGreenValue);
        tvBlueValue = view.findViewById(R.id.tvBlueValue);

        layoutSwatches = view.findViewById(R.id.layoutSwatches);

        setupSwatches();
        parseAndSetInitialColor(initialHex);

        // Reset
        view.findViewById(R.id.btnResetColor).setOnClickListener(v -> parseAndSetInitialColor(defaultHex));

        // Cancel
        view.findViewById(R.id.btnCancelColor).setOnClickListener(v -> dismiss());

        // Apply
        view.findViewById(R.id.btnApplyColor).setOnClickListener(v -> {
            String hex = formatCurrentHex();
            if (listener != null) {
                listener.onColorPicked(hex);
            }
            dismiss();
        });

        // Seekbars
        setupSeekBars();

        // TextWatcher
        setupTextWatcher();
    }

    private void setupSwatches() {
        layoutSwatches.removeAllViews();
        int size = (int) (36 * getResources().getDisplayMetrics().density);
        int margin = (int) (6 * getResources().getDisplayMetrics().density);

        for (String hex : SWATCHES) {
            View swatch = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, margin, 0);
            swatch.setLayoutParams(lp);

            try {
                int c = Color.parseColor(hex);
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(c);
                gd.setStroke(2, Color.parseColor("#40FFFFFF"));
                swatch.setBackground(gd);

                swatch.setOnClickListener(v -> parseAndSetInitialColor(hex));
                layoutSwatches.addView(swatch);
            } catch (Exception ignored) {}
        }
    }

    private void parseAndSetInitialColor(String hex) {
        if (hex == null || hex.isEmpty()) hex = "#0A0A0A";
        String clean = hex.trim();
        if (!clean.startsWith("#")) clean = "#" + clean;

        try {
            int parsed = Color.parseColor(clean);
            currentAlpha = Color.alpha(parsed);
            currentRed = Color.red(parsed);
            currentGreen = Color.green(parsed);
            currentBlue = Color.blue(parsed);

            updateAllUiFromComponents();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Неверный формат HEX", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAllUiFromComponents() {
        isUpdatingInternally = true;

        String hex = formatCurrentHex();
        etHexCode.setText(hex);
        etHexCode.setSelection(hex.length());

        int intColor = Color.argb(currentAlpha, currentRed, currentGreen, currentBlue);

        // Preview View
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(intColor);
        gd.setStroke(2, Color.WHITE);
        viewColorPreview.setBackground(gd);

        tvPreviewHex.setText(hex);
        tvPreviewAlpha.setText(String.format(Locale.getDefault(), "Непрозрачность: %d%%", (int) (currentAlpha / 2.55f)));
        tvHexStatus.setText("OK");
        tvHexStatus.setTextColor(Color.parseColor("#34C759"));

        seekAlpha.setProgress((int) (currentAlpha / 2.55f));
        seekRed.setProgress(currentRed);
        seekGreen.setProgress(currentGreen);
        seekBlue.setProgress(currentBlue);

        tvAlphaValue.setText(String.format(Locale.getDefault(), "%d%%", (int) (currentAlpha / 2.55f)));
        tvRedValue.setText(String.valueOf(currentRed));
        tvGreenValue.setText(String.valueOf(currentGreen));
        tvBlueValue.setText(String.valueOf(currentBlue));

        isUpdatingInternally = false;
    }

    private void setupSeekBars() {
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || isUpdatingInternally) return;

                int id = seekBar.getId();
                if (id == R.id.seekAlpha) {
                    currentAlpha = (int) (progress * 2.55f);
                } else if (id == R.id.seekRed) {
                    currentRed = progress;
                } else if (id == R.id.seekGreen) {
                    currentGreen = progress;
                } else if (id == R.id.seekBlue) {
                    currentBlue = progress;
                }

                updateAllUiFromComponents();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        seekAlpha.setOnSeekBarChangeListener(listener);
        seekRed.setOnSeekBarChangeListener(listener);
        seekGreen.setOnSeekBarChangeListener(listener);
        seekBlue.setOnSeekBarChangeListener(listener);
    }

    private void setupTextWatcher() {
        etHexCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdatingInternally) return;
                String input = s != null ? s.toString().trim() : "";
                if (!input.startsWith("#")) input = "#" + input;

                try {
                    int parsed = Color.parseColor(input);
                    currentAlpha = Color.alpha(parsed);
                    currentRed = Color.red(parsed);
                    currentGreen = Color.green(parsed);
                    currentBlue = Color.blue(parsed);

                    isUpdatingInternally = true;
                    int intColor = parsed;
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.OVAL);
                    gd.setColor(intColor);
                    gd.setStroke(2, Color.WHITE);
                    viewColorPreview.setBackground(gd);

                    tvPreviewHex.setText(input.toUpperCase());
                    tvPreviewAlpha.setText(String.format(Locale.getDefault(), "Непрозрачность: %d%%", (int) (currentAlpha / 2.55f)));
                    tvHexStatus.setText("OK");
                    tvHexStatus.setTextColor(Color.parseColor("#34C759"));

                    seekAlpha.setProgress((int) (currentAlpha / 2.55f));
                    seekRed.setProgress(currentRed);
                    seekGreen.setProgress(currentGreen);
                    seekBlue.setProgress(currentBlue);

                    tvAlphaValue.setText(String.format(Locale.getDefault(), "%d%%", (int) (currentAlpha / 2.55f)));
                    tvRedValue.setText(String.valueOf(currentRed));
                    tvGreenValue.setText(String.valueOf(currentGreen));
                    tvBlueValue.setText(String.valueOf(currentBlue));
                    isUpdatingInternally = false;
                } catch (Exception e) {
                    tvHexStatus.setText("Ошибка");
                    tvHexStatus.setTextColor(Color.parseColor("#E05656"));
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private String formatCurrentHex() {
        if (currentAlpha < 255) {
            return String.format(Locale.US, "#%02X%02X%02X%02X", currentAlpha, currentRed, currentGreen, currentBlue);
        } else {
            return String.format(Locale.US, "#%02X%02X%02X", currentRed, currentGreen, currentBlue);
        }
    }
}
