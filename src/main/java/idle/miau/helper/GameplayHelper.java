package idle.miau.helper;

import idle.miau.module.ModuleManager;
import idle.miau.module.impl.player.Backtrack;
import idle.miau.utility.placement.PlacementSelection;
import idle.miau.utility.ClientContext;
import idle.miau.utility.SemanticEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public final class GameplayHelper {
    private static final TargetCombatStatus COMBAT = new TargetCombatStatus();
    private static double previousDisplaySeconds;

    private GameplayHelper() {
    }

    public static ClientContext context() {
        return new ClientContext(Minecraft.getMinecraft());
    }

    private static boolean clutchEnabled() {
        return ModuleManager.clutch != null && ModuleManager.clutch.isEnabled();
    }

    private static boolean normalEnabled() {
        return ModuleManager.backtrack != null && ModuleManager.backtrack.isEnabled()
                && ModuleManager.backtrack.isNormalMode();
    }

    public static void beginRay(float partialTicks) {
        ClientContext context = context();
        if (!context.ready()) {
            COMBAT.clear();
            return;
        }
        if (clutchEnabled()) {
            PlacementRotationHelper.beginFrame(context, 0.0D, partialTicks);
        }
        if (normalEnabled()) {
            COMBAT.select(context);
        }
        if (ModuleManager.autoblock != null && ModuleManager.autoblock.isEnabled()
                && ModuleManager.autoblock.isNormalMode()) {
            ModuleManager.autoblock.onNormalRay(context);
        }
        if (ModuleManager.criticals != null && ModuleManager.criticals.isTimerMode()) {
            ModuleManager.criticals.onTimerRay(context);
        }
        if (clutchEnabled()) {
            ModuleManager.clutch.placementTick(context);
        }
    }

    public static void afterRay() {
        if (clutchEnabled()) {
            PlacementSelection.frameReset();
        }
    }

    public static void beforeEntities() {
        if (clutchEnabled()) {
            PlacementRotationHelper.beginMovement(context());
        }
    }

    public static void afterEntities() {
        ClientContext context = context();
        if (normalEnabled()) {
            ModuleManager.backtrack.onNormalEvent(context, new SemanticEvent(18));
        }
        if (clutchEnabled()) {
            ModuleManager.clutch.update(context);
            SemanticEvent event = new SemanticEvent(13);
            event.active = ModuleManager.clutch.correctMovement();
            event.remapMovement = event.active;
            event.yaw = PlacementRotationHelper.storedRotationValid() ? PlacementRotationHelper.cameraYaw() : context.player().rotationYaw;
            event.pitch = PlacementRotationHelper.storedRotationValid() ? PlacementRotationHelper.cameraPitch() : context.player().rotationPitch;
            PlacementRotationHelper.finishFrame(context, event);
            PlacementRotationHelper.applyPending(context, true);
        }
    }

    public static void abortEntities() {
        PlacementRotationHelper.endMovement(context());
    }

    public static void beforeDisplay() {
        ClientContext context = context();
        double now = (double)System.nanoTime() / 1.0E9;
        if (previousDisplaySeconds == 0.0D || !context.ready()) {
            previousDisplaySeconds = now;
        }
        if (!context.ready()) {
            return;
        }
        if (clutchEnabled()) {
            PlacementRotationHelper.beforeDisplay(context);
        }
        double elapsed = now - previousDisplaySeconds;
        if (!(0.002D > elapsed)) {
            if (ModuleManager.criticals != null && ModuleManager.criticals.isTimerMode()) {
                ModuleManager.criticals.onTimerFrame(elapsed);
            }
            if (clutchEnabled()) {
                PlacementRotationHelper.applyPending(context, false);
                PlacementRotationHelper.afterDisplay(context);
            }
            previousDisplaySeconds = now;
        }
    }

    public static void preRender() {
        if (clutchEnabled()) {
            PlacementRotationHelper.prepareMouse(context());
        }
    }

    public static void beginCamera() {
        if (clutchEnabled()) {
            PlacementRotationHelper.beginCamera(context());
        }
    }

    public static void endCamera() {
        PlacementRotationHelper.endCamera();
    }

    public static void beginRenderInfo() {
        if (clutchEnabled()) {
            PlacementRotationHelper.beginRenderInfo(context());
        }
    }

    public static void endRenderInfo() {
        PlacementRotationHelper.endRenderInfo();
    }

    public static void endRender() {
        PlacementRotationHelper.endRender();
    }

    public static void beforeUse() {
        ClientContext context = context();
        if (clutchEnabled() && context.ready()) {
            PlacementSelection.capture(context);
        }
    }

    public static void afterUse() {
        ClientContext context = context();
        if (clutchEnabled() && context.ready()) {
            PlacementSelection.consume(context);
        }
    }

    public static void packet(SemanticEvent event) {
        if (event.type == 2 && normalEnabled()) {
            ModuleManager.backtrack.onNormalPacket(event);
        }
    }

    public static EntityPlayer target() {
        return COMBAT.target();
    }

    public static double targetDistanceSquared() {
        return COMBAT.distanceSquared();
    }

    public static boolean hostile(EntityPlayer player) {
        Backtrack backtrack = ModuleManager.backtrack;
        return backtrack != null && backtrack.isTrackableTarget(player);
    }
}
