package idle.miau.module.impl.combat;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.Miau;
import idle.miau.event.AttackEvent;
import idle.miau.event.GameTickEvent;
import idle.miau.event.PostUpdateEvent;
import idle.miau.event.PrePlayerInteractEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.lag.api.EnumLagDirection;
import idle.miau.lag.api.LagRequest;
import idle.miau.lag.timeout.ModuleBackedTimeout;
import idle.miau.mcp.accessor.IAccessorPlayerControllerMP;
import idle.miau.mcp.accessor.IAccessorRenderManager;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.player.BedAura;
import idle.miau.module.impl.player.Scaffold;
import idle.miau.module.impl.render.ArrayListModule;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ItemUtils;
import idle.miau.utility.CombatTargeting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.TeamUtils;
import idle.miau.utility.Utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class LagRange extends Module {
    private static final double MINIMUM_DISTANCE_SQ = 9.0D;
    private static final String[] MODES = {"Delay", "Lag"};
    private static final String[] POSITION_MODES = {"None", "Default", "HUD"};

    public final SliderSetting delay = new SliderSetting("Delay", "ms", 150, 0, 1000, 1);
    public final SliderSetting range = new SliderSetting("Range", 10.0, 3.0, 100.0, 0.1);
    public final SliderSetting mode = new SliderSetting("Mode", 0, MODES);
    private final GroupSetting condition = new GroupSetting("Condition");
    public final ButtonSetting weaponsOnly = new ButtonSetting(condition, "Weapons only", true);
    public final ButtonSetting allowTools = new ButtonSetting(condition, "Allow tools", false);
    public final ButtonSetting botCheck = new ButtonSetting(condition, "Bot check", true);
    public final ButtonSetting ignoreTeams = new ButtonSetting(condition, "Ignore teams", true, "Teams");
    public final SliderSetting showPosition = new SliderSetting("Show position", 0, POSITION_MODES);

    private boolean hasTarget;
    private Vec3 lastPosition;
    private Vec3 currentPosition;

    private boolean isLagging;
    private int lastSelfHurtTime;
    private int lastTargetHurtTime;
    private int hitMarkedEntityId = -1;
    private boolean lastSprintState;
    private boolean lastBlockingState;
    private double lastDistSq = -1.0D;
    private EntityPlayer currentTarget;
    private LagRequest outboundLag;
    private WorldClient lastWorld;
    private int lastMode = -1;
    private int tickIndex = -1;
    private long delayCounter;
    private int correctionCooldownTicks;
    private boolean timerRangeSuspended;
    private long lastLagStartTime;
    private long lastLagEndTime;

    public LagRange() {
        super("LagRange", category.combat);
        setDescription("Uses lag to keep you out of your opponent's range");
        this.registerSetting(delay);
        this.registerSetting(range);
        this.registerSetting(mode);
        this.registerSetting(condition);
        this.registerSetting(weaponsOnly);
        this.registerSetting(allowTools);
        this.registerSetting(botCheck);
        this.registerSetting(ignoreTeams);
        this.registerSetting(showPosition);
        guiUpdate();
    }

    @Override
    public void guiUpdate() {
        delay.setVisible(true, this);
        range.setVisible(true, this);
        mode.setVisible(true, this);
        condition.setVisible(true, this);
        weaponsOnly.setVisible(true, this);
        allowTools.setVisible(weaponsOnly.isToggled(), this);
        botCheck.setVisible(true, this);
        ignoreTeams.setVisible(true, this);
        showPosition.setVisible(true, this);
    }

    @Override
    public void onSlide(SliderSetting setting) {
        guiUpdate();
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        guiUpdate();
    }

    @Override
    public void onEnable() {
        timerRangeSuspended = false;
        resetState(false);
        lastMode = (int) mode.getInput();
        lastWorld = mc.theWorld;
    }

    @Override
    public void onDisable() {
        timerRangeSuspended = false;
        resetState(true);
    }

    @Override
    public String getInfo() {
        return String.format("%dms", (int) delay.getInput());
    }

    public boolean isLagging() {
        if (!isEnabled()) {
            return false;
        }
        if ((int) mode.getInput() == 0) {
            return hasTarget || (Miau.lagManager != null && Miau.lagManager.getDelay() > 0);
        }
        return isLagging;
    }

    public boolean hasActiveLag() {
        if (!isEnabled()) {
            return false;
        }
        if (isLagging()) {
            return true;
        }
        long now = System.currentTimeMillis();
        return (now - lastLagEndTime <= 800L) || (now - lastLagStartTime <= 800L);
    }

    public EntityPlayer getCurrentTarget() {
        return currentTarget;
    }

    public EntityPlayer getLagRangeTarget() {
        if (!isLagging || currentTarget == null || currentTarget.isDead) {
            return null;
        }
        return currentTarget;
    }

    public Vec3 getCurrentPosition() {
        return currentPosition;
    }

    public Vec3 getLagRangePosition() {
        if (!isLagging) {
            return null;
        }
        return getReleasedPosition();
    }

    @Subscribe(priority = Priority.LOW)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if ((int) mode.getInput() != 0) {
            return;
        }
        if (syncWithTimerRange()) {
            return;
        }
        prepareMode(0);
        if (event.phase == TickEvent.Phase.START) {
            if (consumeCorrectionCooldown()) {
                return;
            }
            tickDelayMode();
        } else {
            updateDelayPosition();
        }
    }

    @Subscribe
    public void onPostUpdate(PostUpdateEvent event) {
        if (syncWithTimerRange()) {
            return;
        }
        if ((int) mode.getInput() == 0) {
            return;
        }
        prepareMode(1);
        if (!canOperate() || !isLagging || !hasTarget) {
            lastPosition = null;
            currentPosition = null;
            return;
        }
        Vec3 savedPosition = getReleasedPosition();
        if (currentPosition == null) {
            lastPosition = savedPosition;
        } else {
            lastPosition = currentPosition;
        }
        currentPosition = savedPosition;
    }

    @Subscribe
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        if ((int) mode.getInput() == 0 || mc.currentScreen != null) {
            return;
        }
        if (syncWithTimerRange()) {
            return;
        }
        if (isScaffoldActive()) {
            flushLag();
            return;
        }
        prepareMode(1);
        tickLagMode();
    }

    @Subscribe
    public void onPreMotion(PreMotionEvent event) {
        if (syncWithTimerRange()) {
            return;
        }
    }

    @Subscribe(receiveCanceled = true)
    public void onDelayPacket(SendPacketEvent event) {
        if ((int) mode.getInput() == 0 && shouldResetOnPacket(event.getPacket())) {
            if (Miau.lagManager != null) {
                Miau.lagManager.setDelay(0);
            }
            tickIndex = -1;
        }
    }

    @Subscribe(priority = Priority.HIGHEST, receiveCanceled = true)
    public void onServerCorrection(ReceivePacketEvent event) {
        if (!(event.getPacket() instanceof S08PacketPlayerPosLook)) {
            return;
        }
        resetState(false);
        correctionCooldownTicks = 5;
    }

    @Subscribe(priority = Priority.HIGH)
    public void onPacket(SendPacketEvent event) {
        if ((int) mode.getInput() == 0
                || syncWithTimerRange()
                || !canOperate()
                || !shouldResetOnPacket(event.getPacket())) {
            return;
        }
        flushLag();
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        if ((int) mode.getInput() == 1 && isLagging && event.attacker == mc.thePlayer) {
            lastLagEndTime = System.currentTimeMillis();
            flushLag();
        }
    }

    @Subscribe
    public void onGameTick(GameTickEvent event) {
        if ((int) mode.getInput() != 1) {
            return;
        }
        if (mc.currentScreen == null) {
            return;
        }
        flushLag();
        resetState(false);
    }

    @Subscribe(priority = Priority.HIGH)
    public void onRender(RenderWorldLastEvent event) {
        if (syncWithTimerRange()) {
            return;
        }
        if ((int) showPosition.getInput() == 0
                || mc.gameSettings.thirdPersonView == 0
                || !hasTarget
                || lastPosition == null
                || currentPosition == null) {
            return;
        }

        Color color = new Color(-1);
        if ((int) showPosition.getInput() == 1) {
            color = TeamUtils.getTeamColor(mc.thePlayer, 1.0F);
        } else if ((int) showPosition.getInput() == 2) {
            color = new Color(ArrayListModule.getHudColor(0.0D), true);
        }
        double x = RenderUtils.lerpDouble(currentPosition.xCoord, lastPosition.xCoord, event.partialTicks);
        double y = RenderUtils.lerpDouble(currentPosition.yCoord, lastPosition.yCoord, event.partialTicks);
        double z = RenderUtils.lerpDouble(currentPosition.zCoord, lastPosition.zCoord, event.partialTicks);
        float size = mc.thePlayer.getCollisionBorderSize();
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        AxisAlignedBB box = new AxisAlignedBB(
                x - mc.thePlayer.width / 2.0D,
                y,
                z - mc.thePlayer.width / 2.0D,
                x + mc.thePlayer.width / 2.0D,
                y + mc.thePlayer.height,
                z + mc.thePlayer.width / 2.0D)
                .expand(size, size, size)
                .offset(
                        -renderManager.getRenderPosX(),
                        -renderManager.getRenderPosY(),
                        -renderManager.getRenderPosZ());
        RenderUtils.enableRenderState();
        RenderUtils.drawFilledBox(box, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtils.disableRenderState();
    }

    private void prepareMode(int selectedMode) {
        if (lastMode != selectedMode) {
            resetState(true);
            lastMode = selectedMode;
            lastWorld = mc.theWorld;
            return;
        }
        if (lastWorld != mc.theWorld) {
            resetState(false);
            lastWorld = mc.theWorld;
        }
    }

    private void tickDelayMode() {
        if (Miau.lagManager == null) {
            return;
        }
        Miau.lagManager.setDelay(0);
        hasTarget = false;
        if (mc.thePlayer == null || mc.theWorld == null) {
            tickIndex = -1;
            return;
        }

        BedAura bedAura = ModuleManager.bedAura;
        boolean bedAuraReady = bedAura != null && bedAura.isEnabled() && bedAura.isReady();
        boolean breakingBlock = mc.playerController != null
                && ((IAccessorPlayerControllerMP) mc.playerController).isHittingBlock();
        boolean weaponAllowed = isWeaponAllowed();
        if (bedAuraReady || breakingBlock
                || mc.thePlayer.isUsingItem() && !mc.thePlayer.isBlocking()
                || !weaponAllowed) {
            tickIndex = -1;
            return;
        }

        List<EntityPlayer> players = getValidPlayers();
        if (players.isEmpty()) {
            tickIndex = -1;
            return;
        }

        double height = mc.thePlayer.getEyeHeight();
        Vec3 eyePosition = Miau.lagManager.getLastPosition().addVector(0.0D, height, 0.0D);
        Vec3 targetEyePosition = new Vec3(
                mc.thePlayer.lastTickPosX,
                mc.thePlayer.lastTickPosY + height,
                mc.thePlayer.lastTickPosZ);
        Vec3 playerEyePosition = new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY + height,
                mc.thePlayer.posZ);
        for (EntityPlayer player : players) {
            double distance = RotationUtils.distanceToBox(player, playerEyePosition);
            if (distance > range.getInput()) {
                continue;
            }
            double targetDistance = RotationUtils.distanceToBox(player, targetEyePosition);
            double eyeDistance = RotationUtils.distanceToBox(player, eyePosition);
            if (distance < targetDistance || distance < eyeDistance) {
                if (tickIndex < 0) {
                    startDelayCycle();
                    lastLagStartTime = System.currentTimeMillis();
                }
                Miau.lagManager.setDelay(tickIndex);
                hasTarget = true;
                return;
            }
        }
    }

    private void updateDelayPosition() {
        if (mc.thePlayer == null || mc.theWorld == null || Miau.lagManager == null) {
            return;
        }
        Vec3 savedPosition = Miau.lagManager.getLastPosition();
        if (currentPosition == null) {
            lastPosition = savedPosition;
        } else {
            lastPosition = currentPosition;
        }
        currentPosition = savedPosition;
    }

    private int startDelayCycle() {
        tickIndex = 0;
        for (delayCounter += (long) delay.getInput(); delayCounter > 0L; delayCounter -= 50L) {
            tickIndex++;
        }
        return tickIndex;
    }

    private void tickLagMode() {
        if (!Utils.nullCheck() || mc.thePlayer.isDead || mc.theWorld == null
                || mc.getNetHandler() == null) {
            flushLag();
            resetLagState();
            return;
        }
        hasTarget = false;

        BedAura bedAura = ModuleManager.bedAura;
        if (bedAura != null && bedAura.isActivelyMining()) {
            flushLag();
            return;
        }

        if (isScaffoldActive()) {
            flushLag();
            return;
        }

        Autoblock autoblock = ModuleManager.autoblock;
        if (autoblock != null && autoblock.isActive()) {
            flushLag();
            return;
        }

        double rangeSq = range.getInput() * range.getInput();
        boolean moving = isMoving();
        EntityPlayer nextTarget = findLagTarget(rangeSq);
        if (!sameTarget(nextTarget)) {
            flushLag();
            lastDistSq = -1.0D;
            hitMarkedEntityId = -1;
            lastTargetHurtTime = nextTarget != null ? nextTarget.hurtTime : 0;
        }
        currentTarget = nextTarget;

        if (currentTarget == null) {
            flushLag();
            lastDistSq = -1.0D;
            hitMarkedEntityId = -1;
            lastTargetHurtTime = 0;
            hasTarget = false;
            return;
        }

        double distanceSq = RotationUtils.distanceSqFromEyeToClosestOnAABB(currentTarget);
        int selfHurtTime = mc.thePlayer.hurtTime;
        int targetHurtTime = currentTarget.hurtTime;

        if (isLagging) {
            if (distanceSq > rangeSq) {
                flushLag();
                lastDistSq = distanceSq;
                hitMarkedEntityId = -1;
                lastTargetHurtTime = targetHurtTime;
                return;
            }
            if (lastDistSq >= 0.0D && distanceSq >= lastDistSq) {
                boolean hitHold = hitMarkedEntityId == currentTarget.getEntityId()
                        && distanceSq <= MINIMUM_DISTANCE_SQ && selfHurtTime == 0;
                if (!hitHold) {
                    flushLag();
                    lastDistSq = distanceSq;
                    lastTargetHurtTime = targetHurtTime;
                    return;
                }
            }
            if (selfHurtTime > lastSelfHurtTime) {
                flushLag();
                hitMarkedEntityId = -1;
                lastSelfHurtTime = selfHurtTime;
                lastDistSq = distanceSq;
                lastTargetHurtTime = targetHurtTime;
                return;
            }
            lastSelfHurtTime = selfHurtTime;

            if (Miau.lagHandler != null && outboundLag != null) {
                Miau.lagHandler.releaseExpiredPackets(
                        EnumLagDirection.OUTBOUND,
                        outboundLag,
                        (long) delay.getInput());
            }
            if (!isWeaponAllowed()) {
                flushLag();
                lastDistSq = distanceSq;
                lastTargetHurtTime = targetHurtTime;
                return;
            }
            boolean sprintingNow = mc.thePlayer.isSprinting();
            if (sprintingNow && !lastSprintState) {
                flushLag();
                lastSprintState = sprintingNow;
                lastDistSq = distanceSq;
                lastTargetHurtTime = targetHurtTime;
                return;
            }
            lastSprintState = sprintingNow;
            boolean blockingNow = mc.thePlayer.isBlocking();
            if (blockingNow && !lastBlockingState) {
                flushLag();
                lastBlockingState = blockingNow;
                lastDistSq = distanceSq;
                lastTargetHurtTime = targetHurtTime;
                return;
            }
            lastBlockingState = blockingNow;
            if (isUsingSplashPotion()) {
                flushLag();
                lastDistSq = distanceSq;
                lastTargetHurtTime = targetHurtTime;
                return;
            }

            lastDistSq = distanceSq;
            lastTargetHurtTime = targetHurtTime;
            hasTarget = true;
            return;
        }

        if (selfHurtTime > lastSelfHurtTime) {
            hitMarkedEntityId = -1;
        }
        lastSelfHurtTime = selfHurtTime;
        lastSprintState = mc.thePlayer.isSprinting();
        lastBlockingState = mc.thePlayer.isBlocking();

        if (selfHurtTime == 0 && lastTargetHurtTime == 0 && targetHurtTime > 0) {
            hitMarkedEntityId = currentTarget.getEntityId();
        }
        lastTargetHurtTime = targetHurtTime;

        boolean closing = lastDistSq >= 0.0D && distanceSq < lastDistSq;
        boolean outsideMinimum = distanceSq > MINIMUM_DISTANCE_SQ;
        boolean weaponAllowed = isWeaponAllowed();
        boolean hitStart = hitMarkedEntityId == currentTarget.getEntityId()
                && distanceSq <= MINIMUM_DISTANCE_SQ
                && selfHurtTime == 0
                && moving
                && weaponAllowed;
        lastDistSq = distanceSq;

        if (selfHurtTime == 0 && weaponAllowed && moving
                && ((closing && outsideMinimum) || hitStart)) {
            startLag();
            hasTarget = isLagging;
        }
    }

    private void startLag() {
        if (isLagging || Miau.lagHandler == null) {
            return;
        }
        lastLagStartTime = System.currentTimeMillis();
        outboundLag = new LagRequest(
                EnumLagDirection.ONLY_OUTBOUND,
                new ModuleBackedTimeout(this));
        Miau.lagHandler.requestLag(outboundLag);
        isLagging = true;
    }

    private void flushLag() {
        if (!isLagging) {
            return;
        }
        lastLagEndTime = System.currentTimeMillis();
        LagRequest request = outboundLag;
        outboundLag = null;
        isLagging = false;
        hasTarget = false;
        if (request != null) {
            if (Miau.lagHandler != null) {
                Miau.lagHandler.releaseLag(request);
            }
            else {
                request.getTimeout().forceTimeOut();
            }
        }
    }

    public void flushForCombatAction() {
        if (!isEnabled()) {
            return;
        }
        lastLagEndTime = System.currentTimeMillis();
        if ((int) mode.getInput() == 0) {
            if (Miau.lagManager != null) {
                Miau.lagManager.setDelay(0);
            }
            if (Miau.lagRangePacketController != null) {
                Miau.lagRangePacketController.resetAll(mc.getNetHandler() != null);
            }
            tickIndex = -1;
            delayCounter = 0L;
            hasTarget = false;
            return;
        }
        flushLag();
    }

    private boolean isWeaponAllowed() {
        return !weaponsOnly.isToggled()
                || ItemUtils.hasRawUnbreakingEnchant()
                || allowTools.isToggled() && ItemUtils.isHoldingTool();
    }

    private boolean isUsingSplashPotion() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        return mc.thePlayer.isUsingItem() && stack != null
                && stack.getItem() instanceof ItemPotion
                && ItemPotion.isSplash(stack.getMetadata());
    }

    private boolean isMoving() {
        return mc.thePlayer.moveForward != 0.0F || mc.thePlayer.moveStrafing != 0.0F;
    }

    private boolean isScaffoldActive() {
        Module scaffold = ModuleManager.getModule(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    boolean isValidTarget(EntityPlayer player) {
        if (player == null || player == mc.thePlayer || player == mc.thePlayer.ridingEntity) {
            return false;
        }
        Entity view = mc.getRenderViewEntity();
        if (player == view || view != null && player == view.ridingEntity || player.deathTime > 0) {
            return false;
        }
        if (TeamUtils.isFriend(player)) {
            return false;
        }
        return (!ignoreTeams.isToggled() || !TeamUtils.isSameTeam(player))
                && (!botCheck.isToggled() || !TeamUtils.isBot(player));
    }

    List<EntityPlayer> getValidPlayers() {
        List<EntityPlayer> players = new ArrayList<EntityPlayer>();
        if (mc.theWorld == null) {
            return players;
        }
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityPlayer && isValidTarget((EntityPlayer) entity)) {
                players.add((EntityPlayer) entity);
            }
        }
        return players;
    }

    private EntityPlayer findLagTarget(double maxDistanceSq) {
        if (botCheck.isToggled()) {
            return CombatTargeting.findTarget(maxDistanceSq, ignoreTeams.isToggled());
        }
        EntityPlayer mouseOverTarget = getMouseOverTarget(maxDistanceSq);
        if (mouseOverTarget != null) {
            return mouseOverTarget;
        }
        EntityPlayer closest = null;
        double closestDistanceSq = Double.MAX_VALUE;
        for (EntityPlayer player : getValidPlayers()) {
            double distanceSq = RotationUtils.distanceSqFromEyeToClosestOnAABB(player);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = player;
            }
        }
        return closest;
    }

    private EntityPlayer getMouseOverTarget(double maxDistanceSq) {
        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY
                || !(mc.objectMouseOver.entityHit instanceof EntityPlayer)) {
            return null;
        }
        EntityPlayer player = (EntityPlayer) mc.objectMouseOver.entityHit;
        return isValidTarget(player)
                && RotationUtils.distanceSqFromEyeToClosestOnAABB(player) <= maxDistanceSq
                ? player : null;
    }

    private boolean shouldResetOnPacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            return true;
        }
        if (packet instanceof C07PacketPlayerDigging) {
            return ((C07PacketPlayerDigging) packet).getStatus()
                    != C07PacketPlayerDigging.Action.RELEASE_USE_ITEM;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            ItemStack stack = ((C08PacketPlayerBlockPlacement) packet).getStack();
            return stack == null || !(stack.getItem() instanceof ItemSword);
        }
        return false;
    }

    private boolean sameTarget(EntityPlayer target) {
        if (currentTarget == null || target == null) {
            return currentTarget == target;
        }
        return currentTarget.getEntityId() == target.getEntityId();
    }

    private Vec3 getReleasedPosition() {
        Vec3 released = (int) mode.getInput() == 0
                ? Miau.lagRangePacketController != null
                    ? Miau.lagRangePacketController.getLastServerPosition() : null
                : Miau.lagHandler != null
                    ? Miau.lagHandler.getLastReleasedServerPosition() : null;
        return released != null
                ? released
                : new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
    }

    private boolean canOperate() {
        return !mc.isSingleplayer() && mc.thePlayer != null && mc.theWorld != null
                && mc.getNetHandler() != null && !mc.thePlayer.isDead;
    }

    public void suspendForTimerRange() {
        syncWithTimerRange();
    }

    private boolean syncWithTimerRange() {
        if (!TimerRange.isLocalActionLockActive()) {
            timerRangeSuspended = false;
            return false;
        }

        if (!timerRangeSuspended) {
            resetState(true);
            timerRangeSuspended = true;
        }
        return true;
    }

    private void resetLagState() {
        currentTarget = null;
        lastDistSq = -1.0D;
        isLagging = false;
        lastSelfHurtTime = 0;
        lastTargetHurtTime = 0;
        hitMarkedEntityId = -1;
        lastSprintState = false;
        lastBlockingState = false;
        outboundLag = null;
        hasTarget = false;
        lastPosition = null;
        currentPosition = null;
        tickIndex = -1;
        delayCounter = 0L;
        correctionCooldownTicks = 0;
    }

    private boolean consumeCorrectionCooldown() {
        if (correctionCooldownTicks <= 0) {
            return false;
        }
        correctionCooldownTicks--;
        tickIndex = -1;
        hasTarget = false;
        if (Miau.lagManager != null) {
            Miau.lagManager.setDelay(0);
        }
        return true;
    }

    private void resetState(boolean flushPackets) {
        flushLag();
        if (Miau.lagManager != null) {
            Miau.lagManager.setDelay(0);
        }
        if (Miau.lagRangePacketController != null) {
            Miau.lagRangePacketController.resetAll(flushPackets && mc.getNetHandler() != null);
        }
        resetLagState();
    }
}
