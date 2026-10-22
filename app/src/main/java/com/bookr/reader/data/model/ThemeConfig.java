package com.bookr.reader.data.model;

import android.graphics.Color;

import org.json.JSONObject;

import java.io.Serializable;

public class ThemeConfig implements Serializable, Cloneable {
    public static final int BG_COLOR = 0;
    public static final int BG_PHOTO = 1;
    public static final int BG_GIF = 2;

    public static final int SCALE_FILL = 0;    // CenterCrop
    public static final int SCALE_FIT = 1;     // FitCenter
    public static final int SCALE_STRETCH = 2; // FitXY
    public static final int SCALE_ORIGINAL = 3;// Center

    private long id = 0;
    private String name = "Obsidian";

    // --- UI COLORS (HEX ARGB or RGB) ---
    private String appBgColor = "#0A0A0A";
    private String cardBgColor = "#141414";
    private String panelBgColor = "#121212";
    private String textColor = "#F0F0F0";
    private String textSecondaryColor = "#8E8E93";
    private String borderColor = "#26FFFFFF";
    private String buttonColor = "#20FFFFFF";
    private String buttonTextColor = "#FFFFFF";
    private String highlightColor = "#40FFFFFF";
    private String bookmarkColor = "#E5A93C";
    private String progressColor = "#FFFFFF";
    private String readerBgColor = "#0A0A0A";
    private String readerTextColor = "#E6E6E6";
    private String dialogBgColor = "#1A1A1A";

    // --- TRANSPARENCY & GLASSMORPHISM ---
    private int cardAlpha = 85;          // 0-100%
    private int panelAlpha = 90;         // 0-100%
    private int readerBarsAlpha = 90;    // 0-100%
    private int dialogAlpha = 95;        // 0-100%
    private int underlayAlpha = 85;      // 0-100% text underlay opacity
    private String underlayColor = "#0A0A0A";
    private int borderWidth = 1;         // 0-4 dp
    private int blurRadius = 12;         // 0-25 px

    // --- APP BACKGROUND (LIBRARY / MENUS) ---
    private int appBgType = BG_COLOR;
    private String appBgPath = null;
    private int appBgScale = SCALE_FILL;
    private float appBgOffsetX = 0f;
    private float appBgOffsetY = 0f;
    private float appBgBrightness = 0f;      // -1.0 to 1.0
    private float appBgContrast = 1.0f;      // 0.0 to 2.0
    private float appBgSaturation = 1.0f;    // 0.0 to 2.0
    private int appBgBlur = 0;               // 0 to 25 px
    private float appBgDim = 0.2f;           // 0.0 to 1.0 overlay dimming
    private float appBgOpacity = 1.0f;       // 0.0 to 1.0
    private String appBgTintColor = "#00000000";

    // --- READER BACKGROUND (READING SCREEN) ---
    private int readerBgType = BG_COLOR;
    private String readerBgPath = null;
    private int readerBgScale = SCALE_FILL;
    private float readerBgOffsetX = 0f;
    private float readerBgOffsetY = 0f;
    private float readerBgBrightness = 0f;
    private float readerBgContrast = 1.0f;
    private float readerBgSaturation = 1.0f;
    private int readerBgBlur = 0;
    private float readerBgDim = 0.35f;
    private float readerBgOpacity = 1.0f;
    private String readerBgTintColor = "#00000000";

    // --- GIF PLAYBACK SETTINGS ---
    private float gifSpeed = 1.0f;           // 0.25 to 2.0
    private boolean gifPaused = false;
    private boolean batterySaver = false;    // freeze GIF animation

    // --- PAGED MODE SETTINGS ---
    private boolean pagedMode = true;
    private boolean pageTurnAnimation = true;
    private float tapZoneLeft = 0.25f;       // 0.15 - 0.35
    private float tapZoneRight = 0.75f;      // 0.65 - 0.85
    private int swipeSensitivity = 40;       // dp threshold

    // --- TYPOGRAPHY ---
    private int fontFamily = ReaderSettings.FONT_SERIF;
    private int fontSize = 18;
    private int lineSpacing = ReaderSettings.SPACING_NORMAL;
    private int margins = ReaderSettings.MARGIN_NORMAL;
    private int alignment = ReaderSettings.ALIGN_JUSTIFY;

    private long createdAt = System.currentTimeMillis();

    public ThemeConfig() {}

    public ThemeConfig(ThemeConfig other) {
        if (other == null) return;
        this.id = other.id;
        this.name = other.name;
        this.appBgColor = other.appBgColor;
        this.cardBgColor = other.cardBgColor;
        this.panelBgColor = other.panelBgColor;
        this.textColor = other.textColor;
        this.textSecondaryColor = other.textSecondaryColor;
        this.borderColor = other.borderColor;
        this.buttonColor = other.buttonColor;
        this.buttonTextColor = other.buttonTextColor;
        this.highlightColor = other.highlightColor;
        this.bookmarkColor = other.bookmarkColor;
        this.progressColor = other.progressColor;
        this.readerBgColor = other.readerBgColor;
        this.readerTextColor = other.readerTextColor;
        this.dialogBgColor = other.dialogBgColor;

        this.cardAlpha = other.cardAlpha;
        this.panelAlpha = other.panelAlpha;
        this.readerBarsAlpha = other.readerBarsAlpha;
        this.dialogAlpha = other.dialogAlpha;
        this.underlayAlpha = other.underlayAlpha;
        this.underlayColor = other.underlayColor;
        this.borderWidth = other.borderWidth;
        this.blurRadius = other.blurRadius;

        this.appBgType = other.appBgType;
        this.appBgPath = other.appBgPath;
        this.appBgScale = other.appBgScale;
        this.appBgOffsetX = other.appBgOffsetX;
        this.appBgOffsetY = other.appBgOffsetY;
        this.appBgBrightness = other.appBgBrightness;
        this.appBgContrast = other.appBgContrast;
        this.appBgSaturation = other.appBgSaturation;
        this.appBgBlur = other.appBgBlur;
        this.appBgDim = other.appBgDim;
        this.appBgOpacity = other.appBgOpacity;
        this.appBgTintColor = other.appBgTintColor;

        this.readerBgType = other.readerBgType;
        this.readerBgPath = other.readerBgPath;
        this.readerBgScale = other.readerBgScale;
        this.readerBgOffsetX = other.readerBgOffsetX;
        this.readerBgOffsetY = other.readerBgOffsetY;
        this.readerBgBrightness = other.readerBgBrightness;
        this.readerBgContrast = other.readerBgContrast;
        this.readerBgSaturation = other.readerBgSaturation;
        this.readerBgBlur = other.readerBgBlur;
        this.readerBgDim = other.readerBgDim;
        this.readerBgOpacity = other.readerBgOpacity;
        this.readerBgTintColor = other.readerBgTintColor;

        this.gifSpeed = other.gifSpeed;
        this.gifPaused = other.gifPaused;
        this.batterySaver = other.batterySaver;

        this.pagedMode = other.pagedMode;
        this.pageTurnAnimation = other.pageTurnAnimation;
        this.tapZoneLeft = other.tapZoneLeft;
        this.tapZoneRight = other.tapZoneRight;
        this.swipeSensitivity = other.swipeSensitivity;

        this.fontFamily = other.fontFamily;
        this.fontSize = other.fontSize;
        this.lineSpacing = other.lineSpacing;
        this.margins = other.margins;
        this.alignment = other.alignment;
        this.createdAt = System.currentTimeMillis();
    }

    // --- BUILT-IN PRESETS ---

    public static ThemeConfig getObsidian() {
        ThemeConfig c = new ThemeConfig();
        c.setName("Obsidian");
        c.setAppBgColor("#0A0A0A");
        c.setCardBgColor("#141414");
        c.setPanelBgColor("#121212");
        c.setTextColor("#F0F0F0");
        c.setTextSecondaryColor("#8E8E93");
        c.setBorderColor("#26FFFFFF");
        c.setButtonColor("#20FFFFFF");
        c.setReaderBgColor("#0A0A0A");
        c.setReaderTextColor("#E6E6E6");
        c.setUnderlayColor("#0A0A0A");
        c.setUnderlayAlpha(85);
        return c;
    }

    public static ThemeConfig getGraphite() {
        ThemeConfig c = new ThemeConfig();
        c.setName("Graphite");
        c.setAppBgColor("#181818");
        c.setCardBgColor("#222222");
        c.setPanelBgColor("#1E1E1E");
        c.setTextColor("#D5D5D5");
        c.setTextSecondaryColor("#888888");
        c.setBorderColor("#30FFFFFF");
        c.setButtonColor("#28FFFFFF");
        c.setReaderBgColor("#181818");
        c.setReaderTextColor("#D5D5D5");
        c.setUnderlayColor("#181818");
        c.setUnderlayAlpha(85);
        return c;
    }

    public static ThemeConfig getPaper() {
        ThemeConfig c = new ThemeConfig();
        c.setName("Paper");
        c.setAppBgColor("#F4F4F4");
        c.setCardBgColor("#EAE6E1");
        c.setPanelBgColor("#EFECE6");
        c.setTextColor("#1A1A1A");
        c.setTextSecondaryColor("#666666");
        c.setBorderColor("#20000000");
        c.setButtonColor("#15000000");
        c.setButtonTextColor("#1A1A1A");
        c.setReaderBgColor("#F4F4F4");
        c.setReaderTextColor("#1A1A1A");
        c.setUnderlayColor("#F4F4F4");
        c.setUnderlayAlpha(90);
        return c;
    }

    public static ThemeConfig getWarmGray() {
        ThemeConfig c = new ThemeConfig();
        c.setName("Warm Gray");
        c.setAppBgColor("#22201E");
        c.setCardBgColor("#2C2926");
        c.setPanelBgColor("#282522");
        c.setTextColor("#DCD6CD");
        c.setTextSecondaryColor("#9E968D");
        c.setBorderColor("#26FFFFFF");
        c.setButtonColor("#20FFFFFF");
        c.setReaderBgColor("#22201E");
        c.setReaderTextColor("#DCD6CD");
        c.setUnderlayColor("#22201E");
        c.setUnderlayAlpha(85);
        return c;
    }

    public static ThemeConfig getOledBlack() {
        ThemeConfig c = new ThemeConfig();
        c.setName("OLED Black");
        c.setAppBgColor("#000000");
        c.setCardBgColor("#0D0D0D");
        c.setPanelBgColor("#000000");
        c.setTextColor("#D0D0D0");
        c.setTextSecondaryColor("#777777");
        c.setBorderColor("#1AFFFFFF");
        c.setButtonColor("#18FFFFFF");
        c.setReaderBgColor("#000000");
        c.setReaderTextColor("#D0D0D0");
        c.setUnderlayColor("#000000");
        c.setUnderlayAlpha(100);
        return c;
    }

    // --- JSON SERIALIZATION ---

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("name", name);
            obj.put("appBgColor", appBgColor);
            obj.put("cardBgColor", cardBgColor);
            obj.put("panelBgColor", panelBgColor);
            obj.put("textColor", textColor);
            obj.put("textSecondaryColor", textSecondaryColor);
            obj.put("borderColor", borderColor);
            obj.put("buttonColor", buttonColor);
            obj.put("buttonTextColor", buttonTextColor);
            obj.put("highlightColor", highlightColor);
            obj.put("bookmarkColor", bookmarkColor);
            obj.put("progressColor", progressColor);
            obj.put("readerBgColor", readerBgColor);
            obj.put("readerTextColor", readerTextColor);
            obj.put("dialogBgColor", dialogBgColor);

            obj.put("cardAlpha", cardAlpha);
            obj.put("panelAlpha", panelAlpha);
            obj.put("readerBarsAlpha", readerBarsAlpha);
            obj.put("dialogAlpha", dialogAlpha);
            obj.put("underlayAlpha", underlayAlpha);
            obj.put("underlayColor", underlayColor);
            obj.put("borderWidth", borderWidth);
            obj.put("blurRadius", blurRadius);

            obj.put("appBgType", appBgType);
            obj.put("appBgPath", appBgPath);
            obj.put("appBgScale", appBgScale);
            obj.put("appBgOffsetX", appBgOffsetX);
            obj.put("appBgOffsetY", appBgOffsetY);
            obj.put("appBgBrightness", appBgBrightness);
            obj.put("appBgContrast", appBgContrast);
            obj.put("appBgSaturation", appBgSaturation);
            obj.put("appBgBlur", appBgBlur);
            obj.put("appBgDim", appBgDim);
            obj.put("appBgOpacity", appBgOpacity);
            obj.put("appBgTintColor", appBgTintColor);

            obj.put("readerBgType", readerBgType);
            obj.put("readerBgPath", readerBgPath);
            obj.put("readerBgScale", readerBgScale);
            obj.put("readerBgOffsetX", readerBgOffsetX);
            obj.put("readerBgOffsetY", readerBgOffsetY);
            obj.put("readerBgBrightness", readerBgBrightness);
            obj.put("readerBgContrast", readerBgContrast);
            obj.put("readerBgSaturation", readerBgSaturation);
            obj.put("readerBgBlur", readerBgBlur);
            obj.put("readerBgDim", readerBgDim);
            obj.put("readerBgOpacity", readerBgOpacity);
            obj.put("readerBgTintColor", readerBgTintColor);

            obj.put("gifSpeed", gifSpeed);
            obj.put("gifPaused", gifPaused);
            obj.put("batterySaver", batterySaver);

            obj.put("pagedMode", pagedMode);
            obj.put("pageTurnAnimation", pageTurnAnimation);
            obj.put("tapZoneLeft", tapZoneLeft);
            obj.put("tapZoneRight", tapZoneRight);
            obj.put("swipeSensitivity", swipeSensitivity);

            obj.put("fontFamily", fontFamily);
            obj.put("fontSize", fontSize);
            obj.put("lineSpacing", lineSpacing);
            obj.put("margins", margins);
            obj.put("alignment", alignment);
            obj.put("createdAt", createdAt);
        } catch (Exception ignored) {}
        return obj;
    }

    public static ThemeConfig fromJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) return getObsidian();
        try {
            return fromJsonObject(new JSONObject(jsonString));
        } catch (Exception e) {
            return getObsidian();
        }
    }

    public static ThemeConfig fromJsonObject(JSONObject obj) {
        if (obj == null) return getObsidian();
        ThemeConfig c = new ThemeConfig();
        c.setId(obj.optLong("id", 0));
        c.setName(obj.optString("name", "Custom"));
        c.setAppBgColor(obj.optString("appBgColor", "#0A0A0A"));
        c.setCardBgColor(obj.optString("cardBgColor", "#141414"));
        c.setPanelBgColor(obj.optString("panelBgColor", "#121212"));
        c.setTextColor(obj.optString("textColor", "#F0F0F0"));
        c.setTextSecondaryColor(obj.optString("textSecondaryColor", "#8E8E93"));
        c.setBorderColor(obj.optString("borderColor", "#26FFFFFF"));
        c.setButtonColor(obj.optString("buttonColor", "#20FFFFFF"));
        c.setButtonTextColor(obj.optString("buttonTextColor", "#FFFFFF"));
        c.setHighlightColor(obj.optString("highlightColor", "#40FFFFFF"));
        c.setBookmarkColor(obj.optString("bookmarkColor", "#E5A93C"));
        c.setProgressColor(obj.optString("progressColor", "#FFFFFF"));
        c.setReaderBgColor(obj.optString("readerBgColor", "#0A0A0A"));
        c.setReaderTextColor(obj.optString("readerTextColor", "#E6E6E6"));
        c.setDialogBgColor(obj.optString("dialogBgColor", "#1A1A1A"));

        c.setCardAlpha(obj.optInt("cardAlpha", 85));
        c.setPanelAlpha(obj.optInt("panelAlpha", 90));
        c.setReaderBarsAlpha(obj.optInt("readerBarsAlpha", 90));
        c.setDialogAlpha(obj.optInt("dialogAlpha", 95));
        c.setUnderlayAlpha(obj.optInt("underlayAlpha", 85));
        c.setUnderlayColor(obj.optString("underlayColor", "#0A0A0A"));
        c.setBorderWidth(obj.optInt("borderWidth", 1));
        c.setBlurRadius(obj.optInt("blurRadius", 12));

        c.setAppBgType(obj.optInt("appBgType", BG_COLOR));
        c.setAppBgPath(obj.optString("appBgPath", null));
        c.setAppBgScale(obj.optInt("appBgScale", SCALE_FILL));
        c.setAppBgOffsetX((float) obj.optDouble("appBgOffsetX", 0.0));
        c.setAppBgOffsetY((float) obj.optDouble("appBgOffsetY", 0.0));
        c.setAppBgBrightness((float) obj.optDouble("appBgBrightness", 0.0));
        c.setAppBgContrast((float) obj.optDouble("appBgContrast", 1.0));
        c.setAppBgSaturation((float) obj.optDouble("appBgSaturation", 1.0));
        c.setAppBgBlur(obj.optInt("appBgBlur", 0));
        c.setAppBgDim((float) obj.optDouble("appBgDim", 0.2));
        c.setAppBgOpacity((float) obj.optDouble("appBgOpacity", 1.0));
        c.setAppBgTintColor(obj.optString("appBgTintColor", "#00000000"));

        c.setReaderBgType(obj.optInt("readerBgType", BG_COLOR));
        c.setReaderBgPath(obj.optString("readerBgPath", null));
        c.setReaderBgScale(obj.optInt("readerBgScale", SCALE_FILL));
        c.setReaderBgOffsetX((float) obj.optDouble("readerBgOffsetX", 0.0));
        c.setReaderBgOffsetY((float) obj.optDouble("readerBgOffsetY", 0.0));
        c.setReaderBgBrightness((float) obj.optDouble("readerBgBrightness", 0.0));
        c.setReaderBgContrast((float) obj.optDouble("readerBgContrast", 1.0));
        c.setReaderBgSaturation((float) obj.optDouble("readerBgSaturation", 1.0));
        c.setReaderBgBlur(obj.optInt("readerBgBlur", 0));
        c.setReaderBgDim((float) obj.optDouble("readerBgDim", 0.35));
        c.setReaderBgOpacity((float) obj.optDouble("readerBgOpacity", 1.0));
        c.setReaderBgTintColor(obj.optString("readerBgTintColor", "#00000000"));

        c.setGifSpeed((float) obj.optDouble("gifSpeed", 1.0));
        c.setGifPaused(obj.optBoolean("gifPaused", false));
        c.setBatterySaver(obj.optBoolean("batterySaver", false));

        c.setPagedMode(obj.optBoolean("pagedMode", true));
        c.setPageTurnAnimation(obj.optBoolean("pageTurnAnimation", true));
        c.setTapZoneLeft((float) obj.optDouble("tapZoneLeft", 0.25));
        c.setTapZoneRight((float) obj.optDouble("tapZoneRight", 0.75));
        c.setSwipeSensitivity(obj.optInt("swipeSensitivity", 40));

        c.setFontFamily(obj.optInt("fontFamily", ReaderSettings.FONT_SERIF));
        c.setFontSize(obj.optInt("fontSize", 18));
        c.setLineSpacing(obj.optInt("lineSpacing", ReaderSettings.SPACING_NORMAL));
        c.setMargins(obj.optInt("margins", ReaderSettings.MARGIN_NORMAL));
        c.setAlignment(obj.optInt("alignment", ReaderSettings.ALIGN_JUSTIFY));
        c.setCreatedAt(obj.optLong("createdAt", System.currentTimeMillis()));

        return c;
    }

    public static int parseColorSafe(String hex, int defaultColor) {
        if (hex == null || hex.trim().isEmpty()) return defaultColor;
        try {
            String clean = hex.trim();
            if (!clean.startsWith("#")) clean = "#" + clean;
            return Color.parseColor(clean);
        } catch (Exception e) {
            return defaultColor;
        }
    }

    // --- GETTERS & SETTERS ---

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name != null ? name : "Тема"; }
    public void setName(String name) { this.name = name; }

    public String getAppBgColor() { return appBgColor; }
    public void setAppBgColor(String appBgColor) { this.appBgColor = appBgColor; }

    public String getCardBgColor() { return cardBgColor; }
    public void setCardBgColor(String cardBgColor) { this.cardBgColor = cardBgColor; }

    public String getPanelBgColor() { return panelBgColor; }
    public void setPanelBgColor(String panelBgColor) { this.panelBgColor = panelBgColor; }

    public String getTextColor() { return textColor; }
    public void setTextColor(String textColor) { this.textColor = textColor; }

    public String getTextSecondaryColor() { return textSecondaryColor; }
    public void setTextSecondaryColor(String textSecondaryColor) { this.textSecondaryColor = textSecondaryColor; }

    public String getBorderColor() { return borderColor; }
    public void setBorderColor(String borderColor) { this.borderColor = borderColor; }

    public String getButtonColor() { return buttonColor; }
    public void setButtonColor(String buttonColor) { this.buttonColor = buttonColor; }

    public String getButtonTextColor() { return buttonTextColor; }
    public void setButtonTextColor(String buttonTextColor) { this.buttonTextColor = buttonTextColor; }

    public String getHighlightColor() { return highlightColor; }
    public void setHighlightColor(String highlightColor) { this.highlightColor = highlightColor; }

    public String getBookmarkColor() { return bookmarkColor; }
    public void setBookmarkColor(String bookmarkColor) { this.bookmarkColor = bookmarkColor; }

    public String getProgressColor() { return progressColor; }
    public void setProgressColor(String progressColor) { this.progressColor = progressColor; }

    public String getReaderBgColor() { return readerBgColor; }
    public void setReaderBgColor(String readerBgColor) { this.readerBgColor = readerBgColor; }

    public String getReaderTextColor() { return readerTextColor; }
    public void setReaderTextColor(String readerTextColor) { this.readerTextColor = readerTextColor; }

    public String getDialogBgColor() { return dialogBgColor; }
    public void setDialogBgColor(String dialogBgColor) { this.dialogBgColor = dialogBgColor; }

    public int getCardAlpha() { return cardAlpha; }
    public void setCardAlpha(int cardAlpha) { this.cardAlpha = Math.max(0, Math.min(100, cardAlpha)); }

    public int getPanelAlpha() { return panelAlpha; }
    public void setPanelAlpha(int panelAlpha) { this.panelAlpha = Math.max(0, Math.min(100, panelAlpha)); }

    public int getReaderBarsAlpha() { return readerBarsAlpha; }
    public void setReaderBarsAlpha(int readerBarsAlpha) { this.readerBarsAlpha = Math.max(0, Math.min(100, readerBarsAlpha)); }

    public int getDialogAlpha() { return dialogAlpha; }
    public void setDialogAlpha(int dialogAlpha) { this.dialogAlpha = Math.max(0, Math.min(100, dialogAlpha)); }

    public int getUnderlayAlpha() { return underlayAlpha; }
    public void setUnderlayAlpha(int underlayAlpha) { this.underlayAlpha = Math.max(0, Math.min(100, underlayAlpha)); }

    public String getUnderlayColor() { return underlayColor != null ? underlayColor : "#0A0A0A"; }
    public void setUnderlayColor(String underlayColor) { this.underlayColor = underlayColor; }

    public int getBorderWidth() { return borderWidth; }
    public void setBorderWidth(int borderWidth) { this.borderWidth = Math.max(0, Math.min(6, borderWidth)); }

    public int getBlurRadius() { return blurRadius; }
    public void setBlurRadius(int blurRadius) { this.blurRadius = Math.max(0, Math.min(25, blurRadius)); }

    public int getAppBgType() { return appBgType; }
    public void setAppBgType(int appBgType) { this.appBgType = appBgType; }

    public String getAppBgPath() { return appBgPath; }
    public void setAppBgPath(String appBgPath) { this.appBgPath = appBgPath; }

    public int getAppBgScale() { return appBgScale; }
    public void setAppBgScale(int appBgScale) { this.appBgScale = appBgScale; }

    public float getAppBgOffsetX() { return appBgOffsetX; }
    public void setAppBgOffsetX(float appBgOffsetX) { this.appBgOffsetX = appBgOffsetX; }

    public float getAppBgOffsetY() { return appBgOffsetY; }
    public void setAppBgOffsetY(float appBgOffsetY) { this.appBgOffsetY = appBgOffsetY; }

    public float getAppBgBrightness() { return appBgBrightness; }
    public void setAppBgBrightness(float appBgBrightness) { this.appBgBrightness = Math.max(-1f, Math.min(1f, appBgBrightness)); }

    public float getAppBgContrast() { return appBgContrast; }
    public void setAppBgContrast(float appBgContrast) { this.appBgContrast = Math.max(0f, Math.min(3f, appBgContrast)); }

    public float getAppBgSaturation() { return appBgSaturation; }
    public void setAppBgSaturation(float appBgSaturation) { this.appBgSaturation = Math.max(0f, Math.min(3f, appBgSaturation)); }

    public int getAppBgBlur() { return appBgBlur; }
    public void setAppBgBlur(int appBgBlur) { this.appBgBlur = Math.max(0, Math.min(25, appBgBlur)); }

    public float getAppBgDim() { return appBgDim; }
    public void setAppBgDim(float appBgDim) { this.appBgDim = Math.max(0f, Math.min(1f, appBgDim)); }

    public float getAppBgOpacity() { return appBgOpacity; }
    public void setAppBgOpacity(float appBgOpacity) { this.appBgOpacity = Math.max(0f, Math.min(1f, appBgOpacity)); }

    public String getAppBgTintColor() { return appBgTintColor != null ? appBgTintColor : "#00000000"; }
    public void setAppBgTintColor(String appBgTintColor) { this.appBgTintColor = appBgTintColor; }

    public int getReaderBgType() { return readerBgType; }
    public void setReaderBgType(int readerBgType) { this.readerBgType = readerBgType; }

    public String getReaderBgPath() { return readerBgPath; }
    public void setReaderBgPath(String readerBgPath) { this.readerBgPath = readerBgPath; }

    public int getReaderBgScale() { return readerBgScale; }
    public void setReaderBgScale(int readerBgScale) { this.readerBgScale = readerBgScale; }

    public float getReaderBgOffsetX() { return readerBgOffsetX; }
    public void setReaderBgOffsetX(float readerBgOffsetX) { this.readerBgOffsetX = readerBgOffsetX; }

    public float getReaderBgOffsetY() { return readerBgOffsetY; }
    public void setReaderBgOffsetY(float readerBgOffsetY) { this.readerBgOffsetY = readerBgOffsetY; }

    public float getReaderBgBrightness() { return readerBgBrightness; }
    public void setReaderBgBrightness(float readerBgBrightness) { this.readerBgBrightness = Math.max(-1f, Math.min(1f, readerBgBrightness)); }

    public float getReaderBgContrast() { return readerBgContrast; }
    public void setReaderBgContrast(float readerBgContrast) { this.readerBgContrast = Math.max(0f, Math.min(3f, readerBgContrast)); }

    public float getReaderBgSaturation() { return readerBgSaturation; }
    public void setReaderBgSaturation(float readerBgSaturation) { this.readerBgSaturation = Math.max(0f, Math.min(3f, readerBgSaturation)); }

    public int getReaderBgBlur() { return readerBgBlur; }
    public void setReaderBgBlur(int readerBgBlur) { this.readerBgBlur = Math.max(0, Math.min(25, readerBgBlur)); }

    public float getReaderBgDim() { return readerBgDim; }
    public void setReaderBgDim(float readerBgDim) { this.readerBgDim = Math.max(0f, Math.min(1f, readerBgDim)); }

    public float getReaderBgOpacity() { return readerBgOpacity; }
    public void setReaderBgOpacity(float readerBgOpacity) { this.readerBgOpacity = Math.max(0f, Math.min(1f, readerBgOpacity)); }

    public String getReaderBgTintColor() { return readerBgTintColor != null ? readerBgTintColor : "#00000000"; }
    public void setReaderBgTintColor(String readerBgTintColor) { this.readerBgTintColor = readerBgTintColor; }

    public float getGifSpeed() { return gifSpeed; }
    public void setGifSpeed(float gifSpeed) { this.gifSpeed = Math.max(0.25f, Math.min(3.0f, gifSpeed)); }

    public boolean isGifPaused() { return gifPaused; }
    public void setGifPaused(boolean gifPaused) { this.gifPaused = gifPaused; }

    public boolean isBatterySaver() { return batterySaver; }
    public void setBatterySaver(boolean batterySaver) { this.batterySaver = batterySaver; }

    public boolean isPagedMode() { return pagedMode; }
    public void setPagedMode(boolean pagedMode) { this.pagedMode = pagedMode; }

    public boolean isPageTurnAnimation() { return pageTurnAnimation; }
    public void setPageTurnAnimation(boolean pageTurnAnimation) { this.pageTurnAnimation = pageTurnAnimation; }

    public float getTapZoneLeft() { return tapZoneLeft; }
    public void setTapZoneLeft(float tapZoneLeft) { this.tapZoneLeft = tapZoneLeft; }

    public float getTapZoneRight() { return tapZoneRight; }
    public void setTapZoneRight(float tapZoneRight) { this.tapZoneRight = tapZoneRight; }

    public int getSwipeSensitivity() { return swipeSensitivity; }
    public void setSwipeSensitivity(int swipeSensitivity) { this.swipeSensitivity = swipeSensitivity; }

    public int getFontFamily() { return fontFamily; }
    public void setFontFamily(int fontFamily) { this.fontFamily = fontFamily; }

    public int getFontSize() { return fontSize; }
    public void setFontSize(int fontSize) { this.fontSize = fontSize; }

    public int getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(int lineSpacing) { this.lineSpacing = lineSpacing; }

    public int getMargins() { return margins; }
    public void setMargins(int margins) { this.margins = margins; }

    public int getAlignment() { return alignment; }
    public void setAlignment(int alignment) { this.alignment = alignment; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
