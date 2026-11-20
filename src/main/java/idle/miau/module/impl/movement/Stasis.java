package idle.miau.module.impl.movement;

import idle.miau.event.PlayerMoveEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.PreSuperLivingUpdateEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.event.StrafeEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.module.Module;
import idle.miau.utility.Utils;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

public final class Stasis extends Module {
    static final int STASIS_TICKS = 45;
    static final int RELEASE_TICKS = 1;

    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private int tickCounter;
    private Phase phase = Phase.STASIS;
    private boolean motionSaved;

    public Stasis() {
        super("Stasis", category.movement);
        setDescription("Pauses motion in midair with brief release intervals.");
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        phase = Phase.STASIS;
        motionSaved = false;
        saveMotion();
    }

    @Override
    public void onDisable() {
        restoreMotion();
        tickCounter = 0;
        phase = Phase.STASIS;
        motionSaved = false;
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (!motionSaved) {
            saveMotion();
        }

        tickCounter++;
        if (phase == Phase.STASIS && tickCounter >= STASIS_TICKS) {
            phase = Phase.RELEASE;
            tickCounter = 0;
            restoreMotion();
        }
        else if (phase == Phase.RELEASE && tickCounter >= RELEASE_TICKS) {
            phase = Phase.STASIS;
            tickCounter = 0;
            saveMotion();
        }

        freezeMotion();
        if (mc.thePlayer.onGround) {
            disable();
        }
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPrePlayerInput(PrePlayerInputEvent event) {
        if (!isStasisPhase()) {
            return;
        }
        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
        event.setSneak(false);
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (!isStasisPhase() || mc.thePlayer.movementInput == null) {
            return;
        }
        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.movementInput.jump = false;
        mc.thePlayer.movementInput.sneak = false;
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onLivingUpdate(PreSuperLivingUpdateEvent event) {
        freezeMotion();
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onStrafe(StrafeEvent event) {
        if (!isStasisPhase()) {
            return;
        }
        event.setForward(0.0F);
        event.setStrafe(0.0F);
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onSendPacket(SendPacketEvent event) {
        if (shouldCancelMovementPacket(
                event.getPacket(), isStasisPhase(),
                mc.thePlayer != null,
                mc.thePlayer == null ? 0 : mc.thePlayer.hurtTime)) {
            event.setCanceled(true);
        }
    }

    static boolean shouldCancelMovementPacket(
            Packet<?> packet, boolean stasis,
            boolean playerPresent, int hurtTime) {
        return stasis && playerPresent && hurtTime == 0
                && packet instanceof C03PacketPlayer
                && !(packet instanceof C03PacketPlayer.C05PacketPlayerLook);
    }

    private boolean isStasisPhase() {
        return Utils.nullCheck() && phase == Phase.STASIS;
    }

    private void freezeMotion() {
        if (!isStasisPhase()) {
            return;
        }
        mc.thePlayer.motionX = 0.0D;
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.motionZ = 0.0D;
    }

    private void saveMotion() {
        if (!Utils.nullCheck()) {
            return;
        }
        savedMotionX = mc.thePlayer.motionX;
        savedMotionY = mc.thePlayer.motionY;
        savedMotionZ = mc.thePlayer.motionZ;
        motionSaved = true;
    }

    private void restoreMotion() {
        if (!Utils.nullCheck() || !motionSaved) {
            return;
        }
        mc.thePlayer.motionX = savedMotionX;
        mc.thePlayer.motionY = savedMotionY;
        mc.thePlayer.motionZ = savedMotionZ;
    }

    private enum Phase {
        STASIS,
        RELEASE
    }
}
