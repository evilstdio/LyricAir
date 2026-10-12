package dev.lyricair.lyrics;

/** Reports one cue on entry, and permits re-entry when seeking within the same line. */
public final class CueCursor {
    private int previousIndex = -1;
    private long previousPosition = -1;
    private boolean seek;

    public int update(LyricTimeline timeline, long positionMs) {
        seek = previousPosition >= 0 && (positionMs < previousPosition - 250 || positionMs > previousPosition + 2_000);
        int index = timeline.indexAt(positionMs);
        boolean changed = index != previousIndex || seek;
        previousIndex = index;
        previousPosition = positionMs;
        return changed ? index : -1;
    }

    public boolean sought() { return seek; }
    public void reset() { previousIndex = -1; previousPosition = -1; seek = false; }
}

