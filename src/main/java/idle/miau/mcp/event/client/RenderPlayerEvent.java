package idle.miau.mcp.event.client;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

public class RenderPlayerEvent extends Event {
    public final AbstractClientPlayer entity;
    public final AbstractClientPlayer entityPlayer;
    public final RenderPlayer renderer;
    public final float partialRenderTick;
    public final double x;
    public final double y;
    public final double z;

    protected RenderPlayerEvent(AbstractClientPlayer player, RenderPlayer renderer,
            float partialRenderTick, double x, double y, double z) {
        this.entity = player;
        this.entityPlayer = player;
        this.renderer = renderer;
        this.partialRenderTick = partialRenderTick;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Cancelable
    public static class Pre extends RenderPlayerEvent {
        public Pre(AbstractClientPlayer player, RenderPlayer renderer,
                float partialRenderTick, double x, double y, double z) {
            super(player, renderer, partialRenderTick, x, y, z);
        }
    }

    public static class Post extends RenderPlayerEvent {
        public Post(AbstractClientPlayer player, RenderPlayer renderer,
                float partialRenderTick, double x, double y, double z) {
            super(player, renderer, partialRenderTick, x, y, z);
        }
    }
}
