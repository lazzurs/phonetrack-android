package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;

public class ThemeUtils {

    public static int primaryColor(Context context) {
        int color = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(
                        context.getString(R.string.pref_key_color),
                        ContextCompat.getColor(context, R.color.primary)
                );
        return color;
    }

    public static int primaryDarkColor(Context context) {
        int color = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(context.getString(
                        R.string.pref_key_color),
                        ContextCompat.getColor(context, R.color.primary)
                );
        return manipulateColor(color, 0.7f);
    }

    public static int manipulateColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(a,
                Math.min(r,255),
                Math.min(g,255),
                Math.min(b,255));
    }
}
