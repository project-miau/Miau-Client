package idle.miau.module.impl.render;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import idle.miau.event.AttackEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.utility.Utils;

public class KillEffect extends Module {
    private final ButtonSetting lightning;
    private final ButtonSetting blood;
    private final ButtonSetting explosion;

    private EntityLivingBase target;
    private World targetWorld;

    public KillEffect() {
        super("KillEffect", category.render);
        this.registerSetting(lightning = new ButtonSetting("Lightning", true));
        this.registerSetting(blood = new ButtonSetting("Blood Explosion", true));
        this.registerSetting(explosion = new ButtonSetting("Explosion", true));
    }

    @Override
    public void onDisable() {
        clearTarget();
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        if (!Utils.nullCheck() || event.attacker != mc.thePlayer) {
            return;
        }

        if (event.target instanceof EntityLivingBase) {
            target = (EntityLivingBase) event.target;
            targetWorld = mc.theWorld;
        }
    }

    @Subscribe
    public void onUpdate(PreUpdateEvent event) {
        if (target == null) {
            return;
        }

        if (!Utils.nullCheck() || targetWorld != mc.theWorld) {
            clearTarget();
            return;
        }

        if (mc.theWorld.loadedEntityList.contains(target)) {
            return;
        }

        spawnEffects(target);
        clearTarget();
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        clearTarget();
    }

    private void spawnEffects(EntityLivingBase killedTarget) {
        if (lightning.isToggled()) {
            EntityLightningBolt bolt = new EntityLightningBolt(
                    mc.theWorld,
                    killedTarget.posX,
                    killedTarget.posY,
                    killedTarget.posZ
            );
            mc.theWorld.addEntityToWorld((int) (-Math.random() * 100000.0D), bolt);
            mc.thePlayer.playSound("ambient.weather.thunder", 1.0F, 1.0F);
        }

        if (explosion.isToggled()) {
            for (int i = 0; i <= 8; i++) {
                mc.effectRenderer.emitParticleAtEntity(killedTarget, EnumParticleTypes.FLAME);
            }
            mc.thePlayer.playSound("item.fireCharge.use", 1.0F, 1.0F);
        }

        if (blood.isToggled()) {
            double startY = killedTarget.posY;
            double endY = killedTarget.posY + killedTarget.height + 0.4D;
            double step = 0.4D;
            int redstoneState = Block.getStateId(Blocks.redstone_block.getDefaultState());

            for (int i = 0; i < 100; i++) {
                for (double y = startY; y <= endY; y += step) {
                    mc.theWorld.spawnParticle(
                            EnumParticleTypes.BLOCK_CRACK,
                            killedTarget.posX,
                            y,
                            killedTarget.posZ,
                            0.0D,
                            0.0D,
                            0.0D,
                            redstoneState
                    );
                }
            }

            for (double y = startY; y <= endY; y += step) {
                mc.thePlayer.playSound("dig.stone", 1.0F, 1.0F);
            }
        }
    }

    private void clearTarget() {
        target = null;
        targetWorld = null;
    }
}
