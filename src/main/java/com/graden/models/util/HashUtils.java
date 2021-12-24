package com.graden.models.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stream-based file hashing. Used by the RAG pipeline to deduplicate
 * documents by content (not by file name), so the same file dropped from
 * two paths or renamed is detected and rejected.
 */
public final class HashUtils {

    private static final int BUFFER_SIZE = 8 * 1024;

    private HashUtils() {
    }

    /** SHA-256 of the file's content as lowercase hex. */
    public static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available in this JVM", e);
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(file.toPath())) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    /** SHA-256 of an arbitrary string (UTF-8). Used for in-memory snippets. */
    public static String sha256(String content) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available in this JVM", e);
        }
        digest.update(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
