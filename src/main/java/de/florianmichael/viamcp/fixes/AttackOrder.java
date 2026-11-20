/*
 * This file is derived from ViaMCP and is licensed under GPL-3.0.
 * https://github.com/ViaVersionMCP/ViaMCP
 */
package de.florianmichael.viamcp.fixes;

import de.florianmichael.viamcp.ViaMCP;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

public final class AttackOrder {
    private AttackOrder() {
    }

    public static void sendConditionalSwing(Minecraft mc, MovingObjectPosition hit) {
        if (hit != null && hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            mc.thePlayer.swingItem();
        }
    }

    public static void sendFixedAttack(Minecraft mc, EntityPlayer player, Entity target) {
        if (!ViaMCP.isTargetVersionNewerThanNative()) {
            mc.thePlayer.swingItem();
            mc.playerController.attackEntity(player, target);
        } else {
            mc.playerController.attackEntity(player, target);
            mc.thePlayer.swingItem();
        }
    }
}
