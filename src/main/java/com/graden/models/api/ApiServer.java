package com.graden.models.api;

import com.graden.models.api.handlers.*;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class ApiServer {

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());
    private static ApiServer instance;
    private HttpServer server;
    private boolean running;

    private ApiServer() {}

    public static synchronized ApiServer getInstance() {
        if (instance == null) instance = new ApiServer();
        return instance;
    }

    public synchronized void start() {
        if (running) return;
        int port = ApiConfig.getPort();
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "api-worker");
                t.setDaemon(true);
                return t;
            }));

            // Routes
            server.createContext("/api/health", new HealthHandler());
            server.createContext("/api/models/pull", new ModelPullHandler());
            server.createContext("/api/models/local", new ModelsHandler());
            server.createContext("/api/models", new ModelsHandler());
            server.createContext("/api/chat", new ChatHandler());

            server.start();
            running = true;
            LOG.info("API server started on http://127.0.0.1:" + port);
        } catch (IOException e) {
            LOG.severe("Failed to start API server on port " + port + ": " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (server != null && running) {
            server.stop(1);
            running = false;
            LOG.info("API server stopped");
        }
    }

    public synchronized void restart() {
        stop();
        if (ApiConfig.isEnabled()) start();
    }

    public boolean isRunning() {
        return running;
    }
}
