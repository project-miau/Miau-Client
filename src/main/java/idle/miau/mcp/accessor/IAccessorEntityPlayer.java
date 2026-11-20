package idle.miau.mcp.accessor;

public interface IAccessorEntityPlayer {
    void setItemInUseCount(int count);

    void setSpeedInAir(float speed);

    float getSpeedOnGround();
}
