package net.eneiluj.nextcloud.phonetrack.service;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjobLocation;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.persistence.WebTrackHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import at.bitfire.cert4android.CustomCertManager;

import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.app.PendingIntent.getActivity;
import static android.location.LocationProvider.AVAILABLE;
import static android.location.LocationProvider.OUT_OF_SERVICE;
import static android.location.LocationProvider.TEMPORARILY_UNAVAILABLE;


public class SmsLocationSendService extends IntentService {

    private static final String TAG = SmsLocationSendService.class.getSimpleName();

    public static Map<String, Boolean> isRunning = new HashMap<>();

    private PhoneTrackSQLiteOpenHelper db;
    private LocationManager locManager;
    public static boolean DEBUG = true;
    mLocationListener ll;
    private SmsLocationSendService.LocationThread thread;
    private Looper looper;

    private int c = 0;

    private String from;

    private static int CHANNEL_ID = 11111;
    private static int NOTIFICATION_ID = 1526756641;

    public SmsLocationSendService() {
        super("SmsLocationSendService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (LoggerService.DEBUG) { Log.d(TAG, "[sms send create]"); }

        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        thread = new SmsLocationSendService.LocationThread();
        thread.start();
        looper = thread.getLooper();

        db = PhoneTrackSQLiteOpenHelper.getInstance(this);

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (LoggerService.DEBUG) {
            Log.d(TAG, "[sms send start]");
        }

        from = intent.getStringExtra("from");

        isRunning.put(from, true);

        ll = new mLocationListener();
        if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, ll, looper);
            }
            else {
                Log.d("Location", "GPS is disabled, impossible to get position to send SMS");
            }
        } else {
            Log.d("Location", "no permission to access GPS location");
        }

    }

    private void send(Location location) {
        c++;
        // retry if accuracy is not good enough
        // send anyway if we tried more than 60 times
        if (location.hasAccuracy() && location.getAccuracy() > 50 && c < 60) {
            Log.d("Location", "bad accuracy: " + location.getAccuracy());
            locManager.removeUpdates(ll);
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            ) {
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, ll, looper);
            }
            return;
        }

        locManager.removeUpdates(ll);

        Log.d("Location", "my location is " + location.toString());
        Log.d("Location", "send sms to " + from);

        double battery = getBatteryLevelOnce();

        String latStr = String.format(Locale.ENGLISH,"%.7f", location.getLatitude());
        String lonStr = String.format(Locale.ENGLISH,"%.7f", location.getLongitude());

        String smsContent1 = "* "+getString(R.string.popup_battery_value, battery);
        if (location.hasAltitude()) {
            smsContent1 += "\n* "+getString(R.string.popup_altitude_value, location.getAltitude());
        }
        if (location.hasAccuracy()) {
            smsContent1 += "\n* "+getString(R.string.popup_accuracy_value, location.getAccuracy());
        }
        smsContent1 += "\n* "+getString(R.string.sms_geo_link)+":\ngeo:"+latStr+","+lonStr+"?z=14\n";
        String smsContent2 = "* "+getString(R.string.sms_osm_link)+":\nhttps://www.openstreetmap.org/?mlat="+latStr+"&mlon="+lonStr;
        smsContent2 += "#map=14/"+latStr+"/"+lonStr;
        Log.d("Location1", "SMS content '" + smsContent1 + "' length:" + smsContent1.length());

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        ) {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(from, null, smsContent1, null, null);
            // delay second and third SMS sending
            final String smsContent2f = smsContent2;
            Handler handler2 = new Handler();
            handler2.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d("Location2", "SMS content 2 '" + smsContent2f + "' length:" + smsContent2f.length());
                    smsManager.sendTextMessage(from, null, smsContent2f, null, null);
                    notifySmsWasSent(from);
                }
            }, 1000);
        } else {
            Log.d("SMS", "no permissionnnnnnnnnn to send");
        }

    }

    public void notifySmsWasSent(String from) {
        String notificationFrom = from;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
        ) {
            String contactName = getContactDisplayNameByNumber(from);
            if (!contactName.equals("?")) {
                notificationFrom = contactName;
            }
        }

        createNotificationChannel();

        String chanId = String.valueOf(CHANNEL_ID);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, chanId)
                .setSmallIcon(R.drawable.ic_notify_24dp)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.sms_position_notification, notificationFrom))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                // Set the intent that will fire when the user taps the notification
                //.setContentIntent(pendingIntent)
                //.setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_ID, builder.build());
        NOTIFICATION_ID++;
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String chanId = String.valueOf(CHANNEL_ID);
            CharSequence name = getString(R.string.app_name);
            //String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(chanId, name, importance);
            //channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public String getContactDisplayNameByNumber(String number) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String name = "?";

        ContentResolver contentResolver = getContentResolver();
        Cursor contactLookup = contentResolver.query(uri, new String[] {BaseColumns._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME }, null, null, null);

        try {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                //String contactId = contactLookup.getString(contactLookup.getColumnIndex(BaseColumns._ID));
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        return name;
    }


    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        if (LoggerService.DEBUG) { Log.d(TAG, "[send sms stop]"); }
        isRunning.put(from, false);
        super.onDestroy();
    }

    private class mLocationListener implements LocationListener {

        public mLocationListener() {
        }

        @Override
        public void onLocationChanged(Location loc) {
            send(loc);
        }

        /**
         * Callback on provider disabled
         * @param provider Provider
         */
        @Override
        public void onProviderDisabled(String provider) {
            if (DEBUG) { Log.d(TAG, "[location provider " + provider + " disabled]"); }

        }

        /**
         * Callback on provider enabled
         * @param provider Provider
         */
        @Override
        public void onProviderEnabled(String provider) {
            if (DEBUG) { Log.d(TAG, "[location provider " + provider + " enabled]"); }

        }

        /**
         * Callback on provider status change
         * @param provider Provider
         * @param status Status
         * @param extras Extras
         */
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (DEBUG) {
                final String statusString;
                switch (status) {
                    case OUT_OF_SERVICE:
                        statusString = "out of service";
                        break;
                    case TEMPORARILY_UNAVAILABLE:
                        statusString = "temporarily unavailable";
                        break;
                    case AVAILABLE:
                        statusString = "available";
                        break;
                    default:
                        statusString = "unknown";
                        break;
                }
                if (DEBUG) { Log.d(TAG, "[location status for " + provider + " changed: " + statusString + "]"); }
            }
        }
    }

    private class LocationThread extends HandlerThread {
        LocationThread() {
            super("LoggerThread");
        }
        private final String TAG = SmsLocationSendService.LocationThread.class.getSimpleName();

        @Override
        public void interrupt() {
            if (DEBUG) { Log.d(TAG, "[interrupt]"); }
        }

        @Override
        public void finalize() throws Throwable {
            if (DEBUG) { Log.d(TAG, "[finalize]"); }
            super.finalize();
        }

        @Override
        public void run() {
            if (DEBUG) { Log.d(TAG, "[run]"); }
            super.run();
        }
    }

    private double getBatteryLevelOnce() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if(level == -1 || scale == -1) {
            return 0.0;
        }

        double batLevel = ((double)level / (double)scale) * 100.0;
        batLevel = Math.round(batLevel * 100.0) / 100.0;
        return batLevel;
    }

}
