package idle.miau.mcp.event.client;

import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

public class GuiScreenEvent extends Event {
    public final GuiScreen gui;

    protected GuiScreenEvent(GuiScreen gui) {
        this.gui = gui;
    }

    public static class InitGuiEvent extends GuiScreenEvent {
        public final List<GuiButton> buttonList;

        protected InitGuiEvent(GuiScreen gui, List<GuiButton> buttonList) {
            super(gui);
            this.buttonList = buttonList;
        }

        @Cancelable
        public static class Pre extends InitGuiEvent {
            public Pre(GuiScreen gui, List<GuiButton> buttonList) {
                super(gui, buttonList);
            }
        }

        public static class Post extends InitGuiEvent {
            public Post(GuiScreen gui, List<GuiButton> buttonList) {
                super(gui, buttonList);
            }
        }
    }

    public static class ActionPerformedEvent extends GuiScreenEvent {
        public final GuiButton button;
        public final List<GuiButton> buttonList;

        protected ActionPerformedEvent(GuiScreen gui, GuiButton button, List<GuiButton> buttonList) {
            super(gui);
            this.button = button;
            this.buttonList = buttonList;
        }

        @Cancelable
        public static class Pre extends ActionPerformedEvent {
            public Pre(GuiScreen gui, GuiButton button, List<GuiButton> buttonList) {
                super(gui, button, buttonList);
            }
        }

        public static class Post extends ActionPerformedEvent {
            public Post(GuiScreen gui, GuiButton button, List<GuiButton> buttonList) {
                super(gui, button, buttonList);
            }
        }
    }

    public static class DrawScreenEvent extends GuiScreenEvent {
        public final int mouseX;
        public final int mouseY;
        public final float renderPartialTicks;

        protected DrawScreenEvent(GuiScreen gui, int mouseX, int mouseY, float renderPartialTicks) {
            super(gui);
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.renderPartialTicks = renderPartialTicks;
        }

        @Cancelable
        public static class Pre extends DrawScreenEvent {
            public Pre(GuiScreen gui, int mouseX, int mouseY, float renderPartialTicks) {
                super(gui, mouseX, mouseY, renderPartialTicks);
            }
        }

        public static class Post extends DrawScreenEvent {
            public Post(GuiScreen gui, int mouseX, int mouseY, float renderPartialTicks) {
                super(gui, mouseX, mouseY, renderPartialTicks);
            }
        }
    }
}
