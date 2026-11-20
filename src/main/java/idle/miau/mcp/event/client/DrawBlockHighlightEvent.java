package idle.miau.mcp.event.client;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class DrawBlockHighlightEvent extends Event {
    public final RenderGlobal context;
    public final EntityPlayer player;
    public final MovingObjectPosition target;
    public final int subID;
    public final float partialTicks;

    public DrawBlockHighlightEvent(RenderGlobal context, EntityPlayer player,
            MovingObjectPosition target, int subID, float partialTicks) {
        this.context = context;
        this.player = player;
        this.target = target;
        this.subID = subID;
        this.partialTicks = partialTicks;
    }
}
