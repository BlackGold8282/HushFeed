package app.morphe.extension.tiktok.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.PlaybackPreferenceCategory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** What TikTok's background play reads while Keep playing in the background is on and off. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BackgroundPlayTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();
    public static final class TestActivity extends PreferenceActivity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }

    @Test public void offLeavesTikTokItsOwnAnswers() {
        Settings.BACKGROUND_PLAY.save(false);
        for (int served : new int[]{0, 1, 2, 7, -1}) assertEquals(served, BackgroundPlay.mode(served));
        assertFalse(BackgroundPlay.remembered(false));
        assertTrue(BackgroundPlay.remembered(true));
    }

    @Test public void onKeepsItOnForGood() {
        try {
            Settings.BACKGROUND_PLAY.save(true);
            // 2 is the value the menu leaves on for good, whatever the server moved the account to.
            for (int served : new int[]{0, 1, 2}) assertEquals(2, BackgroundPlay.mode(served));
            assertTrue(BackgroundPlay.remembered(false));
            assertTrue(BackgroundPlay.remembered(true));
        } finally {
            Settings.BACKGROUND_PLAY.save(false);
        }
    }

    @Test public void theLabSaysWhenTheSwitchDecidesTheKey() {
        boolean was = SettingsStatus.backgroundPlayEnabled;
        try {
            Settings.BACKGROUND_PLAY.save(true);
            // Not patched: a stored true from an older bundle decides nothing.
            SettingsStatus.backgroundPlayEnabled = false;
            assertFalse(BackgroundPlay.decidesGate(BackgroundPlay.GATE_KEY));

            SettingsStatus.backgroundPlayEnabled = true;
            assertTrue(BackgroundPlay.decidesGate(BackgroundPlay.GATE_KEY));
            assertFalse(BackgroundPlay.decidesGate("background_play_enable_v2"));
            assertFalse(BackgroundPlay.decidesGate(null));

            Settings.BACKGROUND_PLAY.save(false);
            assertFalse(BackgroundPlay.decidesGate(BackgroundPlay.GATE_KEY));
        } finally {
            Settings.BACKGROUND_PLAY.save(false);
            SettingsStatus.backgroundPlayEnabled = was;
        }
    }

    @Test public void theSwitchIsOnThePlaybackPageOnlyWhenPatched() {
        boolean was = SettingsStatus.backgroundPlayEnabled;
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);

            SettingsStatus.backgroundPlayEnabled = false;
            PreferenceScreen without = activity.getPreferenceManager().createPreferenceScreen(activity);
            new PlaybackPreferenceCategory(activity, without);
            assertNull(without.findPreference(Settings.BACKGROUND_PLAY.key));

            SettingsStatus.backgroundPlayEnabled = true;
            assertTrue(PlaybackPreferenceCategory.isAvailable());
            PreferenceScreen with = activity.getPreferenceManager().createPreferenceScreen(activity);
            new PlaybackPreferenceCategory(activity, with);
            assertNotNull(with.findPreference(Settings.BACKGROUND_PLAY.key));
        } finally {
            SettingsStatus.backgroundPlayEnabled = was;
        }
    }
}
