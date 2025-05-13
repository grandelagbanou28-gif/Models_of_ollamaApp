package com.graden.models.api.handlers;

import com.graden.models.App;
import com.graden.models.manager.OllamaManager;
import com.graden.models.manager.OllamaManager.ProgressCallback;
import com.graden.models.manager.ModelManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModelPullHandler implements HttpHandler {

    private static final Map<String, PullTask> downloads = new ConcurrentHashMap<>();

    static class PullTask {
        final String id;
        final String modelName;
        volatile String status;
        volatile boolean done;
        volatile String error;

        PullTask(String id, String modelName) {
            this.id = id;
            this.modelName = modelName;
            this.status = "starting";
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // GET /api/models/pull/{id}  — check progress
        if ("GET".equals(method) && path.matches("/api/models/pull/[^/]+")) {
            String id = path.replace("/api/models/pull/", "");
            PullTask task = downloads.get(id);
            if (task == null) {
                JsonUtil.sendError(exchange, 404, "Download task not found");
                return;
            }
            String json = String.format(
                "{\"id\":\"%s\",\"model\":\"%s\",\"status\":\"%s\",\"done\":%b,\"error\":\"%s\"}",
                task.id, JsonUtil.escape(task.modelName),
                JsonUtil.escape(task.status), task.done,
                JsonUtil.escape(task.error != null ? task.error : "")
            );
            JsonUtil.sendOk(exchange, json);
            return;
        }

        if (!"POST".equals(method)) {
            JsonUtil.sendError(exchange, 405, "Method not allowed");
            return;
        }

        // Parse "model" field (e.g. "llama3.2:3b" or "llama3.2")
        String body = JsonUtil.readBody(exchange);
        String modelName = extractField(body, "model");
        if (modelName == null || modelName.isBlank()) {
            JsonUtil.sendError(exchange, 400, "Missing 'model' field");
            return;
        }

        // Split name:tag
        String namePart = modelName;
        String tagPart = "latest";
        if (modelName.contains(":")) {
            namePart = modelName.substring(0, modelName.indexOf(':'));
            tagPart = modelName.substring(modelName.indexOf(':') + 1);
        }
        // Also allow explicit "tag" field
        String explicitTag = extractField(body, "tag");
        if (explicitTag != null && !explicitTag.isBlank()) tagPart = explicitTag;

        String id = UUID.randomUUID().toString().substring(0, 8);
        PullTask task = new PullTask(id, modelName);
        downloads.put(id, task);

        String finalName = namePart;
        String finalTag = tagPart;
        App.getExecutorService().submit(() -> {
            try {
                OllamaManager.getInstance().pullModel(finalName, finalTag, new ProgressCallback() {
                    @Override
                    public void onProgress(double progress, String status) {
                        task.status = status;
                    }
                });
                task.status = "completed";
                task.done = true;
                ModelManager.getInstance().refreshLocalModels();
            } catch (Exception e) {
                task.status = "error";
                task.error = e.getMessage();
                task.done = true;
            }
            // Clean up old tasks after 5 min
            App.getExecutorService().submit(() -> {
                try { Thread.sleep(300_000); } catch (InterruptedException ignored) {}
                downloads.remove(id);
            });
        });

        String json = String.format(
            "{\"id\":\"%s\",\"model\":\"%s\",\"status\":\"started\"}",
            id, JsonUtil.escape(modelName)
        );
        JsonUtil.sendOk(exchange, json);
    }

    private String extractField(String body, String field) {
        if (body == null || body.isBlank()) return null;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(body);
            return node.has(field) ? node.get(field).asText() : null;
        } catch (Exception e) {
            String key = "\"" + field + "\"";
            int idx = body.indexOf(key);
            if (idx < 0) return null;
            int start = body.indexOf("\"", idx + key.length() + 1) + 1;
            int end = body.indexOf("\"", start);
            return (start > 0 && end > start) ? body.substring(start, end) : null;
        }
    }
}
