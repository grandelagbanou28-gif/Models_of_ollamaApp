package com.graden.models.service.tools;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ToolRegistry {

    private static final Logger LOGGER = Logger.getLogger(ToolRegistry.class.getName());

    private static ToolRegistry instance;

    private final Map<String, Tool> tools;
    private final List<String> enabledOrder;
    private volatile boolean builtinsRegistered = false;

    private ToolRegistry() {
        this.tools = new ConcurrentHashMap<>();
        this.enabledOrder = new CopyOnWriteArrayList<>();
    }

    public static synchronized ToolRegistry getInstance() {
        if (instance == null) {
            instance = new ToolRegistry();
        }
        return instance;
    }

    /**
     * Registers a tool. Idempotent: re-registering a tool with the same
     * name replaces its instance without duplicating it in the ordered
     * list (which would otherwise feed duplicate definitions to the model).
     */
    public void register(Tool tool) {
        String name = tool.getDefinition().getName();
        boolean isNew = !tools.containsKey(name);
        tools.put(name, tool);
        if (isNew) {
            enabledOrder.add(name);
            LOGGER.info("Registered tool: " + name);
        }
    }

    /**
     * Registers the built-in tools exactly once for the lifetime of the app.
     * Safe to call from every controller's {@code initialize()}.
     *
     * <p>Note: {@code rag_query} is intentionally NOT registered. RAG is
     * handled by deterministic automatic retrieval (every turn, when the
     * user has collections selected) — exposing it also as a tool created
     * two competing paths and inconsistent behaviour on small models.
     */
    public synchronized void registerBuiltinsOnce() {
        if (builtinsRegistered) return;
        register(new com.graden.models.service.tools.builtin.GetCurrentTime());
        register(new com.graden.models.service.tools.builtin.Calculate());
        register(new com.graden.models.service.tools.builtin.SystemInfo());
        register(new com.graden.models.service.tools.builtin.WebFetch());
        builtinsRegistered = true;
    }

    public void unregister(String name) {
        tools.remove(name);
        enabledOrder.remove(name);
    }

    public Tool lookup(String name) {
        return tools.get(name);
    }

    public boolean isRegistered(String name) {
        return tools.containsKey(name);
    }

    public List<Tool> getEnabledTools() {
        return enabledOrder.stream()
                .map(tools::get)
                .filter(t -> t != null)
                .toList();
    }

    public List<ToolDefinition> getEnabledDefinitions() {
        return getEnabledTools().stream()
                .map(Tool::getDefinition)
                .toList();
    }

    public int size() {
        return tools.size();
    }

    public void clear() {
        tools.clear();
        enabledOrder.clear();
    }
}
