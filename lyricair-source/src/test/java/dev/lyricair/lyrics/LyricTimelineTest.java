package dev.lyricair.lyrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricTimelineTest {
    @Test void parsesFractionsRepeatedStampsAndOffsetRegardlessOfMetadataOrder() {
        LyricTimeline lyrics = LyricTimeline.parse("""
                [ar:Example]
                [00:01.2][00:10.123]Same line
                [00:02.34]Second line
                [offset:100]
                """);
        assertEquals(3, lyrics.lines().size());
        assertEquals(new LyricLine(1_100, "Same line"), lyrics.lines().getFirst());
        assertEquals(2_240, lyrics.lines().get(1).timeMs());
        assertEquals(10_023, lyrics.lines().get(2).timeMs());
    }

    @Test void preservesBlankInstrumentalCuesAndSelectsDirectlyAfterSeeking() {
        LyricTimeline lyrics = LyricTimeline.parse("[00:05]First\n[00:08]\n[01:00]Next");
        assertEquals(-1, lyrics.indexAt(4_999));
        assertEquals(0, lyrics.indexAt(5_000));
        assertEquals("", lyrics.lines().get(lyrics.indexAt(9_000)).text());
        assertEquals(2, lyrics.indexAt(62_000));
        assertEquals(0, lyrics.indexAt(6_000));
        assertEquals(1_000, lyrics.remainingMs(0, 7_000, 70_000));
    }

    @Test void stripsEnhancedWordStampsAndGroupsSimultaneousLines() {
        LyricTimeline lyrics = LyricTimeline.parse("[00:01]<00:01.00>One <00:01.50>two\n[00:01]Translation\n[00:99]Bad");
        assertEquals(1, lyrics.lines().size());
        assertEquals("One two / Translation", lyrics.lines().getFirst().text());
    }

    @Test void handlesMissingOrUnsyncedLyrics() {
        assertTrue(LyricTimeline.parse(null).isEmpty());
        assertTrue(LyricTimeline.parse("Plain lyrics only\n[ti:Song]").isEmpty());
        assertEquals(-1, LyricTimeline.EMPTY.indexAt(123));
    }
}

