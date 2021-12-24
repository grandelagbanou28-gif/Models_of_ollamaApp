package com.graden.models.ui;

import java.util.List;

import org.kordamp.ikonli.javafx.FontIcon;

import com.graden.models.App;
import com.graden.models.manager.ConfigManager;
import com.graden.models.manager.HardwareManager;
import com.graden.models.manager.OllamaManager;
import com.graden.models.manager.OllamaServiceManager;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * First-run onboarding wizard, shown as a dimmed in-scene overlay on top of
 * the main window. Four steps: welcome, detect Ollama, install a first
 * model, and a short feature tour.
 *
 * <p>Built programmatically (no FXML) because the steps are highly dynamic —
 * the Ollama-detection step alone has three branching states. Reuses
 * {@link OllamaServiceManager}, {@link OllamaManager} and
 * {@link HardwareManager}; nothing here re-implements existing logic.
 */
public class OnboardingOverlay extends StackPane {

    /** A curated starter model: ollama name, tag, approximate size, blurb key. */
    private record StarterModel(String name, String tag, String size, double minRamGb) {
        String fullName() { return name + ":" + tag; }
    }

    private static final StarterModel[] STARTERS = {
            new StarterModel("llama3.2", "3b", "~2 GB", 0),
            new StarterModel("qwen2.5", "7b", "~4.7 GB", 8),
            new StarterModel("llama3.1", "8b", "~4.9 GB", 16),
    };

    private static final int TOTAL_STEPS = 4;

    private final VBox stepArea = new VBox();
    private final HBox dotsRow = new HBox(8);
    private final Button backBtn = new Button();
    private final Button nextBtn = new Button();
    private final Button skipBtn = new Button();

    private int step = 0;
    private int tourIndex = 0;
    private boolean ollamaReady = false;
    private boolean modelStepDone = false;

    private OnboardingOverlay() {
        getStyleClass().add("onboarding-overlay");
        setAlignment(Pos.CENTER);

        VBox card = new VBox(18);
        card.getStyleClass().add("onboarding-card");
        card.setMaxWidth(540);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPadding(new Insets(28));

        dotsRow.setAlignment(Pos.CENTER);

        stepArea.setMinHeight(280);
        stepArea.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(stepArea, Priority.ALWAYS);

        // Nav row
        skipBtn.setText(t("onboarding.nav.skip"));
        skipBtn.getStyleClass().add("flat");
        skipBtn.setOnAction(e -> finish());

        backBtn.setText(t("onboarding.nav.back"));
        backBtn.getStyleClass().add("button-outlined");
        backBtn.setOnAction(e -> back());

        nextBtn.setText(t("onboarding.nav.next"));
        nextBtn.getStyleClass().add("accent");
        nextBtn.setOnAction(e -> next());

        Region navSpacer = new Region();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);
        HBox nav = new HBox(8, skipBtn, navSpacer, backBtn, nextBtn);
        nav.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(dotsRow, stepArea, nav);
        getChildren().add(card);

        render();
    }

    /** Creates the overlay and adds it on top of the given scene root. */
    public static void showOver(StackPane sceneRoot) {
        if (sceneRoot == null) return;
        OnboardingOverlay overlay = new OnboardingOverlay();
        overlay.setOpacity(0);
        sceneRoot.getChildren().add(overlay);
        FadeTransition fade = new FadeTransition(Duration.millis(220), overlay);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    // ─────────────────────────── navigation ────────────────────────────────

    private void next() {
        if (step == 3) {
            if (tourIndex < 3) {
                tourIndex++;
                render();
            } else {
                finish();
            }
            return;
        }
        if (step < TOTAL_STEPS - 1) {
            step++;
            render();
        }
    }

    private void back() {
        if (step == 3 && tourIndex > 0) {
            tourIndex--;
            render();
        } else if (step > 0) {
            step--;
            render();
        }
    }

    private void finish() {
        ConfigManager.getInstance().setFirstRunCompleted(true);
        FadeTransition fade = new FadeTransition(Duration.millis(200), this);
        fade.setFromValue(getOpacity());
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            if (getParent() instanceof javafx.scene.layout.Pane p) {
                p.getChildren().remove(this);
            }
        });
        fade.play();
    }

    // ─────────────────────────── rendering ─────────────────────────────────

    private void render() {
        renderDots();
        stepArea.getChildren().setAll(switch (step) {
            case 0 -> buildWelcome();
            case 1 -> buildOllamaCheck();
            case 2 -> buildModelInstall();
            default -> buildTour();
        });

        backBtn.setVisible(step > 0);
        backBtn.setManaged(step > 0);
        skipBtn.setVisible(step < 3);
        skipBtn.setManaged(step < 3);

        // Per-step gating of the Next button.
        switch (step) {
            case 1 -> nextBtn.setDisable(!ollamaReady);
            case 2 -> nextBtn.setDisable(!modelStepDone);
            default -> nextBtn.setDisable(false);
        }
        nextBtn.setText(step == 3 && tourIndex == 3
                ? t("onboarding.nav.finish") : t("onboarding.nav.next"));
    }

    private void renderDots() {
        dotsRow.getChildren().clear();
        for (int i = 0; i < TOTAL_STEPS; i++) {
            Region dot = new Region();
            dot.getStyleClass().add("onboarding-step-dot");
            if (i == step) dot.getStyleClass().add("onboarding-step-dot-active");
            dotsRow.getChildren().add(dot);
        }
    }

    // ─────────────────────────── step 1: welcome ───────────────────────────

    private VBox buildWelcome() {
        FontIcon icon = new FontIcon("fth-cpu");
        icon.setIconSize(48);
        icon.getStyleClass().add("onboarding-hero-icon");

        Label title = new Label(t("onboarding.welcome.title"));
        title.getStyleClass().add("onboarding-title");

        Label subtitle = new Label(t("onboarding.welcome.subtitle"));
        subtitle.getStyleClass().add("onboarding-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(420);
        subtitle.setAlignment(Pos.CENTER);

        VBox box = new VBox(14, icon, title, subtitle);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20, 0, 0, 0));
        return box;
    }

    // ─────────────────────────── step 2: Ollama ────────────────────────────

    private VBox buildOllamaCheck() {
        Label title = new Label(t("onboarding.ollama.title"));
        title.getStyleClass().add("onboarding-title");

        VBox statusBox = new VBox(12);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setPadding(new Insets(16, 0, 0, 0));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(32, 32);
        Label statusLabel = new Label(t("onboarding.ollama.checking"));
        statusLabel.getStyleClass().add("onboarding-subtitle");
        statusBox.getChildren().addAll(spinner, statusLabel);

        VBox box = new VBox(8, title, statusBox);
        box.setAlignment(Pos.CENTER);

        // Detect in background — never block the FX thread.
        App.getExecutorService().submit(() -> {
            OllamaServiceManager svc = OllamaServiceManager.getInstance();
            boolean installed = svc.isInstalled();
            boolean running = installed && svc.isRunning();
            Platform.runLater(() -> {
                if (step != 1) return; // user navigated away
                if (running) {
                    ollamaReady = true;
                    statusBox.getChildren().setAll(
                            okIcon(), label(t("onboarding.ollama.running")));
                    nextBtn.setDisable(false);
                } else if (installed) {
                    statusBox.getChildren().setAll(buildStartOllamaPane(statusBox));
                } else {
                    statusBox.getChildren().setAll(buildNotInstalledPane(box));
                }
            });
        });
        return box;
    }

    private VBox buildStartOllamaPane(VBox statusBox) {
        Label msg = label(t("onboarding.ollama.notRunning"));
        Button startBtn = new Button(t("onboarding.ollama.start"));
        startBtn.getStyleClass().add("accent");
        VBox pane = new VBox(12, msg, startBtn);
        pane.setAlignment(Pos.CENTER);

        startBtn.setOnAction(e -> {
            ProgressIndicator spin = new ProgressIndicator();
            spin.setPrefSize(28, 28);
            statusBox.getChildren().setAll(spin, label(t("onboarding.ollama.starting")));
            App.getExecutorService().submit(() -> {
                boolean ok = OllamaServiceManager.getInstance().startOllama();
                Platform.runLater(() -> {
                    if (step != 1) return;
                    if (ok) {
                        ollamaReady = true;
                        statusBox.getChildren().setAll(okIcon(), label(t("onboarding.ollama.running")));
                        nextBtn.setDisable(false);
                    } else {
                        statusBox.getChildren().setAll(buildStartOllamaPane(statusBox));
                        ((Label) ((VBox) statusBox.getChildren().get(0)).getChildren().get(0))
                                .setText(t("onboarding.ollama.startFailed"));
                    }
                });
            });
        });
        return pane;
    }

    private VBox buildNotInstalledPane(VBox box) {
        Label msg = label(t("onboarding.ollama.notInstalled"));
        msg.setWrapText(true);
        msg.setMaxWidth(420);

        Hyperlink dl = new Hyperlink(t("onboarding.ollama.download"));
        dl.setOnAction(e -> {
            if (App.getAppHostServices() != null) {
                App.getAppHostServices().showDocument("https://ollama.com/download");
            }
        });

        Button retry = new Button(t("onboarding.ollama.retry"));
        retry.getStyleClass().add("button-outlined");
        retry.setOnAction(e -> render()); // re-runs buildOllamaCheck

        VBox pane = new VBox(10, msg, dl, retry);
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    // ─────────────────────────── step 3: model ─────────────────────────────

    private VBox buildModelInstall() {
        Label title = new Label(t("onboarding.model.title"));
        title.getStyleClass().add("onboarding-title");

        VBox body = new VBox(10);
        body.setAlignment(Pos.CENTER);
        body.setPadding(new Insets(12, 0, 0, 0));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(28, 28);
        body.getChildren().add(spinner);

        VBox box = new VBox(8, title, body);
        box.setAlignment(Pos.CENTER);

        App.getExecutorService().submit(() -> {
            int localCount = 0;
            try {
                List<?> local = OllamaManager.getInstance().getLocalModels();
                localCount = local != null ? local.size() : 0;
            } catch (Exception ignored) {
            }
            final int count = localCount;
            Platform.runLater(() -> {
                if (step != 2) return;
                if (count > 0) {
                    modelStepDone = true;
                    nextBtn.setDisable(false);
                    body.getChildren().setAll(okIcon(),
                            label(java.text.MessageFormat.format(
                                    t("onboarding.model.alreadyHave"), count)));
                } else {
                    body.getChildren().setAll(buildModelChooser(body));
                }
            });
        });
        return box;
    }

    private VBox buildModelChooser(VBox body) {
        Label intro = label(t("onboarding.model.intro"));
        intro.setWrapText(true);
        intro.setMaxWidth(440);

        double ramGb = HardwareManager.getStats().getTotalRamGB();
        // Recommend the largest starter the machine comfortably fits.
        int recommended = 0;
        for (int i = 0; i < STARTERS.length; i++) {
            if (ramGb >= STARTERS[i].minRamGb() + 4) recommended = i;
        }

        VBox cards = new VBox(8);
        cards.setAlignment(Pos.CENTER);
        for (int i = 0; i < STARTERS.length; i++) {
            cards.getChildren().add(buildModelCard(STARTERS[i], i == recommended, body));
        }

        VBox pane = new VBox(12, intro, cards);
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    private HBox buildModelCard(StarterModel m, boolean recommended, VBox body) {
        FontIcon icon = new FontIcon("fth-box");
        icon.setIconSize(18);

        Label name = new Label(m.fullName());
        name.getStyleClass().add("onboarding-model-name");
        Label size = new Label(m.size());
        size.getStyleClass().add("onboarding-model-size");
        VBox texts = new VBox(2, name, size);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox card = new HBox(10, icon, texts, spacer);
        card.getStyleClass().add("onboarding-model-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 14, 10, 14));

        if (recommended) {
            Label badge = new Label(t("onboarding.model.recommended"));
            badge.getStyleClass().add("onboarding-model-badge");
            card.getChildren().add(badge);
        }

        Button install = new Button(t("onboarding.model.install"));
        install.getStyleClass().add("accent");
        card.getChildren().add(install);

        install.setOnAction(e -> startPull(m, body));
        return card;
    }

    private void startPull(StarterModel m, VBox body) {
        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(360);
        Label status = label(java.text.MessageFormat.format(
                t("onboarding.model.installing"), m.fullName()));
        VBox progress = new VBox(10, status, bar);
        progress.setAlignment(Pos.CENTER);
        body.getChildren().setAll(progress);

        App.getExecutorService().submit(() -> {
            try {
                OllamaManager.getInstance().pullModel(m.name(), m.tag(), (prog, st) ->
                        Platform.runLater(() -> {
                            if (prog >= 0) bar.setProgress(prog);
                        }));
                Platform.runLater(() -> {
                    if (step != 2) return;
                    modelStepDone = true;
                    nextBtn.setDisable(false);
                    body.getChildren().setAll(okIcon(),
                            label(java.text.MessageFormat.format(
                                    t("onboarding.model.installed"), m.fullName())));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (step != 2) return;
                    body.getChildren().setAll(buildModelChooser(body));
                });
            }
        });
    }

    // ─────────────────────────── step 4: tour ──────────────────────────────

    private VBox buildTour() {
        String[] icons = { "fth-message-circle", "fth-book-open", "fth-download-cloud", "fth-settings" };
        String iconLit = icons[tourIndex];

        FontIcon icon = new FontIcon(iconLit);
        icon.setIconSize(44);
        icon.getStyleClass().add("onboarding-hero-icon");

        Label title = new Label(t("onboarding.tour." + (tourIndex + 1) + ".title"));
        title.getStyleClass().add("onboarding-title");

        Label desc = new Label(t("onboarding.tour." + (tourIndex + 1) + ".desc"));
        desc.getStyleClass().add("onboarding-subtitle");
        desc.setWrapText(true);
        desc.setMaxWidth(420);
        desc.setAlignment(Pos.CENTER);

        VBox box = new VBox(14, icon, title, desc);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24, 0, 0, 0));
        return box;
    }

    // ─────────────────────────── helpers ───────────────────────────────────

    private static String t(String key) {
        try {
            return App.getBundle().getString(key);
        } catch (Exception e) {
            return key;
        }
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("onboarding-subtitle");
        l.setWrapText(true);
        l.setMaxWidth(440);
        l.setAlignment(Pos.CENTER);
        return l;
    }

    private static FontIcon okIcon() {
        FontIcon ok = new FontIcon("fth-check-circle");
        ok.setIconSize(34);
        ok.getStyleClass().add("onboarding-ok-icon");
        return ok;
    }
}
