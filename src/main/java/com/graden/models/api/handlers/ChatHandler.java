package com.graden.models.api.handlers;

import com.graden.models.App;
import com.graden.models.manager.OllamaManager;
import com.graden.models.model.StreamRequest;
import io.github.ollama4j.models.generate.OllamaStreamHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ChatHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            JsonUtil.sendError(exchange, 405, "Method not allowed");
            return;
        }

        String body = JsonUtil.readBody(exchange);

        String model = extractField(body, "model");
        String prompt = extractField(body, "prompt");
        String system = extractField(body, "system");
        boolean stream = "true".equals(extractField(body, "stream"));

        if (model == null || model.isBlank()) {
            JsonUtil.sendError(exchange, 400, "Missing 'model' field");
            return;
        }
        if (prompt == null || prompt.isBlank()) {
            JsonUtil.sendError(exchange, 400, "Missing 'prompt' field");
            return;
        }

        if (stream) {
            handleStream(exchange, model, prompt, system);
        } else {
            handleNonStream(exchange, model, prompt, system);
        }
    }

    private void handleNonStream(HttpExchange exchange, String model, String prompt, String system) throws IOException {
        StringBuilder fullContent = new StringBuilder();
        CompletableFuture<Void> done = new CompletableFuture<>();

        StreamRequest req = StreamRequest.of(model, prompt)
                .systemPrompt(system)
                .streamHandler(messagePart -> fullContent.append(messagePart))
                .onComplete(event -> done.complete(null));

        App.getExecutorService().submit(() -> {
            try {
                OllamaManager.getInstance().askModelStream(req);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        });

        try {
            done.get(300, TimeUnit.SECONDS);
        } catch (Exception e) {
            JsonUtil.sendError(exchange, 500, "Request failed: " + e.getMessage());
            return;
        }

        String json = "{\"model\":\"" + JsonUtil.escape(model) +
                "\",\"response\":\"" + JsonUtil.escape(fullContent.toString()) + "\"}";
        JsonUtil.sendOk(exchange, json);
    }

    private void handleStream(HttpExchange exchange, String model, String prompt, String system) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();

        StreamRequest req = StreamRequest.of(model, prompt)
                .systemPrompt(system)
                .streamHandler(new OllamaStreamHandler() {
                    @Override
                    public void accept(String messagePart) {
                        try {
                            String line = "data: " + messagePart + "\n\n";
                            os.write(line.getBytes(StandardCharsets.UTF_8));
                            os.flush();
                        } catch (IOException ignored) {}
                    }
                })
                .onComplete(event -> {
                    try {
                        os.write("event: done\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        os.close();
                    } catch (IOException ignored) {}
                });

        App.getExecutorService().submit(() -> {
            try {
                OllamaManager.getInstance().askModelStream(req);
            } catch (Exception e) {
                try {
                    os.write(("event: error\ndata: " + e.getMessage() + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException ignored) {}
            }
        });
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
