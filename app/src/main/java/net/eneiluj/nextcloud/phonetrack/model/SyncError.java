package net.eneiluj.nextcloud.phonetrack.model;

/** Last upload error of a log job; timestamp 0 when its last upload succeeded. */
public class SyncError {
    private final long timestamp;
    private final String message;
    private final int consecutiveFailures;

    public SyncError(long timestamp, String message, int consecutiveFailures) {
        this.timestamp = timestamp;
        this.message = message;
        this.consecutiveFailures = consecutiveFailures;
    }

    /** Seconds since the epoch, 0 if there is no error */
    public long getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    /** Uploads that failed in a row since the last successful one */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public boolean isFailing() {
        return timestamp != 0;
    }
}
