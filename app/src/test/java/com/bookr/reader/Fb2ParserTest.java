package com.bookr.reader;

import com.bookr.reader.data.model.BookmarkItem;
import com.bookr.reader.data.model.MangaBookmarkItem;
import com.bookr.reader.data.model.MangaChapterItem;
import com.bookr.reader.data.model.MangaItem;
import com.bookr.reader.data.model.OnlineBookItem;
import com.bookr.reader.data.model.ReaderSettings;
import com.bookr.reader.network.MangaApiClient;
import com.bookr.reader.network.OnlineBookClient;
import com.bookr.reader.parser.EncodingDetector;
import com.bookr.reader.parser.EntityFilterReader;
import com.bookr.reader.parser.Fb2Book;
import com.bookr.reader.parser.Fb2Parser;
import com.bookr.reader.parser.Fb2Section;
import com.bookr.reader.parser.MobiParser;
import com.bookr.reader.parser.RtfParser;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Fb2ParserTest {

    @Test
    public void testEncodingDetectionWindows1251() throws Exception {
        String xmlHeader = "<?xml version=\"1.0\" encoding=\"windows-1251\"?>\n<FictionBook></FictionBook>";
        BufferedInputStream bis = new BufferedInputStream(
                new ByteArrayInputStream(xmlHeader.getBytes(StandardCharsets.ISO_8859_1)));
        Charset charset = EncodingDetector.detectXmlCharset(bis);
        Assert.assertEquals("windows-1251", charset.name().toLowerCase());
    }

    @Test
    public void testEncodingDetectionUtf8() throws Exception {
        String xmlHeader = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<FictionBook></FictionBook>";
        BufferedInputStream bis = new BufferedInputStream(
                new ByteArrayInputStream(xmlHeader.getBytes(StandardCharsets.UTF_8)));
        Charset charset = EncodingDetector.detectXmlCharset(bis);
        Assert.assertEquals("utf-8", charset.name().toLowerCase());
    }

    @Test
    public void testFb2HtmlGeneration() {
        Fb2Book book = new Fb2Book();
        book.getMetadata().setTitle("Евгений Онегин");
        book.getMetadata().setAuthor("Александр Пушкин");
        book.getMetadata().setAnnotation("Роман в стихах");

        Fb2Section sec1 = new Fb2Section();
        sec1.setTitle("Глава первая");
        sec1.setEpigraph("И жить торопится и чувствовать спешит.");
        sec1.setEpigraphAuthor("Кн. Вяземский");
        sec1.appendContent("<p>«Мой дядя самых честных правил,<br/>Когда не в шутку занемог...»</p>");

        book.addSection(sec1);
        book.addFootnote("n1", "Сноска о Вяземском");

        ReaderSettings settings = new ReaderSettings();
        settings.setFontSize(20);
        settings.setThemeScheme(ReaderSettings.THEME_OLED);
        settings.setPagedMode(true);

        String html = book.generateHtml(settings);

        Assert.assertTrue(html.contains("Евгений Онегин"));
        Assert.assertTrue(html.contains("Александр Пушкин"));
        Assert.assertTrue(html.contains("Глава первая"));
        Assert.assertTrue(html.contains("Мой дядя самых честных правил"));
        Assert.assertTrue(html.contains("#000000")); // OLED background
        Assert.assertEquals("Сноска о Вяземском", book.getFootnote("n1"));
        Assert.assertEquals("Сноска о Вяземском", book.getFootnote("#n1"));
    }

    @Test
    public void testZipCreationAndStream() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("book.fb2"));
        String fb2Content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">\n"
                + "<description><title-info><book-title>Тестовая книга</book-title></title-info></description>\n"
                + "</FictionBook>";
        zos.write(fb2Content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
        zos.close();

        byte[] zipBytes = baos.toByteArray();
        Assert.assertTrue(zipBytes.length > 0);
        Assert.assertEquals(0x50, zipBytes[0] & 0xFF); // PK header
        Assert.assertEquals(0x4B, zipBytes[1] & 0xFF);
    }

    @Test
    public void testNestedAndSiblingSectionsUniqueAnchors() {
        Fb2Book book = new Fb2Book();
        Fb2Section part1 = new Fb2Section();
        part1.setTitle("Часть 1");
        part1.setLevel(1);

        Fb2Section ch1 = new Fb2Section();
        ch1.setTitle("Глава 1.1");
        ch1.setLevel(2);
        ch1.appendContent("<p>Текст главы 1.1</p>");
        part1.addSubSection(ch1);

        Fb2Section part2 = new Fb2Section();
        part2.setTitle("Часть 2");
        part2.setLevel(1);
        part2.appendContent("<p>Текст части 2</p>");

        book.addSection(part1);
        book.addSection(part2);

        String html = book.generateHtml(new ReaderSettings());
        Assert.assertTrue(html.contains("id=\"chapter_0\""));
        Assert.assertTrue(html.contains("id=\"chapter_1\""));
        Assert.assertTrue(html.contains("id=\"chapter_2\""));
        // Ensure chapter_1 is not duplicated
        int firstIdx = html.indexOf("id=\"chapter_1\"");
        int lastIdx = html.lastIndexOf("id=\"chapter_1\"");
        Assert.assertEquals(firstIdx, lastIdx);
    }

    @Test
    public void testBookmarkItemLocaleAndProgress() {
        BookmarkItem bm = new BookmarkItem();
        bm.setPositionStr("45,3"); // Russian locale decimal comma
        bm.setPageNumber(12); // Page number should not shadow percentage in getPosition()

        Assert.assertEquals(453, bm.getPosition());
        Assert.assertEquals(45.3f, bm.getProgressPercent(), 0.01f);

        BookmarkItem bmDot = new BookmarkItem();
        bmDot.setPositionStr("72.8");
        bmDot.setPageNumber(50);
        Assert.assertEquals(728, bmDot.getPosition());
        Assert.assertEquals(72.8f, bmDot.getProgressPercent(), 0.01f);
    }

    @Test
    public void testRtfParserUnicodeFallbackSkip() {
        // \u1055\'cf is 'П' with CP1251 hex fallback; \u1088? is 'р' with '?' ASCII
        // fallback
        String rtf = "{\\rtf1\\ansi\\ansicpg1251\\uc1 \\u1055\\'cf\\u1088?\\u1080?\\par A & B < C}";
        String html = RtfParser.parseRtfToHtml(rtf.getBytes(StandardCharsets.US_ASCII),
                Charset.forName("windows-1251"));
        Assert.assertTrue(html.contains("<p>При</p>"));
        Assert.assertTrue(html.contains("A &amp; B &lt; C"));
    }

    @Test
    public void testMobiPalmDocDecompression() {
        // Construct a compressed stream:
        // 'A', 'B', 'C', 'D' (literals), then 0xC0 | 'E' (space + 'E'),
        // then LZ77 back-reference distance=6 ('A'..'E'), length=3 ('A','B','C') ->
        // pair:
        // distance=6 (00000000110), length-3=0 (000) -> 16-bit: 10_0000000110_000 =
        // 0x8030
        byte[] compressed = new byte[] {
                'A', 'B', 'C', 'D',
                (byte) (0x80 ^ 'E'), // wait: 0xC0 + ('E' ^ 0x80): for 'a' (0x61), 0x61 ^ 0x80 = 0xE1 (>= 0xC0)
        };
        // Use 'a' (0x61): 0x61 ^ 0x80 = 0xE1 (>= 0xC0), decodes to " a"
        byte[] src = new byte[] {
                'A', 'B', 'C', 'D',
                (byte) 0xE1,
                (byte) 0x80, (byte) 0x30 // distance = 6, count = 3 -> copies "ABC"
        };
        byte[] out = MobiParser.decompressPalmDoc(src, 0, src.length);
        Assert.assertEquals("ABCD aABC", new String(out, StandardCharsets.US_ASCII));
    }

    @Test
    public void testEntityFilterReader() throws Exception {
        String raw = "Текст&nbsp;с&mdash;тире &amp; &laquo;кавычками&raquo;";
        BufferedReader br = new BufferedReader(new EntityFilterReader(new StringReader(raw)));
        String filtered = br.readLine();
        Assert.assertEquals("Текст\u00A0с\u2014тире &amp; \u00ABкавычками\u00BB", filtered);
    }

    @Test
    public void testCustomThemeColorsOrderIndependence() {
        ReaderSettings settings = new ReaderSettings();
        settings.setThemeScheme(ReaderSettings.THEME_CUSTOM);
        settings.setCustomBgColor("#123456");
        settings.setCustomTextColor("#FEDCBA");

        Assert.assertEquals("#123456", settings.getCssBgColor());
        Assert.assertEquals("#FEDCBA", settings.getCssTextColor());
    }

    @Test
    public void testOnlineGutendexAndOpenLibraryParsing() {
        String gutendexJson = "{\n"
                + "  \"count\": 1,\n"
                + "  \"results\": [\n"
                + "    {\n"
                + "      \"id\": 2554,\n"
                + "      \"title\": \"Crime and Punishment\",\n"
                + "      \"authors\": [{\"name\": \"Dostoyevsky, Fyodor\"}],\n"
                + "      \"subjects\": [\"Psychological fiction\", \"Russia\"],\n"
                + "      \"languages\": [\"ru\"],\n"
                + "      \"formats\": {\n"
                + "        \"image/jpeg\": \"https://www.gutenberg.org/cache/epub/2554/pg2554.cover.medium.jpg\",\n"
                + "        \"application/epub+zip\": \"https://www.gutenberg.org/ebooks/2554.epub.images\"\n"
                + "      },\n"
                + "      \"download_count\": 15420\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        java.util.List<OnlineBookItem> gutBooks = OnlineBookClient.parseGutendexJson(gutendexJson);
        Assert.assertEquals(1, gutBooks.size());
        OnlineBookItem b = gutBooks.get(0);
        Assert.assertEquals("gut_2554", b.getId());
        Assert.assertEquals("Crime and Punishment", b.getTitle());
        Assert.assertEquals("Fyodor Dostoyevsky", b.getAuthor());
        Assert.assertEquals("EPUB", b.getFormat());
        Assert.assertEquals("RU", b.getLanguage());
        Assert.assertTrue(b.getDownloadUrl().endsWith(".epub.images"));

        String openLibraryJson = "{\n"
                + "  \"docs\": [\n"
                + "    {\n"
                + "      \"key\": \"/works/OL12345W\",\n"
                + "      \"title\": \"Мастер и Маргарита\",\n"
                + "      \"author_name\": [\"Михаил Булгаков\"],\n"
                + "      \"cover_i\": 98765,\n"
                + "      \"ia\": [\"master_i_margarita_1967\"],\n"
                + "      \"first_publish_year\": 1967,\n"
                + "      \"edition_count\": 20\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        java.util.List<OnlineBookItem> olBooks = OnlineBookClient.parseOpenLibraryJson(openLibraryJson);
        Assert.assertEquals(1, olBooks.size());
        Assert.assertEquals("ol_OL12345W", olBooks.get(0).getId());
        Assert.assertEquals("Мастер и Маргарита", olBooks.get(0).getTitle());
        Assert.assertEquals("Михаил Булгаков", olBooks.get(0).getAuthor());
        Assert.assertTrue(olBooks.get(0).getDownloadUrl().contains("master_i_margarita_1967.epub"));
    }

    @Test
    public void testMangaDexCatalogChaptersAndAtHomeParsing() {
        String mangaJson = "{\n"
                + "  \"data\": [\n"
                + "    {\n"
                + "      \"id\": \"m-101\",\n"
                + "      \"attributes\": {\n"
                + "        \"title\": {\"ru\": \"Клинок Затмения\", \"en\": \"Eclipse Blade\"},\n"
                + "        \"description\": {\"ru\": \"Описание манги на русском\"},\n"
                + "        \"status\": \"ongoing\",\n"
                + "        \"tags\": [\n"
                + "          {\"attributes\": {\"name\": {\"en\": \"Action\"}}}\n"
                + "        ]\n"
                + "      },\n"
                + "      \"relationships\": [\n"
                + "        {\"type\": \"author\", \"attributes\": {\"name\": \"Kenji Takahashi\"}},\n"
                + "        {\"type\": \"cover_art\", \"attributes\": {\"fileName\": \"cover123.jpg\"}}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        java.util.List<MangaItem> mangaList = MangaApiClient.parseMangaDexListJson(mangaJson);
        Assert.assertEquals(1, mangaList.size());
        MangaItem m = mangaList.get(0);
        Assert.assertEquals("m-101", m.getMangaId());
        Assert.assertEquals("Клинок Затмения", m.getTitle());
        Assert.assertEquals("Kenji Takahashi", m.getAuthor());
        Assert.assertTrue(m.getCoverUrl().contains("cover123.jpg.256.jpg"));

        String chaptersJson = "{\n"
                + "  \"data\": [\n"
                + "    {\n"
                + "      \"id\": \"ch-501\",\n"
                + "      \"attributes\": {\n"
                + "        \"chapter\": \"1\",\n"
                + "        \"volume\": \"1\",\n"
                + "        \"title\": \"Пробуждение\",\n"
                + "        \"translatedLanguage\": \"ru\",\n"
                + "        \"pages\": 18\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        java.util.List<MangaChapterItem> chapters = MangaApiClient.parseMangaDexChaptersJson(chaptersJson, "m-101");
        Assert.assertEquals(1, chapters.size());
        Assert.assertEquals("Том 1 Глава 1 — Пробуждение", chapters.get(0).getDisplayTitle());
        Assert.assertEquals(18, chapters.get(0).getPagesCount());

        String atHomeJson = "{\n"
                + "  \"baseUrl\": \"https://uploads.mangadex.org\",\n"
                + "  \"chapter\": {\n"
                + "    \"hash\": \"abc999\",\n"
                + "    \"data\": [\"p1.jpg\", \"p2.jpg\"],\n"
                + "    \"dataSaver\": [\"p1_low.webp\", \"p2_low.webp\"]\n"
                + "  }\n"
                + "}";
        java.util.List<String> hdPages = MangaApiClient.parseAtHomePagesJson(atHomeJson, false);
        java.util.List<String> saverPages = MangaApiClient.parseAtHomePagesJson(atHomeJson, true);
        Assert.assertEquals(2, hdPages.size());
        Assert.assertEquals("https://uploads.mangadex.org/data/abc999/p1.jpg", hdPages.get(0));
        Assert.assertEquals("https://uploads.mangadex.org/data-saver/abc999/p1_low.webp", saverPages.get(0));
    }

    @Test
    public void testCuratedCatalogsAndMangaBookmarkModel() {
        Assert.assertFalse(OnlineBookClient.getCuratedRussianBooks().isEmpty());
        Assert.assertFalse(OnlineBookClient.getCuratedWorldBooks().isEmpty());
        Assert.assertFalse(MangaApiClient.getCuratedMangaList().isEmpty());

        MangaBookmarkItem mbm = new MangaBookmarkItem(
                "builtin_blade_of_eclipse",
                "Клинок Затмения",
                "ch_1",
                "Глава 1",
                4,
                "Эпичный разворот",
                "builtin://cover/1");
        Assert.assertEquals("Клинок Затмения", mbm.getMangaTitle());
        Assert.assertEquals(4, mbm.getPageIndex());
        Assert.assertEquals("Эпичный разворот", mbm.getNote());
    }

    @Test
    public void testMangaChaptersDeduplicationAndSorting() {
        java.util.List<MangaChapterItem> raw = new java.util.ArrayList<>();
        raw.add(new MangaChapterItem("c1_en", "m1", "1", "1", "Prologue EN", "EN", 15));
        raw.add(new MangaChapterItem("c1_ru", "m1", "1", "1", "Пролог RU", "RU", 18));
        raw.add(new MangaChapterItem("c3", "m1", "3", "1", "Chapter 3", "RU", 20));
        raw.add(new MangaChapterItem("c2", "m1", "2", "1", "Chapter 2", "RU", 19));
        raw.add(new MangaChapterItem("c10", "m1", "10", "2", "Chapter 10", "RU", 22));

        java.util.List<MangaChapterItem> deduplicated = MangaApiClient.deduplicateMangaChapters(raw, "RU");

        Assert.assertEquals(4, deduplicated.size());
        // Verify chapter 1 picked Russian version
        Assert.assertEquals("1", deduplicated.get(0).getChapterNumber());
        Assert.assertEquals("RU", deduplicated.get(0).getLanguage());
        Assert.assertEquals("Пролог RU", deduplicated.get(0).getTitle());

        // Verify natural numeric sort order: 1, 2, 3, 10 (not 1, 10, 2, 3)
        Assert.assertEquals("1", deduplicated.get(0).getChapterNumber());
        Assert.assertEquals("2", deduplicated.get(1).getChapterNumber());
        Assert.assertEquals("3", deduplicated.get(2).getChapterNumber());
        Assert.assertEquals("10", deduplicated.get(3).getChapterNumber());
    }

    @Test
    public void testCuratedMangaCatalogIconicTitles() {
        java.util.List<MangaItem> catalog = MangaApiClient.getCuratedMangaList();
        Assert.assertTrue(catalog.size() >= 20);

        boolean hasBerserk = false;
        boolean hasOnePiece = false;
        boolean hasChainsaw = false;
        boolean hasAoT = false;

        for (MangaItem m : catalog) {
            String title = m.getTitle().toLowerCase();
            if (title.contains("berserk") || title.contains("берсерк")) hasBerserk = true;
            if (title.contains("one piece") || title.contains("ван-пис")) hasOnePiece = true;
            if (title.contains("chainsaw") || title.contains("бензопила")) hasChainsaw = true;
            if (title.contains("attack on titan") || title.contains("титанов")) hasAoT = true;
        }

        Assert.assertTrue(hasBerserk);
        Assert.assertTrue(hasOnePiece);
        Assert.assertTrue(hasChainsaw);
        Assert.assertTrue(hasAoT);
    }

    @Test
    public void testCuratedRussianBooksFormatIsEpub() {
        java.util.List<OnlineBookItem> ruBooks = OnlineBookClient.getCuratedRussianBooks();
        for (OnlineBookItem b : ruBooks) {
            if (b.getDownloadUrl() != null && b.getDownloadUrl().contains(".epub")) {
                Assert.assertEquals("EPUB", b.getFormat().toUpperCase());
            }
        }
    }

    @Test
    public void testMangaLibJsonParsing() {
        String mlCatalogJson = "{\n"
                + "  \"data\": [\n"
                + "    {\n"
                + "      \"id\": 7965,\n"
                + "      \"slug_url\": \"7965--chainsaw-man\",\n"
                + "      \"rus_name\": \"Человек-бензопила\",\n"
                + "      \"name\": \"Chainsaw Man\",\n"
                + "      \"rating\": {\"average\": \"9.5\"},\n"
                + "      \"status\": {\"label\": \"Завершён\"},\n"
                + "      \"type\": {\"label\": \"Манга\"},\n"
                + "      \"cover\": {\"default\": \"https://cover.imglib.info/uploads/cover/c.jpg\"}\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        java.util.List<MangaItem> list = MangaApiClient.parseMangaLibListJson(mlCatalogJson);
        Assert.assertEquals(1, list.size());
        MangaItem item = list.get(0);
        Assert.assertEquals("7965--chainsaw-man", item.getMangaId());
        Assert.assertEquals("Человек-бензопила", item.getTitle());
        Assert.assertEquals("https://cover.imglib.info/uploads/cover/c.jpg", item.getCoverUrl());
        Assert.assertEquals("Завершён", item.getStatus());
        Assert.assertTrue(item.getGenres().contains("★ 9.5"));

        String mlChaptersJson = "{\n"
                + "  \"data\": [\n"
                + "    {\n"
                + "      \"id\": 223269,\n"
                + "      \"volume\": \"1\",\n"
                + "      \"number\": \"1\",\n"
                + "      \"name\": \"Пёс и бензопила\",\n"
                + "      \"branches\": [\n"
                + "        {\n"
                + "          \"branch_id\": 4667,\n"
                + "          \"teams\": [{\"name\": \"Team Naginata\"}]\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        java.util.List<MangaChapterItem> chs = MangaApiClient.parseMangaLibChaptersJson(mlChaptersJson, item.getMangaId());
        Assert.assertEquals(1, chs.size());
        MangaChapterItem ch = chs.get(0);
        Assert.assertEquals("ml:223269:1:1:4667", ch.getChapterId());
        Assert.assertEquals("1", ch.getChapterNumber());
        Assert.assertEquals("1", ch.getVolumeNumber());
        Assert.assertEquals("Пёс и бензопила", ch.getTitle());
        Assert.assertEquals("Team Naginata", ch.getScanlationGroup());

        String mlPagesJson = "{\n"
                + "  \"data\": {\n"
                + "    \"pages\": [\n"
                + "      {\"id\": 1, \"url\": \"//manga/chainsaw-man/chapters/1-1/00.jpg\"},\n"
                + "      {\"id\": 2, \"url\": \"//manga/chainsaw-man/chapters/1-1/01.jpg\"}\n"
                + "    ]\n"
                + "  }\n"
                + "}";
        java.util.List<String> pages = MangaApiClient.parseMangaLibPagesJson(mlPagesJson);
        Assert.assertEquals(2, pages.size());
        Assert.assertEquals("https://img3.cdnlibs.org/manga/chainsaw-man/chapters/1-1/00.jpg", pages.get(0));
        Assert.assertEquals("https://img3.cdnlibs.org/manga/chainsaw-man/chapters/1-1/01.jpg", pages.get(1));
    }

    @Test
    public void testRemangaJsonParsing() {
        String remangaSearchJson = "{\n"
                + "  \"results\": [\n"
                + "    {\n"
                + "      \"id\": 136,\n"
                + "      \"main_name\": \"Ненасытный Берсерк\",\n"
                + "      \"dir\": \"berserk_of_gluttony_\",\n"
                + "      \"avg_rating\": \"8.1\",\n"
                + "      \"type\": {\"name\": \"Манга\"},\n"
                + "      \"status\": {\"name\": \"Продолжается\"},\n"
                + "      \"cover\": {\"high\": \"/media/titles/berserk_of_gluttony/cover_df2b9127.webp\"}\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        java.util.List<MangaItem> list = MangaApiClient.parseRemangaListJson(remangaSearchJson);
        Assert.assertEquals(1, list.size());
        MangaItem item = list.get(0);
        Assert.assertEquals("remanga_berserk_of_gluttony_", item.getMangaId());
        Assert.assertEquals("Ненасытный Берсерк", item.getTitle());
        Assert.assertEquals("https://api.remanga.org/media/titles/berserk_of_gluttony/cover_df2b9127.webp", item.getCoverUrl());

        String remangaPagesJson = "{\n"
                + "  \"pages\": [\n"
                + "    [{\"link\": \"https://img3.reimg2.org/images/136/5006/1_re.jpg\"}],\n"
                + "    [{\"link\": \"https://img3.reimg2.org/images/136/5006/2_re.jpg\"}]\n"
                + "  ]\n"
                + "}";
        java.util.List<String> pages = MangaApiClient.parseRemangaPagesJson(remangaPagesJson);
        Assert.assertEquals(2, pages.size());
        Assert.assertEquals("https://img3.reimg2.org/images/136/5006/1_re.jpg", pages.get(0));
        Assert.assertEquals("https://img3.reimg2.org/images/136/5006/2_re.jpg", pages.get(1));
    }
}
