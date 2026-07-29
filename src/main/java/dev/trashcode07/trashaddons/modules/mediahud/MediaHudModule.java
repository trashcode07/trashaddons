package dev.trashcode07.trashaddons.modules.mediahud;

import dev.trashcode07.trashaddons.media.MediaInfo;
import dev.trashcode07.trashaddons.media.MediaUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.cobalt.module.ModuleCategory;
import org.cobalt.module.type.RenderableModule;
import org.cobalt.ui.component.setting.impl.CheckboxSetting;
import org.cobalt.ui.component.setting.impl.SliderSetting;
import org.cobalt.ui.theme.Theme;
import org.cobalt.util.client.WindowUtils;
import org.cobalt.util.render.SkiaRenderer;
import org.cobalt.util.render.skia.data.SkiaCorner;
import org.cobalt.util.render.skia.data.SkiaFont;
import org.cobalt.util.render.skia.data.SkiaImage;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class MediaHudModule extends RenderableModule {
    public static final MediaHudModule INSTANCE = new MediaHudModule();

    private static final float W = 240f, H = 60f, H_EXPANDED = 104f, RR = 10f;
    private static final float THUMB_SIZE = 42f;
    private static final float BTN_R = 14f, BTN_SIDE = 11f, BTN_OFF = 44f;
    private static final SkiaCorner[] ALL_CORNERS = SkiaCorner.values();

    private SkiaImage iconPlay, iconPause, iconPrev, iconNext;
    private Path cachedThumbPath;
    private SkiaImage cachedThumbSkia;
    private String cachedThumbBase64;

    private final CheckboxSetting showControlsWhenChat = new CheckboxSetting(
            "Show Controls When Chat",
            "Show media controls when chat is open",
            true
    );
    private final SliderSetting alpha = new SliderSetting(
            "Alpha",
            "HUD background opacity",
            176, 0, 255
    );

    private boolean prevMouseDown;
    private MediaHudModule() {
        super("Media HUD", ModuleCategory.VISUAL,20f,20f);
        addSettings(showControlsWhenChat, alpha);
        setEnabled(true);
    }

    @Override public float getWidth()  { return W; }
    @Override public float getHeight() { return h(); }

    @Override public void onDisable() {
        super.onDisable();
        freeIcons();
    }

    private SkiaImage icon(String path) {
        try { return SkiaRenderer.createImage(path); } catch (Exception e) { return null; }
    }

    @Override
    public void renderComponent() {
        MediaInfo media = MediaUtil.getCurrent();
        if (!media.hasContent()) return;

        ensureIcons();

        boolean chatOpen = Minecraft.getInstance().gui.screen() instanceof ChatScreen;
        float h = h(), x = getXPos(), y = getYPos();
        Theme t = getTheme();

        SkiaRenderer.roundedRect(x, y, W, h, RR, alpha(t.getBackgroundSecondary(), alpha.getValue()), ALL_CORNERS);
        SkiaRenderer.roundedOutline(x, y, W, h, 1, RR, alpha(t.getBorder(), 0x75), ALL_CORNERS);

        float thumbX = x + 8f, thumbY = y + 9f;
        SkiaImage thumb = getThumbnailSkiaImage(media.thumbnailBase64());
        if (thumb != null) {
            SkiaRenderer.image(thumb, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE, 8f, null);
        } else {
            SkiaRenderer.roundedRect(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE, 8f,
                    alpha(t.getBackgroundPrimary(), 0x99), ALL_CORNERS);
            SkiaRenderer.roundedOutline(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE, 1, 8f,
                    alpha(t.getBorder(), 0x66), ALL_CORNERS);
        }

        float textX = thumbX + THUMB_SIZE + 10f;
        float availW = W - (textX - x) - 10f;
        SkiaFont bold = SkiaRenderer.getBoldFont();
        SkiaFont regular = SkiaRenderer.getRegularFont();

        drawTitle(media, bold, textX, y + 9f, availW, h, t.getTextPrimary(), 12f);
        drawArtist(media, regular, textX, y + 24f, availW, t.getTextMuted());
        drawProgress(media, regular, textX, y + 40f, y + 47f, availW, t);
        if (chatOpen && showControlsWhenChat.getValue()) drawControls(media, x, y, t);
    }

    private void ensureIcons() {
        if (iconPlay == null)  iconPlay  = icon("/assets/trashaddons/ui/play.svg");
        if (iconPause == null) iconPause = icon("/assets/trashaddons/ui/pause.svg");
        if (iconPrev == null)  iconPrev  = icon("/assets/trashaddons/ui/skip_prev.svg");
        if (iconNext == null)  iconNext  = icon("/assets/trashaddons/ui/skip_next.svg");
    }

    private void freeIcons() {
        if (iconPlay  != null) { SkiaRenderer.deleteImage(iconPlay);  iconPlay  = null; }
        if (iconPause != null) { SkiaRenderer.deleteImage(iconPause); iconPause = null; }
        if (iconPrev  != null) { SkiaRenderer.deleteImage(iconPrev);  iconPrev  = null; }
        if (iconNext  != null) { SkiaRenderer.deleteImage(iconNext);  iconNext  = null; }
    }

    private void drawTitle(MediaInfo m, SkiaFont font, float tx, float ty, float aw, float h, Color c, float size) {
        String title = m.title() != null ? m.title() : "Unknown";
        float tw = SkiaRenderer.textWidth(font, title, size);
        if (tw <= aw) {
            SkiaRenderer.text(font, title, tx, ty, size, c);
            return;
        }
        float overflow = tw - aw;
        float scrollDur = overflow / 35f, pause = 1.2f;
        float total = pause * 2f + scrollDur * 2f;
        float cycle = (float)((System.currentTimeMillis() / 1000.0) % total);
        float sx;
        if (cycle < pause) sx = 0;
        else if (cycle < pause + scrollDur) sx = (cycle - pause) / scrollDur * overflow;
        else if (cycle < pause * 2 + scrollDur) sx = overflow;
        else sx = overflow * (1f - (cycle - pause * 2 - scrollDur) / scrollDur);
        SkiaRenderer.pushScissor(tx, getYPos(), aw, h, 0);
        SkiaRenderer.text(font, title, tx - sx, ty, size, c);
        SkiaRenderer.popScissor();
    }

    private void drawArtist(MediaInfo m, SkiaFont font, float tx, float ty, float aw, Color c) {
        String artist = m.artist() != null ? m.artist() : m.source();
        if (artist == null || artist.isEmpty()) return;
        if (SkiaRenderer.textWidth(font, artist, 10.5f) > aw)
            artist = trunc(font, artist, aw, 10.5f);
        SkiaRenderer.text(font, artist, tx, ty, 10.5f, c);
    }

    private void drawProgress(MediaInfo m, SkiaFont font, float bx, float by, float ty, float aw, Theme t) {
        SkiaRenderer.roundedRect(bx, by, aw, 3.5f, 1.75f, alpha(t.getBackgroundPrimary(), alpha.getValue()), ALL_CORNERS);
        float p = Math.max(0f, Math.min(1f, m.progress()));
        if (p > 0) SkiaRenderer.roundedRect(bx, by, Math.max(4f, aw * p), 3.5f, 1.75f, t.getAccentPrimary(), ALL_CORNERS);
        String time = m.durationMs() > 0
                ? m.positionFormatted() + " / " + m.durationFormatted()
                : m.positionFormatted();
        float tw = SkiaRenderer.textWidth(font, time, 9.5f);
        SkiaRenderer.text(font, time, bx + aw - tw, ty, 9.5f, t.getTextMuted());
    }

    private void drawControls(MediaInfo m, float x, float y, Theme t) {
        float cx = x + W / 2f, cy = y + H + (H_EXPANDED - H) / 2f;

        SkiaRenderer.circle(cx, cy, BTN_R, t.getAccentPrimary());
        SkiaImage main = m.isPlaying() ? iconPause : iconPlay;
        if (main != null) SkiaRenderer.image(main, cx - 9, cy - 9, 18, 18, null, t.getTextPrimary());

        SkiaRenderer.circle(cx - BTN_OFF, cy, BTN_SIDE, alpha(t.getBackgroundSecondary(), 0x99));
        if (iconPrev != null) SkiaRenderer.image(iconPrev, cx - BTN_OFF - 7, cy - 7, 14, 14, null, t.getTextSecondary());

        SkiaRenderer.circle(cx + BTN_OFF, cy, BTN_SIDE, alpha(t.getBackgroundSecondary(), 0x99));
        if (iconNext != null) SkiaRenderer.image(iconNext, cx + BTN_OFF - 7, cy - 7, 14, 14, null, t.getTextSecondary());

        handleClicks(m);
    }

    private SkiaImage getThumbnailSkiaImage(String b64) {
        if (b64 == null || b64.isBlank()) return null;
        if (b64.equals(cachedThumbBase64) && cachedThumbSkia != null) return cachedThumbSkia;
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            if (bytes.length < 100) return null;
            if (cachedThumbPath != null) cachedThumbPath.toFile().delete();
            if (cachedThumbSkia != null) SkiaRenderer.deleteImage(cachedThumbSkia);
            cachedThumbPath = Files.createTempFile("trashaddons_thumb_", ".png");
            Files.write(cachedThumbPath, bytes);
            cachedThumbSkia = SkiaRenderer.createImage(cachedThumbPath.toString());
            cachedThumbBase64 = b64;
            return cachedThumbSkia;
        } catch (Exception e) {
            return null;
        }
    }

    private void handleClicks(MediaInfo info) {
        long wnd = Minecraft.getInstance().getWindow().handle();
        boolean down = GLFW.glfwGetMouseButton(wnd, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !prevMouseDown) {
            double[] rx = new double[1], ry = new double[1];
            GLFW.glfwGetCursorPos(wnd, rx, ry);
            float mx = (float) rx[0], my = (float) ry[0];
            float sy = WindowUtils.getScaleY();
            float s = getScale() * sy;

            float lcx = getXPos() + W / 2f, lcy = getYPos() + H + (H_EXPANDED - H) / 2f;
            float scx = (lcx - getXPos()) * s + getXPos() * WindowUtils.getScaleX();
            float scy = (lcy - getYPos()) * s + getYPos() * sy;
            float sr = BTN_R * s, srs = BTN_SIDE * s, so = BTN_OFF * s;

            if (hit(mx, my, scx, scy, sr))         MediaUtil.togglePlayPause(info);
            else if (hit(mx, my, scx - so, scy, srs)) MediaUtil.skipPrevious(info);
            else if (hit(mx, my, scx + so, scy, srs)) MediaUtil.skipNext(info);
        }
        prevMouseDown = down;
    }

    private static boolean hit(float px, float py, float cx, float cy, float r) {
        float dx = px - cx, dy = py - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private float h() {
        return (Minecraft.getInstance().gui.screen() instanceof ChatScreen && showControlsWhenChat.getValue()) ? H_EXPANDED : H;
    }

    private static String trunc(SkiaFont f, String s, float maxW, float size) {
        if (SkiaRenderer.textWidth(f, s, size) <= maxW) return s;
        String t = s;
        while (t.length() > 3 && SkiaRenderer.textWidth(f, t + "...", size) > maxW)
            t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    private static Color alpha(Color c, int a) {
        if (c == null) return new Color(0, 0, 0, a);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
