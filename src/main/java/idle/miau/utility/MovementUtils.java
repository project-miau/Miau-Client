package idle.miau.utility;

import idle.miau.event.PrePlayerInputEvent;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public final class MovementUtils implements IMinecraftInstance {
    private static final float[] FIXED_FORWARD =
            {-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, -1.0F};
    private static final float[] FIXED_STRAFE =
            {0.0F, 1.0F, 1.0F, 1.0F, 0.0F, -1.0F, -1.0F, -1.0F};

    private MovementUtils() {
    }

    public static boolean isForwardPressed() {
        return mc.gameSettings.keyBindForward.isKeyDown() != mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown() != mc.gameSettings.keyBindRight.isKeyDown();
    }

    public static void fixStrafe(PrePlayerInputEvent event, float targetYaw) {
        int forward = (mc.gameSettings.keyBindForward.isKeyDown() ? 1 : 0)
                - (mc.gameSettings.keyBindBack.isKeyDown() ? 1 : 0);
        int strafe = (mc.gameSettings.keyBindLeft.isKeyDown() ? 1 : 0)
                - (mc.gameSettings.keyBindRight.isKeyDown() ? 1 : 0);
        float moveYaw = adjustYaw(mc.thePlayer.rotationYaw, forward, strafe);
        float angle = MathHelper.wrapAngleTo180_float(moveYaw - targetYaw + 22.5F);
        int index = (int) (angle + 180.0F) / 45 % 8;
        event.setForward(FIXED_FORWARD[index]);
        event.setStrafe(FIXED_STRAFE[index]);
    }

    public static float adjustYaw(float yaw, float forward, float strafe) {
        if (forward < 0.0F) {
            yaw += 180.0F;
        }
        if (strafe != 0.0F) {
            float multiplier = forward == 0.0F ? 1.0F : 0.5F * Math.signum(forward);
            yaw += -90.0F * multiplier * Math.signum(strafe);
        }
        return MathHelper.wrapAngleTo180_float(yaw);
    }

    public static int getForwardValue() {
        int forwardValue = 0;
        if (mc.gameSettings.keyBindForward.isKeyDown()) {
            forwardValue++;
        }
        if (mc.gameSettings.keyBindBack.isKeyDown()) {
            forwardValue--;
        }
        return forwardValue;
    }

    public static int getLeftValue() {
        int leftValue = 0;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) {
            leftValue++;
        }
        if (mc.gameSettings.keyBindRight.isKeyDown()) {
            leftValue--;
        }
        return leftValue;
    }

    public static float getAllowedHorizontalDistance() {
        float slipperiness = mc.thePlayer.worldObj.getBlockState(new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ))).getBlock().slipperiness * 0.91F;
        return mc.thePlayer.getAIMoveSpeed()
                * (0.16277136F / (slipperiness * slipperiness * slipperiness));
    }

    public static double[] predictMovement() {
        float strafeInput = getLeftValue() * 0.98F;
        float forwardInput = getForwardValue() * 0.98F;
        float inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput;
        if (inputMagnitude < 1.0E-4F) {
            return new double[]{0.0D, 0.0D};
        }
        inputMagnitude = MathHelper.sqrt_float(inputMagnitude);
        if (inputMagnitude < 1.0F) {
            inputMagnitude = 1.0F;
        }
        inputMagnitude = getAllowedHorizontalDistance() / inputMagnitude;
        float sinYaw = MathHelper.sin(mc.thePlayer.rotationYaw * (float) Math.PI / 180.0F);
        float cosYaw = MathHelper.cos(mc.thePlayer.rotationYaw * (float) Math.PI / 180.0F);
        strafeInput *= inputMagnitude;
        forwardInput *= inputMagnitude;
        return new double[]{
                strafeInput * cosYaw - forwardInput * sinYaw,
                forwardInput * cosYaw + strafeInput * sinYaw
        };
    }

    public static boolean getOnGroundPosBased(
            AxisAlignedBB initialBoundingBox, double horizontalShrink) {
        AxisAlignedBB contracted = initialBoundingBox.contract(
                horizontalShrink, 0.0D, horizontalShrink);
        AxisAlignedBB groundBox = new AxisAlignedBB(
                contracted.minX, contracted.minY - 0.1D, contracted.minZ,
                contracted.maxX, contracted.minY, contracted.maxZ);
        return mc.theWorld.checkBlockCollision(groundBox);
    }

    public static int getTicksForGround(EntityLivingBase entity) {
        int ticks = 12;
        double positionY = entity.posY;
        double lastMotionY = entity.posY - entity.prevPosY;
        for (int index = 0; index < 12; index++) {
            double motionY = (lastMotionY - 0.08D) * 0.98D;
            positionY += motionY;
            if (getOnGroundPosBased(entity.getEntityBoundingBox().offset(
                    0.0D, positionY - entity.posY, 0.0D), 0.0D)) {
                ticks = index;
                break;
            }
            lastMotionY = motionY;
        }
        return ticks;
    }
}
