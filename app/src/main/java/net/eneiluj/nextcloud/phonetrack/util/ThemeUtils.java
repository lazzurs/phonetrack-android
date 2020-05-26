package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;

public class ThemeUtils {

    private static int defaultColor = Color.parseColor("#0000FF");

    public static int primaryColor(Context context) {
        int color;
        try {
            color = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(
                            context.getString(R.string.pref_key_color),
                            ContextCompat.getColor(context, R.color.primary)
                    );
        }
        catch (ClassCastException e) {
            color = defaultColor;
        }
        return color;
    }

    public static int primaryColorTransparent(Context context) {
        int color;
        try {
            color = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(
                            context.getString(R.string.pref_key_color),
                            ContextCompat.getColor(context, R.color.primary)
                    );
        }
        catch (ClassCastException e) {
            color = defaultColor;
        }
        return manipulateColor(color, 1, 150);
    }

    public static int primaryDarkColor(Context context) {
        return manipulateColor(primaryColor(context), 0.6f);
    }

    public static int primaryLightColor(Context context) {
        return manipulateColor(primaryColor(context), 1.4f);
    }

    public static int manipulateColor(int color, float factor) {
        return manipulateColor(color, factor, Color.alpha(color));
    }

    public static int manipulateColor(int color, float factor, int alpha) {
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(alpha,
                Math.min(r,255),
                Math.min(g,255),
                Math.min(b,255));
    }

    public static boolean isBrightColor(int color) {
        if (android.R.color.transparent == color)
            return true;

        boolean rtnValue = false;

        int[] rgb = { Color.red(color), Color.green(color), Color.blue(color) };

        int brightness = (int) Math.sqrt(rgb[0] * rgb[0] * .241 + rgb[1]
                * rgb[1] * .691 + rgb[2] * rgb[2] * .068);

        // color is light
        if (brightness >= 200) {
            rtnValue = true;
        }

        return rtnValue;
    }

    public static Bitmap getRoundedBitmap(Bitmap input, int pixels) {
        Bitmap rounded = Bitmap.createBitmap(input.getWidth(), input
                .getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(rounded);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, input.getWidth(), input.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, pixels, pixels, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(input, rect, rect, paint);

        return rounded;
    }
}
