package idle.miau.module.impl.movement.fly.modes;

import idle.miau.event.ReceivePacketEvent;
import idle.miau.module.impl.movement.fly.FlyMode;
import idle.miau.module.impl.movement.fly.FlyModule;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S27PacketExplosion;

public final class IntaveFlagFly extends FlyMode {
    private boolean boosting = false;
    private int boostTicks = 0;

    public IntaveFlagFly(FlyModule owner) {
        super("IntaveFlagFly", owner);
    }

    @Override
    public boolean usesMaxFlyTicksSetting() {
        return true;
    }

    @Override
    public void onEnable() {
        boosting = false;
        boostTicks = 0;
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof S27PacketExplosion) {
            boosting = true;
            boostTicks = 0;
        }
    }

    @Override
    public void onUpdate() {
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        if (player == null) {
            return;
        }

        if (boosting) {
            double boostValue = 2.2D + Math.random() * 0.25D;
            double yawRad = Math.toRadians(player.rotationYaw);
            player.motionX = -Math.sin(yawRad) * boostValue + ((Math.random() - 0.5D) * 0.07D);
            player.motionZ = Math.cos(yawRad) * boostValue + ((Math.random() - 0.5D) * 0.07D);
            player.motionY = 0.42D + (Math.random() - 0.5D) * 0.08D;
            player.fallDistance = 0.0F;
            boostTicks++;
            if (boostTicks >= owner.getMaxFlyTicksValue()) {
                boosting = false;
                player.motionX = 0.0D;
                player.motionZ = 0.0D;
            }
            return;
        }
    }

    @Override
    public void onDisable() {
        boosting = false;
        boostTicks = 0;
    }
}
