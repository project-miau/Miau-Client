package idle.miau.module.impl.movement;

import idle.miau.event.JumpEvent;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.PostPlayerInputEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.Autoblock;
import idle.miau.module.impl.combat.KillAura;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class Jump45 extends Module {
    private static final float INPUT_DAMPING = 0.98F;
    private static final float SPRINT_JUMP_BOOST = 0.2F;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;

    private final ButtonSetting holdingBlocks;
    private final ButtonSetting holdingThrowable;
    private final ButtonSetting holdingRod;
    private final ButtonSetting targeting;

    private RotationPlan pendingPlan;
    private EntityPlayerSP rotationPlayer;
    private float originalYaw;
    private float originalPitch;
    private float originalPrevYaw;
    private float originalYawHead;
    private float originalPrevYawHead;
    private float originalRenderYawOffset;
    private float originalPrevRenderYawOffset;
    private float originalRenderArmYaw;
    private float originalPrevRenderArmYaw;
    private float packetYaw;
    private float packetPitch;
    private boolean prepareJump;
    private boolean movementYawApplied;
    private boolean straightJumpActive;

    public Jump45() {
        super("45Jump", category.movement);
        setDescription("Aligns sprint jumps to a 45-degree diagonal.");
        this.registerSetting(new DescriptionSetting("45 degree diagonal sprint jumps"));
        this.registerSetting(holdingBlocks = new ButtonSetting("Holding blocks", true));
        this.registerSetting(holdingThrowable = new ButtonSetting("Holding throwable", true));
        this.registerSetting(holdingRod = new ButtonSetting("Holding rod", true));
        this.registerSetting(targeting = new ButtonSetting("Targeting", false));
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPrePlayerInput(PrePlayerInputEvent event) {
        pendingPlan = null;
        prepareJump = false;

        if (!Utils.nullCheck()) {
            straightJumpActive = false;
            return;
        }

        if (straightJumpActive) {
            if (!mc.thePlayer.onGround) {
                event.setStrafe(0.0F);
                return;
            }
            straightJumpActive = false;
        }

        if (!event.isJump()
                || !mc.thePlayer.onGround
                || mc.thePlayer.getJumpTicks() != 0
                || event.isSneak()
                || mc.thePlayer.isInWater()
                || mc.thePlayer.isInLava()
                || mc.thePlayer.capabilities.isFlying
                || mc.thePlayer.isRiding()
                || mc.thePlayer.isCollidedHorizontally
                || RotationHelper.get().isActive()
                || !canActivateForHeldItem()
                || (targeting.isToggled() && !isTargeting())) {
            return;
        }

        float forward = event.getForward();
        float strafe = event.getStrafe();
        if (forward == 0.0F && strafe == 0.0F) {
            forward = 1.0F;
            event.setForward(forward);
        }
        if (forward < 0.99F) {
            return;
        }
        if (Math.abs(strafe) < 0.99F) {
            strafe = chooseStrafeDirection(mc.thePlayer.rotationYaw);
            event.setStrafe(strafe);
        }
        if (!mc.thePlayer.isSprinting()) {
            mc.thePlayer.setSprinting(true);
        }
        if (!mc.thePlayer.isSprinting()) {
            return;
        }

        captureRotationState();
        float[] fixedRotation = RotationUtils.fixRotation(
                originalYaw,
                originalPitch,
                RotationUtils.serverRotations[0],
                RotationUtils.serverRotations[1]
        );
        packetYaw = fixedRotation[0];
        packetPitch = fixedRotation[1];
        RotationHelper.get().setRotations(packetYaw, packetPitch);
        prepareJump = true;
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPostPlayerInput(PostPlayerInputEvent event) {
        if (!prepareJump) {
            return;
        }
        prepareJump = false;

        if (!Utils.nullCheck()
                || !mc.thePlayer.onGround
                || !mc.thePlayer.movementInput.jump
                || !mc.thePlayer.isSprinting()) {
            return;
        }

        float itemSlowdown = getItemMovementMultiplier();
        float forward = mc.thePlayer.movementInput.moveForward * itemSlowdown;
        float strafe = mc.thePlayer.movementInput.moveStrafe * itemSlowdown;
        RotationPlan plan = createRotationPlan(forward, strafe, originalYaw, packetYaw, packetPitch);
        if (plan == null) {
            return;
        }

        pendingPlan = plan;
        rotationPlayer.rotationYaw = plan.movementYaw;
        movementYawApplied = true;
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onJump(JumpEvent event) {
        if (mc.thePlayer == null
                || mc.thePlayer.movementInput == null
                || event.entity != mc.thePlayer
                || !event.applySprint()
                || !mc.thePlayer.onGround
                || pendingPlan == null) {
            return;
        }

        event.setYaw(pendingPlan.jumpYaw);
        straightJumpActive = true;
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPreMotion(PreMotionEvent event) {
        restoreRotationState();
        if (pendingPlan == null) {
            return;
        }

        event.setYaw(packetYaw);
        event.setPitch(packetPitch);
        PreMotionEvent.setRenderYaw(false);
    }

    @Subscribe
    public void onPostMotion(PostMotionEvent event) {
        restoreRotationState();
        resetPendingRotation();
    }

    @Override
    public void onDisable() {
        restoreRotationState();
        resetPendingRotation();
        straightJumpActive = false;
    }

    private void captureRotationState() {
        rotationPlayer = mc.thePlayer;
        originalYaw = rotationPlayer.rotationYaw;
        originalPitch = rotationPlayer.rotationPitch;
        originalPrevYaw = rotationPlayer.prevRotationYaw;
        originalYawHead = rotationPlayer.rotationYawHead;
        originalPrevYawHead = rotationPlayer.prevRotationYawHead;
        originalRenderYawOffset = rotationPlayer.renderYawOffset;
        originalPrevRenderYawOffset = rotationPlayer.prevRenderYawOffset;
        originalRenderArmYaw = rotationPlayer.renderArmYaw;
        originalPrevRenderArmYaw = rotationPlayer.prevRenderArmYaw;
        movementYawApplied = false;
    }

    private void restoreRotationState() {
        if (!movementYawApplied || rotationPlayer == null) {
            return;
        }

        rotationPlayer.rotationYaw = originalYaw;
        rotationPlayer.rotationPitch = originalPitch;
        rotationPlayer.prevRotationYaw = originalPrevYaw;
        rotationPlayer.rotationYawHead = originalYawHead;
        rotationPlayer.prevRotationYawHead = originalPrevYawHead;
        rotationPlayer.renderYawOffset = originalRenderYawOffset;
        rotationPlayer.prevRenderYawOffset = originalPrevRenderYawOffset;
        rotationPlayer.renderArmYaw = originalRenderArmYaw;
        rotationPlayer.prevRenderArmYaw = originalPrevRenderArmYaw;
        movementYawApplied = false;
        rotationPlayer = null;
    }

    private void resetPendingRotation() {
        pendingPlan = null;
        prepareJump = false;
        packetYaw = 0.0F;
        packetPitch = 0.0F;
    }

    private boolean canActivateForHeldItem() {
        boolean hasHeldItemFilter = holdingBlocks.isToggled()
                || holdingThrowable.isToggled() || holdingRod.isToggled();
        if (!hasHeldItemFilter) {
            return true;
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) {
            return false;
        }

        Item item = held.getItem();
        return holdingBlocks.isToggled() && item instanceof ItemBlock
                || holdingRod.isToggled() && item instanceof ItemFishingRod
                || holdingThrowable.isToggled()
                && (item instanceof ItemSnowball
                || item instanceof ItemEgg
                || item instanceof ItemEnderPearl
                || item == Items.experience_bottle
                || item instanceof ItemPotion && ItemPotion.isSplash(held.getItemDamage()));
    }

    private boolean isTargeting() {
        if (KillAura.target != null) {
            return true;
        }
        return mc.gameSettings.keyBindAttack.isKeyDown()
                && mc.objectMouseOver != null
                && mc.objectMouseOver.entityHit != null;
    }

    private static float getItemMovementMultiplier() {
        if (!mc.thePlayer.isUsingItem() || mc.thePlayer.isRiding()) {
            return 1.0F;
        }

        Autoblock autoblock = ModuleManager.autoblock;
        if (autoblock != null && autoblock.shouldSuppressUsingItemSlowdown(mc.thePlayer)) {
            return 1.0F;
        }
        return NoSlow.getSlowed();
    }

    private static float chooseStrafeDirection(float yaw) {
        Vector2 sideDirection = getStrafeVector(yaw, 1.0F);
        double lateralMotion = mc.thePlayer.motionX * sideDirection.x
                + mc.thePlayer.motionZ * sideDirection.z;
        return lateralMotion > 0.0D ? -1.0F : 1.0F;
    }

    private static RotationPlan createRotationPlan(
            float forwardInput,
            float strafeInput,
            float originalYaw,
            float packetYaw,
            float packetPitch
    ) {
        if (!Utils.nullCheck()) {
            return null;
        }

        float acceleration = getGroundAcceleration();
        if (!Float.isFinite(acceleration) || acceleration <= 0.0F) {
            return null;
        }

        float dampedForward = forwardInput * INPUT_DAMPING;
        float dampedStrafe = strafeInput * INPUT_DAMPING;
        Vector2 relativeInput = getMovementAddition(0.0F, dampedStrafe, dampedForward, acceleration);
        if (relativeInput.length() <= 0.0D) {
            return null;
        }

        float relativeInputYaw = yawFromVector(relativeInput, 0.0F);
        float movementYaw = RotationUtils.unwrapYaw(originalYaw - relativeInputYaw, originalYaw);
        Vector2 actualInput = getMovementAddition(movementYaw, dampedStrafe, dampedForward, acceleration);

        Vector2 baseMotion = new Vector2(mc.thePlayer.motionX, mc.thePlayer.motionZ);
        baseMotion.add(actualInput);

        Vector2 forwardDirection = getForwardVector(originalYaw, 1.0F);
        Vector2 sideDirection = getStrafeVector(originalYaw, 1.0F);
        double lateralVelocity = baseMotion.dot(sideDirection);
        double jumpSide = Math.max(-SPRINT_JUMP_BOOST, Math.min(SPRINT_JUMP_BOOST, -lateralVelocity));
        double jumpForward = Math.sqrt(Math.max(
                0.0D,
                SPRINT_JUMP_BOOST * SPRINT_JUMP_BOOST - jumpSide * jumpSide
        ));
        if (baseMotion.dot(forwardDirection) + jumpForward <= 0.0D) {
            return null;
        }

        Vector2 jumpImpulse = new Vector2(
                forwardDirection.x * jumpForward + sideDirection.x * jumpSide,
                forwardDirection.z * jumpForward + sideDirection.z * jumpSide
        );
        float jumpYaw = yawFromVector(jumpImpulse, originalYaw);
        return new RotationPlan(packetYaw, packetPitch, movementYaw, jumpYaw);
    }

    public static float[] get45JumpRotations(float strafeInput) {
        if (!Utils.nullCheck() || mc.thePlayer.movementInput == null) {
            return null;
        }

        float originalYaw = mc.thePlayer.rotationYaw;
        float originalPitch = mc.thePlayer.rotationPitch;
        float[] fixedRotation = RotationUtils.fixRotation(
                originalYaw,
                originalPitch,
                RotationUtils.serverRotations[0],
                RotationUtils.serverRotations[1]
        );
        RotationPlan plan = createRotationPlan(
                mc.thePlayer.movementInput.moveForward,
                strafeInput,
                originalYaw,
                fixedRotation[0],
                fixedRotation[1]
        );
        return plan == null ? null : new float[]{plan.packetYaw, plan.jumpYaw};
    }

    private static float getGroundAcceleration() {
        BlockPos underPlayer = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        Block block = mc.theWorld.getBlockState(underPlayer).getBlock();
        float friction = block.slipperiness * 0.91F;
        return mc.thePlayer.getAIMoveSpeed()
                * (0.16277136F / (friction * friction * friction));
    }

    private static Vector2 getMovementAddition(float yaw, float strafe, float forward, float acceleration) {
        float length = strafe * strafe + forward * forward;
        if (length < 1.0E-4F) {
            return new Vector2(0.0D, 0.0D);
        }

        length = MathHelper.sqrt_float(length);
        if (length < 1.0F) {
            length = 1.0F;
        }

        float scale = acceleration / length;
        strafe *= scale;
        forward *= scale;
        float sin = MathHelper.sin(yaw * DEGREES_TO_RADIANS);
        float cos = MathHelper.cos(yaw * DEGREES_TO_RADIANS);
        return new Vector2(strafe * cos - forward * sin, forward * cos + strafe * sin);
    }

    private static Vector2 getForwardVector(float yaw, double magnitude) {
        float radians = yaw * DEGREES_TO_RADIANS;
        return new Vector2(
                -MathHelper.sin(radians) * magnitude,
                MathHelper.cos(radians) * magnitude
        );
    }

    private static Vector2 getStrafeVector(float yaw, double magnitude) {
        float radians = yaw * DEGREES_TO_RADIANS;
        return new Vector2(
                MathHelper.cos(radians) * magnitude,
                MathHelper.sin(radians) * magnitude
        );
    }

    private static float yawFromVector(Vector2 vector, float referenceYaw) {
        float yaw = (float) Math.toDegrees(Math.atan2(-vector.x, vector.z));
        return RotationUtils.unwrapYaw(yaw, referenceYaw);
    }

    private static final class RotationPlan {
        private final float packetYaw;
        private final float packetPitch;
        private final float movementYaw;
        private final float jumpYaw;

        private RotationPlan(float packetYaw, float packetPitch, float movementYaw, float jumpYaw) {
            this.packetYaw = packetYaw;
            this.packetPitch = packetPitch;
            this.movementYaw = movementYaw;
            this.jumpYaw = jumpYaw;
        }
    }

    private static final class Vector2 {
        private double x;
        private double z;

        private Vector2(double x, double z) {
            this.x = x;
            this.z = z;
        }

        private void add(Vector2 other) {
            this.x += other.x;
            this.z += other.z;
        }

        private double dot(Vector2 other) {
            return this.x * other.x + this.z * other.z;
        }

        private double length() {
            return Math.sqrt(this.x * this.x + this.z * this.z);
        }
    }
}
