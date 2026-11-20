package idle.miau.module.impl.combat.velocity;

import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ClientContext;
import idle.miau.utility.WeaponPolicy;
import idle.miau.utility.inventory.InventoryActions;
import idle.miau.utility.packet.TimingAccess;
import idle.miau.utility.rotation.MiauRandom;

import net.minecraft.potion.Potion;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class JumpReset extends VelocityMode {
    private final SliderSetting chance;
    private final ButtonSetting requireTarget;
    private final ButtonSetting requireMouse;
    private final ButtonSetting requireForward;
    private final ButtonSetting requireWeapon;
    private final ButtonSetting requireSprinting;
    private final MiauRandom.Smooth probability = new MiauRandom.Smooth();
    private boolean jumpOwned;

    public JumpReset(VelocityModule owner) {
        super("JumpReset", owner);
        register(chance = new SliderSetting("Chance", "%", 100, 0, 100, 1));
        register(requireTarget = new ButtonSetting("Looking at player", false));
        register(requireMouse = new ButtonSetting("Mouse pressed", false));
        register(requireForward = new ButtonSetting("Moving forward", false));
        register(requireWeapon = new ButtonSetting("Holding weapon", false));
        register(requireSprinting = new ButtonSetting("Sprinting", true));
    }

    @Override
    public void updateVisibility() {
        boolean active = owner.isMode("JumpReset");
        for (Setting setting :
                new Setting[] {
                    chance,
                    requireTarget,
                    requireMouse,
                    requireForward,
                    requireWeapon,
                    requireSprinting
                }) {
            setting.setVisible(active, owner);
        }
    }

    void afterEntities() {
        if (!InventoryActions.inputReady() || isSuppressed()) {
            return;
        }
        if (jumpOwned) {
            restoreJump();
        } else if (eligible()
                && chance()
                && canJump(
                        requireSprinting.isToggled(),
                        mc.thePlayer.isSprinting(),
                        mc.thePlayer.onGround,
                        mc.thePlayer.motionY,
                        mc.thePlayer.isPotionActive(Potion.jump),
                        mc.gameSettings.keyBindJump.isKeyDown())) {
            mc.gameSettings.keyBindJump.setPressed(true);
            jumpOwned = true;
        }
    }

    static boolean canJump(
            boolean requireSprint,
            boolean sprinting,
            boolean onGround,
            double motionY,
            boolean jumpBoost,
            boolean jumpPressed) {
        return (!requireSprint || sprinting)
                && onGround
                && !(0.0 >= motionY)
                && !jumpBoost
                && !jumpPressed;
    }

    private boolean eligible() {
        int attackCode = mc.gameSettings.keyBindAttack.getKeyCode();
        return (!requireForward.isToggled() || !(0.0F >= mc.thePlayer.moveForward))
                && (!requireMouse.isToggled() || Mouse.isButtonDown(attackCode == -99 ? 1 : 0))
                && (!requireWeapon.isToggled() || WeaponPolicy.SHARED.isWeapon(mc.thePlayer))
                && (!requireTarget.isToggled() || TimingAccess.facingPlayer(new ClientContext(mc)));
    }

    private boolean chance() {
        float percent = (float) chance.getInput();
        if (percent > 99.0F) {
            return true;
        }
        double fraction = (double) (float) ((double) percent * 0.01);
        return probability.gate(
                (float) (-0.05 + fraction),
                (float) (fraction + 0.05),
                60.0,
                MiauRandom.now(),
                MiauRandom.SHARED);
    }

    private void restoreJump() {
        if (!jumpOwned) {
            return;
        }
        int key = mc.gameSettings.keyBindJump.getKeyCode();
        boolean physical =
                key < 0 ? Mouse.isButtonDown(key + 100) : key > 0 && Keyboard.isKeyDown(key);
        mc.gameSettings.keyBindJump.setPressed(physical);
        jumpOwned = false;
    }

    @Override
    public void onDisable() {
        restoreJump();
    }

    @Override
    public void onWorldUnload(WorldEvent.Unload event) {
        restoreJump();
    }
}
