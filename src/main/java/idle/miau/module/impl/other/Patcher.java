package idle.miau.module.impl.other;

import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.patcher.culling.EntityCulling;

public class Patcher extends Module {
    public static ButtonSetting entityCulling;
    public static SliderSetting cullingInterval;
    public static ButtonSetting particleCulling;
    public static ButtonSetting compactChat;
    public static ButtonSetting extendedChatLength;
    public static ButtonSetting noCloseMyChat;
    public static ButtonSetting cleanProjectiles;
    public static ButtonSetting staticItems;
    public static ButtonSetting unstackedItems;
    public static ButtonSetting cropHitbox;
    public static ButtonSetting playerVoidRendering;
    public static ButtonSetting smoothScrolling;
    public static SliderSetting containerOpacity;
    public static SliderSetting containerBackgroundOpacity;

    public Patcher() {
        super("Patcher", category.other);
        setDescription("Adds rendering optimizations and quality-of-life fixes.");
        this.registerSetting(new DescriptionSetting("PolyPatcher optimizations & QoL"));

        this.registerSetting(new DescriptionSetting("Performance"));
        this.registerSetting(entityCulling = new ButtonSetting("Entity Culling", true));
        this.registerSetting(cullingInterval = new SliderSetting("Cull Interval", "ms", 10.0, 5.0, 100.0, 5.0));
        this.registerSetting(particleCulling = new ButtonSetting("Particle Culling", true));
        this.registerSetting(staticItems = new ButtonSetting("Static Items", false));
        this.registerSetting(unstackedItems = new ButtonSetting("Unstacked Items", false));

        this.registerSetting(new DescriptionSetting("QoL & Visuals"));
        this.registerSetting(compactChat = new ButtonSetting("Compact Chat", true));
        this.registerSetting(extendedChatLength = new ButtonSetting("Extended Chat (256)", true));
        this.registerSetting(noCloseMyChat = new ButtonSetting("No Close My Chat", true));
        this.registerSetting(cleanProjectiles = new ButtonSetting("Clean Projectiles", true));
        this.registerSetting(cropHitbox = new ButtonSetting("1.12 Crop Hitbox", true));
        this.registerSetting(playerVoidRendering = new ButtonSetting("Void Skybox Fix", true));
        this.registerSetting(smoothScrolling = new ButtonSetting("Smooth Scrolling", true));

        this.registerSetting(new DescriptionSetting("GUI Opacity"));
        this.registerSetting(containerOpacity = new SliderSetting("Container Opacity", 1.0, 0.0, 1.0, 0.05));
        this.registerSetting(containerBackgroundOpacity = new SliderSetting("Container BG Opacity", 0.65, 0.0, 1.0, 0.05));
    }

    @Subscribe
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            EntityCulling.onClientTick();
        }
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            EntityCulling.onRenderTick();
        }
    }

    public static boolean isEntityCullingActive() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && entityCulling != null && entityCulling.isToggled();
    }

    public static long getCullInterval() {
        return cullingInterval != null ? (long) cullingInterval.getInput() : 10L;
    }

    public static boolean isParticleCullingActive() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && particleCulling != null && particleCulling.isToggled();
    }

    public static boolean isCompactChatActive() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && compactChat != null && compactChat.isToggled();
    }

    public static boolean isExtendedChatLengthActive() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && extendedChatLength != null && extendedChatLength.isToggled();
    }

    public static boolean isNoCloseMyChatActive() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && noCloseMyChat != null && noCloseMyChat.isToggled();
    }

    public static boolean isCleanProjectilesActive() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && cleanProjectiles != null && cleanProjectiles.isToggled();
    }

    public static boolean isStaticItemsActive() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && staticItems != null && staticItems.isToggled();
    }

    public static boolean isUnstackedItemsActive() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && unstackedItems != null && unstackedItems.isToggled();
    }

    public static boolean isCropHitboxActive() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && cropHitbox != null && cropHitbox.isToggled();
    }

    public static boolean isPlayerVoidRenderingFixEnabled() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && playerVoidRendering != null && playerVoidRendering.isToggled();
    }

    public static boolean isSmoothScrollingActive() {
        return ModuleManager.patcher != null && ModuleManager.patcher.isEnabled() && smoothScrolling != null && smoothScrolling.isToggled();
    }

    public static float getContainerOpacity() {
        return containerOpacity != null ? (float) containerOpacity.getInput() : 1.0f;
    }

    public static float getContainerBackgroundOpacity() {
        return containerBackgroundOpacity != null ? (float) containerBackgroundOpacity.getInput() : 0.65f;
    }
}
