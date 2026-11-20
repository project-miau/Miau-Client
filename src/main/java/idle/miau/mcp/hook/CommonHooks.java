package idle.miau.mcp.hook;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

public final class CommonHooks {
    private CommonHooks() {
    }

    public static boolean onPlayerAttackTarget(EntityPlayer player, Entity target) {
        return true;
    }

    public static void onLivingJump(EntityLivingBase entity) {
    }
}
