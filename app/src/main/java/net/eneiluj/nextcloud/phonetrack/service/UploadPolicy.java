package net.eneiluj.nextcloud.phonetrack.service;

/**
 * Decides whether the positions waiting on the phone are uploaded now or held back.
 * Combines the two "Power/data saving" settings, either of which is enough to upload:
 * "send points by bunch of at least N" and "send at the latest after N hours".
 * Pure logic (no Android, no clock) so that it can be unit tested.
 */
public final class UploadPolicy {

    public enum Action {
        /** no position is waiting */
        NOTHING,
        UPLOAD_NOW,
        /** hold the positions back, until {@link Decision#deadline} if there is one */
        WAIT
    }

    public static final class Decision {
        public final Action action;
        /** when the held back positions have to be sent anyway (epoch seconds), 0 = no time limit */
        public final long deadline;

        private Decision(Action action, long deadline) {
            this.action = action;
            this.deadline = deadline;
        }

        public boolean hasDeadline() {
            return deadline > 0;
        }
    }

    private static final Decision NOTHING = new Decision(Action.NOTHING, 0);
    private static final Decision UPLOAD_NOW = new Decision(Action.UPLOAD_NOW, 0);

    private UploadPolicy() {
    }

    /**
     * @param pending           number of positions waiting for upload
     * @param oldestPendingTime time of the oldest of them (epoch seconds)
     * @param now               current time (epoch seconds)
     * @param minBunch          "send points by bunch of at least" setting; 0 and 1 both mean every point
     * @param maxDelaySeconds   "send at the latest after" setting, 0 = no time limit
     * @param force             manual sync or deadline run: ignore the bunch size
     */
    public static Decision decide(int pending, long oldestPendingTime, long now,
                                  long minBunch, long maxDelaySeconds, boolean force) {
        if (pending <= 0) {
            return NOTHING;
        }
        if (force || pending >= Math.max(minBunch, 1)) {
            return UPLOAD_NOW;
        }
        if (maxDelaySeconds <= 0) {
            return new Decision(Action.WAIT, 0);
        }
        // "no point stays on the phone longer than the limit". A position dated in the future
        // (wrong clock) must not push the deadline further away than the limit from now.
        long deadline = Math.min(oldestPendingTime, now) + maxDelaySeconds;
        if (now >= deadline) {
            return UPLOAD_NOW;
        }
        return new Decision(Action.WAIT, deadline);
    }
}
