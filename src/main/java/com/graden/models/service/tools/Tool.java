package com.graden.models.service.tools;

import java.util.Map;

public interface Tool {

    ToolDefinition getDefinition();

    ToolResult execute(Map<String, Object> args) throws Exception;

    default boolean requiresApproval() {
        return false;
    }
}
