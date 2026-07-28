package dev.trashcode07.trashaddons.media;

import dev.trashcode07.trashaddons.TrashAddons;
import dev.trashcode07.trashaddons.media.providers.MediaProvider;
import dev.trashcode07.trashaddons.media.providers.WindowsProvider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class MediaUtil {

    private MediaUtil() {}

    public static final long PROCESS_TIMEOUT_SECONDS = 5;

    public static final long POLL_INTERVAL_MS = 1000;
    public static final long MAX_FRAME_DELTA_MS = 3000;
    public static final long DEFAULT_FRAME_MS = 16;
    public static final long SEEK_CORRECTION_THRESHOLD_MS = 6000;

    private static final MediaProvider provider = createProvider();

    private static MediaProvider createProvider() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return new WindowsProvider();
        return null;
    }

    private static final AtomicReference<MediaInfo> CURRENT = new AtomicReference<>(MediaInfo.EMPTY);
    private static final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "trashaddonsv2-media");
                t.setDaemon(true);
                return t;
            });

    private static volatile ScheduledFuture<?> pollTask;

    public static void start() {
        if (pollTask != null) return;
        pollTask = executor.scheduleAtFixedRate(() -> {
            try { CURRENT.set(provider.pollCurrentMedia()); }
            catch (Exception e) { TrashAddons.logger.warn("[Media] poll error: {}", e.getMessage()); }
        }, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public static void stop() {
        if (pollTask != null) { pollTask.cancel(false); pollTask = null; }
    }

    private static String lastTrackKey = "";
    private static long lastPolledSmtcPos = -1;
    private static long internalPositionMs = -1;
    private static long lastFrameTimestamp = System.currentTimeMillis();

    public static synchronized MediaInfo getCurrent() {
        MediaInfo raw = CURRENT.get();
        if (!raw.hasContent()) {
            lastTrackKey = "";
            lastPolledSmtcPos = -1;
            internalPositionMs = -1;
            lastFrameTimestamp = System.currentTimeMillis();
            return raw;
        }

        long now = System.currentTimeMillis();
        long delta = now - lastFrameTimestamp;
        if (delta < 0 || delta > MAX_FRAME_DELTA_MS) delta = DEFAULT_FRAME_MS;
        lastFrameTimestamp = now;

        String trackKey = raw.trackId() != null ? raw.trackId()
                : (raw.title() != null ? raw.title() : "") + "|" + (raw.artist() != null ? raw.artist() : "");

        if (!trackKey.equals(lastTrackKey)) {
            lastTrackKey = trackKey;
            lastPolledSmtcPos = raw.positionMs();
            internalPositionMs = raw.positionMs() >= 0 ? raw.positionMs() : 0;
        } else if (!raw.isPlaying()) {
            //i blame windows for having to do all of this slop
            if (raw.positionMs() >= 0 && raw.positionMs() != lastPolledSmtcPos) {
                lastPolledSmtcPos = raw.positionMs();
                internalPositionMs = raw.positionMs();
            }
        } else {
            if (internalPositionMs >= 0) {
                internalPositionMs += delta;
                if (raw.durationMs() > 0 && internalPositionMs > raw.durationMs())
                    internalPositionMs = raw.durationMs();
            } else if (raw.positionMs() >= 0) {
                internalPositionMs = raw.positionMs();
            }
            long smtcPos = raw.positionMs();
            if (smtcPos >= 0 && smtcPos != lastPolledSmtcPos) {
                lastPolledSmtcPos = smtcPos;
                if (smtcPos > internalPositionMs + SEEK_CORRECTION_THRESHOLD_MS) {
                    internalPositionMs = smtcPos;
                }
            }
        }

        return new MediaInfo(
                raw.title(), raw.artist(), raw.album(), raw.source(),
                raw.durationMs(), internalPositionMs, raw.isPlaying(), raw.thumbnailBase64(),
                raw.trackId()
        );
    }


    public static void togglePlayPause(MediaInfo info) { provider.togglePlayPause(info); }
    public static void skipNext(MediaInfo info)        { provider.skipNext(info); }
    public static void skipPrevious(MediaInfo info)    { provider.skipPrevious(info); }

    public static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            if (!p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) p.destroyForcibly();
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }
}
