package com.graden.models.model;

/**
 * Representa una versión (release) obtenida desde la API de GitHub.
 */
public record GitHubRelease(String tagName, String publishedAt, String body) {
}
