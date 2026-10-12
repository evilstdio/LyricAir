package dev.lyricair.playback;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackSnapshotTest {
    @Test void extrapolatesPlayingPositionAndClampsToTrackEnd() {
        TrackSnapshot track = new TrackSnapshot("Track", "Artist", "Album", 10_000, 8_000, true, 1, 1_000_000_000L);
        assertEquals(8_500, track.positionAt(1_500_000_000L));
        assertEquals(10_000, track.positionAt(5_000_000_000L));
        assertEquals(8_000, track.positionAt(900_000_000L));
    }

    @Test void holdsPausedPositionAndMarksMissingUpdatesStale() {
        TrackSnapshot track = new TrackSnapshot("Track", "Artist", "Album", 10_000, 3_000, false, 1, 0);
        assertEquals(3_000, track.positionAt(4_000_000_000L));
        assertFalse(track.stale(4_000_000_000L));
        assertTrue(track.stale(6_000_000_000L));
    }
}
