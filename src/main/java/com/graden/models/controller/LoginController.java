package com.graden.models.controller;

import com.graden.models.App;
import com.graden.models.manager.AuthManager;
import com.graden.models.manager.ConfigManager;
import com.graden.models.manager.SupabaseManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
    @FXML private TextField signupEmailField;
    @FXML private PasswordField signupPasswordField;
    @FXML private PasswordField signupConfirmField;
    @FXML private CheckBox rememberCheckbox;
    @FXML private Label loginError;
    @FXML private Button googleButton;

    private Runnable onLoginSuccess;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
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
            clearLoginError();
        });

        updateGoogleButton();

        // Load saved email
        if (ConfigManager.getInstance().isRememberEmail()) {
            rememberCheckbox.setSelected(true);
            loginEmailField.setText(ConfigManager.getInstance().getSavedEmail());
            loginPasswordField.requestFocus();
        }
    }

    private void setLoginError(String message) {
        if (loginError != null) {
            loginError.setText(message);
            loginError.setManaged(true);
            loginError.setVisible(true);
        }
    }

    private void clearLoginError() {
        if (loginError != null) {
            loginError.setText("");
            loginError.setManaged(false);
            loginError.setVisible(false);
        }
    }

    private void saveCredentials() {
        if (rememberCheckbox.isSelected()) {
            ConfigManager.getInstance().setSavedEmail(loginEmailField.getText().trim());
            ConfigManager.getInstance().setRememberEmail(true);
        } else {
            ConfigManager.getInstance().setSavedEmail("");
            ConfigManager.getInstance().setRememberEmail(false);
        }
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
        clearFieldErrors();
        clearLoginError();
        String email = loginEmailField.getText();
        String password = loginPasswordField.getText();
        boolean hasError = false;

        if (email.isEmpty() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            loginEmailField.getStyleClass().add("field-error");
            setLoginError("Email invalide");
            hasError = true;
        }
        if (password.isEmpty()) {
            loginPasswordField.getStyleClass().add("field-error");
            setLoginError("Mot de passe requis");
            hasError = true;
        }
        if (hasError) return;

        if (useSupabase()) {
            SupabaseManager.getInstance().signIn(email, password)
                .thenAccept(error -> Platform.runLater(() -> {
                    if (error != null) {
                        String localErr = AuthManager.getInstance().login(email, password);
                        if (localErr != null) {
                            loginPasswordField.getStyleClass().add("field-error");
                            setLoginError(localErr);
                        } else {
                            saveCredentials();
                            if (onLoginSuccess != null) onLoginSuccess.run();
                        }
                    } else {
                        syncSupabaseUser();
                        saveCredentials();
                        if (onLoginSuccess != null) onLoginSuccess.run();
                    }
                }));
        } else {
            String error = AuthManager.getInstance().login(email, password);
            if (error != null) {
                loginPasswordField.getStyleClass().add("field-error");
                setLoginError(error);
            } else {
                saveCredentials();
                if (onLoginSuccess != null) onLoginSuccess.run();
            }
        }
    }

    @FXML
    private void handleSignup() {
        clearFieldErrors();
        String email = signupEmailField.getText();
        String password = signupPasswordField.getText();
        String confirm = signupConfirmField.getText();
        boolean hasError = false;

        if (email.isEmpty() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            signupEmailField.getStyleClass().add("field-error");
            hasError = true;
        }
        if (password.isEmpty() || password.length() < 6) {
            signupPasswordField.getStyleClass().add("field-error");
            hasError = true;
        }
        if (!password.equals(confirm)) {
            signupConfirmField.getStyleClass().add("field-error");
            hasError = true;
        }
        if (hasError) return;

        if (useSupabase()) {
            SupabaseManager.getInstance().signUp(email, password)
                .thenAccept(error -> Platform.runLater(() -> {
                    if (error != null) {
                        String localErr = AuthManager.getInstance().register(email, password);
                        if (localErr != null) {
                            signupEmailField.getStyleClass().add("field-error");
                        } else {
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
            if (error != null) {
                signupEmailField.getStyleClass().add("field-error");
            } else {
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
    private void handleGitHubLink() {
        try {
            java.awt.Desktop.getDesktop().browse(
                new java.net.URI("https://github.com/grandelagbanou28-gif/Models_of_ollamaApp.git")
            );
        } catch (Exception e) {
            System.err.println("Failed to open GitHub link: " + e.getMessage());
        }
    }

    private void clearFieldErrors() {
        for (var fld : new javafx.scene.Node[]{loginEmailField, loginPasswordField, signupEmailField, signupPasswordField, signupConfirmField}) {
            fld.getStyleClass().remove("field-error");
            fld.getStyleClass().add("field-normal");
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
        SupabaseManager.getInstance().signInWithGoogle()
            .thenRun(() -> Platform.runLater(() -> {
                String email = SupabaseManager.getInstance().getUserEmail();
                if (email != null) {
                    syncSupabaseUser();
                    if (onLoginSuccess != null) onLoginSuccess.run();
                }
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> loginEmailField.getStyleClass().add("field-error"));
                return null;
            });
    }
}
