package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.CredentialStore;
import net.eneiluj.nextcloud.phonetrack.util.SmsCommandGuard;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowSmsManager;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SmsListenerTest {

    // GSM SMS-DELIVER from +31641600986, text "phonetrack" (GSM 7-bit packed)
    private static final String PDU_HEX =
            "07911326040000F0040B911346610089F60000208062917314080A70F4DB5DA6CBC3E335";
    private static final String FROM = "+31641600986";
    private static final String PIN = "4821";

    private Application app;
    private long now = 1_800_000_000_000L;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        DbTestSupport.resetDatabase();
        ShadowSmsManager.reset();
        SmsCommandGuard.setClockForTesting(() -> now);
        shadowOf(app.getSystemService(TelephonyManager.class)).setSimCountryIso("nl");
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS);
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(app.getString(R.string.pref_key_sms), true)
                // the keyword alone is a position request
                .putString(app.getString(R.string.pref_key_sms_keyword), "phonetrack")
                .apply();
    }

    @After
    public void tearDown() {
        SmsCommandGuard.setClockForTesting(null);
        DbTestSupport.resetDatabase();
    }

    private void setAllowedSenders(String numbers) {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putString(app.getString(R.string.pref_key_sms_allowed_senders), numbers)
                .apply();
    }

    private void receiveSms() {
        deliver(hexToBytes(PDU_HEX));
    }

    private void receiveSms(String text) {
        deliver(deliverPdu(FROM, text));
    }

    private void deliver(byte[] pdu) {
        Intent intent = new Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        intent.putExtra("pdus", new Object[]{pdu});
        intent.putExtra("format", "3gpp");
        new SmsListener().onReceive(app, intent);
    }

    @Test
    public void ignoresCommandsWhenNoSenderIsAllowed() {
        setAllowedSenders("");
        receiveSms();
        assertNull(startedLocationReply());
    }

    @Test
    public void ignoresCommandsFromOtherNumbers() {
        setAllowedSenders("+31641600987");
        receiveSms();
        assertNull(startedLocationReply());
    }

    @Test
    public void ignoredCommandFromAStrangerLeavesOneNotificationPerDay() {
        setAllowedSenders("+31641600987");
        receiveSms("phonetrack alarm 99 secretword");
        receiveSms("phonetrack");

        List<Notification> notifications = notifications();
        assertEquals(1, notifications.size());
        String text = notifications.get(0).extras.getCharSequence(Notification.EXTRA_TEXT).toString();
        assertEquals(app.getString(R.string.sms_ignored_notification, FROM), text);
        // never the message itself
        assertFalse(text.contains("secretword"));
        assertEquals(app.getString(R.string.sms_ignored_notification_title),
                notifications.get(0).extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertNull(startedLocationReply());

        // a day later the same sender may be reported again
        now += SmsCommandGuard.IGNORED_NOTICE_INTERVAL_MS + 1;
        receiveSms("phonetrack");
        assertEquals(2, notifications().size());
    }

    @Test
    public void ignoredCommandWithNoAllowedSenderSaysSo() {
        setAllowedSenders("");
        receiveSms("phonetrack");

        List<Notification> notifications = notifications();
        assertEquals(1, notifications.size());
        assertEquals(app.getString(R.string.sms_ignored_notification_no_senders, FROM),
                notifications.get(0).extras.getCharSequence(Notification.EXTRA_TEXT).toString());
    }

    @Test
    public void ignoredCommandsStaySilentWithoutNotificationPermission() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        setAllowedSenders("");
        receiveSms("phonetrack");
        assertTrue(notifications().isEmpty());
        // and the quota was not used up while the notification could not be shown
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        receiveSms("phonetrack");
        assertEquals(1, notifications().size());
    }

    @Test
    public void allowedSendersNeverGetAnIgnoredNotification() {
        setAllowedSenders(FROM);
        receiveSms("phonetrack");
        assertTrue(notifications().isEmpty());
    }

    @Test
    public void answersAnAllowedSenderWrittenInNationalFormat() {
        setAllowedSenders("06 41600986");
        receiveSms();
        Intent started = startedLocationReply();
        assertNotNull(started);
        assertEquals("+31641600986", started.getStringExtra("from"));
    }

    @Test
    public void theTestPduBuilderMatchesARealPdu() {
        setAllowedSenders(FROM);
        receiveSms("phonetrack");
        assertNotNull(startedLocationReply());
    }

    // PIN

    @Test
    public void withAPinTheBareKeywordIsIgnored() {
        setAllowedSenders(FROM);
        CredentialStore.setSmsPin(app, PIN);
        long logjob = addLogjob("walk", true);

        receiveSms("phonetrack");
        receiveSms("phonetrack stoplogjobs");

        assertNull(startedService(null));
        assertTrue(logjob(logjob).isEnabled());
        assertNull(lastSms());
    }

    @Test
    public void withAPinAWrongPinIsIgnored() {
        setAllowedSenders(FROM);
        CredentialStore.setSmsPin(app, PIN);
        long logjob = addLogjob("walk", true);

        receiveSms("phonetrack 1111 stoplogjobs");
        receiveSms("phonetrack 1111 alarm");

        assertNull(startedService(null));
        assertTrue(logjob(logjob).isEnabled());
        assertNull(lastSms());
        assertTrue(notifications().isEmpty());
    }

    @Test
    public void withTheRightPinCommandsRunAndThePinIsNeverEchoed() {
        setAllowedSenders(FROM);
        CredentialStore.setSmsPin(app, PIN);
        long logjob = addLogjob("walk", true);

        receiveSms("phonetrack " + PIN);
        assertNotNull(startedLocationReply());

        receiveSms("phonetrack " + PIN + " stoplogjobs");
        assertFalse(logjob(logjob).isEnabled());
        String reply = lastSms();
        assertEquals(app.getString(R.string.sms_logjobs_stopped, 1), reply);

        List<Notification> notifications = notifications();
        assertEquals(1, notifications.size());
        String text = notifications.get(0).extras.getCharSequence(Notification.EXTRA_TEXT).toString();
        assertTrue(text.contains("stoplogjobs"));
        assertFalse(text.contains(PIN));
        // a PIN is set: no advice to set one
        assertFalse(text.contains(app.getString(R.string.sms_command_notification_no_pin)));
    }

    @Test
    public void withoutAPinCommandsStillWorkAndTheNotificationRecommendsOne() {
        setAllowedSenders(FROM);
        long logjob = addLogjob("walk", false);

        receiveSms("phonetrack startlogjobs");

        assertTrue(logjob(logjob).isEnabled());
        List<Notification> notifications = notifications();
        assertEquals(1, notifications.size());
        assertTrue(notifications.get(0).extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                .contains(app.getString(R.string.sms_command_notification_no_pin)));
    }

    @Test
    public void commandsRunQuietlyWhenNotificationsAreDenied() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        setAllowedSenders(FROM);
        long logjob = addLogjob("walk", true);

        receiveSms("phonetrack stoplogjobs");

        assertFalse(logjob(logjob).isEnabled());
        assertTrue(notifications().isEmpty());
    }

    @Test
    public void wrongPinsLockTheSenderOutUntilTheIntervalHasPassed() {
        setAllowedSenders(FROM);
        CredentialStore.setSmsPin(app, PIN);
        for (int i = 0; i < SmsCommandGuard.MAX_WRONG_PINS; i++) {
            receiveSms("phonetrack 000" + i);
        }

        // locked out: even the right PIN is ignored
        receiveSms("phonetrack " + PIN);
        assertNull(startedLocationReply());

        now += SmsCommandGuard.FIRST_LOCKOUT_MS - 1;
        receiveSms("phonetrack " + PIN);
        assertNull(startedLocationReply());

        now += 1;
        receiveSms("phonetrack " + PIN);
        assertNotNull(startedLocationReply());
    }

    @Test
    public void eachLockoutLastsLongerThanThePreviousOne() {
        setAllowedSenders(FROM);
        CredentialStore.setSmsPin(app, PIN);
        for (int i = 0; i < SmsCommandGuard.MAX_WRONG_PINS; i++) {
            receiveSms("phonetrack 0000");
        }
        now += SmsCommandGuard.FIRST_LOCKOUT_MS;
        for (int i = 0; i < SmsCommandGuard.MAX_WRONG_PINS; i++) {
            receiveSms("phonetrack 0000");
        }

        // the second lockout is twice as long
        now += SmsCommandGuard.FIRST_LOCKOUT_MS;
        receiveSms("phonetrack " + PIN);
        assertNull(startedLocationReply());
        now += SmsCommandGuard.FIRST_LOCKOUT_MS;
        receiveSms("phonetrack " + PIN);
        assertNotNull(startedLocationReply());
    }

    // limits

    @Test
    public void aBurstOfPositionRequestsGetsOneReplyPerMinute() {
        setAllowedSenders(FROM);
        for (int i = 0; i < 5; i++) {
            receiveSms("phonetrack");
        }
        assertEquals(1, countStarted(SmsLocationSendService.class));

        now += SmsCommandGuard.POSITION_REQUEST_INTERVAL_MS;
        receiveSms("phonetrack");
        assertEquals(1, countStarted(SmsLocationSendService.class));
    }

    @Test
    public void aBurstOfCommandsIsCappedPerHour() {
        setAllowedSenders(FROM);
        addLogjob("walk", true);
        int replies = 0;
        for (int i = 0; i < SmsCommandGuard.MAX_COMMANDS_PER_HOUR + 5; i++) {
            receiveSms("phonetrack stoplogjobs");
            if (lastSms() != null) {
                replies++;
                shadowOf(app.getSystemService(SmsManager.class)).clearLastSentTextMessageParams();
            }
        }
        assertEquals(SmsCommandGuard.MAX_COMMANDS_PER_HOUR, replies);

        now += 60 * 60 * 1000L;
        receiveSms("phonetrack stoplogjobs");
        assertNotNull(lastSms());
    }

    @Test
    public void theAlarmDurationIsCapped() {
        setAllowedSenders(FROM);
        receiveSms("phonetrack alarm 2147483647");

        Intent alarm = startedService(SmsAlarmService.class);
        assertNotNull(alarm);
        assertEquals(SmsAlarmService.MAX_DURATION_SECONDS,
                alarm.getIntExtra(SmsAlarmService.EXTRA_DURATION_SECONDS, 0));
        assertEquals(1, notifications().size());
    }

    @Test
    public void aDurationThatDoesNotFitAnIntIsCappedToo() {
        setAllowedSenders(FROM);
        receiveSms("phonetrack alarm 99999999999");

        Intent alarm = startedService(SmsAlarmService.class);
        assertNotNull(alarm);
        assertEquals(SmsAlarmService.MAX_DURATION_SECONDS,
                alarm.getIntExtra(SmsAlarmService.EXTRA_DURATION_SECONDS, 0));
    }

    @Test
    public void aNegativeAlarmDurationIsRejected() {
        setAllowedSenders(FROM);
        receiveSms("phonetrack alarm -5");
        receiveSms("phonetrack alarm 0");

        assertNull(startedService(SmsAlarmService.class));
        assertTrue(notifications().isEmpty());
    }

    @Test
    public void repeatedCreateLogjobKeepsOneSmsLogjob() {
        setAllowedSenders(FROM);
        PhoneTrackSQLiteOpenHelper.getInstance(app).addSessionAndSync(
                new DBSession(0, "tok", "tok", "https://example.org/nc", null, false, false));

        receiveSms("phonetrack createlogjob 30");
        receiveSms("phonetrack createlogjob 1");

        List<DBLogjob> smsLogjobs = new ArrayList<>();
        for (DBLogjob lj : PhoneTrackSQLiteOpenHelper.getInstance(app).getLogjobs()) {
            if (SmsListener.SMS_LOGJOB_TITLE.equals(lj.getTitle())) {
                smsLogjobs.add(lj);
            }
        }
        assertEquals(1, smsLogjobs.size());
        // the second command updated the frequency of the same logjob
        assertEquals(1, smsLogjobs.get(0).getMinTime());
        assertTrue(smsLogjobs.get(0).isEnabled());
        assertEquals(app.getString(R.string.sms_logjob_updated, 1, "tok"), lastSms());
    }

    // helpers

    private long addLogjob(String title, boolean enabled) {
        return PhoneTrackSQLiteOpenHelper.getInstance(app).addLogjob(new DBLogjob(0, title,
                "https://example.org/log", null, null, 60, 0, 50, false, false, false, 60,
                true, enabled, 0, null, null, false));
    }

    private DBLogjob logjob(long id) {
        return PhoneTrackSQLiteOpenHelper.getInstance(app).getLogjob(id);
    }

    private String lastSms() {
        ShadowSmsManager.TextSmsParams params =
                shadowOf(app.getSystemService(SmsManager.class)).getLastSentTextMessageParams();
        return params == null ? null : params.getText();
    }

    private List<Notification> notifications() {
        return shadowOf(app.getSystemService(NotificationManager.class)).getAllNotifications();
    }

    /** Other services may have been started too, look for the SMS position reply only. */
    private Intent startedLocationReply() {
        return startedService(SmsLocationSendService.class);
    }

    /** @param service the service to look for, or null for any */
    private Intent startedService(Class<?> service) {
        Intent intent;
        while ((intent = shadowOf(app).getNextStartedService()) != null) {
            if (service == null || service.getName().equals(intent.getComponent().getClassName())) {
                return intent;
            }
        }
        return null;
    }

    private int countStarted(Class<?> service) {
        int count = 0;
        while (startedService(service) != null) {
            count++;
        }
        return count;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    /**
     * A GSM SMS-DELIVER PDU with a 7-bit text. Only for letters, digits, space and "-",
     * whose GSM codes are the ASCII ones.
     */
    private static byte[] deliverPdu(String from, String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x00); // no SMSC address
        out.write(0x04); // SMS-DELIVER, no more messages to send
        boolean international = from.startsWith("+");
        String digits = international ? from.substring(1) : from;
        out.write(digits.length());
        out.write(international ? 0x91 : 0x81);
        for (int i = 0; i < digits.length(); i += 2) {
            int low = digits.charAt(i) - '0';
            int high = i + 1 < digits.length() ? digits.charAt(i + 1) - '0' : 0xF;
            out.write((high << 4) | low);
        }
        out.write(0x00); // protocol identifier
        out.write(0x00); // GSM 7-bit default alphabet
        byte[] timestamp = hexToBytes("20806291731408");
        out.write(timestamp, 0, timestamp.length);
        out.write(text.length()); // length in septets
        int acc = 0;
        int bits = 0;
        for (int i = 0; i < text.length(); i++) {
            acc |= (text.charAt(i) & 0x7F) << bits;
            bits += 7;
            while (bits >= 8) {
                out.write(acc & 0xFF);
                acc >>>= 8;
                bits -= 8;
            }
        }
        if (bits > 0) {
            out.write(acc & 0xFF);
        }
        return out.toByteArray();
    }
}
