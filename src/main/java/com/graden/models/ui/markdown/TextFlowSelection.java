package com.graden.models.ui.markdown;

import com.graden.models.App;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.HitInfo;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Adds Word-style click-and-drag text selection to a {@link TextFlow}.
 *
 * <p>JavaFX {@code Text} nodes do NOT render a selection background by
 * default (unlike TextField/TextArea). Setting {@code selectionStart/End/Fill}
 * only changes the foreground color of selected glyphs in some contexts, and
 * leaves no visible highlight in a raw TextFlow — and can leak the white fill
 * back into normal rendering on theme switches.
 *
 * <p>This helper uses {@link TextFlow#rangeShape(int, int)} to compute the
 * polygon of the selected range and paints it as a translucent Path BEHIND
 * the text. The original text fill is never modified, so the implementation
 * works identically in light and dark themes.
 *
 * <p>Cmd/Ctrl+C copies the selected text. Esc clears the selection.
 * Right-click shows a Copy context menu when text is selected.
 * Clicking anywhere outside a selectable TextFlow clears all selections.
 */
public final class TextFlowSelection {

    private static final String INSTALLED_KEY = "GrandelGradenNexus.textflow-selection-installed";
    private static final String SELECTABLE_CLASS = "selectable-textflow";
    private static final Set<TextFlowSelection> ACTIVE = new HashSet<>();
    private static final Set<Scene> SCENES_WITH_FILTER = new HashSet<>();
    private static final List<Runnable> CLEAR_CALLBACKS = new CopyOnWriteArrayList<>();

    private final TextFlow flow;
    private final Path selectionShape;
    private final ChangeListener<Scene> sceneListener;
    private int anchor = -1;
    private int caret = -1;

    private TextFlowSelection(TextFlow flow) {
        this.flow = flow;

        selectionShape = new Path();
        selectionShape.setManaged(false);
        selectionShape.setMouseTransparent(true);
        selectionShape.setStroke(null);
        selectionShape.getStyleClass().add("textflow-selection-shape");
        flow.getChildren().add(0, selectionShape);

        flow.setFocusTraversable(true);
        flow.setCursor(Cursor.TEXT);
        flow.getStyleClass().add(SELECTABLE_CLASS);

        flow.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onPressed);
        flow.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onDragged);
        flow.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);

        // Right-click context menu for copying selected text
        flow.setOnContextMenuRequested(e -> {
            String selected = collectSelectedText();
            if (selected.isEmpty()) return;
            ContextMenu menu = ChatContextMenuFactory.build(
                    selected,
                    App.getBundle(),
                    text -> {
                        ClipboardContent cc = new ClipboardContent();
                        cc.putString(text);
                        Clipboard.getSystemClipboard().setContent(cc);
                    },
                    MarkdownTextSelection.getAddToRagHandler());
            menu.show(flow, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        // Track scene attachment for global click-outside-to-deselect
        this.sceneListener = (obs, oldScene, newScene) -> {
            synchronized (ACTIVE) {
                if (oldScene != null) {
                    // Remove instance when detached from scene
                    ACTIVE.remove(this);
                }
                if (newScene != null) {
                    ACTIVE.add(this);
                    installSceneFilter(newScene);
                }
            }
        };
        flow.sceneProperty().addListener(sceneListener);

        // If already in a scene, register immediately
        Scene currentScene = flow.getScene();
        if (currentScene != null) {
            synchronized (ACTIVE) {
                ACTIVE.add(this);
                installSceneFilter(currentScene);
            }
        }

        flow.getProperties().put(INSTALLED_KEY, this);
    }

    private static void installSceneFilter(Scene scene) {
        synchronized (SCENES_WITH_FILTER) {
            if (!SCENES_WITH_FILTER.add(scene)) return;
        }
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            Node target = (Node) e.getTarget();
            if (!hasSelectableAncestor(target)) {
                clearAll();
            }
        });
    }

    private static boolean hasSelectableAncestor(Node node) {
        Node current = node;
        while (current != null) {
            if (current.getStyleClass().contains(SELECTABLE_CLASS)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /** Installs selection support on a single TextFlow (idempotent). */
    public static void install(TextFlow flow) {
        if (flow.getProperties().get(INSTALLED_KEY) != null) return;
        new TextFlowSelection(flow);
    }

    /** Walks {@code root} and installs selection on every nested TextFlow. */
    public static void installRecursively(Node root) {
        if (root instanceof TextFlow tf) {
            install(tf);
        }
        if (root instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                installRecursively(child);
            }
        }
    }

    /**
     * Registers a callback that will be invoked whenever {@link #clearAll()}
     * is called. This allows external selection systems (e.g.
     * {@code MarkdownTextSelection}) to clear their selections in sync.
     */
    public static void registerClearCallback(Runnable callback) {
        CLEAR_CALLBACKS.add(callback);
    }

    /** Clears selection on all active TextFlowSelection instances and
     * notifies registered external callbacks. */
    public static void clearAll() {
        List<TextFlowSelection> snapshot;
        synchronized (ACTIVE) {
            snapshot = new ArrayList<>(ACTIVE);
        }
        for (TextFlowSelection sel : snapshot) {
            sel.clearSelection();
        }
        for (Runnable callback : CLEAR_CALLBACKS) {
            callback.run();
        }
    }

    /**
     * Walks {@code root} and collects selected text from any nested
     * selectable TextFlows. Returns the first non-empty selection found,
     * or an empty string if nothing is selected.
     */
    public static String getSelectedTextIn(Node root) {
        if (root instanceof TextFlow tf && tf.getStyleClass().contains(SELECTABLE_CLASS)) {
            TextFlowSelection sel = (TextFlowSelection) tf.getProperties().get(INSTALLED_KEY);
            if (sel != null) {
                String text = sel.collectSelectedText();
                if (!text.isEmpty()) return text;
            }
        }
        if (root instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                String result = getSelectedTextIn(child);
                if (!result.isEmpty()) return result;
            }
        }
        return "";
    }

    private void onPressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) return;
        flow.requestFocus();
        HitInfo h = flow.hitTest(new Point2D(e.getX(), e.getY()));
        anchor = h.getInsertionIndex();
        caret = anchor;
        applySelection();
    }

    private void onDragged(MouseEvent e) {
        if (anchor < 0) return;
        HitInfo h = flow.hitTest(new Point2D(e.getX(), e.getY()));
        caret = h.getInsertionIndex();
        applySelection();
    }

    private void onKeyPressed(KeyEvent e) {
        if (e.isShortcutDown() && e.getCode() == KeyCode.C) {
            String selected = collectSelectedText();
            if (!selected.isEmpty()) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(selected);
                Clipboard.getSystemClipboard().setContent(cc);
                e.consume();
            }
        } else if (e.getCode() == KeyCode.ESCAPE) {
            clearSelection();
        }
    }

    private void applySelection() {
        int start = Math.min(anchor, caret);
        int end = Math.max(anchor, caret);
        if (start >= end) {
            selectionShape.getElements().clear();
            return;
        }
        try {
            PathElement[] shape = flow.rangeShape(start, end);
            selectionShape.getElements().setAll(shape);
        } catch (Exception ignored) {
            selectionShape.getElements().clear();
        }
    }

    private String collectSelectedText() {
        int start = Math.min(anchor, caret);
        int end = Math.max(anchor, caret);
        if (start >= end) return "";

        StringBuilder sb = new StringBuilder();
        int offset = 0;
        for (Node child : flow.getChildren()) {
            if (child == selectionShape) continue;
            if (child instanceof Text t) {
                String content = t.getText();
                if (content == null) content = "";
                int len = content.length();
                int localStart = Math.max(0, start - offset);
                int localEnd = Math.min(len, end - offset);
                if (localEnd > localStart) {
                    sb.append(content, localStart, localEnd);
                }
                offset += len;
            } else {
                offset += 1;
            }
        }
        return sb.toString();
    }

    private void clearSelection() {
        anchor = -1;
        caret = -1;
        selectionShape.getElements().clear();
    }
}
