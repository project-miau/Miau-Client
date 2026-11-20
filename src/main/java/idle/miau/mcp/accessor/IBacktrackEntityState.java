package idle.miau.mcp.accessor;

public interface IBacktrackEntityState {
    double getBacktrackTrueX();
    void setBacktrackTrueX(double value);
    double getBacktrackTrueY();
    void setBacktrackTrueY(double value);
    double getBacktrackTrueZ();
    void setBacktrackTrueZ(double value);
    double getBacktrackLerpX();
    void setBacktrackLerpX(double value);
    double getBacktrackLerpY();
    void setBacktrackLerpY(double value);
    double getBacktrackLerpZ();
    void setBacktrackLerpZ(double value);
    boolean hasBacktrackTruePosition();
    void setBacktrackTruePosition(boolean value);
}
