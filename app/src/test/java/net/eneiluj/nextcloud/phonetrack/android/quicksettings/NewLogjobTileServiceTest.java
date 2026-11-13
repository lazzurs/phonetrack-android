package net.eneiluj.nextcloud.phonetrack.android.quicksettings;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.service.quicksettings.Tile;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** The tile is only "on" while logging runs. */
@RunWith(AndroidJUnit4.class)
public class NewLogjobTileServiceTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void tileIsInactiveWhenNothingIsLogging() {
        assertEquals(Tile.STATE_INACTIVE, NewLogjobTileService.tileState(false));
        assertEquals("Not logging", NewLogjobTileService.subtitle(context, false, 0).toString());
    }

    @Test
    public void tileIsActiveAndCountsTheLogjobsWhileLogging() {
        assertEquals(Tile.STATE_ACTIVE, NewLogjobTileService.tileState(true));
        assertEquals("Logging 2 log jobs", NewLogjobTileService.subtitle(context, true, 2).toString());
    }
}
