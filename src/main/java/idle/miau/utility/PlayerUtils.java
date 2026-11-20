package idle.miau.utility;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.KillAura;
import idle.miau.module.impl.world.AntiBot;

import java.util.ArrayList;
import java.util.List;

public final class PlayerUtils implements IMinecraftInstance {
    private PlayerUtils() {
    }

    public static boolean canReach(BlockPos blockPos, double reach) {
        if (mc.thePlayer == null) {
            return false;
        }
        return isBlockWithinReach(
                blockPos,
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ,
                reach);
    }

    public static boolean isBlockWithinReach(
            BlockPos blockPos, double x, double y, double z, double reach) {
        return blockPos != null && blockPos.distanceSqToCenter(x, y, z) < reach * reach;
    }

    public static boolean isEntityLoaded(Entity entity) {
        return entity != null && mc.theWorld != null && mc.theWorld.loadedEntityList.contains(entity);
    }

    public static void attackEntityLocally(Entity entity) {
        if (mc.thePlayer != null
                && entity != null
                && entity.canAttackWithItem()
                && !entity.hitByEntity(mc.thePlayer)) {
            mc.thePlayer.attackTargetEntityWithCurrentItem(entity);
        }
    }

    public static boolean canMove(double x, double z) {
        return canMove(x, z, -1.0D);
    }

    public static boolean canMove(double x, double z, double y) {
        AxisAlignedBB boundingBox = mc.thePlayer.getEntityBoundingBox().offset(x, y, z);
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, boundingBox).isEmpty();
    }

    public static Block block(double x, double y, double z) {
        return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    public static Block block(BlockPos position) {
        return mc.theWorld.getBlockState(position).getBlock();
    }

    public static Block blockRelativeToPlayer(float offsetX, float offsetY, float offsetZ) {
        return blockRelativeToPlayer((double) offsetX, offsetY, offsetZ);
    }

    public static Block blockRelativeToPlayer(double offsetX, double offsetY, double offsetZ) {
        return block(mc.thePlayer.posX + offsetX, mc.thePlayer.posY + offsetY,
                mc.thePlayer.posZ + offsetZ);
    }

    public static double getDistanceToEntityBox(Entity entity) {
        return mc.thePlayer.getPositionEyes(1.0F).distanceTo(RotationUtils.getBestHitVec(entity));
    }

    public static double getCustomDistanceToEntityBox(Vec3 eyes, Entity entity) {
        Vec3 point = RotationUtils.clampVecToBox(eyes, entity.getEntityBoundingBox());
        return eyes.distanceTo(point);
    }

    public static double getCustomDistanceToEntityBox(Vec3 eyes, Vec3 entityPosition) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().offset(
                entityPosition.xCoord - mc.thePlayer.posX,
                entityPosition.yCoord - mc.thePlayer.posY,
                entityPosition.zCoord - mc.thePlayer.posZ);
        return eyes.distanceTo(RotationUtils.clampVecToBox(eyes, box));
    }

    public static EntityLivingBase getTarget(double distance) {
        return getTarget(distance, "Distance");
    }

    public static EntityLivingBase getTarget(double distance, String targetPriority) {
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled()
                && KillAura.target != null) {
            return KillAura.target;
        }
        EntityLivingBase target = null;
        double closestDistance = distance + 0.4D;
        double leastHealth = Float.MAX_VALUE;
        double leastHurtTime = 10.0D;
        double closestRayDistance = Double.MAX_VALUE;
        Vec3 rayPoint = getMouseRayPoint();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer
                    || mc.thePlayer.getDistanceSqToEntity(entity) > distance * distance
                    || !isValidDemiseTarget((EntityLivingBase) entity)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            double entityDistance = getDistanceToEntityBox(living);
            if (entityDistance > distance) {
                continue;
            }
            if ("None".equals(targetPriority)) {
                target = living;
            } else if ("Health".equals(targetPriority) && living.getHealth() < leastHealth) {
                target = living;
                leastHealth = living.getHealth();
            } else if ("HurtTime".equals(targetPriority) && living.hurtTime <= leastHurtTime) {
                target = living;
                leastHurtTime = living.hurtTime;
            } else if ("RayTrace".equals(targetPriority)) {
                double rayDistance = getCustomDistanceToEntityBox(rayPoint, living);
                if (rayDistance < closestRayDistance) {
                    target = living;
                    closestRayDistance = rayDistance;
                }
            } else if ("Distance".equals(targetPriority) && entityDistance < closestDistance) {
                target = living;
                closestDistance = entityDistance;
            }
        }
        return target;
    }

    public static List<EntityLivingBase> getTargets(double distance) {
        List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (getDistanceToEntityBox(living) <= distance && isValidDemiseTarget(living)) {
                targets.add(living);
            }
        }
        return targets;
    }

    public static boolean overVoid() {
        return mc.thePlayer != null && overVoid(mc.thePlayer.getPositionVector());
    }

    public static boolean overVoid(Vec3 position) {
        return position != null && overVoid(position.xCoord, position.yCoord, position.zCoord);
    }

    public static boolean overVoid(double x, double y, double z) {
        for (int blockY = (int) y; blockY > -1; blockY--) {
            if (!(block(x, blockY, z) instanceof BlockAir)) {
                return false;
            }
        }
        return true;
    }

    public static MovingObjectPosition blockRayTrace(BlockPos position) {
        Vec3 from = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 direction = Utils.getLookVec(
                RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]);
        Vec3 to = from.addVector(
                direction.xCoord * 4.5D,
                direction.yCoord * 4.5D,
                direction.zCoord * 4.5D);
        MovingObjectPosition result = block(position).collisionRayTrace(
                mc.theWorld, position, from, to);
        return result != null ? result : new MovingObjectPosition(
                MovingObjectPosition.MovingObjectType.MISS,
                to, EnumFacing.UP, new BlockPos(to));
    }

    private static Vec3 getMouseRayPoint() {
        if (mc.objectMouseOver != null && mc.objectMouseOver.hitVec != null) {
            return mc.objectMouseOver.hitVec;
        }
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLookVec();
        return eyes.addVector(look.xCoord * 4.5D, look.yCoord * 4.5D, look.zCoord * 4.5D);
    }

    private static boolean isValidDemiseTarget(EntityLivingBase entity) {
        if (!(entity instanceof EntityAnimal || entity instanceof EntityMob
                || entity instanceof EntityVillager || entity instanceof EntityPlayer
                || entity instanceof EntitySlime || entity instanceof EntityIronGolem)
                || entity.isInvisible() || entity.isDead || entity.deathTime > 0) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            return !TeamUtils.isFriend(player)
                    && (ModuleManager.antiBot == null || !AntiBot.isBot(player));
        }
        return true;
    }
}
