package net.eneiluj.nextcloud.phonetrack.service;

/**
 * Pure decisions for one location sample of an interval-based log job: whether a fix ends the
 * search, and how long to wait before the next search. Kept free of Android types so it can be
 * unit tested without a device.
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
}
