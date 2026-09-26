package dev.jevguard.core.jev;

public final class JevApiException extends RuntimeException {
    private final int status;

    public JevApiException(int status, String message) {
        super("API returned " + status + ": " + message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public boolean retryable() {
        return status == 429 || status == 529 || status >= 500;
    }
}
