package idle.miau.module.impl.player;

import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.accessor.IAccessorMinecraft;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.Utils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

public class FastPlace extends Module {
    public final SliderSetting delay;
    public final ButtonSetting blocksOnly;
    public final ButtonSetting showBlockCount;
    private boolean active;
    private long transitionAt;
    private String countText;

    public FastPlace() {
        super("FastPlace", category.player);
        setDescription("Reduces the vanilla four-tick placement delay.");
        registerSetting(delay = new SliderSetting("Delay", " ticks", 1, 1, 4, 1, "Tick delay"));
        registerSetting(blocksOnly = new ButtonSetting("Blocks only", true, "Filter.Blocks only"));
        registerSetting(showBlockCount = new ButtonSetting("Show block count", true));
        closetModule = true;
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck() || mc.currentScreen != null || !mc.inGameHasFocus) {
            setActive(false);
            return;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        boolean blocks = held != null && held.stackSize > 0 && held.getItem() instanceof ItemBlock
                && BlockUtils.isNormalBlock(((ItemBlock) held.getItem()).getBlock())
                && ((ItemBlock) held.getItem()).getBlock() != net.minecraft.init.Blocks.pumpkin;
        if (!blocks && blocksOnly.isToggled()) {
            setActive(false);
            return;
        }
        if (blocks && showBlockCount.isToggled()) {
            int count = held.stackSize;
            String color = count <= 5 ? "\u00a7c" : count <= 15 ? "\u00a76" : count <= 25 ? "\u00a7e" : "";
            countText = color + count + "\u00a7f blocks";
        } else {
            countText = null;
        }
        setActive(true);
        IAccessorMinecraft accessor = (IAccessorMinecraft) mc;
        int ticks = (int) delay.getInput();
        if (ticks == 1 || accessor.getRightClickDelayTimer() == 4) {
            accessor.setRightClickDelayTimer(ticks);
        }
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        if (event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
            if (packet.getPlacedBlockDirection() == 255 && packet.getStack() != null
                    && packet.getStack().getItem() instanceof ItemBlock && Math.random() > 0.4D) {
                event.setCanceled(true);
            }
        }
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck() || mc.currentScreen != null
                || !showBlockCount.isToggled() || countText == null
                || ModuleManager.scaffold != null && ModuleManager.scaffold.isEnabled()) {
            return;
        }
        float progress = Math.min(1.0F, (System.currentTimeMillis() - transitionAt) / 125.0F);
        int alpha = (int) (240.0F * (active ? progress : 1.0F - progress));
        if (alpha <= 0) {
            if (!active) {
                countText = null;
            }
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.fontRendererObj.drawString(countText, resolution.getScaledWidth() / 2.0F + 11.0F,
                resolution.getScaledHeight() / 2.0F - 3.0F, alpha << 24 | 0xFFFFFF, true);
        GlStateManager.disableBlend();
    }

    @Override
    public void onDisable() {
        active = false;
        transitionAt = 0L;
        countText = null;
    }

    private void setActive(boolean value) {
        if (active != value) {
            active = value;
            transitionAt = System.currentTimeMillis();
        }
    }
}
