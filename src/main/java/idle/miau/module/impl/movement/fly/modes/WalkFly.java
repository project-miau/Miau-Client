package idle.miau.module.impl.movement.fly.modes;

import idle.miau.event.CollisionEvent;
import idle.miau.module.impl.movement.fly.FlyMode;
import idle.miau.module.impl.movement.fly.FlyModule;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.utility.Utils;
import net.minecraft.util.AxisAlignedBB;

public final class WalkFly extends FlyMode {
    private static final AxisAlignedBB FULL_AABB = new AxisAlignedBB(
            0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    public final ButtonSetting keepY = new ButtonSetting("Keep-Y", false);

    private boolean wasFlying;
    private int maxY;

    public WalkFly(FlyModule owner) {
        super("Walk", owner);
    }

    @Override
    public void updateVisibility(boolean active) {
        keepY.setVisible(active, owner);
    }

    @Override
    public void onEnable() {
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        wasFlying = mc.thePlayer.capabilities.isFlying;
        maxY = mc.thePlayer.getPosition().getY();
    }

    @Override
    public void onCollision(CollisionEvent event) {
        if (mc == null || mc.thePlayer == null || mc.gameSettings == null) {
            return;
        }
        if (Utils.isBindDown(mc.gameSettings.keyBindSneak)) {
            maxY = mc.thePlayer.getPosition().getY();
            return;
        }
        if (event.blockPos.getY() < (keepY.isToggled() ? maxY : mc.thePlayer.posY)) {
            event.boundingBox = FULL_AABB.offset(
                    event.blockPos.getX(), event.blockPos.getY(), event.blockPos.getZ());
        }
    }

    @Override
    public void onDisable() {
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.capabilities.isFlying = mc.thePlayer.capabilities.allowFlying && wasFlying;
        wasFlying = false;
    }
}
