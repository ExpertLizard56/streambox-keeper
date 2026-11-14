package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/**
 * Which remote buttons open the input menu. An Onn remote (Vendor_0957_Product_0031) has its own key layout, found
 * Nov 14 on three Onn 4K Pro and 4K Plus boxes: ★ is MACRO_1 there and FEATURED_APP_1 is the "Free TV" button, which
 * must open Free TV and not the input menu.
 */
public class InputKeyTest {

    @Test
    public void onAnOnnRemoteFreeTvIsNotTheInputButton() {
        assertTrue(InputKey.isUsual(KeyEvent.KEYCODE_TV_INPUT, true));
        assertTrue(InputKey.isUsual(KeyEvent.KEYCODE_MACRO_1, true));
        assertTrue(InputKey.isUsual(KeyEvent.KEYCODE_TV, true));
        assertFalse(InputKey.isUsual(KeyEvent.KEYCODE_FEATURED_APP_1, true));
    }

    @Test
    public void onAnotherRemoteFeaturedAppIsTheStar() {
        assertTrue(InputKey.isUsual(KeyEvent.KEYCODE_TV_INPUT, false));
        assertTrue(InputKey.isUsual(KeyEvent.KEYCODE_MACRO_1, false));
        assertTrue(InputKey.isUsual(KeyEvent.KEYCODE_FEATURED_APP_1, false));
        // Another remote's TV button can mean live TV, so it isn't the Input button there.
        assertFalse(InputKey.isUsual(KeyEvent.KEYCODE_TV, false));
    }

    @Test
    public void otherButtonsAreNotTheInputButton() {
        assertFalse(InputKey.isUsual(KeyEvent.KEYCODE_DPAD_CENTER, true));
        assertFalse(InputKey.isUsual(KeyEvent.KEYCODE_DPAD_CENTER, false));
        assertFalse(InputKey.isUsual(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true));
    }
}
