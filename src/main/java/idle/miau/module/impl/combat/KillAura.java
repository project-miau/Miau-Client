package idle.miau.module.impl.combat;

import com.google.gson.JsonObject;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.PostPlayerInputEvent;
import idle.miau.event.PrePlayerInteractEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.helper.RotationPriority;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.MouseEvent;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.CombatAim;
import idle.miau.utility.Utils;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class KillAura extends Module {
    public static KillAura instance;
    public static SliderSetting aps,
            autoBlockSetting,
            autoBlockLag,
            fov,
            attackRangeSetting,
            swingRangeSetting,
            blockRangeSetting,
            rotationSetting,
            rotationSmoothing,
            sortSetting;
    public static RangeSetting autoBlockAps;
    public static GroupSetting targetSettings;
    public static ButtonSetting aimInvis,
            attackMobs,
            attackPlayers,
            hitThroughBlocks,
            ignoreTeammates,
            manualBlock,
            weaponOnly;

    public enum AutoBlock {
        None(false, false, false),
        Vanilla(false, false, false),
        Smart(false, false, false),
        Fake(false, false, false),
        Legit(true, true, true);

        public final boolean interacts;
        public final boolean packet;
        public final boolean lags;

        AutoBlock(boolean interacts, boolean packet, boolean lags) {
            this.interacts = interacts;
            this.packet = packet;
            this.lags = lags;
        }
    }

    public enum Rotation {
        Silent,
        Lock_view,
        None
    }

    public enum Sort {
        Distance,
        Health,
        Hurt_time,
        Yaw
    }

    public static class Target {
        public final Entity entity;
        public final boolean living;

        Target(Entity entity) {
            this.entity = entity;
            this.living = entity instanceof EntityLivingBase;
        }

        public float maxHealth() {
            return this.living ? ((EntityLivingBase) this.entity).getMaxHealth() : 20.0f;
        }

        public float health() {
            return this.living ? ((EntityLivingBase) this.entity).getHealth() : 20.0f;
        }

        public boolean exists() {
            return this.entity != null
                    && Minecraft.getMinecraft().theWorld != null
                    && Minecraft.getMinecraft().theWorld.getEntityByID(this.entity.getEntityId())
                            != null;
        }
    }

    private static class Timer {
        long last = 0L;

        void reset() {
            this.last = System.currentTimeMillis();
        }

        boolean passed(long ms) {
            return System.currentTimeMillis() - this.last >= ms;
        }
    }

    public static AutoBlock autoBlock = AutoBlock.None;
    public static Rotation rotation = Rotation.Silent;
    public static Sort sort = Sort.Distance;
    private static Target auraTarget;
    public static EntityLivingBase target;
    public static EntityLivingBase attackingEntity;
    public static List<EntityLivingBase> targetList = Collections.emptyList();
    public final SliderSetting rotationMode;
    private static Target previousTarget;
    public static boolean fakeBlocking;
    public static boolean serverBlocking;
    public static boolean slowedThisTick;
    public static boolean disabled = true;
    public static int blockState = -1;
    public static double attackRange, swingRange, blockRange, scanRangeSq;
    public static double currentAps;
    private static double minimumAps, maximumAps;
    private static final int TARGET_COUNT = 3;
    private static final long SWITCH_DELAY = 150L;
    private static final Timer attackTimer = new Timer();
    private static long attackDelay;
    private static boolean attackQueued;
    private static int noAttackTicks;
    private static int badSwapTicks;
    private static boolean inSwingRange;
    private static float[] rotations;
    private static boolean returning;
    private static float lastClientYaw, lastClientPitch;
    private static Vec3 aimPoint;
    private static final HashMap<Integer, Vec3> aimPoints = new HashMap<Integer, Vec3>();
    private static final HashMap<UUID, Long> switchTimes = new HashMap<UUID, Long>();
    private static final List<Packet<?>> held =
            Collections.synchronizedList(new ArrayList<Packet<?>>());
    private static boolean lagging;
    private static int lagAccumulator;
    private static boolean attackedThisTick;
    public static boolean attacking;
    private static boolean releasing;
    private static boolean droppedDig;
    private static boolean passedRightClick;
    private static final SmartAutoblock smartAutoblock = new SmartAutoblock();
    private static int enabledTick;
    public static boolean bedAuraRotated;

    public KillAura() {
        super("KillAura", category.combat);
        instance = this;
        setDescription("Automatically attacks and blocks against nearby targets.");
        registerSetting(
                rotationSetting =
                        new SliderSetting(
                                "Rotation mode", 0, new String[] {"Silent", "Lock view", "None"}));
        registerSetting(
                rotationSmoothing =
                        new SliderSetting(
                                "Smoothing", "", 0.0, 0.0, 8.0, 0.1, "Rotation smoothing",
                                "Rotation Smoothing"));
        rotationSmoothing.setMinString("§cDisabled");
        registerSetting(aps = new SliderSetting("APS", 12.0, 1.0, 20.0, 0.5));
        registerSetting(
                attackRangeSetting = new SliderSetting(
                        "Attack Range", "", 3.0, 3.0, 10.0, 0.1, "Range (attack)"));
        registerSetting(
                swingRangeSetting = new SliderSetting(
                        "Swing Range", "", 4.0, 3.0, 10.0, 0.1, "Range (swing)"));
        registerSetting(fov = new SliderSetting("FOV", 360.0, 20.0, 360.0, 5.0));
        registerSetting(
                autoBlockSetting =
                        new SliderSetting(
                                "AutoBlock",
                                0,
                                new String[] {"None", "Vanilla", "Smart", "Fake", "Legit"}) {
                            @Override
                            public void loadConfig(JsonObject data) {
                                String key = data.has(getConfigKey()) ? getConfigKey() : getName();
                                if (data.has(key) && data.get(key).isJsonPrimitive()) {
                                    try {
                                        double value = data.get(key).getAsDouble();
                                        if (value < getMin() || value > getMax()) {
                                            setValue(0);
                                            return;
                                        }
                                    } catch (NumberFormatException exception) {
                                        return;
                                    }
                                }
                                super.loadConfig(data);
                            }
                        });
        registerSetting(
                autoBlockLag =
                        new SliderSetting(
                                "AutoBlock lag", "%", "§cDisabled", 100.0, 0.0, 100.0, 2.0));
        registerSetting(
                blockRangeSetting = new SliderSetting(
                        "AutoBlock Range", "", 6.0, 3.0, 10.0, 0.1, "Range (block)"));
        registerSetting(
                autoBlockAps = new RangeSetting(
                        "AutoBlock Min/Max", " APS", 1.0, 20.0, 10.0, 10.0, 0.5,
                        "AutoBlock APS"));
        registerSetting(manualBlock = new ButtonSetting("Require RightHold", true, "Manual block"));
        registerSetting(
                sortSetting =
                        new SliderSetting(
                                "Sort",
                                0,
                                new String[] {"Distance", "Health", "Hurt time", "Yaw"},
                                "Sort mode"));
        registerSetting(targetSettings = new GroupSetting("Targets"));
        registerSetting(attackPlayers = new ButtonSetting(targetSettings, "Players", true,
                "Attack players"));
        registerSetting(attackMobs = new ButtonSetting(targetSettings, "Mobs", true, "Attack mobs"));
        registerSetting(
                aimInvis =
                        new ButtonSetting(
                                targetSettings,
                                "Attack Invisible",
                                false,
                                "Aim invis",
                                "Invisibles",
                                "Targets.Invisibles",
                                "Target invis"));
        registerSetting(ignoreTeammates = new ButtonSetting(targetSettings, "Ignore teammates", true));
        registerSetting(hitThroughBlocks = new ButtonSetting("Hit Through Block", false,
                "Hit through blocks"));
        registerSetting(weaponOnly = new ButtonSetting("Weapon Only", true, "Weapon only"));
        rotationMode = rotationSetting;
        guiUpdate();
    }

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    public EntityLivingBase getTarget() {
        return target;
    }

    public Entity getCombatTarget() {
        return auraTarget == null ? null : auraTarget.entity;
    }

    public boolean isFakeBlocking() {
        return isEnabled() && fakeBlocking;
    }

    public float[] getRotations(float yaw, float pitch) {
        if (rotation == Rotation.None) {
            return null;
        }
        return takeRotations();
    }

    public static boolean hasTarget() {
        return exists(auraTarget);
    }

    private static boolean exists(Target t) {
        return t != null && t.exists();
    }

    private static boolean smoothReturn() {
        Minecraft mc = mc();
        if (!(rotationSmoothing.getInput() == rotationSmoothing.getMin())
                && (mc.objectMouseOver == null
                        || mc.objectMouseOver.typeOfHit
                                == MovingObjectPosition.MovingObjectType.MISS)
                && !KillAuraSupport.scaffoldActive()
                && !KillAuraSupport.blockInActive()
                && !KillAuraSupport.bedAuraActive()) {
            ItemStack itemStack = mc.thePlayer.getHeldItem();
            Item item = itemStack == null ? null : itemStack.getItem();
            if (item != null) {
                if (item == Items.fishing_rod || item == Items.bow) {
                    return false;
                }
                if (KillAuraSupport.isThrowable(itemStack)) {
                    return false;
                }
            }
            float lastYaw = idle.miau.utility.RotationUtils.serverRotations[0];
            float lastPitch = idle.miau.utility.RotationUtils.serverRotations[1];
            float yaw = mc.thePlayer.rotationYaw;
            float pitch = mc.thePlayer.rotationPitch;
            float dYaw = Math.abs(MathHelper.wrapAngleTo180_float(yaw - lastClientYaw));
            float dPitch = Math.abs(pitch - lastClientPitch);
            lastClientYaw = yaw;
            lastClientPitch = pitch;
            if (dYaw > 20.0f || dPitch > 8.0f) {
                return false;
            }
            rotations = new float[] {yaw, pitch};
            smooth(rotations, lastYaw, lastPitch, 1.5f);
            boolean still =
                    Math.abs(
                                            MathHelper.wrapAngleTo180_float(
                                                    rotations[0] - mc.thePlayer.rotationYaw))
                                    > 3.0f
                            || Math.abs(rotations[1] - mc.thePlayer.rotationPitch) > 3.0f;
            rotations =
                    KillAuraSupport.fixSensitivity(rotations[0], rotations[1], lastYaw, lastPitch);
            return still;
        }
        return false;
    }

    public static void onRenderTick() {
        Minecraft mc = mc();
        if (instance != null
                && instance.isEnabled()
                && !KillAuraSupport.itemActionActive()
                && mc.currentScreen == null
                && currentAps > 0.0
                && (attackTimer.passed(attackDelay)
                        || previousTarget == null && auraTarget != null)) {
            attackTimer.reset();
            setDelay(false);
            if (auraTarget != null) {
                attackQueued = true;
            }
        }
    }

    private static void release(boolean all) {
        if (held.isEmpty()) {
            return;
        }
        synchronized (held) {
            for (Packet<?> packet : held) {
                if (packet instanceof C02PacketUseEntity) {
                    if (all) continue;
                } else if (all) {
                    if (packet instanceof C0APacketAnimation) continue;
                    if (packet instanceof C07PacketPlayerDigging
                            && ((C07PacketPlayerDigging) packet).getStatus()
                                    == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) {
                        droppedDig = true;
                        continue;
                    }
                }
                KillAuraPackets.sendThroughLag(packet);
            }
        }
        held.clear();
    }

    private static void setDelay(boolean now) {
        if (!now) {
            sampleAps();
        }
        attackDelay =
                now
                        ? 0L
                        : (long)
                                (1000.0 / currentAps
                                        + (double)
                                                java.util.concurrent.ThreadLocalRandom.current()
                                                        .nextInt(-4, 5));
    }

    private static void sampleAps() {
        currentAps = minimumAps + java.util.concurrent.ThreadLocalRandom.current().nextDouble()
                * (maximumAps - minimumAps);
    }

    public void onPostPlayerInput() {
        if ((autoBlock.packet || autoBlock == AutoBlock.Smart) && serverBlocking) {
            KillAuraSupport.slowdown();
        }
        if (slowedThisTick) {
            slowedThisTick = false;
        }
    }

    private static void smooth(float[] rot, float lastYaw, float lastPitch, float extra) {
        float maxYaw = 7.0f;
        float maxPitch = 3.0f;
        float keep =
                (float) (1.0 - Math.min(9.9, rotationSmoothing.getInput() + (double) extra) / 10.0);
        float dYaw = MathHelper.wrapAngleTo180_float(rot[0] - lastYaw);
        float aYaw = Math.abs(dYaw);
        float dPitch = rot[1] - lastPitch;
        float aPitch = Math.abs(dPitch);
        if (aYaw > maxYaw) {
            dYaw = Math.signum(dYaw) * ((aYaw - maxYaw) * keep + maxYaw);
        }
        if (aPitch > maxPitch) {
            dPitch = Math.signum(dPitch) * ((aPitch - maxPitch) * keep + maxPitch);
        }
        rot[0] = lastYaw + dYaw;
        rot[1] = lastPitch + dPitch;
    }

    public static void setBlocking(boolean block, boolean packet) {
        Minecraft mc = mc();
        if (packet) {
            ItemStack itemStack;
            if ((itemStack = mc.thePlayer.inventory.getStackInSlot(KillAuraPackets.serverSlot))
                            == null
                    || !(itemStack.getItem() instanceof ItemSword)) {
                return;
            }
        } else if (block && !KillAuraSupport.holdingSword()) {
            return;
        }
        if (packet) {
            if (block) {
                KillAuraPackets.send(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            } else {
                releasing = true;
                KillAuraPackets.send(
                        new C07PacketPlayerDigging(
                                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                                BlockPos.ORIGIN,
                                EnumFacing.DOWN));
                releasing = false;
            }
            serverBlocking = fakeBlocking = block;
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), block);
        }
    }

    private static void releaseFlow(int state) {
        if (state != 0 || !autoBlock.lags) {
            setBlocking(false, true);
        }
        slowedThisTick = true;
    }

    private static boolean dropSetSlot(S2FPacketSetSlot p) {
        Minecraft mc = mc();
        return auraTarget != null
                && Utils.isHypixel()
                && Utils.getSkyWarsStatus() == 2
                && mc.currentScreen == null
                && mc.thePlayer.inventory.currentItem == p.func_149173_d() - 36
                && KillAuraSupport.holdingSword();
    }

    public void afterDisable() {
        if (mc().thePlayer != null && mc().theWorld != null && blockState != -1) {
            releaseFlow(blockState);
        }
        blockState = -1;
        slowedThisTick = false;
        fakeBlocking = false;
        serverBlocking = false;
        disabled = true;
    }

    private static double hurtTime(EntityLivingBase e) {
        return e.hurtTime;
    }

    @Override
    public void onEnable() {
        smartAutoblock.reset(System.nanoTime());
        disabled = false;
        enabledTick = mc().thePlayer == null ? 0 : mc().thePlayer.ticksExisted;
    }

    @Subscribe
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc().thePlayer) {
            setTarget(null);
            held.clear();
        }
    }

    public static boolean stopBlocking(boolean blocking) {
        if (autoBlock == AutoBlock.Smart && (serverBlocking || fakeBlocking)) {
            releaseSmartBlock(true);
            return true;
        }
        if (blockState != -1) {
            releaseFlow(blockState);
            blockState = -1;
            serverBlocking = fakeBlocking = blocking;
            return true;
        }
        return false;
    }

    public void onPostMotion() {
        if (KillAuraSupport.itemActionActive()) {
            if (autoBlock == AutoBlock.Smart) {
                releaseSmartBlock(true);
            }
            attackedThisTick = false;
            return;
        }
        Minecraft mc = mc();
        if (auraTarget != null) {
            if (inSwingRange && rotation == Rotation.Lock_view) {
                KillAuraSupport.setRotation(
                        RotationHelper.get().getServerYaw() == null
                                ? mc.thePlayer.rotationYaw
                                : RotationHelper.get().getServerYaw(),
                        RotationHelper.get().getServerPitch() == null
                                ? mc.thePlayer.rotationPitch
                                : RotationHelper.get().getServerPitch());
            }
            if (blockAllowed() && !autoBlock.packet) {
                switch (autoBlock) {
                    case Vanilla:
                        {
                            setBlocking(true, false);
                            break;
                        }
                    case Fake:
                        {
                            fakeBlocking = true;
                            break;
                        }
                    default:
                        break;
                }
            }
        }
        if (autoBlock.packet
                && !passedRightClick
                && auraTarget == null
                && mc.currentScreen == null
                && mc.objectMouseOver != null
                && KillAuraSupport.holdingSword()
                && rightDown()) {
            boolean pass =
                    mc.objectMouseOver.entityHit != null
                            && AntiBot.isBot(mc.objectMouseOver.entityHit);
            BlockPos pos;
            if (!pass
                    && (pos = mc.objectMouseOver.getBlockPos()) != null
                    && KillAuraSupport.isInteractable(KillAuraSupport.getBlock(pos))) {
                pass = true;
            }
            if (pass) {
                int key = mc.gameSettings.keyBindUseItem.getKeyCode();
                KeyBinding.setKeyBindState(key, true);
                KeyBinding.onTick(key);
                KeyBinding.setKeyBindState(key, false);
                passedRightClick = true;
            }
        }
        attackedThisTick = false;
    }

    private static void updateRotations() {
        float lastYaw = idle.miau.utility.RotationUtils.serverRotations[0];
        float lastPitch = idle.miau.utility.RotationUtils.serverRotations[1];
        rotations = aim();
        if (rotationSmoothing.getInput() != rotationSmoothing.getMin()) {
            smooth(rotations, lastYaw, lastPitch, 0.0f);
        }
        rotations = KillAuraSupport.fixSensitivity(rotations[0], rotations[1], lastYaw, lastPitch);
    }

    private static double yawDifference(EntityLivingBase e) {
        return KillAuraSupport.absYawDifference(e, false);
    }

    public static void setTarget(Entity entity) {
        if (entity == null) {
            if (autoBlock == AutoBlock.Smart) {
                releaseSmartBlock(true);
            }
            targetList = Collections.emptyList();
            switchTimes.clear();
            if (auraTarget != null) {
                if (autoBlock.packet && (!manualBlock.isToggled() || blockState != -1)) {
                    releaseFlow(blockState);
                }
                fakeBlocking = false;
            } else if (autoBlock != AutoBlock.None && !KillAuraSupport.mouseDown(1)) {
                setBlocking(false, false);
            }
            serverBlocking = false;
            inSwingRange = false;
            if (blockState != -1) {
                blockState = -1;
            }
            if (!hitThroughBlocks.isToggled()) {
                aimPoint = null;
                aimPoints.clear();
            }
        } else if (!hitThroughBlocks.isToggled()) {
            aimPoint = aimPoints.get(entity.getEntityId());
        }
        previousTarget = auraTarget;
        if (auraTarget != null && auraTarget.entity == entity) {
            return;
        }
        auraTarget = entity != null ? new Target(entity) : null;
        target = entity instanceof EntityLivingBase ? (EntityLivingBase) entity : null;
        targetList = target == null ? Collections.emptyList() : Collections.singletonList(target);
    }

    @Override
    public String getInfo() {
        if (rotation == Rotation.None) {
            return Integer.toString((int) fov.getInput());
        }
        return rotationSetting.getOptions()[(int) rotationSetting.getInput()];
    }

    @Override
    public void onDisable() {
        if (mc().thePlayer == null || mc().theWorld == null) {
            clearWorldState();
            return;
        }
        if (autoBlock == AutoBlock.Smart) {
            releaseSmartBlock(true);
        }
        if (autoBlock != AutoBlock.None && !rightDown() && mc().thePlayer.isUsingItem()) {
            setBlocking(false, false);
        }
        returning = false;
        attackedThisTick = false;
        droppedDig = false;
        bedAuraRotated = false;
        passedRightClick = false;
        inSwingRange = false;
        attackQueued = false;
        previousTarget = null;
        auraTarget = null;
        target = null;
        targetList = Collections.emptyList();
        attackingEntity = null;
        attacking = false;
        rotations = null;
        aimPoint = null;
        aimPoints.clear();
        switchTimes.clear();
        badSwapTicks = 0;
        noAttackTicks = 0;
        attackDelay = 0L;
        release(true);
        KillAuraLifecycle.queueDisable(this);
    }

    private static void updateAps() {
        boolean blocking = blockAllowed();
        double minimum = blocking ? autoBlockAps.getMinimum() : aps.getInput();
        double maximum = blocking ? autoBlockAps.getMaximum() : minimum;
        if (minimumAps != minimum || maximumAps != maximum) {
            minimumAps = minimum;
            maximumAps = maximum;
            sampleAps();
            setDelay(true);
        }
    }

    public boolean onSendPacket(Packet<?> packet) {
        if (KillAuraSupport.itemActionActive()) {
            return true;
        }
        Minecraft mc = mc();
        if (autoBlock == AutoBlock.None) {
            return true;
        }
        if (packet instanceof C09PacketHeldItemChange) {
            if (autoBlock == AutoBlock.Smart
                    && ((C09PacketHeldItemChange) packet).getSlotId() != KillAuraPackets.previousServerSlot) {
                releaseSmartBlock(true);
            }
            if (autoBlock.packet) {
                C09PacketHeldItemChange p = (C09PacketHeldItemChange) packet;
                if (KillAuraSupport.isSword(mc.thePlayer.inventory.getStackInSlot(p.getSlotId()))) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    badSwapTicks = 1;
                } else if (rightDown() && !KillAuraSupport.scaffoldActive()) {
                    ItemStack itemStack =
                            mc.thePlayer.inventory.getStackInSlot(
                                    KillAuraPackets.previousServerSlot);
                    if (itemStack != null && itemStack.getItem() instanceof ItemSword) {
                        KeyBinding.setKeyBindState(
                                mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    }
                }
            }
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            if (badSwapTicks > 0) {
                KillAuraPackets.lastUse = 3;
                return false;
            }
            C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) packet;
            if (KillAuraSupport.isSword(p.getStack()) && p.getPlacedBlockDirection() != 255) {
                Block block = KillAuraSupport.getBlock(p.getPosition());
                if (!KillAuraSupport.isInteractable(block)) {
                    return false;
                }
            }
        } else if (packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging p = (C07PacketPlayerDigging) packet;
            if (p.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                if (autoBlock == AutoBlock.Smart) {
                    serverBlocking = false;
                    fakeBlocking = smartAutoblock.isActive();
                }
                if (!releasing
                        && autoBlock.packet
                        && KillAuraSupport.holdingSword()
                        && mc.thePlayer.ticksExisted != enabledTick) {
                    KillAuraPackets.lastUse = 0;
                    return false;
                }
            } else if (droppedDig
                    && (p.getStatus() == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK
                            || p.getStatus() == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK)) {
                droppedDig = false;
                return false;
            }
        } else if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity p = (C02PacketUseEntity) packet;
            if (auraTarget == null) {
                if (autoBlock.packet
                        && blockState == -1
                        && badSwapTicks > 0
                        && p.getAction() != C02PacketUseEntity.Action.ATTACK) {
                    return false;
                }
            } else if (autoBlock.packet) {
                if (p.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    if (!attacking
                            && !(p.getEntityFromWorld(mc.theWorld) instanceof EntityFireball)) {
                        return false;
                    }
                } else if (!autoBlock.interacts) {
                    badSwapTicks = 1;
                    return false;
                }
            }
        }
        if (!(!autoBlock.lags
                || mc.isSingleplayer()
                || !lagging && held.isEmpty()
                || blockState != -1
                        && packet instanceof C08PacketPlayerBlockPlacement
                        && packet != KillAuraPackets.sending)) {
            switch (blockState) {
                case -1:
                    {
                        if (packet instanceof C03PacketPlayer) {
                            release(true);
                            lagging = false;
                        } else if (!held.isEmpty()) {
                            held.add(packet);
                            return false;
                        }
                        return true;
                    }
                case 1:
                    {
                        held.add(packet);
                        if (packet instanceof C03PacketPlayer) {
                            release(false);
                        }
                        return false;
                    }
                default:
                    break;
            }
            held.add(packet);
            if (!lagging && packet instanceof C03PacketPlayer) {
                release(true);
            }
            return false;
        }
        if (autoBlock == AutoBlock.Smart && packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
            releaseSmartBlock(false);
            smartAutoblock.onAttack(System.nanoTime());
        }
        return true;
    }

    private static float[] aim() {
        if (aimPoint != null) {
            return KillAuraSupport.toVec(aimPoint);
        }
        return KillAuraSupport.toEntityBox(auraTarget.entity);
    }

    public boolean allowHeldItemPacket() {
        return KillAuraSupport.itemActionActive() || !(autoBlock.packet
                && KillAuraPackets.slotChangesPost > 1
                && KillAuraPackets.slotPost == KillAuraPackets.previousSlotPost);
    }

    @Subscribe
    public void onClientTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = mc();
        if (autoBlock.lags
                && blockState != -1
                && (mc.theWorld == null || mc.currentScreen instanceof GuiGameOver)) {
            blockState = -1;
            held.clear();
        }
    }

    private static void findTarget() {
        Minecraft mc = mc();
        if (weaponOnly.isToggled() && !KillAuraSupport.holdingWeapon()) {
            setTarget(null);
            fakeBlocking = false;
            return;
        }
        if (KillAuraSupport.blockInActive()) {
            setTarget(null);
            return;
        }
        if (KillAuraSupport.scaffoldActive()
                && KillAuraSupport.scaffoldSwaps()
                && KillAuraPackets.slotPost != mc.thePlayer.inventory.currentItem) {
            setTarget(null);
            return;
        }
        double nearest = -1.0;
        EntityLivingBase nearestEntity = null;
        List<EntityLivingBase> list = new ArrayList<EntityLivingBase>();
        HashSet<UUID> seen = new HashSet<UUID>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living == mc.thePlayer || living.deathTime != 0) continue;
            if (!aimInvis.isToggled() && KillAuraSupport.isInvisibleUnarmoured(living)) continue;
            boolean player = living instanceof EntityPlayer;
            if (!(player
                    ? attackPlayers.isToggled()
                    : attackMobs.isToggled()
                            && KillAuraSupport.isMob(living)
                            && !Utils.isTeammate(living))) continue;
            if (seen.contains(living.getUniqueID())) continue;
            double d = mc.thePlayer.getDistanceSqToEntity(living);
            if (d > scanRangeSq) continue;
            if (fov.getInput() != 360.0 && !KillAuraSupport.inFov((float) fov.getInput(), living))
                continue;
            if (player && !KillAuraSupport.eligiblePlayer((EntityPlayer)living)) continue;
            if (!hitThroughBlocks.isToggled()) {
                Vec3 point = KillAuraSupport.visiblePoint(living);
                if (point == null) continue;
                aimPoints.put(living.getEntityId(), point);
            }
            seen.add(living.getUniqueID());
            if (nearest == -1.0 || nearest > d) {
                nearest = d;
                nearestEntity = living;
            }
            list.add(living);
        }
        List<EntityLivingBase> inRange = null;
        boolean sameRange = swingRange == attackRange;
        Iterator<EntityLivingBase> it = list.iterator();
        while (it.hasNext()) {
            EntityLivingBase e = it.next();
            if (inRange == null) {
                if (!KillAuraSupport.rayHits(e, swingRange)) {
                    it.remove();
                    continue;
                }
                if (!sameRange && !KillAuraSupport.rayHits(e, attackRange)) continue;
                inRange = new ArrayList<EntityLivingBase>();
                inRange.add(e);
                continue;
            }
            if (!KillAuraSupport.rayHits(e, attackRange)) continue;
            inRange.add(e);
        }
        if (inRange != null) {
            list = inRange;
        }
        if (list.isEmpty()) {
            if (nearestEntity != null
                    && blockRange > swingRange
                    && KillAuraSupport.rayHits(nearestEntity, blockRange)) {
                inSwingRange = false;
                setTarget(nearestEntity);
            } else {
                setTarget(null);
            }
        } else {
            Comparator<EntityLivingBase> comparator = null;
            switch (sort) {
                case Distance:
                    {
                        comparator = Comparator.comparingDouble(KillAura::distanceSq);
                        break;
                    }
                case Health:
                    {
                        comparator = Comparator.comparingDouble(EntityLivingBase::getHealth);
                        break;
                    }
                case Hurt_time:
                    {
                        comparator = Comparator.comparingDouble(KillAura::hurtTime);
                        break;
                    }
                case Yaw:
                    {
                        comparator = Comparator.comparingDouble(KillAura::yawDifference);
                    }
            }
            list.sort(comparator);
            long delay = SWITCH_DELAY;
            long now = System.currentTimeMillis();
            long cycle = Math.min(list.size(), TARGET_COUNT) * delay;
            Entity chosen = null;
            Iterator<EntityLivingBase> it2 = list.iterator();
            while (it2.hasNext()) {
                EntityLivingBase e = it2.next();
                UUID uuid = e.getUniqueID();
                Long l = switchTimes.get(uuid);
                if (l == null) continue;
                if (l > 0L) {
                    if (Math.abs(now - l) < delay) {
                        chosen = e;
                        break;
                    }
                    if (cycle != delay) {
                        switchTimes.put(uuid, -l.longValue());
                        continue;
                    }
                    switchTimes.remove(uuid);
                    continue;
                }
                if (Math.abs(now - -l.longValue()) < cycle) continue;
                switchTimes.remove(uuid);
            }
            if (chosen == null) {
                it2 = list.iterator();
                while (it2.hasNext()) {
                    EntityLivingBase e = it2.next();
                    if (switchTimes.get(e.getUniqueID()) != null) continue;
                    switchTimes.put(e.getUniqueID(), now);
                    chosen = e;
                    break;
                }
                if (chosen == null && exists(previousTarget)) {
                    chosen = previousTarget.entity;
                }
            }
            FightBot bot = idle.miau.module.ModuleManager.fightBot;
            if (bot != null && bot.isEnabled() && list.contains(bot.getTarget())) chosen = bot.getTarget();
            inSwingRange = true;
            setTarget(chosen);
        }
    }

    private static boolean slotFree(boolean attack) {
        return KillAuraSupport.slotFree(attack);
    }

    private static boolean dropWindowItems(S30PacketWindowItems p) {
        Minecraft mc = mc();
        return auraTarget != null
                && Utils.isHypixel()
                && Utils.getSkyWarsStatus() == 2
                && mc.currentScreen == null
                && p.getItemStacks().length == 45
                && KillAuraSupport.holdingSword()
                && KillAuraSupport.sameItem(
                        p.getItemStacks()[mc.thePlayer.inventory.currentItem + 36],
                        mc.thePlayer.getHeldItem());
    }

    public boolean onReceivePacket(Packet packet) {
        if (autoBlock == AutoBlock.Smart && packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
            if (status.getOpCode() == 2 && status.getEntity(mc().theWorld) == mc().thePlayer) {
                NetworkPlayerInfo info = mc().getNetHandler() == null || mc().thePlayer.getUniqueID() == null ? null
                        : mc().getNetHandler().getPlayerInfo(mc().thePlayer.getUniqueID());
                smartAutoblock.onHit(System.nanoTime(), info == null ? 0 : info.getResponseTime());
                releaseSmartBlock(false);
            }
        }
        if (packet instanceof S2FPacketSetSlot) {
            return !dropSetSlot((S2FPacketSetSlot) packet);
        }
        if (packet instanceof S30PacketWindowItems) {
            return !dropWindowItems((S30PacketWindowItems) packet);
        }
        return true;
    }

    public void onPreUpdate() {
        Minecraft mc = mc();
        guiUpdate();
        if (KillAuraSupport.itemActionActive()) {
            suspendForItemUse();
            return;
        }
        if (mc.currentScreen != null || KillAuraPackets.sentCloseWindow) {
            boolean wasBlocking = blockState != -1 || fakeBlocking || serverBlocking;
            setTarget(null);
            if (wasBlocking) {
                setBlocking(false, false);
            }
            release(true);
            fakeBlocking = serverBlocking = lagging = slowedThisTick = false;
            returning = false;
            rotations = null;
            attackQueued = false;
            return;
        }
        findTarget();
        updateAps();
        boolean canAttack = true;
        if (noAttackTicks > 0) {
            --noAttackTicks;
        }
        if (badSwapTicks > 0) {
            --badSwapTicks;
        }
        if (!exists(auraTarget)) {
            canAttack = false;
        }
        if (auraTarget != null) {
            if (inSwingRange) {
                updateRotations();
                if (!(rotationSmoothing.getInput() == rotationSmoothing.getMin())) {
                    lastClientYaw = mc.thePlayer.rotationYaw;
                    lastClientPitch = mc.thePlayer.rotationPitch;
                    returning = true;
                }
            }
            if (autoBlock.packet) {
                if (!blockAllowed() || !KillAuraSupport.holdingSword()) {
                    if (blockState != -1) {
                        releaseFlow(blockState);
                        blockState = -1;
                        fakeBlocking = false;
                        serverBlocking = false;
                        canAttack = false;
                    }
                } else {
                    if (blockState == 0) {
                        setBlocking(false, false);
                    }
                    if (slotFree(false)) {
                        switch (blockState) {
                            case 0:
                                if (currentAps >= 10.0) {
                                    attackQueued = true;
                                }
                                if (attackQueued) {
                                    ++blockState;
                                }
                                break;
                            case -1:
                            case 1:
                                rollLag();
                                if ((blockState == 1 || KillAuraPackets.lastUse == 1)
                                        && (serverBlocking || KillAuraPackets.lastUse == 1)) {
                                    setBlocking(false, true);
                                    if (lagging) {
                                        fakeBlocking = true;
                                    }
                                }
                                blockState = 0;
                                canAttack = false;
                                break;
                            default:
                                break;
                        }
                    } else {
                        if (KillAuraSupport.bedAuraAllows()) {
                            canAttack = false;
                        }
                        blockState = -1;
                    }
                }
            }
        }
        if (!inSwingRange && returning) {
            returning = smoothReturn();
        }
        if (!slotFree(true)) {
            canAttack = false;
        }
        if (attackQueued && KillAuraPackets.noAttackPlaceOrDig()) {
            attackQueued = false;
            if (canAttack) {
                Entity entity = auraTarget.entity;
                boolean swinging = inSwingRange;
                boolean inReach = false;
                if (swinging) {
                    boolean sameRange = attackRange == swingRange;
                    inReach =
                            sameRange
                                            && (rotationSmoothing.getInput()
                                                    == rotationSmoothing.getMin())
                                    || KillAuraSupport.rayHits(
                                            entity, rotations[0], rotations[1], attackRange);
                }
                if (swinging) {
                    boolean attack = true;
                    boolean swing = true;
                    if (noAttackTicks > 0) {
                        attack = false;
                    } else if (!inReach) {
                        attack = false;
                    }
                    if (KillAuraSupport.holdAttack(entity)) {
                        swing = false;
                        attack = false;
                    }
                    if (attack) {
                        if (autoBlock == AutoBlock.Smart) {
                            releaseSmartBlock(false);
                        }
                        attackedThisTick = true;
                        attacking = true;
                        KillAuraSupport.attack(entity, false);
                        attacking = false;
                    } else if (swing) {
                        KillAuraSupport.swing(false, false);
                    } else {
                        setDelay(true);
                    }
                }
                if (blockAllowed() && blockState == 1 && autoBlock.interacts) {
                    boolean block = true;
                    if (inReach) {
                        MovingObjectPosition hit =
                                KillAuraSupport.rayTrace(
                                        entity,
                                        attackRange + 0.3,
                                        mc.thePlayer.posX,
                                        mc.thePlayer.posY,
                                        mc.thePlayer.posZ,
                                        rotations[0],
                                        rotations[1]);
                        if (hit != null) {
                            Vec3 vec3 =
                                    new Vec3(
                                            hit.hitVec.xCoord - entity.posX,
                                            hit.hitVec.yCoord - entity.posY,
                                            hit.hitVec.zCoord - entity.posZ);
                            KillAuraPackets.send(new C02PacketUseEntity(entity, vec3));
                            KillAuraPackets.send(
                                    new C02PacketUseEntity(
                                            entity, C02PacketUseEntity.Action.INTERACT));
                        } else {
                            block = false;
                        }
                    }
                    if (!KillAuraSupport.isSword(
                            mc.thePlayer.inventory.getStackInSlot(KillAuraPackets.serverSlot))) {
                        block = false;
                    }
                    if (block) {
                        setBlocking(true, true);
                    }
                }
            }
        }
        if (autoBlock == AutoBlock.Smart) {
            updateSmartBlocking();
        }
    }

    public void suspendForItemUse() {
        Minecraft mc = mc();
        if (autoBlock == AutoBlock.Smart) {
            releaseSmartBlock(true);
        }
        if (blockState != -1 || serverBlocking) {
            setBlocking(false, true);
        }
        if (fakeBlocking || serverBlocking || mc.thePlayer.isUsingItem()) {
            setBlocking(false, false);
        }
        release(false);
        blockState = -1;
        fakeBlocking = serverBlocking = lagging = slowedThisTick = false;
        rotations = null;
        returning = false;
    }

    private static boolean rightDown() {
        return KillAuraSupport.mouseDown(1);
    }

    private static void updateSmartBlocking() {
        Minecraft mc = mc();
        if (!exists(auraTarget) || mc.thePlayer.isDead || mc.currentScreen != null
                || !blockAllowed() || !KillAuraSupport.holdingSword() || !slotFree(false)
                || KillAuraPackets.serverSlot != mc.thePlayer.inventory.currentItem) {
            releaseSmartBlock(true);
            return;
        }
        boolean block = smartAutoblock.update(mc, blockRange, ignoreTeammates.isToggled(), System.nanoTime());
        if (block && !serverBlocking) {
            setBlocking(true, true);
        } else if (!block && serverBlocking) {
            releaseSmartBlock(false);
        }
        fakeBlocking = block;
    }

    private static void releaseSmartBlock(boolean reset) {
        if (serverBlocking) {
            releasing = true;
            try {
                KillAuraPackets.send(new C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            } finally {
                releasing = false;
            }
        }
        serverBlocking = false;
        if (reset) {
            smartAutoblock.reset(System.nanoTime());
        }
        fakeBlocking = smartAutoblock.isActive();
    }

    private static void rollLag() {
        if ((lagAccumulator += (int) autoBlockLag.getInput()) >= 100) {
            lagAccumulator -= 100;
            lagging = true;
        } else {
            lagging = false;
        }
    }

    private static double distanceSq(EntityLivingBase e) {
        return mc().thePlayer.getDistanceSqToEntity(e);
    }

    @Subscribe
    public void onMouse(MouseEvent e) {
        Minecraft mc = mc();
        if (e.button == 0) {
            if (e.buttonstate && auraTarget != null) {
                if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
                    e.setCanceled(true);
                } else if (autoBlock.packet && blockAllowed()) {
                    e.setCanceled(true);
                }
            }
        } else if (e.button == 1) {
            if (!e.buttonstate) {
                passedRightClick = false;
            }
            if (autoBlock.packet) {
                if (KillAuraSupport.holdingSword()) {
                    int n;
                    if (auraTarget != null
                            && manualBlock.isToggled()
                            && blockState == -1
                            && (n = KillAuraSupport.heldHotbarKey()) != -1
                            && !KillAuraSupport.isSword(mc.thePlayer.inventory.getStackInSlot(n))) {
                        return;
                    }
                    if (auraTarget == null && mc.objectMouseOver != null) {
                        if (mc.objectMouseOver.entityHit != null) {
                            if (AntiBot.isBot(mc.objectMouseOver.entityHit)) {
                                fakeBlocking = false;
                                return;
                            }
                        }
                        BlockPos pos = mc.objectMouseOver.getBlockPos();
                        if (pos != null
                                && KillAuraSupport.isInteractable(KillAuraSupport.getBlock(pos))) {
                            fakeBlocking = false;
                            return;
                        }
                    }
                    if (e.buttonstate) {
                        fakeBlocking = true;
                    } else if (auraTarget == null) {
                        fakeBlocking = false;
                    }
                    e.setCanceled(true);
                }
            }
        }
    }

    public static boolean blockAllowed() {
        return !KillAuraSupport.itemActionActive()
                && autoBlock != AutoBlock.None && (!manualBlock.isToggled() || rightDown());
    }

    @Override
    public void guiUpdate() {
        AutoBlock nextMode = getAutoBlockMode();
        if (autoBlock != nextMode) {
            if (autoBlock == AutoBlock.Smart && Utils.nullCheck()) {
                releaseSmartBlock(true);
            }
            if (Utils.nullCheck()) {
                if (blockState != -1) {
                    releaseFlow(blockState);
                }
                if (autoBlock != AutoBlock.None && !rightDown()) {
                    setBlocking(false, false);
                }
                release(true);
            } else {
                held.clear();
            }
            blockState = -1;
            fakeBlocking = serverBlocking = lagging = slowedThisTick = false;
            badSwapTicks = noAttackTicks = 0;
            passedRightClick = false;
            lagAccumulator = 0;
            smartAutoblock.reset(System.nanoTime());
            autoBlock = nextMode;
        }
        boolean blocking = autoBlock != AutoBlock.None;
        autoBlockAps.setVisible(blocking, this);
        autoBlockLag.setVisible(autoBlock.lags, this);
        blockRangeSetting.setVisible(blocking, this);
        manualBlock.setVisible(blocking, this);
        KillAuraSupport.order(attackRangeSetting, swingRangeSetting);
        if (blocking) {
            KillAuraSupport.order(swingRangeSetting, blockRangeSetting);
        }
        attackRange = attackRangeSetting.getInput();
        swingRange = swingRangeSetting.getInput();
        blockRange = blocking ? blockRangeSetting.getInput() : swingRange;
        double d = Math.max(Math.max(attackRange, swingRange), blockRange) + 1.2;
        scanRangeSq = d * d;
        rotation = Rotation.values()[(int) rotationSetting.getInput()];
        sort = Sort.values()[(int) sortSetting.getInput()];
    }

    private static float[] takeRotations() {
        float[] rot = rotations;
        rotations = null;
        return rot;
    }

    @Override
    public void guiButtonToggled(ButtonSetting b) {
        if (b == hitThroughBlocks && hitThroughBlocks.isToggled()) {
            aimPoint = null;
            aimPoints.clear();
        }
    }

    @Subscribe(priority = Priority.HIGH)
    public void onClientRotation(ClientRotationEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        onPreUpdate();
        float[] rot = getRotations(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        if (rot != null && event.trySetRotation(this, RotationPriority.COMBAT, rot[0], rot[1])) {
            event.setRotationAlreadyQuantized(true);
        }
    }

    @Subscribe(priority = Priority.HIGH)
    public void onPostMotion(PostMotionEvent event) {
        if (Utils.nullCheck()) {
            onPostMotion();
        }
    }

    @Subscribe(priority = Priority.HIGH)
    public void onPostPlayerInput(PostPlayerInputEvent event) {
        if (Utils.nullCheck()) {
            onPostPlayerInput();
        }
    }

    @Subscribe(priority = Priority.HIGH)
    public void onSendPacket(SendPacketEvent event) {
        if (Utils.nullCheck() && !onSendPacket(event.getPacket())) {
            event.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGH)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (!onReceivePacket(event.getPacket())) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && Utils.nullCheck()) {
            onRenderTick();
        }
    }

    @Subscribe
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        if (auraTarget != null && (autoBlock.packet || autoBlock == AutoBlock.Smart)
                && blockAllowed() && Utils.holdingSword()) {
            event.suppressAll();
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        clearWorldState();
    }

    static void clearWorldState() {
        smartAutoblock.reset(System.nanoTime());
        held.clear();
        auraTarget = previousTarget = null;
        target = attackingEntity = null;
        targetList = Collections.emptyList();
        attacking = fakeBlocking = serverBlocking = lagging = attackQueued = inSwingRange = false;
        blockState = -1;
        rotations = null;
        aimPoint = null;
        aimPoints.clear();
        switchTimes.clear();
        minimumAps = maximumAps = currentAps = 0.0;
    }

    @Override
    public void onSlide(SliderSetting setting) {
        guiUpdate();
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        guiUpdate();
    }

    public AutoBlock getAutoBlockMode() {
        int mode = (int) autoBlockSetting.getInput();
        return mode >= 0 && mode < AutoBlock.values().length
                ? AutoBlock.values()[mode] : AutoBlock.None;
    }

    public boolean isServerBlocking() {
        return isEnabled() && serverBlocking;
    }

    public boolean usesSpoofedRotations() {
        return rotationSetting.getInput() == 0;
    }

    public boolean isRequireMouseDown() {
        return false;
    }

    public boolean isRequireLeftClick() {
        return isRequireMouseDown();
    }

    public double getAutoblockCps() {
        return (autoBlockAps.getMinimum() + autoBlockAps.getMaximum()) / 2.0;
    }

    EntityPlayer getAutoblockTarget(double range, boolean serverAware) {
        return isEnabled()
                        && target instanceof EntityPlayer
                        && KillAuraSupport.rayHits(target, range)
                ? (EntityPlayer) target
                : null;
    }

    public SliderSetting getAttackRangeSetting() {
        return attackRangeSetting;
    }

    public SliderSetting getSwingRangeSetting() {
        return swingRangeSetting;
    }

    public SliderSetting getBlockRangeSetting() {
        return blockRangeSetting;
    }

    public SliderSetting getAimRangeSetting() {
        return blockRangeSetting;
    }

    public int getTargetCount() {
        return TARGET_COUNT;
    }

    public int getSwitchDelayMillis() {
        return (int) SWITCH_DELAY;
    }

    public boolean shouldOverrideMouseOver() {
        return isEnabled()
                && Utils.nullCheck()
                && auraTarget != null
                && inSwingRange
                && rotation != Rotation.None
                && RotationHelper.get().isRotationOwner(this);
    }

    public boolean canHitThroughWalls() {
        return hitThroughBlocks.isToggled();
    }

    public MovingObjectPosition rayCastTarget(EntityLivingBase entity, double reach) {
        return Utils.nullCheck()
                ? CombatAim.trace(
                        entity,
                        idle.miau.utility.RotationUtils.serverRotations[0],
                        idle.miau.utility.RotationUtils.serverRotations[1],
                        reach)
                : null;
    }

    public void modifyMouseOverFromGetMouseOver(float partialTicks) {
        if (shouldOverrideMouseOver()) {
            MovingObjectPosition hit =
                    CombatAim.trace(
                            getCombatTarget(),
                            idle.miau.utility.RotationUtils.serverRotations[0],
                            idle.miau.utility.RotationUtils.serverRotations[1],
                            attackRange);
            if (hit != null
                    && (hitThroughBlocks.isToggled()
                            || CombatAim.clearLine(
                                    mc.thePlayer.getPositionEyes(1.0F), hit.hitVec))) {
                mc.objectMouseOver = new MovingObjectPosition(getCombatTarget(), hit.hitVec);
                mc.pointedEntity = getCombatTarget();
            }
        }
    }
}
