package idle.miau.module.impl.misc.disablers;

import idle.miau.event.PreUpdateEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PushOutOfBlockEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.event.StrafeEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.SliderSetting;

public abstract class DisablerModule extends Module {
    private final SliderSetting mode;
    private final String[] modes = {"VulcanScaffold", "DelayedTransaction", "Miniblox"};
    private final DisablerMode[] modesList;
    private int activeModeIndex;

    protected DisablerModule() {
        super("Disabler", category.other);
        setDescription("Use packets to try disable some AntiCheat checks.");
        registerSetting(mode = new SliderSetting("Mode", 0, modes));
        modesList = new DisablerMode[]{
                new VulcanScaffold(this),
                new TransactionDisabler(this),
                new Miniblox(this)
        };
        activeModeIndex = selectedModeIndex();
        updateModeSettings();
    }

    @Override
    public String getInfo() {
        synchronizeMode();
        return activeMode() instanceof VulcanScaffold
                ? "VulcanScaffold (retired)" : activeMode().getName();
    }

    @Override
    public void guiUpdate() {
        synchronizeMode();
        updateModeSettings();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            synchronizeMode();
            updateModeSettings();
        }
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        synchronizeMode();
        updateModeSettings();
    }

    @Override
    public void onEnable() {
        activeModeIndex = selectedModeIndex();
        activeMode().onEnable();
    }

    @Override
    public void onDisable() {
        activeMode().onDisable();
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        synchronizeMode();
        activeMode().onPreUpdate(event);
    }

    @Subscribe
    public void onPreMotion(PreMotionEvent event) {
        synchronizeMode();
        activeMode().onPreMotion(event);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPushOutOfBlock(PushOutOfBlockEvent event) {
        synchronizeMode();
        activeMode().onPushOutOfBlock(event);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent event) {
        synchronizeMode();
        activeMode().onReceivePacket(event);
    }

    @Subscribe
    public void onStrafe(StrafeEvent event) {
        synchronizeMode();
        activeMode().onStrafe(event);
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        synchronizeMode();
        activeMode().onSendPacket(event);
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        for (DisablerMode disablerMode : modesList) {
            disablerMode.onWorldUnload(event);
        }
    }

    private int selectedModeIndex() {
        int index = (int) mode.getInput();
        int maximum = modesList == null ? modes.length - 1 : modesList.length - 1;
        return Math.max(0, Math.min(index, maximum));
    }

    private void synchronizeMode() {
        if (modesList == null || modesList.length == 0) {
            return;
        }
        int selected = selectedModeIndex();
        if (selected == activeModeIndex) {
            return;
        }
        if (isEnabled()) {
            activeMode().onDisable();
        }
        activeModeIndex = selected;
        updateModeSettings();
        if (isEnabled()) {
            activeMode().onEnable();
        }
    }

    private DisablerMode activeMode() {
        return modesList[activeModeIndex];
    }

    final boolean isActiveMode(DisablerMode disablerMode) {
        return modesList != null && activeMode() == disablerMode;
    }

    private void updateModeSettings() {
        if (modesList == null) {
            return;
        }
        for (DisablerMode disablerMode : modesList) {
            disablerMode.onGuiUpdate();
        }
    }
}
