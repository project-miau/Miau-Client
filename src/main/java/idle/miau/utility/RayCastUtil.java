package idle.miau.utility;

import com.google.common.base.Predicates;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.List;

public final class RayCastUtil implements IMinecraftInstance {
    private RayCastUtil() {
    }

    public static Vec3 getVectorForRotation(float pitch, float yaw) {
        float yawCos = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float yawSin = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float pitchCos = -MathHelper.cos(-pitch * 0.017453292F);
        float pitchSin = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(yawSin * pitchCos, pitchSin, yawCos * pitchCos);
    }

    public static MovingObjectPosition rayCast(float yaw, float pitch, double range, float expand) {
        return rayCast(yaw, pitch, range, expand, mc.thePlayer, false);
    }

    public static MovingObjectPosition rayCast(
            float yaw,
            float pitch,
            double range,
            float expand,
            Entity entity,
            boolean throughWalls) {
        if (entity == null || mc.theWorld == null) {
            return null;
        }

        Vec3 eyes = entity.getPositionEyes(1.0F);
        Vec3 look = getVectorForRotation(pitch, yaw);
        Vec3 reachVector = eyes.addVector(
                look.xCoord * range,
                look.yCoord * range,
                look.zCoord * range);

        MovingObjectPosition blockHit = throughWalls
                ? null
                : mc.theWorld.rayTraceBlocks(eyes, reachVector, false, false, true);
        double closestDistance = range;
        if (blockHit != null && blockHit.hitVec != null) {
            closestDistance = blockHit.hitVec.distanceTo(eyes);
        }

        Entity entityHit = null;
        Vec3 hitVector = null;
        List<Entity> candidates = mc.theWorld.getEntitiesInAABBexcluding(
                entity,
                entity.getEntityBoundingBox()
                        .addCoord(look.xCoord * range, look.yCoord * range, look.zCoord * range)
                        .expand(1.0D, 1.0D, 1.0D),
                Predicates.and(EntitySelectors.NOT_SPECTATING, Entity::canBeCollidedWith));

        for (Entity candidate : candidates) {
            float border = candidate.getCollisionBorderSize() + expand;
            AxisAlignedBB box = candidate.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eyes, reachVector);
            if (box.isVecInside(eyes)) {
                if (closestDistance >= 0.0D) {
                    entityHit = candidate;
                    hitVector = intercept == null ? eyes : intercept.hitVec;
                    closestDistance = 0.0D;
                }
            } else if (intercept != null) {
                double distance = eyes.distanceTo(intercept.hitVec);
                if (distance < closestDistance && closestDistance != 0.0D) {
                    entityHit = candidate;
                    hitVector = intercept.hitVec;
                    closestDistance = distance;
                }
            }
        }

        if (entityHit != null) {
            return new MovingObjectPosition(entityHit, hitVector == null ? eyes : hitVector);
        }
        return blockHit;
    }
}
