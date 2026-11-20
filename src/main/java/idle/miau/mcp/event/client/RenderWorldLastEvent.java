package idle.miau.mcp.event.client;

import net.minecraft.client.renderer.RenderGlobal;
import idle.miau.mcp.event.Event;

public class RenderWorldLastEvent extends Event {
    public final RenderGlobal context;
    public final float partialTicks;

    public RenderWorldLastEvent(RenderGlobal context, float partialTicks) {
        this.context = context;
        this.partialTicks = partialTicks;
    }
}
