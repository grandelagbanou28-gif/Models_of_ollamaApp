package com.graden.models.ui;

import org.kordamp.ikonli.javafx.FontIcon;

import com.graden.models.App;
import com.graden.models.model.AttachedDocument;
import com.graden.models.util.DocumentStats;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Modal showing the extracted text of an attached document, plus stats
 * and RAG status. Opens when the user clicks an attached-document pill.
 * Lets the user verify what the model actually received, especially
 * useful for scanned PDFs where extraction may be poor.
 */
public final class AttachedDocumentPreview {

    private AttachedDocumentPreview() {
    }

    public static void show(AttachedDocument doc) {
        if (doc == null) return;

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(doc.getFileName());

        VBox root = new VBox(10);
        root.getStyleClass().add("attached-doc-preview");
        root.setPadding(new Insets(16));

        // Header: file icon + name
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = new FontIcon(iconFor(doc.getExtension()));
        icon.setIconSize(18);
        icon.getStyleClass().add("attached-doc-preview-icon");
        Label name = new Label(doc.getFileName());
        name.getStyleClass().add("title-4");
        header.getChildren().addAll(icon, name);

        // Stats line
        Label stats = new Label(DocumentStats.format(doc));
        stats.getStyleClass().add("attached-doc-preview-stats");
        stats.setWrapText(true);

        // RAG status badge
        Label ragLabel = new Label(localizedRagStatus(doc.getRagStatus()));
        ragLabel.getStyleClass().addAll("attached-doc-preview-rag",
                ragStatusStyleClass(doc.getRagStatus()));

        // Low-quality warning (if applicable)
        Label lowQualityNote = null;
        if (doc.isLowQuality()) {
            lowQualityNote = new Label("⚠ " + App.getBundle().getString("chat.attach.lowQuality.note"));
            lowQualityNote.setWrapText(true);
            lowQualityNote.getStyleClass().add("attached-doc-preview-warning");
        }

        // Content
        TextArea contentArea = new TextArea(doc.getContent() == null ? "" : doc.getContent());
        contentArea.setEditable(false);
        contentArea.setWrapText(true);
        contentArea.getStyleClass().add("attached-doc-preview-text");
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        // Actions
        Button copy = new Button(App.getBundle().getString("chat.attach.preview.copy"));
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(doc.getContent() == null ? "" : doc.getContent());
            Clipboard.getSystemClipboard().setContent(cc);
            copy.setText("✓ " + App.getBundle().getString("chat.attach.preview.copied"));
            javafx.animation.PauseTransition revert =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.2));
            revert.setOnFinished(ev ->
                    copy.setText(App.getBundle().getString("chat.attach.preview.copy")));
            revert.play();
        });

        Button close = new Button(App.getBundle().getString("chat.attach.preview.close"));
        close.getStyleClass().add("accent");
        close.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, spacer, copy, close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(header, stats, ragLabel);
        if (lowQualityNote != null) root.getChildren().add(lowQualityNote);
        root.getChildren().addAll(contentArea, actions);

        Scene scene = new Scene(root, 760, 560);
        scene.getStylesheets().add(
                AttachedDocumentPreview.class.getResource("/css/graden_models_active.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private static String iconFor(String ext) {
        if (ext == null) return "fth-file";
        switch (ext.toLowerCase()) {
            case "pdf":
            case "md":
            case "txt":
                return "fth-file-text";
            default:
                return "fth-file";
        }
    }

    private static String localizedRagStatus(AttachedDocument.RagStatus s) {
        String key;
        switch (s == null ? AttachedDocument.RagStatus.PENDING : s) {
            case INDEXED:     key = "chat.attach.ragStatus.indexed"; break;
            case INLINE_ONLY: key = "chat.attach.ragStatus.inlineOnly"; break;
            case FAILED:      key = "chat.attach.ragStatus.failed"; break;
            default:          key = "chat.attach.ragStatus.pending";
        }
        return App.getBundle().getString(key);
    }

    private static String ragStatusStyleClass(AttachedDocument.RagStatus s) {
        switch (s == null ? AttachedDocument.RagStatus.PENDING : s) {
            case INDEXED:     return "rag-status-ok";
            case INLINE_ONLY: return "rag-status-partial";
            case FAILED:      return "rag-status-failed";
            default:          return "rag-status-pending";
        }
    }
}
