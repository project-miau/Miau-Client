package idle.miau.module.impl.movement.speeds;

import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.StrafeEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;

public abstract class SpeedModule extends Module {
    private static final String[] SPEED_OPTIONS = {"Legit"};

    public final SliderSetting speed;
    private final SpeedMode[] modes;
    private int selectedMode;
    private boolean modesRunning;

    protected SpeedModule() {
        super("Speed", category.movement, 0);
        setDescription("Bhop.");
        registerSetting(speed = new SliderSetting("Speed", 0, SPEED_OPTIONS));
        modes = new SpeedMode[]{new LegitSpeed(this)};
        selectedMode = getSelectedModeIndex();
        guiUpdate();
    }

    public final boolean isMode(String name) {
        return currentMode().getName().equals(name);
    }

    @Override
    public String getInfo() {
        synchronizeMode();
        return currentMode().getName();
    }

    @Override
    public void guiUpdate() {
        synchronizeMode();
        for (SpeedMode mode : modes) {
            mode.updateVisibility();
        }
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        super.guiButtonToggled(setting);
        for (SpeedMode mode : modes) {
            mode.onButtonToggled(setting);
        }
        guiUpdate();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == speed) {
            synchronizeMode();
        }
        guiUpdate();
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        if (setting == speed) {
            synchronizeMode();
        }
        for (SpeedMode mode : modes) {
            mode.onSettingChanged(setting);
        }
        guiUpdate();
    }

    @Override
    public void onEnable() {
        synchronizeMode();
        modesRunning = true;
        currentMode().onEnable();
        guiUpdate();
    }

    @Override
    public void onDisable() {
        currentMode().onDisable();
        modesRunning = false;
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        synchronizeMode();
        currentMode().onPreUpdate(event);
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onClientRotation(ClientRotationEvent event) {
        synchronizeMode();
        currentMode().onClientRotation(event);
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPrePlayerInput(PrePlayerInputEvent event) {
        synchronizeMode();
        currentMode().onPrePlayerInput(event);
    }

    @Subscribe
    public void onPreMotion(PreMotionEvent event) {
        synchronizeMode();
        currentMode().onPreMotion(event);
    }

    @Subscribe
    public void onStrafe(StrafeEvent event) {
        synchronizeMode();
        currentMode().onStrafe(event);
    }

    @Subscribe(receiveCanceled = true)
    public void onReceivePacket(ReceivePacketEvent event) {
        synchronizeMode();
        currentMode().onReceivePacket(event);
    }

    private void synchronizeMode() {
        if (modes == null) {
            return;
        }
        int nextMode = getSelectedModeIndex();
        if (nextMode == selectedMode) {
            return;
        }
        if (modesRunning) {
            modes[selectedMode].onDisable();
        }
        selectedMode = nextMode;
        if (modesRunning) {
            modes[selectedMode].onEnable();
        }
    }

    private SpeedMode currentMode() {
        return modes[selectedMode];
    }

    private int getSelectedModeIndex() {
        return Math.max(0, Math.min((int) speed.getInput(), modes.length - 1));
    }
}
