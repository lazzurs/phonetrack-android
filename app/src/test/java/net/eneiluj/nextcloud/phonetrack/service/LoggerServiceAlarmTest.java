package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowAlarmManager;

@RunWith(AndroidJUnit4.class)
public class LoggerServiceAlarmTest {

    private AlarmManager alarmManager;
    private PendingIntent operation;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        alarmManager = context.getSystemService(AlarmManager.class);
        operation = PendingIntent.getService(context, 1,
                new Intent(context, LoggerService.class), PendingIntent.FLAG_IMMUTABLE);
    }

    @Test
    public void exactAlarmWhenAllowed() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true);
        LoggerService.setAlarmAllowWhileIdle(alarmManager, SystemClock.elapsedRealtime() + 60_000, operation);

        ShadowAlarmManager.ScheduledAlarm alarm = shadowOf(alarmManager).peekNextScheduledAlarm();
        assertNotNull(alarm);
        assertTrue(alarm.isAllowWhileIdle());
        assertEquals(0, alarm.getWindowLengthMs()); // exact
    }

    @Test
    public void fallsBackToInexactAlarmWhenExactAlarmsAreDenied() {
        // Android 14+ denies SCHEDULE_EXACT_ALARM by default; the exact API would then throw
        ShadowAlarmManager.setCanScheduleExactAlarms(false);
        LoggerService.setAlarmAllowWhileIdle(alarmManager, SystemClock.elapsedRealtime() + 60_000, operation);

        ShadowAlarmManager.ScheduledAlarm alarm = shadowOf(alarmManager).peekNextScheduledAlarm();
        assertNotNull(alarm);
        assertTrue(alarm.isAllowWhileIdle());
        assertNotEquals(0, alarm.getWindowLengthMs()); // inexact
    }
}
