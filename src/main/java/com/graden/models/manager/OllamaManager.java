package com.graden.models.manager;

import com.graden.models.model.OllamaModel;
import com.graden.models.util.SecurityUtils;
import com.graden.models.util.Utils;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.LibraryModel;
import io.github.ollama4j.models.response.Model;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.generate.OllamaStreamHandler;
import com.graden.models.model.StreamRequest;
import com.graden.models.model.ToolCall;
import com.graden.models.model.ChatMessage;
import com.graden.models.model.GenerationMetrics;
import com.graden.models.service.tools.ToolDefinition;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ResourceBundle;
import java.util.Locale;
import java.net.http.HttpTimeoutException;

public class OllamaManager {

    private static OllamaManager instance;
    private OllamaAPI client;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private volatile InputStream activeStream; // To support forceful cancellation
    private volatile boolean streamCancelled; // True when the stream was closed by a user cancel

    private static final Logger LOGGER = Logger.getLogger(OllamaManager.class.getName());

    private OllamaManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
        updateClient();
    }

    public void updateClient() {
        String host = ConfigManager.getInstance().getOllamaHost();
        if (host == null || host.isEmpty()) {
            host = "http://127.0.0.1:11434";
        }
        this.client = new OllamaAPI(host);
        this.client.setRequestTimeoutSeconds(60);
    }

    public static synchronized OllamaManager getInstance() {
        if (instance == null) {
            instance = new OllamaManager();
        }
        return instance;
    }

    // --- MÉTODOS QUE YA FUNCIONAN Y NO NECESITAN CAMBIOS ---
    public List<OllamaModel> getLocalModels() {
        List<OllamaModel> localModelsList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        try {
            List<Model> responseList = client.listModels();
            for (Model model : responseList) {
                String[] nameParts = model.getName().split(":", 2);
                String baseName = nameParts[0];
                String tag = (nameParts.length > 1) ? nameParts[1] : "latest";
                OffsetDateTime modifiedAt = model.getModifiedAt();
                String formattedDate = (modifiedAt != null) ? modifiedAt.format(formatter) : "N/A";
                OllamaModel localModel = new OllamaModel(baseName, "Installed locally", "N/A", tag,
                        Utils.formatSize(model.getSize()), formattedDate);
                localModelsList.add(localModel);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting local models", e);
        }
        return localModelsList;
    }

    public List<OllamaModel> getAvailableBaseModels() {
        try {
            List<LibraryModel> baseModels = client.listModelsFromLibrary();
            return baseModels.stream().map(libraryModel -> new OllamaModel(libraryModel.getName(), "", "", "", "", ""))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting available base models", e);
            return new ArrayList<>();
        }
    }

    // --- SCRAPING LIBRARY LISTS ---

    public List<OllamaModel> getLibraryModels(String sort) {
        if (sort != null && !sort.isEmpty() && !SecurityUtils.isValidModelName(sort)) {
            return new ArrayList<>();
        }
        List<OllamaModel> models = new ArrayList<>();
        String url = "https://ollama.com/library" + (sort != null && !sort.isEmpty() ? "?sort=" + sort : "");

        try {
            Document doc = Jsoup.connect(url).get();
            Elements items = doc.select("li a.group");

            if (items.isEmpty()) {
                items = doc.select("ul li a[href^='/library/']");
            }

            for (Element item : items) {
                String href = item.attr("href");
                String name = href.replace("/library/", "");

                // Extract title/name

                // Extract description
                Element descEl = item.selectFirst("p");
                String description = (descEl != null) ? descEl.text() : "";

                // Extract pull count and badges
                String pullCount = "N/A";
                String lastUpdated = "N/A";
                List<String> badges = new ArrayList<>();

                Elements spans = item.select("span");
                for (Element span : spans) {
                    String text = span.text();
                    String low = text.toLowerCase().trim();
                    boolean isCapability =
                            low.equals("vision") || low.equals("tools") || low.equals("thinking") ||
                            low.equals("embedding") || low.equals("embeddings") || low.equals("code") ||
                            low.equals("multimodal") || low.equals("math") || low.equals("reasoning") ||
                            low.equals("audio") || low.equals("function calling");
                    boolean isSizeBadge = low.matches("^\\d+(\\.\\d+)?[mb]$");
                    if (isCapability || isSizeBadge || text.contains("Provides") || text.contains("param")) {
                        if (!text.isBlank() && !badges.contains(text)) badges.add(text);
                    }
                    if (text.matches(".*[kKmMbB]$") || text.contains("pulls")) {
                        pullCount = text.replace("pulls", "").trim();
                    }
                    if (text.contains("ago")) {
                        lastUpdated = text;
                    }
                }
                // Also pick up capability filter links (Ollama wraps them as <a href="?c=vision">)
                Elements capLinks = item.select("a[href*=?c=], a[href*=&c=]");
                for (Element a : capLinks) {
                    String txt = a.text().trim();
                    if (!txt.isEmpty() && !badges.contains(txt)) badges.add(txt);
                }

                // Create a model object. Using name as base. Tag we can assume "latest" or
                // leave generic.
                OllamaModel model = new OllamaModel(name, description, pullCount, "latest", "N/A", lastUpdated,
                        "Unknown", "Text", badges, "", null);
                models.add(model);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error scraping library models from " + url, e);
        }
        return models;
    }

    // --- MÉTODO DE SCRAPING FINAL Y CORREGIDO (DETALLES) ---

    /**
     * VERSIÓN FINAL con selectores de CSS ajustados al HTML real.
     * 
     * @param modelName El nombre del modelo a buscar (ej: "gemma3n").
     * @return Una lista de OllamaModel, donde cada uno representa un tag.
     * @throws IOException Si la conexión a la página web falla.
     */
    public List<OllamaModel> scrapeModelDetails(String modelName) throws IOException {
        if (!SecurityUtils.isValidModelName(modelName)) {
            throw new IllegalArgumentException("Invalid model name.");
        }
        String url = String.format(ScrapingSelectors.MODEL_URL_FMT, modelName);

        org.jsoup.Connection conn = Jsoup.connect(url)
                .userAgent(ScrapingSelectors.USER_AGENT)
                .timeout(ScrapingSelectors.READ_TIMEOUT_MS)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Accept", "text/html,application/xhtml+xml");

        org.jsoup.Connection.Response resp = conn.execute();
        int status = resp.statusCode();
        String ct = resp.contentType();
        if (status != 200 || ct == null || !ct.toLowerCase().startsWith("text/html")) {
            throw new IOException("Unexpected response for " + url
                    + " status=" + status + " contentType=" + ct);
        }

        Document doc = resp.parse();

        String description = firstNonNullText(doc, ScrapingSelectors.DESCRIPTION_SELECTORS,
                "No description available.");
        String pullCountRaw = firstNonNullText(doc, ScrapingSelectors.PULL_COUNT_SELECTORS, null);
        String pullCount = (pullCountRaw != null) ? pullCountRaw + " downloads" : "N/A";
        String lastUpdatedGlobal = firstNonNullText(doc, ScrapingSelectors.UPDATED_SELECTORS, "N/A");

        int missing = 0;
        if (pullCountRaw == null) missing++;
        if ("N/A".equals(lastUpdatedGlobal)) missing++;
        if ("No description available.".equals(description)) missing++;
        if (missing >= 2) {
            LOGGER.log(Level.FINE,
                    "scrapeModelDetails partial selectors model={0} missingCriticalFields={1}",
                    new Object[]{modelName, missing});
        }

        List<String> badges = extractBadges(doc);
        String readmeContent = extractReadme(doc);

        return extractTags(doc, modelName, description, pullCount, lastUpdatedGlobal, badges, readmeContent);
    }

    /** Returns the first non-empty text matching any of the selectors, or fallback. */
    private String firstNonNullText(Document doc, String[] selectors, String fallback) {
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                String t = el.text();
                if (t != null && !t.trim().isEmpty()) {
                    return t.trim();
                }
            }
        }
        return fallback;
    }

    private List<String> extractBadges(Document doc) {
        List<String> badges = new ArrayList<>();

        // 1) Direct: size badge has x-test-size attribute (ollama.com 2025+ markup).
        Element sizeEl = doc.selectFirst("[x-test-size]");
        if (sizeEl != null) {
            String txt = sizeEl.text().trim();
            if (!txt.isEmpty() && !badges.contains(txt)) badges.add(txt);
        }

        // 2) Try the known badge container selectors and grab every <span>/<a> inside.
        for (String sel : ScrapingSelectors.BADGES_CONTAINER_SELECTORS) {
            Element container = doc.selectFirst(sel);
            if (container == null) continue;
            Elements badgeElements = container.select("span, a");
            for (Element badge : badgeElements) {
                String badgeText = badge.text().trim();
                if (!badgeText.isEmpty() && badgeText.length() <= 24 && !badges.contains(badgeText)) {
                    badges.add(badgeText);
                }
            }
            if (!badges.isEmpty()) break;
        }

        // 3) Robust fallback: globally scan every <span> for known capability keywords
        //    or size-shaped tokens (e.g. "7b", "1.5b", "128b").
        Set<String> capabilityKeywords = Set.of(
                "vision", "tools", "thinking", "embedding", "embeddings",
                "code", "multimodal", "math", "reasoning", "audio", "function calling"
        );
        Elements allSpans = doc.select("span");
        for (Element span : allSpans) {
            String txt = span.text().trim();
            if (txt.isEmpty() || txt.length() > 24) continue;
            String low = txt.toLowerCase();
            boolean isCap = capabilityKeywords.contains(low);
            boolean isSize = low.matches("^\\d+(\\.\\d+)?[mb]$");
            if ((isCap || isSize) && !badges.contains(txt)) {
                badges.add(txt);
            }
        }

        return badges;
    }

    private String extractReadme(Document doc) {
        for (String sel : ScrapingSelectors.README_SELECTORS) {
            Element el = doc.selectFirst(sel);
            if (el != null) return el.html();
        }
        return "No README available.";
    }

    private List<OllamaModel> extractTags(Document doc, String modelName, String description, String pullCount,
            String lastUpdatedGlobal, List<String> badges, String readmeContent) {
        List<OllamaModel> modelTags = new ArrayList<>();

        // Strategy: ollama.com renders EACH tag inside its own card. The card
        // contains both a mobile-only <a class="md:hidden"> wrapper (which already
        // embeds all row text) and a desktop block with <p> cells for size/context.
        // The previous heuristic walked parents until rowText grew, which on the
        // mobile wrapper overflowed into sibling tag cards and made every variant
        // pick the first global size match.
        //
        // New approach:
        //   1) Skip mobile-only <a> wrappers (class contains "md:hidden").
        //   2) Find the smallest ancestor that holds the per-tag desktop row
        //      (`div.grid.grid-cols-12`).
        //   3) Within that row, read the dedicated <p> cells.
        String linkSelector = "a[href^=\"/library/" + modelName + ":\"]";
        Elements tagLinks = doc.select(linkSelector);

        java.util.LinkedHashMap<String, OllamaModel> byTag = new java.util.LinkedHashMap<>();
        for (Element link : tagLinks) {
            String cls = link.attr("class");
            if (cls.contains("md:hidden")) continue;

            String href = link.attr("href");
            int colon = href.indexOf(':');
            if (colon < 0) continue;
            String tag = href.substring(colon + 1);
            int slash = tag.indexOf('/');
            if (slash >= 0) tag = tag.substring(0, slash);
            int q = tag.indexOf('?');
            if (q >= 0) tag = tag.substring(0, q);
            if (tag.isEmpty() || byTag.containsKey(tag)) continue;

            // Climb to this tag's card using a STRUCTURAL criterion instead of
            // hardcoded CSS class names (which break whenever ollama.com tweaks
            // its markup). The per-tag card is the LARGEST ancestor that still
            // contains exactly ONE tag link — climbing one level further would
            // pull in sibling tags and make every variant inherit one size.
            Element row = link;
            Element candidate = link;
            for (int hops = 0; hops < 8; hops++) {
                Element parent = candidate.parent();
                if (parent == null) break;
                if (countTagLinks(parent, modelName) == 1) {
                    row = parent;        // still exclusive to this tag
                    candidate = parent;
                } else {
                    break;               // parent mixes multiple tags → stop
                }
            }

            String size = "N/A";
            String context = "Unknown";
            String input = "Text";
            String updated = lastUpdatedGlobal;

            // Read <p> cells inside this card (desktop layout).
            for (Element p : row.select("p")) {
                String txt = p.text().trim();
                if (txt.matches("\\d+(?:\\.\\d+)?\\s?[GMK]B")) {
                    size = txt;
                } else if (txt.matches("\\d+(?:\\.\\d+)?[KM]")) {
                    context = txt;
                }
            }
            // Fallback for mobile-style inline summary (rare on current layout).
            if ("N/A".equals(size) || "Unknown".equals(context)) {
                String rowText = row.text();
                if ("N/A".equals(size)) {
                    size = firstMatch(rowText, "([0-9]+(?:\\.[0-9]+)?\\s?[GMK]B)", "N/A");
                }
                if ("Unknown".equals(context)) {
                    context = firstMatch(rowText, "([0-9]+(?:\\.[0-9]+)?[KM])(?:\\s*(?:context|tokens))?", "Unknown");
                }
            }

            String rowLower = row.text().toLowerCase();
            if (rowLower.contains("multimodal")) input = "Multimodal";
            else if (rowLower.contains("image")) input = "Image";
            else input = "Text";

            updated = firstMatch(row.text(),
                    "(\\d+\\s+(?:second|minute|hour|day|week|month|year)s?\\s+ago)",
                    lastUpdatedGlobal);

            byTag.put(tag, new OllamaModel(modelName, description, pullCount,
                    tag, size, updated, context, input, badges, readmeContent, null));
        }

        if (!byTag.isEmpty()) {
            modelTags.addAll(byTag.values());
            // Regression guard: if 2+ variants all share the exact same size,
            // the per-tag scoping almost certainly broke (ollama.com markup
            // change). Surface it loudly instead of shipping wrong data.
            if (modelTags.size() >= 2) {
                String first = modelTags.get(0).getSize();
                boolean allSame = first != null && !"N/A".equals(first)
                        && modelTags.stream().allMatch(m -> first.equals(m.getSize()));
                if (allSame) {
                    LOGGER.warning("extractTags: all " + modelTags.size()
                            + " variants of '" + modelName + "' have identical size '"
                            + first + "' — likely a scraping regression.");
                }
            }
            return modelTags;
        }

        // Legacy fallback (older HTML layout): table of div.hidden.sm:grid rows.
        Element tagsContainer = null;
        for (String sel : ScrapingSelectors.TAGS_CONTAINER_SELECTORS) {
            tagsContainer = doc.selectFirst(sel);
            if (tagsContainer != null) break;
        }
        if (tagsContainer != null) {
            Elements tagRows = new Elements();
            for (String sel : ScrapingSelectors.TAG_ROW_SELECTORS) {
                tagRows = tagsContainer.select(sel);
                if (!tagRows.isEmpty()) break;
            }
            for (Element row : tagRows) {
                Element anchor = row.selectFirst("a");
                if (anchor == null) continue;
                String fullTagName = anchor.text();
                Elements columns = row.select("> *");
                String size = "N/A", context = "Unknown", input = "Text";
                for (Element col : columns) {
                    String text = col.text();
                    if (text.matches(".*\\d+(\\.\\d+)?[GMK]B.*")) size = text;
                    else if (text.contains("K") && text.length() < 10) context = text;
                    else if (text.equals("Text") || text.equals("Image") || text.equals("Multimodal")) input = text;
                }
                modelTags.add(new OllamaModel(modelName, description, pullCount,
                        fullTagName.contains(":") ? fullTagName.split(":")[1] : fullTagName,
                        size, lastUpdatedGlobal, context, input, badges, readmeContent, null));
            }
            if (!modelTags.isEmpty()) return modelTags;
        }

        // Last resort: emit a single "latest" placeholder so the model still appears
        // in the library list (we'd rather show it with N/A details than hide it).
        LOGGER.log(Level.FINE, "extractTags fallback to latest-only for model={0}", modelName);
        modelTags.add(new OllamaModel(modelName, description, pullCount, "latest", "N/A",
                lastUpdatedGlobal, "Unknown", "Text", badges, readmeContent, null));
        return modelTags;
    }

    private static String firstMatch(String haystack, String regex, String fallback) {
        if (haystack == null) return fallback;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(haystack);
        return m.find() ? m.group(1) : fallback;
    }

    /**
     * Counts distinct desktop tag links for {@code modelName} inside {@code scope}.
     * Mobile-only wrappers ({@code class} contains "md:hidden") are excluded so
     * they don't double-count. Used by {@link #extractTags} to find the smallest
     * DOM scope that belongs to a single tag variant.
     */
    private static int countTagLinks(Element scope, String modelName) {
        if (scope == null) return 0;
        Elements links = scope.select("a[href^=\"/library/" + modelName + ":\"]");
        int count = 0;
        for (Element l : links) {
            if (l.attr("class").contains("md:hidden")) continue;
            count++;
        }
        return count;
    }

    public interface ProgressCallback {
        void onProgress(double progress, String status);
    }

    /**
     * Descarga un modelo desde Ollama.
     * Nota: La librería ollama4j tiene soporte limitado para callbacks de progreso
     * en algunas versiones.
     * Si no soporta callbacks granulares, simularemos o usaremos lo que haya.
     * 
     * @param modelName Nombre del modelo
     * @param tag       Tag del modelo
     * @param callback  Callback para actualizar la UI
     */
    public void pullModel(String modelName, String tag, ProgressCallback callback) throws Exception {
        if (!SecurityUtils.isValidModelName(modelName) ||
                !SecurityUtils.isValidModelName(tag)) {
            throw new IllegalArgumentException("Invalid model name or tag.");
        }
        String fullName = modelName + ":" + tag;

        // Usamos ProcessBuilder para ejecutar "ollama pull" y leer la salida
        ProcessBuilder builder = new ProcessBuilder(Utils.getOllamaExecutable(), "pull",
                fullName);
        builder.redirectErrorStream(true); // Combinar stderr y stdout

        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // Parsear la línea para extraer progreso
                // Formatos típicos de Ollama:
                // "pulling manifest"
                // "pulling <hash>... 100%"
                // "verifying sha256 digest"
                // "success"

                String status = line;
                double progress = -1; // Indeterminado por defecto

                if (line.contains("%")) {
                    // Intentar extraer el porcentaje
                    try {
                        // Buscar el último número antes del %
                        int percentIndex = line.lastIndexOf('%');
                        // Retroceder para encontrar el inicio del número
                        int start = percentIndex - 1;
                        while (start >= 0 && (Character.isDigit(line.charAt(start)) || line.charAt(start) == '.')) {
                            start--;
                        }
                        String numStr = line.substring(start + 1, percentIndex).trim();
                        progress = Double.parseDouble(numStr);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error parsing progress from line: " + line, e);
                        // Ignorar errores de parsing, mantener progreso actual o indeterminado
                    }
                } else if (line.contains("success")) {
                    progress = 100;
                }

                if (callback != null) {
                    callback.onProgress(progress, status);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Ollama pull failed with exit code: " + exitCode);
        }
    }

    public void deleteModel(String modelName, String tag) throws Exception {
        if (!SecurityUtils.isValidModelName(modelName) ||
                !SecurityUtils.isValidModelName(tag)) {
            throw new IllegalArgumentException("Invalid model name or tag.");
        }
        String fullName = modelName + ":" + tag;
        ProcessBuilder builder = new ProcessBuilder(Utils.getOllamaExecutable(), "rm", fullName);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // Drain the buffer
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Ollama rm failed with exit code: " + exitCode);
        }
    }

    /**
     * Sends a prompt to the specified model and returns the response.
     * This is a synchronous blocking call.
     *
     * @param modelName The name of the model (e.g., "llama3:latest")
     * @param prompt    The user's message
     * @return The AI's response text
     * @throws Exception If the request fails
     */
    /**
     * @deprecated Use {@link #askModelStream} instead. This synchronous method is
     *             no longer called.
     */
    @Deprecated
    public String askModel(String modelName, String prompt) throws Exception {

        // Create a chat message object
        // We need to use the Chat API, not Generate, for better compatibility and
        // future history support.
        List<OllamaChatMessage> messages = new ArrayList<>();
        messages.add(new OllamaChatMessage(OllamaChatMessageRole.USER, prompt));

        OllamaChatRequest request = OllamaChatRequestBuilder
                .getInstance(modelName).withMessages(messages).build();

        OllamaChatResult result = client.chat(request);
        return result.getResponse();
    }

    public void askModelStream(String modelName, String prompt, List<String> images,
            Map<String, Object> requestOptions, String systemPrompt,
            OllamaStreamHandler handler,
            java.util.function.Consumer<com.graden.models.model.GenerationMetrics> onComplete)
            throws Exception {

        // Build Payload manually
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelName);
        payload.put("stream", true);

        List<Map<String, Object>> messages = new ArrayList<>();

        // Some vision models don't support system prompts alongside images.
        // Only include system prompt when there are no images.
        boolean hasImages = images != null && !images.isEmpty();
        if (!hasImages && systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        // Ensure content is never empty — use a default for image-only messages
        String messageContent = (prompt != null && !prompt.trim().isEmpty())
                ? prompt
                : (hasImages ? "What is in this image?" : prompt);
        userMsg.put("content", messageContent);
        if (hasImages) {
            userMsg.put("images", images);
        }
        messages.add(userMsg);

        payload.put("messages", messages);

        // Options: Merge any defaults if needed, but here we assume requestOptions is
        // complete or null
        if (requestOptions != null) {
            payload.put("options", requestOptions);
        } else {
            // Fallback default
            Map<String, Object> defaultOptions = new HashMap<>();
            defaultOptions.put("temperature", 0.7);
            payload.put("options", defaultOptions);
        }

        String jsonBody = mapper.writeValueAsString(payload);
        // Debug: log payload (truncate images for readability)
        if (hasImages) {
            LOGGER.log(Level.INFO, "[GradenModels] Sending multimodal request to model: {0}, images count: {1}",
                    new Object[] { modelName, images.size() });
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ConfigManager.getInstance().getOllamaHost() + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(ConfigManager.getInstance().getApiTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<InputStream> response;
        try {
            // Use send (blocking) but handle interruption gracefully
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            System.err.println("[GradenModels] API Timeout: " + e.getMessage());
            int timeoutVal = ConfigManager.getInstance().getApiTimeout();
            String lang = ConfigManager.getInstance().getLanguage();
            ResourceBundle bundle = ResourceBundle.getBundle("messages",
                    new Locale(lang));
            String errorMsg = bundle.getString("error.timeout").replace("{0}", String.valueOf(timeoutVal));
            throw new Exception(errorMsg);
        }

        if (response.statusCode() != 200) {
            // Read error body for diagnostics
            String errorBody = "";
            try (var errorReader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                errorBody = errorReader.lines().collect(Collectors.joining("\n"));
            } catch (Exception ignored) {
            }

            // Try to parse JSON to get a clean error string for the UI
            String displayError = errorBody;
            try {
                JsonNode node = mapper.readTree(errorBody);
                if (node.has("error")) {
                    displayError = node.get("error").asText();
                }
            } catch (Exception e) {
                // Fallback to raw errorBody if not JSON
            }

            System.err.println("[GradenModels] API Error " + response.statusCode() + ": " + errorBody);
            throw new Exception(displayError);
        }

        this.activeStream = response.body(); // Capture stream

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(activeStream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder fullContent = new StringBuilder();
            int evalCount = 0;
            long evalDuration = 0;

            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    return; // Stop reading
                }

                try {
                    JsonNode node = mapper.readTree(line);
                    if (node.has("message")) {
                        JsonNode msgNode = node.get("message");
                        if (msgNode.has("content")) {
                            String content = msgNode.get("content").asText();
                            fullContent.append(content);
                            handler.accept(fullContent.toString());
                        }
                    }
                    if (node.has("done") && node.get("done").asBoolean()) {
                        if (node.has("eval_count")) {
                            evalCount = node.get("eval_count").asInt();
                        }
                        if (node.has("eval_duration")) {
                            evalDuration = node.get("eval_duration").asLong();
                        }
                        break;
                    }
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception e) {
                }
            }

            if (onComplete != null && evalCount > 0) {
                onComplete.accept(new com.graden.models.model.GenerationMetrics(evalCount, evalDuration));
            }
        } finally {
            this.activeStream = null; // Clean up
        }
    }

    /**
     * New streaming API with full tool support, JSON format, and conversation history.
     * This is the recommended method for all new callers.
     *
     * @param req The fully-configured streaming request
     * @throws Exception if the HTTP request fails
     */
    public void askModelStream(StreamRequest req) throws Exception {

        streamCancelled = false; // fresh request — clear any prior cancel flag

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", req.getModelName());
        payload.put("stream", true);

        List<Map<String, Object>> messages = new ArrayList<>();

        boolean hasImages = req.getImages() != null && !req.getImages().isEmpty();
        String systemPrompt = req.getSystemPrompt();

        if (!hasImages && systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }

        if (req.getConversationHistory() != null && !req.getConversationHistory().isEmpty()) {
            for (ChatMessage histMsg : req.getConversationHistory()) {
                Map<String, Object> msg = new HashMap<>();
                msg.put("role", histMsg.getRole());
                msg.put("content", histMsg.getContent() != null ? histMsg.getContent() : "");
                if (histMsg.getImages() != null && !histMsg.getImages().isEmpty()) {
                    msg.put("images", histMsg.getImages());
                }
                if (histMsg.hasToolCalls()) {
                    List<Map<String, Object>> tcList = new ArrayList<>();
                    for (ToolCall tc : histMsg.getToolCalls()) {
                        Map<String, Object> tcMap = new LinkedHashMap<>();
                        tcMap.put("id", tc.getId());
                        tcMap.put("type", "function");
                        Map<String, Object> fn = new LinkedHashMap<>();
                        fn.put("name", tc.getName());
                        fn.put("arguments", tc.getArgs());
                        tcMap.put("function", fn);
                        tcList.add(tcMap);
                    }
                    msg.put("tool_calls", tcList);
                }
                if (histMsg.isToolResult()) {
                    msg.put("tool_call_id", histMsg.getToolCallId());
                }
                messages.add(msg);
            }
        } else {
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            String messageContent = (req.getPrompt() != null && !req.getPrompt().trim().isEmpty())
                    ? req.getPrompt()
                    : (hasImages ? "What is in this image?" : req.getPrompt());
            userMsg.put("content", messageContent);
            if (hasImages) {
                userMsg.put("images", req.getImages());
            }
            messages.add(userMsg);
        }

        payload.put("messages", messages);

        List<ToolDefinition> tools = req.getTools();
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolsPayload = new ArrayList<>();
            for (ToolDefinition td : tools) {
                toolsPayload.add(td.toOllamaFormat());
            }
            payload.put("tools", toolsPayload);
        }

        String jsonFormat = req.getJsonFormat();
        if (jsonFormat != null && !jsonFormat.isBlank()) {
            try {
                payload.put("format", new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonFormat));
            } catch (Exception e) {
                payload.put("format", jsonFormat);
            }
        }

        if (req.getRequestOptions() != null) {
            payload.put("options", req.getRequestOptions());
        } else {
            Map<String, Object> defaultOptions = new HashMap<>();
            defaultOptions.put("temperature", 0.7);
            payload.put("options", defaultOptions);
        }

        String jsonBody = mapper.writeValueAsString(payload);
        if (hasImages) {
            LOGGER.log(Level.INFO, "[GradenModels] Sending multimodal request to model: {0}, images count: {1}",
                    new Object[]{req.getModelName(), req.getImages().size()});
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ConfigManager.getInstance().getOllamaHost() + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(ConfigManager.getInstance().getApiTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            System.err.println("[GradenModels] API Timeout: " + e.getMessage());
            int timeoutVal = ConfigManager.getInstance().getApiTimeout();
            String lang = ConfigManager.getInstance().getLanguage();
            ResourceBundle bundle = ResourceBundle.getBundle("messages",
                    new Locale(lang));
            String errorMsg = bundle.getString("error.timeout").replace("{0}", String.valueOf(timeoutVal));
            throw new Exception(errorMsg);
        }

        if (response.statusCode() != 200) {
            String errorBody = "";
            try (var errorReader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                errorBody = errorReader.lines().collect(Collectors.joining("\n"));
            } catch (Exception ignored) {
            }

            String displayError = errorBody;
            try {
                JsonNode node = mapper.readTree(errorBody);
                if (node.has("error")) {
                    displayError = node.get("error").asText();
                }
            } catch (Exception e) {
            }

            System.err.println("[GradenModels] API Error " + response.statusCode() + ": " + errorBody);
            throw new Exception(displayError);
        }

        this.activeStream = response.body();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(activeStream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder fullContent = new StringBuilder();
            int evalCount = 0;
            long evalDuration = 0;
            List<ToolCall> toolCalls = null;

            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                try {
                    JsonNode node = mapper.readTree(line);
                    if (node.has("message")) {
                        JsonNode msgNode = node.get("message");
                        if (msgNode.has("content")) {
                            String content = msgNode.get("content").asText();
                            fullContent.append(content);
                            if (req.getStreamHandler() != null) {
                                req.getStreamHandler().accept(fullContent.toString());
                            }
                        }
                        // Tool calls can arrive in ANY chunk — some models emit them
                        // with done=false, others bundle them into the final done
                        // chunk. Parse them wherever they appear, not just at done.
                        if (msgNode.has("tool_calls")) {
                            List<ToolCall> parsed = parseToolCalls(msgNode.get("tool_calls"));
                            if (parsed != null && !parsed.isEmpty()) {
                                if (toolCalls == null) toolCalls = new ArrayList<>();
                                toolCalls.addAll(parsed);
                            }
                        }
                    }
                    if (node.has("done") && node.get("done").asBoolean()) {
                        if (node.has("eval_count")) {
                            evalCount = node.get("eval_count").asInt();
                        }
                        if (node.has("eval_duration")) {
                            evalDuration = node.get("eval_duration").asLong();
                        }
                        break;
                    }
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception e) {
                }
            }

            GenerationMetrics metrics = null;
            if (evalCount > 0) {
                metrics = new com.graden.models.model.GenerationMetrics(evalCount, evalDuration);
            }

            if (req.getOnComplete() != null) {
                req.getOnComplete().accept(new StreamRequest.StreamCompleteEvent(
                        metrics, toolCalls, fullContent.toString()));
            }
        } catch (IOException ioe) {
            // A user-initiated cancel closes the stream, which surfaces here as
            // "IOException: closed". That's expected — swallow it quietly so it
            // isn't logged as a SEVERE generation error.
            if (streamCancelled || Thread.currentThread().isInterrupted()) {
                return;
            }
            throw ioe;
        } finally {
            this.activeStream = null;
        }
    }

    private List<ToolCall> parseToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || !toolCallsNode.isArray()) return null;
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode tcNode : toolCallsNode) {
            try {
                String id = tcNode.has("id") ? tcNode.get("id").asText() : null;
                JsonNode fnNode = tcNode.has("function") ? tcNode.get("function") : tcNode;
                String name = null;
                Map<String, Object> args = null;

                if (fnNode.has("name")) {
                    name = fnNode.get("name").asText();
                }
                if (fnNode.has("arguments")) {
                    JsonNode argsNode = fnNode.get("arguments");
                    com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> mapType =
                            new com.fasterxml.jackson.core.type.TypeReference<>() {};
                    if (argsNode.isObject()) {
                        args = mapper.convertValue(argsNode, mapType);
                    } else if (argsNode.isTextual()) {
                        try {
                            args = mapper.readValue(argsNode.asText(), mapType);
                        } catch (Exception e) {
                            args = Map.of();
                        }
                    }
                }

                if (name != null) {
                    ToolCall call = new ToolCall(id != null ? id : name, name, args);
                    calls.add(call);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to parse tool_call entry", e);
            }
        }
        return calls.isEmpty() ? null : calls;
    }

    public void cancelCurrentRequest() {
        streamCancelled = true;
        if (activeStream != null) {
            try {
                activeStream.close(); // This will throw IOException in the read loop
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}