package com.graden.models.controller;

import com.graden.models.App;
import com.graden.models.manager.AuthManager;
import com.graden.models.manager.GoogleAuthService;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private TabPane tabPane;
    @FXML private Tab loginTab;
    @FXML private Tab signupTab;
    @FXML private TextField loginEmailField;
    @FXML private PasswordField loginPasswordField;
    @FXML private Label loginErrorLabel;
    @FXML private TextField signupEmailField;
    @FXML private PasswordField signupPasswordField;
    @FXML private PasswordField signupConfirmField;
    @FXML private Label signupErrorLabel;

    private Runnable onLoginSuccess;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loginErrorLabel.setManaged(false);
        signupErrorLabel.setManaged(false);
    }

    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    @FXML
    private void handleLogin() {
        String email = loginEmailField.getText();
        String password = loginPasswordField.getText();

        String error = AuthManager.getInstance().login(email, password);
        if (error != null) {
            showLoginError(error);
        } else if (onLoginSuccess != null) {
            onLoginSuccess.run();
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

        String error = AuthManager.getInstance().register(email, password);
        if (error != null) {
            showSignupError(error);
        } else {
            AuthManager.getInstance().login(email, password);
            if (onLoginSuccess != null) {
                onLoginSuccess.run();
            }
        }
    }

    @FXML
    private void handleGoogleLogin() {
        if (!GoogleAuthService.getInstance().isConfigured()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Google Sign-In");
            alert.setHeaderText(null);
            alert.setContentText("Google OAuth is not configured.\n\n" +
                    "Go to Settings → Google OAuth to add your Client ID and Secret.\n\n" +
                    "To get credentials:\n" +
                    "1. Go to https://console.cloud.google.com/\n" +
                    "2. Create a project → APIs & Services → Credentials\n" +
                    "3. Create OAuth 2.0 Client ID (Desktop app type)\n" +
                    "4. Add http://localhost as a redirect URI\n" +
                    "5. Copy the Client ID and Client Secret");
            alert.showAndWait();
            return;
        }

        showLoginError("Opening browser for Google authentication...");

        GoogleAuthService.getInstance().authenticate()
                .thenAccept(email -> {
                    Platform.runLater(() -> {
                        String localError = AuthManager.getInstance().loginWithGoogle(email);
                        if (localError != null) {
                            AuthManager.getInstance().registerWithGoogle(email);
                            AuthManager.getInstance().loginWithGoogle(email);
                        }
                        if (onLoginSuccess != null) {
                            onLoginSuccess.run();
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        showLoginError("Google auth failed: " + ex.getMessage());
                    });
                    return null;
                });
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
