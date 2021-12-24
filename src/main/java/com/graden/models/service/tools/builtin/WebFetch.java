package com.graden.models.service.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graden.models.service.tools.Tool;
import com.graden.models.service.tools.ToolDefinition;
import com.graden.models.service.tools.ToolResult;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class WebFetch implements Tool {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> BLOCKED_SCHEMES = Set.of(
            "file", "gopher", "jar", "ftp", "javascript", "data", "vbscript");

    private static final int MAX_CONTENT_LENGTH = 100_000;
    private static final int MAX_REDIRECTS = 1;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", Map.of(
                "type", "string",
                "description", "The URL to fetch. Must start with http:// or https://."
        ));
        properties.put("max_chars", Map.of(
                "type", "integer",
                "description", "Maximum number of characters to return (default: 5000, max: 100000)."
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("url"));

        return new ToolDefinition(
                "web_fetch",
                "Fetches content from a URL and returns it as plain text. Useful for getting up-to-date information, documentation, or web page content. Use for URLs the user provides.",
                parameters);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) throws Exception {
        long start = System.currentTimeMillis();

        String urlStr = (String) args.get("url");
        if (urlStr == null || urlStr.isBlank()) {
            return ToolResult.failure("Missing required parameter: url", 0);
        }

        urlStr = urlStr.trim();

        URI uri = URI.create(urlStr);
        String scheme = uri.getScheme();
        if (scheme == null || BLOCKED_SCHEMES.contains(scheme.toLowerCase())) {
            return ToolResult.failure("Blocked URL scheme: " + scheme, 0);
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return ToolResult.failure("Only http and https URLs are supported", 0);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return ToolResult.failure("Invalid URL: no host", 0);
        }

        if (isPrivateAddress(host)) {
            return ToolResult.failure("Access to internal/private addresses is blocked", 0);
        }

        int maxChars = 5000;
        if (args.containsKey("max_chars") && args.get("max_chars") instanceof Number) {
            maxChars = Math.max(100, Math.min(((Number) args.get("max_chars")).intValue(), MAX_CONTENT_LENGTH));
        }

        String content = fetchWithRedirects(urlStr, 0, maxChars);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", urlStr);
        result.put("content_length", content.length());
        result.put("content", content);

        return ToolResult.success(MAPPER.writeValueAsString(result), System.currentTimeMillis() - start);
    }

    private String fetchWithRedirects(String url, int redirectCount, int maxChars) throws Exception {
        if (redirectCount > MAX_REDIRECTS) {
            throw new Exception("Too many redirects");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "GrandelGradenNexus-Tool/1.0")
                .header("Accept", "text/html, text/plain, application/json, */*")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            String location = response.headers().firstValue("Location").orElse(null);
            if (location != null) {
                URI redirectUri = URI.create(location);
                if (!redirectUri.isAbsolute()) {
                    redirectUri = URI.create(url).resolve(redirectUri);
                }
                return fetchWithRedirects(redirectUri.toString(), redirectCount + 1, maxChars);
            }
        }

        if (response.statusCode() != 200) {
            throw new Exception("HTTP " + response.statusCode() + ": " + response.body().substring(0, Math.min(200, response.body().length())));
        }

        String body = response.body();
        String contentType = response.headers().firstValue("Content-Type").orElse("");

        if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
            body = htmlToText(body);
        }

        if (body.length() > maxChars) {
            body = body.substring(0, maxChars) + "\n\n[Content truncated at " + maxChars + " characters]";
        }

        return body;
    }

    private static String htmlToText(String html) {
        try {
            Document doc = Jsoup.parse(html);
            doc.select("script, style, nav, footer, header, iframe, noscript, svg").remove();
            String text = doc.body() != null ? doc.body().text() : doc.text();
            return text.replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
    }

    private static boolean isPrivateAddress(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
