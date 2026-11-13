package net.eneiluj.nextcloud.phonetrack.service;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.PowerManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Why enabled log jobs are not logging, or may log badly.
 *
 * The classification ({@link #serviceGate}, {@link #problems}, {@link #retryDelayMillis}) is
 * pure: it works on a {@link State} snapshot, so each combination can be unit tested.
 * {@link #read} takes that snapshot from the device.
 */
public final class TrackingReadiness {

    /** Most important first: the status banner shows the first one that applies. */
    public enum Cause {
        OK(false),
        /** Neither precise nor approximate location is allowed. */
        NEEDS_LOCATION(true),
        /** Only "Approximate" location is allowed. */
        NEEDS_PRECISE_LOCATION(true),
        /** Location is allowed only while the app is in use. */
        NEEDS_BACKGROUND_LOCATION(true),
        /** The device's location switch is off. */
        LOCATION_OFF(true),
        /** Airplane mode is on and the user chose to pause logging then. */
        PAUSED_AIRPLANE_MODE(true),
        /** Battery saver is on and the user chose to pause logging then. */
        PAUSED_BATTERY_SAVER(true),
        /** Logging works, but the user does not see its notification. */
        NOTIFICATIONS_OFF(false),
        /** Logging works, but Android may delay or stop it in the background. */
        BATTERY_OPTIMISED(false);

        /** True when no position can be logged while this lasts. */
        public final boolean stopsLogging;

        Cause(boolean stopsLogging) {
            this.stopsLogging = stopsLogging;
        }

        /** True for the causes the user fixes by granting a location permission. */
        public boolean isMissingLocationPermission() {
            return this == NEEDS_LOCATION || this == NEEDS_PRECISE_LOCATION || this == NEEDS_BACKGROUND_LOCATION;
        }
    }

    /** What the device allows and what the user chose, at one moment. */
    public static final class State {
        public boolean fineLocation;
        public boolean coarseLocation;
        public boolean backgroundLocation;
        public boolean locationEnabled = true;
        public boolean airplaneMode;
        public boolean respectAirplaneMode;
        public boolean powerSaveMode;
        public boolean respectPowerSaveMode;
        public boolean notificationsEnabled = true;
        public boolean ignoringBatteryOptimisations = true;
    }

    /** Shortest wait before a paused log job checks again. */
    static final long MIN_RETRY_MILLIS = 60_000L;
    /** Longest wait before a paused log job checks again. */
    static final long MAX_RETRY_MILLIS = 15 * 60_000L;

    private TrackingReadiness() {
    }

    /**
     * Why the logger service must not request positions now, or {@link Cause#OK}.
     * A switched-off location is not a reason: updates requested then start when it is switched on.
     */
    @NonNull
    public static Cause serviceGate(@NonNull State state) {
        Cause permission = missingLocationPermission(state);
        if (permission != Cause.OK) {
            return permission;
        }
        if (state.respectAirplaneMode && state.airplaneMode) {
            return Cause.PAUSED_AIRPLANE_MODE;
        }
        if (state.respectPowerSaveMode && state.powerSaveMode) {
            return Cause.PAUSED_BATTERY_SAVER;
        }
        return Cause.OK;
    }

    /** Everything that stops or hinders logging, most important first. Empty when all is well. */
    @NonNull
    public static List<Cause> problems(@NonNull State state) {
        List<Cause> problems = new ArrayList<>();
        Cause permission = missingLocationPermission(state);
        if (permission != Cause.OK) {
            problems.add(permission);
        }
        if (!state.locationEnabled) {
            problems.add(Cause.LOCATION_OFF);
        }
        if (state.respectAirplaneMode && state.airplaneMode) {
            problems.add(Cause.PAUSED_AIRPLANE_MODE);
        }
        if (state.respectPowerSaveMode && state.powerSaveMode) {
            problems.add(Cause.PAUSED_BATTERY_SAVER);
        }
        if (!state.notificationsEnabled) {
            problems.add(Cause.NOTIFICATIONS_OFF);
        }
        if (!state.ignoringBatteryOptimisations) {
            problems.add(Cause.BATTERY_OPTIMISED);
        }
        return problems;
    }

    /**
     * How long a paused log job waits before checking again: its own interval, but at least a
     * minute (no busy loop for jobs logging every second) and at most 15 minutes.
     */
    public static long retryDelayMillis(int intervalSeconds) {
        long interval = intervalSeconds * 1000L;
        return Math.max(MIN_RETRY_MILLIS, Math.min(MAX_RETRY_MILLIS, interval));
    }

    // Background location can only be granted on top of precise or approximate location, and the
    // logger has always required precise location, so they are asked for in this order.
    private static Cause missingLocationPermission(State state) {
        if (!state.fineLocation && !state.coarseLocation) {
            return Cause.NEEDS_LOCATION;
        }
        if (!state.fineLocation) {
            return Cause.NEEDS_PRECISE_LOCATION;
        }
        if (!state.backgroundLocation) {
            return Cause.NEEDS_BACKGROUND_LOCATION;
        }
        return Cause.OK;
    }

    /** Snapshot of the device's permissions, switches and the user's pause settings. */
    @NonNull
    public static State read(@NonNull Context context) {
        State state = new State();
        state.fineLocation = granted(context, Manifest.permission.ACCESS_FINE_LOCATION);
        state.coarseLocation = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION);
        state.backgroundLocation = granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        LocationManager locationManager = context.getSystemService(LocationManager.class);
        state.locationEnabled = locationManager == null || locationManager.isLocationEnabled();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        state.respectAirplaneMode = prefs.getBoolean(context.getString(R.string.pref_key_offline_mode_awareness), false);
        state.airplaneMode = SupportUtil.isAirplaneModeOn(context);
        state.respectPowerSaveMode = prefs.getBoolean(context.getString(R.string.pref_key_power_saving_awareness), false);
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        state.powerSaveMode = powerManager != null && powerManager.isPowerSaveMode();
        state.ignoringBatteryOptimisations = powerManager == null
                || powerManager.isIgnoringBatteryOptimizations(context.getPackageName());

        state.notificationsEnabled = granted(context, Manifest.permission.POST_NOTIFICATIONS)
                && NotificationManagerCompat.from(context).areNotificationsEnabled();
        return state;
    }

    /** One sentence for the user: what is wrong and what it does to logging. */
    @NonNull
    public static String describe(@NonNull Context context, @NonNull Cause cause) {
        switch (cause) {
            case NEEDS_LOCATION:
                return context.getString(R.string.status_needs_location);
            case NEEDS_PRECISE_LOCATION:
                return context.getString(R.string.status_needs_precise_location);
            case NEEDS_BACKGROUND_LOCATION:
                return context.getString(R.string.status_needs_background_location, backgroundOptionLabel(context));
            case LOCATION_OFF:
                return context.getString(R.string.status_location_off);
            case PAUSED_AIRPLANE_MODE:
                return context.getString(R.string.status_paused_airplane_mode);
            case PAUSED_BATTERY_SAVER:
                return context.getString(R.string.status_paused_battery_saver);
            case NOTIFICATIONS_OFF:
                return context.getString(R.string.status_notifications_off);
            case BATTERY_OPTIMISED:
                return context.getString(R.string.status_battery_optimised);
            default:
                return "";
        }
    }

    /** The system's name for "Allow all the time", as shown in the location permission settings. */
    @NonNull
    public static String backgroundOptionLabel(@NonNull Context context) {
        CharSequence label = null;
        try {
            label = context.getPackageManager().getBackgroundPermissionOptionLabel();
        } catch (RuntimeException e) {
            // fall back to the usual wording
        }
        return TextUtils.isEmpty(label)
                ? context.getString(R.string.background_location_option_fallback)
                : label.toString();
    }

    private static boolean granted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
