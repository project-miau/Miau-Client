package idle.miau.mcp.event.entity;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class EntityJoinWorldEvent extends Event {
    public final Entity entity;
    public final World world;

    public EntityJoinWorldEvent(Entity entity, World world) {
        this.entity = entity;
        this.world = world;
    }
}
