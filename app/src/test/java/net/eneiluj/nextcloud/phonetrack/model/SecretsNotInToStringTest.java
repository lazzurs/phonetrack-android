package net.eneiluj.nextcloud.phonetrack.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** toString() of these models is logged, so it must not contain tokens or URLs carrying them. */
public class SecretsNotInToStringTest {

    @Test
    public void sessionToStringHasNoToken() {
        DBSession session = new DBSession(3, "SECRET-TOKEN", "My session",
                "https://nc.example/index.php/apps/phonetrack/SECRET-TOKEN", "SECRET-PUBLIC",
                false, true);
        String s = session.toString();
        assertFalse(s, s.contains("SECRET"));
        assertTrue(s, s.contains("My session"));
    }

    @Test
    public void logjobToStringHasNoTokenOrUrl() {
        DBLogjob logjob = new DBLogjob(4, "Job", "https://nc.example/logPost/SECRET-TOKEN/dev",
                "SECRET-TOKEN", "dev", 60, 0, 0, false, false, false, 30, false, true, 0,
                "alice", "SECRET-PASSWORD", false);
        String s = logjob.toString();
        assertFalse(s, s.contains("SECRET"));
        assertFalse(s, s.contains("nc.example"));
        assertTrue(s, s.contains("Job"));
    }
}
