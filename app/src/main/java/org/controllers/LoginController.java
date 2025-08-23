package org.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.App;
import org.services.Session;
import org.util.Http;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginBtn;

    @FXML
    private void initialize() {
        errorLabel.setVisible(false);
    }

    @FXML
    private void onLogin() {
        String u = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String p = passwordField.getText() == null ? "" : passwordField.getText();
        if (u.isEmpty() || p.isEmpty()) { showError("Username dan password wajib diisi."); return; }
        errorLabel.setVisible(false);
        if (loginBtn != null) loginBtn.setDisable(true);
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                Http http = new Http();
                ObjectMapper mapper = new ObjectMapper();
                java.util.Map<String, String> payload = new java.util.HashMap<>();
                payload.put("username", u);
                payload.put("password", p);
                String body = mapper.writeValueAsString(payload);
                String resp = http.POST("api/auth/login", body);
                // Parse response with Jackson
                JsonNode node = mapper.readTree(resp);
                boolean status = node.path("status").asBoolean(false);
                if (!status) {
                    String msg = node.path("message").asText("Login gagal.");
                    throw new IllegalArgumentException(msg);
                }
                String token = node.path("token").asText("");
                if (token.isBlank()) throw new IllegalStateException("Token tidak ditemukan.");
                Session.set(token, u);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            if (loginBtn != null) loginBtn.setDisable(false);
            try { App.switchToMain(); } catch (Exception ex) { System.out.println(ex.getMessage());  showError("Gagal membuka aplikasi: " + ex.getMessage()); }
        });
        task.setOnFailed(e -> {
            if (loginBtn != null) loginBtn.setDisable(false);
            Throwable ex = task.getException();
            showError(ex != null && ex.getMessage() != null ? ex.getMessage() : "Gagal login. Periksa backend.");
        });
        new Thread(task, "login-thread").start();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }
}
