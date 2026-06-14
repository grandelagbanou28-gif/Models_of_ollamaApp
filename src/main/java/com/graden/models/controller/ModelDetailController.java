package com.graden.models.controller;

import com.graden.models.App;
import com.graden.models.manager.ModelManager;
import com.graden.models.manager.OllamaManager;
import com.graden.models.model.OllamaModel;
import atlantafx.base.theme.Styles;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import com.graden.models.manager.HardwareManager;

public class ModelDetailController {

    @FXML private Label modelNameLabel;
    @FXML private Label modelDescriptionLabel;
    @FXML private FlowPane badgesContainer;
    @FXML private Label pullCountLabel;
    @FXML private Label lastUpdatedLabel;
    @FXML private Label parameterSizeLabel;
    @FXML private HBox parameterSizeBox;
    @FXML private Label commandLabel;
    @FXML private Button copyButton;
    @FXML private Label variantsHeaderLabel;

    @FXML private VBox capabilityLegendSection;
    @FXML private javafx.scene.layout.GridPane capabilityLegendGrid;

    @FXML private TableView<OllamaModel> variantsTable;
    @FXML private TableColumn<OllamaModel, String> colName;
    @FXML private TableColumn<OllamaModel, String> colSize;
    @FXML private TableColumn<OllamaModel, String> colContext;
    @FXML private TableColumn<OllamaModel, String> colInput;
    @FXML private TableColumn<OllamaModel, OllamaModel> colAction;

    private ModelManager modelManager;

    public void setModelManager(ModelManager modelManager) {
        this.modelManager = modelManager;
    }

    @FXML
    public void initialize() {
        colName.setCellValueFactory(cd -> cd.getValue().tagProperty());
        colSize.setCellValueFactory(cd -> cd.getValue().sizeProperty());
        colContext.setCellValueFactory(cd -> safeText(cd.getValue().getContextLength()));
        colInput.setCellValueFactory(cd -> safeText(cd.getValue().getInputType()));
        colAction.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));

        colName.getStyleClass().add("col-name");
        colSize.getStyleClass().add("col-numeric");
        colContext.getStyleClass().add("col-numeric");
        colInput.getStyleClass().add("col-numeric");
        colAction.getStyleClass().add("col-action");

        colName.setCellFactory(c -> mutedFallbackCell());
        colSize.setCellFactory(c -> mutedFallbackCell());
        colContext.setCellFactory(c -> mutedFallbackCell());
        colInput.setCellFactory(c -> mutedFallbackCell());
        colAction.setCellFactory(c -> new ActionCell());

        variantsTable.setPlaceholder(new Label(App.getBundle().getString("detail.variants.empty")));
    }

    private static StringProperty safeText(String raw) {
        return new SimpleStringProperty((raw == null || raw.isBlank()) ? "—" : raw);
    }

    private static TableCell<OllamaModel, String> mutedFallbackCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().remove("cell-muted");
                } else {
                    setText(item);
                    if ("—".equals(item) && !getStyleClass().contains("cell-muted")) {
                        getStyleClass().add("cell-muted");
                    } else {
                        getStyleClass().remove("cell-muted");
                    }
                }
            }
        };
    }

    private class ActionCell extends TableCell<OllamaModel, OllamaModel> {
        private final Button btn = new Button();

        ActionCell() {
            btn.getStyleClass().addAll("variant-action-btn");
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(OllamaModel model, boolean empty) {
            super.updateItem(model, empty);
            if (empty || model == null) {
                setGraphic(null);
                return;
            }

            boolean isInstalled = modelManager != null
                    && modelManager.isModelInstalled(model.getName(), model.getTag());

            btn.getStyleClass().removeAll(Styles.SUCCESS, Styles.WARNING, Styles.DANGER, "installed");

            if (isInstalled) {
                btn.setText(App.getBundle().getString("detail.action.installed"));
                btn.getStyleClass().add("installed");
                btn.setDisable(false);
                btn.setOnAction(ev -> {
                    if (modelManager != null) {
                        modelManager.deleteModel(model.getName(), model.getTag());
                    }
                });
            } else {
                btn.setText(App.getBundle().getString("detail.action.get"));
                // Colour the install button by hardware compatibility:
                // green = recommended, orange = standard, red = not recommended.
                btn.getStyleClass().add(compatibilityStyleClass(model.getCompatibilityStatus()));
                btn.setDisable(false);
                btn.setOnAction(ev -> handleDownloadRequest(model));
            }

            setGraphic(btn);
        }
    }

    /**
     * Maps a model's hardware-compatibility status to the AtlantaFX button
     * accent: green (recommended), orange (standard), red (not recommended).
     */
    private static String compatibilityStyleClass(OllamaModel.CompatibilityStatus status) {
        if (status == OllamaModel.CompatibilityStatus.INCOMPATIBLE) return Styles.DANGER;
        if (status == OllamaModel.CompatibilityStatus.CAUTION) return Styles.WARNING;
        return Styles.SUCCESS; // RECOMMENDED or unknown → green
    }

    private void showDownloadPopup(OllamaModel model) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/download_popup.fxml"));
            loader.setResources(App.getBundle());

            Parent root = loader.load();
            DownloadPopupController controller = loader.getController();
            controller.setModelName(model.getName() + ":" + model.getTag());

            String userAgentStylesheet = Application.getUserAgentStylesheet();
            if (userAgentStylesheet != null && userAgentStylesheet.toLowerCase().contains("light")) {
                root.getStyleClass().add("light");
            } else {
                root.getStyleClass().add("dark");
            }

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.setTitle(App.getBundle().getString("download.title.default"));

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);
            stage.setResizable(false);

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    updateMessage(App.getBundle().getString("download.status.process"));
                    updateProgress(0, 100);

                    OllamaManager.getInstance().pullModel(model.getName(), model.getTag(),
                            (progress, status) -> {
                                updateMessage(status);
                                if (progress >= 0) {
                                    updateProgress(progress, 100);
                                } else {
                                    updateProgress(-1, 100);
                                }
                            });

                    return null;
                }
            };

            task.setOnSucceeded(e -> {
                if (modelManager != null) {
                    String date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                            .format(LocalDateTime.now());
                    OllamaModel newModel = new OllamaModel(
                            model.getName(), App.getBundle().getString("model.installed"), "N/A", model.getTag(),
                            model.sizeProperty().get(), date,
                            model.getContextLength(), model.getInputType());
                    modelManager.addLocalModel(newModel);
                }
            });

            task.setOnFailed(e -> {
                System.err.println("Download failed: " + task.getException().getMessage());
            });

            controller.setDownloadTask(task);
            App.getExecutorService().submit(task);
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void populateDetails(List<OllamaModel> modelTags) {
        populateDetails(modelTags, null);
    }

    public void populateDetails(List<OllamaModel> modelTags, OllamaModel libraryModel) {
        if (modelTags == null || modelTags.isEmpty()) {
            modelNameLabel.setText(App.getBundle().getString("model.error"));
            modelDescriptionLabel.setText(App.getBundle().getString("model.error.details"));
            variantsTable.setItems(FXCollections.observableArrayList());
            variantsHeaderLabel.setText(MessageFormat.format(
                    App.getBundle().getString("detail.variants.count"), 0));
            badgesContainer.getChildren().clear();
            return;
        }

        OllamaModel firstTag = modelTags.get(0);

        modelNameLabel.setText(firstTag.getName());
        modelDescriptionLabel.setText(firstTag.descriptionProperty().get());

        commandLabel.setText("ollama run " + firstTag.getName());
        copyButton.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(commandLabel.getText());
            clipboard.setContent(content);
            copyButton.getStyleClass().add("success");
            PauseTransition pause = new PauseTransition(Duration.seconds(1));
            pause.setOnFinished(ev -> copyButton.getStyleClass().remove("success"));
            pause.play();
        });

        String downloads = firstTag.pullCountProperty().get();
        pullCountLabel.setText((downloads == null || downloads.isBlank()) ? "—" : downloads);

        String updated = firstTag.lastUpdatedProperty().get();
        lastUpdatedLabel.setText((updated == null || updated.isBlank()) ? "—" : updated);

        String tag = firstTag.getTag();
        String paramSize = null;
        if (tag != null && tag.contains(":")) {
            String[] parts = tag.split(":");
            if (parts.length > 1 && !parts[1].isBlank()) {
                paramSize = parts[1].toUpperCase();
            }
        }
        if (paramSize == null) {
            parameterSizeBox.setVisible(false);
            parameterSizeBox.setManaged(false);
        } else {
            parameterSizeLabel.setText(paramSize);
            parameterSizeBox.setVisible(true);
            parameterSizeBox.setManaged(true);
        }

        // Prefer badges from the library list (richer source); fallback to detail tag badges.
        List<String> badges = (libraryModel != null && libraryModel.getBadges() != null
                && !libraryModel.getBadges().isEmpty())
                ? libraryModel.getBadges()
                : firstTag.getBadges();
        renderCapabilityPills(badges);

        // Pre-classify all variants once (avoid doing it in cell rendering)
        if (modelManager != null) {
            for (OllamaModel m : modelTags) {
                modelManager.classifyModel(m);
            }
        }

        variantsTable.setItems(FXCollections.observableArrayList(modelTags));
        variantsHeaderLabel.setText(MessageFormat.format(
                App.getBundle().getString("detail.variants.count"), modelTags.size()));

        // Size the table to its content (avoids infinite-height layout loop inside outer ScrollPane).
        double rowHeight = 44.0;
        double headerHeight = 32.0;
        double tableHeight = headerHeight + Math.max(1, modelTags.size()) * rowHeight + 4.0;
        variantsTable.setPrefHeight(tableHeight);
        variantsTable.setMinHeight(tableHeight);
        variantsTable.setMaxHeight(tableHeight);
    }

    private enum PillKind {
        VISION("pill-vision",      "pill.tooltip.vision",      "pill.legend.vision"),
        TOOLS("pill-tools",        "pill.tooltip.tools",       "pill.legend.tools"),
        THINKING("pill-thinking",  "pill.tooltip.thinking",    "pill.legend.thinking"),
        EMBEDDING("pill-embedding","pill.tooltip.embedding",   "pill.legend.embedding"),
        CODE("pill-code",          "pill.tooltip.code",        "pill.legend.code"),
        MULTIMODAL("pill-neutral", "pill.tooltip.multimodal",  "pill.legend.multimodal"),
        MATH("pill-neutral",       "pill.tooltip.math",        "pill.legend.math"),
        REASONING("pill-thinking", "pill.tooltip.reasoning",   "pill.legend.reasoning"),
        AUDIO("pill-neutral",      "pill.tooltip.audio",       "pill.legend.audio"),
        SIZE("pill-size",          "pill.tooltip.size",        "pill.legend.size"),
        GENERIC("pill-neutral",    "pill.tooltip.generic",     "pill.legend.generic");

        final String styleClass;
        final String tooltipKey;
        final String legendKey;
        PillKind(String s, String t, String l) { styleClass = s; tooltipKey = t; legendKey = l; }
    }

    private static PillKind classifyPill(String badge) {
        String low = badge.toLowerCase().trim();
        if (low.contains("vision"))     return PillKind.VISION;
        if (low.contains("tool"))       return PillKind.TOOLS;
        if (low.contains("think"))      return PillKind.THINKING;
        if (low.contains("embed"))      return PillKind.EMBEDDING;
        if (low.contains("code"))       return PillKind.CODE;
        if (low.contains("multimodal")) return PillKind.MULTIMODAL;
        if (low.contains("math"))       return PillKind.MATH;
        if (low.contains("reason"))     return PillKind.REASONING;
        if (low.contains("audio"))      return PillKind.AUDIO;
        if (low.matches("^\\d+(\\.\\d+)?[mb]$") || low.matches(".*\\d+b.*")) return PillKind.SIZE;
        return PillKind.GENERIC;
    }

    private Label buildPill(String badge, PillKind kind) {
        Label pill = new Label(badge);
        pill.getStyleClass().addAll("capability-pill", kind.styleClass);
        try {
            Tooltip tip = new Tooltip(App.getBundle().getString(kind.tooltipKey));
            tip.setShowDelay(Duration.millis(250));
            tip.setHideDelay(Duration.millis(150));
            tip.setWrapText(true);
            tip.setMaxWidth(320);
            Tooltip.install(pill, tip);
        } catch (Exception ignored) {
        }
        return pill;
    }

    private void renderCapabilityPills(List<String> badges) {
        badgesContainer.getChildren().clear();
        capabilityLegendGrid.getChildren().clear();
        capabilityLegendGrid.getRowConstraints().clear();
        capabilityLegendGrid.getColumnConstraints().clear();

        if (badges == null || badges.isEmpty()) {
            capabilityLegendSection.setVisible(false);
            capabilityLegendSection.setManaged(false);
            return;
        }

        // Render pills + collect unique kinds for legend (one row per kind, not per badge text).
        java.util.LinkedHashMap<PillKind, String> kindToLabel = new java.util.LinkedHashMap<>();
        for (String badge : badges) {
            PillKind kind = classifyPill(badge);
            badgesContainer.getChildren().add(buildPill(badge, kind));
            kindToLabel.putIfAbsent(kind, badge);
        }

        // Build legend grid: column 0 = pill, column 1 = description.
        javafx.scene.layout.ColumnConstraints c0 = new javafx.scene.layout.ColumnConstraints();
        c0.setMinWidth(110);
        c0.setPrefWidth(130);
        c0.setHalignment(javafx.geometry.HPos.LEFT);
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints();
        c1.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        c1.setFillWidth(true);
        capabilityLegendGrid.getColumnConstraints().addAll(c0, c1);

        int row = 0;
        for (java.util.Map.Entry<PillKind, String> e : kindToLabel.entrySet()) {
            PillKind kind = e.getKey();
            String label = e.getValue();

            Label pill = new Label(label);
            pill.getStyleClass().addAll("capability-pill", kind.styleClass);

            String desc;
            try { desc = App.getBundle().getString(kind.legendKey); }
            catch (Exception ex) { desc = ""; }

            Label descLabel = new Label(desc);
            descLabel.setWrapText(true);
            descLabel.getStyleClass().add("capability-legend-desc");
            descLabel.setMaxWidth(Double.MAX_VALUE);

            javafx.scene.layout.HBox pillCell = new javafx.scene.layout.HBox(pill);
            pillCell.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            pillCell.getStyleClass().add("capability-legend-pill-cell");

            capabilityLegendGrid.add(pillCell, 0, row);
            capabilityLegendGrid.add(descLabel, 1, row);
            row++;
        }

        capabilityLegendSection.setVisible(true);
        capabilityLegendSection.setManaged(true);
    }

    private void handleDownloadRequest(OllamaModel model) {
        OllamaModel.CompatibilityStatus status = model.getCompatibilityStatus();
        if (status == OllamaModel.CompatibilityStatus.RECOMMENDED) {
            showDownloadPopup(model);
            return;
        }

        if (status == OllamaModel.CompatibilityStatus.CAUTION) {
            Alert alert = new Alert(Alert.AlertType.WARNING, App.getBundle().getString("model.install.warn.content"),
                    ButtonType.OK, ButtonType.CANCEL);
            alert.setTitle(App.getBundle().getString("model.install.warn.title"));
            alert.setHeaderText(
                    MessageFormat.format(App.getBundle().getString("model.install.warn.header"), model.getTag()));
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    showDownloadPopup(model);
                }
            });
            return;
        }

        if (status == OllamaModel.CompatibilityStatus.INCOMPATIBLE) {
            Alert alert = new Alert(Alert.AlertType.ERROR, null, ButtonType.OK, ButtonType.CANCEL);
            alert.setTitle(App.getBundle().getString("model.install.error.title"));
            alert.setHeaderText(
                    MessageFormat.format(App.getBundle().getString("model.install.error.header"), model.getTag()));

            long availableRam = (long) HardwareManager.getStats().getTotalRamGB();
            alert.setContentText(MessageFormat.format(App.getBundle().getString("model.install.error.content"),
                    model.getTag(), availableRam));

            CheckBox confirmCheck = new CheckBox(App.getBundle().getString("model.install.error.confirm"));
            confirmCheck.setWrapText(true);
            alert.getDialogPane().setExpandableContent(new VBox(10, confirmCheck));
            alert.getDialogPane().setExpanded(true);

            Button okButton = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
            okButton.setDisable(true);

            confirmCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
                okButton.setDisable(!newVal);
            });

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK && confirmCheck.isSelected()) {
                    showDownloadPopup(model);
                }
            });
        }
    }
}
