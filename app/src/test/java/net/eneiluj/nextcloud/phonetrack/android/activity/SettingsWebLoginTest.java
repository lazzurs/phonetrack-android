package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.net.Uri;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.textfield.TextInputLayout;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.util.CredentialStore;
import net.eneiluj.nextcloud.phonetrack.util.LocalNetworkAccess;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** The Nextcloud web login in the account screen: every failure leads back to the form. */
@RunWith(AndroidJUnit4.class)
public class SettingsWebLoginTest {

    private static final String SERVER = "https://cloud.example.org";

    private Application app;
    private ActivityController<SettingsActivity> controller;
    private SettingsActivity activity;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
        // no local network check before the login
        shadowOf(app).grantPermissions(LocalNetworkAccess.PERMISSION);
        controller = Robolectric.buildActivity(SettingsActivity.class).setup();
        activity = controller.get();
    }

    @After
    public void tearDown() {
        controller.destroy();
        DbTestSupport.resetDatabase();
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        // background tasks post their result to the main thread
        for (int i = 0; i < 250 && !condition.getAsBoolean(); i++) {
            Thread.sleep(20);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));
        }
        assertTrue("timed out", condition.getAsBoolean());
    }

    private EditText urlField() {
        return activity.findViewById(R.id.settings_url);
    }

    private TextInputLayout urlWrapper() {
        return activity.findViewById(R.id.settings_url_wrapper);
    }

    private WebView webView() {
        return activity.findViewById(R.id.login_webview);
    }

    /** Types the address and taps "Connect": the web login replaces the form. */
    private WebViewClient startWebLogin() throws InterruptedException {
        urlField().setText(SERVER);
        Button submit = activity.findViewById(R.id.settings_submit);
        waitFor(submit::isEnabled);
        submit.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull("web login not shown", webView());
        assertNull(urlField());
        return shadowOf(webView()).getWebViewClient();
    }

    private void assertFormShownWithError(String error) {
        shadowOf(Looper.getMainLooper()).idle();
        assertNull("web login still shown", webView());
        assertNotNull("form not shown", urlField());
        assertEquals("address lost", SERVER, urlField().getText().toString());
        assertEquals(error, String.valueOf(urlWrapper().getError()));
        assertTrue(activity.findViewById(R.id.settings_submit).isEnabled());
        assertFalse(activity.isFinishing());
    }

    private static WebResourceRequest request(String url, boolean mainFrame) {
        return new WebResourceRequest() {
            @Override
            public Uri getUrl() {
                return Uri.parse(url);
            }

            @Override
            public boolean isForMainFrame() {
                return mainFrame;
            }

            @Override
            public boolean isRedirect() {
                return false;
            }

            @Override
            public boolean hasGesture() {
                return false;
            }

            @Override
            public String getMethod() {
                return "GET";
            }

            @Override
            public Map<String, String> getRequestHeaders() {
                return Collections.emptyMap();
            }
        };
    }

    private static WebResourceResponse httpError(int status) {
        return new WebResourceResponse("text/html", "utf-8", status, "Error",
                Collections.emptyMap(), null);
    }

    private String invalidLogin(int reason) {
        return app.getString(R.string.error_invalid_login, app.getString(reason));
    }

    @Test
    public void connectLoadsTheLoginFlowOfTheServer() throws Exception {
        startWebLogin();
        assertEquals(SERVER + "/index.php/login/flow", shadowOf(webView()).getLastLoadedUrl());
    }

    @Test
    public void unreachableServerReturnsToTheFormWithAnError() throws Exception {
        WebViewClient client = startWebLogin();

        client.onReceivedError(webView(), request(SERVER + "/index.php/login/flow", true), null);

        assertFormShownWithError(app.getString(R.string.web_login_error_unreachable));
    }

    @Test
    public void failingImageOrScriptDoesNotLeaveTheLogin() throws Exception {
        WebViewClient client = startWebLogin();

        client.onReceivedError(webView(), request(SERVER + "/favicon.ico", false), null);
        client.onReceivedHttpError(webView(), request(SERVER + "/favicon.ico", false), httpError(404));
        shadowOf(Looper.getMainLooper()).idle();

        assertNotNull(webView());
    }

    @Test
    public void noLoginPageAtTheAddressReturnsToTheFormWithAnError() throws Exception {
        WebViewClient client = startWebLogin();

        client.onReceivedHttpError(webView(), request(SERVER + "/index.php/login/flow", true), httpError(404));

        assertFormShownWithError(app.getString(R.string.web_login_error_not_nextcloud, 404));
    }

    @Test
    public void nextcloudErrorPagesLaterInTheFlowStayVisible() throws Exception {
        WebViewClient client = startWebLogin();
        client.onPageFinished(webView(), SERVER + "/index.php/login/flow");

        // e.g. Nextcloud's own "too many requests" page after a mistyped password
        client.onReceivedHttpError(webView(), request(SERVER + "/index.php/login", true), httpError(429));
        shadowOf(Looper.getMainLooper()).idle();

        assertNotNull(webView());
    }

    @Test
    public void backReturnsToTheFormAndThenLeaves() throws Exception {
        startWebLogin();

        activity.getOnBackPressedDispatcher().onBackPressed();
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(webView());
        assertNotNull(urlField());
        assertEquals(SERVER, urlField().getText().toString());
        assertNull(urlWrapper().getError());
        assertFalse(activity.isFinishing());

        // on the form, Back closes the screen as before
        activity.getOnBackPressedDispatcher().onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void connectingAgainAfterAnErrorWorks() throws Exception {
        WebViewClient client = startWebLogin();
        client.onReceivedError(webView(), request(SERVER + "/index.php/login/flow", true), null);
        shadowOf(Looper.getMainLooper()).idle();
        TextInputLayout wrapper = urlWrapper();

        activity.findViewById(R.id.settings_submit).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertNotNull(webView());
        // the form is hidden now: the old error must not be there when it comes back
        assertNull(wrapper.getError());
    }

    @Test
    @SuppressWarnings("deprecation") // the override the activity uses
    public void unexpectedLoginDataDoesNotCrash() throws Exception {
        WebViewClient client = startWebLogin();

        // an element this app does not know
        assertTrue(client.shouldOverrideUrlLoading(webView(), "nc://login/foo:bar"));

        assertFormShownWithError(invalidLogin(R.string.web_login_error_bad_data));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void tooManyLoginDataElementsDoNotCrash() throws Exception {
        WebViewClient client = startWebLogin();

        assertTrue(client.shouldOverrideUrlLoading(webView(),
                "nc://login/server:" + SERVER + "&user:alice&password:secret&extra:1"));

        assertFormShownWithError(invalidLogin(R.string.web_login_error_bad_data));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void brokenEncodingOrMissingServerDoNotCrash() throws Exception {
        WebViewClient client = startWebLogin();
        assertTrue(client.shouldOverrideUrlLoading(webView(),
                "nc://login/server:" + SERVER + "&user:alice&password:%zz"));
        assertFormShownWithError(invalidLogin(R.string.web_login_error_bad_data));

        client = startWebLogin();
        assertTrue(client.shouldOverrideUrlLoading(webView(), "nc://login/user:alice&password:secret"));
        assertFormShownWithError(invalidLogin(R.string.web_login_error_bad_data));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void failedLoginCheckReturnsToTheFormWithTheReason() throws Exception {
        WebViewClient client = startWebLogin();

        // nothing listens there: the check after the Nextcloud approval fails
        assertTrue(client.shouldOverrideUrlLoading(webView(),
                "nc://login/server:http://127.0.0.1:1&user:alice&password:secret"));
        waitFor(() -> webView() == null);

        assertFormShownWithError(invalidLogin(R.string.error_io));
        // nothing was saved
        assertEquals("", PreferenceManager.getDefaultSharedPreferences(app)
                .getString(SettingsActivity.SETTINGS_URL, ""));
        assertEquals("", CredentialStore.getAccountPassword(app));
    }

    @Test
    public void helperTextExplainsADisabledConnectButton() throws Exception {
        urlField().setText("not an address");
        waitFor(() -> urlWrapper().getHelperText() != null);
        assertEquals(app.getString(R.string.settings_url_helper), urlWrapper().getHelperText().toString());
        assertFalse(activity.findViewById(R.id.settings_submit).isEnabled());

        urlField().setText(SERVER);
        waitFor(() -> urlWrapper().getHelperText() == null);
        assertTrue(activity.findViewById(R.id.settings_submit).isEnabled());
    }
}
