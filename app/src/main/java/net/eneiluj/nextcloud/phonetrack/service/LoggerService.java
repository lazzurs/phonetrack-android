/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.eneiluj.nextcloud.phonetrack.service;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import androidx.preference.PreferenceManager;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.android.fragment.PreferencesFragment;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import static android.location.LocationProvider.AVAILABLE;
import static android.location.LocationProvider.OUT_OF_SERVICE;
import static android.location.LocationProvider.TEMPORARILY_UNAVAILABLE;

/**
 * Background service logging positions to database
 * and synchronizing with remote server.
 *
 */

public class LoggerService extends Service {

    public static double battery = -1.0;

    private static final String TAG = LoggerService.class.getSimpleName();
    public static final String BROADCAST_LOCATION_STARTED = "net.eneiluj.nextcloud.phonetrack.broadcast.location_started";
    public static final String BROADCAST_LOCATION_STOPPED = "net.eneiluj.nextcloud.phonetrack.broadcast.location_stopped";
    public static final String BROADCAST_LOCATION_UPDATED = "net.eneiluj.nextcloud.phonetrack.broadcast.location_updated";
    public static final String BROADCAST_LOCATION_PERMISSION_DENIED = "net.eneiluj.nextcloud.phonetrack.broadcast.location_permission_denied";
    public static final String BROADCAST_LOCATION_NETWORK_DISABLED = "net.eneiluj.nextcloud.phonetrack.broadcast.network_disabled";
    public static final String BROADCAST_LOCATION_GPS_DISABLED = "net.eneiluj.nextcloud.phonetrack.broadcast.gps_disabled";
    public static final String BROADCAST_LOCATION_NETWORK_ENABLED = "net.eneiluj.nextcloud.phonetrack.broadcast.network_enabled";
    public static final String BROADCAST_LOCATION_GPS_ENABLED = "net.eneiluj.nextcloud.phonetrack.broadcast.gps_enabled";
    public static final String BROADCAST_LOCATION_DISABLED = "net.eneiluj.nextcloud.phonetrack.broadcast.location_disabled";
    public static final String BROADCAST_EXTRA_PARAM = "net.eneiluj.nextcloud.phonetrack.broadcast.extra_param";
    public static final String BROADCAST_ERROR_MESSAGE = "net.eneiluj.nextcloud.phonetrack.broadcast.error_message";
    public static final String UPDATE_NOTIFICATION = "net.eneiluj.nextcloud.phonetrack.UPDATE_NOTIFICATION";

    private Intent syncIntent;

    private static volatile boolean isRunning = false;
    private static volatile boolean firstRun = false;
    private LoggerThread thread;
    private Looper looper;
    private LocationManager locManager;
    private Map<Long, mLocationListener> locListeners;
    private Map<Long, DBLogjob> logjobs;
    private PhoneTrackSQLiteOpenHelper db;

    private Map<Long, Location> lastLocations;
    private static volatile Map<Long, Long> lastUpdateRealtime;

    private final int NOTIFICATION_ID = 1526756640;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder;
    private boolean useGps = true;
    private boolean useNet = true;
    public static boolean DEBUG = true;

    private Map<Long, SignificantMotionJobWorker> mSignificantMotionJobs;

    private ConnectionStateMonitor connectionMonitor;

    /**
     * Basic initializations.
     */
    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.d(TAG, "[onCreate]");
        }
        firstRun = true;

        connectionMonitor = null;

        db = PhoneTrackSQLiteOpenHelper.getInstance(getApplicationContext());

        syncIntent = new Intent(getApplicationContext(), WebTrackService.class);
        // start websync service if needed
        if (db.getLocationNotSyncedCount() > 0) {
            startService(syncIntent);
        }

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
        }

        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        lastLocations = new HashMap<>();
        lastUpdateRealtime = new HashMap<>();
        locListeners = new HashMap<>();
        logjobs = new HashMap<>();
        mSignificantMotionJobs = new HashMap<>();

        List<DBLogjob> ljs = db.getLogjobs();
        for (DBLogjob ljob : ljs) {
            if (ljob.isEnabled()) {
                mLocationListener ll = new mLocationListener(ljob);
                locListeners.put(ljob.getId(), ll);
                logjobs.put(ljob.getId(), ljob);
                lastLocations.put(ljob.getId(), null);
                lastUpdateRealtime.put(ljob.getId(), Long.valueOf(0));

                SignificantMotionJobWorker jw = new SignificantMotionJobWorker(ljob, ll);
                mSignificantMotionJobs.put(ljob.getId(), jw);
            }
        }

        // read user preferences
        updatePreferences(null);

        int nbEnabled = 0;
        for (DBLogjob lj : ljs) {
            if (lj.isEnabled()) {
                requestLocationUpdates(lj.getId());
                nbEnabled++;
            }
        }

        if (nbEnabled > 0) {
            final Notification notification = showNotification(NOTIFICATION_ID);
            startForeground(NOTIFICATION_ID, notification);
            updateNotificationContent();

            isRunning = true;

            sendBroadcast(BROADCAST_LOCATION_STARTED);

            thread = new LoggerThread();
            thread.start();
            looper = thread.getLooper();

            battery = getBatteryLevelOnce();
            // register for battery level
            this.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // track network connectivity changes
                connectionMonitor = new ConnectionStateMonitor();
                connectionMonitor.enable(getApplicationContext());
            }
        }
        else {
            final Notification notification = showNotification(NOTIFICATION_ID);
            startForeground(NOTIFICATION_ID, notification);
            if (DEBUG) {
                Log.d(TAG, "[onCreate : stop because no logjob enabled]");
            }
            stopSelf();
        }
    }

    /**
     * Start main thread, request location updates, start synchronization.
     *
     * @param intent Intent
     * @param flags Flags
     * @param startId Unique id
     * @return Always returns START_STICKY
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            final boolean logjobsUpdated = (intent != null) && intent.getBooleanExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, false);
            final boolean providersUpdated = (intent != null) && intent.getBooleanExtra(PreferencesFragment.UPDATED_PROVIDERS, false);
            final boolean updateNotif = (intent != null) && intent.getBooleanExtra(UPDATE_NOTIFICATION, false);
            if (logjobsUpdated) {
                // this to avoid doing two loc upd when service is down and then a logjob is enabled
                // in this scenario, we run onCreate which already does it all, no need to handle logjo updated
                if (firstRun) {
                    if (DEBUG) {
                        Log.d(TAG, "[onStartCommand : upd logjob but firstrun so nothing]");
                    }
                } else {
                    long ljId = intent.getLongExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, 0);
                    if (DEBUG) {
                        Log.d(TAG, "[onStartCommand : upd logjob]");
                    }
                    handleLogjobUpdated(ljId);
                }
            } else if (providersUpdated) {
                if (DEBUG) {
                    Log.d(TAG, "[onStartCommand : upd providers]");
                }
                String providersValue = intent.getStringExtra(PreferencesFragment.UPDATED_PROVIDERS_VALUE);
                updatePreferences(providersValue);
                for (long ljId : logjobs.keySet()) {
                    requestLocationUpdates(ljId);
                }
            } else if (updateNotif && isRunning) {
                updateNotificationContent();
            } else {
                // start without parameter
                if (DEBUG) {
                    Log.d(TAG, "[onStartCommand : start without parameter]");
                }
            }
            // anyway, first run is over
            firstRun = false;
        }

        return START_STICKY;
    }

    /**
     * When user updated a logjob, restart location updates, stop service on failure
     */
    private void handleLogjobUpdated(long ljId) {
        boolean wasAlreadyThere = logjobs.containsKey(ljId);
        updateLogjob(ljId);
        // if it was not deleted or disabled
        if (logjobs.containsKey(ljId)) {
            if (isRunning) {
                // it was modified
                if (wasAlreadyThere) {
                    restartUpdates(ljId);
                }
                // it was created
                else {
                    requestLocationUpdates(ljId);
                }
            }
        }
        // it was deleted or disabled
        else {
            if (logjobs.isEmpty()) {
                stopSelf();
            }
        }
    }

    /**
     * Check if user granted permission to access location.
     *
     * @return True if permission granted, false otherwise
     */
    private boolean canAccessLocation() {
        return (
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        );
    }

    /**
     * Check if given provider exists on device
     * @param provider Provider
     * @return True if exists, false otherwise
     */
    private boolean providerExists(String provider) {
        return locManager.getAllProviders().contains(provider);
    }

    /**
     * Reread preferences
     */
    private void updatePreferences(String value) {
        String providersPref;
        if (value == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            providersPref = prefs.getString(getString(R.string.pref_key_providers), "1");
        }
        else {
            providersPref = value;
        }
        useGps = (!providersPref.equals("2") && providerExists(LocationManager.GPS_PROVIDER));
        useNet = (!providersPref.equals("1") && providerExists(LocationManager.NETWORK_PROVIDER));
        if (DEBUG) { Log.d(TAG, "[update prefs "+providersPref+", gps : "+useGps+", net : "+useNet+"]"); }
    }

    /**
     * update internal values
     *
     * logjob might have been added, modified or deleted
     *
     */
    private void updateLogjob(long ljId) {
        DBLogjob lj = db.getLogjob(ljId);
        if (lj != null && lj.isEnabled()) {
            // new or modified : update logjob
            logjobs.put(ljId, lj);

            // this is a new logjob
            if (!locListeners.containsKey(ljId)) {
                mLocationListener ll = new mLocationListener(lj);
                locListeners.put(ljId, ll);
                lastLocations.put(ljId, null);
                lastUpdateRealtime.put(ljId, Long.valueOf(0));

                // Assume motion exists when beginning logging
                SignificantMotionJobWorker jw = new SignificantMotionJobWorker(lj, ll);
                mSignificantMotionJobs.put(ljId, jw);
            } else {
                // Update listener for changed parameters
                locListeners.get(ljId).populateFromLogjob(lj);

                mSignificantMotionJobs.get(ljId).stop();
                mSignificantMotionJobs.get(ljId).populate(lj);
            }
        }
        // it has been deleted or disabled
        else {
            if (locListeners.containsKey(ljId)) {
                // Stop requested updates, sleeping motion-based job
                stopJob(ljId);

                locListeners.remove(ljId);
                lastLocations.remove(ljId);
                lastUpdateRealtime.remove(ljId);
                logjobs.remove(ljId);
                mSignificantMotionJobs.remove(ljId);
            }
        }
    }

    /**
     * Restart request for location updates
     *
     * @return True if succeeded, false otherwise (eg. disabled all providers)
     */
    private boolean restartUpdates(long jobId) {
        if (DEBUG) { Log.d(TAG, "[job "+jobId+" location updates restart]"); }

        stopJob(jobId);

        return requestLocationUpdates(jobId);
    }

    private void stopJob(long jobId) {
        locManager.removeUpdates(locListeners.get(jobId));

        // If using significant motion stop any runnables waiting for an interval
        DBLogjob lj = db.getLogjob(jobId);
        if (lj != null && lj.useSignificantMotion()) {
            mSignificantMotionJobs.get(jobId).stop();
        }
    }

    /**
     * Request location updates
     * @return True if succeeded from at least one provider
     */
    @SuppressWarnings({"MissingPermission"})
    private boolean requestLocationUpdates(long ljId) {
        // here we start a location request for each activated logjob
        DBLogjob lj = logjobs.get(ljId);
        int minTimeMillis = lj.getMinTime() * 1000;
        int minDistance = lj.getMinDistance();
        boolean keepGpsOn = lj.keepGpsOnBetweenFixes();
        mLocationListener locListener = locListeners.get(ljId);
        boolean hasLocationUpdates = false;
        if (canAccessLocation()) {
            if (useNet) {
                //noinspection MissingPermission
                if (lj.useSignificantMotion()) {
                    // Significant motion based sampling, request single update (for now?)
                    locManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locListener, looper);
                } else {
                    locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMillis, minDistance, locListener, looper);
                }
                if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    hasLocationUpdates = true;
                    if (DEBUG) { Log.d(TAG, "job "+ljId+" [Using net provider, freq "+lj.getMinTime()+"]"); }
                }
            }
            if (useGps) {
                //noinspection MissingPermission
                if (lj.useSignificantMotion()) {
                    // Significant motion based sampling, request single update (for now?)
                    locManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locListener, looper);
                } else {
                    if (keepGpsOn) {
                        minTimeMillis = 1000;
                    }
                    locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMillis, minDistance, locListener, looper);
                }
                if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    hasLocationUpdates = true;
                    if (DEBUG) { Log.d(TAG, "job "+ljId+" [Using gps provider, freq "+(minTimeMillis/1000)+"]"); }
                }
            }
            if (hasLocationUpdates) {
                if (lj.useSignificantMotion()) {
                    // If we don't get a GPS result back after a timeout, use the network result (if we have one) or reschedule
                    mSignificantMotionJobs.get(ljId).startResultTimeout();
                }
            } else {
                // no location provider available
                sendBroadcast(BROADCAST_LOCATION_DISABLED);
                if (DEBUG) { Log.d(TAG, "job "+ljId+"[No available location updates]"); }
            }
        } else {
            // can't access location
            sendBroadcast(BROADCAST_LOCATION_PERMISSION_DENIED);
            if (DEBUG) { Log.d(TAG, "job "+ljId+"[Location permission denied]"); }
        }

        return hasLocationUpdates;
    }

    /**
     * Service cleanup
     */
    @Override
    public void onDestroy() {
        if (DEBUG) { Log.d(TAG, "[onDestroy]"); }

        if (canAccessLocation()) {
            //noinspection MissingPermission
            for (long ljId : locListeners.keySet()) {
                stopJob(ljId);
            }
        }

        isRunning = false;

        mNotificationManager.cancel(NOTIFICATION_ID);


        if (thread != null) {
            thread.interrupt();
            unregisterReceiver(mBatInfoReceiver);
            sendBroadcast(BROADCAST_LOCATION_STOPPED);
        }
        thread = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (connectionMonitor != null) {
                connectionMonitor.disable(getApplicationContext());
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Check if logger service is running.
     *
     * @return True if running, false otherwise
     */
    public static boolean isRunning() {
        return isRunning;
    }

    /**
     * Return realtime of last update in milliseconds
     *
     * @return Time or zero if not set
     */
    public static long lastUpdateRealtime(long ljId) {
        return lastUpdateRealtime.get(ljId);
    }

    /**
     * Reset realtime of last update
     */
    public static void resetUpdateRealtime(long ljId) {
        lastUpdateRealtime.put(ljId, Long.valueOf(0));
    }

    /**
     * Main service thread class handling location updates.
     */
    private class LoggerThread extends HandlerThread {
        LoggerThread() {
            super("LoggerThread");
        }
        private final String TAG = LoggerThread.class.getSimpleName();

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

    /**
     * Show notification
     *
     * @param mId Notification Id
     */
    private Notification showNotification(int mId) {
        if (DEBUG) { Log.d(TAG, "[showNotification " + mId + "]"); }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean lowImportance = prefs.getBoolean(getString(R.string.pref_key_notification_importance), false);
        int priority = NotificationCompat.PRIORITY_DEFAULT;
        if (lowImportance) {
            priority = NotificationCompat.PRIORITY_MIN;
        }

        final String channelId = String.valueOf(mId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(channelId, lowImportance);
        }
        String nbLocations = String.valueOf(db.getLocationNotSyncedCount());
        String nbSent = String.valueOf(db.getNbTotalSync());
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notify_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setPriority(priority)
                        .setOnlyAlertOnce(true)
                        .setContentText(String.format(getString(R.string.is_running), nbLocations, nbSent));
                        //.setSmallIcon(R.drawable.ic_stat_notify_24dp)
                        //.setContentText(String.format(getString(R.string.is_running), getString(R.string.app_name)));
        mNotificationBuilder = mBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mBuilder.setChannelId(channelId);
        }

        Intent resultIntent = new Intent(this, LogjobsListViewActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(LogjobsListViewActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        Notification mNotification = mBuilder.build();
        mNotificationManager.notify(mId, mNotification);
        return mNotification;
    }

    private void updateNotificationContent() {
        String nbLocations = String.valueOf(db.getLocationNotSyncedCount());
        String nbSent = String.valueOf(db.getNbTotalSync());
        mNotificationBuilder.setContentText(String.format(getString(R.string.is_running), nbLocations, nbSent));
        mNotificationManager.notify(this.NOTIFICATION_ID, mNotificationBuilder.build());
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel(String channelId, boolean lowImportance) {
        int importance = NotificationManager.IMPORTANCE_LOW;
        if (lowImportance) {
            importance = NotificationManager.IMPORTANCE_MIN;
        }
        NotificationChannel chan = new NotificationChannel(channelId, getString(R.string.app_name), importance);
        mNotificationManager.createNotificationChannel(chan);
    }

    /**
     * Send broadcast message
     * @param broadcast Broadcast message
     */
    private void sendBroadcast(String broadcast) {
        Intent intent = new Intent(broadcast);
        sendBroadcast(intent);
    }

    /**
     * Send broadcast message
     * @param broadcast Broadcast message
     */
    private void sendBroadcast(String broadcast, long ljId) {
        Intent intent = new Intent(broadcast);
        intent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, ljId);
        sendBroadcast(intent);
    }

    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if(level == -1 || scale == -1) {
                battery = 0.0;
            }
            double batLevel = ((double)level / (double)scale) * 100.0;
            battery = Math.round(batLevel * 100.0) / 100.0;
            if (LoggerService.DEBUG) { Log.d(TAG, "[BATT changed " + battery + "]"); }
        }
    };

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

    /**
     * Location listener class
     */
    private class mLocationListener implements LocationListener {

        private DBLogjob logjob;
        private long logjobId;
        private long minTimeTolerance;
        private long maxTimeMillis;
        private long minTimeMillis;
        private boolean keepGpsOn;
        private boolean useSignificantMotion;

        public mLocationListener(DBLogjob logjob) {
            populateFromLogjob(logjob);
        }

        /**
         * Populate logging job and cache values
         * @param logjob The logging job
         */
        public void populateFromLogjob(DBLogjob logjob) {
            this.logjob = logjob;
            this.logjobId = logjob.getId();
            this.keepGpsOn = logjob.keepGpsOnBetweenFixes();
            this.useSignificantMotion = logjob.useSignificantMotion();
            // max time tolerance is half min time, but not more that 5 min
            this.minTimeMillis = logjob.getMinTime() * 1000;
            minTimeTolerance = Math.min(minTimeMillis / 2, 5 * 60 * 1000);
            maxTimeMillis = minTimeMillis + minTimeTolerance;
        }

        @Override
        public void onLocationChanged(Location loc) {

            if (DEBUG) { Log.d(TAG, "[location changed: " + logjobId + "/"+ logjob.getTitle() +" : bat : "+ battery+", " + loc + "]"); }

            if (useSignificantMotion) {
                mSignificantMotionJobs.get(logjobId).handleLocationChange(loc);
            } else {
                if (!skipLocation(logjob, loc))
                    acceptAndSyncLocation(logjobId, loc);
            }
        }

        /**
         * Should the location be logged or skipped
         * @param loc Location
         * @return True if skipped
         */
        private boolean skipLocation(DBLogjob logjob, Location loc) {
            // if we keep gps on, we take care of the timing between points
            if (keepGpsOn) {
                long elapsedMillisSinceLastUpdate;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedMillisSinceLastUpdate = SystemClock.elapsedRealtime() - lastUpdateRealtime.get(logjobId);
                }
                else {
                    elapsedMillisSinceLastUpdate = (loc.getElapsedRealtimeNanos() / 1000000) - lastUpdateRealtime.get(logjobId);
                }
                if (elapsedMillisSinceLastUpdate < minTimeMillis) {
                    if (DEBUG) { Log.d(TAG,"skip because "+elapsedMillisSinceLastUpdate + " < "+ minTimeMillis); }
                    return true;
                }
            }
            int maxAccuracy = logjob.getMinAccuracy();
            // accuracy radius too high
            if (loc.hasAccuracy() && loc.getAccuracy() > maxAccuracy) {
                if (DEBUG) { Log.d(TAG, "[location accuracy above limit: " + loc.getAccuracy() + " > " + maxAccuracy + "]"); }
                // reset gps provider to get better accuracy even if time and distance criteria don't change
                if (loc.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                    restartUpdates(logjobId);
                }
                return true;
            }
            // use network provider only if recent gps data is missing
            if (loc.getProvider().equals(LocationManager.NETWORK_PROVIDER) && lastLocations.get(logjobId) != null) {
                // we received update from gps provider not later than after maxTime period
                long elapsedMillis = SystemClock.elapsedRealtime() - lastUpdateRealtime.get(logjobId);
                if (lastLocations.get(logjobId).getProvider().equals(LocationManager.GPS_PROVIDER) && elapsedMillis < maxTimeMillis) {
                    // skip network provider
                    if (DEBUG) { Log.d(TAG, "[location network provider skipped]"); }
                    return true;
                }
            }
            return false;
        }

        /**
         * Callback on provider disabled
         * @param provider Provider
         */
        @Override
        public void onProviderDisabled(String provider) {
            if (DEBUG) { Log.d(TAG, "[location provider " + provider + " disabled]"); }
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_GPS_DISABLED);
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_NETWORK_DISABLED);
            }
        }

        /**
         * Callback on provider enabled
         * @param provider Provider
         */
        @Override
        public void onProviderEnabled(String provider) {
            if (DEBUG) { Log.d(TAG, "[location provider " + provider + " enabled]"); }
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_GPS_ENABLED);
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_NETWORK_ENABLED);
            }
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private class ConnectionStateMonitor extends ConnectivityManager.NetworkCallback {

        final NetworkRequest networkRequest;

        public ConnectionStateMonitor() {
            networkRequest = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        }

        public void enable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.registerNetworkCallback(networkRequest , this);
        }

        // Likewise, you can have a disable method that simply calls ConnectivityManager#unregisterCallback(networkRequest) too.

        public void disable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(this);
        }

        @Override
        public void onAvailable(Network network) {
            if (DEBUG) { Log.d(TAG, "Network is available again : launch sync from loggerservice"); }
            try {
                // just to be sure the connection is effective
                // sometimes i experienced problems when connecting to slow wifi networks
                // i think internet access was not yet established when syncService was launched
                TimeUnit.SECONDS.sleep(5);
            }
            catch (InterruptedException e) {

            }
            startService(syncIntent);
        }
    }

    private void acceptAndSyncLocation(long logjobId, Location loc) {
        lastLocations.put(logjobId, loc);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            lastUpdateRealtime.put(logjobId, SystemClock.elapsedRealtime());
        }
        else {
            lastUpdateRealtime.put(logjobId, loc.getElapsedRealtimeNanos() / 1000000);
        }
        db.addLocation(logjobId, loc, battery);

        sendBroadcast(BROADCAST_LOCATION_UPDATED, logjobId);
        updateNotificationContent();

        Intent syncOneDev = new Intent(getApplicationContext(), WebTrackService.class);
        syncOneDev.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, logjobId);
        startService(syncOneDev);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private class SignificantMotionJobWorker extends TriggerEventListener {
        private mLocationListener mLocationListener;
        private DBLogjob mLogJob;
        private long mJobId;

        private Long mLastUpdateRealtime;

        private Handler mIntervalHandler;
        private Runnable mIntervalRunnable;
        private Handler mTimeoutHandler;
        private Runnable mTimeoutRunnable;
        private Boolean mMotionDetected;
        private Location mCachedNetworkResult;

        private SensorManager mSensorManager;
        private Sensor mSensor;

        private long mIntervalTimeMillis;
        private boolean mUseInterval;
        private int mLocationTimeout;

        SignificantMotionJobWorker(DBLogjob logjob, mLocationListener listener) {
            populate(logjob);
            mLocationListener = listener;

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
        }

        private void populate(DBLogjob logjob) {
            mLogJob = logjob;
            mJobId = logjob.getId();

            mLastUpdateRealtime = Long.valueOf(0);

            mIntervalHandler = null;
            mIntervalRunnable = null;
            mTimeoutHandler = null;
            mTimeoutRunnable = null;
            mCachedNetworkResult = null;

            mIntervalTimeMillis = mLogJob.getMinTime() * 1000;
            mUseInterval = mIntervalTimeMillis > 0;
            mLocationTimeout = mLogJob.getLocationRequestTimeout();
        }

        private Runnable createSampleTimeoutDelayRunnable() {

            Runnable runnable = new Runnable() {
                public void run() {
                    Log.d(TAG, "Sampling timeout hit");

                    if (mCachedNetworkResult != null) {
                        // Cancel location request
                        if (useGps) {
                            locManager.removeUpdates(mLocationListener);
                        }

                        Log.d(TAG, "Reached timeout before GPS sample, using network sample");
                        acceptAndSyncLocation(mJobId, mCachedNetworkResult);

                        mCachedNetworkResult = null;
                    } else {
                        // Cancel location request
                        if (useGps || useNet) {
                            locManager.removeUpdates(mLocationListener);
                        }
                    }

                    // Clear significant motion flag for next interval
                    mMotionDetected  = false;

                    // Request significant motion notification
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        mSensorManager.requestTriggerSensor(SignificantMotionJobWorker.this, mSensor);
                    }

                    // Schedule sample for X seconds from last sample
                    if (mUseInterval)
                        scheduleSampleAfterInterval(mIntervalTimeMillis);
                }
            };
            return runnable;
        }

        private void scheduleSampleAfterInterval(long millisDelay) {
            Log.d(TAG, "Scheduling sampling delay for " + millisDelay/1000.0 + "s");
            // Create new handler if one doesn't exist
            if (mIntervalHandler == null) {
                mIntervalHandler = new Handler();
            }

            mIntervalRunnable = new Runnable() {
                public void run() {
                    if (mMotionDetected) {
                        Log.d(TAG, "Significant motion detected during delay, recording point");

                        // Assists with ensuring we don't end up with two interval sequences running for one job
                        mIntervalRunnable = null;

                        requestLocationUpdates(mJobId);
                    } else {
                        Log.d(TAG, "No significant motion, not recording point");

                        scheduleSampleAfterInterval(mIntervalTimeMillis);
                    }
                }
            };

            // Ensure significant motion notifications are enabled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                mSensorManager.requestTriggerSensor(SignificantMotionJobWorker.this, mSensor);

            // Create and post
            mIntervalHandler.postDelayed(mIntervalRunnable, millisDelay);
        }

        private void handleLocationChange(Location loc) {
            if (loc.getProvider().equals(LocationManager.GPS_PROVIDER) || !useGps) {
                // Got GPS result, accept
                Log.d(TAG, "Got result, immediately accepting");

                // Cancel timeout runnable
                if (mTimeoutHandler != null) {
                    mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
                    mTimeoutRunnable = null;
                }

                // Remove any cached network result or disable update
                if (mCachedNetworkResult == null) {
                    if (useNet && loc.getProvider().equals(LocationManager.GPS_PROVIDER))
                        locManager.removeUpdates(mLocationListener);
                } else {
                    mCachedNetworkResult = null;
                }

                // Accept, store and sync location
                acceptAndSyncLocation(mJobId, loc);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    mLastUpdateRealtime = SystemClock.elapsedRealtime();
                } else {
                    mLastUpdateRealtime = loc.getElapsedRealtimeNanos() / 1000000;
                }

                // Clear significant motion flag for next interval
                mMotionDetected = false;

                // Request significant motion notification
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    mSensorManager.requestTriggerSensor(SignificantMotionJobWorker.this, mSensor);
                }

                // If using an interval, schedule sample for X seconds from last sample
                if (mUseInterval)
                    scheduleSampleAfterInterval(mLogJob.getMinTime() * 1000);
            } else {
                Log.d(TAG, "Network location returned first, caching");
                // Cache lower quality network result
                mCachedNetworkResult = loc;
            }
        }

        private void startResultTimeout() {
            mCachedNetworkResult = null;

            if (mLocationTimeout > 0) {
                if (mTimeoutHandler == null)
                    mTimeoutHandler = new Handler();

                // Create and post
                mTimeoutRunnable = createSampleTimeoutDelayRunnable();
                Log.d(TAG, "Waiting " + mLocationTimeout + "s for timeout");
                mTimeoutHandler.postDelayed(mTimeoutRunnable, mLocationTimeout * 1000);
            }
        }

        private void stop() {
            if (mIntervalHandler != null) {
                if (mIntervalRunnable != null) {
                    mIntervalHandler.removeCallbacks(mIntervalRunnable);
                    mIntervalRunnable = null;
                }
                mIntervalHandler = null;
            }
            if (mTimeoutHandler != null) {
                if (mTimeoutRunnable != null) {
                    mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
                    mTimeoutRunnable = null;
                }
                mTimeoutHandler = null;
            }
        }

        @Override
        public void onTrigger(TriggerEvent event) {
            Log.d(TAG, "Significant motion seen");

            // Flag motion in interval
            mMotionDetected = true;

            // If the job doesn't have a minimum interval, or hasn't taken a sample for longer than its interval,
            // sample immediately
            long millisSinceLast = SystemClock.elapsedRealtime() - mLastUpdateRealtime;
            if (!mUseInterval || millisSinceLast > mIntervalTimeMillis) {
                // If not using an interval, definitely request updates
                boolean requestUpdates = !mUseInterval;

                // If we're interval-based and there is a runnable waiting for the next interval we know we haven't
                // already requested a location. This checks helps us prevent having two sampling sequences running
                // for the same job.
                if (mUseInterval && mIntervalRunnable != null) {
                    // Stop waiting runnable
                    mIntervalHandler.removeCallbacks(mIntervalRunnable);
                    mIntervalRunnable = null;

                    Log.d(TAG, "Triggering immediate sample after significant motion due to " +
                            millisSinceLast / 1000.0 + "s since last point");

                    requestUpdates = true;
                }

                if (requestUpdates)
                    requestLocationUpdates(mJobId);
            }

            // Request notification for next significant motion
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mSensorManager.requestTriggerSensor(SignificantMotionJobWorker.this, mSensor);
            }
        }
    }
}
