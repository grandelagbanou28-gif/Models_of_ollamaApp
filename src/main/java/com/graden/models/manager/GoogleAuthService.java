package com.graden.models.manager;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

public class GoogleAuthService {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String PEOPLE_API_URL = "https://people.googleapis.com/v1/people/me?personFields=emailAddresses";
    private static final String SCOPE = "openid%20email%20profile";

    private static GoogleAuthService instance;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GoogleAuthService() {}

    public static synchronized GoogleAuthService getInstance() {
        if (instance == null) instance = new GoogleAuthService();
        return instance;
    }

    public boolean isConfigured() {
        ConfigManager cfg = ConfigManager.getInstance();
        String id = cfg.getGoogleClientId();
        String secret = cfg.getGoogleClientSecret();
        return id != null && !id.isEmpty() && secret != null && !secret.isEmpty();
    }

    public CompletableFuture<String> authenticate() {
        CompletableFuture<String> future = new CompletableFuture<>();

        if (!isConfigured()) {
            future.completeExceptionally(new IllegalStateException(
                    "Google OAuth is not configured. Add your Client ID and Secret in Settings."));
            return future;
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            int port = server.getAddress().getPort();
            String redirectUri = "http://localhost:" + port + "/callback";

            StringBuilder authUrlBuilder = new StringBuilder(AUTH_URL)
                    .append("?client_id=").append(URLEncoder.encode(
                            ConfigManager.getInstance().getGoogleClientId(), StandardCharsets.UTF_8))
                    .append("&redirect_uri=").append(URLEncoder.encode(redirectUri, StandardCharsets.UTF_8))
                    .append("&response_type=code")
                    .append("&scope=").append(SCOPE)
                    .append("&access_type=offline")
                    .append("&prompt=consent");

            String state = java.util.UUID.randomUUID().toString();
            authUrlBuilder.append("&state=").append(state);

            final String[] capturedCode = {null};
            CountDownLatch latch = new CountDownLatch(1);

            server.createContext("/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String html = "<html><body><p>Authentication complete! You can close this window.</p></body></html>";

                if (query != null && query.contains("code=") && query.contains("state=" + state)) {
                    for (String param : query.split("&")) {
                        String[] kv = param.split("=", 2);
                        if (kv.length == 2 && "code".equals(kv[0])) {
                            capturedCode[0] = kv[1];
                            break;
                        }
                    }
                }

                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                latch.countDown();
            });

            server.setExecutor(null);
            server.start();

            URI uri = new URI(authUrlBuilder.toString());
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                System.err.println("Cannot open browser. Open manually: " + uri);
            }

            CompletableFuture<Boolean> timeout = CompletableFuture.supplyAsync(() -> {
                try {
                    return latch.await(120, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    return false;
                }
            });

            timeout.thenAcceptAsync(ok -> {
                server.stop(0);
                if (!ok || capturedCode[0] == null) {
                    future.completeExceptionally(new RuntimeException(
                            "Google authentication timed out or was cancelled."));
                    return;
                }
                try {
                    String email = exchangeCodeForEmail(capturedCode[0], redirectUri);
                    future.complete(email);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private String exchangeCodeForEmail(String code, String redirectUri) throws IOException {
        ConfigManager cfg = ConfigManager.getInstance();
        String body = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(cfg.getGoogleClientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(cfg.getGoogleClientSecret(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code";

        HttpURLConnection conn = (HttpURLConnection) new URL(TOKEN_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("Token exchange failed: HTTP " + status);
        }

        Map<?, ?> tokenResponse;
        try (InputStream is = conn.getInputStream()) {
            tokenResponse = objectMapper.readValue(is, Map.class);
        }

        String accessToken = (String) tokenResponse.get("access_token");
        if (accessToken == null) {
            throw new IOException("No access_token in response");
        }

        HttpURLConnection peopleConn = (HttpURLConnection) new URL(PEOPLE_API_URL).openConnection();
        peopleConn.setRequestProperty("Authorization", "Bearer " + accessToken);

        int peopleStatus = peopleConn.getResponseCode();
        if (peopleStatus != 200) {
            throw new IOException("People API failed: HTTP " + peopleStatus);
        }

        Map<?, ?> peopleResponse;
        try (InputStream is = peopleConn.getInputStream()) {
            peopleResponse = objectMapper.readValue(is, Map.class);
        }

        var emailAddresses = (java.util.List<?>) peopleResponse.get("emailAddresses");
        if (emailAddresses != null && !emailAddresses.isEmpty()) {
            var first = (Map<?, ?>) emailAddresses.get(0);
            String email = (String) first.get("value");
            if (email != null) return email;
        }

        throw new IOException("Could not retrieve email from Google");
    }
}
