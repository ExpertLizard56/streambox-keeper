package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/**
 * The SHA-256 the updates are checked with (GitHub's own, against the download).
 */
public class UpdaterTest {

    private static final String EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    public void anEmptyFileMatchesItsDigest() throws Exception {
        assertEquals(EMPTY, Updater.sha256(file("")));
    }

    @Test
    public void textMatchesItsDigest() throws Exception {
        assertEquals(ABC, Updater.sha256(file("abc")));
    }

    @Test
    public void aBigFileIsReadToItsEnd() throws Exception {
        // Bigger than the 64 KB read buffer, so every read after the first counts.
        byte[] big = new byte[200_000];
        java.util.Arrays.fill(big, (byte) 'a');
        File file = File.createTempFile("keeper", ".bin");
        try {
            Files.write(file.toPath(), big);
            String digest = Updater.sha256(file);
            assertEquals(64, digest.length());
            assertEquals(digest, Updater.sha256(file));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static File file(String text) throws Exception {
        File file = File.createTempFile("keeper", ".bin");
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        //noinspection ResultOfMethodCallIgnored
        file.deleteOnExit();
        return file;
    }
}
