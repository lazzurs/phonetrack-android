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
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
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

    /** WCAG minimum contrast for icons and large text. */
    private static final double MIN_ICON_CONTRAST = 3.0;

    /**
     * The colour for icons and text drawn on {@code background}: white, as the app has always
     * used, unless white falls below the WCAG 3:1 minimum for icons and large text, then black.
     * The user, or the Nextcloud server's theme, can choose a colour as light as lime or amber,
     * where white is unreadable; darker colours such as the default Nextcloud blue keep white.
     * The result is always at least 3:1 (when white is below 3:1, black is above 7:1).
     * A translucent background is judged as if it were opaque.
     */
    @ColorInt
    public static int onPrimaryColor(@ColorInt int background) {
        int opaque = ColorUtils.setAlphaComponent(background, 255);
        return ColorUtils.calculateContrast(Color.WHITE, opaque) >= MIN_ICON_CONTRAST
                ? Color.WHITE
                : Color.BLACK;
    }

    /** {@link #onPrimaryColor(int)} for the app colour. */
    @ColorInt
    public static int onPrimaryColor(Context context) {
        return onPrimaryColor(primaryColor(context));
    }

    /**
     * Gives a navigation drawer header the app colour as a gradient, with its text in the
     * colour that contrasts with it. The gradient moves away from the text colour (darker
     * under white text, lighter under black text) so it never loses contrast. Icons in the
     * header are left to the caller: some show pictures that must not be tinted.
     */
    public static void styleDrawerHeader(Context context, View header) {
        int color = primaryColor(context);
        int foreground = onPrimaryColor(color);
        int end = manipulateColor(color, foreground == Color.WHITE ? 0.7f : 1.4f);
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{color, end}));
        setTextColor(header, foreground);
    }

    private static void setTextColor(View view, @ColorInt int color) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setTextColor(group.getChildAt(i), color);
            }
        }
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
