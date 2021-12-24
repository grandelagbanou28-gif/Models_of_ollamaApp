package com.graden.models.ui;

import java.util.List;

import org.kordamp.ikonli.javafx.FontIcon;

import com.graden.models.App;
import com.graden.models.model.RagResult;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Modal that shows the exact passages a RAG query retrieved from a single
 * source file. This is what makes RAG answers <em>verifiable</em>: the user
 * can click a source pill under an assistant reply and see precisely which
 * fragments — with their relevance score and page — the model was given.
 */
public final class RagSourcePreview {

    private RagSourcePreview() {
    }

    /**
     * @param fileName  the source document
     * @param fragments the RagResults that came from {@code fileName}
     */
    public static void show(String fileName, List<RagResult> fragments) {
        if (fragments == null || fragments.isEmpty()) return;

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(fileName);

        VBox root = new VBox(12);
        root.getStyleClass().add("rag-source-preview");
        root.setPadding(new Insets(16));

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = new FontIcon("fth-file-text");
        icon.setIconSize(18);
        icon.getStyleClass().add("rag-source-preview-icon");
        Label name = new Label(fileName);
        name.getStyleClass().add("title-4");
        header.getChildren().addAll(icon, name);

        Label intro = new Label(java.text.MessageFormat.format(
                App.getBundle().getString("rag.source.preview.intro"), fragments.size()));
        intro.getStyleClass().add("rag-source-preview-intro");
        intro.setWrapText(true);

        // One card per retrieved fragment
        VBox fragmentsBox = new VBox(10);
        int idx = 1;
        for (RagResult r : fragments) {
            fragmentsBox.getChildren().add(buildFragmentCard(idx++, r));
        }

        ScrollPane scroll = new ScrollPane(fragmentsBox);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("rag-source-preview-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button close = new Button(App.getBundle().getString("chat.attach.preview.close"));
        close.getStyleClass().add("accent");
        close.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(spacer, close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(header, intro, scroll, actions);

        Scene scene = new Scene(root, 720, 560);
        scene.getStylesheets().add(
                RagSourcePreview.class.getResource("/css/graden_models_active.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private static VBox buildFragmentCard(int index, RagResult r) {
        VBox card = new VBox(6);
        card.getStyleClass().add("rag-fragment-card");
        card.setPadding(new Insets(10));

        HBox meta = new HBox(8);
        meta.setAlignment(Pos.CENTER_LEFT);

        Label num = new Label("#" + index);
        num.getStyleClass().add("rag-fragment-num");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Relevance shown as a percentage — easier to read than a raw cosine score.
        int pct = (int) Math.round(Math.max(0, Math.min(1, r.getScore())) * 100);
        Label score = new Label(java.text.MessageFormat.format(
                App.getBundle().getString("rag.source.preview.relevance"), pct));
        score.getStyleClass().add("rag-fragment-score");

        meta.getChildren().addAll(num, spacer, score);
        if (r.getPageNumber() > 0) {
            Label page = new Label(java.text.MessageFormat.format(
                    App.getBundle().getString("rag.source.preview.page"), r.getPageNumber()));
            page.getStyleClass().add("rag-fragment-page");
            meta.getChildren().add(2, page);
        }

        TextArea body = new TextArea(r.getContent() == null ? "" : r.getContent().trim());
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(Math.min(10, Math.max(3, countLines(r.getContent()))));
        body.getStyleClass().add("rag-fragment-text");

        card.getChildren().addAll(meta, body);
        return card;
    }

    private static int countLines(String text) {
        if (text == null || text.isEmpty()) return 3;
        return text.length() / 80 + 1;
    }
}
