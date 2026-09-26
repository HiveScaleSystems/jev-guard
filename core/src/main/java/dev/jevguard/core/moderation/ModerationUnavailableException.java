package dev.jevguard.core.moderation;

/** Thrown when the circuit breaker is open and no request was sent. */
public final class ModerationUnavailableException extends RuntimeException {
    public ModerationUnavailableException(String message) {
        super(message);
    }
}
