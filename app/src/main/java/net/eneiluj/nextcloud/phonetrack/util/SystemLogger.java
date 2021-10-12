package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.eneiluj.nextcloud.phonetrack.android.activity.SyslogManagerActivity;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

public class SystemLogger {
    private static PhoneTrackSQLiteOpenHelper db = null;
    private static boolean enabled = false;

    public static void setEnabled(boolean newEnabled) {
        enabled = newEnabled;
    }

    public static boolean getEnabled() {
        return enabled;
    }

    public static void getDb(Context context) {
        if (db == null) {
            db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        }
    }

    public static void v(String tag, String message) {
        Log.v(tag, message);
        if (enabled) {
            db.addSyslog("v", tag, message);
        }
    }

    public static void d(String tag, String message) {
        Log.d(tag, message);
        if (enabled) {
            db.addSyslog("d", tag, message);
        }
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
        if (enabled) {
            db.addSyslog("i", tag, message);
        }
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
        if (enabled) {
            db.addSyslog("w", tag, message);
        }
    }

    public static void e(String tag, String message) {
        Log.e(tag, message);
        if (enabled) {
            db.addSyslog("e", tag, message);
        }
    }

    public static void wtf(String tag, String message) {
        Log.wtf(tag, message);
        if (enabled) {
            db.addSyslog("f", tag, message);
        }
    }
}
