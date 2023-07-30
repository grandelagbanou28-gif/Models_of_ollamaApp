// src/main/java/com/org/GrandelGradenNexus/App.java
package com.graden.models;

import com.graden.models.controller.LoginController;
import com.graden.models.controller.MainController;
import com.graden.models.manager.AuthManager;
import com.graden.models.manager.ChatManager;
import com.graden.models.manager.ConfigManager;
import com.graden.models.manager.ModelLibraryManager;
import com.graden.models.manager.ModelManager;
import com.graden.models.manager.OllamaServiceManager;
import com.graden.models.util.Utils;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;

import java.awt.Taskbar;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class App extends Application {

    private static ExecutorService executorService;
    private static HostServices hostServices;

    public static ExecutorService getExecutorService() {
        return executorService;
    }

    public static HostServices getAppHostServices() {
        return hostServices;
    }

    @Override
    public void init() throws Exception {
        executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }

    public static ResourceBundle getBundle() {
        String lang = ConfigManager.getInstance().getLanguage();
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        return ResourceBundle.getBundle("messages", locale);
    }

    private static Stage primaryStage;
    private static ModelManager modelManager;

    @Override
    public void start(Stage stage) throws IOException {
        // Global Exception Handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("CRITICAL UNCAUGHT EXCEPTION on thread " + thread.getName());
            throwable.printStackTrace();
            Platform.runLater(() -> {
                Utils.showError("Critical Error", "An error occurred: " + throwable.getMessage());
            });
        });

        primaryStage = stage;
        hostServices = getHostServices();

        loadInterFonts();

        // Apply saved theme
        String savedTheme = ConfigManager.getInstance().getTheme();
        if ("dark".equals(savedTheme)) {
            Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
        } else {
            Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
        }

        modelManager = ModelManager.getInstance();

        // Icon
        try {
            stage.getIcons().add(new Image(App.class.getResourceAsStream("/icons/icon.png")));
        } catch (Exception e) {
            // Ignore
        }

        // Show login screen first
        loadLoginUI();
    }

    private void loadLoginUI() throws IOException {
        ResourceBundle.clearCache();

        FXMLLoader loader = new FXMLLoader(App.class.getResource("/ui/login_view.fxml"));
        loader.setResources(getBundle());
        Parent root = loader.load();

        String savedTheme = ConfigManager.getInstance().getTheme();
        root.getStyleClass().add(savedTheme);

        StackPane sceneRoot = new StackPane(root);
        sceneRoot.getStyleClass().add(savedTheme);

        Scene scene = new Scene(sceneRoot);
        scene.getStylesheets().add(App.class.getResource("/css/graden_models_active.css").toExternalForm());

        LoginController controller = loader.getController();
        controller.setOnLoginSuccess(() -> {
            Platform.runLater(() -> {
                try {
                    ChatManager.getInstance().loadChats();
                    ModelLibraryManager.UpdateStatus cacheStatus = ModelLibraryManager.getInstance().getUpdateStatus();
                    boolean needsSplash = (cacheStatus == ModelLibraryManager.UpdateStatus.OUTDATED_HARD);
                    if (needsSplash) {
                        loadSplashScreen();
                    } else {
                        loadMainUI();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        });

        primaryStage.setScene(scene);
        primaryStage.setTitle(getBundle().getString("app.title"));
        primaryStage.setMaximized(false);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(780);
        primaryStage.setMinHeight(520);
        primaryStage.show();
    }

    private void loadInterFonts() {
        String[] fontPaths = {
                "/fonts/Inter-Regular.ttf",
                "/fonts/Inter-SemiBold.ttf",
                "/fonts/Inter-Bold.ttf"
        };
        for (String path : fontPaths) {
            try (var in = App.class.getResourceAsStream(path)) {
                if (in != null) {
                    Font.loadFont(in, 12);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void loadSplashScreen() throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("/ui/splash_view.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(App.class.getResource("/css/graden_models_active.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle(getBundle().getString("app.title"));
    }

    private static void loadMainUI() throws IOException {
        ResourceBundle.clearCache();

        FXMLLoader loader = new FXMLLoader(App.class.getResource("/ui/main_view.fxml"));
        loader.setResources(getBundle());
        Parent root = loader.load();

        String savedTheme = ConfigManager.getInstance().getTheme();
        root.getStyleClass().add(savedTheme);

        // Wrap in a StackPane so overlays (e.g. the onboarding wizard) can be
        // layered on top of the whole window. The theme class MUST also live on
        // this wrapper — it becomes the scene root, and CSS uses `.root.dark`.
        StackPane sceneRoot = new StackPane(root);
        sceneRoot.getStyleClass().add(savedTheme);

        Scene scene = new Scene(sceneRoot);
        scene.getStylesheets().add(App.class.getResource("/css/graden_models_active.css").toExternalForm());

        MainController controller = loader.getController();
        controller.initModelManager(modelManager);

        primaryStage.setScene(scene);
        primaryStage.setTitle(getBundle().getString("app.title"));
        primaryStage.setMaximized(true);
    }

    public static void reloadUI() {
        try {
            ResourceBundle.clearCache();

            FXMLLoader loader = new FXMLLoader(App.class.getResource("/ui/main_view.fxml"));
            loader.setResources(getBundle());
            Parent root = loader.load();

            String savedTheme = ConfigManager.getInstance().getTheme();
            root.getStyleClass().add(savedTheme);

            // See loadMainUI(): wrap in StackPane for overlays; theme class must
            // be on the scene root too (`.root.dark` selectors).
            StackPane sceneRoot = new StackPane(root);
            sceneRoot.getStyleClass().add(savedTheme);

            Scene scene = new Scene(sceneRoot);
            scene.getStylesheets().add(App.class.getResource("/css/graden_models_active.css").toExternalForm());

            MainController controller = loader.getController();
            controller.initModelManager(modelManager);

            primaryStage.setScene(scene);
            primaryStage.setTitle(getBundle().getString("app.title"));

            try {
                primaryStage.getIcons()
                        .add(new Image(App.class.getResourceAsStream("/icons/icon.png")));
                if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                    Taskbar.getTaskbar().setIconImage(
                            Toolkit.getDefaultToolkit().getImage(App.class.getResource("/icons/icon.png")));
                }
            } catch (Exception e) {
            }

            primaryStage.setMaximized(true);

            if (!primaryStage.isShowing()) {
                primaryStage.show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Fuerza la recarga iniciando desde el Splash Screen
     */
    public static void reloadFromSplash() {
        try {
            // Delete details cache file
            File detailsCache = new File(System.getProperty("user.home"),
                    ".GrandelGradenNexus/details_cache.json");
            if (detailsCache.exists()) {
                detailsCache.delete();
            }

            // CRITICAL: Invalidate in-memory cache to force OUTDATED_HARD status
            ModelLibraryManager.getInstance().invalidateCache();

            FXMLLoader loader = new FXMLLoader(App.class.getResource("/ui/splash_view.fxml"));
            loader.setResources(getBundle());
            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(App.class.getResource("/css/splash.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("/css/graden_models_active.css").toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.setTitle(getBundle().getString("app.title"));
            primaryStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void logout() {
        AuthManager.getInstance().logout();
        Platform.runLater(() -> {
            try {
                ResourceBundle.clearCache();
                FXMLLoader loader = new FXMLLoader(App.class.getResource("/ui/login_view.fxml"));
                loader.setResources(getBundle());
                Parent root = loader.load();

                String savedTheme = ConfigManager.getInstance().getTheme();
                root.getStyleClass().add(savedTheme);

                StackPane sceneRoot = new StackPane(root);
                sceneRoot.getStyleClass().add(savedTheme);

                Scene scene = new Scene(sceneRoot);
                scene.getStylesheets().add(App.class.getResource("/css/graden_models_active.css").toExternalForm());

                LoginController controller = loader.getController();
                controller.setOnLoginSuccess(() -> {
                    Platform.runLater(() -> {
                        try {
                            ChatManager.getInstance().loadChats();
                            ModelLibraryManager.UpdateStatus cacheStatus = ModelLibraryManager.getInstance().getUpdateStatus();
                            boolean needsSplash = (cacheStatus == ModelLibraryManager.UpdateStatus.OUTDATED_HARD);
                            if (needsSplash) {
                                loadSplashScreen();
                            } else {
                                reloadUI();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                });

                primaryStage.setScene(scene);
                primaryStage.setTitle(getBundle().getString("app.title"));
                primaryStage.setMaximized(false);
                primaryStage.setResizable(true);
                primaryStage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void stop() throws Exception {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }

        com.graden.models.manager.RagManager.getInstance().shutdown();
        OllamaServiceManager.getInstance().stopOllama();
        ChatManager.getInstance().saveChats();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}