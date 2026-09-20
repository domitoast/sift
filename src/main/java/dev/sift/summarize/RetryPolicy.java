package dev.sift.summarize;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with jitter. Jitter matters: without it, a batch of
 * failures retries in lockstep and hits the provider as one spike.
 */
@Component
public class RetryPolicy {
    private final int maxAttempts;
    private final Duration baseDelay;
    private final double jitterRatio;

    public RetryPolicy(
            @Value("${sift.summarize.retry.max-attempts}") int maxAttempts,
            @Value("${sift.summarize.retry.base-delay}") Duration baseDelay,
            @Value("${sift.summarize.retry.jitter-ratio}") double jitterRatio) {
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.jitterRatio = jitterRatio;
    }

    public boolean shouldRetry(int currentRetryCount) {
        return currentRetryCount < maxAttempts;
    }

    public Instant nextRetryAt(int currentRetryCount) {
        long baseMillis = baseDelay.toMillis() * (1L << currentRetryCount);

        double factor = jitterRatio <= 0
                ? 1.0
                : 1.0 + ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio);

        long delayMillis = Math.round(baseMillis * factor);

        return Instant.now().plusMillis(delayMillis);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
