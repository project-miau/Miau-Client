package idle.miau.module.impl.combat.velocity;

import idle.miau.event.AntiCheatFlagEvent;
import idle.miau.event.AttackEvent;
import idle.miau.event.HitSlowDownEvent;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.entity.living.LivingEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.impl.movement.LongJump;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.PacketUtils;
import idle.miau.utility.Utils;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import java.util.ArrayList;
import java.util.List;

public abstract class VelocityModule extends Module {
    private final String[] modes = {
            "Standard", "OldGrim", "JumpReset", "MMC", "Intave", "Delayed"
    };
    private final MultiSelectSetting mode;
    private final VelocityMode[] modesList;
    private final boolean[] activeModes;
    private final long[] pausedUntil;
    private boolean suppressed;
    private boolean modesRunning;

    protected VelocityModule() {
        super("Velocity", category.combat, 0);
        setDescription("Changes the knockback you receive from damage.");
        registerSetting(mode = new MultiSelectSetting("Mode", modes, "Standard"));
        modesList = new VelocityMode[]{
                new StandardVelocity(this),
                new OldGrimVelocity(this),
                new JumpReset(this),
                new MMCVelocity(this),
                new IntaveVelocity(this),
                new DelayedVelocity(this)
        };
        activeModes = new boolean[modesList.length];
        pausedUntil = new long[modesList.length];
        synchronizeModes();
        guiUpdate();
        closetModule = true;
    }

    public boolean isMode(String name) {
        return mode.isSelected(name);
    }

    public boolean isStandardHorizontalZero() {
        return isMode("Standard") && modesList[0].isHorizontalZero();
    }

    @Subscribe
    public void onPostWorldEntities(idle.miau.event.PostWorldEntitiesEvent event) {
        synchronizeModes();
        long now = System.currentTimeMillis();
        for (int index = 0; index < modesList.length; index++) {
            if (isAvailable(index, now) && modesList[index] instanceof JumpReset) {
                ((JumpReset) modesList[index]).afterEntities();
            }
        }
    }

    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
        for (VelocityMode velocityMode : modesList) {
            velocityMode.setSuppressed(suppressed);
        }
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    @Override
    public String getInfo() {
        synchronizeModes();
        long now = System.currentTimeMillis();
        resumeModes(now);
        List<String> information = new ArrayList<String>();
        for (int index = 0; index < modesList.length; index++) {
            if (!activeModes[index]) {
                continue;
            }
            if (pausedUntil[index] > now) {
                information.add(modesList[index].getName() + " Paused");
            }
            else {
                information.add(modesList[index].getInfo());
            }
        }
        return information.isEmpty() ? "None" : String.join(" + ", information);
    }

    public int getActiveModeCount() {
        synchronizeModes();
        int count = 0;
        for (boolean activeMode : activeModes) {
            if (activeMode) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void guiUpdate() {
        synchronizeModes();
        for (VelocityMode velocityMode : modesList) {
            velocityMode.updateVisibility();
        }
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        super.guiButtonToggled(setting);
        synchronizeModes();
        for (VelocityMode velocityMode : modesList) {
            velocityMode.onButtonToggled(setting);
        }
        guiUpdate();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        guiUpdate();
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        synchronizeModes();
        for (VelocityMode velocityMode : modesList) {
            velocityMode.onSettingChanged(setting);
        }
        guiUpdate();
    }

    @Override
    public void onEnable() {
        synchronizeModes();
        modesRunning = true;
        for (int index = 0; index < modesList.length; index++) {
            pausedUntil[index] = 0L;
            if (activeModes[index]) {
                modesList[index].onEnable();
            }
        }
        guiUpdate();
    }

    @Override
    public void onDisable() {
        for (int index = 0; index < modesList.length; index++) {
            if (activeModes[index]) {
                modesList[index].onDisable();
            }
            pausedUntil[index] = 0L;
        }
        modesRunning = false;
    }

    private void synchronizeModes() {
        if (modesList == null || activeModes == null) {
            return;
        }
        for (int index = 0; index < modesList.length; index++) {
            boolean selected = mode.isSelected(modes[index]);
            if (selected == activeModes[index]) {
                continue;
            }
            if (activeModes[index] && modesRunning) {
                modesList[index].onDisable();
            }
            activeModes[index] = selected;
            pausedUntil[index] = 0L;
            if (selected && modesRunning) {
                modesList[index].onEnable();
            }
        }
    }

    private boolean isAvailable(int index, long now) {
        if (activeModes[index] && modesRunning && pausedUntil[index] != 0L && pausedUntil[index] <= now) {
            pausedUntil[index] = 0L;
            modesList[index].onEnable();
        }
        return activeModes[index] && pausedUntil[index] == 0L;
    }

    private void pauseMode(int index, long now) {
        if (!activeModes[index] || pausedUntil[index] > now) {
            return;
        }
        modesList[index].onDisable();
        pausedUntil[index] = now + Math.max(0L, modesList[index].getAutoVlDelay());
    }

    private void resumeModes(long now) {
        if (!modesRunning) {
            return;
        }
        for (int index = 0; index < modesList.length; index++) {
            if (activeModes[index] && pausedUntil[index] != 0L && pausedUntil[index] <= now) {
                pausedUntil[index] = 0L;
                modesList[index].onEnable();
            }
        }
    }

    @Subscribe
    public void onAntiCheatFlag(AntiCheatFlagEvent event) {
        synchronizeModes();
        long now = System.currentTimeMillis();
        for (int index = 0; index < modesList.length; index++) {
            if (!activeModes[index]) {
                continue;
            }
            if (Utils.nullCheck() && event.entity == mc.thePlayer && modesList[index].isAutoVlEnabled()) {
                pauseMode(index, now);
            }
            else if (isAvailable(index, now)) {
                modesList[index].onAntiCheatFlag(event);
            }
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onReceivePacketHigh(ReceivePacketEvent event) {
        synchronizeModes();
        long now = System.currentTimeMillis();
        for (int index = 0; index < modesList.length; index++) {
            if (isAvailable(index, now)) {
                modesList[index].onReceivePacketHigh(event);
                if (event.isCanceled()) {
                    break;
                }
            }
        }
    }

    @Subscribe(receiveCanceled = true)
    public void onReceivePacket(ReceivePacketEvent event) {
        synchronizeModes();
        long now = System.currentTimeMillis();
        if (Utils.nullCheck() && mc.thePlayer.ticksExisted > 20
                && event.getPacket() instanceof S08PacketPlayerPosLook) {
            for (int index = 0; index < modesList.length; index++) {
                if (activeModes[index] && modesList[index].isAutoVlEnabled()) {
                    pauseMode(index, now);
                }
            }
        }
        handleReceivePacket(event, now);
    }

    void receiveDelayedPacket(Packet<?> packet) {
        ReceivePacketEvent event = new ReceivePacketEvent(packet);
        handleReceivePacket(event, System.currentTimeMillis());
        if (!event.isCanceled()) {
            PacketUtils.receivePacketNoEvent(event.getPacket());
        }
    }

    private void handleReceivePacket(ReceivePacketEvent event, long now) {
        if (event.isCanceled()) {
            return;
        }
        for (int index = 0; index < modesList.length; index++) {
            if (isAvailable(index, now)) {
                modesList[index].onReceivePacket(event);
                if (event.isCanceled()) {
                    break;
                }
            }
        }
    }

    @Subscribe
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        synchronizeModes();
        if (!Utils.nullCheck() || LongJump.stopVelocity || event.entity != mc.thePlayer) {
            return;
        }
        long now = System.currentTimeMillis();
        resumeModes(now);
        for (int index = 0; index < modesList.length; index++) {
            if (isAvailable(index, now)) {
                modesList[index].onLivingUpdate(event);
            }
        }
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        synchronizeModes();
        long now = System.currentTimeMillis();
        for (int index = 0; index < modesList.length; index++) {
            if (isAvailable(index, now)) {
                modesList[index].onAttack(event);
            }
        }
    }

    @Subscribe
    public void onHitSlowDown(HitSlowDownEvent event) {
        synchronizeModes();
        long now = System.currentTimeMillis();
        for (int index = 0; index < modesList.length; index++) {
            if (isAvailable(index, now)) {
                modesList[index].onHitSlowDown(event);
            }
        }
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        synchronizeModes();
        long now = System.currentTimeMillis();
        for (int index = 0; index < modesList.length; index++) {
            if (isAvailable(index, now)) {
                modesList[index].onPreUpdate(event);
            }
        }
    }

    @Subscribe
    public void onPrePlayerInput(PrePlayerInputEvent event) {
        synchronizeModes();
        long now = System.currentTimeMillis();
        for (int index = 0; index < modesList.length; index++) {
            if (isAvailable(index, now)) {
                modesList[index].onPrePlayerInput(event);
            }
        }
    }

    @Subscribe
    public void onPostMotion(PostMotionEvent event) {
        synchronizeModes();
        long now = System.currentTimeMillis();
        for (int index = 0; index < modesList.length; index++) {
            if (isAvailable(index, now)) {
                modesList[index].onPostMotion(event);
            }
        }
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        synchronizeModes();
        long now = System.currentTimeMillis();
        for (int index = 0; index < modesList.length; index++) {
            if (isAvailable(index, now)) {
                modesList[index].onSendPacket(event);
            }
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        for (int index = 0; index < modesList.length; index++) {
            pausedUntil[index] = 0L;
            modesList[index].onWorldUnload(event);
        }
    }
}
