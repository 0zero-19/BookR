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

    public void fetchCatalogManga(final Context context, final String query, final MangaListCallback callback) {
        executor.execute(() -> {
            List<MangaItem> list = new ArrayList<>();
            String safeQuery = query != null ? query.trim() : "";

            try {
                // 1. Add matching curated featured manga series first
                list.addAll(filterMangaByQuery(getCuratedMangaList(), safeQuery));

                // 2. Fetch live from MangaDex API v5 with all ratings (safe, suggestive, erotica for seinen/action)
                StringBuilder urlSb = new StringBuilder("https://api.mangadex.org/manga?limit=60&includes[]=cover_art&includes[]=author&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica");
                if (!safeQuery.isEmpty()) {
                    urlSb.append("&title=").append(URLEncoder.encode(safeQuery, "UTF-8"));
                } else {
                    urlSb.append("&order[followedCount]=desc");
                }

                String json = httpGetString(urlSb.toString());
                if (json != null) {
                    List<MangaItem> live = parseMangaDexListJson(json);
                    for (MangaItem m : live) {
                        boolean dup = false;
                        for (MangaItem ex : list) {
                            if (ex.getMangaId().equals(m.getMangaId()) || ex.getTitle().equalsIgnoreCase(m.getTitle())) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup) list.add(m);
                    }
                }

                // 3. Sync local state (favorites, downloaded chapters, last read chapter/page)
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

    public void fetchChapters(final Context context, final MangaItem manga,
                              final String langFilter, final ChaptersCallback callback) {
        executor.execute(() -> {
            List<MangaChapterItem> chapters = new ArrayList<>();
            BookRepository repo = context != null ? new BookRepository(context) : null;

            try {
                if (manga.getMangaId().startsWith("builtin_")) {
                    chapters.addAll(generateBuiltInChapters(manga));
                } else {
                    List<MangaChapterItem> rawFeedChapters = new ArrayList<>();
                    int offset = 0;
                    int totalFound = Integer.MAX_VALUE;
                    int maxChaptersToFetch = 500;

                    while (offset < totalFound && offset < maxChaptersToFetch) {
                        StringBuilder urlSb = new StringBuilder("https://api.mangadex.org/manga/")
                                .append(manga.getMangaId())
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
                            List<MangaChapterItem> pageChapters = parseMangaDexChaptersJson(json, manga.getMangaId());
                            if (pageChapters.isEmpty()) break;
                            rawFeedChapters.addAll(pageChapters);
                            offset += pageChapters.size();
                            if (pageChapters.size() < 100) break;
                        } catch (Exception ex) {
                            break;
                        }
                    }

                    // Deduplicate chapters by chapter number, preferring RU over EN
                    chapters.addAll(deduplicateMangaChapters(rawFeedChapters, langFilter));
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

                // 2. Fetch from MangaDex At-Home Server if not offline and not builtin
                if (pages.isEmpty() && !chapter.getChapterId().startsWith("builtin_")) {
                    String atHomeUrl = "https://api.mangadex.org/at-home/server/" + chapter.getChapterId();
                    String json = httpGetString(atHomeUrl);
                    if (json != null) {
                        pages.addAll(parseAtHomePagesJson(json, dataSaver));
                    }
                }

                // 3. Fallback to procedural high-contrast manga pages if builtin or unreachable
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
                String desc = extractLocalizedString(attrs.optJSONObject("description"), "Манга из каталога MangaDex.");
                String statusRaw = attrs.optString("status", "ongoing");
                String status = "completed".equalsIgnoreCase(statusRaw) ? "Завершена" : "Выходит";

                // Genres / Tags
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

                // Extract cover_art and author from relationships
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

                // Skip external-only chapters that have 0 pages hosted on MangaDex
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

    private static String httpGetString(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(9000);
            conn.setReadTimeout(12000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "BookR/2.0 (Android Manga Reader)");
            conn.setRequestProperty("Accept", "application/json");

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
        java.util.Collections.sort(sorted, (c1, c2) -> {
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
                "a1c7c817-4e59-43b7-9365-09675a149a6f",
                "One Piece (Ван-Пис)",
                "Эйитиро Ода",
                "Легендарная сага о Манки Д. Луффи и его команде Пиратов Соломенной Шляпы в поисках величайшего сокровища мира — Ван-Пис.",
                "builtin://cover/one_piece",
                "Выходит",
                "Сёнен • Приключения • Фэнтези • Экшен"
        ));

        // 2. Berserk
        list.add(new MangaItem(
                "801513ba-a712-498c-8f57-415555724aab",
                "Berserk (Берсерк)",
                "Кэнтаро Миура",
                "Мрачный шедевр тёмного фэнтези о Чёрном Мечнике Гатсе, его борьбе с судьбой, демонами Руки Бога и предательством.",
                "builtin://cover/berserk",
                "Выходит",
                "Сейнен • Тёмное фэнтези • Драма • Трагедия"
        ));

        // 3. Chainsaw Man
        list.add(new MangaItem(
                "a77742b1-fad3-4238-9c6f-394887723254",
                "Chainsaw Man (Человек-бензопила)",
                "Тацуки Фудзимото",
                "Дэндзи заключает договор с демоническим псом Почитой и становится Человеком-бензопилой, охотясь на опасных демонов Токио.",
                "builtin://cover/chainsaw_man",
                "Выходит",
                "Сёнен • Экшен • Сверхъестественное • Триллер"
        ));

        // 4. Attack on Titan
        list.add(new MangaItem(
                "304ceac3-8cdb-492c-8839-c038a52460f3",
                "Attack on Titan (Атака титанов)",
                "Хадзимэ Исаяма",
                "Человечество живёт за тремя исполинскими стенами, спасаясь от пожирателей-титанов. Эрен Йегер клянётся истребить каждого из них.",
                "builtin://cover/aot",
                "Завершена",
                "Сёнен • Драма • Постапокалиптика • Мистика"
        ));

        // 5. Jujutsu Kaisen
        list.add(new MangaItem(
                "c52b2ce3-7f95-469c-96b0-479524fb7a1a",
                "Jujutsu Kaisen (Магическая битва)",
                "Гэгэ Акутами",
                "Старшеклассник Юдзи Итадори проглатывает палец короля проклятий Сукуны и вступает в Токийский магический колледж.",
                "builtin://cover/jujutsu",
                "Завершена",
                "Сёнен • Магия • Экшен • Сверхъестественное"
        ));

        // 6. Demon Slayer
        list.add(new MangaItem(
                "0c29f864-1e0e-4c8f-b989-13009bc87eb2",
                "Demon Slayer: Kimetsu no Yaiba (Клинок, рассекающий демонов)",
                "Коёхару Готогэ",
                "Тандзиро Камадо становится истребителем демонов, чтобы найти лекарство для сестры Недзуко, обращённой в демона.",
                "builtin://cover/demon_slayer",
                "Завершена",
                "Сёнен • Исторический • Экшен • Самураи"
        ));

        // 7. Solo Leveling
        list.add(new MangaItem(
                "32d76d19-8a05-4db0-9fc2-e0b0648fe9d0",
                "Solo Leveling (Поднятие уровня в одиночку)",
                "Chugong, DUBU",
                "Сон Джин-Ву, слабейший охотник E-ранга, получает тайную систему прокачки и начинает свой путь к вершине абсолютной силы.",
                "builtin://cover/solo_leveling",
                "Завершена",
                "Манхва • ЛитРПГ • Экшен • Фэнтези"
        ));

        // 8. Naruto
        list.add(new MangaItem(
                "6b1eb93e-473a-4ab3-9997-4d5a5704d944",
                "Naruto (Наруто)",
                "Масаси Кисимото",
                "История Наруто Узумаки — юного ниндзя деревни Скрытого Листа, мечтающего преодолеть одиночество и стать Хокагэ.",
                "builtin://cover/naruto",
                "Завершена",
                "Сёнен • Ниндзя • Боевые искусства • Приключения"
        ));

        // 9. Bleach
        list.add(new MangaItem(
                "51689254-2a62-42da-9195-236b281f6213",
                "Bleach (Блич)",
                "Тайто Кубо",
                "Ичиго Куросаки случайно получает силы проводника душ синигами и встаёт на защиту людей от пустых.",
                "builtin://cover/bleach",
                "Завершена",
                "Сёнен • Синигами • Сверхъестественное • Экшен"
        ));

        // 10. Death Note
        list.add(new MangaItem(
                "7b275bfb-7fa7-4f68-8098-b64800331fc3",
                "Death Note (Тетрадь смерти)",
                "Цугуми Оба, Такэси Обата",
                "Лайт Ягами находит тетрадь бога смерти Рюка, способную убивать любого человека по имени, и объявляет себя богом нового мира Кирой.",
                "builtin://cover/death_note",
                "Завершена",
                "Сейнен • Психологический триллер • Детектив • Мистика"
        ));

        // 11. Tokyo Ghoul
        list.add(new MangaItem(
                "11e6a928-a447-495c-9d04-46960d3d3b76",
                "Tokyo Ghoul (Токийский гуль)",
                "Суи Исида",
                "Кэн Канэки после пересадки органов становится наполовину гулем — существом, вынужденным питаться человеческой плотью.",
                "builtin://cover/tokyo_ghoul",
                "Завершена",
                "Сейнен • Тёмное фэнтези • Хоррор • Психология"
        ));

        // 12. Hunter x Hunter
        list.add(new MangaItem(
                "5a092822-1d54-4a4b-84a1-053ae5fb8328",
                "Hunter x Hunter (Охотник х Охотник)",
                "Ёсихиро Тогаси",
                "Гон Фрикс сдаёт экзамен на Охотника, чтобы отправиться в опасный мир на поиски своего отца Джина.",
                "builtin://cover/hxh",
                "Выходит",
                "Сёнен • Приключения • Боевые искусства • Фэнтези"
        ));

        // 13. Vinland Saga
        list.add(new MangaItem(
                "5d111aa7-3fd9-4375-9658-005d5236cf53",
                "Vinland Saga (Сага о Винланде)",
                "Макото Юкимура",
                "Историческая драма о Торфинне, юном воине-викинге, жаждущем отомстить за гибель отца и найти легендарную мирную землю Винланд.",
                "builtin://cover/vinland",
                "Выходит",
                "Сейнен • Исторический • Викинги • Драма"
        ));

        // 14. Spy x Family
        list.add(new MangaItem(
                "61a7a706-f89a-4c26-8367-8e68db359489",
                "Spy x Family (Семья шпиона)",
                "Тацуя Эндо",
                "Шпион «Сумрак» для выполнения миссии создаёт фиктивную семью: его приёмная дочь — телепат, а жена — наёмная убийца.",
                "builtin://cover/spy_family",
                "Выходит",
                "Комедия • Шпионаж • Сёнен • Повседневность"
        ));

        // 15. One Punch Man
        list.add(new MangaItem(
                "d8a959fb-e100-4375-acdc-6e46d153162a",
                "One Punch Man (Ванпанчмен)",
                "ONE, Юсукэ Мурата",
                "Сайтама стал настолько сильным, что побеждает любых монстров с одного удара, из-за чего страдает от скуки и экзистенциального кризиса.",
                "builtin://cover/opm",
                "Выходит",
                "Сёнен • Супергерои • Пародия • Экшен"
        ));

        // 16. Vagabond
        list.add(new MangaItem(
                "d3263063-e380-48e0-bb15-5fb74e201a47",
                "Vagabond (Бродяга)",
                "Такэхико Иноуэ",
                "Глубокая философская биография легендарного мастера меча Миямото Мусаси в эпоху становления сёгуната Токугава.",
                "builtin://cover/vagabond",
                "Завершена",
                "Сейнен • Самураи • Философия • История"
        ));

        // 17. Fullmetal Alchemist
        list.add(new MangaItem(
                "740fc513-39d6-4447-925a-4ba4e0d939c0",
                "Fullmetal Alchemist (Стальной алхимик)",
                "Хирому Аракава",
                "Братья Эдвард и Альфонс Элрики нарушают запрет человеческой трансмутации и отправляются на поиски философского камня.",
                "builtin://cover/fma",
                "Завершена",
                "Сёнен • Алхимия • Стимпанк • Драма"
        ));

        // 18. JoJo's Bizarre Adventure
        list.add(new MangaItem(
                "51859e99-4c17-4eb2-a634-8c887fbef711",
                "JoJo's Bizarre Adventure (ДжоДжо)",
                "Хирохико Араки",
                "Многовековая сага рода Джостаров, сражающихся против древнего зла и обладателей стендов с помощью смекалки и хамона.",
                "builtin://cover/jojo",
                "Выходит",
                "Сёнен • Стенды • Приключения • Экшен"
        ));

        // 19. Monster
        list.add(new MangaItem(
                "e4392473-b64a-4363-95cf-0e10b190f845",
                "Monster (Монстр)",
                "Наоки Урасава",
                "Нейрохирург Кэндзо Тэмма спасает жизнь тяжело раненного мальчика, не подозревая, что взрастил гениального психопата Йохана.",
                "builtin://cover/monster",
                "Завершена",
                "Сейнен • Психологический детектив • Триллер"
        ));

        // 20. Oshi no Ko
        list.add(new MangaItem(
                "2961b701-096b-4256-ba10-9f462eb9ef60",
                "Oshi no Ko (Звёздное дитя)",
                "Ака Акасака, Мэнго Ёкояри",
                "Драматический взгляд на изнанку японского шоу-бизнеса, айдолов, актёрской карьеры и жажды мести.",
                "builtin://cover/oshi_no_ko",
                "Завершена",
                "Сейнен • Драма • Шоу-бизнес • Мистика"
        ));

        // 21. Клинок Затмения
        list.add(new MangaItem(
                "builtin_blade_of_eclipse",
                "Клинок Затмения: Хроники Пепла",
                "Кэндзи Такахаси",
                "В мире, где солнце погасло триста лет назад, орден Ночных Стражей охраняет последние города от Пожирателей Теней. Молодой мечник Рэй находит древний клинок, способный рассекать саму тьму.",
                "builtin://cover/blade_eclipse",
                "Выходит",
                "Тёмное фэнтези • Экшен • Сэйнэн"
        ));

        // 22. Кибер-Токио 2099
        list.add(new MangaItem(
                "builtin_cyber_tokyo_2099",
                "Кибер-Токио 2099: Стеклянный Призрак",
                "Акира Сато",
                "Неоновый мегаполис под куполом вечного дождя. Детектив-нейрохакер расследует исчезновение архитектора искусственного сознания и выходит на след тайного синдиката.",
                "builtin://cover/cyber_tokyo",
                "Выходит",
                "Киберпанк • Детектив • Фантастика"
        ));

        // 23. Башня Белого Алхимика
        list.add(new MangaItem(
                "builtin_alchemist_tower",
                "Башня Белого Алхимика",
                "Харука Мидзуно",
                "Сто этажей Древней Башни скрывают секрет философского камня. Юный алхимик Элрик начинает восхождение с первого уровня, полагаясь лишь на знания древних формул и верных друзей.",
                "builtin://cover/alchemist_tower",
                "Завершена",
                "Приключения • Алхимия • Фэнтези"
        ));

        // 24. Путь Безмолвного Ронина
        list.add(new MangaItem(
                "builtin_silent_samurai",
                "Путь Безмолвного Ронина",
                "Масахиро Куросава",
                "Философская самурайская драма об одиноком мастере меча, странствующем по заснеженным провинциям эпохи Эдо в поисках покоя и искупления.",
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

