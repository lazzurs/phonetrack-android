package net.eneiluj.nextcloud.phonetrack.service;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private PhoneTrackSQLiteOpenHelper db;
    private LocationManager locManager;
    public static boolean DEBUG = true;
    mLocationListener ll;
    private SmsLocationSendService.LocationThread thread;
    private Looper looper;

    private int c = 0;

    private String from;

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

        ll = new mLocationListener();
        if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        ) {
            locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, ll, looper);
        } else {
            Log.d("Location", "no permissionnnnnnnnnn");
        }

    }

    private void send(Location location) {
        c++;
        // retry if accuracy is not good enough
        // send anyway if we tried more than 60 times
        if (location.hasAccuracy() && location.getAccuracy() > 25 && c < 60) {
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

        Log.d("Location", "my location is " + location.toString());
        Log.d("Location", "send sms to " + from);

        String smsContent = "Current position: geo:"+location.getLatitude()+","+location.getLongitude()+"?z=14";
        Log.d("Location", "SMS content " + smsContent);

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        ) {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(from, null, smsContent, null, null);
            thread.interrupt();
            locManager.removeUpdates(ll);
        } else {
            Log.d("SMS", "no permissionnnnnnnnnn to send");
        }


    }

    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        if (LoggerService.DEBUG) { Log.d(TAG, "[send sms stop]"); }
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

}
