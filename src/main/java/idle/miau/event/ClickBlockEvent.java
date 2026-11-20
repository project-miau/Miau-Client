package idle.miau.event;

import idle.miau.mcp.event.Event;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public final class ClickBlockEvent extends Event {
    public final BlockPos clickedBlock;
    public final EnumFacing side;

    public ClickBlockEvent(BlockPos clickedBlock, EnumFacing side) {
        this.clickedBlock = clickedBlock;
        this.side = side;
    }
}
