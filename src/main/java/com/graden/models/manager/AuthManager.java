package com.graden.models.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private static AuthManager instance;
    private final File usersFile;
    private final ObjectMapper objectMapper;
    private final Map<String, UserAccount> users;
    private UserAccount currentUser;
    private boolean loggedIn;

    private AuthManager() {
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, ".GrandelGradenNexus");
        if (!configDir.exists()) configDir.mkdirs();

        usersFile = new File(configDir, "users.json");
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        users = new ConcurrentHashMap<>();
        loadUsers();
    }

    public static synchronized AuthManager getInstance() {
        if (instance == null) {
            instance = new AuthManager();
        }
        return instance;
    }

    private void loadUsers() {
        if (!usersFile.exists()) return;
        try {
            Map<String, UserAccount> loaded = objectMapper.readValue(usersFile,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, UserAccount.class));
            if (loaded != null) users.putAll(loaded);
        } catch (Exception e) {
            System.err.println("Failed to load users: " + e.getMessage());
        }
    }

    private void saveUsers() {
        try {
            objectMapper.writeValue(usersFile, users);
        } catch (Exception e) {
            System.err.println("Failed to save users: " + e.getMessage());
        }
    }

    public boolean isEmailRegistered(String email) {
        return users.containsKey(email.toLowerCase().trim());
    }

    public String register(String email, String password) {
        String key = email.toLowerCase().trim();
        if (key.isEmpty() || password.isEmpty()) return "Email and password are required";
        if (users.containsKey(key)) return "An account with this email already exists";
        if (password.length() < 4) return "Password must be at least 4 characters";

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String hash = hashPassword(password, salt);

        UserAccount account = new UserAccount(email.trim(), hash, Base64.getEncoder().encodeToString(salt));
        users.put(key, account);
        saveUsers();
        return null;
    }

    public String login(String email, String password) {
        String key = email.toLowerCase().trim();
        UserAccount account = users.get(key);
        if (account == null) return "No account found with this email";

        byte[] salt = Base64.getDecoder().decode(account.salt);
        String hash = hashPassword(password, salt);
        if (!hash.equals(account.passwordHash)) return "Incorrect password";

        currentUser = account;
        loggedIn = true;
        return null;
    }

    public void logout() {
        currentUser = null;
        loggedIn = false;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public UserAccount getCurrentUser() {
        return currentUser;
    }

    public String getCurrentUserEmail() {
        return currentUser != null ? currentUser.email : null;
    }

    private String hashPassword(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    public static class UserAccount {
        public String email;
        public String passwordHash;
        public String salt;

        public UserAccount() {}

        public UserAccount(String email, String passwordHash, String salt) {
            this.email = email;
            this.passwordHash = passwordHash;
            this.salt = salt;
        }
    }
}
