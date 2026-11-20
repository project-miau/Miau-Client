package idle.miau.utility;

import net.minecraft.util.AxisAlignedBB;

public final class PredictedMotion {
    private final double motionX;
    private final double motionY;
    private final double motionZ;
    private final boolean onGround;
    private final boolean collidedHorizontally;
    private final boolean collidedVertically;
    private final boolean collided;
    private final boolean inWater;
    private final boolean inLava;
    private final AxisAlignedBB boundingBox;

    public PredictedMotion(
            double motionX,
            double motionY,
            double motionZ,
            boolean onGround,
            boolean collidedHorizontally,
            boolean collidedVertically,
            boolean collided,
            boolean inWater,
            boolean inLava,
            AxisAlignedBB boundingBox
    ) {
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.onGround = onGround;
        this.collidedHorizontally = collidedHorizontally;
        this.collidedVertically = collidedVertically;
        this.collided = collided;
        this.inWater = inWater;
        this.inLava = inLava;
        this.boundingBox = boundingBox;
    }

    public double getMotionX() {
        return motionX;
    }

    public double getMotionY() {
        return motionY;
    }

    public double getMotionZ() {
        return motionZ;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public boolean isCollidedHorizontally() {
        return collidedHorizontally;
    }

    public boolean isCollidedVertically() {
        return collidedVertically;
    }

    public boolean isCollided() {
        return collided;
    }

    public boolean isInWater() {
        return inWater;
    }

    public boolean isInLava() {
        return inLava;
    }

    public AxisAlignedBB getBoundingBox() {
        return boundingBox;
    }

    public double getCenterX() {
        return (boundingBox.maxX + boundingBox.minX) * 0.5D;
    }

    public double getMinY() {
        return boundingBox.minY;
    }

    public double getCenterZ() {
        return (boundingBox.maxZ + boundingBox.minZ) * 0.5D;
    }
}
