package com.graden.models.controller;

import com.graden.models.App;
import com.graden.models.manager.AuthManager;
import com.graden.models.manager.SupabaseManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private ToggleButton loginToggle;
    @FXML private ToggleButton signupToggle;
    @FXML private VBox loginForm;
    @FXML private VBox signupForm;
    @FXML private TextField loginEmailField;
    @FXML private PasswordField loginPasswordField;
    @FXML private Label loginErrorLabel;
    @FXML private TextField signupEmailField;
    @FXML private PasswordField signupPasswordField;
    @FXML private PasswordField signupConfirmField;
    @FXML private Label signupErrorLabel;
    @FXML private Button googleButton;

    private Runnable onLoginSuccess;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loginErrorLabel.setManaged(false);
        signupErrorLabel.setManaged(false);

        ToggleGroup group = new ToggleGroup();
        loginToggle.setToggleGroup(group);
        signupToggle.setToggleGroup(group);
        loginToggle.setSelected(true);

        applyToggleStyle(loginToggle, true);
        applyToggleStyle(signupToggle, false);

        group.selectedToggleProperty().addListener((obs, old, sel) -> {
            boolean isLogin = sel == loginToggle;
            loginForm.setManaged(isLogin);
            loginForm.setVisible(isLogin);
            signupForm.setManaged(!isLogin);
            signupForm.setVisible(!isLogin);
            applyToggleStyle(loginToggle, isLogin);
            applyToggleStyle(signupToggle, !isLogin);
        });

        updateGoogleButton();
    }

    private void applyToggleStyle(ToggleButton btn, boolean selected) {
        if (selected) {
            btn.setStyle("-fx-background-color: #1a73e8; -fx-text-fill: white; -fx-font-weight: 700; -fx-font-size: 14px; -fx-padding: 10; -fx-cursor: hand; -fx-background-radius: 0; -fx-border-color: #1a73e8; -fx-border-width: 2; -fx-border-radius: 0;");
        } else {
            btn.setStyle("-fx-background-color: white; -fx-text-fill: #1a73e8; -fx-font-weight: 700; -fx-font-size: 14px; -fx-padding: 10; -fx-cursor: hand; -fx-background-radius: 0; -fx-border-color: #1a73e8; -fx-border-width: 2; -fx-border-radius: 0;");
        }
        // First button: round left corners
        if (btn == loginToggle) {
            btn.setStyle(btn.getStyle() + "-fx-background-radius: 8 0 0 8; -fx-border-radius: 8 0 0 8;");
        } else {
            btn.setStyle(btn.getStyle() + "-fx-background-radius: 0 8 8 0; -fx-border-radius: 0 8 8 0;");
        }
    }

    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    private void updateGoogleButton() {
        boolean hasSupabase = SupabaseManager.getInstance().isConfigured();
        if (googleButton != null) {
            googleButton.setDisable(!hasSupabase);
            if (!hasSupabase) {
                googleButton.setText("Google (configure Supabase in Settings)");
            } else {
                ResourceBundle bundle = App.getBundle();
                googleButton.setText(bundle != null ? bundle.getString("login.google") : "Continue with Google");
            }
        }
    }

    private boolean useSupabase() {
        return SupabaseManager.getInstance().isConfigured();
    }

    @FXML
    private void handleLogin() {
        String email = loginEmailField.getText();
        String password = loginPasswordField.getText();

        if (useSupabase()) {
            showLoginError("Connecting...");
            SupabaseManager.getInstance().signIn(email, password)
                .thenAccept(error -> Platform.runLater(() -> {
                    if (error != null) {
                        String localErr = AuthManager.getInstance().login(email, password);
                        if (localErr != null) showLoginError(error + " | " + localErr);
                        else if (onLoginSuccess != null) onLoginSuccess.run();
                    } else {
                        syncSupabaseUser();
                        if (onLoginSuccess != null) onLoginSuccess.run();
                    }
                }));
        } else {
            String error = AuthManager.getInstance().login(email, password);
            if (error != null) showLoginError(error);
            else if (onLoginSuccess != null) onLoginSuccess.run();
        }
    }

    @FXML
    private void handleSignup() {
        String email = signupEmailField.getText();
        String password = signupPasswordField.getText();
        String confirm = signupConfirmField.getText();

        if (!password.equals(confirm)) {
            showSignupError("Passwords do not match");
            return;
        }

        if (useSupabase()) {
            SupabaseManager.getInstance().signUp(email, password)
                .thenAccept(error -> Platform.runLater(() -> {
                    if (error != null) {
                        String localErr = AuthManager.getInstance().register(email, password);
                        if (localErr != null) showSignupError(error + " | " + localErr);
                        else {
                            AuthManager.getInstance().login(email, password);
                            if (onLoginSuccess != null) onLoginSuccess.run();
                        }
                    } else {
                        SupabaseManager.getInstance().signIn(email, password);
                        syncSupabaseUser();
                        if (onLoginSuccess != null) onLoginSuccess.run();
                    }
                }));
        } else {
            String error = AuthManager.getInstance().register(email, password);
            if (error != null) showSignupError(error);
            else {
                AuthManager.getInstance().login(email, password);
                if (onLoginSuccess != null) onLoginSuccess.run();
            }
        }
    }

    private void syncSupabaseUser() {
        String email = SupabaseManager.getInstance().getUserEmail();
        if (email != null && !AuthManager.getInstance().isEmailRegistered(email)) {
            AuthManager.getInstance().registerWithGoogle(email);
            AuthManager.getInstance().loginWithGoogle(email);
        }
    }

    @FXML
    private void handleGoogleLogin() {
        if (!useSupabase()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Google Sign-In");
            alert.setHeaderText(null);
            alert.setContentText("To use Google Sign-In, configure Supabase first:\n\n" +
                    "1. Create a free project at https://supabase.com\n" +
                    "2. Go to Project Settings → API\n" +
                    "3. Copy your Project URL and anon key\n" +
                    "4. In Settings → Supabase, enable Google Auth provider\n" +
                    "5. Paste the URL and anon key in Settings\n\n" +
                    "Alternatively, sign up with email and password.");
            alert.showAndWait();
            return;
        }

        showLoginError("Opening browser for Google authentication...");

        SupabaseManager.getInstance().signInWithGoogle()
            .thenRun(() -> Platform.runLater(() -> {
                String email = SupabaseManager.getInstance().getUserEmail();
                if (email != null) {
                    syncSupabaseUser();
                    if (onLoginSuccess != null) onLoginSuccess.run();
                } else {
                    showLoginError("Google auth incomplete. Check browser.");
                }
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    showLoginError("Google auth failed: " + ex.getMessage());
                });
                return null;
            });
    }

    @FXML
    private void handleGitHubLink() {
        try {
            java.awt.Desktop.getDesktop().browse(
                new java.net.URI("https://github.com/grandelagbanou28-gif/Models_of_ollamaApp.git")
            );
        } catch (Exception e) {
            System.err.println("Failed to open GitHub link: " + e.getMessage());
        }
    }

    private void showLoginError(String msg) {
        loginErrorLabel.setText(msg);
        loginErrorLabel.setManaged(true);
    }

    private void showSignupError(String msg) {
        signupErrorLabel.setText(msg);
        signupErrorLabel.setManaged(true);
    }
}
