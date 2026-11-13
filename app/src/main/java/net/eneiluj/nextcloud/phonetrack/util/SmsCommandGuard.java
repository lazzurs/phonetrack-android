package net.eneiluj.nextcloud.phonetrack.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.PhoneNumberUtils;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Limits on SMS commands beyond the sender allowlist, which is not authentication on its
 * own: the sender number of an SMS can be forged.
 *
 * - PIN: wrong PINs are counted per sender; after {@link #MAX_WRONG_PINS} in a row that sender
 *   is ignored, for {@link #FIRST_LOCKOUT_MS} the first time and twice as long each time after,
 *   up to {@link #MAX_LOCKOUT_MS}. A correct PIN, or a new PIN in the settings, starts over.
 * - Rate limit: at most {@link #MAX_COMMANDS_PER_HOUR} commands per sender per hour, and one
 *   position request per sender per {@link #POSITION_REQUEST_INTERVAL_MS}, since every
 *   command is answered by SMS at the user's cost.
 *
 * The state is kept in its own preferences file so it survives a process restart.
 */
public final class SmsCommandGuard {

    @VisibleForTesting
    static final String FILE_NAME = "sms_command_guard";

    public static final int MAX_WRONG_PINS = 3;
    public static final long FIRST_LOCKOUT_MS = 15 * 60 * 1000L;
    public static final long MAX_LOCKOUT_MS = 24 * 60 * 60 * 1000L;
    public static final int MAX_COMMANDS_PER_HOUR = 10;
    public static final long POSITION_REQUEST_INTERVAL_MS = 60 * 1000L;
    private static final long HOUR_MS = 60 * 60 * 1000L;

    private static final String KEY_WRONG_PINS = "wrongPins_";
    private static final String KEY_LOCKOUTS = "lockouts_";
    private static final String KEY_LOCKED_UNTIL = "lockedUntil_";
    private static final String KEY_COMMANDS = "commands_";
    private static final String KEY_LAST_POSITION = "lastPosition_";

    private static LongSupplier clock = System::currentTimeMillis;

    private SmsCommandGuard() {
    }

    /** @param testClock milliseconds since the epoch, or null for the system clock */
    @VisibleForTesting
    public static void setClockForTesting(@Nullable LongSupplier testClock) {
        clock = testClock == null ? System::currentTimeMillis : testClock;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /** The same number written differently must share one counter. */
    private static String key(String prefix, String sender) {
        String normalised = PhoneNumberUtils.normalizeNumber(sender);
        return prefix + (normalised == null || normalised.isEmpty() ? sender : normalised);
    }

    /** Compares in constant time, so the reply timing says nothing about the PIN. */
    public static boolean pinMatches(String expected, String given) {
        try {
            // hashing first makes the comparison independent of the lengths too
            byte[] a = MessageDigest.getInstance("SHA-256").digest(expected.getBytes(StandardCharsets.UTF_8));
            byte[] b = MessageDigest.getInstance("SHA-256").digest(given.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(a, b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isLockedOut(Context context, String sender) {
        return clock.getAsLong() < prefs(context).getLong(key(KEY_LOCKED_UNTIL, sender), 0);
    }

    /** @return true if this wrong PIN locked the sender out */
    @SuppressLint("ApplySharedPref") // commit: a burst of SMS must not outrun the count
    public static boolean recordWrongPin(Context context, String sender) {
        SharedPreferences prefs = prefs(context);
        int wrongPins = prefs.getInt(key(KEY_WRONG_PINS, sender), 0) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        boolean lockedOut = wrongPins >= MAX_WRONG_PINS;
        if (lockedOut) {
            int lockouts = prefs.getInt(key(KEY_LOCKOUTS, sender), 0) + 1;
            editor.putInt(key(KEY_LOCKOUTS, sender), lockouts)
                    .putLong(key(KEY_LOCKED_UNTIL, sender), clock.getAsLong() + lockoutDuration(lockouts))
                    .remove(key(KEY_WRONG_PINS, sender));
        } else {
            editor.putInt(key(KEY_WRONG_PINS, sender), wrongPins);
        }
        editor.commit();
        return lockedOut;
    }

    @VisibleForTesting
    static long lockoutDuration(int lockouts) {
        long duration = FIRST_LOCKOUT_MS;
        for (int i = 1; i < lockouts && duration < MAX_LOCKOUT_MS; i++) {
            duration *= 2;
        }
        return Math.min(duration, MAX_LOCKOUT_MS);
    }

    public static void recordCorrectPin(Context context, String sender) {
        SharedPreferences prefs = prefs(context);
        if (prefs.contains(key(KEY_WRONG_PINS, sender)) || prefs.contains(key(KEY_LOCKOUTS, sender))
                || prefs.contains(key(KEY_LOCKED_UNTIL, sender))) {
            prefs.edit()
                    .remove(key(KEY_WRONG_PINS, sender))
                    .remove(key(KEY_LOCKOUTS, sender))
                    .remove(key(KEY_LOCKED_UNTIL, sender))
                    .apply();
        }
    }

    /**
     * Counts a command against the sender's rate limit.
     *
     * @return false if the sender has sent too many commands: ignore this one
     */
    @SuppressLint("ApplySharedPref") // commit: a burst of SMS must not outrun the count
    public static boolean tryAcquire(Context context, String sender, boolean positionRequest) {
        SharedPreferences prefs = prefs(context);
        long now = clock.getAsLong();
        if (positionRequest && prefs.contains(key(KEY_LAST_POSITION, sender))) {
            long last = prefs.getLong(key(KEY_LAST_POSITION, sender), 0);
            // a clock set back must not block requests for good
            if (last <= now && now - last < POSITION_REQUEST_INTERVAL_MS) {
                return false;
            }
        }
        List<Long> recent = new ArrayList<>();
        for (String s : prefs.getString(key(KEY_COMMANDS, sender), "").split(",")) {
            try {
                long t = Long.parseLong(s);
                if (t <= now && now - t < HOUR_MS) {
                    recent.add(t);
                }
            } catch (NumberFormatException ignored) {
                // empty or corrupted entry
            }
        }
        if (recent.size() >= MAX_COMMANDS_PER_HOUR) {
            return false;
        }
        recent.add(now);
        StringBuilder stored = new StringBuilder();
        for (Long t : recent) {
            if (stored.length() > 0) {
                stored.append(',');
            }
            stored.append(t);
        }
        SharedPreferences.Editor editor = prefs.edit().putString(key(KEY_COMMANDS, sender), stored.toString());
        if (positionRequest) {
            editor.putLong(key(KEY_LAST_POSITION, sender), now);
        }
        editor.commit();
        return true;
    }

    /** Forget wrong PINs and lockouts, e.g. when the PIN is changed. */
    public static void resetPinFailures(Context context) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        for (String k : prefs.getAll().keySet()) {
            if (k.startsWith(KEY_WRONG_PINS) || k.startsWith(KEY_LOCKOUTS) || k.startsWith(KEY_LOCKED_UNTIL)) {
                editor.remove(k);
            }
        }
        editor.apply();
    }
}
