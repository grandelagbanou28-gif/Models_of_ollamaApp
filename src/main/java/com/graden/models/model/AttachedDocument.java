package com.graden.models.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A document the user attached to a {@link ChatMessage}.
 *
 * <p>The attached content is persisted as part of the chat JSON. This is
 * intentional: it lets the chat controller re-inject the document's text
 * into the prompt on every follow-up question, so the model always has
 * access to it (Ollama does not retain server-side history). Documents
 * are ALSO indexed into the "General" RAG collection so the user can
 * search them across chats, but the inline copy is the primary source
 * truth for THIS chat's context.
 *
 * <p>For very large documents this can bloat the chat JSON; future work
 * may add an opt-out for files above a size threshold.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttachedDocument {

    /** Outcome of indexing the document into the RAG store. */
    public enum RagStatus {
        /** Not attempted yet (e.g. just attached, send not pressed). */
        PENDING,
        /** Document is in Lucene and queryable via RAG. */
        INDEXED,
        /** Inline content is available but RAG indexing failed (e.g. embedding model missing). */
        INLINE_ONLY,
        /** Both inline and RAG failed (rare). */
        FAILED
    }

    private String fileName;
    private String extension;
    private int charCount;
    private int wordCount;
    private long fileSizeBytes;
    private RagStatus ragStatus = RagStatus.PENDING;
    /** True if extracted text is suspiciously short or non-textual (likely scanned PDF). */
    private boolean lowQuality;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String content;

    public AttachedDocument() {
        // For Jackson
    }

    public AttachedDocument(String fileName, String content, long fileSizeBytes) {
        this.fileName = fileName;
        this.extension = extractExtension(fileName);
        this.content = content == null ? "" : content;
        this.charCount = this.content.length();
        this.wordCount = countWords(this.content);
        this.fileSizeBytes = fileSizeBytes;
    }

    private static String extractExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    private static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        // Split on any run of whitespace; "  " counts as one separator.
        return text.trim().split("\\s+").length;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) {
        this.fileName = fileName;
        this.extension = extractExtension(fileName);
    }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public int getCharCount() { return charCount; }
    public void setCharCount(int charCount) { this.charCount = charCount; }

    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public RagStatus getRagStatus() { return ragStatus == null ? RagStatus.PENDING : ragStatus; }
    public void setRagStatus(RagStatus ragStatus) { this.ragStatus = ragStatus; }

    public boolean isLowQuality() { return lowQuality; }
    public void setLowQuality(boolean lowQuality) { this.lowQuality = lowQuality; }

    public String getContent() { return content; }
    public void setContent(String content) {
        this.content = content;
        if (content != null) {
            this.charCount = content.length();
            this.wordCount = countWords(content);
        }
    }
}
