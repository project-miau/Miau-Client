package idle.miau.utility.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.optifine.util.FontUtils;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import idle.miau.utility.render.HudScale;

public final class FontManager {
    private static final String MINECRAFT = "Minecraft";
    private static final String FAITHFUL = "Faithful 32x";
    private static final ResourceLocation FAITHFUL_TEXTURE = new ResourceLocation("miau", "fonts/faithful32/ascii.png");
    private static FontRenderer faithfulFont;
    private static final String RESOURCE_ROOT = "/assets/idle.miau/fonts/";

    private static final int MAX_CACHED_RENDERERS = 48;
    private static final float DEFAULT_HUD_FONT_SIZE = 10.0f;
    private static final float DEFAULT_CLICK_GUI_HEADER_HEIGHT = 9.0f;
    private static final float DEFAULT_CLICK_GUI_SETTING_HEIGHT = 9.0f;
    private static final float DEFAULT_NAMETAG_FONT_SIZE = 9.0f;
    static final float HUD_MIN_RASTER_SCALE = 4.0f;
    private static final float NAMETAG_MIN_RASTER_SCALE = 8.0f;
    private static final BundledFont[] BUNDLED_FONTS = {
            new BundledFont("Sf-Bold", "Sf-Bold.ttf"),
            new BundledFont("ProductSans", "product_sans_regular.ttf"),
            new BundledFont("ProductSans-Medium", "product_sans_medium.ttf"),
            new BundledFont("ProductSans-Bold", "product_sans_bold.ttf"),
            new BundledFont("ProductSans-Light", "product_sans_light.ttf"),
            new BundledFont("ProductSans-Italic", "product_sans_italic.ttf"),
            new BundledFont("ProductSans-BoldItalic", "product_sans_bold_italic.ttf"),
            new BundledFont("HarmonyOS", "HarmonyOS_Sans_SC_Regular.ttf"),
            new BundledFont("LINESeed-JP", "LINESeedJP_TTF_Rg.ttf"),
            new BundledFont("LINESeed-KR", "LINESeedKR-Rg.ttf")
    };
    private static final BundledFont[] INTERNAL_FONTS = {
            new BundledFont("MetalGothic", "MetalGothic-DemoVersion-Regular.ttf")
    };
    private static final String[] HUD_FONT_OPTIONS = buildHudFontOptions();
    private static final Map<String, BundledFont> BUNDLED_FONT_MAP = buildBundledFontMap();
    private static final Map<String, Font> BASE_FONT_CACHE = new ConcurrentHashMap<String, Font>();
    private static final Map<String, MiauFontRenderer> FONT_CACHE = new LinkedHashMap<String, MiauFontRenderer>(16, 0.75f, true);

    private FontManager() {
    }

    public static String[] getHudFontOptions() {
        return HUD_FONT_OPTIONS.clone();
    }

    public static MiauFontRenderer getHudRenderer(String family, float effectiveScale) {
        float safeScale = Math.max(0.5f, Math.min(4.0f, effectiveScale));
        return getRenderer(
                family, DEFAULT_HUD_FONT_SIZE * safeScale,
                HUD_MIN_RASTER_SCALE);
    }

    public static MiauFontRenderer getUserScaledHudRenderer(String family, float userScale) {
        return getHudRenderer(family, HudScale.toRenderScale(userScale));
    }

    public static MiauFontRenderer getClickGuiHeaderRenderer(String family) {
        return getRendererForPixelHeight(family, DEFAULT_CLICK_GUI_HEADER_HEIGHT);
    }

    public static MiauFontRenderer getClickGuiSettingRenderer(String family) {
        return getRendererForPixelHeight(family, DEFAULT_CLICK_GUI_SETTING_HEIGHT);
    }

    public static MiauFontRenderer getMiauCategoryRenderer() {
        return getRendererForPixelHeight("ProductSans", 9.0f);
    }

    public static MiauFontRenderer getMiauModuleRenderer() {
        return getRendererForPixelHeight("ProductSans", 10.0f);
    }

    public static MiauFontRenderer getMiauSettingRenderer() {
        return getRendererForPixelHeight("ProductSans", 9.0f);
    }

    public static MiauFontRenderer getMainMenuRenderer(float targetHeight) {
        return getRendererForPixelHeight(
                "Sf-Bold", targetHeight, HUD_MIN_RASTER_SCALE);
    }

    public static MiauFontRenderer getRinbounceHotbarRenderer() {
        String key = "RinbounceHotbar#" + getUiScale()
                + "#" + HUD_MIN_RASTER_SCALE;
        return getCachedRenderer(key, new Supplier<MiauFontRenderer>() {
            @Override
            public MiauFontRenderer get() {
                Font font = new Font(Font.DIALOG, Font.PLAIN, 1).deriveFont(8.75F);
                return new GlyphFontRenderer(font, true, HUD_MIN_RASTER_SCALE);
            }
        });
    }

    public static MiauFontRenderer getNametagRenderer(String family) {
        float fontSize = DEFAULT_NAMETAG_FONT_SIZE;
        if (FAITHFUL.equals(family)) {
            return getFaithfulRenderer(fontSize);
        }
        if (family == null || isMinecraftFont(family)) {
            return getMinecraftRenderer(fontSize);
        }

        BundledFont bundledFont = BUNDLED_FONT_MAP.get(family);
        if (bundledFont == null) {
            return getMinecraftRenderer(fontSize);
        }

        String key = "nametag#" + family + "#"
                + quantizeForCacheKey(fontSize) + "#"
                + getUiScale() + "#"
                + NAMETAG_MIN_RASTER_SCALE;
        return getCachedRenderer(key, new Supplier<MiauFontRenderer>() {
            @Override
            public MiauFontRenderer get() {
                Font baseFont = BASE_FONT_CACHE.computeIfAbsent(
                        bundledFont.fileName, FontManager::loadBaseFont);
                if (baseFont == null) {
                    return getMinecraftRenderer(fontSize);
                }
                return new GlyphFontRenderer(
                        baseFont.deriveFont(fontSize), true,
                        NAMETAG_MIN_RASTER_SCALE, fallbackFonts());
            }
        });
    }

    private static MiauFontRenderer getRenderer(
            String family, float fontSize, float minimumRasterScale) {
        float safeFontSize = Math.max(1.0f, fontSize);
        if (FAITHFUL.equals(family)) {
            return getFaithfulRenderer(safeFontSize);
        }
        BundledFont bundledFont;

        if (family == null || isMinecraftFont(family)) {
            return getMinecraftRenderer(safeFontSize);
        }

        bundledFont = BUNDLED_FONT_MAP.get(family);
        if (bundledFont == null) {
            return getMinecraftRenderer(safeFontSize);
        }

        String key = family + "#" + quantizeForCacheKey(safeFontSize)
                + "#" + getUiScale() + "#"
                + quantizeForCacheKey(minimumRasterScale);
        return getCachedRenderer(key, new Supplier<MiauFontRenderer>() {
            @Override
            public MiauFontRenderer get() {
                Font baseFont = BASE_FONT_CACHE.computeIfAbsent(
                        bundledFont.fileName, FontManager::loadBaseFont);
                if (baseFont == null) {
                    return getMinecraftRenderer(safeFontSize);
                }

                return new GlyphFontRenderer(
                        baseFont.deriveFont(safeFontSize), true,
                        minimumRasterScale, fallbackFonts());
            }
        });
    }

    private static MiauFontRenderer getRendererForPixelHeight(String family, float targetHeight) {
        return getRendererForPixelHeight(family, targetHeight, 2.0f);
    }

    private static MiauFontRenderer getRendererForPixelHeight(
            String family, float targetHeight, float minimumRasterScale) {
        float safeTargetHeight = Math.max(1.0f, targetHeight);
        if (FAITHFUL.equals(family)) {
            return getFaithfulRenderer(safeTargetHeight);
        }
        BundledFont bundledFont;

        if (family == null || isMinecraftFont(family)) {
            return getMinecraftRenderer(safeTargetHeight);
        }

        bundledFont = BUNDLED_FONT_MAP.get(family);
        if (bundledFont == null) {
            return getMinecraftRenderer(safeTargetHeight);
        }

        String key = family + "#height#" + quantizeForCacheKey(safeTargetHeight)
                + "#" + getUiScale() + "#"
                + quantizeForCacheKey(minimumRasterScale);
        return getCachedRenderer(key, new Supplier<MiauFontRenderer>() {
            @Override
            public MiauFontRenderer get() {
                Font baseFont = BASE_FONT_CACHE.computeIfAbsent(
                        bundledFont.fileName, FontManager::loadBaseFont);
                if (baseFont == null) {
                    return getMinecraftRenderer(safeTargetHeight);
                }

                return createHeightMatchedRenderer(
                        baseFont, safeTargetHeight, minimumRasterScale);
            }
        });
    }

    private static MiauFontRenderer createHeightMatchedRenderer(
            Font baseFont, float targetHeight, float minimumRasterScale) {
        float derivedSize = targetHeight;
        GlyphFontRenderer renderer = new GlyphFontRenderer(
                baseFont.deriveFont(derivedSize), true, minimumRasterScale, fallbackFonts());

        for (int i = 0; i < 2; i++) {
            float measuredHeight = Math.max(1.0f, renderer.getFontHeight());
            float difference = Math.abs(measuredHeight - targetHeight);
            if (difference <= 0.5f) {
                break;
            }

            GlyphFontRenderer previousRenderer = renderer;
            derivedSize = Math.max(1.0f, derivedSize * (targetHeight / measuredHeight));
            renderer = new GlyphFontRenderer(
                    baseFont.deriveFont(derivedSize), true, minimumRasterScale, fallbackFonts());
            previousRenderer.destroy();
        }

        return renderer;
    }

    private static MiauFontRenderer getMinecraftRenderer(float fontSize) {
        float vanillaHeight = Math.max(1.0f, Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT);
        float scale = Math.max(0.5f, Math.min(2.0f, fontSize / vanillaHeight));
        String key = MINECRAFT + "#" + quantizeForCacheKey(scale);
        return getCachedRenderer(key, new Supplier<MiauFontRenderer>() {
            @Override
            public MiauFontRenderer get() {
                return new MinecraftFontAdapter(Minecraft.getMinecraft().fontRendererObj, scale);
            }
        });
    }

    private static MiauFontRenderer getFaithfulRenderer(float fontSize) {
        float scale = Math.max(0.5f, Math.min(2.0f, fontSize / 9.0f));
        String key = FAITHFUL + "#" + quantizeForCacheKey(scale);
        return getCachedRenderer(key, () -> {
            if (faithfulFont == null) {
                Minecraft minecraft = Minecraft.getMinecraft();
                faithfulFont = new FontRenderer(minecraft.gameSettings, FAITHFUL_TEXTURE,
                        minecraft.getTextureManager(), false, loadFaithfulCharWidths());
                if (minecraft.getResourceManager() instanceof IReloadableResourceManager resources) {
                    resources.registerReloadListener(faithfulFont);
                } else {
                    faithfulFont.onResourceManagerReload(minecraft.getResourceManager());
                }
            }
            return new MinecraftFontAdapter(faithfulFont, scale, true, true);
        });
    }

    static int[] loadFaithfulCharWidths() {
        Properties properties = new Properties();
        try (InputStream resource = FontManager.class.getResourceAsStream(
                "/assets/miau/fonts/faithful32/ascii.properties")) {
            if (resource == null) {
                throw new IllegalStateException("Missing Faithful 32x character widths");
            }
            properties.load(resource);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load Faithful 32x character widths", exception);
        }
        float[] widths = new float[256];
        Arrays.fill(widths, -1);
        FontUtils.readCustomCharWidths(properties, widths);
        int[] result = new int[widths.length];
        for (int index = 0; index < widths.length; index++) {
            result[index] = Math.round(widths[index]);
        }
        return result;
    }

    public static boolean isMinecraftFont(String family) {
        return family == null || MINECRAFT.equalsIgnoreCase(family);
    }

    private static String[] buildHudFontOptions() {
        String[] options = new String[BUNDLED_FONTS.length + 2];
        options[0] = MINECRAFT;

        for (int i = 0; i < BUNDLED_FONTS.length; i++) {
            options[i + 1] = BUNDLED_FONTS[i].displayName;
        }
        options[options.length - 1] = FAITHFUL;

        return options;
    }

    private static Map<String, BundledFont> buildBundledFontMap() {
        LinkedHashMap<String, BundledFont> fontMap = new LinkedHashMap<String, BundledFont>();

        for (BundledFont bundledFont : BUNDLED_FONTS) {
            fontMap.put(bundledFont.displayName, bundledFont);
        }
        for (BundledFont bundledFont : INTERNAL_FONTS) {
            fontMap.put(bundledFont.displayName, bundledFont);
        }

        return fontMap;
    }

    public static int migrateLegacyFontIndex(int index) {
        if (index <= 1) return Math.max(0, index);
        return 2;
    }

    private static Font[] fallbackFonts() {
        java.util.stream.Stream<Font> bundled = java.util.stream.Stream.of(
                        "HarmonyOS_Sans_SC_Regular.ttf", "LINESeedJP_TTF_Rg.ttf", "LINESeedKR-Rg.ttf")
                .map(file -> BASE_FONT_CACHE.computeIfAbsent(file, FontManager::loadBaseFont))
                .filter(java.util.Objects::nonNull);
        return java.util.stream.Stream.concat(bundled,
                java.util.stream.Stream.of(new Font(Font.DIALOG, Font.PLAIN, 1))).toArray(Font[]::new);
    }

    private static Font loadBaseFont(String fileName) {
        byte[] fontData = readFontData(fileName);
        if (fontData == null) {
            return null;
        }

        try {
            return Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(fontData));
        }
        catch (Exception ignored) {
            try {
                return Font.createFont(Font.TYPE1_FONT, new ByteArrayInputStream(fontData));
            }
            catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static int getUiScale() {
        try {
            return Math.max(1, new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor());
        }
        catch (Exception ignored) {
            return 1;
        }
    }

    private static float quantizeForCacheKey(float value) {
        return Math.round(value * 100.0f) / 100.0f;
    }

    private static byte[] readFontData(String fileName) {
        try (InputStream inputStream = FontManager.class.getResourceAsStream(RESOURCE_ROOT + fileName)) {
            if (inputStream == null) {
                return null;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;

            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            return outputStream.toByteArray();
        }
        catch (IOException ignored) {
            return null;
        }
    }

    private static synchronized MiauFontRenderer getCachedRenderer(String key, Supplier<MiauFontRenderer> rendererSupplier) {
        MiauFontRenderer renderer = FONT_CACHE.get(key);
        if (renderer != null) {
            return renderer;
        }

        renderer = rendererSupplier.get();
        FONT_CACHE.put(key, renderer);
        trimFontCache();
        return renderer;
    }

    private static void trimFontCache() {
        while (FONT_CACHE.size() > MAX_CACHED_RENDERERS) {
            Iterator<Map.Entry<String, MiauFontRenderer>> iterator = FONT_CACHE.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }

            Map.Entry<String, MiauFontRenderer> eldestEntry = iterator.next();
            iterator.remove();
            if (eldestEntry.getValue() != null) {
                eldestEntry.getValue().destroy();
            }
        }
    }

    private static final class BundledFont {
        private final String displayName;
        private final String fileName;

        private BundledFont(String displayName, String fileName) {
            this.displayName = displayName;
            this.fileName = fileName;
        }
    }
}
