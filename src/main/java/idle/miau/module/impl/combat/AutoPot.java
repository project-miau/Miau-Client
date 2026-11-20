package idle.miau.module.impl.combat;

import idle.miau.event.AttackEvent;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PreAttackEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.helper.RotationPriority;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.utility.ItemUtils;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.List;

public final class AutoPot extends Module {
    private final KeySetting selectKeybind;
    private final SliderSetting health;
    private final SliderSetting delay;
    private final SliderSetting maxAimTicks;

    private int attackTicks;
    private int groundTicks;
    private long nextThrowAt;
    private boolean aiming;
    private boolean aimReady;
    private int aimSlot = -1;
    private int aimTicks;
    private int previousSlot = -1;
    private int usedSlot = -1;
    private int restoreTicks;

    public AutoPot() {
        super("AutoPot", category.combat);
        setDescription("Throws healing potions.");
        registerSetting(selectKeybind = new KeySetting("Active Keybind", 0));
        registerSetting(health = new SliderSetting("Health", 15.0D, 1.0D, 20.0D, 1.0D));
        registerSetting(delay = new SliderSetting("Delay", 0.0D, 0.0D, 100.0D, 5.0D));
        registerSetting(maxAimTicks = new SliderSetting("Max aim ticks", 10.0D, 2.0D, 30.0D, 1.0D));
    }

    @Override
    public void onEnable() {
        resetState(true);
    }

    @Override
    public void onDisable() {
        resetState(true);
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck()) {
            resetState(false);
            return;
        }

        attackTicks = Math.min(Integer.MAX_VALUE, attackTicks + 1);
        groundTicks = mc.thePlayer.onGround ? Math.min(Integer.MAX_VALUE, groundTicks + 1) : 0;

        if (restoreTicks > 0) {
            restoreTicks--;
            if (restoreTicks == 0) {
                restoreSlot();
            }
            return;
        }

        if (mc.currentScreen != null) {
            attackTicks = 0;
            stopAiming();
            return;
        }

        if (!selectKeybind.isPressed()) {
            stopAiming();
            return;
        }

        if (aiming) {
            aimTicks++;
            if (aimReady) {
                throwPotion();
            }
            else if (aimTicks >= (int) maxAimTicks.getInput()) {
                stopAiming();
            }
            return;
        }

        if (groundTicks <= 1 || System.currentTimeMillis() < nextThrowAt
                || attackTicks < 10 || scaffoldEnabled() || mc.thePlayer.isUsingItem()) {
            return;
        }

        int slot = findPotionSlot();
        if (slot != -1) {
            startAiming(slot);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onClientRotation(ClientRotationEvent event) {
        if (!aiming || !Utils.nullCheck()) {
            return;
        }

        if (!selectKeybind.isPressed()) {
            stopAiming();
            return;
        }

        float yaw = event.yaw != null ? event.yaw : RotationUtils.serverRotations[0];
        float pitch = event.pitch != null ? event.pitch : RotationUtils.serverRotations[1];
        float[] next = RotationUtils.getPatchedAndCappedRots(
                new float[]{yaw, pitch}, new float[]{yaw, 90.0F}, 10.0F);
        if (event.trySetRotation(
                this, RotationPriority.BLOCK_PLACEMENT, next[0], next[1])) {
            event.setRotationAlreadyQuantized(true);
            aimReady = next[1] > 85.0F;
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreAttack(PreAttackEvent event) {
        if (aiming || restoreTicks > 0) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        if (event.attacker == mc.thePlayer) {
            attackTicks = 0;
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        resetState(false);
    }

    private void startAiming(int slot) {
        aiming = true;
        aimReady = false;
        aimSlot = slot;
        aimTicks = 0;
    }

    private void stopAiming() {
        aiming = false;
        aimReady = false;
        aimSlot = -1;
        aimTicks = 0;
    }

    private void throwPotion() {
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(aimSlot);
        if (!isUsablePotion(stack)) {
            stopAiming();
            return;
        }

        previousSlot = mc.thePlayer.inventory.currentItem;
        usedSlot = aimSlot;
        Utils.switchSlot(aimSlot, true);
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held != null && held.getItem() instanceof ItemPotion) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
            nextThrowAt = System.currentTimeMillis() + (long) delay.getInput() * 10L;
            restoreTicks = 1;
        }
        else {
            restoreSlot();
        }
        stopAiming();
    }

    private int findPotionSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (isUsablePotion(mc.thePlayer.inventory.getStackInSlot(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isUsablePotion(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemPotion)
                || !ItemPotion.isSplash(stack.getMetadata())
                || !ItemUtils.isBuffPotion(stack)) {
            return false;
        }

        List<PotionEffect> effects = ((ItemPotion) stack.getItem()).getEffects(stack);
        if (effects == null || effects.isEmpty()) {
            return false;
        }

        PotionEffect effect = effects.get(0);
        int potionId = effect.getPotionID();
        boolean restorative = potionId == Potion.regeneration.id || potionId == Potion.heal.id;
        if (restorative && mc.thePlayer.getHealth() > health.getInput()) {
            return false;
        }

        Potion potion = potionId >= 0 && potionId < Potion.potionTypes.length
                ? Potion.potionTypes[potionId] : null;
        PotionEffect active = potion == null ? null : mc.thePlayer.getActivePotionEffect(potion);
        return active == null || active.getDuration() == 0;
    }

    private boolean scaffoldEnabled() {
        return ModuleManager.scaffold != null && ModuleManager.scaffold.isEnabled();
    }

    private void restoreSlot() {
        if (Utils.nullCheck() && previousSlot >= 0 && previousSlot < 9
                && mc.thePlayer.inventory.currentItem == usedSlot) {
            Utils.switchSlot(previousSlot, true);
        }
        previousSlot = -1;
        usedSlot = -1;
    }

    private void resetState(boolean restore) {
        if (restore) {
            restoreSlot();
        }
        stopAiming();
        attackTicks = 0;
        groundTicks = 0;
        nextThrowAt = 0L;
        restoreTicks = 0;
        previousSlot = -1;
        usedSlot = -1;
    }
}
