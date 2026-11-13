package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.CorrectingLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** The logging notification: its own channel, what it says, and its stop button. */
@RunWith(AndroidJUnit4.class)
public class LoggingNotificationTest {

    private Application app;
    private PhoneTrackSQLiteOpenHelper db;
    private NotificationManager notificationManager;
    private ServiceController<LoggerService> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
        db = PhoneTrackSQLiteOpenHelper.getInstance(app);
        notificationManager = app.getSystemService(NotificationManager.class);
        // ready to log, so that no log job is paused (see LoggerServicePauseTest)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        shadowOf(app.getSystemService(LocationManager.class)).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        DbTestSupport.resetDatabase();
    }

    private long logjob(String title, boolean enabled) {
        return db.addLogjob(new DBLogjob(0, title, "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, false, false, 60, false, enabled, 0, null, null, false));
    }

    private void addPosition(long logjobId) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(53.35);
        location.setLongitude(-6.26);
        location.setTime(System.currentTimeMillis());
        db.addLocation(logjobId, new CorrectingLocation(location), 80.0);
    }

    private void startService() {
        controller = Robolectric.buildService(LoggerService.class).create();
        controller.get().onStartCommand(new Intent(app, LoggerService.class), 0, 1);
    }

    private Notification loggingNotification() {
        for (Notification n : shadowOf(notificationManager).getAllNotifications()) {
            if (NotificationChannels.LOGGING.equals(n.getChannelId())) {
                return n;
            }
        }
        return null;
    }

    @Test
    public void loggingChannelHasItsOwnNameAndADescription() {
        logjob("hike", true);
        startService();

        NotificationChannel channel = notificationManager.getNotificationChannel(NotificationChannels.LOGGING);
        assertNotNull(channel);
        assertNotEquals(app.getString(R.string.app_name), channel.getName().toString());
        assertEquals(app.getString(R.string.notification_channel_logging), channel.getName().toString());
        assertFalse(channel.getDescription() == null || channel.getDescription().isEmpty());
    }

    @Test
    public void notificationSaysHowManyLogjobsRunAndHowManyPositionsWait() {
        long hike = logjob("hike", true);
        logjob("car", true);
        logjob("off", false);
        addPosition(hike);
        addPosition(hike);
        addPosition(hike);
        startService();

        Notification n = loggingNotification();
        assertNotNull(n);
        assertEquals("Logging 2 log jobs", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("3 positions waiting to upload", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
    }

    @Test
    public void nothingWaitingIsSaidPlainly() {
        logjob("hike", true);
        startService();

        Notification n = loggingNotification();
        assertEquals("Logging 1 log job", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals(app.getString(R.string.logging_notification_nothing_pending),
                n.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
    }

    private String text() {
        return loggingNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
    }

    private void updateNotification() {
        Intent intent = new Intent(app, LoggerService.class);
        intent.putExtra(LoggerService.UPDATE_NOTIFICATION, true);
        controller.get().onStartCommand(intent, 0, 2);
    }

    @Test
    public void uploadThatKeepsFailingIsShownInTheNotificationUntilItWorks() {
        long hike = logjob("hike", true);
        addPosition(hike);
        startService();

        // a first failure is often transient: not worth mentioning yet
        db.setLastSyncError(hike, System.currentTimeMillis() / 1000, "The server refused the login (HTTP 401)");
        updateNotification();
        assertEquals("1 position waiting to upload", text());

        for (int i = 1; i < UploadErrors.NOTIFY_AFTER_FAILURES; i++) {
            db.setLastSyncError(hike, System.currentTimeMillis() / 1000, "The server refused the login (HTTP 401)");
        }
        updateNotification();
        assertEquals("Upload failing for ‘hike’: The server refused the login (HTTP 401)", text());
        // the same notification, not a new one each time
        assertEquals(1, shadowOf(notificationManager).getAllNotifications().size());
        String big = loggingNotification().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString();
        assertTrue(big, big.endsWith("1 position waiting to upload"));

        db.resetLastSyncError(hike);
        updateNotification();
        assertEquals("1 position waiting to upload", text());
    }

    @Test
    public void pausedLoggingComesBeforeAFailingUpload() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        long hike = logjob("hike", true);
        for (int i = 0; i < UploadErrors.NOTIFY_AFTER_FAILURES; i++) {
            db.setLastSyncError(hike, 100 + i, "Could not connect to the server");
        }
        startService();

        Notification n = loggingNotification();
        // nothing is logging: the title does not say otherwise
        assertEquals(app.getString(R.string.notification_channel_logging),
                n.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertTrue(text(), text().startsWith("Logging is stopped"));
        String big = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString();
        assertTrue(big, big.contains("Upload failing for ‘hike’: Could not connect to the server"));
    }

    @Test
    public void severalFailingLogjobsAreCounted() {
        long hike = logjob("hike", true);
        long car = logjob("car", true);
        for (int i = 0; i < UploadErrors.NOTIFY_AFTER_FAILURES; i++) {
            db.setLastSyncError(hike, 100 + i, "a");
            db.setLastSyncError(car, 100 + i, "b");
        }
        startService();

        assertEquals("Uploads failing for 2 log jobs", text());
    }

    @Test
    public void failingWorkerRunsEndUpInTheNotification() {
        long id = logjob("custom", true);
        addPosition(id);
        startService();

        for (int attempt = 0; attempt < UploadErrors.NOTIFY_AFTER_FAILURES; attempt++) {
            WebTrackWorker worker = TestWorkerBuilder.from(app, WebTrackWorker.class, Executors.newSingleThreadExecutor())
                    .setInputData(new Data.Builder().putLong(WebTrackWorker.KEY_LOGJOB_ID, id).build())
                    .setRunAttemptCount(attempt)
                    .build();
            assertEquals(ListenableWorker.Result.retry(), worker.doWork());
            // the worker asks the running service to refresh its notification after each run
            Intent asked = shadowOf(app).getNextStartedService();
            assertTrue(asked.getBooleanExtra(LoggerService.UPDATE_NOTIFICATION, false));
            controller.get().onStartCommand(asked, 0, attempt + 2);
            if (attempt + 1 < UploadErrors.NOTIFY_AFTER_FAILURES) {
                assertEquals("1 position waiting to upload", text());
            }
        }

        assertEquals(UploadErrors.NOTIFY_AFTER_FAILURES, db.getLastSyncError(id).getConsecutiveFailures());
        assertEquals("Upload failing for ‘custom’: Could not connect to the server", text());
    }

    @Test
    public void stopActionTurnsOffEveryLogjobAndStopsTheService() throws Exception {
        long hike = logjob("hike", true);
        long car = logjob("car", true);
        long off = logjob("off", false);
        startService();

        Notification n = loggingNotification();
        assertEquals(1, n.actions.length);
        assertEquals(app.getString(R.string.logging_notification_stop), n.actions[0].title.toString());

        // what the action sends
        PendingIntent action = n.actions[0].actionIntent;
        Intent sent = shadowOf(action).getSavedIntent();
        assertTrue(shadowOf(action).isBroadcastIntent());
        new StopLoggingReceiver().onReceive(app, sent);

        assertFalse(db.getLogjob(hike).isEnabled());
        assertFalse(db.getLogjob(car).isEnabled());
        assertFalse(db.getLogjob(off).isEnabled());

        // the service hears about each log job, as when the user turns them off in the list
        List<Intent> started = new ArrayList<>();
        Intent next;
        while ((next = shadowOf(app).getNextStartedService()) != null) {
            started.add(next);
        }
        assertEquals(2, started.size());
        for (Intent intent : started) {
            controller.get().onStartCommand(intent, 0, 2);
        }
        assertTrue(shadowOf(controller.get()).isStoppedBySelf());
    }

    @Test
    public void stoppingOneOfTwoLogjobsUpdatesTheCount() {
        long hike = logjob("hike", true);
        logjob("car", true);
        startService();

        db.toggleEnabled(db.getLogjob(hike), null, false);
        Intent intent = new Intent(app, LoggerService.class);
        intent.putExtra(net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity.UPDATED_LOGJOBS, true);
        intent.putExtra(net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity.UPDATED_LOGJOB_ID, hike);
        controller.get().onStartCommand(intent, 0, 2);

        assertEquals("Logging 1 log job",
                loggingNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString());
    }

    @Test
    public void startingTheLoggerKeepsTheSmsNotifications() {
        NotificationChannels.createSmsActivity(app);
        Notification sms = new NotificationCompat.Builder(app, NotificationChannels.SMS_ACTIVITY)
                .setSmallIcon(R.drawable.ic_notify_24dp)
                .setContentText("Your position was sent by SMS to Alice")
                .build();
        notificationManager.notify(1526756641, sms);
        logjob("hike", true);

        startService();

        assertNotNull(shadowOf(notificationManager).getNotification(1526756641));
    }

    @Test
    public void smsPositionRequestDoesNotReplaceTheLoggingNotification() {
        assertNotEquals(LoggerService.NOTIFICATION_ID, SmsLocationSendService.FOREGROUND_NOTIFICATION_ID);
    }

    @Test
    public void smsChannelsHaveTheirOwnNames() {
        NotificationChannels.createSmsActivity(app);
        NotificationChannels.createSmsPositionRequest(app);
        NotificationChannels.createLogging(app);

        List<String> names = new ArrayList<>();
        for (NotificationChannel channel : notificationManager.getNotificationChannels()) {
            names.add(channel.getName().toString());
            assertNotNull(channel.getDescription());
        }
        assertEquals(3, names.size());
        assertEquals(3, names.stream().distinct().count());
        assertFalse(names.contains(app.getString(R.string.app_name)));
    }

    @Test
    public void renamingKeepsTheImportanceTheUserChose() {
        // what "Low notification importance" left behind
        notificationManager.createNotificationChannel(new NotificationChannel(NotificationChannels.LOGGING,
                app.getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN));

        NotificationChannels.createLogging(app);

        NotificationChannel channel = notificationManager.getNotificationChannel(NotificationChannels.LOGGING);
        assertEquals(app.getString(R.string.notification_channel_logging), channel.getName().toString());
        assertEquals(NotificationManager.IMPORTANCE_MIN, channel.getImportance());
    }
}
