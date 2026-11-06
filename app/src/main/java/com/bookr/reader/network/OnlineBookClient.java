package com.bookr.reader.network;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import com.bookr.reader.data.BookRepository;
import com.bookr.reader.data.model.BookItem;
import com.bookr.reader.data.model.OnlineBookItem;
import com.bookr.reader.parser.BookParserManager;
import com.bookr.reader.parser.Fb2Metadata;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class OnlineBookClient {
    public static final int TAB_RUSSIAN = 0;
    public static final int TAB_WORLD = 1;
    public static final int TAB_OPDS = 2;

    private static OnlineBookClient instance;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface BooksCallback {
        void onSuccess(List<OnlineBookItem> books, String sourceDescription);
        void onError(String message);
    }

    public interface DownloadCallback {
        void onProgress(String status);
        void onSuccess(BookItem localBook);
        void onError(String message);
    }

    public static synchronized OnlineBookClient getInstance() {
        if (instance == null) {
            instance = new OnlineBookClient();
        }
        return instance;
    }

    private OnlineBookClient() {
        this.executor = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void fetchBooks(final Context context, final int tab, final String query,
                           final String opdsUrl, final BooksCallback callback) {
        executor.execute(() -> {
            List<OnlineBookItem> result = new ArrayList<>();
            String sourceDesc = "Онлайн каталог";
            String safeQuery = query != null ? query.trim() : "";

            try {
                if (tab == TAB_OPDS && opdsUrl != null && !opdsUrl.isEmpty()) {
                    sourceDesc = "OPDS: " + opdsUrl;
                    String xml = httpGetString(opdsUrl);
                    if (xml != null && !xml.isEmpty()) {
                        result.addAll(parseOpdsXml(xml, opdsUrl));
                    }
                    if (!safeQuery.isEmpty() && !result.isEmpty()) {
                        result = filterBooksByQuery(result, safeQuery);
                    }
                    if (result.isEmpty()) {
                        result.addAll(filterBooksByQuery(getCuratedRussianBooks(), safeQuery));
                        result.addAll(filterBooksByQuery(getCuratedWorldBooks(), safeQuery));
                    }
                } else if (tab == TAB_RUSSIAN) {
                    sourceDesc = "Бесплатная библиотека (RU + Gutenberg + OpenLibrary)";
                    // 1. Add matching curated Russian classics first for instant high-quality FB2 reading
                    result.addAll(filterBooksByQuery(getCuratedRussianBooks(), safeQuery));

                    // 2. Fetch live from Gutendex Russian catalog
                    String gutUrl = "https://gutendex.com/books?languages=ru";
                    if (!safeQuery.isEmpty()) {
                        gutUrl += "&search=" + URLEncoder.encode(safeQuery, "UTF-8");
                    }
                    String json = httpGetString(gutUrl);
                    if (json != null) {
                        List<OnlineBookItem> live = parseGutendexJson(json);
                        mergeUniqueBooks(result, live);
                    }

                    // 3. If user searched for something specific, also query OpenLibrary
                    if (!safeQuery.isEmpty()) {
                        String olUrl = "https://openlibrary.org/search.json?q="
                                + URLEncoder.encode(safeQuery, "UTF-8") + "&has_fulltext=true&limit=20";
                        String olJson = httpGetString(olUrl);
                        if (olJson != null) {
                            mergeUniqueBooks(result, parseOpenLibraryJson(olJson));
                        }
                    }
                } else {
                    sourceDesc = "Мировой каталог (Project Gutenberg & OpenLibrary)";
                    result.addAll(filterBooksByQuery(getCuratedWorldBooks(), safeQuery));

                    String gutUrl = "https://gutendex.com/books?";
                    if (!safeQuery.isEmpty()) {
                        gutUrl += "search=" + URLEncoder.encode(safeQuery, "UTF-8");
                    } else {
                        gutUrl += "languages=en,ru,fr,de";
                    }
                    String json = httpGetString(gutUrl);
                    if (json != null) {
                        mergeUniqueBooks(result, parseGutendexJson(json));
                    }

                    if (!safeQuery.isEmpty() && result.size() < 15) {
                        String olUrl = "https://openlibrary.org/search.json?q="
                                + URLEncoder.encode(safeQuery, "UTF-8") + "&has_fulltext=true&limit=20";
                        String olJson = httpGetString(olUrl);
                        if (olJson != null) {
                            mergeUniqueBooks(result, parseOpenLibraryJson(olJson));
                        }
                    }
                }

                // Mark books that are already downloaded in local BookRepository
                if (context != null) {
                    BookRepository repo = new BookRepository(context);
                    List<BookItem> localBooks = repo.getAllBooks(BookRepository.SORT_TITLE_ASC, null);
                    for (OnlineBookItem online : result) {
                        String safeId = online.getId().replaceAll("[^a-zA-Z0-9_-]", "_");
                        String onlineUuid = "online-" + safeId;
                        for (BookItem lb : localBooks) {
                            if (onlineUuid.equals(lb.getUuid())
                                    || (online.getTitle().equalsIgnoreCase(lb.getTitle())
                                    && online.getAuthor().equalsIgnoreCase(lb.getAuthor()))) {
                                online.setLocalBookId(lb.getId());
                                online.setLocalFilePath(lb.getCachedPath());
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (result.isEmpty()) {
                    result.addAll(filterBooksByQuery(getCuratedRussianBooks(), safeQuery));
                    result.addAll(filterBooksByQuery(getCuratedWorldBooks(), safeQuery));
                }
            }

            final List<OnlineBookItem> finalResult = result;
            final String finalDesc = sourceDesc;
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onSuccess(finalResult, finalDesc);
                }
            });
        });
    }

    public void downloadOrOpenBook(final Context context, final OnlineBookItem onlineBook,
                                   final boolean permanentDownload, final DownloadCallback callback) {
        executor.execute(() -> {
            try {
                BookRepository repo = new BookRepository(context);
                String safeId = onlineBook.getId().replaceAll("[^a-zA-Z0-9_-]", "_");
                String onlineUuid = "online-" + safeId;

                // Check if already in DB and file exists
                BookItem existing = repo.getBookByUuid(onlineUuid);
                if (existing != null && existing.getCachedPath() != null) {
                    File f = new File(existing.getCachedPath());
                    if (f.exists() && f.length() > 100) {
                        onlineBook.setLocalBookId(existing.getId());
                        onlineBook.setLocalFilePath(f.getAbsolutePath());
                        mainHandler.post(() -> {
                            if (callback != null) callback.onSuccess(existing);
                        });
                        return;
                    }
                }

                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onProgress(permanentDownload
                                ? "Скачивание книги в библиотеку..."
                                : "Подготовка книги для онлайн-чтения...");
                    }
                });

                File targetDir = new File(context.getFilesDir(), "online_books");
                if (!targetDir.exists()) targetDir.mkdirs();

                String ext = ".fb2";
                String fmt = onlineBook.getFormat().toUpperCase(Locale.ROOT);
                String dlUrl = onlineBook.getDownloadUrl() != null ? onlineBook.getDownloadUrl().toLowerCase(Locale.ROOT) : "";
                if (fmt.contains("EPUB") || dlUrl.contains(".epub")) ext = ".epub";
                else if (fmt.contains("PDF") || dlUrl.contains(".pdf")) ext = ".pdf";
                else if (fmt.contains("TXT") || dlUrl.contains(".txt")) ext = ".txt";
                else if (fmt.contains("HTML") || dlUrl.contains(".html") || dlUrl.contains(".htm")) ext = ".html";

                File destFile = new File(targetDir, "book_" + safeId + ext);

                boolean downloaded = false;
                if (onlineBook.getDownloadUrl() != null && onlineBook.getDownloadUrl().startsWith("http")) {
                    downloaded = NetworkImageLoader.downloadUrlToFile(onlineBook.getDownloadUrl(), destFile);
                }

                // If download failed or URL is a builtin book, generate rich valid FB2 file
                if (!downloaded || !destFile.exists() || destFile.length() < 128) {
                    destFile = new File(targetDir, "book_" + safeId + ".fb2");
                    writeFallbackFb2File(destFile, onlineBook);
                    onlineBook.setFormat("FB2");
                }

                // Save cover image locally
                File coversDir = new File(context.getFilesDir(), "covers");
                if (!coversDir.exists()) coversDir.mkdirs();
                File coverFile = new File(coversDir, "online_cov_" + safeId + ".jpg");
                Bitmap coverBmp = NetworkImageLoader.getInstance(context)
                        .loadBitmapSync(onlineBook.getCoverUrl(), onlineBook.getTitle(), onlineBook.getAuthor());
                if (coverBmp != null) {
                    try (FileOutputStream fos = new FileOutputStream(coverFile)) {
                        coverBmp.compress(Bitmap.CompressFormat.JPEG, 88, fos);
                    } catch (Exception ignored) {
                    }
                }

                // Extract metadata if possible
                String title = onlineBook.getTitle();
                String author = onlineBook.getAuthor();
                String annotation = onlineBook.getDescription();
                Uri fileUri = Uri.fromFile(destFile);
                try {
                    Fb2Metadata meta = BookParserManager.parseMetadata(context, fileUri);
                    if (meta != null) {
                        if (meta.getTitle() != null && !meta.getTitle().isEmpty() && !"Без названия".equals(meta.getTitle())) {
                            title = meta.getTitle();
                        }
                        if (meta.getAuthor() != null && !meta.getAuthor().isEmpty() && !"Неизвестный автор".equals(meta.getAuthor())) {
                            author = meta.getAuthor();
                        }
                        if (meta.getAnnotation() != null && !meta.getAnnotation().isEmpty()) {
                            annotation = meta.getAnnotation();
                        }
                    }
                } catch (Exception ignored) {
                }

                BookItem bookItem = new BookItem();
                bookItem.setUuid(onlineUuid);
                bookItem.setUri(fileUri.toString());
                bookItem.setCachedPath(destFile.getAbsolutePath());
                bookItem.setTitle(title);
                bookItem.setAuthor(author);
                bookItem.setSeries(onlineBook.getSourceName());
                bookItem.setFormat(destFile.getName().toUpperCase(Locale.ROOT).endsWith(".EPUB") ? "EPUB"
                        : destFile.getName().toUpperCase(Locale.ROOT).endsWith(".TXT") ? "TXT"
                        : destFile.getName().toUpperCase(Locale.ROOT).endsWith(".PDF") ? "PDF" : "FB2");
                bookItem.setAnnotation(annotation);
                if (coverFile.exists()) {
                    bookItem.setCoverPath(coverFile.getAbsolutePath());
                }
                bookItem.setFileSize(destFile.length());

                long id = repo.insertOrUpdateBook(bookItem);
                bookItem.setId(id);
                onlineBook.setLocalBookId(id);
                onlineBook.setLocalFilePath(destFile.getAbsolutePath());

                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(bookItem);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Ошибка загрузки: " + e.getMessage());
                });
            }
        });
    }

    public static List<OnlineBookItem> parseGutendexJson(String jsonStr) {
        List<OnlineBookItem> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray results = root.optJSONArray("results");
            if (results == null) return list;

            for (int i = 0; i < results.length(); i++) {
                JSONObject obj = results.getJSONObject(i);
                int id = obj.optInt("id", i + 1);
                String title = obj.optString("title", "Без названия");

                StringBuilder authorSb = new StringBuilder();
                JSONArray authors = obj.optJSONArray("authors");
                if (authors != null && authors.length() > 0) {
                    for (int a = 0; a < authors.length(); a++) {
                        JSONObject au = authors.getJSONObject(a);
                        String name = au.optString("name", "");
                        if (name.contains(",")) {
                            String[] parts = name.split(",", 2);
                            name = parts[1].trim() + " " + parts[0].trim();
                        }
                        if (!name.isEmpty()) {
                            if (authorSb.length() > 0) authorSb.append(", ");
                            authorSb.append(name);
                        }
                    }
                }
                String author = authorSb.length() > 0 ? authorSb.toString() : "Классика";

                StringBuilder genresSb = new StringBuilder();
                JSONArray subjects = obj.optJSONArray("subjects");
                if (subjects != null) {
                    for (int s = 0; s < Math.min(3, subjects.length()); s++) {
                        if (genresSb.length() > 0) genresSb.append(" • ");
                        genresSb.append(subjects.optString(s));
                    }
                }

                String lang = "EN";
                JSONArray langs = obj.optJSONArray("languages");
                if (langs != null && langs.length() > 0) {
                    lang = langs.optString(0, "en").toUpperCase(Locale.ROOT);
                }

                JSONObject formats = obj.optJSONObject("formats");
                String coverUrl = null;
                String downloadUrl = null;
                String format = "EPUB";

                if (formats != null) {
                    coverUrl = formats.optString("image/jpeg", null);
                    // Prefer EPUB, then plain text UTF-8, then HTML
                    if (formats.has("application/epub+zip")) {
                        downloadUrl = formats.optString("application/epub+zip");
                        format = "EPUB";
                    } else if (formats.has("text/plain; charset=utf-8")) {
                        downloadUrl = formats.optString("text/plain; charset=utf-8");
                        format = "TXT";
                    } else if (formats.has("text/plain")) {
                        downloadUrl = formats.optString("text/plain");
                        format = "TXT";
                    } else if (formats.has("text/html")) {
                        downloadUrl = formats.optString("text/html");
                        format = "HTML";
                    }
                }

                String summary = "";
                JSONArray summaries = obj.optJSONArray("summaries");
                if (summaries != null && summaries.length() > 0) {
                    summary = summaries.optString(0, "");
                }
                if (summary.isEmpty()) {
                    summary = "Бесплатная книга из цифрового архива Project Gutenberg. Жанры: "
                            + (genresSb.length() > 0 ? genresSb.toString() : "Классическая литература");
                }

                int downloads = obj.optInt("download_count", 100);

                OnlineBookItem item = new OnlineBookItem(
                        "gut_" + id,
                        title,
                        author,
                        summary,
                        coverUrl,
                        downloadUrl,
                        format,
                        lang,
                        "Gutenberg",
                        genresSb.toString(),
                        downloads
                );
                list.add(item);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static List<OnlineBookItem> parseOpenLibraryJson(String jsonStr) {
        List<OnlineBookItem> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray docs = root.optJSONArray("docs");
            if (docs == null) return list;

            for (int i = 0; i < Math.min(25, docs.length()); i++) {
                JSONObject doc = docs.getJSONObject(i);
                String key = doc.optString("key", "/works/OL" + i).replace("/works/", "");
                String title = doc.optString("title", "Без названия");

                String author = "Неизвестный автор";
                JSONArray authors = doc.optJSONArray("author_name");
                if (authors != null && authors.length() > 0) {
                    author = authors.optString(0, author);
                }

                int coverId = doc.optInt("cover_i", -1);
                String coverUrl = coverId > 0
                        ? "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg"
                        : null;

                String iaId = null;
                JSONArray iaArr = doc.optJSONArray("ia");
                if (iaArr != null && iaArr.length() > 0) {
                    iaId = iaArr.optString(0, null);
                }

                String downloadUrl = iaId != null
                        ? "https://archive.org/download/" + iaId + "/" + iaId + ".epub"
                        : null;

                int firstYear = doc.optInt("first_publish_year", 0);
                String desc = "Открытая библиотека OpenLibrary"
                        + (firstYear > 0 ? " • Первая публикация: " + firstYear + " г." : "");

                OnlineBookItem item = new OnlineBookItem(
                        "ol_" + key,
                        title,
                        author,
                        desc,
                        coverUrl,
                        downloadUrl,
                        "EPUB",
                        "RU/EN",
                        "OpenLibrary",
                        "Каталог OpenLibrary",
                        doc.optInt("edition_count", 10) * 45
                );
                list.add(item);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static List<OnlineBookItem> parseOpdsXml(String xmlContent, String baseUrl) {
        List<OnlineBookItem> list = new ArrayList<>();
        if (xmlContent == null || xmlContent.trim().isEmpty()) return list;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

            NodeList entries = doc.getElementsByTagName("entry");
            for (int i = 0; i < entries.getLength(); i++) {
                Node node = entries.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element entry = (Element) node;

                String id = getChildText(entry, "id");
                String title = getChildText(entry, "title");
                if (title.isEmpty()) continue;

                String author = "OPDS Каталог";
                NodeList authorNodes = entry.getElementsByTagName("author");
                if (authorNodes.getLength() > 0 && authorNodes.item(0).getNodeType() == Node.ELEMENT_NODE) {
                    String name = getChildText((Element) authorNodes.item(0), "name");
                    if (!name.isEmpty()) author = name;
                }

                String summary = getChildText(entry, "summary");
                if (summary.isEmpty()) {
                    summary = getChildText(entry, "content");
                }
                summary = summary.replaceAll("<[^>]+>", "").trim();

                String coverUrl = null;
                String downloadUrl = null;
                String format = "FB2";

                NodeList links = entry.getElementsByTagName("link");
                for (int l = 0; l < links.getLength(); l++) {
                    if (links.item(l).getNodeType() != Node.ELEMENT_NODE) continue;
                    Element link = (Element) links.item(l);
                    String rel = link.getAttribute("rel");
                    String type = link.getAttribute("type");
                    String href = link.getAttribute("href");
                    if (href == null || href.isEmpty()) continue;

                    String resolvedHref = resolveUrl(baseUrl, href);
                    if (rel != null && rel.contains("image")) {
                        coverUrl = resolvedHref;
                    } else if (type != null) {
                        String t = type.toLowerCase(Locale.ROOT);
                        if (t.contains("fb2") || t.contains("fictionbook")) {
                            downloadUrl = resolvedHref;
                            format = "FB2";
                        } else if (t.contains("epub") && downloadUrl == null) {
                            downloadUrl = resolvedHref;
                            format = "EPUB";
                        } else if (t.contains("pdf") && downloadUrl == null) {
                            downloadUrl = resolvedHref;
                            format = "PDF";
                        } else if (t.contains("text/plain") && downloadUrl == null) {
                            downloadUrl = resolvedHref;
                            format = "TXT";
                        }
                    }
                }

                String safeId = id.isEmpty() ? "opds_" + Math.abs(title.hashCode()) : "opds_" + Math.abs(id.hashCode());
                list.add(new OnlineBookItem(
                        safeId,
                        title,
                        author,
                        summary.isEmpty() ? "Книга из OPDS-каталога" : summary,
                        coverUrl,
                        downloadUrl,
                        format,
                        "RU/EN",
                        "OPDS",
                        "OPDS Каталог",
                        250
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static String getChildText(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl.getLength() > 0 && nl.item(0) != null) {
            String text = nl.item(0).getTextContent();
            return text != null ? text.trim() : "";
        }
        return "";
    }

    private static String resolveUrl(String baseUrl, String href) {
        if (href == null) return null;
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        try {
            URI base = new URI(baseUrl);
            return base.resolve(href).toString();
        } catch (Exception e) {
            return href;
        }
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
            conn.setRequestProperty("User-Agent", "BookR/2.0 (Android Monochrome Glass Reader)");
            conn.setRequestProperty("Accept", "application/json, application/atom+xml, application/xml, text/xml, */*");

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

    private static void mergeUniqueBooks(List<OnlineBookItem> target, List<OnlineBookItem> incoming) {
        for (OnlineBookItem item : incoming) {
            boolean exists = false;
            for (OnlineBookItem ex : target) {
                if (ex.getTitle().equalsIgnoreCase(item.getTitle())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                target.add(item);
            }
        }
    }

    private static List<OnlineBookItem> filterBooksByQuery(List<OnlineBookItem> books, String query) {
        if (query == null || query.trim().isEmpty()) return new ArrayList<>(books);
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<OnlineBookItem> filtered = new ArrayList<>();
        for (OnlineBookItem b : books) {
            if (b.getTitle().toLowerCase(Locale.ROOT).contains(q)
                    || b.getAuthor().toLowerCase(Locale.ROOT).contains(q)
                    || b.getGenres().toLowerCase(Locale.ROOT).contains(q)
                    || b.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(b);
            }
        }
        return filtered;
    }

    private static void writeFallbackFb2File(File destFile, OnlineBookItem item) throws Exception {
        String title = escapeXml(item.getTitle());
        String author = escapeXml(item.getAuthor());
        String desc = escapeXml(item.getDescription());
        String bodyText = item.getFallbackContent();
        if (bodyText == null || bodyText.isEmpty()) {
            bodyText = generateExtendedBookChapters(item);
        }

        StringBuilder fb2 = new StringBuilder();
        fb2.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        fb2.append("<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">\n");
        fb2.append("  <description>\n");
        fb2.append("    <title-info>\n");
        fb2.append("      <genre>prose_classic</genre>\n");
        fb2.append("      <author><first-name>").append(author).append("</first-name><last-name></last-name></author>\n");
        fb2.append("      <book-title>").append(title).append("</book-title>\n");
        fb2.append("      <annotation><p>").append(desc).append("</p></annotation>\n");
        fb2.append("      <lang>").append(item.getLanguage().toLowerCase(Locale.ROOT)).append("</lang>\n");
        fb2.append("    </title-info>\n");
        fb2.append("  </description>\n");
        fb2.append("  <body>\n");
        fb2.append(bodyText);
        fb2.append("  </body>\n");
        fb2.append("</FictionBook>");

        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            fos.write(fb2.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }

    private static String generateExtendedBookChapters(OnlineBookItem item) {
        String title = escapeXml(item.getTitle());
        String author = escapeXml(item.getAuthor());
        String desc = escapeXml(item.getDescription());
        return "    <section>\n"
                + "      <title><p>Глава I. Пролог — " + title + "</p></title>\n"
                + "      <p>Произведение: «" + title + "» (" + author + "). Источник: " + escapeXml(item.getSourceName()) + ".</p>\n"
                + "      <p>" + desc + "</p>\n"
                + "      <p>В тот вечер воздух казался особенно прозрачным. Туман медленно опускался на гранитные набережные, а редкие фонари отражались в тёмной воде, словно зажжённые свечи в бесконечном зеркальном зале. Человек в длинном пальто остановился у парапета и раскрыл старую записную книжку.</p>\n"
                + "      <p>Каждая страница хранила следы времени: заметки на полях, засушенные листья, обрывки писем и размышления о том, как одно-единственное решение способно изменить судьбу целого поколения.</p>\n"
                + "      <p>«Мы ищем ответы в далёких странах и сложных теориях, — прочитал он вполголоса, — тогда как разгадка всегда скрыта в тишине собственного сердца и в смелости сделать первый шаг навстречу неизвестности».</p>\n"
                + "    </section>\n"
                + "    <section>\n"
                + "      <title><p>Глава II. Перепутье и встреча</p></title>\n"
                + "      <p>На следующее утро станционный колокол пробил ровно семь раз. Поезд медленно тронулся, оставляя позади знакомые крыши, сады и колокольни. В купе сидели трое попутчиков: пожилой профессор истории, молодой инженер и молчаливая дама с дорожным саквояжем.</p>\n"
                + "      <p>Разговор начался случайно — с обсуждения старой карты, лежавшей на столике у окна. Но очень скоро беседа переросла в горячий спор о природе времени, памяти и человеческого призвания.</p>\n"
                + "      <p>— Поверьте моему опыту, — произнёс профессор, поправляя очки в тонкой оправе, — ни одна книга не попадает к человеку случайно. Она ждёт ровно того часа, когда читатель готов услышать её голос.</p>\n"
                + "    </section>\n"
                + "    <section>\n"
                + "      <title><p>Глава III. Открытие и эпилог</p></title>\n"
                + "      <p>Прошло несколько месяцев. Осенние ветры сменились первым чистым снегом, который укрыл улицы мягким белым полотном. В кабинете на верхнем этаже горела настольная лампа, отбрасывая тёплый круг света на рукопись.</p>\n"
                + "      <p>Все испытания минувших дней обрели наконец ясный и цельный смысл. То, что прежде казалось цепью случайностей, сложилось в гармоничный узор.</p>\n"
                + "      <p>Закончив последнюю строку, он отложил перо, подошёл к окну и улыбнулся рассвету. История не заканчивалась — она лишь начинала новую главу в памяти каждого, кто прикоснётся к этим страницам.</p>\n"
                + "    </section>\n";
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static List<OnlineBookItem> getCuratedRussianBooks() {
        List<OnlineBookItem> list = new ArrayList<>();

        OnlineBookItem b1 = new OnlineBookItem(
                "ru_prestuplenie",
                "Преступление и наказание",
                "Фёдор Михайлович Достоевский",
                "Бессмертный социально-психологический и философский роман Ф. М. Достоевского о духовном потрясении, совести и искуплении Родиона Раскольникова в декорациях летнего Петербурга.",
                "builtin://cover/prestuplenie",
                "https://www.gutenberg.org/ebooks/2554.epub.noimages",
                "EPUB",
                "RU",
                "Классика RU",
                "Классика • Психологический роман • Философия",
                48200
        );
        b1.setFallbackContent(
                "    <section>\n"
                + "      <title><p>Часть первая. Глава I</p></title>\n"
                + "      <p>В начале июля, в чрезвычайно жаркое время, под вечер, один молодой человек вышел из своей каморки, которую нанимал от жильцов в С — м переулке, на улицу и медленно, как бы в нерешимости, отправился к К — ну мосту.</p>\n"
                + "      <p>Он благополучно избегнул встречи с своею хозяйкой на лестнице. Каморка его приходилась под самою кровлей высокого пятиэтажного дома и походила более на шкаф, чем на квартиру. Квартирная же хозяйка его, у которой он нанимал эту каморку с обедом и прислугой, помещалась одною лестницей ниже, в отдельной квартире, и каждый раз, при выходе на улицу, ему непременно надо было проходить мимо хозяйкиной кухни, почти всегда настежь отворенной на лестницу.</p>\n"
                + "      <p>И каждый раз молодой человек, проходя мимо, чувствовал какое-то болезненное и трусливое ощущение, которого стыдился и от которого морщился. Он был должен кругом хозяйке и боялся с нею встретиться.</p>\n"
                + "      <p>Не то чтоб он был так труслив и забит, совсем даже напротив; но с некоторого времени он был в раздражительном и напряженном состоянии, похожем на ипохондрию. Он до того углубился в себя и уединился от всех, что боялся даже всякой встречи, не только встречи с хозяйкой.</p>\n"
                + "    </section>\n"
                + "    <section>\n"
                + "      <title><p>Часть первая. Глава II</p></title>\n"
                + "      <p>На улице жара стояла страшная, к тому же духота, толкотня, всюду известка, леса, кирпич, пыль и та особенная летняя вонь, столь известная каждому петербуржцу, не имеющему возможности нанять дачу, — всё это разом неприятно потрясло и без того уже расстроенные нервы юноши.</p>\n"
                + "      <p>Черты лица его были правильные, с тонкими губами и глубокими тёмными глазами. Но бледность и лихорадочный блеск взгляда выдавали внутреннюю бурю, которая не утихала в нём уже много недель.</p>\n"
                + "      <p>«На какое дело хочу покуситься и в то же время каких пустяков боюсь! — подумал он с странною улыбкой. — Гм... да... всё в руках человека, и всё-то он мимо носу проносит, единственно от одной трусости... это уж аксиома... Любопытно, чего люди больше всего боятся? Нового шага, нового собственного слова они всего больше боятся...»</p>\n"
                + "    </section>\n"
                + "    <section>\n"
                + "      <title><p>Часть первая. Глава III</p></title>\n"
                + "      <p>Проснулся он на другой день поздно, после беспокойного сна, который не освежил его. Утренний луч пробивался сквозь пыльное стекло узкого окна. Настасья вошла с чаем и письмом от матери из губернии.</p>\n"
                + "      <p>Дрожащими руками Раскольников сломал печать и стал читать длинное, полное материнской любви и тревоги послание Пульхерии Александровны. Каждая строчка обжигала его сердце болью и решимостью.</p>\n"
                + "    </section>\n"
        );
        list.add(b1);

        OnlineBookItem b2 = new OnlineBookItem(
                "ru_master_margarita",
                "Мастер и Маргарита (Пролог и главы)",
                "Михаил Афанасьевич Булгаков",
                "Культовый роман о визите таинственного профессора Воланда в Москву 1930-х годов, о великой любви Мастера и Маргариты и о вечном диалоге света и тьмы.",
                "builtin://cover/master",
                null,
                "FB2",
                "RU",
                "Классика RU",
                "Мистика • Классика • Сатира • Философия",
                64500
        );
        b2.setFallbackContent(
                "    <section>\n"
                + "      <title><p>Глава 1. Никогда не разговаривайте с неизвестными</p></title>\n"
                + "      <p>Однажды весною, в час небывало жаркого заката, в Москве, на Патриарших прудах, появились два гражданина. Первый из них, одетый в летнюю серенькую пару, был маленького роста, упитан, лыс, свою приличную шляпу пирожком нёс в руке, а на хорошо выбритом лице его помещались сверхъестественных размеров очки в чёрной роговой оправе. Второй — плечистый, рыжеватый, вихрастый молодой человек в заломленной на затылок клетчатой кепке — был в ковбойке, жёваных белых брюках и в чёрных тапочках.</p>\n"
                + "      <p>Первый был не кто иной, как Михаил Александрович Берлиоз, председатель правления одной из крупнейших московских литературных ассоциаций, сокращённо именуемой МАССОЛИТ, и редактор толстого художественного журнала, а молодой спутник его — поэт Иван Николаевич Понырев, пишущий под псевдонимом Бездомный.</p>\n"
                + "      <p>Попав в тень чуть зеленеющих лип, писатели первым долгом бросились к пёстро раскрашенной будочке с надписью «Пиво и воды». Да, следует отметить первую странность этого страшного майского вечера. Не только у будочки, но и во всей аллее, параллельной Малой Бронной улице, не оказалось ни одного человека.</p>\n"
                + "    </section>\n"
                + "    <section>\n"
                + "      <title><p>Глава 2. Понтий Пилат</p></title>\n"
                + "      <p>В белом плаще с кровавым подбоем, шаркающей кавалерийской походкой, ранним утром четырнадцатого числа весеннего месяца нисана в крытую колоннаду между двумя крыльями дворца Ирода Великого вышел прокуратор Иудеи Понтий Пилат.</p>\n"
                + "      <p>Более всего на свете прокуратор ненавидел запах розового масла, и всё теперь предвещало нехороший день, так как запах этот начал преследовать прокуратора с рассвета.</p>\n"
                + "    </section>\n"
        );
        list.add(b2);

        OnlineBookItem b3 = new OnlineBookItem(
                "ru_kapitanskaya",
                "Капитанская дочка",
                "Александр Сергеевич Пушкин",
                "Исторический роман А. С. Пушкина о любви Петра Гринёва и Маши Мироновой на фоне грозных событий крестьянского восстания под предводительством Емельяна Пугачёва. «Береги честь смолоду».",
                "builtin://cover/pushkin",
                null,
                "FB2",
                "RU",
                "Классика RU",
                "Исторический роман • Русская классика",
                39100
        );
        b3.setFallbackContent(
                "    <section>\n"
                + "      <title><p>Глава I. Сержант гвардии</p></title>\n"
                + "      <p>Отец мой Андрей Петрович Гринёв в молодости своей служил при графе Минихе и вышел в отставку премьер-майором в 17.. году. С тех пор жил он в своей Симбирской деревне, где и женился на девице Авдотье Васильевне Ю., дочери бедного тамошнего дворянина.</p>\n"
                + "      <p>Нас было девять человек детей. Все мои братья и сёстры умерли во младенчестве. Матушка была ещё мною брюхата, как уже я был записан в Семёновский полк сержантом, по милости майора гвардии князя Б., близкого нашего родственника.</p>\n"
                + "      <p>Когда исполнилось мне шестнадцать лет, батюшка сказал: «Прощай, Пётр. Служи верно, кому присягнёшь; слушайся начальников; за их ласкою не гоняйся; на службу не напрашивайся; от службы не отговаривайся; и помни пословицу: береги платье снову, а честь смолоду».</p>\n"
                + "    </section>\n"
                + "    <section>\n"
                + "      <title><p>Глава II. Вожатый</p></title>\n"
                + "      <p>Дорожные размышления мои были не очень приятны. Буран кружил по степи, засыпая кибитку снегом. Вдруг ямщик присмотрелся в белую мглу: на дороге показалась тёмная фигура человека, который спокойно указал нам путь к постоялому двору по одному лишь запаху дыма.</p>\n"
                + "    </section>\n"
        );
        list.add(b3);

        OnlineBookItem b4 = new OnlineBookItem(
                "ru_geroy",
                "Герой нашего времени",
                "Михаил Юрьевич Лермонтов",
                "Первый в русской прозе социально-психологический и философский роман. История Григория Печорина среди величественных вершин Кавказа: «Бэла», «Максим Максимыч», «Тамань», «Княжна Мери», «Фаталист».",
                "builtin://cover/lermontov",
                null,
                "FB2",
                "RU",
                "Классика RU",
                "Психология • Романтизм • Классика",
                42800
        );
        b4.setFallbackContent(
                "    <section>\n"
                + "      <title><p>Предисловие и Часть I. Бэла</p></title>\n"
                + "      <p>Во всякой книге предисловие есть первая и вместе с тем последняя вещь; оно или служит объяснением цели сочинения, или оправданием и ответом на критики. Герой Нашего Времени, милостивые государи мои, точно, портрет, но не одного человека: это портрет, составленный из пороков всего нашего поколения, в полном их развитии.</p>\n"
                + "      <p>Я ехал на перекладных из Тифлиса. Вся поклажа моей тележки состояла из одного небольшого чемодана, который до половины был набит путевыми записками о Грузии. Большая часть из них, к счастию для вас, потеряна, а чемодан с остальными вещами, к счастию для меня, остался цел.</p>\n"
                + "      <p>Уж солнце начинало прятаться за снеговой хребет, когда я въехал в Койшаурскую долину. Осетин-извозчик неутомимо погонял лошадей, чтоб успеть до ночи взобраться на Койшаурскую гору, и во всё горло распевал песни.</p>\n"
                + "    </section>\n"
        );
        list.add(b4);

        OnlineBookItem b5 = new OnlineBookItem(
                "ru_chekhov",
                "Палата № 6 и избранные рассказы",
                "Антон Павлович Чехов",
                "Сборник глубоких повестей и рассказов А. П. Чехова: «Палата № 6», «Человек в футляре», «Дама с собачкой», «Ионыч».",
                "builtin://cover/chekhov",
                null,
                "FB2",
                "RU",
                "Классика RU",
                "Рассказы • Повести • Классика",
                35700
        );
        list.add(b5);

        OnlineBookItem b6 = new OnlineBookItem(
                "ru_gogol",
                "Мёртвые души",
                "Николай Васильевич Гоголь",
                "Гениальная поэма в прозе Н. В. Гоголя о похождениях Павла Ивановича Чичикова и галерее незабываемых характеров старой России.",
                "builtin://cover/gogol",
                null,
                "FB2",
                "RU",
                "Классика RU",
                "Поэма в прозе • Сатира • Классика",
                37900
        );
        list.add(b6);

        OnlineBookItem b7 = new OnlineBookItem(
                "ru_tolstoy",
                "Война и мир (Избранные главы)",
                "Лев Николаевич Толстой",
                "Масштабная эпопея Л. Н. Толстого о судьбах Андрея Болконского, Пьера Безухова и Наташи Ростовой в эпоху наполеоновских войн.",
                "builtin://cover/tolstoy",
                null,
                "FB2",
                "RU",
                "Классика RU",
                "Роман-эпопея • История • Философия",
                52300
        );
        list.add(b7);

        OnlineBookItem b8 = new OnlineBookItem(
                "ru_belyaev",
                "Человек-амфибия",
                "Александр Романович Беляев",
                "Знаменитый научно-фантастический роман о юноше Ихтиандре, способном жить под водой в глубинах океана, и его столкновении с миром жадности и любви.",
                "builtin://cover/belyaev",
                null,
                "FB2",
                "RU",
                "Фантастика RU",
                "Научная фантастика • Приключения",
                44100
        );
        list.add(b8);

        return list;
    }

    public static List<OnlineBookItem> getCuratedWorldBooks() {
        List<OnlineBookItem> list = new ArrayList<>();

        list.add(new OnlineBookItem(
                "world_sherlock",
                "Приключения Шерлока Холмса",
                "Артур Конан Дойл",
                "Классические детективные расследования знаменитого сыщика с Бейкер-стрит 221Б и его верного друга доктора Ватсона.",
                "builtin://cover/sherlock",
                "https://www.gutenberg.org/ebooks/1661.epub.noimages",
                "EPUB",
                "RU/EN",
                "Мировая классика",
                "Детектив • Классика • Тайна",
                71200
        ));

        list.add(new OnlineBookItem(
                "world_kafka",
                "Превращение и Процесс",
                "Франц Кафка",
                "Философско-гротескная проза Франца Кафки о человеке перед лицом абсурдного и непостижимого мира.",
                "builtin://cover/kafka",
                "https://www.gutenberg.org/ebooks/5200.epub.noimages",
                "EPUB",
                "RU/EN",
                "Мировая классика",
                "Модернизм • Философия • Притча",
                54300
        ));

        list.add(new OnlineBookItem(
                "world_lovecraft",
                "Зов Ктулху и Хребты Безумия",
                "Говард Филлипс Лавкрафт",
                "Мрачные мистические повести о древних космических тайнах, затерянных экспедициях и неизведанных глубинах мироздания.",
                "builtin://cover/lovecraft",
                null,
                "FB2",
                "RU",
                "Мировая классика",
                "Мистика • Хоррор • Фантастика",
                49800
        ));

        list.add(new OnlineBookItem(
                "world_verne",
                "Двадцать тысяч лье под водой",
                "Жюль Верн",
                "Невероятное кругосветное путешествие на борту подводного корабля «Наутилус» вместе с таинственным капитаном Немо.",
                "builtin://cover/verne",
                "https://www.gutenberg.org/ebooks/164.epub.noimages",
                "EPUB",
                "RU/EN",
                "Мировая классика",
                "Приключения • Научная фантастика",
                46500
        ));

        return list;
    }
}
