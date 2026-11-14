package net.eneiluj.nextcloud.phonetrack.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.StringRes;

import net.eneiluj.nextcloud.phonetrack.R;

/**
 * The notification channels of the logger and of the SMS commands, each with its own name and
 * description so that they can be told apart in the system notification settings.
 * <p>
 * The ids are the ones earlier versions used, so that what the user set for a channel (sound,
 * importance, hidden) is kept. Creating a channel that exists only updates its name and
 * description: the importance given here applies to new installs only.
 */
public final class NotificationChannels {

    /** The ongoing notification shown while log jobs run */
    public static final String LOGGING = "1526756640";
    /** Shown while the position asked for by SMS is being found */
    static final String SMS_POSITION_REQUEST = "sms_position_request";
    /** What SMS commands did: position sent, log jobs started, alarm rung */
    static final String SMS_ACTIVITY = "11111";

    private NotificationChannels() {
    }

    /**
     * Public for the settings screen: the system screen of a channel closes at once when the
     * channel does not exist, which is the case until logging has started once.
     */
    public static void createLogging(Context context) {
        create(context, LOGGING, R.string.notification_channel_logging,
                R.string.notification_channel_logging_description, NotificationManager.IMPORTANCE_LOW);
    }

    static void createSmsPositionRequest(Context context) {
        create(context, SMS_POSITION_REQUEST, R.string.notification_channel_sms_position_request,
                R.string.notification_channel_sms_position_request_description, NotificationManager.IMPORTANCE_LOW);
    }

    static void createSmsActivity(Context context) {
        create(context, SMS_ACTIVITY, R.string.notification_channel_sms_activity,
                R.string.notification_channel_sms_activity_description, NotificationManager.IMPORTANCE_DEFAULT);
    }

    private static void create(Context context, String id, @StringRes int name, @StringRes int description,
                               int importance) {
        NotificationChannel channel = new NotificationChannel(id, context.getString(name), importance);
        channel.setDescription(context.getString(description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
