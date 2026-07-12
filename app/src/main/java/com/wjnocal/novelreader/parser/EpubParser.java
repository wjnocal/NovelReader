package com.wjnocal.novelreader.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

public class EpubParser {
    public ParsedBook parse(File file, String fallbackTitle) throws Exception {
        try (ZipFile zipFile = new ZipFile(file)) {
            String opfPath = findOpfPath(zipFile);
            Document opf = parseXml(readEntry(zipFile, opfPath));
            String basePath = parentPath(opfPath);

            ParsedBook book = new ParsedBook();
            book.title = firstText(opf, "dc:title", stripExtension(fallbackTitle));
            book.author = firstText(opf, "dc:creator", "本地 EPUB");
            book.description = firstText(opf, "dc:description", "");
            book.fileType = "epub";

            Map<String, String> manifest = readManifest(opf);
            book.coverPath = extractCover(zipFile, opf, basePath);
            Map<String, String> titles = readTocTitles(zipFile, opf, basePath, manifest);
            NodeList itemRefs = opf.getElementsByTagName("itemref");
            for (int i = 0; i < itemRefs.getLength(); i++) {
                Node item = itemRefs.item(i);
                String idRef = attr(item, "idref");
                String href = manifest.get(idRef);
                if (href == null || href.trim().isEmpty()) {
                    continue;
                }
                String entryPath = normalizePath(basePath + href);
                ZipEntry entry = zipFile.getEntry(entryPath);
                if (entry == null) {
                    continue;
                }
                String html = readEntry(zipFile, entryPath);
                String title = titles.get(normalizeHref(href));
                String content = htmlToText(html);
                if (title == null || title.trim().isEmpty()) {
                    title = titleFromHtml(html, i + 1);
                }
                if (!content.trim().isEmpty()) {
                    book.chapters.add(new ParsedChapter(title, content));
                }
            }
            if (book.chapters.isEmpty()) {
                throw new IOException("EPUB 没有可读取的章节");
            }
            return book;
        }
    }

    private static String findOpfPath(ZipFile zipFile) throws Exception {
        String container = readEntry(zipFile, "META-INF/container.xml");
        Document document = parseXml(container);
        NodeList rootFiles = document.getElementsByTagName("rootfile");
        if (rootFiles.getLength() == 0) {
            throw new IOException("EPUB 缺少 rootfile");
        }
        String path = attr(rootFiles.item(0), "full-path");
        if (path == null || path.trim().isEmpty()) {
            throw new IOException("EPUB 缺少 OPF 路径");
        }
        return path;
    }

    private static Map<String, String> readManifest(Document opf) {
        Map<String, String> manifest = new HashMap<>();
        NodeList items = opf.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Node item = items.item(i);
            manifest.put(attr(item, "id"), attr(item, "href"));
        }
        return manifest;
    }

    private static String extractCover(ZipFile zipFile, Document opf, String basePath) {
        try {
            String href = findCoverHref(opf);
            if (href == null || href.trim().isEmpty()) {
                return null;
            }
            String entryPath = normalizePath(basePath + href);
            byte[] bytes = readEntryBytes(zipFile, entryPath);
            String extension = extensionFor(href);
            File coverFile = File.createTempFile("novel_cover_", extension);
            try (FileOutputStream output = new FileOutputStream(coverFile)) {
                output.write(bytes);
            }
            return coverFile.getAbsolutePath();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String findCoverHref(Document opf) {
        NodeList metaItems = opf.getElementsByTagName("meta");
        String coverId = null;
        for (int i = 0; i < metaItems.getLength(); i++) {
            Node item = metaItems.item(i);
            if ("cover".equalsIgnoreCase(attr(item, "name"))) {
                coverId = attr(item, "content");
                break;
            }
        }
        NodeList items = opf.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Node item = items.item(i);
            String id = attr(item, "id");
            String properties = attr(item, "properties");
            String href = attr(item, "href");
            if (href == null || href.trim().isEmpty()) {
                continue;
            }
            if ((coverId != null && coverId.equals(id))
                    || (properties != null && properties.contains("cover-image"))) {
                return href;
            }
        }
        for (int i = 0; i < items.getLength(); i++) {
            Node item = items.item(i);
            String mediaType = attr(item, "media-type");
            String href = attr(item, "href");
            if (mediaType != null && mediaType.startsWith("image/") && href != null && !href.trim().isEmpty()) {
                return href;
            }
        }
        return null;
    }

    private static Map<String, String> readTocTitles(
            ZipFile zipFile,
            Document opf,
            String basePath,
            Map<String, String> manifest
    ) {
        Map<String, String> titles = new HashMap<>();
        try {
            NodeList items = opf.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Node item = items.item(i);
                String properties = attr(item, "properties");
                String mediaType = attr(item, "media-type");
                String href = attr(item, "href");
                if ((properties != null && properties.contains("nav"))
                        || "application/x-dtbncx+xml".equals(mediaType)) {
                    String toc = readEntry(zipFile, normalizePath(basePath + href));
                    if (href.endsWith(".ncx")) {
                        readNcxTitles(toc, titles);
                    } else {
                        readNavTitles(toc, titles);
                    }
                }
            }
        } catch (Exception ignored) {
            titles.clear();
        }
        return titles;
    }

    private static void readNcxTitles(String ncx, Map<String, String> titles) throws Exception {
        Document document = parseXml(ncx);
        NodeList points = document.getElementsByTagName("navPoint");
        for (int i = 0; i < points.getLength(); i++) {
            Node point = points.item(i);
            String title = firstChildText(point, "text");
            String src = firstChildAttr(point, "content", "src");
            if (src != null && title != null) {
                titles.put(normalizeHref(src), title);
            }
        }
    }

    private static void readNavTitles(String nav, Map<String, String> titles) {
        org.jsoup.nodes.Document document = Jsoup.parse(nav);
        Elements links = document.select("nav a, nav span");
        for (Element link : links) {
            String href = link.attr("href");
            String title = link.text();
            if (!href.isEmpty() && !title.isEmpty()) {
                titles.put(normalizeHref(href), title);
            }
        }
    }

    private static String htmlToText(String html) {
        org.jsoup.nodes.Document document = Jsoup.parse(html);
        document.select("script, style, nav").remove();
        Element body = document.body();
        if (body == null) {
            return normalizeText(document.text());
        }
        StringBuilder builder = new StringBuilder();
        appendReadableText(body, builder);
        return normalizeText(builder.toString());
    }

    private static void appendReadableText(org.jsoup.nodes.Node node, StringBuilder builder) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).text()
                    .replace('\u00A0', ' ')
                    .replaceAll("[\\t\\x0B\\f\\r ]+", " ");
            builder.append(text);
            return;
        }
        if (!(node instanceof Element)) {
            return;
        }

        Element element = (Element) node;
        String tag = element.tagName().toLowerCase(java.util.Locale.US);
        if ("br".equals(tag)) {
            appendLineBreak(builder);
            return;
        }

        boolean block = isTextBlock(tag);
        if (block && builder.length() > 0) {
            appendParagraphBreak(builder);
        }
        for (org.jsoup.nodes.Node child : element.childNodes()) {
            appendReadableText(child, builder);
        }
        if (block) {
            appendParagraphBreak(builder);
        }
    }

    private static boolean isTextBlock(String tag) {
        return "p".equals(tag)
                || "div".equals(tag)
                || "section".equals(tag)
                || "article".equals(tag)
                || "blockquote".equals(tag)
                || "li".equals(tag)
                || "h1".equals(tag)
                || "h2".equals(tag)
                || "h3".equals(tag)
                || "h4".equals(tag)
                || "h5".equals(tag)
                || "h6".equals(tag)
                || "tr".equals(tag)
                || "table".equals(tag);
    }

    private static void appendLineBreak(StringBuilder builder) {
        int length = builder.length();
        if (length == 0 || builder.charAt(length - 1) != '\n') {
            builder.append('\n');
        }
    }

    private static void appendParagraphBreak(StringBuilder builder) {
        int length = builder.length();
        while (length > 0 && builder.charAt(length - 1) == ' ') {
            builder.deleteCharAt(length - 1);
            length--;
        }
        if (length == 0) {
            return;
        }
        if (length >= 2 && builder.charAt(length - 1) == '\n' && builder.charAt(length - 2) == '\n') {
            return;
        }
        if (builder.charAt(length - 1) == '\n') {
            builder.append('\n');
        } else {
            builder.append("\n\n");
        }
    }

    private static String normalizeText(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder normalized = new StringBuilder();
        int blankCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                blankCount++;
                continue;
            }
            if (normalized.length() > 0) {
                normalized.append(blankCount > 0 ? "\n\n" : "\n");
            }
            normalized.append(trimmed);
            blankCount = 0;
        }
        return normalized.toString().trim();
    }

    private static String titleFromHtml(String html, int index) {
        org.jsoup.nodes.Document document = Jsoup.parse(html);
        Element heading = document.selectFirst("h1, h2, h3, title");
        String title = heading == null ? "" : heading.text().trim();
        return title.isEmpty() ? "第 " + index + " 章" : title;
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String readEntry(ZipFile zipFile, String path) throws IOException {
        byte[] bytes = readEntryBytes(zipFile, path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readEntryBytes(ZipFile zipFile, String path) throws IOException {
        ZipEntry entry = zipFile.getEntry(path);
        if (entry == null) {
            throw new IOException("EPUB 缺少文件：" + path);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream input = zipFile.getInputStream(entry)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private static String extensionFor(String href) {
        String normalized = normalizeHref(href).toLowerCase(java.util.Locale.US);
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot < normalized.length() - 1) {
            return normalized.substring(dot);
        }
        return ".img";
    }

    private static String firstText(Document document, String tag, String fallback) {
        NodeList nodes = document.getElementsByTagName(tag);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        }
        return fallback;
    }

    private static String firstChildText(Node parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (tag.equals(child.getNodeName())) {
                return child.getTextContent();
            }
            String nested = firstChildText(child, tag);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static String firstChildAttr(Node parent, String tag, String attribute) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (tag.equals(child.getNodeName())) {
                return attr(child, attribute);
            }
            String nested = firstChildAttr(child, tag, attribute);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static String attr(Node node, String name) {
        Node attr = node.getAttributes() == null ? null : node.getAttributes().getNamedItem(name);
        return attr == null ? null : attr.getNodeValue();
    }

    private static String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    private static String normalizePath(String path) {
        return path.replace("\\", "/").replaceAll("/+", "/");
    }

    private static String normalizeHref(String href) {
        String normalized = href.replace("\\", "/");
        int hash = normalized.indexOf('#');
        if (hash >= 0) {
            normalized = normalized.substring(0, hash);
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String stripExtension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name == null || name.trim().isEmpty() ? "未命名 EPUB" : name;
    }
}
