package com.graden.models.ui;

import com.graden.models.model.ToolCall;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

public class ToolCallCard extends VBox {

    private final ToolCall toolCall;
    private final FontIcon headerIcon;
    private final Label headerName;
    private final Label statusLabel;
    private final VBox detailsBox;
    private final Button copyButton;
    private boolean expanded = false;

    public ToolCallCard(ToolCall toolCall) {
        this.toolCall = toolCall;

        getStyleClass().add("tool-call-card");
        setMaxWidth(Double.MAX_VALUE);
        setCursor(Cursor.HAND);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("tool-call-header");

        this.headerIcon = new FontIcon("fth-tool");
        headerIcon.setIconSize(14);
        headerIcon.getStyleClass().add("tool-call-icon");

        this.headerName = new Label(toolCall.getName());
        headerName.getStyleClass().add("tool-call-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.statusLabel = new Label("\u23F3");
        statusLabel.getStyleClass().add("tool-call-status");
        statusLabel.getStyleClass().add("tool-call-status-pending");

        this.copyButton = createCopyButton();

        header.getChildren().addAll(headerIcon, headerName, spacer, statusLabel, copyButton);
        getChildren().add(header);

        this.detailsBox = new VBox(6);
        detailsBox.getStyleClass().add("tool-call-details");
        detailsBox.setVisible(false);
        detailsBox.setManaged(false);
        getChildren().add(detailsBox);

        setOnMouseClicked(e -> toggleExpanded());
    }

    public ToolCall getToolCall() {
        return toolCall;
    }

    public void setRunning() {
        Platform.runLater(() -> {
            statusLabel.setText("\u23F3");
            statusLabel.getStyleClass().removeAll("tool-call-status-success", "tool-call-status-error");
            statusLabel.getStyleClass().add("tool-call-status-pending");
        });
    }

    public void setCompleted() {
        Platform.runLater(() -> {
            if (toolCall.isFailed()) {
                statusLabel.setText("\u2717");
                statusLabel.getStyleClass().removeAll("tool-call-status-pending", "tool-call-status-success");
                statusLabel.getStyleClass().add("tool-call-status-error");
            } else {
                statusLabel.setText("\u2713");
                statusLabel.getStyleClass().removeAll("tool-call-status-pending", "tool-call-status-error");
                statusLabel.getStyleClass().add("tool-call-status-success");
            }
            populateDetails();
        });
    }

    private void toggleExpanded() {
        expanded = !expanded;
        detailsBox.setVisible(expanded);
        detailsBox.setManaged(expanded);

        if (expanded && detailsBox.getChildren().isEmpty()) {
            populateDetails();
        }
    }

    private void populateDetails() {
        detailsBox.getChildren().clear();

        Label argsLabel = new Label("Arguments:");
        argsLabel.getStyleClass().add("tool-call-detail-label");

        Label argsValue = new Label(formatArgs());
        argsValue.getStyleClass().add("tool-call-detail-value");
        argsValue.setWrapText(true);

        Label resultLabel = new Label("Result:");
        resultLabel.getStyleClass().add("tool-call-detail-label");

        String resultText = toolCall.getResult() != null ? toolCall.getResult() : "(pending)";
        Label resultValue = new Label(resultText);
        resultValue.getStyleClass().add("tool-call-detail-value");
        resultValue.setWrapText(true);

        detailsBox.getChildren().addAll(argsLabel, argsValue, resultLabel, resultValue);
    }

    private String formatArgs() {
        if (toolCall.getArgs() == null || toolCall.getArgs().isEmpty()) {
            return "(none)";
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(toolCall.getArgs());
        } catch (Exception e) {
            return toolCall.getArgs().toString();
        }
    }

    private Button createCopyButton() {
        Button btn = new Button();
        btn.getStyleClass().add("tool-call-copy-btn");
        FontIcon icon = new FontIcon("fth-copy");
        icon.setIconSize(12);
        btn.setGraphic(icon);

        btn.setOnAction(e -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Tool: ").append(toolCall.getName()).append("\n");
            sb.append("Args: ").append(formatArgs()).append("\n");
            sb.append("Result: ").append(toolCall.getResult() != null ? toolCall.getResult() : "(pending)");
            sb.append("\nDuration: ").append(toolCall.formattedDuration());

            ClipboardContent content = new ClipboardContent();
            content.putString(sb.toString());
            Clipboard.getSystemClipboard().setContent(content);

            FontIcon copyIcon = (FontIcon) btn.getGraphic();
            copyIcon.setStyle("-fx-icon-color: -color-success-fg;");
            PauseTransition pause = new PauseTransition(Duration.seconds(1));
            pause.setOnFinished(ev -> copyIcon.setStyle(""));
            pause.play();
        });

        return btn;
    }
}
