package com.graden.models.service.tools;

public class ToolResult {

    private final boolean success;
    private final String content;
    private final long durationMs;
    private final String errorMessage;

    private ToolResult(boolean success, String content, long durationMs, String errorMessage) {
        this.success = success;
        this.content = content;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
    }

    public static ToolResult success(String content, long durationMs) {
        return new ToolResult(true, content, durationMs, null);
    }

    public static ToolResult failure(String errorMessage, long durationMs) {
        return new ToolResult(false, null, durationMs, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content != null ? content : (errorMessage != null ? "Error: " + errorMessage : "");
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return content;
        }
        return "{\"error\": \"" + (errorMessage != null ? errorMessage.replace("\"", "\\\"") : "unknown") + "\"}";
    }
}
