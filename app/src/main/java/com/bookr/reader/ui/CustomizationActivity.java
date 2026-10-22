package com.bookr.reader.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bookr.reader.R;
import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.ReaderSettings;
import com.bookr.reader.data.model.ThemeConfig;
import com.bookr.reader.ui.dialog.ColorPickerDialog;
import com.bookr.reader.util.ImageHelper;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class CustomizationActivity extends AppCompatActivity {

    private ReaderSettings settings;
    private BookRepository repository;
    private ThemeConfig workingTheme;

    // Preview views
    private ThemeBackgroundView previewBackgroundView;
    private View previewUnderlayView;
    private LinearLayout previewMockPanel;
    private TextView tvPreviewHeading, tvPreviewBadge, tvPreviewTextBody, tvPreviewSecondary, btnPreviewAction;

    // Tab buttons
    private TextView tabPresets, tabColors, tabAppBg, tabReaderBg, tabTypography, tabGlass, tabPaged;
    private LinearLayout containerPresets, containerColors, containerAppBg, containerReaderBg, containerTypography, containerGlass, containerPaged;

    // Presets
    private TextView presetObsidian, presetGraphite, presetPaper, presetWarmGray, presetOled;
    private LinearLayout layoutSavedThemesList;

    // Color list
    private LinearLayout layoutColorsList;

    // App background views
    private TextView btnAppBgTypeColor, btnAppBgTypePhoto, btnAppBgTypeGif;
    private LinearLayout layoutAppMediaActions, layoutAppGifOptions;
    private TextView btnPickAppMedia, btnClearAppMedia;
    private TextView scaleAppFill, scaleAppFit, scaleAppStretch, scaleAppOriginal;
    private SeekBar seekAppBlur, seekAppDim, seekAppBrightness, seekAppGifSpeed;
    private TextView tvAppBlurValue, tvAppDimValue, tvAppBrightnessValue, tvAppGifSpeedValue;
    private TextView btnAppGifPause, btnAppBatterySaver;

    // Reader background views
    private TextView btnReaderBgTypeColor, btnReaderBgTypePhoto, btnReaderBgTypeGif;
    private LinearLayout layoutReaderMediaActions;
    private TextView btnPickReaderMedia, btnClearReaderMedia;
    private SeekBar seekUnderlayAlpha;
    private TextView tvUnderlayAlphaValue, btnPickUnderlayColor;

    // Typography views
    private TextView fontSerif, fontSans, fontMono;
    private SeekBar seekFontSize;
    private TextView tvCustomFontSize;
    private TextView spacingCompact, spacingNormal, spacingRelaxed;
    private TextView marginNarrow, marginNormal, marginWide;

    // Glassmorphism views
    private SeekBar seekCardAlpha, seekPanelAlpha, seekBorderWidth;
    private TextView tvCardAlphaValue, tvPanelAlphaValue, tvBorderWidthValue;

    // Paged mode views
    private TextView btnModePaged, btnModeScroll, btnTogglePageAnim;

    private boolean isReaderCategoryActive = false;

    // SAF media pickers
    private final ActivityResultLauncher<String[]> pickAppMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    handlePickedMedia(uri, false);
                }
            });

    private final ActivityResultLauncher<String[]> pickReaderMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    handlePickedMedia(uri, true);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customization);

        settings = ReaderSettings.load(this);
        repository = new BookRepository(this);
        workingTheme = new ThemeConfig(settings.getActiveTheme());

        initViews();
        setupTabs();
        setupPresetsCategory();
        setupColorsCategory();
        setupAppBgCategory();
        setupReaderBgCategory();
        setupTypographyCategory();
        setupGlassCategory();
        setupPagedCategory();

        updateLivePreview();
        updateAllControls();
    }

    private void initViews() {
        // Top Action Bar
        findViewById(R.id.btnBackCustomization).setOnClickListener(v -> finish());
        findViewById(R.id.btnResetAll).setOnClickListener(v -> confirmResetAll());
        findViewById(R.id.btnSaveAsTheme).setOnClickListener(v -> promptSaveAsTheme());
        findViewById(R.id.btnApplyChanges).setOnClickListener(v -> applyAndFinish());

        // Preview views
        previewBackgroundView = findViewById(R.id.previewBackgroundView);
        previewUnderlayView = findViewById(R.id.previewUnderlayView);
        previewMockPanel = findViewById(R.id.previewMockPanel);
        tvPreviewHeading = findViewById(R.id.tvPreviewHeading);
        tvPreviewBadge = findViewById(R.id.tvPreviewBadge);
        tvPreviewTextBody = findViewById(R.id.tvPreviewTextBody);
        tvPreviewSecondary = findViewById(R.id.tvPreviewSecondary);
        btnPreviewAction = findViewById(R.id.btnPreviewAction);

        // Tabs
        tabPresets = findViewById(R.id.tabPresets);
        tabColors = findViewById(R.id.tabColors);
        tabAppBg = findViewById(R.id.tabAppBg);
        tabReaderBg = findViewById(R.id.tabReaderBg);
        tabTypography = findViewById(R.id.tabTypography);
        tabGlass = findViewById(R.id.tabGlass);
        tabPaged = findViewById(R.id.tabPaged);

        containerPresets = findViewById(R.id.containerPresets);
        containerColors = findViewById(R.id.containerColors);
        containerAppBg = findViewById(R.id.containerAppBg);
        containerReaderBg = findViewById(R.id.containerReaderBg);
        containerTypography = findViewById(R.id.containerTypography);
        containerGlass = findViewById(R.id.containerGlass);
        containerPaged = findViewById(R.id.containerPaged);
    }

    private void setupTabs() {
        tabPresets.setOnClickListener(v -> selectTab(0));
        tabColors.setOnClickListener(v -> selectTab(1));
        tabAppBg.setOnClickListener(v -> selectTab(2));
        tabReaderBg.setOnClickListener(v -> selectTab(3));
        tabTypography.setOnClickListener(v -> selectTab(4));
        tabGlass.setOnClickListener(v -> selectTab(5));
        tabPaged.setOnClickListener(v -> selectTab(6));
    }

    private void selectTab(int index) {
        TextView[] tabs = {tabPresets, tabColors, tabAppBg, tabReaderBg, tabTypography, tabGlass, tabPaged};
        LinearLayout[] containers = {containerPresets, containerColors, containerAppBg, containerReaderBg, containerTypography, containerGlass, containerPaged};

        for (int i = 0; i < tabs.length; i++) {
            boolean active = (i == index);
            tabs[i].setBackgroundResource(active ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
            tabs[i].setTextColor(active ? Color.WHITE : Color.parseColor("#8E8E93"));
            containers[i].setVisibility(active ? View.VISIBLE : View.GONE);
        }

        // Toggle preview mode between reader background and app background
        isReaderCategoryActive = (index == 3 || index == 4 || index == 6);
        updateLivePreview();
    }

    private void updateLivePreview() {
        if (workingTheme == null) return;

        // 1. Update Preview Background View
        previewBackgroundView.setConfig(workingTheme, isReaderCategoryActive);

        // 2. Update Underlay Layer
        int underlayColor = ThemeConfig.parseColorSafe(workingTheme.getUnderlayColor(), Color.BLACK);
        float underlayAlpha = workingTheme.getUnderlayAlpha() / 100f;
        previewUnderlayView.setBackgroundColor(underlayColor);
        previewUnderlayView.setAlpha(isReaderCategoryActive ? underlayAlpha : 0f);

        // 3. Update Mock Top Panel
        int panelColor = ThemeConfig.parseColorSafe(workingTheme.getPanelBgColor(), Color.parseColor("#121212"));
        int panelAlpha = (int) (workingTheme.getPanelAlpha() * 2.55f);
        GradientDrawable panelGd = new GradientDrawable();
        panelGd.setCornerRadius(10 * getResources().getDisplayMetrics().density);
        panelGd.setColor((panelColor & 0x00FFFFFF) | (panelAlpha << 24));
        panelGd.setStroke(workingTheme.getBorderWidth(), ThemeConfig.parseColorSafe(workingTheme.getBorderColor(), Color.parseColor("#26FFFFFF")));
        previewMockPanel.setBackground(panelGd);

        // 4. Update Texts & Colors
        int textColor = ThemeConfig.parseColorSafe(isReaderCategoryActive ? workingTheme.getReaderTextColor() : workingTheme.getTextColor(), Color.WHITE);
        int secColor = ThemeConfig.parseColorSafe(workingTheme.getTextSecondaryColor(), Color.parseColor("#8E8E93"));
        int btnColor = ThemeConfig.parseColorSafe(workingTheme.getButtonColor(), Color.parseColor("#20FFFFFF"));
        int btnTextColor = ThemeConfig.parseColorSafe(workingTheme.getButtonTextColor(), Color.WHITE);

        tvPreviewHeading.setTextColor(textColor);
        tvPreviewTextBody.setTextColor(textColor);
        tvPreviewSecondary.setTextColor(secColor);

        // Font size & line height on preview text
        tvPreviewTextBody.setTextSize(Math.max(10, Math.min(20, workingTheme.getFontSize() - 4)));
        tvPreviewTextBody.setText(workingTheme.isPagedMode() ?
                "Постраничный режим: Каждая страница имеет фиксированные границы в пределах экрана. Листание осуществляется свайпом или касанием зон." :
                "Режим скролла: Плавная непрерывная прокрутка текста без разделения на отдельные экраны.");

        // Mock Button
        GradientDrawable btnGd = new GradientDrawable();
        btnGd.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        btnGd.setColor(btnColor);
        btnPreviewAction.setBackground(btnGd);
        btnPreviewAction.setTextColor(btnTextColor);
    }

    // --- 1. PRESETS CATEGORY ---

    private void setupPresetsCategory() {
        presetObsidian = findViewById(R.id.presetObsidian);
        presetGraphite = findViewById(R.id.presetGraphite);
        presetPaper = findViewById(R.id.presetPaper);
        presetWarmGray = findViewById(R.id.presetWarmGray);
        presetOled = findViewById(R.id.presetOled);

        presetObsidian.setOnClickListener(v -> applyPreset(ThemeConfig.getObsidian()));
        presetGraphite.setOnClickListener(v -> applyPreset(ThemeConfig.getGraphite()));
        presetPaper.setOnClickListener(v -> applyPreset(ThemeConfig.getPaper()));
        presetWarmGray.setOnClickListener(v -> applyPreset(ThemeConfig.getWarmGray()));
        presetOled.setOnClickListener(v -> applyPreset(ThemeConfig.getOledBlack()));

        findViewById(R.id.btnCreateTheme).setOnClickListener(v -> promptCreateNewTheme());
        findViewById(R.id.btnImportTheme).setOnClickListener(v -> promptImportTheme());

        layoutSavedThemesList = findViewById(R.id.layoutSavedThemesList);
        refreshSavedThemesList();
    }

    private void applyPreset(ThemeConfig preset) {
        workingTheme = new ThemeConfig(preset);
        updateLivePreview();
        updateAllControls();
        Toast.makeText(this, "Применён пресет: " + preset.getName(), Toast.LENGTH_SHORT).show();
    }

    private void refreshSavedThemesList() {
        layoutSavedThemesList.removeAllViews();
        List<ThemeConfig> themes = repository.getAllThemeConfigs();

        if (themes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет сохранённых тем. Нажмите «Сохранить» выше, чтобы создать.");
            empty.setTextColor(Color.parseColor("#8E8E93"));
            empty.setTextSize(12);
            empty.setPadding(0, 16, 0, 16);
            layoutSavedThemesList.addView(empty);
            return;
        }

        for (ThemeConfig t : themes) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackgroundResource(R.drawable.bg_glass_card);
            card.setPadding(24, 20, 24, 20);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 12);
            card.setLayoutParams(lp);

            // Left color circle
            View dot = new View(this);
            int dotSize = (int) (18 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.setMargins(0, 0, 16, 0);
            dot.setLayoutParams(dotLp);
            GradientDrawable dotGd = new GradientDrawable();
            dotGd.setShape(GradientDrawable.OVAL);
            dotGd.setColor(ThemeConfig.parseColorSafe(t.getAppBgColor(), Color.BLACK));
            dotGd.setStroke(2, Color.WHITE);
            dot.setBackground(dotGd);
            card.addView(dot);

            // Theme Name
            TextView nameTv = new TextView(this);
            nameTv.setText(t.getName());
            nameTv.setTextColor(Color.WHITE);
            nameTv.setTextSize(14);
            nameTv.setTypeface(nameTv.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nameTv.setLayoutParams(nameLp);
            card.addView(nameTv);

            // Apply Button
            TextView btnApply = createCardActionBtn("Применить", Color.WHITE, () -> {
                workingTheme = new ThemeConfig(t);
                updateLivePreview();
                updateAllControls();
                Toast.makeText(this, "Выбрана тема: " + t.getName(), Toast.LENGTH_SHORT).show();
            });
            card.addView(btnApply);

            // Duplicate Button
            TextView btnDup = createCardActionBtn("Копия", Color.parseColor("#8E8E93"), () -> {
                ThemeConfig copy = new ThemeConfig(t);
                copy.setId(0);
                copy.setName(t.getName() + " (Копия)");
                repository.saveThemeConfig(copy);
                refreshSavedThemesList();
                Toast.makeText(this, "Создана копия темы", Toast.LENGTH_SHORT).show();
            });
            card.addView(btnDup);

            // Export Button
            TextView btnExport = createCardActionBtn("Экспорт", Color.parseColor("#8E8E93"), () -> exportTheme(t));
            card.addView(btnExport);

            // Delete Button
            TextView btnDel = createCardActionBtn("Удалить", Color.parseColor("#E05656"), () -> {
                new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog)
                        .setTitle("Удалить тему")
                        .setMessage("Удалить тему «" + t.getName() + "»?")
                        .setPositiveButton("Удалить", (d, w) -> {
                            repository.deleteCustomTheme(t.getId());
                            refreshSavedThemesList();
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
            });
            card.addView(btnDel);

            layoutSavedThemesList.addView(card);
        }
    }

    private TextView createCardActionBtn(String title, int textColor, Runnable onClick) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(textColor);
        tv.setTextSize(11);
        tv.setPadding(12, 6, 12, 6);
        tv.setBackgroundResource(R.drawable.bg_glass_pill_inactive);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(6, 0, 0, 0);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    private void promptCreateNewTheme() {
        EditText et = new EditText(this);
        et.setHint("Название новой темы");
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.parseColor("#8E8E93"));
        et.setPadding(24, 24, 24, 24);

        new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog)
                .setTitle("Создать тему с нуля")
                .setView(et)
                .setPositiveButton("Создать", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        workingTheme = ThemeConfig.getObsidian();
                        workingTheme.setId(0);
                        workingTheme.setName(name);
                        repository.saveThemeConfig(workingTheme);
                        refreshSavedThemesList();
                        updateLivePreview();
                        updateAllControls();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void promptSaveAsTheme() {
        EditText et = new EditText(this);
        et.setText(workingTheme.getName());
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.parseColor("#8E8E93"));
        et.setPadding(24, 24, 24, 24);

        new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog)
                .setTitle("Сохранить тему")
                .setView(et)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        workingTheme.setName(name);
                        repository.saveThemeConfig(workingTheme);
                        settings.setActiveTheme(workingTheme);
                        settings.save(this);
                        refreshSavedThemesList();
                        Toast.makeText(this, "Тема «" + name + "» сохранена", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void exportTheme(ThemeConfig t) {
        String json = t.toJson().toString();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("BookR Theme", json));
            Toast.makeText(this, "JSON темы скопирован в буфер обмена", Toast.LENGTH_SHORT).show();
        }

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "BookR Theme: " + t.getName());
        share.putExtra(Intent.EXTRA_TEXT, json);
        startActivity(Intent.createChooser(share, "Экспорт темы"));
    }

    private void promptImportTheme() {
        EditText et = new EditText(this);
        et.setHint("Вставьте JSON код темы...");
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.parseColor("#8E8E93"));
        et.setMinLines(4);
        et.setPadding(24, 24, 24, 24);

        new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog)
                .setTitle("Импорт темы из JSON")
                .setView(et)
                .setPositiveButton("Импортировать", (d, w) -> {
                    String raw = et.getText().toString().trim();
                    if (!raw.isEmpty()) {
                        try {
                            ThemeConfig imported = ThemeConfig.fromJson(raw);
                            imported.setId(0);
                            imported.setName(imported.getName() + " (Импорт)");
                            repository.saveThemeConfig(imported);
                            workingTheme = imported;
                            refreshSavedThemesList();
                            updateLivePreview();
                            updateAllControls();
                            Toast.makeText(this, "Тема успешно импортирована", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Ошибка формата JSON", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // --- 2. UI COLORS CATEGORY ---

    private void setupColorsCategory() {
        layoutColorsList = findViewById(R.id.layoutColorsList);
        buildColorsList();
    }

    private void buildColorsList() {
        layoutColorsList.removeAllViews();

        addColorRow("Основной фон приложения", workingTheme.getAppBgColor(), "#0A0A0A", hex -> {
            workingTheme.setAppBgColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Фон карточек и списков", workingTheme.getCardBgColor(), "#141414", hex -> {
            workingTheme.setCardBgColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Фон панелей управления", workingTheme.getPanelBgColor(), "#121212", hex -> {
            workingTheme.setPanelBgColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Основной текст и заголовки", workingTheme.getTextColor(), "#F0F0F0", hex -> {
            workingTheme.setTextColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Вторичный текст", workingTheme.getTextSecondaryColor(), "#8E8E93", hex -> {
            workingTheme.setTextSecondaryColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Границы и разделители", workingTheme.getBorderColor(), "#26FFFFFF", hex -> {
            workingTheme.setBorderColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Кнопки и активные чипы", workingTheme.getButtonColor(), "#20FFFFFF", hex -> {
            workingTheme.setButtonColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Текст и иконки на кнопках", workingTheme.getButtonTextColor(), "#FFFFFF", hex -> {
            workingTheme.setButtonTextColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Выделение текста", workingTheme.getHighlightColor(), "#40FFFFFF", hex -> {
            workingTheme.setHighlightColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Цвет закладок и заметок", workingTheme.getBookmarkColor(), "#E5A93C", hex -> {
            workingTheme.setBookmarkColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Индикаторы прогресса", workingTheme.getProgressColor(), "#FFFFFF", hex -> {
            workingTheme.setProgressColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Фон области чтения книги", workingTheme.getReaderBgColor(), "#0A0A0A", hex -> {
            workingTheme.setReaderBgColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Текст книги в читалке", workingTheme.getReaderTextColor(), "#E6E6E6", hex -> {
            workingTheme.setReaderTextColor(hex);
            updateLivePreview();
            buildColorsList();
        });

        addColorRow("Фон всплывающих окон и диалогов", workingTheme.getDialogBgColor(), "#1A1A1A", hex -> {
            workingTheme.setDialogBgColor(hex);
            updateLivePreview();
            buildColorsList();
        });
    }

    private void addColorRow(String title, String currentHex, String defaultHex, ColorPickerDialog.OnColorPickedListener onPicked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_glass_card);
        row.setPadding(20, 16, 20, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8);
        row.setLayoutParams(lp);

        // Color circle preview
        View circle = new View(this);
        int cSize = (int) (26 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(cSize, cSize);
        cLp.setMargins(0, 0, 16, 0);
        circle.setLayoutParams(cLp);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(ThemeConfig.parseColorSafe(currentHex, Color.BLACK));
        gd.setStroke(2, Color.WHITE);
        circle.setBackground(gd);
        row.addView(circle);

        // Title and Hex code
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(tLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        textCol.addView(tvTitle);

        TextView tvHex = new TextView(this);
        tvHex.setText(currentHex);
        tvHex.setTextColor(Color.parseColor("#8E8E93"));
        tvHex.setTextSize(11);
        tvHex.setTypeface(android.graphics.Typeface.MONOSPACE);
        textCol.addView(tvHex);

        row.addView(textCol);

        row.setOnClickListener(v -> {
            ColorPickerDialog.newInstance(title, currentHex, defaultHex, onPicked)
                    .show(getSupportFragmentManager(), "color_picker");
        });

        layoutColorsList.addView(row);
    }

    // --- 3. APP BACKGROUND CATEGORY ---

    private void setupAppBgCategory() {
        btnAppBgTypeColor = findViewById(R.id.btnAppBgTypeColor);
        btnAppBgTypePhoto = findViewById(R.id.btnAppBgTypePhoto);
        btnAppBgTypeGif = findViewById(R.id.btnAppBgTypeGif);

        layoutAppMediaActions = findViewById(R.id.layoutAppMediaActions);
        btnPickAppMedia = findViewById(R.id.btnPickAppMedia);
        btnClearAppMedia = findViewById(R.id.btnClearAppMedia);

        scaleAppFill = findViewById(R.id.scaleAppFill);
        scaleAppFit = findViewById(R.id.scaleAppFit);
        scaleAppStretch = findViewById(R.id.scaleAppStretch);
        scaleAppOriginal = findViewById(R.id.scaleAppOriginal);

        seekAppBlur = findViewById(R.id.seekAppBlur);
        seekAppDim = findViewById(R.id.seekAppDim);
        seekAppBrightness = findViewById(R.id.seekAppBrightness);
        seekAppGifSpeed = findViewById(R.id.seekAppGifSpeed);

        tvAppBlurValue = findViewById(R.id.tvAppBlurValue);
        tvAppDimValue = findViewById(R.id.tvAppDimValue);
        tvAppBrightnessValue = findViewById(R.id.tvAppBrightnessValue);
        tvAppGifSpeedValue = findViewById(R.id.tvAppGifSpeedValue);

        layoutAppGifOptions = findViewById(R.id.layoutAppGifOptions);
        btnAppGifPause = findViewById(R.id.btnAppGifPause);
        btnAppBatterySaver = findViewById(R.id.btnAppBatterySaver);

        btnAppBgTypeColor.setOnClickListener(v -> setAppBgType(ThemeConfig.BG_COLOR));
        btnAppBgTypePhoto.setOnClickListener(v -> setAppBgType(ThemeConfig.BG_PHOTO));
        btnAppBgTypeGif.setOnClickListener(v -> setAppBgType(ThemeConfig.BG_GIF));

        btnPickAppMedia.setOnClickListener(v -> pickAppMediaLauncher.launch(new String[]{"image/*"}));
        btnClearAppMedia.setOnClickListener(v -> {
            workingTheme.setAppBgPath(null);
            workingTheme.setAppBgType(ThemeConfig.BG_COLOR);
            updateAppBgUi();
            updateLivePreview();
        });

        scaleAppFill.setOnClickListener(v -> setAppScale(ThemeConfig.SCALE_FILL));
        scaleAppFit.setOnClickListener(v -> setAppScale(ThemeConfig.SCALE_FIT));
        scaleAppStretch.setOnClickListener(v -> setAppScale(ThemeConfig.SCALE_STRETCH));
        scaleAppOriginal.setOnClickListener(v -> setAppScale(ThemeConfig.SCALE_ORIGINAL));

        seekAppBlur.setOnSeekBarChangeListener(new SimpleSeekBarListener(progress -> {
            workingTheme.setAppBgBlur(progress);
            tvAppBlurValue.setText(progress + " px");
            updateLivePreview();
        }));

        seekAppDim.setOnSeekBarChangeListener(new SimpleSeekBarListener(progress -> {
            workingTheme.setAppBgDim(progress / 100f);
            tvAppDimValue.setText(progress + "%");
            updateLivePreview();
        }));

        seekAppBrightness.setOnSeekBarChangeListener(new SimpleSeekBarListener(progress -> {
            float val = (progress - 100) / 100f;
            workingTheme.setAppBgBrightness(val);
            tvAppBrightnessValue.setText(String.format(Locale.getDefault(), "%.1f", val));
            updateLivePreview();
        }));

        seekAppGifSpeed.setOnSeekBarChangeListener(new SimpleSeekBarListener(progress -> {
            float speed = Math.max(0.25f, progress / 100f);
            workingTheme.setGifSpeed(speed);
            tvAppGifSpeedValue.setText(String.format(Locale.getDefault(), "%.1fx", speed));
            updateLivePreview();
        }));

        btnAppGifPause.setOnClickListener(v -> {
            workingTheme.setGifPaused(!workingTheme.isGifPaused());
            btnAppGifPause.setText(workingTheme.isGifPaused() ? "Возобновить анимацию" : "Остановить на кадре");
            updateLivePreview();
        });

        btnAppBatterySaver.setOnClickListener(v -> {
            workingTheme.setBatterySaver(!workingTheme.isBatterySaver());
            btnAppBatterySaver.setBackgroundResource(workingTheme.isBatterySaver() ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
            updateLivePreview();
        });
    }

    private void setAppBgType(int type) {
        workingTheme.setAppBgType(type);
        updateAppBgUi();
        updateLivePreview();
    }

    private void setAppScale(int scale) {
        workingTheme.setAppBgScale(scale);
        updateAppBgUi();
        updateLivePreview();
    }

    private void updateAppBgUi() {
        int type = workingTheme.getAppBgType();
        setPillState(btnAppBgTypeColor, type == ThemeConfig.BG_COLOR);
        setPillState(btnAppBgTypePhoto, type == ThemeConfig.BG_PHOTO);
        setPillState(btnAppBgTypeGif, type == ThemeConfig.BG_GIF);

        boolean hasMedia = type != ThemeConfig.BG_COLOR;
        layoutAppMediaActions.setVisibility(hasMedia ? View.VISIBLE : View.GONE);
        layoutAppGifOptions.setVisibility(type == ThemeConfig.BG_GIF ? View.VISIBLE : View.GONE);

        int scale = workingTheme.getAppBgScale();
        setPillState(scaleAppFill, scale == ThemeConfig.SCALE_FILL);
        setPillState(scaleAppFit, scale == ThemeConfig.SCALE_FIT);
        setPillState(scaleAppStretch, scale == ThemeConfig.SCALE_STRETCH);
        setPillState(scaleAppOriginal, scale == ThemeConfig.SCALE_ORIGINAL);

        seekAppBlur.setProgress(workingTheme.getAppBgBlur());
        tvAppBlurValue.setText(workingTheme.getAppBgBlur() + " px");

        int dimPct = (int) (workingTheme.getAppBgDim() * 100);
        seekAppDim.setProgress(dimPct);
        tvAppDimValue.setText(dimPct + "%");

        int bProgress = (int) (workingTheme.getAppBgBrightness() * 100 + 100);
        seekAppBrightness.setProgress(bProgress);
        tvAppBrightnessValue.setText(String.format(Locale.getDefault(), "%.1f", workingTheme.getAppBgBrightness()));

        seekAppGifSpeed.setProgress((int) (workingTheme.getGifSpeed() * 100));
        tvAppGifSpeedValue.setText(String.format(Locale.getDefault(), "%.1fx", workingTheme.getGifSpeed()));

        btnAppGifPause.setText(workingTheme.isGifPaused() ? "Возобновить анимацию" : "Остановить на кадре");
        btnAppBatterySaver.setBackgroundResource(workingTheme.isBatterySaver() ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
    }

    // --- 4. READER BACKGROUND CATEGORY ---

    private void setupReaderBgCategory() {
        btnReaderBgTypeColor = findViewById(R.id.btnReaderBgTypeColor);
        btnReaderBgTypePhoto = findViewById(R.id.btnReaderBgTypePhoto);
        btnReaderBgTypeGif = findViewById(R.id.btnReaderBgTypeGif);

        layoutReaderMediaActions = findViewById(R.id.layoutReaderMediaActions);
        btnPickReaderMedia = findViewById(R.id.btnPickReaderMedia);
        btnClearReaderMedia = findViewById(R.id.btnClearReaderMedia);

        seekUnderlayAlpha = findViewById(R.id.seekUnderlayAlpha);
        tvUnderlayAlphaValue = findViewById(R.id.tvUnderlayAlphaValue);
        btnPickUnderlayColor = findViewById(R.id.btnPickUnderlayColor);

        btnReaderBgTypeColor.setOnClickListener(v -> setReaderBgType(ThemeConfig.BG_COLOR));
        btnReaderBgTypePhoto.setOnClickListener(v -> setReaderBgType(ThemeConfig.BG_PHOTO));
        btnReaderBgTypeGif.setOnClickListener(v -> setReaderBgType(ThemeConfig.BG_GIF));

        btnPickReaderMedia.setOnClickListener(v -> pickReaderMediaLauncher.launch(new String[]{"image/*"}));
        btnClearReaderMedia.setOnClickListener(v -> {
            workingTheme.setReaderBgPath(null);
            workingTheme.setReaderBgType(ThemeConfig.BG_COLOR);
            updateReaderBgUi();
            updateLivePreview();
        });

        seekUnderlayAlpha.setOnSeekBarChangeListener(new SimpleSeekBarListener(progress -> {
            workingTheme.setUnderlayAlpha(progress);
            tvUnderlayAlphaValue.setText(progress + "%");
            updateLivePreview();
        }));

        btnPickUnderlayColor.setOnClickListener(v -> {
            ColorPickerDialog.newInstance("Цвет подложки текста", workingTheme.getUnderlayColor(), "#0A0A0A", hex -> {
                workingTheme.setUnderlayColor(hex);
                updateLivePreview();
            }).show(getSupportFragmentManager(), "color_picker");
        });
    }

    private void setReaderBgType(int type) {
        workingTheme.setReaderBgType(type);
        updateReaderBgUi();
        updateLivePreview();
    }

    private void updateReaderBgUi() {
        int type = workingTheme.getReaderBgType();
        setPillState(btnReaderBgTypeColor, type == ThemeConfig.BG_COLOR);
        setPillState(btnReaderBgTypePhoto, type == ThemeConfig.BG_PHOTO);
        setPillState(btnReaderBgTypeGif, type == ThemeConfig.BG_GIF);

        boolean hasMedia = type != ThemeConfig.BG_COLOR;
        layoutReaderMediaActions.setVisibility(hasMedia ? View.VISIBLE : View.GONE);

        seekUnderlayAlpha.setProgress(workingTheme.getUnderlayAlpha());
        tvUnderlayAlphaValue.setText(workingTheme.getUnderlayAlpha() + "%");
    }

    // --- 5. TYPOGRAPHY CATEGORY ---

    private void setupTypographyCategory() {
        fontSerif = findViewById(R.id.fontSerif);
        fontSans = findViewById(R.id.fontSans);
        fontMono = findViewById(R.id.fontMono);

        seekFontSize = findViewById(R.id.seekFontSize);
        tvCustomFontSize = findViewById(R.id.tvCustomFontSize);

        spacingCompact = findViewById(R.id.spacingCompact);
        spacingNormal = findViewById(R.id.spacingNormal);
        spacingRelaxed = findViewById(R.id.spacingRelaxed);

        marginNarrow = findViewById(R.id.marginNarrow);
        marginNormal = findViewById(R.id.marginNormal);
        marginWide = findViewById(R.id.marginWide);

        fontSerif.setOnClickListener(v -> setFontFamily(ReaderSettings.FONT_SERIF));
        fontSans.setOnClickListener(v -> setFontFamily(ReaderSettings.FONT_SANS));
        fontMono.setOnClickListener(v -> setFontFamily(ReaderSettings.FONT_MONO));

        seekFontSize.setOnSeekBarChangeListener(new SimpleSeekBarListener(progress -> {
            int fs = progress + 12;
            workingTheme.setFontSize(fs);
            tvCustomFontSize.setText(fs + " sp");
            updateLivePreview();
        }));

        spacingCompact.setOnClickListener(v -> setSpacing(ReaderSettings.SPACING_COMPACT));
        spacingNormal.setOnClickListener(v -> setSpacing(ReaderSettings.SPACING_NORMAL));
        spacingRelaxed.setOnClickListener(v -> setSpacing(ReaderSettings.SPACING_RELAXED));

        marginNarrow.setOnClickListener(v -> setMargin(ReaderSettings.MARGIN_NARROW));
        marginNormal.setOnClickListener(v -> setMargin(ReaderSettings.MARGIN_NORMAL));
        marginWide.setOnClickListener(v -> setMargin(ReaderSettings.MARGIN_WIDE));
    }

    private void setFontFamily(int f) {
        workingTheme.setFontFamily(f);
        updateTypographyUi();
        updateLivePreview();
    }

    private void setSpacing(int s) {
        workingTheme.setLineSpacing(s);
        updateTypographyUi();
        updateLivePreview();
    }

    private void setMargin(int m) {
        workingTheme.setMargins(m);
        updateTypographyUi();
        updateLivePreview();
    }

    private void updateTypographyUi() {
        int f = workingTheme.getFontFamily();
        setPillState(fontSerif, f == ReaderSettings.FONT_SERIF);
        setPillState(fontSans, f == ReaderSettings.FONT_SANS);
        setPillState(fontMono, f == ReaderSettings.FONT_MONO);

        seekFontSize.setProgress(workingTheme.getFontSize() - 12);
        tvCustomFontSize.setText(workingTheme.getFontSize() + " sp");

        int s = workingTheme.getLineSpacing();
        setPillState(spacingCompact, s == ReaderSettings.SPACING_COMPACT);
        setPillState(spacingNormal, s == ReaderSettings.SPACING_NORMAL);
        setPillState(spacingRelaxed, s == ReaderSettings.SPACING_RELAXED);

        int m = workingTheme.getMargins();
        setPillState(marginNarrow, m == ReaderSettings.MARGIN_NARROW);
        setPillState(marginNormal, m == ReaderSettings.MARGIN_NORMAL);
        setPillState(marginWide, m == ReaderSettings.MARGIN_WIDE);
    }

    // --- 6. GLASSMORPHISM & EFFECTS CATEGORY ---

    private void setupGlassCategory() {
        seekCardAlpha = findViewById(R.id.seekCardAlpha);
        seekPanelAlpha = findViewById(R.id.seekPanelAlpha);
        seekBorderWidth = findViewById(R.id.seekBorderWidth);

        tvCardAlphaValue = findViewById(R.id.tvCardAlphaValue);
        tvPanelAlphaValue = findViewById(R.id.tvPanelAlphaValue);
        tvBorderWidthValue = findViewById(R.id.tvBorderWidthValue);

        seekCardAlpha.setOnSeekBarChangeListener(new SimpleSeekBarListener(progress -> {
            workingTheme.setCardAlpha(progress);
            tvCardAlphaValue.setText(progress + "%");
            updateLivePreview();
        }));

        seekPanelAlpha.setOnSeekBarChangeListener(new SimpleSeekBarListener(progress -> {
            workingTheme.setPanelAlpha(progress);
            workingTheme.setReaderBarsAlpha(progress);
            tvPanelAlphaValue.setText(progress + "%");
            updateLivePreview();
        }));

        seekBorderWidth.setOnSeekBarChangeListener(new SimpleSeekBarListener(progress -> {
            workingTheme.setBorderWidth(progress);
            tvBorderWidthValue.setText(progress + " dp");
            updateLivePreview();
        }));
    }

    private void updateGlassUi() {
        seekCardAlpha.setProgress(workingTheme.getCardAlpha());
        tvCardAlphaValue.setText(workingTheme.getCardAlpha() + "%");

        seekPanelAlpha.setProgress(workingTheme.getPanelAlpha());
        tvPanelAlphaValue.setText(workingTheme.getPanelAlpha() + "%");

        seekBorderWidth.setProgress(workingTheme.getBorderWidth());
        tvBorderWidthValue.setText(workingTheme.getBorderWidth() + " dp");
    }

    // --- 7. PAGED MODE CATEGORY ---

    private void setupPagedCategory() {
        btnModePaged = findViewById(R.id.btnModePaged);
        btnModeScroll = findViewById(R.id.btnModeScroll);
        btnTogglePageAnim = findViewById(R.id.btnTogglePageAnim);

        btnModePaged.setOnClickListener(v -> {
            workingTheme.setPagedMode(true);
            updatePagedUi();
            updateLivePreview();
        });

        btnModeScroll.setOnClickListener(v -> {
            workingTheme.setPagedMode(false);
            updatePagedUi();
            updateLivePreview();
        });

        btnTogglePageAnim.setOnClickListener(v -> {
            workingTheme.setPageTurnAnimation(!workingTheme.isPageTurnAnimation());
            updatePagedUi();
        });
    }

    private void updatePagedUi() {
        boolean paged = workingTheme.isPagedMode();
        setPillState(btnModePaged, paged);
        setPillState(btnModeScroll, !paged);

        boolean anim = workingTheme.isPageTurnAnimation();
        btnTogglePageAnim.setText(anim ? "Плавная анимация перелистывания: Вкл" : "Плавная анимация перелистывания: Выкл");
    }

    private void updateAllControls() {
        updateAppBgUi();
        updateReaderBgUi();
        updateTypographyUi();
        updateGlassUi();
        updatePagedUi();
        buildColorsList();
    }

    private void handlePickedMedia(Uri uri, boolean isReader) {
        String ext = ImageHelper.isGifFile(uri.getPath()) ? ".gif" : ".jpg";
        String localPath = ImageHelper.copyMediaToThemesDir(this, uri, ext);
        if (localPath != null) {
            boolean isGif = ImageHelper.isGifFile(localPath);
            int newType = isGif ? ThemeConfig.BG_GIF : ThemeConfig.BG_PHOTO;

            if (isReader) {
                workingTheme.setReaderBgPath(localPath);
                workingTheme.setReaderBgType(newType);
                updateReaderBgUi();
            } else {
                workingTheme.setAppBgPath(localPath);
                workingTheme.setAppBgType(newType);
                updateAppBgUi();
            }
            updateLivePreview();
            Toast.makeText(this, "Фон успешно загружен", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmResetAll() {
        new AlertDialog.Builder(this, R.style.Theme_BookR_Dialog)
                .setTitle("Сброс оформления")
                .setMessage("Сбросить все параметры к стандартной теме Obsidian?")
                .setPositiveButton("Сбросить", (d, w) -> applyPreset(ThemeConfig.getObsidian()))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void applyAndFinish() {
        settings.setActiveTheme(workingTheme);
        settings.save(this);
        Toast.makeText(this, "Оформление применено", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setPillState(TextView tv, boolean active) {
        if (tv == null) return;
        tv.setBackgroundResource(active ? R.drawable.bg_glass_pill : R.drawable.bg_glass_pill_inactive);
        tv.setTextColor(active ? Color.WHITE : Color.parseColor("#8E8E93"));
    }

    private static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        interface OnProgress {
            void onProgress(int progress);
        }
        private final OnProgress callback;

        SimpleSeekBarListener(OnProgress callback) {
            this.callback = callback;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && callback != null) {
                callback.onProgress(progress);
            }
        }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
