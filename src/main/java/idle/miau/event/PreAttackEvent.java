package idle.miau.event;

import net.minecraft.util.MovingObjectPosition;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class PreAttackEvent extends Event {

    public final MovingObjectPosition objectMouseOver;

    public PreAttackEvent(MovingObjectPosition objectMouseOver) {
        this.objectMouseOver = objectMouseOver;
    }
}
