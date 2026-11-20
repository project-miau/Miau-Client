package net.minecraft.util;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import idle.miau.mcp.accessor.IAccessorMouseHelper;

public class MouseHelper implements IAccessorMouseHelper {
   public int deltaX;
   public int deltaY;

   public void grabMouseCursor() {
      Mouse.setGrabbed(true);
      this.deltaX = 0;
      this.deltaY = 0;
   }

   public void ungrabMouseCursor() {
      Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
      Mouse.setGrabbed(false);
   }

   public void mouseXYChange() {
      this.deltaX = Mouse.getDX();
      this.deltaY = Mouse.getDY();
   }

   public int getDeltaX() {
      return this.deltaX;
   }

   public int getDeltaY() {
      return this.deltaY;
   }
}
