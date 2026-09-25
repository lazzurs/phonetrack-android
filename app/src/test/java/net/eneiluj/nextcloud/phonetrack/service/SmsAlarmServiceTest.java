package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Looper;
import android.telephony.SmsManager;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowSmsManager;

import java.time.Duration;

@RunWith(AndroidJUnit4.class)
public class SmsAlarmServiceTest {

    private static final String FROM = "+31641600986";
    private static final int ORIGINAL_VOLUME = 2;

    private Application app;
    private AudioManager audio;
    private ServiceController<SmsAlarmService> controller;
    private SmsAlarmService service;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS);
        ShadowSmsManager.reset();
        audio = app.getSystemService(AudioManager.class);
        audio.setStreamVolume(AudioManager.STREAM_ALARM, ORIGINAL_VOLUME, 0);
        controller = Robolectric.buildService(SmsAlarmService.class).create();
        service = controller.get();
    }

    private void toggle(int seconds) {
        controller.withIntent(new Intent(app, SmsAlarmService.class)
                .setAction(SmsAlarmService.ACTION_TOGGLE)
                .putExtra(SmsAlarmService.EXTRA_FROM, FROM)
                .putExtra(SmsAlarmService.EXTRA_DURATION_SECONDS, seconds)).startCommand(0, 1);
    }

    private int alarmVolume() {
        return audio.getStreamVolume(AudioManager.STREAM_ALARM);
    }

    private int maxVolume() {
        return audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
    }

    private String lastSms() {
        return shadowOf(app.getSystemService(SmsManager.class)).getLastSentTextMessageParams().getText();
    }

    private boolean restorePending() {
        return PreferenceManager.getDefaultSharedPreferences(app).contains(SmsAlarmService.PREF_VOLUME_TO_RESTORE);
    }

    private void assertStoppedAndRestored() {
        assertFalse(service.isRinging());
        assertEquals(ORIGINAL_VOLUME, alarmVolume());
        assertFalse(restorePending());
        assertTrue(shadowOf(service).isStoppedBySelf());
    }

    @Test
    public void ringsAtFullVolumeFromAMediaForegroundServiceWithAStopAction() {
        toggle(60);

        assertTrue(service.isRinging());
        assertEquals(maxVolume(), alarmVolume());
        assertTrue(restorePending());
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, service.getForegroundServiceType());
        Notification notification = shadowOf(service).getLastForegroundNotification();
        assertNotNull(notification);
        assertEquals(1, notification.actions.length);
        assertTrue(lastSms().contains("60"));
    }

    @Test
    public void stopsAndRestoresTheVolumeWhenTheTimeIsUp() {
        toggle(30);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31));

        assertStoppedAndRestored();
    }

    @Test
    public void aSecondAlarmSmsStopsItAndReplies() {
        toggle(60);
        toggle(60);

        assertStoppedAndRestored();
        assertEquals(app.getString(net.eneiluj.nextcloud.phonetrack.R.string.sms_alarm_stopped), lastSms());
    }

    @Test
    public void theNotificationStopActionStopsIt() {
        toggle(60);
        controller.withIntent(new Intent(app, SmsAlarmService.class)
                .setAction(SmsAlarmService.ACTION_STOP)).startCommand(0, 2);

        assertStoppedAndRestored();
    }

    @Test
    public void destroyingTheServiceRestoresTheVolume() {
        toggle(60);
        controller.destroy();

        assertFalse(service.isRinging());
        assertEquals(ORIGINAL_VOLUME, alarmVolume());
        assertFalse(restorePending());
    }

    @Test
    public void theNextAppStartRestoresTheVolumeIfTheProcessDiedMidAlarm() {
        toggle(60);
        // the process is killed: no stop, no onDestroy
        assertEquals(maxVolume(), alarmVolume());

        SmsAlarmService.restoreVolumeAfterInterruptedAlarm(app);

        assertEquals(ORIGINAL_VOLUME, alarmVolume());
        assertFalse(restorePending());
    }
}
