package keystrokesmod.event;

public class PrePlayerInputEvent extends idle.miau.event.PrePlayerInputEvent {
    public PrePlayerInputEvent(float forward, float strafe, boolean jump, boolean sneak, double sneakSlowDownMultiplier) {
        super(forward, strafe, jump, sneak, sneakSlowDownMultiplier);
    }
}
