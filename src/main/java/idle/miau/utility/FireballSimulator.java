package idle.miau.utility;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.List;

public final class FireballSimulator {
    private static final int DEFAULT_MAX_TICKS = 600;
    private static final int IGNORE_SHOOTER_TICKS = 25;
    private static final double ENTITY_HIT_EXPANSION = 0.3F;
    private static final double SEARCH_EXPANSION = 1.0D;
    private static final double COLLISION_EPSILON_SQ = 1.0E-7D;
    private static final double WATER_FLOW_ACCELERATION = 0.014D;
    private static final double WATER_CHECK_EXPAND_Y = -0.4000000059604645D;
    private static final double WATER_CHECK_CONTRACT = 0.001D;
    private static final float AIR_MOTION_FACTOR = 0.95F;
    private static final float WATER_MOTION_FACTOR = 0.8F;

    public static MovingObjectPosition traceAim(EntityPlayer player, float partialTicks, double range) {
        Vec3 start = player.getPositionEyes(partialTicks);
        Vec3 direction = RotationUtils.getVectorForRotation(player.rotationPitch, player.rotationYaw);
        Vec3 end = start.addVector(direction.xCoord * range, direction.yCoord * range, direction.zCoord * range);
        World world = player.worldObj;
        MovingObjectPosition closest = traceBlocks(world, start, end, true);
        double closestDistance = closest == null ? range * range : start.squareDistanceTo(closest.hitVec);
        Vec3 searchEnd = closest == null ? end : closest.hitVec;
        AxisAlignedBB searchBox = new AxisAlignedBB(start.xCoord, start.yCoord, start.zCoord,
                start.xCoord, start.yCoord, start.zCoord)
                .addCoord(searchEnd.xCoord - start.xCoord, searchEnd.yCoord - start.yCoord,
                        searchEnd.zCoord - start.zCoord)
                .expand(SEARCH_EXPANSION, SEARCH_EXPANSION, SEARCH_EXPANSION);

        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(player, searchBox)) {
            if (!entity.canBeCollidedWith() || entity.isDead) {
                continue;
            }
            AxisAlignedBB box = entity.getEntityBoundingBox()
                    .expand(ENTITY_HIT_EXPANSION, ENTITY_HIT_EXPANSION, ENTITY_HIT_EXPANSION);
            MovingObjectPosition hit = box.calculateIntercept(start, searchEnd);
            if (box.isVecInside(start)) {
                hit = new MovingObjectPosition(entity, start);
            }
            if (hit != null) {
                double distance = start.squareDistanceTo(hit.hitVec);
                if (distance + COLLISION_EPSILON_SQ < closestDistance) {
                    closestDistance = distance;
                    closest = new MovingObjectPosition(entity, hit.hitVec);
                }
            }
        }
        return closest == null ? new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS,
                end, null, new BlockPos(end)) : closest;
    }

    public static Result simulate(EntityLargeFireball fireball) {
        return simulate(fireball, DEFAULT_MAX_TICKS);
    }

    public static Result simulate(EntityLargeFireball fireball, int maxTicks) {
        if (fireball == null) {
            throw new IllegalArgumentException("fireball cannot be null");
        }

        World world = fireball.worldObj;
        if (world == null) {
            Vec3 pos = new Vec3(fireball.posX, fireball.posY, fireball.posZ);
            return new Result(HitType.NONE, null, null, null, pos, 0);
        }

        double posX = fireball.posX;
        double posY = fireball.posY;
        double posZ = fireball.posZ;
        double motionX = fireball.motionX;
        double motionY = fireball.motionY;
        double motionZ = fireball.motionZ;
        double accelerationX = finiteAcceleration(fireball.accelerationX);
        double accelerationY = finiteAcceleration(fireball.accelerationY);
        double accelerationZ = finiteAcceleration(fireball.accelerationZ);
        float width = fireball.width;
        float height = fireball.height;
        EntityLivingBase shooter = fireball.shootingEntity;
        int ticksInAir = Math.max(0, fireball.ticksExisted);
        int simulatedTicks = 0;
        Vec3 finalPosition = new Vec3(posX, posY, posZ);

        for (int tick = 0; tick < Math.max(1, maxTicks); tick++) {
            simulatedTicks = tick + 1;
            ticksInAir++;
            WaterState waterState = sampleWaterState(world, posX, posY, posZ, width, height, fireball);

            if (fireball.isPushedByWater()) {
                motionX += waterState.flowDirection.xCoord * WATER_FLOW_ACCELERATION;
                motionY += waterState.flowDirection.yCoord * WATER_FLOW_ACCELERATION;
                motionZ += waterState.flowDirection.zCoord * WATER_FLOW_ACCELERATION;
            }

            Vec3 start = new Vec3(posX, posY, posZ);
            Vec3 end = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);
            AxisAlignedBB sweepBounds = getSweepBounds(posX, posY, posZ, motionX, motionY, motionZ, width, height);
            MovingObjectPosition blockHit = traceBlocks(world, start, end, false);
            Vec3 searchEnd = blockHit != null ? blockHit.hitVec : end;
            double bestDistanceSq = blockHit != null ? start.squareDistanceTo(blockHit.hitVec) : Double.MAX_VALUE;
            MovingObjectPosition bestEntityHit = null;

            AxisAlignedBB searchBox = sweepBounds
                    .expand(SEARCH_EXPANSION, SEARCH_EXPANSION, SEARCH_EXPANSION);
            List<Entity> candidates = world.getEntitiesWithinAABBExcludingEntity(fireball, searchBox);

            for (Entity candidate : candidates) {
                if (candidate.isDead || !candidate.canBeCollidedWith()) {
                    continue;
                }

                if (candidate.isEntityEqual(shooter) && ticksInAir < IGNORE_SHOOTER_TICKS) {
                    continue;
                }

                AxisAlignedBB expandedBox = candidate.getEntityBoundingBox()
                        .expand(ENTITY_HIT_EXPANSION, ENTITY_HIT_EXPANSION, ENTITY_HIT_EXPANSION);
                MovingObjectPosition entityHit = expandedBox.calculateIntercept(start, searchEnd);

                if (entityHit == null) {
                    continue;
                }

                double distanceSq = start.squareDistanceTo(entityHit.hitVec);
                if (distanceSq + COLLISION_EPSILON_SQ < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    bestEntityHit = new MovingObjectPosition(candidate, entityHit.hitVec);
                }
            }

            if (bestEntityHit != null) {
                return new Result(HitType.ENTITY, bestEntityHit, bestEntityHit.hitVec, start, start, simulatedTicks);
            }

            if (blockHit != null) {
                return new Result(HitType.BLOCK, blockHit, blockHit.hitVec, start, start, simulatedTicks);
            }

            posX += motionX;
            posY += motionY;
            posZ += motionZ;
            finalPosition = new Vec3(posX, posY, posZ);

            motionX += accelerationX;
            motionY += accelerationY;
            motionZ += accelerationZ;

            float motionFactor = waterState.inWater ? WATER_MOTION_FACTOR : AIR_MOTION_FACTOR;
            motionX *= motionFactor;
            motionY *= motionFactor;
            motionZ *= motionFactor;

            if (posY < -64.0D) {
                break;
            }
        }

        return new Result(HitType.NONE, null, null, finalPosition, finalPosition, simulatedTicks);
    }

    private static AxisAlignedBB getBoundingBox(double posX, double posY, double posZ, float width, float height) {
        double halfWidth = width * 0.5D;
        return new AxisAlignedBB(
                posX - halfWidth,
                posY,
                posZ - halfWidth,
                posX + halfWidth,
                posY + height,
                posZ + halfWidth
        );
    }

    private static AxisAlignedBB getSweepBounds(double posX, double posY, double posZ,
                                                double motionX, double motionY, double motionZ,
                                                float width, float height) {
        return getBoundingBox(posX, posY, posZ, width, height).addCoord(motionX, motionY, motionZ);
    }

    private static MovingObjectPosition traceBlocks(World world, Vec3 start, Vec3 end, boolean ignoreWithoutBounds) {
        Vec3 difference = end.subtract(start);
        int segments = Math.max(1, (int) Math.ceil(difference.lengthVector() / 64.0D));
        Vec3 segmentStart = start;
        for (int segment = 1; segment <= segments; segment++) {
            double progress = (double) segment / segments;
            Vec3 segmentEnd = start.addVector(difference.xCoord * progress,
                    difference.yCoord * progress, difference.zCoord * progress);
            MovingObjectPosition hit = world.rayTraceBlocks(segmentStart, segmentEnd, false, ignoreWithoutBounds, false);
            if (hit != null) {
                return hit;
            }
            segmentStart = segmentEnd;
        }
        return null;
    }

    private static WaterState sampleWaterState(World world, double posX, double posY, double posZ,
                                               float width, float height, Entity fireball) {
        AxisAlignedBB waterCheckBox = getBoundingBox(posX, posY, posZ, width, height)
                .expand(0.0D, WATER_CHECK_EXPAND_Y, 0.0D)
                .contract(WATER_CHECK_CONTRACT, WATER_CHECK_CONTRACT, WATER_CHECK_CONTRACT);

        int minX = MathHelper.floor_double(waterCheckBox.minX);
        int maxX = MathHelper.floor_double(waterCheckBox.maxX + 1.0D);
        int minY = MathHelper.floor_double(waterCheckBox.minY);
        int maxY = MathHelper.floor_double(waterCheckBox.maxY + 1.0D);
        int minZ = MathHelper.floor_double(waterCheckBox.minZ);
        int maxZ = MathHelper.floor_double(waterCheckBox.maxZ + 1.0D);

        if (!world.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new WaterState(false, new Vec3(0.0D, 0.0D, 0.0D));
        }

        boolean inWater = false;
        Vec3 flow = new Vec3(0.0D, 0.0D, 0.0D);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x < maxX; ++x) {
            for (int y = minY; y < maxY; ++y) {
                for (int z = minZ; z < maxZ; ++z) {
                    mutablePos.set(x, y, z);
                    IBlockState blockState = world.getBlockState(mutablePos);

                    if (blockState.getBlock().getMaterial() != Material.water) {
                        continue;
                    }

                    double liquidSurfaceY = (double) ((float) (y + 1)
                            - BlockLiquid.getLiquidHeightPercent((Integer) blockState.getValue(BlockLiquid.LEVEL)));

                    if ((double) maxY < liquidSurfaceY) {
                        continue;
                    }

                    inWater = true;
                    flow = blockState.getBlock().modifyAcceleration(world, mutablePos, fireball, flow);
                }
            }
        }

        if (flow.lengthVector() > 0.0D) {
            flow = flow.normalize();
        }

        return new WaterState(inWater, flow);
    }

    private static double finiteAcceleration(double acceleration) {
        return Double.isFinite(acceleration) ? acceleration : 0.0D;
    }

    private static final class WaterState {
        private final boolean inWater;
        private final Vec3 flowDirection;

        private WaterState(boolean inWater, Vec3 flowDirection) {
            this.inWater = inWater;
            this.flowDirection = flowDirection;
        }
    }

    public enum HitType {
        NONE,
        BLOCK,
        ENTITY
    }

    public static final class Result {
        private final HitType hitType;
        private final MovingObjectPosition collision;
        private final Vec3 hitPosition;
        private final Vec3 impactPosition;
        private final Vec3 finalPosition;
        private final int simulatedTicks;

        private Result(HitType hitType, MovingObjectPosition collision, Vec3 hitPosition, Vec3 impactPosition,
                       Vec3 finalPosition, int simulatedTicks) {
            this.hitType = hitType;
            this.collision = collision;
            this.hitPosition = hitPosition;
            this.impactPosition = impactPosition;
            this.finalPosition = finalPosition;
            this.simulatedTicks = simulatedTicks;
        }

        public HitType getHitType() {
            return hitType;
        }

        public boolean collided() {
            return hitType != HitType.NONE;
        }

        public boolean hitBlock() {
            return hitType == HitType.BLOCK;
        }

        public boolean hitEntity() {
            return hitType == HitType.ENTITY;
        }

        public MovingObjectPosition getCollision() {
            return collision;
        }

        public Entity getTarget() {
            return collision != null ? collision.entityHit : null;
        }

        public Entity getHitEntity() {
            return getTarget();
        }

        public BlockPos getHitBlockPos() {
            return collision != null ? collision.getBlockPos() : null;
        }

        public Vec3 getHitPosition() {
            return hitPosition;
        }

        public Vec3 getImpactPosition() {
            return impactPosition;
        }

        public Vec3 getFinalPosition() {
            return finalPosition;
        }

        public int getSimulatedTicks() {
            return simulatedTicks;
        }
    }
}
