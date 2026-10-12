package dev.lyricair.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable line-synced timeline. Blank cues deliberately end the previous lyric. */
public final class LyricTimeline {
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_STAMP = Pattern.compile("<\\d{1,3}:[0-5]\\d(?:[.:]\\d{1,3})?>");
    public static final LyricTimeline EMPTY = new LyricTimeline(List.of());
    private final List<LyricLine> lines;

    public LyricTimeline(List<LyricLine> lines) {
        this.lines = lines.stream().sorted(Comparator.comparingLong(LyricLine::timeMs)).toList();
    }

    public static LyricTimeline parse(String lrc) {
        if (lrc == null || lrc.isBlank()) return EMPTY;
        long offset = 0;
        Matcher offsetMatcher = OFFSET.matcher(lrc);
        while (offsetMatcher.find()) {
            try { offset = Long.parseLong(offsetMatcher.group(1)); }
            catch (NumberFormatException ignored) { }
        }
        List<LyricLine> result = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher matcher = STAMP.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (matcher.find()) {
                String fraction = matcher.group(3);
                long millis = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                long time = Long.parseLong(matcher.group(1)) * 60_000L
                        + Long.parseLong(matcher.group(2)) * 1_000L + millis;
                times.add(Math.max(0, time - offset));
                end = matcher.end();
            }
            if (times.isEmpty()) continue;
            String text = WORD_STAMP.matcher(raw.substring(end)).replaceAll("")
                    .replaceAll("[\\p{Cntrl}§]", "").strip();
            // Bounds guard malformed or unexpectedly huge provider responses.
            if (text.length() > 500) text = text.substring(0, 500);
            for (long time : times) result.add(new LyricLine(time, text));
            if (result.size() > 20_000) break;
        }
        result.sort(Comparator.comparingLong(LyricLine::timeMs));
        List<LyricLine> grouped = new ArrayList<>();
        for (LyricLine line : result) {
            if (!grouped.isEmpty() && grouped.getLast().timeMs() == line.timeMs()) {
                LyricLine previous = grouped.removeLast();
                String joined = previous.text().isBlank() ? line.text()
                        : line.text().isBlank() ? previous.text() : previous.text() + " / " + line.text();
                grouped.add(new LyricLine(line.timeMs(), joined));
            } else grouped.add(line);
        }
        return new LyricTimeline(grouped);
    }

    public List<LyricLine> lines() { return lines; }
    public boolean isEmpty() { return lines.isEmpty(); }

    /** Select directly by position rather than replaying missed cues after a seek. */
    public int indexAt(long positionMs) {
        int low = 0, high = lines.size() - 1, result = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).timeMs() <= positionMs) {
                result = middle;
                low = middle + 1;
            } else high = middle - 1;
        }
        return result;
    }

    public long remainingMs(int index, long positionMs, long durationMs) {
        long end = index + 1 < lines.size() ? lines.get(index + 1).timeMs() : durationMs;
        return Math.max(0, end - positionMs);
    }
}

