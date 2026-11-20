package idle.miau.mcp.event.entity.living;

import net.minecraft.entity.EntityLivingBase;

public class LivingSetAttackTargetEvent extends LivingEvent {
    public final EntityLivingBase target;

    public LivingSetAttackTargetEvent(EntityLivingBase entityLiving, EntityLivingBase target) {
        super(entityLiving);
        this.target = target;
    }
}
