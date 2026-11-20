package idle.miau.module.impl.combat;

import idle.miau.event.ClientRotationEvent;
import idle.miau.helper.RotationManager;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.PlayerUtils;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBow;

import java.util.Comparator;
import java.util.List;

public final class BowAimbot extends Module {
    private final SliderSetting fov;
    private final SliderSetting predictSize;
    private final RotationManager rotationManager;
    private final ButtonSetting autoRelease;
    private EntityLivingBase target;
    public static boolean canShoot;

    public BowAimbot() {
        super("BowAimbot", category.combat);
        setDescription("Auto aim bow.");
        registerSetting(fov = new SliderSetting("Fov", 90.0D, 10.0D, 180.0D, 1.0D));
        registerSetting(predictSize = new SliderSetting(
                "Predict size", 2.0D, 0.1D, 5.0D, 0.1D));
        rotationManager = new RotationManager(this);
        registerSetting(autoRelease = new ButtonSetting("Auto release", false));
    }

    @Override
    public void onUpdate() {
        if (autoRelease.isToggled() && canShoot && Utils.nullCheck()) {
            mc.playerController.onStoppedUsingItem(mc.thePlayer);
        }
    }

    @Override
    public void onDisable() {
        canShoot = false;
        target = null;
        rotationManager.reset();
    }

    @Override
    public void guiUpdate() {
        rotationManager.updateVisibility();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        rotationManager.updateVisibility();
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        rotationManager.updateVisibility();
        super.guiButtonToggled(setting);
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        rotationManager.updateVisibility();
    }

    @Subscribe(priority = Priority.LOW)
    public void onClientRotation(ClientRotationEvent event) {
        canShoot = false;
        target = null;
        if (!Utils.nullCheck()) {
            rotationManager.reset();
            return;
        }
        List<EntityLivingBase> targets = PlayerUtils.getTargets(500.0D);
        targets.sort(Comparator.comparingDouble(RotationUtils::getFovToTarget));
        if (targets.isEmpty()) {
            rotationManager.reset();
            return;
        }
        EntityLivingBase nextTarget = targets.get(0);
        if (RotationUtils.getFovToTarget(nextTarget) >= fov.getInput()
                || mc.thePlayer.getHeldItem() == null
                || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow)
                || !mc.thePlayer.isUsingItem()) {
            rotationManager.reset();
            return;
        }
        target = nextTarget;
        int useTicks = 72000 - mc.thePlayer.getItemInUseCount();
        float power = useTicks / 20.0F;
        power = (power * power + power * 2.0F) / 3.0F;
        if (power > 1.0F) {
            power = 1.0F;
        }
        float[] targetRotation = RotationUtils.faceTrajectory(
                target, (float) predictSize.getInput(), 0.05F, power);
        float[] currentRotation = rotationManager.getCurrentRotation(event);
        if (RotationUtils.getRotationDifference(currentRotation, targetRotation) < 0.05F) {
            canShoot = true;
        }
        rotationManager.setRotation(event, targetRotation);
    }
}
