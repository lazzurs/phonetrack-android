package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Keeps the text of the last SMS the app sent, so the notification can show it.
 * The launcher activity is exported, so the notification's intent carries only a random
 * token; the text is shown only if the token matches the one stored here. Another app
 * cannot guess the token, so it cannot make PhoneTrack display text of its choosing.
 */
public final class SmsInfoStore {

    public static final String EXTRA_TOKEN = "net.eneiluj.nextcloud.phonetrack.smsinfoToken";

    private static final String PREFS = "sms_info";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_FROM = "from";

    private SmsInfoStore() {
    }

    /** Stores the SMS text and returns the intent that opens the dialog for it. */
    public static Intent save(Context context, String content, String from) {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder token = new StringBuilder();
        for (byte b : bytes) {
            token.append(String.format("%02x", b));
        }
        prefs(context).edit()
                .putString(KEY_TOKEN, token.toString())
                .putString(KEY_CONTENT, content)
                .putString(KEY_FROM, from)
                .commit();
        Intent intent = new Intent(context.getApplicationContext(), LogjobsListViewActivity.class);
        intent.putExtra(EXTRA_TOKEN, token.toString());
        return intent;
    }

    /** @return {content, from} if the intent carries the stored token, otherwise null */
    public static String[] lookup(Context context, Intent intent) {
        String token = intent == null ? null : intent.getStringExtra(EXTRA_TOKEN);
        SharedPreferences prefs = prefs(context);
        String stored = prefs.getString(KEY_TOKEN, null);
        String content = prefs.getString(KEY_CONTENT, null);
        if (token == null || stored == null || content == null
                || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return new String[]{content, prefs.getString(KEY_FROM, "")};
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
