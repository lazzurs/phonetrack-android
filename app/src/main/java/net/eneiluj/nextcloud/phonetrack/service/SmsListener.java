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
import android.os.Build;
import android.os.Bundle;
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
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

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
        if (bundle != null && listenToSms) {
            //---retrieve the SMS message received---
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
                if (msgContent.equals(keyword.toLowerCase())) {
                    Log.d(TAG, "We received the keyword: "+keyword);
                    keywordReceived(msg_from, context);
                }
            } catch (Exception e) {
                Log.d(TAG, "SMS Exception caught: " + e.getMessage());
            }
        }
    }

    private void keywordReceived(String from, Context context) {
        PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        int nbLogjobs = db.getLogjobs().size();

        Intent serviceIntent = new Intent(context, SmsLocationSendService.class);
        serviceIntent.putExtra("from", from);
        context.startService(serviceIntent);
    }
}


