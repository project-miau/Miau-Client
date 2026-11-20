package idle.miau.utility;

import idle.miau.mcp.accessor.IAccessorMinecraft;
import idle.miau.utility.rotation.MiauRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Timer;

public final class TimerCriticals {
    private final WeaponPolicy weapons = WeaponPolicy.SHARED;
    private int chanceCounter;
    private boolean cachedChance;
    private boolean active;
    private boolean restore;
    private double accumulated;

    public void update(ClientContext context, float chance, boolean requireMouse, boolean requireWeapon) {
        boolean eligible = false;
        if (context.inputReady()) {
            if (chanceCounter == 0) {
                chanceCounter = 10;
                cachedChance = MiauRandom.SHARED.chance(chance);
            }
            chanceCounter--;
            eligible = cachedChance
                    && (!requireMouse || AttackInput.pressed(context))
                    && (!requireWeapon || weapons.isWeapon(context.player()))
                    && context.player().ticksExisted - context.player().getLastAttackerTime() < 11;
        }
        MovingObjectPosition hit = context.minecraft().objectMouseOver;
        active = eligible
                && !context.player().onGround
                && !(context.player().motionY >= 0.0D)
                && context.player().hurtTime != 10
                && hit != null
                && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY;
    }

    public void frame(Minecraft minecraft, double elapsedSeconds, float speed, float boost) {
        Timer timer = ((IAccessorMinecraft)minecraft).getTimer();
        if (active) {
            timer.timerSpeed = speed;
            restore = true;
            accumulated = accumulate(accumulated, speed, elapsedSeconds, boost);
        } else {
            if (accumulated > 1.0D) {
                int whole = MiauMath.truncateInt(accumulated);
                timer.elapsedPartialTicks += (float)whole;
                accumulated -= (double)whole;
            }
            restore(minecraft);
        }
    }

    public static double accumulate(double current, float speed, double elapsedSeconds, float boost) {
        double next = (double)(1.0F - speed) * (elapsedSeconds * 20.0D) + current;
        return next > (double)boost ? (double)boost : next;
    }

    public void restore(Minecraft minecraft) {
        if (restore) {
            ((IAccessorMinecraft)minecraft).getTimer().timerSpeed = 1.0F;
        }
        restore = false;
    }

    public void reset(Minecraft minecraft) {
        restore(minecraft);
        chanceCounter = 0;
        cachedChance = active = false;
        accumulated = 0.0D;
    }
}
