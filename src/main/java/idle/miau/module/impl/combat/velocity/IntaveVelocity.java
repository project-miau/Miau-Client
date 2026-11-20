package idle.miau.module.impl.combat.velocity;

import idle.miau.event.AttackEvent;
import idle.miau.event.HitSlowDownEvent;
import idle.miau.mcp.accessor.IAccessorEntity;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.setting.impl.SliderSetting;
import net.minecraft.entity.EntityLivingBase;

public final class IntaveVelocity extends VelocityMode {
    private static final int LATEST = 0;
    private static final int INTAVE_13 = 1;

    private final SliderSetting intaveMode;
    private boolean hitSlowdown;

    public IntaveVelocity(VelocityModule owner) {
        super("Intave", owner);
        register(intaveMode = new SliderSetting(
                "Intave mode", LATEST, new String[]{"Latest", "13"}));
    }

    @Override
    public String getInfo() {
        return "Intave " + intaveMode.getOptions()[(int) intaveMode.getInput()];
    }

    @Override
    public void updateVisibility() {
        intaveMode.setVisible(owner.isMode("Intave"), owner);
    }

    @Override
    public void onEnable() {
        hitSlowdown = false;
    }

    @Override
    public void onDisable() {
        hitSlowdown = false;
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (isSuppressed() || mc.thePlayer == null
                || event.attacker != mc.thePlayer
                || !(event.target instanceof EntityLivingBase)) {
            return;
        }

        if ((int) intaveMode.getInput() == INTAVE_13) {
            if (mc.thePlayer.hurtTime > 0 && !isInLiquidOrWeb()) {
                double multiplier = mc.thePlayer.onGround ? 0.52D : 0.8D;
                mc.thePlayer.motionX *= multiplier;
                mc.thePlayer.motionZ *= multiplier;
            }
            return;
        }

        if (mc.thePlayer.hurtTime > 0) {
            if (!hitSlowdown && mc.thePlayer.isSprinting()) {
                mc.thePlayer.motionX *= 0.6D;
                mc.thePlayer.motionZ *= 0.6D;
                mc.thePlayer.setSprinting(false);
            }
            hitSlowdown = false;
        }
    }

    @Override
    public void onHitSlowDown(HitSlowDownEvent event) {
        if (!isSuppressed() && mc.thePlayer != null) {
            hitSlowdown = true;
        }
    }

    @Override
    public void onWorldUnload(WorldEvent.Unload event) {
        hitSlowdown = false;
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater()
                || mc.thePlayer.isInLava()
                || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }
}
