package idle.miau.module.impl.movement.fly;

import idle.miau.event.CollisionEvent;
import idle.miau.event.DispatchPacketEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.accessor.IAccessorEntityPlayer;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.impl.movement.fly.modes.FastFly;
import idle.miau.module.impl.movement.fly.modes.GrimFireBall;
import idle.miau.module.impl.movement.fly.modes.IntaveFlagFly;
import idle.miau.module.impl.movement.fly.modes.IntaveFireBall;
import idle.miau.module.impl.movement.fly.modes.IntaveFireBall2;
import idle.miau.module.impl.movement.fly.modes.VanillaFly;
import idle.miau.module.impl.movement.fly.modes.WalkFly;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;

import java.awt.Color;

public abstract class FlyModule extends Module {
    private static final String[] MODE_NAMES = {
            "Vanilla", "Fast", "IntaveFlagFly", "Walk", "IntaveFireBall",
            "IntaveFireBall2", "GrimFireBall"
    };

    private final SliderSetting mode;
    private final SliderSetting horizontalSpeed;
    private final SliderSetting verticalSpeed;
    private final SliderSetting maxFlyTicksValue;
    private final ButtonSetting showBPS;
    private final ButtonSetting stopMotion;
    private final ButtonSetting mark;
    private final FlyMode[] modesList;
    private FlyMode activeMode;
    private boolean modesRunning;
    private double startY;
    private boolean wasFlying;

    protected FlyModule() {
        super("Fly", category.movement, 0);
        setDescription("Lets you fly using the selected movement mode.");
        registerSetting(mode = new SliderSetting("Fly", 0, MODE_NAMES), "setting.mode");
        registerSetting(horizontalSpeed = new SliderSetting(
                "Horizontal speed", 2.0D, 1.0D, 9.0D, 0.1D),
                "setting.horizontalSpeed");
        registerSetting(verticalSpeed = new SliderSetting(
                "Vertical speed", 2.0D, 1.0D, 9.0D, 0.1D),
                "setting.verticalSpeed");

        VanillaFly vanilla = new VanillaFly(this);
        FastFly fast = new FastFly(this);
        IntaveFlagFly intaveFlag = new IntaveFlagFly(this);
        WalkFly walk = new WalkFly(this);
        IntaveFireBall intaveFireBall = new IntaveFireBall(this);
        IntaveFireBall2 intaveFireBall2 = new IntaveFireBall2(this);
        GrimFireBall grimFireBall = new GrimFireBall(this);
        modesList = new FlyMode[]{
                vanilla, fast, intaveFlag, walk, intaveFireBall,
                intaveFireBall2, grimFireBall
        };

        registerSetting(maxFlyTicksValue = new SliderSetting(
                "MaxFlyTicks", 15.0D, 5.0D, 30.0D, 1.0D),
                "setting.maxFlyTicks");
        registerSetting(showBPS = new ButtonSetting("Show BPS", false), "setting.showBPS");
        registerSetting(stopMotion = new ButtonSetting("Stop motion", false), "setting.stopMotion");
        registerSetting(walk.keepY, "setting.keepY");
        registerSetting(mark = new ButtonSetting("Mark", true), "setting.mark");
        registerSetting(intaveFireBall.motionMode, "setting.intaveMotionMode");
        registerSetting(intaveFireBall2.loops, "setting.intaveGlideLoops");
        registerSetting(grimFireBall.mode, "setting.grimFireBallMode");
        registerSetting(grimFireBall.loops, "setting.grimGlideLoops");
        synchronizeMode();
        guiUpdate();
    }

    public final boolean isMode(String name) {
        return selectedMode().getName().equals(name);
    }

    public final double getHorizontalSpeed() {
        return horizontalSpeed.getInput();
    }

    public final double getVerticalSpeed() {
        return verticalSpeed.getInput();
    }

    public final int getMaxFlyTicksValue() {
        return (int) maxFlyTicksValue.getInput();
    }

    @Override
    public String getInfo() {
        synchronizeMode();
        return activeMode.getName();
    }

    @Override
    public void guiUpdate() {
        synchronizeMode();
        horizontalSpeed.setVisible(activeMode.usesSpeedSettings(), this);
        verticalSpeed.setVisible(activeMode.usesSpeedSettings(), this);
        maxFlyTicksValue.setVisible(activeMode.usesMaxFlyTicksSetting(), this);
        stopMotion.setVisible(activeMode.usesStopMotionSetting()
                && !isMode("IntaveFlagFly"), this);
        mark.setVisible(true, this);
        for (FlyMode flyMode : modesList) {
            flyMode.updateVisibility(flyMode == activeMode);
        }
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        super.guiButtonToggled(setting);
        activeMode.onButtonToggled(setting);
        guiUpdate();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        synchronizeMode();
        activeMode.onSettingChanged(setting);
        guiUpdate();
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        synchronizeMode();
        activeMode.onSettingChanged(setting);
        guiUpdate();
    }

    @Override
    public void onEnable() {
        synchronizeMode();
        modesRunning = true;
        if (mc == null || mc.thePlayer == null) {
            guiUpdate();
            return;
        }
        startY = mc.thePlayer.posY;
        wasFlying = mc.thePlayer.capabilities.isFlying;
        activeMode.onEnable();
        guiUpdate();
    }

    @Override
    public void onDisable() {
        if (activeMode == null) {
            modesRunning = false;
            return;
        }
        if (activeMode instanceof IntaveFlagFly) {
            if (mc == null || mc.thePlayer == null) {
                modesRunning = false;
                return;
            }
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionY = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
            mc.thePlayer.capabilities.isFlying = wasFlying;
            mc.getTimer().timerSpeed = 1.0F;
            ((IAccessorEntityPlayer) mc.thePlayer).setSpeedInAir(0.02F);
            activeMode.onDisable();
            modesRunning = false;
            return;
        }
        activeMode.onDisable();
        modesRunning = false;
        if (activeMode.usesStopMotionSetting()
                && stopMotion.isToggled() && mc != null && mc.thePlayer != null) {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionY = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
        }
    }

    @Override
    public void onUpdate() {
        synchronizeMode();
        activeMode.onUpdate();
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        synchronizeMode();
        activeMode.onPreUpdate(event);
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPreMotion(PreMotionEvent event) {
        synchronizeMode();
        activeMode.onPreMotion(event);
    }

    @Subscribe
    public void onCollision(CollisionEvent event) {
        synchronizeMode();
        activeMode.onCollision(event);
    }

    @Subscribe(priority = Priority.HIGHEST, receiveCanceled = true)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        synchronizeMode();
        activeMode.onReceivePacket(event);
    }

    @Subscribe(priority = Priority.HIGHEST, receiveCanceled = true)
    public void onSendPacket(SendPacketEvent event) {
        synchronizeMode();
        activeMode.onSendPacket(event);
    }

    @Subscribe
    public void onDispatchPacket(DispatchPacketEvent event) {
        synchronizeMode();
        activeMode.onDispatchPacket(event);
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent event) {
        synchronizeMode();
        if (!mark.isToggled() || !Utils.nullCheck() || isMode("Vanilla")) {
            return;
        }
        double y = startY + 2.0D;
        Color color = mc.thePlayer.getEntityBoundingBox().maxY < y
                ? new Color(0, 255, 0, 90)
                : new Color(255, 0, 0, 90);
        RenderUtils.drawLiquidBouncePlatform(y, color, 1.0D);
        activeMode.onRenderWorld(event);
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!showBPS.isToggled() || event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }
        RenderUtils.renderBPS(true, false);
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        if (activeMode != null) {
            activeMode.onWorldUnload(event);
        }
    }

    private FlyMode selectedMode() {
        int index = Math.max(0, Math.min(modesList.length - 1, (int) mode.getInput()));
        return modesList[index];
    }

    private void synchronizeMode() {
        FlyMode selected = selectedMode();
        if (selected == activeMode) {
            return;
        }
        if (modesRunning && activeMode != null) {
            activeMode.onDisable();
        }
        activeMode = selected;
        if (modesRunning) {
            activeMode.onEnable();
        }
    }
}
