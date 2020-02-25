package net.eneiluj.nextcloud.phonetrack.util;

import android.location.Location;

import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Wraps the Location class to handle fix time from buggy sensors
 * that doesn't do correct GPS week number rollover.
 */
public class CorrectingLocation extends Location {
    private static final Date FIRST_ROLLOVER_DATE = new GregorianCalendar(1999, 8, 21).getTime();
    private static final Date SECOND_ROLLOVER_DATE = new GregorianCalendar(2019, 4, 6).getTime();
    private static final long ROLLOVER_CYCLE_IN_SECONDS = 1024*7*24*60*60;
    private long time;

    public CorrectingLocation(Location location) {
        super(location);
        time = location.getTime();
        // Add 1024 weeks of seconds if timestamp is off
        if ((time > FIRST_ROLLOVER_DATE.getTime())
                && (time < SECOND_ROLLOVER_DATE.getTime())) {
            time += ROLLOVER_CYCLE_IN_SECONDS;
        }
    }

    @Override
    public long getTime() {
        return time;
    }
}
