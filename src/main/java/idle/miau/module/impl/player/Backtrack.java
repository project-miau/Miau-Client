package idle.miau.module.impl.player;

import idle.miau.Miau;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.RunGameLoopEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.entity.player.AttackEntityEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.KillAura;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BacktrackClassic;
import idle.miau.utility.BacktrackHitSync;
import idle.miau.utility.BacktrackModern;
import idle.miau.utility.BacktrackPacketPolicy;
import idle.miau.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.world.World;

public final class Backtrack extends Module {
    private static final int BLATANT_MODE = 0;
    private static final int NORMAL_MODE = 1;
    private static final int HITSYNC_MODE = 2;
    private static final String[] MODES = {
            "Blatant", "Normal", "HitSync"
    };

    private final SliderSetting mode = new SliderSetting("Mode", NORMAL_MODE, MODES);
    private final ButtonSetting legit = new ButtonSetting("Legit", false);
    private final ButtonSetting flushOnDamage = new ButtonSetting("Flush on damage", false);
    private final ButtonSetting ignoreTeam = new ButtonSetting("IgnoreTeam", true);
    private final ButtonSetting onlyKillaura = new ButtonSetting("OnlyKillaura", true);
    private final BacktrackClassic classicBacktrack;
    private final BacktrackModern modernBacktrack;
    private final BacktrackHitSync hitSyncBacktrack;

    public static volatile boolean shouldLag;

    public boolean isBacktracking() {
        if (!isEnabled()) {
            return false;
        }
        if (isClassicMode()) {
            return shouldLag;
        }
        if (isHitSyncMode()) {
            return shouldLag;
        }
        return modernBacktrack.isActive();
    }

    public Entity getBacktrackTarget() {
        if (isClassicMode()) {
            return classicBacktrack.getTarget();
        }
        if (isHitSyncMode()) {
            return hitSyncBacktrack.getTarget();
        }
        return modernBacktrack.getTarget();
    }

    public net.minecraft.util.Vec3 getBacktrackPosition() {
        if (isClassicMode()) {
            return classicBacktrack.getTargetPosition();
        }
        if (isHitSyncMode()) {
            return hitSyncBacktrack.getTargetPosition();
        }
        return modernBacktrack.getTargetPosition();
    }

    public Backtrack() {
        super("Backtrack", category.combat);
        setDescription("Lag other players when they are about to move out of range.");
        registerSetting(mode);
        registerSetting(legit);
        registerSetting(flushOnDamage);
        registerSetting(ignoreTeam);
        registerSetting(onlyKillaura);
        classicBacktrack = new BacktrackClassic(this);
        modernBacktrack = new BacktrackModern(this);
        hitSyncBacktrack = new BacktrackHitSync(this);
        guiUpdate();
    }

    @Override
    public void registerSetting(Setting setting, String configId) {
        super.registerSetting(setting, configId);
    }

    @Override
    public void guiUpdate() {
        classicBacktrack.setSettingsVisible(isClassicMode());
        modernBacktrack.setSettingsVisible(isModernMode());
        hitSyncBacktrack.setSettingsVisible(isHitSyncMode());
        legit.setVisible(false, this);
        flushOnDamage.setVisible(!isNormalMode(), this);
        ignoreTeam.setVisible(true, this);
        onlyKillaura.setVisible(!isNormalMode(), this);
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            classicBacktrack.reset(true);
            modernBacktrack.reset(true);
            hitSyncBacktrack.reset(true);
            shouldLag = false;
        }
        guiUpdate();
    }

    @Override
    public void onCommandSettingChanged(idle.miau.module.setting.Setting setting) {
        if (setting == mode || setting == legit) {
            classicBacktrack.reset(true);
            modernBacktrack.reset(true);
            hitSyncBacktrack.reset(true);
            shouldLag = false;
        }
        guiUpdate();
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == onlyKillaura || setting == legit) {
            classicBacktrack.reset(true);
            modernBacktrack.reset(true);
            hitSyncBacktrack.reset(true);
            shouldLag = false;
        }
        super.guiButtonToggled(setting);
    }

    @Override
    public void onEnable() {
        classicBacktrack.reset(false);
        modernBacktrack.reset(false);
        hitSyncBacktrack.reset(false);
        shouldLag = false;
    }

    @Override
    public void onDisable() {
        classicBacktrack.reset(true);
        modernBacktrack.reset(true);
        hitSyncBacktrack.reset(true);
        shouldLag = false;
    }

    @Override
    public String getInfo() {
        if (isClassicMode()) {
            return classicBacktrack.getCurrentDelay() + "ms";
        }
        if (isHitSyncMode()) {
            return hitSyncBacktrack.getCurrentDelay() + "ms";
        }
        return modernBacktrack.getInfo();
    }

    @Subscribe(priority = Priority.HIGH, receiveCanceled = true)
    public void onReceivePacket(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (isNormalMode()) {
            if (BacktrackPacketPolicy.isSessionBoundaryPacket(packet)) {
                modernBacktrack.reset(false);
            }
            return;
        }
        if (packet instanceof S2DPacketOpenWindow) {
            classicBacktrack.reset(true);
            modernBacktrack.reset(true);
            hitSyncBacktrack.reset(true);
            shouldLag = false;
            return;
        }
        if (BacktrackPacketPolicy.isSessionBoundaryPacket(packet)
                || BacktrackPacketPolicy.isTimelineResetPacket(packet)) {
            classicBacktrack.reset(false);
            modernBacktrack.reset(false);
            hitSyncBacktrack.reset(false);
            if (Miau.backtrackPacketController != null) {
                Miau.backtrackPacketController.clearReplayQueue();
            }
            return;
        }

        if (flushOnDamage.isToggled() && isLocalDamage(packet)) {
            if (isClassicMode()) {
                classicBacktrack.reset(true);
            } else if (isHitSyncMode()) {
                hitSyncBacktrack.reset(true);
            } else {
                modernBacktrack.reset(true);
            }
            return;
        }

        if (isClassicMode()) {
            classicBacktrack.onIncoming(event);
        } else if (isHitSyncMode()) {
            hitSyncBacktrack.onIncoming(event);
        }
    }

    @Subscribe
    public void onGameLoop(RunGameLoopEvent event) {
        if (isNormalMode()) {
            return;
        }
        if (!Utils.nullCheck() || (mc != null && mc.currentScreen instanceof GuiContainer)) {
            classicBacktrack.reset(false);
            modernBacktrack.reset(false);
            hitSyncBacktrack.reset(false);
            if (Miau.backtrackPacketController != null) {
                Miau.backtrackPacketController.clearReplayQueue();
            }
            shouldLag = false;
            return;
        }

        if (isClassicMode()) {
            classicBacktrack.onGameLoop(event);
        } else if (isHitSyncMode()) {
            hitSyncBacktrack.onGameLoop(event);
        }
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onQueuePacketClear(RunGameLoopEvent event) {
        if (isClassicMode()) {
            classicBacktrack.onQueuePacketClear();
        }
    }

    @Subscribe
    public void onAttack(AttackEntityEvent event) {
        if (!canUseAttackSource(event.target)) {
            return;
        }
        if (isClassicMode()) {
            classicBacktrack.onAttack(event);
        } else if (isHitSyncMode()) {
            hitSyncBacktrack.onAttack(event);
        }
    }

    @Subscribe
    public void onWorldChange(EntityJoinWorldEvent event) {
        if (event.entity != getMinecraftPlayer()) {
            return;
        }
        resetWorldState();
    }

    @Subscribe
    public void onWorldUnload(idle.miau.mcp.event.world.WorldEvent.Unload event) {
        resetWorldState();
    }

    @Subscribe
    public void onRender3D(RenderWorldLastEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (isClassicMode()) {
            classicBacktrack.onRender(event.partialTicks);
        } else if (isHitSyncMode()) {
            hitSyncBacktrack.onRender(event.partialTicks);
        } else {
            idle.miau.utility.SemanticEvent frame = new idle.miau.utility.SemanticEvent(6);
            frame.timerRenderPartialTicks = event.partialTicks;
            net.minecraft.client.renderer.entity.RenderManager manager = mc.getRenderManager();
            frame.cameraX = manager.viewerPosX;
            frame.cameraY = manager.viewerPosY;
            frame.cameraZ = manager.viewerPosZ;
            modernBacktrack.onEvent(idle.miau.helper.GameplayHelper.context(), frame);
        }
    }

    public void onNormalEvent(idle.miau.utility.ClientContext context, idle.miau.utility.SemanticEvent event) {
        modernBacktrack.onEvent(context, event);
        shouldLag = modernBacktrack.isActive();
    }

    public void onNormalPacket(idle.miau.utility.SemanticEvent event) {
        modernBacktrack.packet(event);
        shouldLag = modernBacktrack.isActive();
    }

    public static boolean shouldPassBacktrackImmediately(Packet<?> packet) {
        if (packet instanceof C00Handshake
                || packet instanceof C00PacketServerQuery
                || packet instanceof S02PacketChat
                || packet instanceof S01PacketPong
                || packet instanceof S2DPacketOpenWindow
                || packet instanceof S2EPacketCloseWindow
                || packet instanceof S30PacketWindowItems
                || packet instanceof S2FPacketSetSlot) {
            return true;
        }
        return packet instanceof S29PacketSoundEffect
                && BacktrackPacketPolicy.isImmediateSound(
                ((S29PacketSoundEffect) packet).getSoundName());
    }

    public static boolean shouldPassMovementImmediately(Packet<?> packet) {
        return shouldPassBacktrackImmediately(packet);
    }

    public net.minecraft.client.entity.EntityPlayerSP getMinecraftPlayer() {
        return mc.thePlayer;
    }

    public World getWorld() {
        return mc.theWorld;
    }

    public net.minecraft.client.renderer.entity.RenderManager getRenderManager() {
        return mc.getRenderManager();
    }

    public boolean isSingleplayer() {
        return mc.isSingleplayer();
    }

    public boolean hasServerData() {
        return mc.getCurrentServerData() != null;
    }

    public boolean isSpectator() {
        return mc.playerController != null
                && mc.playerController.getCurrentGameType()
                == net.minecraft.world.WorldSettings.GameType.SPECTATOR;
    }

    public boolean isTrackableTarget(EntityPlayer player) {
        return player != null
                && !AntiBot.isBot(player)
                && !Utils.isFriended(player)
                && !isIgnoredTeam(player);
    }

    public boolean isOnlyKillaura() {
        return onlyKillaura.isToggled();
    }

    public boolean isKillauraTarget(Entity target) {
        return target != null
                && ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target == target;
    }

    public boolean isIgnoredTeam(EntityPlayer player) {
        return ignoreTeam.isToggled() && Utils.isTeammate(player);
    }

    private boolean canUseAttackSource(Entity target) {
        if (target == null || AntiBot.isBot(target)) {
            return false;
        }
        return !isOnlyKillaura() || isKillauraTarget(target);
    }

    private boolean isLocalDamage(Packet<?> packet) {
        if (!(packet instanceof S19PacketEntityStatus)
                || mc.theWorld == null
                || mc.thePlayer == null) {
            return false;
        }
        S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
        return status.getOpCode() == 2 && status.getEntity(mc.theWorld) == mc.thePlayer;
    }

    private boolean isClassicMode() {
        return selectedMode() == BLATANT_MODE;
    }

    private boolean isModernMode() {
        return selectedMode() == NORMAL_MODE;
    }

    public boolean isHitSyncMode() {
        return selectedMode() == HITSYNC_MODE;
    }

    public boolean isNormalMode() {
        return selectedMode() == NORMAL_MODE;
    }

    private int selectedMode() {
        int selected = (int) mode.getInput();
        return Math.max(BLATANT_MODE, Math.min(HITSYNC_MODE, selected));
    }

    private void resetWorldState() {
        classicBacktrack.reset(false);
        modernBacktrack.reset(false);
        hitSyncBacktrack.reset(false);
        if (Miau.backtrackPacketController != null) {
            Miau.backtrackPacketController.clearReplayQueue();
        }
        shouldLag = false;
    }

}
