package net.eneiluj.nextcloud.phonetrack.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URL;

public class RedirectPolicyTest {

    private static boolean allowed(String from, String to) throws Exception {
        return RedirectPolicy.isAllowed(new URL(from), new URL(to));
    }

    @Test
    public void httpsToHttpIsRefused() throws Exception {
        assertFalse(allowed("https://a/x", "http://a/x"));
        assertFalse(allowed("https://a:8443/x", "http://a:8443/x"));
    }

    @Test
    public void otherPortIsRefused() throws Exception {
        assertFalse(allowed("https://a/x", "https://a:8443/x"));
        assertFalse(allowed("http://a/x", "http://a:8080/x"));
        // an explicit default port is the same port
        assertTrue(allowed("https://a/x", "https://a:443/y"));
    }

    @Test
    public void sameHostSamePortIsAllowed() throws Exception {
        assertTrue(allowed("https://a/x", "https://a/y"));
        assertTrue(allowed("https://A.example/x", "https://a.example/y?z=1"));
        assertTrue(allowed("http://a/x", "http://a/y"));
    }

    @Test
    public void otherHostIsRefused() throws Exception {
        assertFalse(allowed("https://a/x", "https://b/x"));
        assertFalse(allowed("http://a/x", "https://b/x"));
        assertFalse(allowed("https://a/x", "https://a.evil.example/x"));
    }

    @Test
    public void upgradeToHttpsIsAllowed() throws Exception {
        assertTrue(allowed("http://a/x", "https://a/x"));
    }

    @Test
    public void nonHttpSchemesAreRefused() throws Exception {
        assertFalse(allowed("https://a/x", "ftp://a/x"));
        assertFalse(allowed("http://a/x", "file://a/x"));
    }

    @Test
    public void resolveHandlesRelativeAndBadLocations() throws Exception {
        URL current = new URL("https://a/nc/index.php/x");
        assertEquals("https://a/other", RedirectPolicy.resolve(current, "/other").toString());
        assertEquals("https://a/nc/index.php/y", RedirectPolicy.resolve(current, "y").toString());
        assertNull(RedirectPolicy.resolve(current, null));
        assertNull(RedirectPolicy.resolve(current, "http://a/x"));
        assertNull(RedirectPolicy.resolve(current, "//b/x"));
        assertNull(RedirectPolicy.resolve(current, "gopher://a/x"));
    }

    @Test
    public void redirectCodesIncludePermanentRedirect() {
        assertTrue(RedirectPolicy.isRedirect(301));
        assertTrue(RedirectPolicy.isRedirect(302));
        assertTrue(RedirectPolicy.isRedirect(303));
        assertTrue(RedirectPolicy.isRedirect(307));
        assertTrue(RedirectPolicy.isRedirect(308));
        assertFalse(RedirectPolicy.isRedirect(200));
        assertFalse(RedirectPolicy.isRedirect(304));
        assertFalse(RedirectPolicy.isRedirect(401));
    }

    @Test
    public void redirectCountIsCapped() {
        assertEquals(5, RedirectPolicy.MAX_REDIRECTS);
    }
}
