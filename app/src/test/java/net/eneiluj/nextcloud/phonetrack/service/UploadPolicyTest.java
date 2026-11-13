package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.eneiluj.nextcloud.phonetrack.service.UploadPolicy.Action;
import net.eneiluj.nextcloud.phonetrack.service.UploadPolicy.Decision;

import org.junit.Test;

public class UploadPolicyTest {

    private static final long NOW = 1_800_000_000L;
    private static final long HOUR = 3600;

    @Test
    public void nothingPendingIsNothingToDoEvenWhenForced() {
        assertEquals(Action.NOTHING, UploadPolicy.decide(0, 0, NOW, 0, 0, false).action);
        assertEquals(Action.NOTHING, UploadPolicy.decide(0, 0, NOW, 10, 2 * HOUR, true).action);
    }

    @Test
    public void bunchOfZeroOrOneSendsEveryPoint() {
        assertEquals(Action.UPLOAD_NOW, UploadPolicy.decide(1, NOW, NOW, 0, 0, false).action);
        assertEquals(Action.UPLOAD_NOW, UploadPolicy.decide(1, NOW, NOW, 1, 0, false).action);
        // a time limit changes nothing then
        assertEquals(Action.UPLOAD_NOW, UploadPolicy.decide(1, NOW, NOW, 0, 2 * HOUR, false).action);
    }

    @Test
    public void belowTheBunchWithoutTimeLimitWaitsForMorePoints() {
        Decision decision = UploadPolicy.decide(9, NOW - 100 * HOUR, NOW, 10, 0, false);
        assertEquals(Action.WAIT, decision.action);
        assertFalse(decision.hasDeadline());
    }

    @Test
    public void reachingTheBunchUploads() {
        assertEquals(Action.UPLOAD_NOW, UploadPolicy.decide(10, NOW, NOW, 10, 0, false).action);
        assertEquals(Action.UPLOAD_NOW, UploadPolicy.decide(11, NOW, NOW, 10, 2 * HOUR, false).action);
    }

    @Test
    public void belowTheBunchWaitsUntilTheOldestPointHasWaitedTheTimeLimit() {
        Decision decision = UploadPolicy.decide(3, NOW - HOUR, NOW, 10, 2 * HOUR, false);
        assertEquals(Action.WAIT, decision.action);
        assertTrue(decision.hasDeadline());
        assertEquals(NOW + HOUR, decision.deadline);
    }

    @Test
    public void pastTheTimeLimitUploadsASinglePoint() {
        assertEquals(Action.UPLOAD_NOW, UploadPolicy.decide(1, NOW - 2 * HOUR, NOW, 100, 2 * HOUR, false).action);
        assertEquals(Action.UPLOAD_NOW, UploadPolicy.decide(1, NOW - 3 * HOUR, NOW, 100, 2 * HOUR, false).action);
        assertEquals(Action.WAIT, UploadPolicy.decide(1, NOW - 2 * HOUR + 1, NOW, 100, 2 * HOUR, false).action);
    }

    @Test
    public void forceUploadsBelowTheBunch() {
        assertEquals(Action.UPLOAD_NOW, UploadPolicy.decide(1, NOW, NOW, 100, 0, true).action);
        assertEquals(Action.UPLOAD_NOW, UploadPolicy.decide(1, NOW, NOW, 100, 2 * HOUR, true).action);
    }

    @Test
    public void aPointDatedInTheFutureDoesNotPushTheDeadlineBeyondTheTimeLimit() {
        Decision decision = UploadPolicy.decide(1, NOW + 50 * HOUR, NOW, 10, 2 * HOUR, false);
        assertEquals(Action.WAIT, decision.action);
        assertEquals(NOW + 2 * HOUR, decision.deadline);
    }
}
