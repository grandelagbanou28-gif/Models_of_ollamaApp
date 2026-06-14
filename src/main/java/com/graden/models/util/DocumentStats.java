package com.graden.models.util;

import com.graden.models.model.AttachedDocument;

/**
 * Formatter for the stats line shown in attached-document pills and chip
 * tooltips. Centralized so the chat bubble and the input strip use the
 * same wording.
 */
public final class DocumentStats {

    private DocumentStats() {
    }

    /** Compact one-liner suitable for tooltips. */
    public static String format(AttachedDocument doc) {
        if (doc == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(doc.getFileName());
        if (doc.getFileSizeBytes() > 0) {
            sb.append(" · ").append(formatBytes(doc.getFileSizeBytes()));
        }
        if (doc.getWordCount() > 0) {
            sb.append(" · ").append(formatThousands(doc.getWordCount())).append(" palabras");
        }
        if (doc.getCharCount() > 0) {
            sb.append(" · ").append(formatThousands(doc.getCharCount())).append(" caracteres");
        }
        return sb.toString();
    }

    /** Short stats fragment used as the pill subtitle (no filename). */
    public static String formatShort(AttachedDocument doc) {
        if (doc == null) return "";
        StringBuilder sb = new StringBuilder();
        if (doc.getFileSizeBytes() > 0) sb.append(formatBytes(doc.getFileSizeBytes()));
        if (doc.getWordCount() > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(formatThousands(doc.getWordCount())).append(" palabras");
        }
        return sb.toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String formatThousands(int n) {
        return String.format("%,d", n);
    }
}
