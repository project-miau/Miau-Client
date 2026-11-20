package idle.miau.event;

import net.minecraft.item.ItemStack;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class UseItemEvent extends Event {
    public ItemStack usedItemStack;

    public UseItemEvent(ItemStack usedItemStack) {
        this.usedItemStack = usedItemStack;
    }
}
