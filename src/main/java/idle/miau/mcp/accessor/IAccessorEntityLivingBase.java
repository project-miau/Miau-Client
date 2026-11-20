package idle.miau.mcp.accessor;

public interface IAccessorEntityLivingBase {
    void setJumpTicks(int ticks);

    int getJumpTicks();

    float getLandMovementFactor();
}
