package idle.miau.module.impl.other;

import idle.miau.event.AntiCheatFlagEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.McpRuntime;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockLeaves;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

public class Anticheat extends Module {
    public final SliderSetting flagInterval;
    public final ButtonSetting addCheatersAsEnemy, autoReport, disableOutsideMinigames, hideChatAlerts, ignoreTeammates, sendPartyChat, shouldPing;
    private final EnumMap<Check, ButtonSetting> checkSettings = new EnumMap<>(Check.class);
    private final HashSet<String> cheaters = new HashSet<>();
    private long lastPacket;

    public enum Check {
        AutoBlock("AutoBlock", false), LegitScaffold("Legit Scaffold", false), NoSlow("NoSlow", false), Nuker("Nuker", false),
        BedNuker("Bed Nuker", false), Scaffold("Scaffold", false), AimAssist("AimAssist", true), AutoClicker("AutoClicker", true),
        Blink("Blink", true), FastMine("FastMine", true), NoFall("NoFall", true), Protocol("Protocol", true);

        public final String displayName;
        public final boolean experimental;

        Check(String displayName, boolean experimental) {
            this.displayName = displayName;
            this.experimental = experimental;
        }
    }

    final class Tracker {
        Block belowBlock;
        int quickSwings, lastPitchTick, lastSwing, lastMoveTick, yawTicks, restTicks, sprintTicks, lastRestTicks, lastSprintTicks, lastStopUsingTick,
                lastFallingTicks, lastDeltaYTick, stillTicks, lastBreakAnim = -1, lastSneakTick, swingBlockTicks, useSprintTicks, lastHorizontalMoveTick,
                perfectSneaks, fallingTicks, scaffoldTicks, horizontalTicks;
        boolean wasUsing, wasSneaking;
        double maxHorizontal;
        double motionX, motionY, motionZ;
        Vec3 fallStart;

        void update(EntityPlayer p) {
            int n = p.ticksExisted;
            this.updateMotion(p);
            below.set(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posY - 1.0), MathHelper.floor_double(p.posZ));
            this.belowBlock = BlockUtils.getBlock(below);
            this.maxHorizontal = Math.max(Math.abs(this.motionX), Math.abs(this.motionZ));
            if (this.maxHorizontal == 0.0 && this.motionY == 0.0) {
                ++this.restTicks;
            } else {
                this.restTicks = 0;
                this.lastMoveTick = n;
            }
            if (this.maxHorizontal >= 0.07) {
                ++this.horizontalTicks;
                this.lastHorizontalMoveTick = n;
            } else {
                this.horizontalTicks = 0;
            }
            if (Math.abs(this.motionY) >= 0.1) {
                this.lastDeltaYTick = n;
            }
            this.fallingTicks = this.motionY < 0.0 && this.belowBlock == Blocks.air ? ++this.fallingTicks : 0;
            if (p.isSneaking()) {
                this.lastSneakTick = n;
            }
            if (p.rotationPitch != p.prevRotationPitch) {
                this.lastPitchTick = n;
            }
            if (!p.isUsingItem() && this.wasUsing) {
                this.lastStopUsingTick = n;
            }
            this.yawTicks = Math.abs(p.rotationYaw - p.prevRotationYaw) >= 1.0f ? ++this.yawTicks : 0;
            this.swingBlockTicks = p.isSwingInProgress && p.isBlocking() ? ++this.swingBlockTicks : 0;
            this.sprintTicks = p.isSprinting() ? ++this.sprintTicks : 0;
            this.useSprintTicks = p.isSprinting() && p.isUsingItem() && !p.isRiding() && !p.isInWater() ? ++this.useSprintTicks : 0;
            if (p.isSwingInProgress && p.rotationPitch >= 65.0f && isBlock(p.getHeldItem())) {
                this.scaffoldTicks = this.horizontalTicks >= 15 ? ++this.scaffoldTicks : 0;
                if (p.swingProgressInt == 1) {
                    this.perfectSneaks = !this.wasSneaking && p.isSneaking() ? ++this.perfectSneaks : 0;
                }
            } else {
                this.perfectSneaks = 0;
                this.scaffoldTicks = 0;
            }
            if (p.isSwingInProgress && (this.lastBreakAnim == -1 || this.lastBreakAnim == 255)) {
                if (p.swingProgressInt == 0) {
                    this.quickSwings = this.lastSwing == 3 ? ++this.quickSwings : 0;
                }
            } else {
                this.quickSwings = 0;
            }
            this.stillTicks = !p.isSneaking() && !p.isSwingInProgress && this.maxHorizontal == 0.0 && this.motionY == 0.0
                    && p.rotationYaw == p.prevRotationYaw && p.rotationPitch == p.prevRotationPitch ? ++this.stillTicks : 0;
        }

        void endTick(EntityPlayer p) {
            this.lastRestTicks = this.restTicks;
            this.lastSprintTicks = this.sprintTicks;
            this.lastFallingTicks = this.fallingTicks;
            this.wasSneaking = p.isSneaking();
            this.lastSwing = p.swingProgressInt;
            this.wasUsing = p.isUsingItem();
        }

        void onBreakAnim(EntityPlayer p, S25PacketBlockBreakAnim anim) {
            this.lastBreakAnim = anim.getProgress();
            if (enabled(Check.FastMine)) {
                S25PacketBlockBreakAnim previous = breakingPlayers.get(p.getUniqueID());
                if (previous != null && anim.getProgress() == 255) {
                    breakingBlocks.put(anim.getPosition(), previous);
                }
                breakingPlayers.put(p.getUniqueID(), anim);
            }
        }

        void updateMotion(EntityPlayer p) {
            this.motionX = p.posX - p.lastTickPosX;
            this.motionY = p.posY - p.lastTickPosY;
            this.motionZ = p.posZ - p.lastTickPosZ;
        }
    }

    private final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
    private final HashMap<UUID, Tracker> trackers = new HashMap<UUID, Tracker>();
    private final HashMap<UUID, HashMap<Check, Long>> lastFlags = new HashMap<UUID, HashMap<Check, Long>>();
    private final HashMap<UUID, S25PacketBlockBreakAnim> breakingPlayers = new HashMap<UUID, S25PacketBlockBreakAnim>();
    private final HashMap<BlockPos, S25PacketBlockBreakAnim> breakingBlocks = new HashMap<BlockPos, S25PacketBlockBreakAnim>();
    private final HashSet<String> reported = new HashSet<String>();
    private String partyMessage, reportName;
    private long lastPing;

    public Anticheat() {
        super("AntiCheat", category.other);
        setDescription("Detects suspicious combat, movement and block interactions.");
        registerSetting(flagInterval = new SliderSetting("Flag interval", " seconds", 5, 0, 30, 1));
        registerSetting(addCheatersAsEnemy = new ButtonSetting("Add cheaters as enemy", false));
        registerSetting(autoReport = new ButtonSetting("Auto report", false));
        registerSetting(disableOutsideMinigames = new ButtonSetting("Disable outside minigames", false));
        registerSetting(hideChatAlerts = new ButtonSetting("Hide chat alerts", false));
        registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", false));
        registerSetting(sendPartyChat = new ButtonSetting("Send party chat", false));
        registerSetting(shouldPing = new ButtonSetting("Should ping", false));
        for (Check check : Check.values()) {
            if (check != Check.BedNuker) {
                ButtonSetting setting = new ButtonSetting(check.displayName, !check.experimental);
                checkSettings.put(check, setting);
                registerSetting(setting);
            }
        }
        closetModule = true;
    }

    private boolean enabled(Check check) {
        return checkSettings.get(check == Check.BedNuker ? Check.Nuker : check).isToggled();
    }

    private void check(EntityPlayer p, Tracker t) {

        ItemStack held;
        int n = p.ticksExisted;
        boolean adventure = mc.playerController.getCurrentGameType() == WorldSettings.GameType.ADVENTURE;
        if (enabled(Check.AimAssist) && p.rotationPitch > -80.0f && p.rotationPitch < 80.0f && t.yawTicks >= 25 && n - t.lastPitchTick >= 25) {
            flag(p, Check.AimAssist);
            return;
        }
        if (enabled(Check.AutoClicker) && !adventure && t.quickSwings >= 10 && mc.thePlayer.getDistanceSqToEntity(p) < 700.0
                && ((held = p.getHeldItem()) == null || held.getItem() != Items.fishing_rod)) {
            flag(p, Check.AutoClicker);
            return;
        }
        if (enabled(Check.AutoBlock) && t.swingBlockTicks >= 15) {
            flag(p, Check.AutoBlock);
            return;
        }
        if (enabled(Check.Blink) && !adventure && t.lastSprintTicks >= 2 && t.lastRestTicks >= 6 && t.maxHorizontal > 0.5 && t.maxHorizontal < 3.0
                && System.currentTimeMillis() - lastPacket < 100L) {
            flag(p, Check.Blink);
            return;
        }
        if (enabled(Check.LegitScaffold) && t.perfectSneaks >= 3) {
            flag(p, Check.LegitScaffold);
            return;
        }
        if (enabled(Check.NoFall)) {
            if (t.stillTicks == 10 && t.belowBlock == Blocks.air) {
                t.fallStart = new Vec3(p.posX, p.posY, p.posZ);
            }
            if (t.fallStart != null) {
                if (p.hurtTime != 0) {
                    t.fallStart = null;
                } else if (t.lastFallingTicks > 0 && t.lastFallingTicks < 6 && t.belowBlock != Blocks.air
                        && !(p.getHeldItem() != null && p.getHeldItem().getItem() == Item.getItemFromBlock(Blocks.ladder))) {
                    double dx = p.posX - t.fallStart.xCoord;
                    double dz = p.posZ - t.fallStart.zCoord;
                    double h = dx * dx + dz * dz;
                    double dy = p.posY - t.fallStart.yCoord;
                    t.fallStart = null;
                    if (h < 144.0 && dy <= -5.0 && !excludedFallMode()) {
                        flag(p, Check.NoFall);
                        return;
                    }
                }
            }
        }
        if (enabled(Check.NoSlow) && t.useSprintTicks >= 11 && t.maxHorizontal >= 0.08 && n - t.lastStopUsingTick >= 35) {
            flag(p, Check.NoSlow);
            return;
        }
        if (enabled(Check.Scaffold) && t.scaffoldTicks > 0 && n - t.lastSneakTick >= 25 && (n - t.lastDeltaYTick >= 20 || t.scaffoldTicks > 25)
                && !gameTitle().contains("BUILD BATTLE")) {
            boolean air = true;
            BlockPos pos = p.getPosition().down(2);
            for (int i = 0; i < 4; ++i) {
                if (!(BlockUtils.getBlock(pos) instanceof BlockAir)) {
                    air = false;
                    break;
                }
                pos = pos.down();
            }
            if (air) {
                flag(p, Check.Scaffold);
                return;
            }
        }
        if (enabled(Check.Protocol) && Math.abs(p.rotationPitch) >= 92.0f) {
            flag(p, Check.Protocol);
        }
    }

    @Subscribe
    public void onPreMotion(PreMotionEvent e) {

        if (!active()) {
            return;
        }
        if (partyMessage != null) {
            mc.thePlayer.sendChatMessage("/pc " + partyMessage);
            partyMessage = null;
        } else if (reportName != null) {
            mc.thePlayer.sendChatMessage("/report " + reportName + " cheating");
            reportName = null;
        }
        cleanup();
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (ignored(p)) continue;
            Tracker t = trackers.get(p.getUniqueID());
            if (t == null) {
                t = new Tracker();
            }
            t.update(p);
            check(p, t);
            t.endTick(p);
            trackers.put(p.getUniqueID(), t);
        }
    }

    @Override
    public void onDisable() {
        cheaters.clear();
        lastPacket = 0L;
        trackers.clear();
        lastFlags.clear();
        breakingPlayers.clear();
        breakingBlocks.clear();
        reported.clear();
        partyMessage = null;
        reportName = null;
        lastPing = 0L;
    }

    private boolean ignored(EntityPlayer p) {
        return p == mc.thePlayer || p.isDead || p.deathTime != 0 || AntiBot.isBot(p);
    }

    @Subscribe
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            this.onDisable();
        }
    }

    private boolean active() {
        return Utils.nullCheck() && !mc.isSingleplayer()
                && (!disableOutsideMinigames.isToggled() || Utils.isHypixel() && !Utils.isLobby());
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();
        lastPacket = System.currentTimeMillis();

        if (!active()) {
            return;
        }
        Entity entity;
        if (packet instanceof S25PacketBlockBreakAnim && (enabled(Check.AutoClicker) || enabled(Check.FastMine))
                && (entity = mc.theWorld.getEntityByID(((S25PacketBlockBreakAnim) packet).getBreakerId())) instanceof EntityPlayer) {
            Tracker t = trackers.get(entity.getUniqueID());
            if (t != null) {
                t.onBreakAnim((EntityPlayer) entity, (S25PacketBlockBreakAnim) packet);
            }
        }
        if (packet instanceof S23PacketBlockChange) {
            S23PacketBlockChange p = (S23PacketBlockChange) packet;
            if (p.getBlockState().getBlock() instanceof BlockAir) {
                BlockPos pos = p.getBlockPosition();
                Block block = BlockUtils.getBlock(pos);
                if (block == Blocks.air || block == Blocks.bed || block == Blocks.ladder || block instanceof BlockBush || block instanceof BlockLeaves) {
                    return;
                }
                EntityPlayer breaker = null;
                S25PacketBlockBreakAnim anim = breakingBlocks.get(pos);
                if (anim != null) {
                    Entity e = mc.theWorld.getEntityByID(anim.getBreakerId());
                    if (e instanceof EntityPlayer) {
                        breaker = (EntityPlayer) e;
                        if (anim.getProgress() > 4 && anim.getProgress() < 10) {
                            flag(breaker, Check.FastMine);
                        }
                    }
                    breakingBlocks.remove(pos);
                }
                if (enabled(Check.Nuker)) {
                    if (exposed(pos)) {
                        return;
                    }
                    double d = -1.0;
                    if (!(breaker != null || ModuleManager.bedAura != null && ModuleManager.bedAura.isEnabled() && mc.thePlayer.getDistanceSq(pos) <= 36.0)) {
                        for (EntityPlayer other : mc.theWorld.playerEntities) {
                            if (ignored(other)) continue;
                            double d2 = other.getDistanceSq(pos);
                            if (d != -1.0 && !(d2 < d)) continue;
                            d = d2;
                            breaker = other;
                        }
                    }
                    if (breaker != null) {
                        flag(breaker, Check.Nuker);
                    }
                }
            }
        } else if (packet instanceof S22PacketMultiBlockChange && enabled(Check.Nuker)) {
            S22PacketMultiBlockChange p = (S22PacketMultiBlockChange) packet;
            if (p.getChangedBlocks().length != 2) {
                return;
            }
            if (!(p.getChangedBlocks()[0].getBlockState().getBlock() instanceof BlockAir)) {
                return;
            }
            BlockPos a = p.getChangedBlocks()[0].getPos();
            BlockPos b = p.getChangedBlocks()[1].getPos();
            if (exposed(a) || exposed(b)) {
                return;
            }
            EntityPlayer breaker = null;
            for (EntityPlayer other : mc.theWorld.playerEntities) {
                if (other == mc.thePlayer && ModuleManager.bedAura != null && ModuleManager.bedAura.isEnabled() && (other.getDistanceSq(a) <= 36.0 || other.getDistanceSq(b) <= 36.0)) {
                    break;
                }
                if (ignored(other) || other.getDistanceSq(a) > 36.0 && other.getDistanceSq(b) > 36.0) continue;
                breaker = other;
                break;
            }
            if (breaker != null) {
                flag(breaker, Check.BedNuker);
            }
        }
    }

    private void flag(EntityPlayer player, Check check) {
        if (!enabled(check) || ignored(player) || Utils.isFriended(player)
                || ignoreTeammates.isToggled() && Utils.isTeammate(player)) {
            return;
        }
        mark(player);
        if (addCheatersAsEnemy.isToggled()) {
            Utils.addEnemy(player.getName());
        }
        long now = System.currentTimeMillis();
        HashMap<Check, Long> flags = lastFlags.computeIfAbsent(player.getUniqueID(), key -> new HashMap<>());
        Long previous = flags.get(check);
        if (flagInterval.getInput() > 0.0D && previous != null
                && now - previous <= flagInterval.getInput() * 1000.0D) {
            return;
        }
        flags.put(check, now);
        boolean hypixel = Utils.isHypixel();
        String name = player.getName();
        if (hypixel && autoReport.isToggled() && mc.currentScreen == null && reported.add(name)) {
            reportName = name;
        }
        String message = player.getDisplayName().getUnformattedText() + " &7detected for &d"
                + check.displayName + " &7(" + Utils.round(mc.thePlayer.getDistanceToEntity(player), 1) + "m)";
        ChatComponentText text = new ChatComponentText(Utils.formatColor("&8[&5AntiCheat&8]&r " + message));
        if (hypixel && sendPartyChat.isToggled()) {
            partyMessage = Utils.stripColor(text.getUnformattedText());
        }
        if (!hideChatAlerts.isToggled()) {
            if (hypixel) {
                ChatStyle style = new ChatStyle();
                style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText("/report " + name + " cheating")));
                style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/report " + name + " cheating"));
                text.appendSibling(new ChatComponentText(Utils.formatColor(" &7[&cWDR&7]")).setChatStyle(style));
            }
            mc.thePlayer.addChatMessage(text);
        }
        if (shouldPing.isToggled() && now - lastPing > 1500L) {
            mc.thePlayer.playSound("note.pling", 1.0F, 1.0F);
            lastPing = now;
        }
        postAntiCheatFlagEvent(check.displayName, player);
    }

    public void mark(EntityPlayer player) {
        cheaters.add(player.getName());
    }

    public boolean isCheater(EntityPlayer player) {
        return player != null && cheaters.contains(player.getName());
    }

    public void cleanup() {
        if (!Utils.nullCheck()) {
            onDisable();
            return;
        }
        HashSet<UUID> online = new HashSet<>();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            online.add(player.getUniqueID());
        }
        trackers.keySet().retainAll(online);
        lastFlags.keySet().retainAll(online);
        breakingPlayers.keySet().retainAll(online);
        breakingBlocks.entrySet().removeIf(entry -> mc.theWorld.getEntityByID(entry.getValue().getBreakerId()) == null);
    }

    public void postAntiCheatFlagEvent(String check, Entity entity) {
        McpRuntime.EVENTS.post(new AntiCheatFlagEvent(check, entity));
    }

    private boolean isBlock(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBlock;
    }

    private boolean exposed(BlockPos position) {
        for (EnumFacing facing : EnumFacing.values()) {
            if (!BlockUtils.getBlock(position.offset(facing)).isFullCube()) {
                return true;
            }
        }
        return false;
    }

    private String gameTitle() {
        ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        return objective == null ? "" : Utils.stripColor(objective.getDisplayName()).toUpperCase(Locale.ROOT);
    }

    private boolean excludedFallMode() {
        String title = gameTitle();
        return Utils.isLobby() || Utils.isBedwarsPracticeOrReplay()
                || title.contains("PIT") || title.contains("SKYWARS") || title.contains("BUILD BATTLE");
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        onDisable();
    }
}
