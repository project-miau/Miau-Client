package idle.miau.mcp.event.client;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

public class RenderLivingEvent<T extends EntityLivingBase> extends Event {
    public final T entity;
    public final RendererLivingEntity<T> renderer;
    public final double x;
    public final double y;
    public final double z;

    protected RenderLivingEvent(T entity, RendererLivingEntity<T> renderer, double x, double y, double z) {
        this.entity = entity;
        this.renderer = renderer;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Cancelable
    public static class Pre<T extends EntityLivingBase> extends RenderLivingEvent<T> {
        public Pre(T entity, RendererLivingEntity<T> renderer, double x, double y, double z) {
            super(entity, renderer, x, y, z);
        }
    }

    public static class Post<T extends EntityLivingBase> extends RenderLivingEvent<T> {
        public Post(T entity, RendererLivingEntity<T> renderer, double x, double y, double z) {
            super(entity, renderer, x, y, z);
        }
    }

    public static class Specials<T extends EntityLivingBase> extends RenderLivingEvent<T> {
        protected Specials(T entity, RendererLivingEntity<T> renderer, double x, double y, double z) {
            super(entity, renderer, x, y, z);
        }

        @Cancelable
        public static class Pre<T extends EntityLivingBase> extends Specials<T> {
            public Pre(T entity, RendererLivingEntity<T> renderer, double x, double y, double z) {
                super(entity, renderer, x, y, z);
            }
        }

        public static class Post<T extends EntityLivingBase> extends Specials<T> {
            public Post(T entity, RendererLivingEntity<T> renderer, double x, double y, double z) {
                super(entity, renderer, x, y, z);
            }
        }
    }
}
