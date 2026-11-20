package idle.miau.mcp.accessor;

public interface IAccessorEntityPlayerSP {
    double getLastReportedPosX();

    double getLastReportedPosY();

    double getLastReportedPosZ();

    float getLastReportedYaw();

    boolean getServerSprintState();

    void setServerSprintState(boolean state);

    boolean getServerSneakState();

    void setServerSneakState(boolean state);
}
