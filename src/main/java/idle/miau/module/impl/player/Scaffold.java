package idle.miau.module.impl.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.DispatchPacketEvent;
import idle.miau.event.JumpEvent;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.PostPlayerInputEvent;
import idle.miau.event.PostSprintUpdateEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.PrePlayerInteractEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.event.RightClickMouseEvent;
import idle.miau.event.StrafeEvent;
import idle.miau.mcp.McpRuntime;
import idle.miau.helper.RotationHelper;
import idle.miau.helper.RotationPriority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.client.MouseEvent;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.AntiFireball;
import idle.miau.module.impl.player.scaffolds.BlockRenderFeature;
import idle.miau.module.impl.render.MiauHudAnimation;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ScaffoldUtil;
import idle.miau.utility.Utils;
import idle.miau.utility.PacketUtils;
import idle.miau.event.EventMovementInput;
import idle.miau.event.EventSilentRotation;
import idle.miau.helper.SilentRotationManager;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.inventory.SilentHotbar;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.Packet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Scaffold extends Module {
    private static final String[] MODES = {"Normal", "Telly", "Telly2", "Telly3", "Telly4"};
    private static final String[] ROTATION_MODES = {
            "Intave", "Minimal", "Normal"
    };
    private static final String[] COUNTER_ANIMATIONS = {"None", "Move in", "Scale in"};
    private static final String[] SPRINT_MODES = {"Off", "Vanilla"};
    private static final String[] TELLY_SNEAK_MODES = {"Off", "Mid cycle"};
    private static final String[] YAW_OPTIONS = {"45", "0", "-45"};
    private static final float[] YAW_OFFSETS = {45.0F, 0.0F, -45.0F};
    private static List<net.minecraft.block.Block> telly2InvalidBlocks;
    private static final int NORMAL_MODE = 0;
    private static final int TELLY_MODE = 1;
    private static final int TELLY2_MODE = 2;
    private static final int TELLY3_MODE = 3;
    private static final int TELLY4_MODE = 4;
    private static final int INTAVE_ROTATION = 0;
    private static final int MINIMAL_ROTATION = 1;
    private static final int MIAU_ROTATION = 2;
    private static final int NO_SLOT = -1;
    private static final int OFF_SPRINT = 0;
    private static final int VANILLA_SPRINT = 1;
    private static final int ENABLE_CLUTCH_TICKS = 4;
    private static final double NORMAL_PLACE_MAX_MOTION_Y = 0.3D;
    private static final float INTAVE_SIGNIFICANT_YAW_DELTA = 30.0F;
    private static final float INTAVE_SIGNIFICANT_PITCH_DELTA = 20.0F;
    private static final int NORMAL_INTAVE_ENTRY_WAIT_TICKS = 1;
    private static final float MAX_SCAFFOLD_YAW_DELTA = 149.0F;
    private static final int VULCAN_SCAFFOLD_ROTATION_WINDOW = 5;
    private static final float MINIMAL_BASE_PITCH = 76.0F;
    private static final float MINIMAL_MIN_PITCH = 70.0F;
    private static final float MINIMAL_INTAVE_MAX_PITCH = 84.0F;
    private static final float MINIMAL_POLAR_MAX_PITCH = 80.0F;
    private static final float MINIMAL_POLAR_SECTOR = 45.0F;
    private static final float MINIMAL_POLAR_DIAGONAL_THRESHOLD = 10.0F;
    private static final float MINIMAL_INTAVE_MIN_YAW_STEP = 1.8F;
    private static final float MINIMAL_INTAVE_YAW_STEP_VARIANCE = 0.1F;
    private static final int MINIMAL_INTAVE_YAW_ATTEMPTS = 100;
    private static final SilentRotationManager SILENT_ROTATIONS =
            SilentRotationManager.getInstance();
    private static final BlockCounterRenderer COUNTER_RENDERER = new BlockCounterRenderer();
    private static boolean counterRendererRegistered;
    private final ScaffoldSneakSafety sneakSafety = new ScaffoldSneakSafety();
    private final ScaffoldPlacementSafety placementSafety = new ScaffoldPlacementSafety();

    private final SliderSetting mode;
    private final SliderSetting rotationMode;
    private final SliderSetting sprintMode;
    private final SliderSetting yaw;
    private final SliderSetting tellySneak;
    private final SliderSetting tellySneakEveryCycle;
    private final ButtonSetting spoofItems;
    private final ButtonSetting flick;
    private final ButtonSetting keepY;
    private final ButtonSetting jumpOnUserInput;
    private final ButtonSetting rmbOnly;
    public final ButtonSetting whileKillAura;
    private final ButtonSetting clutch;
    private final RangeSetting rotationSpeed;
    private final ButtonSetting safeWalk;
    private final ButtonSetting countBlock;
    private final SliderSetting counterAnimation;
    private final ButtonSetting normalIntave;
    private final ButtonSetting tellyIntave;
    private final ButtonSetting telly2SameY;
    private final SliderSetting tellyMultiPlace;
    private final ButtonSetting telly4Tower;
    private final ButtonSetting telly4KeepYOnRight;
    private final ButtonSetting eagle;
    private final ButtonSetting noSwing;
    private final SliderSetting eagleDistance;
    private final RangeSetting eagleTicks;
    private final RangeSetting sneakPerBlock;
    private final BlockRenderFeature blockRenderFeature;

    private final ScaffoldTellyMode tellyMode;
    private final ScaffoldTelly2Mode telly2Mode;
    private final ScaffoldTelly4Mode telly4Mode;
    private final ScaffoldTelly3Mode telly3Mode;
    private float[] rotations = new float[2];
    private EventSilentRotation.Post pendingPost;
    private NormalPlacementPlan pendingNormalPlacement;
    private float lastNormalPlacedYawDelta = Float.NaN;
    private int normalDuplicateNudgeSign = 1;
    private boolean normalDispatchedPitchInitialized;
    private float normalLastDispatchedPitch;
    private long normalLastDispatchedPitchDeltaRaw = -1L;
    private int normalVulcanRotationWindow;
    private int scaffoldRotationGuardTicks;
    private int eagleSneakTicks;
    private int blocksSinceEagle;
    private int blocksUntilEagle;
    private int keepYLevel;
    private boolean keepYAnchored;
    private boolean userTowering;
    private int previousSlot = NO_SLOT;
    private int switchedBlockSlot = NO_SLOT;
    private int enableClutchTicks;
    private NormalPlacementPlan pendingEnableClutch;
    private boolean enableClutchRotationActive;
    private boolean yieldingToAntiFireball;
    private final MiauHudAnimation blockCounterAnimation =
            new MiauHudAnimation(false, System.currentTimeMillis());

    private final NormalIntaveTransitionController normalIntaveTransition =
            new NormalIntaveTransitionController();
    public Scaffold() {
        super("Scaffold", category.player);
        setDescription("Automatically places blocks beneath you while moving.");
        registerSetting(mode = new ScaffoldModeSetting());
        registerSetting(rotationMode = new SliderSetting(
                "Rotation", INTAVE_ROTATION, ROTATION_MODES));
        registerSetting(sprintMode = new SliderSetting("Sprint", VANILLA_SPRINT, SPRINT_MODES));
        registerSetting(yaw = new SliderSetting(
                "Yaw", 1, YAW_OPTIONS, "setting.tellyYaw"));
        registerSetting(tellySneak = new SliderSetting(
                "Sneak", 0, TELLY_SNEAK_MODES, "setting.tellySneak"));
        registerSetting(tellySneakEveryCycle = new SliderSetting(
                "Sneak every x cycle", 1.0D, 1.0D, 10.0D, 1.0D),
                "setting.tellySneakEveryCycle");
        spoofItems = new ButtonSetting("Spoof Items (Silent)", false);
        registerSetting(flick = new ButtonSetting("Flick", true));
        registerSetting(keepY = new ButtonSetting("KeepY", false));
        registerSetting(jumpOnUserInput = new ButtonSetting("Jump on userinput", false));
        registerSetting(rmbOnly = new ButtonSetting("RMB Only", false));
        registerSetting(whileKillAura = new ButtonSetting("While KillAura", false));
        registerSetting(clutch = new ButtonSetting("Clutch", false));
        registerSetting(rotationSpeed = new RangeSetting(
                "Rotation Speed", 1.0D, 360.0D, 180.0D, 360.0D, 1.0D));
        registerSetting(safeWalk = new ButtonSetting("SafeWalk", true));
        registerSetting(noSwing = new ButtonSetting("NoSwing", false));
        registerSetting(countBlock = new ButtonSetting("Count-Block", true));
        registerSetting(counterAnimation = new SliderSetting(
                "Animation", 2, COUNTER_ANIMATIONS));
        registerSetting(eagle = new ButtonSetting("Eagle", false));
        registerSetting(eagleDistance = new SliderSetting(
                "Eagle Distance", 0.0D, 0.0D, 0.5D, 0.01D));
        registerSetting(eagleTicks = new RangeSetting(
                "Eagle Ticks", 0.0D, 20.0D, 1.0D, 5.0D, 1.0D));
        registerSetting(sneakPerBlock = new RangeSetting(
                "Sneak Per Block", 0.0D, 10.0D, 0.0D, 0.0D, 1.0D));
        blockRenderFeature = new BlockRenderFeature(this);
        registerSetting(normalIntave = new ModeButtonSetting(
                "Intave", "Normal.Intave", false),
                "setting.normalIntave");
        registerSetting(tellyIntave = new ModeButtonSetting(
                "Intave", "Telly.Intave", false),
                "setting.tellyIntave");
        registerSetting(telly2SameY = new ModeButtonSetting(
                "SameY", "Telly2.SameY", true),
                "setting.telly2SameY");
        registerSetting(tellyMultiPlace = new SliderSetting(
                "Multi-Place", 0, new String[]{"Disabled", "1 extra", "2 extra"},
                "setting.hypixelMultiPlace"));
        registerSetting(telly4Tower = new ButtonSetting("Tower", true, "setting.hypixelTower"));
        registerSetting(telly4KeepYOnRight = new ButtonSetting(
                "Keep-Y on Press Right", true, "setting.hypixelKeepYOnRight"));
        tellyMode = new ScaffoldTellyMode(this);
        telly2Mode = new ScaffoldTelly2Mode(this);
        telly4Mode = new ScaffoldTelly4Mode(this);
        telly3Mode = new ScaffoldTelly3Mode(this);
        guiUpdate();
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    public boolean allowKillAura() {
        return !isEnabled() || (isTellyBridgeMode()
                ? getTellyBridgeMode().pauseForKillAura() : whileKillAura.isToggled());
    }

    public boolean allowsSprint() {
        return !isEnabled() || !isTellyBridgeMode() || getTellyBridgeMode().canSprint();
    }

    @Override
    public void onEnable() {
        sneakSafety.reset();
        placementSafety.reset();
        normalIntaveTransition.cancel(false);
        ensureCounterRendererRegistered();
        SilentHotbar.INSTANCE.resetSlot(this, true);
        clearItemSwitchState();
        if (mc.gameSettings != null && !isTellyMode() && !isTellyBridgeMode()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
        pendingPost = null;
        pendingNormalPlacement = null;
        resetNormalDuplicateState();
        scaffoldRotationGuardTicks = 0;
        resetEagleState();
        resetKeepYState();
        tellyMode.reset();
        telly2Mode.reset();
        blockRenderFeature.clear();
        SILENT_ROTATIONS.synchronize();
        resetNormalRotationState();
        armNormalIntaveEntry();
        if (isTellyBridgeMode()) {
            getTellyBridgeMode().onEnable();
        } else {
            armEnableClutch();
        }
    }

    @Override
    public void onDisable() {
        if (isTellyBridgeMode()) {
            getTellyBridgeMode().onDisable();
        }
        sneakSafety.reset();
        placementSafety.reset();
        boolean intaveRequested = isNormalMode()
                && normalIntave.isToggled() && Utils.nullCheck();
        boolean intaveRelease = intaveRequested
                && normalIntaveTransition.armExit(
                    RotationUtils.serverRotations[0],
                    RotationUtils.serverRotations[1],
                    mc.thePlayer.rotationYaw,
                    mc.thePlayer.rotationPitch);
        if (!intaveRequested) {
            normalIntaveTransition.cancel(false);
        }
        restoreItemSlot();
        pendingPost = null;
        pendingNormalPlacement = null;
        clearEnableClutch();
        yieldingToAntiFireball = false;
        resetNormalDuplicateState();
        scaffoldRotationGuardTicks = 0;
        resetEagleState();
        resetKeepYState();
        tellyMode.reset();
        telly2Mode.reset();
        setShift(false);
        if (!intaveRelease) {
            SILENT_ROTATIONS.synchronize();
        }
        blockRenderFeature.clear();
        long now = System.currentTimeMillis();
        if (getCounterAnimationMode() == 0) {
            blockCounterAnimation.snapTo(false, now);
        } else {
            blockCounterAnimation.setVisible(false, now);
        }
    }

    @Override
    public void guiUpdate() {
        boolean normalMode = isNormalMode();
        boolean telly = isTellyMode();
        boolean towerBridge = isTellyBridgeMode();
        rotationMode.setVisible(normalMode, this);
        sprintMode.setVisible(!towerBridge, this);
        yaw.setVisible(telly || normalMode && !isMinimalRotation(), this);
        tellySneak.setVisible(telly, this);
        tellySneakEveryCycle.setVisible(
                telly && isTellyMidCycleSneakEnabled(), this);
        normalIntave.setVisible(normalMode, this);
        tellyIntave.setVisible(isOriginalTellyMode(), this);
        telly2SameY.setVisible(isTelly2Mode(), this);
        flick.setVisible(normalMode, this);
        keepY.setVisible(normalMode, this);
        jumpOnUserInput.setVisible(normalMode && keepY.isToggled(), this);
        rmbOnly.setVisible(normalMode && keepY.isToggled(), this);
        rotationSpeed.setVisible(normalMode, this);
        safeWalk.setVisible(normalMode, this);
        whileKillAura.setVisible(!towerBridge, this);
        clutch.setVisible(!towerBridge, this);
        noSwing.setVisible(!towerBridge, this);
        countBlock.setVisible(!towerBridge, this);
        counterAnimation.setVisible(!towerBridge && countBlock.isToggled(), this);
        eagle.setVisible(normalMode, this);
        eagleDistance.setVisible(normalMode && eagle.isToggled(), this);
        eagleTicks.setVisible(normalMode && eagle.isToggled(), this);
        sneakPerBlock.setVisible(normalMode && eagle.isToggled(), this);
        blockRenderFeature.guiUpdate(!towerBridge);
        tellyMultiPlace.setVisible(towerBridge, this);
        telly4Tower.setVisible(towerBridge && !isTelly3Mode(), this);
        telly4KeepYOnRight.setVisible(towerBridge && !isTelly3Mode(), this);
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            onModeChanged();
        }
        else if (setting == rotationMode) {
            onNormalRotationModeChanged();
        }
    }

    @Override
    public void onCommandSettingChanged(idle.miau.module.setting.Setting setting) {
        if (setting == mode) {
            onModeChanged();
        }
        else if (setting == rotationMode) {
            onNormalRotationModeChanged();
        }
        else if (setting == clutch) {
            updateEnableClutchSetting();
        }
    }

    private void onModeChanged() {
        telly4Mode.onDisable();
        telly3Mode.onDisable();
        telly4Mode.reset();
        telly3Mode.reset();
        restoreItemSlot();
        normalIntaveTransition.cancel(false);
        clearNormalPlacementState(true);
        clearEnableClutch();
        scaffoldRotationGuardTicks = 0;
        yieldingToAntiFireball = false;
        tellyMode.reset();
        telly2Mode.reset();
        setShift(false);
        if (isEnabled()) {
            SILENT_ROTATIONS.synchronize();
            resetNormalRotationState();
            armNormalIntaveEntry();
            if (isTellyBridgeMode()) {
                getTellyBridgeMode().onEnable();
            }
        }
        guiUpdate();
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == spoofItems && isEnabled()) {
            updateItemSwitchMode();
        }
        if (setting == normalIntave && isEnabled() && isNormalMode()) {
            normalIntaveTransition.cancel(false);
            armNormalIntaveEntry();
        }
        if (setting == clutch) {
            updateEnableClutchSetting();
        }
        guiUpdate();
        super.guiButtonToggled(setting);
    }

    private void onNormalRotationModeChanged() {
        normalIntaveTransition.cancel(false);
        clearNormalPlacementState(true);
        resetNormalRotationState();
        if (isEnabled() && isNormalMode()) {
            SILENT_ROTATIONS.synchronize();
            resetNormalRotationState();
            armNormalIntaveEntry();
        }
        guiUpdate();
    }

    @Subscribe
    public void onClientRotation(ClientRotationEvent event) {
        enableClutchRotationActive = false;
        pendingEnableClutch = null;
        yieldingToAntiFireball = false;
        if (!Utils.nullCheck()) {
            enableClutchTicks = 0;
            return;
        }
        if (isTellyBridgeMode()) {
            pendingPost = null;
            pendingNormalPlacement = null;
            getTellyBridgeMode().onClientRotation(event);
            return;
        }
        if (prepareEnableClutch(event)) {
            pendingPost = null;
            pendingNormalPlacement = null;
            return;
        }
        AntiFireball antiFireball = ModuleManager.antiFireball;
        if (enableClutchTicks <= 0 && antiFireball != null
                && antiFireball.shouldTakeScaffoldRotation()
                && scaffoldRotationGuardTicks <= 0
                && !isImmediatePlacementRequired()) {
            yieldingToAntiFireball = true;
            pendingPost = null;
            pendingNormalPlacement = null;
            telly2Mode.onRotationClaimed(false);
            return;
        }
        if (isOriginalTellyMode()) {
            pendingPost = null;
            pendingNormalPlacement = null;
            EventSilentRotation.Post settled = SILENT_ROTATIONS.update(tellyMode::updateRotation);
            float boundedPitch = limitScaffoldPitch(settled.getPitch(),
                    RotationUtils.serverRotations[1], RotationUtils.getGCD());
            boolean claimed = !settled.isModified() || event.trySetRotation(
                    this, RotationPriority.BLOCK_PLACEMENT,
                    settled.getYaw(), boundedPitch, true);
            tellyMode.onPlannedRotation(
                    settled.getYaw(), boundedPitch,
                    settled.isModified() && claimed);
            if (settled.isModified()) {
                if (claimed) {
                    event.setMaxYawDelta(ScaffoldTellyMode.MAX_YAW_STEP);
                    if (Float.compare(boundedPitch, settled.getPitch()) != 0) {
                        event.setRotationAlreadyQuantized(true);
                    }
                }
            }
            return;
        }
        if (isTelly2Mode()) {
            pendingPost = null;
            pendingNormalPlacement = null;
            ScaffoldTelly2Mode.RotationCommand command =
                    telly2Mode.prepareRotation(
                            RotationUtils.serverRotations[0],
                            RotationUtils.serverRotations[1]);
            if (command == null) {
                telly2Mode.onRotationClaimed(false);
                return;
            }
            boolean claimed = event.trySetRotation(
                    this, RotationPriority.BLOCK_PLACEMENT,
                    command.yaw, command.pitch, true);
            telly2Mode.onRotationClaimed(claimed);
            if (claimed) {
                event.setRotationAlreadyQuantized(true);
            }
            return;
        }
        updateKeepYState();
        NormalRotationRequest request = prepareNormalRotation();
        normalIntaveTransition.beginEntrySneak(
                request.yaw, request.pitch,
                shouldSkipNormalIntaveSneak(request));

        EventSilentRotation.Post settled = SILENT_ROTATIONS.update(request::apply);
        float settledYaw = settled.getYaw();
        float settledPitch = settled.getPitch();
        if (request.placement != null && request.placeNow) {
            float[] diversified = diversifyNormalPlacementRotation(
                    request.placement, settledYaw, settledPitch);
            settledYaw = diversified[0];
            settledPitch = diversified[1];
        }
        boolean vulcanPitchCorrected = false;
        float boundedPitch = limitScaffoldPitch(settledPitch,
                RotationUtils.serverRotations[1], RotationUtils.getGCD());
        if (Float.compare(boundedPitch, settledPitch) != 0) {
            settledPitch = boundedPitch;
            vulcanPitchCorrected = true;
            SILENT_ROTATIONS.synchronizeEmitted(settledYaw, settledPitch);
        }
        if (normalVulcanRotationWindow > 0) {
            float safePitch = safeguardNormalVulcanPitch(
                    RotationUtils.serverRotations[1], settledYaw,
                    settledPitch,
                    request.placement == null
                            ? null : request.placement.data);
            if (Float.compare(safePitch, settledPitch) != 0) {
                settledPitch = safePitch;
                vulcanPitchCorrected = true;
                SILENT_ROTATIONS.synchronizeEmitted(
                        settledYaw, settledPitch);
            }
        }
        boolean requiresRotation = settled.isModified()
                || request.placement != null || vulcanPitchCorrected;
        boolean claimed = !requiresRotation || event.trySetRotation(
                this, RotationPriority.BLOCK_PLACEMENT,
                settledYaw, settledPitch, true);
        if (claimed) {
            event.setMaxYawDelta(MAX_SCAFFOLD_YAW_DELTA);
            if (vulcanPitchCorrected) event.setRotationAlreadyQuantized(true);
        }
        pendingPost = claimed ? settled : null;
        pendingNormalPlacement = claimed && request.placeNow
                ? request.placement : null;
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (isEnabled() && Utils.nullCheck()) {
            if (isTellyBridgeMode()) {
                getTellyBridgeMode().renderPlaced();
            } else {
                blockRenderFeature.render(event);
            }
        }
    }

    private void renderBlockCounter(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        if (isTellyBridgeMode()) {
            if (!mc.gameSettings.showDebugInfo
                    && (mc.currentScreen == null || mc.currentScreen instanceof GuiChat)) {
                getTellyBridgeMode().renderCount();
            }
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean counterVisible = isEnabled() && countBlock.isToggled();
        int animationMode = getCounterAnimationMode();
        if (animationMode == 0) {
            blockCounterAnimation.snapTo(counterVisible, now);
        } else {
            blockCounterAnimation.setVisible(counterVisible, now);
        }
        if (!counterVisible && (animationMode == 0
                || blockCounterAnimation.isFinishedHidden(now))) {
            return;
        }
        float progress = animationMode == 0
                ? 1.0F : (float) blockCounterAnimation.getOutput(now);
        if (progress <= 0.0F) {
            return;
        }

        int count = countHotbarBlocks(mc.thePlayer.inventory.mainInventory);
        String countColor = count <= 5 ? "\u00a7c" : count <= 15 ? "\u00a76" : count <= 25 ? "\u00a7e" : "";
        String text = countColor + count + "\u00a7f blocks";
        ScaledResolution resolution = new ScaledResolution(mc);
        float x = resolution.getScaledWidth() / 2.0F + 11.0F;
        float y = resolution.getScaledHeight() / 2.0F - 3.0F;
        int color = applyCounterAnimationAlpha(0xF0FFFFFF, progress);
        float renderWidth = mc.fontRendererObj.getStringWidth(text);
        float renderHeight = mc.fontRendererObj.FONT_HEIGHT;
        float centerX = x + renderWidth / 2.0F;
        float centerY = y + renderHeight / 2.0F;

        GlStateManager.pushMatrix();
        if (animationMode == 1) {
            GlStateManager.translate(getCounterMoveOffset(
                    x, x + renderWidth, resolution.getScaledWidth(), progress), 0.0F, 0.0F);
        } else if (animationMode == 2) {
            GlStateManager.translate(centerX, centerY, 0.0F);
            GlStateManager.scale(progress, progress, 1.0F);
            GlStateManager.translate(-centerX, -centerY, 0.0F);
        }
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.fontRendererObj.drawString(text, x, y, color, true);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private static final class BlockCounterRenderer {
        private Scaffold owner;

        @Subscribe
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            Scaffold current = owner;
            if (current != null) {
                current.renderBlockCounter(event);
                if (event.phase == TickEvent.Phase.END
                        && !current.isEnabled()
                        && current.isCounterAnimationHidden()) {
                    McpRuntime.EVENTS.unregister(this);
                    counterRendererRegistered = false;
                }
            }
        }
    }

    private static final class ModeButtonSetting extends ButtonSetting {
        private final String configKey;

        private ModeButtonSetting(
                String name, String configKey, boolean enabled) {
            super(name, enabled);
            this.configKey = configKey;
        }

        @Override
        public String getConfigKey() {
            return configKey;
        }
    }

    private static final class ScaffoldModeSetting extends SliderSetting {
        private ScaffoldModeSetting() {
            super("Mode", NORMAL_MODE, MODES);
        }

        @Override
        public void loadConfig(JsonObject data) {
            Double legacyValue = readLegacyModeValue(data);
            super.loadConfig(data);
            if (legacyValue == null) {
                return;
            }
            if (legacyValue >= 1.5D && legacyValue < 2.5D
                    && !hasCurrentTelly2Settings(data)) {
                setValue(TELLY_MODE);
            }
            else if (legacyValue >= 0.5D && legacyValue < 1.5D
                    && !hasCurrentTelly2Settings(data)
                    && hasLegacyGodBridgeSettings(data)) {
                setValue(NORMAL_MODE);
            }
        }

        private static Double readLegacyModeValue(JsonObject data) {
            if (data == null) {
                return null;
            }
            for (String key : new String[]{"Mode", "setting.mode"}) {
                JsonElement value = data.get(key);
                if (value != null && value.isJsonPrimitive()) {
                    try {
                        return value.getAsDouble();
                    }
                    catch (RuntimeException ignored) {
                    }
                }
            }
            return null;
        }

        private static boolean hasCurrentTelly2Settings(JsonObject data) {
            if (data == null) {
                return false;
            }
            if (data.has("setting.telly2SameY") || data.has("Telly2.SameY")) {
                return true;
            }
            JsonElement portable = data.get("portableSettings");
            if (portable == null || !portable.isJsonArray()) {
                return false;
            }
            for (JsonElement entry : portable.getAsJsonArray()) {
                if (entry == null || !entry.isJsonObject()) {
                    continue;
                }
                JsonElement name = entry.getAsJsonObject().get("name");
                if (name != null && name.isJsonPrimitive()
                        && name.getAsString().equalsIgnoreCase("Telly2.SameY")) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasLegacyGodBridgeSettings(JsonObject data) {
            if (data == null) {
                return false;
            }
            if (data.has("setting.tellyYaw")) {
                return true;
            }
            for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                String key = entry.getKey();
                if (key.regionMatches(true, 0,
                        "GodBridge.", 0, "GodBridge.".length())) {
                    return true;
                }
            }
            JsonElement portable = data.get("portableSettings");
            if (portable == null || !portable.isJsonArray()) {
                return false;
            }
            for (JsonElement entry : portable.getAsJsonArray()) {
                if (entry == null || !entry.isJsonObject()) {
                    continue;
                }
                JsonElement name = entry.getAsJsonObject().get("name");
                if (name != null && name.isJsonPrimitive()
                        && name.getAsString().regionMatches(true, 0,
                        "GodBridge.", 0, "GodBridge.".length())) {
                    return true;
                }
            }
            return false;
        }
    }

    private final class NormalIntaveTransitionController {
        private boolean registered;
        private boolean entryPending;
        private boolean sneakTransition;
        private boolean exitActive;
        private float exitYaw;
        private float exitPitch;
        private float rotationSneakYaw = Float.NaN;
        private float rotationSneakPitch = Float.NaN;
        private int sneakSamples;

        void armEntry() {
            exitActive = false;
            entryPending = true;
            sneakTransition = false;
            sneakSamples = 0;
            clearRotationSneakTarget();
            ensureRegistered();
        }

        void beginEntrySneak(
                float targetYaw, float targetPitch,
                boolean verticalPlacement) {
            if (!normalIntave.isToggled() || !isNormalMode()
                    || verticalPlacement) {
                if (entryPending || sneakTransition) {
                    entryPending = false;
                    sneakTransition = false;
                    sneakSamples = 0;
                    clearRotationSneakTarget();
                    setShift(false);
                    unregisterIfIdle();
                }
                return;
            }
            if (!Utils.nullCheck()) {
                cancel(false);
                return;
            }
            if (sneakTransition && sneakSamples > 0) {
                finishSneakSample();
            }
            float serverYaw = RotationUtils.serverRotations[0];
            float serverPitch = RotationUtils.serverRotations[1];
            if (!entryPending && isExistingRotationSneak(
                    serverYaw, serverPitch, targetYaw, targetPitch)) {
                return;
            }
            boolean starting = entryPending;
            boolean significantTurn = isSignificantNormalIntaveRotation(
                    serverYaw, serverPitch, targetYaw, targetPitch);
            if (!starting && !significantTurn) {
                return;
            }
            entryPending = false;
            sneakTransition = true;
            sneakSamples = 0;
            rotationSneakYaw = targetYaw;
            rotationSneakPitch = targetPitch;
            setShift(true);
            ensureRegistered();
        }

        boolean armExit(
                float fromYaw, float fromPitch,
                float cameraYaw, float cameraPitch) {
            entryPending = false;
            sneakTransition = false;
            sneakSamples = 0;
            clearRotationSneakTarget();
            exitYaw = cameraYaw;
            exitPitch = cameraPitch;
            exitActive = true;
            sneakTransition = true;
            sneakSamples = 0;
            setShift(true);
            ensureRegistered();
            return true;
        }

        boolean isSneaking() {
            return sneakTransition;
        }

        void cancel(boolean clearRotations) {
            entryPending = false;
            sneakTransition = false;
            exitActive = false;
            sneakSamples = 0;
            clearRotationSneakTarget();
            if (registered) {
                registered = false;
                McpRuntime.EVENTS.unregister(this);
            }
            if (clearRotations) {
                SILENT_ROTATIONS.clear();
            }
        }

        @Subscribe(priority = Priority.LOWEST)
        public void onPostPlayerInput(PostPlayerInputEvent event) {
            if (!sneakTransition) {
                return;
            }
            if (!Utils.nullCheck()
                    || mc.thePlayer.movementInput == null) {
                cancel(true);
                return;
            }
            applyNormalIntaveSneak(mc.thePlayer.movementInput);
            setShift(true);
            mc.thePlayer.setSprinting(false);
            sneakSamples++;
        }

        @Subscribe(priority = Priority.LOWEST)
        public void onClientRotation(ClientRotationEvent event) {
            if (!sneakTransition && !exitActive) {
                return;
            }
            if (!Utils.nullCheck()) {
                cancel(true);
                return;
            }
            if (exitActive) {
                float currentYaw = RotationUtils.serverRotations[0];
                float currentPitch = RotationUtils.serverRotations[1];
                float returnSpeed = (float) rotationSpeed.getRandomInRange();
                if (!isFinite(returnSpeed) || returnSpeed <= 0.0F) {
                    returnSpeed = 1.0F;
                }
                float nextYaw = approachYaw(
                        currentYaw, exitYaw, returnSpeed);
                float nextPitch = approach(
                        currentPitch, exitPitch,
                        returnSpeed);
                boolean claimed = event.trySetRotation(
                        Scaffold.this, RotationPriority.BLOCK_PLACEMENT,
                        nextYaw, nextPitch, true);
                if (!claimed) {
                    return;
                }
                event.setMaxYawDelta(returnSpeed);
                if (sneakSamples > 0) {
                    finishSneakSample();
                }
                if (sameRotation(nextYaw, nextPitch, exitYaw, exitPitch)) {
                    finishExit();
                }
            } else {
                event.setMaxYawDelta(MAX_SCAFFOLD_YAW_DELTA);
            }
        }

        @Subscribe
        public void onWorldUnload(WorldEvent.Unload event) {
            cancel(true);
        }

        private void finishSneakSample() {
            entryPending = false;
            sneakTransition = false;
            sneakSamples = 0;
            setShift(false);
            unregisterIfIdle();
        }

        private void finishExit() {
            exitActive = false;
            clearRotationSneakTarget();
            SILENT_ROTATIONS.synchronize();
            unregisterIfIdle();
        }

        private void ensureRegistered() {
            if (!registered) {
                registered = true;
                McpRuntime.EVENTS.register(this);
            }
        }

        private void unregisterIfIdle() {
            if (registered && !entryPending && !sneakTransition && !exitActive) {
                registered = false;
                McpRuntime.EVENTS.unregister(this);
            }
        }

        private boolean isExistingRotationSneak(
                float serverYaw, float serverPitch,
                float targetYaw, float targetPitch) {
            if (!isFinite(rotationSneakYaw) || !isFinite(rotationSneakPitch)) {
                return false;
            }
            boolean sameTarget = !isSignificantNormalIntaveRotation(
                    rotationSneakYaw, rotationSneakPitch,
                    targetYaw, targetPitch);
            boolean stillTurning = isSignificantNormalIntaveRotation(
                    serverYaw, serverPitch,
                    rotationSneakYaw, rotationSneakPitch);
            if (sameTarget && stillTurning) {
                return true;
            }
            clearRotationSneakTarget();
            return false;
        }

        private void clearRotationSneakTarget() {
            rotationSneakYaw = Float.NaN;
            rotationSneakPitch = Float.NaN;
        }

        private float approachYaw(float current, float target, float step) {
            float delta = MathHelper.wrapAngleTo180_float(target - current);
            return current + Math.max(-step, Math.min(step, delta));
        }

        private float approach(float current, float target, float step) {
            return current < target
                    ? Math.min(current + step, target)
                    : Math.max(current - step, target);
        }

        private boolean sameRotation(
                float yaw, float pitch, float targetYaw, float targetPitch) {
            return Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - yaw)) < 0.001F
                    && Math.abs(targetPitch - pitch) < 0.001F;
        }
    }

    static void applyNormalIntaveSneak(MovementInput input) {
        if (input == null) {
            return;
        }
        input.sneak = true;
    }

    @Deprecated
    static void releaseMovementInput(MovementInput input) {
        if (input == null) {
            return;
        }
        input.moveForward = 0.0F;
        input.moveStrafe = 0.0F;
        input.jump = false;
    }

    static boolean hasReleasedNormalIntaveInput(int samples) {
        return samples >= 1;
    }

    static boolean hasReleasedNormalIntaveInput(int samples, int requiredSamples) {
        return requiredSamples > 0 && samples >= requiredSamples;
    }

    static int normalEntryWaitTicks(boolean normalIntaveEnabled) {
        return normalIntaveEnabled ? NORMAL_INTAVE_ENTRY_WAIT_TICKS : 0;
    }

    static boolean isNormalIntavePauseSafe(boolean willFallNextTick) {
        return !willFallNextTick;
    }

    static boolean isSignificantNormalIntaveRotation(
            float fromYaw, float fromPitch,
            float toYaw, float toPitch) {
        if (!isFinite(fromYaw) || !isFinite(fromPitch)
                || !isFinite(toYaw) || !isFinite(toPitch)) {
            return false;
        }
        float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(
                toYaw - fromYaw));
        float pitchDelta = Math.abs(toPitch - fromPitch);
        return yawDelta >= INTAVE_SIGNIFICANT_YAW_DELTA
                || pitchDelta >= INTAVE_SIGNIFICANT_PITCH_DELTA;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private boolean shouldSkipNormalIntaveSneak(
            NormalRotationRequest request) {
        if (request != null && request.placement != null
                && request.placement.data.facing == EnumFacing.UP) {
            return true;
        }
        if (mc.thePlayer == null) {
            return false;
        }
        if (mc.thePlayer.motionY > 0.0D
                || (mc.gameSettings != null
                && Utils.isBindDown(mc.gameSettings.keyBindJump))) {
            return true;
        }
        return mc.thePlayer.onGround
                && isKeepYActive()
                && isNormalInputMoving();
    }

    static boolean shouldPlaceNormalBlock(
            boolean unsupportedNextTick, double motionY) {
        return unsupportedNextTick
                && !Double.isNaN(motionY)
                && motionY < NORMAL_PLACE_MAX_MOTION_Y;
    }

    static boolean shouldArmEnableClutch(
            boolean enabled, boolean validContext,
            boolean onGround, double motionY) {
        return enabled && validContext && !onGround && Double.isFinite(motionY)
                && motionY < 0.0D;
    }

    static float minimalRotationMinPitch() {
        return MINIMAL_MIN_PITCH;
    }

    static float minimalRotationMaxPitch(boolean polar) {
        return polar ? MINIMAL_POLAR_MAX_PITCH : MINIMAL_INTAVE_MAX_PITCH;
    }

    static float updateMinimalRotation(float current, float intended, float factor) {
        float delta = MathHelper.wrapAngleTo180_float(intended - current);
        float limit = Math.max(0.0F, factor);
        return current + Math.max(-limit, Math.min(limit, delta));
    }

    static boolean isPolarDiagonal(float cameraYaw) {
        float normalized = cameraYaw % 360.0F;
        if (normalized < 0.0F) {
            normalized += 360.0F;
        }
        for (float diagonal : new float[]{45.0F, 135.0F, 225.0F, 315.0F}) {
            if (Math.abs(normalized - diagonal) < MINIMAL_POLAR_DIAGONAL_THRESHOLD
                    || Math.abs(normalized - (diagonal + 360.0F))
                    < MINIMAL_POLAR_DIAGONAL_THRESHOLD
                    || Math.abs(normalized - (diagonal - 360.0F))
                    < MINIMAL_POLAR_DIAGONAL_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    static float minimalBaseYaw(float cameraYaw) {
        float diagonalOffset = isPolarDiagonal(cameraYaw)
                ? 180.0F : 135.0F;
        return Math.round((cameraYaw - diagonalOffset) / MINIMAL_POLAR_SECTOR)
                * MINIMAL_POLAR_SECTOR;
    }

    static float normalRotationYawSpeed(float configuredSpeed, boolean instantTurn) {
        return instantTurn ? 180.0F : configuredSpeed;
    }

    static float normalRotationPitchSpeed(float configuredSpeed, boolean instantTurn) {
        return instantTurn ? 180.0F : configuredSpeed / 2.0F;
    }

    static boolean isNormalPlacementImprovement(
            double existingScore, double candidateScore) {
        return !Double.isNaN(existingScore)
                && !Double.isNaN(candidateScore)
                && candidateScore < existingScore;
    }

    static boolean isNormalTowerIntent(
            boolean airborne, boolean keepYActive,
            boolean jumpDown) {
        return airborne && !keepYActive && jumpDown;
    }

    static boolean normalHorizontalFootprintOverlap(
            AxisAlignedBB predicted, BlockPos block) {
        if (predicted == null || block == null) {
            return false;
        }
        return predicted.maxX > block.getX()
                && predicted.minX < block.getX() + 1.0D
                && predicted.maxZ > block.getZ()
                && predicted.minZ < block.getZ() + 1.0D;
    }

    private void ensureCounterRendererRegistered() {
        COUNTER_RENDERER.owner = this;
        if (!counterRendererRegistered) {
            McpRuntime.EVENTS.register(COUNTER_RENDERER);
            counterRendererRegistered = true;
        }
    }

    private void armNormalIntaveEntry() {
        if (isEnabled() && isNormalMode() && normalIntave.isToggled()
                && Utils.nullCheck()) {
            normalIntaveTransition.armEntry();
        }
    }

    private void clearNormalPlacementState(boolean resetDuplicate) {
        pendingPost = null;
        pendingNormalPlacement = null;
        if (resetDuplicate) {
            resetNormalDuplicateState();
        }
    }

    private void resetNormalRotationState() {
        if (isMiauRotation()) {
            return;
        }
        if (mc.thePlayer == null) {
            rotations = new float[]{0.0F, MINIMAL_BASE_PITCH};
            return;
        }
        rotations = new float[]{
                mc.thePlayer.rotationYaw + 180.0F,
                MINIMAL_BASE_PITCH
        };
    }

    private void resetNormalDuplicateState() {
        lastNormalPlacedYawDelta = Float.NaN;
        normalDuplicateNudgeSign = 1;
        resetNormalVulcanPitchState();
    }

    private void resetNormalVulcanPitchState() {
        normalDispatchedPitchInitialized = false;
        normalLastDispatchedPitch = 0.0F;
        normalLastDispatchedPitchDeltaRaw = -1L;
        normalVulcanRotationWindow = 0;
    }

    private boolean isCounterAnimationHidden() {
        if (isTellyBridgeMode()) {
            return getTellyBridgeMode().isCounterHidden();
        }
        return getCounterAnimationMode() == 0
                || blockCounterAnimation.isFinishedHidden(System.currentTimeMillis());
    }

    private int getCounterAnimationMode() {
        return Math.max(0, Math.min(
                COUNTER_ANIMATIONS.length - 1, (int) counterAnimation.getInput()));
    }

    static float getCounterMoveOffset(
            float left, float right, int screenWidth, float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        float center = (left + right) * 0.5F;
        float hiddenDistance = center <= screenWidth * 0.5F ? -right : screenWidth - left;
        return hiddenDistance * (1.0F - clamped);
    }

    static int applyCounterAnimationAlpha(int color, float progress) {
        int alpha = color >>> 24;
        int animatedAlpha = Math.round(alpha * Math.max(0.0F, Math.min(1.0F, progress)));
        return color & 0x00FFFFFF | animatedAlpha << 24;
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        if (isTellyBridgeMode()) {
            return;
        }
        if (enableClutchRotationActive || yieldingToAntiFireball) {
            pendingPost = null;
            pendingNormalPlacement = null;
            return;
        }
        if (isTelly2Mode()) {
            pendingPost = null;
            pendingNormalPlacement = null;
            return;
        }
        if (isOriginalTellyMode()) {
            pendingPost = null;
            pendingNormalPlacement = null;
            return;
        }
        if (!Utils.nullCheck()) {
            pendingPost = null;
            pendingNormalPlacement = null;
            return;
        }
        if (pendingPost == null) {
            pendingNormalPlacement = null;
            return;
        }
        EventSilentRotation.Post settled = pendingPost;
        NormalPlacementPlan placement = pendingNormalPlacement;
        pendingPost = null;
        pendingNormalPlacement = null;
        placePost(settled, placement);
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPreMotion(PreMotionEvent event) {
        if (isTellyBridgeMode()) {
            getTellyBridgeMode().onPreMotion();
            return;
        }
        if (enableClutchRotationActive) {
            Float yaw = RotationHelper.get().getServerYaw();
            Float pitch = RotationHelper.get().getServerPitch();
            if (yaw != null && pitch != null
                    && isFinite(yaw) && isFinite(pitch)) {
                event.setRotations(yaw, pitch);
            }
            return;
        }
        if (yieldingToAntiFireball) {
            return;
        }
        if (isOriginalTellyMode()) {
            tellyMode.onPreMotion(event);
        }
        if (mc.thePlayer != null && (RotationHelper.get().isRotationOwner(this)
                || isOriginalTellyMode() && tellyMode.hasPlannedRotation())) {
            limitScaffoldMotionPitch(event, RotationUtils.serverRotations[1], RotationUtils.getGCD());
        }
    }

    @Subscribe
    public void onPostMotion(PostMotionEvent event) {
        if (!enableClutchRotationActive || pendingEnableClutch == null
                || !Utils.nullCheck()
                || !RotationHelper.get().isRotationOwner(this)) {
            return;
        }
        NormalPlacementPlan placement = pendingEnableClutch;
        pendingEnableClutch = null;
        float yaw = RotationUtils.serverRotations[0];
        float pitch = RotationUtils.serverRotations[1];
        MovingObjectPosition hit = rayTrace(yaw, pitch);
        if (placeEnableClutch(placement, hit)) {
            enableClutchTicks = 0;
        }
    }

    private NormalFaceRotation resolveNormalFaceRotation(
            BlockPos blockPos, EnumFacing facing,
            float profileYaw, float profilePitch) {
        if (isMiauRotation()) {
            float[] rotation = getRotationsForFace(
                    blockPos, facing, profileYaw);
            MovingObjectPosition hit = rotation == null
                    ? null : rayTrace(rotation[0], rotation[1]);
            if (!matchesPlacement(hit, blockPos, facing)) {
                rotation = getFreeRotationsForFace(blockPos, facing);
                hit = rotation == null
                        ? null : rayTrace(rotation[0], rotation[1]);
            }
            return matchesPlacement(hit, blockPos, facing)
                    ? new NormalFaceRotation(rotation[0], rotation[1], false)
                    : null;
        }
        if (isMinimalRotation()) {
            PitchProbe polarPitch = findMinimalPitch(
                    blockPos, facing, profileYaw, profilePitch,
                    MINIMAL_POLAR_MAX_PITCH);
            return polarPitch.strict
                    ? new NormalFaceRotation(
                    profileYaw, polarPitch.pitch, false)
                    : null;
        }

        float yaw = profileYaw;
        PitchProbe intavePitch = findMinimalPitch(
                blockPos, facing, yaw, profilePitch,
                MINIMAL_INTAVE_MAX_PITCH);
        if (intavePitch.strict) {
            return new NormalFaceRotation(yaw, intavePitch.pitch, true);
        }
        if (hitsBlock(blockPos, rayTrace(yaw, intavePitch.pitch))) {
            return null;
        }

        float targetYaw = directionToFace(blockPos, facing)[0];
        for (int attempt = 0; attempt <= MINIMAL_INTAVE_YAW_ATTEMPTS; attempt++) {
            float step = MINIMAL_INTAVE_MIN_YAW_STEP
                    + ThreadLocalRandom.current().nextFloat()
                    * MINIMAL_INTAVE_YAW_STEP_VARIANCE;
            yaw = updateMinimalRotation(yaw, targetYaw, step);
            if (!canMinimalYawReachFace(
                    blockPos, facing, yaw,
                    MINIMAL_INTAVE_MAX_PITCH)) {
                continue;
            }
            intavePitch = findMinimalPitch(
                    blockPos, facing, yaw, intavePitch.pitch,
                    MINIMAL_INTAVE_MAX_PITCH);
            if (intavePitch.strict) {
                return new NormalFaceRotation(yaw, intavePitch.pitch, true);
            }
            if (Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - yaw))
                    < MINIMAL_INTAVE_MIN_YAW_STEP) {
                break;
            }
        }
        return null;
    }

    private PitchProbe findMinimalPitch(
            BlockPos blockPos, EnumFacing facing,
            float yaw, float lastPitch, float maxPitch) {
        MovingObjectPosition lastHit = rayTrace(yaw, lastPitch);
        if (matchesPlacement(lastHit, blockPos, facing)) {
            return new PitchProbe(lastPitch, true);
        }
        if (!canMinimalYawReachFace(blockPos, facing, yaw, maxPitch)) {
            return new PitchProbe(lastPitch, false);
        }
        float pitch = MINIMAL_MIN_PITCH;
        while (pitch <= maxPitch + 0.0001F) {
            if (matchesPlacement(rayTrace(yaw, pitch), blockPos, facing)) {
                return new PitchProbe(pitch, true);
            }
            pitch += 0.1F;
        }
        return new PitchProbe(lastPitch, false);
    }

    private boolean canMinimalYawReachFace(
            BlockPos blockPos, EnumFacing facing,
            float yaw, float maxPitch) {
        if (facing == EnumFacing.UP || facing == EnumFacing.DOWN) {
            float[] candidate = getRotationsForFace(
                    blockPos, facing, yaw, MINIMAL_BASE_PITCH);
            return candidate != null
                    && candidate[1] >= MINIMAL_MIN_PITCH - 0.2F
                    && candidate[1] <= maxPitch + 0.2F;
        }

        EntityPlayerSP player = mc.thePlayer;
        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;
        double yawRadians = Math.toRadians(yaw);
        double horizontalX = -Math.sin(yawRadians);
        double horizontalZ = Math.cos(yawRadians);
        double horizontalDistance;
        if (facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH) {
            double planeZ = blockPos.getZ()
                    + (facing == EnumFacing.SOUTH ? 1.0D : 0.0D);
            if (Math.abs(horizontalZ) < 1.0E-6D) {
                return false;
            }
            horizontalDistance = (planeZ - eyeZ) / horizontalZ;
            double hitX = eyeX + horizontalX * horizontalDistance;
            if (hitX < blockPos.getX() || hitX > blockPos.getX() + 1.0D) {
                return false;
            }
        }
        else {
            double planeX = blockPos.getX()
                    + (facing == EnumFacing.EAST ? 1.0D : 0.0D);
            if (Math.abs(horizontalX) < 1.0E-6D) {
                return false;
            }
            horizontalDistance = (planeX - eyeX) / horizontalX;
            double hitZ = eyeZ + horizontalZ * horizontalDistance;
            if (hitZ < blockPos.getZ() || hitZ > blockPos.getZ() + 1.0D) {
                return false;
            }
        }
        if (horizontalDistance <= 0.0D
                || horizontalDistance > 4.5D) {
            return false;
        }

        double minPitch = Math.toDegrees(Math.atan(
                (eyeY - (blockPos.getY() + 1.0D)) / horizontalDistance));
        double maxPossiblePitch = Math.toDegrees(Math.atan(
                (eyeY - blockPos.getY()) / horizontalDistance));
        double lower = Math.min(minPitch, maxPossiblePitch);
        double upper = Math.max(minPitch, maxPossiblePitch);
        return upper >= MINIMAL_MIN_PITCH - 0.2D
                && lower <= maxPitch + 0.2D;
    }

    private boolean hitsBlock(
            BlockPos blockPos, MovingObjectPosition hit) {
        return hit != null
                && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && blockPos != null && blockPos.equals(hit.getBlockPos());
    }

    private float[] directionToFace(BlockPos blockPos, EnumFacing facing) {
        EntityPlayerSP player = mc.thePlayer;
        double faceX = blockPos.getX() + 0.5D
                + facing.getFrontOffsetX() * 0.5D;
        double faceY = blockPos.getY() + 0.5D
                + facing.getFrontOffsetY() * 0.5D;
        double faceZ = blockPos.getZ() + 0.5D
                + facing.getFrontOffsetZ() * 0.5D;
        double dx = faceX - player.posX;
        double dy = faceY - (player.posY + player.getEyeHeight());
        double dz = faceZ - player.posZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(
                Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new float[]{yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    private static final class PitchProbe {
        private final float pitch;
        private final boolean strict;

        private PitchProbe(float pitch, boolean strict) {
            this.pitch = pitch;
            this.strict = strict;
        }
    }

    private static final class NormalFaceRotation {
        private final float yaw;
        private final float pitch;
        private final boolean instantTurn;

        private NormalFaceRotation(float yaw, float pitch, boolean instantTurn) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.instantTurn = instantTurn;
        }
    }

    private boolean prepareEnableClutch(ClientRotationEvent event) {
        if (!clutch.isToggled()) {
            clearEnableClutch();
            return false;
        }
        if (enableClutchTicks <= 0) {
            return false;
        }
        if (!shouldArmEnableClutch(true, true,
                mc.thePlayer.onGround, mc.thePlayer.motionY)) {
            clearEnableClutch();
            return false;
        }
        enableClutchTicks--;
        if (!ensurePlacementBlock()) {
            return false;
        }
        NormalPlacementPlan placement = findEnableClutchPlan();
        if (placement == null || !event.trySetRotation(
                this, RotationPriority.BLOCK_PLACEMENT,
                placement.yaw, placement.pitch, true)) {
            return false;
        }
        event.setRotationAlreadyQuantized(true);
        event.setMovementYawCorrection(true);
        pendingEnableClutch = placement;
        enableClutchRotationActive = true;
        return true;
    }

    private NormalPlacementPlan findEnableClutchPlan() {
        Item item = keyBlock();
        if (!(item instanceof ItemBlock)) {
            return null;
        }
        ItemBlock block = (ItemBlock) item;
        AxisAlignedBB current = mc.thePlayer.getEntityBoundingBox();
        AxisAlignedBB predicted = ScaffoldUtil.getPredictedBoundingBox(1.0D);
        AxisAlignedBB path = current.union(predicted);
        int minX = MathHelper.floor_double(path.minX + 1.0E-6D);
        int maxX = MathHelper.floor_double(path.maxX - 1.0E-6D);
        int minZ = MathHelper.floor_double(path.minZ + 1.0E-6D);
        int maxZ = MathHelper.floor_double(path.maxZ - 1.0E-6D);
        int topY = MathHelper.floor_double(path.minY) - 1;
        EnumFacing[] faces = {
                EnumFacing.UP, EnumFacing.DOWN,
                EnumFacing.NORTH, EnumFacing.SOUTH,
                EnumFacing.WEST, EnumFacing.EAST
        };
        NormalPlacementPlan best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double centerX = (path.minX + path.maxX) * 0.5D;
        double centerZ = (path.minZ + path.maxZ) * 0.5D;
        for (int layer = 0; layer >= -2; layer--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos destination = new BlockPos(x, topY + layer, z);
                    if (!isReplaceable(destination)
                            || !normalHorizontalFootprintOverlap(path, destination)) {
                        continue;
                    }
                    for (EnumFacing face : faces) {
                        BlockPos support = destination.offset(face.getOpposite());
                        if (mc.theWorld.getBlockState(support).getBlock()
                                .getMaterial() == Material.air
                                || !block.canPlaceBlockOnSide(
                                mc.theWorld, support, face, mc.thePlayer,
                                mc.thePlayer.getHeldItem())) {
                            continue;
                        }
                        float[] aim = getFreeRotationsForFace(
                                support, face,
                                RotationUtils.serverRotations[0],
                                RotationUtils.serverRotations[1]);
                        MovingObjectPosition hit = rayTrace(aim[0], aim[1]);
                        if (!matchesPlacement(hit, support, face)) {
                            continue;
                        }
                        double dx = destination.getX() + 0.5D - centerX;
                        double dz = destination.getZ() + 0.5D - centerZ;
                        double score = -layer * 16.0D + dx * dx + dz * dz;
                        if (score < bestScore) {
                            bestScore = score;
                            best = new NormalPlacementPlan(
                                    new BlockData(support, face),
                                    aim[0], aim[1], true);
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean placeEnableClutch(
            NormalPlacementPlan placement, MovingObjectPosition hit) {
        if (placement == null || !matchesPlacement(hit, placement.data)
                || mc.playerController == null) {
            return false;
        }
        Item item = keyBlock();
        if (!(item instanceof ItemBlock)) {
            return false;
        }
        BlockPos destination = placement.data.position.offset(
                placement.data.facing);
        ItemBlock block = (ItemBlock) item;
        if (!isReplaceable(destination)
                || mc.theWorld.getBlockState(placement.data.position)
                .getBlock().getMaterial() == Material.air
                || !block.canPlaceBlockOnSide(
                mc.theWorld, placement.data.position,
                placement.data.facing, mc.thePlayer,
                mc.thePlayer.getHeldItem())) {
            return false;
        }
        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld,
                mc.thePlayer.inventory.getCurrentItem(),
                placement.data.position, placement.data.facing, hit.hitVec);
        if (placed) {
            swingArm();
            blockRenderFeature.markPlaced(destination);
            blocksSinceEagle++;
            scaffoldRotationGuardTicks = VULCAN_SCAFFOLD_ROTATION_WINDOW;
        }
        return placed;
    }

    private void clearEnableClutch() {
        enableClutchTicks = 0;
        pendingEnableClutch = null;
        enableClutchRotationActive = false;
    }

    private void updateEnableClutchSetting() {
        clearEnableClutch();
        if (isEnabled()) {
            armEnableClutch();
        }
    }

    private void armEnableClutch() {
        enableClutchTicks = shouldArmEnableClutch(
                clutch.isToggled(), Utils.nullCheck(),
                mc.thePlayer != null && mc.thePlayer.onGround,
                mc.thePlayer == null ? 0.0D : mc.thePlayer.motionY)
                ? ENABLE_CLUTCH_TICKS : 0;
        if (enableClutchTicks > 0) {
            ensurePlacementBlock();
        }
    }

    public boolean isYieldingToAntiFireball() {
        return yieldingToAntiFireball;
    }

    private boolean isImmediatePlacementRequired() {
        if (!Utils.nullCheck() || !Double.isFinite(mc.thePlayer.motionY)
                || mc.thePlayer.motionY >= NORMAL_PLACE_MAX_MOTION_Y) {
            return false;
        }
        AxisAlignedBB predicted = mc.thePlayer.getEntityBoundingBox().offset(
                mc.thePlayer.motionX, 0.0D, mc.thePlayer.motionZ);
        return mc.theWorld.getCollidingBoundingBoxes(
                mc.thePlayer, predicted.offset(0.0D, -0.5D, 0.0D)).isEmpty();
    }

    private NormalRotationRequest prepareNormalRotation() {
        AxisAlignedBB predicted = ScaffoldUtil.getPredictedBoundingBox(1.0D);
        boolean willFall = mc.theWorld.getCollidingBoundingBoxes(
                mc.thePlayer, predicted.offset(0.0D, -0.5D, 0.0D)).isEmpty();
        float placementYaw = getNormalProfileYaw();
        Item item = keyBlock();
        NormalPlacementPlan placement = item instanceof ItemBlock
                ? findBestPlacementPlan(placementYaw, predicted) : null;
        float speed = (float) rotationSpeed.getRandomInRange();
        float targetYaw = getNormalIdleYaw(placementYaw);
        float targetPitch = getNormalProfilePitch();
        if (placement != null) {
            rotations = new float[]{placement.yaw, placement.pitch};
            targetYaw = placement.yaw;
            targetPitch = placement.pitch;
        }
        float yawSpeed = isMiauRotation()
                ? speed : normalRotationYawSpeed(
                speed, placement != null && placement.instantTurn);
        float pitchSpeed = isMiauRotation()
                ? speed : normalRotationPitchSpeed(
                speed, placement != null && placement.instantTurn);
        return new NormalRotationRequest(
                targetYaw, targetPitch, yawSpeed, pitchSpeed, placement,
                shouldPlaceNormalBlock(willFall, mc.thePlayer.motionY));
    }

    private static final class NormalRotationRequest {
        private final float yaw;
        private final float pitch;
        private final float yawSpeed;
        private final float pitchSpeed;
        private final NormalPlacementPlan placement;
        private final boolean placeNow;

        private NormalRotationRequest(
                float yaw, float pitch, float yawSpeed, float pitchSpeed,
                NormalPlacementPlan placement, boolean placeNow) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.yawSpeed = yawSpeed;
            this.pitchSpeed = pitchSpeed;
            this.placement = placement;
            this.placeNow = placeNow;
        }

        private void apply(EventSilentRotation event) {
            event.setYawSpeed(yawSpeed);
            event.setPitchSpeed(pitchSpeed);
            event.setMaxYawDelta(MAX_SCAFFOLD_YAW_DELTA);
            event.setYaw(yaw);
            event.setPitch(pitch);
        }
    }

    private static final class NormalPlacementPlan {
        private final BlockData data;
        private final float yaw;
        private final float pitch;
        private final boolean instantTurn;

        private NormalPlacementPlan(
                BlockData data, float yaw, float pitch,
                boolean instantTurn) {
            this.data = data;
            this.yaw = yaw;
            this.pitch = pitch;
            this.instantTurn = instantTurn;
        }
    }

    private void placePost(
            EventSilentRotation.Post event, NormalPlacementPlan placement) {
        boolean placed = false;
        Item item = keyBlock();
        if (item instanceof ItemBlock && placement != null
                && isNormalPlacementStillRequired(placement)) {
            ItemBlock itemBlock = (ItemBlock) item;
            Float emittedYaw = RotationHelper.get().getServerYaw();
            Float emittedPitch = RotationHelper.get().getServerPitch();
            float finalYaw = emittedYaw == null || !isFinite(emittedYaw)
                    ? event.getYaw() : emittedYaw;
            float finalPitch = emittedPitch == null || !isFinite(emittedPitch)
                    ? event.getPitch() : emittedPitch;
            MovingObjectPosition hit = rayTrace(finalYaw, finalPitch);
            if (matchesPlacement(hit, placement.data)
                    && hit.sideHit != EnumFacing.DOWN
                    && (!isKeepYActive() || hit.sideHit != EnumFacing.UP)
                    && itemBlock.canPlaceBlockOnSide(
                    mc.theWorld, hit.getBlockPos(), hit.sideHit,
                    mc.thePlayer, mc.thePlayer.getHeldItem())) {
                placed = placeBlock(
                        placement.data, hit,
                        RotationUtils.serverRotations[0], finalYaw);
            }
        }
        updateShift(placed);
    }

    private boolean isNormalPlacementStillRequired(
            NormalPlacementPlan planned) {
        if (planned == null || !Utils.nullCheck()
                || mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        AxisAlignedBB predicted = ScaffoldUtil.getPredictedBoundingBox(1.0D);
        boolean unsupported = mc.theWorld.getCollidingBoundingBoxes(
                mc.thePlayer, predicted.offset(0.0D, -0.5D, 0.0D)).isEmpty();
        if (!shouldPlaceNormalBlock(unsupported, mc.thePlayer.motionY)) {
            return false;
        }

        BlockPos destination = planned.data.position.offset(planned.data.facing);
        IBlockState destinationState = mc.theWorld.getBlockState(destination);
        AxisAlignedBB placementPath = mc.thePlayer.getEntityBoundingBox().union(predicted);
        if (!destinationState.getBlock().isReplaceable(mc.theWorld, destination)
                || !normalHorizontalFootprintOverlap(placementPath, destination)) {
            return false;
        }

        NormalPlacementPlan refreshed = findBestPlacementPlan(
                getNormalProfileYaw(), predicted);
        return refreshed != null
                && refreshed.data.position.offset(refreshed.data.facing)
                .equals(destination);
    }

    private float[] diversifyNormalPlacementRotation(
            NormalPlacementPlan placement,
            float requestedYaw, float requestedPitch) {
        float previousYaw = RotationUtils.serverRotations[0];
        float previousPitch = RotationUtils.serverRotations[1];
        float[] projected = projectNormalRotation(
                previousYaw, previousPitch, requestedYaw, requestedPitch);
        if (!matchesPlacement(
                rayTrace(projected[0], projected[1]), placement.data)) {
            return new float[]{requestedYaw, requestedPitch};
        }

        float candidate = diversifyNormalPlacementYaw(
                previousYaw, projected[0], lastNormalPlacedYawDelta,
                RotationUtils.getGCD(), MAX_SCAFFOLD_YAW_DELTA,
                normalDuplicateNudgeSign);
        if (Float.compare(candidate, projected[0]) == 0) {
            return new float[]{requestedYaw, requestedPitch};
        }

        for (int sign : new int[]{
                normalDuplicateNudgeSign, -normalDuplicateNudgeSign}) {
            candidate = diversifyNormalPlacementYaw(
                    previousYaw, projected[0], lastNormalPlacedYawDelta,
                    RotationUtils.getGCD(), MAX_SCAFFOLD_YAW_DELTA, sign);
            if (Float.compare(candidate, projected[0]) == 0
                    || !matchesPlacement(
                    rayTrace(candidate, projected[1]), placement.data)) {
                continue;
            }
            normalDuplicateNudgeSign = -normalDuplicateNudgeSign;
            SILENT_ROTATIONS.synchronizeEmitted(candidate, projected[1]);
            return new float[]{candidate, projected[1]};
        }

        return new float[]{requestedYaw, requestedPitch};
    }

    private float safeguardNormalVulcanPitch(
            float previousPitch, float yaw, float requestedPitch,
            BlockData placement) {
        if (!normalDispatchedPitchInitialized
                || normalLastDispatchedPitchDeltaRaw <= 0L
                || !isFinite(previousPitch) || !isFinite(requestedPitch)) {
            return requestedPitch;
        }
        float requestedDelta = requestedPitch - previousPitch;
        if (!ScaffoldTelly2Mode.isVulcanScaffoldDUnsafe(
                requestedDelta, normalLastDispatchedPitchDeltaRaw)) {
            return requestedPitch;
        }

        float step = Math.abs(RotationUtils.getGCD());
        if (!isFinite(step) || step <= 0.0001F) {
            return requestedPitch;
        }
        int requestedSteps = Math.max(1, Math.round(
                Math.abs(requestedDelta) / step));
        int direction = requestedDelta < 0.0F ? -1 : 1;
        MovingObjectPosition originalHit = placement == null
                ? null : rayTrace(yaw, requestedPitch);
        boolean originalOwnsPlacement = placement == null
                || matchesPlacement(originalHit, placement);

        for (int distance = 0; distance <= 64; distance++) {
            int lower = requestedSteps - distance;
            int upper = requestedSteps + distance;
            int[] candidates = distance == 0
                    ? new int[]{requestedSteps}
                    : new int[]{lower, upper};
            for (int candidateSteps : candidates) {
                if (candidateSteps <= 0) {
                    continue;
                }
                float candidatePitch = MathHelper.clamp_float(
                        previousPitch + direction * candidateSteps * step,
                        -90.0F, 90.0F);
                float candidateDelta = candidatePitch - previousPitch;
                if (!isFinite(candidatePitch)
                        || !withinScaffoldPitchBudget(candidatePitch, previousPitch)
                        || Math.abs(candidateDelta) <= 0.15F
                        || ScaffoldTelly2Mode.isVulcanScaffoldDUnsafe(
                        candidateDelta, normalLastDispatchedPitchDeltaRaw)) {
                    continue;
                }
                if (originalOwnsPlacement && placement != null
                        && !matchesPlacement(
                        rayTrace(yaw, candidatePitch), placement)) {
                    continue;
                }
                return candidatePitch;
            }
        }
        return requestedPitch;
    }

    private static float[] projectNormalRotation(
            float previousYaw, float previousPitch,
            float requestedYaw, float requestedPitch) {
        float gcd = Math.abs(RotationUtils.getGCD());
        float limit = SilentRotationManager.gcdFlooredLimit(
                MAX_SCAFFOLD_YAW_DELTA, gcd);
        float delta = MathHelper.wrapAngleTo180_float(
                requestedYaw - previousYaw);
        float cappedYaw = previousYaw
                + SilentRotationManager.clampYawDelta(delta, limit);
        return RotationUtils.fixRotation(
                cappedYaw, requestedPitch, previousYaw, previousPitch);
    }

    static float diversifyNormalPlacementYaw(
            float previousYaw, float proposedYaw,
            float lastPlacedYawDelta, float gcd,
            float maxYawDelta, int preferredSign) {
        if (!isFinite(previousYaw) || !isFinite(proposedYaw)
                || !isFinite(lastPlacedYawDelta)) {
            return proposedYaw;
        }
        float proposedDelta = Math.abs(proposedYaw - previousYaw);
        if (!isDuplicateNormalPlacementDelta(
                proposedDelta, lastPlacedYawDelta)) {
            return proposedYaw;
        }

        float step = Math.abs(gcd);
        if (!isFinite(step) || step <= 0.0F) {
            return proposedYaw;
        }
        float limit = SilentRotationManager.gcdFlooredLimit(
                maxYawDelta, step);
        int firstSign = preferredSign < 0 ? -1 : 1;
        for (int sign : new int[]{firstSign, -firstSign}) {
            float candidate = proposedYaw + sign * step;
            float candidateDelta = Math.abs(candidate - previousYaw);
            if (candidateDelta > limit
                    || candidateDelta > 2.0F
                    && Math.abs(candidateDelta - lastPlacedYawDelta) < 0.0001F) {
                continue;
            }
            return candidate;
        }
        return proposedYaw;
    }

    private static boolean isDuplicateNormalPlacementDelta(
            float proposedDelta, float lastPlacedDelta) {
        return isFinite(proposedDelta) && isFinite(lastPlacedDelta)
                && proposedDelta > 2.0F
                && Math.abs(proposedDelta - lastPlacedDelta) < 0.0001F;
    }

    @Subscribe
    public void onPrePlayerInput(PrePlayerInputEvent event) {
        if (isTellyBridgeMode()) {
            return;
        }
        if (event == null || mc == null || mc.thePlayer == null || mc.gameSettings == null) {
            sneakSafety.reset();
            return;
        }
        boolean physicalSneak = mc.gameSettings != null
                && Utils.isBindDown(mc.gameSettings.keyBindSneak);
        sneakSafety.beginInput(getInfo() + (physicalSneak ? "/physical Shift"
                : event.isSneak() ? "/pre-existing input" : "/none"),
                event.isSneak(), mc.thePlayer.onGround);
        if (hasExternalRotationWindow()) {
            return;
        }
        if (isTellyMode()) {
            sneakSafety.continueOwnedPulse(mc.thePlayer, mc.theWorld, event, physicalSneak);
        }
        if (isOriginalTellyMode()) {
            tellyMode.onPrePlayerInput(
                    event, isTellyMidCycleSneakEnabled(),
                    getTellySneakEveryCycle());
        }
        else if (isTelly2Mode()) {
            telly2Mode.onPrePlayerInput(
                    event, safeWalk.isToggled(),
                    isTellyMidCycleSneakEnabled(),
                    getTellySneakEveryCycle());
        }
    }

    @Subscribe
    public void onMovementInput(EventMovementInput event) {
        if (isTellyBridgeMode()) {
            return;
        }
        if (hasExternalRotationWindow()) {
            return;
        }
        if (isOriginalTellyMode()) {
            tellyMode.applyMovementInput(event);
            return;
        }
        if (isTelly2Mode()) {
            telly2Mode.applyMovementInput(event);
            return;
        }
        SILENT_ROTATIONS.applyMovementInput(event, getMovementYaw());
    }

    @Subscribe
    public void onPostMovementInput(PostPlayerInputEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (isTellyBridgeMode()) {
            return;
        }
        updateKeepYState();
        boolean jumpPressed = mc.thePlayer.movementInput.jump
                || consumePhysicalJumpTap();
        EventMovementInput movement = new EventMovementInput(
                mc.thePlayer.movementInput.moveForward,
                mc.thePlayer.movementInput.moveStrafe,
                jumpPressed);
        McpRuntime.EVENTS.post(movement);
        boolean externalRotation = hasExternalRotationWindow();
        if (!externalRotation && isTellyMode() && sneakSafety.isCoastingInput()
                && !mc.thePlayer.movementInput.sneak) {
            movement.setSpeed(0.0F);
            movement.setStrafe(0.0F);
        }
        if (!externalRotation && isOriginalTellyMode()) {
            tellyMode.onFinalizedMovementInput(movement);
        }
        boolean tellyJump = !externalRotation && isOriginalTellyMode()
                && tellyMode.consumeJump(movement.isJumping());
        if (movement.isCanceled()) {
            mc.thePlayer.movementInput.moveForward = 0.0F;
            mc.thePlayer.movementInput.moveStrafe = 0.0F;
        }
        else {
            mc.thePlayer.movementInput.moveForward = movement.getSpeed();
            mc.thePlayer.movementInput.moveStrafe = movement.getStrafe();
            if (shouldAutoJumpKeepY(movement)) {
                movement.setJump(true);
            }
            if (!externalRotation && isOriginalTellyMode()) {
                movement.setJump(tellyJump);
            }
            mc.thePlayer.movementInput.jump = movement.isJumping();
        }

    }

    @Subscribe(priority = Priority.LOWEST)
    public void onTellyPostMovementInput(PostPlayerInputEvent event) {
        if (isTellyBridgeMode() && Utils.nullCheck()) {
            getTellyBridgeMode().onPostPlayerInput();
        }
    }

    private boolean consumePhysicalJumpTap() {
        return mc.inGameHasFocus && mc.currentScreen == null
                && mc.gameSettings != null
                && mc.gameSettings.keyBindJump.isPressed();
    }

    @Subscribe
    public void onPostSprintUpdate(PostSprintUpdateEvent event) {
        if (isTellyBridgeMode()) {
            getTellyBridgeMode().onPostSprintUpdate();
            return;
        }
        if (hasExternalRotationWindow()) {
            if ((int) sprintMode.getInput() == OFF_SPRINT
                    || !canSprint()
                    || !canSprintWithServerRelativeInput(
                    mc.thePlayer.movementInput.moveForward)) {
                mc.thePlayer.setSprinting(false);
            }
            return;
        }
        if (isOriginalTellyMode()) {
            if ((int) sprintMode.getInput() == OFF_SPRINT
                    || !tellyMode.canSprintWithFrame()) {
                mc.thePlayer.setSprinting(false);
            }
            else if (canSprint()) {
                mc.thePlayer.setSprinting(true);
            }
            tellyMode.onFinalSprintState(mc.thePlayer.isSprinting());
            return;
        }
        if (isTelly2Mode()) {
            if ((int) sprintMode.getInput() == OFF_SPRINT
                    || !canSprint()
                    || !canSprintWithServerRelativeInput(
                    mc.thePlayer.movementInput.moveForward)) {
                mc.thePlayer.setSprinting(false);
            }
            else {
                mc.thePlayer.setSprinting(true);
            }
            return;
        }
        if ((int) sprintMode.getInput() == OFF_SPRINT) {
            mc.thePlayer.setSprinting(false);
            return;
        }
        if (!canSprint()) {
            return;
        }
        if (canSprintWithServerRelativeInput(mc.thePlayer.movementInput.moveForward)) {
            mc.thePlayer.setSprinting(true);
        }
        else {
            mc.thePlayer.setSprinting(false);
        }
    }

    @Subscribe
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        if (hasExternalRotationWindow()) {
            return;
        }
        if (isTelly2Mode()) {
            telly2Mode.onClick();
        }
    }

    @Subscribe
    public void onRightClick(RightClickMouseEvent event) {
        if (isTellyBridgeMode() && getTellyBridgeMode().isActive()) {
            event.setCanceled(true);
            return;
        }
        if (isNormalMode() && keepY.isToggled() && rmbOnly.isToggled()) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onMouse(MouseEvent event) {
        if (isTellyBridgeMode() && getTellyBridgeMode().isActive() && event.buttonstate) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onStrafe(StrafeEvent event) {
        if (isTellyBridgeMode()) {
            if (getTellyBridgeMode().isActive() && RotationHelper.get().isRotationOwner(this)) {
                event.setYaw(getTellyBridgeMode().getMovementYaw());
            }
            return;
        }
        if (hasExternalRotationWindow()) {
            return;
        }
        if (isOriginalTellyMode()) {
            tellyMode.applyStrafe(event);
            return;
        }
        if (isTelly2Mode()) {
            telly2Mode.applyStrafe(event);
            return;
        }
        SILENT_ROTATIONS.applyStrafe(event, getMovementYaw());
    }

    @Subscribe
    public void onJump(JumpEvent event) {
        if (isTellyBridgeMode()) {
            if (getTellyBridgeMode().isActive() && RotationHelper.get().isRotationOwner(this)) {
                event.setYaw(getTellyBridgeMode().getMovementYaw());
            }
            return;
        }
        if (hasExternalRotationWindow()) {
            return;
        }
        if (isOriginalTellyMode()) {
            tellyMode.applyJump(event);
            return;
        }
        if (isTelly2Mode()) {
            telly2Mode.applyJump(event);
            return;
        }
        SILENT_ROTATIONS.applyJump(event, getMovementYaw());
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        telly4Mode.reset();
        telly3Mode.reset();
        sneakSafety.reset();
        placementSafety.reset();
        normalIntaveTransition.cancel(true);
        SilentHotbar.INSTANCE.resetSlot(this, false);
        clearItemSwitchState();
        clearNormalPlacementState(true);
        clearEnableClutch();
        scaffoldRotationGuardTicks = 0;
        yieldingToAntiFireball = false;
        resetEagleState();
        resetKeepYState();
        tellyMode.reset();
        telly2Mode.reset();
        setShift(false);
        blockRenderFeature.clear();
        SILENT_ROTATIONS.clear();
    }

    @Subscribe(priority = Priority.LOWEST, receiveCanceled = true)
    public void onSendPacket(SendPacketEvent event) {
        if (!hasExternalRotationWindow() && isOriginalTellyMode()) {
            tellyMode.onPacketSent(event);
        }
    }

    @Subscribe
    public void onDispatchPacket(DispatchPacketEvent event) {
        telly4Mode.observePacket(event.getPacket());
        telly3Mode.observePacket(event.getPacket());
        placementSafety.observe(event.getPacket());
        if (event.getPacket() instanceof C03PacketPlayer
                && ((C03PacketPlayer) event.getPacket()).getRotating()
                && scaffoldRotationGuardTicks > 0) {
            scaffoldRotationGuardTicks--;
        }
        if (mc.thePlayer != null) {
            String context = event.getPacket() instanceof C0BPacketEntityAction ? "mode=" + getInfo()
                    + " sameY=" + isTelly2SameYEnabled()
                    + " pulse=" + isTellyMidCycleSneakEnabled()
                    + " safeWalk=" + safeWalk.isToggled()
                    + " ground=" + mc.thePlayer.onGround
                    + " motionY=" + mc.thePlayer.motionY
                    + " motionXZ=" + Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ)
                    + " airFactor=" + mc.thePlayer.jumpMovementFactor
                    + " deferredRelease=" + sneakSafety.isHoldingRelease()
                    + " inputSneak=" + (mc.thePlayer.movementInput != null
                    && mc.thePlayer.movementInput.sneak)
                    + " forward=" + (mc.thePlayer.movementInput == null ? 0 : mc.thePlayer.movementInput.moveForward)
                    + " strafe=" + (mc.thePlayer.movementInput == null ? 0 : mc.thePlayer.movementInput.moveStrafe)
                    + " jump=" + (mc.thePlayer.movementInput != null
                    && mc.thePlayer.movementInput.jump) : "";
            sneakSafety.observe(event.getPacket(), context);
        }
        if (!hasExternalRotationWindow() && isOriginalTellyMode()) {
            tellyMode.onPacketDispatched(event.getPacket());
        }
        else if (!hasExternalRotationWindow() && isTelly2Mode()) {
            telly2Mode.onPacketDispatched(event.getPacket());
        }
        else if (isNormalMode()) {
            onNormalPacketDispatched(event.getPacket());
        }
    }

    private void onNormalPacketDispatched(Packet<?> packet) {
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            normalVulcanRotationWindow = VULCAN_SCAFFOLD_ROTATION_WINDOW;
            return;
        }
        if (!(packet instanceof C03PacketPlayer)
                || !((C03PacketPlayer) packet).getRotating()) {
            return;
        }
        C03PacketPlayer movement = (C03PacketPlayer) packet;
        float pitch = movement.getPitch();
        if (normalDispatchedPitchInitialized) {
            normalLastDispatchedPitchDeltaRaw =
                    ScaffoldTelly2Mode.expandedPitchDelta(
                            pitch - normalLastDispatchedPitch);
        }
        else {
            normalDispatchedPitchInitialized = true;
            normalLastDispatchedPitchDeltaRaw = -1L;
        }
        normalLastDispatchedPitch = pitch;
        if (normalVulcanRotationWindow > 0) {
            normalVulcanRotationWindow--;
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            sneakSafety.reset();
            placementSafety.reset();
        }
        if (isOriginalTellyMode()) {
            tellyMode.onPacketReceived(event.getPacket());
        }
        if (isTellyMode() && event.getPacket()
                instanceof S08PacketPlayerPosLook) {
            if (isTelly2Mode()) {
                telly2Mode.reset();
            }
            SILENT_ROTATIONS.synchronize();
        }
    }

    @Subscribe
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.entity != mc.thePlayer) {
            return;
        }
        telly4Mode.reset();
        telly3Mode.reset();
        sneakSafety.reset();
        clearNormalPlacementState(true);
        clearEnableClutch();
        scaffoldRotationGuardTicks = 0;
        yieldingToAntiFireball = false;
        placementSafety.reset();
        if (isTellyMode()) {
            tellyMode.reset();
            telly2Mode.reset();
        }
        else {
            normalIntaveTransition.cancel(false);
        }
        SILENT_ROTATIONS.synchronize();
        resetNormalRotationState();
        armNormalIntaveEntry();
    }

    private boolean isNormalMode() {
        return (int) mode.getInput() == NORMAL_MODE;
    }

    private boolean isTellyBridgeMode() {
        return (int) mode.getInput() == TELLY4_MODE || isTelly3Mode();
    }

    private boolean isTelly3Mode() {
        return (int) mode.getInput() == TELLY3_MODE;
    }

    private ScaffoldTellyBridgeMode getTellyBridgeMode() {
        return isTelly3Mode() ? telly3Mode : telly4Mode;
    }

    int getTellyMultiPlace() {
        return (int) tellyMultiPlace.getInput();
    }

    boolean isTellyTowerEnabled() {
        return isTelly3Mode() || telly4Tower.isToggled();
    }

    boolean isTelly4KeepYOnRightEnabled() {
        return telly4KeepYOnRight.isToggled();
    }

    private boolean hasExternalRotationWindow() {
        return enableClutchRotationActive || yieldingToAntiFireball;
    }

    private boolean isTellyMode() {
        return (int) mode.getInput() == TELLY_MODE
                || (int) mode.getInput() == TELLY2_MODE;
    }

    private boolean isOriginalTellyMode() {
        return (int) mode.getInput() == TELLY_MODE;
    }

    boolean isTelly2Mode() {
        return (int) mode.getInput() == TELLY2_MODE;
    }

    private float getNormalPlacementYaw() {
        float forward = 0.0F;
        float strafe = 0.0F;
        if (mc.gameSettings.keyBindForward.isKeyDown()) {
            forward += 1.0F;
        }
        if (mc.gameSettings.keyBindBack.isKeyDown()) {
            forward -= 1.0F;
        }
        if (mc.gameSettings.keyBindLeft.isKeyDown()) {
            strafe += 1.0F;
        }
        if (mc.gameSettings.keyBindRight.isKeyDown()) {
            strafe -= 1.0F;
        }
        return normalPlacementYaw(
                mc.thePlayer.rotationYaw, forward, strafe,
                mc.thePlayer.motionX, mc.thePlayer.motionZ, getYawOffset());
    }

    private float getNormalProfileYaw() {
        if (isMiauRotation()) {
            return getNormalPlacementYaw();
        }
        if (!isNormalInputMoving() || mc.thePlayer.hurtTime != 0) {
            return isFinite(rotations[0])
                    ? rotations[0] : mc.thePlayer.rotationYaw;
        }
        if (isMinimalRotation()) {
            return minimalBaseYaw(mc.thePlayer.rotationYaw);
        }
        return getNormalPlacementYaw();
    }

    private float getNormalProfilePitch() {
        if (isMiauRotation()) {
            return rotations[1];
        }
        if (isNormalInputMoving() && mc.thePlayer.hurtTime == 0) {
            return MINIMAL_BASE_PITCH;
        }
        return isFinite(rotations[1])
                ? rotations[1] : mc.thePlayer.rotationPitch;
    }

    private boolean isNormalInputMoving() {
        return mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown();
    }

    private boolean isMinimalRotation() {
        return (int) rotationMode.getInput() == MINIMAL_ROTATION;
    }

    private boolean isMiauRotation() {
        return (int) rotationMode.getInput() == MIAU_ROTATION;
    }

    private float getNormalIdleYaw(float placementYaw) {
        return flick.isToggled() ? mc.thePlayer.rotationYaw : placementYaw;
    }

    static float normalPlacementYaw(
            float cameraYaw, float forward, float strafe,
            double motionX, double motionZ, float configuredOffset) {
        if (forward != 0.0F || strafe != 0.0F) {
            double travelOffset = Math.toDegrees(Math.atan2(-strafe, forward));
            double placementOffset = travelOffset + 180.0D;
            while (placementOffset > 180.0D) {
                placementOffset -= 360.0D;
            }
            while (placementOffset <= -180.0D) {
                placementOffset += 360.0D;
            }
            return cameraYaw + (float) placementOffset + configuredOffset;
        }

        double horizontalMotion = motionX * motionX + motionZ * motionZ;
        float travelYaw = horizontalMotion > 1.0E-8D
                ? (float) Math.toDegrees(Math.atan2(-motionX, motionZ))
                : cameraYaw;
        return travelYaw + 180.0F + configuredOffset;
    }

    static int countHotbarBlocks(ItemStack[] inventory) {
        if (inventory == null) {
            return 0;
        }
        int count = 0;
        int limit = Math.min(InventoryPlayer.getHotbarSize(), inventory.length);
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = inventory[slot];
            if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) {
                continue;
            }
            ItemBlock item = (ItemBlock) stack.getItem();
            if (ScaffoldUtil.isBlockValid(item.getBlock())) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private void updateShift(boolean placedThisTick) {
        if (isOriginalTellyMode()) {
            resetEagleState();
            setShift(tellyMode.shouldSneak());
            return;
        }
        if (isTelly2Mode()) {
            resetEagleState();
            setShift(false);
            return;
        }
        boolean shift = safeWalk.isToggled()
                && mc.thePlayer.onGround && !placedThisTick && ScaffoldUtil.willFallNextTick();
        if (!eagle.isToggled()) {
            resetEagleState();
        }
        else {
            if (eagleSneakTicks <= 0
                    && blocksSinceEagle >= blocksUntilEagle
                    && isWithinEagleDistance()) {
                eagleSneakTicks = randomInt(eagleTicks);
                blocksSinceEagle = 0;
                blocksUntilEagle = randomInt(sneakPerBlock);
            }
            if (eagleSneakTicks > 0) {
                shift = true;
                eagleSneakTicks--;
            }
        }
        setShift(shift);
    }

    private void setShift(boolean shift) {
        if (mc.gameSettings == null) {
            return;
        }
        KeyBinding.setKeyBindState(
                mc.gameSettings.keyBindSneak.getKeyCode(),
                shift || normalIntaveTransition.isSneaking()
                        || Utils.isBindDown(mc.gameSettings.keyBindSneak));
    }

    private boolean isWithinEagleDistance() {
        BlockPos below = new BlockPos(mc.thePlayer).down();
        if (isReplaceable(below)) {
            return true;
        }
        double closestEdge = Double.MAX_VALUE;
        EnumFacing[] horizontalFacings = {
                EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
        };
        for (EnumFacing facing : horizontalFacings) {
            BlockPos neighbor = below.offset(facing);
            if (!isReplaceable(neighbor)) {
                continue;
            }
            double distance = facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH
                    ? Math.abs(neighbor.getZ() + 0.5D - mc.thePlayer.posZ) - 0.5D
                    : Math.abs(neighbor.getX() + 0.5D - mc.thePlayer.posX) - 0.5D;
            closestEdge = Math.min(closestEdge, distance);
        }
        return closestEdge < eagleDistance.getInput();
    }

    private boolean isReplaceable(BlockPos position) {
        return mc.theWorld.getBlockState(position).getBlock()
                .isReplaceable(mc.theWorld, position);
    }

    private void resetEagleState() {
        eagleSneakTicks = 0;
        blocksSinceEagle = 0;
        blocksUntilEagle = randomInt(sneakPerBlock);
    }

    private static int randomInt(RangeSetting setting) {
        int minimum = (int) Math.round(setting.getMinimum());
        int maximum = (int) Math.round(setting.getMaximum());
        return minimum == maximum
                ? minimum
                : ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
    }

    private void updateKeepYState() {
        if (isTellyMode() || !keepY.isToggled()) {
            resetKeepYState();
            return;
        }
        boolean userJump = Utils.isBindDown(mc.gameSettings.keyBindJump);
        if (!jumpOnUserInput.isToggled()) {
            userTowering = false;
        }
        else if (userJump) {
            userTowering = true;
        }
        else if (mc.thePlayer.onGround) {
            userTowering = false;
        }
        boolean requested = !rmbOnly.isToggled()
                || Utils.isBindDown(mc.gameSettings.keyBindUseItem);
        if (!requested || userTowering) {
            keepYAnchored = false;
            return;
        }
        if (!keepYAnchored) {
            keepYLevel = MathHelper.floor_double(mc.thePlayer.posY);
            keepYAnchored = true;
        }
    }

    private void resetKeepYState() {
        keepYAnchored = false;
        userTowering = false;
        keepYLevel = 0;
    }

    private boolean isKeepYActive() {
        return keepY.isToggled()
                && keepYAnchored
                && !userTowering
                && (!rmbOnly.isToggled()
                || Utils.isBindDown(mc.gameSettings.keyBindUseItem));
    }

    private boolean shouldAutoJumpKeepY(EventMovementInput movement) {
        return isKeepYActive()
                && mc.thePlayer.onGround
                && (movement.getSpeed() != 0.0F || movement.getStrafe() != 0.0F);
    }

    private boolean canSprint() {
        if (!Utils.nullCheck()
                || mc.thePlayer.movementInput == null
                || mc.thePlayer.isCollidedHorizontally
                || mc.thePlayer.isPotionActive(Potion.blindness)
                || mc.thePlayer.isUsingItem()
                || mc.thePlayer.movementInput.sneak) {
            return false;
        }
        boolean moving = mc.thePlayer.movementInput.moveForward != 0.0F
                || mc.thePlayer.movementInput.moveStrafe != 0.0F;
        boolean canUseSprint = mc.thePlayer.getFoodStats().getFoodLevel() > 6
                || mc.thePlayer.capabilities.allowFlying;
        return moving && canUseSprint;
    }

    static boolean canSprintWithServerRelativeInput(float moveForward) {
        return moveForward >= 0.8F;
    }

    private float getMovementYaw() {
        Float serverYaw = RotationHelper.get().getServerYaw();
        if (serverYaw != null && !serverYaw.isNaN()) {
            return serverYaw;
        }
        if (isOriginalTellyMode() && tellyMode.hasPlannedRotation()) {
            return tellyMode.getPlannedYaw();
        }
        return SILENT_ROTATIONS.getYaw();
    }

    public BlockData findBestPlacement() {
        NormalPlacementPlan plan = findBestPlacementPlan(
                getNormalProfileYaw(), ScaffoldUtil.getPredictedBoundingBox(1.0D));
        return plan == null ? null : plan.data;
    }

    private NormalPlacementPlan findBestPlacementPlan(
            float placementYaw, AxisAlignedBB predicted) {
        EntityPlayerSP player = mc.thePlayer;
        boolean keepYActive = isKeepYActive();
        BlockPos scanY = keepYActive
                ? new BlockPos(
                MathHelper.floor_double(player.posX), keepYLevel - 1,
                MathHelper.floor_double(player.posZ))
                : new BlockPos(player).down();
        double targetX = (predicted.minX + predicted.maxX) * 0.5D;
        double targetZ = (predicted.minZ + predicted.maxZ) * 0.5D;
        double targetY = scanY.getY() + 0.5D;
        double existingScore = Double.MAX_VALUE;
        boolean tower = isNormalTowerIntent(
                !player.onGround, keepYActive,
                Utils.isBindDown(mc.gameSettings.keyBindJump));
        int lowestLayer = tower ? -1 : 0;
        ItemBlock placeholderBlock = placeholderBlock();
        List<NormalPlacementCandidate> candidates =
                new ArrayList<NormalPlacementCandidate>();

        for (int layer = 0; layer >= lowestLayer; layer--) {
            BlockPos layerPos = scanY.add(0, layer, 0);
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = layerPos.add(x, 0, z);
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    if (state.getBlock() == Blocks.air || !state.getBlock().isFullCube()) {
                        continue;
                    }

                    double existingX = pos.getX() + 0.5D - targetX;
                    double existingZ = pos.getZ() + 0.5D - targetZ;
                    double existingY = pos.getY() + 0.5D - targetY;
                    double score = existingX * existingX + existingZ * existingZ
                            + existingY * existingY * 0.25D;
                    if (score < existingScore) {
                        existingScore = score;
                    }

                    List<EnumFacing> facings = new ArrayList<EnumFacing>(Arrays.asList(
                            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST));
                    if (tower) {
                        facings.add(EnumFacing.UP);
                    }
                    for (EnumFacing facing : facings) {
                        if (!placeholderBlock.canPlaceBlockOnSide(
                                mc.theWorld, pos, facing, mc.thePlayer, mc.thePlayer.getHeldItem())) {
                            continue;
                        }
                        BlockPos neighbor = pos.offset(facing);
                        if (mc.theWorld.getBlockState(neighbor).getBlock() != Blocks.air) {
                            continue;
                        }

                        double dx = neighbor.getX() + 0.5D - targetX;
                        double dz = neighbor.getZ() + 0.5D - targetZ;
                        double dy = neighbor.getY() + 0.5D - targetY;
                        double candidateScore = dx * dx + dz * dz + dy * dy * 0.25D;
                        candidates.add(new NormalPlacementCandidate(
                                pos, facing, candidateScore));
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(candidate -> candidate.score));
        float profilePitch = getNormalProfilePitch();
        for (NormalPlacementCandidate candidate : candidates) {
            if (!isNormalPlacementImprovement(existingScore, candidate.score)) {
                break;
            }
            NormalFaceRotation candidateRotations = resolveNormalFaceRotation(
                    candidate.position, candidate.facing,
                    placementYaw, profilePitch);
            MovingObjectPosition hit = candidateRotations == null
                    ? null : rayTrace(
                    candidateRotations.yaw,
                    candidateRotations.pitch);
            if (!matchesPlacement(
                    hit, candidate.position, candidate.facing)) {
                continue;
            }
            return new NormalPlacementPlan(
                    new BlockData(candidate.position, candidate.facing),
                    candidateRotations.yaw,
                    candidateRotations.pitch,
                    candidateRotations.instantTurn);
        }
        return null;
    }

    private static final class NormalPlacementCandidate {
        private final BlockPos position;
        private final EnumFacing facing;
        private final double score;

        private NormalPlacementCandidate(
                BlockPos position, EnumFacing facing, double score) {
            this.position = position;
            this.facing = facing;
            this.score = score;
        }
    }

    public boolean ensurePlacementBlock() {
        return Utils.nullCheck() && keyBlock() instanceof ItemBlock;
    }

    EntityPlayerSP getPlayerForTelly() {
        return mc.thePlayer;
    }

    float getTellyYawOffset() {
        return getYawOffset();
    }

    float getNormalYawOffset() {
        return getYawOffset();
    }

    boolean isTelly2SameYEnabled() {
        return telly2SameY.isToggled();
    }

    boolean isTellyIntaveEnabled() {
        return isOriginalTellyMode() && tellyIntave.isToggled();
    }

    boolean isTellyMidCycleSneakEnabled() {
        return (int) tellySneak.getInput() == 1;
    }

    int getTellySneakEveryCycle() {
        return Math.max(1, Math.min(10,
                (int) tellySneakEveryCycle.getInput()));
    }

    boolean requestTellySneakPulse(PrePlayerInputEvent event, String source) {
        return sneakSafety.tryPulse(mc.thePlayer, mc.theWorld, event, source);
    }

    boolean isPreparingTellySneak() {
        return sneakSafety.isCoastingInput();
    }

    float limitScaffoldPitch(float requested, float previousPitch, float gcd) {
        return placementSafety.limitPitch(requested, previousPitch, gcd);
    }

    void limitScaffoldMotionPitch(PreMotionEvent event, float previousPitch, float gcd) {
        event.setPitch(limitScaffoldPitch(event.getPitch(), previousPitch, gcd));
    }

    boolean withinScaffoldPitchBudget(float candidate, float previousPitch) {
        return placementSafety.withinPitchBudget(candidate, previousPitch);
    }

    boolean isHoldingTellySneakRelease() {
        return sneakSafety.isHoldingRelease();
    }

    void noteScaffoldSneak(String source) {
        sneakSafety.ownSafetySneak(source);
    }

    private float getYawOffset() {
        int index = (int) yaw.getInput();
        if (index < 0 || index >= YAW_OFFSETS.length) {
            index = 1;
        }
        return YAW_OFFSETS[index];
    }

    ItemBlock getCurrentTellyBlock() {
        Item item = keyBlock();
        return item instanceof ItemBlock ? (ItemBlock) item : null;
    }

    boolean hasTelly2PlacementBlock() {
        return findTelly2BlockSlot() != NO_SLOT;
    }

    boolean ensureTelly2PlacementBlock() {
        int slot = findTelly2BlockSlot();
        if (slot == NO_SLOT) {
            return false;
        }
        switchToBlockSlot(slot);
        ItemStack held = mc.thePlayer == null
                ? null : mc.thePlayer.inventory.getCurrentItem();
        return held != null && held.stackSize > 0
                && held.getItem() instanceof ItemBlock
                && isTelly2PlacementBlock(
                ((ItemBlock) held.getItem()).getBlock());
    }

    private int findTelly2BlockSlot() {
        if (mc.thePlayer == null) {
            return NO_SLOT;
        }
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held != null && held.stackSize > 1
                && held.getItem() instanceof ItemBlock
                && isTelly2PlacementBlock(
                ((ItemBlock) held.getItem()).getBlock())) {
            return mc.thePlayer.inventory.currentItem;
        }
        int bestSlot = NO_SLOT;
        int largestStack = 0;
        for (int slot = 0; slot < InventoryPlayer.getHotbarSize(); slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize <= largestStack
                    || !(stack.getItem() instanceof ItemBlock)
                    || !isTelly2PlacementBlock(
                    ((ItemBlock) stack.getItem()).getBlock())) {
                continue;
            }
            largestStack = stack.stackSize;
            bestSlot = slot;
        }
        return bestSlot;
    }

    private static boolean isTelly2PlacementBlock(
            net.minecraft.block.Block block) {
        return block != null && !getTelly2InvalidBlocks().contains(block);
    }

    private static List<net.minecraft.block.Block> getTelly2InvalidBlocks() {
        if (telly2InvalidBlocks == null) {
            telly2InvalidBlocks = Arrays.asList(
                    Blocks.enchanting_table, Blocks.carpet, Blocks.glass_pane,
                    Blocks.ladder, Blocks.web, Blocks.stained_glass_pane,
                    Blocks.iron_bars, Blocks.air, Blocks.water,
                    Blocks.flowing_water, Blocks.lava, Blocks.soul_sand,
                    Blocks.ice, Blocks.sand, Blocks.flowing_lava,
                    Blocks.snow_layer, Blocks.chest, Blocks.ender_chest,
                    Blocks.torch, Blocks.anvil, Blocks.trapped_chest,
                    Blocks.noteblock, Blocks.jukebox,
                    Blocks.wooden_pressure_plate, Blocks.stone_pressure_plate,
                    Blocks.light_weighted_pressure_plate,
                    Blocks.heavy_weighted_pressure_plate, Blocks.stone_button,
                    Blocks.tnt, Blocks.wooden_button, Blocks.lever,
                    Blocks.crafting_table, Blocks.furnace, Blocks.stone_slab,
                    Blocks.wooden_slab, Blocks.stone_slab2,
                    Blocks.brown_mushroom, Blocks.red_mushroom,
                    Blocks.gold_block, Blocks.red_flower,
                    Blocks.yellow_flower, Blocks.flower_pot);
        }
        return telly2InvalidBlocks;
    }

    boolean placeTelly2Block(
            BlockPos selectedPosition, EnumFacing selectedFacing,
            MovingObjectPosition hit) {
        if (mc.thePlayer == null || mc.theWorld == null
                || selectedPosition == null || selectedFacing == null
                || hit == null
                || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.getBlockPos() == null
                || !selectedPosition.equals(hit.getBlockPos())
                || selectedFacing != hit.sideHit
                || !placementSafety.allowsPlacement(hit.sideHit)) {
            return false;
        }
        if (mc.theWorld.getBlockState(hit.getBlockPos()).getBlock()
                .getMaterial() == Material.air) {
            return false;
        }
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            return false;
        }
        boolean clicked = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held,
                hit.getBlockPos(), hit.sideHit, hit.hitVec);
        if (clicked) {
            swingArm();
            blockRenderFeature.markPlaced(
                    hit.getBlockPos().offset(hit.sideHit));
            scaffoldRotationGuardTicks = VULCAN_SCAFFOLD_ROTATION_WINDOW;
        }
        return clicked;
    }

    boolean placeTellyBlock(BlockData data, MovingObjectPosition hit) {
        if (data == null || hit == null
                || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !data.getPosition().equals(hit.getBlockPos())
                || data.getFacing() != hit.sideHit
                || data.getFacing() == EnumFacing.DOWN
                || !placementSafety.allowsPlacement(hit.sideHit)) {
            return false;
        }
        Item item = keyBlock();
        if (!(item instanceof ItemBlock)
                || !((ItemBlock) item).canPlaceBlockOnSide(
                mc.theWorld, hit.getBlockPos(), hit.sideHit,
                mc.thePlayer, mc.thePlayer.getHeldItem())) {
            return false;
        }
        if (mc.theWorld.getBlockState(hit.getBlockPos()).getBlock().getMaterial() == Material.air) {
            return false;
        }
        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                data.getPosition(), data.getFacing(), hit.hitVec);
        swingArm();
        if (placed) {
            blockRenderFeature.markPlaced(data.getPosition().offset(data.getFacing()));
            blocksSinceEagle++;
            scaffoldRotationGuardTicks = VULCAN_SCAFFOLD_ROTATION_WINDOW;
        }
        return placed;
    }

    void finishTellyPlacement() {
        setShift(tellyMode.shouldSneak());
    }

    void setTellySneak(boolean sneak) {
        if (isTellyMode()) {
            setShift(sneak);
        }
    }

    private Item keyBlock() {
        ItemStack current = mc.thePlayer.inventory.getCurrentItem();
        if (current == null || !(current.getItem() instanceof ItemBlock) || current.stackSize <= 1) {
            int blockSlot = ScaffoldUtil.getBlockSlot();
            ItemStack blockStack = mc.thePlayer.inventory.getStackInSlot(blockSlot);
            if (blockStack != null && blockStack.getItem() instanceof ItemBlock) {
                switchToBlockSlot(blockSlot);
            }
            else if (current == null || !(current.getItem() instanceof ItemBlock)) {
                SilentHotbar.INSTANCE.resetSlot(this, true);
            }
        }
        current = mc.thePlayer.inventory.getCurrentItem();
        return current == null ? null : current.getItem();
    }

    private void switchToBlockSlot(int blockSlot) {
        if (blockSlot < 0 || blockSlot >= InventoryPlayer.getHotbarSize()) {
            return;
        }
        SilentHotbar.INSTANCE.resetSlot(this, false);
        int currentSlot = mc.thePlayer.inventory.currentItem;
        if (currentSlot != blockSlot) {
            if (previousSlot == NO_SLOT || currentSlot != switchedBlockSlot) {
                previousSlot = currentSlot;
            }
            switchedBlockSlot = blockSlot;
            mc.thePlayer.inventory.currentItem = blockSlot;
        }
        if (spoofItems.isToggled() && isHotbarSlot(previousSlot)) {
            SilentHotbar.INSTANCE.selectSlotSilently(
                    this, blockSlot, previousSlot, null, false, false, true);
        }
    }

    private void updateItemSwitchMode() {
        if (!Utils.nullCheck()) {
            SilentHotbar.INSTANCE.resetSlot(this, false);
            clearItemSwitchState();
            return;
        }
        if (spoofItems.isToggled()) {
            if (isHotbarSlot(previousSlot)
                    && isBlockSlot(switchedBlockSlot)
                    && mc.thePlayer.inventory.currentItem == switchedBlockSlot) {
                SilentHotbar.INSTANCE.selectSlotSilently(
                        this, switchedBlockSlot, previousSlot, null, false, false, true);
            }
            return;
        }
        SilentHotbar.INSTANCE.resetSlot(this, false);
    }

    private void restoreItemSlot() {
        if (mc.thePlayer != null
                && isHotbarSlot(previousSlot)
                && mc.thePlayer.inventory.currentItem == switchedBlockSlot) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        SilentHotbar.INSTANCE.resetSlot(this, true);
        clearItemSwitchState();
    }

    private boolean isBlockSlot(int slot) {
        if (!isHotbarSlot(slot)) {
            return false;
        }
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        return stack != null && stack.getItem() instanceof ItemBlock;
    }

    private static boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot < InventoryPlayer.getHotbarSize();
    }

    private void clearItemSwitchState() {
        previousSlot = NO_SLOT;
        switchedBlockSlot = NO_SLOT;
    }

    private boolean placeBlock(
            BlockData data, MovingObjectPosition hit,
            float previousEmittedYaw, float finalEmittedYaw) {
        if (data == null || hit == null
                || !matchesPlacement(hit, data)
                || !placementSafety.allowsPlacement(hit.sideHit)) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        if (hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || mc.theWorld.getBlockState(pos).getBlock().getMaterial() == Material.air) {
            return false;
        }
        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                data.position, data.facing, hit.hitVec);
        swingArm();
        if (placed) {
            blockRenderFeature.markPlaced(data.position.offset(data.facing));
            blocksSinceEagle++;
            scaffoldRotationGuardTicks = VULCAN_SCAFFOLD_ROTATION_WINDOW;
            lastNormalPlacedYawDelta = Math.abs(
                    finalEmittedYaw - previousEmittedYaw);
        }
        return placed;
    }

    private void swingArm() {
        if (noSwing.isToggled()) {
            PacketUtils.sendPacketNoEvent(
                    new net.minecraft.network.play.client.C0APacketAnimation());
        } else {
            mc.thePlayer.swingItem();
        }
    }

    private static boolean matchesPlacement(
            MovingObjectPosition hit, BlockData data) {
        return data != null && matchesPlacement(hit, data.position, data.facing);
    }

    private static boolean matchesPlacement(
            MovingObjectPosition hit, BlockPos position, EnumFacing facing) {
        return hit != null
                && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && position != null && position.equals(hit.getBlockPos())
                && facing == hit.sideHit;
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = Utils.getLookVec(yaw, pitch);
        Vec3 end = eye.addVector(look.xCoord * 4.5D, look.yCoord * 4.5D, look.zCoord * 4.5D);
        return mc.theWorld.rayTraceBlocks(eye, end, false, false, true);
    }

    public static float[] getRotationsForFace(BlockPos blockPos, EnumFacing facing, float lockedYaw) {
        return getRotationsForFace(
                blockPos, facing, lockedYaw,
                SILENT_ROTATIONS.getPitch());
    }

    static float[] getRotationsForFace(
            BlockPos blockPos, EnumFacing facing, float lockedYaw, float currentPitch) {
        EntityPlayerSP player = mc.thePlayer;
        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;
        float yawRad = (float) Math.toRadians(lockedYaw);
        double horizontalX = -Math.sin(yawRad);
        double horizontalZ = Math.cos(yawRad);
        double minX = blockPos.getX();
        double maxX = minX + 1.0D;
        double minY = blockPos.getY();
        double maxY = minY + 1.0D;
        double minZ = blockPos.getZ();
        double maxZ = minZ + 1.0D;
        float bestPitch = Float.MAX_VALUE;
        float bestDifference = Float.MAX_VALUE;

        if (facing == EnumFacing.UP) {
            float pitch = pitchToHitPoint(
                    eyeX, eyeY, eyeZ, horizontalX, horizontalZ,
                    minX + 0.5D, maxY, minZ + 0.5D);
            float[] best = selectPitch(pitch, currentPitch, bestPitch, bestDifference);
            bestPitch = best[0];
            bestDifference = best[1];
            for (double x : new double[]{minX + 0.1D, maxX - 0.1D}) {
                for (double z : new double[]{minZ + 0.1D, maxZ - 0.1D}) {
                    best = selectPitch(
                            pitchToHitPoint(eyeX, eyeY, eyeZ, horizontalX, horizontalZ, x, maxY, z),
                            currentPitch, bestPitch, bestDifference);
                    bestPitch = best[0];
                    bestDifference = best[1];
                }
            }
        }
        else if (facing == EnumFacing.DOWN) {
            float[] best = selectPitch(
                    pitchToHitPoint(eyeX, eyeY, eyeZ, horizontalX, horizontalZ,
                            minX + 0.5D, minY, minZ + 0.5D),
                    currentPitch, bestPitch, bestDifference);
            bestPitch = best[0];
        }
        else {
            float[] candidates;
            if (facing == EnumFacing.NORTH) {
                candidates = pitchesToHitZPlane(
                        eyeX, eyeY, eyeZ, horizontalX, horizontalZ,
                        minZ, minX, maxX, minY, maxY);
            }
            else if (facing == EnumFacing.SOUTH) {
                candidates = pitchesToHitZPlane(
                        eyeX, eyeY, eyeZ, horizontalX, horizontalZ,
                        maxZ, minX, maxX, minY, maxY);
            }
            else if (facing == EnumFacing.WEST) {
                candidates = pitchesToHitXPlane(
                        eyeX, eyeY, eyeZ, horizontalX, horizontalZ,
                        minX, minY, maxY, minZ, maxZ);
            }
            else {
                candidates = pitchesToHitXPlane(
                        eyeX, eyeY, eyeZ, horizontalX, horizontalZ,
                        maxX, minY, maxY, minZ, maxZ);
            }
            for (float pitch : candidates) {
                float[] best = selectPitch(pitch, currentPitch, bestPitch, bestDifference);
                bestPitch = best[0];
                bestDifference = best[1];
            }
        }

        if (bestPitch == Float.MAX_VALUE) {
            return null;
        }
        bestPitch = MathHelper.clamp_float(bestPitch, -90.0F, 90.0F);
        float[] fixed = RotationUtils.patchGCD(
                new float[]{lockedYaw, currentPitch},
                new float[]{lockedYaw, bestPitch});
        fixed[0] = lockedYaw;
        return fixed;
    }

    public static float[] getFreeRotationsForFace(BlockPos blockPos, EnumFacing facing) {
        return getFreeRotationsForFace(
                blockPos, facing,
                SILENT_ROTATIONS.getYaw(), SILENT_ROTATIONS.getPitch());
    }

    private static float[] getFreeRotationsForFace(
            BlockPos blockPos, EnumFacing facing, float currentYaw, float currentPitch) {
        EntityPlayerSP player = mc.thePlayer;
        double faceX = blockPos.getX() + 0.5D + facing.getFrontOffsetX() * 0.5D;
        double faceY = blockPos.getY() + 0.5D + facing.getFrontOffsetY() * 0.5D;
        double faceZ = blockPos.getZ() + 0.5D + facing.getFrontOffsetZ() * 0.5D;
        double dx = faceX - player.posX;
        double dy = faceY - (player.posY + player.getEyeHeight());
        double dz = faceZ - player.posZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);
        return RotationUtils.patchGCD(
                new float[]{currentYaw, currentPitch},
                new float[]{yaw, pitch});
    }

    private static float[] selectPitch(
            float candidate, float currentPitch, float bestPitch, float bestDifference) {
        if (Float.isNaN(candidate)) {
            return new float[]{bestPitch, bestDifference};
        }
        float difference = Math.abs(MathHelper.wrapAngleTo180_float(candidate - currentPitch));
        return difference < bestDifference
                ? new float[]{candidate, difference}
                : new float[]{bestPitch, bestDifference};
    }

    private static float pitchToHitPoint(
            double eyeX, double eyeY, double eyeZ, double horizontalX, double horizontalZ,
            double targetX, double targetY, double targetZ) {
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;
        double horizontalDistance;
        if (Math.abs(horizontalX) > Math.abs(horizontalZ)) {
            if (Math.abs(horizontalX) < 1.0E-6D) {
                return Float.NaN;
            }
            horizontalDistance = dx / horizontalX;
        }
        else {
            if (Math.abs(horizontalZ) < 1.0E-6D) {
                return Float.NaN;
            }
            horizontalDistance = dz / horizontalZ;
        }
        if (horizontalDistance <= 0.0D) {
            return Float.NaN;
        }
        return (float) Math.toDegrees(Math.atan(-dy / horizontalDistance));
    }

    private static float[] pitchesToHitZPlane(
            double eyeX, double eyeY, double eyeZ, double horizontalX, double horizontalZ,
            double faceZ, double minX, double maxX, double minY, double maxY) {
        if (Math.abs(horizontalZ) < 1.0E-6D) {
            return new float[0];
        }
        double horizontalDistance = (faceZ - eyeZ) / horizontalZ;
        if (horizontalDistance <= 0.0D) {
            return new float[0];
        }
        double hitX = eyeX + horizontalX * horizontalDistance;
        if (hitX < minX || hitX > maxX) {
            return new float[0];
        }
        return samplePitches(eyeY, horizontalDistance, minY, maxY);
    }

    private static float[] pitchesToHitXPlane(
            double eyeX, double eyeY, double eyeZ, double horizontalX, double horizontalZ,
            double faceX, double minY, double maxY, double minZ, double maxZ) {
        if (Math.abs(horizontalX) < 1.0E-6D) {
            return new float[0];
        }
        double horizontalDistance = (faceX - eyeX) / horizontalX;
        if (horizontalDistance <= 0.0D) {
            return new float[0];
        }
        double hitZ = eyeZ + horizontalZ * horizontalDistance;
        if (hitZ < minZ || hitZ > maxZ) {
            return new float[0];
        }
        return samplePitches(eyeY, horizontalDistance, minY, maxY);
    }

    private static float[] samplePitches(
            double eyeY, double horizontalDistance, double minY, double maxY) {
        double[] sampleY = {
                minY + 0.1D,
                minY + (maxY - minY) * 0.2D,
                minY + (maxY - minY) * 0.3D,
                minY + (maxY - minY) * 0.4D,
                (minY + maxY) * 0.5D,
                minY + (maxY - minY) * 0.6D,
                minY + (maxY - minY) * 0.7D,
                minY + (maxY - minY) * 0.8D,
                minY + (maxY - minY) * 0.9D,
                maxY - 0.1D
        };
        float[] values = new float[sampleY.length];
        for (int index = 0; index < sampleY.length; index++) {
            values[index] = (float) Math.toDegrees(
                    Math.atan(-(sampleY[index] - eyeY) / horizontalDistance));
        }
        return values;
    }

    private static ItemBlock placeholderBlock() {
        return PlaceholderBlockHolder.INSTANCE;
    }

    private static final class PlaceholderBlockHolder {
        private static final ItemBlock INSTANCE = new ItemBlock(Blocks.tnt);
    }

    public static final class BlockData {
        private final BlockPos position;
        private final EnumFacing facing;

        public BlockData(BlockPos position, EnumFacing facing) {
            this.position = position;
            this.facing = facing;
        }

        public EnumFacing getFacing() {
            return facing;
        }

        public BlockPos getPosition() {
            return position;
        }
    }

}
