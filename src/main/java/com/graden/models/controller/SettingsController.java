package com.graden.models.controller;

import com.graden.models.App;
import com.graden.models.manager.AuthManager;
import com.graden.models.manager.ConfigManager;
import com.graden.models.manager.HardwareManager;
import com.graden.models.manager.LibraryCacheManager;
import com.graden.models.manager.ModelDetailsCacheManager;
import com.graden.models.manager.ModelLibraryManager;
import com.graden.models.manager.ScrapeResult;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

public class SettingsController {

    @FXML private TextField hostTextField;
    @FXML private TextField apiTimeoutField;
    @FXML private Button themeButton;
    @FXML private ComboBox<String> languageComboBox;
    @FXML private Label statusLabel;
    @FXML private Label ramLabel;
    @FXML private Label vramLabel;
    @FXML private Label cpuLabel;
    @FXML private Label osLabel;

    // Library cache labels
    @FXML private Label cacheFileLabel;
    @FXML private Label cacheLocationLabel;
    @FXML private Label cacheSizeLabel;
    @FXML private Label cacheLastUpdateLabel;
    @FXML private Label cacheDaysLabel;
    @FXML private Button refreshLibraryButton;

    // Refresh state machine + diagnostics
    @FXML private ProgressIndicator refreshSpinner;
    @FXML private Label refreshStatusLabel;
    @FXML private HBox refreshSummary;
    @FXML private Label refreshSummaryLabel;
    @FXML private Hyperlink diagnosticsToggle;
    @FXML private javafx.scene.layout.VBox diagnosticsPanel;
    @FXML private Label diagTimestampLabel;
    @FXML private Label diagPagesLabel;
    @FXML private Label diagModelsLabel;
    @FXML private Label diagFailedLabel;
    @FXML private TitledPane failedModelsPane;
    @FXML private ListView<String> failedModelsList;

    // RAG tuning
    @FXML private Slider ragMinScoreSlider;
    @FXML private Label ragMinScoreValueLabel;
    @FXML private Spinner<Integer> ragTopKSpinner;

    // Account
    @FXML private Label userEmailLabel;

    private final ConfigManager configManager = ConfigManager.getInstance();
    private final LibraryCacheManager cacheManager = LibraryCacheManager.getInstance();
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        bundle = App.getBundle();

        hostTextField.setText(configManager.getOllamaHost());
        apiTimeoutField.setText(String.valueOf(configManager.getApiTimeout()));

        ramLabel.setText(HardwareManager.getRamDetails());
        if (vramLabel != null) vramLabel.setText(HardwareManager.getVramDetails());
        cpuLabel.setText(HardwareManager.getCpuDetails());
        osLabel.setText(HardwareManager.getOsDetails());

        // Account section
        String email = AuthManager.getInstance().getCurrentUserEmail();
        userEmailLabel.setText(email != null ? email : "Not signed in");

        populateCacheInfo();
        populateDiagnostics();

        initRagControls();

        languageComboBox.getItems().addAll("English", "Español");
        String currentLang = configManager.getLanguage();
        languageComboBox.getSelectionModel().select("es".equals(currentLang) ? "Español" : "English");
        languageComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                configManager.setLanguage(newVal.equals("Español") ? "es" : "en");
                App.reloadUI();
            }
        });
    }

    private void initRagControls() {
        if (ragMinScoreSlider == null || ragTopKSpinner == null) return;

        double currentScore = configManager.getRagMinScore();
        ragMinScoreSlider.setValue(currentScore);
        if (ragMinScoreValueLabel != null) {
            ragMinScoreValueLabel.setText(String.format("%.2f", currentScore));
        }
        ragMinScoreSlider.valueProperty().addListener((obs, oldV, newV) -> {
            double v = newV.doubleValue();
            if (ragMinScoreValueLabel != null) {
                ragMinScoreValueLabel.setText(String.format("%.2f", v));
            }
            configManager.setRagMinScore(v);
        });

        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, configManager.getRagTopK());
        ragTopKSpinner.setValueFactory(factory);
        ragTopKSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) configManager.setRagTopK(newV);
        });
    }

    private void populateCacheInfo() {
        if (cacheManager.cacheExists()) {
            cacheFileLabel.setText(cacheManager.getCacheFileName());
            cacheLocationLabel.setText(cacheManager.getCacheFilePath());
            cacheSizeLabel.setText(cacheManager.getCacheFileSizeKB() + " KB");

            long lastUpdated = cacheManager.getLastUpdatedTimestamp();
            if (lastUpdated > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                cacheLastUpdateLabel.setText(sdf.format(new Date(lastUpdated)));
                int days = cacheManager.getDaysSinceUpdate();
                cacheDaysLabel.setText(days + " " + (days == 1 ? "día" : "días"));
            } else {
                cacheLastUpdateLabel.setText(bundle.getString("settings.library.never"));
                cacheDaysLabel.setText("-");
            }
        } else {
            String noCache = bundle.getString("settings.library.nocache");
            cacheFileLabel.setText(noCache);
            cacheLocationLabel.setText("-");
            cacheSizeLabel.setText("-");
            cacheLastUpdateLabel.setText(bundle.getString("settings.library.never"));
            cacheDaysLabel.setText("-");
        }
    }

    private void populateDiagnostics() {
        ScrapeResult last = ModelLibraryManager.getInstance().getLastResult();
        if (last == null) {
            if (diagTimestampLabel != null) {
                diagTimestampLabel.setText(bundle.getString("settings.library.diagnostics.empty"));
            }
            if (diagPagesLabel != null) diagPagesLabel.setText("");
            if (diagModelsLabel != null) diagModelsLabel.setText("");
            if (diagFailedLabel != null) diagFailedLabel.setText("");
            if (failedModelsPane != null) {
                failedModelsPane.setManaged(false);
                failedModelsPane.setVisible(false);
            }
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        diagTimestampLabel.setText(MessageFormat.format(
                bundle.getString("settings.library.diagnostics.timestamp"),
                sdf.format(new Date(last.getTimestamp()))));
        diagPagesLabel.setText(MessageFormat.format(
                bundle.getString("settings.library.diagnostics.pagesScanned"), last.getPagesScanned()));
        diagModelsLabel.setText(MessageFormat.format(
                bundle.getString("settings.library.diagnostics.modelsDiscovered"), last.getModelsDiscovered()));
        diagFailedLabel.setText(MessageFormat.format(
                bundle.getString("settings.library.diagnostics.detailsFailed"), last.getDetailsFailed()));

        List<String> failed = last.getFailedModels();
        if (failed.isEmpty()) {
            failedModelsPane.setManaged(false);
            failedModelsPane.setVisible(false);
        } else {
            failedModelsPane.setManaged(true);
            failedModelsPane.setVisible(true);
            failedModelsPane.setText(MessageFormat.format(
                    bundle.getString("settings.library.diagnostics.failedModels"), failed.size()));
            failedModelsList.getItems().setAll(failed);
        }
    }

    @FXML
    private void saveSettings() {
        String newHost = hostTextField.getText();
        String newTimeout = apiTimeoutField.getText();

        if (newHost != null && !newHost.trim().isEmpty()) {
            configManager.setOllamaHost(newHost.trim());
            try {
                int parsedTimeout = Integer.parseInt(newTimeout.trim());
                if (parsedTimeout > 0) configManager.setApiTimeout(parsedTimeout);
            } catch (NumberFormatException ignored) { }

            com.graden.models.manager.OllamaManager.getInstance().updateClient();
            statusLabel.setText("✓ " + bundle.getString("settings.status.saved"));
            statusLabel.setStyle("-fx-text-fill: -color-success-fg;");

            FadeTransition fade = new FadeTransition(Duration.millis(500), statusLabel);
            fade.setDelay(Duration.seconds(3));
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> {
                statusLabel.setText("");
                statusLabel.setOpacity(1.0);
            });
            fade.play();
        } else {
            statusLabel.setText("⚠ " + bundle.getString("settings.status.invalid"));
            statusLabel.setStyle("-fx-text-fill: -color-danger-fg;");
        }
    }

    @FXML
    private void refreshLibrary() {
        // === State: invalidating ===
        setRefreshState("invalidating", null);

        // Invalidate both caches + in-memory state. These are local File.delete()
        // calls, safe on the FX thread.
        try {
            LibraryCacheManager.getInstance().invalidate();
            ModelDetailsCacheManager.getInstance().invalidate();
            ModelLibraryManager.getInstance().invalidateCache();
        } catch (Exception e) {
            e.printStackTrace();
            setRefreshState("error", e.getMessage());
            return;
        }

        // Brief visual confirmation, then jump to splash to rescrape. The Splash
        // owns the actual progress + error UX (showScrapeErrorDialog).
        PauseTransition pt = new PauseTransition(Duration.millis(250));
        pt.setOnFinished(ev -> {
            setRefreshState("scraping", null);
            Platform.runLater(App::reloadFromSplash);
        });
        pt.play();
    }

    @FXML
    private void toggleDiagnostics() {
        if (diagnosticsPanel == null) return;
        boolean show = !diagnosticsPanel.isVisible();
        diagnosticsPanel.setVisible(show);
        diagnosticsPanel.setManaged(show);
        if (diagnosticsToggle != null) {
            diagnosticsToggle.setText(show
                    ? bundle.getString("settings.library.diagnostics.hide")
                    : bundle.getString("settings.library.diagnostics.show"));
        }
        if (show) populateDiagnostics();
    }

    /**
     * State machine for the Refresh Library button.
     * @param state one of {@code idle | invalidating | scraping | success | error}
     * @param detail i18n-resolved supporting message (may be null)
     */
    private void setRefreshState(String state, String detail) {
        if (refreshLibraryButton == null) return;
        // Reset variant classes
        refreshLibraryButton.getStyleClass().removeAll("success", "danger");
        if (refreshStatusLabel != null) {
            refreshStatusLabel.getStyleClass().removeAll("success", "danger");
        }
        if (refreshSummary != null) {
            refreshSummary.getStyleClass().removeAll("success", "danger");
        }

        switch (state) {
            case "invalidating" -> {
                refreshLibraryButton.setDisable(true);
                showSpinner(true);
                showStatus(bundle.getString("settings.refresh.invalidating"), null);
                hideSummary();
            }
            case "scraping" -> {
                refreshLibraryButton.setDisable(true);
                showSpinner(true);
                showStatus(bundle.getString("settings.refresh.scraping"), null);
                hideSummary();
            }
            case "success" -> {
                refreshLibraryButton.setDisable(false);
                refreshLibraryButton.getStyleClass().add("success");
                showSpinner(false);
                showStatus(null, null);
                showSummary(true, detail);
                // revert variant after 1.2s
                PauseTransition pt = new PauseTransition(Duration.millis(1200));
                pt.setOnFinished(e -> refreshLibraryButton.getStyleClass().remove("success"));
                pt.play();
            }
            case "error" -> {
                refreshLibraryButton.setDisable(false);
                refreshLibraryButton.getStyleClass().add("danger");
                showSpinner(false);
                showStatus(null, null);
                showSummary(false, detail);
            }
            default -> {
                refreshLibraryButton.setDisable(false);
                showSpinner(false);
                showStatus(null, null);
            }
        }
    }

    private void showSpinner(boolean show) {
        if (refreshSpinner != null) {
            refreshSpinner.setManaged(show);
            refreshSpinner.setVisible(show);
        }
    }

    private void showStatus(String text, String variant) {
        if (refreshStatusLabel == null) return;
        boolean show = text != null;
        refreshStatusLabel.setManaged(show);
        refreshStatusLabel.setVisible(show);
        refreshStatusLabel.setText(text == null ? "" : text);
        if (variant != null) refreshStatusLabel.getStyleClass().add(variant);
    }

    private void hideSummary() {
        if (refreshSummary != null) {
            refreshSummary.setManaged(false);
            refreshSummary.setVisible(false);
        }
    }

    private void showSummary(boolean success, String detail) {
        if (refreshSummary == null) return;
        refreshSummary.setManaged(true);
        refreshSummary.setVisible(true);
        refreshSummary.getStyleClass().add(success ? "success" : "danger");
        if (refreshSummaryLabel != null) {
            String msg = success
                    ? bundle.getString("settings.refresh.success")
                    : bundle.getString("settings.refresh.error");
            if (detail != null && !detail.isBlank()) msg = msg + " — " + detail;
            refreshSummaryLabel.setText(msg);
        }
        FadeTransition fade = new FadeTransition(Duration.millis(150), refreshSummary);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    @FXML
    private void showHardwareLogic() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/hardware_explanation_popup.fxml"));
            loader.setResources(bundle);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(bundle.getString("settings.hardware.popup.title"));
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void replayOnboarding() {
        if (themeButton != null && themeButton.getScene() != null
                && themeButton.getScene().getRoot() instanceof javafx.scene.layout.StackPane sp) {
            com.graden.models.ui.OnboardingOverlay.showOver(sp);
        }
    }

    @FXML
    private void toggleTheme() {
        if (Application.getUserAgentStylesheet()
                .equals(new atlantafx.base.theme.CupertinoDark().getUserAgentStylesheet())) {
            Application.setUserAgentStylesheet(new atlantafx.base.theme.CupertinoLight().getUserAgentStylesheet());
            if (themeButton.getScene() != null) {
                themeButton.getScene().getRoot().getStyleClass().remove("dark");
                themeButton.getScene().getRoot().getStyleClass().add("light");
            }
            ConfigManager.getInstance().setTheme("light");
        } else {
            Application.setUserAgentStylesheet(new atlantafx.base.theme.CupertinoDark().getUserAgentStylesheet());
            if (themeButton.getScene() != null) {
                themeButton.getScene().getRoot().getStyleClass().remove("light");
                themeButton.getScene().getRoot().getStyleClass().add("dark");
            }
            ConfigManager.getInstance().setTheme("dark");
        }
    }

    @FXML
    private void handleLogout() {
        App.logout();
    }
}
