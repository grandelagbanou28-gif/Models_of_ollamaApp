package com.graden.models.controller;

import com.graden.models.App;
import com.graden.models.manager.GitHubStatsService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import javafx.geometry.Pos;
import com.graden.models.model.GitHubRelease;

import java.net.URL;
import java.util.Properties;
import java.util.ResourceBundle;
import java.io.InputStream;
import java.io.IOException;

public class AboutController implements Initializable {

    @FXML
    private Label versionLabel;
    @FXML
    private Label downloadsLabel;
    @FXML
    private ProgressIndicator downloadSpinner;
    @FXML
    private VBox changelogTimeline;

    // Buttons with icons set programmatically
    @FXML
    private Button paypalButton;
    @FXML
    private Button coffeeButton;
    @FXML
    private Button githubButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Load version
        String version = "Unknown";
        try (InputStream input = getClass().getResourceAsStream("/app.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);
                version = prop.getProperty("app.version", "Unknown");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        versionLabel.setText("Version " + version);

        // Load GitHub stats
        loadGitHubStats();
        loadGitHubReleases();
    }

    private void loadGitHubStats() {
        if (downloadsLabel != null && downloadSpinner != null) {
            downloadsLabel.setText("...");
            downloadSpinner.setVisible(true);
        }

        GitHubStatsService.getInstance().fetchTotalDownloads()
                .thenAccept(downloads -> {
                    Platform.runLater(() -> {
                        if (downloads > 0) {
                            downloadsLabel.setText(String.format("%,d", downloads));
                        } else {
                            downloadsLabel.setText("N/A");
                        }
                        if (downloadSpinner != null) {
                            downloadSpinner.setVisible(false);
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        downloadsLabel.setText("N/A");
                        if (downloadSpinner != null) {
                            downloadSpinner.setVisible(false);
                        }
                    });
                    return null;
                });
    }

    @FXML
    private void openGitHub() {
        openLink("https://github.com/grandelagbanou28-gif/Models_of_ollamaApp");
    }

    @FXML
    private void openPayPal() {
        openLink("https://www.paypal.com/donate/?business=grandelagbanou28@gmail.com");
    }

    @FXML
    private void openBuyMeCoffee() {
        openLink("https://buymeacoffee.com/grandelagbanou");
    }

    @FXML
    private void openGitHubContribute() {
        openLink("https://github.com/grandelagbanou28-gif/Models_of_ollamaApp/issues");
    }

    private void openLink(String url) {
        try {
            if (App.getAppHostServices() != null) {
                App.getAppHostServices().showDocument(url);
            } else {
                System.err.println("HostServices not available. URL: " + url);
            }
        } catch (Exception e) {
            System.err.println("Error opening URL: " + url);
            e.printStackTrace();
        }
    }

    private void loadGitHubReleases() {
        GitHubStatsService.getInstance().fetchReleases()
                .thenAccept(releases -> {
                    Platform.runLater(() -> {
                        if (changelogTimeline != null) {
                            changelogTimeline.getChildren().clear();
                            if (releases.isEmpty()) {
                                Label emptyLabel = new Label("No release history found.");
                                emptyLabel.setStyle("-fx-padding: 20px; -fx-text-fill: -color-fg-muted;");
                                changelogTimeline.getChildren().add(emptyLabel);
                                return;
                            }
                            
                            for (int i = 0; i < releases.size(); i++) {
                                GitHubRelease release = releases.get(i);
                                boolean isFirst = (i == 0);
                                boolean isLast = (i == releases.size() - 1);
                                
                                HBox itemBox = createTimelineItem(release, isFirst, isLast);
                                changelogTimeline.getChildren().add(itemBox);
                            }
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        if (changelogTimeline != null) {
                            changelogTimeline.getChildren().clear();
                            Label errorLabel = new Label("Error loading history.");
                            errorLabel.setStyle("-fx-padding: 20px; -fx-text-fill: -color-danger-fg;");
                            changelogTimeline.getChildren().add(errorLabel);
                        }
                    });
                    return null;
                });
    }

    private HBox createTimelineItem(GitHubRelease release, boolean isFirst, boolean isLast) {
        HBox itemBox = new HBox();
        itemBox.getStyleClass().add("timeline-item");

        // 1. Marker Container
        VBox markerContainer = new VBox();
        markerContainer.setAlignment(Pos.TOP_CENTER);
        markerContainer.getStyleClass().add("timeline-marker-container");

        StackPane dotPane = new StackPane();
        dotPane.setPrefHeight(24);
        dotPane.setMinHeight(24);

        if (!isLast) {
            Region lineThrough = new Region();
            lineThrough.getStyleClass().add("timeline-line");
            dotPane.getChildren().add(lineThrough);
        } else {
            VBox halfLineBox = new VBox();
            halfLineBox.setAlignment(Pos.TOP_CENTER);
            Region halfLine = new Region();
            halfLine.getStyleClass().add("timeline-line");
            halfLine.setPrefHeight(12);
            halfLine.setMinHeight(12);
            halfLineBox.getChildren().add(halfLine);
            dotPane.getChildren().add(halfLineBox);
        }

        Region dot = new Region();
        dot.getStyleClass().add("timeline-dot");
        if (isFirst) {
            dot.getStyleClass().add("timeline-dot-active");
        }
        dotPane.getChildren().add(dot);
        markerContainer.getChildren().add(dotPane);

        if (!isLast) {
            Region lineDown = new Region();
            lineDown.getStyleClass().add("timeline-line");
            VBox.setVgrow(lineDown, Priority.ALWAYS);
            markerContainer.getChildren().add(lineDown);
        }

        // 2. Details Container
        VBox detailsContainer = new VBox();
        detailsContainer.setSpacing(4);
        detailsContainer.getStyleClass().add("changelog-details");
        HBox.setHgrow(detailsContainer, Priority.ALWAYS);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setSpacing(8);

        Label versionLabel = new Label(release.tagName());
        versionLabel.getStyleClass().add("changelog-version-badge");
        if (isFirst) {
            versionLabel.getStyleClass().add("changelog-badge-current");
        }
        
        Label dateLabel = new Label(release.publishedAt());
        dateLabel.getStyleClass().add("changelog-date");
        
        headerBox.getChildren().addAll(versionLabel, dateLabel);
        detailsContainer.getChildren().add(headerBox);

        VBox bulletsContainer = new VBox();
        bulletsContainer.setSpacing(3);
        bulletsContainer.getStyleClass().add("changelog-bullets-container");
        
        // Parse simple markdown bullets from body
        String[] lines = release.body().split("\n");
        for (String line : lines) {
            String cleanLine = line.trim();
            if (!cleanLine.isEmpty() && !cleanLine.startsWith("#")) {
                if (cleanLine.startsWith("* ") || cleanLine.startsWith("- ")) {
                    cleanLine = "• " + cleanLine.substring(2);
                } else if (!cleanLine.startsWith("•")) {
                    cleanLine = "• " + cleanLine;
                }
                
                cleanLine = cleanLine.replace("**", ""); // remove bold tags
                
                Label bulletLabel = new Label(cleanLine);
                bulletLabel.getStyleClass().add("changelog-bullet-text");
                bulletLabel.setWrapText(true);
                bulletsContainer.getChildren().add(bulletLabel);
            }
        }
        
        detailsContainer.getChildren().add(bulletsContainer);
        itemBox.getChildren().addAll(markerContainer, detailsContainer);
        
        return itemBox;
    }
}
