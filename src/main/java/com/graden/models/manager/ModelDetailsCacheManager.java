package com.graden.models.manager;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graden.models.model.ModelDetailsCache;
import com.graden.models.model.ModelDetailsEntry;
import com.graden.models.model.OllamaModel;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ModelDetailsCacheManager {

    private static final String CACHE_FILE_NAME = "details_cache.json";

    private final File cacheFile;
    private final ObjectMapper mapper;

    private ModelDetailsCache memoryCache;

    // Singleton instance
    private static ModelDetailsCacheManager instance;

    private ModelDetailsCacheManager() {
        String userHome = System.getProperty("user.home");
        File appDir = new File(userHome, ".GradenModels");
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        this.cacheFile = new File(appDir, CACHE_FILE_NAME);

        // Configure ObjectMapper to only use getters, not fields
        // This prevents serialization of JavaFX StringProperty fields
        this.mapper = new ObjectMapper();
        this.mapper.setVisibility(
                this.mapper.getSerializationConfig()
                        .getDefaultVisibilityChecker()
                        .withFieldVisibility(JsonAutoDetect.Visibility.NONE)
                        .withGetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                        .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));

        this.memoryCache = loadCacheFromDisk();
        if (this.memoryCache == null) {
            this.memoryCache = new ModelDetailsCache();
        }
    }

    public static synchronized ModelDetailsCacheManager getInstance() {
        if (instance == null) {
            instance = new ModelDetailsCacheManager();
        }
        return instance;
    }

    private synchronized ModelDetailsCache loadCacheFromDisk() {
        if (!cacheFile.exists()) {
            return new ModelDetailsCache();
        }
        try {
            return mapper.readValue(cacheFile, ModelDetailsCache.class);
        } catch (IOException e) {
            System.err.println("ModelDetailsCacheManager: Cache corrupto, eliminando archivo: " + e.getMessage());
            // DELETE corrupt file so fresh cache can be created
            cacheFile.delete();
            return new ModelDetailsCache();
        }
    }

    public synchronized void saveCache() {
        try {
            mapper.writeValue(cacheFile, memoryCache);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ModelDetailsEntry getDetails(String modelName) {
        return memoryCache.getEntry(modelName);
    }

    public void saveDetails(String modelName, List<OllamaModel> tags) {
        ModelDetailsEntry entry = new ModelDetailsEntry(tags,
                System.currentTimeMillis());
        memoryCache.addEntry(modelName, entry);
        saveCache(); // Persist immediately (or could debounce)
    }

    /**
     * Drops both the in-memory and on-disk per-model details cache. Used by
     * Settings → Refresh Library to guarantee a fresh scrape.
     */
    public synchronized void invalidate() {
        this.memoryCache = new ModelDetailsCache();
        if (cacheFile.exists()) {
            boolean deleted = cacheFile.delete();
            if (!deleted) {
                System.err.println("ModelDetailsCacheManager: invalidate() delete failed, overwriting empty");
                saveCache();
            }
        }
    }
}
