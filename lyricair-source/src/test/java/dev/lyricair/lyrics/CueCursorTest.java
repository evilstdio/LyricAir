package dev.lyricair.lyrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CueCursorTest {
    private final LyricTimeline lyrics = LyricTimeline.parse("[00:01]A\n[00:06]B\n[00:12]C");

    @Test void emitsOneCuePerLineWithoutDuplicatesWhilePaused() {
        CueCursor cursor = new CueCursor();
        assertEquals(0, cursor.update(lyrics, 1_000));
        assertEquals(-1, cursor.update(lyrics, 1_050));
        assertEquals(-1, cursor.update(lyrics, 1_050));
        assertFalse(cursor.sought());
    }

    @Test void seekingSkipsMissedCuesAndCanReenterTheSameLine() {
        CueCursor cursor = new CueCursor();
        cursor.update(lyrics, 1_000);
        assertEquals(2, cursor.update(lyrics, 13_000));
        assertTrue(cursor.sought());
        assertEquals(0, cursor.update(lyrics, 4_000));
        assertEquals(0, cursor.update(lyrics, 2_000));
        assertTrue(cursor.sought());
        assertEquals(-1, cursor.update(lyrics, 2_050));
        assertFalse(cursor.sought());
    }

    @Test void ignoresSmallPositionCorrectionsAndResetAllowsCurrentLineAgain() {
        CueCursor cursor = new CueCursor();
        cursor.update(lyrics, 2_000);
        assertEquals(-1, cursor.update(lyrics, 1_900));
        assertFalse(cursor.sought());
        cursor.reset();
        assertEquals(0, cursor.update(lyrics, 1_900));
    }
}

