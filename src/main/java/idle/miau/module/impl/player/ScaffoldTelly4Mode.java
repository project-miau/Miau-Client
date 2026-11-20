package idle.miau.module.impl.player;

import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import org.lwjgl.input.Mouse;

final class ScaffoldTelly4Mode extends ScaffoldTellyBridgeMode {
    private int tellyStage;

    ScaffoldTelly4Mode(Scaffold owner) {
        super(owner);
    }

    @Override
    protected boolean isKeepYRequested() {
        return super.isKeepYRequested()
                && (!owner.isTelly4KeepYOnRightEnabled() || Mouse.isButtonDown(1));
    }

    @Override
    protected void updateKeepY() {
        if (!canKeepY()) {
            if (keepYActive) {
                disableKeepY();
            }
            if (!towering && legitTicks > 0 && mc.thePlayer.onGround) {
                delay = 0;
                legitTicks = 0;
            }
            return;
        }
        if (!keepYActive) {
            enableKeepY();
        }
        if (keptY == -1 && mc.thePlayer.onGround) {
            keptY = MathHelper.floor_double(mc.thePlayer.posY) - 1;
        }
    }

    @Override
    protected void resetKeepYCycle() {
        tellyStage = 0;
    }

    @Override
    protected void updateKeepYMotion() {
        tellyStage = advanceJumpCycle(tellyStage);
    }

    @Override
    protected void updateKeepYInput(MovementInput input) {
        input.jump = tellyStage == 2;
        if (input.jump) {
            tellyStage = 0;
        }
    }
}
