package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.util.HttpStatusException;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLHandshakeException;

/** Upload errors are shown as plain sentences, never "null" or an exception class. */
@RunWith(AndroidJUnit4.class)
public class UploadErrorsTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    private String describe(Throwable e) {
        String text = UploadErrors.describe(context, e);
        assertNotNull(text);
        assertFalse(text, text.contains("null"));
        assertFalse(text, text.contains("Exception"));
        return text;
    }

    @Test
    public void exceptionWithoutMessage() {
        assertEquals("Unknown error", describe(new IOException()));
        assertEquals("Unknown error", describe(new NullPointerException()));
        assertEquals("Unknown error", describe(null));
    }

    @Test
    public void untrustedCertificate() {
        assertEquals("The server's certificate is not trusted",
                describe(new SSLHandshakeException("java.security.cert.CertPathValidatorException: Trust anchor not found")));
        // wrapped by the HTTP client
        assertEquals("The server's certificate is not trusted",
                describe(new IOException("failed", new SSLHandshakeException("handshake"))));
    }

    @Test
    public void timeout() {
        assertEquals("The server did not answer in time", describe(new SocketTimeoutException("Read timed out")));
    }

    @Test
    public void refusedLogin() {
        assertEquals("The server refused the login (HTTP 401)",
                describe(new HttpStatusException(401, context.getString(R.string.e_auth_failure, 401))));
        assertEquals("The server refused the login (HTTP 403)",
                describe(new HttpStatusException(403, context.getString(R.string.e_http_code, 403))));
    }

    @Test
    public void otherHttpErrors() {
        assertEquals("Address not found on the server (HTTP 404)",
                describe(new HttpStatusException(404, context.getString(R.string.e_http_code, 404))));
        assertEquals("The server had an internal error (HTTP 503)",
                describe(new HttpStatusException(503, context.getString(R.string.e_http_code, 503))));
        assertEquals("The server refused the positions (HTTP 400)",
                describe(new HttpStatusException(400, context.getString(R.string.e_http_code, 400))));
    }

    @Test
    public void networkErrors() {
        assertEquals("Server not found: example.invalid", describe(new UnknownHostException("example.invalid")));
        assertEquals("Server not found", describe(new UnknownHostException()));
        assertEquals("Could not connect to the server", describe(new ConnectException("Connection refused")));
        assertEquals("The upload address is not valid", describe(new MalformedURLException("no protocol: x")));
        assertEquals("The server did not accept the positions",
                describe(new IOException(context.getString(R.string.e_server_response))));
    }

    @Test
    public void ownMessagesAreKept() {
        String noAccount = context.getString(R.string.error_no_account_maps);
        assertEquals(noAccount, describe(new IllegalStateException(noAccount)));
    }
}
