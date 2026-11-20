package idle.miau.module.impl.combat;

import idle.miau.performance.MiauDrawCalls;
import de.florianmichael.viamcp.fixes.AttackOrder;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.GameTickEvent;
import idle.miau.event.PreAttackEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.helper.RotationPriority;
import idle.miau.mcp.accessor.IAccessorPlayerControllerMP;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.CombatTargeting;
import idle.miau.utility.SelectivePacketBlink;
import idle.miau.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;

import java.util.Random;

public final class KnockbackManipulator extends Module {
    private static final int NO_SLOT = -1;
    private static final int ANGLE_COUNT = 18;
    private static final float ANGLE_STEP = 20.0F;
    private static final double MAX_VOID_DISTANCE = 4.0D;
    private static final int STATIC_MODE = 0;
    private static final int VOID_MODE = 1;
    private static final String[] MODES = {"Static", "Void"};
    private static final int DIRECTION_LEFT = 0;
    private static final int DIRECTION_RIGHT = 1;
    private static final int DIRECTION_RANDOM = 2;
    private static final String[] DIRECTIONS = {"Left", "Right", "Random"};
    private static final long VOID_DIRECTION_LOCK_MS = 1500L;
    private static final double VOID_DIRECTION_SWITCH_ADVANTAGE = 1.2D;

    private final GroupSetting condition;
    private final ButtonSetting weaponOnly;
    private final ButtonSetting sprintOnly;
    private final ButtonSetting ignoreTeammates;
    private final SliderSetting mode;
    private final SliderSetting direction;
    private final ButtonSetting drawArrow;
    private final RangeSetting yawOffset;
    private final ButtonSetting blink;
    private final SelectivePacketBlink blinkPackets = new SelectivePacketBlink();
    private final Random random = new Random();

    private boolean restoreRotation;
    private boolean flickRequested;
    private boolean handlingSyntheticAttack;
    private boolean rotationEventActive;
    private boolean syntheticAttackThisTick;
    private float voidYawOffset;
    private float pendingFlickYaw;
    private int savedSlot = NO_SLOT;
    private EntityPlayer pendingTarget;
    private int lastVoidTargetId = -1;
    private float lastVoidFlickSign;
    private long lastVoidFlickMs;

    private EntityPlayer arrowPlayer;
    private float arrowYaw;
    private float arrowFadeStartAlpha;
    private float arrowFadeEndAlpha;
    private long arrowFadeStartMs;
    private boolean arrowVisible;
    private long arrowNudgeStartMs = -1L;
    private int arrowNudgePlayerId = -1;
    private long lastArrowHitMs = -1L;

    public KnockbackManipulator() {
        super("KBDisplacement", category.combat);
        setDescription("Adjusts attack yaw to influence knockback direction.");
        registerSetting(condition = new GroupSetting("Condition"));
        registerSetting(weaponOnly = new ButtonSetting(condition, "Weapon only", true));
        registerSetting(sprintOnly = new ButtonSetting(condition, "Sprint only", true));
        registerSetting(ignoreTeammates = new ButtonSetting(condition, "Ignore teammates", true));
        registerSetting(mode = new SliderSetting("Mode", STATIC_MODE, MODES));
        registerSetting(direction = new SliderSetting("Direction", DIRECTION_RANDOM, DIRECTIONS));
        registerSetting(drawArrow = new ButtonSetting("Draw Arrow", true));
        registerSetting(yawOffset = new RangeSetting(
                "Yaw offset", 0.0D, 180.0D, 75.0D, 90.0D, 0.1D));
        registerSetting(blink = new ButtonSetting("HideFlick", false));
        closetModule = true;
        guiUpdate();
    }

    @Override
    public void guiUpdate() {
        condition.setVisible(true, this);
        direction.setVisible(!isVoidMode(), this);
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    @Override
    public void onEnable() {
        blinkPackets.clear();
        resetState();
    }

    @Override
    public void onDisable() {
        blinkPackets.flush();
        restoreSlot();
        resetState();
    }

    @Subscribe
    public void onGameTick(GameTickEvent event) {
        rotationEventActive = false;
        syntheticAttackThisTick = false;
        if (!blink.isToggled()) {
            blinkPackets.flush();
        }
    }

    @Subscribe(priority = Priority.HIGH)
    public void onClientRotation(ClientRotationEvent event) {
        if (!Utils.nullCheck()) {
            resetState();
            return;
        }

        rotationEventActive = true;
        if (!blink.isToggled()) {
            blinkPackets.flush();
        }

        EntityPlayer player = mc.thePlayer;
        if (restoreRotation) {
            if (!applyRotation(event, player.rotationYaw, player.rotationPitch)) {
                return;
            }
            if (blink.isToggled()) {
                blinkPackets.flush();
            }
            performSyntheticAttack(player);
            restoreRotation = false;
        }

        if (flickRequested) {
            if (!applyRotation(event, pendingFlickYaw, player.rotationPitch)) {
                return;
            }
            if (blink.isToggled()) {
                blinkPackets.refresh(150L, true);
            }
            restoreRotation = true;
            flickRequested = false;
        }

        restoreSlotIfPossible();
    }

    @Subscribe
    public void onPreAttack(PreAttackEvent event) {
        if (handlingSyntheticAttack || !rotationEventActive || !Utils.nullCheck()) {
            return;
        }
        if (restoreRotation || flickRequested || syntheticAttackThisTick) {
            event.setCanceled(true);
            return;
        }

        EntityPlayer target = getCrosshairTarget(event.objectMouseOver);
        if (target == null || !canManipulate(target)) {
            return;
        }

        event.setCanceled(true);
        swapToSharpestWeapon();
        pendingTarget = target;
        float offset = nextYawOffset();
        pendingFlickYaw = mc.thePlayer.rotationYaw + offset;
        flickRequested = true;
        showArrow(target, pendingFlickYaw);
    }

    @Subscribe(priority = Priority.HIGH)
    public void onSendPacket(SendPacketEvent event) {
        if (blink.isToggled()) {
            blinkPackets.capture(event);
        }
        if (drawArrow.isToggled() && arrowPlayer != null && !event.isCanceled() && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() == C02PacketUseEntity.Action.ATTACK) {
                Entity entity = packet.getEntityFromWorld(mc.theWorld);
                if (entity == arrowPlayer) {
                    triggerArrowNudge(arrowPlayer);
                }
            }
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        blinkPackets.clear();
        restoreSlot();
        resetState();
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!Utils.nullCheck() || !drawArrow.isToggled()) {
            clearArrow();
            return;
        }
        if (arrowPlayer == null || arrowPlayer.isDead || arrowPlayer.deathTime != 0) {
            clearArrow();
            return;
        }
        long now = System.currentTimeMillis();
        if (arrowVisible && lastArrowHitMs > 0L && now - lastArrowHitMs > 1200L) {
            hideArrow();
        }
        double nudgeProgress = getArrowNudgeProgress(arrowPlayer, now);
        float alpha = getArrowAlpha(now) * getArrowNudgeAlpha(nudgeProgress);
        if (alpha <= 0.0F) {
            if (!arrowVisible || nudgeProgress >= 1.0D) {
                clearArrow();
            }
            return;
        }
        double nudgeOffset = getArrowNudgeOffset(nudgeProgress);
        drawArrow(arrowPlayer, arrowYaw, event.partialTicks, alpha, nudgeOffset);
    }

    private boolean canManipulate(EntityPlayer target) {
        if (!isValidTarget(target)) {
            return false;
        }
        if (sprintOnly.isToggled() && !mc.thePlayer.isSprinting()) {
            return false;
        }
        if (weaponOnly.isToggled() && !hasWeapon(mc.thePlayer)) {
            return false;
        }
        return !isVoidMode() || voidCheck(target);
    }

    private void swapToSharpestWeapon() {
        if (ModuleManager.autoTool == null || !ModuleManager.autoTool.isEnabled()) {
            return;
        }

        int currentSlot = mc.thePlayer.inventory.currentItem;
        int selectedSlot = NO_SLOT;
        int bestSharpness = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!isWeapon(stack)) {
                continue;
            }
            int sharpness = EnchantmentHelper.getEnchantmentLevel(
                    Enchantment.sharpness.effectId, stack);
            if (sharpness > bestSharpness || currentSlot == slot) {
                bestSharpness = sharpness;
                selectedSlot = slot;
            }
        }

        if (selectedSlot == NO_SLOT || selectedSlot == currentSlot) {
            return;
        }

        savedSlot = currentSlot;
        mc.thePlayer.inventory.currentItem = selectedSlot;
        syncCurrentItem();
    }

    private boolean voidCheck(EntityPlayer target) {
        if (target == null || mc.theWorld == null) {
            return false;
        }

        double targetX = target.posX;
        double targetZ = target.posZ;
        int targetY = MathHelper.floor_double(target.posY);
        float playerYaw = mc.thePlayer.rotationYaw;
        BlockPos targetPosition = new BlockPos(
                MathHelper.floor_double(targetX), targetY, MathHelper.floor_double(targetZ));
        long now = System.currentTimeMillis();
        boolean directionLocked = target.getEntityId() == lastVoidTargetId
                && now - lastVoidFlickMs < VOID_DIRECTION_LOCK_MS;

        if (hasVoidBelow(targetPosition)) {
            float bestAngleDifference = 0.0F;
            double bestDistance = Double.MAX_VALUE;
            boolean found = false;
            for (int index = 0; index < ANGLE_COUNT; index++) {
                float angle = index * ANGLE_STEP;
                double radians = Math.toRadians(angle);
                double directionX = -Math.sin(radians);
                double directionZ = Math.cos(radians);
                for (double distance = 0.5D; distance <= MAX_VOID_DISTANCE; distance += 0.3D) {
                    int x = MathHelper.floor_double(targetX + directionX * distance);
                    int z = MathHelper.floor_double(targetZ + directionZ * distance);
                    if (hasCollisionAt(x, targetY, z)) {
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestAngleDifference = MathHelper.wrapAngleTo180_float(angle - playerYaw);
                            found = true;
                        }
                        break;
                    }
                }
            }
            if (!found) {
                voidYawOffset = 0.0F;
                return false;
            }
            float sign = bestAngleDifference > 0.0F ? -1.0F : 1.0F;
            if (directionLocked && lastVoidFlickSign != 0.0F) {
                boolean landOnLockedSide = lastVoidFlickSign > 0.0F && bestAngleDifference > 0.0F
                        || lastVoidFlickSign < 0.0F && bestAngleDifference < 0.0F;
                if (!landOnLockedSide) {
                    sign = lastVoidFlickSign;
                }
            }
            updateVoidDirection(target, sign, now);
            return true;
        }

        double positiveVoidDistance = Double.MAX_VALUE;
        double negativeVoidDistance = Double.MAX_VALUE;
        boolean foundPositive = false;
        boolean foundNegative = false;

        for (int index = 0; index < ANGLE_COUNT; index++) {
            float angle = index * ANGLE_STEP;
            double radians = Math.toRadians(angle);
            double directionX = -Math.sin(radians);
            double directionZ = Math.cos(radians);
            float angleDifference = MathHelper.wrapAngleTo180_float(angle - playerYaw);
            float absoluteAngleDifference = Math.abs(angleDifference);
            if (absoluteAngleDifference < 30.0F || absoluteAngleDifference > 150.0F) {
                continue;
            }

            double voidDistance = Double.MAX_VALUE;
            boolean blocked = false;
            for (double distance = 0.5D; distance <= MAX_VOID_DISTANCE; distance += 0.5D) {
                int x = MathHelper.floor_double(targetX + directionX * distance);
                int z = MathHelper.floor_double(targetZ + directionZ * distance);
                if (hasCollisionAtLevel(x, targetY, z)
                        || hasCollisionAtLevel(x, targetY + 1, z)) {
                    blocked = true;
                    break;
                }
                if (hasVoidBelow(new BlockPos(x, targetY, z))) {
                    voidDistance = distance;
                    break;
                }
            }
            if (blocked || voidDistance > MAX_VOID_DISTANCE) {
                continue;
            }
            if (angleDifference > 0.0F && voidDistance < positiveVoidDistance) {
                positiveVoidDistance = voidDistance;
                foundPositive = true;
            } else if (angleDifference < 0.0F && voidDistance < negativeVoidDistance) {
                negativeVoidDistance = voidDistance;
                foundNegative = true;
            }
        }

        float sign = resolveFlickSign(
                directionLocked,
                lastVoidFlickSign,
                foundPositive,
                positiveVoidDistance,
                foundNegative,
                negativeVoidDistance);
        if (sign == 0.0F) {
            return false;
        }
        updateVoidDirection(target, sign, now);
        return true;
    }

    static float resolveFlickSign(
            boolean directionLocked,
            float lockedSign,
            boolean foundPositive,
            double positiveVoidDistance,
            boolean foundNegative,
            double negativeVoidDistance) {
        if (!foundPositive && !foundNegative) {
            return 0.0F;
        }
        if (!foundPositive) {
            return -1.0F;
        }
        if (!foundNegative) {
            return 1.0F;
        }
        if (directionLocked && lockedSign > 0.0F
                && positiveVoidDistance <= negativeVoidDistance + VOID_DIRECTION_SWITCH_ADVANTAGE) {
            return 1.0F;
        }
        if (directionLocked && lockedSign < 0.0F
                && negativeVoidDistance <= positiveVoidDistance + VOID_DIRECTION_SWITCH_ADVANTAGE) {
            return -1.0F;
        }
        return positiveVoidDistance < negativeVoidDistance ? 1.0F : -1.0F;
    }

    private void updateVoidDirection(EntityPlayer target, float sign, long now) {
        lastVoidTargetId = target.getEntityId();
        lastVoidFlickSign = sign;
        lastVoidFlickMs = now;
        voidYawOffset = sign * (float) yawOffset.getRandomInRange();
    }

    private float nextYawOffset() {
        if (isVoidMode()) {
            return voidYawOffset;
        }
        float offset = (float) yawOffset.getRandomInRange();
        int dir = (int) direction.getInput();
        if (dir == DIRECTION_LEFT) {
            return -offset;
        } else if (dir == DIRECTION_RIGHT) {
            return offset;
        } else {
            return random.nextBoolean() ? offset : -offset;
        }
    }

    private boolean applyRotation(ClientRotationEvent event, float yaw, float pitch) {
        return event.trySetRotation(this, RotationPriority.COMBAT, yaw, pitch);
    }

    private boolean hasCollisionAt(int x, int y, int z) {
        for (int offset = -2; offset <= 3; offset++) {
            if (hasCollisionAtLevel(x, y + offset, z)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCollisionAtLevel(int x, int y, int z) {
        if (y < 0 || mc.theWorld == null) {
            return false;
        }
        BlockPos position = new BlockPos(x, y, z);
        IBlockState state = mc.theWorld.getBlockState(position);
        Block block = state.getBlock();
        AxisAlignedBB collisionBox = block.getCollisionBoundingBox(mc.theWorld, position, state);
        return collisionBox != null;
    }

    private boolean hasVoidBelow(BlockPos position) {
        for (int offset = 0; offset < 10; offset++) {
            BlockPos below = position.add(0, -offset, 0);
            if (below.getY() < 1) {
                return true;
            }
            if (hasCollisionAtLevel(below.getX(), below.getY(), below.getZ())) {
                return false;
            }
        }
        return true;
    }

    private EntityPlayer getCrosshairTarget(MovingObjectPosition hit) {
        if (hit == null || !(hit.entityHit instanceof EntityPlayer)) {
            return null;
        }
        EntityPlayer target = (EntityPlayer) hit.entityHit;
        return isValidTarget(target) ? target : null;
    }

    private boolean isValidTarget(EntityPlayer target) {
        return target != null && CombatTargeting.isTrackablePlayer(target, ignoreTeammates.isToggled());
    }

    private boolean hasWeapon(EntityPlayer player) {
        ItemStack stack = player == null ? null : player.getHeldItem();
        return isWeapon(stack);
    }

    private boolean isWeapon(ItemStack stack) {
        return stack != null
                && (stack.getItem() instanceof ItemSword
                || stack.getItem() instanceof ItemAxe
                || stack.getItem() == Items.stick);
    }

    private void performSyntheticAttack(EntityPlayer player) {
        EntityPlayer target = pendingTarget;
        pendingTarget = null;
        syntheticAttackThisTick = true;
        handlingSyntheticAttack = true;
        try {
            if (target != null && isValidTarget(target) && mc.playerController != null) {
                triggerArrowNudge(target);
                AttackOrder.sendFixedAttack(mc, player, target);
            }
        } finally {
            handlingSyntheticAttack = false;
        }
    }

    private void restoreSlotIfPossible() {
        if (savedSlot == NO_SLOT
                || Utils.isBindDown(mc.gameSettings.keyBindUseItem)) {
            return;
        }
        restoreSlot();
    }

    private void restoreSlot() {
        if (savedSlot == NO_SLOT || mc.thePlayer == null) {
            savedSlot = NO_SLOT;
            return;
        }
        mc.thePlayer.inventory.currentItem = savedSlot;
        syncCurrentItem();
        savedSlot = NO_SLOT;
    }

    private void syncCurrentItem() {
        if (mc.playerController instanceof IAccessorPlayerControllerMP) {
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        }
    }

    private void resetState() {
        restoreRotation = false;
        flickRequested = false;
        handlingSyntheticAttack = false;
        rotationEventActive = false;
        syntheticAttackThisTick = false;
        voidYawOffset = 0.0F;
        pendingFlickYaw = 0.0F;
        pendingTarget = null;
        savedSlot = NO_SLOT;
        lastVoidTargetId = -1;
        lastVoidFlickSign = 0.0F;
        lastVoidFlickMs = 0L;
        clearArrow();
    }

    private boolean isVoidMode() {
        return (int) mode.getInput() == VOID_MODE;
    }

    private void showArrow(EntityPlayer player, float yaw) {
        if (!drawArrow.isToggled()) {
            clearArrow();
            return;
        }
        long now = System.currentTimeMillis();
        boolean playerChanged = arrowPlayer != player;
        if (playerChanged) {
            clearArrowNudge();
        }
        arrowPlayer = player;
        arrowYaw = yaw;
        arrowFadeStartAlpha = 1.0F;
        arrowFadeEndAlpha = 1.0F;
        arrowFadeStartMs = now;
        arrowVisible = true;
        lastArrowHitMs = now;
        triggerArrowNudge(player);
    }

    private void hideArrow() {
        if (arrowPlayer != null && arrowVisible) {
            long now = System.currentTimeMillis();
            arrowFadeStartAlpha = getArrowAlpha(now);
            arrowFadeEndAlpha = 0.0F;
            arrowFadeStartMs = now;
            arrowVisible = false;
        }
    }

    private float getArrowAlpha(long now) {
        if (arrowFadeStartMs <= 0L) {
            return 1.0F;
        }
        float progress = Math.min(1.0F, Math.max(0.0F, (float) (now - arrowFadeStartMs) / 100.0F));
        return arrowFadeStartAlpha + (arrowFadeEndAlpha - arrowFadeStartAlpha) * progress;
    }

    private void triggerArrowNudge(EntityPlayer player) {
        if (player != null) {
            long now = System.currentTimeMillis();
            if (arrowNudgePlayerId != player.getEntityId() || now - arrowNudgeStartMs >= 120L) {
                arrowNudgePlayerId = player.getEntityId();
                arrowNudgeStartMs = now;
            }
        }
    }

    private double getArrowNudgeProgress(EntityPlayer player, long now) {
        if (player != null && arrowNudgePlayerId == player.getEntityId() && arrowNudgeStartMs >= 0L) {
            double progress = (double) (now - arrowNudgeStartMs) / 420.0D;
            return Math.min(1.0D, Math.max(0.0D, progress));
        }
        return -1.0D;
    }

    private double getArrowNudgeOffset(double progress) {
        if (progress < 0.0D || progress >= 1.0D) {
            return 0.0D;
        }
        double bounce = Math.sin(progress * Math.PI * 2.5D) * Math.exp(-progress * 3.0D) * (1.0D - progress);
        return bounce * 1.45D;
    }

    private float getArrowNudgeAlpha(double progress) {
        if (progress < 0.0D || progress <= 0.45D) {
            return 1.0F;
        }
        double fadeProgress = (progress - 0.45D) / 0.55D;
        double smoothFade = fadeProgress * fadeProgress * (3.0D - 2.0D * fadeProgress);
        return (float) Math.max(0.0D, Math.min(1.0D, 1.0D - smoothFade));
    }

    private void clearArrowNudge() {
        arrowNudgeStartMs = -1L;
        arrowNudgePlayerId = -1;
    }

    private void clearArrow() {
        arrowPlayer = null;
        arrowFadeStartAlpha = 0.0F;
        arrowFadeEndAlpha = 0.0F;
        arrowFadeStartMs = 0L;
        arrowVisible = false;
        lastArrowHitMs = -1L;
        clearArrowNudge();
    }

    private void drawArrow(EntityPlayer player, float yaw, float partialTicks, float alpha, double nudgeOffset) {
        long now = System.currentTimeMillis();
        double hover = Math.sin((double) (now % 2400L) / 2400.0D * Math.PI * 2.0D) * 0.03D;
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks + (double) player.height * 0.52D + hover;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks;
        double radians = Math.toRadians((double) yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        double sideX = -forwardZ;
        double sideZ = forwardX;

        double startDist = (double) player.width * 0.5D + 0.22D + nudgeOffset;
        double shaftLen = 0.30D;
        double shaftW = 0.045D;
        double headLen = 0.24D;
        double headW = 0.17D;
        double h = 0.02D;

        double sX = x + forwardX * startDist;
        double sZ = z + forwardZ * startDist;
        double hbX = sX + forwardX * shaftLen;
        double hbZ = sZ + forwardZ * shaftLen;
        double tipX = hbX + forwardX * headLen;
        double tipZ = hbZ + forwardZ * headLen;

        double sLX = sX - sideX * shaftW, sLZ = sZ - sideZ * shaftW;
        double sRX = sX + sideX * shaftW, sRZ = sZ + sideZ * shaftW;
        double hbLX = hbX - sideX * shaftW, hbLZ = hbZ - sideZ * shaftW;
        double hbRX = hbX + sideX * shaftW, hbRZ = hbZ + sideZ * shaftW;
        double wLX = hbX - sideX * headW, wLZ = hbZ - sideZ * headW;
        double wRX = hbX + sideX * headW, wRZ = hbZ + sideZ * headW;

        RenderManager renderManager = mc.getRenderManager();
        double vx = renderManager.viewerPosX;
        double vy = renderManager.viewerPosY;
        double vz = renderManager.viewerPosZ;

        GL11.glPushMatrix();
        GL11.glTranslated(x - vx, y - vy, z - vz);
        GL11.glRotated(90.0D, forwardX, 0.0D, forwardZ);
        GL11.glTranslated(vx - x, vy - y, vz - z);
        GL11.glPushAttrib(1048575);
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(3553);
        GL11.glDisable(2896);
        GL11.glDisable(2929);
        GL11.glDisable(2884);
        GL11.glDepthMask(false);
        GL11.glEnable(2848);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.88F * alpha);
        GL11.glBegin(4);
        vertex(sLX, y + h, sLZ, vx, vy, vz);
        vertex(hbLX, y + h, hbLZ, vx, vy, vz);
        vertex(hbRX, y + h, hbRZ, vx, vy, vz);

        vertex(sLX, y + h, sLZ, vx, vy, vz);
        vertex(hbRX, y + h, hbRZ, vx, vy, vz);
        vertex(sRX, y + h, sRZ, vx, vy, vz);

        vertex(wLX, y + h, wLZ, vx, vy, vz);
        vertex(tipX, y + h, tipZ, vx, vy, vz);
        vertex(wRX, y + h, wRZ, vx, vy, vz);

        GL11.glColor4f(0.85F, 0.88F, 0.92F, 0.85F * alpha);
        vertex(sLX, y - h, sLZ, vx, vy, vz);
        vertex(hbRX, y - h, hbRZ, vx, vy, vz);
        vertex(hbLX, y - h, hbLZ, vx, vy, vz);

        vertex(sLX, y - h, sLZ, vx, vy, vz);
        vertex(sRX, y - h, sRZ, vx, vy, vz);
        vertex(hbRX, y - h, hbRZ, vx, vy, vz);

        vertex(wLX, y - h, wLZ, vx, vy, vz);
        vertex(wRX, y - h, wRZ, vx, vy, vz);
        vertex(tipX, y - h, tipZ, vx, vy, vz);

        GL11.glColor4f(0.72F, 0.76F, 0.82F, 0.80F * alpha);
        drawQuad(sLX, sLZ, hbLX, hbLZ, y + h, y - h, vx, vy, vz);
        drawQuad(hbRX, hbRZ, sRX, sRZ, y + h, y - h, vx, vy, vz);
        drawQuad(sRX, sRZ, sLX, sLZ, y + h, y - h, vx, vy, vz);
        drawQuad(hbLX, hbLZ, wLX, wLZ, y + h, y - h, vx, vy, vz);
        drawQuad(wRX, wRZ, hbRX, hbRZ, y + h, y - h, vx, vy, vz);
        drawQuad(wLX, wLZ, tipX, tipZ, y + h, y - h, vx, vy, vz);
        drawQuad(tipX, tipZ, wRX, wRZ, y + h, y - h, vx, vy, vz);
        MiauDrawCalls.glEnd();

        GL11.glLineWidth(1.6F);
        GL11.glColor4f(0.08F, 0.08F, 0.12F, 0.80F * alpha);
        GL11.glBegin(2);
        vertex(sLX, y + h, sLZ, vx, vy, vz);
        vertex(hbLX, y + h, hbLZ, vx, vy, vz);
        vertex(wLX, y + h, wLZ, vx, vy, vz);
        vertex(tipX, y + h, tipZ, vx, vy, vz);
        vertex(wRX, y + h, wRZ, vx, vy, vz);
        vertex(hbRX, y + h, hbRZ, vx, vy, vz);
        vertex(sRX, y + h, sRZ, vx, vy, vz);
        MiauDrawCalls.glEnd();

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private void drawQuad(double x1, double z1, double x2, double z2, double yTop, double yBot, double vx, double vy, double vz) {
        vertex(x1, yTop, z1, vx, vy, vz);
        vertex(x2, yTop, z2, vx, vy, vz);
        vertex(x2, yBot, z2, vx, vy, vz);

        vertex(x1, yTop, z1, vx, vy, vz);
        vertex(x2, yBot, z2, vx, vy, vz);
        vertex(x1, yBot, z1, vx, vy, vz);
    }

    private void vertex(double x, double y, double z, double viewerX, double viewerY, double viewerZ) {
        GL11.glVertex3d(x - viewerX, y - viewerY, z - viewerZ);
    }
}
