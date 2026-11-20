package idle.miau.module.impl.player;

import idle.miau.event.ClientRotationEvent;
import idle.miau.event.DispatchPacketEvent;
import idle.miau.event.EventMovementInput;
import idle.miau.event.PostPlayerInputEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.helper.RotationPriority;
import idle.miau.mcp.McpRuntime;
import idle.miau.mcp.accessor.IAccessorPlayerControllerMP;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.KillAura;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class ScaffoldTellyBridgeMode {
    private static final EnumFacing[] SIDES = {
            EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.WEST
    };
    private static final float UNSET_PITCH = 100.0F;

    protected final Scaffold owner;
    protected final Minecraft mc = Minecraft.getMinecraft();
    private final Map<BlockPos, Long> placed = new HashMap<BlockPos, Long>();
    private final PacketTracker packetTracker = new PacketTracker();
    private boolean active;
    protected boolean keepYActive;
    private boolean sentCloseWindow;
    private boolean sentUseEntity;
    private boolean sentAnyDigging;
    protected boolean towering;
    private boolean diagonalTower;
    protected int keptY = -1;
    private int towerStage;
    private int diagonalTicks;
    private int airborneTicks;
    protected int legitTicks;
    protected int delay;
    private int jumpState;
    private int jumpTick;
    private int startTick;
    private int airBelowTicks;
    private int pausedTicks;
    private int rememberedSlot = -1;
    private long placedAt;
    private long startedAt;
    private long stoppedAt;
    private float aimYaw;
    private float aimPitch = UNSET_PITCH;
    private float restPitch;
    private float yawOffset;
    private float legitYaw;
    private float playerYawAtSend;
    private BlockPos lastPlaced;
    private String countText;

    ScaffoldTellyBridgeMode(Scaffold owner) {
        this.owner = owner;
    }

    void reset() {
        McpRuntime.EVENTS.unregister(this);
        McpRuntime.EVENTS.unregister(packetTracker);
        active = false;
        keepYActive = false;
        sentCloseWindow = false;
        sentUseEntity = false;
        sentAnyDigging = false;
        towering = false;
        diagonalTower = false;
        keptY = -1;
        resetKeepYCycle();
        towerStage = 0;
        diagonalTicks = 0;
        airborneTicks = 0;
        legitTicks = 0;
        delay = 0;
        jumpState = 0;
        jumpTick = 0;
        startTick = 0;
        airBelowTicks = 0;
        pausedTicks = 0;
        rememberedSlot = -1;
        placedAt = 0L;
        startedAt = 0L;
        stoppedAt = 0L;
        aimYaw = 0.0F;
        aimPitch = UNSET_PITCH;
        legitYaw = 0.0F;
        playerYawAtSend = 0.0F;
        lastPlaced = null;
        countText = null;
        placed.clear();
    }

    void onEnable() {
        McpRuntime.EVENTS.unregister(this);
        McpRuntime.EVENTS.register(packetTracker);
        restPitch = randomFloat(84.5F, 87.5F);
        yawOffset = randomOffset();
        if (!hasWorld()) {
            return;
        }
        rememberedSlot = mc.thePlayer.inventory.currentItem;
        if (!mc.thePlayer.onGround) {
            delay = 3;
        } else if ((!mc.thePlayer.isPotionActive(Potion.jump)
                || mc.thePlayer.getActivePotionEffect(Potion.jump).getAmplifier() < 3)
                && nearEdge(0.2D) && countBlocks() > 0) {
            jumpState = 2;
            jumpTick = mc.thePlayer.ticksExisted;
            delay = 4;
        }
    }

    void onDisable() {
        stop();
        legitTicks = 0;
        delay = 0;
        airBelowTicks = 0;
        pausedTicks = 0;
        placedAt = 0L;
        if (hasWorld() && (jumpState != 0 || !placed.isEmpty())) {
            McpRuntime.EVENTS.register(this);
        }
    }

    boolean isActive() {
        return active;
    }

    void onClientRotation(ClientRotationEvent event) {
        update();
        if (!active || !event.canClaimRotation(owner, RotationPriority.BLOCK_PLACEMENT)) {
            return;
        }
        float[] rotation = rotations(RotationUtils.serverRotations[0],
                RotationUtils.serverRotations[1]);
        if (event.trySetRotation(owner, RotationPriority.BLOCK_PLACEMENT,
                rotation[0], rotation[1], true)) {
            event.setRotationAlreadyQuantized(true);
            event.setMovementYawCorrection(true);
        }
    }

    private void update() {
        if (!hasWorld()) {
            stop();
            return;
        }
        updateCount();
        if (pausedTicks > 0 && --pausedTicks != 0) {
            stop();
            return;
        }
        if (sentCloseWindow || screenOpen() || !mc.thePlayer.capabilities.allowEdit) {
            stop();
            return;
        }
        ItemStack stack = selectBlocks();
        if (!isScaffoldBlock(stack)) {
            if (countBlocks() == 0) {
                stop();
            }
            return;
        }
        if (!active) {
            startTick = mc.thePlayer.ticksExisted;
            startedAt = System.currentTimeMillis();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
        active = true;
        updateKeepY();
        if (delay > 0) {
            delay--;
            return;
        }
        if (sentUseEntity || sentAnyDigging) {
            return;
        }
        BlockPos below = new BlockPos(mc.thePlayer.posX,
                mc.thePlayer.posY - 1.0D, mc.thePlayer.posZ);
        airBelowTicks = isReplaceable(below) ? airBelowTicks + 1 : 0;
        placeBlocks(stack);
    }

    private void placeBlocks(ItemStack stack) {
        if (place(null, stack) == null || stack.stackSize <= 0) {
            return;
        }
        if (towering || canKeepY()) {
            for (int extra = 0; extra < owner.getTellyMultiPlace() && stack.stackSize > 0; extra++) {
                if (!placeExtra(stack)) {
                    break;
                }
            }
        }
    }

    private BlockPos place(BlockPos last, ItemStack stack) {
        BlockPos target = search(last);
        if (target == null) {
            return null;
        }
        EnumFacing facing = faceFor(target, null);
        if (facing == null) {
            return null;
        }
        BlockPos against = target.offset(facing.getOpposite());
        if (airBelowTicks <= 0) {
            if (isMoving() && Math.random() > 0.8D) {
                ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(stack));
            }
            return null;
        }
        float[] rotation = searchBlockRotation(against, facing, RotationUtils.serverRotations[0]);
        if (rotation == null) {
            return last == null ? null : place(null, stack);
        }
        aimYaw = rotation[0];
        aimPitch = rotation[1];
        MovingObjectPosition hit = rayTrace(rotation[0], rotation[1]);
        if (!matches(hit, against, null)) {
            return last == null ? null : place(null, stack);
        }
        if (!mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                stack, against, facing, hit.hitVec)) {
            return null;
        }
        swing();
        if (keptY != -1 && canKeepY()) {
            onKeepYPlaced();
        }
        recordPlacement(target);
        airBelowTicks = 0;
        return target;
    }

    private boolean placeExtra(ItemStack stack) {
        BlockPos target = search(lastPlaced);
        EnumFacing facing = target == null ? null : faceFor(target, lastPlaced);
        if (facing == null) {
            return false;
        }
        BlockPos against = target.offset(facing.getOpposite());
        float[] rotation = searchBlockRotation(against, facing, RotationUtils.serverRotations[0]);
        MovingObjectPosition hit = rotation == null ? null : rayTrace(rotation[0], rotation[1]);
        if (!matches(hit, against, null)) {
            return false;
        }
        float x = (float) (hit.hitVec.xCoord - against.getX());
        float y = (float) (hit.hitVec.yCoord - against.getY());
        float z = (float) (hit.hitVec.zCoord - against.getZ());
        mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(
                against, hit.sideHit.getIndex(), stack, x, y, z));
        if (!mc.thePlayer.capabilities.isCreativeMode) {
            stack.onItemUse(mc.thePlayer, mc.theWorld, against, hit.sideHit, x, y, z);
        }
        swing();
        recordPlacement(target);
        return true;
    }

    private void recordPlacement(BlockPos target) {
        lastPlaced = target;
        placedAt = System.currentTimeMillis();
        placed.put(target, placedAt);
    }

    private void swing() {
        mc.thePlayer.swingItem();
        if (mc.getItemRenderer() != null) {
            mc.getItemRenderer().resetEquippedProgress();
        }
    }

    private BlockPos search(BlockPos last) {
        boolean fixedY = keptY != -1 && canKeepY();
        List<Vec3> candidates = new ArrayList<Vec3>();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int offsetY = -3; offsetY <= -1; offsetY++) {
                    int y = MathHelper.floor_double(mc.thePlayer.posY) + offsetY;
                    if (fixedY && y != keptY) {
                        continue;
                    }
                    double candidateX = mc.thePlayer.posX + x;
                    double candidateZ = mc.thePlayer.posZ + z;
                    if (isReplaceable(new BlockPos(candidateX, y, candidateZ))) {
                        continue;
                    }
                    for (int side = -1; side <= 1; side += 2) {
                        candidates.add(new Vec3(candidateX + side, y, candidateZ));
                        candidates.add(new Vec3(candidateX, y, candidateZ + side));
                        if (!fixedY) {
                            candidates.add(new Vec3(candidateX, y + side, candidateZ));
                        }
                    }
                }
            }
        }
        Set<Long> seen = new HashSet<Long>(candidates.size() * 2);
        List<Vec3> unique = new ArrayList<Vec3>(candidates.size());
        for (Vec3 candidate : candidates) {
            long hash = Double.doubleToLongBits(candidate.xCoord)
                    ^ Double.doubleToLongBits(candidate.yCoord) * 31L
                    ^ Double.doubleToLongBits(candidate.zCoord) * 31L * 31L;
            if (seen.add(hash)) {
                unique.add(candidate);
            }
        }
        candidates = unique;
        double targetY = mc.thePlayer.posY - 1.0D;
        if (!mc.thePlayer.onGround && !jumpKeyDown() && !canKeepY()) {
            targetY--;
        }
        final Vec3 origin = new Vec3(mc.thePlayer.posX, targetY, mc.thePlayer.posZ);
        Comparator<Vec3> comparator = Comparator.comparingDouble(origin::squareDistanceTo);
        if (mc.thePlayer.ticksExisted - startTick <= 3) {
            double yaw = mc.thePlayer.rotationYaw * ((float) Math.PI / 180.0F);
            double directionX = -Math.sin(yaw);
            double directionZ = Math.cos(yaw);
            comparator = comparator.thenComparingDouble(point ->
                    -((point.xCoord - origin.xCoord) * directionX
                            + (point.zCoord - origin.zCoord) * directionZ));
        }
        candidates.sort(comparator);
        for (Vec3 candidate : candidates) {
            BlockPos position = new BlockPos(candidate);
            if (isReplaceable(position) && faceFor(position, last) != null) {
                return position;
            }
        }
        return null;
    }

    private EnumFacing faceFor(BlockPos target, BlockPos only) {
        BlockPos down = target.down();
        if (canPlaceAgainst(down) && (only == null || down.equals(only))) {
            return EnumFacing.UP;
        }
        double x = mc.thePlayer.posX - (target.getX() + 0.5D);
        double z = mc.thePlayer.posZ - (target.getZ() + 0.5D);
        EnumFacing preferred = Math.abs(x) > Math.abs(z)
                ? x > 0.0D ? EnumFacing.EAST : EnumFacing.WEST
                : z > 0.0D ? EnumFacing.SOUTH : EnumFacing.NORTH;
        BlockPos toward = target.offset(preferred);
        if (canPlaceAgainst(toward) && (only == null || toward.equals(only))) {
            return preferred.getOpposite();
        }
        for (EnumFacing side : SIDES) {
            BlockPos against = target.offset(side);
            if (side != preferred && canPlaceAgainst(against)
                    && (only == null || against.equals(only))) {
                return side.getOpposite();
            }
        }
        return null;
    }

    private boolean canPlaceAgainst(BlockPos position) {
        if (!placed.isEmpty() && !placed.containsKey(position) && sincePlace() < 250L) {
            for (BlockPos recent : placed.keySet()) {
                if (mc.thePlayer.getDistanceSq(recent) < 12.0D) {
                    return false;
                }
            }
        }
        return !isReplaceable(position) && !isInteractable(mc.theWorld.getBlockState(position).getBlock());
    }

    private float[] searchBlockRotation(BlockPos against, EnumFacing facing, float lastYaw) {
        float base = mc.thePlayer.onGround ? movementAngle() : lastYaw;
        float bestDifference = 360.0F;
        float bestYaw = 0.0F;
        float bestPitch = 0.0F;
        for (float yaw = base - 170.0F; yaw <= base + 170.0F; yaw += 5.0F) {
            float candidateYaw = (float) ((double) yaw + (Math.random() - 0.5D));
            for (float pitch = 90.0F; pitch > 30.0F; pitch -= pitchStep(pitch)) {
                float candidatePitch = MathHelper.clamp_float(
                        (float) ((double) pitch + (Math.random() - 0.5D)), -90.0F, 90.0F);
                MovingObjectPosition hit = rayTrace(candidateYaw, candidatePitch);
                float difference = Math.abs(base - candidateYaw);
                if (matches(hit, against, facing)
                        && awayFromEdge((float) (hit.hitVec.xCoord - against.getX()))
                        && awayFromEdge((float) (hit.hitVec.yCoord - against.getY()))
                        && awayFromEdge((float) (hit.hitVec.zCoord - against.getZ()))
                        && difference < bestDifference) {
                    bestDifference = difference;
                    bestYaw = candidateYaw;
                    bestPitch = candidatePitch;
                }
            }
        }
        return bestDifference == 360.0F ? null : new float[]{bestYaw, bestPitch};
    }

    private static float pitchStep(float pitch) {
        return pitch > 75.0F ? 1.0F : pitch > 60.0F ? 2.0F : 5.0F;
    }

    private static boolean awayFromEdge(float coordinate) {
        return coordinate == 0.0F || coordinate == 1.0F
                || (double) coordinate > 0.06D && (double) coordinate < 0.94D;
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch) {
        Vec3 eye = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        float cosYaw = MathHelper.cos((yaw *= -((float) Math.PI / 180.0F)) - (float) Math.PI);
        float sinYaw = MathHelper.sin(yaw - (float) Math.PI);
        float cosPitch = -MathHelper.cos(pitch *= -((float) Math.PI / 180.0F));
        float sinPitch = MathHelper.sin(pitch);
        Vec3 look = new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
        double reach = mc.playerController.getBlockReachDistance();
        return mc.theWorld.rayTraceBlocks(eye, eye.addVector(
                look.xCoord * reach, look.yCoord * reach, look.zCoord * reach), false, false, false);
    }

    private static boolean matches(MovingObjectPosition hit, BlockPos position, EnumFacing facing) {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && position.equals(hit.getBlockPos()) && (facing == null || hit.sideHit == facing);
    }

    private float[] rotations(float lastYaw, float lastPitch) {
        if (legitTicks > 0) {
            legitTicks--;
            float offset = -180.0F;
            float pitch = aimPitch == UNSET_PITCH ? mc.thePlayer.rotationPitch : aimPitch;
            if (legitTicks == 4) {
                if ((double) (mc.thePlayer.rotationYaw - playerYawAtSend) < -0.7D) {
                    offset = -offset;
                }
                legitYaw = offset;
                delay--;
            } else {
                if (legitTicks == 3) {
                    if (legitYaw > 0.0F) {
                        offset = -offset;
                    }
                    pitch = randomFloat(20.0F, 25.0F);
                } else if (legitTicks == 2) {
                    legitYaw -= randomFloat(105.0F, 120.0F) * Math.signum(legitYaw);
                    offset = legitYaw;
                    pitch = randomFloat(40.0F, 45.0F);
                } else if (legitTicks == 1) {
                    legitYaw -= randomFloat(33.0F, 35.0F) * Math.signum(legitYaw);
                    offset = legitYaw;
                    if (aimPitch == UNSET_PITCH) {
                        pitch = randomFloat(50.0F, 55.0F);
                    }
                } else {
                    legitYaw -= randomFloat(29.0F, 33.0F) * Math.signum(legitYaw);
                    offset = legitYaw;
                    if (aimPitch == UNSET_PITCH) {
                        pitch = randomFloat(65.0F, 70.0F);
                    }
                }
                return fixSensitivity(movementAngle() + offset + randomOffset(), pitch, lastYaw, lastPitch);
            }
        }
        long elapsed = sincePlace();
        long holdTime = mc.thePlayer.isSprinting() ? 710L : !isMoving() ? 460L : 110L;
        if (aimPitch != UNSET_PITCH && elapsed < holdTime) {
            float yaw = aimYaw;
            float pitch = aimPitch;
            if (elapsed > 5L && isMoving() && Math.random() > 0.3D) {
                yaw = (float) ((double) yaw + (0.05D + 0.45D * Math.random())
                        * (Math.random() < 0.5D ? -1 : 1));
                pitch += randomOffset();
            }
            return fixSensitivity(yaw, pitch, lastYaw, lastPitch);
        }
        aimPitch = UNSET_PITCH;
        int ticks = mc.thePlayer.ticksExisted - startTick;
        float yaw = movementAngle() + yawOffset;
        float pitch = restPitch + randomOffset();
        if (ticks <= 2 && !mc.thePlayer.onGround && mc.thePlayer.ticksExisted - jumpTick > 3) {
            BlockPos target = search(null);
            EnumFacing facing = target == null ? null : faceFor(target, null);
            if (facing != null && facing != EnumFacing.UP) {
                float[] rotation = searchBlockRotation(target.offset(facing.getOpposite()), facing, lastYaw);
                if (rotation != null) {
                    yaw = rotation[0];
                    pitch = rotation[1];
                }
            }
        }
        if (ticks <= 2) {
            float factor = (float) ((double) (ticks == 0 ? 0.3F : ticks == 1 ? 0.8F : 0.55F)
                    + (-0.02D + 0.04D * Math.random()));
            float targetYaw = yaw;
            yaw = lastYaw + factor * (yaw - lastYaw);
            pitch = lastPitch + factor * (pitch - lastPitch);
            if (Math.abs(yaw - targetYaw) < 5.0F) {
                delay = 0;
            }
        }
        float[] rotation = fixSensitivity(yaw, pitch, lastYaw, lastPitch);
        if (rotation[0] != lastYaw) {
            yawOffset = randomOffset();
        } else {
            rotation[1] = lastPitch;
        }
        return rotation;
    }

    private float[] fixSensitivity(float yaw, float pitch, float lastYaw, float lastPitch) {
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        double step = (double) (sensitivity * sensitivity * sensitivity) * 1.2D;
        return new float[]{lastYaw + (float) (Math.round((yaw - lastYaw) / step) * step),
                MathHelper.clamp_float(lastPitch + (float) (Math.round((pitch - lastPitch) / step) * step),
                        -90.0F, 90.0F)};
    }

    protected abstract void updateKeepY();

    protected abstract void resetKeepYCycle();

    protected abstract void updateKeepYMotion();

    protected abstract void updateKeepYInput(MovementInput input);

    protected void onKeepYPlaced() {
    }

    protected boolean isKeepYRequested() {
        return mc.currentScreen == null;
    }

    protected final void enableKeepY() {
        resetKeepYCycle();
        keepYActive = true;
    }

    protected final void disableKeepY() {
        keptY = -1;
        keepYActive = false;
        resetKeepYCycle();
    }

    protected final boolean canKeepY() {
        return active && isKeepYRequested() && !towering && !jumpKeyDown()
                && isMoving() && !mc.thePlayer.isSneaking();
    }

    void onPreMotion() {
        if (!hasWorld()) {
            return;
        }
        airborneTicks = mc.thePlayer.onGround ? 0 : airborneTicks + 1;
        updateTowerSetting();
        if (towering) {
            diagonalTower = isDiagonal();
            if (diagonalTower) {
                diagonalTicks = 1;
            } else if (diagonalTicks > 0 && ++diagonalTicks < 8) {
                diagonalTower = true;
            }
            towerStage = advanceJumpCycle(towerStage);
        }
        if (canKeepY()) {
            updateKeepYMotion();
        }
    }

    protected final int advanceJumpCycle(int stage) {
        if (stage == 0 && mc.thePlayer.onGround) {
            if (legitTicks == 0) {
                legitTicks = 5;
                return 1;
            }
            return 2;
        }
        if (stage == 1) {
            delay = legitTicks;
            return 2;
        }
        return stage;
    }

    private void correctMovementInput() {
        if (!active || !hasWorld() || !RotationHelper.get().isRotationOwner(owner)) {
            return;
        }
        MovementInput input = mc.thePlayer.movementInput;
        float[] corrected = correctMovement(mc.thePlayer.rotationYaw, getMovementYaw(),
                input.moveForward, input.moveStrafe);
        input.moveForward = corrected[0];
        input.moveStrafe = corrected[1];
    }

    static float[] correctMovement(float cameraYaw, float serverYaw, float forward, float strafe) {
        if (forward == 0.0F && strafe == 0.0F) {
            return new float[]{forward, strafe};
        }
        float target = MathHelper.wrapAngleTo180_float(moveYaw(cameraYaw, forward, strafe));
        float bestForward = 0.0F;
        float bestStrafe = 0.0F;
        float best = -1.0F;
        for (float nextForward = -1.0F; nextForward <= 1.0F; nextForward++) {
            for (float nextStrafe = -1.0F; nextStrafe <= 1.0F; nextStrafe++) {
                if (nextForward == 0.0F && nextStrafe == 0.0F) {
                    continue;
                }
                float yaw = MathHelper.wrapAngleTo180_float(moveYaw(serverYaw, nextForward, nextStrafe));
                float difference = Math.abs(MathHelper.wrapAngleTo180_float(target - yaw));
                if (best == -1.0F || difference < best) {
                    best = difference;
                    bestForward = nextForward;
                    bestStrafe = nextStrafe;
                }
            }
        }
        float speed = Math.max(Math.abs(forward), Math.abs(strafe));
        return new float[]{bestForward * speed, bestStrafe * speed};
    }

    private static float moveYaw(float yaw, float forward, float strafe) {
        float factor = 1.0F;
        if (forward < 0.0F) {
            yaw += 180.0F;
            factor = -0.5F;
        } else if (forward > 0.0F) {
            factor = 0.5F;
        }
        if (strafe > 0.0F) {
            yaw -= 90.0F * factor;
        } else if (strafe < 0.0F) {
            yaw += 90.0F * factor;
        }
        return yaw;
    }

    void onPostPlayerInput() {
        if (!hasWorld()) {
            return;
        }
        MovementInput input = mc.thePlayer.movementInput;
        updateTowerSetting();
        updateJumpInput();
        if (canKeepY()) {
            updateKeepYInput(input);
        }
        if (canTower()) {
            towering = true;
            input.jump = towerStage == 2;
            if (towerStage == 2) {
                towerStage = 0;
            }
            if (diagonalTower && airborneTicks == 1) {
                input.moveForward = 0.0F;
                input.moveStrafe = 0.0F;
            }
        } else if (towering) {
            towering = false;
            towerStage = 0;
            keptY = -1;
        }
        EventMovementInput movement = new EventMovementInput(input.moveForward, input.moveStrafe, input.jump);
        McpRuntime.EVENTS.post(movement);
        input.moveForward = movement.isCanceled() ? 0.0F : movement.getSpeed();
        input.moveStrafe = movement.isCanceled() ? 0.0F : movement.getStrafe();
        input.jump = movement.isJumping();
        correctMovementInput();
    }

    private void updateJumpInput() {
        MovementInput input = mc.thePlayer.movementInput;
        if (jumpState == 1) {
            if (jumpKeyDown()) {
                input.jump = false;
                jumpState = 2;
            } else {
                jumpState = 0;
            }
        } else if (jumpState == 2) {
            input.jump = true;
            jumpState = 0;
        }
    }

    @Subscribe
    public void onDisabledPlayerInput(PostPlayerInputEvent event) {
        if (!hasWorld()) {
            McpRuntime.EVENTS.unregister(this);
            return;
        }
        updateJumpInput();
        if (jumpState == 0 && placed.isEmpty()) {
            McpRuntime.EVENTS.unregister(this);
        }
    }

    @Subscribe
    public void onDisabledRenderWorld(RenderWorldLastEvent event) {
        if (hasWorld()) {
            renderPlaced();
        }
        if (jumpState == 0 && placed.isEmpty()) {
            McpRuntime.EVENTS.unregister(this);
        }
    }

    @Subscribe
    public void onDisabledWorldUnload(WorldEvent.Unload event) {
        reset();
    }

    private boolean canTower() {
        long elapsed = sincePlace();
        return owner.isTellyTowerEnabled() && active && mc.currentScreen == null
                && !mc.thePlayer.capabilities.isFlying && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava() && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock
                && isMoving() && !mc.thePlayer.isSneaking()
                && (jumpKeyDown() || towerStage != 0 && legitTicks != 0)
                && (elapsed < 400L || elapsed > 700L);
    }

    private void updateTowerSetting() {
        if (!owner.isTellyTowerEnabled()) {
            towering = false;
            towerStage = 0;
            diagonalTower = false;
            diagonalTicks = 0;
        }
    }

    void onPostSprintUpdate() {
        if (!hasWorld() || ModuleManager.sprint == null || !ModuleManager.sprint.isEnabled()) {
            return;
        }
        if (!canSprint()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            mc.thePlayer.resetSprintToggleTimer();
            mc.thePlayer.setSprinting(false);
        }
    }

    boolean canSprint() {
        if (!active || towering || !hasWorld()) {
            return true;
        }
        return canKeepY() ? legitTicks > 0
                : mc.thePlayer.ticksExisted != startTick || jumpState != 2;
    }

    float getMovementYaw() {
        Float yaw = RotationHelper.get().getServerYaw();
        return yaw == null ? mc.thePlayer.rotationYaw : yaw;
    }

    private ItemStack selectBlocks() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (isScaffoldBlock(held) && held.stackSize > 1) {
            return held;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.stackSize <= 0) {
                mc.thePlayer.inventory.setInventorySlotContents(slot, null);
            }
        }
        int bestSize = -1;
        int bestSlot = -1;
        for (int slot = 8; slot >= 0; slot--) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (slot == mc.thePlayer.inventory.currentItem || !isScaffoldBlock(stack)
                    || stack.stackSize <= bestSize
                    || isScaffoldBlock(held) && stack.stackSize <= held.stackSize) {
                continue;
            }
            bestSize = stack.stackSize;
            bestSlot = slot;
        }
        if (bestSlot >= 0) {
            mc.thePlayer.inventory.currentItem = bestSlot;
        }
        return mc.thePlayer.getHeldItem();
    }

    static boolean isScaffoldBlock(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return (block == Blocks.glass || block.isFullBlock())
                && block != Blocks.gravel && block != Blocks.sand && block != Blocks.soul_sand
                && block != Blocks.tnt && block != Blocks.crafting_table && block != Blocks.furnace
                && block != Blocks.dispenser && block != Blocks.dropper && block != Blocks.noteblock
                && block != Blocks.command_block && block != Blocks.pumpkin;
    }

    private static boolean isInteractable(Block block) {
        return block instanceof BlockContainer || block == Blocks.crafting_table
                || block == Blocks.anvil || block == Blocks.bed || block == Blocks.lever
                || block instanceof BlockTrapDoor || block instanceof BlockFence
                || block instanceof BlockFenceGate
                || block instanceof BlockDoor && block != Blocks.iron_door;
    }

    private boolean isReplaceable(BlockPos position) {
        Block block = mc.theWorld.getBlockState(position).getBlock();
        return block == Blocks.air || block == Blocks.fire || block instanceof BlockLiquid
                || block.getMaterial().isReplaceable();
    }

    private boolean nearEdge(double distance) {
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int y = MathHelper.floor_double(mc.thePlayer.posY - 0.01D);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        if (mc.theWorld.isAirBlock(new BlockPos(x, y, z))) {
            return true;
        }
        double fractionX = mc.thePlayer.posX - x;
        double fractionZ = mc.thePlayer.posZ - z;
        return fractionX < distance && mc.theWorld.isAirBlock(new BlockPos(x - 1, y, z))
                || fractionX > 1.0D - distance && mc.theWorld.isAirBlock(new BlockPos(x + 1, y, z))
                || fractionZ < distance && mc.theWorld.isAirBlock(new BlockPos(x, y, z - 1))
                || fractionZ > 1.0D - distance && mc.theWorld.isAirBlock(new BlockPos(x, y, z + 1));
    }

    private float forwardInput() {
        return (mc.gameSettings.keyBindForward.isKeyDown() ? 1.0F : 0.0F)
                - (mc.gameSettings.keyBindBack.isKeyDown() ? 1.0F : 0.0F);
    }

    private float strafeInput() {
        return (mc.gameSettings.keyBindLeft.isKeyDown() ? 1.0F : 0.0F)
                - (mc.gameSettings.keyBindRight.isKeyDown() ? 1.0F : 0.0F);
    }

    private boolean isMoving() {
        return mc.thePlayer.movementInput.moveForward != 0.0F
                || mc.thePlayer.movementInput.moveStrafe != 0.0F;
    }

    private boolean isDiagonal() {
        float angle = Math.abs(mc.thePlayer.rotationYaw % 90.0F);
        return angle > 10.0F && angle < 80.0F
                || forwardInput() != 0.0F && strafeInput() != 0.0F;
    }

    private boolean jumpKeyDown() {
        return GameSettings.isKeyDown(mc.gameSettings.keyBindJump);
    }

    private boolean hasWorld() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    private float movementAngle() {
        double angle = Math.toDegrees(Math.atan2(strafeInput(), -forwardInput()));
        return mc.thePlayer.rotationYaw + (float) ((angle + 360.0D) % 360.0D);
    }

    private long sincePlace() {
        return Math.abs(System.currentTimeMillis() - placedAt);
    }

    private void stop() {
        if (active) {
            stoppedAt = System.currentTimeMillis();
            if (hasWorld() && !screenOpen() && rememberedSlot != -1) {
                mc.thePlayer.inventory.currentItem = rememberedSlot;
                rememberedSlot = -1;
            }
            if (keepYActive) {
                disableKeepY();
            }
            jumpState = 1;
        }
        active = false;
        towering = false;
        towerStage = 0;
        diagonalTicks = 0;
        keptY = -1;
        lastPlaced = null;
        aimPitch = UNSET_PITCH;
    }

    void observePacket(Packet<?> packet) {
        if (packet instanceof C03PacketPlayer) {
            sentCloseWindow = false;
            sentUseEntity = false;
            sentAnyDigging = false;
            if (hasWorld()) {
                playerYawAtSend = mc.thePlayer.rotationYaw;
            }
        } else if (packet instanceof C02PacketUseEntity) {
            sentUseEntity = true;
        } else if (packet instanceof C07PacketPlayerDigging) {
            sentAnyDigging = true;
        } else if (packet instanceof C0DPacketCloseWindow) {
            sentCloseWindow = true;
        }
    }

    boolean pauseForKillAura() {
        if (!hasWorld() || countBlocks() == 0) {
            return true;
        }
        if (KillAura.stopBlocking(false)) {
            pausedTicks = 2;
        }
        return false;
    }

    private boolean screenOpen() {
        return mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)
                && !(mc.currentScreen instanceof idle.miau.clickgui.ClickGui)
                && !(mc.currentScreen instanceof idle.miau.clickgui.modern.MiauClickGui)
                && !(mc.currentScreen instanceof idle.miau.clickgui.miau.MiauClickGui)
                && !(mc.currentScreen instanceof idle.miau.clickgui.panel.MiauPanelClickGui);
    }

    private int countBlocks() {
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (isScaffoldBlock(stack)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private void updateCount() {
        int count = countBlocks();
        String color = count <= 5 ? "\u00a7c" : count <= 15 ? "\u00a76" : count <= 25 ? "\u00a7e" : null;
        countText = color == null ? count + " blocks" : color + count + "\u00a7f blocks";
    }

    void renderCount() {
        if (countText == null || ModuleManager.blockIn != null
                && ModuleManager.blockIn.hasPercentageText()) {
            return;
        }
        long now = System.currentTimeMillis();
        int alpha;
        if (active) {
            long elapsed = now - startedAt;
            alpha = elapsed < 125L ? (int) ((float) elapsed / 125.0F * 240.0F) : 240;
        } else {
            long elapsed = now - stoppedAt;
            if (elapsed < 125L) {
                alpha = (int) (240.0F - (float) elapsed / 125.0F * 240.0F);
            } else {
                alpha = 0;
                countText = null;
            }
        }
        if (alpha <= 0) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.fontRendererObj.drawString(countText,
                resolution.getScaledWidth() / 2.0F + 11.0F,
                resolution.getScaledHeight() / 2.0F - 3.0F, alpha << 24 | 0xFFFFFF, true);
        GlStateManager.disableBlend();
    }

    boolean isCounterHidden() {
        return !active && System.currentTimeMillis() - stoppedAt >= 125L;
    }

    void renderPlaced() {
        long now = System.currentTimeMillis();
        double blend = (Math.sin(now / 1.0E8D * 0.5D * 400000.0D) + 1.0D) * 0.5D;
        int firstWeight = (int) (blend * 255.0D);
        int secondWeight = 255 - firstWeight;
        int red = (255 * firstWeight + 243 * secondWeight) >> 8;
        int green = (200 * firstWeight + 58 * secondWeight) >> 8;
        int blue = (200 * firstWeight + 106 * secondWeight) >> 8;
        Iterator<Map.Entry<BlockPos, Long>> iterator = placed.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            float progress = (now - Math.abs(entry.getValue())) / 500.0F;
            if (progress >= 1.0F) {
                iterator.remove();
                continue;
            }
            int alpha = (int) (255.0F - Math.min(progress * 255.0F, 255.0F));
            RenderUtils.renderBlock(entry.getKey(), alpha << 24 | red << 16 | green << 8 | blue,
                    true, false);
        }
    }

    private static float randomOffset() {
        return (float) (Math.random() - 0.5D);
    }

    private static float randomFloat(float minimum, float maximum) {
        return (float) ((double) minimum + (double) (maximum - minimum) * Math.random());
    }

    private final class PacketTracker {
        @Subscribe
        public void onPacketDispatched(DispatchPacketEvent event) {
            observePacket(event.getPacket());
        }

        @Subscribe
        public void onWorldUnload(WorldEvent.Unload event) {
            reset();
        }
    }
}
