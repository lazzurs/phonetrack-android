package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SmsSenderAllowlistTest {

    @Test
    public void parsesCommaSemicolonAndNewlineSeparatedNumbers() {
        assertEquals(Arrays.asList("+353 87 123 4567", "0861234567", "+33612345678"),
                SmsSenderAllowlist.parse(" +353 87 123 4567 ,0861234567;\n+33612345678, ,"));
        assertTrue(SmsSenderAllowlist.parse(null).isEmpty());
        assertTrue(SmsSenderAllowlist.parse("").isEmpty());
    }

    @Test
    public void validatesEntries() {
        assertTrue(SmsSenderAllowlist.isValidEntry("+353 (0)87 123-4567"));
        assertFalse(SmsSenderAllowlist.isValidEntry("phonetrack"));
        assertFalse(SmsSenderAllowlist.isValidEntry("12"));
    }

    @Test
    public void emptyListAllowsNobody() {
        assertFalse(SmsSenderAllowlist.isAllowed("+353871234567", Collections.emptyList(), "ie"));
    }

    @Test
    public void matchesTheSameNumberInDifferentFormats() {
        List<String> allowed = Collections.singletonList("087 123 4567");
        assertTrue(SmsSenderAllowlist.isAllowed("+353871234567", allowed, "ie"));
        assertTrue(SmsSenderAllowlist.isAllowed("0871234567", allowed, "ie"));
    }

    @Test
    public void rejectsOtherNumbersAndMissingSender() {
        List<String> allowed = Collections.singletonList("+353871234567");
        assertFalse(SmsSenderAllowlist.isAllowed("+353871234568", allowed, "ie"));
        assertFalse(SmsSenderAllowlist.isAllowed("+33871234567", allowed, "ie"));
        assertFalse(SmsSenderAllowlist.isAllowed(null, allowed, "ie"));
        assertFalse(SmsSenderAllowlist.isAllowed("", allowed, "ie"));
    }
}
