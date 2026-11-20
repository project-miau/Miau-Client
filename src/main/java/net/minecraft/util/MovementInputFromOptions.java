package net.minecraft.util;

import net.minecraft.client.settings.GameSettings;
import idle.miau.event.PostPlayerInputEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.mcp.McpRuntime;
import idle.miau.mcp.callback.CallbackInfo;

public class MovementInputFromOptions extends MovementInput {
   private final GameSettings gameSettings;

   public MovementInputFromOptions(GameSettings gameSettingsIn) {
      this.gameSettings = gameSettingsIn;
   }

   public void updatePlayerMoveState() {
      this.moveStrafe = 0.0F;
      this.moveForward = 0.0F;
      if(this.gameSettings.keyBindForward.isKeyDown()) {
         ++this.moveForward;
      }

      if(this.gameSettings.keyBindBack.isKeyDown()) {
         --this.moveForward;
      }

      if(this.gameSettings.keyBindLeft.isKeyDown()) {
         ++this.moveStrafe;
      }

      if(this.gameSettings.keyBindRight.isKeyDown()) {
         --this.moveStrafe;
      }

      this.jump = this.gameSettings.keyBindJump.isKeyDown();
      this.sneak = this.gameSettings.keyBindSneak.isKeyDown();
      PrePlayerInputEvent moveInputEvent = new PrePlayerInputEvent(this.moveForward, this.moveStrafe, this.jump, this.sneak, 0.3D);
      McpRuntime.EVENTS.post(moveInputEvent);
      double sneakMultiplier = moveInputEvent.getSneakSlowDownMultiplier();
      this.moveForward = moveInputEvent.getForward();
      this.moveStrafe = moveInputEvent.getStrafe();
      this.jump = moveInputEvent.isJump();
      this.sneak = moveInputEvent.isSneak();
      if(this.sneak) {
         this.moveStrafe = (float)((double)this.moveStrafe * sneakMultiplier);
         this.moveForward = (float)((double)this.moveForward * sneakMultiplier);
      }

      this.mimeow$onUpdatePlayerMoveState(new CallbackInfo("updatePlayerMoveState", false));
   }

   private void mimeow$onUpdatePlayerMoveState(CallbackInfo ci) {
      McpRuntime.EVENTS.post(new PostPlayerInputEvent());
   }
}
