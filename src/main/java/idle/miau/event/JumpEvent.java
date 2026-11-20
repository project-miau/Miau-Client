package idle.miau.event;

import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;
import net.minecraft.entity.EntityLivingBase;

@Cancelable
public class JumpEvent extends Event {
    public EntityLivingBase entity;
    private float motionY, yaw;
    private boolean applySprint;

    public JumpEvent(EntityLivingBase entity, float motionY, float yaw, boolean applySprint) {
        this.entity = entity;
        this.motionY = motionY;
        this.yaw = yaw;
        this.applySprint = applySprint;
    }

    public float getMotionY() {
        return motionY;
    }

    public void setMotionY(float motionY) {
        this.motionY = motionY;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public boolean applySprint() {
        return applySprint;
    }

    public void setSprint(boolean applySprint) {
        this.applySprint = applySprint;
    }
}
