package idle.miau.module.impl.render;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.util.ResourceLocation;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.*;
import idle.miau.utility.Utils;
import idle.miau.utility.font.*;
import idle.miau.utility.shader.RoundedUtils;
import idle.miau.utility.spotify.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.*;

public class SpotifyMod extends Module {
    private enum ProviderMode {
        AUTO,
        SPOTIFY,
        LAST_FM;

        private static ProviderMode fromIndex(int index) {
            ProviderMode[] modes = values();
            return modes[Math.max(0, Math.min(modes.length - 1, index))];
        }
    }

    private enum PlaybackAction {
        PAUSE,
        RESUME,
        NEXT,
        PREVIOUS
    }
    private final SliderSetting providerMode = new SliderSetting("Provider", 0, new String[]{"Auto", "Spotify", "Last.fm"});
    private final TextSetting lastFmUser = new TextSetting("Last.fm user", "", "Username", 64);
    private final TextSetting lastFmKey = new TextSetting("Last.fm API key", "", "API key", 128).setSensitive(true);
    private final TextSetting spotifyClientId = new TextSetting("Spotify client ID", "", "Client ID", 128);

    private final SliderSetting posX = new SliderSetting("Position X", 0.02, 0, 1, .01);
    private final SliderSetting posY = new SliderSetting("Position Y", .25, 0, 1, .01);
    private final SliderSetting volume = new SliderSetting("Volume", 50, 0, 100, 1);
    private final ButtonSetting showLyrics = new ButtonSetting("Show lyrics", true);

    private final ButtonSetting connect = new ButtonSetting("Connect", this::connect);
    private final ButtonSetting reconnect = new ButtonSetting("Reconnect", this::reconnect);
    private final ButtonSetting disconnect = new ButtonSetting("Disconnect", this::disconnect);
    private final ButtonSetting clearAuthorization = new ButtonSetting("Clear authorization", this::clearAuthorization);
    private final ButtonSetting clearCredentials = new ButtonSetting("Clear credentials", this::clearCredentials);
    private final ButtonSetting pause = new ButtonSetting("Pause", () -> runPlaybackAction(PlaybackAction.PAUSE));
    private final ButtonSetting resume = new ButtonSetting("Resume", () -> runPlaybackAction(PlaybackAction.RESUME));
    private final ButtonSetting next = new ButtonSetting("Next", () -> runPlaybackAction(PlaybackAction.NEXT));
    private final ButtonSetting previous = new ButtonSetting("Previous", () -> runPlaybackAction(PlaybackAction.PREVIOUS));
    private final ButtonSetting openTrack = new ButtonSetting("Open track", this::openTrack);

    private final MusicCredentialsStore store = new MusicCredentialsStore();
    private final LyricsService lyricsService = new LyricsService();
    private final LinkedHashMap<String, ResourceLocation> albumCache = new LinkedHashMap<String, ResourceLocation>(16, .75f, true);

    private MusicProvider provider;
    private SpotifyAPI spotify;
    private String requestedTrack = "";
    private ResourceLocation albumTexture;
    private ResourceLocation previousTexture;
    private long albumGeneration;
    private long transitionAt;
    private volatile boolean albumReady;
    private boolean dragging;
    private double startedX;
    private double startedY;
    private double draggedX;
    private double draggedY;

    public SpotifyMod() {
        super("Spotify", category.render);
        setDescription("Displays music playback.");
        registerSetting(providerMode);
        registerSetting(lastFmUser);
        registerSetting(lastFmKey);
        registerSetting(spotifyClientId);
        registerSetting(posX);
        registerSetting(posY);
        registerSetting(showLyrics);
        registerSetting(volume);
        registerSetting(connect);
        registerSetting(reconnect);
        registerSetting(disconnect);
        registerSetting(clearAuthorization);
        registerSetting(clearCredentials);
        registerSetting(pause);
        registerSetting(resume);
        registerSetting(next);
        registerSetting(previous);
        registerSetting(openTrack);
        store.load();
        lastFmUser.setText(store.get("lastfm_user"));
        lastFmKey.setText(store.get("lastfm_key"));
        spotifyClientId.setText(store.get("spotify_client_id"));
    }

    @Override
    public void guiUpdate() {
        ProviderMode mode = getProviderMode();
        boolean needsLastFm = mode == ProviderMode.AUTO || mode == ProviderMode.LAST_FM;
        boolean needsSpotify = mode == ProviderMode.AUTO || mode == ProviderMode.SPOTIFY;

        lastFmUser.setVisible(needsLastFm, this);
        lastFmKey.setVisible(needsLastFm, this);
        spotifyClientId.setVisible(needsSpotify, this);
        boolean spotifyOnly = mode == ProviderMode.SPOTIFY;
        volume.setVisible(spotifyOnly, this);
        connect.setVisible(spotifyOnly, this);
        reconnect.setVisible(spotifyOnly, this);
        disconnect.setVisible(spotifyOnly, this);
        clearAuthorization.setVisible(spotifyOnly, this);
        clearCredentials.setVisible(spotifyOnly, this);
        pause.setVisible(spotifyOnly, this);
        resume.setVisible(spotifyOnly, this);
        next.setVisible(spotifyOnly, this);
        previous.setVisible(spotifyOnly, this);
        openTrack.setVisible(spotifyOnly, this);
    }

    private ProviderMode getProviderMode() {
        return ProviderMode.fromIndex((int) providerMode.getInput());
    }

    @Override
    public void onEnable() {
        if (!Utils.nullCheck()) {
            disable();
            return;
        }
        persistCredentials();
        startProvider();
    }

    private void startProvider() {
        stopProvider();

        ProviderMode mode = getProviderMode();
        String clientId = spotifyClientId.getText().trim();
        String username = lastFmUser.getText().trim();
        String apiKey = lastFmKey.getText().trim();

        if (!validateCredentials(mode, clientId, username, apiKey)) {
            disable();
            return;
        }

        spotify = clientId.isEmpty() ? null : new SpotifyAPI(clientId, store);
        provider = createProvider(mode, username, apiKey);
        provider.start();
    }

    private boolean validateCredentials(ProviderMode mode, String clientId, String username, String apiKey) {
        if (mode == ProviderMode.SPOTIFY && clientId.isEmpty()) {
            Utils.sendMessage("&cSpotify HUD: enter your Spotify client ID.");
            return false;
        }

        if (mode == ProviderMode.LAST_FM && (username.isEmpty() || apiKey.isEmpty())) {
            Utils.sendMessage("&cSpotify HUD: enter Last.fm credentials.");
            return false;
        }

        if (mode == ProviderMode.AUTO
                && (clientId.isEmpty() || username.isEmpty() || apiKey.isEmpty())) {
            Utils.sendMessage("&cAuto provider needs Spotify Client ID and Last.fm credentials.");
            return false;
        }

        return true;
    }

    private MusicProvider createProvider(ProviderMode mode, String username, String apiKey) {
        switch (mode) {
            case AUTO:
                return new AutoMusicProvider(spotify, new LastFmAPI(username, apiKey));
            case SPOTIFY:
                return spotify;
            case LAST_FM:
            default:
                return new LastFmAPI(username, apiKey);
        }
    }

    @Override
    public void onDisable() {
        stopProvider();
        releaseTextures();
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        persistCredentials();
        if (setting == volume) {
            SpotifyAPI api = activeSpotify();
            if (api != null) api.volume((int) volume.getInput());
            return;
        }
        if (isEnabled()
                && (setting == providerMode
                        || setting == lastFmUser
                        || setting == lastFmKey
                        || setting == spotifyClientId)) startProvider();
    }

    public void shutdown() {
        stopProvider();
        releaseTextures();
        lyricsService.clear();
    }

    private synchronized void stopProvider() {
        if (provider != null) provider.stop();
        provider = null;
        spotify = null;
        albumReady = false;
        requestedTrack = "";
        albumGeneration++;
        lyricsService.clear();
    }

    private void persistCredentials() {
        store.put("lastfm_user", lastFmUser.getText().trim());
        store.put("lastfm_key", lastFmKey.getText().trim());
        store.put("spotify_client_id", spotifyClientId.getText().trim());
        try {
            store.save();
        } catch (IOException ignored) {
            Utils.sendMessage("&cCould not save music credentials.");
        }
    }

    private SpotifyAPI activeSpotify() {
        return getProviderMode() == ProviderMode.SPOTIFY ? spotify : null;
    }

    private void connect() {
        SpotifyAPI api = activeSpotify();
        if (api != null) api.connect();
        else Utils.sendMessage("&cSpotify provider is not active.");
    }

    private void reconnect() {
        SpotifyAPI api = activeSpotify();
        if (api != null) api.reconnect();
    }

    private void disconnect() {
        SpotifyAPI api = activeSpotify();
        if (api != null) api.disconnect();
    }

    private void clearAuthorization() {
        SpotifyAPI api = activeSpotify();
        if (api != null) api.clearAuthorization();
        else
            try {
                store.clearSpotifyTokens();
            } catch (IOException ignored) {
            }
    }

    private void clearCredentials() {
        try {
            store.clearAll();
            lastFmUser.setText("");
            lastFmKey.setText("");
            spotifyClientId.setText("");
            stopProvider();
            Utils.sendMessage("&aMusic credentials cleared.");
        } catch (IOException ignored) {
            Utils.sendMessage("&cCould not clear credentials.");
        }
    }

    private void runPlaybackAction(PlaybackAction action) {
        SpotifyAPI api = activeSpotify();
        if (api == null) {
            Utils.sendMessage("&cSpotify controls are unavailable.");
            return;
        }

        switch (action) {
            case PAUSE:
                api.pause();
                break;
            case RESUME:
                api.resume();
                break;
            case NEXT:
                api.next();
                break;
            case PREVIOUS:
                api.previous();
                break;
        }
    }

    private void openTrack() {
        MusicTrack t = provider == null ? MusicTrack.STOPPED : provider.getSnapshot();
        try {
            if (!t.getExternalUrl().isEmpty() && Desktop.isDesktopSupported())
                Desktop.getDesktop().browse(new URI(t.getExternalUrl()));
        } catch (Exception ignored) {
            Utils.sendMessage("&cCould not open the current track.");
        }
    }

    @Subscribe
    public void onRender(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Utils.nullCheck() || mc.gameSettings.showDebugInfo)
            return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;
        MusicProvider p = provider;
        if (p == null) return;
        MusicTrack t = p.getSnapshot();
        if (!t.isPlaying() && !(mc.currentScreen instanceof GuiChat)) return;
        render(t);
    }

    private void render(MusicTrack track) {
        ScaledResolution resolution = new ScaledResolution(mc);
        int screenWidth = Math.max(1, resolution.getScaledWidth());
        int screenHeight = Math.max(1, resolution.getScaledHeight());
        float width = 235.0F * HudStyle.scale();
        float height = 145.0F * HudStyle.scale();
        float x = clampToScreen((float) (posX.getInput() * screenWidth), screenWidth - width);
        float y = clampToScreen((float) (posY.getInput() * screenHeight), screenHeight - height);

        drag(resolution, screenWidth, screenHeight, x, y, width, height);
        x = clampToScreen((float) (posX.getInput() * screenWidth), screenWidth - width);
        y = clampToScreen((float) (posY.getInput() * screenHeight), screenHeight - height);

        ensureAlbum(track);
        if (showLyrics.isToggled()) {
            lyricsService.request(track);
        }

        renderLyricsCard(x, y, width, height, track);
    }

    private static float progressRatio(MusicTrack track) {
        long duration = track.getDurationMs();
        if (duration <= 0) {
            return 0;
        }

        long progress = track.getDisplayProgressMs(System.currentTimeMillis());
        return Math.max(0, Math.min(1, progress / (float) duration));
    }

    private static float clampToScreen(float value, float maximum) {
        return Math.max(0, Math.min(Math.max(0, maximum), value));
    }

    private void drag(ScaledResolution sr, int sw, int sh, float x, float y, float w, float h) {
        if (!(mc.currentScreen instanceof GuiChat)) return;
        int mx = Mouse.getX() * sw / mc.displayWidth,
                my = sh - Mouse.getY() * sh / mc.displayHeight - 1;
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        double f = 1D / sr.getScaleFactor();
        if (hover && !dragging && Mouse.isButtonDown(0)) {
            dragging = true;
            startedX = posX.getInput();
            startedY = posY.getInput();
            draggedX = Mouse.getX() * f;
            draggedY = (mc.displayHeight - Mouse.getY()) * f;
        }
        if (dragging) {
            if (!Mouse.isButtonDown(0)) dragging = false;
            else {
                posX.setValue(clamp(startedX + (Mouse.getX() * f - draggedX) / sw));
                posY.setValue(
                        clamp(startedY + ((mc.displayHeight - Mouse.getY()) * f - draggedY) / sh));
            }
        }
    }

    private void renderLyricsCard(float x, float y, float w, float h, MusicTrack t) {
        HudStyle.panel(x, y, w, h, () -> drawLyricsText(x, y, w, t));
        float scale = HudStyle.scale();
        drawAlbum(x + 14 * scale, y + 14 * scale, 52 * scale);
        drawProgress(x + 76 * scale, y + 56 * scale, w - 91 * scale, t);
    }

    private void drawLyricsText(float x, float y, float w, MusicTrack t) {
        float scale = HudStyle.scale();
        MiauFontRenderer title = HudStyle.font(), body = HudStyle.font(.75f), lyric = HudStyle.font(.82f);
        title.drawString(marquee(t.getTrackName(), 25, 45), x + 76 * scale, y + 15 * scale, -1, true);
        body.drawString(marquee(t.getArtistName(), 29, 40), x + 76 * scale, y + 30 * scale, 0xFFD9E8F1, true);
        long progress = t.getDisplayProgressMs(System.currentTimeMillis());
        body.drawString(
                formatTime(progress) + " / " + formatTime(t.getDurationMs()),
                x + w - 58 * scale,
                y + 45 * scale,
                0xFFB9D1DD,
                true);
        body.drawString("Lyrics", x + 14 * scale, y + 78 * scale, 0xFFB7D2DF, true);
        LyricsService.Snapshot lyrics = lyricsService.getSnapshot();
        if (!showLyrics.isToggled()) {
            body.drawString("Lyrics are disabled", x + 14 * scale, y + 94 * scale, 0xFFB7C6CE, true);
            return;
        }
        if (lyrics.isLoading()) {
            body.drawString("Finding lyrics...", x + 14 * scale, y + 94 * scale, 0xFFDCEAF1, true);
            return;
        }
        if (lyrics.isInstrumental()) {
            lyric.drawString("Instrumental", x + 14 * scale, y + 94 * scale, -1, true);
            return;
        }
        java.util.List<LyricsService.Line> lines = lyrics.getLines();
        if (lines.isEmpty()) {
            body.drawString("No lyrics found", x + 14 * scale, y + 94 * scale, 0xFFB7C6CE, true);
            return;
        }
        int active = lyrics.activeIndex(progress);
        if (active < 0) active = 0;
        for (int row = -1; row <= 1; row++) {
            int index = Math.max(0, Math.min(lines.size() - 1, active + row));
            String text = ellipsize(lines.get(index).getText(), 44);
            int color = row == 0 ? 0xFFFFFFFF : row < 0 ? 0xFFB9CBD4 : 0xFF91A9B5;
            (row == 0 ? lyric : body)
                    .drawString(text, x + 14 * scale, y + (99 + (row + 1) * 14) * scale, color, true);
        }
    }

    private void drawProgress(float x, float y, float width, MusicTrack track) {
        float progress = progressRatio(track);
        RoundedUtils.drawRound(x, y, width, 3, 1.5f, new Color(255, 255, 255, 45));
        drawReactiveBar(x, y, width, 3, progress);
    }

    private void drawReactiveBar(float x, float y, float width, float height, float progress) {
        float filledWidth = Math.max(0, width * Math.max(0, Math.min(1, progress)));
        if (filledWidth <= 0) {
            return;
        }

        Color left = arrayListColor(x);
        Color right = arrayListColor(x + filledWidth);
        RoundedUtils.drawGradientHorizontal(x, y, filledWidth, height, height / 2, left, right);
    }

    private static Color arrayListColor(double offset) {
        return new Color(ArrayListModule.getHudColor(offset), true);
    }

    private void drawAlbum(float x, float y, float size) {
        if (!albumReady || albumTexture == null) {
            RoundedUtils.drawRound(
                    x,
                    y,
                    size,
                    size,
                    Math.min(9, HudStyle.radius()),
                    new Color(HudStyle.accent(x, y), true));
            return;
        }
        mc.getTextureManager().bindTexture(albumTexture);
        GlStateManager.color(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        RoundedUtils.drawRoundTextured(x, y, size, size, Math.min(9, HudStyle.radius()), 1);
    }

    private void ensureAlbum(MusicTrack t) {
        String id = t.getTrackId();
        if (id.isEmpty() || id.equals(requestedTrack)) return;
        requestedTrack = id;
        long generation = ++albumGeneration;
        ResourceLocation cached = albumCache.get(id);
        if (cached != null) {
            previousTexture = albumTexture;
            albumTexture = cached;
            albumReady = true;
            transitionAt = System.currentTimeMillis();
            return;
        }
        albumReady = false;
        String url = t.getAlbumUrl();
        if (url.isEmpty()) return;
        ResourceLocation next =
                new ResourceLocation("miau", "music/" + Integer.toHexString(id.hashCode()));
        ThreadDownloadImageData image =
                new ThreadDownloadImageData(
                        null,
                        url,
                        null,
                        new IImageBuffer() {
                            public BufferedImage parseUserSkin(BufferedImage source) {
                                if (generation == albumGeneration) {
                                    albumReady = true;
                                }
                                return source;
                            }

                            public void skinAvailable() {}
                        });
        mc.getTextureManager().loadTexture(next, image);
        previousTexture = albumTexture;
        albumTexture = next;
        albumCache.put(id, next);
        trimCache();
        transitionAt = System.currentTimeMillis();
    }

    private void trimCache() {
        while (albumCache.size() > 12) {
            Iterator<Map.Entry<String, ResourceLocation>> it = albumCache.entrySet().iterator();
            Map.Entry<String, ResourceLocation> e = it.next();
            it.remove();
            if (!e.getValue().equals(albumTexture))
                mc.getTextureManager().deleteTexture(e.getValue());
        }
    }

    private void releaseTextures() {
        for (ResourceLocation r : albumCache.values()) mc.getTextureManager().deleteTexture(r);
        albumCache.clear();
        albumTexture = null;
        previousTexture = null;
    }

    private static String marquee(String s, int max, int speed) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        String loop = s + "   ";
        int start = (int) ((System.currentTimeMillis() / Math.max(1, speed)) % loop.length());
        String doubled = loop + loop;
        return doubled.substring(start, Math.min(start + max, doubled.length()));
    }

    private static String ellipsize(String s, int max) {
        return s == null
                ? ""
                : s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "\u2026";
    }

    private static String formatTime(long ms) {
        long seconds = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
