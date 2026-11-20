package idle.miau.mcp.accessor;

import net.minecraft.util.BlockPos;

public interface IAccessorPlayerControllerMP {
    float getCurBlockDamageMP();

    BlockPos getCurrentBlock();

    void setCurBlockDamageMP(float damage);

    int getBlockHitDelay();

    void setBlockHitDelay(int delay);

    boolean isHittingBlock();

    int getCurrentPlayerItem();

    void setCurrentPlayerItem(int slot);

    void callSyncCurrentPlayItem();
}
