package idle.miau.utility;

import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public final class RodAim {
    private RodAim() {}

    public static Vec3 point(Entity player, Entity target) {
        Vec3 eyes = player.getPositionEyes(1.0F);
        AxisAlignedBB box = target.getEntityBoundingBox();
        Vec3 center = new Vec3((box.minX + box.maxX) * 0.5,
                box.minY + (box.maxY - box.minY) * 0.55, (box.minZ + box.maxZ) * 0.5);
        double flight = MathHelper.clamp_double(eyes.distanceTo(center) / 1.5, 1.0, 6.0);
        Vec3 velocity = velocity(target);
        return center.addVector(velocity.xCoord * flight,
                velocity.yCoord * flight + 0.02 * flight * flight, velocity.zCoord * flight);
    }

    public static boolean enteringMelee(Entity player, Entity target, double range) {
        Vec3 movement = velocity(target).subtract(velocity(player));
        AxisAlignedBB box = target.getEntityBoundingBox().offset(
                movement.xCoord * 1.5, movement.yCoord * 1.5, movement.zCoord * 1.5);
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 closest = new Vec3(MathHelper.clamp_double(eyes.xCoord, box.minX, box.maxX),
                MathHelper.clamp_double(eyes.yCoord, box.minY, box.maxY),
                MathHelper.clamp_double(eyes.zCoord, box.minZ, box.maxZ));
        return eyes.squareDistanceTo(closest) <= range * range;
    }

    private static Vec3 velocity(Entity entity) {
        double x = entity.posX - entity.prevPosX;
        double y = entity.posY - entity.prevPosY;
        double z = entity.posZ - entity.prevPosZ;
        if (x * x + y * y + z * z > 1.0) {
            x = entity.motionX;
            y = entity.motionY;
            z = entity.motionZ;
        }
        return new Vec3(MathHelper.clamp_double(x, -0.8, 0.8),
                MathHelper.clamp_double(y, -0.5, 0.5), MathHelper.clamp_double(z, -0.8, 0.8));
    }
}
