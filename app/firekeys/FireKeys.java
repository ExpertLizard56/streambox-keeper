import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * Runs on the Fire TV (app_process, as the ADB shell) for as long as the box is connected: each line on stdin is an
 * Android key code, pressed and released at once. The Fire TV's own `input keyevent` starts a new Java program for
 * every key, about 0.9 s on this TV (Nov 8), which made every switch to an HDMI input that much slower; this one
 * starts once per connection. Built into the app's assets by build.sh; FireTv copies it over and starts it.
 */
public final class FireKeys {

    public static void main(String[] args) throws Exception {
        InputManager input = (InputManager) InputManager.class.getMethod("getInstance").invoke(null);
        Method inject = InputManager.class.getMethod("injectInputEvent", InputEvent.class, int.class);
        System.out.println("ready");
        BufferedReader lines = new BufferedReader(new InputStreamReader(System.in));
        for (String line; (line = lines.readLine()) != null; ) {
            int code;
            try {
                code = Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            long now = SystemClock.uptimeMillis();
            for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                KeyEvent key = new KeyEvent(now, now, action, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                        InputDevice.SOURCE_KEYBOARD);
                inject.invoke(input, key, 0);  // 0: don't wait for the key to be handled
            }
        }
    }
}
