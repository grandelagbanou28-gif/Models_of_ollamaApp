package com.graden.models.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Service to fetch GitHub repository statistics asynchronously.
 * Uses GitHub API to retrieve download counts from releases.
 */
public class GitHubStatsService {

    private static GitHubStatsService instance;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String RELEASES_API_URL = "https://api.github.com/repos/gradenmodels/GradenModels/releases";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private GitHubStatsService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static synchronized GitHubStatsService getInstance() {
        if (instance == null) {
            instance = new GitHubStatsService();
        }
        return instance;
    }

    /**
     * Fetches total download count from all assets of the latest release.
     * 
     * @return CompletableFuture with total downloads, or -1 if error occurs
     */
    public CompletableFuture<Integer> fetchTotalDownloads() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(RELEASES_API_URL))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    System.err.println("GitHub API returned status: " + response.statusCode());
                    return -1;
                }

                return parseDownloadCount(response.body());

            } catch (Exception e) {
                System.err.println("Error fetching GitHub stats: " + e.getMessage());
                return -1;
            }
        });
    }

    /**
     * Parses the JSON response to extract total download count.
     * Sums download_count from all assets in the latest (first) release.
     * 
     * @param jsonResponse JSON string from GitHub API
     * @return total download count, or -1 if parsing fails
     */
    private int parseDownloadCount(String jsonResponse) {
        try {
            JsonNode releases = objectMapper.readTree(jsonResponse);

            if (releases.isArray() && releases.size() > 0) {
                int totalDownloads = 0;
                for (JsonNode release : releases) {
                    JsonNode assets = release.get("assets");
                    if (assets != null && assets.isArray()) {
                        for (JsonNode asset : assets) {
                            JsonNode downloadCount = asset.get("download_count");
                            if (downloadCount != null) {
                                totalDownloads += downloadCount.asInt();
                            }
                        }
                    }
                }
                return totalDownloads;
            }

            return -1;

        } catch (Exception e) {
            System.err.println("Error parsing GitHub response: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Fetches all releases from the GitHub repository.
     *
     * @return CompletableFuture with a list of GitHubRelease objects.
     */
    public CompletableFuture<java.util.List<com.graden.models.model.GitHubRelease>> fetchReleases() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(RELEASES_API_URL))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString());

                java.util.List<com.graden.models.model.GitHubRelease> releaseList = new java.util.ArrayList<>();
                if (response.statusCode() != 200) {
                    System.err.println("GitHub API returned status: " + response.statusCode());
                    return releaseList;
                }

                JsonNode releases = objectMapper.readTree(response.body());
                if (releases.isArray()) {
                    for (JsonNode release : releases) {
                        String tagName = release.has("tag_name") ? release.get("tag_name").asText() : "Unknown";
                        String publishedAt = release.has("published_at") ? release.get("published_at").asText() : "";
                        String body = release.has("body") ? release.get("body").asText() : "";
                        
                        // Parse date locally
                        if (!publishedAt.isEmpty()) {
                            try {
                                java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(publishedAt);
                                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy");
                                publishedAt = zdt.format(formatter);
                            } catch (Exception e) {
                                // fallback to raw string
                            }
                        }
                        
                        releaseList.add(new com.graden.models.model.GitHubRelease(tagName, publishedAt, body));
                    }
                }
                return releaseList;
            } catch (Exception e) {
                System.err.println("Error fetching GitHub releases: " + e.getMessage());
                return new java.util.ArrayList<>();
            }
        });
    }
}
