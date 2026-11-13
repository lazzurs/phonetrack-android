package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SmsCommandGuardTest {

    private static final String SENDER = "+353871234567";

    private Context context;
    private long now = 1_800_000_000_000L;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SmsCommandGuard.setClockForTesting(() -> now);
    }

    @After
    public void tearDown() {
        SmsCommandGuard.setClockForTesting(null);
    }

    @Test
    public void comparesPins() {
        assertTrue(SmsCommandGuard.pinMatches("4821", "4821"));
        assertFalse(SmsCommandGuard.pinMatches("4821", "4822"));
        assertFalse(SmsCommandGuard.pinMatches("4821", "48210"));
        assertFalse(SmsCommandGuard.pinMatches("4821", ""));
    }

    @Test
    public void lockoutsDoubleUpToADay() {
        assertEquals(SmsCommandGuard.FIRST_LOCKOUT_MS, SmsCommandGuard.lockoutDuration(1));
        assertEquals(2 * SmsCommandGuard.FIRST_LOCKOUT_MS, SmsCommandGuard.lockoutDuration(2));
        assertEquals(4 * SmsCommandGuard.FIRST_LOCKOUT_MS, SmsCommandGuard.lockoutDuration(3));
        assertEquals(SmsCommandGuard.MAX_LOCKOUT_MS, SmsCommandGuard.lockoutDuration(1000));
    }

    @Test
    public void theSameNumberWrittenDifferentlySharesTheCounter() {
        SmsCommandGuard.recordWrongPin(context, "+353 87 123 4567");
        SmsCommandGuard.recordWrongPin(context, "+353-87-1234567");
        assertTrue(SmsCommandGuard.recordWrongPin(context, SENDER));
        assertTrue(SmsCommandGuard.isLockedOut(context, "+353 87 123 4567"));
    }

    @Test
    public void aCorrectPinClearsTheWrongOnes() {
        SmsCommandGuard.recordWrongPin(context, SENDER);
        SmsCommandGuard.recordWrongPin(context, SENDER);
        SmsCommandGuard.recordCorrectPin(context, SENDER);
        assertFalse(SmsCommandGuard.recordWrongPin(context, SENDER));
        assertFalse(SmsCommandGuard.isLockedOut(context, SENDER));
    }

    @Test
    public void changingThePinLiftsLockouts() {
        for (int i = 0; i < SmsCommandGuard.MAX_WRONG_PINS; i++) {
            SmsCommandGuard.recordWrongPin(context, SENDER);
        }
        assertTrue(SmsCommandGuard.isLockedOut(context, SENDER));
        SmsCommandGuard.resetPinFailures(context);
        assertFalse(SmsCommandGuard.isLockedOut(context, SENDER));
    }

    @Test
    public void aClockSetBackDoesNotBlockPositionRequestsForGood() {
        assertTrue(SmsCommandGuard.tryAcquire(context, SENDER, true));
        now -= 24 * 60 * 60 * 1000L;
        assertTrue(SmsCommandGuard.tryAcquire(context, SENDER, true));
    }

    @Test
    public void ignoredNoticesAreOnePerSenderPerDay() {
        assertTrue(SmsCommandGuard.tryAcquireIgnoredNotice(context, SENDER));
        assertFalse(SmsCommandGuard.tryAcquireIgnoredNotice(context, SENDER));
        // the same number written differently is the same sender
        assertFalse(SmsCommandGuard.tryAcquireIgnoredNotice(context, "+353 87 123 4567"));
        now += SmsCommandGuard.IGNORED_NOTICE_INTERVAL_MS - 1;
        assertFalse(SmsCommandGuard.tryAcquireIgnoredNotice(context, SENDER));
        now += 1;
        assertTrue(SmsCommandGuard.tryAcquireIgnoredNotice(context, SENDER));
    }

    @Test
    public void ignoredNoticesHaveATotalCapSoManyNumbersCannotFloodTheShade() {
        for (int i = 0; i < SmsCommandGuard.MAX_IGNORED_NOTICES; i++) {
            assertTrue(SmsCommandGuard.tryAcquireIgnoredNotice(context, "+35387000000" + i));
        }
        assertFalse(SmsCommandGuard.tryAcquireIgnoredNotice(context, "+353879999999"));
        now += SmsCommandGuard.IGNORED_NOTICE_INTERVAL_MS;
        assertTrue(SmsCommandGuard.tryAcquireIgnoredNotice(context, "+353879999999"));
    }

    @Test
    public void ignoredNoticesDoNotTouchThePinLockoutOrCommandLimits() {
        SmsCommandGuard.tryAcquireIgnoredNotice(context, SENDER);
        assertFalse(SmsCommandGuard.isLockedOut(context, SENDER));
        assertTrue(SmsCommandGuard.tryAcquire(context, SENDER, true));
    }
}
