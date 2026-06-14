package com.graden.models.service.tools;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolDefinition {

    private String name;
    private String description;
    private Map<String, Object> parameters;

    public ToolDefinition() {
    }

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> toOllamaFormat() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        if (description != null) {
            function.put("description", description);
        }
        if (parameters != null) {
            function.put("parameters", parameters);
        }

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);

        return tool;
    }
}
