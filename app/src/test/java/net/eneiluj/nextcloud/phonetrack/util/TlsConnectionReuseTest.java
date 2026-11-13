package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.persistence.WebTrackHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;

import at.bitfire.cert4android.CustomCertManager;
import at.bitfire.cert4android.CustomCertStore;

/**
 * Uploads must reuse one TLS socket factory (so connections are pooled and TLS sessions resumed),
 * but never across a narrowing of trust: after a reset the certificate has to be checked again.
 */
@RunWith(AndroidJUnit4.class)
public class TlsConnectionReuseTest {

    private static final char[] PASSWORD = "test-only".toCharArray();

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private final Context context = ApplicationProvider.getApplicationContext();
    private TestHttpsServer server;

    @Before
    public void setUp() throws Exception {
        // process-wide singletons: start every test from scratch
        Field instance = CustomCertStore.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
        SupportUtil.resetTlsConnections();
    }

    @After
    public void tearDown() throws IOException {
        if (server != null) {
            server.close();
        }
        SupportUtil.resetTlsConnections();
    }

    @Test
    public void reusesTheSocketFactoryAndHostnameVerifier() throws Exception {
        CustomCertManager ccm = CertificateTrust.newCertManager(context);

        HttpsURLConnection first = (HttpsURLConnection) SupportUtil.getHttpURLConnection(ccm, "https://example.invalid/a");
        HttpsURLConnection second = (HttpsURLConnection) SupportUtil.getHttpURLConnection(ccm, "https://example.invalid/b");

        assertSame(first.getSSLSocketFactory(), second.getSSLSocketFactory());
        assertSame(first.getHostnameVerifier(), second.getHostnameVerifier());
        // still cert4android's trust manager and hostname check, not the platform defaults
        assertNotSame(HttpsURLConnection.getDefaultSSLSocketFactory(), first.getSSLSocketFactory());
        assertTrue(first.getHostnameVerifier() instanceof CustomCertManager.HostnameVerifier);
    }

    @Test
    public void resettingCertificatesReplacesTheSocketFactory() throws Exception {
        CustomCertManager ccm = CertificateTrust.newCertManager(context);
        HttpsURLConnection before = (HttpsURLConnection) SupportUtil.getHttpURLConnection(ccm, "https://example.invalid/");

        CertificateTrust.resetUserDecisions(context);

        HttpsURLConnection after = (HttpsURLConnection) SupportUtil.getHttpURLConnection(ccm, "https://example.invalid/");
        assertNotSame(before.getSSLSocketFactory(), after.getSSLSocketFactory());
        assertNotSame(before.getHostnameVerifier(), after.getHostnameVerifier());
    }

    @Test
    public void changingSystemCertificateTrustReplacesTheSocketFactory() throws Exception {
        CustomCertManager ccm = CertificateTrust.newCertManager(context);
        HttpsURLConnection before = (HttpsURLConnection) SupportUtil.getHttpURLConnection(ccm, "https://example.invalid/");

        CertificateTrust.onTrustSystemCertsChanged();

        HttpsURLConnection after = (HttpsURLConnection) SupportUtil.getHttpURLConnection(ccm, "https://example.invalid/");
        assertNotSame(before.getSSLSocketFactory(), after.getSSLSocketFactory());
    }

    @Test
    public void anotherCertManagerGetsItsOwnSocketFactory() throws Exception {
        HttpsURLConnection first = (HttpsURLConnection) SupportUtil.getHttpURLConnection(
                CertificateTrust.newCertManager(context), "https://example.invalid/");
        HttpsURLConnection second = (HttpsURLConnection) SupportUtil.getHttpURLConnection(
                CertificateTrust.newCertManager(context), "https://example.invalid/");

        assertNotSame(first.getSSLSocketFactory(), second.getSSLSocketFactory());
    }

    @Test
    public void uploadsShareOneConnectionUntilTrustIsReset() throws Exception {
        File keyStoreFile = createServerKeyStore();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(keyStoreFile)) {
            keyStore.load(in, PASSWORD);
        }
        X509Certificate serverCert = (X509Certificate) keyStore.getCertificate("server");
        assertNotNull(serverCert);
        server = new TestHttpsServer(keyStore);

        // self-signed: trusted only because the user accepted it
        CustomCertStore.Companion.getInstance(context).setTrustedByUser(serverCert);
        CustomCertManager ccm = CertificateTrust.newCertManager(context);
        WebTrackHelper helper = new WebTrackHelper(context, ccm);
        URL url = new URL("https://127.0.0.1:" + server.port() + "/index.php/apps/phonetrack/logPost/token/device");
        Map<String, String> params = new HashMap<>();
        params.put(WebTrackHelper.PARAM_LAT, "53.35");
        params.put(WebTrackHelper.PARAM_LON, "-6.26");

        helper.postPositionToPhoneTrack(url, params);
        helper.postPositionToPhoneTrack(url, params);
        helper.postPositionToPhoneTrack(url, params);

        assertEquals(3, server.requests.get());
        assertEquals("one TCP connection and one TLS handshake for three uploads", 1, server.connections.get());

        // the user withdraws trust: neither the pooled connection nor a resumed TLS session
        // may carry on trusting the certificate
        CertificateTrust.resetUserDecisions(context);
        assertThrows(IOException.class, () -> helper.postPositionToPhoneTrack(url, params));
        assertEquals(3, server.requests.get());
    }

    /** A throwaway self-signed key pair for 127.0.0.1, made with the test JDK's keytool. */
    private File createServerKeyStore() throws Exception {
        File file = new File(tmp.getRoot(), "server.p12");
        String keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair",
                "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", file.getPath(),
                "-storepass", new String(PASSWORD), "-keypass", new String(PASSWORD))
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue("keytool timed out", process.waitFor(60, TimeUnit.SECONDS));
        assertEquals("keytool failed: " + output, 0, process.exitValue());
        return file;
    }

    /** Minimal HTTP/1.1 keep-alive server that answers every request with {"done":1}. */
    private static final class TestHttpsServer implements AutoCloseable {
        final AtomicInteger connections = new AtomicInteger();
        final AtomicInteger requests = new AtomicInteger();
        private final SSLServerSocket serverSocket;

        TestHttpsServer(KeyStore keyStore) throws Exception {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, PASSWORD);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory()
                    .createServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            Thread acceptor = new Thread(this::acceptLoop, "test-https-accept");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    connections.incrementAndGet();
                    Thread handler = new Thread(() -> serve(socket), "test-https-conn");
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void serve(Socket socket) {
            try (Socket s = socket) {
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
                String requestLine;
                while ((requestLine = reader.readLine()) != null) {
                    if (requestLine.isEmpty()) {
                        continue;
                    }
                    int contentLength = 0;
                    String header;
                    while ((header = reader.readLine()) != null && !header.isEmpty()) {
                        String lower = header.toLowerCase(Locale.ROOT);
                        if (lower.startsWith("content-length:")) {
                            contentLength = Integer.parseInt(lower.substring("content-length:".length()).trim());
                        }
                    }
                    for (int i = 0; i < contentLength; i++) {
                        if (reader.read() == -1) {
                            return;
                        }
                    }
                    requests.incrementAndGet();
                    byte[] body = "{\"done\":1}".getBytes(StandardCharsets.UTF_8);
                    String head = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + body.length + "\r\n"
                            + "\r\n";
                    out.write(head.getBytes(StandardCharsets.ISO_8859_1));
                    out.write(body);
                    out.flush();
                }
            } catch (IOException e) {
                // client went away or rejected the certificate
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
