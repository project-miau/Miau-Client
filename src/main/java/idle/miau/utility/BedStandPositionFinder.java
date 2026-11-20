package idle.miau.utility;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import idle.miau.module.impl.player.AutoTool;

import java.util.ArrayList;
import java.util.List;

public final class BedStandPositionFinder implements IMinecraftInstance {
    private static final double EYE_HEIGHT = 1.62D;
    private static final double REACH = 4.5D;
    private static final double REACH_EPSILON = 1.0E-4D;
    private static final int MAX_PATH_BLOCKS = 64;

    private BedStandPositionFinder() {
    }

    public static final class StandPosition {
        public final BlockPos feet;
        public final Vec3 aim;
        public final double breakTime;
        public final int defenseBlocks;

        StandPosition(BlockPos feet, Vec3 aim, double breakTime, int defenseBlocks) {
            this.feet = feet;
            this.aim = aim;
            this.breakTime = breakTime;
            this.defenseBlocks = defenseBlocks;
        }

        public boolean isProtected() {
            return defenseBlocks > 0;
        }
    }

    public static StandPosition findBestStandPosition(BlockPos bed) {
        return findBestStandPosition(bed, null);
    }

    public static StandPosition findBestStandPosition(BlockPos bed, BlockPos currentBestFeet) {
        if (bed == null || mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }
        IBlockState state = mc.theWorld.getBlockState(bed);
        if (!(state.getBlock() instanceof BlockBed)) {
            return null;
        }
        BlockPos otherHalf = getOtherBedHalf(bed, state);
        if (otherHalf == null) {
            return null;
        }

        StandPosition best = null;
        for (BlockPos feet : candidateStandPositions(bed, otherHalf)) {
            Vec3 eyes = eyesAt(feet);
            for (BlockPos bedPart : new BlockPos[]{bed, otherHalf}) {
                for (Vec3 aim : getBlockAimPoints(bedPart)) {
                    StandPosition candidate = evaluatePath(feet, eyes, aim, bed, otherHalf);
                    if (candidate != null && (best == null || isBetter(candidate, best, currentBestFeet))) {
                        best = candidate;
                    }
                }
            }
        }
        return best != null && best.isProtected() ? best : null;
    }

    private static boolean isBetter(StandPosition candidate, StandPosition best, BlockPos currentBestFeet) {
        int timeCompare = Double.compare(candidate.breakTime, best.breakTime);
        if (timeCompare != 0) {
            return timeCompare < 0;
        }
        if (candidate.defenseBlocks != best.defenseBlocks) {
            return candidate.defenseBlocks < best.defenseBlocks;
        }
        if (currentBestFeet != null) {
            boolean candidateIsCurrent = candidate.feet.equals(currentBestFeet);
            boolean bestIsCurrent = best.feet.equals(currentBestFeet);
            if (candidateIsCurrent != bestIsCurrent) {
                double candidateDistSq = feetDistanceToPlayerSq(candidate.feet);
                double bestDistSq = feetDistanceToPlayerSq(best.feet);
                if (bestIsCurrent) {
                    return candidateDistSq + 1.5D < bestDistSq;
                } else {
                    return candidateDistSq < bestDistSq + 1.5D;
                }
            }
        }
        return feetDistanceToPlayerSq(candidate.feet) < feetDistanceToPlayerSq(best.feet);
    }

    private static double feetDistanceToPlayerSq(BlockPos feet) {
        double dx = feet.getX() + 0.5D - mc.thePlayer.posX;
        double dy = feet.getY() - mc.thePlayer.posY;
        double dz = feet.getZ() + 0.5D - mc.thePlayer.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static StandPosition evaluatePath(
            BlockPos feet, Vec3 eyes, Vec3 aim, BlockPos bed, BlockPos otherHalf) {
        Block firstBlock = null;
        double breakTime = 0.0D;
        int defenseBlocks = 0;
        int blockCount = 0;
        for (BlockPos position : traceBlocks(eyes, aim)) {
            IBlockState state = mc.theWorld.getBlockState(position);
            Block block = state.getBlock();
            if (!block.canCollideCheck(state, false)) {
                continue;
            }
            MovingObjectPosition hit = block.collisionRayTrace(mc.theWorld, position, eyes, aim);
            if (hit == null || hit.hitVec == null) {
                continue;
            }
            double distance = eyes.distanceTo(hit.hitVec);
            if (distance > REACH + REACH_EPSILON) {
                return null;
            }
            boolean bedBlock = block instanceof BlockBed;
            if (bedBlock && position.equals(bed)) {
                return new StandPosition(feet, aim, breakTime, defenseBlocks);
            }
            if (bedBlock && position.equals(otherHalf)) {
                return null;
            }
            if (block.getBlockHardness(mc.theWorld, position) < 0.0D) {
                return null;
            }
            if (firstBlock == null) {
                firstBlock = block;
            }
            float strength = BedBreakMath.blockStrength(position);
            if (strength <= 0.0F) {
                return null;
            }
            breakTime += 1.0D / strength;
            defenseBlocks++;
            blockCount++;
            if (blockCount >= MAX_PATH_BLOCKS) {
                return null;
            }
        }
        return null;
    }

    private static List<BlockPos> candidateStandPositions(BlockPos bed, BlockPos otherHalf) {
        List<BlockPos> positions = new ArrayList<>();
        int radius = (int) Math.ceil(REACH) + 1;
        int baseX = bed.getX();
        int baseY = bed.getY();
        int baseZ = bed.getZ();
        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                for (int y = baseY - radius; y <= baseY + 1; y++) {
                    BlockPos feet = new BlockPos(x, y, z);
                    if (!isValidStandPosition(feet, bed, otherHalf)) {
                        continue;
                    }
                    positions.add(feet);
                }
            }
        }
        return positions;
    }

    private static boolean isValidStandPosition(BlockPos feet, BlockPos bed, BlockPos otherHalf) {
        if (!mc.theWorld.isAirBlock(feet.up()) || !mc.theWorld.isAirBlock(feet.up(2))) {
            return false;
        }
        if (mc.theWorld.isAirBlock(feet)) {
            return false;
        }
        IBlockState ground = mc.theWorld.getBlockState(feet);
        if (ground.getBlock().getMaterial() == Material.lava
                || ground.getBlock().getMaterial() == Material.water) {
            return false;
        }
        Vec3 eyes = eyesAt(feet);
        return isBlockWithinReachOfEyes(bed, eyes)
                || isBlockWithinReachOfEyes(otherHalf, eyes);
    }

    private static boolean isBlockWithinReachOfEyes(BlockPos pos, Vec3 eyes) {
        if (pos == null) {
            return false;
        }
        AxisAlignedBB box = BlockUtils.getBlockSelectionBox(pos);
        if (box == null) {
            box = new AxisAlignedBB(pos, pos.add(1, 1, 1));
        }
        double x = Math.max(box.minX - eyes.xCoord, Math.max(0.0D, eyes.xCoord - box.maxX));
        double y = Math.max(box.minY - eyes.yCoord, Math.max(0.0D, eyes.yCoord - box.maxY));
        double z = Math.max(box.minZ - eyes.zCoord, Math.max(0.0D, eyes.zCoord - box.maxZ));
        return x * x + y * y + z * z <= (REACH + REACH_EPSILON) * (REACH + REACH_EPSILON);
    }

    private static Vec3 eyesAt(BlockPos feet) {
        return new Vec3(feet.getX() + 0.5D, feet.getY() + 1.0D + EYE_HEIGHT, feet.getZ() + 0.5D);
    }

    private static BlockPos getOtherBedHalf(BlockPos bed, IBlockState state) {
        BlockBed.EnumPartType part = state.getValue(BlockBed.PART);
        EnumFacing facing = state.getValue(BlockBed.FACING);
        return bed.offset(part == BlockBed.EnumPartType.HEAD ? facing.getOpposite() : facing);
    }

    private static Vec3[] getBlockAimPoints(BlockPos position) {
        AxisAlignedBB box = BlockUtils.getBlockSelectionBox(position);
        if (box == null) {
            box = new AxisAlignedBB(position, position.add(1, 1, 1));
        }
        double insetX = Math.min(0.1D, (box.maxX - box.minX) * 0.2D);
        double insetY = Math.min(0.1D, (box.maxY - box.minY) * 0.2D);
        double insetZ = Math.min(0.1D, (box.maxZ - box.minZ) * 0.2D);
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerY = (box.minY + box.maxY) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        return new Vec3[]{
                new Vec3(centerX, centerY, centerZ),
                new Vec3(centerX, box.minY + insetY, centerZ),
                new Vec3(centerX, box.maxY - insetY, centerZ)
        };
    }

    private static List<BlockPos> traceBlocks(Vec3 start, Vec3 end) {
        List<BlockPos> blocks = new ArrayList<>();
        int x = MathHelper.floor_double(start.xCoord);
        int y = MathHelper.floor_double(start.yCoord);
        int z = MathHelper.floor_double(start.zCoord);
        int endX = MathHelper.floor_double(end.xCoord);
        int endY = MathHelper.floor_double(end.yCoord);
        int endZ = MathHelper.floor_double(end.zCoord);
        double deltaX = end.xCoord - start.xCoord;
        double deltaY = end.yCoord - start.yCoord;
        double deltaZ = end.zCoord - start.zCoord;
        int stepX = Integer.compare(endX, x);
        int stepY = Integer.compare(endY, y);
        int stepZ = Integer.compare(endZ, z);
        double stepSizeX = stepX == 0 ? Double.MAX_VALUE : 1.0D / Math.abs(deltaX);
        double stepSizeY = stepY == 0 ? Double.MAX_VALUE : 1.0D / Math.abs(deltaY);
        double stepSizeZ = stepZ == 0 ? Double.MAX_VALUE : 1.0D / Math.abs(deltaZ);
        double nextX = stepX == 0 ? Double.MAX_VALUE
                : (stepX > 0 ? x + 1.0D - start.xCoord : start.xCoord - x)
                / Math.abs(deltaX);
        double nextY = stepY == 0 ? Double.MAX_VALUE
                : (stepY > 0 ? y + 1.0D - start.yCoord : start.yCoord - y)
                / Math.abs(deltaY);
        double nextZ = stepZ == 0 ? Double.MAX_VALUE
                : (stepZ > 0 ? z + 1.0D - start.zCoord : start.zCoord - z)
                / Math.abs(deltaZ);
        blocks.add(new BlockPos(x, y, z));
        while ((x != endX || y != endY || z != endZ) && blocks.size() < MAX_PATH_BLOCKS) {
            if (nextX <= nextY && nextX <= nextZ) {
                x += stepX;
                nextX += stepSizeX;
            } else if (nextY <= nextZ) {
                y += stepY;
                nextY += stepSizeY;
            } else {
                z += stepZ;
                nextZ += stepSizeZ;
            }
            blocks.add(new BlockPos(x, y, z));
        }
        return blocks;
    }

    static final class BedBreakMath {
        private BedBreakMath() {
        }

        static float blockStrength(BlockPos pos) {
            IBlockState state = mc.theWorld.getBlockState(pos);
            int slot = AutoTool.findBestToolSlot(
                    state.getBlock(), mc.thePlayer.inventory.currentItem);
            Block block = state.getBlock();
            float hardness = block.getBlockHardness(mc.theWorld, pos);
            float divisor = canHarvest(block, slot) ? 30.0F : 100.0F;
            return hardness < 0.0F ? 0.0F : digSpeed(state, slot) / hardness / divisor;
        }

        private static float digSpeed(IBlockState state, int slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            float speed = stack == null ? 1.0F
                    : stack.getItem().getStrVsBlock(stack, state.getBlock());
            if (speed > 1.0F) {
                int efficiency = EnchantmentHelper.getEnchantmentLevel(
                        Enchantment.efficiency.effectId, stack);
                if (efficiency > 0) {
                    speed += efficiency * efficiency + 1.0F;
                }
            }
            if (mc.thePlayer.isPotionActive(Potion.digSpeed)) {
                speed *= 1.0F + (mc.thePlayer.getActivePotionEffect(
                        Potion.digSpeed).getAmplifier() + 1) * 0.2F;
            }
            return speed;
        }

        private static boolean canHarvest(Block block, int slot) {
            if (block.getMaterial().isToolNotRequired()) {
                return true;
            }
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            return stack != null && stack.canHarvestBlock(block);
        }
    }
}
