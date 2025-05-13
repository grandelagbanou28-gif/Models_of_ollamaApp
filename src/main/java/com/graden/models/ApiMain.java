package com.graden.models;

import com.graden.models.api.ApiConfig;
import com.graden.models.api.ApiServer;
import com.graden.models.manager.ModelLibraryManager;
import com.graden.models.manager.ModelManager;

/**
 * Standalone entry point to run only the REST API server,
 * without the JavaFX UI. Useful for headless / server deployments.
 *
 * Usage:
 *   java -cp GradenModels.jar com.graden.models.ApiMain [port]
 */
public class ApiMain {

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : ApiConfig.getPort();
        ApiConfig.setPort(port);
        ApiConfig.setEnabled(true);

        System.out.println("Starting Graden Models API server on port " + port + "...");

        // Initialize managers (triggers local model fetch)
        ModelManager.getInstance();
        ModelLibraryManager.getInstance();

        ApiServer.getInstance().start();

        System.out.println("API server running at http://127.0.0.1:" + port);
        System.out.println("Endpoints:");
        System.out.println("  GET  /api/health");
        System.out.println("  GET  /api/models");
        System.out.println("  GET  /api/models/local");
        System.out.println("  POST /api/models/pull  {\"model\":\"name:tag\"}");
        System.out.println("  GET  /api/models/pull/{id}");
        System.out.println("  DELETE /api/models/{name}");
        System.out.println("  POST /api/chat  {\"model\":\"...\",\"prompt\":\"...\",\"system\":\"...\",\"stream\":false}");
        System.out.println();
        System.out.println("Press Ctrl+C to stop.");

        // Keep alive
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ApiServer.getInstance().stop();
            System.out.println("API server stopped.");
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
