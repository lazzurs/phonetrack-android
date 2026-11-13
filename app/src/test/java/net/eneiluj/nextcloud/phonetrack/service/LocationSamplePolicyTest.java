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
}
