package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
