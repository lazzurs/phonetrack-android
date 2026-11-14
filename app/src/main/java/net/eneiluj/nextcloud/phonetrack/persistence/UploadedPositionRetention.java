package net.eneiluj.nextcloud.phonetrack.persistence;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;

/**
 * "Keep uploaded positions on this phone": how long positions of the current run stay in the
 * database once a server has accepted them. The default keeps them forever, as before.
 * <p>
 * Only positions that were uploaded are ever deleted, and only those of PhoneTrack and Nextcloud
 * Maps log jobs, whose server keeps the track. A custom log job sends to an address the app knows
 * nothing about (it may only keep the latest position), so the phone may have the only full copy:
 * its positions are always kept. Positions that were never uploaded are always kept too, so a log
 * job that cannot upload (no account, a server that refuses them) loses nothing.
 * <p>
 * The deletion runs in {@link PhoneTrackSQLiteOpenHelper#markSynced}, in the transaction that
 * records an accepted upload: no timer or wake-up of its own.
 */
public final class UploadedPositionRetention {

    /** Setting value for "Forever". */
    public static final int KEEP_FOREVER = 0;
    private static final int DAYS_PER_YEAR = 365;
    private static final long SECONDS_PER_DAY = 24 * 60 * 60;

    private UploadedPositionRetention() {
    }

    /** The setting, in days; {@link #KEEP_FOREVER} when unset or not understood. */
    public static int keepDays(@NonNull Context context) {
        String value = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.pref_key_keep_uploaded), null);
        return parseDays(value);
    }

    /** The setting value as days; {@link #KEEP_FOREVER} for null, negative or not a number. */
    public static int parseDays(@Nullable String value) {
        if (value == null) {
            return KEEP_FOREVER;
        }
        try {
            int days = Integer.parseInt(value.trim());
            return Math.max(days, KEEP_FOREVER);
        } catch (NumberFormatException e) {
            return KEEP_FOREVER;
        }
    }

    /**
     * Whether the setting may delete uploaded positions of this log job: PhoneTrack and Nextcloud
     * Maps log jobs only, told apart as {@code WebTrackSync} does when it uploads them.
     */
    public static boolean appliesTo(@NonNull DBLogjob logjob) {
        boolean maps = logjob.getDeviceName().isEmpty() && logjob.getToken().isEmpty()
                && logjob.getUrl().isEmpty();
        boolean phoneTrack = !logjob.getDeviceName().isEmpty() && !logjob.getToken().isEmpty();
        return maps || phoneTrack;
    }

    /** Days of uploaded positions this log job keeps, {@link #KEEP_FOREVER} if it keeps them all. */
    public static int keepDays(@NonNull Context context, @NonNull DBLogjob logjob) {
        return appliesTo(logjob) ? keepDays(context) : KEEP_FOREVER;
    }

    /**
     * Uploaded positions of this log job recorded before this time (epoch seconds) are deleted,
     * or 0 if none are.
     */
    static long oldestKeptTime(@NonNull Context context, @NonNull DBLogjob logjob, long nowSeconds) {
        int days = keepDays(context, logjob);
        return days == KEEP_FOREVER ? 0 : nowSeconds - days * SECONDS_PER_DAY;
    }

    /** "1 year", "90 days": how long positions are kept, to use in a sentence. */
    @NonNull
    public static String describe(@NonNull Context context, int days) {
        Resources res = context.getResources();
        if (days % DAYS_PER_YEAR == 0) {
            int years = days / DAYS_PER_YEAR;
            return res.getQuantityString(R.plurals.settings_keep_uploaded_years, years, years);
        }
        return res.getQuantityString(R.plurals.settings_keep_uploaded_days, days, days);
    }
}
