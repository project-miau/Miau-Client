package idle.miau.mcp.event.world;

import net.minecraft.world.World;
import idle.miau.mcp.event.Event;

public class WorldEvent extends Event {
    public final World world;

    protected WorldEvent(World world) {
        this.world = world;
    }

    public static class Load extends WorldEvent {
        public Load(World world) { super(world); }
    }

    public static class Unload extends WorldEvent {
        public Unload(World world) { super(world); }
    }
}
