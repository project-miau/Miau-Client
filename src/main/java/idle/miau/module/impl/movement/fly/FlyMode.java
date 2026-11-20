package idle.miau.module.impl.movement.fly;

import idle.miau.event.CollisionEvent;
import idle.miau.event.DispatchPacketEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import net.minecraft.client.Minecraft;

public abstract class FlyMode {
    protected final FlyModule owner;
    protected final Minecraft mc = Minecraft.getMinecraft();
    private final String name;

    protected FlyMode(String name, FlyModule owner) {
        this.name = name;
        this.owner = owner;
    }

    public final String getName() {
        return name;
    }

    public boolean usesSpeedSettings() {
        return false;
    }

    public boolean usesStopMotionSetting() {
        return true;
    }

    public boolean usesMaxFlyTicksSetting() {
        return false;
    }

    public void updateVisibility(boolean active) {
    }

    public void onButtonToggled(ButtonSetting setting) {
        updateVisibility(owner.isMode(name));
    }

    public void onSettingChanged(Setting setting) {
        updateVisibility(owner.isMode(name));
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onUpdate() {
    }

    public void onPreUpdate(PreUpdateEvent event) {
    }

    public void onPreMotion(PreMotionEvent event) {
    }

    public void onReceivePacket(ReceivePacketEvent event) {
    }

    public void onSendPacket(SendPacketEvent event) {
    }

    public void onDispatchPacket(DispatchPacketEvent event) {
    }

    public void onCollision(CollisionEvent event) {
    }

    public void onRenderWorld(RenderWorldLastEvent event) {
    }

    public void onWorldUnload(WorldEvent.Unload event) {
    }

    protected final void setSpeed(double speed) {
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        if (speed == 0.0D) {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
            return;
        }
        double moveForward = mc.thePlayer.movementInput.moveForward;
        double moveStrafe = mc.thePlayer.movementInput.moveStrafe;
        float yaw = mc.thePlayer.rotationYaw;
        if (moveForward == 0.0D && moveStrafe == 0.0D) {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
            return;
        }
        if (moveForward != 0.0D) {
            if (moveStrafe > 0.0D) {
                yaw += moveForward > 0.0D ? -45.0F : 45.0F;
            } else if (moveStrafe < 0.0D) {
                yaw += moveForward > 0.0D ? 45.0F : -45.0F;
            }
            moveStrafe = 0.0D;
            moveForward = moveForward > 0.0D ? 1.0D : -1.0D;
        }
        double radians = Math.toRadians(yaw + 90.0F);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        mc.thePlayer.motionX = moveForward * speed * cos + moveStrafe * speed * sin;
        mc.thePlayer.motionZ = moveForward * speed * sin - moveStrafe * speed * cos;
    }
}
