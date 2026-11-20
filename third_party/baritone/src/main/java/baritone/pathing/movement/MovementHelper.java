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

package baritone.pathing.movement;

import net.minecraft.init.Blocks;
import baritone.compat.MovementPlatform189;
import baritone.compat.Minecraft189;
import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.ActionCosts;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.*;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementState.MovementTarget;
import baritone.pathing.precompute.Ternary;
import baritone.utils.BlockStateInterface;
import baritone.utils.ToolSet;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.block.*;
import net.minecraft.block.BlockPistonMoving;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static baritone.api.utils.RotationUtils.DEG_TO_RAD_F;
import static baritone.pathing.movement.Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP;
import static baritone.pathing.precompute.Ternary.*;

/**
 * Static helpers for cost calculation
 *
 * @author leijurv
 */
public interface MovementHelper extends ActionCosts, Helper {

    static boolean avoidBreaking(BlockStateInterface bsi, int x, int y, int z, IBlockState state) {
        if (!bsi.worldBorder.canPlaceAt(x, z)) {
            return true;
        }
        Block b = state.getBlock();
        return Baritone.settings().blocksToDisallowBreaking.value.contains(b)
                || b == Blocks.ice // ice becomes water, and water can mess up the path
                || b instanceof BlockSilverfish // obvious reasons
                // call context.get directly with x,y,z. no need to make 5 new BlockPos for no reason
                || avoidAdjacentBreaking(bsi, x, y + 1, z, true)
                || avoidAdjacentBreaking(bsi, x + 1, y, z, false)
                || avoidAdjacentBreaking(bsi, x - 1, y, z, false)
                || avoidAdjacentBreaking(bsi, x, y, z + 1, false)
                || avoidAdjacentBreaking(bsi, x, y, z - 1, false);
    }

    static boolean avoidAdjacentBreaking(BlockStateInterface bsi, int x, int y, int z, boolean directlyAbove) {
        // returns true if you should avoid breaking a block that's adjacent to this one (e.g. lava that will start flowing if you give it a path)
        // this is only called for north, south, east, west, and up. this is NOT called for down.
        // we assume that it's ALWAYS okay to break the block thats ABOVE liquid
        IBlockState state = bsi.get0(x, y, z);
        Block block = state.getBlock();
        if (!directlyAbove // it is fine to mine a block that has a falling block directly above, this (the cost of breaking the stacked fallings) is included in cost calculations
                // therefore if directlyAbove is true, we will actually ignore if this is falling
                && block instanceof BlockFalling // obviously, this check is only valid for falling blocks
                && Baritone.settings().avoidUpdatingFallingBlocks.value // and if the setting is enabled
                && Minecraft189.canFallThrough(bsi.get0(x, y - 1, z))) { // and if it would fall (i.e. it's unsupported)
            return true; // dont break a block that is adjacent to unsupported gravel because it can cause really weird stuff
        }
        // only pure liquids for now
        // waterlogged blocks can have closed bottom sides and such
        if (block instanceof BlockLiquid) {
            if (directlyAbove || Baritone.settings().strictLiquidCheck.value) {
                return true;
            }
            int level = state.getValue(BlockLiquid.LEVEL);
            if (level == 0) {
                return true; // source blocks like to flow horizontally
            }
            // everything else will prefer flowing down
            return !(bsi.get0(x, y - 1, z).getBlock() instanceof BlockLiquid); // assume everything is in a static state
        }
        return MovementPlatform189.isLiquid(state);
    }

    static boolean canWalkThrough(IPlayerContext ctx, BetterBlockPos pos) {
        return canWalkThrough(new BlockStateInterface(ctx), pos.x, pos.y, pos.z);
    }

    static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z) {
        return canWalkThrough(bsi, x, y, z, bsi.get0(x, y, z));
    }

    static boolean canWalkThrough(CalculationContext context, int x, int y, int z, IBlockState state) {
        return context.precomputedData.canWalkThrough(context.bsi, x, y, z, state);
    }

    static boolean canWalkThrough(CalculationContext context, int x, int y, int z) {
        return context.precomputedData.canWalkThrough(context.bsi, x, y, z, context.get(x, y, z));
    }

    static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, IBlockState state) {
        Ternary canWalkThrough = canWalkThroughBlockState(state);
        if (canWalkThrough == YES) {
            return true;
        }
        if (canWalkThrough == NO) {
            return false;
        }
        return canWalkThroughPosition(bsi, x, y, z, state);
    }

    static Ternary canWalkThroughBlockState(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockAir) {
            return YES;
        }
        if (block instanceof BlockFire || block == Blocks.web || block == Blocks.end_portal || block == Blocks.cocoa || block instanceof BlockSkull || block instanceof BlockSlab || block instanceof BlockTrapDoor) {
            return NO;
        }
        if (Baritone.settings().blocksToAvoid.value.contains(block)) {
            return NO;
        }
        if (block instanceof BlockDoor || block instanceof BlockFenceGate) {
            // TODO this assumes that all doors in all mods are openable
            if (block == Blocks.iron_door) {
                return NO;
            }
            return YES;
        }
        if (block instanceof BlockCarpet) {
            return MAYBE;
        }
        if (block instanceof BlockSnow) {
            // snow layers cached as the top layer of a packed chunk have no metadata, we can't make a decision based on their depth here
            // it would otherwise make long distance pathing through snowy biomes impossible
            return MAYBE;
        }
        if (MovementPlatform189.isLiquid(state)) {
            if (MovementPlatform189.fluidAmount(state) != 8) {
                return NO;
            } else {
                return MAYBE;
            }
        }
        if (block instanceof BlockCauldron) {
            return NO;
        }
        if (state.getBlock().isPassable(null, null)) {
            return YES;
        } else {
            return NO;
        }
    }

    static boolean canWalkThroughPosition(BlockStateInterface bsi, int x, int y, int z, IBlockState state) {
        Block block = state.getBlock();

        if (block instanceof BlockCarpet) {
            return canWalkOn(bsi, x, y - 1, z);
        }

        if (block instanceof BlockSnow) {
            // if they're cached as a top block, we don't know their metadata
            // default to true (mostly because it would otherwise make long distance pathing through snowy biomes impossible)
            if (!bsi.worldContainsLoadedChunk(x, z)) {
                return true;
            }
            // the check in BlockSnow.isPassable is layers < 5
            // while actually, we want < 3 because 3 or greater makes it impassable in a 2 high ceiling
            if (state.getValue(BlockSnow.LAYERS) >= 3) {
                return false;
            }
            // ok, it's low enough we could walk through it, but is it supported?
            return canWalkOn(bsi, x, y - 1, z);
        }

        if (MovementPlatform189.isLiquid(state)) {
            if (isFlowing(x, y, z, state, bsi)) {
                return false;
            }
            // Everything after this point has to be a special case as it relies on the water not being flowing, which means a special case is needed.
            if (Baritone.settings().assumeWalkOnWater.value) {
                return false;
            }

            IBlockState up = bsi.get0(x, y + 1, z);
            if (MovementPlatform189.isLiquid(up) || up.getBlock() instanceof BlockLilyPad) {
                return false;
            }
            return MovementPlatform189.isWater(state);
        }

        return state.getBlock().isPassable(null, null);
    }

    static Ternary fullyPassableBlockState(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockAir) { // early return for most common case
            return YES;
        }
        // exceptions - blocks that are isPassable true, but we can't actually jump through
        if (block instanceof BlockFire
                || block == Blocks.tripwire
                || block == Blocks.web
                || block == Blocks.vine
                || block == Blocks.ladder
                || block == Blocks.cocoa
                || block instanceof BlockDoor
                || block instanceof BlockFenceGate
                || block instanceof BlockSnow
                || MovementPlatform189.isLiquid(state)
                || block instanceof BlockTrapDoor
                || block instanceof BlockEndPortal
                || block instanceof BlockSkull) {
            return NO;
        }
        // door, fence gate, liquid, trapdoor have been accounted for, nothing else uses the world or pos parameters
        // at least in 1.12.2 vanilla, that is.....
        if (state.getBlock().isPassable(null, null)) {
            return YES;
        } else {
            return NO;
        }
    }

    /**
     * canWalkThrough but also won't impede movement at all. so not including doors or fence gates (we'd have to right click),
     * not including water, and not including ladders or vines or cobwebs (they slow us down)
     */
    static boolean fullyPassable(CalculationContext context, int x, int y, int z) {
        return fullyPassable(context, x, y, z, context.get(x, y, z));
    }

    static boolean fullyPassable(CalculationContext context, int x, int y, int z, IBlockState state) {
        return context.precomputedData.fullyPassable(context.bsi, x, y, z, state);
    }

    static boolean fullyPassable(IPlayerContext ctx, BlockPos pos) {
        IBlockState state = ctx.world().getBlockState(pos);
        Ternary fullyPassable = fullyPassableBlockState(state);
        if (fullyPassable == YES) {
            return true;
        }
        if (fullyPassable == NO) {
            return false;
        }
        return state.getBlock().isPassable(null, null);
    }

    /**
     * params retained for backwards compatibility
     */
    static boolean fullyPassablePosition(BlockStateInterface bsi, int x, int y, int z, IBlockState state) {
        return state.getBlock().isPassable(null, null);
    }

    static boolean isReplaceable(int x, int y, int z, IBlockState state, BlockStateInterface bsi) {
        // for MovementTraverse and MovementAscend
        // block double plant defaults to true when the block doesn't match, so don't need to check that case
        // all other overrides just return true or false
        // the only case to deal with is snow
        /*
         *  public boolean isReplaceable(IBlockAccess worldIn, BlockPos pos)
         *     {
         *         return ((Integer)worldIn.getBlockState(pos).getValue(LAYERS)).intValue() == 1;
         *     }
         */
        Block block = state.getBlock();
        if (block instanceof BlockAir) {
            // early return for common cases hehe
            return true;
        }
        if (block instanceof BlockSnow) {
            // as before, default to true (mostly because it would otherwise make long distance pathing through snowy biomes impossible)
            if (!bsi.worldContainsLoadedChunk(x, z)) {
                return true;
            }
            return state.getValue(BlockSnow.LAYERS) == 1;
        }
        if (block instanceof BlockDoublePlant) {
            BlockDoublePlant.EnumPlantType type = state.getValue(BlockDoublePlant.VARIANT);
            return type == BlockDoublePlant.EnumPlantType.FERN || type == BlockDoublePlant.EnumPlantType.GRASS;
        }
        return state.getBlock().getMaterial().isReplaceable();
    }

    static boolean isDoorPassable(IPlayerContext ctx, BlockPos doorPos, BlockPos playerPos) {
        if (playerPos.equals(doorPos)) {
            return false;
        }

        IBlockState state = BlockStateInterface.get(ctx, doorPos);
        if (!(state.getBlock() instanceof BlockDoor)) {
            return true;
        }

        // 1.8 stores some door properties only in the other half; modern states contain both.
        state = state.getBlock().getActualState(state, ctx.world(), doorPos);
        return isHorizontalBlockPassable(doorPos, state, playerPos, BlockDoor.OPEN);
    }

    static boolean isGatePassable(IPlayerContext ctx, BlockPos gatePos, BlockPos playerPos) {
        if (playerPos.equals(gatePos)) {
            return false;
        }

        IBlockState state = BlockStateInterface.get(ctx, gatePos);
        if (!(state.getBlock() instanceof BlockFenceGate)) {
            return true;
        }

        return state.getValue(BlockFenceGate.OPEN);
    }

    static boolean isHorizontalBlockPassable(BlockPos blockPos, IBlockState blockState, BlockPos playerPos, PropertyBool propertyOpen) {
        if (playerPos.equals(blockPos)) {
            return false;
        }

        EnumFacing.Axis facing = blockState.getValue(BlockDirectional.FACING).getAxis();
        boolean open = blockState.getValue(propertyOpen);

        EnumFacing.Axis playerFacing;
        if (playerPos.north().equals(blockPos) || playerPos.south().equals(blockPos)) {
            playerFacing = EnumFacing.Axis.Z;
        } else if (playerPos.east().equals(blockPos) || playerPos.west().equals(blockPos)) {
            playerFacing = EnumFacing.Axis.X;
        } else {
            return true;
        }

        return (facing == playerFacing) == open;
    }

    static boolean avoidWalkingInto(IBlockState state) {
        Block block = state.getBlock();
        return MovementPlatform189.isLiquid(state)
                || (false && !false)
                || block == Blocks.cactus
                || block instanceof BlockFire
                || block == Blocks.end_portal
                || block == Blocks.web;
    }

    /**
     * Can I walk on this block without anything weird happening like me falling
     * through? Includes water because we know that we automatically jump on
     * water
     * <p>
     * If changing something in this function remember to also change it in precomputed data
     *
     * @param bsi   Block state provider
     * @param x     The block's x position
     * @param y     The block's y position
     * @param z     The block's z position
     * @param state The state of the block at the specified location
     * @return Whether or not the specified block can be walked on
     */
    static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, IBlockState state) {
        Ternary canWalkOn = canWalkOnBlockState(state);
        if (canWalkOn == YES) {
            return true;
        }
        if (canWalkOn == NO) {
            return false;
        }
        return canWalkOnPosition(bsi, x, y, z, state);
    }

    static Ternary canWalkOnBlockState(IBlockState state) {
        Block block = state.getBlock();
        if (isBlockNormalCube(state) && (true || false)) {
            return YES;
        }
        if (false) {
            return YES;
        }
        if (block == Blocks.ladder || (isClimbable(block) && Baritone.settings().allowVines.value)) { // TODO reconsider this
            return YES;
        }
        if (block == Blocks.farmland || block == Blocks.soul_sand) {
            return YES;
        }
        if (block == Blocks.ender_chest || block == Blocks.chest || block == Blocks.trapped_chest) {
            return YES;
        }
        if (block == Blocks.glass || block instanceof BlockStainedGlass) {
            return YES;
        }
        if (block instanceof BlockStairs) {
            return YES;
        }
        if (isWater(state)) {
            return MAYBE;
        }
        if (MovementHelper.isLava(state) && Baritone.settings().assumeWalkOnLava.value) {
            return MAYBE;
        }
        if (block instanceof BlockSlab) {
            if (!Baritone.settings().allowWalkOnBottomSlab.value) {
                if (!MovementPlatform189.isBottomSlab(state)) {
                    return YES;
                }
                return NO;
            }
            return YES;
        }
        return NO;
    }

    static boolean canWalkOnPosition(BlockStateInterface bsi, int x, int y, int z, IBlockState state) {
        Block block = state.getBlock();
        if (isWater(state)) {
            // since this is called literally millions of times per second, the benefit of not allocating millions of useless "pos.up()"
            // BlockPos s that we'd just garbage collect immediately is actually noticeable. I don't even think its a decrease in readability
            IBlockState upState = bsi.get0(x, y + 1, z);
            Block up = upState.getBlock();
            if (up == Blocks.waterlily || up instanceof BlockCarpet) {
                return true;
            }
            if (MovementHelper.isFlowing(x, y, z, state, bsi) || MovementPlatform189.isFlowingWater(upState)) {
                // the only scenario in which we can walk on flowing water is if it's under still water with jesus off
                return isWater(upState) && !Baritone.settings().assumeWalkOnWater.value;
            }
            // if assumeWalkOnWater is on, we can only walk on water if there isn't water above it
            // if assumeWalkOnWater is off, we can only walk on water if there is water above it
            return isWater(upState) ^ Baritone.settings().assumeWalkOnWater.value;
        }

        if (MovementHelper.isLava(state) && !MovementHelper.isFlowing(x, y, z, state, bsi) && Baritone.settings().assumeWalkOnLava.value) { // if we get here it means that assumeWalkOnLava must be true, so put it last
            return true;
        }

        return false; // If we don't recognise it then we want to just return false to be safe.
    }

    static boolean canWalkOn(CalculationContext context, int x, int y, int z, IBlockState state) {
        return context.precomputedData.canWalkOn(context.bsi, x, y, z, state);
    }

    static boolean canWalkOn(CalculationContext context, int x, int y, int z) {
        return canWalkOn(context, x, y, z, context.get(x, y, z));
    }

    static boolean canWalkOn(IPlayerContext ctx, BetterBlockPos pos, IBlockState state) {
        return canWalkOn(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, state);
    }

    static boolean canWalkOn(IPlayerContext ctx, BlockPos pos) {
        return canWalkOn(new BlockStateInterface(ctx), pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean canWalkOn(IPlayerContext ctx, BetterBlockPos pos) {
        return canWalkOn(new BlockStateInterface(ctx), pos.x, pos.y, pos.z);
    }

    static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z) {
        return canWalkOn(bsi, x, y, z, bsi.get0(x, y, z));
    }

    static boolean canUseFrostWalker(CalculationContext context, IBlockState state) {
        return false; // This enchantment and frosted ice do not exist in 1.8.9.
    }

    static boolean canUseFrostWalker(IPlayerContext ctx, BlockPos pos) {
        return false; // This enchantment and frosted ice do not exist in 1.8.9.
    }

    /**
     * If movements make us stand/walk on this block, will it have a top to walk on?
     */
    static boolean mustBeSolidToWalkOn(CalculationContext context, int x, int y, int z, IBlockState state) {
        Block block = state.getBlock();
        if (isClimbable(block)) {
            return false;
        }
        if (MovementPlatform189.isLiquid(state)) {
            // used for frostwalker so only includes blocks where we are still on ground when leaving them to any side
            // 1.8 liquids cannot waterlog slabs, stairs, trapdoors, scaffolding or leaves.
            if (context.assumeWalkOnWater) {
                return false;
            }
            Block blockAbove = context.getBlock(x, y + 1, z);
            if (blockAbove instanceof BlockLiquid) {
                return false;
            }
        }
        return true;
    }

    static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z) {
        return canPlaceAgainst(bsi, x, y, z, bsi.get0(x, y, z));
    }

    static boolean canPlaceAgainst(BlockStateInterface bsi, BlockPos pos) {
        return canPlaceAgainst(bsi, pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean canPlaceAgainst(IPlayerContext ctx, BlockPos pos) {
        return canPlaceAgainst(new BlockStateInterface(ctx), pos);
    }

    static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z, IBlockState state) {
        if (!bsi.worldBorder.canPlaceAt(x, z)) {
            return false;
        }
        // can we look at the center of a side face of this block and likely be able to place?
        // (thats how this check is used)
        // therefore dont include weird things that we technically could place against (like carpet) but practically can't
        return isBlockNormalCube(state) || state.getBlock() == Blocks.glass || state.getBlock() instanceof BlockStainedGlass;
    }

    /**
     * Can we climb up this block by pressing space while inside it?
     * Also doubles as "If I start a movement on this, can weird things happen?"
     * because movements can end/start on these blocks despite them not being canWalkOn.
     */
    static boolean isClimbable(Block block) {
        return block == Blocks.ladder
            || block == Blocks.vine;
    }

    static double getMiningDurationTicks(CalculationContext context, int x, int y, int z, boolean includeFalling) {
        return getMiningDurationTicks(context, x, y, z, context.get(x, y, z), includeFalling);
    }

    static double getMiningDurationTicks(CalculationContext context, int x, int y, int z, IBlockState state, boolean includeFalling) {
        Block block = state.getBlock();
        if (!canWalkThrough(context, x, y, z, state)) {
            if (MovementPlatform189.isLiquid(state)) {
                return COST_INF;
            }
            double mult = context.breakCostMultiplierAt(x, y, z, state);
            if (mult >= COST_INF) {
                return COST_INF;
            }
            if (avoidBreaking(context.bsi, x, y, z, state)) {
                return COST_INF;
            }
            double strVsBlock = context.toolSet.getStrVsBlock(state);
            if (strVsBlock <= 0) {
                return COST_INF;
            }
            double result = 1 / strVsBlock;
            result += context.breakBlockAdditionalCost;
            result *= mult;
            if (includeFalling) {
                IBlockState above = context.get(x, y + 1, z);
                if (above.getBlock() instanceof BlockFalling) {
                    result += getMiningDurationTicks(context, x, y + 1, z, above, true);
                }
            }
            return result;
        }
        return 0; // we won't actually mine it, so don't check fallings above
    }

    static boolean isBottomSlab(IBlockState state) {
        return state.getBlock() instanceof BlockSlab
                && MovementPlatform189.isBottomSlab(state);
    }

    /**
     * AutoTool for a specific block
     *
     * @param ctx The player context
     * @param b   the blockstate to mine
     */
    static void switchToBestToolFor(IPlayerContext ctx, IBlockState b) {
        switchToBestToolFor(ctx, b, new ToolSet(ctx.player()), BaritoneAPI.getSettings().preferSilkTouch.value);
    }

    /**
     * AutoTool for a specific block with precomputed ToolSet data
     *
     * @param ctx The player context
     * @param b   the blockstate to mine
     * @param ts  previously calculated ToolSet
     */
    static void switchToBestToolFor(IPlayerContext ctx, IBlockState b, ToolSet ts, boolean preferSilkTouch) {
        if (Baritone.settings().autoTool.value && !Baritone.settings().assumeExternalAutoTool.value) {
            ctx.player().inventory.currentItem = ts.getBestSlot(b.getBlock(), preferSilkTouch);
        }
    }

    static void moveTowards(IPlayerContext ctx, MovementState state, BlockPos pos) {
        state.setTarget(new MovementTarget(
                RotationUtils.calcRotationFromVec3(ctx.playerHead(),
                        VecUtils.getBlockPosCenter(pos),
                        ctx.playerRotations()).withPitch(ctx.playerRotations().getPitch()),
                false
        )).setInput(Input.MOVE_FORWARD, true);
    }

    static void moveTowardsWithoutRotation(IPlayerContext ctx, MovementState state, float idealYaw) {
        MovementOption.getOptions(
                MathHelper.sin(ctx.playerRotations().getYaw() * DEG_TO_RAD_F),
                MathHelper.cos(ctx.playerRotations().getYaw() * DEG_TO_RAD_F),
                Baritone.settings().allowSprint.value
        ).min(Comparator.comparing(option -> option.distanceToSq(
                MathHelper.sin(idealYaw * DEG_TO_RAD_F),
                MathHelper.cos(idealYaw * DEG_TO_RAD_F)
        ))).ifPresent(selection -> selection.setInputs(state));
    }

    static void moveTowardsWithoutRotation(IPlayerContext ctx, MovementState state, BlockPos dest) {
        float idealYaw = RotationUtils.calcRotationFromVec3(
                ctx.playerHead(),
                VecUtils.getBlockPosCenter(dest),
                ctx.playerRotations()
        ).getYaw();
        moveTowardsWithoutRotation(ctx, state, idealYaw);
    }

    static void moveTowardsWithSlightRotation(IPlayerContext ctx, MovementState state, BlockPos dest) {
        float idealYaw = RotationUtils.calcRotationFromVec3(
                ctx.playerHead(),
                VecUtils.getBlockPosCenter(dest),
                ctx.playerRotations()
        ).getYaw();
        float distance = Rotation.yawDistanceFromOffset(ctx.playerRotations().getYaw(), idealYaw) % 45f;
        float newYaw = distance > 0f ?
                distance > 22.5f ? distance - 45f : distance :
                distance < -22.5f ? distance + 45f : distance;
        state.setTarget(new MovementTarget(new Rotation(
                ctx.playerRotations().getYaw() - newYaw,
                ctx.playerRotations().getPitch()
        ), true));
        moveTowardsWithoutRotation(ctx, state, idealYaw);
    }

    /**
     * Returns whether or not the specified block is
     * water, regardless of whether or not it is flowing.
     *
     * @param state The block state
     * @return Whether or not the block is water
     */
    static boolean isWater(IBlockState state) {
        return MovementPlatform189.isWater(state);
    }

    /**
     * Returns whether or not the block at the specified pos is
     * water, regardless of whether or not it is flowing.
     *
     * @param ctx The player context
     * @param bp  The block pos
     * @return Whether or not the block is water
     */
    static boolean isWater(IPlayerContext ctx, BlockPos bp) {
        return isWater(BlockStateInterface.get(ctx, bp));
    }

    static boolean isLava(IBlockState state) {
        return MovementPlatform189.isLava(state);
    }

    /**
     * Returns whether or not the specified pos has a liquid
     *
     * @param ctx The player context
     * @param p   The pos
     * @return Whether or not the block is a liquid
     */
    static boolean isLiquid(IPlayerContext ctx, BlockPos p) {
        return isLiquid(BlockStateInterface.get(ctx, p));
    }

    static boolean isLiquid(IBlockState blockState) {
        return MovementPlatform189.isLiquid(blockState);
    }

    static boolean possiblyFlowing(IBlockState state) {
        return MovementPlatform189.isLiquid(state) && MovementPlatform189.fluidAmount(state) != 8;
    }

    static boolean isFlowing(int x, int y, int z, IBlockState state, BlockStateInterface bsi) {
        if (!MovementPlatform189.isLiquid(state)) return false;
        if (MovementPlatform189.fluidAmount(state) != 8) return true;
        return possiblyFlowing(bsi.get0(x + 1, y, z))
                || possiblyFlowing(bsi.get0(x - 1, y, z))
                || possiblyFlowing(bsi.get0(x, y, z + 1))
                || possiblyFlowing(bsi.get0(x, y, z - 1));
    }

    static boolean isBlockNormalCube(IBlockState state) {
        Block block = state.getBlock();
        if (false
                || block instanceof BlockPistonMoving) {
            return false;
        }
        try {
            return MovementPlatform189.isFullCube(state);
        } catch (Exception ignored) {
            // if we can't get the collision shape, assume it's bad and add to blocksToAvoid
        }
        return false;
    }

    static PlaceResult attemptToPlaceABlock(MovementState state, IBaritone baritone, BlockPos placeAt, boolean preferDown, boolean wouldSneak) {
        IPlayerContext ctx = baritone.getPlayerContext();
        Optional<Rotation> direct = RotationUtils.reachable(ctx, placeAt, wouldSneak); // we assume that if there is a block there, it must be replacable
        boolean found = false;
        if (direct.isPresent()) {
            state.setTarget(new MovementTarget(direct.get(), true));
            found = true;
        }
        for (int i = 0; i < 5; i++) {
            BlockPos against1 = placeAt.offset(HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i]);
            if (MovementHelper.canPlaceAgainst(ctx, against1)) {
                if (!((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(false, placeAt.getX(), placeAt.getY(), placeAt.getZ())) { // get ready to place a throwaway block
                    Helper.HELPER.logDebug("bb pls get me some blocks. dirt, netherrack, cobble");
                    state.setStatus(MovementStatus.UNREACHABLE);
                    return PlaceResult.NO_OPTION;
                }
                double faceX = (placeAt.getX() + against1.getX() + 1.0D) * 0.5D;
                double faceY = (placeAt.getY() + against1.getY() + 0.5D) * 0.5D;
                double faceZ = (placeAt.getZ() + against1.getZ() + 1.0D) * 0.5D;
                Rotation place = RotationUtils.calcRotationFromVec3(wouldSneak ? RayTraceUtils.inferSneakingEyePosition(ctx.player()) : ctx.playerHead(), new Vec3(faceX, faceY, faceZ), ctx.playerRotations());
                Rotation actual = baritone.getLookBehavior().getAimProcessor().peekRotation(place);
                MovingObjectPosition res = RayTraceUtils.rayTraceTowards(ctx.player(), actual, ctx.playerController().getBlockReachDistance(), wouldSneak);
                if (res != null && res.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && ((MovingObjectPosition) res).getBlockPos().equals(against1) && ((MovingObjectPosition) res).getBlockPos().offset(res.sideHit).equals(placeAt)) {
                    state.setTarget(new MovementTarget(place, true));
                    found = true;

                    if (!preferDown) {
                        // if preferDown is true, we want the last option
                        // if preferDown is false, we want the first
                        break;
                    }
                }
            }
        }
        if (ctx.getSelectedBlock().isPresent()) {
            BlockPos selectedBlock = ctx.getSelectedBlock().get();
            EnumFacing side = ctx.objectMouseOver().sideHit;
            // only way for selectedBlock.equals(placeAt) to be true is if it's replaceable
            if (selectedBlock.equals(placeAt) || (MovementHelper.canPlaceAgainst(ctx, selectedBlock) && selectedBlock.offset(side).equals(placeAt))) {
                if (wouldSneak) {
                    state.setInput(Input.SNEAK, true);
                }
                ((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(true, placeAt.getX(), placeAt.getY(), placeAt.getZ());
                return PlaceResult.READY_TO_PLACE;
            }
        }
        if (found) {
            if (wouldSneak) {
                state.setInput(Input.SNEAK, true);
            }
            ((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(true, placeAt.getX(), placeAt.getY(), placeAt.getZ());
            return PlaceResult.ATTEMPTING;
        }
        return PlaceResult.NO_OPTION;
    }

    enum PlaceResult {
        READY_TO_PLACE, ATTEMPTING, NO_OPTION;
    }

    static boolean isTransparent(Block b) {

        return b instanceof BlockAir ||
                MovementPlatform189.isLava(b) ||
                MovementPlatform189.isWater(b);
    }

    static List<BetterBlockPos> steppingOnBlocks(IPlayerContext ctx) {
        List<BetterBlockPos> blocks = new ArrayList<>();
        BlockPos feet = new BlockPos(ctx.player());
        for (byte x = -1; x <= 1; x++) {
            for (byte z = -1; z <= 1; z++) {
                if (ctx.player().getEntityBoundingBox().intersectsWith(new net.minecraft.util.AxisAlignedBB(
                        feet.getX() + x, feet.getY(), feet.getZ() + z,
                        feet.getX() + x + 1, feet.getY() + 1, feet.getZ() + z + 1))) {
                    blocks.add(new BetterBlockPos(feet.getX() + x, feet.getY() - 1, feet.getZ() + z));
                }
            }
        }
        return blocks;
    }
    public static boolean isWater(Block block) { return MovementPlatform189.isWater(block); }
    public static boolean isLava(Block block) { return MovementPlatform189.isLava(block); }
    public static boolean avoidWalkingInto(Block block) { return avoidWalkingInto(block.getDefaultState()); }

}
