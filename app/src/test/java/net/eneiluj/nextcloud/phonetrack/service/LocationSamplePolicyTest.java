package net.eneiluj.nextcloud.phonetrack.service;

import static net.eneiluj.nextcloud.phonetrack.service.LocationSamplePolicy.Decision.ACCEPT;
import static net.eneiluj.nextcloud.phonetrack.service.LocationSamplePolicy.Decision.KEEP_SEARCHING;
import static net.eneiluj.nextcloud.phonetrack.service.LocationSamplePolicy.Decision.STATIONARY;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Plain JUnit: the policy has no Android dependencies. */
public class LocationSamplePolicyTest {

    @Test
    public void accurateFixFarEnoughFromTheLastPointIsAccepted() {
        assertEquals(ACCEPT, LocationSamplePolicy.decide(10, 50, true, 60, 50));
        assertEquals(ACCEPT, LocationSamplePolicy.decide(50, 50, true, 50, 50));
    }

    @Test
    public void accurateFixWithinTheMinimumDistanceEndsTheSearchWithoutLogging() {
        assertEquals(STATIONARY, LocationSamplePolicy.decide(10, 50, true, 2, 50));
        assertEquals(STATIONARY, LocationSamplePolicy.decide(10, 50, true, 4.9, 5));
    }

    @Test
    public void inaccurateFixKeepsSearchingWhateverTheDistance() {
        assertEquals(KEEP_SEARCHING, LocationSamplePolicy.decide(51, 50, true, 2, 50));
        assertEquals(KEEP_SEARCHING, LocationSamplePolicy.decide(500, 50, true, 1000, 50));
        assertEquals(KEEP_SEARCHING, LocationSamplePolicy.decide(500, 50, false, 0, 0));
    }

    @Test
    public void minimumDistanceZeroAcceptsEveryAccurateFix() {
        assertEquals(ACCEPT, LocationSamplePolicy.decide(10, 50, true, 0, 0));
    }

    @Test
    public void firstFixIsAcceptedWhenNothingWasLoggedYet() {
        assertEquals(ACCEPT, LocationSamplePolicy.decide(10, 50, false, 0, 50));
    }

    @Test
    public void fixWithoutAccuracyPassesTheAccuracyCheckAsBefore() {
        // Location.getAccuracy() is 0 when unknown
        assertEquals(ACCEPT, LocationSamplePolicy.decide(0, 50, false, 0, 5));
    }

    @Test
    public void defaultTimeoutForNewLogjobsIsHalfTheIntervalUpTo30Seconds() {
        // was 59 s on the default 60 s interval
        assertEquals(30, LocationSamplePolicy.defaultTimeoutSeconds(60));
        assertEquals(30, LocationSamplePolicy.defaultTimeoutSeconds(300));
        assertEquals(5, LocationSamplePolicy.defaultTimeoutSeconds(10));
        assertEquals(0, LocationSamplePolicy.defaultTimeoutSeconds(1));
    }

    @Test
    public void smsLogjobsGetAFiniteTimeoutBelowTheInterval() {
        // was 0: no timeout, so GPS stayed on indoors
        assertEquals(9, LocationSamplePolicy.smsTimeoutSeconds(10));
        assertEquals(59, LocationSamplePolicy.smsTimeoutSeconds(60));
        assertEquals(60, LocationSamplePolicy.smsTimeoutSeconds(600));
        assertEquals(0, LocationSamplePolicy.smsTimeoutSeconds(1));
        assertEquals(0, LocationSamplePolicy.smsTimeoutSeconds(0));
    }

    @Test
    public void warnsWhenTheTimeoutIsMoreThanHalfTheInterval() {
        assertEquals(98, LocationSamplePolicy.gpsOnPercentWithoutFixWarning(60, 59));
        assertEquals(52, LocationSamplePolicy.gpsOnPercentWithoutFixWarning(60, 31));
        assertEquals(0, LocationSamplePolicy.gpsOnPercentWithoutFixWarning(60, 30));
        assertEquals(0, LocationSamplePolicy.gpsOnPercentWithoutFixWarning(60, 0));
        // invalid forms are flagged elsewhere; never more than 100 %
        assertEquals(100, LocationSamplePolicy.gpsOnPercentWithoutFixWarning(60, 90));
        assertEquals(0, LocationSamplePolicy.gpsOnPercentWithoutFixWarning(-1, 30));
    }
}
