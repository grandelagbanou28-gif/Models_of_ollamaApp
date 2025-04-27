package com.graden.models.util;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.stage.Window;

public class ResponsiveManager {

    public enum Breakpoint {
        MOBILE,      // < 768px
        TABLET,      // 768px - 1023px
        DESKTOP      // >= 1024px
    }

    private static final double MOBILE_BREAKPOINT = 768;
    private static final double TABLET_BREAKPOINT = 1024;

    private final BooleanProperty mobile = new SimpleBooleanProperty(false);
    private final BooleanProperty tablet = new SimpleBooleanProperty(false);
    private final BooleanProperty desktop = new SimpleBooleanProperty(true);

    private Breakpoint currentBreakpoint = Breakpoint.DESKTOP;

    private static ResponsiveManager instance;

    private ResponsiveManager() {}

    public static ResponsiveManager getInstance() {
        if (instance == null) {
            instance = new ResponsiveManager();
        }
        return instance;
    }

    public void bindToScene(Scene scene) {
        scene.widthProperty().addListener((obs, oldVal, newVal) -> {
            updateBreakpoint(newVal.doubleValue());
        });
        // Initial check
        updateBreakpoint(scene.getWidth());
    }

    public void bindToWindow(Window window) {
        window.widthProperty().addListener((obs, oldVal, newVal) -> {
            updateBreakpoint(newVal.doubleValue());
        });
        // Initial check
        updateBreakpoint(window.getWidth());
    }

    private void updateBreakpoint(double width) {
        Breakpoint newBreakpoint;
        if (width < MOBILE_BREAKPOINT) {
            newBreakpoint = Breakpoint.MOBILE;
        } else if (width < TABLET_BREAKPOINT) {
            newBreakpoint = Breakpoint.TABLET;
        } else {
            newBreakpoint = Breakpoint.DESKTOP;
        }

        if (newBreakpoint != currentBreakpoint) {
            currentBreakpoint = newBreakpoint;
            Platform.runLater(() -> {
                mobile.set(newBreakpoint == Breakpoint.MOBILE);
                tablet.set(newBreakpoint == Breakpoint.TABLET);
                desktop.set(newBreakpoint == Breakpoint.DESKTOP);
            });
        }
    }

    public BooleanProperty mobileProperty() {
        return mobile;
    }

    public BooleanProperty tabletProperty() {
        return tablet;
    }

    public BooleanProperty desktopProperty() {
        return desktop;
    }

    public Breakpoint getCurrentBreakpoint() {
        return currentBreakpoint;
    }

    public boolean isMobile() {
        return currentBreakpoint == Breakpoint.MOBILE;
    }

    public boolean isTablet() {
        return currentBreakpoint == Breakpoint.TABLET;
    }

    public boolean isDesktop() {
        return currentBreakpoint == Breakpoint.DESKTOP;
    }

    public static double getMobileBreakpoint() {
        return MOBILE_BREAKPOINT;
    }

    public static double getTabletBreakpoint() {
        return TABLET_BREAKPOINT;
    }
}