package com.bookr.reader.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bookr.reader.R;
import com.bookr.reader.data.model.ReaderSettings;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ReaderSettingsBottomSheet extends BottomSheetDialogFragment {

    public interface OnSettingsChangeListener {
        void onSettingsChanged(ReaderSettings settings);
        void onBrightnessChanged(float brightness);
        default void onSleepTimerSelected(int minutes) {}
    }

    private ReaderSettings settings;
    private OnSettingsChangeListener listener;

    private TextView tvFontSize;
    private TextView fontSerif, fontSans, fontMono;
    private TextView spacingCompact, spacingNormal, spacingRelaxed;
    private TextView marginNarrow, marginNormal, marginWide;
    private FrameLayout alignJustify, alignLeft, alignCenter;
    private TextView themeOled, themeCharcoal, themeSlate, themePaper;
    private TextView modePaged, modeScroll;
    private SeekBar seekBrightness;
    private TextView timerOff, timer15, timer30, timer60;
    private TextView btnToggleVolume, btnToggleFullscreen;
    private int selectedSleepMinutes = 0;

    public static ReaderSettingsBottomSheet newInstance() {
        return new ReaderSettingsBottomSheet();
    }

    public void setListener(OnSettingsChangeListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_reader_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        settings = ReaderSettings.load(context);

        // Bind views
        seekBrightness = view.findViewById(R.id.seekBrightness);
        tvFontSize = view.findViewById(R.id.tvFontSize);
        fontSerif = view.findViewById(R.id.fontSerif);
        fontSans = view.findViewById(R.id.fontSans);
        fontMono = view.findViewById(R.id.fontMono);
        spacingCompact = view.findViewById(R.id.spacingCompact);
        spacingNormal = view.findViewById(R.id.spacingNormal);
        spacingRelaxed = view.findViewById(R.id.spacingRelaxed);
        marginNarrow = view.findViewById(R.id.marginNarrow);
        marginNormal = view.findViewById(R.id.marginNormal);
        marginWide = view.findViewById(R.id.marginWide);
        alignJustify = view.findViewById(R.id.alignJustify);
        alignLeft = view.findViewById(R.id.alignLeft);
        alignCenter = view.findViewById(R.id.alignCenter);
        themeOled = view.findViewById(R.id.themeOled);
        themeCharcoal = view.findViewById(R.id.themeCharcoal);
        themeSlate = view.findViewById(R.id.themeSlate);
        themePaper = view.findViewById(R.id.themePaper);
        modePaged = view.findViewById(R.id.modePaged);
        modeScroll = view.findViewById(R.id.modeScroll);
        timerOff = view.findViewById(R.id.timerOff);
        timer15 = view.findViewById(R.id.timer15);
        timer30 = view.findViewById(R.id.timer30);
        timer60 = view.findViewById(R.id.timer60);
        btnToggleVolume = view.findViewById(R.id.btnToggleVolume);
        btnToggleFullscreen = view.findViewById(R.id.btnToggleFullscreen);

        // Sleep timer clicks
        if (timerOff != null) {
            timerOff.setOnClickListener(v -> selectTimer(0));
            timer15.setOnClickListener(v -> selectTimer(15));
            timer30.setOnClickListener(v -> selectTimer(30));
            timer60.setOnClickListener(v -> selectTimer(60));
        }

        // Toggle clicks
        if (btnToggleVolume != null) {
            btnToggleVolume.setOnClickListener(v -> {
                settings.setVolumeKeysPageTurn(!settings.isVolumeKeysPageTurn());
                settings.save(context);
                updateUiState();
                notifySettingsChanged();
            });
        }
        if (btnToggleFullscreen != null) {
            btnToggleFullscreen.setOnClickListener(v -> {
                settings.setFullscreenMode(!settings.isFullscreenMode());
                settings.save(context);
                updateUiState();
                notifySettingsChanged();
            });
        }

        // Brightness setup
        int initialBrightness = (int) (settings.getBrightness() < 0 ? 50 : settings.getBrightness() * 100);
        seekBrightness.setProgress(initialBrightness);
        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float b = Math.max(0.01f, progress / 100f);
                    settings.setBrightness(b);
                    settings.save(context);
                    if (listener != null) listener.onBrightnessChanged(b);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Font size setup
        updateFontSizeDisplay();
        view.findViewById(R.id.btnFontDecrease).setOnClickListener(v -> {
            settings.setFontSize(settings.getFontSize() - 1);
            settings.save(context);
            updateFontSizeDisplay();
            notifySettingsChanged();
        });
        view.findViewById(R.id.btnFontIncrease).setOnClickListener(v -> {
            settings.setFontSize(settings.getFontSize() + 1);
            settings.save(context);
            updateFontSizeDisplay();
            notifySettingsChanged();
        });

        // Font family
        fontSerif.setOnClickListener(v -> selectFont(ReaderSettings.FONT_SERIF));
        fontSans.setOnClickListener(v -> selectFont(ReaderSettings.FONT_SANS));
        fontMono.setOnClickListener(v -> selectFont(ReaderSettings.FONT_MONO));

        // Spacing
        spacingCompact.setOnClickListener(v -> selectSpacing(ReaderSettings.SPACING_COMPACT));
        spacingNormal.setOnClickListener(v -> selectSpacing(ReaderSettings.SPACING_NORMAL));
        spacingRelaxed.setOnClickListener(v -> selectSpacing(ReaderSettings.SPACING_RELAXED));

        // Margins
        marginNarrow.setOnClickListener(v -> selectMargin(ReaderSettings.MARGIN_NARROW));
        marginNormal.setOnClickListener(v -> selectMargin(ReaderSettings.MARGIN_NORMAL));
        marginWide.setOnClickListener(v -> selectMargin(ReaderSettings.MARGIN_WIDE));

        // Alignment
        alignJustify.setOnClickListener(v -> selectAlignment(ReaderSettings.ALIGN_JUSTIFY));
        alignLeft.setOnClickListener(v -> selectAlignment(ReaderSettings.ALIGN_LEFT));
        alignCenter.setOnClickListener(v -> selectAlignment(ReaderSettings.ALIGN_CENTER));

        // Themes
        themeOled.setOnClickListener(v -> selectTheme(ReaderSettings.THEME_OLED));
        themeCharcoal.setOnClickListener(v -> selectTheme(ReaderSettings.THEME_CHARCOAL));
        themeSlate.setOnClickListener(v -> selectTheme(ReaderSettings.THEME_SLATE));
        themePaper.setOnClickListener(v -> selectTheme(ReaderSettings.THEME_PAPER));

        // Mode
        modePaged.setOnClickListener(v -> selectMode(true));
        modeScroll.setOnClickListener(v -> selectMode(false));

        updateUiState();
    }

    private void updateFontSizeDisplay() {
        if (tvFontSize != null) {
            tvFontSize.setText(String.valueOf(settings.getFontSize()));
        }
    }

    private void selectFont(int f) {
        settings.setFontFamily(f);
        settings.save(requireContext());
        updateUiState();
        notifySettingsChanged();
    }

    private void selectSpacing(int s) {
        settings.setLineSpacing(s);
        settings.save(requireContext());
        updateUiState();
        notifySettingsChanged();
    }

    private void selectMargin(int m) {
        settings.setMargins(m);
        settings.save(requireContext());
        updateUiState();
        notifySettingsChanged();
    }

    private void selectAlignment(int a) {
        settings.setAlignment(a);
        settings.save(requireContext());
        updateUiState();
        notifySettingsChanged();
    }

    private void selectTheme(int t) {
        settings.setThemeScheme(t);
        settings.save(requireContext());
        updateUiState();
        notifySettingsChanged();
    }

    private void selectMode(boolean paged) {
        settings.setPagedMode(paged);
        settings.save(requireContext());
        updateUiState();
        notifySettingsChanged();
    }

    private void updateUiState() {
        Context context = getContext();
        if (context == null) return;

        // Fonts
        setPillState(fontSerif, settings.getFontFamily() == ReaderSettings.FONT_SERIF);
        setPillState(fontSans, settings.getFontFamily() == ReaderSettings.FONT_SANS);
        setPillState(fontMono, settings.getFontFamily() == ReaderSettings.FONT_MONO);

        // Spacing
        setPillState(spacingCompact, settings.getLineSpacing() == ReaderSettings.SPACING_COMPACT);
        setPillState(spacingNormal, settings.getLineSpacing() == ReaderSettings.SPACING_NORMAL);
        setPillState(spacingRelaxed, settings.getLineSpacing() == ReaderSettings.SPACING_RELAXED);

        // Margins
        setPillState(marginNarrow, settings.getMargins() == ReaderSettings.MARGIN_NARROW);
        setPillState(marginNormal, settings.getMargins() == ReaderSettings.MARGIN_NORMAL);
        setPillState(marginWide, settings.getMargins() == ReaderSettings.MARGIN_WIDE);

        // Alignment
        setFramePillState(alignJustify, settings.getAlignment() == ReaderSettings.ALIGN_JUSTIFY);
        setFramePillState(alignLeft, settings.getAlignment() == ReaderSettings.ALIGN_LEFT);
        setFramePillState(alignCenter, settings.getAlignment() == ReaderSettings.ALIGN_CENTER);

        // Themes
        setPillState(themeOled, settings.getThemeScheme() == ReaderSettings.THEME_OLED);
        setPillState(themeCharcoal, settings.getThemeScheme() == ReaderSettings.THEME_CHARCOAL);
        setPillState(themeSlate, settings.getThemeScheme() == ReaderSettings.THEME_SLATE);
        setPillState(themePaper, settings.getThemeScheme() == ReaderSettings.THEME_PAPER);

        // Mode
        setPillState(modePaged, settings.isPagedMode());
        setPillState(modeScroll, !settings.isPagedMode());

        // Sleep Timer
        if (timerOff != null) {
            setPillState(timerOff, selectedSleepMinutes == 0);
            setPillState(timer15, selectedSleepMinutes == 15);
            setPillState(timer30, selectedSleepMinutes == 30);
            setPillState(timer60, selectedSleepMinutes == 60);
        }

        // Toggles
        if (btnToggleVolume != null) {
            setPillState(btnToggleVolume, settings.isVolumeKeysPageTurn());
        }
        if (btnToggleFullscreen != null) {
            setPillState(btnToggleFullscreen, settings.isFullscreenMode());
        }
    }

    private void selectTimer(int minutes) {
        this.selectedSleepMinutes = minutes;
        updateUiState();
        if (listener != null) {
            listener.onSleepTimerSelected(minutes);
        }
    }

    private void setPillState(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
        tv.setTextColor(ContextCompat.getColor(requireContext(), active ? R.color.white : R.color.text_secondary));
    }

    private void setFramePillState(FrameLayout fl, boolean active) {
        fl.setBackgroundResource(active ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
    }

    private void notifySettingsChanged() {
        if (listener != null) {
            listener.onSettingsChanged(settings);
        }
    }
}
