package com.graden.models.manager;

import com.graden.models.model.OllamaModel;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Static utility that resolves model capabilities (Vision, Tools, Code) and
 * parameter-size ranges. Prefers {@link OllamaModel#getBadges()} as primary
 * source; falls back to curated family-name heuristics when badges are empty.
 * <p>
 * This helper has no state and no instances — use only the public static methods.
 */
public final class ModelCapabilityResolver {

    public enum SizeRange {
        LT_2B,
        B2_7,
        B7_13,
        B13_30,
        B30_PLUS,
        UNKNOWN
    }

    // ------------------------------------------------------------------
    // Curated family sets (already lowercased)
    // ------------------------------------------------------------------

    private static final Set<String> VISION_FAMILIES = Collections.unmodifiableSet(new HashSet<>() {{
        add("llava");
        add("bakllava");
        add("moondream");
        add("minicpm-v");
        add("llama3.2-vision");
        add("llama3.2-vis");
        add("qwen2-vl");
        add("qwen2.5vl");
        add("qwen2.5-vl");
        add("granite3.2-vision");
        add("gemma3");
        add("llama4");
    }});

    private static final Set<String> TOOLS_FAMILIES = Collections.unmodifiableSet(new HashSet<>() {{
        add("llama3.1");
        add("llama3.2");
        add("llama3.3");
        add("llama4");
        add("qwen2.5");
        add("qwen3");
        add("mistral-nemo");
        add("mistral-small");
        add("mistral-large");
        add("command-r");
        add("firefunction");
        add("nemotron");
        add("hermes3");
        add("granite3");
    }});

    private static final Set<String> CODE_FAMILIES = Collections.unmodifiableSet(new HashSet<>() {{
        add("codellama");
        add("starcoder");
        add("qwen2.5-coder");
        add("qwen-coder");
        add("deepseek-coder");
        add("deepseek-coder-v2");
        add("granite-code");
        add("stable-code");
        add("codegemma");
        add("codestral");
        add("codeqwen");
        add("phind-codellama");
        add("magicoder");
        add("wizardcoder");
        add("yi-coder");
    }});

    // ------------------------------------------------------------------
    // Non-instantiable
    // ------------------------------------------------------------------

    private ModelCapabilityResolver() {
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Returns true if the model has vision capabilities. */
    public static boolean hasVision(OllamaModel model) {
        if (model == null || model.getName() == null) return false;
        List<String> badges = model.getBadges();
        if (badges != null && !badges.isEmpty()) {
            if (badgesContain(badges, "vision")) return true;
        }
        return nameMatchesAnyFamily(model.getName().toLowerCase(), VISION_FAMILIES);
    }

    /** Returns true if the model supports tool/function calling. */
    public static boolean hasTools(OllamaModel model) {
        if (model == null || model.getName() == null) return false;
        List<String> badges = model.getBadges();
        if (badges != null && !badges.isEmpty()) {
            if (badgesContain(badges, "tool") || badgesContain(badges, "tools")) return true;
        }
        return nameMatchesAnyFamily(model.getName().toLowerCase(), TOOLS_FAMILIES);
    }

    /** Returns true if the model is code-oriented. */
    public static boolean hasCode(OllamaModel model) {
        if (model == null || model.getName() == null) return false;
        List<String> badges = model.getBadges();
        if (badges != null && !badges.isEmpty()) {
            if (badgesContain(badges, "code") || badgesContain(badges, "coding")) return true;
        }
        return nameMatchesAnyFamily(model.getName().toLowerCase(), CODE_FAMILIES);
    }

    /**
     * Resolves the parameter-count range of the model.
     * Uses {@link ModelManager#extractParameterCount(OllamaModel)}.
     * Returns {@link SizeRange#UNKNOWN} if the count cannot be determined.
     */
    public static SizeRange resolveSize(OllamaModel model) {
        if (model == null || model.getName() == null) return SizeRange.UNKNOWN;
        double b = ModelManager.extractParameterCount(model);
        if (b <= 0.0) return SizeRange.UNKNOWN;
        if (b < 2.0)  return SizeRange.LT_2B;
        if (b < 7.0)  return SizeRange.B2_7;
        if (b < 13.0) return SizeRange.B7_13;
        if (b < 30.0) return SizeRange.B13_30;
        return SizeRange.B30_PLUS;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    static boolean badgesContain(List<String> badges, String needle) {
        String lower = needle.toLowerCase();
        return badges.stream().anyMatch(b -> b != null && b.toLowerCase().contains(lower));
    }

    static boolean nameMatchesAnyFamily(String nameLower, Set<String> familyRoots) {
        return familyRoots.stream().anyMatch(nameLower::contains);
    }
}
