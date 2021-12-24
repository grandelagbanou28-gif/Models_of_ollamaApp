package com.graden.models.service.tools;

import com.graden.models.model.ToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ToolExecutor {

    private static final Logger LOGGER = Logger.getLogger(ToolExecutor.class.getName());
    private static final long TOOL_TIMEOUT_MS = 30_000;

    public static List<ToolCall> executeAll(List<ToolCall> calls) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ToolCall call : calls) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                executeOne(call);
            });
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .get(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Tool execution timed out or was interrupted", e);
            for (ToolCall call : calls) {
                if (call.getResult() == null) {
                    call.setFailed(true);
                    call.setResult("{\"error\": \"Tool execution timeout\"}");
                }
            }
        }

        return calls;
    }

    private static void executeOne(ToolCall call) {
        long start = System.currentTimeMillis();
        try {
            Tool tool = ToolRegistry.getInstance().lookup(call.getName());
            if (tool == null) {
                call.setFailed(true);
                call.setResult("{\"error\": \"Unknown tool: " + call.getName() + "\"}");
                call.setDurationMs(System.currentTimeMillis() - start);
                return;
            }

            Map<String, Object> args = call.getArgs() != null ? call.getArgs() : Map.of();
            ToolResult result = tool.execute(args);
            call.setResult(result.toString());
            call.setFailed(!result.isSuccess());
            call.setDurationMs(result.getDurationMs() > 0 ? result.getDurationMs()
                    : System.currentTimeMillis() - start);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Tool " + call.getName() + " failed", e);
            call.setFailed(true);
            call.setResult("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            call.setDurationMs(System.currentTimeMillis() - start);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "unknown error";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
