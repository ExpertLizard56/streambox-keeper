package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The main screen's "Update check" line: one wording for each way a self-update check ends. */
public class UpdateCheckWordsTest {

    @Test
    public void eachOutcomeHasItsWords() {
        assertEquals("Off", Updater.checkWords(Updater.Outcome.OFF, null));
        assertEquals("Up to date (0.1.6)", Updater.checkWords(Updater.Outcome.UP_TO_DATE, "0.1.6"));
        assertEquals("No release on GitHub yet", Updater.checkWords(Updater.Outcome.NO_RELEASE, null));
        assertEquals("No app file in release v0.2.0", Updater.checkWords(Updater.Outcome.NO_APK, "v0.2.0"));
        assertEquals("Download didn't match GitHub's checksum, not installed",
                Updater.checkWords(Updater.Outcome.BAD_CHECKSUM, null));
        assertEquals("Updating to 0.2.0", Updater.checkWords(Updater.Outcome.UPDATING, "0.2.0"));
        assertEquals("Check failed: timeout", Updater.checkWords(Updater.Outcome.FAILED, " timeout "));
    }

    @Test
    public void aFailureWithoutAReasonStillSaysSomething() {
        assertEquals("Check failed: unknown error", Updater.checkWords(Updater.Outcome.FAILED, null));
    }

    @Test
    public void aLongReasonIsCut() {
        String words = Updater.checkWords(Updater.Outcome.FAILED, new String(new char[200]).replace('\0', 'x'));
        assertTrue(words.length() <= "Check failed: ".length() + 80);
        assertTrue(words.endsWith("..."));
    }
}
