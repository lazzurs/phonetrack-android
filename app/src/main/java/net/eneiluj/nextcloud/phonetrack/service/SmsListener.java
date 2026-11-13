package net.eneiluj.nextcloud.phonetrack.service;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
//import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.util.CredentialStore;
import net.eneiluj.nextcloud.phonetrack.util.SmsCommandGuard;
import net.eneiluj.nextcloud.phonetrack.util.SmsSenderAllowlist;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import java.util.Arrays;
import java.util.List;

import static net.eneiluj.nextcloud.phonetrack.service.LoggerService.BROADCAST_LOCATION_UPDATED;

public class SmsListener extends BroadcastReceiver {
    private static final String TAG = SmsListener.class.getSimpleName();
    public static final String BROADCAST_LOGJOB_LIST_UPDATED = "net.eneiluj.nextcloud.phonetrack.broadcast.logjob_list_updated";
    /** Title of the logjob made by the createlogjob command */
    static final String SMS_LOGJOB_TITLE = "sms";
    private static int commandNotificationId = 1526756670;


    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Boolean listenToSms = prefs.getBoolean(context.getString(R.string.pref_key_sms), false);
        String keyword = prefs.getString(context.getString(R.string.pref_key_sms_keyword), "phonetrack");

        Log.d(TAG, "we received an SMS ");
        String msg_from = "";
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())
                && listenToSms && keyword != null && !keyword.equals("")) {
            try {
                // handles the PDU format and multipart messages (createFromPdu(byte[]) is deprecated)
                SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                String msgContent = "";
                for (int i = 0; i < msgs.length; i++) {
                    msg_from = msgs[i].getOriginatingAddress();
                    String msgBody = msgs[i].getMessageBody();
                    msgContent += msgBody;
                }

                String word0 = msgContent.split("\\s+")[0].trim().toLowerCase();
                Log.d(TAG, "current keyword: '" + keyword + "'");
                if (word0.equals(keyword.trim().toLowerCase())) {
                    // the keyword is visible in any SMS, it is not a secret: only allowed numbers may send commands
                    if (!SmsSenderAllowlist.isAllowed(context, msg_from)) {
                        SystemLogger.w(TAG, "Ignored SMS command from " + msg_from + ": not an allowed sender");
                        return;
                    }
                    String[] words = checkPinAndRateLimit(context, msgContent.split("\\s+"), msg_from);
                    if (words == null) {
                        return;
                    }
                    Log.d(TAG, "We received the keyword: "+keyword);
                    keywordReceived(words, msg_from, context);
                }
            } catch (Exception e) {
                Log.d(TAG, "SMS Exception caught: " + e.getMessage());
            }
        }
    }

    /**
     * The sender number can be forged, so when a PIN is set it must follow the keyword:
     * "KEYWORD PIN [COMMAND...]". Every sender is also rate limited, as each command is
     * answered by SMS at the user's cost.
     *
     * @return the words of the message without the PIN, or null to ignore the message
     */
    @Nullable
    private static String[] checkPinAndRateLimit(Context context, String[] words, String from) {
        String pin = CredentialStore.getSmsPin(context);
        if (!pin.isEmpty()) {
            if (SmsCommandGuard.isLockedOut(context, from)) {
                SystemLogger.w(TAG, "Ignored SMS command from " + from + ": too many wrong PINs, try again later");
                return null;
            }
            String givenPin = words.length > 1 ? words[1] : "";
            if (!SmsCommandGuard.pinMatches(pin, givenPin)) {
                boolean lockedOut = SmsCommandGuard.recordWrongPin(context, from);
                SystemLogger.w(TAG, "Ignored SMS command from " + from + ": missing or wrong PIN"
                        + (lockedOut ? ", sender ignored for a while" : ""));
                return null;
            }
            SmsCommandGuard.recordCorrectPin(context, from);
            String[] withoutPin = new String[words.length - 1];
            withoutPin[0] = words[0];
            System.arraycopy(words, 2, withoutPin, 1, words.length - 2);
            words = withoutPin;
        }
        if (!SmsCommandGuard.tryAcquire(context, from, words.length == 1)) {
            SystemLogger.w(TAG, "Ignored SMS command from " + from + ": too many commands, try again later");
            return null;
        }
        return words;
    }

    private void keywordReceived(String[] words, String from, Context context) {
        if (words.length > 1) {
            String word1 = words[1].toLowerCase();
            // make some noise!
            if (word1.equals("alarm")) {
                long duration = 60;
                if (words.length > 2) {
                    try {
                        duration = Long.parseLong(words[2]);
                    }
                    catch (Exception e) {
                    }
                }
                if (duration <= 0) {
                    SystemLogger.w(TAG, "Ignored SMS alarm command: the duration must be positive");
                    return;
                }
                startAlarm(context, from, (int) Math.min(duration, SmsAlarmService.MAX_DURATION_SECONDS));
                notifyCommand(context, from, word1);
            }
            else if (word1.equals("startlogjobs")) {
                String logjobName = null;
                if (words.length > 2) {
                    // too recent solution ;-)
                    //logjobName = String.join(" ", Arrays.copyOfRange(words, 2, words.length));
                    logjobName = "";
                    for (int i=2; i < words.length; i++) {
                        logjobName += words[i] + " ";
                    }
                    logjobName = logjobName.trim();
                }
                Log.v(TAG, "LOLO '"+logjobName+"'");
                startOrStopLogjobs(context, true, from, logjobName);
                notifyCommand(context, from, word1);
            }
            else if (word1.equals("stoplogjobs")) {
                String logjobName = null;
                if (words.length > 2) {
                    // too recent solution ;-)
                    //logjobName = String.join(" ", Arrays.copyOfRange(words, 2, words.length));
                    logjobName = "";
                    for (int i=2; i < words.length; i++) {
                        logjobName += words[i] + " ";
                    }
                    logjobName = logjobName.trim();
                }
                startOrStopLogjobs(context, false, from, logjobName);
                notifyCommand(context, from, word1);
            }
            else if (word1.equals("createlogjob")) {
                int minTime = 10;
                if (words.length > 2) {
                    try {
                        minTime = Integer.parseInt(words[2]);
                    }
                    catch (Exception e) {
                    }
                }
                createLogjob(context, from, minTime);
                notifyCommand(context, from, word1);
            }
        }
        else {
            // don't answer if we're already trying to answer this phone number
            if (!SmsLocationSendService.isRunning.containsKey(from) || !SmsLocationSendService.isRunning.get(from)) {
                // send location information
                Intent serviceIntent = new Intent(context, SmsLocationSendService.class);
                serviceIntent.putExtra(SmsLocationSendService.EXTRA_FROM, from);
                try {
                    // allowed from here: SMS_RECEIVED temporarily exempts the app from background limits
                    context.startService(serviceIntent);
                } catch (IllegalStateException e) {
                    Log.w(TAG, "Unable to start the SMS position reply: " + e);
                }
            }
            else {
                Log.d(TAG, "Sms location service already running");
            }
        }
    }

    private void startAlarm(Context context, String from, int duration) {
        // rings from a foreground service, which also restores the alarm volume afterwards
        SmsAlarmService.toggle(context, from, duration);
    }

    private void startOrStopLogjobs(Context context, boolean start, String from, @Nullable String logjobName) {
        SmsManager smsManager = SupportUtil.getSmsManager(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean resetOnToggle = prefs.getBoolean(context.getString(R.string.pref_key_reset_stats), false);
        PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        List<DBLogjob> logjobs = db.getLogjobs();

        int nbLogjobToggled = 0;

        for (DBLogjob lj: logjobs) {
            if (logjobName == null || logjobName.equals(lj.getTitle())) {
                // we toggle disabled logjobs if this is the start command
                // we toggle enabled logjobs if this is NOT the start command
                if ((start && !lj.isEnabled()) ||
                        (!start && lj.isEnabled())
                ) {
                    db.toggleEnabled(lj, null, resetOnToggle);

                    // let LoggerService know
                    Intent intent = new Intent(context, LoggerService.class);
                    intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, true);
                    intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, lj.getId());
                    startLoggerService(context, intent);

                    // update potential logjob list view
                    Intent broadcastIntent = new Intent(BROADCAST_LOCATION_UPDATED);
                    broadcastIntent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, lj.getId());
                    broadcastIntent.setPackage(context.getPackageName());
                    context.sendBroadcast(broadcastIntent);

                    nbLogjobToggled++;
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        ) {
            String smsContent;
            if (start) {
                smsContent = context.getString(R.string.sms_logjobs_started, nbLogjobToggled);
            }
            else {
                smsContent = context.getString(R.string.sms_logjobs_stopped, nbLogjobToggled);
            }
            smsManager.sendTextMessage(from, null, smsContent, null, null);
        }
    }

    /**
     * SMS_RECEIVED grants a short foreground-service start allowance, but a plain
     * startService() of a stopped service from here is a background start. Use
     * startForegroundService() when LoggerService will have to promote itself.
     */
    private static void startLoggerService(Context context, Intent intent) {
        try {
            if (!LoggerService.isRunning() && SupportUtil.hasBackgroundLocationPermission(context)) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "Unable to notify LoggerService: " + e);
        }
    }

    private void createLogjob(Context context, String from, int minTime) {
        Log.d(TAG, "CREATE LOGJOB YO");
        SmsManager smsManager = SupportUtil.getSmsManager(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean resetOnToggle = prefs.getBoolean(context.getString(R.string.pref_key_reset_stats), false);
        PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);

        List<DBSession> sessions = db.getSessions();
        if (sessions.size() > 0) {
            DBSession s = sessions.get(0);
            // reuse the logjob of an earlier command: repeated SMS must not pile up logjobs
            DBLogjob existing = findSmsLogjob(db, s);
            long newLjId;
            if (existing == null) {
                DBLogjob lj = new DBLogjob(0, SMS_LOGJOB_TITLE, s.getNextURL(), s.getToken(),
                        "me", minTime, 0, 50,
                        false, false, false,
                        LocationSamplePolicy.smsTimeoutSeconds(minTime), false, true, 0, null, null, false);
                newLjId = db.addLogjob(lj);
            } else {
                if (existing.getMinTime() != minTime) {
                    existing = db.updateLogjobAndSync(existing, existing.getTitle(), existing.getToken(),
                            existing.getUrl(), existing.getDeviceName(), existing.getPost(), minTime,
                            existing.getMinDistance(), existing.getMinAccuracy(), existing.keepGpsOnBetweenFixes(),
                            existing.useSignificantMotion(), existing.useSignificantMotionMixed(),
                            // keep the timeout below the new interval, as the edit screen requires
                            Math.min(existing.getLocationRequestTimeout(), LocationSamplePolicy.smsTimeoutSeconds(minTime)),
                            existing.getLogin(), existing.getPassword(),
                            existing.getJson(), null);
                }
                if (!existing.isEnabled()) {
                    db.toggleEnabled(existing, null, resetOnToggle);
                }
                newLjId = existing.getId();
            }

            // let LoggerService know
            Intent intent = new Intent(context, LoggerService.class);
            intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, true);
            intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, newLjId);
            startLoggerService(context, intent);

            // update potential logjob list view
            Intent broadcastIntent = new Intent(BROADCAST_LOGJOB_LIST_UPDATED);
            broadcastIntent.setPackage(context.getPackageName());
            context.sendBroadcast(broadcastIntent);

            String sessionName = s.getName();

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            ) {
                String smsContent;
                smsContent = context.getString(existing == null ? R.string.sms_logjob_created : R.string.sms_logjob_updated,
                        minTime, sessionName);
                smsManager.sendTextMessage(from, null, smsContent, null, null);
            }
        }
        else {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            ) {
                String smsContent;
                smsContent = context.getString(R.string.sms_logjob_creation_impossible);
                smsManager.sendTextMessage(from, null, smsContent, null, null);
            }
        }
    }

    @Nullable
    private static DBLogjob findSmsLogjob(PhoneTrackSQLiteOpenHelper db, DBSession session) {
        for (DBLogjob lj : db.getLogjobs()) {
            if (SMS_LOGJOB_TITLE.equals(lj.getTitle()) && session.getToken() != null
                    && session.getToken().equals(lj.getToken())) {
                return lj;
            }
        }
        return null;
    }

    /**
     * Shows every command that changed something, so a forged one does not go unnoticed.
     * Never shows the message itself, which may contain the PIN.
     */
    private static void notifyCommand(Context context, String from, String command) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String text = context.getString(R.string.sms_command_notification, command, from);
        if (CredentialStore.getSmsPin(context).isEmpty()) {
            text += " " + context.getString(R.string.sms_command_notification_no_pin);
        }
        // the channel of the other SMS command notifications
        String channelId = SmsLocationSendService.INFO_CHANNEL_ID;
        NotificationChannels.createSmsActivity(context);
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, LogjobsListViewActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notify_24dp)
                .setContentTitle(context.getString(R.string.sms_command_notification_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(commandNotificationId++, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to show the SMS command notification: " + e);
        }
    }

}
