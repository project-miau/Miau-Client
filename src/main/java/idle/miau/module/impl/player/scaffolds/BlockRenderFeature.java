package idle.miau.module.impl.player.scaffolds;

import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.module.impl.player.Scaffold;
import idle.miau.module.impl.render.ArrayListModule;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class BlockRenderFeature {
    private static final String[] MODES = {"Default", "HUD", "None"};
    private static final int NONE_MODE = 2;
    private static final long HIGHLIGHT_DURATION_MS = 750L;

    private final Scaffold scaffold;
    public final SliderSetting mode = new SliderSetting(
            "Block-Render", 1, MODES, "ESP");
    public final ButtonSetting raytrace = new ButtonSetting("Raytrace", false);
    public final SliderSetting alpha = new SliderSetting("ESP alpha", 200, 0, 255, 1);
    public final ButtonSetting outline = new ButtonSetting("ESP outline", true);
    public final ButtonSetting shade = new ButtonSetting("ESP shade", false);

    private final Map<BlockPos, Long> highlights = new HashMap<BlockPos, Long>();
    private MovingObjectPosition lastRaytrace;

    public BlockRenderFeature(Scaffold scaffold) {
        this.scaffold = scaffold;
        scaffold.registerSetting(mode);
        scaffold.registerSetting(raytrace);
        scaffold.registerSetting(alpha);
        scaffold.registerSetting(outline);
        scaffold.registerSetting(shade);
    }

    public void guiUpdate(boolean modeVisible) {
        boolean enabled = modeVisible && (int) mode.getInput() != NONE_MODE;
        mode.setVisible(modeVisible, scaffold);
        raytrace.setVisible(enabled, scaffold);
        outline.setVisible(enabled, scaffold);
        shade.setVisible(enabled, scaffold);
        alpha.setVisible(enabled && raytrace.isToggled(), scaffold);
    }

    public void clear() {
        highlights.clear();
        lastRaytrace = null;
    }

    public void markPlaced(BlockPos position) {
        if (position != null && (int) mode.getInput() != NONE_MODE && !raytrace.isToggled()) {
            highlights.put(position, System.currentTimeMillis());
        }
    }

    public void render(RenderWorldLastEvent event) {
        if ((int) mode.getInput() == NONE_MODE) {
            return;
        }

        int themeColor = (int) mode.getInput() == 1
                ? ArrayListModule.getHudColor(0.0D)
                : Color.CYAN.getRGB();
        renderPlacedHighlights(themeColor);
        if (raytrace.isToggled()) {
            renderRaytrace(themeColor);
        }
    }

    private void renderPlacedHighlights(int themeColor) {
        Iterator<Map.Entry<BlockPos, Long>> iterator = highlights.entrySet().iterator();
        long now = System.currentTimeMillis();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            long age = now - entry.getValue();
            int currentAlpha = fadingAlpha(age);
            if (currentAlpha <= 0) {
                iterator.remove();
                continue;
            }
            if (!raytrace.isToggled()) {
                RenderUtils.renderBlock(
                        entry.getKey(),
                        withAlpha(themeColor, currentAlpha),
                        outline.isToggled(),
                        shade.isToggled());
            }
        }
    }

    private void renderRaytrace(int themeColor) {
        MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
            hit = lastRaytrace;
        } else {
            lastRaytrace = hit;
        }
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            RenderUtils.renderBlock(
                    hit.getBlockPos(),
                    withAlpha(themeColor, (int) alpha.getInput()),
                    outline.isToggled(),
                    shade.isToggled());
        }
    }

    static int fadingAlpha(long age) {
        if (age < 0L || age > HIGHLIGHT_DURATION_MS) {
            return 0;
        }
        return (int) (210.0D - age / (double) HIGHLIGHT_DURATION_MS * 210.0D);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0xFFFFFF) | (alpha << 24);
    }
}
