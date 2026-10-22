package com.bookr.reader.parser;

import com.bookr.reader.data.model.ReaderSettings;
import com.bookr.reader.data.model.TocItem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Fb2Book implements Serializable {
    private Fb2Metadata metadata = new Fb2Metadata();
    private List<Fb2Section> sections = new ArrayList<>();
    private List<TocItem> toc = new ArrayList<>();
    private Map<String, String> footnotes = new HashMap<>();
    private Map<String, String> images = new HashMap<>();

    public Fb2Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Fb2Metadata metadata) {
        this.metadata = metadata;
    }

    public List<Fb2Section> getSections() {
        return sections;
    }

    public void addSection(Fb2Section section) {
        this.sections.add(section);
    }

    public List<TocItem> getToc() {
        return toc;
    }

    public void setToc(List<TocItem> toc) {
        this.toc = toc;
    }

    public Map<String, String> getFootnotes() {
        return footnotes;
    }

    public void addFootnote(String id, String text) {
        this.footnotes.put(id, text);
    }

    public Map<String, String> getImages() {
        return images;
    }

    public void addImage(String id, String dataUri) {
        this.images.put(id, dataUri);
    }

    public String getFootnote(String id) {
        if (id == null) return null;
        if (id.startsWith("#")) id = id.substring(1);
        return footnotes.get(id);
    }

    /**
     * Builds complete HTML for display in WebView with our monochrome glassmorphism style.
     */
    /**
     * Builds complete HTML for display in WebView with our monochrome glassmorphism style.
     */
    public String generateHtml(ReaderSettings settings) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no, maximum-scale=1.0\">");
        sb.append("<style id=\"bookr-style\">");
        sb.append(generateCss(settings));
        sb.append("</style>");
        sb.append("</head><body>");

        // Glassmorphism text underlay / substrate
        sb.append("<div id=\"book-underlay\"></div>");

        // Main reading container
        sb.append("<div id=\"book-wrapper\"><div id=\"book-content\">");

        // Book title header (styled like front page)
        sb.append("<div class=\"book-cover-header\">");
        if (metadata.getTitle() != null && !metadata.getTitle().isEmpty()) {
            sb.append("<h1 class=\"book-main-title\">").append(escapeHtml(metadata.getTitle())).append("</h1>");
        }
        if (metadata.getAuthor() != null && !metadata.getAuthor().isEmpty()) {
            sb.append("<div class=\"book-main-author\">").append(escapeHtml(metadata.getAuthor())).append("</div>");
        }
        if (metadata.getAnnotation() != null && !metadata.getAnnotation().isEmpty()) {
            sb.append("<div class=\"book-annotation\">").append(metadata.getAnnotation()).append("</div>");
        }
        sb.append("</div><div class=\"book-divider\"></div>");

        // Sections / Chapters
        int chapterIdx = 0;
        for (Fb2Section sec : sections) {
            renderSection(sb, sec, chapterIdx++);
        }

        if (!settings.isPagedMode()) {
            // Space at bottom for comfortable scrolling only in scroll mode
            sb.append("<div style=\"height: 40vh;\"></div>");
        }

        sb.append("</div></div>");

        // Client JavaScript for pagination engine, gestures, and tap zones
        sb.append("<script>");
        sb.append(generateJs(settings));
        sb.append("</script>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private void renderSection(StringBuilder sb, Fb2Section sec, int chapterIdx) {
        String anchorId = "chapter_" + chapterIdx;
        sb.append("<section id=\"").append(anchorId).append("\" class=\"book-section level-").append(sec.getLevel()).append("\">");

        if (sec.getTitle() != null && !sec.getTitle().trim().isEmpty()) {
            sb.append("<h2 class=\"chapter-heading\">").append(escapeHtml(sec.getTitle())).append("</h2>");
        }

        if (sec.getEpigraph() != null && !sec.getEpigraph().trim().isEmpty()) {
            sb.append("<div class=\"epigraph\">");
            sb.append("<div class=\"epigraph-text\">").append(sec.getEpigraph()).append("</div>");
            if (sec.getEpigraphAuthor() != null && !sec.getEpigraphAuthor().trim().isEmpty()) {
                sb.append("<div class=\"epigraph-author\">— ").append(escapeHtml(sec.getEpigraphAuthor())).append("</div>");
            }
            sb.append("</div>");
        }

        sb.append(sec.getContent());

        for (Fb2Section sub : sec.getSubSections()) {
            renderSection(sb, sub, ++chapterIdx);
        }

        sb.append("</section>");
    }

    public static String generateCss(ReaderSettings settings) {
        String text = settings.getCssTextColor();
        String font = settings.getCssFontFamily();
        String lh = settings.getCssLineHeight();
        String align = settings.getCssTextAlign();
        int fs = settings.getFontSize();
        boolean paged = settings.isPagedMode();

        String underlayColor = settings.getUnderlayColor();
        float underlayOpacity = settings.getUnderlayOpacity();

        int marginX = 24;
        int marginTop = 24;
        int marginBottom = 28;
        switch (settings.getMargins()) {
            case ReaderSettings.MARGIN_NARROW:
                marginX = 16;
                marginTop = 16;
                marginBottom = 20;
                break;
            case ReaderSettings.MARGIN_WIDE:
                marginX = 36;
                marginTop = 36;
                marginBottom = 40;
                break;
            case ReaderSettings.MARGIN_NORMAL:
            default:
                marginX = 24;
                marginTop = 24;
                marginBottom = 28;
                break;
        }

        StringBuilder sb = new StringBuilder(2048);
        sb.append("* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }\n");

        if (paged) {
            sb.append("html, body {\n")
              .append("    margin: 0; padding: 0;\n")
              .append("    width: 100vw; height: 100vh; max-height: 100vh;\n")
              .append("    overflow: hidden;\n")
              .append("    background: transparent !important;\n")
              .append("    -webkit-user-select: text; user-select: text;\n")
              .append("}\n")
              .append("#book-underlay {\n")
              .append("    position: fixed; top: 0; left: 0; right: 0; bottom: 0;\n")
              .append("    background-color: ").append(underlayColor).append(";\n")
              .append("    opacity: ").append(underlayOpacity).append(";\n")
              .append("    pointer-events: none; z-index: -1;\n")
              .append("}\n")
              .append("#book-wrapper {\n")
              .append("    position: absolute; top: 0; left: 0;\n")
              .append("    width: 100vw; height: 100vh;\n")
              .append("    overflow-x: hidden; overflow-y: hidden;\n")
              .append("}\n")
              .append("#book-content {\n")
              .append("    width: calc(100vw - ").append(2 * marginX).append("px);\n")
              .append("    height: calc(100vh - ").append(marginTop + marginBottom).append("px);\n")
              .append("    margin: ").append(marginTop).append("px ").append(marginX).append("px ").append(marginBottom).append("px ").append(marginX).append("px;\n")
              .append("    column-width: calc(100vw - ").append(2 * marginX).append("px);\n")
              .append("    column-gap: ").append(2 * marginX).append("px;\n")
              .append("    column-fill: auto;\n")
              .append("    color: ").append(text).append(";\n")
              .append("    font-family: ").append(font).append(";\n")
              .append("    font-size: ").append(fs).append("px;\n")
              .append("    line-height: ").append(lh).append(";\n")
              .append("    text-align: ").append(align).append(";\n")
              .append("    word-wrap: break-word; overflow-wrap: break-word;\n")
              .append("    hyphens: auto; -webkit-hyphens: auto;\n")
              .append("}\n")
              .append(".book-cover-header {\n")
              .append("    text-align: center; margin-bottom: 2em; padding-top: 1em;\n")
              .append("    break-after: page; page-break-after: always;\n")
              .append("}\n")
              .append(".chapter-heading {\n")
              .append("    break-before: page; page-break-before: always;\n")
              .append("    break-after: avoid; page-break-after: avoid;\n")
              .append("    font-size: ").append((int)(fs * 1.3)).append("px;\n")
              .append("    font-weight: 600; text-align: center;\n")
              .append("    margin: 0.5em 0 1em 0; line-height: 1.3;\n")
              .append("}\n")
              .append("img.book-image {\n")
              .append("    display: block; max-width: 100%;\n")
              .append("    max-height: calc(100vh - ").append(marginTop + marginBottom + 40).append("px);\n")
              .append("    object-fit: contain; margin: 1em auto; border-radius: 8px;\n")
              .append("    break-inside: avoid; page-break-inside: avoid;\n")
              .append("}\n");
        } else {
            sb.append("html, body {\n")
              .append("    margin: 0; padding: 0;\n")
              .append("    width: 100%; min-height: 100%;\n")
              .append("    overflow-x: hidden; overflow-y: auto;\n")
              .append("    background: transparent !important;\n")
              .append("    -webkit-user-select: text; user-select: text;\n")
              .append("}\n")
              .append("#book-underlay {\n")
              .append("    position: fixed; top: 0; left: 0; right: 0; bottom: 0;\n")
              .append("    background-color: ").append(underlayColor).append(";\n")
              .append("    opacity: ").append(underlayOpacity).append(";\n")
              .append("    pointer-events: none; z-index: -1;\n")
              .append("}\n")
              .append("#book-wrapper {\n")
              .append("    width: 100%; overflow: visible;\n")
              .append("}\n")
              .append("#book-content {\n")
              .append("    width: 100%;\n")
              .append("    padding: ").append(marginTop).append("px ").append(marginX).append("px ").append(marginBottom + 60).append("px ").append(marginX).append("px;\n")
              .append("    color: ").append(text).append(";\n")
              .append("    font-family: ").append(font).append(";\n")
              .append("    font-size: ").append(fs).append("px;\n")
              .append("    line-height: ").append(lh).append(";\n")
              .append("    text-align: ").append(align).append(";\n")
              .append("    word-wrap: break-word; overflow-wrap: break-word;\n")
              .append("    hyphens: auto; -webkit-hyphens: auto;\n")
              .append("}\n")
              .append(".book-cover-header {\n")
              .append("    text-align: center; margin-bottom: 2.5em; padding: 2em 0 1em 0;\n")
              .append("}\n")
              .append(".chapter-heading {\n")
              .append("    font-size: ").append((int)(fs * 1.3)).append("px;\n")
              .append("    font-weight: 600; text-align: center;\n")
              .append("    margin: 2em 0 1em 0; line-height: 1.3;\n")
              .append("}\n")
              .append("img.book-image {\n")
              .append("    display: block; max-width: 95%; height: auto;\n")
              .append("    margin: 1.5em auto; border-radius: 8px;\n")
              .append("}\n");
        }

        // Shared typographic styles
        sb.append("p {\n")
          .append("    margin: 0 0 0.85em 0;\n")
          .append("    text-indent: 1.5em;\n")
          .append("    orphans: 2; widows: 2;\n")
          .append("    break-inside: auto;\n")
          .append("}\n")
          .append(".book-main-title {\n")
          .append("    font-size: ").append((int)(fs * 1.5)).append("px;\n")
          .append("    font-weight: 700; letter-spacing: 0.5px;\n")
          .append("    margin: 0 0 0.5em 0; line-height: 1.25;\n")
          .append("}\n")
          .append(".book-main-author {\n")
          .append("    font-size: ").append((int)(fs * 1.1)).append("px;\n")
          .append("    color: #8E8E93; font-style: italic;\n")
          .append("    margin-bottom: 1.2em;\n")
          .append("}\n")
          .append(".book-annotation {\n")
          .append("    font-size: ").append((int)(fs * 0.9)).append("px;\n")
          .append("    font-style: italic; background: rgba(255,255,255,0.04);\n")
          .append("    border-left: 2px solid rgba(255,255,255,0.2);\n")
          .append("    padding: 12px 16px; margin: 1.5em auto;\n")
          .append("    text-align: left; border-radius: 4px;\n")
          .append("    break-inside: avoid; page-break-inside: avoid;\n")
          .append("}\n")
          .append(".book-divider {\n")
          .append("    height: 1px; background: rgba(255,255,255,0.12);\n")
          .append("    margin: 1.5em 0;\n")
          .append("}\n")
          .append(".epigraph {\n")
          .append("    margin: 1.5em 0 1.8em 25%; font-style: italic;\n")
          .append("    font-size: ").append((int)(fs * 0.92)).append("px;\n")
          .append("    color: #A0A0A0;\n")
          .append("    break-inside: avoid; page-break-inside: avoid;\n")
          .append("}\n")
          .append(".epigraph-text { text-indent: 0; }\n")
          .append(".epigraph-author {\n")
          .append("    text-align: right; font-size: ").append((int)(fs * 0.85)).append("px;\n")
          .append("    margin-top: 0.5em;\n")
          .append("}\n")
          .append("blockquote, .cite {\n")
          .append("    margin: 1.2em 0; padding-left: 1.2em;\n")
          .append("    border-left: 2px solid rgba(255,255,255,0.2); font-style: italic;\n")
          .append("    break-inside: avoid; page-break-inside: avoid;\n")
          .append("}\n")
          .append(".poem {\n")
          .append("    margin: 1.2em 0 1.2em 15%; font-style: italic;\n")
          .append("    break-inside: avoid; page-break-inside: avoid;\n")
          .append("}\n")
          .append(".poem .v { text-indent: 0; margin-bottom: 0.2em; }\n")
          .append(".poem .stanza { margin-bottom: 0.8em; }\n")
          .append(".footnote-link {\n")
          .append("    display: inline-block; font-size: 0.75em; vertical-align: super;\n")
          .append("    color: #8E8E93; background: rgba(255,255,255,0.08);\n")
          .append("    border: 1px solid rgba(255,255,255,0.15);\n")
          .append("    border-radius: 4px; padding: 1px 4px; margin: 0 2px;\n")
          .append("    text-decoration: none; font-weight: 500; cursor: pointer;\n")
          .append("}\n");

        return sb.toString();
    }

    public static String generateJs(ReaderSettings settings) {
        boolean isPaged = settings.isPagedMode();
        boolean anim = settings.getActiveTheme() != null && settings.getActiveTheme().isPageTurnAnimation();
        float leftZone = settings.getActiveTheme() != null ? settings.getActiveTheme().getTapZoneLeft() : 0.25f;
        float rightZone = settings.getActiveTheme() != null ? settings.getActiveTheme().getTapZoneRight() : 0.75f;
        int swipeSens = settings.getActiveTheme() != null ? settings.getActiveTheme().getSwipeSensitivity() : 40;

        StringBuilder sb = new StringBuilder(4096);
        sb.append("var isPaged = ").append(isPaged).append(";\n")
          .append("var animEnabled = ").append(anim).append(";\n")
          .append("var tapZoneLeft = ").append(leftZone).append(";\n")
          .append("var tapZoneRight = ").append(rightZone).append(";\n")
          .append("var swipeSensitivity = ").append(swipeSens).append(";\n")
          .append("var currentPage = 0;\n")
          .append("var totalPages = 1;\n")
          .append("\n")
          .append("function getPageWidth() {\n")
          .append("    return window.innerWidth || document.documentElement.clientWidth || 360;\n")
          .append("}\n")
          .append("\n")
          .append("function calculateTotalPages() {\n")
          .append("    var content = document.getElementById('book-content');\n")
          .append("    if (!content) return 1;\n")
          .append("    var pw = getPageWidth();\n")
          .append("    if (pw <= 0) return 1;\n")
          .append("    var totalW = content.scrollWidth;\n")
          .append("    var pages = Math.ceil((totalW - 2) / pw);\n")
          .append("    return Math.max(1, pages);\n")
          .append("}\n")
          .append("\n")
          .append("function updatePagination(report) {\n")
          .append("    if (!isPaged) return;\n")
          .append("    totalPages = calculateTotalPages();\n")
          .append("    if (currentPage >= totalPages) currentPage = totalPages - 1;\n")
          .append("    if (currentPage < 0) currentPage = 0;\n")
          .append("    if (report !== false) notifyPage();\n")
          .append("}\n")
          .append("\n")
          .append("function notifyPage() {\n")
          .append("    if (window.BookRBridge) {\n")
          .append("        var pct = totalPages > 1 ? (currentPage / (totalPages - 1)) * 100 : 0;\n")
          .append("        window.BookRBridge.onPageChanged(currentPage + 1, totalPages, Math.min(100, Math.max(0, pct)));\n")
          .append("    }\n")
          .append("}\n")
          .append("\n")
          .append("function goToPage(pageIndex, animate) {\n")
          .append("    if (!isPaged) return;\n")
          .append("    totalPages = calculateTotalPages();\n")
          .append("    if (pageIndex < 0) pageIndex = 0;\n")
          .append("    if (pageIndex >= totalPages) pageIndex = totalPages - 1;\n")
          .append("    currentPage = pageIndex;\n")
          .append("    var wrapper = document.getElementById('book-wrapper');\n")
          .append("    if (!wrapper) return;\n")
          .append("    var targetX = currentPage * getPageWidth();\n")
          .append("    var shouldAnimate = (animate !== false) && animEnabled;\n")
          .append("    if (shouldAnimate) {\n")
          .append("        wrapper.scrollTo({ left: targetX, top: 0, behavior: 'smooth' });\n")
          .append("    } else {\n")
          .append("        wrapper.scrollLeft = targetX;\n")
          .append("    }\n")
          .append("    notifyPage();\n")
          .append("}\n")
          .append("\n")
          .append("function nextPage(animate) {\n")
          .append("    if (currentPage < totalPages - 1) {\n")
          .append("        goToPage(currentPage + 1, animate);\n")
          .append("    }\n")
          .append("}\n")
          .append("\n")
          .append("function prevPage(animate) {\n")
          .append("    if (currentPage > 0) {\n")
          .append("        goToPage(currentPage - 1, animate);\n")
          .append("    }\n")
          .append("}\n")
          .append("\n")
          .append("function scrollToPercent(pct) {\n")
          .append("    if (isPaged) {\n")
          .append("        totalPages = calculateTotalPages();\n")
          .append("        var p = Math.min(totalPages - 1, Math.max(0, Math.round((pct / 100) * (totalPages - 1))));\n")
          .append("        goToPage(p, false);\n")
          .append("    } else {\n")
          .append("        var maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);\n")
          .append("        window.scrollTo({ top: (pct / 100) * maxScroll, behavior: 'auto' });\n")
          .append("    }\n")
          .append("}\n")
          .append("\n")
          .append("function scrollToAnchor(id) {\n")
          .append("    var el = document.getElementById(id);\n")
          .append("    if (!el) return;\n")
          .append("    if (isPaged) {\n")
          .append("        var wrapper = document.getElementById('book-wrapper');\n")
          .append("        var curScroll = wrapper ? wrapper.scrollLeft : 0;\n")
          .append("        var rect = el.getBoundingClientRect();\n")
          .append("        var elLeft = rect.left + curScroll;\n")
          .append("        var p = Math.floor(elLeft / getPageWidth());\n")
          .append("        goToPage(p, true);\n")
          .append("    } else {\n")
          .append("        el.scrollIntoView({ behavior: 'smooth', block: 'start' });\n")
          .append("    }\n")
          .append("}\n")
          .append("\n")
          .append("function updateStyle(newCss) {\n")
          .append("    var prevPct = totalPages > 1 ? currentPage / (totalPages - 1) : 0;\n")
          .append("    var styleEl = document.getElementById('bookr-style');\n")
          .append("    if (styleEl) styleEl.innerHTML = newCss;\n")
          .append("    setTimeout(function() {\n")
          .append("        totalPages = calculateTotalPages();\n")
          .append("        if (isPaged) {\n")
          .append("            var newP = Math.min(totalPages - 1, Math.max(0, Math.round(prevPct * (totalPages - 1))));\n")
          .append("            goToPage(newP, false);\n")
          .append("        }\n")
          .append("    }, 150);\n")
          .append("}\n")
          .append("\n")
          .append("// Gestures and Tap Zones\n")
          .append("var touchStartX = 0;\n")
          .append("var touchStartY = 0;\n")
          .append("var touchStartTime = 0;\n")
          .append("\n")
          .append("document.addEventListener('touchstart', function(e) {\n")
          .append("    if (e.touches && e.touches.length === 1) {\n")
          .append("        touchStartX = e.touches[0].clientX;\n")
          .append("        touchStartY = e.touches[0].clientY;\n")
          .append("        touchStartTime = Date.now();\n")
          .append("    }\n")
          .append("}, { passive: true });\n")
          .append("\n")
          .append("document.addEventListener('touchend', function(e) {\n")
          .append("    if (!e.changedTouches || e.changedTouches.length === 0) return;\n")
          .append("    var sel = window.getSelection ? window.getSelection().toString() : '';\n")
          .append("    if (sel && sel.trim().length > 0) return;\n")
          .append("    \n")
          .append("    var touchEndX = e.changedTouches[0].clientX;\n")
          .append("    var touchEndY = e.changedTouches[0].clientY;\n")
          .append("    var touchDuration = Date.now() - touchStartTime;\n")
          .append("    var dx = touchEndX - touchStartX;\n")
          .append("    var dy = touchEndY - touchStartY;\n")
          .append("    var absDx = Math.abs(dx);\n")
          .append("    var absDy = Math.abs(dy);\n")
          .append("    \n")
          .append("    // Horizontal swipe in paged mode\n")
          .append("    if (isPaged && absDx > swipeSensitivity && absDx > absDy * 1.3 && touchDuration < 800) {\n")
          .append("        if (dx < 0) {\n")
          .append("            nextPage(true);\n")
          .append("        } else {\n")
          .append("            prevPage(true);\n")
          .append("        }\n")
          .append("        return;\n")
          .append("    }\n")
          .append("    \n")
          .append("    // Tap gesture\n")
          .append("    if (absDx < 15 && absDy < 15 && touchDuration < 350) {\n")
          .append("        var target = e.target.closest('.footnote-link');\n")
          .append("        if (target) {\n")
          .append("            var noteId = target.getAttribute('data-note-id');\n")
          .append("            if (window.BookRBridge && noteId) {\n")
          .append("                window.BookRBridge.onFootnoteClicked(noteId);\n")
          .append("            }\n")
          .append("            return;\n")
          .append("        }\n")
          .append("        var x = touchEndX;\n")
          .append("        var w = window.innerWidth;\n")
          .append("        if (x < w * tapZoneLeft) {\n")
          .append("            if (isPaged) prevPage(true);\n")
          .append("            else if (window.BookRBridge) window.BookRBridge.onTapLeft();\n")
          .append("        } else if (x > w * tapZoneRight) {\n")
          .append("            if (isPaged) nextPage(true);\n")
          .append("            else if (window.BookRBridge) window.BookRBridge.onTapRight();\n")
          .append("        } else {\n")
          .append("            if (window.BookRBridge) window.BookRBridge.onTapCenter();\n")
          .append("        }\n")
          .append("    }\n")
          .append("}, { passive: false });\n")
          .append("\n")
          .append("// Scroll mode position reporting\n")
          .append("window.addEventListener('scroll', function() {\n")
          .append("    if (!isPaged && window.BookRBridge) {\n")
          .append("        var maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);\n")
          .append("        var cur = window.scrollY || window.pageYOffset || 0;\n")
          .append("        var pct = Math.min(100, Math.max(0, (cur / maxScroll) * 100));\n")
          .append("        window.BookRBridge.onScrollPosition(Math.round(cur), pct);\n")
          .append("    }\n")
          .append("});\n")
          .append("\n")
          .append("// Orientation / window resize listener\n")
          .append("window.addEventListener('resize', function() {\n")
          .append("    var prevPct = totalPages > 1 ? currentPage / (totalPages - 1) : 0;\n")
          .append("    setTimeout(function() {\n")
          .append("        totalPages = calculateTotalPages();\n")
          .append("        if (isPaged) {\n")
          .append("            var p = Math.min(totalPages - 1, Math.max(0, Math.round(prevPct * (totalPages - 1))));\n")
          .append("            goToPage(p, false);\n")
          .append("        }\n")
          .append("    }, 150);\n")
          .append("});\n")
          .append("\n")
          .append("window.addEventListener('DOMContentLoaded', function() { updatePagination(true); });\n")
          .append("window.addEventListener('load', function() { updatePagination(true); });\n");

        return sb.toString();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
