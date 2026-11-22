package net.eneiluj.nextcloud.phonetrack.android.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.text.Spanned;
import android.text.style.URLSpan;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.AboutActivity;
import net.eneiluj.nextcloud.phonetrack.android.fragment.about.AboutFragmentContributingTab;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.Locale;

/**
 * The About screen's Translate section sends translators to this fork's guide, also in a
 * language whose strings.xml still has the original app's Crowdin text and link.
 */
@RunWith(AndroidJUnit4.class)
public class AboutTranslateLinkTest {

    private static final String GUIDE = "https://github.com/lazzurs/phonetrack-android/blob/main/TRANSLATING.md";

    private ActivityController<AboutActivity> controller;

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
    }

    private TextView translateText() {
        controller = Robolectric.buildActivity(AboutActivity.class).setup();
        AboutFragmentContributingTab tab = new AboutFragmentContributingTab();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, tab).commitNow();
        return tab.requireView().findViewById(R.id.about_translate);
    }

    private static void assertLinksToTheGuide(TextView view) {
        Spanned text = (Spanned) view.getText();
        URLSpan[] links = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals(1, links.length);
        assertEquals(GUIDE, links[0].getURL());
        assertFalse(text.toString(), text.toString().toLowerCase(Locale.ROOT).contains("crowdin"));
    }

    @Test
    public void linksToThisForksTranslationGuide() {
        assertLinksToTheGuide(translateText());
    }

    @Test
    @Config(qualifiers = "de-rDE")
    public void linksToTheGuideInALanguageWithTheOldCrowdinText() {
        assertLinksToTheGuide(translateText());
    }
}
