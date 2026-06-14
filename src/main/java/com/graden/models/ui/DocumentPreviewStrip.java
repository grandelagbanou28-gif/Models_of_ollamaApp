package com.graden.models.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kordamp.ikonli.javafx.FontIcon;

import com.graden.models.model.AttachedDocument;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * Horizontal strip of document chips. Each chip shows a spinner while the
 * file is being parsed in the background and turns into a check mark when
 * the text is extracted and ready to be sent.
 *
 * <p>Two states matter to the controller:
 * <ul>
 *   <li>{@link #hasFiles()} — is there anything attached at all?</li>
 *   <li>{@link #allReady()} — has every file finished parsing?</li>
 * </ul>
 *
 * <p>Parsed content is exposed via {@link #getReadyAttachments()} as
 * {@link AttachedDocument} instances that the controller can inline into
 * the chat history.
 */
public class DocumentPreviewStrip extends FlowPane {

    private static final Logger LOGGER = Logger.getLogger(DocumentPreviewStrip.class.getName());
    private static final ExecutorService PARSE_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "doc-preview-parser");
        t.setDaemon(true);
        return t;
    });

    /** Reject attaching files larger than this. 20 MB ≈ several million tokens of text, beyond what's usable. */
    public static final long MAX_ATTACH_BYTES = 20L * 1024 * 1024;
    /** Below this many extracted characters, the document is flagged as low-quality (likely scanned PDF). */
    private static final int LOW_QUALITY_CHAR_THRESHOLD = 200;

    /** Result of {@link #addFile(File)}; null on success. */
    public enum AddResult { OK, TOO_LARGE, UNSUPPORTED, ALREADY_ADDED }

    private static final class Entry {
        final File file;
        final HBox chip;
        String parsedContent; // null until parsed
        boolean failed;
        boolean lowQuality;

        Entry(File file, HBox chip) {
            this.file = file;
            this.chip = chip;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<File, Entry> byFile = new HashMap<>();
    private final BooleanProperty empty = new SimpleBooleanProperty(true);
    private final BooleanProperty allReady = new SimpleBooleanProperty(true);

    public DocumentPreviewStrip() {
        getStyleClass().add("document-preview-strip");
        setHgap(6);
        setVgap(6);
        setPadding(new Insets(4, 0, 4, 0));
        setAlignment(Pos.CENTER_LEFT);
        setVisible(false);
        setManaged(false);
    }

    public BooleanProperty emptyProperty() { return empty; }
    public BooleanProperty allReadyProperty() { return allReady; }

    public boolean hasFiles() { return !entries.isEmpty(); }
    public boolean allReady() { return allReady.get(); }

    public List<File> getFiles() {
        List<File> out = new ArrayList<>(entries.size());
        for (Entry e : entries) out.add(e.file);
        return out;
    }

    public List<AttachedDocument> getReadyAttachments() {
        List<AttachedDocument> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.parsedContent != null) {
                AttachedDocument d = new AttachedDocument(e.file.getName(), e.parsedContent, e.file.length());
                d.setLowQuality(e.lowQuality);
                out.add(d);
            }
        }
        return out;
    }

    /**
     * Tries to queue {@code file} for parsing. Returns the outcome so the
     * caller can show a meaningful status message on rejection.
     */
    public AddResult addFile(File file) {
        if (file == null || !file.exists()) return AddResult.UNSUPPORTED;
        if (file.length() > MAX_ATTACH_BYTES) return AddResult.TOO_LARGE;
        if (byFile.containsKey(file)) return AddResult.ALREADY_ADDED;
        for (Entry existing : entries) {
            if (existing.file.getAbsolutePath().equals(file.getAbsolutePath())) {
                return AddResult.ALREADY_ADDED;
            }
        }

        HBox chip = buildChip(file);
        Entry entry = new Entry(file, chip);
        entries.add(entry);
        byFile.put(file, entry);
        getChildren().add(chip);
        refreshState();

        // Start parsing in background; UI updates via Platform.runLater.
        PARSE_EXECUTOR.submit(() -> parseAndUpdate(entry));
        return AddResult.OK;
    }

    public void clear() {
        entries.clear();
        byFile.clear();
        getChildren().clear();
        refreshState();
    }

    private void parseAndUpdate(Entry entry) {
        String content;
        try {
            content = parseFile(entry.file);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to parse " + entry.file.getName(), ex);
            content = null;
        }
        String parsed = content;
        Platform.runLater(() -> {
            if (parsed == null) {
                entry.failed = true;
                markChipFailed(entry);
            } else {
                entry.parsedContent = parsed;
                entry.lowQuality = isLowQuality(parsed);
                if (entry.lowQuality) {
                    markChipWarning(entry);
                } else {
                    markChipReady(entry);
                }
            }
            refreshState();
        });
    }

    /**
     * Heuristic for "the extractor didn't really get useful text" — most
     * commonly a scanned PDF where PDFBox returns just whitespace/symbols.
     */
    private static boolean isLowQuality(String text) {
        if (text == null) return true;
        String trimmed = text.trim();
        if (trimmed.length() < LOW_QUALITY_CHAR_THRESHOLD) return true;
        int alpha = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isLetter(trimmed.charAt(i))) alpha++;
        }
        // Less than 30% letters → probably garbage (page numbers, symbols, etc.)
        return ((double) alpha / trimmed.length()) < 0.30;
    }

    private static String parseFile(File file) throws Exception {
        String name = file.getName().toLowerCase();
        DocumentParser parser;
        if (name.endsWith(".pdf")) {
            parser = new ApachePdfBoxDocumentParser();
        } else if (name.endsWith(".docx") || name.endsWith(".xlsx") || name.endsWith(".pptx")) {
            parser = new ApachePoiDocumentParser();
        } else {
            // Plain UTF-8 text: code files, csv, json, yaml, txt, md, etc.
            parser = new TextDocumentParser();
        }
        try (InputStream in = new FileInputStream(file)) {
            Document doc = parser.parse(in);
            return doc.text();
        }
    }

    private void refreshState() {
        boolean isEmpty = entries.isEmpty();
        empty.set(isEmpty);
        setVisible(!isEmpty);
        setManaged(!isEmpty);

        boolean ready = true;
        for (Entry e : entries) {
            if (e.parsedContent == null && !e.failed) {
                ready = false;
                break;
            }
        }
        allReady.set(ready);
    }

    // ───────────────────────── chip rendering ──────────────────────────

    private HBox buildChip(File file) {
        HBox chip = new HBox(6);
        chip.getStyleClass().add("document-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setPadding(new Insets(4, 8, 4, 8));

        // Status indicator (spinner → check / x). Hosted in a fixed-size StackPane.
        StackPane status = new StackPane();
        status.setPrefSize(14, 14);
        status.getStyleClass().add("document-chip-status");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(12, 12);
        spinner.setMaxSize(12, 12);
        status.getChildren().add(spinner);

        FontIcon typeIcon = new FontIcon(iconLiteralFor(file));
        typeIcon.setIconSize(14);
        typeIcon.getStyleClass().add("document-chip-icon");

        Label name = new Label(file.getName());
        name.getStyleClass().add("document-chip-label");
        name.setTooltip(new Tooltip(file.getAbsolutePath()));

        Button remove = new Button();
        remove.getStyleClass().add("document-chip-remove");
        FontIcon x = new FontIcon("fth-x");
        x.setIconSize(11);
        remove.setGraphic(x);
        remove.setOnAction(e -> removeChip(file));

        chip.getChildren().addAll(status, typeIcon, name, remove);
        return chip;
    }

    private void removeChip(File file) {
        Entry entry = byFile.remove(file);
        if (entry == null) return;
        entries.remove(entry);
        getChildren().remove(entry.chip);
        refreshState();
    }

    private void markChipReady(Entry entry) {
        // Replace spinner with a check icon.
        if (entry.chip.getChildren().isEmpty()) return;
        StackPane status = (StackPane) entry.chip.getChildren().get(0);
        status.getChildren().clear();
        FontIcon check = new FontIcon("fth-check");
        check.setIconSize(13);
        check.getStyleClass().add("document-chip-ready");
        status.getChildren().add(check);

        // Enrich the file-name tooltip with stats now that parsing is done.
        AttachedDocument stats = new AttachedDocument(
                entry.file.getName(), entry.parsedContent, entry.file.length());
        for (javafx.scene.Node n : entry.chip.getChildren()) {
            if (n instanceof Label label && entry.file.getName().equals(label.getText())) {
                label.setTooltip(new Tooltip(com.graden.models.util.DocumentStats.format(stats)));
                break;
            }
        }
    }

    private void markChipFailed(Entry entry) {
        if (entry.chip.getChildren().isEmpty()) return;
        StackPane status = (StackPane) entry.chip.getChildren().get(0);
        status.getChildren().clear();
        FontIcon alert = new FontIcon("fth-alert-triangle");
        alert.setIconSize(13);
        alert.getStyleClass().add("document-chip-error");
        status.getChildren().add(alert);
    }

    private void markChipWarning(Entry entry) {
        if (entry.chip.getChildren().isEmpty()) return;
        entry.chip.getStyleClass().add("document-chip-warning");
        StackPane status = (StackPane) entry.chip.getChildren().get(0);
        status.getChildren().clear();
        FontIcon warn = new FontIcon("fth-alert-circle");
        warn.setIconSize(13);
        warn.getStyleClass().add("document-chip-warn-icon");
        status.getChildren().add(warn);

        // Enrich tooltip with low-quality warning.
        String stats = "Texto extraído: " + entry.parsedContent.length() + " caracteres. "
                + "¿Es un PDF escaneado? El modelo puede no entender el contenido.";
        for (javafx.scene.Node n : entry.chip.getChildren()) {
            if (n instanceof Label label && entry.file.getName().equals(label.getText())) {
                label.setTooltip(new Tooltip(stats));
                break;
            }
        }
    }

    private static String iconLiteralFor(File file) {
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "fth-file";
        String ext = name.substring(dot + 1);
        switch (ext) {
            case "pdf": case "txt": case "md": case "markdown": case "rtf":
            case "docx": case "log":
                return "fth-file-text";
            case "py": case "js": case "ts": case "jsx": case "tsx":
            case "java": case "kt": case "scala": case "go": case "rs":
            case "cpp": case "cc": case "cxx": case "c": case "h": case "hpp":
            case "cs": case "swift": case "rb": case "php": case "sh":
            case "bash": case "zsh": case "sql": case "r": case "lua":
            case "groovy": case "dart": case "elm": case "ex": case "exs":
            case "clj": case "cljs":
                return "fth-code";
            case "csv": case "tsv": case "xlsx":
                return "fth-grid";
            case "json": case "yaml": case "yml": case "xml":
            case "toml": case "ini": case "conf": case "properties":
                return "fth-settings";
            case "html": case "htm": case "css": case "scss":
                return "fth-globe";
            case "pptx":
                return "fth-monitor";
            default:
                return "fth-file";
        }
    }
}
