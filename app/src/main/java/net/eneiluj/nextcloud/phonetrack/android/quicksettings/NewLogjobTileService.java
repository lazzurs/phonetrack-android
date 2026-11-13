package net.eneiluj.nextcloud.phonetrack.android.quicksettings;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.VisibleForTesting;
import androidx.core.service.quicksettings.PendingIntentActivityWrapper;
import androidx.core.service.quicksettings.TileServiceCompat;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.EditPhoneTrackLogjobActivity;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;

/**
 * This {@link TileService} adds a quick settings tile that leads to the new logjob view.
 * The tile is on, and says how many log jobs run, only while logging runs.
 */
public class NewLogjobTileService extends TileService {

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        boolean running = LoggerService.isRunning();
        int enabled = 0;
        if (running) {
            Integer count = PhoneTrackSQLiteOpenHelper.getInstance(this).getEnabledCount().get("1");
            enabled = count != null ? count : 0;
        }
        tile.setState(tileState(running));
        CharSequence subtitle = subtitle(this, running, enabled);
        tile.setSubtitle(subtitle);
        tile.setStateDescription(subtitle);
        tile.updateTile();
    }

    /** An "on" tile reads as "tracking is running": only show it on while it is. */
    @VisibleForTesting
    static int tileState(boolean loggingRunning) {
        return loggingRunning ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
    }

    @VisibleForTesting
    static CharSequence subtitle(Context context, boolean loggingRunning, int enabledLogjobs) {
        if (!loggingRunning) {
            return context.getString(R.string.quicksettings_not_logging);
        }
        return context.getResources().getQuantityString(R.plurals.logging_notification_title, enabledLogjobs, enabledLogjobs);
    }

    @Override
    public void onClick() {
        // create new logjob intent
        final Intent newLogjobIntent = new Intent(getApplicationContext(), EditPhoneTrackLogjobActivity.class);
        // ensure it won't open twice if already running
        newLogjobIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // ask to unlock the screen if locked, then start new logjob intent
        unlockAndRun(new Runnable() {
            @Override
            public void run() {
                // startActivityAndCollapse(Intent) throws UnsupportedOperationException from
                // API 34; the compat helper uses the PendingIntent overload there.
                TileServiceCompat.startActivityAndCollapse(NewLogjobTileService.this,
                        new PendingIntentActivityWrapper(getApplicationContext(), 0, newLogjobIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT, false));
            }
        });

    }
}
