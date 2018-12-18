package net.eneiluj.nextcloud.phonetrack.model;

import junit.framework.TestCase;

/**
 * Tests the Session Model
 */
public class SessionTest extends TestCase {

    public void testMarkDownStrip() {
        CloudSession session = new CloudSession("sessionName", "toto", "https://next.url");
        assertTrue("sessionName".equals(session.getName()));
        session.setName("yop");
        assertTrue("yop".equals(session.getName()));
    }
}
