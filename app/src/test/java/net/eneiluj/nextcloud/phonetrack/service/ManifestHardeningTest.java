package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import net.eneiluj.nextcloud.phonetrack.util.LocalNetworkAccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Guards manifest declarations that platform rules or security depend on.
 */
@RunWith(AndroidJUnit4.class)
public class ManifestHardeningTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private final PackageManager pm = context.getPackageManager();

    @Test
    public void testsEmulateTheTargetSdk() {
        // raise sdk= in src/test/resources/robolectric.properties together with targetSdk
        assertEquals(context.getApplicationInfo().targetSdkVersion, Build.VERSION.SDK_INT);
    }

    @Test
    public void usesTheForksOwnApplicationId() {
        // changing it makes a different app: installs can no longer be updated
        assertTrue(context.getPackageName(), context.getPackageName().matches("io\\.github\\.lazzurs\\.phonetrack(\\.dev|\\.play)?"));
    }

    @Test
    public void loggerServiceIsALocationForegroundService() throws Exception {
        // dataSync is capped at 6h/day on Android 15+ and can't start from BOOT_COMPLETED
        ServiceInfo info = pm.getServiceInfo(new ComponentName(context, LoggerService.class), 0);
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, info.getForegroundServiceType());

        PackageInfo pkg = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        List<String> requested = Arrays.asList(pkg.requestedPermissions);
        assertTrue(requested.contains(Manifest.permission.FOREGROUND_SERVICE_LOCATION));
        assertFalse(requested.contains(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC));
    }

    @Test
    public void declaresLocalNetworkAccessForAndroid17() throws Exception {
        PackageInfo pkg = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertTrue(Arrays.asList(pkg.requestedPermissions).contains(LocalNetworkAccess.PERMISSION));
    }

    @Test
    public void smsListenerOnlyAcceptsBroadcastsFromTelephony() throws Exception {
        // otherwise any app can forge SMS_RECEIVED and trigger the remote commands
        android.content.pm.ActivityInfo info = pm.getReceiverInfo(new ComponentName(context, SmsListener.class), 0);
        assertEquals(Manifest.permission.BROADCAST_SMS, info.permission);
    }

    @Test
    public void certificateDialogIsNotExported() throws Exception {
        // cert4android exports it and trusts the certificate in its intent when asked to:
        // exported, any app could make PhoneTrack trust a certificate of its choosing
        android.content.pm.ActivityInfo info = pm.getActivityInfo(
                new ComponentName(context, "at.bitfire.cert4android.TrustCertificateActivity"), 0);
        assertFalse(info.exported);
    }

    @Test
    public void bootReceiverIsNotExported() throws Exception {
        android.content.pm.ActivityInfo info = pm.getReceiverInfo(new ComponentName(context, BootCompletedReceiver.class), 0);
        assertFalse(info.exported);
    }

    @Test
    public void onlyTheExpectedComponentsAreExported() throws Exception {
        PackageInfo pkg = pm.getPackageInfo(context.getPackageName(),
                PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                        | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS);
        Set<String> exported = new TreeSet<>();
        List<android.content.pm.ComponentInfo> all = new ArrayList<>();
        for (android.content.pm.ComponentInfo[] group : new android.content.pm.ComponentInfo[][]{
                pkg.activities, pkg.services, pkg.receivers, pkg.providers}) {
            if (group != null) {
                all.addAll(Arrays.asList(group));
            }
        }
        for (android.content.pm.ComponentInfo info : all) {
            if (info.exported) {
                exported.add(info.name);
            }
        }
        String activities = "net.eneiluj.nextcloud.phonetrack.android.activity.";
        Set<String> expected = new TreeSet<>(Arrays.asList(
                // launcher
                activities + "LogjobsListViewActivity",
                // the two share targets (aliases of the non-exported editors)
                activities + "SharePhoneTrackLogjobAlias",
                activities + "ShareCustomLogjobAlias",
                "net.eneiluj.nextcloud.phonetrack.android.quicksettings.NewLogjobTileService",
                SmsListener.class.getName(),
                // AndroidX libraries; each is protected by a system-only permission or ignores its input
                "androidx.profileinstaller.ProfileInstallReceiver",
                "androidx.work.impl.background.systemjob.SystemJobService",
                "androidx.work.impl.diagnostics.DiagnosticsReceiver"));
        assertEquals(expected, exported);
    }

    @Test
    public void logjobEditorsAreNotExported() throws Exception {
        // an exported editor would let any app open an existing logjob by ID
        String activities = "net.eneiluj.nextcloud.phonetrack.android.activity.";
        for (String name : new String[]{"EditPhoneTrackLogjobActivity", "EditCustomLogjobActivity", "EditMapsLogjobActivity"}) {
            assertFalse(name, pm.getActivityInfo(new ComponentName(context.getPackageName(), activities + name), 0).exported);
        }
    }
}
