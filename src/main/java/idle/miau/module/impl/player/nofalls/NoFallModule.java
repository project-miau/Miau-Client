package idle.miau.module.impl.player.nofalls;

import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PostUpdateEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.SliderSetting;

public abstract class NoFallModule extends Module {
    private final SliderSetting mode;
    private final String[] modes = {"Spoof", "NoGround", "MLG"};
    private final NoFallMode[] modesList;
    private int activeModeIndex;

    protected NoFallModule() {
        super("NoFall", category.player);
        setDescription("Removes fall damage");
        registerSetting(mode = new SliderSetting("Mode", 0, modes));
        modesList = new NoFallMode[]{
                new SpoofNoFall(this),
                new NoGroundNoFall(this),
                new WaterBucketNoFall(this)
        };
        activeModeIndex = selectedModeIndex();
        guiUpdate();
        closetModule = true;
    }

    public boolean isMode(String name) {
        return name.equals(modes[selectedModeIndex()]);
    }

    @Override
    public String getInfo() {
        synchronizeMode();
        return activeMode().getName();
    }

    @Override
    public void guiUpdate() {
        synchronizeMode();
        for (NoFallMode noFallMode : modesList) {
            noFallMode.updateVisibility();
        }
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            synchronizeMode();
        }
        guiUpdate();
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        synchronizeMode();
        guiUpdate();
    }

    @Override
    public void onEnable() {
        activeModeIndex = selectedModeIndex();
        activeMode().onEnable();
        guiUpdate();
    }

    @Override
    public void onDisable() {
        activeMode().onDisable();
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onClientRotation(ClientRotationEvent event) {
        synchronizeMode();
        activeMode().onClientRotation(event);
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        synchronizeMode();
        activeMode().onPreUpdate(event);
    }

    @Subscribe
    public void onPostUpdate(PostUpdateEvent event) {
        synchronizeMode();
        activeMode().onPostUpdate(event);
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPreMotion(PreMotionEvent event) {
        synchronizeMode();
        activeMode().onPreMotion(event);
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        for (NoFallMode noFallMode : modesList) {
            noFallMode.onWorldUnload(event);
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
        if (isEnabled()) {
            activeMode().onEnable();
        }
    }

    private NoFallMode activeMode() {
        return modesList[activeModeIndex];
    }
}
