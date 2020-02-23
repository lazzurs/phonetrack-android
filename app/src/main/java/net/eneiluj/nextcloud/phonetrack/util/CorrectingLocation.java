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
        this.time = location.getTime();
        // Add 1024 weeks of seconds if timestamp is off
        if (this.time < 1_000_000_000) {
            this.time += 1024*7*24*60*60;
        }
    }

    @Override
    public long getTime() {
        return time;
    }
}
