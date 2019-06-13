package net.eneiluj.nextcloud.phonetrack.service;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
//import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.android.fragment.PreferencesFragment;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.eneiluj.nextcloud.phonetrack.service.LoggerService.BROADCAST_LOCATION_UPDATED;

public class SmsListener extends BroadcastReceiver {
    private static final String TAG = SmsListener.class.getSimpleName();

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
                msgContent = msgContent.trim().toLowerCase();
                Log.d(TAG, "Received message: '" + msgContent + "'");
                Log.d(TAG, "current keyword: '" + keyword + "'");
                Log.d(TAG, "Received from: " + msg_from);
                if (msgContent.startsWith(keyword.trim().toLowerCase())) {
                    Log.d(TAG, "We received the keyword: "+keyword);
                    keywordReceived(msgContent, msg_from, context);
                }
            } catch (Exception e) {
                Log.d(TAG, "SMS Exception caught: " + e.getMessage());
            }
        }
    }

    private void keywordReceived(String msgContent, String from, Context context) {
        // send location information
        Intent serviceIntent = new Intent(context, SmsLocationSendService.class);
        serviceIntent.putExtra("from", from);
        context.startService(serviceIntent);

        // make some noise!
        String[] words = msgContent.split("\\s+");
        if (words.length > 1) {
            if (words[1].equals("alarm")) {
                startAlarm(context);
            }
            else if (words[1].equals("startlogjobs")) {
                startLogjobs(context);
            }
            else if (words[1].equals("stoplogjobs")) {
                //stopLogjobs(context);
            }
        }
    }

    private void startAlarm(Context context) {

        AudioManager am;
        am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        int initialAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM),0);

        Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alert == null){
            // alert is null, using backup
            alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (alert == null){
                // alert backup is null, using 2nd backup
                alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
        }
        Ringtone ringtone = RingtoneManager.getRingtone(context, alert);
        ringtone.setStreamType(AudioManager.STREAM_ALARM);
        ringtone.play();

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ringtone.stop();
                am.setStreamVolume(AudioManager.STREAM_ALARM, initialAlarmVolume,0);
            }
        }, 20000);
    }

    private void startLogjobs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean resetOnToggle = prefs.getBoolean(context.getString(R.string.pref_key_reset_stats), false);
        PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        List<DBLogjob> logjobs = db.getLogjobs();

        for (DBLogjob lj: logjobs) {
            if (!lj.isEnabled()) {
                db.toggleEnabled(lj, null, resetOnToggle);
            }

            // let LoggerService know
            Intent intent = new Intent(context, LoggerService.class);
            intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, true);
            intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, lj.getId());
            context.startService(intent);

            // update potential logjob list view
            Intent broadcastIntent = new Intent(BROADCAST_LOCATION_UPDATED);
            broadcastIntent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, lj.getId());
            context.sendBroadcast(broadcastIntent);
        }
    }
}


