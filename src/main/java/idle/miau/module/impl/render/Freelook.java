package idle.miau.module.impl.render;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;
import idle.miau.module.Module;

public final class Freelook extends Module {
    private float cameraYaw;
    private float cameraPitch;
    private boolean active;

    public Freelook() {
        super("FreeLook", category.render);
        setDescription("Lets you look around without changing player rotation.");
    }

    @Override
    public void onEnable() {
        EntityPlayerSP player = mc.thePlayer;
        if (player != null) {
            cameraYaw = player.rotationYaw;
            cameraPitch = player.rotationPitch;
            active = true;
            if (mc.gameSettings.thirdPersonView == 0) {
                mc.gameSettings.thirdPersonView = 1;
            }
        }
    }

    @Override
    public void onDisable() {
        active = false;
        if (mc.gameSettings.thirdPersonView != 0) {
            mc.gameSettings.thirdPersonView = 0;
        }
    }

    public boolean isFreeLooking() {
        return isEnabled() && active && mc.thePlayer != null;
    }

    public void updateCamera(float yawDelta, float pitchDelta) {
        cameraYaw += yawDelta * 0.15F;
        cameraPitch = MathHelper.clamp_float(
                cameraPitch - pitchDelta * 0.15F, -90.0F, 90.0F);
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }
}
