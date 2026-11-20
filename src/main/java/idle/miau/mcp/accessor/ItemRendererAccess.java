package idle.miau.mcp.accessor;

public interface ItemRendererAccess {
    void setCancelUpdate(boolean cancel);

    void setCancelReset(boolean reset);

    boolean isRenderItemInUse();

    void setRenderItemInUse(boolean renderItemInUse);
}
