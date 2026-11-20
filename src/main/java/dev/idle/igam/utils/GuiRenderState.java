package dev.idle.igam.utils;

import net.minecraft.client.renderer.GlStateManager;

public final class GuiRenderState {
  private static final State OPEN_GL_STATE =
      new State() {
        @Override
        public void disableLighting() {
          GlStateManager.disableLighting();
        }

        @Override
        public void resetColor() {
          GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
      };

  private GuiRenderState() {}

  public static void draw(Runnable drawCall) {
    draw(drawCall, OPEN_GL_STATE);
  }

  static void draw(Runnable drawCall, State state) {
    prepare(state);
    try {
      drawCall.run();
    } finally {
      prepare(state);
    }
  }

  private static void prepare(State state) {
    state.disableLighting();
    state.resetColor();
  }

  interface State {
    void disableLighting();

    void resetColor();
  }
}
