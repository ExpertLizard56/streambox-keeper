package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;

/** The buttons that do nothing, kept as a comma list. */
public class BlockedKeysTest {

    @Test
    public void readsBackWhatItSaved() {
        Set<Integer> keys = new LinkedHashSet<>(Arrays.asList(196, 197));
        assertEquals("196,197", BlockedKeys.join(keys));
        assertEquals(keys, BlockedKeys.parse("196,197"));
    }

    @Test
    public void emptyOrDamagedEntriesAreSkipped() {
        assertTrue(BlockedKeys.parse("").isEmpty());
        assertEquals(new LinkedHashSet<>(Arrays.asList(196, 197)), BlockedKeys.parse(" 196,,x,-3,0,197 "));
    }
}
