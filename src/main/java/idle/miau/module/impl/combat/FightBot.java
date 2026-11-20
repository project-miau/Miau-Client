package idle.miau.module.impl.combat;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.IInputControlProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.utils.PathingCommandContext;
import idle.miau.baritone.BaritoneBridge;
import idle.miau.event.ModuleStateChangeEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.CombatAim;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import java.util.HashMap;
import java.util.Map;

public final class FightBot extends Module {
    public final SliderSetting searchRange = new SliderSetting("Search range", 32, 4, 96, 1);
    public final SliderSetting movement = new SliderSetting("Movement", 1, new String[]{"Continuous", "Rhythm"});
    public final SliderSetting distance = new SliderSetting("Fight distance", 2.4, 1, 4, 0.05);
    public final SliderSetting advanceTicks = new SliderSetting("Move ticks", 6, 1, 20, 1);
    public final SliderSetting stopTicks = new SliderSetting("Stop ticks", 2, 1, 10, 1);
    public final ButtonSetting strafe = new ButtonSetting("Strafe", true);
    public final ButtonSetting sprint = new ButtonSetting("Sprint", true);
    public final ButtonSetting autoAura = new ButtonSetting("Auto KillAura", true);
    private final FightBotMovement rhythm = new FightBotMovement();
    private final Map<Integer, Integer> unreachable = new HashMap<>();
    private IBaritone baritone;
    private CombatProcess process;
    private EntityPlayer target;
    private FightBotMovement.Step step;
    private boolean enabledAura, changingAura, cancelled, enabledBaritone;
    private String status = "Searching";

    public FightBot() {
        super("FightBot", category.combat);
        setDescription("Automatic find and kill targets.");
        registerSetting(searchRange); registerSetting(movement); registerSetting(distance);
        registerSetting(advanceTicks); registerSetting(stopTicks); registerSetting(strafe);
        registerSetting(sprint); registerSetting(autoAura);
        guiUpdate();
    }

    @Override public void guiUpdate() {
        advanceTicks.setVisible((int)movement.getInput() == 1, this);
        stopTicks.setVisible((int)movement.getInput() == 1, this);
    }
    @Override public void onSlide(SliderSetting setting) { guiUpdate(); }
    @Override public String getInfo() { return status + (target == null ? "" : " " + target.getName()); }
    public EntityPlayer getTarget() { return target; }
    public boolean isPausing() { return step != null && step.phase() == FightBotMovement.Phase.PAUSE; }

    @Override public void onEnable() {
        enabledBaritone = !ModuleManager.baritone.isEnabled();
        ModuleManager.baritone.enable();
        BaritoneBridge.initialize();
        baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getPathingBehavior().cancelEverything();
        if (process == null) {
            process = new CombatProcess();
            baritone.getPathingControlManager().registerProcess(process);
        }
        cancelled = false; target = null; step = null; rhythm.reset(); unreachable.clear();
        process.active = true;
        KillAura aura = ModuleManager.killAura;
        enabledAura = autoAura.isToggled() && aura != null && !aura.isEnabled();
        if (enabledAura) {
            changingAura = true;
            try { aura.enable(); } finally { changingAura = false; }
        }
    }

    @Override public void onDisable() {
        boolean ownsPath = baritone != null && process != null
                && baritone.getPathingControlManager().mostRecentInControl().orElse(null) == process;
        if (process != null) process.active = false;
        if (ownsPath) BaritoneBridge.cancel();
        target = null; step = null; rhythm.reset(); unreachable.clear(); status = "Searching";
        if (enabledAura && ModuleManager.killAura != null && ModuleManager.killAura.isEnabled()) {
            changingAura = true;
            try { ModuleManager.killAura.disable(); } finally { changingAura = false; }
        }
        enabledAura = false;
        if (enabledBaritone) ModuleManager.baritone.disable();
        enabledBaritone = false;
    }

    public void beforeBaritoneTick() {
        if (cancelled) { disable(); return; }
        step = null;
        if (mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.isEntityAlive()
                || mc.thePlayer.isSpectator() || mc.currentScreen != null) {
            target = null; status = "Paused"; return;
        }
        if (ModuleManager.killAura == null || !ModuleManager.killAura.isEnabled()) {
            target = null; status = "KillAura off"; return;
        }
        EntityPlayer previous = target;
        target = chooseTarget();
        if (previous != target) rhythm.reset();
        status = target == null ? "Searching" : "Approach";
    }

    EntityPlayer chooseTarget() {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        int tick = mc.thePlayer.ticksExisted;
        unreachable.entrySet().removeIf(e -> e.getValue() <= tick);
        EntityPlayer current = eligible(target) ? target : null;
        EntityPlayer closest = null;
        double best = Double.POSITIVE_INFINITY;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!eligible(player) || unreachable.containsKey(player.getEntityId())) continue;
            double candidate = mc.thePlayer.getDistanceSqToEntity(player);
            if (candidate < best) { closest = player; best = candidate; }
        }
        if (current != null && !unreachable.containsKey(current.getEntityId())
                && (closest == null || Math.sqrt(mc.thePlayer.getDistanceSqToEntity(current)) <= Math.sqrt(best) + 1.5)) return current;
        return closest;
    }

    boolean eligible(EntityPlayer player) {
        return player != null && mc.theWorld != null && mc.thePlayer != null && player.worldObj == mc.theWorld
                && mc.theWorld.playerEntities.contains(player) && mc.thePlayer.getDistanceSqToEntity(player) <= searchRange.getInput() * searchRange.getInput()
                && KillAuraSupport.eligiblePlayer(player);
    }

    public void afterBaritoneTick() {
        if (step == null || process == null || !process.active
                || baritone.getPathingControlManager().mostRecentInControl().orElse(null) != process) return;
        var input = baritone.getInputOverrideHandler();
        input.clearAllKeys();
        input.setInputForceState(Input.MOVE_FORWARD, step.forward() > 0);
        input.setInputForceState(Input.MOVE_BACK, step.forward() < 0);
        input.setInputForceState(Input.MOVE_LEFT, step.strafe() > 0);
        input.setInputForceState(Input.MOVE_RIGHT, step.strafe() < 0);
        input.setInputForceState(Input.SPRINT, step.sprint());
        if (!step.sprint()) mc.thePlayer.setSprinting(false);
    }

    @Subscribe(priority = Priority.LOW)
    public void onHurt(ReceivePacketEvent event) {
        if (target != null && mc.thePlayer != null && event.getPacket() instanceof S19PacketEntityStatus hurt
                && hurt.getOpCode() == 2 && hurt.getEntity(mc.theWorld) == target
                && KillAura.attackingEntity == target) {
            rhythm.onHit(mc.thePlayer.ticksExisted, (int)advanceTicks.getInput(), (int)stopTicks.getInput());
        }
    }
    @Subscribe public void onModuleState(ModuleStateChangeEvent event) {
        if (event.getModule() == ModuleManager.killAura && !changingAura) enabledAura = false;
        if (event.getModule() == ModuleManager.baritone && !ModuleManager.baritone.isEnabled()) disable();
    }
    @Subscribe public void onWorldUnload(WorldEvent.Unload event) { disable(); }

    private boolean safeDirection(float forward, float sideways, float yaw) {
        if (forward == 0 && sideways == 0) return true;
        double radians = Math.toRadians(yaw);
        double dx = sideways * Math.cos(radians) - forward * Math.sin(radians);
        double dz = forward * Math.cos(radians) + sideways * Math.sin(radians);
        double length = Math.hypot(dx, dz); dx = dx / length * 0.7; dz = dz / length * 0.7;
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().offset(dx, 0, dz);
        if (!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box).isEmpty()) return false;
        if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box.offset(0, -0.8, 0)).isEmpty()) return false;
        var material = mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX + dx, mc.thePlayer.posY - 0.2, mc.thePlayer.posZ + dz)).getBlock().getMaterial();
        return material != net.minecraft.block.material.Material.lava && material != net.minecraft.block.material.Material.fire;
    }

    private final class CombatProcess implements IInputControlProcess {
        private boolean active, direct;
        private CalculationContext context;
        private int contextTick = Integer.MIN_VALUE;
        @Override public boolean isActive() { return active && FightBot.this.isEnabled(); }
        @Override public boolean isTemporary() { return false; }
        @Override public double priority() { return DEFAULT_PRIORITY + 1; }
        @Override public String displayName0() { return "FightBot " + status; }
        @Override public boolean isControllingInput() { return direct; }
        @Override public void onLostControl() {
            if (active) cancelled = true;
            active = direct = false; context = null; contextTick = Integer.MIN_VALUE;
            target = null; step = null; rhythm.reset();
        }
        @Override public PathingCommand onTick(boolean calcFailed, boolean safeToCancel) {
            direct = false;
            if (target == null) {
                direct = safeToCancel || !baritone.getPathingBehavior().isPathing();
                if (direct) step = new FightBotMovement.Step(FightBotMovement.Phase.SEARCHING, 0, 0, false);
                return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
            if (calcFailed) {
                unreachable.put(target.getEntityId(), mc.thePlayer.ticksExisted + 60);
                target = null;
                return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
            double horizontal = Math.hypot(target.posX - mc.thePlayer.posX, target.posZ - mc.thePlayer.posZ);
            boolean visible = CombatAim.visiblePoint(target) != null;
            if (safeToCancel && mc.thePlayer.onGround && horizontal < 4.5
                    && Math.abs(target.posY - mc.thePlayer.posY) < 1.2 && visible) {
                double ideal = Math.min(distance.getInput(), KillAura.attackRangeSetting.getInput() - 0.2);
                FightBotMovement.Step planned = rhythm.step(mc.thePlayer.ticksExisted, horizontal, ideal, (int)movement.getInput() == 1,
                        (int)advanceTicks.getInput(), (int)stopTicks.getInput(), strafe.isToggled(), sprint.isToggled());
                float[] angles = CombatAim.rotations(target, null);
                if (!safeDirection(planned.forward(), planned.strafe(), angles[0])) {
                    if (safeDirection(planned.forward(), -planned.strafe(), angles[0])) planned = new FightBotMovement.Step(planned.phase(), planned.forward(), -planned.strafe(), planned.sprint());
                    else if (safeDirection(planned.forward(), 0, angles[0])) planned = new FightBotMovement.Step(planned.phase(), planned.forward(), 0, planned.sprint());
                    else planned = null;
                }
                if (planned != null) {
                    direct = true; step = planned;
                    status = switch (planned.phase()) {
                        case PAUSE -> "Pause";
                        case RETREAT -> "Retreat";
                        default -> "Advance";
                    };
                    baritone.getLookBehavior().updateTarget(new Rotation(angles[0], angles[1]), false);
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
            }
            status = "Approach";
            int tick = mc.thePlayer.ticksExisted;
            if (context == null || tick < contextTick || tick - contextTick >= 10) {
                context = new CalculationContext(baritone, true, false); contextTick = tick;
            }
            BlockPos goal = new BlockPos(target);
            return new PathingCommandContext(new GoalNear(goal, 1), PathingCommandType.REVALIDATE_GOAL_AND_PATH, context);
        }
    }
}
