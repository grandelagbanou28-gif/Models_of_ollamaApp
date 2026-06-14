package com.graden.models.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads prompt templates from {@code /prompts/<name>.txt} on the classpath
 * and caches them in memory.
 *
 * <p>Templates live as plain text files so they can be tuned without
 * recompiling. Simple {@code {placeholder}} interpolation is supported via
 * {@link #render(String, Map)}.
 *
 * <p>If a template is missing on disk, the loader returns the registered
 * fallback (passed by the caller) instead of failing — keeps the app
 * usable if someone deletes or misnames a file.
 */
public final class PromptTemplates {

    private static final Logger LOGGER = Logger.getLogger(PromptTemplates.class.getName());
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PromptTemplates() {
    }

    /** Load the raw text of a template, falling back to {@code fallback} if missing. */
    public static String load(String name, String fallback) {
        return CACHE.computeIfAbsent(name, n -> readResource(n, fallback));
    }

    /** Load and substitute simple {@code {key}} placeholders. */
    public static String render(String name, String fallback, Map<String, String> vars) {
        String tpl = load(name, fallback);
        if (vars == null || vars.isEmpty()) return tpl;
        String out = tpl;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    /** Clears the in-memory cache. Useful if templates are edited at runtime. */
    public static void reload() {
        CACHE.clear();
    }

    private static String readResource(String name, String fallback) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream in = PromptTemplates.class.getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.warning("Prompt template not found on classpath: " + path + " — using fallback");
                return fallback;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read prompt template " + path + " — using fallback", e);
            return fallback;
        }
    }
}
