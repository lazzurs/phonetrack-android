package net.eneiluj.nextcloud.phonetrack.util;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;

import androidx.appcompat.view.ContextThemeWrapper;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;

import java.util.List;

/**
 * The one way to delete log jobs from the UI: confirm (mentioning positions that were never
 * uploaded), delete, then tell {@link LoggerService} so that a running job is stopped.
 */
public final class LogjobDeletion {

    private LogjobDeletion() {
    }

    /** Number of positions of these log jobs that have not been uploaded yet. */
    // small local SQLite reads/deletes on the main thread, like the other delete paths of the UI
    @SuppressLint("ThreadConstraint")
    public static int countUnsent(PhoneTrackSQLiteOpenHelper db, List<DBLogjob> logjobs) {
        int unsent = 0;
        for (DBLogjob logjob : logjobs) {
            unsent += db.getLogjobLocationNotSyncedCount(logjob.getId());
        }
        return unsent;
    }

    /**
     * Ask for confirmation, then delete the log jobs (active ones included) and stop them in the
     * logger service.
     *
     * @param onDeleted called once the log jobs are deleted
     */
    @SuppressLint("ThreadConstraint")
    public static void confirmAndDelete(Context context, List<DBLogjob> logjobs, Runnable onDeleted) {
        final PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        int unsent = countUnsent(db, logjobs);
        String message = context.getResources().getQuantityString(
                R.plurals.delete_logjobs_confirm, logjobs.size(), logjobs.size());
        if (unsent > 0) {
            message += "\n\n" + context.getResources().getQuantityString(
                    R.plurals.delete_logjobs_unsent_warning, unsent, unsent);
        }
        new AlertDialog.Builder(new ContextThemeWrapper(context, R.style.AppThemeDialog))
                .setMessage(message)
                .setPositiveButton(R.string.menu_delete, (dialog, which) -> {
                    delete(context, logjobs);
                    if (onDeleted != null) {
                        onDeleted.run();
                    }
                })
                .setNegativeButton(R.string.simple_cancel, null)
                .show();
    }

    /** Delete without asking, then stop the log jobs in the logger service. */
    @SuppressLint("ThreadConstraint")
    public static void delete(Context context, List<DBLogjob> logjobs) {
        PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        for (DBLogjob logjob : logjobs) {
            db.deleteLogjob(logjob.getId());
            Intent intent = new Intent(context, LoggerService.class);
            intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, true);
            intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, logjob.getId());
            context.startService(intent);
        }
    }
}
