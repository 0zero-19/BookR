package com.bookr.reader.data.model;

import android.content.Context;
import android.content.SharedPreferences;

public class ReaderSettings {
    private static final String PREF_NAME = "bookr_reader_settings";

    public static final int FONT_SERIF = 0;
    public static final int FONT_SANS = 1;
    public static final int FONT_MONO = 2;

    public static final int SPACING_COMPACT = 0;
    public static final int SPACING_NORMAL = 1;
    public static final int SPACING_RELAXED = 2;

    public static final int MARGIN_NARROW = 0;
    public static final int MARGIN_NORMAL = 1;
    public static final int MARGIN_WIDE = 2;

    public static final int ALIGN_JUSTIFY = 0;
    public static final int ALIGN_LEFT = 1;
    public static final int ALIGN_CENTER = 2;

    public static final int THEME_OBSIDIAN = 0;
    public static final int THEME_GRAPHITE = 1;
    public static final int THEME_PAPER = 2;
    public static final int THEME_WARM_GRAY = 3;
    public static final int THEME_OLED_BLACK = 4;
    public static final int THEME_CUSTOM = 5;

    // Backward compatibility aliases
    public static final int THEME_OLED = THEME_OLED_BLACK;
    public static final int THEME_CHARCOAL = THEME_GRAPHITE;
    public static final int THEME_SLATE = THEME_WARM_GRAY;

    public static final int ORIENTATION_AUTO = 0;
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_LANDSCAPE = 2;

    private int fontSize = 18;
    private int fontFamily = FONT_SERIF;
    private int lineSpacing = SPACING_NORMAL;
    private int margins = MARGIN_NORMAL;
    private int alignment = ALIGN_JUSTIFY;
    private int themeScheme = THEME_OBSIDIAN;
    private float brightness = -1.0f;
    private boolean isPagedMode = true;
    private boolean volumeKeysPageTurn = true;
    private int orientationMode = ORIENTATION_AUTO;
    private boolean fullscreenMode = false;
    private boolean autoScanOnStartup = true;

    private String customBgColor = "#0A0A0A";
    private String customTextColor = "#E6E6E6";
    private String customSurfaceColor = "#141414";

    private ThemeConfig activeTheme;

    public static ReaderSettings load(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        ReaderSettings s = new ReaderSettings();
        s.fontSize = sp.getInt("font_size", 18);
        s.fontFamily = sp.getInt("font_family", FONT_SERIF);
        s.lineSpacing = sp.getInt("line_spacing", SPACING_NORMAL);
        s.margins = sp.getInt("margins", MARGIN_NORMAL);
        s.alignment = sp.getInt("alignment", ALIGN_JUSTIFY);
        s.themeScheme = sp.getInt("theme_scheme", THEME_OBSIDIAN);
        s.brightness = sp.getFloat("brightness", -1.0f);
        s.isPagedMode = sp.getBoolean("paged_mode", true);
        s.volumeKeysPageTurn = sp.getBoolean("volume_keys", true);
        s.orientationMode = sp.getInt("orientation", ORIENTATION_AUTO);
        s.fullscreenMode = sp.getBoolean("fullscreen", false);
        s.autoScanOnStartup = sp.getBoolean("auto_scan", true);
        s.customBgColor = sp.getString("custom_bg", "#0A0A0A");
        s.customTextColor = sp.getString("custom_text", "#E6E6E6");
        s.customSurfaceColor = sp.getString("custom_surface", "#141414");

        String themeJson = sp.getString("active_theme_config", null);
        if (themeJson != null && !themeJson.trim().isEmpty()) {
            s.activeTheme = ThemeConfig.fromJson(themeJson);
        } else {
            switch (s.themeScheme) {
                case THEME_GRAPHITE:
                    s.activeTheme = ThemeConfig.getGraphite();
                    break;
                case THEME_PAPER:
                    s.activeTheme = ThemeConfig.getPaper();
                    break;
                case THEME_WARM_GRAY:
                    s.activeTheme = ThemeConfig.getWarmGray();
                    break;
                case THEME_OLED_BLACK:
                    s.activeTheme = ThemeConfig.getOledBlack();
                    break;
                case THEME_CUSTOM:
                    s.activeTheme = new ThemeConfig();
                    s.activeTheme.setName("Custom");
                    s.activeTheme.setAppBgColor(s.customBgColor);
                    s.activeTheme.setCardBgColor(s.customSurfaceColor);
                    s.activeTheme.setReaderBgColor(s.customBgColor);
                    s.activeTheme.setReaderTextColor(s.customTextColor);
                    break;
                case THEME_OBSIDIAN:
                default:
                    s.activeTheme = ThemeConfig.getObsidian();
                    break;
            }
        }

        s.activeTheme.setPagedMode(s.isPagedMode);
        s.activeTheme.setFontSize(s.fontSize);
        s.activeTheme.setFontFamily(s.fontFamily);
        s.activeTheme.setLineSpacing(s.lineSpacing);
        s.activeTheme.setMargins(s.margins);
        s.activeTheme.setAlignment(s.alignment);

        return s;
    }

    public void save(Context context) {
        if (activeTheme != null) {
            activeTheme.setPagedMode(isPagedMode);
            activeTheme.setFontSize(fontSize);
            activeTheme.setFontFamily(fontFamily);
            activeTheme.setLineSpacing(lineSpacing);
            activeTheme.setMargins(margins);
            activeTheme.setAlignment(alignment);
        }

        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = sp.edit()
                .putInt("font_size", fontSize)
                .putInt("font_family", fontFamily)
                .putInt("line_spacing", lineSpacing)
                .putInt("margins", margins)
                .putInt("alignment", alignment)
                .putInt("theme_scheme", themeScheme)
                .putFloat("brightness", brightness)
                .putBoolean("paged_mode", isPagedMode)
                .putBoolean("volume_keys", volumeKeysPageTurn)
                .putInt("orientation", orientationMode)
                .putBoolean("fullscreen", fullscreenMode)
                .putBoolean("auto_scan", autoScanOnStartup)
                .putString("custom_bg", customBgColor)
                .putString("custom_text", customTextColor)
                .putString("custom_surface", customSurfaceColor);

        if (activeTheme != null) {
            edit.putString("active_theme_config", activeTheme.toJson().toString());
        }

        edit.apply();
    }

    public ThemeConfig getActiveTheme() {
        if (activeTheme == null) {
            switch (themeScheme) {
                case THEME_GRAPHITE:
                    activeTheme = ThemeConfig.getGraphite();
                    break;
                case THEME_PAPER:
                    activeTheme = ThemeConfig.getPaper();
                    break;
                case THEME_WARM_GRAY:
                    activeTheme = ThemeConfig.getWarmGray();
                    break;
                case THEME_OLED_BLACK:
                    activeTheme = ThemeConfig.getOledBlack();
                    break;
                case THEME_CUSTOM:
                    activeTheme = new ThemeConfig();
                    activeTheme.setName("Custom");
                    activeTheme.setAppBgColor(customBgColor);
                    activeTheme.setCardBgColor(customSurfaceColor);
                    activeTheme.setReaderBgColor(customBgColor);
                    activeTheme.setReaderTextColor(customTextColor);
                    break;
                case THEME_OBSIDIAN:
                default:
                    activeTheme = ThemeConfig.getObsidian();
                    break;
            }
            activeTheme.setPagedMode(isPagedMode);
            activeTheme.setFontSize(fontSize);
            activeTheme.setFontFamily(fontFamily);
            activeTheme.setLineSpacing(lineSpacing);
            activeTheme.setMargins(margins);
            activeTheme.setAlignment(alignment);
        }
        return activeTheme;
    }

    public void setActiveTheme(ThemeConfig config) {
        if (config != null) {
            this.activeTheme = config;
            this.isPagedMode = config.isPagedMode();
            this.fontSize = config.getFontSize();
            this.fontFamily = config.getFontFamily();
            this.lineSpacing = config.getLineSpacing();
            this.margins = config.getMargins();
            this.alignment = config.getAlignment();
            this.customBgColor = config.getReaderBgColor();
            this.customTextColor = config.getReaderTextColor();
            this.customSurfaceColor = config.getCardBgColor();
            this.themeScheme = THEME_CUSTOM;
        }
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = Math.max(12, Math.min(36, fontSize));
        if (activeTheme != null) activeTheme.setFontSize(this.fontSize);
    }

    public int getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(int fontFamily) {
        this.fontFamily = fontFamily;
        if (activeTheme != null) activeTheme.setFontFamily(this.fontFamily);
    }

    public int getLineSpacing() {
        return lineSpacing;
    }

    public void setLineSpacing(int lineSpacing) {
        this.lineSpacing = lineSpacing;
        if (activeTheme != null) activeTheme.setLineSpacing(this.lineSpacing);
    }

    public int getMargins() {
        return margins;
    }

    public void setMargins(int margins) {
        this.margins = margins;
        if (activeTheme != null) activeTheme.setMargins(this.margins);
    }

    public int getAlignment() {
        return alignment;
    }

    public void setAlignment(int alignment) {
        this.alignment = alignment;
        if (activeTheme != null) activeTheme.setAlignment(this.alignment);
    }

    public int getThemeScheme() {
        return themeScheme;
    }

    public void setThemeScheme(int themeScheme) {
        this.themeScheme = themeScheme;
        switch (themeScheme) {
            case THEME_GRAPHITE:
                this.activeTheme = ThemeConfig.getGraphite();
                break;
            case THEME_PAPER:
                this.activeTheme = ThemeConfig.getPaper();
                break;
            case THEME_WARM_GRAY:
                this.activeTheme = ThemeConfig.getWarmGray();
                break;
            case THEME_OLED_BLACK:
                this.activeTheme = ThemeConfig.getOledBlack();
                break;
            case THEME_CUSTOM:
                if (this.activeTheme == null) {
                    this.activeTheme = ThemeConfig.getObsidian();
                }
                if (customBgColor != null) this.activeTheme.setReaderBgColor(customBgColor);
                if (customTextColor != null) this.activeTheme.setReaderTextColor(customTextColor);
                if (customSurfaceColor != null) this.activeTheme.setCardBgColor(customSurfaceColor);
                break;
            case THEME_OBSIDIAN:
            default:
                this.activeTheme = ThemeConfig.getObsidian();
                break;
        }
        if (this.activeTheme != null) {
            this.activeTheme.setPagedMode(this.isPagedMode);
            this.activeTheme.setFontSize(this.fontSize);
            this.activeTheme.setFontFamily(this.fontFamily);
            this.activeTheme.setLineSpacing(this.lineSpacing);
            this.activeTheme.setMargins(this.margins);
            this.activeTheme.setAlignment(this.alignment);
        }
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public boolean isPagedMode() {
        return isPagedMode;
    }

    public void setPagedMode(boolean pagedMode) {
        isPagedMode = pagedMode;
        if (activeTheme != null) activeTheme.setPagedMode(this.isPagedMode);
    }

    public boolean isVolumeKeysPageTurn() {
        return volumeKeysPageTurn;
    }

    public void setVolumeKeysPageTurn(boolean volumeKeysPageTurn) {
        this.volumeKeysPageTurn = volumeKeysPageTurn;
    }

    public int getOrientationMode() {
        return orientationMode;
    }

    public void setOrientationMode(int orientationMode) {
        this.orientationMode = orientationMode;
    }

    public boolean isFullscreenMode() {
        return fullscreenMode;
    }

    public void setFullscreenMode(boolean fullscreenMode) {
        this.fullscreenMode = fullscreenMode;
    }

    public boolean isAutoScanOnStartup() {
        return autoScanOnStartup;
    }

    public void setAutoScanOnStartup(boolean autoScanOnStartup) {
        this.autoScanOnStartup = autoScanOnStartup;
    }

    public String getCustomBgColor() {
        return customBgColor;
    }

    public void setCustomBgColor(String customBgColor) {
        this.customBgColor = customBgColor;
    }

    public String getCustomTextColor() {
        return customTextColor;
    }

    public void setCustomTextColor(String customTextColor) {
        this.customTextColor = customTextColor;
    }

    public String getCustomSurfaceColor() {
        return customSurfaceColor;
    }

    public void setCustomSurfaceColor(String customSurfaceColor) {
        this.customSurfaceColor = customSurfaceColor;
    }

    public String getCssFontFamily() {
        switch (fontFamily) {
            case FONT_SANS:
                return "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif";
            case FONT_MONO:
                return "'Roboto Mono', 'Courier New', monospace";
            case FONT_SERIF:
            default:
                return "Georgia, 'Times New Roman', serif";
        }
    }

    public String getCssLineHeight() {
        switch (lineSpacing) {
            case SPACING_COMPACT:
                return "1.3";
            case SPACING_RELAXED:
                return "1.85";
            case SPACING_NORMAL:
            default:
                return "1.55";
        }
    }

    public String getCssPadding() {
        switch (margins) {
            case MARGIN_NARROW:
                return "14px 16px";
            case MARGIN_WIDE:
                return "28px 36px";
            case MARGIN_NORMAL:
            default:
                return "20px 24px";
        }
    }

    public String getCssTextAlign() {
        switch (alignment) {
            case ALIGN_LEFT:
                return "left";
            case ALIGN_CENTER:
                return "center";
            case ALIGN_JUSTIFY:
            default:
                return "justify";
        }
    }

    public String getCssBgColor() {
        if (activeTheme != null && activeTheme.getReaderBgColor() != null) {
            return activeTheme.getReaderBgColor();
        }
        switch (themeScheme) {
            case THEME_GRAPHITE:
                return "#181818";
            case THEME_PAPER:
                return "#F4F4F4";
            case THEME_WARM_GRAY:
                return "#22201E";
            case THEME_OLED_BLACK:
                return "#000000";
            case THEME_CUSTOM:
                return customBgColor != null ? customBgColor : "#0A0A0A";
            case THEME_OBSIDIAN:
            default:
                return "#0A0A0A";
        }
    }

    public String getCssTextColor() {
        if (activeTheme != null && activeTheme.getReaderTextColor() != null) {
            return activeTheme.getReaderTextColor();
        }
        switch (themeScheme) {
            case THEME_GRAPHITE:
                return "#D5D5D5";
            case THEME_PAPER:
                return "#1A1A1A";
            case THEME_WARM_GRAY:
                return "#DCD6CD";
            case THEME_OLED_BLACK:
                return "#D0D0D0";
            case THEME_CUSTOM:
                return customTextColor != null ? customTextColor : "#E6E6E6";
            case THEME_OBSIDIAN:
            default:
                return "#F0F0F0";
        }
    }

    public String getUnderlayColor() {
        return activeTheme != null ? activeTheme.getUnderlayColor() : "#0A0A0A";
    }

    public float getUnderlayOpacity() {
        return activeTheme != null ? (activeTheme.getUnderlayAlpha() / 100f) : 0.85f;
    }
}
