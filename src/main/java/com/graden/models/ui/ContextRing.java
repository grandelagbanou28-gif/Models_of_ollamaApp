package com.graden.models.ui;

import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;

/**
 * Small circular context-usage gauge, à la Claude: a thin ring that fills
 * clockwise as the conversation context grows toward the model's budget.
 * Replaces the old full-width green pill — same information, far less
 * intrusive. Exact numbers live in the tooltip.
 *
 * <ul>
 *   <li>green  — under 60% of budget</li>
 *   <li>amber  — 60–85%</li>
 *   <li>red    — over 85% (the model will start truncating)</li>
 * </ul>
 */
public class ContextRing extends Pane {

    private static final double SIZE = 22;
    private static final double RADIUS = 8;
    private static final double STROKE = 3;

    private static final Color TRACK = Color.web("#888888", 0.28);
    private static final Color GREEN = Color.web("#16a34a");
    private static final Color AMBER = Color.web("#f59e0b");
    private static final Color RED = Color.web("#dc2626");

    private final Arc progress;
    private final Tooltip tooltip = new Tooltip();

    public ContextRing() {
        setPrefSize(SIZE, SIZE);
        setMinSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);

        Circle track = new Circle(SIZE / 2, SIZE / 2, RADIUS);
        track.setFill(null);
        track.setStroke(TRACK);
        track.setStrokeWidth(STROKE);

        // startAngle 90° = 12 o'clock; negative length = clockwise fill.
        progress = new Arc(SIZE / 2, SIZE / 2, RADIUS, RADIUS, 90, 0);
        progress.setType(ArcType.OPEN);
        progress.setFill(null);
        progress.setStroke(GREEN);
        progress.setStrokeWidth(STROKE);
        progress.setStrokeLineCap(StrokeLineCap.ROUND);

        getChildren().addAll(track, progress);

        tooltip.setShowDelay(javafx.util.Duration.millis(200));
        Tooltip.install(this, tooltip);

        setVisible(false);
        setManaged(false);
    }

    /**
     * Updates the gauge.
     *
     * @param usedTokens   approximate tokens currently consumed
     * @param budgetTokens usable budget
     * @param tooltipText  human-readable detail shown on hover
     */
    public void update(int usedTokens, int budgetTokens, String tooltipText) {
        double ratio = budgetTokens > 0 ? (double) usedTokens / budgetTokens : 0;
        double visual = Math.max(0, Math.min(1.0, ratio));
        progress.setLength(-visual * 360);

        if (ratio < 0.6) {
            progress.setStroke(GREEN);
        } else if (ratio < 0.85) {
            progress.setStroke(AMBER);
        } else {
            progress.setStroke(RED);
        }

        tooltip.setText(tooltipText);
        setVisible(true);
        setManaged(true);
    }

    /** Hides the gauge (no context worth showing). */
    public void hideRing() {
        setVisible(false);
        setManaged(false);
    }
}
