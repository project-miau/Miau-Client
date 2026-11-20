package idle.miau.module.impl.player;

import idle.miau.event.ClientRotationEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ClientContext;
import idle.miau.utility.SemanticEvent;
import idle.miau.utility.placement.ClutchState;
import idle.miau.utility.placement.PlacementFrame;
import idle.miau.utility.placement.PlacementSelection;
import idle.miau.utility.rotation.MiauRandom;
import idle.miau.helper.PlacementRotationHelper;

public final class Clutch extends Module {
    private static final float CLICK_SPEED = 20.0F;

    private final ClutchState.State state = new ClutchState.State();
    private final SliderSetting range = new SliderSetting("Range", 7.0, 1.0, 7.0, 1.0);
    private final SliderSetting maxAimDist = new SliderSetting("FOV", 360.0, 50.0, 360.0, 0.001);
    private final SliderSetting minHeight = new SliderSetting("Minimum height", 4.0, 1.0, 10.0, 1.0);
    private final SliderSetting aimSpeed = new SliderSetting("Aim speed", "", 600.0, 400.0, 3600.0, 20.0, "Base speed");
    private final SliderSetting aimMult = new SliderSetting("Aim boost", "", 0.5, 0.3, 1.0, 0.001, "Acceleration");
    private final SliderSetting aimMultStrength = new SliderSetting("Aim boost amount", "%", 30.0, 0.0, 100.0, 0.1, "Acceleration strength");
    private final ButtonSetting aimMultipoint = new ButtonSetting("Flexible", false, "Multipoint");
    private final SliderSetting snapbackDelay = new SliderSetting("Return view delay", "ms", 300.0, 50.0, 500.0, 50.0, "Delay");
    private final SliderSetting snapbackDuration = new SliderSetting("Return view time", "ms", 200.0, 50.0, 500.0, 50.0, "Duration");
    private final ButtonSetting snapbackInstantOnJump = new ButtonSetting("Return view on jump", false, "Keep jump direction");
    private final ButtonSetting snapbackDisableAfter = new ButtonSetting("Auto disable", false, "Disable afterwards");
    private final ButtonSetting requireAir = new ButtonSetting("Only Fall", true, "In air", "In Air");
    private final ButtonSetting requireDamage = new ButtonSetting("Damaged", false);

    public Clutch() {
        super("Clutch", category.player);
        setDescription("Auto places blocks to save you from fall.");
        registerSetting(range);
        registerSetting(maxAimDist);
        registerSetting(minHeight);
        registerSetting(aimSpeed);
        registerSetting(aimMult);
        registerSetting(aimMultStrength);
        registerSetting(aimMultipoint);
        registerSetting(snapbackDelay);
        registerSetting(snapbackDuration);
        registerSetting(snapbackInstantOnJump);
        registerSetting(snapbackDisableAfter);
        registerSetting(requireAir);
        registerSetting(requireDamage);
    }

    private ClutchState.Settings settings() {
        ClutchState.Settings s = new ClutchState.Settings();
        s.range = (float)(range.getInput());
        s.fov = (float)(maxAimDist.getInput());
        s.height = (float)(minHeight.getInput());
        s.clickSpeed = CLICK_SPEED;
        s.randomization = 0.5F;
        s.aimSpeed = (float)(aimSpeed.getInput() / 20.0D);
        s.aimMult = (float)(aimMult.getInput());
        s.aimStrength = (float)(aimMultStrength.getInput() / 100.0D);
        s.snapDelay = (float)(snapbackDelay.getInput() / 50.0D);
        s.snapDuration = (float)(snapbackDuration.getInput() / 50.0D);
        s.select = 2;
        s.sideways = false;
        s.multipoint = aimMultipoint.isToggled();
        s.instantJump = snapbackInstantOnJump.isToggled();
        s.disableAfter = snapbackDisableAfter.isToggled();
        s.air = requireAir.isToggled();
        s.damage = requireDamage.isToggled();
        s.backwards = false;
        return s;
    }

    public void placementTick(ClientContext context) {
        state.placementTick(new PlacementFrame.Game(context, this), CLICK_SPEED);
    }

    public void update(ClientContext context) {
        state.update(new PlacementFrame.Game(context, this), settings(), MiauRandom.SHARED, MiauRandom.now());
    }

    public boolean correctMovement() {
        return state.correctMovement;
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onClientRotation(ClientRotationEvent event) {
        if (mc.thePlayer != null && PlacementRotationHelper.storedRotationValid()
                && event.trySetRotation(this, 1000, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch)) {
            event.setRotationAlreadyQuantized(true);
            event.setMovementYawCorrection(false);
        }
    }

    @Override
    public void onDisable() {
        state.clear();
        PlacementSelection.release(this);
        ClientContext context = new ClientContext(mc);
        SemanticEvent event = new SemanticEvent(13);
        event.active = false;
        PlacementRotationHelper.finishFrame(context, event);
        PlacementRotationHelper.reset(context);
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        onDisable();
        PlacementSelection.reset();
    }
}
