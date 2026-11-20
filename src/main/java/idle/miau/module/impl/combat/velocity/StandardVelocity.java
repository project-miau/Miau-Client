package idle.miau.module.impl.combat.velocity;

import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import org.lwjgl.input.Keyboard;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;

public final class StandardVelocity extends VelocityMode {
    public final SliderSetting horizontal;
    public final SliderSetting vertical;
    public final SliderSetting chance;
    public final ButtonSetting onlyWhileTargeting;
    public final ButtonSetting disableS;

    public StandardVelocity(VelocityModule owner) {
        super("Standard", owner);
        register(horizontal = new SliderSetting("Horizontal", "%", 90.0D, 0.0D, 100.0D, 1.0D));
        register(vertical = new SliderSetting("Vertical", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        register(chance = new SliderSetting("Chance", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        register(onlyWhileTargeting = new ButtonSetting("Only while targeting", false));
        register(disableS = new ButtonSetting("Disable while holding S", false));
    }

    @Override
    public String getInfo() {
        return (int) horizontal.getInput() + "% " + (int) vertical.getInput() + "%";
    }

    @Override
    public boolean isHorizontalZero() {
        return horizontal.getInput() == 0.0D && vertical.getInput() == 0.0D;
    }

    @Override
    public void updateVisibility() {
        boolean active = owner.isMode("Standard");
        horizontal.setVisible(active, owner);
        vertical.setVisible(active, owner);
        chance.setVisible(active, owner);
        onlyWhileTargeting.setVisible(active, owner);
        disableS.setVisible(active, owner);
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (!Utils.nullCheck() || isSuppressed() || event.isCanceled()) {
            return;
        }
        S12PacketEntityVelocity entityVelocity = null;
        S27PacketExplosion explosion = null;
        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            entityVelocity = (S12PacketEntityVelocity) event.getPacket();
            if (entityVelocity.getEntityID() != mc.thePlayer.getEntityId()) {
                return;
            }
        } else if (event.getPacket() instanceof S27PacketExplosion) {
            explosion = (S27PacketExplosion) event.getPacket();
        } else {
            return;
        }
        if (onlyWhileTargeting.isToggled()
                && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) {
            return;
        }
        if (disableS.isToggled() && Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) {
            return;
        }
        if (!passesChance()) {
            return;
        }
        double horizontalMultiplier = horizontal.getInput() / 100.0D;
        double verticalMultiplier = vertical.getInput() / 100.0D;
        if (entityVelocity != null) {
            entityVelocity.setMotionX(scale(entityVelocity.getMotionX(), horizontalMultiplier));
            entityVelocity.setMotionY(scale(entityVelocity.getMotionY(), verticalMultiplier));
            entityVelocity.setMotionZ(scale(entityVelocity.getMotionZ(), horizontalMultiplier));
        } else {
            explosion.setPlayerMotionX((float) (explosion.func_149149_c() * horizontalMultiplier));
            explosion.setPlayerMotionY((float) (explosion.func_149144_d() * verticalMultiplier));
            explosion.setPlayerMotionZ((float) (explosion.func_149147_e() * horizontalMultiplier));
        }
    }

    private boolean passesChance() {
        double value = chance.getInput();
        return value > 0.0D && (value >= 100.0D || Math.random() < value / 100.0D);
    }

    private static int scale(int motion, double multiplier) {
        return (int) Math.round(motion * multiplier);
    }
}
