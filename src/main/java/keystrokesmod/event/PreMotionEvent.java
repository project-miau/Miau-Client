package keystrokesmod.event;

public class PreMotionEvent extends idle.miau.event.PreMotionEvent {
    public PreMotionEvent(double posX, double posY, double posZ, float yaw, float pitch, boolean onGround, boolean isSprinting, boolean isSneaking) {
        super(posX, posY, posZ, yaw, pitch, onGround, isSprinting, isSneaking);
    }
}
