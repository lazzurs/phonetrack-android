package net.eneiluj.nextcloud.phonetrack.util;

import android.location.Location;

/**
 * Wraps the Location class to handle fix time from buggy sensors
 * that doesn't do correct GPS week number rollover.
 */
public class CorrectingLocation extends Location {
    private static final long ROLLOVER_CYCLE_IN_SECONDS = 1024*7*24*60*60;
    private long time;

    public CorrectingLocation(Location location) {
        super(location);
        time = location.getTime();
        long now = System.currentTimeMillis() / 1000;
        // Add 1024 weeks of seconds if timestamp is off
        if (time < now - (ROLLOVER_CYCLE_IN_SECONDS / 2)) {
            time += ROLLOVER_CYCLE_IN_SECONDS;
        }
    }

    @Override
    public long getTime() {
        return time;
    }
}
