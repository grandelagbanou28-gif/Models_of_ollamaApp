package com.graden.models.manager;

/**
 * Centralized scraping selectors for ollama.com.
 * <p>
 * Each {@code String[]} declares the CSS selectors that the crawler will try
 * in priority order. The first selector returning a non-empty match wins; the
 * remaining ones act as fallbacks when ollama.com tweaks its markup.
 * <p>
 * Reference URLs validated manually:
 * <ul>
 *   <li>{@code https://ollama.com/library?sort=popular&page=1} — library listing</li>
 *   <li>{@code https://ollama.com/library/llama3} — single-model details</li>
 * </ul>
 * Last verified: 2026-05-17.
 */
public final class ScrapingSelectors {

    private ScrapingSelectors() {
        // utility class
    }

    // --- Endpoints --------------------------------------------------------
    public static final String LIBRARY_URL = "https://ollama.com/library";
    public static final String LIBRARY_PAGE_FMT = "https://ollama.com/library?sort=popular&page=%d";
    public static final String MODEL_URL_FMT = "https://ollama.com/library/%s";

    // --- HTTP knobs -------------------------------------------------------
    public static final String USER_AGENT = "GradenModels/1.0 (+https://github.com/gradenmodels/GradenModels)";
    public static final int CONNECT_TIMEOUT_MS = 8_000;
    public static final int READ_TIMEOUT_MS = 15_000;

    // --- Library listing --------------------------------------------------
    /** Anchors leading to {@code /library/<model>} on the catalog pages. */
    public static final String[] MODEL_LINK_SELECTORS = {
            "li a[href^='/library/']",
            "a[href^='/library/']",
            "[x-test-model] a",
            "main a[href*='/library/']"
    };

    // --- Per-model page ---------------------------------------------------
    public static final String[] DESCRIPTION_SELECTORS = {
            "#summary-content",
            "[x-test-summary]",
            "section p.text-neutral-700",
            "main p"
    };

    public static final String[] PULL_COUNT_SELECTORS = {
            "span[x-test-pull-count]",
            "[x-test-pull-count]",
            "span[title*='pulls']"
    };

    public static final String[] UPDATED_SELECTORS = {
            "span[x-test-updated]",
            "[x-test-updated]",
            "span[title*='ago']"
    };

    public static final String[] TAGS_CONTAINER_SELECTORS = {
            "div.min-w-full.divide-y",
            "section[aria-label='Tags']",
            "div[role='table']"
    };

    public static final String[] TAG_ROW_SELECTORS = {
            "div.hidden.sm\\:grid",
            "div[role='row']",
            "li[x-test-tag]"
    };

    public static final String[] BADGES_CONTAINER_SELECTORS = {
            "div.flex.flex-wrap.gap-2",
            "div.flex.flex-wrap.space-x-2",
            "[x-test-capabilities]",
            "div[data-testid='badges']"
    };

    public static final String[] README_SELECTORS = {
            "#readme",
            ".prose",
            "article"
    };
}
