package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The version comparison the update checks use: a GitHub release's tag against the version on the box.
 */
public class JobsTest {

    @Test
    public void aNewerVersionIsHigher() {
        assertTrue(Jobs.compare("0.2.0", "0.1.0") > 0);
        assertTrue(Jobs.compare("4.0.81", "4.0.80") > 0);
    }

    @Test
    public void aLowerVersionIsLower() {
        assertTrue(Jobs.compare("0.1.0", "0.2.0") < 0);
        assertTrue(Jobs.compare("4.0.80", "4.0.81") < 0);
    }

    @Test
    public void aTagMayCarryAV() {
        assertTrue(Jobs.compare("v0.2.0", "0.1.0") > 0);
        assertEquals(0, Jobs.compare("v0.1.0", "0.1.0"));
    }

    @Test
    public void eachNumberCountsOnItsOwn() {
        // 1.10 is newer than 1.9, which comparing the tags as text would get wrong.
        assertTrue(Jobs.compare("1.10", "1.9") > 0);
        assertTrue(Jobs.compare("0.10.0", "0.9.9") > 0);
    }

    @Test
    public void aTrailingZeroChangesNothing() {
        assertEquals(0, Jobs.compare("0.1", "0.1.0"));
    }

    @Test
    public void nothingToCompareIsEven() {
        assertEquals(0, Jobs.compare(null, null));
        assertEquals(0, Jobs.compare("", ""));
    }
}
