package com.graden.models.ui.markdown;

import java.util.ResourceBundle;
import java.util.function.Consumer;

import org.kordamp.ikonli.javafx.FontIcon;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

/**
 * Builds the right-click context menu shown on selected text inside an
 * LLM response. Centralizes i18n + Ikonli Feather icons so both
 * {@link TextFlowSelection} (per-block) and {@link MarkdownTextSelection}
 * (cross-block) render the same menu.
 *
 * <p>Actions are passed in as {@code Consumer<String>} callbacks so this
 * factory stays decoupled from the system clipboard and the RAG manager.
 */
public final class ChatContextMenuFactory {

    private ChatContextMenuFactory() {
    }

    public static ContextMenu build(String selectedText,
                                    ResourceBundle bundle,
                                    Consumer<String> onCopy,
                                    Consumer<String> onAddToRag) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("chat-selection-context-menu");

        MenuItem copyItem = new MenuItem(label(bundle, "context.menu.copy", "Copy"));
        copyItem.setGraphic(icon("fth-copy"));
        copyItem.setOnAction(ev -> {
            if (onCopy != null) onCopy.accept(selectedText);
        });
        menu.getItems().add(copyItem);

        if (onAddToRag != null) {
            MenuItem ragItem = new MenuItem(label(bundle, "context.menu.add.to.rag", "Add to RAG"));
            ragItem.setGraphic(icon("fth-book-open"));
            ragItem.setOnAction(ev -> onAddToRag.accept(selectedText));
            menu.getItems().add(ragItem);
        }

        return menu;
    }

    private static FontIcon icon(String literal) {
        FontIcon ic = new FontIcon(literal);
        ic.setIconSize(14);
        ic.getStyleClass().add("chat-selection-menu-icon");
        return ic;
    }

    private static String label(ResourceBundle bundle, String key, String fallback) {
        if (bundle != null && bundle.containsKey(key)) {
            return bundle.getString(key);
        }
        return fallback;
    }
}
