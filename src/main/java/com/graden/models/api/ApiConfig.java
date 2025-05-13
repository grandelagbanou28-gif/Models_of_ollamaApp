package com.graden.models.api;

import com.graden.models.manager.ConfigManager;

public class ApiConfig {

    private static final String PORT_KEY = "api_port";
    private static final String ENABLED_KEY = "api_enabled";
    private static final int DEFAULT_PORT = 9876;

    private ApiConfig() {}

    public static int getPort() {
        String val = ConfigManager.getInstance().getPreference(PORT_KEY, String.valueOf(DEFAULT_PORT));
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    public static void setPort(int port) {
        ConfigManager.getInstance().setPreference(PORT_KEY, String.valueOf(port));
    }

    public static boolean isEnabled() {
        return "true".equals(ConfigManager.getInstance().getPreference(ENABLED_KEY, "false"));
    }

    public static void setEnabled(boolean enabled) {
        ConfigManager.getInstance().setPreference(ENABLED_KEY, String.valueOf(enabled));
    }
}
