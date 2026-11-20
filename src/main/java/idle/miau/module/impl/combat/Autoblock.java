package idle.miau.module.impl.combat;

import de.florianmichael.viamcp.fixes.AttackOrder;
import idle.miau.Miau;
import idle.miau.event.PrePlayerInteractEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.RightClickMouseEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.event.UseItemEvent;
import idle.miau.helper.TargetCombatStatus;
import idle.miau.lag.api.EnumLagDirection;
import idle.miau.lag.api.LagRequest;
import idle.miau.lag.timeout.ModuleBackedTimeout;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.MouseEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.setting.Setting;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.CombatTargeting;
import idle.miau.utility.ClientContext;
import idle.miau.utility.rotation.MiauRandom;
import idle.miau.utility.ReflectionUtils;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Mouse;

public class Autoblock extends Module {
    private static final int NORMAL_MODE = 0;
    private static final int SMART_MODE = 1;
    private static final int VANILLA_MODE = 2;
    private static final String[] MODES = {
            "Normal", "Smart", "Vanilla"
    };
    private static final long SMART_BLOCK_DURATION_NANOS = 5_000_000L;

    private final SliderSetting mode;
    private final SliderSetting range;
    private final SliderSetting maxHurtTimeMs;
    private final SliderSetting maxHoldMs;
    private final ButtonSetting requireLmb;
    private final ButtonSetting requireRmb;
    private final ButtonSetting onlyWhenDamaged;
    private final ButtonSetting ignoreTeammates;
    private final SliderSetting lagChance;
    private final SliderSetting lagDelayMs;
    private final ButtonSetting lagFlushAttack;
    private final ButtonSetting lagReblock;
    private final ButtonSetting forceBlockAnimation;
    private final ButtonSetting animationRequireTarget;
    private final GroupSetting blockAnimation;
    private final GroupSetting lagSettings;
    private final GroupSetting condition;
    private final GroupSetting targets;
    public final ButtonSetting onlyKillAura;
    private final TargetCombatStatus normalTargets = new TargetCombatStatus();
    private boolean isBlocking;
    private boolean manualBlock;
    private boolean allowingAlwaysInteraction;
    private int blockStartTick = -1;
    private EntityLivingBase currentTarget;
    private int lastSelfHurtTime;
    private volatile boolean isLagging;
    private int lagStartTick = -1;
    private LagRequest outboundLag;
    private boolean serverBlockActive;
    private int tickCounter;
    private int lastMode = -1;
    private long smartAttackReference;
    private long smartBlockReference;
    private long smartBlockUntilNanos;
    private long hitStatusReference;

    public Autoblock() {
        super("AutoBlock", category.combat);
        setDescription("Help you reduce damage taken.");
        registerSetting(mode = new SliderSetting("Mode", NORMAL_MODE, MODES));
        registerSetting(range = new SliderSetting("Range", 3.1D, 2.0D, 6.0D, 0.1D));
        registerSetting(maxHurtTimeMs = new SliderSetting(
                "Maximum hurt time", "ms", 300.0D, 50.0D, 500.0D, 1.0D));
        registerSetting(maxHoldMs = new SliderSetting(
                "Maximum hold duration", "ms", 250.0D, 50.0D, 500.0D, 1.0D));
        registerSetting(blockAnimation = new GroupSetting("Fake block animation"));
        registerSetting(forceBlockAnimation = new ButtonSetting(blockAnimation, "Force block animation", true));
        registerSetting(animationRequireTarget = new ButtonSetting(blockAnimation, "Only within range", false));
        registerSetting(lagSettings = new GroupSetting("Lag"));
        registerSetting(lagChance = new SliderSetting(
                lagSettings, "Lag Chance", "%", 0.0D, 0.0D, 100.0D, 1.0D, "Lag chance"));
        registerSetting(lagDelayMs = new SliderSetting(
                lagSettings, "Lag Delay", "ms", 100.0D, 50.0D, 500.0D, 1.0D, "Lag max duration"));
        registerSetting(lagFlushAttack = new ButtonSetting(lagSettings, "Prevent delaying attacks", true));
        registerSetting(lagReblock = new ButtonSetting(lagSettings, "Block again immediately", false));
        registerSetting(condition = new GroupSetting("Condition"));
        registerSetting(requireLmb = new ButtonSetting(condition, "Require left mouse", true));
        registerSetting(requireRmb = new ButtonSetting(condition, "Require right mouse", false));
        registerSetting(onlyWhenDamaged = new ButtonSetting(condition, "Damaged", false));
        registerSetting(onlyKillAura = new ButtonSetting(condition, "Only KillAura", false));
        registerSetting(targets = new GroupSetting("Targets"));
        registerSetting(ignoreTeammates = new ButtonSetting(targets, "Ignore teammates", true));
        closetModule = true;
        guiUpdate();
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        condition.setVisible(true, this);
        targets.setVisible(!isNormalMode(), this);
        boolean normalMode = isNormalMode();
        maxHurtTimeMs.setVisible(normalMode, this);
        maxHoldMs.setVisible(normalMode, this);
        blockAnimation.setVisible(true, this);
        lagSettings.setVisible(normalMode, this);
        lagChance.setVisible(isNormalMode(), this);
        lagDelayMs.setVisible(isNormalMode(), this);
        lagFlushAttack.setVisible(isNormalMode(), this);
        lagReblock.setVisible(isNormalMode(), this);
        forceBlockAnimation.setVisible(true, this);
        animationRequireTarget.setVisible(normalMode && forceBlockAnimation.isToggled(), this);
        onlyKillAura.setVisible(!normalMode, this);
        requireLmb.setVisible(true, this);
        requireRmb.setVisible(true, this);
        onlyWhenDamaged.setVisible(normalMode, this);
        ignoreTeammates.setVisible(!normalMode, this);
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            resetState(true);
            lastMode = (int) mode.getInput();
            if (isNormalMode()) {
                ReflectionUtils.setItemInUse(false);
            }
            guiUpdate();
        }
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        guiUpdate();
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        guiUpdate();
        super.guiButtonToggled(setting);
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        serverBlockActive = false;
        lastMode = (int) mode.getInput();
        resetState(false);
    }

    private static int normalTicks(double ms) {
        float ticks = (float) (ms / 50.0D);
        return ticks <= 0.0F ? 0 : (int) Math.ceil((double) ticks);
    }

    @Override
    public void onDisable() {
        resetState(true);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onMouse(MouseEvent event) {
        if (yieldToAuraBlocking()) {
            return;
        }
        if (isNormalMode()) {
            if (isEnabled() && event.button == 1 && Utils.nullCheck()
                    && mc.currentScreen == null && holdingNormalSword()) {
                event.setCanceled(true);
            }
            return;
        }
        if (!Utils.nullCheck() || !Utils.holdingSword()) {
            return;
        }
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
            return;
        }
        if (event.button == 1) {
            if (!event.buttonstate && allowingAlwaysInteraction) {
                allowingAlwaysInteraction = false;
                return;
            }
            if (event.buttonstate && canInteractWhileAlwaysUnblocked()) {
                releaseLag();
                stopBlocking(true);
                manualBlock = false;
                allowingAlwaysInteraction = true;
                return;
            }
            event.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onRightClickMouse(RightClickMouseEvent event) {
        if (yieldToAuraBlocking()) {
            return;
        }
        if (isNormalMode()) {
            return;
        }
        if (shouldBlockVanillaUse()) {
            event.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onUseItem(UseItemEvent event) {
        if (yieldToAuraBlocking()) {
            return;
        }
        if ((!isNormalMode() && allowingAlwaysInteraction) || shouldBlockVanillaUse()) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (yieldToAuraBlocking()) {
            return;
        }
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (!Utils.nullCheck()) {
            syncBlockAnimation();
            return;
        }
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
            ReflectionUtils.setItemInUse(false);
            return;
        }
        if (mc.currentScreen != null && (isBlocking || isLagging)) {
            resetState(true);
            return;
        }
        syncBlockAnimation();
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (!isSmartMode() || !Utils.nullCheck()) {
            return;
        }
        if (event.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
            if (packet.getOpCode() == 2 && packet.getEntity(mc.theWorld) == mc.thePlayer) {
                hitStatusReference = System.currentTimeMillis() - getPingMillis();
            }
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        if (isNormalMode()) {
            resetNormalState(false);
            return;
        }
        tickCounter = 0;
        serverBlockActive = false;
        resetState(true);
    }

    @Subscribe(priority = Priority.HIGH)
    public void onSendPacket(SendPacketEvent event) {
        if (yieldToAuraBlocking()) {
            return;
        }
        if (isNormalMode()) {
            if (isLagging && lagFlushAttack.isToggled()
                    && event.getPacket() instanceof C02PacketUseEntity
                    && ((C02PacketUseEntity) event.getPacket()).getAction() == C02PacketUseEntity.Action.ATTACK) {
                releaseLag();
                if (lagReblock.isToggled() && holdingNormalSword()) {
                    startBlocking(tickCounter);
                }
            }
            return;
        }
        if (!event.isCanceled()) {
            trackServerBlockPacket(event.getPacket());
        }
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
            releaseLag();
            return;
        }
        if (!(event.getPacket() instanceof C02PacketUseEntity)) {
            return;
        }
        if (((C02PacketUseEntity) event.getPacket()).getAction() != C02PacketUseEntity.Action.ATTACK) {
            return;
        }
        if (isSmartMode()) {
            smartAttackReference = System.currentTimeMillis();
        }
    }

    @Subscribe(priority = Priority.LOW)
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        if (yieldToAuraBlocking()) {
            return;
        }
        if (isNormalMode()) {
            return;
        }
        if (!Utils.nullCheck() || mc.thePlayer.isDead || mc.currentScreen != null) {
            resetState(true);
            return;
        }
        if (onlyKillAura.isToggled()) {
            KillAura killAura = ModuleManager.killAura;
            if (killAura == null || !killAura.isEnabled() || (KillAura.target == null && KillAura.attackingEntity == null)) {
                if (isBlocking || isLagging) {
                    releaseLag();
                    stopBlocking(true);
                }
                manualBlock = false;
                return;
            }
        }
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
            resetState(true);
            return;
        }

        if (!Utils.holdingSword()) {
            resetState(false);
            return;
        }
        int selectedMode = (int) mode.getInput();
        if (selectedMode != lastMode) {
            resetState(true);
            lastMode = selectedMode;
        }
        if (allowingAlwaysInteraction) {
            releaseLag();
            stopBlocking(true);
            manualBlock = false;
            return;
        }

        int currentTick = ++tickCounter;
        if (!isNormalMode() && isLagging) {
            releaseLag();
        }
        int selfHurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = selfHurtTime > lastSelfHurtTime;
        lastSelfHurtTime = selfHurtTime;

        boolean physicalLmbDown = Mouse.isButtonDown(0);
        if (isSmartMode()) {
            currentTarget = null;
            boolean smartAssistActive = isSmartAssistActive();
            boolean killAuraAttacking = ModuleManager.killAura != null
                    && ModuleManager.killAura.isEnabled()
                    && !ModuleManager.killAura.isRequireLeftClick()
                    && smartAssistActive;
            boolean rmbDown = Mouse.isButtonDown(1);
            if (!checkConditions(physicalLmbDown || killAuraAttacking, rmbDown)) {
                stopBlocking(true);
                manualBlock = false;
                return;
            }
            tickSmartMode(currentTick);
            return;
        }
        currentTarget = findBlockTarget(physicalLmbDown);
        boolean killAuraAttacking = ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && !ModuleManager.killAura.isRequireLeftClick()
                && currentTarget != null;
        boolean rmbDown = Mouse.isButtonDown(1);
        boolean lmbDown = physicalLmbDown || killAuraAttacking;
        boolean hasTarget = currentTarget != null;
        boolean conditionsMet = hasTarget && checkConditions(lmbDown, rmbDown);

        if (!hasTarget) {
            releaseLag();
            stopBlocking(true);
            manualBlock = false;
            return;
        }

        if (!conditionsMet && rmbDown) {
            if (isLagging) {
                releaseLag();
            }
            if (!isBlocking) {
                startBlocking(currentTick);
            }
            manualBlock = true;
            return;
        }

        if (manualBlock) {
            stopBlocking(true);
            manualBlock = false;
        }

        if (!conditionsMet) {
            stopBlocking(true);
            return;
        }

        if (isVanillaMode()) {
            tickVanillaMode(currentTick);
            return;
        }

    }

    @Subscribe
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (yieldToAuraBlocking()) {
            return;
        }
        if (event.phase != TickEvent.Phase.START || !isNormalMode()) {
            return;
        }
        if (lastMode != NORMAL_MODE) {
            resetState(true);
            lastMode = NORMAL_MODE;
            ReflectionUtils.setItemInUse(false);
        }
        if (!Utils.nullCheck() || mc.thePlayer.isDead || mc.currentScreen != null) {
            resetNormalState(true);
            return;
        }
        int selfHurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = selfHurtTime > lastSelfHurtTime;
        lastSelfHurtTime = selfHurtTime;
        if (!holdingNormalSword()) {
            resetNormalState(false);
            return;
        }
        currentTarget = findNormalTarget();
        tickNormalMode(++tickCounter, Mouse.isButtonDown(0), Mouse.isButtonDown(1), hurtAgain);
    }

    private void tickNormalMode(int currentTick, boolean lmbDown, boolean rmbDown, boolean hurtAgain) {
        if (!lmbDown) {
            if (!rmbDown) {
                resetState(true);
            } else {
                releaseLag();
                if (!isBlocking) {
                    startBlocking(currentTick);
                    manualBlock = true;
                }
            }
            return;
        }
        if (manualBlock) {
            stopBlocking(true);
            manualBlock = false;
        }

        boolean conditionsMet = currentTarget != null && (!requireRmb.isToggled() || rmbDown);
        if (isLagging) {
            int lagTicks = normalTicks(lagDelayMs.getInput());
            boolean lagExpired = lagTicks > 0 && lagStartTick >= 0
                    && currentTick - lagStartTick >= lagTicks;
            if (lagExpired || !conditionsMet) {
                releaseLag();
                if (lagExpired && lagReblock.isToggled() && conditionsMet) {
                    startBlocking(currentTick);
                }
            }
        }
        if (!conditionsMet) {
            stopBlocking(true);
            return;
        }
        if (!isBlocking && !isLagging && shouldStartBlock()) {
            startBlocking(currentTick);
        }
        if (isBlocking) {
            int holdTicks = normalTicks(maxHoldMs.getInput());
            boolean shouldStop = holdTicks > 0 && blockStartTick >= 0
                    && currentTick - blockStartTick >= holdTicks;
            if (onlyWhenDamaged.isToggled() && hurtAgain) {
                shouldStop = true;
            }
            if (shouldStop) {
                if (shouldStartLag()) {
                    startLag(currentTick);
                }
                stopBlocking(true);
            }
        }
    }

    private void tickSmartMode(int currentTick) {
        if (smartBlockUntilNanos > 0L) {
            KeyBinding.setKeyBindState(
                    mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }
        if (isHitSyncWindow()) {
            stopBlocking(true);
            return;
        }

        if (shouldBlockSmartFights()) {
            smartBlockUntilNanos = System.nanoTime() + SMART_BLOCK_DURATION_NANOS;
            if (!isBlocking) {
                startBlocking(currentTick);
            }
            return;
        }

        if (smartBlockUntilNanos > 0L
                && System.nanoTime() > smartBlockUntilNanos) {
            stopBlocking(true);
        }
    }

    private void tickVanillaMode(int currentTick) {
        if (!isBlocking) {
            startBlocking(currentTick);
        }

        EntityLivingBase attackTarget = findVanillaAttackTarget();
        if (attackTarget == null) {
            return;
        }

        while (mc.gameSettings.keyBindAttack.isPressed()) {
            AttackOrder.sendFixedAttack(mc, mc.thePlayer, attackTarget);
        }
    }

    private EntityLivingBase findVanillaAttackTarget() {
        KillAura killAura = ModuleManager.killAura;
        if (killAura != null && killAura.isEnabled() && KillAura.attackingEntity != null) {
            EntityLivingBase target = KillAura.attackingEntity;
            if (target != mc.thePlayer && !target.isDead && target.deathTime == 0) {
                return target;
            }
        }

        double attackRange = Math.min(3.0D, range.getInput());
        MovingObjectPosition hit = RotationUtils.rayTrace(
                attackRange,
                1.0F,
                RotationUtils.serverRotations,
                null);
        if (hit == null) {
            return null;
        }
        return CombatTargeting.asValidPlayer(
                hit.entityHit,
                attackRange * attackRange,
                ignoreTeammates.isToggled());
    }

    private boolean shouldBlockSmartFights() {
        if (!Utils.nullCheck()) {
            return false;
        }

        long now = System.currentTimeMillis();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!SmartAutoblock.isThreat(mc.thePlayer, player, 4.0D, ignoreTeammates.isToggled())) {
                continue;
            }
            float distance = mc.thePlayer.getDistanceToEntity(player);
            boolean attackCooldownReady = distance >= 3.0F
                    || now - smartAttackReference < 100L;
            boolean blockCooldownReady = distance >= 3.0F
                    || now - smartBlockReference >= 200L;
            if (!attackCooldownReady || !blockCooldownReady) {
                continue;
            }

            currentTarget = player;
            smartBlockReference = now;
            return true;
        }

        currentTarget = null;
        return false;
    }

    private boolean isSmartAssistActive() {
        return ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && (KillAura.target instanceof EntityPlayer
                || KillAura.attackingEntity instanceof EntityPlayer);
    }

    private int getRemainingHitSyncTicks() {
        if (hitStatusReference == 0L) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - hitStatusReference;
        int elapsedTicks = (int) (elapsed / 50L);
        return Math.max(0, 20 - elapsedTicks);
    }

    private boolean isHitSyncWindow() {
        return getRemainingHitSyncTicks() > 12;
    }

    private int getPingMillis() {
        if (mc.getNetHandler() == null || mc.thePlayer == null) {
            return 0;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? 0 : Math.max(0, info.getResponseTime());
    }

    public boolean controlsAutoClicker() {
        return false;
    }

    public boolean shouldSuppressUsingItemSlowdown(EntityPlayer player) {
        return false;
    }

    private boolean checkConditions(boolean lmbDown, boolean rmbDown) {
        if (onlyKillAura.isToggled()) {
            KillAura killAura = ModuleManager.killAura;
            if (killAura == null || !killAura.isEnabled() || (KillAura.target == null && KillAura.attackingEntity == null)) {
                return false;
            }
        }
        if (requireLmb.isToggled() && !lmbDown) {
            return false;
        }
        if (requireRmb.isToggled() && !rmbDown) {
            return false;
        }
        return true;
    }

    private EntityPlayer findBlockTarget(boolean lmbDown) {
        KillAura killAura = ModuleManager.killAura;
        boolean auraInputActive = killAura != null
                && killAura.isEnabled()
                && (!killAura.isRequireLeftClick() || lmbDown);
        if (auraInputActive) {
            return killAura.getAutoblockTarget(range.getInput(), isLagging);
        }
        if (onlyKillAura.isToggled()) {
            return null;
        }
        return CombatTargeting.findTarget(
                range.getInput() * range.getInput(), ignoreTeammates.isToggled());
    }

    public void onNormalRay(ClientContext context) {
        if (yieldToAuraBlocking()) {
            return;
        }
        normalTargets.select(context, player -> !AntiBot.isBot(player)
                && !Utils.isFriended(player) && !Utils.isTeammate(player));
    }

    private EntityPlayer findNormalTarget() {
        EntityPlayer target = normalTargets.target();
        float blockRange = (float) range.getInput();
        return target != null && normalTargets.distanceSquared() <= (double) (blockRange * blockRange)
                ? target : null;
    }

    private boolean holdingNormalSword() {
        if (mc.thePlayer == null) {
            return false;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        int id = held == null ? -267 : net.minecraft.item.Item.getIdFromItem(held.getItem()) - 267;
        return Integer.compareUnsigned(id, 16) <= 0 && (66083 >>> (id & 31) & 1) != 0;
    }

    private boolean shouldStartBlock() {
        int ourHurtTime = mc.thePlayer.hurtTime;
        int triggerTick = (int) Math.round((float) (maxHurtTimeMs.getInput() / 50.0D));
        triggerTick = Math.max(1, Math.min(10, triggerTick));
        return !onlyWhenDamaged.isToggled() || ourHurtTime == triggerTick;
    }

    private boolean shouldBlockVanillaUse() {
        return isEnabled()
                && isLagging
                && Utils.nullCheck()
                && (isNormalMode() ? holdingNormalSword() : Utils.holdingSword())
                && mc.currentScreen == null;
    }

    private void startBlocking(int currentTick) {
        if (!(isNormalMode() ? holdingNormalSword() : Utils.holdingSword())) {
            return;
        }
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, true);
        if (!isSmartMode()) {
            KeyBinding.onTick(keyCode);
        }
        isBlocking = true;
        blockStartTick = currentTick;
        syncBlockAnimation();
    }

    private void stopBlocking(boolean forceRelease) {
        if (isNormalMode() || lastMode == NORMAL_MODE) {
            if (isBlocking || forceRelease) {
                if (mc != null && mc.gameSettings != null) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                }
                isBlocking = false;
                blockStartTick = -1;
            }
            return;
        }
        if (!isBlocking && !forceRelease) {
            return;
        }
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(
                keyCode,
                isSmartMode() && Utils.isBindDown(mc.gameSettings.keyBindUseItem));
        isBlocking = false;
        blockStartTick = -1;
        smartBlockUntilNanos = 0L;
        syncBlockAnimation();
    }

    private boolean shouldStartLag() {
        if (!isNormalMode()) {
            return false;
        }
        double chance = lagChance.getInput();
        if (chance <= 0.0D) {
            return false;
        }
        if (chance >= 100.0D) {
            return true;
        }
        return MiauRandom.SHARED.chance((float) (chance / 100.0D));
    }

    private void startLag(int currentTick) {
        if (isLagging) {
            return;
        }
        outboundLag = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
        Miau.lagHandler.requestLag(outboundLag);
        isLagging = true;
        lagStartTick = currentTick;
        syncBlockAnimation();
    }

    private void releaseLag() {
        if (!isLagging && outboundLag == null) {
            return;
        }
        LagRequest request = outboundLag;
        outboundLag = null;
        isLagging = false;
        lagStartTick = -1;
        if (request != null) {
            if (Miau.lagHandler != null) {
                Miau.lagHandler.releaseLag(request);
            }
            else {
                request.getTimeout().forceTimeOut();
            }
        }
        syncBlockAnimation();
    }

    public boolean isActive() {
        if (isNormalMode()) {
            return isEnabled() && (isBlocking || isLagging);
        }
        return isEnabled() && (isBlocking
                || isLagging
                || serverBlockActive
                || outboundLag != null);
    }

    public boolean isLagBlockIntentActive() {
        return isNormalMode() && isActive() && Utils.nullCheck() && holdingNormalSword();
    }

    public boolean ownsKillAuraAttack(EntityLivingBase auraTarget) {
        return false;
    }

    public boolean isNormalMode() {
        return (int) mode.getInput() == NORMAL_MODE;
    }

    private boolean isSmartMode() {
        return (int) mode.getInput() == SMART_MODE;
    }

    public boolean isBlocking() {
        return isBlocking;
    }

    public EntityLivingBase getCurrentTarget() {
        return isNormalMode() ? findNormalTarget() : currentTarget;
    }

    private boolean isVanillaMode() {
        return (int) mode.getInput() == VANILLA_MODE;
    }

    private boolean canInteractWhileAlwaysUnblocked() {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null) {
            return false;
        }
        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return BlockUtils.isInteractable(hit);
        }
        if (hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || hit.entityHit == null) {
            return false;
        }
        Entity entity = hit.entityHit;
        return !(entity instanceof EntityPlayer) || AntiBot.isBot(entity);
    }

    private boolean yieldToAuraBlocking() {
        KillAura aura = ModuleManager.killAura;
        boolean ownsBlocking = aura != null && aura.isEnabled() && aura.getCombatTarget() != null
                && aura.getAutoBlockMode() != KillAura.AutoBlock.None;
        if (ownsBlocking && (isBlocking || isLagging || outboundLag != null)) {
            resetState(true);
        }
        return ownsBlocking;
    }

    private void syncBlockAnimation() {
        if (!isEnabled() || !Utils.nullCheck() || mc.currentScreen != null
                || (isNormalMode() ? !holdingNormalSword() : !Utils.holdingSword())
                || ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
            ReflectionUtils.setItemInUse(false);
            return;
        }
        EntityLivingBase target = isNormalMode() ? findNormalTarget() : currentTarget;
        boolean killAuraAttacking = ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && !ModuleManager.killAura.isRequireLeftClick()
                && target != null;
        boolean requiredMouseButtonsDown = checkConditions(
                Mouse.isButtonDown(0) || killAuraAttacking, Mouse.isButtonDown(1));
        boolean continuousUndamagedBlock = !isSmartMode()
                && !onlyWhenDamaged.isToggled()
                && target != null
                && !allowingAlwaysInteraction;
        boolean activeBlockState = requiredMouseButtonsDown
                && (continuousUndamagedBlock || isBlocking || isLagging);
        boolean withinRange = !isNormalMode() || !animationRequireTarget.isToggled() || target != null;
        ReflectionUtils.setItemInUse(forceBlockAnimation.isToggled() && activeBlockState && withinRange);
    }

    private void resetNormalState(boolean releaseUseKey) {
        releaseLag();
        currentTarget = null;
        ReflectionUtils.setItemInUse(false);
        if (mc != null && mc.gameSettings != null) {
            if (isBlocking || releaseUseKey) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                isBlocking = false;
                blockStartTick = -1;
            }
            manualBlock = false;
            if (releaseUseKey && Mouse.isCreated() && Mouse.isButtonDown(1)
                    && mc.currentScreen == null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            }
            lastSelfHurtTime = 0;
        } else {
            isBlocking = false;
            manualBlock = false;
        }
    }

    private void resetState(boolean releaseUseKey) {
        if (lastMode == NORMAL_MODE || lastMode < 0 && isNormalMode()) {
            resetNormalState(releaseUseKey);
            return;
        }
        resetSmartState();
        stopBlocking(releaseUseKey);
        releaseLag();
        manualBlock = false;
        allowingAlwaysInteraction = false;
        currentTarget = null;
        lastSelfHurtTime = 0;
        ReflectionUtils.setItemInUse(false);
        if (releaseUseKey && Mouse.isCreated() && Mouse.isButtonDown(1)
                && mc.currentScreen == null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }
    }

    private void resetSmartState() {
        long now = System.currentTimeMillis();
        smartAttackReference = now;
        smartBlockReference = now - 200L;
        smartBlockUntilNanos = 0L;
        hitStatusReference = 0L;
    }

    private void trackServerBlockPacket(Packet<?> packet) {
        if (!isNormalMode() && !isVanillaMode()) {
            return;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            serverBlockActive = true;
            return;
        }
        if (packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus()
                == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            serverBlockActive = false;
        }
    }
}
