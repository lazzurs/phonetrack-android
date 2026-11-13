package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.service.TrackingReadiness;
import net.eneiluj.nextcloud.phonetrack.util.BackgroundTask;
import net.eneiluj.nextcloud.phonetrack.util.LocalNetworkAccess;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;

import java.util.Map;

/**
 * Asks for what logging needs when the user turns a log job on, not at start-up.
 *
 * Each step is explained first and can be declined with "Not now". A declined step is remembered
 * and not asked for again on its own: the status banner of the log job list names the problem and
 * its button ({@link #fix}) asks again, or opens the app's settings once Android no longer shows
 * the request ("Don't ask again").
 */
final class TrackingPermissionFlow {

    private static final String TAG = TrackingPermissionFlow.class.getSimpleName();

    static final String PREF_DECLINED_PREFIX = "trackingSetupDeclined_";
    static final String PREF_ASKED_PREFIX = "trackingSetupAsked_";

    /** The steps, in the order they are asked for. */
    enum Step { LOCATION, BACKGROUND_LOCATION, NOTIFICATIONS, BATTERY_OPTIMISATION, LOCAL_NETWORK }

    interface Listener {
        /** A permission or setting may have changed: refresh the status and recheck paused jobs. */
        void onTrackingReadinessMayHaveChanged();
    }

    private final AppCompatActivity activity;
    private final Listener listener;
    private final ActivityResultLauncher<String[]> permissionLauncher;
    private final ActivityResultLauncher<Intent> settingsLauncher;

    /** The step whose system request or screen is open, if it was started by {@link #onJobEnabled}. */
    private Step pendingStep;
    /** True while going through the steps after a log job was turned on. */
    private boolean walking;

    /** Create while the activity is being constructed (it registers activity result callbacks). */
    TrackingPermissionFlow(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
        permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);
        settingsLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> onReturnFromScreen());
    }

    /** A log job was just turned on: ask for what is missing, skipping what was declined before. */
    void onJobEnabled() {
        if (walking) {
            return;
        }
        walking = true;
        continueFrom(Step.LOCATION);
    }

    /** The status banner's button: fix this cause, asking again even if it was declined before. */
    void fix(@NonNull TrackingReadiness.Cause cause) {
        switch (cause) {
            case NEEDS_LOCATION:
            case NEEDS_PRECISE_LOCATION:
                requestOrOpenSettings(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
                break;
            case NEEDS_BACKGROUND_LOCATION:
                requestOrOpenSettings(Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                break;
            case NOTIFICATIONS_OFF:
                if (!granted(Manifest.permission.POST_NOTIFICATIONS)
                        && !isPermanentlyDenied(Manifest.permission.POST_NOTIFICATIONS)) {
                    request(Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    // the permission is granted but notifications are blocked, or it can no longer be asked for
                    open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
                }
                break;
            case BATTERY_OPTIMISED:
                if (!launch(batteryOptimisationRequest())) {
                    open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
                break;
            case LOCATION_OFF:
                open(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                break;
            case PAUSED_BATTERY_SAVER:
                open(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
                break;
            case PAUSED_AIRPLANE_MODE:
                open(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
                break;
            default:
                break;
        }
    }

    /** True if the banner's button for this cause opens the app's settings page (no request is possible). */
    boolean opensAppSettings(@NonNull TrackingReadiness.Cause cause) {
        switch (cause) {
            case NEEDS_LOCATION:
            case NEEDS_PRECISE_LOCATION:
                return isPermanentlyDenied(Manifest.permission.ACCESS_FINE_LOCATION);
            case NEEDS_BACKGROUND_LOCATION:
                return isPermanentlyDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            default:
                return false;
        }
    }

    // ---- the steps after a log job was turned on ----

    private void continueFrom(Step step) {
        for (Step s = step; s != null; s = next(s)) {
            if (s == Step.LOCAL_NETWORK) {
                checkLocalNetwork();
                return;
            }
            if (!isNeeded(s)) {
                continue;
            }
            if (isDeclined(s)) {
                if (s == Step.LOCATION) {
                    // nothing else is useful without location: the banner explains
                    endWalk();
                    return;
                }
                continue;
            }
            explain(s);
            return;
        }
        endWalk();
    }

    private static Step next(Step step) {
        Step[] steps = Step.values();
        return step.ordinal() + 1 < steps.length ? steps[step.ordinal() + 1] : null;
    }

    private boolean isNeeded(Step step) {
        switch (step) {
            case LOCATION:
                // approximate location is not enough to log
                return !granted(Manifest.permission.ACCESS_FINE_LOCATION);
            case BACKGROUND_LOCATION:
                return !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            case NOTIFICATIONS:
                return !granted(Manifest.permission.POST_NOTIFICATIONS);
            case BATTERY_OPTIMISATION:
                PowerManager pm = activity.getSystemService(PowerManager.class);
                return pm != null && !pm.isIgnoringBatteryOptimizations(activity.getPackageName());
            case LOCAL_NETWORK:
                return LocalNetworkAccess.isEnforced(activity) && !LocalNetworkAccess.isGranted(activity);
            default:
                return false;
        }
    }

    private void explain(Step step) {
        @StringRes int title;
        String message;
        switch (step) {
            case LOCATION:
                title = R.string.location_rationale_title;
                message = activity.getString(R.string.location_rationale);
                break;
            case BACKGROUND_LOCATION:
                title = R.string.background_location_permission_title;
                message = activity.getString(R.string.background_location_rationale,
                        TrackingReadiness.backgroundOptionLabel(activity));
                break;
            case NOTIFICATIONS:
                title = R.string.notification_rationale_title;
                message = activity.getString(R.string.notification_rationale);
                break;
            case BATTERY_OPTIMISATION:
                title = R.string.battery_rationale_title;
                message = activity.getString(R.string.battery_rationale);
                break;
            default:
                title = R.string.local_network_permission_title;
                message = activity.getString(R.string.local_network_permission_message);
                break;
        }
        new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.AppThemeDialog))
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.action_continue, (dialog, which) -> requestStep(step))
                // "Not now" or back: remember it, the status banner offers it again
                .setNegativeButton(R.string.action_not_now, (dialog, which) -> declineAndContinue(step))
                .setOnCancelListener(dialog -> declineAndContinue(step))
                .show();
    }

    private void declineAndContinue(Step step) {
        setDeclined(step);
        if (step == Step.LOCATION) {
            endWalk();
        } else {
            continueFrom(next(step));
        }
    }

    private void requestStep(Step step) {
        pendingStep = step;
        switch (step) {
            case LOCATION:
                request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
                break;
            case BACKGROUND_LOCATION:
                // on Android 11+ this opens the app's location permission page
                request(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                break;
            case NOTIFICATIONS:
                request(Manifest.permission.POST_NOTIFICATIONS);
                break;
            case BATTERY_OPTIMISATION:
                if (!launch(batteryOptimisationRequest())) {
                    pendingStep = null;
                    declineAndContinue(step);
                }
                break;
            case LOCAL_NETWORK:
                request(LocalNetworkAccess.PERMISSION);
                break;
        }
    }

    private void checkLocalNetwork() {
        if (!isNeeded(Step.LOCAL_NETWORK) || isDeclined(Step.LOCAL_NETWORK)) {
            endWalk();
            return;
        }
        // Android 17: reaching a server on the LAN needs the "Nearby devices" permission
        new BackgroundTask<Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                Context context = activity.getApplicationContext();
                return LocalNetworkAccess.isNeededFor(context, LocalNetworkAccess.configuredServerUrls(context));
            }

            @Override
            protected void onPostExecute(Boolean needed) {
                if (activity.isDestroyed()) {
                    return;
                }
                if (needed) {
                    explain(Step.LOCAL_NETWORK);
                } else {
                    endWalk();
                }
            }
        }.executeInParallel();
    }

    private void endWalk() {
        walking = false;
        listener.onTrackingReadinessMayHaveChanged();
    }

    private void onPermissionResult(Map<String, Boolean> result) {
        onReturnFromScreen();
    }

    private void onReturnFromScreen() {
        Step step = pendingStep;
        pendingStep = null;
        if (step == null || !walking) {
            // asked from the banner (or the activity was recreated meanwhile)
            listener.onTrackingReadinessMayHaveChanged();
            return;
        }
        if (isNeeded(step)) {
            // denied, or back from the screen without allowing it
            declineAndContinue(step);
        } else {
            continueFrom(next(step));
        }
    }

    // ---- requests and settings screens ----

    private void requestOrOpenSettings(String permission, String... permissions) {
        if (isPermanentlyDenied(permission)) {
            open(appDetailsSettings());
        } else {
            request(permissions);
        }
    }

    private void request(String... permissions) {
        SharedPreferences.Editor editor = prefs().edit();
        for (String permission : permissions) {
            editor.putBoolean(PREF_ASKED_PREFIX + permission, true);
        }
        editor.apply();
        SystemLogger.d(TAG, "request " + String.join(", ", permissions));
        permissionLauncher.launch(permissions);
    }

    /**
     * Android shows nothing for a request once the user denied it twice, and then no longer
     * wants a rationale shown: only the app's settings page can grant it.
     */
    boolean isPermanentlyDenied(String permission) {
        return !granted(permission)
                && prefs().getBoolean(PREF_ASKED_PREFIX + permission, false)
                && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    @SuppressLint("BatteryLife")
    private Intent batteryOptimisationRequest() {
        return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + activity.getPackageName()));
    }

    private Intent appDetailsSettings() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.getPackageName(), null));
    }

    /** Open a settings screen, or the app's settings page if this device has no such screen. */
    private void open(Intent intent) {
        if (!launch(intent)) {
            launch(appDetailsSettings());
        }
    }

    private boolean launch(Intent intent) {
        try {
            settingsLauncher.launch(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            SystemLogger.d(TAG, "no screen for " + intent.getAction());
            return false;
        }
    }

    // ---- remembered answers ----

    private boolean isDeclined(Step step) {
        return prefs().getBoolean(PREF_DECLINED_PREFIX + step.name(), false);
    }

    private void setDeclined(Step step) {
        prefs().edit().putBoolean(PREF_DECLINED_PREFIX + step.name(), true).apply();
    }

    private boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(activity);
    }
}
