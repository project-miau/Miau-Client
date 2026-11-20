package idle.miau.module.impl.player;

import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;
import idle.miau.event.PostPlayerInputEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ItemUtils;
import idle.miau.utility.Utils;
import idle.miau.utility.MovementUtils;
import idle.miau.utility.PlayerUtils;

public class Eagle extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private int sneakDelay = 0;

  public final SliderSetting minDelay = new SliderSetting("Min delay", " ticks", 2, 0, 10, 1);
  public final SliderSetting maxDelay = new SliderSetting("Max delay", " ticks", 3, 0, 10, 1);
  private final GroupSetting condition = new GroupSetting("Condition");
  public final ButtonSetting directionCheck = new ButtonSetting(condition, "Direction check", true);
  public final ButtonSetting jumpCheck = new ButtonSetting(condition, "Jump check", true);
  public final ButtonSetting pitchCheck = new ButtonSetting(condition, "Pitch check", true);
  public final ButtonSetting blocksOnly = new ButtonSetting(condition, "Blocks only", true);
  public final ButtonSetting normalSneakOnly = new ButtonSetting(condition, "Sneaking only", false);

  public Eagle() {
    super("Eagle", category.player);
    setDescription("Automatically sneaks for you when you are near the edge of a block.");
    registerSetting(minDelay);
    registerSetting(maxDelay);
    registerSetting(condition);
    registerSetting(directionCheck);
    registerSetting(jumpCheck);
    registerSetting(pitchCheck);
    registerSetting(blocksOnly);
    registerSetting(normalSneakOnly);
  }

  @Override
  public String getInfo() {
    int min = (int) minDelay.getInput();
    int max = (int) maxDelay.getInput();
    return min == max ? Integer.toString(min) : min + "-" + max;
  }

  @Override
  public void onSlide(SliderSetting setting) {
    if (setting == minDelay && minDelay.getInput() > maxDelay.getInput()) {
      maxDelay.setValue(minDelay.getInput());
    } else if (setting == maxDelay && maxDelay.getInput() < minDelay.getInput()) {
      minDelay.setValue(maxDelay.getInput());
    }
  }

  @Override
  public void onDisable() {
    this.sneakDelay = 0;
  }

  private boolean canMoveSafely() {
    double[] offset = MovementUtils.predictMovement();
    return PlayerUtils.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
  }

  private boolean shouldSneak() {
    if (this.directionCheck.isToggled() && mc.gameSettings.keyBindForward.isKeyDown()) {
      return false;
    } else if (this.jumpCheck.isToggled() && mc.gameSettings.keyBindJump.isKeyDown()) {
      return false;
    } else if (this.pitchCheck.isToggled() && mc.thePlayer.rotationPitch < 69.0F) {
      return false;
    } else if (this.normalSneakOnly.isToggled()
        && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
      return false;
    } else {
      return (!this.blocksOnly.isToggled() || ItemUtils.isHoldingBlock()) && mc.thePlayer.onGround;
    }
  }

  @Subscribe
  public void onTick(TickEvent.ClientTickEvent event) {
    if (!isEnabled() || !Utils.nullCheck() || event.phase != TickEvent.Phase.START) return;
    if (this.sneakDelay > 0) {
      this.sneakDelay--;
    }
    if (this.sneakDelay == 0 && this.canMoveSafely()) {
      this.sneakDelay =
          RandomUtils.nextInt((int) this.minDelay.getInput(), (int) this.maxDelay.getInput() + 1);
    }
  }

  @Subscribe
  public void onMoveInput(PostPlayerInputEvent event) {
    if (!isEnabled() || !Utils.nullCheck() || mc.currentScreen != null) return;
    if (this.normalSneakOnly.isToggled()
        && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())
        && shouldSneak()) {
      mc.thePlayer.movementInput.sneak = false;
      mc.thePlayer.movementInput.moveForward /= 0.3F;
      mc.thePlayer.movementInput.moveStrafe /= 0.3F;
    }

    if (!mc.thePlayer.movementInput.sneak) {
      if (this.shouldSneak() && (this.sneakDelay > 0 || this.canMoveSafely())) {
        mc.thePlayer.movementInput.sneak = true;
        mc.thePlayer.movementInput.moveStrafe *= 0.3F;
        mc.thePlayer.movementInput.moveForward *= 0.3F;
      }
    }
  }

  public int getSneakDelay() {
    return sneakDelay;
  }
}
