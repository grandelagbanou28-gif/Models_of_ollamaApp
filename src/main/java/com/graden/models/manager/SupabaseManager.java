package com.graden.models.manager;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SupabaseManager {

    private static SupabaseManager instance;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> sessionStore = new ConcurrentHashMap<>();
    private String accessToken;
    private String userEmail;
    private boolean loggedIn;

    private SupabaseManager() {}

    public static synchronized SupabaseManager getInstance() {
        if (instance == null) instance = new SupabaseManager();
        return instance;
    }

    public boolean isConfigured() {
        ConfigManager cfg = ConfigManager.getInstance();
        return cfg.getSupabaseUrl() != null && !cfg.getSupabaseUrl().isEmpty()
            && cfg.getSupabaseAnonKey() != null && !cfg.getSupabaseAnonKey().isEmpty();
    }

    private String getApiUrl() {
        return ConfigManager.getInstance().getSupabaseUrl();
    }

    private String getAnonKey() {
        return ConfigManager.getInstance().getSupabaseAnonKey();
    }

    public String getAccessToken() { return accessToken; }
    public String getUserEmail() { return userEmail; }
    public boolean isLoggedIn() { return loggedIn; }

    public void logout() {
        accessToken = null;
        userEmail = null;
        loggedIn = false;
    }

    public CompletableFuture<String> signUp(String email, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = mapper.writeValueAsString(Map.of("email", email.trim(), "password", password));
                HttpURLConnection conn = post("/auth/v1/signup", body);
                int code = conn.getResponseCode();
                if (code == 200 || code == 201) {
                    Map<?, ?> resp = mapper.readValue(conn.getInputStream(), Map.class);
                    Object user = resp.get("user");
                    if (user instanceof Map) {
                        String id = (String) ((Map<?, ?>) user).get("id");
                        if (id != null) return null;
                    }
                    return "Signup succeeded but user data missing";
                }
                String err = conn.getErrorStream() != null
                    ? new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
                    : "HTTP " + code;
                return "Signup failed: " + err;
            } catch (Exception e) {
                return "Signup error: " + e.getMessage();
            }
        });
    }

    public CompletableFuture<String> signIn(String email, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = mapper.writeValueAsString(Map.of("email", email.trim(), "password", password, "gotrue_meta_security", Map.of()));
                HttpURLConnection conn = post("/auth/v1/token?grant_type=password", body);
                int code = conn.getResponseCode();
                if (code == 200) {
                    Map<?, ?> resp = mapper.readValue(conn.getInputStream(), Map.class);
                    accessToken = (String) resp.get("access_token");
                    Object user = resp.get("user");
                    if (user instanceof Map) {
                        userEmail = (String) ((Map<?, ?>) user).get("email");
                    }
                    if (accessToken != null) {
                        loggedIn = true;
                        return null;
                    }
                    return "Login: no access token";
                }
                String err = conn.getErrorStream() != null
                    ? new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
                    : "HTTP " + code;
                return "Login failed: " + err;
            } catch (Exception e) {
                return "Login error: " + e.getMessage();
            }
        });
    }

    public CompletableFuture<Void> signInWithGoogle() {
        return CompletableFuture.runAsync(() -> {
            try {
                String url = getApiUrl() + "/auth/v1/authorize?provider=google&redirect_to=http://localhost:PORT";
                java.awt.Desktop.getDesktop().browse(new URI(url));
                // For full flow we'd need local server like GoogleAuthService
                // For now this opens the browser; user completes auth there
            } catch (Exception e) {
                throw new RuntimeException("Failed to open Google auth URL", e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<String> exchangeGoogleCode(String code, String redirectUri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> body = Map.of(
                    "code", code,
                    "redirect_uri", redirectUri
                );
                HttpURLConnection conn = post("/auth/v1/token?grant_type=authorization_code", mapper.writeValueAsString(body));
                int httpCode = conn.getResponseCode();
                if (httpCode == 200) {
                    Map<String, Object> resp = mapper.readValue(conn.getInputStream(), Map.class);
                    accessToken = (String) resp.get("access_token");
                    Map<String, Object> user = (Map<String, Object>) resp.get("user");
                    if (user != null) userEmail = (String) user.get("email");
                    if (accessToken != null) {
                        loggedIn = true;
                        return null;
                    }
                    return "No access token";
                }
                String err = conn.getErrorStream() != null
                    ? new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
                    : "HTTP " + httpCode;
                return "Google code exchange failed: " + err;
            } catch (Exception e) {
                return "Google exchange error: " + e.getMessage();
            }
        });
    }

    private HttpURLConnection post(String path, String jsonBody) throws Exception {
        URL url = new URI(getApiUrl() + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("apikey", getAnonKey());
        conn.setRequestProperty("Authorization", "Bearer " + getAnonKey());
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }
}
