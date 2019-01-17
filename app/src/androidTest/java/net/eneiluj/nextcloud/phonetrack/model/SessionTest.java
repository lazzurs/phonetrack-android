package net.eneiluj.nextcloud.phonetrack.model;

import junit.framework.TestCase;

/**
 * Tests the Session Model
 */
public class SessionTest extends TestCase {

    public void testMarkDownStrip() {
        DBSession session = new DBSession(0, "toktok", "sessionName", "https://next.url", "pubtok", false, true);
        assertTrue("sessionName".equals(session.getName()));
        session.setName("yop");
        assertTrue("yop".equals(session.getName()));
    }
}
