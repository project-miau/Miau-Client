package keystrokesmod.utility;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

public class BlockUtils extends idle.miau.utility.BlockUtils {
    public static Block getBlock(BlockPos blockPos) {
        return getBlockState(blockPos).getBlock();
    }

    public static Block getBlock(double x, double y, double z) {
        return getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    public static Block getBlock(Vec3 position) {
        return getBlockState(new BlockPos(position.xCoord, position.yCoord, position.zCoord)).getBlock();
    }

    public static IBlockState getBlockState(BlockPos blockPos) {
        if (mc.theWorld == null || blockPos == null) {
            return Blocks.air.getDefaultState();
        }
        return mc.theWorld.getBlockState(blockPos);
    }
}
