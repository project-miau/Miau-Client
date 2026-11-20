package idle.miau.module.impl.combat;

import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PreAttackEvent;
import idle.miau.event.PreMouseSelectionEvent;
import idle.miau.event.RightClickMouseEvent;
import idle.miau.helper.RotationPriority;
import idle.miau.helper.TargetCombatStatus;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ClientContext;
import idle.miau.utility.CombatTargeting;
import idle.miau.utility.ModuleActionService;
import idle.miau.utility.MiauMath;
import idle.miau.utility.RodAim;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import idle.miau.utility.WeaponPolicy;
import idle.miau.utility.inventory.InventoryActions;
import idle.miau.utility.inventory.ItemActionTiming;
import idle.miau.utility.packet.TimingAccess;
import idle.miau.utility.rotation.MiauRandom;
import idle.miau.utility.rotation.RotationMath;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.util.Vec3;

public final class AutoRod extends Module {
    private final KeySetting activeKeybind;
    private final SliderSetting retractSpeed;
    private final SliderSetting actionDelay;
    private final ButtonSetting randomizeDelays;
    private final ButtonSetting alwaysSwitchToWeapon;
    private final TargetCombatStatus targeting = new TargetCombatStatus();
    private final ItemActionTiming switchDelay = new ItemActionTiming();
    private Phase phase = Phase.IDLE;
    private InventoryPlayer owner;
    private Entity cycleTarget;
    private int previous = -1;
    private int selected;
    private int retract;
    private int nextAutoTick;
    private float timer;
    private boolean inputSeen;
    private boolean automaticCycle;
    private boolean waitingForRelease;
    private boolean usePending;
    private boolean hookSeen;
    private boolean hookConnected;

    public AutoRod() {
        super("AutoRod", category.combat);
        setDescription(
                "Uses a fishing rod while the active key is held or assists KillAura"
                        + " automatically.");
        registerSetting(activeKeybind = new KeySetting("Active Keybind", 0));
        registerSetting(retractSpeed = new SliderSetting("Retract speed", 1.0, 0.5, 2.0, 0.01));
        registerSetting(
                actionDelay = new SliderSetting("Delay between actions", 0.5, 0.0, 2.0, 0.01));
        registerSetting(randomizeDelays = new ButtonSetting("Randomize delays", true));
        registerSetting(
                alwaysSwitchToWeapon = new ButtonSetting("Always switch back to weapon", false));
    }

    @Override
    public void onEnable() {
        resetAction(true);
        waitingForRelease = false;
        nextAutoTick = 0;
    }

    @Override
    public void onDisable() {
        resetAction(true);
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        resetAction(true);
        waitingForRelease = false;
        nextAutoTick = 0;
        targeting.clear();
    }

    private Entity target() {
        if (cycleTarget != null) {
            return cycleTarget;
        }
        targeting.select(new ClientContext(mc), CombatTargeting::isTrackablePlayer);
        return targeting.target();
    }

    private Entity automaticTarget() {
        KillAura aura = ModuleManager.killAura;
        if (aura == null
                || !aura.isEnabled()
                || KillAuraSupport.scaffoldActive()
                || KillAuraSupport.blockInActive()) {
            return null;
        }
        Entity entity = aura.getCombatTarget();
        if (!validAutomaticTarget(entity)) {
            return null;
        }
        double distance = RotationUtils.distanceFromEyeToClosestOnAABB(entity);
        double range = aura.getAttackRangeSetting().getInput();
        return distance > range && distance <= 8.0
                        && !RodAim.enteringMelee(mc.thePlayer, entity, range)
                ? entity
                : null;
    }

    private boolean validAutomaticTarget(Entity entity) {
        return entity instanceof EntityLivingBase
                && entity != mc.thePlayer
                && !entity.isDead
                && ((EntityLivingBase) entity).deathTime == 0
                && ((EntityLivingBase) entity).getHealth() > 0.0F
                && entity.worldObj == mc.theWorld
                && mc.theWorld.loadedEntityList.contains(entity)
                && mc.thePlayer.canEntityBeSeen(entity);
    }

    private float delay() {
        return randomizeDelays.isToggled()
                ? switchDelay.switching(
                        (float) actionDelay.getInput(),
                        MiauRandom.now(),
                        MiauRandom.SHARED)
                : (float) actionDelay.getInput();
    }

    public static int choose(int[] types) {
        for (int slot = 0; slot < 9; slot++) {
            if (types[slot] == 346) {
                return slot;
            }
        }
        return -1;
    }

    private int scan(InventoryPlayer inventory) {
        for (int slot = 0; slot < 9; slot++) {
            if (InventoryActions.type(inventory.getStackInSlot(slot)) == 346) {
                return slot;
            }
        }
        return -1;
    }

    public static int retractTicks(Vec3 origin, Vec3 target, float speed) {
        float delay;
        if (target == null) {
            delay = 10.0F / speed;
        } else {
            Vec3 closest =
                    new Vec3(
                            RotationMath.clamp(
                                    target.xCoord - 0.3, target.xCoord + 0.3, origin.xCoord),
                            RotationMath.clamp(target.yCoord, target.yCoord + 1.8, origin.yCoord),
                            RotationMath.clamp(
                                    target.zCoord - 0.3, target.zCoord + 0.3, origin.zCoord));
            delay = (float) (RotationMath.distance(origin, closest) / (double) speed);
        }
        return MiauMath.truncateInt(MiauMath.ceiling(delay));
    }

    @Subscribe(priority = Priority.LOW)
    public void onClientRotation(ClientRotationEvent event) {
        if (automaticCycle && InventoryActions.inputReady() && validAutomaticTarget(cycleTarget)) {
            Vec3 point = RodAim.point(mc.thePlayer, cycleTarget);
            float[] rotation = RotationUtils.calculate(point);
            event.trySetRotation(this, RotationPriority.BLOCK_PLACEMENT, rotation[0], rotation[1]);
        }
    }

    @Subscribe
    public void onPreAttack(PreAttackEvent event) {
        if (ModuleActionService.owner() == this) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onRightClick(RightClickMouseEvent event) {
        if (ModuleActionService.owner() == this
                && Utils.nullCheck()
                && InventoryActions.type(mc.thePlayer.getHeldItem()) == 346) {
            if (inputSeen) {
                event.setCanceled(true);
            } else {
                inputSeen = true;
                usePending = false;
            }
        }
    }

    @Subscribe
    public void onTick(PreMouseSelectionEvent event) {
        if (!InventoryActions.inputReady() || mc.currentScreen != null) {
            resetAction(true);
            return;
        }
        boolean manual = activeKeybind.getKey() != 0;
        boolean pressed = manual && activeKeybind.isPressed();
        if (!pressed) {
            waitingForRelease = false;
        }
        if (manual && (!pressed || waitingForRelease)) {
            resetAction(true);
            return;
        }
        if (automaticCycle && automaticTarget() != cycleTarget) {
            finishCycle();
            return;
        }
        if (retract > 0) {
            retract--;
        }
        if (automaticCycle && phase == Phase.WAITING) {
            EntityFishHook hook = mc.thePlayer.fishEntity;
            if (hook != null) {
                hookSeen = true;
                hookConnected = hook.caughtEntity != null;
            }
            if (hookConnected || hookSeen && (hook == null || hook.isDead || hook.onGround)
                    || retract == 0) {
                finishCycle();
                return;
            }
        }
        if (timer > 0.0F && --timer > 0.0F) {
            return;
        }
        for (int pass = 0; pass < 3; pass++) {
            InventoryPlayer inventory = mc.thePlayer.inventory;
            if (owner != null && phase != Phase.SWITCHING && inventory.currentItem != selected) {
                resetAction(false);
                waitingForRelease = manual;
                nextAutoTick = mc.thePlayer.ticksExisted + 10;
                return;
            }
            switch (phase) {
                case IDLE:
                    Entity nextTarget = manual ? null : automaticTarget();
                    if (!manual
                            && (nextTarget == null || mc.thePlayer.ticksExisted < nextAutoTick)) {
                        return;
                    }
                    int slot = scan(inventory);
                    if (slot < 0 || !ModuleActionService.claim(this)) {
                        return;
                    }
                    InventoryActions.prepareItemUse();
                    owner = inventory;
                    previous = inventory.currentItem;
                    selected = slot;
                    cycleTarget = nextTarget;
                    automaticCycle = !manual;
                    inputSeen = false;
                    if (automaticCycle || alwaysSwitchToWeapon.isToggled()) {
                        int weapon = WeaponPolicy.SHARED.firstAllowedSlot(inventory);
                        if (weapon != -1) {
                            previous = weapon;
                        }
                    }
                    phase = selected == inventory.currentItem ? Phase.READY : Phase.SWITCHING;
                    break;
                case SWITCHING:
                    inventory.currentItem = selected;
                    phase = Phase.READY;
                    timer += delay();
                    break;
                case READY:
                    int next = scan(inventory);
                    if (next < 0) {
                        finishCycle();
                        return;
                    }
                    if (next != inventory.currentItem) {
                        selected = next;
                        phase = Phase.SWITCHING;
                        timer += delay();
                        break;
                    }
                    InventoryActions.pressCount(mc.gameSettings.keyBindUseItem, 1);
                    usePending = true;
                    phase = Phase.WAITING;
                    Entity target = target();
                    retract =
                            retractTicks(
                                    new Vec3(
                                            mc.thePlayer.posX,
                                            mc.thePlayer.posY + 1.62,
                                            mc.thePlayer.posZ),
                                    target == null ? null : TimingAccess.server(target),
                                    (float) retractSpeed.getInput());
                    if (automaticCycle) {
                        retract = Math.max(3, retract + 2);
                    }
                    timer += delay();
                    return;
                case WAITING:
                    if (retract != 0) {
                        return;
                    }
                    phase = previous == selected ? Phase.RETRACTING : Phase.RESTORING;
                    timer += delay();
                    break;
                case RETRACTING:
                    selected = (previous + 1) % 9;
                    inventory.currentItem = selected;
                    phase = Phase.RESTORING;
                    return;
                case RESTORING:
                    finishCycle();
                    return;
                default:
                    throw new IllegalStateException("Invalid rod phase");
            }
            if (timer > 0.0F) {
                return;
            }
        }
    }

    private void finishCycle() {
        if (automaticCycle) {
            nextAutoTick = mc.thePlayer.ticksExisted + (hookConnected ? 8 : 4);
        }
        float remaining = timer + delay();
        resetAction(true);
        timer = remaining;
    }

    private void resetAction(boolean restore) {
        if (usePending && mc.gameSettings != null) {
            InventoryActions.pressCount(mc.gameSettings.keyBindUseItem, 0);
        }
        if (restore && owner != null && previous >= 0 && owner.currentItem == selected) {
            owner.currentItem = previous;
        }
        ModuleActionService.release(this);
        owner = null;
        previous = -1;
        phase = Phase.IDLE;
        cycleTarget = null;
        automaticCycle = false;
        inputSeen = false;
        usePending = hookSeen = hookConnected = false;
        retract = 0;
        timer = 0.0F;
    }

    private enum Phase {
        IDLE,
        SWITCHING,
        READY,
        WAITING,
        RETRACTING,
        RESTORING
    }
}
