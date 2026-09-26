package dev.jevguard.core.moderation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Stops calling the API after repeated failures, then tries again after a cooldown. */
public final class CircuitBreaker {
    private final int failureThreshold;
    private final Duration cooldown;
    private final Clock clock;
    private int consecutiveFailures;
    private Instant openUntil = Instant.MIN;

    public CircuitBreaker(int failureThreshold, Duration cooldown, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.cooldown = cooldown;
        this.clock = clock;
    }

    public synchronized boolean allowRequest() {
        return !clock.instant().isBefore(openUntil);
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
    }

    public synchronized void recordFailure() {
        if (++consecutiveFailures >= failureThreshold) {
            openUntil = clock.instant().plus(cooldown);
            consecutiveFailures = 0;
        }
    }

    public synchronized boolean isOpen() {
        return !allowRequest();
    }
}
