package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Reading and copying streams, which the box's downloads and the GitHub lookups go through (Android 9 has no
 * readAllBytes or transferTo).
 */
public class StreamsTest {

    @Test
    public void everyByteIsRead() throws Exception {
        byte[] text = "{\"tag_name\":\"v0.1.0\"}".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(text, Streams.readAll(new ByteArrayInputStream(text)));
    }

    @Test
    public void anEmptyStreamIsEmpty() throws Exception {
        assertEquals(0, Streams.readAll(new ByteArrayInputStream(new byte[0])).length);
    }

    @Test
    public void aBigStreamIsCopiedWhole() throws Exception {
        // Bigger than the 64 KB buffer, so the reads after the first count.
        byte[] big = new byte[200_000];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i % 251);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Streams.copy(new ByteArrayInputStream(big), out);
        assertArrayEquals(big, out.toByteArray());
    }
}
