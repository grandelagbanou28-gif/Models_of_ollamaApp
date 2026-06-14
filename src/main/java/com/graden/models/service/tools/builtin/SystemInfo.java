package com.graden.models.service.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graden.models.manager.HardwareManager;
import com.graden.models.service.tools.Tool;
import com.graden.models.service.tools.ToolDefinition;
import com.graden.models.service.tools.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

public class SystemInfo implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", new LinkedHashMap<>());

        return new ToolDefinition(
                "system_info",
                "Returns information about the user's hardware: RAM, VRAM, CPU, operating system, and architecture. Takes no arguments.",
                parameters);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) throws Exception {
        long start = System.currentTimeMillis();

        HardwareManager.HardwareStats stats = HardwareManager.getStats();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("ram_total_gb", String.format("%.2f", stats.getTotalRamGB()));
        info.put("ram_available_gb", String.format("%.2f", stats.totalRamBytes > 0
                ? (double) stats.availableRamBytes / (1024.0 * 1024.0 * 1024.0) : 0));
        info.put("vram_total_gb", String.format("%.2f", stats.getVramGB()));
        info.put("cpu", HardwareManager.getCpuDetails());
        info.put("os", HardwareManager.getOsDetails());
        info.put("unified_memory", stats.isUnifiedMemory);
        info.put("java_version", System.getProperty("java.version"));

        return ToolResult.success(MAPPER.writeValueAsString(info), System.currentTimeMillis() - start);
    }
}
