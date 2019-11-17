package net.eneiluj.nextcloud.phonetrack.service;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
//import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;
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

    // those static attributes are unique and accessible to any SmsListener instance
    private static Handler handler = null;
    private static Ringtone ringtone;
    private static int initialAlarmVolume = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Boolean listenToSms = prefs.getBoolean(context.getString(R.string.pref_key_sms), false);
        String keyword = prefs.getString(context.getString(R.string.pref_key_sms_keyword), "phonetrack");

        Log.d(TAG, "we received an SMS ");
        Bundle bundle = intent.getExtras();
        SmsMessage[] msgs = null;
        String msg_from = "";
        if (bundle != null && listenToSms && keyword != null && !keyword.equals("")) {
            try {
                Object[] pdus = (Object[]) bundle.get("pdus");
                msgs = new SmsMessage[pdus.length];
                String msgContent = "";
                for (int i = 0; i < msgs.length; i++) {
                    msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    msg_from = msgs[i].getOriginatingAddress();
                    String msgBody = msgs[i].getMessageBody();
                    msgContent += msgBody;
                }

                String word0 = msgContent.split("\\s+")[0].trim().toLowerCase();
                Log.d(TAG, "Received message: '" + msgContent + "'");
                Log.d(TAG, "current keyword: '" + keyword + "'");
                Log.d(TAG, "Received from: " + msg_from);
                if (word0.equals(keyword.trim().toLowerCase())) {
                    Log.d(TAG, "We received the keyword: "+keyword);
                    keywordReceived(msgContent, msg_from, context);
                }
            } catch (Exception e) {
                Log.d(TAG, "SMS Exception caught: " + e.getMessage());
            }
        }
    }

    private void keywordReceived(String msgContent, String from, Context context) {
        String[] words = msgContent.split("\\s+");
        if (words.length > 1) {
            String word1 = words[1].toLowerCase();
            // make some noise!
            if (word1.equals("alarm")) {
                int duration = 60;
                if (words.length > 2) {
                    try {
                        duration = Integer.parseInt(words[2]);
                    }
                    catch (Exception e) {
                    }
                }
                startAlarm(context, from, duration);
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
            }
        }
        else {
            // don't answer if we're already trying to answer this phone number
            if (!SmsLocationSendService.isRunning.containsKey(from) || !SmsLocationSendService.isRunning.get(from)) {
                // send location information
                Intent serviceIntent = new Intent(context, SmsLocationSendService.class);
                serviceIntent.putExtra("from", from);
                context.startService(serviceIntent);
            }
            else {
                Log.d(TAG, "Sms location service already running for "+from);
            }
        }
    }

    private void startAlarm(Context context, String from, int duration) {
        SmsManager smsManager = SmsManager.getDefault();
        AudioManager am;
        am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // our alarm is ringing : stop it and restore volume
        if (SmsListener.handler != null) {
            Log.d(TAG, "STOPING ALARM YO");
            SmsListener.ringtone.stop();
            am.setStreamVolume(AudioManager.STREAM_ALARM, SmsListener.initialAlarmVolume, 0);
            SmsListener.handler.removeCallbacksAndMessages(null);
            SmsListener.handler = null;

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            ) {
                String smsContent = context.getString(R.string.sms_alarm_stopped);
                smsManager.sendTextMessage(from, null, smsContent, null, null);
            }
        }
        // no alarm yet, save alarm volume and start it yo
        else {
            Log.d(TAG, "STARTING ALARM YO");

            SmsListener.initialAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alert == null) {
                // alert is null, using backup
                alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                if (alert == null) {
                    // alert backup is null, using 2nd backup
                    alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }
            }

            SmsListener.ringtone = RingtoneManager.getRingtone(context, alert);
            SmsListener.ringtone.setStreamType(AudioManager.STREAM_ALARM);
            SmsListener.ringtone.play();

            SmsListener.handler = new Handler();
            SmsListener.handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    SmsListener.ringtone.stop();
                    am.setStreamVolume(AudioManager.STREAM_ALARM, SmsListener.initialAlarmVolume, 0);
                    SmsListener.handler = null;
                }
            }, duration*1000);

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            ) {
                String smsContent = context.getString(R.string.sms_alarm_started, duration);
                smsManager.sendTextMessage(from, null, smsContent, null, null);
                Log.d(TAG, "Send SMS: "+smsContent);
            }
        }
    }

    private void startOrStopLogjobs(Context context, boolean start, String from, @Nullable String logjobName) {
        SmsManager smsManager = SmsManager.getDefault();
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
                    context.startService(intent);

                    // update potential logjob list view
                    Intent broadcastIntent = new Intent(BROADCAST_LOCATION_UPDATED);
                    broadcastIntent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, lj.getId());
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

    private void createLogjob(Context context, String from, int minTime) {
        Log.d(TAG, "CREATE LOGJOB YO");
        SmsManager smsManager = SmsManager.getDefault();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean resetOnToggle = prefs.getBoolean(context.getString(R.string.pref_key_reset_stats), false);
        PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);

        List<DBSession> sessions = db.getSessions();
        if (sessions.size() > 0) {
            DBSession s = sessions.get(0);
            DBLogjob lj = new DBLogjob(0, "sms", s.getNextURL(), s.getToken(),
                    "me", minTime, 0, 50,
                    false, false, false,
                    0, false, true, 0, null, null, false);
            long newLjId = db.addLogjob(lj);

            // let LoggerService know
            Intent intent = new Intent(context, LoggerService.class);
            intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, true);
            intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, newLjId);
            context.startService(intent);

            // update potential logjob list view
            Intent broadcastIntent = new Intent(BROADCAST_LOGJOB_LIST_UPDATED);
            context.sendBroadcast(broadcastIntent);

            String sessionName = s.getName();

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            ) {
                String smsContent;
                smsContent = context.getString(R.string.sms_logjob_created, minTime, sessionName);
                smsManager.sendTextMessage(from, null, smsContent, null, null);
                Log.d(TAG, "Send SMS: "+smsContent);
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
                Log.d(TAG, "Send SMS: "+smsContent);
            }
        }
    }

}


