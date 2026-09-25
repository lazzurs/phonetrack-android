package net.eneiluj.nextcloud.phonetrack.android.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import net.eneiluj.nextcloud.phonetrack.R;

import java.util.Locale;

/**
 * A color stored as an int, shown as a round swatch at the end of the row.
 * Replaces com.kizitonwose.colorpreference (support library only, needed Jetifier)
 * and persists the same int under the same key.
 */
public class ColorPreference extends Preference {

    @ColorInt
    private int value = Color.BLUE;

    public ColorPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_widget_color);
    }

    @Override
    protected Object onGetDefaultValue(@NonNull TypedArray a, int index) {
        return a.getColor(index, Color.BLUE);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        value = getPersistedInt(defaultValue instanceof Integer ? (Integer) defaultValue : value);
    }

    @ColorInt
    public int getValue() {
        return value;
    }

    public void setValue(@ColorInt int color) {
        value = color;
        persistInt(color);
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View swatch = holder.findViewById(R.id.colorSwatch);
        if (swatch != null) {
            swatch.setBackground(circle(value));
        }
    }

    /** A filled circle with a thin outline, so light colors stay visible. */
    public static GradientDrawable circle(@ColorInt int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(2, 0x33000000);
        return d;
    }

    /** "#RRGGBB" (alpha dropped: the app color is always opaque). */
    public static String toHex(@ColorInt int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    /** Parses "#RRGGBB" or "RRGGBB" (case-insensitive) into an opaque color, or null. */
    @Nullable
    @ColorInt
    public static Integer parseHex(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        return 0xFF000000 | Integer.parseInt(hex, 16);
    }
}
