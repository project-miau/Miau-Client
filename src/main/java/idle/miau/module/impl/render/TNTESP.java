package idle.miau.module.impl.render;

import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ExplosionDamage;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import idle.miau.utility.render.ExplosionRenderer;
import idle.miau.utility.render.HudScale;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TNTESP extends Module {
    private static final class TrackedTnt {
        private final EntityTNTPrimed entity;
        private final float damage;
        private final String label;

        private TrackedTnt(EntityTNTPrimed entity, float damage) {
            this.entity = entity;
            this.damage = damage;
            this.label = String.format(Locale.ROOT, "Damage: %.1f", damage);
        }
    }

    private final ButtonSetting blastRadius;
    private final ButtonSetting showDamage;
    private final ButtonSetting throughWalls;
    private final SliderSetting explosionPower;
    private final SliderSetting opacity;
    private final SliderSetting range;
    private final SliderSetting scale;
    private final ColorSetting blastColor;
    private final List<TrackedTnt> trackedTnt = new ArrayList<>();

    public TNTESP() {
        super("TNT ESP", category.render);
        setDescription("Shows TNT blast areas and estimated damage to you in health points.");
        registerSetting(blastRadius = new ButtonSetting("Blast radius", true));
        registerSetting(showDamage = new ButtonSetting("Damage", true));
        registerSetting(throughWalls = new ButtonSetting("Through walls", true));
        registerSetting(explosionPower = new SliderSetting("Explosion power", 4.0, 1.0, 8.0, 0.5));
        registerSetting(opacity = new SliderSetting("Opacity", 15.0, 0.0, 50.0, 1.0));
        registerSetting(range = new SliderSetting("Range", 64.0, 8.0, 128.0, 4.0));
        registerSetting(scale = new SliderSetting("Scale", 1.0, 0.5, 3.0, 0.05));
        registerSetting(blastColor = new ColorSetting("Blast color", 255, 70, 70));
    }

    @Override
    public void onEnable() {
        updateTrackedTnt();
    }

    @Override
    public void onDisable() {
        trackedTnt.clear();
    }

    @Subscribe
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            updateTrackedTnt();
        }
    }

    private void updateTrackedTnt() {
        trackedTnt.clear();
        if (!Utils.nullCheck()) {
            return;
        }
        double maxDistanceSq = range.getInput() * range.getInput();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityTNTPrimed) || entity.isDead
                    || entity.getDistanceSqToEntity(mc.thePlayer) > maxDistanceSq) {
                continue;
            }
            EntityTNTPrimed tnt = (EntityTNTPrimed) entity;
            if (tnt.fuse <= 0) {
                continue;
            }
            float damage = showDamage.isToggled()
                    ? ExplosionDamage.estimate(tnt, (float) explosionPower.getInput(), mc.thePlayer)
                    : 0.0F;
            trackedTnt.add(new TrackedTnt(tnt, damage));
        }
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        RenderManager rm = mc.getRenderManager();
        float fillAlpha = (float) opacity.getInput() / 100.0F;
        double power = explosionPower.getInput();
        for (TrackedTnt tracked : trackedTnt) {
            EntityTNTPrimed tnt = tracked.entity;
            if (tnt.isDead || tnt.fuse <= 0 || tnt.worldObj != mc.theWorld) {
                continue;
            }
            double x = tnt.lastTickPosX + (tnt.posX - tnt.lastTickPosX) * event.partialTicks - rm.viewerPosX;
            double y = tnt.lastTickPosY + (tnt.posY - tnt.lastTickPosY) * event.partialTicks - rm.viewerPosY;
            double z = tnt.lastTickPosZ + (tnt.posZ - tnt.lastTickPosZ) * event.partialTicks - rm.viewerPosZ;
            Vec3 center = new Vec3(x, y + tnt.height / 16.0F, z);
            if (blastRadius.isToggled()) {
                ExplosionRenderer.renderSphere(center, power * 2.0D, 0xFFFFFF,
                        fillAlpha * 0.1F, 0.12F, throughWalls.isToggled());
                ExplosionRenderer.renderSphere(center, power, blastColor.getColor(),
                        fillAlpha, 0.35F, throughWalls.isToggled());
            }
            renderTntBox(tnt, x, y, z, fillAlpha);
            if (showDamage.isToggled()) {
                renderDamageLabel(tracked, x, y + tnt.height + 0.35D, z, rm);
            }
        }
    }

    private void renderTntBox(EntityTNTPrimed tnt, double x, double y, double z, float fillAlpha) {
        double halfWidth = tnt.width * 0.5D;
        AxisAlignedBB box = new AxisAlignedBB(x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + tnt.height, z + halfWidth).expand(0.002D, 0.002D, 0.002D);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            if (throughWalls.isToggled()) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.5F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.9F);
            RenderUtils.drawOutlinedBox(box, 0.0D, 0.0D, 0.0D);
            RenderUtils.drawBoundingBox(box, 1.0F, 1.0F, 1.0F, fillAlpha);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void renderDamageLabel(TrackedTnt tracked, double x, double y, double z, RenderManager rm) {
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(rm.playerViewX * (mc.gameSettings.thirdPersonView == 2 ? -1 : 1),
                    1.0F, 0.0F, 0.0F);
            float renderScale = 0.02666667F * HudScale.toRenderScale(scale.getInput());
            GlStateManager.scale(-renderScale, -renderScale, renderScale);
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            if (throughWalls.isToggled()) {
                GlStateManager.disableDepth();
            }
            GlStateManager.depthMask(false);
            int color = tracked.damage >= mc.thePlayer.getHealth() ? 0xFFFF4040
                    : tracked.damage > 0.0F ? 0xFFFFAA55 : 0xFF80FF80;
            FontRenderer font = mc.fontRendererObj;
            font.drawStringWithShadow(tracked.label, -font.getStringWidth(tracked.label) * 0.5F, 0.0F, color);
        } finally {
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.depthMask(depthMask);
            GlStateManager.tryBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            GlStateManager.resetColor();
        }
    }
}
