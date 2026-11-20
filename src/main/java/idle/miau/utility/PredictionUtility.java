package idle.miau.utility;

import idle.miau.script.model.SimulatedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovementInput;

public final class PredictionUtility {
    private static final double GRAVITY = 0.08D;
    private static final double AIR_DRAG = 0.98D;

    private PredictionUtility() {
    }

    public static AxisAlignedBB predictBox(EntityLivingBase entity, int steps) {
        PredictedMotion[] predictions = predict(entity, steps);
        return predictions[predictions.length - 1].getBoundingBox();
    }

    public static AxisAlignedBB[] predictBoxes(EntityLivingBase entity, int steps) {
        PredictedMotion[] predictions = predict(entity, steps);
        AxisAlignedBB[] boxes = new AxisAlignedBB[predictions.length];
        for (int index = 0; index < predictions.length; index++) {
            boxes[index] = predictions[index].getBoundingBox();
        }
        return boxes;
    }

    public static PredictedMotion[] predict(EntityLivingBase entity, int steps) {
        return predict(entity, steps, 1.0D);
    }

    public static PredictedMotion[] predict(EntityLivingBase entity, int steps, double inputScale) {
        int predictionSteps = Math.max(0, steps);
        if (!(entity instanceof EntityPlayerSP)
                || Minecraft.getMinecraft().thePlayer != entity) {
            PredictedMotion current = snapshot(entity);
            PredictedMotion[] predictions = new PredictedMotion[predictionSteps + 1];
            for (int index = 0; index < predictions.length; index++) {
                predictions[index] = current;
            }
            return predictions;
        }

        EntityPlayerSP player = (EntityPlayerSP) entity;
        MovementInput input = new MovementInput();
        input.moveForward = player.movementInput.moveForward * (float) inputScale;
        input.moveStrafe = player.movementInput.moveStrafe * (float) inputScale;
        input.jump = player.movementInput.jump;
        input.sneak = player.movementInput.sneak;

        SimulatedPlayer simulation = SimulatedPlayer.fromClientPlayer(input);
        simulation.motionX = player.posX - player.prevPosX;
        simulation.motionY = (player.posY - player.prevPosY - GRAVITY) * AIR_DRAG;
        simulation.motionZ = player.posZ - player.prevPosZ;

        PredictedMotion[] predictions = new PredictedMotion[predictionSteps + 1];
        predictions[0] = snapshot(simulation);
        for (int index = 0; index < predictionSteps; index++) {
            simulation.tick();
            predictions[index + 1] = snapshot(simulation);
        }
        return predictions;
    }

    private static PredictedMotion snapshot(SimulatedPlayer simulation) {
        boolean collidedVertically = simulation.isCollidedVertically();
        boolean collidedHorizontally = simulation.isCollidedHorizontally;
        return new PredictedMotion(
                simulation.motionX,
                simulation.motionY,
                simulation.motionZ,
                simulation.onGround,
                collidedHorizontally,
                collidedVertically,
                collidedHorizontally || collidedVertically,
                simulation.isInWater(),
                simulation.isInLava(),
                simulation.box
        );
    }

    private static PredictedMotion snapshot(EntityLivingBase entity) {
        return new PredictedMotion(
                entity.motionX,
                entity.motionY,
                entity.motionZ,
                entity.onGround,
                entity.isCollidedHorizontally,
                entity.isCollidedVertically,
                entity.isCollided,
                entity.isInWater(),
                entity.isInLava(),
                entity.getEntityBoundingBox()
        );
    }
}
