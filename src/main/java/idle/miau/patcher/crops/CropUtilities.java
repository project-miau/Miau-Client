package idle.miau.patcher.crops;

import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockCarrot;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockPotato;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

public class CropUtilities {
    public static final AxisAlignedBB[] CARROT_POTATO_BOX = {
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.125D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.1875D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.25D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.3125D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.375D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.4375D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5625D, 1.0D)
    };

    public static final AxisAlignedBB[] WHEAT_BOX = {
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.125D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.25D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.375D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.625D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.75D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.875D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)
    };

    public static final AxisAlignedBB[] NETHER_WART_BOX = {
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.3125D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.6875D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.875D, 1.0D)
    };

    public static final AxisAlignedBB CACTUS_BOX = new AxisAlignedBB(0.0625D, 0.0D, 0.0625D, 0.9375D, 1.0D, 0.9375D);

    public static AxisAlignedBB getCropSelectedBox(BlockPos pos, IBlockState state) {
        int age = state.getValue(BlockCrops.AGE);
        if (age < 0) age = 0;
        if (age > 7) age = 7;
        AxisAlignedBB box = (state.getBlock() instanceof BlockPotato || state.getBlock() instanceof BlockCarrot)
                ? CARROT_POTATO_BOX[age]
                : WHEAT_BOX[age];
        return new AxisAlignedBB(
                pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ,
                pos.getX() + box.maxX, pos.getY() + box.maxY, pos.getZ() + box.maxZ
        );
    }

    public static AxisAlignedBB getNetherWartSelectedBox(BlockPos pos, IBlockState state) {
        int age = state.getValue(BlockNetherWart.AGE);
        if (age < 0) age = 0;
        if (age > 3) age = 3;
        AxisAlignedBB box = NETHER_WART_BOX[age];
        return new AxisAlignedBB(
                pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ,
                pos.getX() + box.maxX, pos.getY() + box.maxY, pos.getZ() + box.maxZ
        );
    }
}
