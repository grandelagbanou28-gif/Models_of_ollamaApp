package com.graden.models.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import atlantafx.base.controls.ProgressSliderSkin;
import atlantafx.base.controls.RingProgressIndicator;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.animation.PauseTransition;
import com.graden.models.util.Utils;
import com.graden.models.service.MarkdownService;

import com.graden.models.App;
import com.graden.models.manager.ChatManager;
import com.graden.models.manager.ModelManager;
import com.graden.models.manager.OllamaManager;
import com.graden.models.manager.RagManager;
import com.graden.models.model.ChatMessage;
import com.graden.models.model.ChatSession;
import com.graden.models.model.OllamaModel;
import com.graden.models.model.RagCollection;
import com.graden.models.model.RagResult;
import com.graden.models.model.GenerationMetrics;
import com.graden.models.model.ToolCall;
import com.graden.models.model.StreamRequest;
import com.graden.models.ui.ImagePreviewStrip;
import com.graden.models.ui.MarkdownOutput;
import com.graden.models.ui.ToolCallCard;
import com.graden.models.ui.markdown.MarkdownTextSelection;
import com.graden.models.util.ImageUtils;
import com.graden.models.manager.CapabilityManager;
import com.graden.models.service.tools.ToolDefinition;
import com.graden.models.service.tools.ToolExecutor;
import com.graden.models.service.tools.ToolRegistry;

import io.github.ollama4j.models.generate.OllamaStreamHandler;
import com.graden.models.manager.ChatCollectionManager;
import com.graden.models.model.ChatFolder;
import com.graden.models.model.RagDocumentItem;
import com.graden.models.util.ResponsiveManager;
import java.util.ArrayList;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SeparatorMenuItem;

public class ChatController {

    private Future<?> currentGenerationTask;
    private boolean isGenerating = false;
    private final StringBuilder activeResponseBuffer = new StringBuilder();
    private long lastUiUpdate = 0;
    private static final long UI_UPDATE_INTERVAL_MS = 30; // ~30fps for text updates

    // RAG collection selection
    private final Set<String> selectedRagCollections = new HashSet<>();
    @FXML
    private HBox ragChipsContainer;

    // Multimodal: Image preview strip
    private ImagePreviewStrip imagePreviewStrip;
    private com.graden.models.ui.DocumentPreviewStrip documentPreviewStrip;
    private com.graden.models.ui.ContextRing contextRing;
    private Label visionWarningLabel;

    @FXML
    private HBox inputToolbarRow;

    @FXML
    private ComboBox<String> modelSelector;
    @FXML
    private Label statusLabel;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox messagesContainer;
    @FXML
    private TextArea inputField;
    @FXML
    private Button sendButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button attachButton;
    @FXML
    private Button exportMdButton;

    // Adaptive UI Elements
    @FXML
    private VBox welcomeContainer;
    @FXML
    private VBox bottomInputContainer;
    @FXML
    private VBox inputCapsule;

    @FXML
    private Label welcomeLabel;

    // Welcome Flow Elements
    @FXML
    private VBox setupContainer;
    @FXML
    private ComboBox<String> initialModelSelector;

    @FXML
    private Slider tempSlider;
    @FXML
    private Label tempValueLabel;
    @FXML
    private TextArea systemPromptField;

    // Advanced Params
    @FXML
    private ComboBox<String> presetSelector;
    @FXML
    private Slider ctxSlider;
    @FXML
    private Label ctxValueLabel;
    @FXML
    private Slider topKSlider;
    @FXML
    private Label topKValueLabel;
    @FXML
    private Slider topPSlider;
    @FXML
    private Label topPValueLabel;
    @FXML
    private TextField seedField;

    @FXML
    private javafx.scene.control.CheckBox toolsToggle;
    @FXML
    private javafx.scene.control.CheckBox jsonModeToggle;
    @FXML
    private TextField jsonSchemaField;

    private static final Logger LOGGER = Logger.getLogger(ChatController.class.getName());

    @FXML
    public void initialize() {
        // Responsive Manager
        responsiveManager = ResponsiveManager.getInstance();
        if (sidebarToggleButton != null && sidebarToggleButton.getScene() != null) {
            responsiveManager.bindToScene(sidebarToggleButton.getScene());
        } else if (sidebarToggleButton != null) {
            sidebarToggleButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    responsiveManager.bindToScene(newScene);
                }
            });
        }

        setupInputField();
        setupListeners();
        setupMultimedia();
        buildRagChips();
        ToolRegistry.getInstance().registerBuiltinsOnce();

        MarkdownTextSelection.registerAddToRagHandler(this::onAddSelectionToRag);

        setupResponsiveRightSidebar();

        updateUIState(true);
    }

    /**
     * Saves the user-selected snippet as a temporary Markdown file and asks
     * {@link RagManager} to ingest it. The user picks the destination
     * collection via a context menu shown over the chat input area.
     */
    private void onAddSelectionToRag(String selectedText) {
        if (selectedText == null || selectedText.isBlank()) return;
        RagManager ragManager = RagManager.getInstance();
        ragManager.initialize();
        ObservableList<RagCollection> collections = ragManager.getCollections();
        if (collections.isEmpty()) {
            RagCollection def = ragManager.getOrCreateDefaultCollection();
            indexSnippetIntoCollection(selectedText, def);
            return;
        }
        Node owner = inputField != null ? inputField : statusLabel;
        ContextMenu menu = new ContextMenu();
        MenuItem header = new MenuItem(
                App.getBundle().getString("context.menu.add.to.rag.dialog.prompt"));
        header.setDisable(true);
        header.setStyle("-fx-opacity: 1; -fx-font-weight: bold;");
        menu.getItems().add(header);
        for (RagCollection col : collections) {
            MenuItem item = new MenuItem(col.getName());
            item.setGraphic(new FontIcon("fth-book-open"));
            item.setOnAction(ev -> indexSnippetIntoCollection(selectedText, col));
            menu.getItems().add(item);
        }
        if (owner != null && owner.getScene() != null) {
            menu.show(owner,
                    owner.localToScreen(0, 0).getX(),
                    owner.localToScreen(0, 0).getY());
        }
    }

    private void indexSnippetIntoCollection(String selectedText, RagCollection collection) {
        App.getExecutorService().submit(() -> {
            try {
                String hash = com.graden.models.util.HashUtils.sha256(selectedText);
                if (RagManager.getInstance().isDocumentIndexedByHash(hash, collection.getId())) {
                    Platform.runLater(() -> {
                        if (statusLabel != null) {
                            String tpl = App.getBundle().getString("context.menu.add.to.rag.success");
                            statusLabel.setText(java.text.MessageFormat.format(tpl, collection.getName()));
                        }
                    });
                    return;
                }

                String stamp = String.valueOf(System.currentTimeMillis());
                String displayName = "selection-" + stamp + ".md";
                File tempFile = File.createTempFile("GrandelGradenNexus-snippet-", ".md");
                tempFile.deleteOnExit();
                java.nio.file.Files.writeString(tempFile.toPath(), selectedText);

                RagDocumentItem item = new RagDocumentItem(
                        displayName, tempFile.getAbsolutePath(), collection.getId(), hash);
                RagManager.getInstance().getDocuments().add(item);
                RagManager.getInstance().indexDocument(tempFile, item);

                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        String tpl = App.getBundle().getString("context.menu.add.to.rag.success");
                        statusLabel.setText(java.text.MessageFormat.format(tpl, collection.getName()));
                    }
                });
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to index selection into RAG", e);
                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText(App.getBundle().getString("context.menu.add.to.rag.error"));
                    }
                });
            }
        });
    }

    private void setupInputField() {
        inputField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    return;
                }
                event.consume();
                sendMessage();
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.V) {
                handleClipboardPaste(event);
            }
        });
    }

    /** Persist selected RAG collections to the current session. */
    private void saveRagStateToSession() {
        if (currentSession != null) {
            currentSession.setRagCollectionIds(new ArrayList<>(selectedRagCollections));
        }
    }

    private void buildRagChips() {
        if (ragChipsContainer == null) return;

        ragChipsContainer.getChildren().clear();
        ragChipsContainer.setVisible(true);
        ragChipsContainer.setManaged(true);

        RagManager ragManager = RagManager.getInstance();
        ragManager.initialize();
        var allCollections = ragManager.getCollections();

        // Always show book icon prefix (indicates knowledge base)
        FontIcon prefixIcon = new FontIcon("fth-book-open");
        prefixIcon.setIconSize(13);
        prefixIcon.getStyleClass().add("rag-chip-prefix");
        ragChipsContainer.getChildren().add(prefixIcon);

        // Filter valid selected collections. Hidden "__..." collections (per-
        // chat attachment buckets) are auto-included in retrieval but never
        // rendered as a chip — they would just confuse the user.
        List<RagCollection> activeCollections = allCollections.stream()
                .filter(c -> selectedRagCollections.contains(c.getId()))
                .filter(c -> c.getName() == null || !c.getName().startsWith("__"))
                .collect(Collectors.toList());

        if (!activeCollections.isEmpty()) {
            for (RagCollection col : activeCollections) {
                HBox chip = new HBox(3);
                chip.setAlignment(Pos.CENTER_LEFT);
                chip.getStyleClass().add("rag-chip-inline");

                Label nameLabel = new Label(col.getName());
                nameLabel.getStyleClass().add("rag-chip-label");

                Button removeBtn = new Button();
                removeBtn.getStyleClass().add("rag-chip-remove");
                removeBtn.setGraphic(new FontIcon("fth-x"));
                removeBtn.setOnAction(e -> {
                    selectedRagCollections.remove(col.getId());
                    saveRagStateToSession();
                    buildRagChips();
                });

                chip.getChildren().addAll(nameLabel, removeBtn);
                ragChipsContainer.getChildren().add(chip);
            }
        }

        // "+" button is ALWAYS visible so users can add RAG collections from any chat
        Button addBtn = new Button();
        addBtn.getStyleClass().add("rag-chip-add");
        FontIcon plusIcon = new FontIcon("fth-plus");
        plusIcon.setIconSize(12);
        addBtn.setGraphic(plusIcon);
        addBtn.setTooltip(new Tooltip(App.getBundle().getString("chat.ragAddCollection")));
        addBtn.setOnAction(e -> showAddCollectionMenu(addBtn, RagManager.getInstance().getCollections()));
        ragChipsContainer.getChildren().add(addBtn);
    }

    private void showAddCollectionMenu(Node owner, ObservableList<RagCollection> collections) {
        ContextMenu menu = new ContextMenu();
        
        MenuItem header = new MenuItem(App.getBundle().getString("rag.selectCollections"));
        header.setDisable(true);
        header.setStyle("-fx-opacity: 1; -fx-font-weight: bold;");
        menu.getItems().add(header);
        
        menu.getItems().add(new SeparatorMenuItem());
        
        Map<String, CheckBox> cbMap = new HashMap<>();
        
        for (RagCollection col : collections) {
            CheckBox cb = new CheckBox(col.getName());
            CustomMenuItem item = new CustomMenuItem(cb);
            item.setHideOnClick(false);
            menu.getItems().add(item);
            cbMap.put(col.getId(), cb);
        }

        Runnable syncUi = () -> {
            for (RagCollection col : collections) {
                cbMap.get(col.getId()).setSelected(selectedRagCollections.contains(col.getId()));
            }
        };

        syncUi.run();

        for (RagCollection col : collections) {
            CheckBox cb = cbMap.get(col.getId());
            cb.setOnAction(e -> {
                if (cb.isSelected()) {
                    selectedRagCollections.add(col.getId());
                } else {
                    selectedRagCollections.remove(col.getId());
                }
                saveRagStateToSession();
                syncUi.run();
                buildRagChips();
            });
        }
        
        menu.show(owner, javafx.geometry.Side.TOP, 0, -4);
    }

    private void handleClipboardPaste(KeyEvent event) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles()) {
            boolean handledImagePaste = false;
            for (File file : clipboard.getFiles()) {
                if (ImageUtils.isValidImageFile(file)) {
                    imagePreviewStrip.addImage(file);
                    handledImagePaste = true;
                }
            }
            if (handledImagePaste) {
                event.consume();
            }
        }
    }

    private void setupListeners() {
        messagesContainer.heightProperty().addListener((observable, oldValue, newValue) -> {
            scrollPane.setVvalue(1.0);
        });

        // Keep the context-usage ring live as the user types.
        inputField.textProperty().addListener((obs, oldVal, newVal) -> updateContextBudget());

        modelSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentSession != null && newVal != null) {
                currentSession.setModelName(newVal);
                ChatManager.getInstance().saveChats();
            }
            updateVisionWarning();
            updateToolsToggleState(newVal);
        });

        toolsToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (currentSession != null) {
                currentSession.setToolsEnabled(newVal);
                ChatManager.getInstance().saveChats();
            }
        });

        jsonModeToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean enabled = Boolean.TRUE.equals(newVal);
            jsonSchemaField.setVisible(enabled);
            jsonSchemaField.setManaged(enabled);
            if (currentSession != null) {
                if (enabled) {
                    String schema = jsonSchemaField.getText().trim();
                    currentSession.setJsonFormat(schema.isEmpty() ? "json" : schema);
                } else {
                    currentSession.setJsonFormat(null);
                }
                ChatManager.getInstance().saveChats();
            }
        });

        jsonSchemaField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentSession != null && jsonModeToggle.isSelected()) {
                String schema = newVal.trim();
                currentSession.setJsonFormat(schema.isEmpty() ? "json" : schema);
                ChatManager.getInstance().saveChats();
            }
        });

        tempSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateCreativityLabel(newVal.doubleValue());
            if (currentSession != null) {
                currentSession.setTemperature(newVal.doubleValue());
                ChatManager.getInstance().saveChats();
            }
        });

        systemPromptField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentSession != null) {
                currentSession.setSystemPrompt(newVal);
                ChatManager.getInstance().saveChats();
            }
        });
    }

    private void setupMultimedia() {
        initializeAdvancedParameters();
        initializeImagePreviewStrip();
        initializeDragAndDrop();
    }

    // Helper method for Adaptive UI
    private void updateUIState(boolean isNewChat) {
        if (isNewChat) {
            // If models are already available, skip welcome and go straight to chat
            boolean hasModels = modelSelector != null && modelSelector.getItems() != null
                    && !modelSelector.getItems().isEmpty();
            if (hasModels) {
                String first = modelSelector.getItems().get(0);
                modelSelector.setValue(first);
                if (initialModelSelector != null)
                    initialModelSelector.setValue(first);
                if (scrollPane != null)
                    scrollPane.setVisible(true);
                if (welcomeContainer != null)
                    welcomeContainer.setVisible(false);
                if (bottomInputContainer != null && inputCapsule != null
                        && !bottomInputContainer.getChildren().contains(inputCapsule)) {
                    if (welcomeContainer != null)
                        welcomeContainer.getChildren().remove(inputCapsule);
                    bottomInputContainer.getChildren().add(inputCapsule);
                }
                if (inputCapsule != null) {
                    inputCapsule.setVisible(true);
                    inputCapsule.setManaged(true);
                }
                return;
            }
            // WELCOME STATE
            if (scrollPane != null)
                scrollPane.setVisible(false);
            if (welcomeContainer != null)
                welcomeContainer.setVisible(true);

            // Reset for Welcome Flow
            if (setupContainer != null) {
                setupContainer.setVisible(true);
                setupContainer.setManaged(true);
                animateWelcomeText(); // Trigger Typewriter
            }

            // Default: Show Sidebar for New Chat
            if (rightSidebar != null && !rightSidebar.isVisible()) {
                rightSidebar.setVisible(true);
                rightSidebar.setManaged(true);
            }

            // Move Input Capsule to Welcome Container BUT HIDE IT initially
            if (welcomeContainer != null && inputCapsule != null
                    && !welcomeContainer.getChildren().contains(inputCapsule)) {
                if (bottomInputContainer != null)
                    bottomInputContainer.getChildren().remove(inputCapsule);
                welcomeContainer.getChildren().add(inputCapsule);
            }

            if (inputCapsule != null) {
                inputCapsule.setVisible(false); // Hidden until model selected
                inputCapsule.setManaged(false); // Don't take space
            }
        } else {
            // ACTIVE CHAT STATE
            if (scrollPane != null)
                scrollPane.setVisible(true);
            if (welcomeContainer != null)
                welcomeContainer.setVisible(false);

            // Move Input Capsule to Bottom Container
            if (bottomInputContainer != null && inputCapsule != null
                    && !bottomInputContainer.getChildren().contains(inputCapsule)) {
                if (welcomeContainer != null)
                    welcomeContainer.getChildren().remove(inputCapsule);
                bottomInputContainer.getChildren().add(inputCapsule);
            }

            // Ensure visible in Active Chat
            if (inputCapsule != null) {
                inputCapsule.setVisible(true);
                inputCapsule.setManaged(true);
            }
        }
    }

    @FXML
    private void onInitialModelSelected() {
        String selected = initialModelSelector.getValue();
        if (selected != null) {
            // Sync to main selector
            modelSelector.setValue(selected);

            // Transition: Hide Setup, Show Input
            setupContainer.setVisible(false);
            setupContainer.setManaged(false);

            if (inputCapsule != null) {
                inputCapsule.setVisible(true);
                inputCapsule.setManaged(true);
            }
        }
    }

    private void animateWelcomeText() {
        String fullText = App.getBundle().getString("chat.welcome");
        welcomeLabel.setText("");

        Timeline timeline = new Timeline();
        timeline.setCycleCount(1); // Run once works by adding keyframes

        for (int k = 0; k < fullText.length(); k++) {
            final int index = k;
            KeyFrame keyFrame = new KeyFrame(
                    Duration.millis(50 * (k + 1)), // 50ms per char
                    event -> welcomeLabel.setText(fullText.substring(0, index + 1)));
            timeline.getKeyFrames().add(keyFrame);
        }
        timeline.play();
    }

    public void setModelManager(ModelManager modelManager) {
        if (modelManager != null) {
            // Initial population
            updateModelList(modelManager.getLocalModels());

            // Listener for future updates
            modelManager.getLocalModels().addListener(
                    (ListChangeListener.Change<? extends OllamaModel> c) -> {
                        updateModelList(modelManager.getLocalModels());
                    });
        }
    }

    private void updateModelList(List<OllamaModel> models) {
        Platform.runLater(() -> {
            ObservableList<String> modelNames = models.stream()
                    .map(model -> model.getName() + ":" + model.getTag())
                    .collect(Collectors.toCollection(FXCollections::observableArrayList));

            String currentSelection = modelSelector.getValue();

            modelSelector.setItems(modelNames);
            if (initialModelSelector != null) {
                initialModelSelector.setItems(modelNames);
            }

            modelListLoaded = true;

            // If this is a new chat and models just arrived after auto-start,
            // re-evaluate UI state to auto-select model and show input
            boolean wasEmpty = currentSelection == null || currentSelection.isEmpty();
            if (wasEmpty && !modelNames.isEmpty() && currentSession != null
                    && currentSession.getMessages().isEmpty()) {
                String first = modelNames.get(0);
                modelSelector.setValue(first);
                if (initialModelSelector != null)
                    initialModelSelector.setValue(first);
                updateUIState(false); // Switch to active chat mode
                return;
            }

            // Priority: Pending Selection (from loading chat) > Current Selection
            // (preserve)
            if (pendingModelSelection != null) {
                setModelName(pendingModelSelection);
                pendingModelSelection = null; // Clear after applying
            } else if (currentSelection != null && modelNames.contains(currentSelection)) {
                // Restore previous selection if still valid and no pending overwrite
                modelSelector.setValue(currentSelection);
                if (initialModelSelector != null)
                    initialModelSelector.setValue(currentSelection);
            }
        });
    }

    private ChatSession currentSession;

    public void setChatSession(ChatSession session) {
        this.currentSession = session;
        messagesContainer.getChildren().clear();

        // Clear any pending images when switching chats
        if (imagePreviewStrip != null) {
            imagePreviewStrip.clearImages();
        }

        if (session != null) {
            // Adaptive UI: Specific state based on message count
            boolean newChat = session.getMessages().isEmpty();
            updateUIState(newChat);

            // Restore model selection
            if (session.getModelName() != null && !session.getModelName().isEmpty()) {
                setModelName(session.getModelName());
            }

            // Restore Parameters
            double temp = session.getTemperature();
            tempSlider.setValue(temp);
            updateCreativityLabel(temp);

            ctxSlider.setValue(session.getNumCtx());
            topKSlider.setValue(session.getTopK());
            topPSlider.setValue(session.getTopP());

            seedField.setText(session.getSeed() == -1 ? "" : String.valueOf(session.getSeed()));

            // Restore System Prompt
            systemPromptField.setText(session.getSystemPrompt() != null ? session.getSystemPrompt() : "");

            // Restore tools and JSON mode
            toolsToggle.setSelected(session.isToolsEnabled());
            jsonModeToggle.setSelected(session.hasJsonFormat());
            if (session.hasJsonFormat()) {
                String fmt = session.getJsonFormat();
                jsonSchemaField.setText(!"json".equals(fmt) ? fmt : "");
                jsonSchemaField.setVisible(true);
                jsonSchemaField.setManaged(true);
            } else {
                jsonSchemaField.setVisible(false);
                jsonSchemaField.setManaged(false);
            }

            // Restore RAG collections
            selectedRagCollections.clear();
            if (session.getRagCollectionIds() != null) {
                selectedRagCollections.addAll(session.getRagCollectionIds());
            }
            buildRagChips();

            // Cleanup any empty assistant messages (from errors or cancellations) before
            // rendering
            session.getMessages().removeIf(msg -> "assistant".equals(msg.getRole())
                    && (msg.getContent() == null || msg.getContent().isEmpty()));

            for (ChatMessage msg : session.getMessages()) {
                boolean isUser = "user".equals(msg.getRole());
                boolean isTool = "tool".equals(msg.getRole());
                if (isTool) {
                    continue;
                }
                if (isUser && msg.hasAttachedDocuments()) {
                    addUserMessage(msg.getContent(),
                            msg.hasImages() ? msg.getImages() : null,
                            msg.getAttachedDocuments());
                } else if (!isUser && msg.hasToolCalls()) {
                    addAssistantMessage(msg.getContent() != null ? msg.getContent() : "");
                    for (ToolCall tc : msg.getToolCalls()) {
                        ToolCallCard card = new ToolCallCard(tc);
                        if (tc.getResult() != null) {
                            card.setCompleted();
                        }
                        addToolCallCard(card);
                    }
                    if (msg.hasGenerationMetrics()) {
                        addMetricsToLastMessage(msg.getGenerationMetrics());
                    }
                } else {
                    addMessage(msg.getContent(), isUser, msg.hasImages() ? msg.getImages() : null);
                    if (!isUser && msg.hasGenerationMetrics()) {
                        addMetricsToLastMessage(msg.getGenerationMetrics());
                    }
                }
            }
            updateContextBudget();
        }
    }

    @FXML
    private void sendMessage() {
        String text = inputField.getText();
        boolean hasImages = imagePreviewStrip.hasImages();
        boolean hasDocuments = documentPreviewStrip != null && documentPreviewStrip.hasFiles();

        if ((text.isEmpty() && !hasImages && !hasDocuments) || currentSession == null) {
            return;
        }

        updateUIState(false);

        if (isGenerating) {
            cancelGeneration();
            return;
        }

        String modelName = modelSelector.getValue();
        if (modelName == null) {
            addMessage("Error: " + App.getBundle().getString("chat.selectModel"), false, null);
            if (statusLabel != null) {
                statusLabel.setText(App.getBundle().getString("chat.status.ready"));
            }
            return;
        }

        // If documents are attached, route through the tiered flow.
        if (hasDocuments) {
            dispatchWithAttachments(text, modelName);
            return;
        }

        // Standard path: capture images, prepare session, dispatch generation.
        List<String> images = hasImages ? imagePreviewStrip.getBase64Images() : null;
        prepareSessionForMessage(text, modelName, images);

        inputField.clear();
        imagePreviewStrip.clearImages();
        setGeneratingState(true);
        updateStatusLabelForGeneration(images);

        activeResponseBuffer.setLength(0);
        ChatMessage assistantMsg = createAssistantPlaceholder();
        // Re-inline any documents attached in prior turns of this chat so
        // the model has them available again (Ollama does not retain
        // server-side conversation history).
        handleGenerationTask(modelName, wrapWithAttachedDocs(text), images, assistantMsg);
    }

    /**
     * Walks the session history, collects every previously attached
     * document (deduped by file name, keeping the latest content), and
     * prepends them to {@code userText} as a reference block. Returns the
     * original text unchanged if there are no attached documents in the
     * session.
     */
    private String wrapWithAttachedDocs(String userText) {
        if (currentSession == null) return userText;
        java.util.LinkedHashMap<String, com.graden.models.model.AttachedDocument> dedup = new java.util.LinkedHashMap<>();
        for (ChatMessage msg : currentSession.getMessages()) {
            if (!"user".equals(msg.getRole())) continue;
            if (!msg.hasAttachedDocuments()) continue;
            for (com.graden.models.model.AttachedDocument d : msg.getAttachedDocuments()) {
                if (d.getContent() == null || d.getContent().isBlank()) continue;
                dedup.put(d.getFileName(), d);
            }
        }
        if (dedup.isEmpty()) return userText;

        StringBuilder body = new StringBuilder();
        for (com.graden.models.model.AttachedDocument d : dedup.values()) {
            body.append("--- ").append(d.getFileName()).append(" ---\n");
            body.append(d.getContent()).append("\n\n");
        }
        String safeUserText = userText == null ? "" : userText;
        String langCode = com.graden.models.manager.ConfigManager.getInstance().getLanguage();
        String langName = "es".equals(langCode) ? "español" : "English";

        String fallback = "The user attached the following document(s). Use them as reference:\n\n"
                + body
                + "\nYou MUST respond in " + langName + ".\n\nUser's message: " + safeUserText;
        String rendered = com.graden.models.util.PromptTemplates.render(
                "inline_document",
                fallback,
                java.util.Map.of(
                        "count", String.valueOf(dedup.size()),
                        "documents", body.toString().trim(),
                        "language", langName));
        if (!safeUserText.isBlank()) {
            rendered = rendered + "\n\n" + safeUserText;
        }
        // Soft language hint at the very end — small models like gemma3:1b
        // often default to the language of the dominant content. This nudge
        // is intentionally light so the user can still ask mid-chat to
        // switch languages without fighting the directive.
        String reminder = "es".equals(langCode)
                ? "(Idioma de respuesta por defecto: español.)"
                : "(Default response language: English.)";
        rendered = rendered + "\n\n" + reminder;
        return rendered;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Attachment dispatch — single path: index into the "General" RAG
    // collection, auto-select it for the chat, then run the standard RAG
    // flow. Attached document metadata is stored on the user's
    // ChatMessage so the chat bubble can render pills with stats.
    // ─────────────────────────────────────────────────────────────────────

    private static final String GENERAL_COLLECTION_NAME = "General";

    private void dispatchWithAttachments(String userText, String modelName) {
        // Wait for any in-flight parsing before deciding inline vs RAG.
        if (!documentPreviewStrip.allReady()) {
            setGeneratingState(true);
            if (statusLabel != null) {
                statusLabel.setText(App.getBundle().getString("chat.attach.waitingParse"));
            }
            documentPreviewStrip.allReadyProperty().addListener(new javafx.beans.value.ChangeListener<>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends Boolean> obs, Boolean was, Boolean ready) {
                    if (Boolean.TRUE.equals(ready)) {
                        documentPreviewStrip.allReadyProperty().removeListener(this);
                        dispatchWithAttachments(userText, modelName);
                    }
                }
            });
            return;
        }

        List<com.graden.models.model.AttachedDocument> parsed = documentPreviewStrip.getReadyAttachments();
        if (parsed.isEmpty()) {
            // All parsing failed — show error and bail out.
            setGeneratingState(false);
            if (statusLabel != null) {
                statusLabel.setText(App.getBundle().getString("chat.attach.indexError"));
            }
            documentPreviewStrip.clear();
            return;
        }

        documentPreviewStrip.clear();
        dispatchToGeneral(userText, modelName, parsed);
    }

    /**
     * Indexes the parsed documents into the user's "General" RAG
     * collection (created if missing), auto-selects it for this chat, and
     * dispatches generation via the standard RAG path. The attached
     * document metadata (name, size, word count) is stored on the user's
     * {@link ChatMessage} so the chat bubble can render pills.
     */
    private void dispatchToGeneral(String userText, String modelName, List<com.graden.models.model.AttachedDocument> docs) {
        setGeneratingState(true);
        if (statusLabel != null) {
            statusLabel.setText(java.text.MessageFormat.format(
                    App.getBundle().getString("chat.status.indexingAttachments"), docs.size()));
        }

        App.getExecutorService().submit(() -> {
            try {
                RagManager rag = RagManager.getInstance();
                rag.initialize();

                // Always index into the user-visible "General" collection.
                RagCollection collection = rag.getCollections().stream()
                        .filter(c -> GENERAL_COLLECTION_NAME.equalsIgnoreCase(c.getName()))
                        .findFirst()
                        .orElseGet(() -> rag.createCollection(GENERAL_COLLECTION_NAME));

                java.util.List<javafx.concurrent.Task<Void>> tasks = new ArrayList<>();
                java.util.List<String> fileNames = new ArrayList<>();
                // Map fileName → RagDocumentItem so we can read its final
                // status after indexing and propagate to AttachedDocument.
                java.util.Map<String, RagDocumentItem> itemByName = new java.util.HashMap<>();
                for (com.graden.models.model.AttachedDocument d : docs) {
                    String hash = com.graden.models.util.HashUtils.sha256(d.getContent());
                    fileNames.add(d.getFileName());
                    if (rag.isDocumentIndexedByHash(hash, collection.getId())) {
                        // Already in the store from a previous attach — treat as indexed.
                        d.setRagStatus(com.graden.models.model.AttachedDocument.RagStatus.INDEXED);
                        continue;
                    }

                    File temp = File.createTempFile("GrandelGradenNexus-attach-", ".md");
                    temp.deleteOnExit();
                    java.nio.file.Files.writeString(temp.toPath(), d.getContent());
                    RagDocumentItem item = new RagDocumentItem(
                            d.getFileName(), temp.getAbsolutePath(), collection.getId(), hash);
                    rag.getDocuments().add(item);
                    itemByName.put(d.getFileName(), item);
                    tasks.add(rag.indexDocument(temp, item));
                }

                for (javafx.concurrent.Task<Void> t : tasks) {
                    try { t.get(); } catch (Exception ignored) { /* surfaced on item */ }
                }

                // Resolve RAG status for every attached doc. Inline content is
                // already available, so a RAG failure degrades to INLINE_ONLY,
                // not a hard failure.
                for (com.graden.models.model.AttachedDocument d : docs) {
                    if (d.getRagStatus() == com.graden.models.model.AttachedDocument.RagStatus.INDEXED) continue;
                    RagDocumentItem item = itemByName.get(d.getFileName());
                    if (item == null) {
                        d.setRagStatus(com.graden.models.model.AttachedDocument.RagStatus.INDEXED);
                    } else if (item.getStatus() == RagDocumentItem.Status.READY) {
                        d.setRagStatus(com.graden.models.model.AttachedDocument.RagStatus.INDEXED);
                    } else if (item.getStatus() == RagDocumentItem.Status.ERROR) {
                        d.setRagStatus(com.graden.models.model.AttachedDocument.RagStatus.INLINE_ONLY);
                    } else {
                        d.setRagStatus(com.graden.models.model.AttachedDocument.RagStatus.INLINE_ONLY);
                    }
                }

                Platform.runLater(() -> {
                    selectedRagCollections.add(collection.getId());
                    saveRagStateToSession();
                    buildRagChips();

                    String effectiveUserText = isTrivialMessage(userText)
                            ? buildAttachAutoprompt(fileNames)
                            : userText;
                    String displayedText = (userText == null || userText.isBlank())
                            ? buildAttachedSummaryDisplay(fileNames)
                            : userText;

                    List<String> images = imagePreviewStrip.hasImages()
                            ? imagePreviewStrip.getBase64Images() : null;

                    // Persist the attached document metadata on the user's
                    // ChatMessage so pills can be re-rendered after reload.
                    addUserMessage(displayedText, images, docs);
                    if (currentSession != null) {
                        ChatMessage userMsg = new ChatMessage("user", displayedText, images);
                        // Persist full content alongside metadata so we can
                        // re-inline the document on every follow-up turn.
                        userMsg.setAttachedDocuments(new ArrayList<>(docs));
                        currentSession.addMessage(userMsg);
                        currentSession.setModelName(modelName);
                        ChatManager.getInstance().saveChats();
                    }

                    inputField.clear();
                    imagePreviewStrip.clearImages();
                    updateStatusLabelForGeneration(images);
                    updateContextBudget();
                    activeResponseBuffer.setLength(0);
                    ChatMessage assistantMsg = createAssistantPlaceholder();
                    handleGenerationTask(modelName, wrapWithAttachedDocs(effectiveUserText), images, assistantMsg);
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Attachment dispatch to General failed", e);
                Platform.runLater(() -> {
                    setGeneratingState(false);
                    if (statusLabel != null) {
                        statusLabel.setText(App.getBundle().getString("chat.attach.indexError"));
                    }
                });
            }
        });
    }

    /** Considered "trivial" when there's effectively no question. */
    private static boolean isTrivialMessage(String text) {
        if (text == null) return true;
        String t = text.trim().toLowerCase();
        if (t.isEmpty()) return true;
        if (t.length() > 20) return false;
        return t.matches("(hola|hi|hello|hey|holi|holis|buenas|buenos d[ií]as|saludos|ok|okay|gracias)\\W*");
    }

    /** Synthetic prompt used by the hidden-RAG path when the user's message is trivial. */
    private String buildAttachAutoprompt(List<String> fileNames) {
        String langCode = com.graden.models.manager.ConfigManager.getInstance().getLanguage();
        String langName = "es".equals(langCode) ? "español" : "English";
        StringBuilder list = new StringBuilder();
        for (String n : fileNames) list.append("- ").append(n).append('\n');
        String fallback =
                "The user has just attached and indexed these documents:\n\n"
                + list
                + "\nGreet briefly in " + langName
                + " and give a short overview of what each appears to be about, using ONLY the retrieved context.";
        return com.graden.models.util.PromptTemplates.render(
                "attach_autoprompt",
                fallback,
                java.util.Map.of(
                        "document_list", list.toString().trim(),
                        "language", langName));
    }

    private String buildAttachedSummaryDisplay(List<String> fileNames) {
        String tpl = App.getBundle().getString("chat.attach.userSummary");
        return java.text.MessageFormat.format(tpl, fileNames.size());
    }

    private void prepareSessionForMessage(String text, String modelName, List<String> images) {
        addMessage(text, true, images);
        if (currentSession != null) {
            currentSession.addMessage(new ChatMessage("user", text, images));
            currentSession.setModelName(modelName);
            ChatManager.getInstance().saveChats();
        }
        updateContextBudget();
    }

    private void updateStatusLabelForGeneration(List<String> images) {
        if (statusLabel != null) {
            if (images != null && !images.isEmpty()) {
                statusLabel.setText(App.getBundle().getString("chat.status.analyzingImage"));
            } else {
                statusLabel.setText(App.getBundle().getString("chat.status.thinking"));
            }
        }
    }

    private ChatMessage createAssistantPlaceholder() {
        ChatMessage assistantMsg = new ChatMessage("assistant", "");
        if (currentSession != null) {
            currentSession.addMessage(assistantMsg);
            ChatManager.getInstance().saveChats();
        }
        addMessage("", false, null);
        if (statusLabel != null) {
            statusLabel.setText(App.getBundle().getString("chat.status.generating"));
        }
        return assistantMsg;
    }

    private void handleGenerationTask(String modelName, String text, List<String> images, ChatMessage assistantMsg) {
        final ChatSession targetSession = currentSession;
        final boolean ragEnabled = !selectedRagCollections.isEmpty();
        final Set<String> ragCollections = ragEnabled ? new HashSet<>(selectedRagCollections) : null;
        final boolean toolsEnabled = targetSession != null && targetSession.isToolsEnabled()
                && CapabilityManager.getInstance().supportsTools(modelName);

        currentGenerationTask = App.getExecutorService().submit(() -> {
            try {
                // RAG context retrieval (if enabled)
                List<RagResult> ragResults = null;
                if (ragEnabled && (images == null || images.isEmpty())) {
                    Platform.runLater(() -> {
                        if (statusLabel != null) {
                            statusLabel.setText(App.getBundle().getString("chat.status.searchingDocs"));
                        }
                    });
                    RagManager ragManager = RagManager.getInstance();
                    ragManager.initialize();
                    ragResults = ragManager.queryContext(
                            text,
                            com.graden.models.manager.ConfigManager.getInstance().getRagTopK(),
                            ragCollections);
                }

                final String effectiveUserText;
                if (ragResults != null && !ragResults.isEmpty()) {
                    int numCtx = targetSession != null ? targetSession.getNumCtx() : 4096;
                    effectiveUserText = RagManager.getInstance()
                            .buildAugmentedPrompt(text, ragResults, numCtx);
                } else if (ragEnabled) {
                    // RAG is on but retrieval found nothing relevant for this
                    // question. Tell the model so it answers honestly instead
                    // of claiming it "has no access to documents".
                    effectiveUserText =
                            "[Knowledge base note: the user has a knowledge base attached, "
                            + "but no passage relevant to this question was found. If the answer "
                            + "requires that knowledge base, say clearly that the document does not "
                            + "contain it — do NOT claim you have no access to documents.]\n\n"
                            + text;
                } else {
                    effectiveUserText = text;
                }

                Map<String, Object> options = collectGenerationOptions();
                String userSystemPrompt = targetSession != null ? targetSession.getSystemPrompt()
                        : systemPromptField.getText();
                final String languageDirective = loadLanguageDirective();
                final String markdownDirective = loadMarkdownDirectiveFor(modelName);

                String systemPrompt;
                if (userSystemPrompt == null || userSystemPrompt.isBlank()) {
                    systemPrompt = languageDirective + "\n\n" + markdownDirective;
                } else {
                    systemPrompt = languageDirective + "\n\n"
                            + userSystemPrompt.trim() + "\n\n"
                            + markdownDirective;
                }

                List<ToolDefinition> toolDefs = null;
                if (toolsEnabled) {
                    toolDefs = ToolRegistry.getInstance().getEnabledDefinitions();
                    if (!toolDefs.isEmpty()) {
                        systemPrompt = buildToolUseDirective(systemPrompt, toolDefs);
                    }
                }

                List<ChatMessage> conversationHistory = buildConversationHistory(targetSession);

                StringBuilder fullResponseBuilder = new StringBuilder();
                final java.util.concurrent.atomic.AtomicReference<GenerationMetrics> generatedMetrics =
                        new java.util.concurrent.atomic.AtomicReference<>();

                final int MAX_TOOL_ROUNDS = 8;
                final List<RagResult> finalRagResults = ragResults;

                for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                    if (Thread.currentThread().isInterrupted()) return;

                    Map<String, Object> currentOptions = new HashMap<>(options);
                    StreamRequest req = new StreamRequest()
                            .modelName(modelName)
                            .requestOptions(currentOptions)
                            .systemPrompt(systemPrompt)
                            .images(round == 0 ? images : null)
                            .tools(round == 0 ? toolDefs : toolDefs)
                            .jsonFormat(targetSession != null ? targetSession.getJsonFormat() : null)
                            .conversationHistory(new ArrayList<>(conversationHistory));

                    final java.util.concurrent.atomic.AtomicReference<StreamRequest.StreamCompleteEvent> completeEvent =
                            new java.util.concurrent.atomic.AtomicReference<>();

                    req.onComplete(event -> {
                        completeEvent.set(event);
                        if (event != null && event.metrics() != null) {
                            generatedMetrics.set(event.metrics());
                        }
                    });

                    req.streamHandler(new OllamaStreamHandler() {
                        @Override
                        public void accept(String messagePart) {
                            if (Thread.currentThread().isInterrupted()) return;
                            fullResponseBuilder.setLength(0);
                            fullResponseBuilder.append(messagePart);
                            String properFullText = fullResponseBuilder.toString();
                            assistantMsg.setContent(properFullText);

                            long now = System.currentTimeMillis();
                            if (now - lastUiUpdate > UI_UPDATE_INTERVAL_MS) {
                                Platform.runLater(() -> {
                                    if (currentSession == targetSession) {
                                        updateLastMessage(properFullText);
                                    }
                                });
                                lastUiUpdate = now;
                            }
                        }
                    });

                    OllamaManager.getInstance().askModelStream(req);

                    StreamRequest.StreamCompleteEvent event = completeEvent.get();
                    if (event == null) break;

                    String finalText = event.fullResponse();
                    assistantMsg.setContent(finalText);

                    List<com.graden.models.model.ToolCall> calls = event.toolCalls();
                    if (calls == null || calls.isEmpty()) {
                        GenerationMetrics metrics = generatedMetrics.get();
                        if (metrics != null) {
                            assistantMsg.setGenerationMetrics(metrics);
                        }
                        Platform.runLater(() -> {
                            if (currentSession == targetSession) {
                                updateLastMessage(finalText);
                                if (metrics != null) {
                                    addMetricsToLastMessage(metrics);
                                }
                                if (finalRagResults != null && !finalRagResults.isEmpty()) {
                                    addSourceCitations(finalRagResults);
                                }
                            }
                        });
                        break;
                    }

                    assistantMsg.setToolCalls(new ArrayList<>(calls));

                    Platform.runLater(() -> {
                        if (currentSession == targetSession) {
                            for (com.graden.models.model.ToolCall tc : calls) {
                                ToolCallCard card = new ToolCallCard(tc);
                                card.setRunning();
                                addToolCallCard(card);
                            }
                        }
                    });

                    ToolExecutor.executeAll(calls);

                    Platform.runLater(() -> {
                        if (currentSession == targetSession) {
                            for (com.graden.models.model.ToolCall tc : calls) {
                                updateToolCallCard(tc);
                            }
                        }
                    });

                    for (com.graden.models.model.ToolCall tc : calls) {
                        ChatMessage toolMsg = new ChatMessage("tool", tc.getResult());
                        toolMsg.setToolCallId(tc.getId());
                        conversationHistory.add(toolMsg);
                        if (targetSession != null) {
                            targetSession.addMessage(toolMsg);
                        }
                    }

                    if (round == MAX_TOOL_ROUNDS - 1) {
                        GenerationMetrics metrics = generatedMetrics.get();
                        if (metrics != null) {
                            assistantMsg.setGenerationMetrics(metrics);
                        }
                        Platform.runLater(() -> {
                            if (currentSession == targetSession) {
                                updateLastMessage(finalText);
                                if (metrics != null) {
                                    addMetricsToLastMessage(metrics);
                                }
                            }
                        });
                        break;
                    }
                }

                Platform.runLater(() -> {
                    if (currentSession == targetSession) {
                        assistantMsg.setContent(fullResponseBuilder.toString());
                        GenerationMetrics metrics = generatedMetrics.get();
                        if (metrics != null) {
                            assistantMsg.setGenerationMetrics(metrics);
                        }
                        ChatManager.getInstance().saveChats();
                        updateContextBudget();
                    }
                    setGeneratingState(false);
                });

            } catch (Exception e) {
                handleGenerationError(e, assistantMsg, targetSession);
            }
        });
    }

    private Map<String, Object> collectGenerationOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", tempSlider.getValue());
        options.put("num_ctx", (int) ctxSlider.getValue());
        options.put("top_k", (int) topKSlider.getValue());
        options.put("top_p", topPSlider.getValue());

        try {
            String seedText = seedField.getText().trim();
            if (!seedText.isEmpty()) {
                options.put("seed", Integer.parseInt(seedText));
            }
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Invalid seed format: {0}", seedField.getText());
        }
        return options;
    }

    private void handleGenerationError(Exception e, ChatMessage assistantMsg, ChatSession targetSession) {
        if (e instanceof InterruptedException || Thread.interrupted()
                || "Cancelled by user".equals(e.getMessage())) {
            Platform.runLater(() -> setGeneratingState(false));
            return;
        }

        LOGGER.log(Level.SEVERE, "Generation error", e);
        Platform.runLater(() -> {
            String errorMsg = "⚡ Error: " + e.getMessage();
            assistantMsg.setContent(errorMsg);
            if (targetSession != null) {
                ChatManager.getInstance().saveChats();
            }

            if (currentSession == targetSession) {
                updateLastMessage(errorMsg);
            }
            setGeneratingState(false);
        });
    }

    private void updateCreativityLabel(double val) {
        String label = "";
        // Colors: Precise (Blue), Balanced (Green), Creative (Orange/Red)
        if (val < 0.3) {
            label = App.getBundle().getString("chat.creativity.precise");
        } else if (val < 0.7) {
            label = App.getBundle().getString("chat.creativity.balanced");
        } else {
            label = App.getBundle().getString("chat.creativity.imaginative");
        }
        tempValueLabel.setText(label);
    }

    private void initializeAdvancedParameters() {
        // Presets
        ObservableList<String> presets = FXCollections.observableArrayList(
                App.getBundle().getString("chat.preset.default"),
                App.getBundle().getString("chat.preset.writer"), // Creative
                App.getBundle().getString("chat.preset.precise"), // Dev
                App.getBundle().getString("chat.preset.lawyer"),
                App.getBundle().getString("chat.preset.doctor"),
                App.getBundle().getString("chat.preset.student"));
        presetSelector.setItems(presets);

        presetSelector.setOnAction(e -> applyPreset());

        // Context Window
        ctxSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int val = newVal.intValue();
            ctxValueLabel.setText(String.valueOf(val));
            // Just use a static accent for context or maybe based on size?
            // Let's keep context blue/neutral or maybe subtle scale.
            // For now, let's just colorize Temp/TopP as they are "vibe" params.
            if (currentSession != null) {
                currentSession.setNumCtx(val);
                ChatManager.getInstance().saveChats();
            }
            updateContextBudget(); // budget changed → refresh the ring
        });

        // Top-K
        topKSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int val = newVal.intValue();
            topKValueLabel.setText(String.valueOf(val));
            // Maybe Green for low K (narrow)?
            // updateSliderColor(topKSlider, val, 1, 100);
            if (currentSession != null) {
                currentSession.setTopK(val);
                ChatManager.getInstance().saveChats();
            }
        });

        // Top-P
        topPSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double val = Math.round(newVal.doubleValue() * 100.0) / 100.0;
            topPValueLabel.setText(String.format("%.2f", val));

            if (currentSession != null) {
                currentSession.setTopP(val);
                ChatManager.getInstance().saveChats();
            }
        });

        // Init colors

        // Seed (Numeric only)
        seedField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("-?\\d*")) {
                seedField.setText(newValue.replaceAll("[^-?\\d]", ""));
            }
            // Save logic
            if (currentSession != null) {
                try {
                    int seed = newValue.isEmpty() || newValue.equals("-") ? -1 : Integer.parseInt(newValue);
                    currentSession.setSeed(seed);
                    ChatManager.getInstance().saveChats();
                } catch (NumberFormatException ignored) {
                }
            }
        });

        // Apply AtlantaFX ProgressSliderSkin
        tempSlider.setSkin(new ProgressSliderSkin(tempSlider));
        topPSlider.setSkin(new ProgressSliderSkin(topPSlider));
        topKSlider.setSkin(new ProgressSliderSkin(topKSlider));
        ctxSlider.setSkin(new ProgressSliderSkin(ctxSlider));
    }

    private void applyPreset() {
        String selected = presetSelector.getValue();
        if (selected == null)
            return;

        // Reset Styles
        rightSidebar.getStyleClass().removeAll("theme-creative", "theme-precise", "theme-professional",
                "theme-academic", "theme-medical");

        if (selected.equals(App.getBundle().getString("chat.preset.writer"))) {
            // Creative Writer
            tempSlider.setValue(0.9);
            topPSlider.setValue(0.95);
            topKSlider.setValue(50);
            ctxSlider.setValue(8192); // More context for stories
            rightSidebar.getStyleClass().add("theme-creative"); // Green
        } else if (selected.equals(App.getBundle().getString("chat.preset.precise"))) {
            // Developer
            tempSlider.setValue(0.2);
            topPSlider.setValue(0.3);
            topKSlider.setValue(20);
            ctxSlider.setValue(16384); // High context for codebases
            rightSidebar.getStyleClass().add("theme-precise"); // Blue
        } else if (selected.equals(App.getBundle().getString("chat.preset.lawyer"))) {
            // Lawyer
            tempSlider.setValue(0.3); // Low creativity, high accuracy
            topPSlider.setValue(0.4);
            ctxSlider.setValue(32768); // Max context for legal docs
            rightSidebar.getStyleClass().add("theme-professional"); // Purple
        } else if (selected.equals(App.getBundle().getString("chat.preset.doctor"))) {
            // Doctor
            tempSlider.setValue(0.1); // Extremely factual
            topPSlider.setValue(0.2);
            ctxSlider.setValue(16384);
            rightSidebar.getStyleClass().add("theme-medical"); // Red
        } else if (selected.equals(App.getBundle().getString("chat.preset.student"))) {
            // Student
            tempSlider.setValue(0.6); // Balanced, slightly creative
            topPSlider.setValue(0.8);
            ctxSlider.setValue(4096);
            rightSidebar.getStyleClass().add("theme-academic"); // Orange
        } else {
            // Default
            tempSlider.setValue(0.7);
            topPSlider.setValue(0.9);
            topKSlider.setValue(40);
            ctxSlider.setValue(4096);
            // No theme class implies default border
        }

    }

    private void updateLastMessage(String text) {
        if (!messagesContainer.getChildren().isEmpty()) {
            Node lastNode = messagesContainer.getChildren().get(messagesContainer.getChildren().size() - 1);

            if (lastNode instanceof HBox) {
                HBox container = (HBox) lastNode;
                if (!container.getChildren().isEmpty()) {
                    Node content = container.getChildren().get(0);

                    // Case 1: Direct MarkdownOutput
                    if (content instanceof MarkdownOutput) {
                        ((MarkdownOutput) content).updateContent(text);
                    }
                    // Case 2: Wrapped in VBox (New structure with Copy Button)
                    else if (content instanceof VBox) {
                        VBox wrapper = (VBox) content;
                        if (!wrapper.getChildren().isEmpty()) {
                            Node firstChild = wrapper.getChildren().get(0);
                            if (firstChild instanceof RingProgressIndicator) {
                                // Ollama emits empty content chunks before tool calls
                                // and during prefill. Keep the ring spinning until a
                                // real token arrives — replacing it with an empty
                                // MarkdownOutput would leave a blank gap.
                                if (text == null || text.isBlank()) {
                                    return;
                                }
                                // First real token: replace ONLY the ring with
                                // MarkdownOutput + Footer. A full clear() would also
                                // wipe any ToolCallCards already added to the wrapper
                                // during a tool-calling round.
                                wrapper.getChildren().remove(firstChild);
                                setupAssistantContent(wrapper, text);
                            } else {
                                for (Node child : wrapper.getChildren()) {
                                    if (child instanceof MarkdownOutput) {
                                        ((MarkdownOutput) child).updateContent(text);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void setGeneratingState(boolean generating) {
        this.isGenerating = generating;
        if (generating) {
            sendButton.setVisible(false);
            sendButton.setManaged(false);

            cancelButton.setVisible(true);
            cancelButton.setManaged(true);

            if (statusLabel != null)
                statusLabel.setText(App.getBundle().getString("chat.status.generating"));
        } else {
            cancelButton.setVisible(false);
            cancelButton.setManaged(false);

            sendButton.setVisible(true);
            sendButton.setManaged(true);

            if (statusLabel != null)
                statusLabel.setText(App.getBundle().getString("chat.status.ready"));
        }
    }

    @FXML
    private void onCancelClicked() {
        cancelGeneration();
    }

    private void cancelGeneration() {
        // Force close the stream at the network level
        OllamaManager.getInstance().cancelCurrentRequest();

        if (currentGenerationTask != null) {
            currentGenerationTask.cancel(true); // Interrupt thread as well
        }

        // Remove thinking indicator if it's still there
        Platform.runLater(this::cleanupThinkingIndicator);

        // Remove empty assistant message from history if cancelled before any tokens
        // arrived
        if (currentSession != null) {
            List<ChatMessage> msgs = currentSession.getMessages();
            if (!msgs.isEmpty()) {
                ChatMessage last = msgs.get(msgs.size() - 1);
                if ("assistant".equals(last.getRole()) && (last.getContent() == null || last.getContent().isEmpty())) {
                    msgs.remove(msgs.size() - 1);
                    ChatManager.getInstance().saveChats();
                }
            }
        }

        setGeneratingState(false);
    }

    private void cleanupThinkingIndicator() {
        if (!messagesContainer.getChildren().isEmpty()) {
            Node lastNode = messagesContainer.getChildren().get(messagesContainer.getChildren().size() - 1);
            if (lastNode instanceof HBox) {
                HBox container = (HBox) lastNode;
                if (!container.getChildren().isEmpty() && container.getChildren().get(0) instanceof VBox) {
                    VBox wrapper = (VBox) container.getChildren().get(0);
                    if (!wrapper.getChildren().isEmpty()
                            && wrapper.getChildren().get(0) instanceof RingProgressIndicator) {
                        // Remove the whole message container if it's just a placeholder ring
                        messagesContainer.getChildren().remove(lastNode);
                    }
                }
            }
        }
    }

    private void addMessage(String text, boolean isUser, List<String> images) {
        if (isUser) {
            addUserMessage(text, images, null);
        } else {
            addAssistantMessage(text);
        }
    }

    private void addUserMessage(String text, List<String> images,
                                List<com.graden.models.model.AttachedDocument> attachedDocs) {
        VBox userBubbleWrapper = new VBox(4);
        userBubbleWrapper.setAlignment(Pos.CENTER_RIGHT);

        if (images != null && !images.isEmpty()) {
            userBubbleWrapper.getChildren().add(createThumbnailRow(images));
        }

        if (attachedDocs != null && !attachedDocs.isEmpty()) {
            userBubbleWrapper.getChildren().add(createAttachedDocsRow(attachedDocs));
        }

        String displayText = text.isEmpty() ? App.getBundle().getString("chat.image.marker") : text;
        Label bubble = new Label(displayText);
        bubble.setWrapText(true);
        bubble.getStyleClass().addAll("chat-bubble", "chat-bubble-user");
        bubble.maxWidthProperty()
                .bind(Bindings.min(600.0, messagesContainer.widthProperty().subtract(60)));

        userBubbleWrapper.getChildren().add(bubble);

        // --- Action Toolbar: Copy + Edit ---
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("chat-message-toolbar");

        Button copyBtn = createActionButton("fth-copy",
                App.getBundle().getString("chat.copyMessage"));

        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(displayText);
            Clipboard.getSystemClipboard().setContent(content);

            FontIcon icon = (FontIcon) copyBtn.getGraphic();
            icon.setStyle("-fx-icon-color: -color-success-fg;");
            PauseTransition pause = new PauseTransition(Duration.seconds(1));
            pause.setOnFinished(ev -> icon.setStyle(""));
            pause.play();
        });

        Button editBtn = createActionButton("fth-edit",
                App.getBundle().getString("chat.editMessage"));

        editBtn.setOnAction(e -> {
            inputField.setText(text);
            inputField.requestFocus();
            inputField.positionCaret(text.length());
        });

        toolbar.getChildren().addAll(copyBtn, editBtn);
        userBubbleWrapper.getChildren().add(toolbar);

        HBox bubbleContainer = new HBox();
        bubbleContainer.setAlignment(Pos.CENTER_RIGHT);
        bubbleContainer.getChildren().add(userBubbleWrapper);
        messagesContainer.getChildren().add(bubbleContainer);
    }

    /**
     * Pill row rendered above the user's bubble for attached documents:
     * file-type icon + filename. Each pill has a tooltip with stats
     * (size, words, chars).
     */
    private javafx.scene.layout.FlowPane createAttachedDocsRow(
            List<com.graden.models.model.AttachedDocument> docs) {
        javafx.scene.layout.FlowPane row = new javafx.scene.layout.FlowPane(6, 6);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.getStyleClass().add("attached-docs-row");
        row.setPadding(new Insets(0, 0, 4, 0));

        for (com.graden.models.model.AttachedDocument d : docs) {
            HBox pill = new HBox(6);
            pill.getStyleClass().add("attached-doc-pill");
            pill.setAlignment(Pos.CENTER_LEFT);
            pill.setPadding(new Insets(4, 10, 4, 8));
            pill.setCursor(javafx.scene.Cursor.HAND);

            FontIcon icon = new FontIcon(iconForExtension(d.getExtension()));
            icon.setIconSize(14);
            icon.getStyleClass().add("attached-doc-pill-icon");

            Label name = new Label(d.getFileName());
            name.getStyleClass().add("attached-doc-pill-label");

            // Secondary badge reflecting RAG / quality state.
            FontIcon badge = ragBadgeForDoc(d);

            // Tooltip combines stats + RAG status so the user gets context
            // before opening the preview modal.
            String tipBase = com.graden.models.util.DocumentStats.format(d);
            String tipRag = "RAG: " + ragStatusKeyShort(d.getRagStatus());
            String tipQual = d.isLowQuality()
                    ? "\n⚠ " + App.getBundle().getString("chat.attach.lowQuality.note") : "";
            Tooltip tooltip = new Tooltip(tipBase + " · " + tipRag + tipQual);
            Tooltip.install(pill, tooltip);

            pill.getChildren().addAll(icon, name);
            if (badge != null) pill.getChildren().add(badge);
            pill.setOnMouseClicked(e -> com.graden.models.ui.AttachedDocumentPreview.show(d));

            row.getChildren().add(pill);
        }
        return row;
    }

    /** Small status badge shown next to the filename inside a pill. */
    private static FontIcon ragBadgeForDoc(com.graden.models.model.AttachedDocument d) {
        if (d.isLowQuality()) {
            FontIcon warn = new FontIcon("fth-alert-triangle");
            warn.setIconSize(11);
            warn.getStyleClass().add("attached-doc-pill-badge-warn");
            return warn;
        }
        switch (d.getRagStatus()) {
            case INDEXED:
                FontIcon ok = new FontIcon("fth-check");
                ok.setIconSize(11);
                ok.getStyleClass().add("attached-doc-pill-badge-ok");
                return ok;
            case INLINE_ONLY:
                FontIcon partial = new FontIcon("fth-zap");
                partial.setIconSize(11);
                partial.getStyleClass().add("attached-doc-pill-badge-partial");
                return partial;
            case FAILED:
                FontIcon fail = new FontIcon("fth-x-circle");
                fail.setIconSize(11);
                fail.getStyleClass().add("attached-doc-pill-badge-fail");
                return fail;
            default:
                return null;
        }
    }

    private static String ragStatusKeyShort(com.graden.models.model.AttachedDocument.RagStatus s) {
        if (s == null) return "—";
        switch (s) {
            case INDEXED:     return App.getBundle().getString("chat.attach.ragStatus.indexed.short");
            case INLINE_ONLY: return App.getBundle().getString("chat.attach.ragStatus.inlineOnly.short");
            case FAILED:      return App.getBundle().getString("chat.attach.ragStatus.failed.short");
            default:          return App.getBundle().getString("chat.attach.ragStatus.pending.short");
        }
    }

    private static String iconForExtension(String ext) {
        if (ext == null) return "fth-file";
        switch (ext.toLowerCase()) {
            case "pdf": case "txt": case "md": case "markdown": case "rtf":
            case "docx": case "log":
                return "fth-file-text";
            case "py": case "js": case "ts": case "jsx": case "tsx":
            case "java": case "kt": case "scala": case "go": case "rs":
            case "cpp": case "cc": case "cxx": case "c": case "h": case "hpp":
            case "cs": case "swift": case "rb": case "php": case "sh":
            case "bash": case "zsh": case "sql": case "r": case "lua":
            case "groovy": case "dart":
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

    private HBox createThumbnailRow(List<String> images) {
        HBox thumbnailRow = new HBox(6);
        thumbnailRow.setAlignment(Pos.CENTER_RIGHT);
        thumbnailRow.setPadding(new Insets(0, 0, 4, 0));
        for (String base64 : images) {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64);
                Image img = new Image(new ByteArrayInputStream(bytes), 80,
                        80, true, true);
                ImageView iv = new ImageView(img);
                iv.setFitWidth(80);
                iv.setFitHeight(80);
                iv.setPreserveRatio(true);
                Rectangle clip = new Rectangle(80, 80);
                clip.setArcWidth(12);
                clip.setArcHeight(12);
                iv.setClip(clip);
                iv.getStyleClass().add("chat-bubble-image");
                thumbnailRow.getChildren().add(iv);
            } catch (Exception ignored) {
            }
        }
        return thumbnailRow;
    }

    private void addAssistantMessage(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(10, 20, 10, 20));

        VBox contentWrapper = new VBox();
        contentWrapper.setStyle("-fx-background-color: transparent;");
        HBox.setHgrow(contentWrapper, Priority.ALWAYS);
        contentWrapper.maxWidthProperty()
                .bind(Bindings.min(800.0, messagesContainer.widthProperty().subtract(100)));

        if (text.isEmpty()) {
            RingProgressIndicator ring = new RingProgressIndicator();
            ring.setProgress(-1);
            contentWrapper.getChildren().add(ring);
        } else {
            setupAssistantContent(contentWrapper, text);
        }

        container.getChildren().add(contentWrapper);
        messagesContainer.getChildren().add(container);
    }

    private void setupAssistantContent(VBox contentWrapper, String text) {
        MarkdownOutput markdownOutput = new MarkdownOutput();
        markdownOutput.setMaxWidth(Double.MAX_VALUE);
        markdownOutput.setMarkdown(text);

        // --- Action Toolbar: Copy + Regenerate ---
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("chat-message-toolbar");

        Button copyBtn = createActionButton("fth-copy",
                App.getBundle().getString("chat.copyMessage"));

        copyBtn.setOnAction(e -> {
            String selectedText = MarkdownTextSelection.getSelectedTextIn(markdownOutput);
            String textToCopy = selectedText.isEmpty() ? markdownOutput.getMarkdown() : selectedText;

            ClipboardContent content = new ClipboardContent();
            content.putString(textToCopy);
            Clipboard.getSystemClipboard().setContent(content);

            FontIcon icon = (FontIcon) copyBtn.getGraphic();
            icon.setStyle("-fx-icon-color: -color-success-fg;");
            PauseTransition pause = new PauseTransition(Duration.seconds(1));
            pause.setOnFinished(ev -> icon.setStyle(""));
            pause.play();
        });

        Button regenBtn = createActionButton("fth-refresh-cw",
                App.getBundle().getString("chat.regenerateResponse"));

        regenBtn.setOnAction(e -> regenerateResponse());

        toolbar.getChildren().addAll(copyBtn, regenBtn);
        contentWrapper.getChildren().addAll(markdownOutput, toolbar);
    }

    private Button createActionButton(String iconLiteral, String tooltipText) {
        Button btn = new Button();

        FontIcon icon = new FontIcon(iconLiteral);
        btn.setGraphic(icon);
        btn.setTooltip(new Tooltip(tooltipText));
        return btn;
    }

    private void regenerateResponse() {
        if (currentSession == null) return;
        if (isGenerating) {
            cancelGeneration();
        }

        List<ChatMessage> messages = currentSession.getMessages();
        if (messages.size() < 2) return;

        ChatMessage lastUserMsg = null;
        int lastUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                lastUserMsg = messages.get(i);
                lastUserIdx = i;
                break;
            }
        }

        if (lastUserMsg == null) return;

        // Remove assistant and tool messages after the last user message from session
        for (int i = messages.size() - 1; i > lastUserIdx; i--) {
            messages.remove(i);
        }

        // Remove the last assistant message bubble (including any ToolCallCards) from the UI
        if (!messagesContainer.getChildren().isEmpty()) {
            messagesContainer.getChildren().remove(messagesContainer.getChildren().size() - 1);
        }

        // Start regeneration
        setGeneratingState(true);
        activeResponseBuffer.setLength(0);

        String modelName = modelSelector.getValue();
        if (modelName == null) {
            modelName = currentSession.getModelName();
        }
        if (modelName == null) return;

        ChatMessage assistantMsg = createAssistantPlaceholder();
        handleGenerationTask(modelName, lastUserMsg.getContent(), lastUserMsg.getImages(), assistantMsg);
    }

    /**
     * Add clickable source citation pills below the last assistant message.
     */
    private void addSourceCitations(List<RagResult> results) {
        if (results == null || results.isEmpty()) return;

        javafx.scene.layout.FlowPane sourcesRow = new javafx.scene.layout.FlowPane(6, 6);
        sourcesRow.setAlignment(Pos.CENTER_LEFT);
        sourcesRow.setPadding(new Insets(5, 20, 5, 20));

        Label sourcesLabel = new Label(App.getBundle().getString("chat.rag.sources"));
        sourcesLabel.getStyleClass().add("rag-doc-status");
        sourcesRow.getChildren().add(sourcesLabel);

        // Group retrieved fragments by source file. One pill per file; clicking
        // it opens an in-app modal showing the EXACT passages the model was
        // given — citations the user can verify, not just a filename.
        java.util.LinkedHashMap<String, List<RagResult>> byFile = new java.util.LinkedHashMap<>();
        for (RagResult r : results) {
            String fn = r.getFileName() != null ? r.getFileName() : "—";
            byFile.computeIfAbsent(fn, k -> new ArrayList<>()).add(r);
        }

        byFile.forEach((fileName, fragments) -> {
            HBox pill = new HBox(5);
            pill.getStyleClass().add("rag-source-pill");
            pill.setAlignment(Pos.CENTER_LEFT);
            pill.setCursor(javafx.scene.Cursor.HAND);

            FontIcon pillIcon = new FontIcon("fth-file-text");
            pillIcon.setIconSize(11);
            pillIcon.getStyleClass().add("rag-source-pill-icon");

            Label pillLabel = new Label(fileName);
            pillLabel.getStyleClass().add("rag-source-pill-label");

            pill.getChildren().addAll(pillIcon, pillLabel);
            if (fragments.size() > 1) {
                Label count = new Label(String.valueOf(fragments.size()));
                count.getStyleClass().add("rag-source-pill-count");
                pill.getChildren().add(count);
            }

            pill.setOnMouseClicked(e ->
                    com.graden.models.ui.RagSourcePreview.show(fileName, fragments));
            sourcesRow.getChildren().add(pill);
        });

        messagesContainer.getChildren().add(sourcesRow);
    }

    /**
     * Add a subtle generation-metrics label below the last assistant message,
     * before the action toolbar. Shows elapsed seconds, token count and tok/s.
     */
    private void addMetricsToLastMessage(GenerationMetrics metrics) {
        if (metrics == null || messagesContainer.getChildren().isEmpty()) return;

        Node lastNode = messagesContainer.getChildren().get(messagesContainer.getChildren().size() - 1);
        if (!(lastNode instanceof HBox)) return;

        HBox container = (HBox) lastNode;
        if (container.getChildren().isEmpty()) return;

        Node content = container.getChildren().get(0);
        if (!(content instanceof VBox)) return;

        VBox wrapper = (VBox) content;
        // Find index of the toolbar to insert metrics just before it
        int toolbarIdx = -1;
        for (int i = 0; i < wrapper.getChildren().size(); i++) {
            Node child = wrapper.getChildren().get(i);
            if (child instanceof HBox && child.getStyleClass().contains("chat-message-toolbar")) {
                toolbarIdx = i;
                break;
            }
        }

        double seconds = metrics.elapsedSeconds();
        String metricsText = String.format("%.1fs \u00B7 %d tokens \u00B7 %.1f tok/s",
                seconds, metrics.evalCount(), metrics.tokensPerSecond());

        // Buscar si ya existe una etiqueta de métricas para actualizarla
        Label metricsLabel = null;
        for (Node child : wrapper.getChildren()) {
            if (child instanceof Label && child.getStyleClass().contains("chat-metrics")) {
                metricsLabel = (Label) child;
                break;
            }
        }

        if (metricsLabel != null) {
            metricsLabel.setText(metricsText);
        } else {
            metricsLabel = new Label(metricsText);
            metricsLabel.getStyleClass().add("chat-metrics");
            int insertIdx = (toolbarIdx >= 0) ? toolbarIdx : wrapper.getChildren().size();
            wrapper.getChildren().add(insertIdx, metricsLabel);
        }
    }

    private void addToolCallCard(ToolCallCard card) {
        if (messagesContainer.getChildren().isEmpty()) return;

        Node lastNode = messagesContainer.getChildren().get(messagesContainer.getChildren().size() - 1);
        if (!(lastNode instanceof HBox)) return;

        HBox container = (HBox) lastNode;
        if (container.getChildren().isEmpty()) return;

        Node content = container.getChildren().get(0);
        if (!(content instanceof VBox)) return;

        VBox wrapper = (VBox) content;

        int toolbarIdx = -1;
        for (int i = 0; i < wrapper.getChildren().size(); i++) {
            Node child = wrapper.getChildren().get(i);
            if (child instanceof javafx.scene.layout.HBox && child.getStyleClass().contains("chat-message-toolbar")) {
                toolbarIdx = i;
                break;
            }
        }

        int insertIdx = (toolbarIdx >= 0) ? toolbarIdx : wrapper.getChildren().size();
        VBox.setMargin(card, new Insets(8, 0, 4, 0));
        wrapper.getChildren().add(insertIdx, card);
    }

    private void updateToolCallCard(ToolCall tc) {
        if (messagesContainer.getChildren().isEmpty()) return;

        Node lastNode = messagesContainer.getChildren().get(messagesContainer.getChildren().size() - 1);
        if (!(lastNode instanceof HBox)) return;

        HBox container = (HBox) lastNode;
        if (container.getChildren().isEmpty()) return;

        Node content = container.getChildren().get(0);
        if (!(content instanceof VBox)) return;

        VBox wrapper = (VBox) content;
        for (Node child : wrapper.getChildren()) {
            if (child instanceof ToolCallCard) {
                ToolCallCard card = (ToolCallCard) child;
                if (tc.getId().equals(card.getToolCall().getId())) {
                    card.setCompleted();
                    return;
                }
            }
        }
    }

    @FXML
    private void toggleSidebar() {
        if (rightSidebar != null) {
            boolean isVisible = rightSidebar.isVisible();
            rightSidebar.setVisible(!isVisible);
            rightSidebar.setManaged(!isVisible);
        }
    }

    private void setupResponsiveRightSidebar() {
        responsiveManager.mobileProperty().addListener((obs, wasMobile, isMobile) -> {
            Platform.runLater(() -> {
                if (isMobile) {
                    // On mobile, hide right sidebar by default
                    if (rightSidebar != null) {
                        rightSidebar.setVisible(false);
                        rightSidebar.setManaged(false);
                    }
                    // Show floating toggle button
                    if (sidebarToggleButton != null) {
                        sidebarToggleButton.setVisible(true);
                        sidebarToggleButton.setManaged(true);
                    }
                } else {
                    // On tablet/desktop, show right sidebar
                    if (rightSidebar != null) {
                        rightSidebar.setVisible(true);
                        rightSidebar.setManaged(true);
                    }
                    // Hide floating toggle button
                    if (sidebarToggleButton != null) {
                        sidebarToggleButton.setVisible(false);
                        sidebarToggleButton.setManaged(false);
                    }
                }
            });
        });

        // Initial state
        if (responsiveManager.isMobile()) {
            if (rightSidebar != null) {
                rightSidebar.setVisible(false);
                rightSidebar.setManaged(false);
            }
            if (sidebarToggleButton != null) {
                sidebarToggleButton.setVisible(true);
                sidebarToggleButton.setManaged(true);
            }
        }
    }

    @FXML
    private VBox rightSidebar;
    @FXML
    private Button sidebarToggleButton;

    private ResponsiveManager responsiveManager;

    private boolean modelListLoaded = false;
    private String pendingModelSelection = null;

    public void setModelName(String name) {
        if (!modelListLoaded || modelSelector.getItems() == null || modelSelector.getItems().isEmpty()) {
            this.pendingModelSelection = name;
            return;
        }

        for (String modelName : modelSelector.getItems()) {
            if (modelName.equals(name) || modelName.startsWith(name + ":")) {
                modelSelector.getSelectionModel().select(modelName);
                break;
            }
        }
    }

    // Default Sidebar state logic moved to setChatSession and initialize

    // --- Multimodal: Drag & Drop and Image Preview ---

    private void initializeImagePreviewStrip() {
        imagePreviewStrip = new ImagePreviewStrip();

        visionWarningLabel = new Label();
        visionWarningLabel.getStyleClass().add("vision-warning-label");
        visionWarningLabel.setVisible(false);
        visionWarningLabel.setManaged(false);

        // Listen for image changes to toggle warning
        imagePreviewStrip.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
            updateVisionWarning();
        });

        // Inject strip + warning into inputCapsule (between TextArea and toolbar)
        if (inputCapsule != null && inputCapsule.getChildren().size() >= 2) {
            inputCapsule.getChildren().add(1, visionWarningLabel);
            inputCapsule.getChildren().add(1, imagePreviewStrip);
        }

        // Documents preview strip — files attached but not yet indexed.
        documentPreviewStrip = new com.graden.models.ui.DocumentPreviewStrip();
        if (inputCapsule != null && inputCapsule.getChildren().size() >= 2) {
            inputCapsule.getChildren().add(1, documentPreviewStrip);
        }

        // Context-usage ring — a small Claude-style gauge tucked into the
        // input toolbar (next to the model selector), instead of an
        // intrusive full-width pill.
        contextRing = new com.graden.models.ui.ContextRing();
        if (inputToolbarRow != null) {
            int idx = modelSelector != null
                    ? inputToolbarRow.getChildren().indexOf(modelSelector)
                    : -1;
            if (idx >= 0) {
                inputToolbarRow.getChildren().add(idx, contextRing);
            } else {
                inputToolbarRow.getChildren().add(contextRing);
            }
        }
        // Refresh whenever the user adds/removes a document.
        documentPreviewStrip.emptyProperty().addListener((o, was, isEmpty) -> updateContextBudget());
        documentPreviewStrip.allReadyProperty().addListener((o, was, ready) -> updateContextBudget());
    }

    /** Rough fixed overhead (chars) for the injected system prompt
     *  (language directive + markdown directive + optional tool directive). */
    private static final int SYSTEM_PROMPT_OVERHEAD_CHARS = 700;

    /**
     * Updates the context-usage ring. Estimates the WHOLE context the model
     * would see — every chat message (user/assistant/tool), attached
     * document contents, the system prompt overhead, and whatever the user
     * is currently typing — against the configured {@code numCtx}.
     * Approximation: 4 chars ≈ 1 token.
     */
    private void updateContextBudget() {
        if (contextRing == null) return;
        int totalChars = 0;
        int messageCount = 0;

        if (currentSession != null) {
            for (ChatMessage msg : currentSession.getMessages()) {
                messageCount++;
                if (msg.getContent() != null) totalChars += msg.getContent().length();
                if (msg.hasAttachedDocuments()) {
                    for (com.graden.models.model.AttachedDocument d : msg.getAttachedDocuments()) {
                        if (d.getContent() != null) totalChars += d.getContent().length();
                    }
                }
            }
            // System instructions configured for this session.
            if (currentSession.getSystemPrompt() != null) {
                totalChars += currentSession.getSystemPrompt().length();
            }
        }
        // Documents queued in the strip but not sent yet.
        if (documentPreviewStrip != null) {
            for (com.graden.models.model.AttachedDocument d : documentPreviewStrip.getReadyAttachments()) {
                if (d.getContent() != null) totalChars += d.getContent().length();
            }
        }
        // What the user is currently typing.
        if (inputField != null && inputField.getText() != null) {
            totalChars += inputField.getText().length();
        }

        // Nothing meaningful yet (empty chat, empty input) → hide the ring.
        if (totalChars == 0 && messageCount == 0) {
            contextRing.hideRing();
            return;
        }

        totalChars += SYSTEM_PROMPT_OVERHEAD_CHARS; // injected directives

        int numCtx = currentSession != null ? currentSession.getNumCtx() : 4096;
        int approxTokens = totalChars / 4;

        String tooltip = java.text.MessageFormat.format(
                App.getBundle().getString("chat.context.usage"), approxTokens, numCtx);
        contextRing.update(approxTokens, numCtx, tooltip);
    }

    @FXML
    private void onExportMdClicked() {
        if (currentSession == null || currentSession.getMessages().isEmpty()) {
            Utils.showError("Export Error", "There are no messages to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Chat to Markdown");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown Files", "*.md"));

        // Suggest a filename
        String safeName = currentSession.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        fileChooser.setInitialFileName(safeName + ".md");

        File file = fileChooser.showSaveDialog(inputField.getScene().getWindow());
        if (file != null) {
            try {
                MarkdownService.exportChatToMarkdown(currentSession, file);

                // Show info using standard Alert
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText(null);
                alert.setContentText("Chat exported to " + file.getName());
                alert.showAndWait();

            } catch (IOException e) {
                e.printStackTrace();
                Utils.showError("Export Error", "Failed to export chat: " + e.getMessage());
            }
        }
    }

    private void initializeDragAndDrop() {
        // Drag over handler for inputField
        inputField.setOnDragOver(this::handleDragOver);
        inputField.setOnDragDropped(this::handleDragDropped);
        inputField.setOnDragExited(e -> inputField.getStyleClass().remove("drag-overlay"));

        // Also allow drops on the messages container
        messagesContainer.setOnDragOver(this::handleDragOver);
        messagesContainer.setOnDragDropped(this::handleDragDropped);
    }

    private void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            boolean anyAcceptable = event.getDragboard().getFiles().stream()
                    .anyMatch(f -> ImageUtils.isSupportedFormat(f) || RagManager.isSupportedFile(f));
            if (anyAcceptable) {
                event.acceptTransferModes(TransferMode.COPY);
                if (!inputField.getStyleClass().contains("drag-overlay")) {
                    inputField.getStyleClass().add("drag-overlay");
                }
            }
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        inputField.getStyleClass().remove("drag-overlay");
        boolean success = false;
        int rejectedLarge = 0;
        if (event.getDragboard().hasFiles()) {
            for (File file : event.getDragboard().getFiles()) {
                if (ImageUtils.isSupportedFormat(file)) {
                    String error = imagePreviewStrip.addImage(file);
                    if (error != null) {
                        if (statusLabel != null) {
                            String msg = App.getBundle().getString(error);
                            statusLabel.setText(msg);
                            PauseTransition reset = new PauseTransition(Duration.seconds(3));
                            reset.setOnFinished(ev -> statusLabel.setText(
                                    App.getBundle().getString("chat.status.ready")));
                            reset.play();
                        }
                    } else {
                        success = true;
                    }
                } else if (RagManager.isSupportedFile(file)) {
                    com.graden.models.ui.DocumentPreviewStrip.AddResult r =
                            documentPreviewStrip.addFile(file);
                    if (r == com.graden.models.ui.DocumentPreviewStrip.AddResult.OK) {
                        success = true;
                    } else if (r == com.graden.models.ui.DocumentPreviewStrip.AddResult.TOO_LARGE) {
                        rejectedLarge++;
                    }
                }
            }
        }
        if (rejectedLarge > 0 && statusLabel != null) {
            long limitMb = com.graden.models.ui.DocumentPreviewStrip.MAX_ATTACH_BYTES / (1024 * 1024);
            statusLabel.setText(java.text.MessageFormat.format(
                    App.getBundle().getString("chat.attach.tooLarge"),
                    rejectedLarge, limitMb));
        }
        event.setDropCompleted(success);
        event.consume();
    }

    @FXML
    private void onAttachClicked() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("attach-menu");

        MenuItem images = new MenuItem(App.getBundle().getString("chat.attach.images"));
        FontIcon imgIcon = new FontIcon("fth-image");
        imgIcon.setIconSize(14);
        images.setGraphic(imgIcon);
        images.setOnAction(e -> chooseImagesAttachment());

        MenuItem docs = new MenuItem(App.getBundle().getString("chat.attach.documents"));
        FontIcon docIcon = new FontIcon("fth-file-text");
        docIcon.setIconSize(14);
        docs.setGraphic(docIcon);
        docs.setOnAction(e -> chooseDocumentsAttachment());

        menu.getItems().addAll(images, docs);
        // Show above the button (chat input sits at the bottom of the screen).
        javafx.geometry.Point2D pos = attachButton.localToScreen(0, 0);
        menu.show(attachButton, pos.getX(), pos.getY() - 4);
    }

    private void chooseImagesAttachment() {
        FileChooser fc = new FileChooser();
        fc.setTitle(App.getBundle().getString("chat.attach.images.title"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        List<File> selected = fc.showOpenMultipleDialog(attachButton.getScene().getWindow());
        if (selected == null) return;
        for (File file : selected) {
            if (ImageUtils.isValidImageFile(file)) {
                imagePreviewStrip.addImage(file);
            }
        }
    }

    private void chooseDocumentsAttachment() {
        FileChooser fc = new FileChooser();
        fc.setTitle(App.getBundle().getString("chat.attach.documents.title"));
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All supported",
                        "*.pdf", "*.txt", "*.md", "*.markdown", "*.rtf",
                        "*.docx", "*.xlsx", "*.pptx",
                        "*.csv", "*.tsv", "*.json", "*.yaml", "*.yml", "*.xml",
                        "*.toml", "*.ini", "*.conf", "*.log", "*.properties",
                        "*.html", "*.htm", "*.css", "*.scss",
                        "*.py", "*.js", "*.ts", "*.jsx", "*.tsx",
                        "*.java", "*.kt", "*.scala", "*.go", "*.rs",
                        "*.cpp", "*.cc", "*.c", "*.h", "*.hpp",
                        "*.cs", "*.swift", "*.rb", "*.php",
                        "*.sh", "*.bash", "*.zsh", "*.sql", "*.r", "*.lua",
                        "*.groovy", "*.dart"),
                new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.txt", "*.md", "*.docx", "*.rtf"),
                new FileChooser.ExtensionFilter("Code", "*.py", "*.js", "*.ts", "*.java", "*.go", "*.rs",
                        "*.cpp", "*.c", "*.cs", "*.swift", "*.rb", "*.php", "*.sh", "*.sql"),
                new FileChooser.ExtensionFilter("Data", "*.csv", "*.tsv", "*.json", "*.yaml", "*.xml", "*.xlsx"));
        List<File> selected = fc.showOpenMultipleDialog(attachButton.getScene().getWindow());
        if (selected == null) return;
        int rejectedLarge = 0;
        for (File file : selected) {
            if (!RagManager.isSupportedFile(file)) continue;
            com.graden.models.ui.DocumentPreviewStrip.AddResult r = documentPreviewStrip.addFile(file);
            if (r == com.graden.models.ui.DocumentPreviewStrip.AddResult.TOO_LARGE) {
                rejectedLarge++;
            }
        }
        if (rejectedLarge > 0 && statusLabel != null) {
            long limitMb = com.graden.models.ui.DocumentPreviewStrip.MAX_ATTACH_BYTES / (1024 * 1024);
            statusLabel.setText(java.text.MessageFormat.format(
                    App.getBundle().getString("chat.attach.tooLarge"),
                    rejectedLarge, limitMb));
        }
    }

    /**
     * Index a document file into the RAG collection mapped to the chat's folder.
     * If the chat is inside a folder, use/create a RAG collection named after that folder.
     * If the chat is at root level, use/create a "General" collection.
     */
    private void indexDocumentToRag(File file) {
        if (currentSession == null) return;

        // Determine target collection name from chat's folder
        String collectionName = "General";
        ChatCollectionManager ccm = ChatCollectionManager.getInstance();
        ChatFolder folder = ccm.getFolderForChat(currentSession);
        if (folder != null && folder.getName() != null && !folder.getName().isBlank()) {
            collectionName = folder.getName();
        }

        // Find or create RAG collection
        RagManager ragManager = RagManager.getInstance();
        ragManager.initialize();
        final String targetName = collectionName;
        RagCollection collection = ragManager.getCollections().stream()
                .filter(c -> c.getName().equalsIgnoreCase(targetName))
                .findFirst()
                .orElseGet(() -> ragManager.createCollection(targetName));

        // Create document item and index
        RagDocumentItem docItem = new RagDocumentItem(file.getName(), file.getAbsolutePath(), collection.getId());
        ragManager.getDocuments().add(docItem); // <-- ADDED: Also add to the UI list
        ragManager.indexDocument(file, docItem);

        // Add to selected collections and update UI
        selectedRagCollections.add(collection.getId());
        saveRagStateToSession();
        buildRagChips();

        LOGGER.info("Attached document '" + file.getName() + "' to RAG collection '" + targetName + "'");
    }

    private void updateVisionWarning() {
        if (visionWarningLabel == null || imagePreviewStrip == null)
            return;

        if (!imagePreviewStrip.hasImages()) {
            visionWarningLabel.setVisible(false);
            visionWarningLabel.setManaged(false);
            return;
        }

        // Check if current model supports vision
        String selectedModel = modelSelector.getValue();
        boolean isVisionModel = false;
        if (selectedModel != null) {
            // Check badges from the model manager's local models
            isVisionModel = ModelManager.getInstance().getLocalModels().stream()
                    .filter(m -> (m.getName() + ":" + m.getTag()).equals(selectedModel))
                    .findFirst()
                    .map(m -> m.getBadges().stream()
                            .anyMatch(b -> b.toLowerCase().contains("vision")))
                    .orElse(false);
        }

        if (!isVisionModel) {
            visionWarningLabel.setText(App.getBundle().getString("chat.image.visionWarning"));
            visionWarningLabel.setVisible(true);
            visionWarningLabel.setManaged(true);
        } else {
            visionWarningLabel.setVisible(false);
            visionWarningLabel.setManaged(false);
        }
    }

    /** Models with fewer than this many billions of parameters get the minimal directive. */
    private static final double SMALL_MODEL_THRESHOLD_B = 3.0;

    /** Loads the language directive for the currently configured app language. */
    private static String loadLanguageDirective() {
        String langCode = com.graden.models.manager.ConfigManager.getInstance().getLanguage();
        String langName = "es".equals(langCode) ? "español" : "English";
        String fallback = "CRITICAL: Always respond in " + langName
                + ", regardless of the language of the input or context.";
        return com.graden.models.util.PromptTemplates.render(
                "language_directive",
                fallback,
                java.util.Map.of("language", langName));
    }

    /**
     * Loads the Markdown directive matching the model's size. Small models
     * (≤ {@value #SMALL_MODEL_THRESHOLD_B}B) get a minimal directive because
     * they tend to over-apply complex formatting rules. Templates live in
     * {@code resources/prompts/} so they can be tuned without recompiling.
     */
    private static String loadMarkdownDirectiveFor(String modelName) {
        double sizeB = parseModelSizeBillions(modelName);
        boolean small = sizeB > 0 && sizeB < SMALL_MODEL_THRESHOLD_B;
        String templateName = small ? "markdown_directive_minimal" : "markdown_directive";
        String fallback = small
                ? "Reply in plain prose. Match the user's language. Wrap any code in triple-backtick fenced blocks. Never start a normal line with \"#\"."
                : "Use Markdown only when it helps readability. Wrap code in triple-backtick fenced blocks; never emit \"#\" lines outside fenced blocks. Use headings sparingly. Match the user's language.";
        return com.graden.models.util.PromptTemplates.load(templateName, fallback);
    }

    /**
     * Best-effort extraction of parameter count (in billions) from a model
     * name like "gemma3:1b" or "llama3:8b-instruct". Returns 0 if unknown.
     */
    private static double parseModelSizeBillions(String modelName) {
        if (modelName == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)b\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(modelName);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private void updateToolsToggleState(String modelName) {
        if (toolsToggle == null || modelName == null || modelName.isBlank()) return;
        boolean supportsTools = CapabilityManager.getInstance().supportsTools(modelName);
        toolsToggle.setDisable(!supportsTools);
        if (!supportsTools) {
            toolsToggle.setSelected(false);
            toolsToggle.setTooltip(new Tooltip(App.getBundle().getString("chat.tools.unsupported")));
        } else {
            toolsToggle.setTooltip(new Tooltip(App.getBundle().getString("chat.tools.desc")));
        }
    }

    private String buildToolUseDirective(String baseSystemPrompt, List<ToolDefinition> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append(baseSystemPrompt != null ? baseSystemPrompt.trim() : "");
        sb.append("\n\nYou have access to the following functions. Use them when appropriate:\n");
        for (ToolDefinition td : tools) {
            sb.append("- ").append(td.getName()).append(": ").append(td.getDescription()).append("\n");
        }
        sb.append("\nIf you need information that a function can provide, call it instead of guessing. ");
        sb.append("When you call a function, do NOT also produce a text response in the same turn. ");
        sb.append("After receiving function results, continue your response naturally.");
        return sb.toString().trim();
    }

    private List<ChatMessage> buildConversationHistory(ChatSession session) {
        List<ChatMessage> history = new ArrayList<>();
        if (session == null) return history;
        for (ChatMessage msg : session.getMessages()) {
            if ("assistant".equals(msg.getRole()) && msg.hasToolCalls()) {
                history.add(msg);
            } else if ("tool".equals(msg.getRole())) {
                history.add(msg);
            } else if ("user".equals(msg.getRole()) || "assistant".equals(msg.getRole())) {
                history.add(msg);
            }
        }
        return history;
    }
}
