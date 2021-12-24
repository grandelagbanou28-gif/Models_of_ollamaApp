package com.graden.models.model;

import com.graden.models.service.tools.ToolDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import io.github.ollama4j.models.generate.OllamaStreamHandler;

public class StreamRequest {

    private String modelName;
    private String prompt;
    private List<String> images;
    private Map<String, Object> requestOptions;
    private String systemPrompt;
    private List<ToolDefinition> tools;
    private String jsonFormat;
    private List<ChatMessage> conversationHistory;
    private OllamaStreamHandler streamHandler;
    private Consumer<StreamCompleteEvent> onComplete;
    private int maxToolRounds = 8;

    public StreamRequest() {
    }

    public String getModelName() {
        return modelName;
    }

    public StreamRequest modelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public String getPrompt() {
        return prompt;
    }

    public StreamRequest prompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    public List<String> getImages() {
        return images;
    }

    public StreamRequest images(List<String> images) {
        this.images = images;
        return this;
    }

    public Map<String, Object> getRequestOptions() {
        return requestOptions;
    }

    public StreamRequest requestOptions(Map<String, Object> requestOptions) {
        this.requestOptions = requestOptions;
        return this;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public StreamRequest systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public StreamRequest tools(List<ToolDefinition> tools) {
        this.tools = (tools != null && !tools.isEmpty()) ? tools : null;
        return this;
    }

    public String getJsonFormat() {
        return jsonFormat;
    }

    public StreamRequest jsonFormat(String jsonFormat) {
        this.jsonFormat = (jsonFormat != null && !jsonFormat.isBlank()) ? jsonFormat : null;
        return this;
    }

    public List<ChatMessage> getConversationHistory() {
        return conversationHistory;
    }

    public StreamRequest conversationHistory(List<ChatMessage> conversationHistory) {
        this.conversationHistory = conversationHistory;
        return this;
    }

    public OllamaStreamHandler getStreamHandler() {
        return streamHandler;
    }

    public StreamRequest streamHandler(OllamaStreamHandler streamHandler) {
        this.streamHandler = streamHandler;
        return this;
    }

    public Consumer<StreamCompleteEvent> getOnComplete() {
        return onComplete;
    }

    public StreamRequest onComplete(Consumer<StreamCompleteEvent> onComplete) {
        this.onComplete = onComplete;
        return this;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public StreamRequest maxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
        return this;
    }

    public static StreamRequest of(String modelName, String prompt) {
        return new StreamRequest().modelName(modelName).prompt(prompt);
    }

    public record StreamCompleteEvent(
            GenerationMetrics metrics,
            List<ToolCall> toolCalls,
            String fullResponse) {

        public StreamCompleteEvent(GenerationMetrics metrics, List<ToolCall> toolCalls, String fullResponse) {
            this.metrics = metrics;
            this.toolCalls = toolCalls != null ? Collections.unmodifiableList(toolCalls) : null;
            this.fullResponse = fullResponse;
        }

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
