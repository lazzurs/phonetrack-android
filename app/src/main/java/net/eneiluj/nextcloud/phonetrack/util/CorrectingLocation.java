package net.eneiluj.nextcloud.phonetrack.util;

import android.location.Location;

/**
 * Wraps the Location class to handle fix time from buggy sensors
 * that doesn't do correct GPS week number rollover.
 */
public class CorrectingLocation extends Location {
    private long time;

    public CorrectingLocation(Location location) {
        super(location);
        long now = System.currentTimeMillis();
        // use current time if time of the fix is more than a week behind
        if (location.getTime() < (now - 7*24*3600*1000)) {
            this.time = now;
        } else {
            this.time = location.getTime();
        }
    }

    @Override
    public long getTime() {
        return time;
    }
}
