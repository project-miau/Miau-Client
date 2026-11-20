package idle.miau.utility.action;

import idle.miau.helper.ReflectionHelper;
import net.minecraft.block.Block;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.lang.reflect.Field;

public final class ActionAccess {
    private static final Field PRESSES = ReflectionHelper.findField(KeyBinding.class, "pressTime");

    private ActionAccess() {
    }

    public static void increment(KeyBinding key) {
        try {
            PRESSES.setInt(key, PRESSES.getInt(key) + 1);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }

    public static boolean isBlock(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item == null) {
            return false;
        }
        Block block = Block.getBlockFromItem(item);
        return block != null && Block.getIdFromBlock(block) != 0;
    }

    public static Vec3 adjacentPosition(MovingObjectPosition hit) {
        BlockPos block = hit.getBlockPos();
        EnumFacing face = hit.sideHit;
        return new Vec3((double)block.getX() + face.getFrontOffsetX(),
                (double)block.getY() + face.getFrontOffsetY(),
                (double)face.getFrontOffsetZ() + block.getZ());
    }
}
