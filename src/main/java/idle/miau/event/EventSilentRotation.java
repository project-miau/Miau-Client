package idle.miau.event;

import idle.miau.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.List;

public final class EventSilentRotation {
    private final float initialYaw;
    private final float initialPitch;
    private float yaw;
    private float pitch;
    private float yawSpeed;
    private float pitchSpeed;
    private boolean preventDuplicateLook;
    private boolean blockUserInput;
    private float maxYawDelta = Float.POSITIVE_INFINITY;

    public EventSilentRotation(float yaw, float pitch, float speed) {
        this(yaw, pitch, speed, speed);
    }

    public EventSilentRotation(float yaw, float pitch, float yawSpeed, float pitchSpeed) {
        initialYaw = yaw;
        initialPitch = pitch;
        this.yaw = yaw;
        this.pitch = pitch;
        this.yawSpeed = yawSpeed;
        this.pitchSpeed = pitchSpeed;
    }

    public boolean hasBeenModified() {
        return initialYaw != yaw || initialPitch != pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getSpeed() {
        return Math.max(yawSpeed, pitchSpeed);
    }

    public void setSpeed(float speed) {
        yawSpeed = speed;
        pitchSpeed = speed;
    }

    public float getYawSpeed() {
        return yawSpeed;
    }

    public void setYawSpeed(float yawSpeed) {
        this.yawSpeed = yawSpeed;
    }

    public float getPitchSpeed() {
        return pitchSpeed;
    }

    public void setPitchSpeed(float pitchSpeed) {
        this.pitchSpeed = pitchSpeed;
    }

    public boolean isPreventDuplicateLook() {
        return preventDuplicateLook;
    }

    public void setPreventDuplicateLook(boolean preventDuplicateLook) {
        this.preventDuplicateLook = preventDuplicateLook;
    }

    public boolean isBlockUserInput() {
        return blockUserInput;
    }

    public void setBlockUserInput(boolean blockUserInput) {
        this.blockUserInput = blockUserInput;
    }

    public void setMaxYawDelta(float maxYawDelta) {
        this.maxYawDelta = Float.isNaN(maxYawDelta)
                ? Float.POSITIVE_INFINITY
                : Math.max(0.0F, maxYawDelta);
    }

    public float getMaxYawDelta() {
        return maxYawDelta;
    }

    public static final class Post {
        private static final Minecraft MC = Minecraft.getMinecraft();
        private final float yaw;
        private final float pitch;
        private final float previousYaw;
        private final float previousPitch;
        private final boolean modified;
        private final float yawSpeed;
        private final float pitchSpeed;

        public Post(float yaw, float pitch, float previousYaw, float previousPitch,
                    boolean modified, float speed) {
            this(yaw, pitch, previousYaw, previousPitch, modified, speed, speed);
        }

        public Post(float yaw, float pitch, float previousYaw, float previousPitch,
                    boolean modified, float yawSpeed, float pitchSpeed) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.previousYaw = previousYaw;
            this.previousPitch = previousPitch;
            this.modified = modified;
            this.yawSpeed = yawSpeed;
            this.pitchSpeed = pitchSpeed;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public float getPreviousYaw() {
            return previousYaw;
        }

        public float getPreviousPitch() {
            return previousPitch;
        }

        public boolean isModified() {
            return modified;
        }

        public float getSpeed() {
            return Math.max(yawSpeed, pitchSpeed);
        }

        public float getYawSpeed() {
            return yawSpeed;
        }

        public float getPitchSpeed() {
            return pitchSpeed;
        }

        public MovingObjectPosition getRayTrace() {
            Vec3 start = MC.thePlayer.getPositionEyes(1.0F);
            Vec3 look = Utils.getLookVec(yaw, pitch);
            Vec3 end = start.addVector(look.xCoord * 4.5D, look.yCoord * 4.5D, look.zCoord * 4.5D);
            return MC.thePlayer.worldObj.rayTraceBlocks(start, end, false, false, true);
        }

        public MovingObjectPosition getRayTraceEntity() {
            Vec3 start = MC.thePlayer.getPositionEyes(1.0F);
            Vec3 look = Utils.getLookVec(yaw, pitch);
            Vec3 end = start.addVector(look.xCoord * 4.5D, look.yCoord * 4.5D, look.zCoord * 4.5D);
            MovingObjectPosition blockHit = MC.thePlayer.worldObj.rayTraceBlocks(
                    start, end, false, false, true);
            double blockDistance = blockHit == null
                    ? Double.MAX_VALUE : start.distanceTo(blockHit.hitVec);
            MovingObjectPosition entityHit = null;
            double entityDistance = Double.MAX_VALUE;
            List<Entity> entities = MC.thePlayer.worldObj.getEntitiesInAABBexcluding(
                    MC.thePlayer,
                    MC.thePlayer.getEntityBoundingBox()
                            .addCoord(look.xCoord * 4.5D, look.yCoord * 4.5D, look.zCoord * 4.5D)
                            .expand(1.0D, 1.0D, 1.0D),
                    entity -> entity.canBeCollidedWith());
            for (Entity entity : entities) {
                float border = entity.getCollisionBorderSize();
                AxisAlignedBB bounds = entity.getEntityBoundingBox().expand(border, border, border);
                MovingObjectPosition hit = bounds.calculateIntercept(start, end);
                if (hit == null) {
                    continue;
                }
                double distance = start.distanceTo(hit.hitVec);
                if (distance < entityDistance) {
                    entityHit = new MovingObjectPosition(entity, hit.hitVec);
                    entityDistance = distance;
                }
            }
            return entityDistance < blockDistance ? entityHit : blockHit;
        }
    }
}
