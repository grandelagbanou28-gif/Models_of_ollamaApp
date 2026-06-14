package com.graden.models.ui.markdown;

import com.graden.models.App;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TextInputControl;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Cross-block text selection for a {@code MarkdownOutput} container.
 * <p>
 * Unlike per-TextFlow selection, this tracks selection at the container level,
 * allowing the user to drag across multiple paragraphs, headings, list items,
 * and other markdown blocks seamlessly — just like selecting text in a word
 * processor.
 * <p>
 * Cmd/Ctrl+C copies the selection to the system clipboard via a Scene-level
 * keyboard filter (independent of which node has focus, as long as a text input
 * is not focused). Esc clears the selection. Right-click shows a Copy context
 * menu. Clicking anywhere outside a selectable TextFlow clears the selection.
 */
public final class MarkdownTextSelection {

    private static final String INSTANCE_KEY = "GradenModels.markdown-text-selection";
    private static final String SHAPE_KEY = "GradenModels.markdown-selection-shape";
    private static final String SCENE_FILTER_KEY = "GradenModels.markdown-key-filter";

    private static final Map<Scene, List<MarkdownTextSelection>> INSTANCES_BY_SCENE = new HashMap<>();

    /**
     * Optional handler invoked when the user picks "Add to RAG" from the
     * context menu. The selection classes do not depend on the RAG manager
     * directly; controllers register a handler via
     * {@link #registerAddToRagHandler(Consumer)} (typically once during
     * {@code initialize()}). If no handler is registered the menu entry is
     * hidden.
     */
    private static volatile Consumer<String> ADD_TO_RAG_HANDLER;

    public static void registerAddToRagHandler(Consumer<String> handler) {
        ADD_TO_RAG_HANDLER = handler;
    }

    static Consumer<String> getAddToRagHandler() {
        return ADD_TO_RAG_HANDLER;
    }

    private final Parent root;
    private final List<Block> blocks = new ArrayList<>();

    private int anchorGlobal = -1;
    private int caretGlobal = -1;

    private MarkdownTextSelection(Parent root) {
        this.root = root;

        root.setFocusTraversable(true);

        root.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);

        root.setOnContextMenuRequested(e -> {
            String sel = collectSelectedText();
            if (sel.isEmpty()) return;
            ContextMenu menu = ChatContextMenuFactory.build(
                    sel,
                    App.getBundle(),
                    text -> copyToClipboard(),
                    ADD_TO_RAG_HANDLER);
            menu.show(root, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        TextFlowSelection.registerClearCallback(this::clearSelection);

        root.getProperties().put(INSTANCE_KEY, this);

        // Track scene attachment for the global keyboard shortcut
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            synchronized (INSTANCES_BY_SCENE) {
                if (oldScene != null) {
                    INSTANCES_BY_SCENE.getOrDefault(oldScene, List.of()).remove(this);
                    cleanupSceneFilter(oldScene);
                }
                if (newScene != null) {
                    INSTANCES_BY_SCENE.computeIfAbsent(newScene, k -> new ArrayList<>()).add(this);
                    installSceneKeyFilter(newScene);
                }
            }
        });

        Scene currentScene = root.getScene();
        if (currentScene != null) {
            synchronized (INSTANCES_BY_SCENE) {
                INSTANCES_BY_SCENE.computeIfAbsent(currentScene, k -> new ArrayList<>()).add(this);
                installSceneKeyFilter(currentScene);
            }
        }

        rebuildBlocks();
    }

    private static void installSceneKeyFilter(Scene scene) {
        synchronized (INSTANCES_BY_SCENE) {
            if (scene.getProperties().get(SCENE_FILTER_KEY) != null) return;
            scene.getProperties().put(SCENE_FILTER_KEY, Boolean.TRUE);
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                clearAllInScene(scene);
                e.consume();
                return;
            }
            if (e.isShortcutDown() && e.getCode() == KeyCode.C) {
                // Don't interfere with text input controls
                Node focusOwner = scene.getFocusOwner();
                if (focusOwner instanceof TextInputControl) return;

                List<MarkdownTextSelection> instances;
                synchronized (INSTANCES_BY_SCENE) {
                    instances = new ArrayList<>(INSTANCES_BY_SCENE.getOrDefault(scene, List.of()));
                }
                for (MarkdownTextSelection sel : instances) {
                    if (sel.hasSelection()) {
                        sel.copyToClipboard();
                        e.consume();
                        return;
                    }
                }
            }
        });
    }

    private static void cleanupSceneFilter(Scene scene) {
        synchronized (INSTANCES_BY_SCENE) {
            List<MarkdownTextSelection> remaining = INSTANCES_BY_SCENE.get(scene);
            if (remaining == null || remaining.isEmpty()) {
                INSTANCES_BY_SCENE.remove(scene);
            }
        }
    }

    private static void clearAllInScene(Scene scene) {
        List<MarkdownTextSelection> instances;
        synchronized (INSTANCES_BY_SCENE) {
            instances = new ArrayList<>(INSTANCES_BY_SCENE.getOrDefault(scene, List.of()));
        }
        for (MarkdownTextSelection sel : instances) {
            sel.clearSelection();
        }
    }

    private void copyToClipboard() {
        String selected = collectSelectedText();
        if (selected.isEmpty()) return;
        ClipboardContent cc = new ClipboardContent();
        cc.putString(selected);
        Clipboard.getSystemClipboard().setContent(cc);
    }

    // ───────────────────── public static API ───────────────────────────────────

    public static void install(Parent root) {
        if (root.getProperties().get(INSTANCE_KEY) != null) return;
        new MarkdownTextSelection(root);
    }

    public static void rebuild(Parent root) {
        MarkdownTextSelection sel = (MarkdownTextSelection) root.getProperties().get(INSTANCE_KEY);
        if (sel != null) {
            sel.rebuildBlocks();
        }
    }

    public static String getSelectedTextIn(Node node) {
        if (node instanceof Parent p) {
            MarkdownTextSelection sel = (MarkdownTextSelection) p.getProperties().get(INSTANCE_KEY);
            if (sel != null) {
                String text = sel.collectSelectedText();
                if (!text.isEmpty()) return text;
            }
        }
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                String result = getSelectedTextIn(child);
                if (!result.isEmpty()) return result;
            }
        }
        return "";
    }

    // ───────────────────────────── block indexing ─────────────────────────────

    private void rebuildBlocks() {
        for (Block block : blocks) {
            Path shape = (Path) block.textFlow.getProperties().get(SHAPE_KEY);
            if (shape != null) {
                block.textFlow.getChildren().remove(shape);
                block.textFlow.getProperties().remove(SHAPE_KEY);
            }
        }
        blocks.clear();

        int offset = 0;
        for (Node child : root.getChildrenUnmodifiable()) {
            List<TextFlow> found = new ArrayList<>();
            collectTextFlows(child, found);
            for (TextFlow tf : found) {
                tf.setCursor(Cursor.TEXT);
                tf.getStyleClass().add("selectable-textflow");
                int len = computeTextLength(tf);

                Path shape = new Path();
                shape.setManaged(false);
                shape.setMouseTransparent(true);
                shape.setStroke(null);
                shape.getStyleClass().add("textflow-selection-shape");
                tf.getChildren().add(0, shape);
                tf.getProperties().put(SHAPE_KEY, shape);

                blocks.add(new Block(tf, shape, offset, len));
                offset += len;
            }
        }

        clearSelection();
    }

    private static void collectTextFlows(Node node, List<TextFlow> out) {
        if (node instanceof TextFlow tf) {
            out.add(tf);
            return;
        }
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                collectTextFlows(child, out);
            }
        }
    }

    private static int computeTextLength(TextFlow flow) {
        int len = 0;
        for (Node child : flow.getChildrenUnmodifiable()) {
            if (child.getStyleClass() != null
                    && child.getStyleClass().contains("textflow-selection-shape")) {
                continue;
            }
            if (child instanceof Text t) {
                len += t.getText() == null ? 0 : t.getText().length();
            } else {
                len += 1;
            }
        }
        return len;
    }

    // ──────────────────────── mouse / coordinate logic ─────────────────────────

    private void onMousePressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) return;
        root.requestFocus();
        int global = pointToGlobal(e.getSceneX(), e.getSceneY());
        if (global < 0) {
            clearSelection();
            return;
        }
        anchorGlobal = global;
        caretGlobal = global;
        applySelection();
    }

    private void onMouseDragged(MouseEvent e) {
        if (anchorGlobal < 0) return;
        int global = pointToGlobal(e.getSceneX(), e.getSceneY());
        if (global < 0) return;
        caretGlobal = global;
        applySelection();
    }

    private int pointToGlobal(double sceneX, double sceneY) {
        Point2D scenePoint = new Point2D(sceneX, sceneY);
        Point2D local = root.sceneToLocal(scenePoint);
        return pointToGlobalLocal(local.getX(), local.getY(), root);
    }

    private int pointToGlobalLocal(double localX, double localY, Parent container) {
        Point2D p = new Point2D(localX, localY);
        for (Node child : container.getChildrenUnmodifiable()) {
            Point2D childLocal = child.parentToLocal(p);
            if (child.contains(childLocal)) {
                if (child instanceof TextFlow tf) {
                    return blockOffsetFor(tf, childLocal);
                }
                if (child instanceof Parent pc) {
                    int result = pointToGlobalLocal(childLocal.getX(), childLocal.getY(), pc);
                    if (result >= 0) return result;
                }
            }
        }
        return -1;
    }

    private int blockOffsetFor(TextFlow tf, Point2D localPoint) {
        HitInfo hit = tf.hitTest(localPoint);
        int localIdx = hit.getInsertionIndex();
        for (Block block : blocks) {
            if (block.textFlow == tf) {
                return block.globalOffset + Math.min(localIdx, block.length);
            }
        }
        return -1;
    }

    // ────────────────────────── selection rendering ────────────────────────────

    private void applySelection() {
        int start = Math.min(anchorGlobal, caretGlobal);
        int end = Math.max(anchorGlobal, caretGlobal);

        for (Block block : blocks) {
            int localStart = Math.max(0, start - block.globalOffset);
            int localEnd = Math.min(block.length, end - block.globalOffset);

            if (localStart >= localEnd) {
                block.shape.getElements().clear();
            } else {
                try {
                    PathElement[] shapeElements = block.textFlow.rangeShape(localStart, localEnd);
                    block.shape.getElements().setAll(shapeElements);
                } catch (Exception ignored) {
                    block.shape.getElements().clear();
                }
            }
        }
    }

    // ──────────────────────── text collection ──────────────────────────────────

    private boolean hasSelection() {
        int start = Math.min(anchorGlobal, caretGlobal);
        int end = Math.max(anchorGlobal, caretGlobal);
        return start < end;
    }

    String collectSelectedText() {
        int start = Math.min(anchorGlobal, caretGlobal);
        int end = Math.max(anchorGlobal, caretGlobal);
        if (start >= end) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            int blockEnd = block.globalOffset + block.length;
            if (blockEnd <= start) continue;
            if (block.globalOffset >= end) continue;

            if (sb.length() > 0) {
                sb.append('\n');
            }

            int localStart = Math.max(0, start - block.globalOffset);
            int localEnd = Math.min(block.length, end - block.globalOffset);

            int offset = 0;
            for (Node child : block.textFlow.getChildrenUnmodifiable()) {
                if (child == block.shape) continue;
                if (child instanceof Text t) {
                    String content = t.getText();
                    if (content == null) content = "";
                    int len = content.length();
                    int segStart = Math.max(0, localStart - offset);
                    int segEnd = Math.min(len, localEnd - offset);
                    if (segEnd > segStart) {
                        sb.append(content, segStart, segEnd);
                    }
                    offset += len;
                } else {
                    if (offset >= localStart && offset < localEnd) {
                        sb.append(' ');
                    }
                    offset += 1;
                }
            }
        }
        return sb.toString();
    }

    private void clearSelection() {
        anchorGlobal = -1;
        caretGlobal = -1;
        for (Block block : blocks) {
            block.shape.getElements().clear();
        }
    }

    // ─────────────────────────── inner types ───────────────────────────────────

    private static final class Block {
        final TextFlow textFlow;
        final Path shape;
        final int globalOffset;
        final int length;

        Block(TextFlow textFlow, Path shape, int globalOffset, int length) {
            this.textFlow = textFlow;
            this.shape = shape;
            this.globalOffset = globalOffset;
            this.length = length;
        }
    }
}
