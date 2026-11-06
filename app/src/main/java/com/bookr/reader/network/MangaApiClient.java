package com.bookr.reader.network;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.MangaChapterItem;
import com.bookr.reader.data.model.MangaItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MangaApiClient {
    private static MangaApiClient instance;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface MangaListCallback {
        void onSuccess(List<MangaItem> mangaList);
        void onError(String error);
    }

    public interface ChaptersCallback {
        void onSuccess(List<MangaChapterItem> chapters);
        void onError(String error);
    }

    public interface PagesCallback {
        void onSuccess(List<String> pageUrlsOrPaths, boolean isOffline);
        void onError(String error);
    }

    public interface DownloadChapterCallback {
        void onProgress(int currentPage, int totalPages);
        void onSuccess(MangaChapterItem chapter);
        void onError(String error);
    }

    public static synchronized MangaApiClient getInstance() {
        if (instance == null) {
            instance = new MangaApiClient();
        }
        return instance;
    }

    private MangaApiClient() {
        this.executor = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Primary catalog fetch: MangaLib (largest RU catalog) with Remanga and curated titles.
     */
    public void fetchCatalogManga(final Context context, final String query, final MangaListCallback callback) {
        executor.execute(() -> {
            List<MangaItem> list = new ArrayList<>();
            String safeQuery = query != null ? query.trim() : "";

            try {
                // 1. Add matching curated top classic manga first
                list.addAll(filterMangaByQuery(getCuratedMangaList(), safeQuery));

                // 2. Fetch from MangaLib (api.lib.social)
                StringBuilder mlUrl = new StringBuilder("https://api.lib.social/api/manga?site_id[]=1&page=1");
                if (!safeQuery.isEmpty()) {
                    mlUrl.append("&q=").append(URLEncoder.encode(safeQuery, "UTF-8"));
                }
                String mlJson = httpGetString(mlUrl.toString());
                if (mlJson != null && !mlJson.isEmpty()) {
                    List<MangaItem> mlItems = parseMangaLibListJson(mlJson);
                    for (MangaItem m : mlItems) {
                        boolean dup = false;
                        for (MangaItem ex : list) {
                            if (ex.getMangaId().equalsIgnoreCase(m.getMangaId())
                                    || ex.getTitle().equalsIgnoreCase(m.getTitle())) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup) list.add(m);
                    }
                }

                // 3. Search Remanga as secondary source (for licensed or exclusive scanlations)
                if (!safeQuery.isEmpty() || list.size() < 15) {
                    String remangaUrl;
                    if (!safeQuery.isEmpty()) {
                        remangaUrl = "https://api.remanga.org/api/v2/search/?query=" + URLEncoder.encode(safeQuery, "UTF-8") + "&page=1&count=20";
                    } else {
                        remangaUrl = "https://api.remanga.org/api/v2/search/catalog/?ordering=-votes&page=1&count=20";
                    }
                    String remangaJson = httpGetString(remangaUrl);
                    if (remangaJson != null && !remangaJson.isEmpty()) {
                        List<MangaItem> remangaItems = parseRemangaListJson(remangaJson);
                        for (MangaItem m : remangaItems) {
                            boolean dup = false;
                            for (MangaItem ex : list) {
                                if (ex.getMangaId().equalsIgnoreCase(m.getMangaId())
                                        || ex.getTitle().equalsIgnoreCase(m.getTitle())) {
                                    dup = true;
                                    break;
                                }
                            }
                            if (!dup) list.add(m);
                        }
                    }
                }

                // 4. Sync local state (favorites, downloaded chapters, last read chapter/page)
                if (context != null) {
                    BookRepository repo = new BookRepository(context);
                    for (MangaItem m : list) {
                        MangaItem local = repo.getMangaByExternalId(m.getMangaId());
                        if (local != null) {
                            m.setId(local.getId());
                            m.setFavorite(local.isFavorite());
                            m.setDownloaded(local.isDownloaded());
                            m.setDownloadedChaptersCount(local.getDownloadedChaptersCount());
                            m.setLastChapterId(local.getLastChapterId());
                            m.setLastChapterTitle(local.getLastChapterTitle());
                            m.setLastPage(local.getLastPage());
                            if (local.getLocalCoverPath() != null) {
                                m.setLocalCoverPath(local.getLocalCoverPath());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (list.isEmpty()) {
                    list.addAll(filterMangaByQuery(getCuratedMangaList(), safeQuery));
                }
            }

            final List<MangaItem> finalResult = list;
            mainHandler.post(() -> {
                if (callback != null) callback.onSuccess(finalResult);
            });
        });
    }

    /**
     * Fetch chapters for a given manga.
     */
    public void fetchChapters(final Context context, final MangaItem manga,
                              final String langFilter, final ChaptersCallback callback) {
        executor.execute(() -> {
            List<MangaChapterItem> chapters = new ArrayList<>();
            BookRepository repo = context != null ? new BookRepository(context) : null;
            String mangaId = manga.getMangaId();

            try {
                if (mangaId.startsWith("builtin_")) {
                    chapters.addAll(generateBuiltInChapters(manga));
                } else if (mangaId.startsWith("remanga_")) {
                    // Remanga source
                    String dir = mangaId.substring(8);
                    chapters.addAll(fetchRemangaChapters(dir, mangaId));
                } else if (isMangaDexUuid(mangaId)) {
                    // Legacy MangaDex source
                    chapters.addAll(fetchMangaDexChaptersFeed(mangaId, langFilter));
                } else {
                    // Primary MangaLib source
                    String url = "https://api.lib.social/api/manga/" + mangaId + "/chapters";
                    String json = httpGetString(url);
                    if (json != null && !json.isEmpty()) {
                        chapters.addAll(parseMangaLibChaptersJson(json, mangaId));
                    }

                    // Fallback to Remanga if MangaLib has 0 chapters (e.g. licensed by RU publisher)
                    if (chapters.isEmpty()) {
                        String searchRemanga = "https://api.remanga.org/api/v2/search/?query=" + URLEncoder.encode(manga.getTitle(), "UTF-8") + "&page=1&count=5";
                        String remSearchJson = httpGetString(searchRemanga);
                        if (remSearchJson != null) {
                            List<MangaItem> remItems = parseRemangaListJson(remSearchJson);
                            if (!remItems.isEmpty()) {
                                String rDir = remItems.get(0).getMangaId().replace("remanga_", "");
                                chapters.addAll(fetchRemangaChapters(rDir, mangaId));
                            }
                        }
                    }

                    // Secondary fallback to MangaDex feed if still empty
                    if (chapters.isEmpty()) {
                        String mdSearchUrl = "https://api.mangadex.org/manga?limit=5&title=" + URLEncoder.encode(manga.getTitle(), "UTF-8");
                        String mdSearchJson = httpGetString(mdSearchUrl);
                        if (mdSearchJson != null) {
                            List<MangaItem> mdItems = parseMangaDexListJson(mdSearchJson);
                            if (!mdItems.isEmpty()) {
                                chapters.addAll(fetchMangaDexChaptersFeed(mdItems.get(0).getMangaId(), langFilter));
                            }
                        }
                    }
                }

                // Deduplicate chapters by chapter number
                if (!chapters.isEmpty()) {
                    List<MangaChapterItem> deduped = deduplicateMangaChapters(chapters, langFilter);
                    chapters.clear();
                    chapters.addAll(deduped);
                }

                // Fallback to cached chapters in DB or generated chapters if feed is empty
                if (chapters.isEmpty() && repo != null) {
                    chapters.addAll(repo.getChaptersForManga(manga.getMangaId()));
                }
                if (chapters.isEmpty()) {
                    chapters.addAll(generateBuiltInChapters(manga));
                }

                // Sync with DB
                if (repo != null) {
                    repo.saveOrUpdateManga(manga);
                    for (MangaChapterItem ch : chapters) {
                        repo.saveOrUpdateChapter(ch);
                    }
                }
            } catch (Exception e) {
                if (chapters.isEmpty()) {
                    chapters.addAll(generateBuiltInChapters(manga));
                }
            }

            final List<MangaChapterItem> finalChapters = chapters;
            mainHandler.post(() -> {
                if (callback != null) callback.onSuccess(finalChapters);
            });
        });
    }

    /**
     * Fetch high-resolution pages for a specific chapter.
     */
    public void fetchChapterPages(final Context context, final MangaItem manga,
                                  final MangaChapterItem chapter, final boolean dataSaver,
                                  final PagesCallback callback) {
        executor.execute(() -> {
            List<String> pages = new ArrayList<>();
            boolean offline = false;

            try {
                // 1. Check if downloaded offline on disk
                if (context != null) {
                    BookRepository repo = new BookRepository(context);
                    MangaChapterItem dbCh = repo.getChapterByExternalId(chapter.getChapterId());
                    String dirPath = dbCh != null && dbCh.getLocalDirPath() != null
                            ? dbCh.getLocalDirPath() : chapter.getLocalDirPath();
                    if (dirPath != null) {
                        File dir = new File(dirPath);
                        if (dir.exists() && dir.isDirectory()) {
                            File[] files = dir.listFiles((d, name) ->
                                    name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".webp"));
                            if (files != null && files.length > 0) {
                                Arrays.sort(files, Comparator.comparing(File::getName));
                                for (File f : files) {
                                    pages.add(f.getAbsolutePath());
                                }
                                offline = true;
                            }
                        }
                    }
                }

                String chId = chapter.getChapterId();

                // 2. MangaLib chapter pages
                if (pages.isEmpty() && chId.startsWith("ml:")) {
                    String[] parts = chId.split(":");
                    String vol = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : chapter.getVolumeNumber();
                    String num = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : chapter.getChapterNumber();
                    String branchId = parts.length > 4 ? parts[4] : "";

                    if (vol.isEmpty()) vol = "1";
                    StringBuilder urlSb = new StringBuilder("https://api.lib.social/api/manga/")
                            .append(manga.getMangaId())
                            .append("/chapter?number=").append(num)
                            .append("&volume=").append(vol);
                    if (!branchId.isEmpty() && !"null".equalsIgnoreCase(branchId)) {
                        urlSb.append("&branch_id=").append(branchId);
                    }

                    String json = httpGetString(urlSb.toString());
                    if (json != null) {
                        pages.addAll(parseMangaLibPagesJson(json));
                    }
                }

                // 3. Remanga chapter pages
                if (pages.isEmpty() && (chId.startsWith("remanga_") || manga.getMangaId().startsWith("remanga_"))) {
                    String rawId = chId.replace("remanga_", "");
                    String url = "https://api.remanga.org/api/v2/titles/chapters/" + rawId + "/";
                    String json = httpGetString(url);
                    if (json != null) {
                        pages.addAll(parseRemangaPagesJson(json));
                    }
                }

                // 4. MangaDex At-Home server fallback
                if (pages.isEmpty() && !chId.startsWith("builtin_") && !chId.startsWith("ml:")) {
                    String atHomeUrl = "https://api.mangadex.org/at-home/server/" + chId;
                    String json = httpGetString(atHomeUrl);
                    if (json != null) {
                        pages.addAll(parseAtHomePagesJson(json, dataSaver));
                    }
                }

                // 5. Fallback to procedural high-contrast manga pages if builtin or unreachable
                if (pages.isEmpty()) {
                    int count = Math.max(8, chapter.getPagesCount() > 0 ? chapter.getPagesCount() : 12);
                    for (int i = 1; i <= count; i++) {
                        pages.add("builtin://manga_page/" + chapter.getChapterId() + "/" + i);
                    }
                }
            } catch (Exception e) {
                if (pages.isEmpty()) {
                    for (int i = 1; i <= 10; i++) {
                        pages.add("builtin://manga_page/" + chapter.getChapterId() + "/" + i);
                    }
                }
            }

            final List<String> finalPages = pages;
            final boolean finalOffline = offline;
            mainHandler.post(() -> {
                if (callback != null) callback.onSuccess(finalPages, finalOffline);
            });
        });
    }

    public void downloadChapterOffline(final Context context, final MangaItem manga,
                                       final MangaChapterItem chapter, final boolean dataSaver,
                                       final DownloadChapterCallback callback) {
        fetchChapterPages(context, manga, chapter, dataSaver, new PagesCallback() {
            @Override
            public void onSuccess(List<String> pageUrls, boolean isAlreadyOffline) {
                executor.execute(() -> {
                    try {
                        String safeMangaId = manga.getMangaId().replaceAll("[^a-zA-Z0-9_-]", "_");
                        String safeChapId = chapter.getChapterId().replaceAll("[^a-zA-Z0-9_-]", "_");
                        File chapDir = new File(new File(new File(context.getFilesDir(), "manga_offline"), safeMangaId), safeChapId);
                        if (!chapDir.exists()) chapDir.mkdirs();

                        int total = pageUrls.size();
                        NetworkImageLoader loader = NetworkImageLoader.getInstance(context);

                        for (int i = 0; i < total; i++) {
                            final int pageNum = i + 1;
                            mainHandler.post(() -> {
                                if (callback != null) callback.onProgress(pageNum, total);
                            });

                            String src = pageUrls.get(i);
                            File pageFile = new File(chapDir, String.format(Locale.US, "page_%03d.jpg", pageNum));
                            if (pageFile.exists() && pageFile.length() > 512) {
                                continue;
                            }

                            boolean saved = false;
                            if (src.startsWith("http://") || src.startsWith("https://")) {
                                saved = NetworkImageLoader.downloadUrlToFile(src, pageFile);
                            }
                            if (!saved) {
                                Bitmap bmp = loader.loadMangaPageSync(src, pageNum, total,
                                        manga.getTitle(), chapter.getDisplayTitle());
                                if (bmp != null) {
                                    try (FileOutputStream fos = new FileOutputStream(pageFile)) {
                                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                                    }
                                }
                            }
                        }

                        // Save manga cover locally too
                        File coversDir = new File(context.getFilesDir(), "covers");
                        if (!coversDir.exists()) coversDir.mkdirs();
                        File coverFile = new File(coversDir, "manga_cov_" + safeMangaId + ".jpg");
                        if (!coverFile.exists()) {
                            Bitmap cov = loader.loadBitmapSync(manga.getCoverUrl(), manga.getTitle(), manga.getAuthor());
                            if (cov != null) {
                                try (FileOutputStream fos = new FileOutputStream(coverFile)) {
                                    cov.compress(Bitmap.CompressFormat.JPEG, 88, fos);
                                    manga.setLocalCoverPath(coverFile.getAbsolutePath());
                                } catch (Exception ignored) {
                                }
                            }
                        }

                        BookRepository repo = new BookRepository(context);
                        manga.setDownloaded(true);
                        repo.saveOrUpdateManga(manga);

                        chapter.setDownloaded(true);
                        chapter.setLocalDirPath(chapDir.getAbsolutePath());
                        chapter.setPagesCount(total);
                        repo.saveOrUpdateChapter(chapter);
                        repo.markChapterDownloaded(manga.getMangaId(), chapter.getChapterId(),
                                chapDir.getAbsolutePath(), total);

                        mainHandler.post(() -> {
                            if (callback != null) callback.onSuccess(chapter);
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError("Ошибка скачивания главы: " + e.getMessage());
                        });
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    // =========================================================================
    // JSON Parsers for MangaLib, Remanga, MangaDex
    // =========================================================================

    public static List<MangaItem> parseMangaLibListJson(String jsonStr) {
        List<MangaItem> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray data = root.optJSONArray("data");
            if (data == null) return list;

            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                String slugUrl = item.optString("slug_url", "");
                if (slugUrl.isEmpty()) {
                    long id = item.optLong("id", 0);
                    if (id > 0) slugUrl = String.valueOf(id);
                }
                if (slugUrl.isEmpty()) continue;

                String title = item.optString("rus_name", "");
                if (title.isEmpty()) title = item.optString("name", "Без названия");

                JSONObject typeObj = item.optJSONObject("type");
                String typeLabel = typeObj != null ? typeObj.optString("label", "Манга") : "Манга";

                JSONObject ratingObj = item.optJSONObject("rating");
                String avgRating = ratingObj != null ? ratingObj.optString("average", "") : "";

                JSONObject statusObj = item.optJSONObject("status");
                String statusLabel = statusObj != null ? statusObj.optString("label", "Выходит") : "Выходит";

                JSONObject coverObj = item.optJSONObject("cover");
                String coverUrl = "";
                if (coverObj != null) {
                    coverUrl = coverObj.optString("default", "");
                    if (coverUrl.isEmpty()) coverUrl = coverObj.optString("md", "");
                    if (coverUrl.isEmpty()) coverUrl = coverObj.optString("thumbnail", "");
                }
                if (coverUrl.isEmpty()) {
                    coverUrl = "builtin://cover/" + slugUrl;
                }

                String genres = !avgRating.isEmpty() ? typeLabel + " • ★ " + avgRating : typeLabel;
                String desc = "MangaLib • " + typeLabel + (!avgRating.isEmpty() ? " • Рейтинг " + avgRating : "") + " • " + statusLabel;

                list.add(new MangaItem(
                        slugUrl,
                        title,
                        typeLabel,
                        desc,
                        coverUrl,
                        statusLabel,
                        genres
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static List<MangaChapterItem> parseMangaLibChaptersJson(String jsonStr, String mangaId) {
        List<MangaChapterItem> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray data = root.optJSONArray("data");
            if (data == null) return list;

            for (int i = 0; i < data.length(); i++) {
                JSONObject obj = data.getJSONObject(i);
                String id = obj.optString("id", String.valueOf(i + 1));
                String vol = obj.optString("volume", "1");
                String num = obj.optString("number", String.valueOf(i + 1));
                String name = obj.optString("name", "");

                String branchId = "";
                String teamName = "";
                JSONArray branches = obj.optJSONArray("branches");
                if (branches != null && branches.length() > 0) {
                    JSONObject b = branches.getJSONObject(0);
                    branchId = b.optString("branch_id", "");
                    JSONArray teams = b.optJSONArray("teams");
                    if (teams != null && teams.length() > 0) {
                        teamName = teams.getJSONObject(0).optString("name", "");
                    }
                }

                String chapterId = "ml:" + id + ":" + vol + ":" + num + ":" + branchId;

                MangaChapterItem ch = new MangaChapterItem(
                        chapterId,
                        mangaId,
                        num,
                        vol,
                        name,
                        "RU",
                        20
                );
                if (!teamName.isEmpty()) {
                    ch.setScanlationGroup(teamName);
                }
                list.add(ch);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static List<String> parseMangaLibPagesJson(String jsonStr) {
        List<String> pages = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return pages;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return pages;

            JSONArray arr = data.optJSONArray("pages");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject p = arr.getJSONObject(i);
                    String url = p.optString("url", "");
                    if (!url.isEmpty()) {
                        if (url.startsWith("//")) {
                            pages.add("https://img3.cdnlibs.org" + url.substring(1));
                        } else if (url.startsWith("http://") || url.startsWith("https://")) {
                            pages.add(url);
                        } else {
                            pages.add("https://img3.cdnlibs.org" + (url.startsWith("/") ? url : "/" + url));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return pages;
    }

    public static List<MangaItem> parseRemangaListJson(String jsonStr) {
        List<MangaItem> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray results = root.optJSONArray("results");
            if (results == null) return list;

            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                String dir = r.optString("dir", "");
                if (dir.isEmpty()) continue;

                String id = "remanga_" + dir;
                String title = r.optString("main_name", "");
                if (title.isEmpty()) title = r.optString("secondary_name", "Без названия");

                JSONObject typeObj = r.optJSONObject("type");
                String typeName = typeObj != null ? typeObj.optString("name", "Манга") : "Манга";

                String avgRating = r.optString("avg_rating", "");
                JSONObject statusObj = r.optJSONObject("status");
                String statusName = statusObj != null ? statusObj.optString("name", "Выходит") : "Выходит";

                JSONObject coverObj = r.optJSONObject("cover");
                String coverPath = "";
                if (coverObj != null) {
                    coverPath = coverObj.optString("high", "");
                    if (coverPath.isEmpty()) coverPath = coverObj.optString("mid", "");
                }
                String coverUrl = !coverPath.isEmpty()
                        ? (coverPath.startsWith("http") ? coverPath : "https://api.remanga.org" + coverPath)
                        : "builtin://cover/" + dir;

                String genres = !avgRating.isEmpty() ? typeName + " • ★ " + avgRating : typeName;
                String desc = "Remanga • " + typeName + (!avgRating.isEmpty() ? " • Рейтинг " + avgRating : "") + " • " + statusName;

                list.add(new MangaItem(
                        id,
                        title,
                        typeName,
                        desc,
                        coverUrl,
                        statusName,
                        genres
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static List<MangaChapterItem> parseRemangaChaptersJson(String jsonStr, String mangaId) {
        List<MangaChapterItem> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray results = root.optJSONArray("results");
            if (results == null) return list;

            for (int i = 0; i < results.length(); i++) {
                JSONObject c = results.getJSONObject(i);
                long id = c.optLong("id", 0);
                String chNum = c.optString("chapter", String.valueOf(i + 1));
                int tome = c.optInt("tome", 1);
                String name = c.optString("name", "");

                JSONArray pubArr = c.optJSONArray("publishers");
                String team = "";
                if (pubArr != null && pubArr.length() > 0) {
                    team = pubArr.getJSONObject(0).optString("name", "");
                }

                MangaChapterItem item = new MangaChapterItem(
                        "remanga_" + id,
                        mangaId,
                        chNum,
                        String.valueOf(tome),
                        name,
                        "RU",
                        20
                );
                if (!team.isEmpty()) item.setScanlationGroup(team);
                list.add(item);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static List<String> parseRemangaPagesJson(String jsonStr) {
        List<String> pages = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return pages;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray pagesArr = root.optJSONArray("pages");
            if (pagesArr != null) {
                for (int i = 0; i < pagesArr.length(); i++) {
                    Object elem = pagesArr.get(i);
                    if (elem instanceof JSONArray) {
                        JSONArray sub = (JSONArray) elem;
                        if (sub.length() > 0) {
                            JSONObject p = sub.getJSONObject(0);
                            String link = p.optString("link", "");
                            if (!link.isEmpty()) pages.add(link);
                        }
                    } else if (elem instanceof JSONObject) {
                        JSONObject p = (JSONObject) elem;
                        String link = p.optString("link", "");
                        if (!link.isEmpty()) pages.add(link);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return pages;
    }

    private List<MangaChapterItem> fetchRemangaChapters(String dir, String mangaId) {
        List<MangaChapterItem> chapters = new ArrayList<>();
        try {
            String titleUrl = "https://api.remanga.org/api/v2/titles/" + dir + "/";
            String titleJson = httpGetString(titleUrl);
            if (titleJson != null) {
                JSONObject root = new JSONObject(titleJson);
                JSONArray branches = root.optJSONArray("branches");
                long branchId = 0;
                if (branches != null && branches.length() > 0) {
                    branchId = branches.getJSONObject(0).optLong("id", 0);
                }
                if (branchId > 0) {
                    String chUrl = "https://api.remanga.org/api/v2/titles/chapters/?branch_id=" + branchId + "&page=1&count=500";
                    String chJson = httpGetString(chUrl);
                    if (chJson != null) {
                        chapters.addAll(parseRemangaChaptersJson(chJson, mangaId));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return chapters;
    }

    private List<MangaChapterItem> fetchMangaDexChaptersFeed(String mangaId, String langFilter) {
        List<MangaChapterItem> rawFeedChapters = new ArrayList<>();
        int offset = 0;
        int totalFound = Integer.MAX_VALUE;
        int maxChaptersToFetch = 500;

        while (offset < totalFound && offset < maxChaptersToFetch) {
            StringBuilder urlSb = new StringBuilder("https://api.mangadex.org/manga/")
                    .append(mangaId)
                    .append("/feed?limit=100&offset=").append(offset)
                    .append("&order[chapter]=asc&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica");
            if ("RU".equalsIgnoreCase(langFilter)) {
                urlSb.append("&translatedLanguage[]=ru");
            } else if ("EN".equalsIgnoreCase(langFilter)) {
                urlSb.append("&translatedLanguage[]=en");
            } else {
                urlSb.append("&translatedLanguage[]=ru&translatedLanguage[]=en");
            }

            String json = httpGetString(urlSb.toString());
            if (json == null || json.isEmpty()) break;

            try {
                JSONObject root = new JSONObject(json);
                totalFound = root.optInt("total", 0);
                List<MangaChapterItem> pageChapters = parseMangaDexChaptersJson(json, mangaId);
                if (pageChapters.isEmpty()) break;
                rawFeedChapters.addAll(pageChapters);
                offset += pageChapters.size();
                if (pageChapters.size() < 100) break;
            } catch (Exception ex) {
                break;
            }
        }
        return rawFeedChapters;
    }

    public static List<MangaItem> parseMangaDexListJson(String jsonStr) {
        List<MangaItem> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray data = root.optJSONArray("data");
            if (data == null) return list;

            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                String id = item.optString("id", "");
                JSONObject attrs = item.optJSONObject("attributes");
                if (id.isEmpty() || attrs == null) continue;

                String title = extractLocalizedString(attrs.optJSONObject("title"), "Без названия");
                String desc = extractLocalizedString(attrs.optJSONObject("description"), "Манга из каталога.");
                String statusRaw = attrs.optString("status", "ongoing");
                String status = "completed".equalsIgnoreCase(statusRaw) ? "Завершена" : "Выходит";

                StringBuilder tagsSb = new StringBuilder();
                JSONArray tags = attrs.optJSONArray("tags");
                if (tags != null) {
                    for (int t = 0; t < Math.min(4, tags.length()); t++) {
                        JSONObject tagObj = tags.getJSONObject(t).optJSONObject("attributes");
                        if (tagObj != null) {
                            String tagName = extractLocalizedString(tagObj.optJSONObject("name"), "");
                            if (!tagName.isEmpty()) {
                                if (tagsSb.length() > 0) tagsSb.append(" • ");
                                tagsSb.append(tagName);
                            }
                        }
                    }
                }

                String coverFileName = null;
                String authorName = "Мангака";
                JSONArray rels = item.optJSONArray("relationships");
                if (rels != null) {
                    for (int r = 0; r < rels.length(); r++) {
                        JSONObject rel = rels.getJSONObject(r);
                        String type = rel.optString("type", "");
                        JSONObject relAttrs = rel.optJSONObject("attributes");
                        if ("cover_art".equals(type) && relAttrs != null) {
                            coverFileName = relAttrs.optString("fileName", null);
                        } else if ("author".equals(type) && relAttrs != null) {
                            authorName = relAttrs.optString("name", authorName);
                        }
                    }
                }

                String coverUrl = coverFileName != null
                        ? "https://uploads.mangadex.org/covers/" + id + "/" + coverFileName + ".256.jpg"
                        : "builtin://cover/" + id;

                list.add(new MangaItem(
                        id,
                        title,
                        authorName,
                        desc,
                        coverUrl,
                        status,
                        tagsSb.length() > 0 ? tagsSb.toString() : "Манга • Приключения"
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static List<MangaChapterItem> parseMangaDexChaptersJson(String jsonStr, String mangaId) {
        List<MangaChapterItem> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray data = root.optJSONArray("data");
            if (data == null) return list;

            for (int i = 0; i < data.length(); i++) {
                JSONObject obj = data.getJSONObject(i);
                String chId = obj.optString("id", "");
                JSONObject attrs = obj.optJSONObject("attributes");
                if (chId.isEmpty() || attrs == null) continue;

                int pages = attrs.optInt("pages", 0);
                String externalUrl = attrs.optString("externalUrl", "");
                if (pages <= 0 && externalUrl != null && !externalUrl.isEmpty() && !"null".equals(externalUrl)) {
                    continue;
                }

                String chNum = attrs.optString("chapter", String.valueOf(i + 1));
                if ("null".equalsIgnoreCase(chNum) || chNum.isEmpty()) chNum = String.valueOf(i + 1);
                String volNum = attrs.optString("volume", "");
                if ("null".equalsIgnoreCase(volNum)) volNum = "";
                String title = attrs.optString("title", "");
                if ("null".equalsIgnoreCase(title)) title = "";
                String lang = attrs.optString("translatedLanguage", "ru").toUpperCase(Locale.ROOT);

                MangaChapterItem ch = new MangaChapterItem(
                        chId,
                        mangaId,
                        chNum,
                        volNum,
                        title,
                        lang,
                        Math.max(1, pages)
                );
                list.add(ch);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static List<String> parseAtHomePagesJson(String jsonStr, boolean dataSaver) {
        List<String> pages = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return pages;
        try {
            JSONObject root = new JSONObject(jsonStr);
            String baseUrl = root.optString("baseUrl", "https://uploads.mangadex.org");
            JSONObject chapter = root.optJSONObject("chapter");
            if (chapter == null) return pages;

            String hash = chapter.optString("hash", "");
            JSONArray arr = dataSaver ? chapter.optJSONArray("dataSaver") : chapter.optJSONArray("data");
            String qualityFolder = dataSaver ? "data-saver" : "data";
            if (arr == null || arr.length() == 0) {
                arr = chapter.optJSONArray("data");
                qualityFolder = "data";
            }
            if (arr != null && !hash.isEmpty()) {
                for (int i = 0; i < arr.length(); i++) {
                    String fileName = arr.optString(i, "");
                    if (!fileName.isEmpty()) {
                        pages.add(baseUrl + "/" + qualityFolder + "/" + hash + "/" + fileName);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return pages;
    }

    private static String extractLocalizedString(JSONObject obj, String fallback) {
        if (obj == null) return fallback;
        if (obj.has("ru")) return obj.optString("ru", fallback);
        if (obj.has("en")) return obj.optString("en", fallback);
        if (obj.has("ja-ro")) return obj.optString("ja-ro", fallback);
        if (obj.has("ja")) return obj.optString("ja", fallback);
        Iterator<String> keys = obj.keys();
        if (keys.hasNext()) {
            String k = keys.next();
            String val = obj.optString(k, fallback);
            if (!val.isEmpty()) return val;
        }
        return fallback;
    }

    private static boolean isMangaDexUuid(String str) {
        if (str == null || str.length() != 36) return false;
        int dashes = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '-') dashes++;
        }
        return dashes == 4;
    }

    private static String httpGetString(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(18000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Referer", "https://mangalib.me/");
            conn.setRequestProperty("Site-Id", "1");

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static List<MangaItem> filterMangaByQuery(List<MangaItem> list, String query) {
        if (query == null || query.trim().isEmpty()) return new ArrayList<>(list);
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<MangaItem> res = new ArrayList<>();
        for (MangaItem m : list) {
            if (m.getTitle().toLowerCase(Locale.ROOT).contains(q)
                    || m.getAuthor().toLowerCase(Locale.ROOT).contains(q)
                    || m.getGenres().toLowerCase(Locale.ROOT).contains(q)
                    || m.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
                res.add(m);
            }
        }
        return res;
    }

    public static List<MangaChapterItem> deduplicateMangaChapters(List<MangaChapterItem> raw, String preferredLang) {
        Map<String, MangaChapterItem> map = new LinkedHashMap<>();
        for (MangaChapterItem ch : raw) {
            String num = ch.getChapterNumber() != null ? ch.getChapterNumber().trim() : "";
            if (num.isEmpty()) num = ch.getChapterId();

            if (!map.containsKey(num)) {
                map.put(num, ch);
            } else {
                MangaChapterItem existing = map.get(num);
                boolean incomingIsRu = "RU".equalsIgnoreCase(ch.getLanguage());
                boolean existingIsRu = "RU".equalsIgnoreCase(existing.getLanguage());
                if (incomingIsRu && !existingIsRu) {
                    map.put(num, ch);
                } else if (incomingIsRu == existingIsRu) {
                    if (existing.getTitle().isEmpty() && !ch.getTitle().isEmpty()) {
                        map.put(num, ch);
                    } else if (ch.getPagesCount() > existing.getPagesCount()) {
                        map.put(num, ch);
                    }
                }
            }
        }

        List<MangaChapterItem> sorted = new ArrayList<>(map.values());
        Collections.sort(sorted, (c1, c2) -> {
            try {
                float f1 = Float.parseFloat(c1.getChapterNumber());
                float f2 = Float.parseFloat(c2.getChapterNumber());
                return Float.compare(f1, f2);
            } catch (Exception e) {
                return c1.getChapterNumber().compareTo(c2.getChapterNumber());
            }
        });
        return sorted;
    }

    public static List<MangaItem> getCuratedMangaList() {
        List<MangaItem> list = new ArrayList<>();

        // 1. One Piece
        list.add(new MangaItem(
                "206--one-piece",
                "One Piece (Ван-Пис)",
                "Эйитиро Ода",
                "Легендарная сага о Манки Д. Луффи и его команде Пиратов Соломенной Шляпы в поисках величайшего сокровища мира — Ван-Пис.",
                "https://cover.imglib.info/uploads/cover/one-piece/cover/default.jpg",
                "Выходит",
                "Сёнен • Приключения • Фэнтези • Экшен"
        ));

        // 2. Chainsaw Man
        list.add(new MangaItem(
                "7965--chainsaw-man",
                "Chainsaw Man (Человек-бензопила)",
                "Тацуки Фудзимото",
                "Дэндзи заключает договор с демоническим псом Почитой и становится Человеком-бензопилой, охотясь на опасных демонов Токио.",
                "https://cover.imglib.info/uploads/cover/chainsaw-man/cover/default.jpg",
                "Выходит",
                "Сёнен • Экшен • Сверхъестественное • Триллер"
        ));

        // 3. Jujutsu Kaisen
        list.add(new MangaItem(
                "4464--jujutsu-kaisen",
                "Jujutsu Kaisen (Магическая битва)",
                "Гэгэ Акутами",
                "Старшеклассник Юдзи Итадори проглатывает палец короля проклятий Сукуны и вступает в Токийский магический колледж.",
                "https://cover.imglib.info/uploads/cover/jujutsu-kaisen/cover/default.jpg",
                "Завершена",
                "Сёнен • Магия • Экшен • Сверхъестественное"
        ));

        // 4. Demon Slayer
        list.add(new MangaItem(
                "3595--kimetsu-no-yaiba",
                "Demon Slayer (Клинок, рассекающий демонов)",
                "Коёхару Готогэ",
                "Тандзиро Камадо становится истребителем демонов, чтобы найти лекарство для сестры Недзуко, обращённой в демона.",
                "https://cover.imglib.info/uploads/cover/kimetsu-no-yaiba/cover/default.jpg",
                "Завершена",
                "Сёнен • Исторический • Экшен • Самураи"
        ));

        // 5. Naruto
        list.add(new MangaItem(
                "195--naruto",
                "Naruto (Наруто)",
                "Масаси Кисимото",
                "История Наруто Узумаки — юного ниндзя деревни Скрытого Листа, мечтающего преодолеть одиночество и стать Хокагэ.",
                "https://cover.imglib.info/uploads/cover/naruto/cover/default.jpg",
                "Завершена",
                "Сёнен • Ниндзя • Боевые искусства • Приключения"
        ));

        // 6. Bleach
        list.add(new MangaItem(
                "43--bleach",
                "Bleach (Блич)",
                "Тайто Кубо",
                "Ичиго Куросаки случайно получает силы проводника душ синигами и встаёт на защиту людей от пустых.",
                "https://cover.imglib.info/uploads/cover/bleach/cover/default.jpg",
                "Завершена",
                "Сёнен • Синигами • Сверхъестественное • Экшен"
        ));

        // 7. Omniscient Reader
        list.add(new MangaItem(
                "34466--jeonjijeog-dogja-sijeom_",
                "Omniscient Reader (Всеведущий читатель)",
                "sing N song, Sleepy-C",
                "Ким Докча был единственным человеком, дочитавшим постапокалиптический веб-роман до конца. Вдруг мир книги становится реальностью.",
                "https://cover.imglib.info/uploads/cover/jeonjijeog-dogja-sijeom_/cover/default.jpg",
                "Выходит",
                "Манхва • Апокалипсис • Экшен • Система"
        ));

        // 8. The Greatest Estate Developer
        list.add(new MangaItem(
                "89959--yeokdagp-yeongji-sulgyesa",
                "The Greatest Estate Developer (Система всемогущего дизайнера)",
                "Lee Hyunmin, Kim Hyunsoo",
                "Студент-инженер Ким Сухо просыпается в теле дворянина-лентяя Ллойда Фронтеры в фэнтези-мире и строит империю своими знаниями.",
                "https://cover.imglib.info/uploads/cover/yeokdagp-yeongji-sulgyesa/cover/default.jpg",
                "Выходит",
                "Манхва • Комедия • Исекай • Строительство"
        ));

        // 9. Wind Breaker
        list.add(new MangaItem(
                "1773--wind-breaker",
                "Wind Breaker (Ветролом)",
                "Чо Ёнсок",
                "Джей — идеальный президент школьного совета, но его настоящая страсть — уличные гонки на велосипедах фикс-гир.",
                "https://cover.imglib.info/uploads/cover/wind-breaker/cover/default.jpg",
                "Выходит",
                "Манхва • Спорт • Драма • Гонки"
        ));

        // 10. Berserk
        list.add(new MangaItem(
                "109483--berserk-memorial",
                "Berserk (Берсерк: Памятник)",
                "Кэнтаро Миура",
                "Мрачный шедевр тёмного фэнтези о Чёрном Мечнике Гатсе, его борьбе с судьбой, демонами Руки Бога и предательством.",
                "https://cover.imglib.info/uploads/cover/berserk-memorial/cover/default.jpg",
                "Выходит",
                "Сейнен • Тёмное фэнтези • Драма • Трагедия"
        ));

        // 11. Solo Leveling Ragnarok
        list.add(new MangaItem(
                "168691--na-honjamaen-lebel-eob-lageunalokeu",
                "Solo Leveling: Ragnarok (Поднятие уровня в одиночку: Рагнарёк)",
                "Chugong, Daul",
                "Продолжение культовой манхвы о Сон Сухо — сыне Теневого Монарха Сон Джин-Ву, в котором пробуждаются силы отца.",
                "https://cover.imglib.info/uploads/cover/na-honjamaen-lebel-eob-lageunalokeu/cover/default.jpg",
                "Выходит",
                "Манхва • ЛитРПГ • Экшен • Фэнтези"
        ));

        // 12. Attack on Titan
        list.add(new MangaItem(
                "247--shingeki-no-kyojin",
                "Attack on Titan (Атака титанов)",
                "Хадзимэ Исаяма",
                "Человечество живёт за тремя исполинскими стенами, спасаясь от титанов. Эрен Йегер клянётся истребить каждого из них.",
                "https://cover.imglib.info/uploads/cover/shingeki-no-kyojin/cover/default.jpg",
                "Завершена",
                "Сёнен • Драма • Постапокалиптика • Мистика"
        ));

        // 13. Death Note
        list.add(new MangaItem(
                "7b275bfb-7fa7-4f68-8098-b64800331fc3",
                "Death Note (Тетрадь смерти)",
                "Цугуми Оба, Такэси Обата",
                "Лайт Ягами находит тетрадь бога смерти Рюка, способную убивать любого человека по имени, и объявляет себя богом Кирой.",
                "builtin://cover/death_note",
                "Завершена",
                "Сейнен • Психологический триллер • Детектив"
        ));

        // 14. Tokyo Ghoul
        list.add(new MangaItem(
                "11e6a928-a447-495c-9d04-46960d3d3b76",
                "Tokyo Ghoul (Токийский гуль)",
                "Суи Исида",
                "Кэн Канэки после пересадки органов становится наполовину гулем — существом, вынужденным питаться человеческой плотью.",
                "builtin://cover/tokyo_ghoul",
                "Завершена",
                "Сейнен • Тёмное фэнтези • Хоррор • Психология"
        ));

        // 15. Hunter x Hunter
        list.add(new MangaItem(
                "5a092822-1d54-4a4b-84a1-053ae5fb8328",
                "Hunter x Hunter (Охотник х Охотник)",
                "Ёсихиро Тогаси",
                "Гон Фрикс сдаёт экзамен на Охотника, чтобы отправиться в опасный мир на поиски своего отца Джина.",
                "builtin://cover/hxh",
                "Выходит",
                "Сёнен • Приключения • Боевые искусства • Фэнтези"
        ));

        // 16. Vinland Saga
        list.add(new MangaItem(
                "5d111aa7-3fd9-4375-9658-005d5236cf53",
                "Vinland Saga (Сага о Винланде)",
                "Макото Юкимура",
                "Историческая драма о Торфинне, юном воине-викинге, жаждущем отомстить за гибель отца и найти мирную землю Винланд.",
                "builtin://cover/vinland",
                "Выходит",
                "Сейнен • Исторический • Викинги • Драма"
        ));

        // 17. Spy x Family
        list.add(new MangaItem(
                "61a7a706-f89a-4c26-8367-8e68db359489",
                "Spy x Family (Семья шпиона)",
                "Тацуя Эндо",
                "Шпион «Сумрак» для выполнения миссии создаёт фиктивную семью: его приёмная дочь — телепат, а жена — наёмная убийца.",
                "builtin://cover/spy_family",
                "Выходит",
                "Комедия • Шпионаж • Сёнен • Повседневность"
        ));

        // 18. One Punch Man
        list.add(new MangaItem(
                "d8a959fb-e100-4375-acdc-6e46d153162a",
                "One Punch Man (Ванпанчмен)",
                "ONE, Юсукэ Мурата",
                "Сайтама стал настолько сильным, что побеждает любых монстров с одного удара, из-за чего страдает от скуки.",
                "builtin://cover/opm",
                "Выходит",
                "Сёнен • Супергерои • Пародия • Экшен"
        ));

        // 19. Fullmetal Alchemist
        list.add(new MangaItem(
                "740fc513-39d6-4447-925a-4ba4e0d939c0",
                "Fullmetal Alchemist (Стальной алхимик)",
                "Хирому Аракава",
                "Братья Эдвард и Альфонс Элрики нарушают запрет трансмутации человека и отправляются на поиски философского камня.",
                "builtin://cover/fma",
                "Завершена",
                "Сёнен • Алхимия • Стимпанк • Драма"
        ));

        // 20. JoJo's Bizarre Adventure
        list.add(new MangaItem(
                "51859e99-4c17-4eb2-a634-8c887fbef711",
                "JoJo's Bizarre Adventure (ДжоДжо)",
                "Хирохико Араки",
                "Многовековая сага рода Джостаров, сражающихся против древнего зла и обладателей стендов с помощью смекалки.",
                "builtin://cover/jojo",
                "Выходит",
                "Сёнен • Стенды • Приключения • Экшен"
        ));

        // 21. Клинок Затмения (Builtin)
        list.add(new MangaItem(
                "builtin_blade_of_eclipse",
                "Клинок Затмения: Хроники Пепла",
                "Кэндзи Такахаси",
                "В мире, где солнце погасло триста лет назад, орден Ночных Стражей охраняет последние города от Пожирателей Теней.",
                "builtin://cover/blade_eclipse",
                "Выходит",
                "Тёмное фэнтези • Экшен • Сэйнэн"
        ));

        // 22. Кибер-Токио 2099 (Builtin)
        list.add(new MangaItem(
                "builtin_cyber_tokyo_2099",
                "Кибер-Токио 2099: Стеклянный Призрак",
                "Акира Сато",
                "Неоновый мегаполис под куполом вечного дождя. Детектив-нейрохакер расследует исчезновение архитектора сознания.",
                "builtin://cover/cyber_tokyo",
                "Выходит",
                "Киберпанк • Детектив • Фантастика"
        ));

        // 23. Башня Белого Алхимика (Builtin)
        list.add(new MangaItem(
                "builtin_alchemist_tower",
                "Башня Белого Алхимика",
                "Харука Мидзуно",
                "Сто этажей Древней Башни скрывают секрет философского камня. Юный алхимик начинает восхождение с первого уровня.",
                "builtin://cover/alchemist_tower",
                "Завершена",
                "Приключения • Алхимия • Фэнтези"
        ));

        // 24. Путь Безмолвного Ронина (Builtin)
        list.add(new MangaItem(
                "builtin_silent_samurai",
                "Путь Безмолвного Ронина",
                "Масахиро Куросава",
                "Философская самурайская драма об одиноком мастере меча, странствующем по заснеженным провинциям эпохи Эдо.",
                "builtin://cover/silent_samurai",
                "Завершена",
                "Самураи • История • Драма"
        ));

        return list;
    }

    public static List<MangaChapterItem> generateBuiltInChapters(MangaItem manga) {
        List<MangaChapterItem> chapters = new ArrayList<>();
        String[] titles = new String[]{
                "Пробуждение в ночи",
                "Тень за вратами",
                "Испытание сталью",
                "Секрет древнего свитка",
                "Пепел и сталь",
                "Поединок на крыше",
                "Голос из бездны",
                "Клятва на рассвете",
                "Лабиринт зеркал",
                "Решающий удар",
                "Рассвет над руинами",
                "Дыхание бездны",
                "Печать четырёх стихий",
                "Схватка в тумане",
                "Зеркало судеб",
                "Огни цитадели",
                "Врата забвения",
                "Глаз бури",
                "Триумф духа",
                "Новая эра"
        };
        for (int i = 0; i < titles.length; i++) {
            int chNum = i + 1;
            int vol = (i / 4) + 1;
            MangaChapterItem ch = new MangaChapterItem(
                    "builtin_ch_" + manga.getMangaId() + "_" + chNum,
                    manga.getMangaId(),
                    String.valueOf(chNum),
                    String.valueOf(vol),
                    titles[i],
                    "RU",
                    16
            );
            chapters.add(ch);
        }
        return chapters;
    }
}
