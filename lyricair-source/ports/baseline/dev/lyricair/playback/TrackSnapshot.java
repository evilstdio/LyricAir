package dev.lyricair.playback;

/** The media bridge supplies a position projected to the instant the sample was taken. */
public record TrackSnapshot(String title, String artist, String album, long durationMs,
                            long positionMs, boolean playing, double playbackRate, long sampledNanos) {
    public String key() { return title + "\u0000" + artist + "\u0000" + album + "\u0000" + durationMs; }
    public long positionAt(long nowNanos) {
        long elapsed = playing ? Math.round(Math.max(0, nowNanos - sampledNanos) / 1_000_000.0 * playbackRate) : 0;
        long position = Math.max(0, positionMs + elapsed);
        return durationMs > 0 ? Math.min(durationMs, position) : position;
    }
    public boolean stale(long nowNanos) { return nowNanos - sampledNanos > 5_000_000_000L; }
}

