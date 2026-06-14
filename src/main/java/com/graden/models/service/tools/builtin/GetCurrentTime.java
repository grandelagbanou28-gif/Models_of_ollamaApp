package com.graden.models.service.tools.builtin;

import com.graden.models.service.tools.Tool;
import com.graden.models.service.tools.ToolDefinition;
import com.graden.models.service.tools.ToolResult;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class GetCurrentTime implements Tool {

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("timezone", Map.of(
                "type", "string",
                "description", "IANA timezone name (e.g. 'America/New_York', 'Europe/Madrid'). If omitted, system default is used."
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);

        return new ToolDefinition(
                "get_current_time",
                "Returns the current date and time in ISO-8601 format. Optionally accepts a timezone parameter.",
                parameters);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) throws Exception {
        long start = System.currentTimeMillis();

        ZoneId zone = ZoneId.systemDefault();
        if (args.containsKey("timezone")) {
            String tz = (String) args.get("timezone");
            if (tz != null && !tz.isBlank()) {
                try {
                    zone = ZoneId.of(tz.trim());
                } catch (Exception e) {
                    zone = ZoneId.systemDefault();
                }
            }
        }

        ZonedDateTime now = ZonedDateTime.now(zone);
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("iso8601", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        resultMap.put("timezone", zone.getId());
        resultMap.put("timestamp_seconds", now.toEpochSecond());

        return ToolResult.success(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(resultMap), System.currentTimeMillis() - start);
    }
}
