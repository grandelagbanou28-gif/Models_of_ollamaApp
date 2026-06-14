package com.graden.models.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graden.models.model.ModelCapabilities;
import com.graden.models.model.OllamaModel;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CapabilityManager {

    private static final Logger LOGGER = Logger.getLogger(CapabilityManager.class.getName());
    private static final String CAPABILITIES_FILE = "model_capabilities.json";
    private static final long TTL_MS = 24 * 60 * 60 * 1000;

    private static CapabilityManager instance;

    private final Map<String, ModelCapabilities> cache;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final File storageFile;

    private CapabilityManager() {
        this.cache = new ConcurrentHashMap<>();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();

        String userHome = System.getProperty("user.home");
        File appDir = new File(userHome, ".GradenModels");
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        this.storageFile = new File(appDir, CAPABILITIES_FILE);

        loadFromDisk();
    }

    public static synchronized CapabilityManager getInstance() {
        if (instance == null) {
            instance = new CapabilityManager();
        }
        return instance;
    }

    public ModelCapabilities getCapabilities(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return new ModelCapabilities(modelName);
        }

        ModelCapabilities cached = cache.get(modelName);
        if (cached != null && !cached.isExpired(TTL_MS)) {
            return cached;
        }

        return fetchAndCache(modelName);
    }

    public boolean supportsTools(String modelName) {
        return getCapabilities(modelName).isTools();
    }

    private ModelCapabilities fetchAndCache(String modelName) {
        ModelCapabilities caps = fetchFromApi(modelName);

        if (caps.getRaw().isEmpty()) {
            caps = inferFromScrapedBadges(modelName);
        }

        cache.put(modelName, caps);
        persistToDisk();
        return caps;
    }

    private ModelCapabilities fetchFromApi(String modelName) {
        ModelCapabilities caps = new ModelCapabilities(modelName);

        try {
            String host = ConfigManager.getInstance().getOllamaHost();
            Map<String, String> body = Map.of("name", modelName);
            String json = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(host + "/api/show"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                LOGGER.fine("GET /api/show for " + modelName + " returned " + response.statusCode());
                return caps;
            }

            JsonNode root = mapper.readTree(response.body());

            if (root.has("capabilities")) {
                caps = ModelCapabilities.fromRawCapabilities(modelName,
                        extractCapabilities(root.get("capabilities")));
            } else if (root.has("details") && root.get("details").has("capabilities")) {
                caps = ModelCapabilities.fromRawCapabilities(modelName,
                        extractCapabilities(root.get("details").get("capabilities")));
            }

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not fetch capabilities from /api/show for " + modelName, e);
        }

        return caps;
    }

    /**
     * Extracts capability strings from the {@code /api/show} response.
     * Modern Ollama returns {@code capabilities} as a JSON array
     * (e.g. {@code ["completion","tools","vision"]}); older/edge cases may
     * return an object. Both are handled.
     */
    private static Set<String> extractCapabilities(JsonNode capNode) {
        Set<String> out = new HashSet<>();
        if (capNode == null) return out;
        if (capNode.isArray()) {
            capNode.forEach(n -> out.add(n.asText()));
        } else if (capNode.isObject()) {
            capNode.fieldNames().forEachRemaining(out::add);
        }
        return out;
    }

    private ModelCapabilities inferFromScrapedBadges(String modelName) {
        ModelCapabilities caps = new ModelCapabilities(modelName);

        OllamaModel model = findLocalOrCachedModel(modelName);
        if (model != null && model.getBadges() != null) {
            Set<String> rawCaps = model.getBadges().stream()
                    .map(String::toLowerCase)
                    .map(String::trim)
                    .collect(Collectors.toCollection(HashSet::new));
            caps = ModelCapabilities.fromRawCapabilities(modelName, rawCaps);
        }

        if (caps.getRaw().isEmpty()) {
            caps = inferFromFamilyName(modelName);
        }

        return caps;
    }

    private ModelCapabilities inferFromFamilyName(String modelName) {
        Set<String> rawCaps = new HashSet<>();
        String lower = modelName.toLowerCase();

        if (lower.startsWith("qwen2.5") || lower.startsWith("qwen-2.5")
                || lower.startsWith("qwen3") || lower.startsWith("qwen-3")) {
            rawCaps.add("tools");
            rawCaps.add("vision");
        }
        if (lower.startsWith("llama3.1") || lower.startsWith("llama-3.1")
                || lower.startsWith("llama3.2") || lower.startsWith("llama-3.2")
                || lower.startsWith("llama3.3") || lower.startsWith("llama-3.3")
                || lower.startsWith("llama4") || lower.startsWith("llama-4")) {
            rawCaps.add("tools");
        }
        if (lower.startsWith("mistral-nemo") || lower.startsWith("mistral-small")
                || lower.startsWith("mistral-large") || lower.startsWith("ministral")
                || lower.startsWith("codestral")) {
            rawCaps.add("tools");
        }
        if (lower.startsWith("phi4") || lower.startsWith("phi-4")
                || lower.startsWith("phi3") || lower.startsWith("phi-3")) {
            rawCaps.add("tools");
        }
        if (lower.startsWith("command-r") || lower.startsWith("aya")) {
            rawCaps.add("tools");
        }
        if (lower.startsWith("granite3") || lower.startsWith("granite-3")
                || lower.startsWith("granite4") || lower.startsWith("granite-4")) {
            rawCaps.add("tools");
        }
        if (lower.startsWith("nemotron")) {
            rawCaps.add("tools");
        }
        if (lower.startsWith("deepseek-r1") || lower.startsWith("deepseek-coder")) {
            rawCaps.add("tools");
        }
        if (lower.startsWith("gemma3") && (lower.contains("12b") || lower.contains("27b"))) {
            rawCaps.add("tools");
        }
        if (lower.startsWith("hermes3") || lower.startsWith("hermes-3")) {
            rawCaps.add("tools");
        }

        return ModelCapabilities.fromRawCapabilities(modelName, rawCaps);
    }

    private OllamaModel findLocalOrCachedModel(String modelName) {
        String baseName = modelName.contains(":") ? modelName.split(":")[0] : modelName;

        try {
            return ModelManager.getInstance().getLocalModels().stream()
                    .filter(m -> m.getName().equals(baseName) || m.getName().equals(modelName))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public void refresh(String modelName) {
        cache.remove(modelName);
        getCapabilities(modelName);
    }

    private void persistToDisk() {
        try {
            Map<String, ModelCapabilities> copy = new LinkedHashMap<>(cache);
            mapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, copy.values());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save model capabilities", e);
        }
    }

    private void loadFromDisk() {
        if (!storageFile.exists()) return;

        try {
            ModelCapabilities[] saved = mapper.readValue(storageFile, ModelCapabilities[].class);
            for (ModelCapabilities caps : saved) {
                if (caps.getModelName() != null && !caps.getModelName().isBlank()) {
                    cache.put(caps.getModelName(), caps);
                }
            }
            LOGGER.info("Loaded " + cache.size() + " model capabilities from disk");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not load model capabilities, will rebuild", e);
        }
    }
}
