package com.graden.models.model;

/**
 * Metrics captured from the final Ollama streaming response.
 *
 * @param evalCount     number of tokens generated (eval_count)
 * @param evalDurationNs duration in nanoseconds spent generating (eval_duration)
 */
public record GenerationMetrics(int evalCount, long evalDurationNs) {

    public double elapsedSeconds() {
        return evalDurationNs / 1_000_000_000.0;
    }

    public double tokensPerSecond() {
        if (evalDurationNs <= 0 || evalCount <= 0) return 0;
        return evalCount / elapsedSeconds();
    }
}
