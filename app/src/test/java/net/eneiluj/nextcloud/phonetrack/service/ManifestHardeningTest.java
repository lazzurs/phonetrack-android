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

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/**
 * Guards manifest declarations that platform rules or security depend on.
 */
@RunWith(AndroidJUnit4.class)
public class ManifestHardeningTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private final PackageManager pm = context.getPackageManager();

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
    public void smsListenerOnlyAcceptsBroadcastsFromTelephony() throws Exception {
        // otherwise any app can forge SMS_RECEIVED and trigger the remote commands
        android.content.pm.ActivityInfo info = pm.getReceiverInfo(new ComponentName(context, SmsListener.class), 0);
        assertEquals(Manifest.permission.BROADCAST_SMS, info.permission);
    }

    @Test
    public void bootReceiverIsNotExported() throws Exception {
        android.content.pm.ActivityInfo info = pm.getReceiverInfo(new ComponentName(context, BootCompletedReceiver.class), 0);
        assertFalse(info.exported);
    }
}
