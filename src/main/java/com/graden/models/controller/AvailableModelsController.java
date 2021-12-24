package com.graden.models.controller;

import com.graden.models.manager.ModelCapabilityResolver;
import com.graden.models.manager.ModelCapabilityResolver.SizeRange;
import com.graden.models.manager.ModelManager;
import com.graden.models.model.OllamaModel;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TextField;
import javafx.scene.control.ListCell;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import com.graden.models.App;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

public class AvailableModelsController {

    @FXML
    private ListView<OllamaModel> modelListView;
    @FXML
    private TextField searchField;
    @FXML
    private StackPane detailViewContainer;
    @FXML
    private Label resultsLabel;

    // Compatibility filters
    @FXML
    private ToggleButton filterRecommended;

    // Capability filters
    @FXML
    private ToggleButton filterVision;
    @FXML
    private ToggleButton filterTools;
    @FXML
    private ToggleButton filterCode;

    // Size filters
    @FXML
    private ToggleButton filterSizeLt2b;
    @FXML
    private ToggleButton filterSize2to7b;
    @FXML
    private ToggleButton filterSize7to13b;
    @FXML
    private ToggleButton filterSize13to30b;
    @FXML
    private ToggleButton filterSize30bPlus;

    // Active filters indicator
    @FXML
    private HBox activeFiltersIndicator;
    @FXML
    private Label activeFiltersCountLabel;
    @FXML
    private Hyperlink clearFiltersLink;

    private FilteredList<OllamaModel> filteredModels;
    private ModelManager modelManager;
    private PauseTransition selectionDebounce;

    public void setModelManager(ModelManager modelManager) {
        this.modelManager = modelManager;

        filteredModels = new FilteredList<>(modelManager.getAvailableModels(), p -> true);
        modelListView.setItems(filteredModels);

        setupFilters();
        setupSimpleListCell();
        setupSelectionLogic();
        setupEmptyState();
        setupActiveFiltersBinding();
        updateResultsLabel();

        filteredModels.predicateProperty().addListener((obs, oldP, newP) -> updateResultsLabel());
    }

    private void setupFilters() {
        // Search filter
        searchField.textProperty().addListener((obs, old, newVal) -> applyFilters());

        // Toggle filters — capabilities
        filterRecommended.selectedProperty().addListener((obs, old, sel) -> applyFilters());
        filterVision.selectedProperty().addListener((obs, old, sel) -> applyFilters());
        filterTools.selectedProperty().addListener((obs, old, sel) -> applyFilters());
        filterCode.selectedProperty().addListener((obs, old, sel) -> applyFilters());

        // Toggle filters — size
        filterSizeLt2b.selectedProperty().addListener((obs, old, sel) -> applyFilters());
        filterSize2to7b.selectedProperty().addListener((obs, old, sel) -> applyFilters());
        filterSize7to13b.selectedProperty().addListener((obs, old, sel) -> applyFilters());
        filterSize13to30b.selectedProperty().addListener((obs, old, sel) -> applyFilters());
        filterSize30bPlus.selectedProperty().addListener((obs, old, sel) -> applyFilters());
    }

    private void applyFilters() {
        String searchText = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();

        boolean hasRecommendedFilter = filterRecommended.isSelected();
        boolean hasAnyCapability = anyCapabilitySelected();
        boolean hasAnySize = anySizeSelected();

        filteredModels.setPredicate(model -> {
            // 1. Search by name - always applies (AND)
            if (!searchText.isEmpty() && !model.getName().toLowerCase().contains(searchText)) {
                return false;
            }

            // 2. Recommended filter (AND)
            if (hasRecommendedFilter) {
                modelManager.classifyModel(model);
                if (model.getCompatibilityStatus() != OllamaModel.CompatibilityStatus.RECOMMENDED) {
                    return false;
                }
            }

            // 3. Capability filter (AND with OR internal)
            if (hasAnyCapability && !modelMatchesCapability(model)) {
                return false;
            }

            // 4. Size filter (AND with OR internal; UNKNOWN excluded when size filters active)
            if (hasAnySize) {
                SizeRange r = ModelCapabilityResolver.resolveSize(model);
                if (r == SizeRange.UNKNOWN) return false;
                if (!modelMatchesSize(r)) return false;
            }

            return true;
        });
    }

    /**
     * Returns true if the model matches at least one selected capability toggle
     * (OR within the capability group).
     */
    private boolean modelMatchesCapability(OllamaModel model) {
        if (filterVision.isSelected() && ModelCapabilityResolver.hasVision(model)) return true;
        if (filterTools.isSelected() && ModelCapabilityResolver.hasTools(model)) return true;
        if (filterCode.isSelected() && ModelCapabilityResolver.hasCode(model)) return true;
        return false;
    }

    /**
     * Returns true if the model's resolved SizeRange matches at least one selected
     * size toggle (OR within the size group).
     */
    private boolean modelMatchesSize(SizeRange r) {
        return (filterSizeLt2b.isSelected() && r == SizeRange.LT_2B)
                || (filterSize2to7b.isSelected() && r == SizeRange.B2_7)
                || (filterSize7to13b.isSelected() && r == SizeRange.B7_13)
                || (filterSize13to30b.isSelected() && r == SizeRange.B13_30)
                || (filterSize30bPlus.isSelected() && r == SizeRange.B30_PLUS);
    }

    private boolean anyCapabilitySelected() {
        return filterVision.isSelected() || filterTools.isSelected() || filterCode.isSelected();
    }

    private boolean anySizeSelected() {
        return filterSizeLt2b.isSelected() || filterSize2to7b.isSelected()
                || filterSize7to13b.isSelected() || filterSize13to30b.isSelected()
                || filterSize30bPlus.isSelected();
    }

    /**
     * Deselects all 9 filter toggles and clears the search field.
     */
    private void clearAllFilters() {
        filterRecommended.setSelected(false);
        filterVision.setSelected(false);
        filterTools.setSelected(false);
        filterCode.setSelected(false);
        filterSizeLt2b.setSelected(false);
        filterSize2to7b.setSelected(false);
        filterSize7to13b.setSelected(false);
        filterSize13to30b.setSelected(false);
        filterSize30bPlus.setSelected(false);
        searchField.clear();
    }

    /**
     * Creates bindings for the active-filters indicator (count chip + clear link)
     * that appears at the right side of the size filter row.
     */
    private void setupActiveFiltersBinding() {
        // BooleanBinding: true when any filter is active
        BooleanBinding hasActiveFilters = Bindings.createBooleanBinding(
                () -> anyCapabilitySelected() || anySizeSelected()
                        || filterRecommended.isSelected()
                        || (searchField.getText() != null && !searchField.getText().isEmpty()),
                filterVision.selectedProperty(),
                filterTools.selectedProperty(),
                filterCode.selectedProperty(),
                filterRecommended.selectedProperty(),
                filterSizeLt2b.selectedProperty(),
                filterSize2to7b.selectedProperty(),
                filterSize7to13b.selectedProperty(),
                filterSize13to30b.selectedProperty(),
                filterSize30bPlus.selectedProperty(),
                searchField.textProperty());

        activeFiltersIndicator.visibleProperty().bind(hasActiveFilters);
        activeFiltersIndicator.managedProperty().bind(hasActiveFilters);

        // StringBinding: computes "N filtros activos" / "N filters active"
        StringBinding countBinding = Bindings.createStringBinding(
                () -> {
                    int count = 0;
                    if (filterRecommended.isSelected()) count++;
                    if (filterVision.isSelected()) count++;
                    if (filterTools.isSelected()) count++;
                    if (filterCode.isSelected()) count++;
                    if (filterSizeLt2b.isSelected()) count++;
                    if (filterSize2to7b.isSelected()) count++;
                    if (filterSize7to13b.isSelected()) count++;
                    if (filterSize13to30b.isSelected()) count++;
                    if (filterSize30bPlus.isSelected()) count++;
                    if (searchField.getText() != null && !searchField.getText().isEmpty()) count++;

                    if (count <= 0) return "";
                    String key = (count == 1)
                            ? "available.filter.active.count.one"
                            : "available.filter.active.count";
                    return MessageFormat.format(App.getBundle().getString(key), count);
                },
                filterVision.selectedProperty(),
                filterTools.selectedProperty(),
                filterCode.selectedProperty(),
                filterRecommended.selectedProperty(),
                filterSizeLt2b.selectedProperty(),
                filterSize2to7b.selectedProperty(),
                filterSize7to13b.selectedProperty(),
                filterSize13to30b.selectedProperty(),
                filterSize30bPlus.selectedProperty(),
                searchField.textProperty());

        activeFiltersCountLabel.textProperty().bind(countBinding);

        // Clear link action
        clearFiltersLink.setOnAction(e -> clearAllFilters());
    }

    private void updateResultsLabel() {
        int count = filteredModels.size();
        resultsLabel.setText(count + " " + (count == 1 ? "modelo" : "modelos"));
    }

    private void setupEmptyState() {
        Label placeholder = new Label();
        placeholder.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    String search = searchField.getText();
                    boolean hasAnyFilter = filterRecommended.isSelected()
                            || anyCapabilitySelected()
                            || anySizeSelected();

                    if ((search != null && !search.isEmpty()) || hasAnyFilter) {
                        return App.getBundle().getString("available.empty.no_match");
                    }
                    return App.getBundle().getString("available.empty.connecting");
                },
                searchField.textProperty(),
                filterRecommended.selectedProperty(),
                filterVision.selectedProperty(),
                filterTools.selectedProperty(),
                filterCode.selectedProperty(),
                filterSizeLt2b.selectedProperty(),
                filterSize2to7b.selectedProperty(),
                filterSize7to13b.selectedProperty(),
                filterSize13to30b.selectedProperty(),
                filterSize30bPlus.selectedProperty()));
        placeholder.setStyle("-fx-text-fill: -color-fg-muted;");
        modelListView.setPlaceholder(placeholder);
    }

    // Simple, clean list cell - just the model name
    private void setupSimpleListCell() {
        modelListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(OllamaModel item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getName());
                    setGraphic(null);
                }
            }
        });
    }

    private void setupSelectionLogic() {
        selectionDebounce = new PauseTransition(Duration.millis(100));
        selectionDebounce.setOnFinished(event -> {
            OllamaModel selected = modelListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                displayModelDetails(selected);
            }
        });

        modelListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                selectionDebounce.playFromStart();
            } else {
                detailViewContainer.getChildren().clear();
            }
        });
    }

    private void displayModelDetails(OllamaModel selectedModel) {
        try {
            if (modelManager == null) {
                showErrorInView("Error", "ModelManager is null");
                return;
            }
            List<OllamaModel> details = modelManager.getModelDetails(selectedModel.getName());
            showDetailsInView(details, selectedModel);
        } catch (Exception e) {
            showErrorInView("Error", "Failed to load model details.");
        }
    }

    private void showDetailsInView(List<OllamaModel> modelTags, OllamaModel libraryModel) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/model_detail_view.fxml"));
            loader.setResources(App.getBundle());
            Parent detailView = loader.load();
            ModelDetailController controller = loader.getController();
            controller.setModelManager(this.modelManager);
            controller.populateDetails(modelTags, libraryModel);
            detailViewContainer.getChildren().setAll(detailView);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showErrorInView(String title, String message) {
        VBox errorBox = new VBox(10);
        errorBox.setAlignment(Pos.CENTER);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px;");
        titleLabel.setTextFill(Color.RED);
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        errorBox.getChildren().addAll(titleLabel, messageLabel);
        detailViewContainer.getChildren().setAll(errorBox);
    }

    @FXML
    private void sortByNameAsc() {
        modelManager.getAvailableModels().sort((m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));
    }

    @FXML
    private void sortByNameDesc() {
        modelManager.getAvailableModels().sort((m1, m2) -> m2.getName().compareToIgnoreCase(m1.getName()));
    }
}
