package idle.miau.event;

import net.minecraft.entity.Entity;
import idle.miau.mcp.event.Event;

public class StepHeightEvent extends Event {
    public Entity entity;
    public float stepHeight;

    public StepHeightEvent(Entity entity, float stepHeight) {
        this.entity = entity;
        this.stepHeight = stepHeight;
    }
}
