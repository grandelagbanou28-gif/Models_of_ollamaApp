package com.graden.models.manager;

import java.util.prefs.Preferences;

public class ConfigManager {

    private static ConfigManager instance;
    private final Preferences prefs;

    private static final String KEY_OLLAMA_HOST = "ollama_host";
    private static final String KEY_THEME = "app_theme";
    private static final String DEFAULT_HOST = "http://127.0.0.1:11434";
    private static final String DEFAULT_THEME = "dark"; // dark or light

    private ConfigManager() {
        prefs = Preferences.userNodeForPackage(ConfigManager.class);
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public String getOllamaHost() {
        return prefs.get(KEY_OLLAMA_HOST, DEFAULT_HOST);
    }

    public void setOllamaHost(String host) {
        prefs.put(KEY_OLLAMA_HOST, host);
    }

    public String getTheme() {
        return prefs.get(KEY_THEME, DEFAULT_THEME);
    }

    public void setTheme(String theme) {
        prefs.put(KEY_THEME, theme);
    }

    private static final String KEY_API_TIMEOUT = "api_timeout_seconds";
    private static final int DEFAULT_API_TIMEOUT = 120; // Default 120 seconds (useful for slow multimodal models)

    public int getApiTimeout() {
        return prefs.getInt(KEY_API_TIMEOUT, DEFAULT_API_TIMEOUT);
    }

    public void setApiTimeout(int seconds) {
        prefs.putInt(KEY_API_TIMEOUT, seconds);
    }

    private static final String KEY_LANGUAGE = "app_language";
    private static final String DEFAULT_LANGUAGE = "fr"; // Default to French

    public String getLanguage() {
        return prefs.get(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    public void setLanguage(String language) {
        prefs.put(KEY_LANGUAGE, language);
        try {
            prefs.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────── Supabase ──────────────────────────────
    private static final String KEY_SUPABASE_URL = "supabase_url";
    private static final String KEY_SUPABASE_ANON_KEY = "supabase_anon_key";

    public String getSupabaseUrl() {
        return prefs.get(KEY_SUPABASE_URL, "");
    }

    public void setSupabaseUrl(String url) {
        prefs.put(KEY_SUPABASE_URL, url);
    }

    public String getSupabaseAnonKey() {
        return prefs.get(KEY_SUPABASE_ANON_KEY, "");
    }

    public void setSupabaseAnonKey(String key) {
        prefs.put(KEY_SUPABASE_ANON_KEY, key);
    }

    // ─────────────────────────── Google OAuth ──────────────────────────────
    private static final String KEY_GOOGLE_CLIENT_ID = "google_client_id";
    private static final String KEY_GOOGLE_CLIENT_SECRET = "google_client_secret";

    public String getGoogleClientId() {
        return prefs.get(KEY_GOOGLE_CLIENT_ID, "");
    }

    public void setGoogleClientId(String id) {
        prefs.put(KEY_GOOGLE_CLIENT_ID, id);
    }

    public String getGoogleClientSecret() {
        return prefs.get(KEY_GOOGLE_CLIENT_SECRET, "");
    }

    public void setGoogleClientSecret(String secret) {
        prefs.put(KEY_GOOGLE_CLIENT_SECRET, secret);
    }

    // ─────────────────────────── Last active session ───────────────────────
    private static final String KEY_LAST_ACTIVE_SESSION = "last_active_session_id";

    public String getLastActiveSessionId() {
        return prefs.get(KEY_LAST_ACTIVE_SESSION, "");
    }

    public void setLastActiveSessionId(String sessionId) {
        prefs.put(KEY_LAST_ACTIVE_SESSION, sessionId != null ? sessionId : "");
        try { prefs.flush(); } catch (Exception ignored) {}
    }

    // ─────────────────────────── First-run onboarding ──────────────────────
    private static final String KEY_FIRST_RUN_COMPLETED = "first_run_completed";

    /** True once the user has gone through (or skipped) the onboarding wizard. */
    public boolean isFirstRunCompleted() {
        return prefs.getBoolean(KEY_FIRST_RUN_COMPLETED, false);
    }

    public void setFirstRunCompleted(boolean completed) {
        prefs.putBoolean(KEY_FIRST_RUN_COMPLETED, completed);
        try {
            prefs.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────── RAG tuning ────────────────────────────────
    // Default minScore moved from 0.2 (too permissive — captured cross-language
    // noise) to 0.55 (balanced: precise within-language, still tolerant).
    // ─────────────────────────── Generic helpers ──────────────────────────
    public String getPreference(String key, String defaultValue) {
        return prefs.get(key, defaultValue);
    }

    public void setPreference(String key, String value) {
        prefs.put(key, value);
        try { prefs.flush(); } catch (Exception ignored) {}
    }

    private static final String KEY_RAG_MIN_SCORE = "rag_min_score";
    private static final String KEY_RAG_TOP_K = "rag_top_k";
    private static final double DEFAULT_RAG_MIN_SCORE = 0.55;
    private static final int DEFAULT_RAG_TOP_K = 5;

    public double getRagMinScore() {
        double v = prefs.getDouble(KEY_RAG_MIN_SCORE, DEFAULT_RAG_MIN_SCORE);
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    public void setRagMinScore(double score) {
        double clamped = Math.max(0.0, Math.min(1.0, score));
        prefs.putDouble(KEY_RAG_MIN_SCORE, clamped);
    }

    public int getRagTopK() {
        int v = prefs.getInt(KEY_RAG_TOP_K, DEFAULT_RAG_TOP_K);
        if (v < 1) return 1;
        if (v > 20) return 20;
        return v;
    }

    public void setRagTopK(int topK) {
        int clamped = Math.max(1, Math.min(20, topK));
        prefs.putInt(KEY_RAG_TOP_K, clamped);
    }
}
