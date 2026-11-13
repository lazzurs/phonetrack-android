package net.eneiluj.nextcloud.phonetrack.service;

/**
 * Pure decisions for the location samples of interval-based log jobs: whether a fix ends the
 * search, and the location timeout defaults. Kept free of Android types so it can be unit tested
 * without a device.
 */
public final class LocationSamplePolicy {

    /** What to do with a fix received while a sample is being searched for. */
    public enum Decision {
        /** Good enough and far enough from the last logged point: log it and end the search. */
        ACCEPT,
        /**
         * Accurate, but closer than the minimum distance to the last logged point: the phone has
         * not moved enough, so end the search without logging anything.
         */
        STATIONARY,
        /** Not accurate enough: keep searching until the timeout, if any. */
        KEEP_SEARCHING
    }

    /** Default location timeout for new log jobs never exceeds this. */
    static final int MAX_DEFAULT_TIMEOUT_SECONDS = 30;

    /** Location timeout given to log jobs created by SMS command never exceeds this. */
    static final int MAX_SMS_TIMEOUT_SECONDS = 60;

    private LocationSamplePolicy() {
    }

    /**
     * @param accuracyMetres accuracy radius of the fix (0 when unknown, which passes)
     * @param maxAccuracyMetres the log job's minimum accuracy setting (largest allowed radius)
     * @param hasLastLogged whether a point has been logged since the job started
     * @param distanceFromLastLoggedMetres distance to the last logged point, ignored without one
     * @param minDistanceMetres the log job's minimum distance setting, 0 to log every fix
     */
    public static Decision decide(float accuracyMetres, int maxAccuracyMetres, boolean hasLastLogged,
                                  double distanceFromLastLoggedMetres, int minDistanceMetres) {
        if (accuracyMetres > maxAccuracyMetres) {
            return Decision.KEEP_SEARCHING;
        }
        if (minDistanceMetres == 0 || !hasLastLogged || distanceFromLastLoggedMetres >= minDistanceMetres) {
            return Decision.ACCEPT;
        }
        return Decision.STATIONARY;
    }

    /**
     * Location timeout offered for a new log job when the user has not saved one before: long
     * enough for a GPS fix, short enough that GPS is not on for most of every interval when no
     * fix can be had (the old default was 59 s on a 60 s interval).
     */
    public static int defaultTimeoutSeconds(int intervalSeconds) {
        return Math.max(0, Math.min(MAX_DEFAULT_TIMEOUT_SECONDS, intervalSeconds / 2));
    }

    /**
     * Share of the time GPS can be on while no fix can be had, in percent, when that is worth a
     * warning in the edit screen (the timeout is more than half the interval), otherwise 0.
     */
    public static int gpsOnPercentWithoutFixWarning(int intervalSeconds, int timeoutSeconds) {
        if (intervalSeconds <= 0 || timeoutSeconds <= 0 || timeoutSeconds * 2 <= intervalSeconds) {
            return 0;
        }
        return (int) Math.min(100, Math.round(100.0 * timeoutSeconds / intervalSeconds));
    }

    /**
     * Location timeout for a log job created by SMS command. Such a job cannot be tuned on the
     * phone when it is created, so it gets as long a search as the edit screen allows (the timeout
     * must stay below the interval), up to {@link #MAX_SMS_TIMEOUT_SECONDS}, instead of no timeout,
     * which kept GPS on for as long as no fix could be had.
     */
    public static int smsTimeoutSeconds(int intervalSeconds) {
        return Math.max(0, Math.min(MAX_SMS_TIMEOUT_SECONDS, intervalSeconds - 1));
    }
}
