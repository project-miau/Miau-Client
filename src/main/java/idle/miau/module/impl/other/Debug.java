package idle.miau.module.impl.other;

import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.entity.player.AttackEntityEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.KillAura;
import idle.miau.module.impl.player.Backtrack;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Debug extends Module {
    private static final DecimalFormat HEALTH_FORMAT = new DecimalFormat(
            "+0.0;-0.0", DecimalFormatSymbols.getInstance(Locale.US));

    private final ButtonSetting attackRange;
    private final ButtonSetting ignoreBackTrack;
    private final ButtonSetting timerRangeDebug;
    private final ButtonSetting lagRangeDebug;
    private final ButtonSetting backtrackDebug;
    private final ButtonSetting healthDebug;
    private final ButtonSetting serversideChat;
    private final ButtonSetting blink;
    private final ButtonSetting transaction;
    private final ButtonSetting motionDebug;

    private final Map<Integer, PendingAttack> pendingAttacks = new ConcurrentHashMap<>();

    private int lastHealthTick = Integer.MIN_VALUE;
    private int motionTick = Integer.MIN_VALUE;

    public Debug() {
        super("Debug", category.other);
        setDescription("Displays diagnostic data for combat and network modules.");
        registerSetting(attackRange = new ButtonSetting("AttackRange", true));
        registerSetting(ignoreBackTrack = new ButtonSetting("IgnoreBackTrack", false));
        registerSetting(timerRangeDebug = new ButtonSetting("TimerRangeDebug", true));
        registerSetting(lagRangeDebug = new ButtonSetting("LagRangeDebug", true));
        registerSetting(backtrackDebug = new ButtonSetting("BacktrackDebug", true));
        registerSetting(healthDebug = new ButtonSetting("HealthDebug", true));
        registerSetting(serversideChat = new ButtonSetting("ServersideChat", false));
        registerSetting(blink = new ButtonSetting("Blink", false));
        registerSetting(transaction = new ButtonSetting("Transaction", false));
        registerSetting(motionDebug = new ButtonSetting("Motion Debug", false));
    }

    @Override
    public void onEnable() {
        lastHealthTick = Integer.MIN_VALUE;
        motionTick = Integer.MIN_VALUE;
        pendingAttacks.clear();
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onAttack(AttackEntityEvent event) {
        if (!attackRange.isToggled() || !Utils.nullCheck()
                || event.entityPlayer != mc.thePlayer || event.target == null) {
            return;
        }
        if (event.isCanceled() || !(event.target instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer targetPlayer = (EntityPlayer) event.target;
        if (AntiBot.isBot(targetPlayer) || targetPlayer.isDead) {
            return;
        }
        if (targetPlayer.hurtResistantTime > 10) {
            return;
        }

        KillAura aura = ModuleManager.killAura;
        double maxRange = (aura != null && aura.isEnabled())
                ? aura.getAttackRangeSetting().getInput()
                : 3.0D;

        boolean backtracking = ModuleManager.backtrack != null
                && ModuleManager.backtrack.isEnabled()
                && ModuleManager.backtrack.isBacktracking();
        if (ignoreBackTrack.isToggled() && backtracking) {
            return;
        }

        double distance = RotationUtils.distanceFromEyeToClosestOnAABB(targetPlayer);
        boolean timerRangeActive = ModuleManager.timerRange != null
                && ModuleManager.timerRange.isEnabled()
                && (ModuleManager.timerRange.isCycleActive() || ModuleManager.timerRange.hasRecentCycle(800L));
        boolean lagRangeActive = ModuleManager.lagRange != null
                && ModuleManager.lagRange.isEnabled()
                && ModuleManager.lagRange.hasActiveLag();

        pendingAttacks.put(targetPlayer.getEntityId(), new PendingAttack(
                targetPlayer.getEntityId(),
                distance,
                maxRange,
                timerRangeActive,
                lagRangeActive,
                backtracking,
                System.currentTimeMillis()
        ));
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck()) {
            pendingAttacks.clear();
            return;
        }

        if (!pendingAttacks.isEmpty()) {
            long now = System.currentTimeMillis();
            pendingAttacks.entrySet().removeIf(entry -> {
                PendingAttack attack = entry.getValue();
                if (now - attack.timestamp > 1000L) {
                    return true;
                }
                if (mc.theWorld != null) {
                    Entity entity = mc.theWorld.getEntityByID(attack.targetId);
                    if (entity instanceof EntityLivingBase) {
                        EntityLivingBase living = (EntityLivingBase) entity;
                        if (living.hurtTime > 0 && living.hurtResistantTime >= living.maxHurtResistantTime - 1) {
                            confirmHit(attack);
                            return true;
                        }
                    }
                }
                return false;
            });
        }
    }

    @Subscribe(priority = Priority.LOWEST, receiveCanceled = true)
    public void onSendPacket(SendPacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (transaction.isToggled() && packet instanceof C0FPacketConfirmTransaction) {
            C0FPacketConfirmTransaction transactionPacket = (C0FPacketConfirmTransaction) packet;
            debug("Transaction C0F window=" + transactionPacket.getWindowId()
                    + " uid=" + transactionPacket.getUid() + " canceled=" + event.isCanceled());
        }
        if (blink.isToggled() && event.isCanceled() && ModuleManager.blink != null
                && ModuleManager.blink.isEnabled()) {
            debug("Blink buffered " + packet.getClass().getSimpleName());
        }
        if (motionDebug.isToggled() && packet instanceof C03PacketPlayer && Utils.nullCheck()
                && motionTick != mc.thePlayer.ticksExisted) {
            C03PacketPlayer motion = (C03PacketPlayer) packet;
            motionTick = mc.thePlayer.ticksExisted;
            debug("Motion x=" + format(mc.thePlayer.motionX)
                    + " y=" + format(mc.thePlayer.motionY)
                    + " z=" + format(mc.thePlayer.motionZ)
                    + " ground=" + motion.isOnGround()
                    + " moving=" + motion.isMoving());
        }
    }

    @Subscribe(priority = Priority.LOWEST, receiveCanceled = true)
    public void onReceivePacket(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (serversideChat.isToggled() && packet instanceof S02PacketChat) {
            debug("Server chat: "
                    + ((S02PacketChat) packet).getChatComponent().getUnformattedText());
        }
        if (transaction.isToggled() && packet instanceof S32PacketConfirmTransaction) {
            S32PacketConfirmTransaction transactionPacket = (S32PacketConfirmTransaction) packet;
            debug("Transaction S32 window=" + transactionPacket.getWindowId()
                    + " action=" + transactionPacket.getActionNumber()
                    + " accepted=" + transactionPacket.func_148888_e()
                    + " canceled=" + event.isCanceled());
        }
        if (packet instanceof S19PacketEntityStatus && !pendingAttacks.isEmpty() && mc.theWorld != null) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
            if (status.getOpCode() == 2) {
                Entity hitEntity = status.getEntity(mc.theWorld);
                if (hitEntity != null) {
                    PendingAttack attack = pendingAttacks.remove(hitEntity.getEntityId());
                    if (attack != null && System.currentTimeMillis() - attack.timestamp <= 1000L) {
                        confirmHit(attack);
                    }
                }
            }
        }
        if (!healthDebug.isToggled() || event.isCanceled() || !Utils.nullCheck()) {
            return;
        }
        if (packet instanceof S06PacketUpdateHealth) {
            debugHealth(((S06PacketUpdateHealth) packet).getHealth() - mc.thePlayer.getHealth());
            return;
        }
        if (!(packet instanceof S1CPacketEntityMetadata)) {
            return;
        }

        S1CPacketEntityMetadata metadataPacket = (S1CPacketEntityMetadata) packet;
        if (metadataPacket.getEntityId() != mc.thePlayer.getEntityId()) {
            return;
        }
        List<DataWatcher.WatchableObject> metadata = metadataPacket.func_149376_c();
        if (metadata == null) {
            return;
        }
        for (DataWatcher.WatchableObject value : metadata) {
            if (value.getDataValueId() == 6 && value.getObject() instanceof Number) {
                debugHealth(((Number) value.getObject()).floatValue() - mc.thePlayer.getHealth());
                return;
            }
        }
    }

    private void debugHealth(float difference) {
        if (difference == 0.0F || lastHealthTick == mc.thePlayer.ticksExisted) {
            return;
        }
        lastHealthTick = mc.thePlayer.ticksExisted;
        debug("Health: " + (difference > 0.0F ? "&a&l" : "&c&l")
                + HEALTH_FORMAT.format(difference) + "&r &f(&otick: "
                + mc.thePlayer.ticksExisted + "&r&f)");
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private void debug(String message) {
        Utils.sendMessage("&7[&dDebug&7] &f" + message);
    }

    private void confirmHit(PendingAttack attack) {
        StringBuilder message = new StringBuilder("AttackRange ")
                .append(format(attack.distance))
                .append(" / ")
                .append(format(attack.maxRange));
        if (timerRangeDebug.isToggled()) {
            message.append(" | TimerRange=").append(attack.timerRangeActive);
        }
        if (lagRangeDebug.isToggled()) {
            message.append(" | LagRange=").append(attack.lagRangeActive);
        }
        if (backtrackDebug.isToggled()) {
            message.append(" | Backtrack=").append(attack.backtrackActive);
        }
        debug(message.toString());
    }

    private static final class PendingAttack {
        final int targetId;
        final double distance;
        final double maxRange;
        final boolean timerRangeActive;
        final boolean lagRangeActive;
        final boolean backtrackActive;
        final long timestamp;

        PendingAttack(int targetId, double distance, double maxRange,
                      boolean timerRangeActive, boolean lagRangeActive, boolean backtrackActive,
                      long timestamp) {
            this.targetId = targetId;
            this.distance = distance;
            this.maxRange = maxRange;
            this.timerRangeActive = timerRangeActive;
            this.lagRangeActive = lagRangeActive;
            this.backtrackActive = backtrackActive;
            this.timestamp = timestamp;
        }
    }
}
