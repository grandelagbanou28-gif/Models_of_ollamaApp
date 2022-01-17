package com.graden.models.controller;

import com.graden.models.App;
import com.graden.models.manager.AuthManager;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Google Sign-In");
        alert.setHeaderText(null);
        alert.setContentText("Google OAuth is not yet configured.\n\n" +
                "To enable Google Sign-In, you need to:\n" +
                "1. Create a Google Cloud Project\n" +
                "2. Enable the Google+ API\n" +
                "3. Configure OAuth consent screen\n" +
                "4. Register this app's redirect URI\n\n" +
                "For now, please use email and password to create an account.\n" +
                "This feature will be available in a future update.");
        alert.showAndWait();
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
