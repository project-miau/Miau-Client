package idle.miau.module.impl.combat;

import idle.miau.event.ClientRotationEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AimAssist extends Module {

    private SliderSetting mode;
    private SliderSetting speed;
    private SliderSetting aimPoint;
    private SliderSetting humanization;
    private SliderSetting fov;
    private SliderSetting range;
    private ButtonSetting requireAttack;
    private ButtonSetting pauseWhenBreak;
    private ButtonSetting ignoreTeammates;
    private ButtonSetting visibleTargets;

    private static final String[] AIM_MODES = new String[]{"Normal", "Silent"};

    public AimAssist() {
        super("Aim Assist", category.combat);
        setDescription("Helps aim at nearby targets within your FOV.");
        this.registerSetting(mode = new SliderSetting("Mode", 0, AIM_MODES));
        this.registerSetting(speed = new SliderSetting("Speed", 10, 1, 30, 1));
        this.registerSetting(aimPoint = new SliderSetting(
                "Aim point", "%", 0, 0, 100, 1, "Multipoint horizontal"),
                "setting.multipointHorizontal");
        this.registerSetting(humanization = new SliderSetting(
                "Humanization", "%", 50, 0, 100, 1, "Randomization"),
                "setting.randomization");
        this.registerSetting(fov = new SliderSetting("FOV", 90.0D, 15.0D, 360.0D, 1.0D));
        this.registerSetting(range = new SliderSetting("Range", 4.5D, 0.0D, 5.0D, 0.1D));
        this.registerSetting(requireAttack = new ButtonSetting(
                "Require attack", true, "Require mouse", "Condition.Require mouse"),
                "setting.clickAim");
        this.registerSetting(pauseWhenBreak = new ButtonSetting("Pause when break", true));
        this.registerSetting(visibleTargets = new ButtonSetting(
                "Visible targets only", false, "Ignore behind walls", "Targets.Ignore behind walls"),
                "setting.ignoreBehindWalls");
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
    }

    @Override
    public String getInfo() {
        return AIM_MODES[(int) mode.getInput()];
    }

    public Entity getAimAssistTarget() {
        if (!Utils.nullCheck()) {
            return null;
        }
        if (ModuleManager.bedAura != null
                && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return null;
        }
        if (ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target != null) {
            return null;
        }
        if (!conditionsMet()) {
            return null;
        }
        return getEnemy(mode.getInput() == 1.0D);
    }

    public EntityPlayer getTarget() {
        Entity target = getAimAssistTarget();
        return target instanceof EntityPlayer ? (EntityPlayer) target : null;
    }

    @Subscribe(priority = Priority.NORMAL)
    public void onClientRotation(ClientRotationEvent e) {
        if (ModuleManager.bedAura != null
                && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return;
        }
        if (ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target != null) {
            return;
        }
        if (mode.getInput() == 0.0D || !conditionsMet()) {
            return;
        }

        Entity target = getEnemy(true);
        if (target == null) {
            return;
        }

        double point = aimPoint.getInput();
        boolean requireVisibility = visibleTargets.isToggled();
        float[] rotations = RotationHelper.get().getRotationsToTarget(
                target,
                e,
                (int) speed.getInput(),
                point,
                point,
                (float) humanization.getInput(),
                requireVisibility,
                range.getInput(),
                !requireVisibility,
                !requireVisibility);
        if (rotations == null) {
            return;
        }

        RotationHelper.get().forceMovementFix = true;
        e.yaw = rotations[0];
        e.pitch = rotations[1];
    }

    @Override
    public void onUpdate() {
        if (ModuleManager.bedAura != null
                && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return;
        }
        if (ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target != null) {
            return;
        }
        if (mode.getInput() == 1.0D || !conditionsMet()) {
            return;
        }

        Entity target = getEnemy(false);
        if (target == null) {
            return;
        }

        double point = aimPoint.getInput();
        boolean requireVisibility = visibleTargets.isToggled();
        float[] rotations = RotationHelper.get().getRotationsToTarget(
                target,
                (int) speed.getInput(),
                point,
                point,
                (float) humanization.getInput(),
                requireVisibility,
                range.getInput(),
                !requireVisibility,
                !requireVisibility);
        if (rotations == null) {
            return;
        }

        mc.thePlayer.rotationYaw = rotations[0];
        mc.thePlayer.rotationPitch = rotations[1];
    }

    private Entity getEnemy(boolean silentMode) {
        int fovValue = (int) fov.getInput();
        float viewYaw = mc.thePlayer.rotationYaw;
        float viewPitch = mc.thePlayer.rotationPitch;
        if (silentMode) {
            Float serverYaw = RotationHelper.get().getServerYaw();
            if (serverYaw != null) {
                viewYaw = serverYaw;
            }
            Float serverPitch = RotationHelper.get().getServerPitch();
            if (serverPitch != null) {
                viewPitch = serverPitch;
            }
        }

        List<EntityPlayer> candidates = new ArrayList<>();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.deathTime != 0) {
                continue;
            }
            if (Utils.isFriended(player)) {
                continue;
            }
            if (ignoreTeammates.isToggled() && Utils.isTeammate(player)) {
                continue;
            }
            if (player.isInvisible()) {
                continue;
            }
            if (RotationUtils.distanceSqFromEyeToClosestOnAABB(player)
                    > range.getInput() * range.getInput()) {
                continue;
            }
            if (AntiBot.isBot(player)) {
                continue;
            }
            if (fovValue != 360) {
                float angleToEntity = RotationUtils.angle(player.posX, player.posZ);
                if (!Utils.inFov(viewYaw, fovValue, angleToEntity)) {
                    continue;
                }
            }
            candidates.add(player);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        final float selectionYaw = viewYaw;
        final float selectionPitch = viewPitch;
        Comparator<EntityPlayer> primary = Comparator.comparingDouble(player -> {
            double yawDifference = Math.abs(MathHelper.wrapAngleTo180_float(
                    selectionYaw - Utils.getYaw(player)));
            double pitchDifference = Math.abs(MathHelper.wrapAngleTo180_float(
                    selectionPitch - Utils.getPitch(player)));
            return yawDifference + pitchDifference;
        });
        candidates.sort(primary.thenComparingDouble(
                player -> mc.thePlayer.getDistanceSqToEntity(player)));

        if (visibleTargets.isToggled()) {
            double point = aimPoint.getInput();
            double rangeValue = range.getInput();
            for (EntityPlayer candidate : candidates) {
                if (RotationUtils.hasValidAimPoint(
                        candidate,
                        point,
                        point,
                        rangeValue,
                        false,
                        false)) {
                    return candidate;
                }
            }
            return null;
        }

        return candidates.get(0);
    }

    private boolean conditionsMet() {
        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            return false;
        }
        if (requireAttack.isToggled()
                && !GameSettings.isKeyDown(mc.gameSettings.keyBindAttack)) {
            return false;
        }
        return !pauseWhenBreak.isToggled() || !Utils.isMining();
    }
}
