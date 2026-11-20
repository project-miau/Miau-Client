package idle.miau.module.impl.combat;

import idle.miau.event.PreAttackEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public final class HitSelect extends Module {
    private static final String[] MODES = {"Burst", "Criticals"};
    private static final int BURST_MODE = 0;
    private static final int CRITICALS_MODE = 1;

    private static final double EYE_HEIGHT = (double) 1.62F;
    private static final double AIR_MOVEMENT_FACTOR = 0.026D;
    private static final double MAX_BURST_DISTANCE_SQUARED = 16.0D;
    private static final float BURST_YAW_CONE = 20.0F;
    private static final float AIR_SWING_CANCEL_CHANCE = 0.9F;

    private final SliderSetting mode;
    private final SliderSetting pauseDuration;
    private final ButtonSetting fakeSwing;
    private final ButtonSetting reduceAirSwings;

    public HitSelect() {
        super("Hit Select", category.combat);
        setDescription("Times attacks around damage and hit trades.");
        mode = new SliderSetting("Mode", BURST_MODE, MODES);
        pauseDuration = new SliderSetting(
                "Pause duration", "ms", 200.0D, 50.0D, 500.0D, 50.0D);
        fakeSwing = new ButtonSetting("Fake swing", false);
        reduceAirSwings = new ButtonSetting("Reduce air swings", false);
        registerSetting(mode);
        registerSetting(pauseDuration);
        registerSetting(fakeSwing);
        registerSetting(reduceAirSwings);
        closetModule = true;
    }

    public SliderSetting mode() {
        return mode;
    }

    public SliderSetting pauseDuration() {
        return pauseDuration;
    }

    public ButtonSetting fakeSwing() {
        return fakeSwing;
    }

    public ButtonSetting reduceAirSwings() {
        return reduceAirSwings;
    }

    @Override
    public String getInfo() {
        int selected = modeIndex();
        return selected >= 0 && selected < MODES.length ? MODES[selected] : "";
    }

    @Subscribe(priority = Priority.LOW)
    public void onPreAttack(PreAttackEvent event) {
        if (!Utils.nullCheck() || mc.thePlayer.isDead) {
            return;
        }

        if (!shouldCancel(event.objectMouseOver)) {
            return;
        }

        event.setCanceled(true);
        if (fakeSwing.isToggled() && !mc.thePlayer.isUsingItem()) {
            mc.thePlayer.swingItem();
        }
    }

    private boolean shouldCancel(MovingObjectPosition mouseOver) {
        boolean enterSelection = mouseOver == null;
        if (mouseOver != null) {
            if (mouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                return false;
            }

            if (mouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                Vec3 hit = mouseOver.hitVec;
                if (hit == null) {
                    hit = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                }
                if (fullMotionMovesFarther(hit)) {
                    return false;
                }
                enterSelection = true;
            } else {
                enterSelection = true;
            }
        }

        if (!enterSelection) {
            return false;
        }

        if (reduceAirSwings.isToggled()
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
            return ThreadLocalRandom.current().nextFloat() < AIR_SWING_CANCEL_CHANCE;
        }

        int selectedMode = modeIndex();
        if (selectedMode == CRITICALS_MODE) {
            return criticalsShouldPause();
        }
        if (selectedMode != BURST_MODE) {
            return false;
        }
        return burstShouldPause();
    }

    private boolean fullMotionMovesFarther(Vec3 hit) {
        final EntityPlayer player = mc.thePlayer;
        final float movementFactor = player.onGround
                ? player.getAIMoveSpeed() : (float) AIR_MOVEMENT_FACTOR;
        final double[] movement = movementContribution(
                player.moveStrafing, player.moveForward,
                movementFactor, player.rotationYaw);
        final double eyeY = player.posY + EYE_HEIGHT;

        final double full = distanceSquared(
                player.posX + player.motionX + movement[0],
                eyeY + player.motionY,
                player.posZ + player.motionZ + movement[1],
                hit.xCoord, hit.yCoord, hit.zCoord);
        final double damped = distanceSquared(
                player.posX + player.motionX * 0.6D + movement[0],
                eyeY + player.motionY,
                player.posZ + player.motionZ * 0.6D + movement[1],
                hit.xCoord, hit.yCoord, hit.zCoord);
        return full > damped;
    }

    static double[] movementContribution(float strafe, float forward,
                                         float movementFactor, float yaw) {
        strafe *= 0.9800000190734863F;
        forward *= 0.9800000190734863F;
        final float magnitudeSquared = strafe * strafe + forward * forward;
        if (magnitudeSquared < 0.00009999999747378752F) {
            return new double[]{0.0D, 0.0D};
        }

        final float magnitude = (float) Math.sqrt(magnitudeSquared);
        float scale = movementFactor * 0.9999998211860657F;
        if (!(magnitude < 1.0F)) {
            scale /= magnitude;
        }
        strafe *= scale;
        forward *= scale;

        final float radians = (float) ((double) yaw * Math.PI / 180.0D);
        final float sine = (float) Math.sin(radians);
        final float cosine = (float) Math.cos(radians);
        final float movementX = strafe * cosine - forward * sine;
        final float movementZ = forward * cosine + sine * strafe;
        return new double[]{(double) movementX, (double) movementZ};
    }

    private boolean criticalsShouldPause() {
        final EntityPlayer player = mc.thePlayer;
        return !player.onGround && player.prevPosY < player.posY;
    }

    private boolean burstShouldPause() {
        final EntityPlayer local = mc.thePlayer;
        int bestHurtTime = 0;
        double bestDistance = MAX_BURST_DISTANCE_SQUARED;

        if (mc.theWorld != null) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (!(entity instanceof EntityPlayer)
                        || !validBurstCandidate(local, (EntityPlayer) entity)) {
                    continue;
                }

                final EntityPlayer candidate = (EntityPlayer) entity;
                final double distance = distanceSquared(
                        local.posX, local.posY + EYE_HEIGHT, local.posZ,
                        candidate.posX, candidate.posY, candidate.posZ);
                if (distance > bestDistance) {
                    continue;
                }

                final float targetYaw = yawTo(
                        local.posX, local.posZ, candidate.posX, candidate.posZ);
                if (Math.abs(wrapDegrees(targetYaw - local.rotationYaw)) > BURST_YAW_CONE) {
                    continue;
                }

                bestDistance = distance;
                bestHurtTime = candidate.hurtTime;
            }
        }

        final double pauseTicks = pauseDuration.getInput() / 50.0D;
        return bestHurtTime > 9.0D - pauseTicks;
    }

    private boolean validBurstCandidate(EntityPlayer local, EntityPlayer candidate) {
        if (local == null || candidate == null || candidate == local || candidate.isDead
                || !candidate.isEntityAlive()) {
            return false;
        }
        if (Utils.isFriended(candidate)) {
            return false;
        }
        return ModuleManager.antiBot == null
                || !ModuleManager.antiBot.isEnabled()
                || !AntiBot.isBot(candidate);
    }

    private static double distanceSquared(double ax, double ay, double az,
                                           double bx, double by, double bz) {
        final double dx = ax - bx;
        final double dy = ay - by;
        final double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static float yawTo(double fromX, double fromZ, double toX, double toZ) {
        return (float) (Math.atan2(toZ - fromZ, toX - fromX)
                * 180.0D / Math.PI - 90.0D);
    }

    static float wrapDegrees(float angle) {
        final float result = angle % 360.0F;
        if (result >= 180.0F) {
            return result - 360.0F;
        }
        if (result < -180.0F) {
            return result + 360.0F;
        }
        return result;
    }

    private int modeIndex() {
        final double value;
        try {
            value = mode.getInput();
        } catch (RuntimeException malformedValue) {
            return -1;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return -1;
        }
        if (value != Math.rint(value)) {
            return -1;
        }
        return (int) value;
    }
}
