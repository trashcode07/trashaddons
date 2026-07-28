package dev.trashcode07.trashaddons.media;

public final class MediaInfo {
    public static final MediaInfo EMPTY = new MediaInfo(null, null, null, null, -1, -1, false, null, null);

    private final String title;
    private final String artist;
    private final String album;
    private final String source;
    private final long durationMs;
    private final long positionMs;
    private final boolean playing;
    private final String thumbnailBase64;
    private final String trackId;
    private final long pollTimestampMs;

    public MediaInfo(String title, String artist, String album, String source,
                     long durationMs, long positionMs, boolean playing, String thumbnailBase64,
                     String trackId) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.source = source;
        this.durationMs = durationMs;
        this.positionMs = positionMs;
        this.playing = playing;
        this.thumbnailBase64 = thumbnailBase64;
        this.trackId = trackId;
        this.pollTimestampMs = System.currentTimeMillis();
    }

    public String title()           { return title; }
    public String artist()          { return artist; }
    public String album()           { return album; }
    public String source()          { return source; }
    public long   durationMs()      { return durationMs; }
    public long   positionMs()      { return positionMs; }
    public boolean isPlaying()      { return playing; }
    public String thumbnailBase64() { return thumbnailBase64; }
    public String trackId()         { return trackId; }

    public boolean hasContent() {
        return (title != null && !title.isEmpty()) || (artist != null && !artist.isEmpty());
    }

    public long currentPositionMs() {
        if (positionMs < 0) return -1;
        if (!playing) return positionMs;
        long elapsed = System.currentTimeMillis() - pollTimestampMs;
        long current = positionMs + elapsed;
        if (durationMs > 0 && current > durationMs) return durationMs;
        return current;
    }

    public long remainingMs() {
        long current = currentPositionMs();
        return durationMs > 0 && current >= 0 ? durationMs - current : -1;
    }

    public float progress() {
        if (durationMs <= 0) return 0f;
        long pos = currentPositionMs();
        if (pos < 0) return 0f;
        return (float) pos / durationMs;
    }

    public String durationFormatted()   { return formatMs(durationMs); }
    public String positionFormatted()   { return formatMs(currentPositionMs()); }

    private static String formatMs(long ms) {
        if (ms <= 0) return "0:00";
        long secs = ms / 1000;
        return String.format("%d:%02d", secs / 60, secs % 60);
    }
}
