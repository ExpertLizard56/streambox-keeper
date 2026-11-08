package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import java.util.List;

/** The TV this box is plugged into, as LinkService and the input panel see it. One per kind of TV. */
interface Tv {

    /** What showing() answers while the TV shows this box. */
    String BOX = "box";
    /** The TV's own home screen, as an entry in inputs(). */
    String HOME = "home";

    /**
     * What the TV shows: BOX, HOME, or another input's id. Null while the TV is off or can't be reached, so the box
     * keeps its keys. Asked twice a second from a background thread; may block briefly.
     */
    String showing();

    /**
     * True while the TV is on, false while it is off (standby), null if it can't be reached. Only a change from false
     * to true counts as the TV coming on, so a power-off never does. Asked twice a second; may block briefly.
     */
    Boolean isOn();

    /** True if the TV takes this key while it shows something other than the box. */
    boolean handles(int keyCode);

    /** A press (down) or release of a key handles() took. Returns at once. */
    void key(int keyCode, boolean down);

    /** The TV's inputs in its own order as {id, name}, the box among them, then {HOME, its name}. Blocks. */
    List<String[]> inputs();

    /** Shows the input with this id (BOX, HOME or one from inputs()). Returns at once. */
    void switchTo(String id);

    /** When the TV last answered (System.currentTimeMillis()), or 0 if it hasn't since this app started. */
    long lastAnswered();

    /** A setting on the TV that stops the remote working, said plainly with the fix, or null. */
    default String problem() {
        return null;
    }

    /** Stops talking to the TV (another TV was set up instead). */
    default void close() {
    }

    /** The TV set up for this box (TvConfig), or null while none is. */
    static Tv create(Context context) {
        TvConfig config = TvConfig.load(context);
        if (config == null) return null;
        return "fire".equals(config.kind) ? new FireTv(context, config) : new Roku(config);
    }
}
