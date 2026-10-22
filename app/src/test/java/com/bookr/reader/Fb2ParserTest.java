package com.bookr.reader;

import com.bookr.reader.data.model.ReaderSettings;
import com.bookr.reader.parser.EncodingDetector;
import com.bookr.reader.parser.Fb2Book;
import com.bookr.reader.parser.Fb2Parser;
import com.bookr.reader.parser.Fb2Section;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Fb2ParserTest {

    @Test
    public void testEncodingDetectionWindows1251() throws Exception {
        String xmlHeader = "<?xml version=\"1.0\" encoding=\"windows-1251\"?>\n<FictionBook></FictionBook>";
        BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(xmlHeader.getBytes(StandardCharsets.ISO_8859_1)));
        Charset charset = EncodingDetector.detectXmlCharset(bis);
        Assert.assertEquals("windows-1251", charset.name().toLowerCase());
    }

    @Test
    public void testEncodingDetectionUtf8() throws Exception {
        String xmlHeader = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<FictionBook></FictionBook>";
        BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(xmlHeader.getBytes(StandardCharsets.UTF_8)));
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
}
