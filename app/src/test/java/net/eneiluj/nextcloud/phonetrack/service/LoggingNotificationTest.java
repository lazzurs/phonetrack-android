package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

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
import androidx.work.testing.SynchronousExecutor;
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
