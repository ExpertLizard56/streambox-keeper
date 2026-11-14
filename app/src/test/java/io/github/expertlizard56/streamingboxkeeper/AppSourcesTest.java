package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Which sources the app updates accept on the app's screen: a GitHub page, a link to an .apk, or a Downloader code.
 */
public class AppSourcesTest {

    @Test
    public void aGitHubPageIsASource() {
        assertTrue(AppSources.validSource("github.com/yuliskov/SmartTube"));
        assertTrue(AppSources.validSource("https://github.com/moonlight-stream/moonlight-android"));
    }

    @Test
    public void anApkLinkIsASource() {
        assertTrue(AppSources.validSource("https://example.com/app.apk"));
        assertTrue(AppSources.validSource("http://example.com/app.apk"));
    }

    @Test
    public void aDownloaderCodeIsASource() {
        assertTrue(AppSources.validSource("123456"));
        assertTrue(AppSources.validSource("1234"));
        assertTrue(AppSources.validSource("12345678"));
    }

    @Test
    public void spacesAroundItAreIgnored() {
        assertTrue(AppSources.validSource("  https://example.com/app.apk  "));
    }

    @Test
    public void anythingElseIsNotASource() {
        assertFalse(AppSources.validSource(""));
        assertFalse(AppSources.validSource("SmartTube"));
        assertFalse(AppSources.validSource("123"));
        // A Downloader code is 4 to 8 numbers, no more.
        assertFalse(AppSources.validSource("123456789"));
    }

    @Test
    public void aKnownAppOnTheBoxIsOfferedOnce() {
        Set<String> installed = new HashSet<>(Arrays.asList("com.limelight.noir", "io.github.expertlizard56.homewatch.tv"));
        Set<String> offered = new HashSet<>(Arrays.asList("org.smarttube.stable", "com.limelight.noir"));
        assertEquals(1, AppSources.toOffer(offered, installed::contains).size());
        assertEquals("io.github.expertlizard56.homewatch.tv", AppSources.toOffer(offered, installed::contains).get(0)[0]);
        offered.add("io.github.expertlizard56.homewatch.tv");
        assertTrue(AppSources.toOffer(offered, installed::contains).isEmpty());
    }

    @Test
    public void anAppInUseWaits() {
        String report = "  topResumedActivity=ActivityRecord{28d7c0 u0 com.stremio.one/.MainActivity t63}\n"
                + "  AudioPlaybackConfiguration piid:71 u/pid:10123/689 state:started attr:AudioAttributes\n";
        assertTrue(AppSources.inUse(report, "com.stremio.one", 10200, false));
        assertFalse(AppSources.inUse(report, "com.stremio.one", 10200, true));
        assertTrue(AppSources.inUse(report, "org.music", 10123, true));
        assertFalse(AppSources.inUse(report, "com.stremio", 10050, false));
        assertFalse(AppSources.inUse("", "com.stremio.one", 10200, false));
    }
}
