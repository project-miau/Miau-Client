package idle.miau.module.impl.movement;

import idle.miau.event.AttackEvent;
import idle.miau.event.JumpEvent;
import idle.miau.event.PreSuperLivingUpdateEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.KillAura;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.SliderSetting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.Vec3;

public class KeepSprint extends Module {
    private static final int TICK_MODE = 1;
    private static final int PREDICTION_IDLE = 0;
    private static final int PREDICTION_DEFERRED = 1;
    private static final int PREDICTION_READY = 2;
    private static final String[] MODES = {"Normal", "Tick"};

    public static SliderSetting mode;
    public static SliderSetting slow;
    public static ButtonSetting hypixel;
    public static ButtonSetting stopSprint;
    public static ButtonSetting disableWhileJump;
    public static ButtonSetting reduceReachHits;
    public static int predictionStage;
    public static int predictionStageTicks;
    private final DescriptionSetting description;
    private boolean sprintCancelled;
    private boolean processedAttackThisTick;
    private int offGroundTicks;
    private int ticksSinceVelocity = Integer.MAX_VALUE;
    private Entity tickTarget;
    private int lastTargetTick = Integer.MIN_VALUE;

    public KeepSprint() {
        super("KeepSprint", Module.category.movement, 0);
        setDescription("Reduces the sprint slowdown caused by attacks.");
        registerSetting(description = new DescriptionSetting("Default is 40% motion reduction."));
        registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        registerSetting(hypixel = new ButtonSetting("Hypixel", false));
        registerSetting(slow = new SliderSetting("Slow %", 40.0D, 0.0D, 40.0D, 1.0D));
        registerSetting(stopSprint = new ButtonSetting("Stop Sprint", true));
        registerSetting(disableWhileJump = new ButtonSetting("Disable while jumping", false));
        registerSetting(reduceReachHits = new ButtonSetting("Only reduce reach hits", false));
        resetState();
        updateSettingVisibility();
    }

    public static void keepSprint(Entity entity) {
        int selectedMode = (int) mode.getInput();
        if (isHypixelMode()) {
            applyHypixelAttackSlowdown();
            return;
        }
        if (selectedMode == TICK_MODE) {
            mc.thePlayer.motionX *= 0.6D;
            mc.thePlayer.motionZ *= 0.6D;
            mc.thePlayer.setSprinting(false);
            return;
        }
        boolean vanilla = false;
        if (disableWhileJump.isToggled() && !mc.thePlayer.onGround) {
            vanilla = true;
        } else if (reduceReachHits.isToggled() && !mc.thePlayer.capabilities.isCreativeMode) {
            double distance = -1.0D;
            Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
            if (ModuleManager.killAura != null
                    && ModuleManager.killAura.isEnabled()
                    && KillAura.target != null) {
                distance = eyes.distanceTo(KillAura.target.getPositionEyes(1.0F));
            } else if (ModuleManager.reach != null
                    && ModuleManager.reach.isEnabled()
                    && mc.objectMouseOver != null
                    && mc.objectMouseOver.hitVec != null) {
                distance = eyes.distanceTo(mc.objectMouseOver.hitVec);
            }
            if (distance >= 0.0D && distance <= 3.0D) {
                vanilla = true;
            }
        }

        double multiplier = attackMultiplier(vanilla, slow.getInput());
        mc.thePlayer.motionX *= multiplier;
        mc.thePlayer.motionZ *= multiplier;

        if (stopSprint.isToggled()) {
            mc.thePlayer.motionX *= 0.5D;
        }
    }

    static double attackMultiplier(boolean vanilla, double slowPercent) {
        if (vanilla) {
            return 0.6D;
        }
        return (100.0D - slowPercent) / 100.0D;
    }

    public static void setHypixelPredictionStage(int stage) {
        predictionStage = Math.max(
                PREDICTION_IDLE,
                Math.min(PREDICTION_READY, stage));
        predictionStageTicks = 0;
    }

    public static boolean isHypixelMode() {
        return mode != null
                && hypixel != null
                && (int) mode.getInput() == TICK_MODE
                && hypixel.isToggled();
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void guiUpdate() {
        updateSettingVisibility();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            updateSettingVisibility();
            resetState();
        }
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == hypixel) {
            resetState();
        }
        super.guiButtonToggled(setting);
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        if (setting == mode || setting == hypixel) {
            resetState();
        }
        updateSettingVisibility();
    }

    @Subscribe
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            processedAttackThisTick = false;
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onAttack(AttackEvent event) {
        if (event.attacker != mc.thePlayer) {
            return;
        }
        int selectedMode = (int) mode.getInput();
        if (isHypixelMode()) {
            handleHypixelAttack(event);
            return;
        }
        resetPredictionState();
        if (selectedMode != TICK_MODE) {
            return;
        }

        tickTarget = event.target;
        lastTargetTick = mc.thePlayer.ticksExisted;
        if (ticksSinceVelocity < 8) {
            return;
        }
        if (stopSprintingForTick()) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onJump(JumpEvent event) {
        if ((int) mode.getInput() == TICK_MODE
                && !isHypixelMode()
                && event.entity == mc.thePlayer
                && sprintCancelled
                && !mc.thePlayer.isSprinting()) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        if (mc.thePlayer == null) {
            resetState();
            return;
        }
        if (mc.thePlayer.onGround) {
            offGroundTicks = 0;
        } else if (offGroundTicks < Integer.MAX_VALUE) {
            offGroundTicks++;
        }
        if (ticksSinceVelocity < Integer.MAX_VALUE) {
            ticksSinceVelocity++;
        }

        int selectedMode = (int) mode.getInput();
        if (isHypixelMode()) {
            sprintCancelled = false;
            return;
        }
        resetPredictionState();
        if (selectedMode != TICK_MODE) {
            sprintCancelled = false;
            return;
        }
        if (ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()) {
            tickTarget = KillAura.target;
            if (tickTarget != null) {
                lastTargetTick = mc.thePlayer.ticksExisted;
            }
        }
        if (mc.thePlayer.isSprinting() || !isTickTargetActive()) {
            sprintCancelled = false;
        }
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onHypixelPreUpdate(PreUpdateEvent event) {
        if (mc.thePlayer == null || !isHypixelMode()) {
            resetPredictionState();
            return;
        }
        updateHypixelPrediction();
    }

    @Subscribe
    public void onPreSuperLivingUpdate(PreSuperLivingUpdateEvent event) {
        if (mc.thePlayer == null || !isHypixelMode()) {
            resetPredictionState();
            return;
        }
        if (predictionStage == PREDICTION_DEFERRED) {
            mc.thePlayer.setSprinting(false);
        } else if (predictionStage == PREDICTION_READY) {
            restartSprintIfAllowed();
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof S12PacketEntityVelocity
                && mc.thePlayer != null
                && ((S12PacketEntityVelocity) event.getPacket()).getEntityID()
                == mc.thePlayer.getEntityId()) {
            ticksSinceVelocity = 0;
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        resetState();
    }

    private void handleHypixelAttack(AttackEvent event) {
        if (processedAttackThisTick) {
            return;
        }
        if (event.target instanceof EntityPlayer) {
            if (predictionStage == PREDICTION_IDLE) {
                if (mc.thePlayer.isSprinting()) {
                    event.setCanceled(true);
                    predictionStage = PREDICTION_DEFERRED;
                } else {
                    predictionStage = PREDICTION_READY;
                }
                predictionStageTicks = 0;
            } else if (predictionStage == PREDICTION_DEFERRED) {
                mc.thePlayer.setSprinting(false);
                predictionStageTicks = 0;
                predictionStage = PREDICTION_READY;
            }
        }
        processedAttackThisTick = true;
    }

    private void updateHypixelPrediction() {
        if (predictionStageTicks > 5) {
            resetPredictionState();
        }
        if (predictionStage == PREDICTION_DEFERRED) {
            mc.thePlayer.setSprinting(false);
            predictionStageTicks++;
        } else if (predictionStage == PREDICTION_READY) {
            restartSprintIfAllowed();
            predictionStageTicks = 0;
            predictionStage = PREDICTION_IDLE;
        }
    }

    private static void restartSprintIfAllowed() {
        if (mc.thePlayer.isUsingItem() && !canSprintWhileUsingItem()) {
            return;
        }
        mc.thePlayer.setSprinting(true);
    }

    private static boolean canSprintWhileUsingItem() {
        boolean noSlowActive = ModuleManager.noSlow != null
                && ModuleManager.noSlow.isEnabled()
                && (int) NoSlow.mode.getInput() == 0
                && NoSlow.getSlowed() > 0.2F;
        return mc.thePlayer.moveForward > 0.0F
                && (noSlowActive || !mc.thePlayer.isUsingItem())
                && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isCollidedHorizontally
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6;
    }

    private static void applyHypixelAttackSlowdown() {
        if (!mc.thePlayer.isSprinting()) {
            return;
        }
        if (predictionStage == PREDICTION_READY) {
            return;
        }
        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
        mc.thePlayer.setSprinting(false);
    }

    private boolean stopSprintingForTick() {
        if (offGroundTicks == 1) {
            return true;
        }
        if (!mc.thePlayer.isSprinting()) {
            return false;
        }
        mc.thePlayer.setSprinting(false);
        KeyBinding.setKeyBindState(
                mc.gameSettings.keyBindSprint.getKeyCode(), false);
        sprintCancelled = true;
        return true;
    }

    private boolean isTickTargetActive() {
        if (tickTarget == null || tickTarget.isDead) {
            return false;
        }
        if (tickTarget instanceof EntityLivingBase
                && ((EntityLivingBase) tickTarget).getHealth() <= 0.0F) {
            return false;
        }
        return mc.thePlayer.ticksExisted - lastTargetTick <= 10;
    }

    private void resetState() {
        sprintCancelled = false;
        offGroundTicks = 0;
        ticksSinceVelocity = Integer.MAX_VALUE;
        tickTarget = null;
        lastTargetTick = Integer.MIN_VALUE;
        resetPredictionState();
    }

    private void resetPredictionState() {
        predictionStage = PREDICTION_IDLE;
        predictionStageTicks = 0;
        processedAttackThisTick = false;
    }

    private void updateSettingVisibility() {
        int selectedMode = (int) mode.getInput();
        boolean normal = selectedMode == 0;
        boolean tick = selectedMode == TICK_MODE;
        description.setVisible(normal, this);
        hypixel.setVisible(tick, this);
        slow.setVisible(normal, this);
        stopSprint.setVisible(normal, this);
        disableWhileJump.setVisible(normal, this);
        reduceReachHits.setVisible(normal, this);
    }

    @Override
    public String getInfo() {
        return MODES[Math.max(0, Math.min((int) mode.getInput(), MODES.length - 1))];
    }
}
