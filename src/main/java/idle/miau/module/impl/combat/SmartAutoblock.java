package idle.miau.module.impl.combat;

import idle.miau.utility.CombatTargeting;
import idle.miau.utility.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

final class SmartAutoblock {
    private static final long ATTACK_WINDOW_NANOS = 100_000_000L;
    private static final long BLOCK_COOLDOWN_NANOS = 200_000_000L;
    private static final long MINIMUM_HOLD_NANOS = 100_000_000L;
    private static final long RELEASE_GRACE_NANOS = 75_000_000L;
    private long attackReference;
    private long blockReference;
    private long blockUntil;
    private long hitUntil;
    private boolean active;

    SmartAutoblock() {
        reset(System.nanoTime());
    }

    void reset(long now) {
        attackReference = now;
        blockReference = now - BLOCK_COOLDOWN_NANOS;
        blockUntil = hitUntil = now;
        active = false;
    }

    void onAttack(long now) {
        attackReference = now;
    }

    void onHit(long now, int pingMillis) {
        hitUntil = now + Math.max(0L, 400L - Math.max(0, pingMillis)) * 1_000_000L;
        blockUntil = now;
        active = false;
    }

    boolean isActive() {
        return active;
    }

    boolean update(Minecraft mc, double range, boolean ignoreTeammates, long now) {
        if (now < hitUntil) {
            active = false;
            return false;
        }
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!isThreat(mc.thePlayer, player, Math.min(4.0D, range), ignoreTeammates)) {
                continue;
            }
            boolean close = mc.thePlayer.getDistanceToEntity(player) < 3.0F;
            if (close && (now - attackReference >= ATTACK_WINDOW_NANOS
                    || !active && now - blockReference < BLOCK_COOLDOWN_NANOS)) {
                continue;
            }
            if (!active) {
                blockReference = now;
                blockUntil = now + MINIMUM_HOLD_NANOS;
            } else {
                blockUntil = Math.max(blockUntil, now + RELEASE_GRACE_NANOS);
            }
            active = true;
            return true;
        }
        active = active && now < blockUntil;
        return active;
    }

    static boolean isThreat(EntityPlayer self, EntityPlayer player, double range, boolean ignoreTeammates) {
        if (!CombatTargeting.isTrackablePlayer(player, ignoreTeammates)
                || player == self || !player.isSwingInProgress
                || player.swingProgressInt == 0 && !player.isBlocking()
                || player.isUsingItem() && !player.isBlocking()) {
            return false;
        }
        float motionX = (float) (player.posX - player.prevPosX);
        float motionZ = (float) (player.posZ - player.prevPosZ);
        double predictedX = player.posX + motionX * 4.0F;
        double predictedZ = player.posZ + motionZ * 4.0F;
        float distance = self.getDistanceToEntity(player);
        if (Math.min(self.getDistance(predictedX, player.posY, predictedZ), distance) > range) {
            return false;
        }
        if (distance <= 2.0F) {
            return true;
        }
        Vec3 eye = self.getPositionEyes(1.0F);
        float[] rotations = RotationUtils.getRotationsFromEye(player.getPositionEyes(1.0F),
                eye.xCoord, eye.yCoord, eye.zCoord);
        return rotations != null
                && Math.abs(RotationUtils.getYawDifference(rotations[0], player.rotationYaw)) <= 35.0F;
    }
}
