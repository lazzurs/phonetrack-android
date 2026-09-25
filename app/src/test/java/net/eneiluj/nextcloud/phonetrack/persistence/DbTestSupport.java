package net.eneiluj.nextcloud.phonetrack.persistence;

/** Lets tests outside this package reset the database singleton. */
public final class DbTestSupport {
    private DbTestSupport() {
    }

    public static void resetDatabase() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
    }
}
