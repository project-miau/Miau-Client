package idle.miau.module.impl.render;

import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.module.Module;
import idle.miau.module.impl.combat.KillAura;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class Emojify extends Module {
    private static final String[] MODES = {
            "Ibuki", "Bocchi", "Lappland", "Jane Doe", "Hoshino1",
            "Hoshino2", "Miyu", "Reisa1", "Reisa2", "Seia",
            "Nachoneko1", "Nachoneko2", "Nachoneko3", "Nachoneko4", "MaoTuanMaoMao"
    };
    private static final String[] STATIC_TEXTURES = {
            "owo.png", "owo2.png", "owo3.png", "owo4.png", "owo5.png",
            "owo6.png", "owo7.png", "owo8.png", "owo9.png", "owo10.png",
            "owo11.png", "owo12.png", "owo13.png", "owo14.png"
    };
    private static final int ANIMATED_MODE = MODES.length - 1;
    private static final int FRAME_COUNT = 33;
    private static final int FRAME_RATE = 30;
    private static final ResourceLocation[] ICONS = createStaticIcons();
    private static final ResourceLocation[] FRAMES = createAnimatedFrames();

    private final SliderSetting mode;
    private final SliderSetting scale;
    private final SliderSetting heightOffset;
    private final SliderSetting opacity;

    public Emojify() {
        super("Emojify", category.render);
        setDescription("Displays icon above your combat target.");
        this.registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        this.registerSetting(scale = new SliderSetting("Scale", "x", 1.5, 0.1, 5.0, 0.1));
        this.registerSetting(heightOffset = new SliderSetting("Height offset", 0.5, -2.0, 3.0, 0.1));
        this.registerSetting(opacity = new SliderSetting("Opacity", 1.0, 0.1, 1.0, 0.05));
    }

    @Subscribe
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityLivingBase target = KillAura.target;
        if (!Utils.nullCheck() || target == null || target.isDead || !target.isEntityAlive() || target.isInvisible()) {
            return;
        }

        RenderManager renderManager = mc.getRenderManager();
        float partialTicks = event.partialTicks;
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks - renderManager.viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks - renderManager.viewerPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks - renderManager.viewerPosZ;

        renderIcon(renderManager, x, y + target.getEyeHeight() + heightOffset.getInput(), z);
    }

    private void renderIcon(RenderManager renderManager, double x, double y, double z) {
        GL11.glPushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(
                renderManager.playerViewX * (mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F),
                1.0F,
                0.0F,
                0.0F
        );

        float iconScale = 0.025F * (float) scale.getInput();
        GlStateManager.scale(-iconScale, -iconScale, iconScale);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        mc.getTextureManager().bindTexture(selectedTexture());
        float textureWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        float textureHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        float aspectRatio = textureHeight > 0.0F ? textureWidth / textureHeight : 1.0F;
        float halfHeight = 50.0F;
        float halfWidth = halfHeight * aspectRatio;

        GlStateManager.color(1.0F, 1.0F, 1.0F, (float) opacity.getInput());
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-halfWidth, -halfHeight, 0.0D).tex(0.0D, 0.0D).endVertex();
        buffer.pos(-halfWidth, halfHeight, 0.0D).tex(0.0D, 1.0D).endVertex();
        buffer.pos(halfWidth, halfHeight, 0.0D).tex(1.0D, 1.0D).endVertex();
        buffer.pos(halfWidth, -halfHeight, 0.0D).tex(1.0D, 0.0D).endVertex();
        tessellator.draw();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GL11.glPopMatrix();
    }

    private ResourceLocation selectedTexture() {
        int selectedMode = (int) Math.max(0, Math.min(MODES.length - 1, mode.getInput()));
        if (selectedMode != ANIMATED_MODE) {
            return ICONS[selectedMode];
        }
        long frameDuration = 1000L / FRAME_RATE;
        int frame = (int) ((System.currentTimeMillis() / frameDuration) % FRAME_COUNT);
        return FRAMES[frame];
    }

    @Override
    public String getInfo() {
        int selectedMode = (int) Math.max(0, Math.min(MODES.length - 1, mode.getInput()));
        return MODES[selectedMode];
    }

    private static ResourceLocation[] createStaticIcons() {
        ResourceLocation[] icons = new ResourceLocation[STATIC_TEXTURES.length];
        for (int index = 0; index < STATIC_TEXTURES.length; index++) {
            icons[index] = texture(STATIC_TEXTURES[index]);
        }
        return icons;
    }

    private static ResourceLocation[] createAnimatedFrames() {
        ResourceLocation[] frames = new ResourceLocation[FRAME_COUNT];
        for (int index = 0; index < FRAME_COUNT; index++) {
            frames[index] = texture(String.format("owo15/ezgif-frame-%03d.png", index + 1));
        }
        return frames;
    }

    static ResourceLocation texture(String path) {
        return new ResourceLocation("mimeow/targeticon/" + path);
    }
}
