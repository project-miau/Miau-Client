package idle.miau.mcp.event.game;

import net.minecraft.entity.player.EntityPlayer;
import idle.miau.mcp.event.Event;

public class TickEvent extends Event {
    public enum Phase { START, END }

    public final Phase phase;

    protected TickEvent(Phase phase) {
        this.phase = phase;
    }

    public static class ClientTickEvent extends TickEvent {
        public ClientTickEvent(Phase phase) { super(phase); }
    }

    public static class RenderTickEvent extends TickEvent {
        public final float renderTickTime;
        public RenderTickEvent(Phase phase, float renderTickTime) {
            super(phase);
            this.renderTickTime = renderTickTime;
        }
    }

    public static class PlayerTickEvent extends TickEvent {
        public final EntityPlayer player;
        public PlayerTickEvent(Phase phase, EntityPlayer player) {
            super(phase);
            this.player = player;
        }
    }
}
