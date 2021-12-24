package com.graden.models.manager;

import java.util.Collections;
import java.util.List;

/**
 * Immutable DTO describing the outcome of a library scrape run.
 * Lives entirely in memory; never persisted to disk.
 *
 * @see ModelLibraryManager#updateLibraryFull()
 */
public final class ScrapeResult {

    public enum FailureCode {
        OK,
        HTTP_ERROR,
        EMPTY_FIRST_PAGE,
        SELECTOR_MISMATCH,
        NETWORK,
        CANCELLED
    }

    private final boolean success;
    private final FailureCode failureCode;
    private final int pagesScanned;
    private final int modelsDiscovered;
    private final int detailsOk;
    private final int detailsFailed;
    private final List<String> failedModels;
    private final String errorMessage;
    private final long timestamp;

    private ScrapeResult(Builder b) {
        this.success = b.success;
        this.failureCode = b.failureCode == null ? FailureCode.OK : b.failureCode;
        this.pagesScanned = b.pagesScanned;
        this.modelsDiscovered = b.modelsDiscovered;
        this.detailsOk = b.detailsOk;
        this.detailsFailed = b.detailsFailed;
        this.failedModels = b.failedModels == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(b.failedModels);
        this.errorMessage = b.errorMessage == null ? "" : b.errorMessage;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isSuccess() { return success; }
    public FailureCode getFailureCode() { return failureCode; }
    public int getPagesScanned() { return pagesScanned; }
    public int getModelsDiscovered() { return modelsDiscovered; }
    public int getDetailsOk() { return detailsOk; }
    public int getDetailsFailed() { return detailsFailed; }
    public List<String> getFailedModels() { return failedModels; }
    public String getErrorMessage() { return errorMessage; }
    public long getTimestamp() { return timestamp; }

    public static Builder builder() { return new Builder(); }

    public static ScrapeResult ok(int pages, int models, int detailsOk, int detailsFailed, List<String> failedModels) {
        return builder()
                .success(true)
                .failureCode(FailureCode.OK)
                .pagesScanned(pages)
                .modelsDiscovered(models)
                .detailsOk(detailsOk)
                .detailsFailed(detailsFailed)
                .failedModels(failedModels)
                .build();
    }

    public static ScrapeResult failure(FailureCode code, String msg, int pages, int models) {
        return builder()
                .success(false)
                .failureCode(code)
                .errorMessage(msg)
                .pagesScanned(pages)
                .modelsDiscovered(models)
                .build();
    }

    public static final class Builder {
        private boolean success;
        private FailureCode failureCode = FailureCode.OK;
        private int pagesScanned;
        private int modelsDiscovered;
        private int detailsOk;
        private int detailsFailed;
        private List<String> failedModels;
        private String errorMessage;

        public Builder success(boolean v) { this.success = v; return this; }
        public Builder failureCode(FailureCode v) { this.failureCode = v; return this; }
        public Builder pagesScanned(int v) { this.pagesScanned = v; return this; }
        public Builder modelsDiscovered(int v) { this.modelsDiscovered = v; return this; }
        public Builder detailsOk(int v) { this.detailsOk = v; return this; }
        public Builder detailsFailed(int v) { this.detailsFailed = v; return this; }
        public Builder failedModels(List<String> v) { this.failedModels = v; return this; }
        public Builder errorMessage(String v) { this.errorMessage = v; return this; }
        public ScrapeResult build() { return new ScrapeResult(this); }
    }
}
