package idle.miau.utility;

import idle.miau.mcp.McpRuntime;
import idle.miau.module.impl.player.Scaffold;
import idle.miau.event.EventMovementInput;
import idle.miau.helper.SilentRotationManager;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public final class ScaffoldUtil implements IMinecraftInstance {
    private ScaffoldUtil() {
    }

    public static Block block(double x, double y, double z) {
        return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    public static boolean willFallNextTick() {
        return willFallNextTick(1.0D);
    }

    public static AxisAlignedBB getPredictedBoundingBox(double precision) {
        EntityPlayerSP player = mc.thePlayer;
        SilentRotationManager rotations = SilentRotationManager.getInstance();
        double motionX = player.motionX;
        double motionZ = player.motionZ;
        float moveForward = 0.0F;
        float moveStrafe = 0.0F;
        GameSettings settings = mc.gameSettings;

        if (settings.keyBindForward.isKeyDown()) {
            moveForward++;
        }
        if (settings.keyBindBack.isKeyDown()) {
            moveForward--;
        }
        if (settings.keyBindLeft.isKeyDown()) {
            moveStrafe++;
        }
        if (settings.keyBindRight.isKeyDown()) {
            moveStrafe--;
        }

        EventMovementInput event = new EventMovementInput(
                moveForward, moveStrafe, settings.keyBindJump.isKeyDown());
        McpRuntime.EVENTS.post(event);
        if (event.isCanceled()) {
            moveStrafe = 0.0F;
            moveForward = 0.0F;
        }
        else {
            moveForward = event.getSpeed();
            moveStrafe = event.getStrafe();
        }

        motionX *= 0.98D;
        motionZ *= 0.98D;
        if (Math.abs(motionX) < 0.005D) {
            motionX = 0.0D;
        }
        if (Math.abs(motionZ) < 0.005D) {
            motionZ = 0.0D;
        }
        moveStrafe *= 0.98F;
        moveForward *= 0.98F;

        float friction = 0.91F;
        if (player.onGround) {
            BlockPos below = new BlockPos(
                    MathHelper.floor_double(player.posX),
                    MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1,
                    MathHelper.floor_double(player.posZ));
            friction = player.worldObj.getBlockState(below).getBlock().slipperiness * 0.91F;
        }

        float acceleration = 0.16277136F / (friction * friction * friction);
        float moveSpeed = player.getAIMoveSpeed() * acceleration;
        float magnitude = moveStrafe * moveStrafe + moveForward * moveForward;
        if (magnitude >= 1.0E-4F) {
            magnitude = MathHelper.sqrt_float(magnitude);
            if (magnitude < 1.0F) {
                magnitude = 1.0F;
            }
            magnitude = moveSpeed / magnitude;
            moveStrafe *= magnitude;
            moveForward *= magnitude;
            float sine = MathHelper.sin(rotations.getYaw() * (float) Math.PI / 180.0F);
            float cosine = MathHelper.cos(rotations.getYaw() * (float) Math.PI / 180.0F);
            motionX += moveStrafe * cosine - moveForward * sine;
            motionZ += moveForward * cosine + moveStrafe * sine;
        }

        return player.getEntityBoundingBox().offset(
                motionX * precision, 0.0D, motionZ * precision);
    }

    public static AxisAlignedBB getPredictedBoundingBox(
            double precision, float movementYaw,
            float moveForward, float moveStrafe) {
        EntityPlayerSP player = mc.thePlayer;
        double motionX = player.motionX;
        double motionZ = player.motionZ;
        if (Math.abs(motionX) < 0.005D) {
            motionX = 0.0D;
        }
        if (Math.abs(motionZ) < 0.005D) {
            motionZ = 0.0D;
        }
        moveStrafe *= 0.98F;
        moveForward *= 0.98F;

        float friction = 0.91F;
        if (player.onGround) {
            BlockPos below = new BlockPos(
                    MathHelper.floor_double(player.posX),
                    MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1,
                    MathHelper.floor_double(player.posZ));
            friction = player.worldObj.getBlockState(below).getBlock().slipperiness * 0.91F;
        }

        float acceleration = 0.16277136F / (friction * friction * friction);
        float moveSpeed = player.getAIMoveSpeed() * acceleration;
        float magnitude = moveStrafe * moveStrafe + moveForward * moveForward;
        if (magnitude >= 1.0E-4F) {
            magnitude = MathHelper.sqrt_float(magnitude);
            if (magnitude < 1.0F) {
                magnitude = 1.0F;
            }
            magnitude = moveSpeed / magnitude;
            moveStrafe *= magnitude;
            moveForward *= magnitude;
            float sine = MathHelper.sin(movementYaw * (float) Math.PI / 180.0F);
            float cosine = MathHelper.cos(movementYaw * (float) Math.PI / 180.0F);
            motionX += moveStrafe * cosine - moveForward * sine;
            motionZ += moveForward * cosine + moveStrafe * sine;
        }

        return player.getEntityBoundingBox().offset(
                motionX * precision, 0.0D, motionZ * precision);
    }

    public static boolean willFallNextTick(double precision) {
        AxisAlignedBB predicted = getPredictedBoundingBox(precision)
                .offset(0.0D, -0.5D, 0.0D);
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, predicted).isEmpty();
    }

    public static boolean willFallNextTick(
            double precision, float movementYaw,
            float moveForward, float moveStrafe) {
        AxisAlignedBB predicted = getPredictedBoundingBox(
                precision, movementYaw, moveForward, moveStrafe)
                .offset(0.0D, -0.5D, 0.0D);
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, predicted).isEmpty();
    }

    public static Vec3 getNewVector(Scaffold.BlockData data) {
        if (data == null) {
            return null;
        }
        return getNewVector(data.getPosition(), data.getFacing());
    }

    public static Vec3 getNewVector(BlockPos position, EnumFacing facing) {
        Vec3 vector = new Vec3(position);
        double first = 0.45D + Math.random() * 0.1D;
        double second = 0.45D + Math.random() * 0.1D;
        if (facing == EnumFacing.UP) {
            vector = vector.addVector(first, 1.0D, second);
        }
        else if (facing == EnumFacing.DOWN) {
            vector = vector.addVector(first, 0.0D, second);
        }
        else if (facing == EnumFacing.EAST) {
            vector = vector.addVector(1.0D, first, second);
        }
        else if (facing == EnumFacing.WEST) {
            vector = vector.addVector(0.0D, first, second);
        }
        else if (facing == EnumFacing.NORTH) {
            vector = vector.addVector(first, second, 0.0D);
        }
        else if (facing == EnumFacing.SOUTH) {
            vector = vector.addVector(first, second, 1.0D);
        }
        return vector;
    }

    public static int getBlockSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            if (stack == null || !(stack.getItem() instanceof ItemBlock) || stack.stackSize <= 0) {
                continue;
            }
            ItemBlock item = (ItemBlock) stack.getItem();
            if (isBlockValid(item.getBlock())) {
                return slot;
            }
        }
        return mc.thePlayer.inventory.currentItem;
    }

    public static boolean isBlockValid(Block block) {
        return (block.isFullBlock() || block == Blocks.glass)
                && block != Blocks.sand
                && block != Blocks.gravel
                && block != Blocks.dispenser
                && block != Blocks.command_block
                && block != Blocks.noteblock
                && block != Blocks.furnace
                && block != Blocks.crafting_table
                && block != Blocks.tnt
                && block != Blocks.dropper
                && block != Blocks.beacon;
    }
}
