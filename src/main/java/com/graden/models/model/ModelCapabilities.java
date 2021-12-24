package com.graden.models.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ModelCapabilities {

    private String modelName;
    private boolean vision;
    private boolean tools;
    private boolean thinking;
    private boolean embedding;
    private Set<String> raw;

    private long checkedAt;

    public ModelCapabilities() {
        this.raw = new LinkedHashSet<>();
    }

    public ModelCapabilities(String modelName) {
        this();
        this.modelName = modelName;
        this.checkedAt = System.currentTimeMillis();
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public boolean isVision() {
        return vision;
    }

    public void setVision(boolean vision) {
        this.vision = vision;
    }

    public boolean isTools() {
        return tools;
    }

    public void setTools(boolean tools) {
        this.tools = tools;
    }

    public boolean isThinking() {
        return thinking;
    }

    public void setThinking(boolean thinking) {
        this.thinking = thinking;
    }

    public boolean isEmbedding() {
        return embedding;
    }

    public void setEmbedding(boolean embedding) {
        this.embedding = embedding;
    }

    public Set<String> getRaw() {
        return raw;
    }

    public void setRaw(Set<String> raw) {
        this.raw = raw != null ? raw : new LinkedHashSet<>();
    }

    public long getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(long checkedAt) {
        this.checkedAt = checkedAt;
    }

    public boolean isExpired(long ttlMs) {
        return System.currentTimeMillis() - checkedAt > ttlMs;
    }

    public static ModelCapabilities fromRawCapabilities(String modelName, Set<String> rawCaps) {
        ModelCapabilities caps = new ModelCapabilities(modelName);
        if (rawCaps == null) return caps;

        Set<String> lower = new LinkedHashSet<>();
        for (String c : rawCaps) {
            if (c != null) lower.add(c.trim().toLowerCase());
        }
        caps.raw = Collections.unmodifiableSet(lower);

        caps.vision = lower.contains("vision") || lower.contains("multimodal");
        caps.tools = lower.contains("tools") || lower.contains("function calling");
        caps.thinking = lower.contains("thinking");
        caps.embedding = lower.contains("embedding") || lower.contains("embeddings");

        return caps;
    }

    @Override
    public String toString() {
        return "ModelCapabilities{model=" + modelName
                + ", vision=" + vision + ", tools=" + tools
                + ", thinking=" + thinking + ", embedding=" + embedding + "}";
    }
}
