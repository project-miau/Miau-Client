package idle.miau.module.impl.player;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;

public class PearlMacro extends Module {
    private final KeySetting keybind;
    private final ButtonSetting switchBack;
    private final SliderSetting switchDelay;
    private final SliderSetting cooldown;

    private boolean wasPressed = false;
    private long lastThrowTime = 0L;
    private int restoreSlot = -1;
    private int pearlSlot = -1;
    private long restoreAt = 0L;

    public PearlMacro() {
        super("PearlMacro", category.player);
        setDescription("Switches to an ender pearl and throws it.");
        this.registerSetting(keybind = new KeySetting("Keybind", 0));
        this.registerSetting(switchBack = new ButtonSetting("Switch back", true));
        this.registerSetting(switchDelay = new SliderSetting("Switch delay", "ms", 0, 0, 300, 10));
        this.registerSetting(cooldown = new SliderSetting("Cooldown", "ms", 250, 50, 1000, 25));
        this.closetModule = true;
    }

    public KeySetting getKeybind() {
        return keybind;
    }

    public ButtonSetting getSwitchBack() {
        return switchBack;
    }

    public SliderSetting getSwitchDelay() {
        return switchDelay;
    }

    public SliderSetting getCooldown() {
        return cooldown;
    }

    @Override
    public void onDisable() {
        wasPressed = false;
        restoreSlot = -1;
        pearlSlot = -1;
        restoreAt = 0L;
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            wasPressed = false;
            return;
        }

        long now = System.currentTimeMillis();

        if (restoreSlot != -1 && now >= restoreAt) {
            if (pearlSlot != -1 && mc.thePlayer.inventory.currentItem == pearlSlot) {
                Utils.switchSlot(restoreSlot, true);
            }
            restoreSlot = -1;
            pearlSlot = -1;
        }

        if (mc.currentScreen != null) {
            wasPressed = false;
            return;
        }

        boolean isDown = keybind.isPressed();
        if (isDown) {
            if (!wasPressed) {
                wasPressed = true;
                if (now - lastThrowTime >= (long) cooldown.getInput()) {
                    throwPearl(now);
                }
            }
        } else {
            wasPressed = false;
        }
    }

    public boolean throwPearl(long now) {
        if (!Utils.nullCheck()) {
            return false;
        }

        int slot = findPearlSlot();
        if (slot == -1) {
            return false;
        }

        int prevSlot = mc.thePlayer.inventory.currentItem;

        if (RotationUtils.serverRotations[0] != mc.thePlayer.rotationYaw
                || RotationUtils.serverRotations[1] != mc.thePlayer.rotationPitch) {
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(
                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.thePlayer.onGround));
            }
            RotationUtils.serverRotations[0] = mc.thePlayer.rotationYaw;
            RotationUtils.serverRotations[1] = mc.thePlayer.rotationPitch;
        }

        if (prevSlot != slot) {
            Utils.switchSlot(slot, true);
        }

        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        if (stack != null && mc.playerController != null) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
            mc.thePlayer.swingItem();
        }

        lastThrowTime = now;

        if (switchBack.isToggled() && prevSlot != slot) {
            long delay = (long) switchDelay.getInput();
            if (delay <= 0) {
                Utils.switchSlot(prevSlot, true);
            } else {
                restoreSlot = prevSlot;
                pearlSlot = slot;
                restoreAt = now + delay;
            }
        }

        return true;
    }

    public int findPearlSlot() {
        if (mc.thePlayer == null || mc.thePlayer.inventory == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == Items.ender_pearl) {
                return i;
            }
        }
        return -1;
    }
}
