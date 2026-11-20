package idle.miau.module.impl.combat;

import idle.miau.event.AttackEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.accessor.IAccessorEntity;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;

public class Criticals extends Module {
    private static final int LEGIT_MODE = 2;
    private static final int TIMER_MODE = 3;

    private final SliderSetting mode;
    private final SliderSetting delay;
    private final SliderSetting hurtTime;
    private final SliderSetting chance;
    private final SliderSetting timerSpeed;
    private final SliderSetting boost;
    private final GroupSetting conditions;
    private final ButtonSetting holdingWeapon;
    private final ButtonSetting mousePressed;

    private static final String[] MODES = new String[]{
        "Packet", "NoGround", "Legit", "Timer"
    };

    private long lastMS = 0L;
    private final idle.miau.utility.TimerCriticals timerCriticals = new idle.miau.utility.TimerCriticals();

    public Criticals() {
        super("Criticals", category.combat);
        setDescription("Increase the damage of your hits.");
        this.registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        this.registerSetting(delay = new SliderSetting("Delay", 0.0D, 0.0D, 500.0D, 1.0D));
        this.registerSetting(hurtTime = new SliderSetting("HurtTime", 10.0D, 0.0D, 10.0D, 1.0D));
        this.registerSetting(chance = new SliderSetting("Chance", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(timerSpeed = new SliderSetting("Timer speed", "x", 0.85D, 0.50D, 1.00D, 0.01D));
        this.registerSetting(boost = new SliderSetting("Boost", "ms", 0.0D, 0.0D, 250.0D, 50.0D));
        this.registerSetting(conditions = new GroupSetting("Conditions"));
        this.registerSetting(holdingWeapon = new ButtonSetting(conditions, "Holding weapon", false));
        this.registerSetting(mousePressed = new ButtonSetting(conditions, "Mouse pressed", false));
        guiUpdate();
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        boolean isLegit = (int) mode.getInput() == LEGIT_MODE;
        boolean isTimer = (int) mode.getInput() == TIMER_MODE;
        delay.setVisible(!isLegit && !isTimer, this);
        hurtTime.setVisible(!isLegit && !isTimer, this);
        chance.setVisible(isTimer, this);
        timerSpeed.setVisible(isTimer, this);
        boost.setVisible(isTimer, this);
        conditions.setVisible(true, this);
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            if ((int) mode.getInput() != TIMER_MODE) {
                resetTimerState();
            }
            guiUpdate();
        }
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        if (setting == mode) {
            if ((int) mode.getInput() != TIMER_MODE) {
                resetTimerState();
            }
            guiUpdate();
        }
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            disable();
            return;
        }
        int currentMode = (int) mode.getInput();
        if (currentMode == 1) {
            mc.thePlayer.jump();
        }
    }

    @Override
    public void onDisable() {
        timerCriticals.restore(mc);
    }

    public boolean isTimerMode() {
        return isEnabled() && (int)mode.getInput() == TIMER_MODE;
    }

    public synchronized void onTimerRay(idle.miau.utility.ClientContext context) {
        timerCriticals.update(context, (float)(chance.getInput() / 100.0D),
                mousePressed.isToggled(), holdingWeapon.isToggled());
    }

    public synchronized void onTimerFrame(double elapsedSeconds) {
        timerCriticals.frame(mc, elapsedSeconds, (float)timerSpeed.getInput(),
                (float)(boost.getInput() / 50.0D));
    }

    static double nextBoostAccumulator(double current, float speed,
                                       double deltaSeconds, float maxBoostTicks) {
        return idle.miau.utility.TimerCriticals.accumulate(current, speed, deltaSeconds, maxBoostTicks);
    }

    private boolean passesConditions() {
        EntityPlayer player = mc.thePlayer;
        if (player == null || !mc.inGameHasFocus) {
            return false;
        }
        if (mousePressed.isToggled()
                && (mc.gameSettings == null || mc.gameSettings.keyBindAttack == null
                || !Utils.isBindDown(mc.gameSettings.keyBindAttack))) {
            return false;
        }
        return !holdingWeapon.isToggled() || Utils.holdingWeapon(player);
    }

    private void resetTimerState() {
        timerCriticals.reset(mc);
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        if (event.target instanceof EntityLivingBase) {
            if (mc.thePlayer == null || mc.theWorld == null) {
                return;
            }
            if (!passesConditions()) {
                return;
            }
            int currentMode = (int) mode.getInput();
            if (currentMode == LEGIT_MODE || currentMode == TIMER_MODE) {
                return;
            }
            EntityLivingBase entity = (EntityLivingBase) event.target;

            if (!mc.thePlayer.onGround || mc.thePlayer.isOnLadder() || ((IAccessorEntity) mc.thePlayer).getIsInWeb() || mc.thePlayer.isInWater() || mc.thePlayer.isInLava() ||
                mc.thePlayer.ridingEntity != null || entity.hurtTime > (int) hurtTime.getInput() ||
                (ModuleManager.fly != null && ModuleManager.fly.isEnabled()) ||
                System.currentTimeMillis() - lastMS < (long) delay.getInput()
            ) {
                return;
            }

            double x = mc.thePlayer.posX;
            double y = mc.thePlayer.posY;
            double z = mc.thePlayer.posZ;

            switch (currentMode) {
                case 0:
                    mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0625D, z, true));
                    mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                    mc.thePlayer.onCriticalHit(entity);
                    break;
                case 1:
                    break;
            }
            lastMS = System.currentTimeMillis();
        }
    }

    @Subscribe
    public void onPrePlayerInput(PrePlayerInputEvent event) {
        if ((int) mode.getInput() == LEGIT_MODE
                && mc.thePlayer != null
                && passesConditions()
                && mc.thePlayer.onGround
                && ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target != null) {
            event.setJump(true);
        }
    }

    @Subscribe
    public void onPreMotion(PreMotionEvent event) {
        if ((int) mode.getInput() == LEGIT_MODE
                && mc.thePlayer != null
                && passesConditions()
                && mc.thePlayer.isSwingInProgress
                && ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target != null) {
            mc.thePlayer.onCriticalHit(KillAura.target);
        }
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent e) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }

        if (!passesConditions()) {
            return;
        }

        int currentMode = (int) mode.getInput();
        if (currentMode == 1) {
            if (e.getPacket() instanceof C03PacketPlayer) {
                setOnGround(e.getPacket(), false);
            }
        }
    }

    @Subscribe
    public void onWorldLoad(WorldEvent.Load event) {
        resetTimerState();
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        resetTimerState();
    }

    private void setOnGround(Object packet, boolean onGround) {
        try {
            java.lang.reflect.Field field = C03PacketPlayer.class.getDeclaredField("onGround");
            field.setAccessible(true);
            field.setBoolean(packet, onGround);
        } catch (Exception ex) {
            try {
                java.lang.reflect.Field field = C03PacketPlayer.class.getDeclaredField("field_149474_g");
                field.setAccessible(true);
                field.setBoolean(packet, onGround);
            } catch (Exception ex2) {}
        }
    }
}
