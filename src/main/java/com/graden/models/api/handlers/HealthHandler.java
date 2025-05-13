package com.graden.models.api.handlers;

import com.graden.models.App;
import com.graden.models.manager.OllamaServiceManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public class HealthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            JsonUtil.sendError(exchange, 405, "Method not allowed");
            return;
        }
        boolean ollamaRunning = false;
        try {
            ollamaRunning = OllamaServiceManager.getInstance().isRunning();
        } catch (Exception ignored) {}
        String json = "{\"status\":\"ok\",\"ollama_running\":" + ollamaRunning + ",\"app\":\"Graden Models\"}";
        JsonUtil.sendOk(exchange, json);
    }
}
