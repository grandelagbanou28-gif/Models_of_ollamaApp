package com.graden.models.manager;

import com.graden.models.model.LibraryCache;
import com.graden.models.model.OllamaModel;
import com.graden.models.util.Utils;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModelLibraryManager {

    private static final Logger LOGGER = Logger.getLogger(ModelLibraryManager.class.getName());

    private static ModelLibraryManager instance;
    private final OllamaManager ollamaManager = OllamaManager.getInstance();
    private final LibraryCacheManager cacheManager = LibraryCacheManager.getInstance();
    private LibraryCache currentLibrary;

    // Status tracking for UI progress
    private volatile double currentProgress = 0.0;
    private volatile String currentStatus = "";
    private final AtomicBoolean isCancelling = new AtomicBoolean(false);

    // Last result (read from UI thread; written from crawler)
    private volatile int lastPagesScanned = 0;
    private volatile int lastModelsDiscovered = 0;
    private volatile ScrapeResult lastResult;

    private ModelLibraryManager() {
        this.currentLibrary = cacheManager.loadCache();
        if (this.currentLibrary == null) {
            this.currentLibrary = new LibraryCache();
        }
    }

    public static synchronized ModelLibraryManager getInstance() {
        if (instance == null) {
            instance = new ModelLibraryManager();
        }
        return instance;
    }

    /**
     * Invalidates the in-memory cache, forcing OUTDATED_HARD status on next
     * getUpdateStatus() call. Used when refreshing library from Settings.
     */
    public void invalidateCache() {
        this.currentLibrary = new LibraryCache();
    }

    public enum UpdateStatus {
        UP_TO_DATE,
        OUTDATED_SOFT, // > 5 days
        OUTDATED_HARD  // > 15 days or missing
    }

    public UpdateStatus getUpdateStatus() {
        if (currentLibrary == null || currentLibrary.getAllModels().isEmpty() || currentLibrary.getLastUpdated() == 0) {
            return UpdateStatus.OUTDATED_HARD;
        }
        long ageMs = System.currentTimeMillis() - currentLibrary.getLastUpdated();
        long days = ageMs / (1000 * 60 * 60 * 24);

        if (days > 10) return UpdateStatus.OUTDATED_HARD;
        if (days > 5)  return UpdateStatus.OUTDATED_SOFT;
        return UpdateStatus.UP_TO_DATE;
    }

    /** Checks if Ollama is installed by running 'ollama --version'. */
    public boolean isOllamaInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder(Utils.getOllamaExecutable(), "--version");
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public LibraryCache getLibrary() { return currentLibrary; }
    public double getProgress() { return currentProgress; }
    public String getStatus() { return currentStatus; }
    public ScrapeResult getLastResult() { return lastResult; }
    public int getLastPagesScanned() { return lastPagesScanned; }
    public int getLastModelsDiscovered() { return lastModelsDiscovered; }

    public void cancelUpdate() { isCancelling.set(true); }

    /**
     * Bifásico:
     *  Fase 1: discoverAllModelNames() — recorre páginas hasta agotarlas.
     *  Fase 2: scrapeModelDetails() por modelo — clasifica y persiste.
     * Devuelve un {@link ScrapeResult} con códigos de fallo explícitos.
     */
    public ScrapeResult updateLibraryFull() {
        isCancelling.set(false);
        currentProgress = -1.0;
        currentStatus = "Descubriendo catálogo...";

        // --- FASE 1: DESCUBRIMIENTO ---
        DiscoveryOutcome discovery = discoverAllModelNames();
        lastPagesScanned = discovery.pagesScanned;
        lastModelsDiscovered = discovery.names.size();

        if (isCancelling.get()) {
            currentStatus = "Cancelado.";
            ScrapeResult cancelled = ScrapeResult.failure(
                    ScrapeResult.FailureCode.CANCELLED, "cancelled",
                    discovery.pagesScanned, discovery.names.size());
            lastResult = cancelled;
            return cancelled;
        }

        if (discovery.failure != ScrapeResult.FailureCode.OK) {
            LOGGER.log(Level.SEVERE,
                    "scrape aborted phase=1 pages={0} discovered={1} code={2} detail={3}",
                    new Object[]{discovery.pagesScanned, discovery.names.size(),
                            discovery.failure, discovery.detail});
            currentStatus = "Error de conexión.";
            ScrapeResult fail = ScrapeResult.failure(
                    discovery.failure, discovery.detail,
                    discovery.pagesScanned, discovery.names.size());
            lastResult = fail;
            return fail;
        }

        Set<String> uniqueModels = discovery.names;
        if (uniqueModels.isEmpty()) {
            // Defensive — should have been classified upstream.
            currentStatus = "Error de conexión.";
            ScrapeResult fail = ScrapeResult.failure(
                    ScrapeResult.FailureCode.EMPTY_FIRST_PAGE,
                    "no models discovered",
                    discovery.pagesScanned, 0);
            lastResult = fail;
            return fail;
        }

        int total = uniqueModels.size();
        List<OllamaModel> allFoundModels = new ArrayList<>();
        List<String> sortedList = new ArrayList<>(uniqueModels);
        Collections.sort(sortedList);

        int current = 0;
        int detailsOk = 0;
        int detailsFailed = 0;
        List<String> failedModels = new ArrayList<>();

        for (String name : sortedList) {
            if (isCancelling.get()) {
                currentStatus = "Cancelado.";
                ScrapeResult cancelled = ScrapeResult.builder()
                        .success(false)
                        .failureCode(ScrapeResult.FailureCode.CANCELLED)
                        .pagesScanned(discovery.pagesScanned)
                        .modelsDiscovered(total)
                        .detailsOk(detailsOk)
                        .detailsFailed(detailsFailed)
                        .failedModels(failedModels)
                        .errorMessage("cancelled")
                        .build();
                lastResult = cancelled;
                return cancelled;
            }

            currentStatus = String.format("Procesando (%d/%d): %s", (current + 1), total, name);

            try {
                List<OllamaModel> tags = ollamaManager.scrapeModelDetails(name);
                for (OllamaModel tag : tags) {
                    ModelManager.getInstance().classifyModel(tag);
                }
                if (!tags.isEmpty()) {
                    ModelDetailsCacheManager.getInstance().saveDetails(name, tags);
                    allFoundModels.add(tags.get(0));
                    detailsOk++;
                } else {
                    detailsFailed++;
                    failedModels.add(name);
                }
            } catch (Exception e) {
                detailsFailed++;
                failedModels.add(name);
                LOGGER.log(Level.FINE, "Error procesando modelo " + name + ": " + e.getMessage());
            }

            current++;
            currentProgress = (double) current / total;
        }

        // Selector-drift warning when >10% of details fail
        if (total > 0 && (detailsFailed * 100) / total > 10) {
            LOGGER.log(Level.WARNING,
                    "selector drift detected (failed={0}/{1} = {2}%) — review ScrapingSelectors",
                    new Object[]{detailsFailed, total, (detailsFailed * 100) / total});
        }

        currentStatus = "Guardando librería...";
        currentLibrary.setAllModels(allFoundModels);

        List<OllamaModel> popular = new ArrayList<>(allFoundModels);
        popular.sort((a, b) -> Long.compare(parsePullCount(b.getPullCount()), parsePullCount(a.getPullCount())));
        currentLibrary.setPopularModels(popular.size() > 20 ? popular.subList(0, 20) : popular);

        List<OllamaModel> newest = new ArrayList<>(allFoundModels);
        newest.sort((a, b) -> Long.compare(parseRelativeDate(b.getLastUpdated()), parseRelativeDate(a.getLastUpdated())));
        currentLibrary.setNewModels(newest.size() > 20 ? newest.subList(0, 20) : newest);

        currentLibrary.setLastUpdated(System.currentTimeMillis());
        cacheManager.saveCache(currentLibrary);

        currentStatus = "Completado.";
        currentProgress = 1.0;

        LOGGER.log(Level.INFO,
                "scrape complete pagesScanned={0} modelsDiscovered={1} detailsOk={2} detailsFailed={3}",
                new Object[]{discovery.pagesScanned, total, detailsOk, detailsFailed});

        ScrapeResult ok = ScrapeResult.ok(discovery.pagesScanned, total, detailsOk, detailsFailed, failedModels);
        lastResult = ok;
        return ok;
    }

    // --- internal types ---------------------------------------------------

    private record DiscoveryOutcome(Set<String> names, int pagesScanned,
                                    ScrapeResult.FailureCode failure, String detail) {}

    private record PageScrapeResult(int httpStatus, String matchedSelector,
                                    List<String> names, String contentType, String finalUrl) {}

    private DiscoveryOutcome discoverAllModelNames() {
        // ollama.com no longer supports query-string pagination: ?page=1, ?page=2, …
        // all return the same HTML. The full catalog (~229 models as of 2026-05)
        // is server-rendered in a single response to /library?sort=popular.
        Set<String> uniqueNames = new LinkedHashSet<>();
        currentStatus = "Escaneando catálogo...";

        PageScrapeResult psr;
        try {
            psr = scrapeModelNamesFromPageWithRetry(1);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "catalog network error: " + e.getMessage());
            return new DiscoveryOutcome(uniqueNames, 0,
                    ScrapeResult.FailureCode.NETWORK, e.getMessage());
        }

        if (psr.httpStatus() != 200) {
            return new DiscoveryOutcome(uniqueNames, 1,
                    ScrapeResult.FailureCode.HTTP_ERROR,
                    "HTTP " + psr.httpStatus() + " on " + psr.finalUrl());
        }
        if (psr.contentType() == null || !psr.contentType().toLowerCase().startsWith("text/html")) {
            return new DiscoveryOutcome(uniqueNames, 1,
                    ScrapeResult.FailureCode.HTTP_ERROR,
                    "unexpected content-type=" + psr.contentType());
        }

        for (String n : psr.names()) {
            uniqueNames.add(n);
        }

        if (uniqueNames.isEmpty()) {
            LOGGER.log(Level.SEVERE,
                    "scrape aborted: catalog returned zero models with HTTP 200 — selector mismatch?");
            return new DiscoveryOutcome(uniqueNames, 1,
                    ScrapeResult.FailureCode.EMPTY_FIRST_PAGE,
                    "catalog returned zero models with HTTP 200");
        }

        LOGGER.log(Level.INFO,
                "catalog discovered status=200 selector=\"{0}\" rawLinks={1} uniqueModels={2}",
                new Object[]{psr.matchedSelector(), psr.names().size(), uniqueNames.size()});

        return new DiscoveryOutcome(uniqueNames, 1, ScrapeResult.FailureCode.OK, "");
    }

    private PageScrapeResult scrapeModelNamesFromPageWithRetry(int page) throws IOException {
        try {
            PageScrapeResult first = scrapeModelNamesFromPage(page);
            int status = first.httpStatus();
            if (status == 429 || (status >= 500 && status < 600)) {
                LOGGER.log(Level.WARNING, "retry page {0} after status {1}", new Object[]{page, status});
                try { Thread.sleep(1500); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return scrapeModelNamesFromPage(page);
            }
            return first;
        } catch (IOException e) {
            // single retry on transient network error
            LOGGER.log(Level.WARNING, "retry page {0} after IOException {1}", new Object[]{page, e.getMessage()});
            try { Thread.sleep(1500); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return scrapeModelNamesFromPage(page);
        }
    }

    private PageScrapeResult scrapeModelNamesFromPage(int page) throws IOException {
        // 'page' is kept in the signature for compatibility/logging only; the
        // catalog is single-page server-rendered.
        String url = ScrapingSelectors.LIBRARY_URL + "?sort=popular";
        long t0 = System.currentTimeMillis();

        Connection conn = Jsoup.connect(url)
                .userAgent(ScrapingSelectors.USER_AGENT)
                .timeout(ScrapingSelectors.READ_TIMEOUT_MS)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Accept", "text/html,application/xhtml+xml");

        Connection.Response resp = conn.execute();
        int status = resp.statusCode();
        String contentType = resp.contentType();
        String finalUrl = resp.url() != null ? resp.url().toString() : url;

        if (status != 200 || contentType == null || !contentType.toLowerCase().startsWith("text/html")) {
            return new PageScrapeResult(status, null, new ArrayList<>(), contentType, finalUrl);
        }

        Document doc = resp.parse();
        List<String> names = new ArrayList<>();
        String matchedSelector = null;

        for (String selector : ScrapingSelectors.MODEL_LINK_SELECTORS) {
            Elements links = doc.select(selector);
            if (links.isEmpty()) continue;
            for (Element link : links) {
                String href = link.attr("href");
                if (href == null || href.isEmpty()) continue;
                int idx = href.indexOf("/library/");
                if (idx < 0) continue;
                String name = href.substring(idx + "/library/".length());
                if (name.indexOf('/') >= 0) name = name.substring(0, name.indexOf('/'));
                if (name.indexOf('?') >= 0) name = name.substring(0, name.indexOf('?'));
                if (!name.isEmpty()) names.add(name);
            }
            if (!names.isEmpty()) {
                matchedSelector = selector;
                break;
            }
        }

        long dt = System.currentTimeMillis() - t0;
        LOGGER.log(Level.FINE, "page {0} fetched in {1}ms status={2}", new Object[]{page, dt, status});

        return new PageScrapeResult(status, matchedSelector, names, contentType, finalUrl);
    }

    /** Parse pull count string like "1.2M", "500K", "1000" to long. */
    private long parsePullCount(String pullCount) {
        if (pullCount == null || pullCount.isEmpty()) return 0;
        try {
            String clean = pullCount.toUpperCase().trim();
            double value = Double.parseDouble(clean.replaceAll("[^0-9.]", ""));
            if (clean.contains("M")) return (long) (value * 1_000_000);
            if (clean.contains("K")) return (long) (value * 1_000);
            return (long) value;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Parse relative date like "2 days ago" to timestamp. */
    private long parseRelativeDate(String relativeDate) {
        if (relativeDate == null || relativeDate.isEmpty()) return 0;
        try {
            String lower = relativeDate.toLowerCase().trim();
            long now = System.currentTimeMillis();

            Pattern p = Pattern.compile("(\\d+)\\s+(second|minute|hour|day|week|month|year)");
            Matcher m = p.matcher(lower);

            if (m.find()) {
                int amount = Integer.parseInt(m.group(1));
                String unit = m.group(2);
                long ms = switch (unit) {
                    case "second" -> 1000L;
                    case "minute" -> 60 * 1000L;
                    case "hour" -> 60 * 60 * 1000L;
                    case "day" -> 24 * 60 * 60 * 1000L;
                    case "week" -> 7 * 24 * 60 * 60 * 1000L;
                    case "month" -> 30L * 24 * 60 * 60 * 1000L;
                    case "year" -> 365L * 24 * 60 * 60 * 1000L;
                    default -> 0L;
                };
                return now - (amount * ms);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
