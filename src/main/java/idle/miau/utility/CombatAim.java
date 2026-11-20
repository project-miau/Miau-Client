package idle.miau.utility;

import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final class CombatAim implements IMinecraftInstance {
    private CombatAim() {
    }

    public static Vec3 point(Entity entity) {
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        if (box.isVecInside(eyes)) {
            return eyes;
        }
        return new Vec3(MathHelper.clamp_double(eyes.xCoord, box.minX + 0.03D, box.maxX - 0.03D),
                MathHelper.clamp_double(eyes.yCoord, box.minY + 0.03D, box.maxY - 0.03D),
                MathHelper.clamp_double(eyes.zCoord, box.minZ + 0.03D, box.maxZ - 0.03D));
    }

    public static Vec3 visiblePoint(Entity entity) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 point = point(entity);
        if (clearLine(eyes, point)) {
            return point;
        }
        AxisAlignedBB box = entity.getEntityBoundingBox();
        for (double height : new double[]{0.9D, 0.65D, 0.4D, 0.15D}) {
            for (double x : new double[]{0.5D, 0.1D, 0.9D}) {
                for (double z : new double[]{0.5D, 0.1D, 0.9D}) {
                    Vec3 candidate = new Vec3(box.minX + (box.maxX - box.minX) * x,
                            box.minY + (box.maxY - box.minY) * height,
                            box.minZ + (box.maxZ - box.minZ) * z);
                    if (clearLine(eyes, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    public static float[] rotations(Entity entity, Vec3 point) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 aim = point == null ? point(entity) : point;
        if (eyes.squareDistanceTo(aim) < 1.0E-8D) {
            return new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
        }
        return RotationUtils.calculate(aim);
    }

    public static MovingObjectPosition trace(Entity entity, float yaw, float pitch, double range) {
        if (entity == null || !entity.canBeCollidedWith() || range <= 0.0D || mc.thePlayer == null) {
            return null;
        }
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = RotationUtils.getVectorForRotation(pitch, yaw);
        Vec3 end = eyes.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
        return box.isVecInside(eyes) ? new MovingObjectPosition(entity, eyes) : box.calculateIntercept(eyes, end);
    }

    public static boolean inRange(Entity entity, double range) {
        float[] rotations = rotations(entity, null);
        return trace(entity, rotations[0], rotations[1], range) != null;
    }

    public static boolean clearLine(Vec3 from, Vec3 to) {
        return mc.theWorld.rayTraceBlocks(from, to, false, true, false) == null;
    }
}
