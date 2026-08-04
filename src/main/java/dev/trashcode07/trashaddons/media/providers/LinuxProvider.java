package dev.trashcode07.trashaddons.media.providers;

import dev.trashcode07.trashaddons.TrashAddons;
import dev.trashcode07.trashaddons.media.MediaInfo;
import dev.trashcode07.trashaddons.media.MediaUtil;
import net.minecraft.client.Minecraft;
import org.cobalt.ui.notification.NotificationManager;
import org.cobalt.util.chat.ChatUtils;
import org.cobalt.util.chat.MessageType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class LinuxProvider implements MediaProvider {

    // stolen from poli <3
    private static final List<String> FLATPAK_PREFIX =
            new File("/.flatpak-info").exists()
                    ? List.of("flatpak-spawn", "--host")
                    : List.of();

    private static boolean hasFlatpakHostPerm() {
        try (BufferedReader r = new BufferedReader(new FileReader("/.flatpak-info"))) {
            boolean in = false;
            String l;
            while ((l = r.readLine()) != null) {
                if (l.startsWith("[")) { in = l.equals("[Session Bus Policy]"); continue; }
                if (in && l.startsWith("org.freedesktop.Flatpak=talk")) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    static {
        if (new File("/.flatpak-info").exists() && !hasFlatpakHostPerm()) {
            NotificationManager.queue(
                    "Flatpak Permission Missing",
                    "MediaHUD requires host access.\nRun: flatpak override --user --talk-name=org.freedesktop.Flatpak " + System.getenv("FLATPAK_ID"),
                    Duration.ofSeconds(30)
            );
        }
    }

    @Override
    public MediaInfo pollCurrentMedia() {
        String bus = findMprisBus();
        if (bus == null) return MediaInfo.EMPTY;

        String meta   = dbusProperty(bus, "--session", "org.mpris.MediaPlayer2.Player", "Metadata");
        String status = dbusProperty(bus, "--session", "org.mpris.MediaPlayer2.Player", "PlaybackStatus");
        String posStr = dbusProperty(bus, "--session", "org.mpris.MediaPlayer2.Player", "Position");
        if (meta == null) return MediaInfo.EMPTY;

        long positionMs  = MediaParser.parseMprisVariantInt64(posStr);
        long durationMs  = MediaParser.parseMprisMetadataInt64(meta, "mpris:length") / 1000;
        String thumbnail = decodeArt(MediaParser.parseMprisMetadataString(meta, "mpris:artUrl"));

        return new MediaInfo(
                MediaParser.parseMprisMetadataString(meta, "xesam:title"),
                MediaParser.parseMprisMetadataString(meta, "xesam:artist"),
                MediaParser.parseMprisMetadataString(meta, "xesam:album"),
                bus, durationMs, positionMs,
                status != null && status.contains("Playing"), thumbnail,
                MediaParser.parseMprisMetadataString(meta, "mpris:trackid"));
    }

    @Override
    public void togglePlayPause(MediaInfo info) { sendCommand("PlayPause", info); }
    @Override
    public void skipNext(MediaInfo info)        { sendCommand("Next", info); }
    @Override
    public void skipPrevious(MediaInfo info)    { sendCommand("Previous", info); }

    private void sendCommand(String method, MediaInfo info) {
        new Thread(() -> {
            try {
                String bus = info != null ? info.source() : null;
                if (bus == null || !bus.startsWith("org.mpris.MediaPlayer2")) {
                    bus = findMprisBus();
                }
                if (bus != null) {
                    List<String> cmd = new ArrayList<>(FLATPAK_PREFIX);
                    cmd.addAll(List.of("dbus-send", "--session", "--type=method_call",
                            "--dest=" + bus, "/org/mpris/MediaPlayer2",
                            "org.mpris.MediaPlayer2.Player." + method));
                    MediaUtil.run(cmd.toArray(new String[0]));
                }
            } catch (Exception e) {
                TrashAddons.logger.debug("[Media] linux control error: {}", e.getMessage());
            }
        }, "trashaddons-media-linux").start();
    }
    private static String findMprisBus() {
        for (String bt : new String[]{"--session", "--system"}) {
            String list = dbusCall("org.freedesktop.DBus", "/org/freedesktop/DBus",
                    "org.freedesktop.DBus.ListNames", bt);
            if (list == null) continue;
            for (String line : list.split("\n")) {
                int s = line.indexOf("org.mpris.MediaPlayer2.");
                if (s < 0) continue;
                int e = line.indexOf('"', s);
                return line.substring(s, e < 0 ? line.length() : e);
            }
        }
        return null;
    }
    private static String dbusCall(String dest, String path, String method, String busType, String... args) {
        List<String> cmd = new ArrayList<>(FLATPAK_PREFIX);
        cmd.addAll(List.of("dbus-send", busType, "--print-reply",
                "--dest=" + dest, path, method));
        cmd.addAll(List.of(args));
        return MediaUtil.run(cmd.toArray(new String[0]));
    }
    private static String dbusProperty(String bus, String busType, String iface, String prop) {
        return dbusCall(bus, "/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties.Get",
                busType, "string:" + iface, "string:" + prop);
    }
    private static String decodeArt(String artUrl) {
        if (artUrl == null || artUrl.isEmpty()) return null;
        if (artUrl.startsWith("file://")) {
            try { return Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(artUrl.substring(7)))); }
            catch (Exception e) { return null; }
        }
        if (artUrl.startsWith("http")) return fetchUrl(artUrl);
        return null;
    }
    private static String fetchUrl(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) return null;
            return Base64.getEncoder().encodeToString(conn.getInputStream().readAllBytes());
        } catch (Exception e) { return null; }
    }

}
