package baritone.compat;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/** Version-specific conversions kept outside Minecraft's public API. */
public final class Minecraft189 {
    private Minecraft189() {}
    public static boolean isEmpty(ItemStack stack) { return stack == null || stack.stackSize <= 0 || stack.getItem() == null; }
    public static Item item(ItemStack stack) { return stack == null ? null : stack.getItem(); }
    public static int count(ItemStack stack) { return isEmpty(stack) ? 0 : stack.stackSize; }
    public static int hotbarSlot(InventoryPlayer inventory, ItemStack wanted) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.mainInventory[i];
            if (!isEmpty(stack) && !isEmpty(wanted) && stack.getItem() == wanted.getItem()
                    && stack.getMetadata() == wanted.getMetadata()) return i;
        }
        return -1;
    }
    public static boolean isHotbar(int slot) { return slot >= 0 && slot < 9; }
    public static Class<? extends Entity> entityClass(ResourceLocation id) {
        for (String name : EntityList.getEntityNameList()) {
            if (name.equalsIgnoreCase(id.getResourcePath()) || name.replace("_", "").equalsIgnoreCase(id.getResourcePath().replace("_", ""))) {
                return EntityList.getClassFromID(EntityList.getIDFromString(name));
            }
        }
        return null;
    }
    public static int itemHash(ItemStack stack) {
        return stack == null || stack.getItem() == null ? -1 : stack.getItem().hashCode() + stack.getItemDamage();
    }
    public static ResourceLocation entityKey(Class<? extends Entity> type) {
        for (String name : EntityList.getEntityNameList()) {
            if (EntityList.getClassFromID(EntityList.getIDFromString(name)) == type) return new ResourceLocation(name);
        }
        return null;
    }
    public static ResourceLocation entityKey(Entity entity) {
        String name = EntityList.getEntityString(entity);
        return name == null ? null : new ResourceLocation(name);
    }
    public static net.minecraft.world.chunk.Chunk loadedChunk(net.minecraft.world.chunk.IChunkProvider provider, int x, int z) {
        net.minecraft.world.chunk.Chunk chunk = provider.provideChunk(x, z);
        return chunk == null || chunk instanceof net.minecraft.world.chunk.EmptyChunk ? null : chunk;
    }
    public static boolean canFallThrough(IBlockState state) {
        net.minecraft.block.material.Material material = state.getBlock().getMaterial();
        return state.getBlock() == net.minecraft.init.Blocks.fire || material == net.minecraft.block.material.Material.air
                || material == net.minecraft.block.material.Material.water || material == net.minecraft.block.material.Material.lava;
    }
    public static AxisAlignedBB selectedBounds(IBlockState state, World world, BlockPos pos) {
        return bounds(state, world, pos).offset(pos.getX(), pos.getY(), pos.getZ());
    }
    public static void renderBeamSegment(double x, double y, double z, float partialTicks, double scale,
                                         long ticks, int offset, int height, float[] color) {
        // Use raw GL inside an attribute scope so Minecraft's cached state stays valid.
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ENABLE_BIT
                | org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT
                | org.lwjgl.opengl.GL11.GL_TEXTURE_BIT);
        try {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(770, 771);
            org.lwjgl.opengl.GL11.glColor4f(color[0], color[1], color[2], 0.75F);
            double scroll = -((ticks + partialTicks) * 0.025) % 1.0;
            double lo = y + offset, hi = lo + height * scale;
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            double[][] corners = {{0.2, 0.2}, {0.8, 0.2}, {0.8, 0.8}, {0.2, 0.8}};
            for (int i = 0; i < 4; i++) {
                double[] a = corners[i], b = corners[(i + 1) % 4];
                org.lwjgl.opengl.GL11.glTexCoord2d(0, scroll); org.lwjgl.opengl.GL11.glVertex3d(x+a[0], lo, z+a[1]);
                org.lwjgl.opengl.GL11.glTexCoord2d(0, height * 2.5 + scroll); org.lwjgl.opengl.GL11.glVertex3d(x+a[0], hi, z+a[1]);
                org.lwjgl.opengl.GL11.glTexCoord2d(1, height * 2.5 + scroll); org.lwjgl.opengl.GL11.glVertex3d(x+b[0], hi, z+b[1]);
                org.lwjgl.opengl.GL11.glTexCoord2d(1, scroll); org.lwjgl.opengl.GL11.glVertex3d(x+b[0], lo, z+b[1]);
            }
            org.lwjgl.opengl.GL11.glEnd();
        } finally {
            org.lwjgl.opengl.GL11.glPopAttrib();
        }
    }
    public static AxisAlignedBB bounds(IBlockState state, World world, BlockPos position) {
        Block block = state.getBlock();
        synchronized (block) {
            try {
                block.setBlockBoundsBasedOnState(world, position);
                return new AxisAlignedBB(block.getBlockBoundsMinX(), block.getBlockBoundsMinY(), block.getBlockBoundsMinZ(),
                        block.getBlockBoundsMaxX(), block.getBlockBoundsMaxY(), block.getBlockBoundsMaxZ());
            } finally {
                block.setBlockBoundsForItemRender();
            }
        }
    }
}
