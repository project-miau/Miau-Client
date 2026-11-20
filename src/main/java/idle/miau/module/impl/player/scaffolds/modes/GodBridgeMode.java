package idle.miau.module.impl.player.scaffolds.modes;

import idle.miau.module.impl.player.Scaffold;
import idle.miau.script.api.MiauScriptMode;
import idle.miau.utility.RotationUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static idle.miau.script.api.MiauScriptApi.*;

public final class GodBridgeMode extends MiauScriptMode {
private final Scaffold scaffold;

public GodBridgeMode(Scaffold owner) {
    super(owner, "GodBridge");
    scaffold = owner;
    initializeScript();
}

public boolean isRunning() {
    return running;
}

public boolean shouldHoldRotation() {
    return aiming || waitingToStart;
}

public float getPreparationYaw(float fallback) {
    return shouldHoldRotation() ? scriptedRotationYaw : fallback;
}

public float getPreparationPitch(float fallback) {
    return shouldHoldRotation() ? scriptedRotationPitch : fallback;
}

public float getPlacementRotationSpeed() {
    return placementRotationSpeed(modules.getSlider(scriptName, "Rotation Speed"));
}

static float placementRotationSpeed(double durationMs) {
    double duration = Math.max(50.0, Math.min(1000.0, durationMs));
    return (float) (18000.0 / duration);
}

public float getPlacementYaw(float fallback) {
    return running ? baseYaw + placementYawJitter : fallback;
}

public float getPlacementPitch(float fallback) {
    return running ? currentGodbridgePitch : fallback;
}

public float getPlacementPitchPreference(float fallback) {
    return running
            ? clamp(currentGodbridgePitch + placementPitchJitter, -90.0f, 90.0f)
            : fallback;
}

public float resolvePlacementPitch(float targetPitch, float emittedPitch) {
    if (!running) return targetPitch;
    float minimum = emittedPitch - MAX_PLACEMENT_PITCH_CHANGE;
    float maximum = emittedPitch + MAX_PLACEMENT_PITCH_CHANGE;
    if (hasLastPlacementPitch) {
        minimum = Math.max(minimum, lastPlacementPitch - MAX_PLACEMENT_PITCH_CHANGE);
        maximum = Math.min(maximum, lastPlacementPitch + MAX_PLACEMENT_PITCH_CHANGE);
    }
    if (minimum > maximum) {
        return constrainPitch(
                lastPlacementPitch, targetPitch, MAX_PLACEMENT_PITCH_CHANGE);
    }
    return clamp(targetPitch, minimum, maximum);
}

public void acceptPlacementPitch(float pitch) {
    if (running) currentGodbridgePitch = clamp(pitch, -90.0f, 90.0f);
}

public float getMaximumYawDelta() {
    return MAX_GODBRIDGE_YAW_DELTA;
}

public void recordScaffoldPlacement(float emittedPitch) {
    if (!running) return;
    currentGodbridgePitch = clamp(emittedPitch, -90.0f, 90.0f);
    lastPlacementPitch = currentGodbridgePitch;
    hasLastPlacementPitch = true;
    placedThisTick = true;
    blocksPlacedSinceJump++;
}

public void updatePlacementSafety(boolean placementAvailable, boolean willFall) {
    if (!running) return;
    if (willFall && !placementAvailable) {
        startEdgeRecovery();
    } else if (edgeRecoveryTicks <= 0) {
        edgeRecovery = false;
    }
}

public void updatePlacementResult(boolean placed, boolean danger) {
    if (running && danger && !placed) startEdgeRecovery();
}

public int getPlacementFloorY(int fallback) {
    return running && bridgeFloorY != Integer.MIN_VALUE ? bridgeFloorY : fallback;
}

boolean armed = false;
boolean aiming = false;
boolean waitingToStart = false;
boolean running = false;
HashSet<String> cancelledGhostBlocks = new HashSet<String>();
boolean tellyAutoPlaceWindow = false;

final float GODBRIDGE_PITCH_BASE = 75.6f;
float currentGodbridgePitch = 75.6f;
float baseYaw = 0.0f;
int travelX = 0;
int travelZ = 0;
int tellyLaneY = -2147483648;
int tellyFloorY = -2147483648;

long takeoverDetectionAt = 0L;
boolean takeoverCameraValid = false;
float takeoverCameraYaw = 0.0f;
float takeoverCameraPitch = 0.0f;
float takeoverAccumulated = 0.0f;
long takeoverLastFrameAt = 0L;
long freezeLastTickAt = 0L;

boolean ignoreForwardUntilRelease = false;
boolean ignoreBackUntilRelease = false;
boolean ignoreLeftUntilRelease = false;
boolean ignoreRightUntilRelease = false;
boolean ignoreJumpUntilRelease = false;
boolean ignoreSneakUntilRelease = false;
boolean ignoreSprintUntilRelease = false;

boolean rotationActive = false;
long rotationDuration = 250L;
float rotationTargetYaw = 0.0f;
float rotationTargetPitch = 0.0f;
float scriptedRotationYaw = 0.0f;
float scriptedRotationPitch = 0.0f;
int rotationTicks = 0;
float placementYawJitter = 0.0f;
float placementYawJitterTarget = 0.0f;
float placementPitchJitter = 0.0f;
float placementPitchJitterTarget = 0.0f;
int placementJitterTicks = 0;
float lastPlacementPitch = 75.6f;
boolean hasLastPlacementPitch = false;
boolean edgeRecovery = false;
int edgeRecoveryTicks = 0;
int bridgeFloorY = Integer.MIN_VALUE;
boolean placedThisTick = false;

int blocksPlacedSinceJump = 0;
boolean isAutoJumping = false;
int nextJumpBlock = 8;

static final double SENSITIVITY_QUANTUM = 0.03404715;
static final long STOP_AT_START_DURATION_MS = 100L;
static final float MAX_GODBRIDGE_YAW_DELTA = 8.5f;
static final float MAX_PLACEMENT_PITCH_JITTER = 0.12f;
static final float MAX_PLACEMENT_PITCH_CHANGE = 2.25f;
long bridgeStartAt = 0L;

protected void onLoad() {
    modules.registerButton("Stop at start", true);
    modules.registerButton("Auto Jump", true);
    modules.registerSlider("Jump per block min", " blocks", 8, 1, 30, 1);
    modules.registerSlider("Jump per block max", " blocks", 12, 1, 30, 1);
    modules.registerSlider("Rotation Speed", " ms", 280, 50, 1000, 10);
}

public void updateSettingsVisibility(boolean visible) {
    boolean showJumpRange = visible && !modules.getButton(scriptName, "Auto Jump");
    modules.setSliderVisible("Jump per block min", showJumpRange);
    modules.setSliderVisible("Jump per block max", showJumpRange);
}

public void onEnable() {
    armAutomation();
}

public void onDisable() {
    stopAutomation();
}

protected void onWorldJoin(Entity entity) {
    if (entity != null && entity.isUser) stopAutomation();
}

protected void onPreUpdate() {
    placedThisTick = false;
    if (running || aiming || waitingToStart) {
        keybinds.setPressed("attack", false);
        if (aiming) applySmoothedRotation();
    }

    if (waitingToStart) {
        if (client.time() >= bridgeStartAt) startBridging();
        else return;
    }

    if (armed && !running && !aiming && !waitingToStart) {
        updateAutomaticActivation();
    }

    if (!running) return;

    updatePlacementJitter();

    long freezeNow = client.time();
    if (freezeLastTickAt != 0L && freezeNow - freezeLastTickAt > 300L) {
        stopAutomation();
        return;
    }
    freezeLastTickAt = freezeNow;

    Entity player = client.getPlayer();
    if (player == null || player.isDead() || player.getFallDistance() > 7.0f) {
        stopAutomation();
        return;
    }
    if (!scaffold.ensurePlacementBlock()) {
        stopAutomation();
        return;
    }

}

void updateAutomaticActivation() {
    Entity player = client.getPlayer();
    boolean playerReady = player != null && !player.isDead() && player.onGround();
    boolean screenClear = client.getScreen().isEmpty();
    boolean blockReady = playerReady && screenClear && scaffold.ensurePlacementBlock();
    if (!blockReady) return;
    beginAiming();
}

void handleAutoSwap(Entity player) {
    if (!modules.getButton(scriptName, "Auto swap")) return;

    int threshold = 5;
    ItemStack held = player.getHeldItem();
    int heldCount = held != null && isUsableBlockStack(held) ? held.stackSize : 0;
    if (heldCount > threshold) return;

    int bestSlot = -1;
    int bestSize = heldCount;
    for (int slot = 0; slot <= 8; slot++) {
        if (slot == inventory.getSlot()) continue;
        ItemStack stack = inventory.getStackInSlot(slot);
        if (!isUsableBlockStack(stack)) continue;
        if (stack.stackSize > bestSize) {
            bestSize = stack.stackSize;
            bestSlot = slot;
        }
    }

    if (bestSlot != -1) inventory.setSlot(bestSlot);
}

static int[] travelDirectionFromYaw(float yaw) {
    double radians = Math.toRadians(yaw);
    double rawX = Math.sin(radians) - Math.cos(radians);
    double rawZ = -Math.cos(radians) - Math.sin(radians);
    if (Math.abs(rawX) >= Math.abs(rawZ)) return new int[]{rawX >= 0.0 ? 1 : -1, 0};
    return new int[]{0, rawZ >= 0.0 ? 1 : -1};
}

protected boolean onMouse(int button, boolean state, int mouseX, int mouseY) {
    if ((running || aiming || waitingToStart) && button == 0) {
        keybinds.setPressed("attack", false);
        return false;
    }
    return true;
}

boolean onKey(String keyName, int keyCode, boolean state, boolean inGui) {
    boolean dropKey = keyCode == keybinds.getKeyCode("drop")
            || (keyName != null && keyName.toLowerCase().contains("drop"));
    if (isDropProtected() && dropKey) {
        keybinds.setPressed("drop", false);
        return false;
    }

    if ((running || aiming || waitingToStart) && state && !inGui) {
        int kF = keybinds.getKeyCode("forward");
        if (keyCode == kF) {
            stopAutomation();
            return true;
        }
    }

    if (!running && !aiming && !waitingToStart) return true;

    if (keyCode == keybinds.getKeyCode("sneak")) {
        suppressSneakInput();
        return false;
    }
    if (!state) clearInitialMovementHold(keyCode);
    if (state
            && isManualMovementKey(keyCode)
            && !isInitialMovementHold(keyCode)
            && !isScriptHeldKey(keyCode)) {
        stopAutomation();
        return true;
    }
    if (inGui) return true;
    if (isManualMovementKey(keyCode)) return false;
    return true;
}

boolean isScriptHeldKey(int keyCode) {
    if (keyCode == keybinds.getKeyCode("forward")) return keybinds.isPressed("forward");
    if (keyCode == keybinds.getKeyCode("back")) return keybinds.isPressed("back");
    if (keyCode == keybinds.getKeyCode("left")) return keybinds.isPressed("left");
    if (keyCode == keybinds.getKeyCode("right")) return keybinds.isPressed("right");
    if (keyCode == keybinds.getKeyCode("jump")) return keybinds.isPressed("jump");
    if (keyCode == keybinds.getKeyCode("sprint")) return keybinds.isPressed("sprint");
    return false;
}

boolean isManualMovementKey(int keyCode) {
    return keyCode == keybinds.getKeyCode("forward")
            || keyCode == keybinds.getKeyCode("back")
            || keyCode == keybinds.getKeyCode("left")
            || keyCode == keybinds.getKeyCode("right")
            || keyCode == keybinds.getKeyCode("jump")
            || keyCode == keybinds.getKeyCode("sneak")
            || keyCode == keybinds.getKeyCode("sprint");
}

void captureInitialMovementHolds() {
    ignoreForwardUntilRelease = keybinds.isKeyDown(keybinds.getKeyCode("forward"));
    ignoreBackUntilRelease = keybinds.isKeyDown(keybinds.getKeyCode("back"));
    ignoreLeftUntilRelease = keybinds.isKeyDown(keybinds.getKeyCode("left"));
    ignoreRightUntilRelease = keybinds.isKeyDown(keybinds.getKeyCode("right"));
    ignoreJumpUntilRelease = keybinds.isKeyDown(keybinds.getKeyCode("jump"));
    ignoreSneakUntilRelease = keybinds.isKeyDown(keybinds.getKeyCode("sneak"));
    ignoreSprintUntilRelease = keybinds.isKeyDown(keybinds.getKeyCode("sprint"));
}

boolean isInitialMovementHold(int keyCode) {
    if (keyCode == keybinds.getKeyCode("forward")) return ignoreForwardUntilRelease;
    if (keyCode == keybinds.getKeyCode("back")) return ignoreBackUntilRelease;
    if (keyCode == keybinds.getKeyCode("left")) return ignoreLeftUntilRelease;
    if (keyCode == keybinds.getKeyCode("right")) return ignoreRightUntilRelease;
    if (keyCode == keybinds.getKeyCode("jump")) return ignoreJumpUntilRelease;
    if (keyCode == keybinds.getKeyCode("sneak")) return ignoreSneakUntilRelease;
    if (keyCode == keybinds.getKeyCode("sprint")) return ignoreSprintUntilRelease;
    return false;
}

void clearInitialMovementHold(int keyCode) {
    if (keyCode == keybinds.getKeyCode("forward")) ignoreForwardUntilRelease = false;
    if (keyCode == keybinds.getKeyCode("back")) ignoreBackUntilRelease = false;
    if (keyCode == keybinds.getKeyCode("left")) ignoreLeftUntilRelease = false;
    if (keyCode == keybinds.getKeyCode("right")) ignoreRightUntilRelease = false;
    if (keyCode == keybinds.getKeyCode("jump")) ignoreJumpUntilRelease = false;
    if (keyCode == keybinds.getKeyCode("sneak")) ignoreSneakUntilRelease = false;
    if (keyCode == keybinds.getKeyCode("sprint")) ignoreSprintUntilRelease = false;
}

void clearInitialMovementHolds() {
    ignoreForwardUntilRelease = false;
    ignoreBackUntilRelease = false;
    ignoreLeftUntilRelease = false;
    ignoreRightUntilRelease = false;
    ignoreJumpUntilRelease = false;
    ignoreSneakUntilRelease = false;
    ignoreSprintUntilRelease = false;
}

boolean detectManualCameraTakeover() {
    if (!running || client.time() < takeoverDetectionAt) return false;
    Entity player = client.getPlayer();
    if (player == null) return false;

    long now = client.time();
    float expectedYaw = scriptedRotationYaw;
    float expectedPitch = scriptedRotationPitch;
    if (!takeoverCameraValid) {
        takeoverCameraValid = true;
        takeoverCameraYaw = player.getYaw();
        takeoverCameraPitch = player.getPitch();
        takeoverAccumulated = 0.0f;
        takeoverLastFrameAt = now;
        return false;
    }

    double yawInput = Math.abs(tellyWrapAngle(player.getYaw() - expectedYaw));
    double pitchInput = Math.abs(player.getPitch() - expectedPitch);
    double noiseFloor = SENSITIVITY_QUANTUM * 0.45;

    long elapsed = Math.max(0L, now - takeoverLastFrameAt);
    takeoverLastFrameAt = now;
    takeoverAccumulated -= (float) (elapsed * 0.045);
    if (takeoverAccumulated < 0.0f) takeoverAccumulated = 0.0f;
    if (yawInput > noiseFloor || pitchInput > noiseFloor) {
        takeoverAccumulated += (float) (yawInput + pitchInput);
    }

    takeoverCameraYaw = player.getYaw();
    takeoverCameraPitch = player.getPitch();

    if (takeoverAccumulated >= 25.0f) {
        stopAutomation();
        return true;
    }
    return false;
}

protected boolean onPacketSent(CPacket packet) {
    if (isDropProtected() && packet instanceof C07) {
        C07 digging = (C07) packet;
        String status = digging.status == null ? "" : String.valueOf(digging.status).toUpperCase();
        if (status.contains("DROP")) return false;
    }
    if (!running) return true;
    if (packet instanceof C02) {
        C02 interaction = (C02) packet;
        if ("ATTACK".equals(interaction.action)) return false;
    }
    if (packet instanceof C07) {
        C07 digging = (C07) packet;
        String status = digging.status == null ? "" : String.valueOf(digging.status).toUpperCase();
        if (status.contains("DESTROY")) return false;
    }
    if (packet instanceof C0B) {
        C0B action = (C0B) packet;
        if ("START_SNEAKING".equals(action.action) && !edgeRecovery) return false;
    }
    return true;
}

boolean isDropProtected() {
    return running || aiming || waitingToStart;
}

protected void onPostPlayerInput() {
    if (aiming || waitingToStart) {
        applyMovement(0.0f, 0.0f, false, false, false);
        return;
    }
    if (!running) return;
    if (edgeRecovery) {
        isAutoJumping = false;
        Entity recoveryPlayer = client.getPlayer();
        boolean airborne = recoveryPlayer != null && !recoveryPlayer.onGround();
        float recoveryInput = airborne ? 1.0f : -1.0f;
        applyMovement(recoveryInput, recoveryInput, false, false, true);
        client.setForward(recoveryInput * 0.3f);
        client.setStrafe(recoveryInput * 0.3f);
        if (edgeRecoveryTicks > 0) edgeRecoveryTicks--;
        edgeRecovery = edgeRecoveryTicks > 0;
        return;
    }
    suppressSneakInput();

    boolean manualJump = false;
    try {
        manualJump = keybinds.isKeyDown(keybinds.getKeyCode("jump"));
    } catch (Exception ignored) {}

    Entity player = client.getPlayer();
    isAutoJumping = player != null && shouldTriggerJump(player);
    if (isAutoJumping) {
        blocksPlacedSinceJump = 0;
        nextJumpBlock = randomJumpInterval();
    }

    boolean finalJump = manualJump || isAutoJumping;
    applyMovement(-1.0f, -1.0f, finalJump, false, false);
}

boolean shouldTriggerJump(Entity player) {
    if (!player.onGround() || !placedThisTick) return false;
    if (!modules.getButton(scriptName, "Auto Jump")) {
        return blocksPlacedSinceJump >= nextJumpBlock;
    }
    Vec3 position = player.getPosition();
    Vec3 previous = player.getLastPosition();
    if (position == null || previous == null) return false;
    double deltaX = position.x - previous.x;
    double deltaZ = position.z - previous.z;
    double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    double edgeProgress = travelEdgeProgress(position, travelX, travelZ);
    return shouldAutomaticallyJump(
            placedThisTick, blocksPlacedSinceJump, speed, edgeProgress);
}

static boolean shouldAutomaticallyJump(
        boolean placementConfirmed, int placedBlocks, double speed, double edgeProgress) {
    if (!placementConfirmed) return false;
    int target = automaticJumpTarget(speed);
    if (placedBlocks >= target + 2) return true;
    double requiredProgress = speed >= 0.24 ? 0.55 : 0.68;
    return placedBlocks >= target && edgeProgress >= requiredProgress;
}

static int automaticJumpTarget(double speed) {
    if (speed >= 0.24) return 7;
    if (speed >= 0.17) return 8;
    return 9;
}

static double travelEdgeProgress(Vec3 position, int directionX, int directionZ) {
    double coordinate = directionX != 0 ? position.x : position.z;
    int direction = directionX != 0 ? directionX : directionZ;
    double fraction = coordinate - Math.floor(coordinate);
    return direction >= 0 ? fraction : 1.0 - fraction;
}

int randomJumpInterval() {
    int minimum = (int) modules.getSlider(scriptName, "Jump per block min");
    int maximum = (int) modules.getSlider(scriptName, "Jump per block max");
    return randomJumpInterval(minimum, maximum, Math.random());
}

static int randomJumpInterval(int minimum, int maximum, double random) {
    int low = Math.max(1, Math.min(minimum, maximum));
    int high = Math.max(low, Math.max(minimum, maximum));
    double normalized = Math.max(0.0, Math.min(0.999999999, random));
    return low + (int) Math.floor(normalized * (high - low + 1));
}

protected boolean onPacketReceived(SPacket packet) {
    if ((running || aiming || waitingToStart)
            && packet != null && "S08PacketPlayerPosLook".equals(packet.name)) {
        stopAutomation();
        return true;
    }
    return true;
}

void armAutomation() {
    armed = true;
    running = false;
    aiming = false;
    waitingToStart = false;
    rotationActive = false;
    placementYawJitter = 0.0f;
    placementYawJitterTarget = 0.0f;
    placementPitchJitter = 0.0f;
    placementPitchJitterTarget = 0.0f;
    placementJitterTicks = 0;
    lastPlacementPitch = GODBRIDGE_PITCH_BASE;
    hasLastPlacementPitch = false;
    edgeRecovery = false;
    edgeRecoveryTicks = 0;
    bridgeFloorY = Integer.MIN_VALUE;
    placedThisTick = false;
    bridgeStartAt = 0L;
}

void calculateTravelDirection(float yaw) {
    int[] dir = travelDirectionFromYaw(yaw);
    travelX = dir[0];
    travelZ = dir[1];
}

void beginAiming() {
    Entity player = client.getPlayer();
    if (player == null || !scaffold.ensurePlacementBlock()) {
        return;
    }

    float currentYaw = player.getYaw();
    baseYaw = diagonalPlacementYaw(currentYaw);
    calculateTravelDirection(baseYaw);
    blocksPlacedSinceJump = 0;
    isAutoJumping = false;
    nextJumpBlock = randomJumpInterval();
    placementYawJitter = quantizeFrom(0.0f, randomRotationJitter(0.075f));
    placementYawJitterTarget = placementYawJitter;
    placementPitchJitter = quantizeFrom(
            0.0f, randomRotationJitter(MAX_PLACEMENT_PITCH_JITTER));
    placementPitchJitterTarget = placementPitchJitter;
    placementJitterTicks = 3 + (int) (Math.random() * 5.0);

    armed = false;
    running = false;
    aiming = true;
    waitingToStart = false;

    long rotSpeed = (long) modules.getSlider(scriptName, "Rotation Speed");
    currentGodbridgePitch = GODBRIDGE_PITCH_BASE;
    lastPlacementPitch = currentGodbridgePitch;
    hasLastPlacementPitch = false;
    edgeRecovery = false;
    edgeRecoveryTicks = 0;
    bridgeFloorY = (int) Math.floor(player.getPosition().y) - 1;
    placedThisTick = false;

    setRotationTarget(
            baseYaw + placementYawJitter,
            currentGodbridgePitch + placementPitchJitter,
            rotSpeed);
}

float diagonalPlacementYaw(float viewYaw) {
    float offset = Math.max(0.2f, RotationUtils.getGCD() * 2.0f);
    return Math.round(viewYaw / 45.0f) * 45.0f - 135.0f
            + offset;
}

void startBridging() {
    aiming = false;
    waitingToStart = false;
    running = true;
    bridgeStartAt = 0L;

    freezeLastTickAt = client.time();

    scriptedRotationYaw = rotationTargetYaw;
    scriptedRotationPitch = rotationTargetPitch;
    clearInitialMovementHolds();
    keybinds.setPressed("attack", false);
    applyMovement(-1.0f, -1.0f, false, false, false);
}

void stopAutomation() {
    armed = false;
    running = false;
    aiming = false;
    waitingToStart = false;
    rotationActive = false;
    scriptedRotationYaw = 0.0f;
    scriptedRotationPitch = 0.0f;
    takeoverDetectionAt = 0L;
    takeoverCameraValid = false;
    takeoverCameraYaw = 0.0f;
    takeoverCameraPitch = 0.0f;
    takeoverAccumulated = 0.0f;
    takeoverLastFrameAt = 0L;
    blocksPlacedSinceJump = 0;
    isAutoJumping = false;
    nextJumpBlock = 8;
    placementYawJitter = 0.0f;
    placementYawJitterTarget = 0.0f;
    placementPitchJitter = 0.0f;
    placementPitchJitterTarget = 0.0f;
    placementJitterTicks = 0;
    lastPlacementPitch = GODBRIDGE_PITCH_BASE;
    hasLastPlacementPitch = false;
    edgeRecovery = false;
    edgeRecoveryTicks = 0;
    bridgeFloorY = Integer.MIN_VALUE;
    placedThisTick = false;
    bridgeStartAt = 0L;

    try {
        clearInitialMovementHolds();
        client.setForward(0.0f);
        client.setStrafe(0.0f);
        client.setJump(false);
        client.setSneak(false);
        client.setSprinting(false);
        releaseMovementKeys();
        keybinds.setPressed("attack", keybinds.isMouseDown(0));
    } catch (Exception ignored) {}

    freezeLastTickAt = 0L;
    armed = true;
}

void setRotationTarget(float targetYaw, float targetPitch, long duration) {
    if (rotationActive && Math.abs(targetYaw - rotationTargetYaw) < 0.1f && Math.abs(targetPitch - rotationTargetPitch) < 0.1f) return;

    Entity player = client.getPlayer();
    if (player == null) return;

    float startYaw = player.getYaw();
    float startPitch = player.getPitch();
    scriptedRotationYaw = startYaw;
    scriptedRotationPitch = startPitch;

    rotationTargetYaw = startYaw + tellyWrapAngle(targetYaw - startYaw);
    rotationTargetPitch = clamp(targetPitch, -90.0f, 90.0f);
    rotationDuration = Math.max(1L, duration);
    rotationTicks = 0;
    rotationActive = true;
}

void applySmoothedRotation() {
    if (!rotationActive) return;
    Entity player = client.getPlayer();
    if (player == null) return;

    float yawDifference = tellyWrapAngle(rotationTargetYaw - scriptedRotationYaw);
    float pitchDifference = rotationTargetPitch - scriptedRotationPitch;
    float baseStep = preparationYawStep(rotationDuration);
    float yawStep = humanizedRotationStep(baseStep, Math.abs(yawDifference), rotationTicks);
    float pitchStep = humanizedRotationStep(
            Math.min(6.5f, baseStep * 0.82f), Math.abs(pitchDifference), rotationTicks + 2);
    float nextYaw = scriptedRotationYaw + clamp(yawDifference, -yawStep, yawStep);
    float nextPitch = scriptedRotationPitch + clamp(pitchDifference, -pitchStep, pitchStep);
    scriptedRotationYaw = quantizeFrom(scriptedRotationYaw, nextYaw);
    scriptedRotationPitch = clamp(
            quantizeFrom(scriptedRotationPitch, nextPitch), -90.0f, 90.0f);
    rotationTicks++;

    boolean yawSettled = Math.abs(tellyWrapAngle(rotationTargetYaw - scriptedRotationYaw))
            <= SENSITIVITY_QUANTUM;
    boolean pitchSettled = Math.abs(rotationTargetPitch - scriptedRotationPitch)
            <= SENSITIVITY_QUANTUM;
    if (yawSettled && pitchSettled) {
        rotationActive = false;
        if (aiming) finishAiming();
    }
}

void finishAiming() {
    long holdDuration = startHoldDuration(modules.getButton(scriptName, "Stop at start"));
    if (holdDuration == 0L) {
        startBridging();
        return;
    }
    aiming = false;
    waitingToStart = true;
    bridgeStartAt = client.time() + holdDuration;
    applyMovement(0.0f, 0.0f, false, false, false);
}

static long startHoldDuration(boolean enabled) {
    return enabled ? STOP_AT_START_DURATION_MS : 0L;
}

static float preparationYawStep(long durationMs) {
    float step = 5000.0f / Math.max(50L, Math.min(1000L, durationMs));
    return Math.max(5.0f, Math.min(8.5f, step));
}

static float humanizedRotationStep(float maximumStep, float remaining, int tick) {
    float distanceScale = Math.max(0.34f, Math.min(1.0f, remaining / 28.0f));
    float acceleration = Math.min(1.0f, 0.55f + Math.max(0, tick) * 0.12f);
    float rhythm = 1.0f + (float) Math.sin(tick * 1.37f) * 0.035f;
    return Math.max((float) SENSITIVITY_QUANTUM, maximumStep * distanceScale * acceleration * rhythm);
}

void updatePlacementJitter() {
    if (placementJitterTicks <= 0) {
        placementYawJitterTarget = randomRotationJitter(0.075f);
        placementPitchJitterTarget = randomRotationJitter(MAX_PLACEMENT_PITCH_JITTER);
        placementJitterTicks = 3 + (int) (Math.random() * 5.0);
    } else {
        placementJitterTicks--;
    }
    float delta = clamp(placementYawJitterTarget - placementYawJitter, -0.025f, 0.025f);
    placementYawJitter = quantizeFrom(0.0f, placementYawJitter + delta);
    float pitchDelta = clamp(
            placementPitchJitterTarget - placementPitchJitter,
            -(float) SENSITIVITY_QUANTUM,
            (float) SENSITIVITY_QUANTUM);
    placementPitchJitter = quantizeFrom(0.0f, placementPitchJitter + pitchDelta);
}

static float randomRotationJitter(float amplitude) {
    return (float) ((Math.random() * 2.0 - 1.0) * amplitude);
}

static float constrainPitch(float origin, float target, float maximumChange) {
    float limit = Math.max(0.0f, maximumChange);
    return Math.max(origin - limit, Math.min(origin + limit, target));
}

void startEdgeRecovery() {
    edgeRecovery = true;
    edgeRecoveryTicks = Math.max(edgeRecoveryTicks, 3);
}

float quantizeFrom(float origin, float value) {
    double steps = Math.round((value - origin) / SENSITIVITY_QUANTUM);
    return (float) (origin + steps * SENSITIVITY_QUANTUM);
}

void applyMovement(float forward, float strafe, boolean jumping, boolean sprinting, boolean sneaking) {
    keybinds.setPressed("forward", forward > 0.03f);
    keybinds.setPressed("back", forward < -0.03f);
    keybinds.setPressed("left", strafe > 0.5f);
    keybinds.setPressed("right", strafe < -0.5f);
    keybinds.setPressed("jump", jumping);
    keybinds.setPressed("sneak", sneaking);
    keybinds.setPressed("sprint", sprinting);
    client.setForward(forward);
    client.setStrafe(strafe);
    client.setJump(jumping);
    client.setSneak(sneaking);
    client.setSprinting(sprinting);
}

void suppressSneakInput() {
    keybinds.setPressed("sneak", false);
    client.setSneak(false);
}

void applyUse(boolean pressed) {
    tellyAutoPlaceWindow = pressed;
    keybinds.setPressed("use", pressed);
}

void restorePhysicalUse() {
    tellyAutoPlaceWindow = false;
    keybinds.setPressed("use", keybinds.isMouseDown(1));
}

void releaseMovementKeys() {
    restorePhysicalKey("forward");
    restorePhysicalKey("back");
    restorePhysicalKey("left");
    restorePhysicalKey("right");
    restorePhysicalKey("jump");
    restorePhysicalKey("sneak");
    restorePhysicalKey("sprint");
}

void restorePhysicalKey(String key) {
    int code = keybinds.getKeyCode(key);
    keybinds.setPressed(key, code >= 0 && keybinds.isKeyDown(code));
}

float tellyWrapAngle(float angle) {
    while (angle <= -180.0f) angle += 360.0f;
    while (angle > 180.0f) angle -= 360.0f;
    return angle;
}

float clamp(float value, float minimum, float maximum) {
    if (value < minimum) return minimum;
    if (value > maximum) return maximum;
    return value;
}

final double[] FACE_HIT_OFFSETS = {0.5, 0.25, 0.75, 0.15, 0.85};
final double[] EXTENDED_FACE_HIT_OFFSETS = {0.5, 0.25, 0.75, 0.15, 0.85, 0.35, 0.65, 0.05, 0.95};
final int[] ALLOWED_PLACE_FACES = {2, 3, 4, 5, 1};
final String[] REPLACEABLE_BLOCKS = {"air", "water", "flowing_water", "lava", "flowing_lava", "fire", "tallgrass", "deadbush", "snow_layer", "double_plant", "vine"};
final String[] EXPERIMENTAL_REPLACEABLE_BLOCKS = {
        "sapling", "yellow_flower", "red_flower", "brown_mushroom", "red_mushroom",
        "wheat", "carrots", "potatoes", "nether_wart", "reeds"
};
final String[] UNPLACEABLE_EXACT = {
        "snow_layer", "web", "sapling", "daylight_detector", "beacon", "banner",
        "end_portal_frame", "end_portal", "lever", "stone_button", "wooden_button",
        "skull", "cactus", "double_plant", "waterlily", "carpet", "tripwire_hook",
        "tallgrass", "yellow_flower", "red_flower", "flower_pot", "sign", "ladder",
        "torch", "redstone_torch", "unlit_redstone_torch", "gravel", "clay", "sand",
        "soul_sand", "chest", "trapped_chest", "ender_chest", "furnace", "lit_furnace",
        "jukebox", "enchanting_table", "dropper", "dispenser", "hopper", "anvil",
        "noteblock", "crafting_table", "mob_spawner", "brewing_stand", "bed"
};
final String[] UNPLACEABLE_CONTAINS = {
        "stairs", "slab", "fence", "pane", "rail", "door",
        "torch", "pumpkin", "flower", "sapling", "banner", "button",
        "skull", "web", "carpet", "cactus", "sign", "mushroom"
};
final String[] INTERACTABLE_TYPES = {
        "BlockTrapDoor", "BlockDoor", "BlockContainer", "BlockJukebox", "BlockFenceGate",
        "BlockChest", "BlockEnderChest", "BlockEnchantmentTable", "BlockBrewingStand",
        "BlockBed", "BlockDropper", "BlockDispenser", "BlockHopper", "BlockAnvil",
        "BlockNote", "BlockWorkbench", "BlockFurnace", "BlockBeacon", "BlockMobSpawner",
        "BlockDaylightDetector", "BlockCommandBlock", "BlockStandingSign", "BlockWallSign", "BlockSkull"
};
int currentClientTick = -2147483648;
int placementEvaluationTick = -2147483648;
int lastPlacementAttemptTick = -2147483648;
int lastSuccessfulPlaceTick = -2147483648;
int forceSuppressTick = -2147483648;
long totalC08Counter = 0L;
long c08CounterAtTickBoundary = 0L;
boolean hasLastSentServerPos = false;
        double lastSentServerPosX, lastSentServerPosY, lastSentServerPosZ;
Object[] cachedCandidate = null;
int cachedCandidateTick = -2147483648;
float cachedCandidateYaw = Float.NaN;
float cachedCandidatePitch = Float.NaN;
boolean candidateResolvedThisTick = false;
int[] lastPlacedPos = null;
int[] lastSupportPos = null;
int lastSupportFace = -1;
List<int[]> cachedBelowTargets = null;
int cachedBelowTargetsTick = -2147483648;
Map<String, Integer> rejectedTargets = new HashMap<>();
boolean useSuppressed = false;
boolean silentPitchActive = false;
float silentPitch = 0f;
boolean placingViaModule = false;
boolean manualC08InWindow = false;

void autoPlaceOnEnable() {
    keybinds.setPressed("attack", false);
    resetControllerState();
}

void autoPlaceOnDisable() {
    resetControllerState();
    restoreUseToPhysicalState();
    keybinds.setPressed("attack", false);
    bridge.remove("AutoPlacePlacing");
    releaseExperimentalPlacementClaim();
}

void resetControllerState() {
    currentClientTick = -2147483648;
    placementEvaluationTick = -2147483648;
    lastPlacementAttemptTick = -2147483648;
    lastSuccessfulPlaceTick = -2147483648;
    forceSuppressTick = -2147483648;
    totalC08Counter = 0L;
    c08CounterAtTickBoundary = 0L;
    hasLastSentServerPos = false;
    clearCachedCandidate();
    lastPlacedPos = null;
    lastSupportPos = null;
    lastSupportFace = -1;
    cachedBelowTargets = null;
    cachedBelowTargetsTick = -2147483648;
    rejectedTargets.clear();
    useSuppressed = false;
    silentPitchActive = false;
    placingViaModule = false;
    manualC08InWindow = false;
}

void autoPlaceOnWorldJoin(Entity entity) {
    if (entity != null && entity.isUser) {
        resetControllerState();
    }
}

void autoPlaceOnPreUpdate() {
    Entity player = client.getPlayer();
    if (player == null) return;

    syncPlacementTick(player);

    if (placementEvaluationTick != currentClientTick) {
        placementEvaluationTick = currentClientTick;
        processAutoPlaceTick(player);
    }
}

void syncPlacementTick(Entity player) {
    int tick = placementTick(player);
    if (tick == currentClientTick) return;
    currentClientTick = tick;
    candidateResolvedThisTick = false;
    silentPitchActive = false;
    updateStandingFloorY(player);
}

void updateStandingFloorY(Entity player) {
    if (player == null || !player.onGround()) return;
    tellyFloorY = floor(player.getPosition().y) - 1;
}

boolean useExtendedSearch() {
    return true;
}

void autoPlaceOnPostMotion() {
    c08CounterAtTickBoundary = totalC08Counter;
    manualC08InWindow = false;
}

void autoPlaceOnRenderTick(float partialTicks) {
    Entity player = client.getPlayer();
    if (player == null) return;
    if (!isAutoPlaceActiveWindow(player)) return;

    ItemStack heldStack = player.getHeldItem();
    if (!isUsableBlockStack(heldStack)) return;

    float basePitch = sanitizePitch(player.getPitch(), player.getPitch());
    Object[] candidate = resolveCandidateWithOffCursorSilentPitch(player, player.getYaw(), basePitch, heldStack);
    if (candidate != null) {
        silentPitch = sanitizePitch(candidatePitch(candidate), basePitch);
        silentPitchActive = true;
        suppressUse();
    }
}

void autoPlaceOnPreMotion(PlayerState state) {
    if (silentPitchActive && !manualC08InWindow) {
        if (modules.getButton(scriptName, "Silent Aim")) {
            state.pitch = sanitizePitch(silentPitch, state.pitch);
        }
    }
}

boolean autoPlaceOnPacketSent(CPacket packet) {
    if (packet instanceof C03) {
        C03 c03 = (C03) packet;
        if (c03.position != null) {
            hasLastSentServerPos = true;
            lastSentServerPosX = c03.position.x;
            lastSentServerPosY = c03.position.y;
            lastSentServerPosZ = c03.position.z;
        }
        return true;
    }
    if (packet instanceof C08) {
        C08 c08 = (C08) packet;
        if (c08.direction == 255) {
            if (shouldCancelAutoPlaceUseItem()) {
                suppressUse();
                return false;
            }
        } else {
            if (c08.itemStack != null && c08.itemStack.isBlock) {
                totalC08Counter++;
                if (!placingViaModule) manualC08InWindow = true;
            }
        }
    }
    return true;
}

boolean autoPlaceOnMouse(int button, boolean state) {
    if (!state || (button != 0 && button != 1)) return true;

    if (button == 1 && shouldCancelAutoPlaceUseItem()) {
        suppressUse();
        return false;
    }
    if (!shouldSuppressManualClicksThisTick()) return true;
    keybinds.setPressed("attack", false);
    return false;
}

boolean shouldSuppressManualClicksThisTick() {
    if (!isInGameContext()) return false;
    return lastSuccessfulPlaceTick == currentClientTick || forceSuppressTick == currentClientTick;
}

boolean shouldCancelAutoPlaceUseItem() {
    if (!isInGameContext()) return false;
    if (shouldSuppressManualClicksThisTick()) return true;
    return useSuppressed && silentPitchActive;
}

void suppressUse() {
    keybinds.setPressed("use", false);
    useSuppressed = true;
}

void restoreUseToPhysicalState() {
    keybinds.setPressed("use", running
            ? tellyAutoPlaceWindow
            : keybinds.isMouseDown(1));
    useSuppressed = false;
}

boolean isInGameContext() {
    return client.getPlayer() != null && client.getScreen().isEmpty();
}

boolean areAutoPlaceConditionsMet(Entity player) {
    if (!tellyAutoPlaceWindow) return false;
    return isUsableBlockStack(player.getHeldItem());
}

boolean isAutoPlaceActiveWindow(Entity player) {
    if (!isInGameContext()) return false;
    if (bridge.has("ScaffoldRunning")) return false;
    if (!areAutoPlaceConditionsMet(player)) return false;
    return isUsableBlockStack(player.getHeldItem());
}

boolean isUsableBlockStack(ItemStack stack) {
    if (stack == null || !stack.isBlock || stack.name == null || stack.stackSize <= 0) return false;
    String name = stack.name.toLowerCase();
    for (String bad : UNPLACEABLE_EXACT) {
        if (name.equals(bad)) return false;
    }
    for (String bad : UNPLACEABLE_CONTAINS) {
        if (name.contains(bad)) return false;
    }
    return true;
}

boolean isBlockBelowPlayerReplaceable(Entity player) {
    Vec3 pos = player.getPosition();
    return isReplaceable(floor(pos.x), floor(pos.y) - 1, floor(pos.z));
}

boolean placedInCurrentWindow() {
    return totalC08Counter > c08CounterAtTickBoundary;
}

boolean claimExperimentalPlacementTick() {
    Object tickValue = bridge.get("PlacementArbiterTick");
    Object ownerValue = bridge.get("PlacementArbiterOwner");
    if (tickValue instanceof Number
            && ((Number) tickValue).intValue() == currentClientTick
            && ownerValue != null
            && !scriptName.equals(String.valueOf(ownerValue))) {
        return false;
    }
    bridge.add("PlacementArbiterTick", currentClientTick);
    bridge.add("PlacementArbiterOwner", scriptName);
    return true;
}

void releaseExperimentalPlacementClaim() {
    Object ownerValue = bridge.get("PlacementArbiterOwner");
    if (ownerValue == null || !scriptName.equals(String.valueOf(ownerValue))) return;
    bridge.remove("PlacementArbiterTick");
    bridge.remove("PlacementArbiterOwner");
}

void processAutoPlaceTick(Entity player) {
    pruneRejectedTargets();

    if (lastPlacedPos != null && !isSupportAvailable(lastPlacedPos[0], lastPlacedPos[1], lastPlacedPos[2])) {
        lastPlacedPos = null;
        lastSupportPos = null;
        lastSupportFace = -1;
    }

    if (!isAutoPlaceActiveWindow(player)) {
        clearCachedCandidate();
        bridge.remove("AutoPlacePlacing");
        if (useSuppressed) restoreUseToPhysicalState();
        return;
    }

    ItemStack heldStack = player.getHeldItem();
    if (!isUsableBlockStack(heldStack)) {
        clearCachedCandidate();
        if (useSuppressed) restoreUseToPhysicalState();
        return;
    }

    if (!isBlockBelowPlayerReplaceable(player)) {
        clearCachedCandidate();
        if (useSuppressed) restoreUseToPhysicalState();
        return;
    }

    float yaw = player.getYaw();
    float basePitch = sanitizePitch(player.getPitch(), player.getPitch());
    Object[] candidate = resolveCandidateWithOffCursorSilentPitch(player, yaw, basePitch, heldStack);
    if (candidate != null) {
        silentPitch = sanitizePitch(candidatePitch(candidate), basePitch);
        silentPitchActive = true;
        suppressUse();
    } else if (useSuppressed && !placedInCurrentWindow() && lastPlacementAttemptTick != currentClientTick) {
        restoreUseToPhysicalState();
    }

    if (placedInCurrentWindow() || lastPlacementAttemptTick == currentClientTick) {
        suppressUse();
        return;
    }

    if (candidate == null) {
        clearCachedCandidate();
        return;
    }

    if (!claimExperimentalPlacementTick()) {
        clearCachedCandidate();
        return;
    }

    bridge.add("AutoPlacePlacing");
    lastPlacementAttemptTick = currentClientTick;

    if (attemptPlacement(player, candidate, heldStack)) return;

    if (placedInCurrentWindow()) return;

    float retryYaw = player.getYaw();
    float retryPitch = player.getPitch();
    clearCachedCandidate();
    Object[] retryCandidate = findBelowPlacement(player, retryYaw, retryPitch, heldStack, client.time() + (useExtendedSearch() ? 4L : 2L));
    cacheCandidate(retryCandidate, retryYaw, retryPitch);
    if (retryCandidate != null) {
        silentPitch = sanitizePitch(candidatePitch(retryCandidate), retryPitch);
        silentPitchActive = true;
        if (attemptPlacement(player, retryCandidate, heldStack)) return;
    }
    releaseExperimentalPlacementClaim();
}

boolean attemptPlacement(Entity player, Object[] candidate, ItemStack heldStack) {
    if (candidate == null) return false;
    int[] placedPos = candidatePlacedPos(candidate);
    int[] supportPos = candidateSupportPos(candidate);
    int face = candidateFace(candidate);
    if (placedPos == null || supportPos == null || face <= 0) return false;
    if (!isBlockBelowPlayerReplaceable(player)) return false;
    if (!isUsableBlockStack(player.getHeldItem())) return false;
    if (placedInCurrentWindow()) return false;

    float placementPitch = sanitizePitch(candidatePitch(candidate), player.getPitch());
    Object[] prePlaceHit = resolveVerifiedHit(player.getYaw(), placementPitch, supportPos, face, placedPos);
    if (prePlaceHit == null) return false;

    if (cancelledGhostBlocks.contains(posKey(supportPos))) return false;
    if (!isReplaceable(placedPos[0], placedPos[1], placedPos[2])) return false;
    if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return false;
    if (doesPlacementIntersectPlayer(player, placedPos)) return false;

    long counterBefore = totalC08Counter;
    Vec3 hitAbs = (Vec3) prePlaceHit[2];
    placingViaModule = true;
    boolean placed = client.placeBlock(new Vec3(supportPos[0], supportPos[1], supportPos[2]), faceName(face), hitAbs);
    placingViaModule = false;
    boolean packetSent = totalC08Counter > counterBefore;

    if (!placed && !packetSent) return false;
    if (!packetSent) {
        markRejectedTarget(placedPos);
        return false;
    }

    lastPlacedPos = placedPos;
    lastSupportPos = supportPos;
    recordLanePlacement(player, placedPos);
    lastSupportFace = face;
    lastSuccessfulPlaceTick = currentClientTick;
    forceSuppressTick = currentClientTick;
    client.swing();
    return true;
}

Object[] resolveVerifiedHit(float yaw, float pitch, int[] expectedSupport, int expectedFace, int[] expectedPlaced) {
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return null;
    int[] tracedSupport = (int[]) traced[0];
    int tracedFace = (Integer) traced[1];
    if (!posEquals(tracedSupport, expectedSupport) || tracedFace != expectedFace) return null;
    int[] tracedPlaced = offsetPos(tracedSupport, tracedFace);
    if (!posEquals(tracedPlaced, expectedPlaced)) return null;
    return traced;
}

Object[] resolveCandidateWithOffCursorSilentPitch(Entity player, float yaw, float basePitch, ItemStack heldStack) {
    float safeBasePitch = sanitizePitch(basePitch, player.getPitch());
    Object[] previousCandidate = cachedCandidate;
    Object[] baseCandidate = resolveCandidateForCurrentTick(player, yaw, safeBasePitch, heldStack);
    if (baseCandidate == null) {
        if (previousCandidate != null) {
            float previousBlockPitch = getBlockDerivedSilentPitch(player, previousCandidate, safeBasePitch);
            Object[] recovered = resolveCandidateForCurrentTick(player, yaw, previousBlockPitch, heldStack);
            if (recovered != null) return recovered;
            cacheCandidate(previousCandidate, yaw, safeBasePitch);
            return previousCandidate;
        }
        return null;
    }
    if (isPlacementLookAligned(yaw, safeBasePitch, candidateSupportPos(baseCandidate), candidateFace(baseCandidate), candidatePlacedPos(baseCandidate))) {
        return baseCandidate;
    }
    float blockPitch = getBlockDerivedSilentPitch(player, baseCandidate, safeBasePitch);
    if (isPlacementLookAligned(yaw, blockPitch, candidateSupportPos(baseCandidate), candidateFace(baseCandidate), candidatePlacedPos(baseCandidate))) {
        return new Object[]{blockPitch, candidateSupportPos(baseCandidate), candidateFace(baseCandidate), candidateHitVec(baseCandidate), candidatePlacedPos(baseCandidate)};
    }
    Object[] corrected = resolveCandidateForCurrentTick(player, yaw, blockPitch, heldStack);
    if (corrected != null && posEquals(candidatePlacedPos(baseCandidate), candidatePlacedPos(corrected))) {
        return corrected;
    }
    cacheCandidate(baseCandidate, yaw, safeBasePitch);
    return baseCandidate;
}

Object[] resolveCandidateForCurrentTick(Entity player, float yaw, float pitch, ItemStack heldStack) {
    float safePitch = sanitizePitch(pitch, player.getPitch());
    if (hasCachedCandidateForCurrentTick(yaw, safePitch)) return cachedCandidate;
    Object[] candidate = findBelowPlacement(player, yaw, safePitch, heldStack, client.time() + (useExtendedSearch() ? 8L : 4L));
    cacheCandidate(candidate, yaw, safePitch);
    return candidate;
}

float getBlockDerivedSilentPitch(Entity player, Object[] candidate, float fallbackPitch) {
    if (candidate == null) return sanitizePitch(fallbackPitch, fallbackPitch);
    Vec3 hitVec = candidateHitVec(candidate);
    if (hitVec != null) {
        Float derived = computePitchToHitVec(player, hitVec);
        if (derived != null) return sanitizePitch(derived, fallbackPitch);
    }
    return sanitizePitch(candidatePitch(candidate), fallbackPitch);
}

void cacheCandidate(Object[] candidate, float yaw, float pitch) {
    cachedCandidate = candidate;
    cachedCandidateTick = currentClientTick;
    cachedCandidateYaw = yaw;
    cachedCandidatePitch = pitch;
    candidateResolvedThisTick = candidate != null;
}

boolean hasCachedCandidateForCurrentTick(float yaw, float pitch) {
    if (cachedCandidateTick != currentClientTick || !candidateResolvedThisTick || cachedCandidate == null) return false;
    if (Float.isNaN(cachedCandidateYaw) || Float.isNaN(cachedCandidatePitch)) return false;
    return Math.abs(wrapAngle(yaw - cachedCandidateYaw)) <= 0.75f && Math.abs(pitch - cachedCandidatePitch) <= 0.75f;
}

void clearCachedCandidate() {
    cachedCandidate = null;
    cachedCandidateTick = -2147483648;
    cachedCandidateYaw = Float.NaN;
    cachedCandidatePitch = Float.NaN;
    candidateResolvedThisTick = false;
}

Object[] findBelowPlacement(Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
    if (client.time() >= deadlineMs) return null;

    Object[] cursorRayCandidate = findDirectCursorRayPlacement(player, yaw, currentPitch, heldStack);
    if (cursorRayCandidate != null) return cursorRayCandidate;

    int currentY = getCurrentBelowTargetY(player);
    int strictY = getStrictBelowTargetY(player);
    int previousY = getPreviousBelowTargetY(player);
    int[] feetPos = getFeetBelowTargetAtY(player, currentY);

    List<int[]> targets = new ArrayList<>();
    addBelowTarget(player, targets, feetPos);
    addBelowTarget(player, targets, offsetPos(feetPos, facingFromYaw(yaw)));

    for (int dy = 0; dy <= 2; dy++) {
        int targetY = dy == 0 ? currentY : dy == 1 ? strictY : previousY;
        if (targetY == -2147483648 || (dy == 1 && targetY == currentY) || (dy == 2 && (targetY == currentY || targetY == strictY))) continue;
        addBelowTarget(player, targets, new int[]{feetPos[0], targetY, feetPos[2]});
    }

    for (int dx = -1; dx <= 1; dx++) {
        for (int dz = -1; dz <= 1; dz++) {
            addBelowTarget(player, targets, new int[]{feetPos[0] + dx, currentY, feetPos[2] + dz});
            if (strictY != currentY) addBelowTarget(player, targets, new int[]{feetPos[0] + dx, strictY, feetPos[2] + dz});
            if (previousY != currentY && previousY != strictY) addBelowTarget(player, targets, new int[]{feetPos[0] + dx, previousY, feetPos[2] + dz});
        }
    }

    if (!player.onGround()) {
        addBelowTarget(player, targets, getMotionBelowTargetAtY(player, currentY, 1.0));
        if (previousY != currentY && previousY != strictY) {
            addBelowTarget(player, targets, getMotionBelowTargetAtY(player, previousY, 1.0));
        }
    }

    Object[] bestCandidate = null;
    double bestScore = Double.POSITIVE_INFINITY;
    for (int[] targetPos : targets) {
        if (client.time() >= deadlineMs) return null;
        if (!isPlacementTargetAvailable(player, targetPos)) continue;
        Object[] candidate = findPitchPlacementForTarget(player, yaw, currentPitch, targetPos, heldStack, null, -1, deadlineMs, false, true);
        if (candidate == null) continue;
        double score = scorePlacementCandidate(currentPitch, candidatePitch(candidate), candidateFace(candidate), 0.5, 0.5);
        if (score < bestScore) {
            bestScore = score;
            bestCandidate = candidate;
        }
    }
    return bestCandidate;
}

Object[] findDirectCursorRayPlacement(Entity player, float yaw, float pitch, ItemStack heldStack) {
    if (!isUsableBlockStack(heldStack)) return null;
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return null;
    int[] supportPos = (int[]) traced[0];
    int face = (Integer) traced[1];
    if (face == 0) return null;
    int[] targetPos = offsetPos(supportPos, face);
    if (!isPlacementTargetAvailable(player, targetPos)) return null;
    if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return null;
    float tracedPitch = clampFloat(pitch, -89.0f, 89.0f);
    return new Object[]{tracedPitch, supportPos, face, (Vec3) traced[2], targetPos};
}

List<int[]> getBelowPlayerFallbackEndpoints(Entity player, float yaw, float pitch, int targetY) {
    List<int[]> endpoints = new ArrayList<>();
    if (!isDiagonalMovementContext(player)) {
        if (!player.onGround()) {
            addBelowTargetIfUnique(player, endpoints, getFeetBelowTargetAtY(player, targetY));
            addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.0));
            addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.7));
        }
        addBelowTargetIfUnique(player, endpoints, getCursorStartTargetAtY(player, yaw, pitch, targetY));
        addBelowTargetIfUnique(player, endpoints, getCursorPlacedTargetFromRay(yaw, pitch, targetY));
        addBelowTargetIfUnique(player, endpoints, getCursorTargetAtY(player, yaw, pitch, targetY));
        return endpoints;
    }
    addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.0));
    addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.7));
    return endpoints;
}

boolean hasValidLastPlacedPos(Entity player) {
    if (lastPlacedPos == null) return false;
    return isWithinReach(player, lastPlacedPos) && isSupportAvailable(lastPlacedPos[0], lastPlacedPos[1], lastPlacedPos[2]) && !isInteractable(lastPlacedPos[0], lastPlacedPos[1], lastPlacedPos[2]);
}

boolean hasValidLastSupportFace(Entity player) {
    if (lastSupportPos == null || lastSupportFace < 0) return false;
    return isWithinReach(player, lastSupportPos) && isSupportAvailable(lastSupportPos[0], lastSupportPos[1], lastSupportPos[2]) && !isInteractable(lastSupportPos[0], lastSupportPos[1], lastSupportPos[2]);
}

boolean isWithinReach(Entity player, int[] pos) {
    if (pos == null) return false;
    Vec3 eyes = getEyes(player);
    double cx = Math.max(pos[0], Math.min(eyes.x, pos[0] + 1.0));
    double cy = Math.max(pos[1], Math.min(eyes.y, pos[1] + 1.0));
    double cz = Math.max(pos[2], Math.min(eyes.z, pos[2] + 1.0));
    double dx = eyes.x - cx;
    double dy = eyes.y - cy;
    double dz = eyes.z - cz;
    return dx * dx + dy * dy + dz * dz <= reach() * reach();
}

Object[] findPitchPlacementForTarget(Entity player, float yaw, float currentPitch, int[] targetPos, ItemStack heldStack, int[] preferredSupportPos, int preferredSupportFace, long deadlineMs, boolean requireLookAlignment, boolean allowNonCursorTarget) {
    if (client.time() >= deadlineMs || targetPos == null) return null;
    boolean effectiveAllowNonCursorTarget = allowNonCursorTarget || shouldAllowPlayerOneNonCursorTarget(player, targetPos);
    if (!effectiveAllowNonCursorTarget && !isCursorOrBelowPlayerTarget(player, targetPos, yaw, currentPitch)) return null;
    if (!isPlacementTargetAvailable(player, targetPos)) return null;

    Object[] bestCandidate = null;
    double bestScore = Double.POSITIVE_INFINITY;
    for (int placeFace : ALLOWED_PLACE_FACES) {
        if (client.time() >= deadlineMs) break;
        int[] supportPos = offsetPos(targetPos, opposite(placeFace));
        if (preferredSupportPos != null && !posEquals(supportPos, preferredSupportPos)) continue;
        if (preferredSupportFace >= 0 && placeFace != preferredSupportFace) continue;
        if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) continue;
        if (!isWithinReach(player, supportPos)) continue;

        double[] hitOffsets = useExtendedSearch() ? EXTENDED_FACE_HIT_OFFSETS : FACE_HIT_OFFSETS;
        for (double primaryOffset : hitOffsets) {
            for (double secondaryOffset : hitOffsets) {
                if (client.time() >= deadlineMs) break;
                Vec3 hitVec = getSupportFaceHitVec(supportPos, placeFace, primaryOffset, secondaryOffset);
                Object[] candidate = buildPlacementCandidateForHitVec(player, yaw, targetPos, supportPos, placeFace, hitVec, requireLookAlignment, effectiveAllowNonCursorTarget);
                if (candidate == null) continue;
                double candidateScore = scorePlacementCandidate(currentPitch, candidatePitch(candidate), placeFace, primaryOffset, secondaryOffset);
                if (candidateScore < bestScore) {
                    bestScore = candidateScore;
                    bestCandidate = candidate;
                }
            }
        }
    }
    if (bestCandidate == null && preferredSupportPos != null && preferredSupportFace >= 0) {
        return findRayAlignedPitchCandidate(yaw, currentPitch, targetPos, preferredSupportPos, preferredSupportFace, deadlineMs);
    }
    return bestCandidate;
}

Object[] findRayAlignedPitchCandidate(float yaw, float currentPitch, int[] targetPos, int[] supportPos, int placeFace, long deadlineMs) {
    float clampedBasePitch = clampFloat(currentPitch, 40.0f, 89.0f);
    for (int offset = 0; offset <= 49; offset++) {
        if (client.time() >= deadlineMs) return null;
        float upPitch = clampedBasePitch + offset;
        if (upPitch <= 89.0f) {
            Object[] candidate = tryRayAlignedPitch(yaw, upPitch, targetPos, supportPos, placeFace);
            if (candidate != null) return candidate;
        }
        if (offset == 0) continue;
        float downPitch = clampedBasePitch - offset;
        if (downPitch >= 40.0f) {
            Object[] candidate = tryRayAlignedPitch(yaw, downPitch, targetPos, supportPos, placeFace);
            if (candidate != null) return candidate;
        }
    }
    return null;
}

Object[] tryRayAlignedPitch(float yaw, float pitch, int[] targetPos, int[] supportPos, int placeFace) {
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return null;
    int[] tracedSupport = (int[]) traced[0];
    int tracedFace = (Integer) traced[1];
    if (!posEquals(tracedSupport, supportPos) || tracedFace != placeFace) return null;
    int[] tracedPlaced = offsetPos(tracedSupport, tracedFace);
    if (!posEquals(tracedPlaced, targetPos)) return null;
    return new Object[]{pitch, tracedSupport, tracedFace, (Vec3) traced[2], tracedPlaced};
}

double scorePlacementCandidate(float currentPitch, float candidatePitchValue, int placeFace, double primaryOffset, double secondaryOffset) {
    double pitchPenalty = Math.abs(wrapAngle(candidatePitchValue - currentPitch));
    double centerPenalty = Math.abs(primaryOffset - 0.5) + Math.abs(secondaryOffset - 0.5);
    double facePenalty = placeFace == 1 ? 0.0 : 0.35;
    return pitchPenalty + centerPenalty * 2.0 + facePenalty;
}

Object[] buildPlacementCandidateForHitVec(Entity player, float yaw, int[] targetPos, int[] supportPos, int placeFace, Vec3 hitVec, boolean requireLookAlignment, boolean allowNonCursorTarget) {
    if (hitVec == null) return null;
    int[] offsetTarget = offsetPos(supportPos, placeFace);
    if (!posEquals(offsetTarget, targetPos)) return null;
    if (!isStrictOneBelowPlayer(player, offsetTarget)) return null;
    Float pitch = computePitchToHitVec(player, hitVec);
    if (pitch == null) return null;
    if (!isPlacementLookAligned(yaw, pitch, supportPos, placeFace, targetPos)) return null;
    if (!(allowNonCursorTarget || isDiagonalMovementContext(player) || isSupportFaceVisible(player, supportPos, placeFace, hitVec))) return null;
    return new Object[]{pitch, supportPos, placeFace, hitVec, offsetTarget};
}

boolean isPlacementLookAligned(float yaw, float pitch, int[] supportPos, int placeFace, int[] targetPos) {
    if (supportPos == null || placeFace < 0 || targetPos == null) return false;
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return false;
    if (!posEquals((int[]) traced[0], supportPos) || (Integer) traced[1] != placeFace) return false;
    int[] tracedOffset = offsetPos((int[]) traced[0], (Integer) traced[1]);
    return posEquals(tracedOffset, targetPos);
}

boolean isSupportFaceVisible(Entity player, int[] supportPos, int placeFace, Vec3 hitVec) {
    Vec3 eyes = getEyes(player);
    double dx = hitVec.x - eyes.x;
    double dy = hitVec.y - eyes.y;
    double dz = hitVec.z - eyes.z;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (distance < 1.0E-4) return false;
    float traceYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    float tracePitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    Object[] traced = client.raycastBlock(distance + 0.5, traceYaw, tracePitch);
    if (traced == null) return false;
    int[] tracedPos = posFromVec((Vec3) traced[0]);
    int tracedFace = faceFromName((String) traced[2]);
    return posEquals(tracedPos, supportPos) && tracedFace == placeFace;
}

Vec3 getSupportFaceHitVec(int[] supportPos, int placeFace, double primaryOffset, double secondaryOffset) {
    double primary = Math.max(0.001, Math.min(0.999, primaryOffset));
    double secondary = Math.max(0.001, Math.min(0.999, secondaryOffset));
    if (placeFace == 2) return new Vec3(supportPos[0] + primary, supportPos[1] + secondary, supportPos[2] + 0.001);
    if (placeFace == 3) return new Vec3(supportPos[0] + primary, supportPos[1] + secondary, supportPos[2] + 0.999);
    if (placeFace == 5) return new Vec3(supportPos[0] + 0.999, supportPos[1] + primary, supportPos[2] + secondary);
    if (placeFace == 4) return new Vec3(supportPos[0] + 0.001, supportPos[1] + primary, supportPos[2] + secondary);
    if (placeFace == 0) return new Vec3(supportPos[0] + primary, supportPos[1] + 0.001, supportPos[2] + secondary);
    return new Vec3(supportPos[0] + primary, supportPos[1] + 0.999, supportPos[2] + secondary);
}

Float computePitchToHitVec(Entity player, Vec3 hitVec) {
    Vec3 eyes = getEyes(player);
    double dx = hitVec.x - eyes.x;
    double dz = hitVec.z - eyes.z;
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    double dy = hitVec.y - eyes.y;
    float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
    return Math.max(-89.0f, Math.min(89.0f, pitch));
}

List<int[]> getBelowTargets(Entity player, float yaw, float pitch) {
    if (cachedBelowTargetsTick == currentClientTick && cachedBelowTargets != null) return cachedBelowTargets;
    List<int[]> belowTargets = new ArrayList<>();
    boolean diagonal = isDiagonalMovementContext(player);
    if (!diagonal) {
        int currentY = getCurrentBelowTargetY(player);
        addBelowTarget(player, belowTargets, getCursorStartTargetAtY(player, yaw, pitch, currentY));
        if (belowTargets.isEmpty()) {
            int strictY = getStrictBelowTargetY(player);
            if (strictY != currentY) addBelowTarget(player, belowTargets, getCursorStartTargetAtY(player, yaw, pitch, strictY));
        }
        if (belowTargets.isEmpty()) addBelowTarget(player, belowTargets, getCursorPlacedTargetFromRay(yaw, pitch, currentY));
        if (belowTargets.isEmpty()) {
            int strictY = getStrictBelowTargetY(player);
            if (strictY != currentY) addBelowTarget(player, belowTargets, getCursorPlacedTargetFromRay(yaw, pitch, strictY));
        }
        if (belowTargets.isEmpty()) addBelowTarget(player, belowTargets, getCursorTargetAtY(player, yaw, pitch, currentY));
    } else {
        int currentY = getCurrentBelowTargetY(player);
        addBelowTarget(player, belowTargets, getMotionBelowTargetAtY(player, currentY, 1.0));
        addBelowTarget(player, belowTargets, getMotionBelowTargetAtY(player, currentY, 1.7));
        for (int[] endpoint : getBelowPlayerFallbackEndpoints(player, yaw, pitch, currentY)) {
            addBelowTarget(player, belowTargets, endpoint);
        }
    }
    cachedBelowTargets = belowTargets;
    cachedBelowTargetsTick = currentClientTick;
    return belowTargets;
}

boolean isCursorOrBelowPlayerTarget(Entity player, int[] targetPos, float yaw, float pitch) {
    if (targetPos == null) return false;
    if (!isDiagonalMovementContext(player)) {
        int currentY = getCurrentBelowTargetY(player);
        if (posEquals(getCursorStartTargetAtY(player, yaw, pitch, currentY), targetPos)) return true;
        if (posEquals(getCursorPlacedTargetFromRay(yaw, pitch, currentY), targetPos)) return true;
        int strictY = getStrictBelowTargetY(player);
        if (strictY != currentY) {
            if (posEquals(getCursorStartTargetAtY(player, yaw, pitch, strictY), targetPos)) return true;
            if (posEquals(getCursorPlacedTargetFromRay(yaw, pitch, strictY), targetPos)) return true;
        }
        if (isCursorInsideTargetAtY(player, targetPos, yaw, pitch, currentY)) return true;
        return posEquals(getCursorTargetAtY(player, yaw, pitch, currentY), targetPos);
    }
    int strictY = getStrictBelowTargetY(player);
    if (isBelowPlayerTargetAtY(player, targetPos, strictY, yaw, pitch)) return true;
    return isBelowPlayerTargetAtY(player, targetPos, getCurrentBelowTargetY(player), yaw, pitch);
}

boolean isBelowPlayerTargetAtY(Entity player, int[] targetPos, int targetY, float yaw, float pitch) {
    for (int[] candidate : getBelowPlayerFallbackEndpoints(player, yaw, pitch, targetY)) {
        if (posEquals(targetPos, candidate)) return true;
    }
    return false;
}

int[] getFeetBelowTargetAtY(Entity player, int targetY) {
    Vec3 pos = player.getPosition();
    return new int[]{floor(pos.x), targetY, floor(pos.z)};
}

boolean shouldAllowPlayerOneNonCursorTarget(Entity player, int[] targetPos) {
    if (targetPos == null) return false;
    if (isDiagonalMovementContext(player) || player.onGround()) return false;
    if (!isPlayerBoundsFullyInsideSingleBlockColumn(player)) return false;
    if (!hasValidLastSupportFace(player) || lastSupportFace == 0) return false;
    int[] continuationTarget = offsetPos(lastSupportPos, lastSupportFace);
    if (!posEquals(targetPos, continuationTarget)) return false;
    int targetY = targetPos[1];
    int currentY = getCurrentBelowTargetY(player);
    int strictY = getStrictBelowTargetY(player);
    if (targetY != currentY && targetY != strictY) return false;
    int[] feetBelow = getFeetBelowTargetAtY(player, targetY);
    int horizontalDistance = Math.abs(targetPos[0] - feetBelow[0]) + Math.abs(targetPos[2] - feetBelow[2]);
    return horizontalDistance <= 1;
}

boolean isPlayerBoundsFullyInsideSingleBlockColumn(Entity player) {
    Vec3 pos = player.getPosition();
    double half = player.getWidth() / 2.0;
    int minX = floor(pos.x - half + 1.0E-4);
    int maxX = floor(pos.x + half - 1.0E-4);
    if (minX != maxX) return false;
    int minZ = floor(pos.z - half + 1.0E-4);
    int maxZ = floor(pos.z + half - 1.0E-4);
    return minZ == maxZ;
}

int[] getMotionBelowTargetAtY(Entity player, int targetY, double multiplier) {
    Vec3 pos = player.getPosition();
    Vec3 motion = client.getMotion();
    return new int[]{floor(pos.x + motion.x * multiplier), targetY, floor(pos.z + motion.z * multiplier)};
}

boolean hasDirectSupportNeighbor(int[] targetPos) {
    for (int placeFace : ALLOWED_PLACE_FACES) {
        int[] supportPos = offsetPos(targetPos, opposite(placeFace));
        if (isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return true;
    }
    return false;
}

void addBelowTargetIfUnique(Entity player, List<int[]> targets, int[] candidate) {
    if (candidate == null) return;
    if (!isStrictOneBelowPlayer(player, candidate)) return;
    for (int[] existing : targets) {
        if (posEquals(existing, candidate)) return;
    }
    targets.add(candidate);
}

void addBelowTarget(Entity player, List<int[]> targets, int[] candidate) {
    addBelowTargetIfUnique(player, targets, candidate);
}

boolean isDiagonalMovementContext(Entity player) {
    return running;
}

int[] getCursorPlacedTargetFromRay(float yaw, float pitch, int targetY) {
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return null;
    int[] offsetTarget = offsetPos((int[]) traced[0], (Integer) traced[1]);
    if (offsetTarget[1] != targetY) return null;
    return offsetTarget;
}

int[] getCursorStartTargetAtY(Entity player, float fallbackYaw, float fallbackPitch, int targetY) {
    Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
    Vec3 lookVec = getCursorLookVec(player);
    if (cursorPoint == null || lookVec == null) return null;
    double startX = cursorPoint.x - lookVec.x * 0.03;
    double startZ = cursorPoint.z - lookVec.z * 0.03;
    return new int[]{floor(startX), targetY, floor(startZ)};
}

int[] getCursorTargetAtY(Entity player, float fallbackYaw, float fallbackPitch, int targetY) {
    Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
    if (cursorPoint == null) return null;
    return new int[]{floor(cursorPoint.x), targetY, floor(cursorPoint.z)};
}

Vec3 getCursorIntersectionAtY(Entity player, int targetY) {
    Vec3 eyes = getEyes(player);
    Vec3 lookVec = getCursorLookVec(player);
    if (lookVec == null || Math.abs(lookVec.y) < 1.0E-4) return null;
    double t = (targetY - eyes.y) / lookVec.y;
    if (t <= 0.0) return null;
    return new Vec3(eyes.x + lookVec.x * t, targetY + 0.5, eyes.z + lookVec.z * t);
}

Vec3 getCursorLookVec(Entity player) {
    double[] cameraRotations = render.getRotations();
    if (cameraRotations != null && cameraRotations.length >= 2) {
        return getLookVec((float) cameraRotations[0], (float) cameraRotations[1]);
    }
    return getLookVec(player.getYaw(), player.getPitch());
}

Vec3 getLookVec(float yaw, float pitch) {
    double yawRad = Math.toRadians(yaw);
    double pitchRad = Math.toRadians(pitch);
    double cosPitch = Math.cos(pitchRad);
    return new Vec3(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
}

boolean isCursorInsideTargetAtY(Entity player, int[] targetPos, float yaw, float pitch, int targetY) {
    if (targetPos == null || targetPos[1] != targetY) return false;
    Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
    if (cursorPoint == null) return false;
    double x = cursorPoint.x;
    double z = cursorPoint.z;
    return x >= targetPos[0] - 1.0E-6 && x <= targetPos[0] + 1.0 + 1.0E-6 && z >= targetPos[2] - 1.0E-6 && z <= targetPos[2] + 1.0 + 1.0E-6;
}

boolean isPlacementTargetAvailable(Entity player, int[] pos) {
    return isBasePlacementTargetAvailable(player, pos) && isStrictOneBelowPlayer(player, pos);
}

boolean isBasePlacementTargetAvailable(Entity player, int[] pos) {
    return pos != null
            && !isRejectedTarget(pos)
            && !doesPlacementIntersectPlayer(player, pos)
            && isReplaceable(pos[0], pos[1], pos[2]);
}

boolean doesPlacementIntersectPlayer(Entity player, int[] placePos) {
    if (placePos == null) return false;
    if (isInsideAnyPlayerPositionCell(player, placePos)) return true;

    Vec3 pos = player.getPosition();
    double half = player.getWidth() / 2.0;
    double height = player.getHeight();
    if (boxIntersectsBlock(pos.x - half, pos.y, pos.z - half, pos.x + half, pos.y + height, pos.z + half, placePos)) return true;
    if (isBlockPosInsideBounds(placePos, pos.x - half, pos.y, pos.z - half, pos.x + half, pos.y + height, pos.z + half)) return true;

    if (!shouldUseHistoricalPlayerCollisionChecks(player, placePos)) return false;

    Vec3 last = player.getLastPosition();
    if (last.x != pos.x || last.y != pos.y || last.z != pos.z) {
        if (boxIntersectsBlock(last.x - half, last.y, last.z - half, last.x + half, last.y + height, last.z + half, placePos)) return true;
        if (isBlockPosInsideBounds(placePos, last.x - half, last.y, last.z - half, last.x + half, last.y + height, last.z + half)) return true;
    }
    if (hasLastSentServerPos && (lastSentServerPosX != pos.x || lastSentServerPosY != pos.y || lastSentServerPosZ != pos.z)) {
        double sx = lastSentServerPosX;
        double sy = lastSentServerPosY;
        double sz = lastSentServerPosZ;
        if (boxIntersectsBlock(sx - half, sy, sz - half, sx + half, sy + height, sz + half, placePos)) return true;
        if (isBlockPosInsideBounds(placePos, sx - half, sy, sz - half, sx + half, sy + height, sz + half)) return true;
    }
    return false;
}

boolean boxIntersectsBlock(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int[] pos) {
    return maxX > pos[0] && minX < pos[0] + 1.0 && maxY > pos[1] && minY < pos[1] + 1.0 && maxZ > pos[2] && minZ < pos[2] + 1.0;
}

boolean isBlockPosInsideBounds(int[] pos, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    int bMinX = floor(minX + 1.0E-4);
    int bMaxX = floor(maxX - 1.0E-4);
    if (pos[0] < bMinX || pos[0] > bMaxX) return false;
    int bMinZ = floor(minZ + 1.0E-4);
    int bMaxZ = floor(maxZ - 1.0E-4);
    if (pos[2] < bMinZ || pos[2] > bMaxZ) return false;
    int bMinY = floor(minY + 1.0E-4);
    int bMaxY = floor(maxY - 1.0E-4);
    return pos[1] >= bMinY && pos[1] <= bMaxY;
}

boolean isInsideAnyPlayerPositionCell(Entity player, int[] placePos) {
    Vec3 pos = player.getPosition();
    if (isInsidePlayerPositionCell(placePos, pos.x, pos.y, pos.z)) return true;
    if (!shouldUseHistoricalPlayerCollisionChecks(player, placePos)) return false;
    Vec3 last = player.getLastPosition();
    if (isInsidePlayerPositionCell(placePos, last.x, last.y, last.z)) return true;
    return hasLastSentServerPos && isInsidePlayerPositionCell(placePos, lastSentServerPosX, lastSentServerPosY, lastSentServerPosZ);
}

boolean shouldUseHistoricalPlayerCollisionChecks(Entity player, int[] placePos) {
    if (!player.onGround()) return false;
    if (placePos == null) return true;
    return placePos[1] > getCurrentBelowTargetY(player);
}

boolean isInsidePlayerPositionCell(int[] placePos, double x, double y, double z) {
    int playerX = floor(x);
    int playerY = floor(y);
    int playerZ = floor(z);
    return placePos[0] == playerX && placePos[2] == playerZ && (placePos[1] == playerY || placePos[1] == playerY + 1);
}

boolean isStrictOneBelowPlayer(Entity player, int[] pos) {
    if (pos == null) return false;
    return strictHeightAccepts(player, pos);
}

boolean strictHeightAccepts(Entity player, int[] pos) {
    int targetY = pos[1];
    int currentY = getCurrentBelowTargetY(player);
    if (targetY == currentY) return true;
    if (targetY == getStrictBelowTargetY(player)) return true;
    int previousY = getPreviousBelowTargetY(player);
    if (previousY != -2147483648 && targetY == previousY) return true;
    return false;
}

double getStableBelowReferenceY(Entity player) {
    Vec3 pos = player.getPosition();
    double referenceY = pos.y;
    Vec3 motion = client.getMotion();
    if (!player.onGround() && motion.y > -0.12 && motion.y <= 0.0) {
        referenceY = Math.max(referenceY, player.getLastPosition().y);
    }
    return referenceY;
}

int getStrictBelowTargetY(Entity player) {
    if (isDiagonalMovementContext(player)) return getCurrentBelowTargetY(player);
    double projectedY = getStableBelowReferenceY(player);
    Vec3 motion = client.getMotion();
    if (!player.onGround() && motion.y < -0.12) {
        projectedY = player.getPosition().y + motion.y * 0.75;
    }
    return floor(projectedY) - 1;
}

int getCurrentBelowTargetY(Entity player) {
    return floor(getStableBelowReferenceY(player)) - 1;
}

int getPreviousBelowTargetY(Entity player) {
    return floor(player.getLastPosition().y) - 1;
}

boolean isSupportAvailable(int x, int y, int z) {
    if (isInteractable(x, y, z)) return false;
    return !isReplaceable(x, y, z);
}

boolean isRejectedTarget(int[] pos) {
    Integer rejectedAtTick = rejectedTargets.get(posKey(pos));
    if (rejectedAtTick == null) return false;
    return currentClientTick - rejectedAtTick <= 4;
}

void markRejectedTarget(int[] pos) {
    if (pos == null) return;
    rejectedTargets.put(posKey(pos), currentClientTick);
}

void pruneRejectedTargets() {
    if (rejectedTargets.isEmpty()) return;
    Iterator<Map.Entry<String, Integer>> iterator = rejectedTargets.entrySet().iterator();
    while (iterator.hasNext()) {
        Map.Entry<String, Integer> entry = iterator.next();
        if (currentClientTick - entry.getValue() > 4) {
            iterator.remove();
            continue;
        }
        String[] parts = entry.getKey().split(",");
        if (!isReplaceable(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))) {
            iterator.remove();
        }
    }
}

Object[] rayCast(float yaw, float pitch) {
    Object[] hit = client.raycastBlock(reach(), yaw, pitch);
    if (hit == null || hit[0] == null || hit[2] == null) return null;
    int face = faceFromName((String) hit[2]);
    if (face < 0 || face == 0) return null;
    int[] supportPos = posFromVec((Vec3) hit[0]);
    Vec3 offset = (Vec3) hit[1];
    Vec3 hitAbs = new Vec3(supportPos[0] + offset.x, supportPos[1] + offset.y, supportPos[2] + offset.z);
    return new Object[]{supportPos, face, hitAbs};
}

Vec3 getEyes(Entity player) {
    Vec3 pos = player.getPosition();
    return new Vec3(pos.x, pos.y + player.getEyeHeight(), pos.z);
}

String blockNameAt(int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    return block == null || block.name == null ? "air" : block.name.toLowerCase();
}

boolean isReplaceable(int x, int y, int z) {
    return isReplaceableName(blockNameAt(x, y, z), false);
}

boolean isReplaceableName(String name, boolean airOnly) {
    if (airOnly) return name.equals("air");
    for (String replaceable : REPLACEABLE_BLOCKS) {
        if (name.equals(replaceable)) return true;
    }
    for (String replaceable : EXPERIMENTAL_REPLACEABLE_BLOCKS) {
        if (name.equals(replaceable)) return true;
    }
    return false;
}

boolean isInteractable(int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    if (block == null) return false;
    if (block.interactable) return true;
    if (block.type == null) return false;
    for (String interactableType : INTERACTABLE_TYPES) {
        if (block.type.equals(interactableType)) return true;
    }
    return false;
}

double reach() {
    return client.isCreative() ? 5.0 : 4.5;
}

int placementTick(Entity player) {
    if (isRavenTimerActive()) return (int) (client.time() / 50L);
    return player.getTicksExisted();
}

boolean isRavenTimerActive() {
    try {
        return modules.isEnabled("Timer");
    } catch (Exception ignored) {
        return false;
    }
}

float candidatePitch(Object[] candidate) {
    return clampFloat((Float) candidate[0], -90.0f, 90.0f);
}

int[] candidateSupportPos(Object[] candidate) {
    return (int[]) candidate[1];
}

int candidateFace(Object[] candidate) {
    return (Integer) candidate[2];
}

Vec3 candidateHitVec(Object[] candidate) {
    return (Vec3) candidate[3];
}

int[] candidatePlacedPos(Object[] candidate) {
    return (int[]) candidate[4];
}

float sanitizePitch(float pitch, float fallbackPitch) {
    float safeFallback = clampFloat(Float.isNaN(fallbackPitch) ? 0.0f : fallbackPitch, -90.0f, 90.0f);
    if (Float.isNaN(pitch) || Float.isInfinite(pitch)) return safeFallback;
    return clampFloat(pitch, -90.0f, 90.0f);
}

int floor(double value) {
    int i = (int) value;
    return value < i ? i - 1 : i;
}

float clampFloat(float value, float min, float max) {
    return value < min ? min : (value > max ? max : value);
}

float wrapAngle(float angle) {
    angle = angle % 360f;
    if (angle >= 180f) angle -= 360f;
    if (angle < -180f) angle += 360f;
    return angle;
}

boolean posEquals(int[] a, int[] b) {
    return a != null && b != null && a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
}

String posKey(int[] pos) {
    return pos[0] + "," + pos[1] + "," + pos[2];
}

int[] posFromVec(Vec3 vec) {
    return new int[]{floor(vec.x), floor(vec.y), floor(vec.z)};
}

int[] offsetPos(int[] pos, int face) {
    if (face == 0) return new int[]{pos[0], pos[1] - 1, pos[2]};
    if (face == 1) return new int[]{pos[0], pos[1] + 1, pos[2]};
    if (face == 2) return new int[]{pos[0], pos[1], pos[2] - 1};
    if (face == 3) return new int[]{pos[0], pos[1], pos[2] + 1};
    if (face == 4) return new int[]{pos[0] - 1, pos[1], pos[2]};
    return new int[]{pos[0] + 1, pos[1], pos[2]};
}

int opposite(int face) {
    if (face == 0) return 1;
    if (face == 1) return 0;
    if (face == 2) return 3;
    if (face == 3) return 2;
    if (face == 4) return 5;
    return 4;
}

int rotateY(int face) {
    if (face == 2) return 5;
    if (face == 5) return 3;
    if (face == 3) return 4;
    if (face == 4) return 2;
    return face;
}

int rotateYCCW(int face) {
    if (face == 2) return 4;
    if (face == 4) return 3;
    if (face == 3) return 5;
    if (face == 5) return 2;
    return face;
}

int facingFromYaw(float yaw) {
    int index = floor(yaw / 90.0 + 0.5) & 3;
    if (index == 0) return 3;
    if (index == 1) return 4;
    if (index == 2) return 2;
    return 5;
}

String faceName(int face) {
    if (face == 0) return "DOWN";
    if (face == 1) return "UP";
    if (face == 2) return "NORTH";
    if (face == 3) return "SOUTH";
    if (face == 4) return "WEST";
    return "EAST";
}

int faceFromName(String name) {
    if (name == null) return -1;
    String upper = name.toUpperCase();
    if (upper.equals("DOWN")) return 0;
    if (upper.equals("UP")) return 1;
    if (upper.equals("NORTH")) return 2;
    if (upper.equals("SOUTH")) return 3;
    if (upper.equals("WEST")) return 4;
    if (upper.equals("EAST")) return 5;
    return -1;
}

void recordLanePlacement(Entity player, int[] placedPos) {
    if (placedPos == null) return;
    if (player == null) return;
    blocksPlacedSinceJump++;
}

}
