package com.graden.models.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
    private String role; // "user" or "assistant"
    private String content;
    private String timestamp;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> images; // Base64-encoded image data (null when text-only)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<AttachedDocument> attachedDocuments; // Inlined PDF/TXT/MD content (null when none)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ToolCall> toolCalls; // Tool calls made by this assistant message (null when none)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String toolCallId; // Links a tool-result message back to the originating tool call (null for non-tool messages)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private GenerationMetrics generationMetrics; // Metrics from Ollama generation (null for user/tool messages)

    public ChatMessage() {
        // Default constructor for Jackson
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = LocalDateTime.now().toString();
    }

    public ChatMessage(String role, String content, List<String> images) {
        this(role, content);
        this.images = (images != null && !images.isEmpty()) ? images : null;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }

    public List<AttachedDocument> getAttachedDocuments() {
        return attachedDocuments;
    }

    public void setAttachedDocuments(List<AttachedDocument> attachedDocuments) {
        this.attachedDocuments = (attachedDocuments != null && !attachedDocuments.isEmpty())
                ? attachedDocuments : null;
    }

    public boolean hasAttachedDocuments() {
        return attachedDocuments != null && !attachedDocuments.isEmpty();
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = (toolCalls != null && !toolCalls.isEmpty()) ? toolCalls : null;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public boolean isToolResult() {
        return "tool".equals(role);
    }

    public GenerationMetrics getGenerationMetrics() {
        return generationMetrics;
    }

    public void setGenerationMetrics(GenerationMetrics generationMetrics) {
        this.generationMetrics = generationMetrics;
    }

    public boolean hasGenerationMetrics() {
        return generationMetrics != null;
    }
}
