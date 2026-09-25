package net.eneiluj.nextcloud.phonetrack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.XmlResourceParser;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The launcher shortcut's intent names its package explicitly, and each flavor has its own
 * applicationId, so each flavor needs its own shortcuts.xml. Runs in every flavor's unit tests
 * against that variant's merged resources.
 */
@RunWith(AndroidJUnit4.class)
public class ShortcutsTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void shortcutsTargetThisVariantsPackageAndAnExistingActivity() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        List<ComponentName> targets = new ArrayList<>();
        try (XmlResourceParser parser = context.getResources().getXml(R.xml.shortcuts)) {
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG && "intent".equals(parser.getName())) {
                    targets.add(new ComponentName(
                            parser.getAttributeValue(ANDROID_NS, "targetPackage"),
                            parser.getAttributeValue(ANDROID_NS, "targetClass")));
                }
            }
        }

        assertEquals(1, targets.size());
        for (ComponentName target : targets) {
            assertEquals(context.getPackageName(), target.getPackageName());
            // throws NameNotFoundException if the activity isn't declared in this package
            assertNotNull(context.getPackageManager().getActivityInfo(target, 0));
        }
    }
}
