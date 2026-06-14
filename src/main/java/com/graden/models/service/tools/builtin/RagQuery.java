package com.graden.models.service.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graden.models.manager.ConfigManager;
import com.graden.models.manager.RagManager;
import com.graden.models.model.RagResult;
import com.graden.models.service.tools.Tool;
import com.graden.models.service.tools.ToolDefinition;
import com.graden.models.service.tools.ToolResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RagQuery implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of(
                "type", "string",
                "description", "The search query to look up in your knowledge base."
        ));
        properties.put("collection", Map.of(
                "type", "string",
                "description", "Optional: name of the collection to search in (e.g. 'General'). If omitted, searches all collections."
        ));
        properties.put("top_k", Map.of(
                "type", "integer",
                "description", "Number of results to return (default: 3, max: 10)."
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("query"));

        return new ToolDefinition(
                "rag_query",
                "Searches your local knowledge base for documents related to the query. Use this when you need to find information that the user has previously uploaded or indexed (PDFs, text files, notes, etc.).",
                parameters);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) throws Exception {
        long start = System.currentTimeMillis();

        String query = (String) args.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.failure("Missing required parameter: query", 0);
        }

        RagManager rag = RagManager.getInstance();
        rag.initialize();

        int topK = 3;
        if (args.containsKey("top_k") && args.get("top_k") instanceof Number) {
            topK = Math.max(1, Math.min(((Number) args.get("top_k")).intValue(), 10));
        }

        Set<String> collectionIds = null;
        if (args.containsKey("collection") && args.get("collection") instanceof String) {
            String colName = (String) args.get("collection");
            if (!colName.isBlank()) {
                collectionIds = rag.getCollections().stream()
                        .filter(c -> c.getName().equalsIgnoreCase(colName.trim()))
                        .map(com.graden.models.model.RagCollection::getId)
                        .collect(Collectors.toSet());
            }
        }

        List<RagResult> results = rag.queryContext(query.trim(), topK, collectionIds);

        List<Map<String, Object>> hits = results.stream()
                .map(r -> Map.<String, Object>of(
                        "content", r.getContent(),
                        "source", r.getFileName(),
                        "score", String.format("%.3f", r.getScore())
                ))
                .collect(Collectors.toList());

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("query", query);
        output.put("results_count", hits.size());
        output.put("results", hits);

        return ToolResult.success(MAPPER.writeValueAsString(output), System.currentTimeMillis() - start);
    }
}
