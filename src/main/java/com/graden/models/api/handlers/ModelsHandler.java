package com.graden.models.api.handlers;

import com.graden.models.manager.ModelManager;
import com.graden.models.model.OllamaModel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ModelsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("DELETE".equals(method) && path.matches("/api/models/[^/]+")) {
            handleDelete(exchange);
            return;
        }

        if (!"GET".equals(method)) {
            JsonUtil.sendError(exchange, 405, "Method not allowed");
            return;
        }

        if (path.equals("/api/models/local")) {
            handleLocal(exchange);
        } else {
            handleLibrary(exchange);
        }
    }

    private void handleLibrary(HttpExchange exchange) throws IOException {
        ModelManager mm = ModelManager.getInstance();
        List<OllamaModel> all = mm.getAvailableModels();
        String json = toJsonArray(all);
        JsonUtil.sendOk(exchange, json);
    }

    private void handleLocal(HttpExchange exchange) throws IOException {
        ModelManager mm = ModelManager.getInstance();
        List<OllamaModel> local = mm.getLocalModels();
        String json = toJsonArray(local);
        JsonUtil.sendOk(exchange, json);
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        String name = exchange.getRequestURI().getPath().replace("/api/models/", "");
        com.graden.models.manager.OllamaManager om = com.graden.models.manager.OllamaManager.getInstance();
        try {
            om.deleteModel(name);
            ModelManager.getInstance().refreshLocalModels();
            JsonUtil.sendOk(exchange, "{\"deleted\":\"" + JsonUtil.escape(name) + "\"}");
        } catch (Exception e) {
            JsonUtil.sendError(exchange, 500, "Failed to delete: " + e.getMessage());
        }
    }

    private String toJsonArray(List<OllamaModel> models) {
        String items = models.stream()
                .map(this::toJson)
                .collect(Collectors.joining(","));
        return "[" + items + "]";
    }

    private String toJson(OllamaModel m) {
        return "{\"name\":\"" + JsonUtil.escape(m.getName()) +
                "\",\"tag\":\"" + JsonUtil.escape(m.getTag()) +
                "\",\"size\":\"" + JsonUtil.escape(m.getSize()) +
                "\",\"description\":\"" + JsonUtil.escape(m.getDescription()) +
                "\",\"pull_count\":\"" + JsonUtil.escape(m.getPullCount()) +
                "\"}";
    }
}
