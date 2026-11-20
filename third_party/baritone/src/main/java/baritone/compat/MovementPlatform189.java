/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.compat;

import baritone.api.utils.IPlayerContext;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/** Minecraft 1.8.9 API/physics bindings for the v1.20.0 movement sources. */
public final class MovementPlatform189 {
    private MovementPlatform189() {}

    public static boolean is(IBlockState state, Block block) { return state.getBlock() == block; }
    public static boolean isLiquid(IBlockState state) { return state.getBlock() instanceof BlockLiquid; }
    public static boolean isWater(IBlockState state) { return isWater(state.getBlock()); }
    public static boolean isWater(Block block) { return block == Blocks.water || block == Blocks.flowing_water; }
    public static boolean isLava(IBlockState state) { return isLava(state.getBlock()); }
    public static boolean isLava(Block block) { return block == Blocks.lava || block == Blocks.flowing_lava; }
    public static int fluidLevel(IBlockState state) { return isLiquid(state) ? state.getValue(BlockLiquid.LEVEL) : -1; }
    public static int fluidAmount(IBlockState state) { return isLiquid(state) ? 8 - (fluidLevel(state) & 7) : 0; }
    public static boolean isFluidSource(IBlockState state) { return isLiquid(state) && fluidLevel(state) == 0; }
    public static boolean isFlowingWater(IBlockState state) { return isWater(state) && fluidLevel(state) != 0; }
    public static boolean isBottomSlab(IBlockState state) {
        return state.getBlock() instanceof BlockSlab && !((BlockSlab)state.getBlock()).isDouble()
                && state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.BOTTOM;
    }
    public static boolean isDoubleSlab(IBlockState state) {
        return state.getBlock() instanceof BlockSlab && ((BlockSlab)state.getBlock()).isDouble();
    }
    public static boolean isFullCube(IBlockState state) {
        // Modern collision-shape queries are state-based; 1.8 pistons expose that state separately.
        Block block = state.getBlock();
        if (block instanceof BlockPistonBase) return !state.getValue(BlockPistonBase.EXTENDED);
        if (!block.getMaterial().blocksMovement() || !block.isFullCube()) return false;
        try {
            net.minecraft.util.AxisAlignedBB box = block.getCollisionBoundingBox(null, BlockPos.ORIGIN, state);
            return box != null && box.minX == 0 && box.minY == 0 && box.minZ == 0
                    && box.maxX == 1 && box.maxY == 1 && box.maxZ == 1;
        } catch (RuntimeException ignored) {
            // Match upstream's fallback when the collision shape cannot be queried.
            return false;
        }
    }
    public static float waterSpeedMultiplier(EntityLivingBase player) {
        // The 1.8 equivalent of the modern water movement efficiency enchantment attribute.
        return Math.min(3, EnchantmentHelper.getDepthStriderModifier(player)) / 3.0F;
    }

    /** 1.8 ladders/vines require horizontal collision; modern jump-to-climb does not exist. */
    public static BlockPos climbTarget(IPlayerContext ctx, BlockPos pos) {
        IBlockState state = ctx.world().getBlockState(pos);
        if (state.getBlock() == Blocks.ladder) return pos.offset(state.getValue(BlockLadder.FACING).getOpposite());
        if (state.getBlock() == Blocks.vine) {
            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                BlockPos against = pos.offset(direction);
                if (ctx.world().getBlockState(against).getBlock().isBlockNormalCube()) return against;
            }
            return null;
        }
        return pos;
    }
}
