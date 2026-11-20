package idle.miau.module.impl.player;

import idle.miau.Miau;
import idle.miau.event.PreUpdateEvent;
import idle.miau.lag.api.EnumLagDirection;
import idle.miau.lag.api.LagRequest;
import idle.miau.lag.timeout.ModuleBackedTimeout;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import net.minecraft.util.Vec3;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.entity.player.AttackEntityEvent;
import idle.miau.mcp.event.Subscribe;

import java.awt.*;
import java.util.Set;

public class Blink extends Module {

    private static final String[] MODE_LABELS = new String[] { "Inbound", "Outbound", "Both" };

    private SliderSetting mode;
    private SliderSetting disableAfterMs;
    private ButtonSetting maxDuration;
    private ButtonSetting disableOnAttack;
    private ButtonSetting initialPosition;

    private Vec3 pos;
    private int color = new Color(0, 255, 0, 120).getRGB();
    private int blinkTicks;
    private long enableTime;
    private LagRequest lagRequest;

    public Blink() {
        super("Blink", category.player);
        this.registerSetting(mode = new SliderSetting("Mode", 1, MODE_LABELS));
        this.registerSetting(maxDuration = new ButtonSetting("Max duration", false));
        this.registerSetting(disableAfterMs = new SliderSetting("Disable after", "ms", 500.0, 50.0, 20000.0, 50.0));
        this.registerSetting(new DescriptionSetting("Disable on"));
        this.registerSetting(disableOnAttack = new ButtonSetting("Attack", false));
        this.registerSetting(initialPosition = new ButtonSetting("Show initial position", true));
    }

    @Override
    public void guiUpdate() {
        disableAfterMs.setVisible(maxDuration.isToggled(), this);
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            disable();
            return;
        }
        pos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        blinkTicks = 0;
        enableTime = System.currentTimeMillis();
        lagRequest = new LagRequest(lagDirectionsForMode(), new ModuleBackedTimeout(this));
        Miau.lagHandler.requestLag(lagRequest);
    }

    @Override
    public void onDisable() {
        if (lagRequest != null) {
            lagRequest.getTimeout().forceTimeOut();
            lagRequest = null;
        }
        pos = null;
        blinkTicks = 0;
        enableTime = 0L;
    }

    private Set<EnumLagDirection> lagDirectionsForMode() {
        switch ((int) mode.getInput()) {
            case 0:
                return EnumLagDirection.ONLY_INBOUND;
            case 2:
                return EnumLagDirection.BIDIRECTIONAL;
            case 1:
            default:
                return EnumLagDirection.ONLY_OUTBOUND;
        }
    }

    public boolean delaysInboundPackets() {
        int m = (int) mode.getInput();
        return m == 0 || m == 2;
    }

    @Override
    public String getInfo() {
        return String.valueOf(blinkTicks);
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent e) {
        ++blinkTicks;
        if (!maxDuration.isToggled()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - enableTime;
        if (elapsed >= (int) disableAfterMs.getInput()) {
            this.disable();
        }
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        if (!this.isEnabled() || !disableOnAttack.isToggled() || !Utils.nullCheck()) {
            return;
        }
        if (event.entityPlayer != mc.thePlayer) {
            return;
        }
        this.disable();
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || pos == null || !initialPosition.isToggled()) {
            return;
        }
        RenderUtils.drawPlayerBoundingBox(pos, color);
    }
}
