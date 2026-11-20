package idle.miau.module.impl.player;

import idle.miau.module.Module;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

public class NoRotate extends Module {
    private float prevPitch = 0f;
    private float prevYaw = 0f;

    public NoRotate() {
        super("NoRotate", category.player);
        setDescription("Keeps server corrections from changing your view.");
    }

    public void handlePlayerPosLookPre() {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.thePlayer.rotationPitch == 0) {
            return;
        }
        prevPitch = mc.thePlayer.rotationPitch % 360;
        prevYaw = mc.thePlayer.rotationYaw % 360;
    }

    public void handlePlayerPosLook(net.minecraft.network.Packet<?> packet) {
        if (!this.isEnabled()) {
            return;
        }
        if (!(packet instanceof S08PacketPlayerPosLook) || mc.thePlayer == null) {
            return;
        }
        S08PacketPlayerPosLook posLook = (S08PacketPlayerPosLook) packet;
        if (posLook.getPitch() == 0) {
            return;
        }
        mc.thePlayer.prevRotationYaw = prevYaw;
        mc.thePlayer.prevRotationPitch = prevPitch;
        mc.thePlayer.rotationPitch = prevPitch;
        mc.thePlayer.rotationYaw = prevYaw;
    }
}
