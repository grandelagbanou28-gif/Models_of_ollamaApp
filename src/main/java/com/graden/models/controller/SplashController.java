package com.graden.models.controller;

import atlantafx.base.controls.RingProgressIndicator;
import com.graden.models.App;
import com.graden.models.manager.LibraryCacheManager;
import com.graden.models.manager.ModelLibraryManager;
import com.graden.models.manager.ScrapeResult;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.InputStream;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

public class SplashController {

    @FXML private Label titleLabel;
    @FXML private Label counterLabel;
    @FXML private RingProgressIndicator progressIndicator;
    @FXML private Label actionLabel;
    @FXML private Label modelLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label descriptionSecondaryLabel;
    @FXML private Label modelsLabel;

    private final ModelLibraryManager libraryManager = ModelLibraryManager.getInstance();
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        bundle = App.getBundle();

        String version = loadAppVersion();
        titleLabel.setText("GrandelGradenNexus v" + version);

        modelsLabel.setText(bundle.getString("splash.models"));
        descriptionLabel.setText(bundle.getString("splash.description"));
        descriptionSecondaryLabel.setText(bundle.getString("splash.description.secondary"));

        startInitialization();
    }

    private String loadAppVersion() {
        try (InputStream input = getClass().getResourceAsStream("/app.properties")) {
            if (input != null) {
                Properties props = new Properties();
                props.load(input);
                return props.getProperty("app.version", "0.0.0");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0.0.0";
    }

    private void startInitialization() {
        Thread initThread = new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    counterLabel.setText("...");
                    actionLabel.setText(bundle.getString("splash.verifying"));
                    modelLabel.setText("");
                });
                Thread.sleep(600);

                if (!libraryManager.isOllamaInstalled()) {
                    Platform.runLater(this::showOllamaMissingAlert);
                    return;
                }

                ModelLibraryManager.UpdateStatus status = libraryManager.getUpdateStatus();
                boolean needsUpdate = (status == ModelLibraryManager.UpdateStatus.OUTDATED_HARD);

                if (needsUpdate) {
                    final AtomicReference<ScrapeResult> crawlerResult = new AtomicReference<>();
                    Thread crawlerThread = new Thread(() -> {
                        try {
                            crawlerResult.set(libraryManager.updateLibraryFull());
                        } catch (Exception e) {
                            e.printStackTrace();
                            crawlerResult.set(ScrapeResult.failure(
                                    ScrapeResult.FailureCode.NETWORK,
                                    e.getMessage() == null ? "unknown" : e.getMessage(),
                                    0, 0));
                        }
                    });
                    crawlerThread.setDaemon(true);
                    crawlerThread.start();

                    while (crawlerThread.isAlive()) {
                        String statusRaw = libraryManager.getStatus();

                        Platform.runLater(() -> {
                            progressIndicator.setProgress(-1.0);

                            if (statusRaw.startsWith("Procesando (")) {
                                try {
                                    int closeParen = statusRaw.indexOf(')');
                                    if (closeParen > 0) {
                                        String counts = statusRaw.substring(statusRaw.indexOf('(') + 1, closeParen);
                                        String modelName = statusRaw.substring(closeParen + 2).trim();
                                        counterLabel.setText(counts.replace("/", " / "));
                                        actionLabel.setText(bundle.getString("splash.downloading"));
                                        modelLabel.setText(modelName);
                                    }
                                } catch (Exception e) {
                                    modelLabel.setText("...");
                                }
                            } else if (statusRaw.startsWith("Escaneando")) {
                                counterLabel.setText("...");
                                actionLabel.setText(bundle.getString("splash.discovering"));
                                modelLabel.setText(statusRaw.replace("Escaneando ", "").replace("...", ""));
                            } else {
                                actionLabel.setText("...");
                                modelLabel.setText(statusRaw);
                            }
                        });
                        Thread.sleep(100);
                    }

                    ScrapeResult result = crawlerResult.get();
                    if (result == null || result.isSuccess()) {
                        Platform.runLater(this::launchMainApp);
                    } else if (result.getFailureCode() == ScrapeResult.FailureCode.CANCELLED) {
                        // Silent: keep previous cache as-is, just open the main UI.
                        Platform.runLater(this::launchMainApp);
                    } else {
                        Platform.runLater(() -> showScrapeErrorDialog(result));
                    }
                    return;
                }

                Platform.runLater(() -> {
                    counterLabel.setText("✓");
                    actionLabel.setText(bundle.getString("splash.ready"));
                    modelLabel.setText("");
                    progressIndicator.setProgress(1.0);
                });
                Thread.sleep(800);

                Platform.runLater(this::launchMainApp);

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showErrorAlert(e));
            }
        });
        initThread.setDaemon(true);
        initThread.start();
    }

    private void showOllamaMissingAlert() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error de Ollama");
        alert.setHeaderText("Ollama no está instalado o corriendo.");
        alert.setContentText("Por favor verifica que Ollama esté instalado y ejecutándose.");
        alert.showAndWait();
        Platform.exit();
    }

    private void showErrorAlert(Throwable e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error de Inicialización");
        alert.setHeaderText("Ocurrió un error al iniciar.");
        alert.setContentText(e != null ? e.getMessage() : "Error desconocido");
        alert.showAndWait();
        Platform.exit();
    }

    private void launchMainApp() {
        App.reloadUI();
    }

    /**
     * Fail-loud dialog shown when {@link ModelLibraryManager#updateLibraryFull()}
     * finishes with a non-OK {@link ScrapeResult.FailureCode}. Built
     * programmatically to reuse the {@code .apple-dialog} CSS language.
     */
    private void showScrapeErrorDialog(ScrapeResult result) {
        LibraryCacheManager lcm = LibraryCacheManager.getInstance();
        boolean canOffline = lcm.cacheExists() && lcm.getLastUpdatedTimestamp() > 0;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        Window owner = titleLabel != null && titleLabel.getScene() != null
                ? titleLabel.getScene().getWindow() : null;
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle(bundle.getString("scrape.error.title"));

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("apple-dialog", "scrape-error-dialog");

        // --- Header
        StackPane halo = new StackPane();
        halo.getStyleClass().add("scrape-error-icon-halo");
        FontIcon alertIcon = new FontIcon(Feather.ALERT_TRIANGLE);
        alertIcon.getStyleClass().add("scrape-error-icon");
        alertIcon.setIconSize(28);
        halo.getChildren().add(alertIcon);

        Label title = new Label(bundle.getString("scrape.error.title"));
        title.getStyleClass().add("apple-dialog-title");
        Label subtitle = new Label(bundle.getString("scrape.error.subtitle"));
        subtitle.getStyleClass().add("apple-dialog-status-subtle");
        VBox titles = new VBox(4, title, subtitle);

        HBox header = new HBox(14, halo, titles);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("scrape-error-header");

        // --- Body
        String reasonKey = switch (result.getFailureCode()) {
            case HTTP_ERROR -> "scrape.error.http";
            case SELECTOR_MISMATCH -> "scrape.error.selector";
            case EMPTY_FIRST_PAGE -> "scrape.error.empty_first_page";
            case NETWORK -> "scrape.error.network";
            default -> "scrape.error.network";
        };
        Label reason = new Label(bundle.getString(reasonKey));
        reason.setWrapText(true);
        reason.getStyleClass().add("scrape-error-reason");
        reason.setMaxWidth(420);

        // Meta row: last success + cached count
        long lastTs = lcm.getLastUpdatedTimestamp();
        String lastSuccess = (lastTs > 0)
                ? MessageFormat.format(bundle.getString("scrape.error.lastSuccess"),
                        formatTimestamp(lastTs))
                : bundle.getString("scrape.error.lastSuccess.never");
        int cachedCount = libraryManager.getLibrary() != null
                ? libraryManager.getLibrary().getAllModels().size() : 0;
        String cachedText = MessageFormat.format(
                bundle.getString("scrape.error.cachedCount"), cachedCount);

        FontIcon clock = new FontIcon(Feather.CLOCK);
        clock.getStyleClass().add("scrape-error-meta-icon");
        clock.setIconSize(12);
        Label lastLbl = new Label(lastSuccess);
        lastLbl.getStyleClass().add("scrape-error-meta-text");

        FontIcon pkg = new FontIcon(Feather.PACKAGE);
        pkg.getStyleClass().add("scrape-error-meta-icon");
        pkg.setIconSize(12);
        Label cachedLbl = new Label(cachedText);
        cachedLbl.getStyleClass().add("scrape-error-meta-text");

        HBox meta = new HBox(8, clock, lastLbl,
                new Separator(javafx.geometry.Orientation.VERTICAL), pkg, cachedLbl);
        meta.setAlignment(Pos.CENTER_LEFT);
        meta.getStyleClass().add("scrape-error-meta");

        // Code chip
        FontIcon tag = new FontIcon(Feather.TAG);
        tag.setIconSize(11);
        Label code = new Label(result.getFailureCode().name());
        code.getStyleClass().add("scrape-error-code-text");
        HBox chip = new HBox(6, tag, code);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("scrape-error-code-chip");
        chip.setMaxWidth(Region.USE_PREF_SIZE);

        VBox body = new VBox(10, reason, meta, chip);
        body.getStyleClass().add("scrape-error-body");

        VBox root = new VBox(0, header, body);
        root.setPrefWidth(460);
        pane.setContent(root);

        // --- Buttons
        ButtonType retryBt = new ButtonType(bundle.getString("scrape.error.retry"),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType offlineBt = new ButtonType(bundle.getString("scrape.error.continue_offline"),
                ButtonBar.ButtonData.OTHER);
        ButtonType exitBt = new ButtonType(bundle.getString("scrape.error.exit"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(exitBt, offlineBt, retryBt);

        // Wire stylesheet
        if (pane.getScene() != null) {
            try {
                pane.getStylesheets().add(
                        App.class.getResource("/css/graden_models_active.css").toExternalForm());
            } catch (Exception ignored) { }
        }
        try {
            pane.getStylesheets().add(
                    App.class.getResource("/css/graden_models_active.css").toExternalForm());
        } catch (Exception ignored) { }

        // Disable "Continue offline" when no previous cache
        Button offBtn = (Button) pane.lookupButton(offlineBt);
        if (offBtn != null) {
            offBtn.getStyleClass().add("apple-button-secondary");
            offBtn.setDisable(!canOffline);
        }
        Button retBtn = (Button) pane.lookupButton(retryBt);
        if (retBtn != null) {
            retBtn.getStyleClass().add("apple-button-action");
            retBtn.setDefaultButton(true);
            FontIcon rfr = new FontIcon(Feather.REFRESH_CW);
            rfr.setIconSize(13);
            retBtn.setGraphic(rfr);
            retBtn.setGraphicTextGap(6);
        }
        Button exBtn = (Button) pane.lookupButton(exitBt);
        if (exBtn != null) {
            exBtn.getStyleClass().add("apple-button-destructive");
        }

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() == exitBt) {
            Platform.exit();
            return;
        }
        ButtonType picked = choice.get();
        if (picked == retryBt) {
            startInitialization();
        } else if (picked == offlineBt) {
            // Reload main UI relying on existing cache (we never overwrote it on failure).
            App.reloadUI();
        }
    }

    private String formatTimestamp(long ts) {
        try {
            Locale loc = Locale.getDefault();
            DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(loc).withZone(ZoneId.systemDefault());
            return fmt.format(Instant.ofEpochMilli(ts));
        } catch (Exception e) {
            return String.valueOf(ts);
        }
    }
}
