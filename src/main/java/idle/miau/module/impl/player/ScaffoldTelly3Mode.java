package idle.miau.module.impl.player;

import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;

final class ScaffoldTelly3Mode extends ScaffoldTellyBridgeMode {
    private int keepYState;
    private int keepYPlaceTick;

    ScaffoldTelly3Mode(Scaffold owner) {
        super(owner);
    }

    @Override
    protected void updateKeepY() {
        boolean can = canKeepY();
        if (!can && keepYActive) {
            disableKeepY();
        } else if (can && !keepYActive) {
            enableKeepY();
        }
        if (!can) {
            if (!towering && legitTicks > 0 && mc.thePlayer.onGround) {
                delay = 0;
                legitTicks = 0;
            }
            return;
        }
        if (mc.thePlayer.onGround) {
            if (keepYState != 2) {
                if (mc.thePlayer.ticksExisted - keepYPlaceTick > 10) {
                    keptY = MathHelper.floor_double(mc.thePlayer.posY) - 1;
                    if (legitTicks == 0) {
                        legitTicks = 5;
                    }
                    keepYState = 1;
                }
            } else {
                keepYState = 0;
            }
        } else if (mc.thePlayer.motionY < 0.0D) {
            double floor = Math.floor(mc.thePlayer.posY);
            if (floor - 1.0D == keptY && mc.thePlayer.posY + mc.thePlayer.motionY < floor) {
                legitTicks = 5;
            }
        }
    }

    @Override
    protected void resetKeepYCycle() {
        keepYState = 0;
        keepYPlaceTick = 0;
    }

    @Override
    protected void onKeepYPlaced() {
        keepYPlaceTick = mc.thePlayer.ticksExisted;
    }

    @Override
    protected void updateKeepYMotion() {
        if (mc.thePlayer.onGround) {
            if (keepYState == 1) {
                keepYState = 2;
            }
            delay = legitTicks;
        }
    }

    @Override
    protected void updateKeepYInput(MovementInput input) {
        if (mc.thePlayer.onGround && keepYState == 0) {
            input.jump = true;
        }
    }
}
