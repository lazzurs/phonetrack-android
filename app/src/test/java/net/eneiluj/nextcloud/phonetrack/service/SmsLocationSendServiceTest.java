package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import android.telephony.SmsManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSmsManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SmsLocationSendServiceTest {

    private static final String ALICE = "+31641600986";
    private static final String BOB = "+353871234567";

    private Application app;
    private ServiceController<SmsLocationSendService> controller;
    private SmsLocationSendService service;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS);
        shadowOf(app.getSystemService(LocationManager.class)).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        ShadowSmsManager.reset();
        controller = Robolectric.buildService(SmsLocationSendService.class).create();
        service = controller.get();
    }

    @After
    public void tearDown() {
        controller.destroy();
        SmsLocationSendService.isRunning.clear();
    }

    private void grantLocation() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    private void request(String from) {
        controller.withIntent(new Intent(app, SmsLocationSendService.class)
                .putExtra(SmsLocationSendService.EXTRA_FROM, from)).startCommand(0, 1);
        worker().idle();
    }

    private ShadowLooper worker() {
        return shadowOf(service.getWorkLooper());
    }

    private static Location fix(float accuracy) {
        Location l = new Location(LocationManager.GPS_PROVIDER);
        l.setLatitude(53.3498);
        l.setLongitude(-6.2603);
        l.setAccuracy(accuracy);
        l.setTime(System.currentTimeMillis());
        l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        return l;
    }

    private void simulate(Location location) {
        shadowOf(app.getSystemService(LocationManager.class)).simulateLocation(location);
        worker().idle();
    }

    private SmsManager smsManager() {
        return app.getSystemService(SmsManager.class);
    }

    private List<String> notificationTexts() {
        List<String> texts = new ArrayList<>();
        for (Notification n : shadowOf(app.getSystemService(NotificationManager.class)).getAllNotifications()) {
            CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            texts.add(text == null ? "" : text.toString());
        }
        return texts;
    }

    @Test
    public void runsAsALocationForegroundServiceAndRepliesWithThePosition() {
        grantLocation();
        request(ALICE);

        assertNotNull(shadowOf(service).getLastForegroundNotification());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, service.getForegroundServiceType());
        }
        assertTrue(SmsLocationSendService.isRunning.containsKey(ALICE));

        // a poor fix is not good enough yet
        simulate(fix(500));
        assertEquals(null, shadowOf(smsManager()).getLastSentTextMessageParams());

        // updates are requested at most once per second
        worker().idleFor(Duration.ofSeconds(2));
        simulate(fix(8));
        worker().idleFor(Duration.ofSeconds(3));

        ShadowSmsManager.TextSmsParams last = shadowOf(smsManager()).getLastSentTextMessageParams();
        assertEquals(ALICE, last.getDestinationAddress());
        assertTrue(last.getText(), last.getText().contains("openstreetmap.org/?mlat=53.3498000&mlon=-6.2603000"));
        assertTrue(shadowOf(service).isStoppedBySelf());
        assertTrue(shadowOf(service).isForegroundStopped());
        assertFalse(SmsLocationSendService.isRunning.containsKey(ALICE));
    }

    @Test
    public void answersEveryRequesterWithTheirOwnReply() {
        // used to share one "from" field: the second request overwrote the first
        grantLocation();
        request(ALICE);
        request(BOB);

        simulate(fix(8));
        worker().idleFor(Duration.ofSeconds(3));

        List<String> texts = notificationTexts();
        assertTrue(texts.toString(), texts.stream().anyMatch(t -> t.contains(ALICE)));
        assertTrue(texts.toString(), texts.stream().anyMatch(t -> t.contains(BOB)));
        assertTrue(SmsLocationSendService.isRunning.isEmpty());
    }

    @Test
    public void repliesWithAnErrorWhenNoFixArrivesInTime() {
        grantLocation();
        request(ALICE);

        worker().idleFor(Duration.ofSeconds(121));

        ShadowSmsManager.TextSmsParams last = shadowOf(smsManager()).getLastSentTextMessageParams();
        assertEquals(ALICE, last.getDestinationAddress());
        assertTrue(last.getText(), last.getText().contains("120"));
        assertTrue(shadowOf(service).isStoppedBySelf());
    }

    @Test
    public void repliesWithAnErrorWithoutLocationPermission() {
        request(ALICE);

        ShadowSmsManager.TextSmsParams last = shadowOf(smsManager()).getLastSentTextMessageParams();
        assertEquals(ALICE, last.getDestinationAddress());
        assertTrue(shadowOf(service).isStoppedBySelf());
        assertFalse(SmsLocationSendService.isRunning.containsKey(ALICE));
    }
}
