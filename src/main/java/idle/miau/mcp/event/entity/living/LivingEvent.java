package idle.miau.mcp.event.entity.living;

import net.minecraft.entity.EntityLivingBase;
import idle.miau.mcp.event.Event;

public class LivingEvent extends Event {
    public final EntityLivingBase entity;
    public final EntityLivingBase entityLiving;

    protected LivingEvent(EntityLivingBase entityLiving) {
        this.entity = entityLiving;
        this.entityLiving = entityLiving;
    }

    public static class LivingUpdateEvent extends LivingEvent {
        public LivingUpdateEvent(EntityLivingBase entityLiving) {
            super(entityLiving);
        }
    }
}
