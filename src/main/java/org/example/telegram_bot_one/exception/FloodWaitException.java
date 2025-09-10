package org.example.telegram_bot_one.exception;

public class FloodWaitException extends RuntimeException {
    private final int retrySeconds;

    public FloodWaitException(int retrySeconds) {
        super("Flood wait " + retrySeconds + "s");
        this.retrySeconds = retrySeconds;
    }

    public int getRetrySeconds() {
        return retrySeconds;
    }
}
