package idle.miau.module.impl.combat;

import idle.miau.event.AttackEvent;
import idle.miau.event.PostPlayerInputEvent;
import idle.miau.event.PostUpdateEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.TimerUtil;
import idle.miau.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S19PacketEntityStatus;

public final class SprintReset extends Module {
    private static final int W_TAP_MODE = 0;
    private static final int NO_STOP_MODE = 1;
    private static final double MAX_TARGET_RANGE = 6.0D;
    private static final String[] MODES = {"WTap", "NoStop"};

    public final SliderSetting mode;
    private final RangeSetting wait;
    private final RangeSetting delay;
    private final ButtonSetting onGroundOnly;
    private final TimerUtil timer = new TimerUtil();

    private int waitMillis;
    private int delayMillis;
    private int attackTick = -1;
    private Entity lastAttackedEntity;
    private long lastAttackTime;
    private KeyBinding spoofedKey;
    private boolean spoofedKeyState;

    public SprintReset() {
        super("SprintReset", category.combat);
        setDescription("Resets sprint during combat to increase knockback.");
        mode = new SliderSetting("Mode", W_TAP_MODE, MODES);
        wait = new RangeSetting("Wait", 0.0D, 500.0D, 175.0D, 250.0D, 25.0D);
        delay = new RangeSetting("Delay", 0.0D, 500.0D, 100.0D, 200.0D, 25.0D);
        onGroundOnly = new ButtonSetting("OnGroundOnly", false);
        registerSetting(mode);
        registerSetting(wait);
        registerSetting(delay);
        registerSetting(onGroundOnly);
        closetModule = true;
        guiUpdate();
    }

    @Override
    public String getInfo() {
        int selectedMode = Math.max(W_TAP_MODE, Math.min(NO_STOP_MODE, (int) mode.getInput()));
        return MODES[selectedMode];
    }

    @Override
    public void guiUpdate() {
        boolean inputMode = isInputMode();
        wait.setVisible(inputMode, this);
        delay.setVisible(inputMode, this);
        onGroundOnly.setVisible(true, this);
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            restoreSpoofedKey();
            resetState();
        }
        guiUpdate();
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        if (setting == mode) {
            restoreSpoofedKey();
            resetState();
        }
        guiUpdate();
    }

    @Override
    public void onEnable() {
        resetState();
        timer.reset();
    }

    @Override
    public void onDisable() {
        restoreSpoofedKey();
        resetState();
    }

    @Subscribe(priority = Priority.LOW)
    public void onPacketReceive(ReceivePacketEvent event) {
        if (!Utils.nullCheck() || !mc.thePlayer.isSprinting()) {
            return;
        }
        if (!(event.getPacket() instanceof S19PacketEntityStatus)
                || !timer.hasTimeElapsed(waitMillis + delayMillis)) {
            return;
        }

        S19PacketEntityStatus status = (S19PacketEntityStatus) event.getPacket();
        Entity damagedEntity = status.getEntity(mc.theWorld);
        if (status.getOpCode() != 2 || !isValidTarget(damagedEntity)) {
            return;
        }

        waitMillis = (int) wait.getRandomInRange();
        delayMillis = (int) delay.getRandomInRange();
        attackTick = mc.thePlayer.ticksExisted;
        timer.reset();
    }

    @Subscribe
    public void onPreMotion(PreMotionEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }

        if (isMode(NO_STOP_MODE)
                && attackTick + 1 == mc.thePlayer.ticksExisted
                && (mc.thePlayer.onGround || !onGroundOnly.isToggled())) {
            mc.thePlayer.setSprinting(false);
        }

        if (!mc.thePlayer.onGround
                && !timer.hasTimeElapsed(waitMillis)
                && !onGroundOnly.isToggled()) {
            waitMillis = 0;
            delayMillis = 0;
            attackTick = -1;
        }
    }

    @Subscribe
    public void onPrePlayerInput(PrePlayerInputEvent event) {
        if (!Utils.nullCheck() || attackTick < 0) {
            return;
        }

        if (isMode(NO_STOP_MODE) && attackTick + 1 == mc.thePlayer.ticksExisted) {
            spoofKey(mc.gameSettings.keyBindSprint, false);
        }

        if (!isInputWindow()) {
            return;
        }

        if (isMode(W_TAP_MODE)) {
            boolean wasDown = mc.gameSettings.keyBindForward.isKeyDown();
            spoofKey(mc.gameSettings.keyBindForward, false);
            if (wasDown) {
                event.setForward(event.getForward() - 1.0F);
            }
        }
    }

    @Subscribe
    public void onPostPlayerInput(PostPlayerInputEvent event) {
        if (!isMode(NO_STOP_MODE)) {
            restoreSpoofedKey();
        }
    }

    @Subscribe
    public void onPostUpdate(PostUpdateEvent event) {
        restoreSpoofedKey();
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        if (event.target != null && (event.attacker == null || event.attacker == mc.thePlayer)) {
            lastAttackedEntity = event.target;
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @Subscribe
    public void onWorldUnload(idle.miau.mcp.event.world.WorldEvent.Unload event) {
        restoreSpoofedKey();
        resetState();
    }

    public static boolean breakSprint() {
        SprintReset module = ModuleManager.sprintReset;
        return module != null && module.isEnabled() && module.shouldBreakSprint();
    }

    public static boolean startSprint() {
        return false;
    }

    private boolean shouldBreakSprint() {
        return Utils.nullCheck()
                && isMode(NO_STOP_MODE)
                && attackTick + 1 == mc.thePlayer.ticksExisted
                && (mc.thePlayer.onGround || !onGroundOnly.isToggled());
    }

    private boolean isInputWindow() {
        if (attackTick < 0) {
            return false;
        }
        boolean beforeTotalDelay = !timer.hasTimeElapsed(waitMillis + delayMillis);
        boolean firstDelayComplete = timer.hasTimeElapsed(waitMillis) || waitMillis == 0;
        return (beforeTotalDelay || attackTick == mc.thePlayer.ticksExisted)
                && (firstDelayComplete || attackTick == mc.thePlayer.ticksExisted);
    }

    private boolean isValidTarget(Entity entity) {
        return entity instanceof EntityPlayer
                && entity != mc.thePlayer
                && !entity.isDead
                && entity.isEntityAlive()
                && !AntiBot.isBot(entity)
                && !Utils.isFriended((EntityPlayer) entity)
                && !Utils.isTeammate((EntityPlayer) entity)
                && isCurrentOrRecentTarget(entity)
                && isInTargetRange(entity);
    }

    private boolean isCurrentOrRecentTarget(Entity entity) {
        if (KillAura.target == entity) {
            return true;
        }
        if (lastAttackedEntity == entity && (System.currentTimeMillis() - lastAttackTime) <= 1000L) {
            return true;
        }
        return mc.objectMouseOver != null && mc.objectMouseOver.entityHit == entity;
    }

    private boolean isInTargetRange(Entity entity) {
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        return distance <= MAX_TARGET_RANGE;
    }

    private boolean isInputMode() {
        return isMode(W_TAP_MODE);
    }

    private boolean isMode(int selectedMode) {
        return (int) mode.getInput() == selectedMode;
    }

    private void spoofKey(KeyBinding key, boolean pressed) {
        if (spoofedKey != null) {
            return;
        }
        spoofedKey = key;
        spoofedKeyState = key.isKeyDown();
        KeyBinding.setKeyBindState(key.getKeyCode(), pressed);
    }

    private void restoreSpoofedKey() {
        if (spoofedKey == null) {
            return;
        }
        KeyBinding.setKeyBindState(spoofedKey.getKeyCode(), spoofedKeyState);
        spoofedKey = null;
    }

    private void resetState() {
        waitMillis = 0;
        delayMillis = 0;
        attackTick = -1;
        lastAttackedEntity = null;
        lastAttackTime = 0L;
    }
}
