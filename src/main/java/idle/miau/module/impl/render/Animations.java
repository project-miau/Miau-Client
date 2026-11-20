package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemCarrotOnAStick;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

public class Animations extends Module {
    public static SliderSetting mode;
    public static SliderSetting itemScale;
    public static SliderSetting xOffset;
    public static SliderSetting yOffset;
    public static SliderSetting zOffset;
    public static SliderSetting swingSpeed;

    private static final String[] MODES = {
        "1.7B", "1.7", "Stella", "Exhi", "Shred"
    };

    public Animations() {
        super("Animations", category.render);
        this.registerSetting(mode = new SliderSetting("Mode", 1, MODES));
        this.registerSetting(itemScale = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.05));
        this.registerSetting(xOffset = new SliderSetting("X Offset", 0.0, -1.0, 1.0, 0.05));
        this.registerSetting(yOffset = new SliderSetting("Y Offset", 0.0, -1.0, 1.0, 0.05));
        this.registerSetting(zOffset = new SliderSetting("Z Offset", 0.0, -1.0, 1.0, 0.05));
        this.registerSetting(swingSpeed = new SliderSetting("Swing Speed", 1.0, -200.0, 50.0, 1.0));
    }

    public static boolean isEnabledStatic() {
        idle.miau.module.Module m = idle.miau.module.ModuleManager.getModule(Animations.class);
        return m != null && m.isEnabled();
    }

    public static int getSwingAnimationEnd(net.minecraft.entity.EntityLivingBase entity, int original) {
        idle.miau.module.Module m = idle.miau.module.ModuleManager.getModule(Animations.class);
        if (m != null && m.isEnabled() && entity == Minecraft.getMinecraft().thePlayer) {
            float speedVal = (float) swingSpeed.getInput();
            float multiplier = (-speedVal / 100.0F) + 1.0F;
            return Math.max(1, (int) (original * multiplier));
        }
        return original;
    }

    public static boolean transform(float equipProgress, float swingProgress) {
        if (!isEnabledStatic()) return false;

        int animMode = (int) mode.getInput();

        GlStateManager.translate((float) xOffset.getInput(), (float) yOffset.getInput(), (float) zOffset.getInput());

        float swingSin = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float swingRootSin = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        float halfEquip = equipProgress * 0.5F;
        float sneakingOffset = Minecraft.getMinecraft().thePlayer.isSneaking() ? -0.2F : 0.0F;

        switch (animMode) {
            case 0:
                transformItem(equipProgress, swingProgress);
                doBlockTransformations();
                break;
            case 1:
                applyFirstPersonItemTweaks(swingProgress);
                transformItem(equipProgress, swingProgress);
                doBlockTransformations();
                performBlockhit();
                break;
            case 2:
                transformItem(-0.1F, swingProgress);
                GlStateManager.translate(-0.5F, 0.4F, -0.2F);
                GlStateManager.rotate(32.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(-70.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(40.0F, 0.0F, 1.0F, 0.0F);
                break;
            case 3:
                transformItem(halfEquip, 0.0F);
                GlStateManager.rotate(-swingRootSin * 20.0F, swingRootSin * 0.5F, 0.0F, 9.0F);
                GlStateManager.rotate(-swingRootSin * 30.0F, 1.0F, swingRootSin * 0.5F, 0.0F);
                doBlockTransformations();
                GlStateManager.translate(-0.05F, sneakingOffset, 0.1F);
                break;
            case 4:
                transformItem(halfEquip, swingProgress);
                GlStateManager.rotate(swingSin * 15.0F, -swingSin, 0.0F, 9.0F);
                GlStateManager.rotate(swingSin * 40.0F, 1.0F, -swingSin * 0.5F, 0.0F);
                doBlockTransformations();
                GlStateManager.translate(-0.05F, sneakingOffset, 0.1F);
                break;
            default:
                transformItem(equipProgress, swingProgress);
                doBlockTransformations();
                break;
        }
        return true;
    }

    private static void transformItem(float equipProgress, float swingProgress) {
        transformFirstPersonItem(equipProgress, swingProgress);
        float scale = (float) itemScale.getInput();
        GlStateManager.scale(scale, scale, scale);
    }

    private static void applyFirstPersonItemTweaks(float swingProgress) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;

        ItemStack heldItem = player.getHeldItem();
        if (heldItem != null) {
            if (heldItem.getItem() instanceof ItemCarrotOnAStick
                    || heldItem.getItem() instanceof ItemFishingRod) {
                GlStateManager.translate(0.08f, -0.027f, -0.33f);
                GlStateManager.scale(0.93f, 1.0f, 1.0f);
            }
            if (heldItem.getItem() instanceof ItemBow) {
                GlStateManager.translate(-0.01f, 0.05f, -0.06f);
            }
        }

        if (swingProgress != 0.0f && !player.isPlayerSleeping() && !player.isRiding() && !player.isBlocking()) {
            GlStateManager.scale(0.85f, 0.85f, 0.85f);
            GlStateManager.translate(-0.06f, 0.003f, 0.05f);
        }
    }

    private static void performBlockhit() {
        GlStateManager.scale(0.83f, 0.88f, 0.85f);
        GlStateManager.translate(-0.3f, 0.1f, 0.0f);
    }

    private static void transformFirstPersonItem(float equipProgress, float swingProgress) {
        GlStateManager.translate(0.56f, -0.52f, -0.72f);
        GlStateManager.translate(0f, equipProgress * -0.6f, 0f);
        GlStateManager.rotate(45f, 0f, 1f, 0f);
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        GlStateManager.rotate(f * -20f, 0f, 1f, 0f);
        GlStateManager.rotate(f1 * -20f, 0f, 0f, 1f);
        GlStateManager.rotate(f1 * -80f, 1f, 0f, 0f);
        GlStateManager.scale(0.4f, 0.4f, 0.4f);
    }

    private static void doBlockTransformations() {
        GlStateManager.translate(-0.5f, 0.2f, 0f);
        GlStateManager.rotate(30f, 0f, 1f, 0f);
        GlStateManager.rotate(-80f, 1f, 0f, 0f);
        GlStateManager.rotate(60f, 0f, 1f, 0f);
    }
}
