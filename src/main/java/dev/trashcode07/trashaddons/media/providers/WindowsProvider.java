package dev.trashcode07.trashaddons.media.providers;

import dev.trashcode07.trashaddons.TrashAddons;
import dev.trashcode07.trashaddons.media.MediaInfo;
import dev.trashcode07.trashaddons.media.MediaUtil;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class WindowsProvider implements MediaProvider {

    private static Path smtcScriptPath;
    private static Path ctrlScriptPath;

    public WindowsProvider() {
        smtcScriptPath = extractScript("media_smtc.ps1", "media_smtc.ps1");
        ctrlScriptPath = extractScript("media_ctrl.ps1", "media_ctrl.ps1");
    }

    private static Path extractScript(String resourceName, String fileName) {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "trashaddons");
            Files.createDirectories(dir);
            Path dest = dir.resolve(fileName);
            if (!Files.exists(dest)) {
                try (InputStream in = WindowsProvider.class.getResourceAsStream("/" + resourceName)) {
                    if (in == null) return null;
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            dest.toFile().deleteOnExit();
            return dest;
        } catch (Exception e) {
            TrashAddons.logger.warn("[Media] Failed to extract script {}: {}", resourceName, e.getMessage());
            return null;
        }
    }

    @Override
    public MediaInfo pollCurrentMedia() {
        if (smtcScriptPath == null) return MediaInfo.EMPTY;
        try {
            String out = MediaUtil.run("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", smtcScriptPath.toString());
            if (out == null || out.startsWith("NONE") || out.startsWith("ERROR:")) return MediaInfo.EMPTY;
            String firstLine = out.split("\r?\n")[0].trim();
            if (firstLine.isEmpty()) return MediaInfo.EMPTY;
            return MediaParser.parseWindowsPipe(firstLine);
        } catch (Exception e) {
            return MediaInfo.EMPTY;
        }
    }

    @Override
    public void togglePlayPause(MediaInfo info) { runControl("TryTogglePlayPauseAsync", info); }
    @Override
    public void skipNext(MediaInfo info)        { runControl("TrySkipNextAsync", info); }
    @Override
    public void skipPrevious(MediaInfo info)    { runControl("TrySkipPreviousAsync", info); }

    private void runControl(String action, MediaInfo info) {
        new Thread(() -> {
            try {
                if (ctrlScriptPath == null) return;
                List<String> cmd = new ArrayList<>(List.of(
                        "powershell.exe", "-NoProfile", "-NonInteractive",
                        "-ExecutionPolicy", "Bypass", "-File", ctrlScriptPath.toString(),
                        "-Action", action));
                if (info != null && info.title() != null && !info.title().isEmpty()) {
                    cmd.add("-TargetTitle"); cmd.add(info.title());
                }
                if (info != null && info.source() != null && !info.source().isEmpty()) {
                    cmd.add("-TargetSource"); cmd.add(info.source());
                }
                new ProcessBuilder(cmd).redirectErrorStream(true).start()
                        .waitFor(MediaUtil.PROCESS_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                TrashAddons.logger.debug("[Media] control error: {}", e.getMessage());
            }
        }, "trashaddons-media-ctrl").start();
    }
}
