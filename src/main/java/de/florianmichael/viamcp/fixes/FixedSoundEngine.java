/*
 * This file is derived from ViaMCP and is licensed under GPL-3.0.
 * https://github.com/ViaVersionMCP/ViaMCP
 */
package de.florianmichael.viamcp.fixes;

import de.florianmichael.viamcp.ViaMCP;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

public final class FixedSoundEngine {
    private FixedSoundEngine() {
    }

    public static boolean destroyBlock(World world, BlockPos pos, boolean dropBlock) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        world.playAuxSFX(2001, pos, Block.getStateId(state));
        if (block.getMaterial() == Material.air) {
            return false;
        }
        if (dropBlock) {
            block.dropBlockAsItem(world, pos, state, 0);
        }
        return world.setBlockState(pos, Blocks.air.getDefaultState(), 3);
    }

    public static boolean onItemUse(ItemBlock item, ItemStack stack, EntityPlayer player,
                                    World world, BlockPos pos, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        IBlockState state = world.getBlockState(pos);
        if (!state.getBlock().isReplaceable(world, pos)) {
            pos = pos.offset(side);
        }
        if (stack.stackSize == 0 || !player.canPlayerEdit(pos, side, stack)) {
            return false;
        }
        if (!world.canBlockBePlaced(item.getBlock(), pos, false, side, (Entity) null, stack)) {
            return false;
        }

        int metadata = item.getMetadata(stack.getMetadata());
        IBlockState placed = item.getBlock().onBlockPlaced(world, pos, side, hitX, hitY, hitZ,
                metadata, player);
        if (world.setBlockState(pos, placed, 3)) {
            placed = world.getBlockState(pos);
            if (placed.getBlock() == item.getBlock()) {
                ItemBlock.setTileEntityNBT(world, player, pos, stack);
                item.getBlock().onBlockPlacedBy(world, pos, placed, player, stack);
            }

            Block.SoundType sound = item.getBlock().stepSound;
            if (ViaMCP.isTargetVersionNewerThanNative() && Minecraft.getMinecraft().theWorld != null) {
                Minecraft.getMinecraft().theWorld.playSoundAtPos(pos.add(0.5D, 0.5D, 0.5D),
                        sound.getPlaceSound(), (sound.getVolume() + 1.0F) / 2.0F,
                        sound.getFrequency() * 0.8F, false);
            } else {
                world.playSoundEffect(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        sound.getPlaceSound(), (sound.getVolume() + 1.0F) / 2.0F,
                        sound.getFrequency() * 0.8F);
            }
            --stack.stackSize;
        }
        return true;
    }
}
