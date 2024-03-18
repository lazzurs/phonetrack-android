package net.eneiluj.nextcloud.phonetrack.service;

import android.Manifest;
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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.CorrectingLocation;

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
    private String fromNotification;

    private static int CHANNEL_ID = 11111;
    private static int NOTIFICATION_ID = 1526756641;
    private static int TIMEOUT_SECONDS = 120;

    private Runnable mTimeoutRunnable;
    private Handler mTimeoutHandler;

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
        mTimeoutHandler = new Handler(looper);

        db = PhoneTrackSQLiteOpenHelper.getInstance(this);

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (LoggerService.DEBUG) {
            Log.d(TAG, "[sms send start]");
        }

        from = intent.getStringExtra("from");
        fromNotification = getContactNameForNotification(from);

        isRunning.put(from, true);

        ll = new mLocationListener();
        boolean locAllowed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            locAllowed = (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED);
        }
        else {
            locAllowed = (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        }
        if (locAllowed) {
            if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                //locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, ll, looper);
                locManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, ll, looper);
                launchTimeout(TIMEOUT_SECONDS);
            }
            else {
                Log.d("Location", "GPS is disabled, impossible to get position to send SMS");
                sendSmsNoProviderFailure();
                isRunning.put(from, false);
            }
        } else {
            Log.d("Location", "no permission to access GPS location");
            sendSmsPermissionFailure();
            isRunning.put(from, false);
        }

    }

    private void launchTimeout(long nbSec) {
        mTimeoutRunnable = new Runnable() {
            public void run() {
                Log.d(TAG, "SMS sampling timeout hit");
                locManager.removeUpdates(ll);
                sendSmsTimeout();
                isRunning.put(from, false);
            }
        };
        if (LoggerService.DEBUG) { Log.d(TAG, "[sms] launch timeout"); }
        mTimeoutHandler.postDelayed(mTimeoutRunnable, nbSec * 1000);
    }

    private void sendSmsTimeout() {
        SmsManager smsManager = SmsManager.getDefault();
        String smsFailureContent = getString(R.string.sms_failure_timeout_sms, TIMEOUT_SECONDS);
        double battery = getBatteryLevelOnce();
        smsFailureContent += "\n\n* "+getString(R.string.popup_battery_value, battery);
        smsManager.sendTextMessage(from, null, smsFailureContent, null, null);
        String notificationContent = getString(
                R.string.sms_failure_timeout_notification,
                TIMEOUT_SECONDS,
                fromNotification
        );
        notifySmsWasSent(smsFailureContent, notificationContent);
    }

    private void sendSmsPermissionFailure() {
        SmsManager smsManager = SmsManager.getDefault();
        String smsFailureContent = getString(R.string.sms_failure_permission_sms);
        double battery = getBatteryLevelOnce();
        smsFailureContent += "\n\n* "+getString(R.string.popup_battery_value, battery);
        smsManager.sendTextMessage(from, null, smsFailureContent, null, null);
        String notificationContent = getString(
                R.string.sms_failure_permission_notification,
                fromNotification
        );
        notifySmsWasSent(smsFailureContent, notificationContent);
    }

    private void sendSmsNoProviderFailure() {
        SmsManager smsManager = SmsManager.getDefault();
        String smsFailureContent = getString(R.string.sms_failure_provider_sms);
        double battery = getBatteryLevelOnce();
        smsFailureContent += "\n\n* "+getString(R.string.popup_battery_value, battery);
        smsManager.sendTextMessage(from, null, smsFailureContent, null, null);
        String notificationContent = getString(
                R.string.sms_failure_provider_notification,
                fromNotification
        );
        notifySmsWasSent(smsFailureContent, notificationContent);
    }

    private void send(CorrectingLocation loc) {
        c++;
        // retry if accuracy is not good enough
        // send anyway if we tried more than 60 times
        if (loc.hasAccuracy() && loc.getAccuracy() > 50 && c < 60) {
            Log.d("Location", "bad accuracy: " + loc.getAccuracy());
            locManager.removeUpdates(ll);
            boolean locAllowed;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                locAllowed = (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED);
            }
            else {
                locAllowed = (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
            }
            if (locAllowed) {
                //locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, ll, looper);
                locManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, ll, looper);
            }
            return;
        }

        // from here we accept the location
        // so we clean location manager and timeout
        locManager.removeUpdates(ll);
        // Cancel timeout runnable
        if (mTimeoutHandler != null) {
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
            mTimeoutRunnable = null;
        }

        Log.d("Location", "my location is " + loc.toString());
        Log.d("Location", "send sms to " + from);

        double battery = getBatteryLevelOnce();

        String latStr = String.format(Locale.ENGLISH,"%.7f", loc.getLatitude());
        String lonStr = String.format(Locale.ENGLISH,"%.7f", loc.getLongitude());

        String smsContent1 = "* " + getString(R.string.popup_battery_value, battery);
        if (loc.hasAltitude()) {
            smsContent1 += "\n* " + getString(R.string.popup_altitude_value, loc.getAltitude());
        }
        if (loc.hasAccuracy()) {
            smsContent1 += "\n* " + getString(R.string.popup_accuracy_value, loc.getAccuracy());
        }
        if (loc.hasSpeed()) {
            smsContent1 += "\n* " + getString(R.string.popup_speed_value, loc.getSpeed() * 3.6);
        }
        if (loc.hasBearing()) {
            smsContent1 += "\n* " + getString(R.string.sms_bearing_value, loc.getBearing());
        }
        String smsContent2 = "\n* "+getString(R.string.sms_geo_link) + ":\ngeo:"+latStr+","+lonStr+"?z=14\n";
        String smsContent3 = "* " + getString(R.string.sms_osm_link) + ":\nhttps://www.openstreetmap.org/?mlat=" + latStr + "&mlon=" + lonStr;
        smsContent3 += "#map=14/" + latStr + "/" + lonStr;
        Log.d("Location1", "SMS content '" + smsContent1 + "' length:" + smsContent1.length());

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        ) {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(from, null, smsContent1, null, null);
            // delay second SMS sending
            final String smsContent1f = smsContent1;
            final String smsContent2f = smsContent2;
            final String smsContent3f = smsContent3;
            Handler handler2 = new Handler();
            handler2.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d("Location2", "SMS content 2 '" + smsContent2f + "' length:" + smsContent2f.length());
                    smsManager.sendTextMessage(from, null, smsContent2f, null, null);
                }
            }, 1000);
            Handler handler3 = new Handler();
            handler3.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d("Location3", "SMS content 3 '" + smsContent3f + "' length:" + smsContent3f.length());
                    smsManager.sendTextMessage(from, null, smsContent3f, null, null);
                    String notificationContent = getString(R.string.sms_position_notification, fromNotification);
                    notifySmsWasSent(smsContent1f + "\n" + smsContent2f + "\n" + smsContent3f, notificationContent);
                    isRunning.put(from, false);
                }
            }, 2000);
        } else {
            Log.d("SMS", "no permissionnnnnnnnnn to send");
        }

    }

    public void notifySmsWasSent(String smsContent, String notificationContent) {
        // intent of notification
        Intent ptIntent = new Intent(getApplicationContext(), LogjobsListViewActivity.class);
        ptIntent.putExtra(LogjobsListViewActivity.PARAM_SMSINFO_CONTENT, smsContent);
        ptIntent.putExtra(LogjobsListViewActivity.PARAM_SMSINFO_FROM, fromNotification);

        createNotificationChannel();

        String chanId = String.valueOf(CHANNEL_ID);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, chanId)
                .setSmallIcon(R.drawable.ic_notify_24dp)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(notificationContent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(PendingIntent.getActivity(this, 1, ptIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT))
                .setAutoCancel(true);

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

    public String getContactNameForNotification(String number) {
        String result = number;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
        ) {
            String contactName = getContactDisplayNameByNumber(from);
            if (!contactName.equals("?")) {
                result = contactName;
            }
        }
        return result;
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
        if (LoggerService.DEBUG) { Log.d(TAG, "[send sms service stop]"); }
        super.onDestroy();
    }

    private class mLocationListener implements LocationListener {

        public mLocationListener() {
        }

        @Override
        public void onLocationChanged(Location location) {
            CorrectingLocation loc = new CorrectingLocation(location);
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
