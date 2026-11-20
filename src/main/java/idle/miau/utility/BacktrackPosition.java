package idle.miau.utility;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

public final class BacktrackPosition {
    private final double x;
    private final double y;
    private final double z;
    private final long timestamp;

    public BacktrackPosition(double x, double y, double z, long timestamp) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.timestamp = timestamp;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public long getTimestamp() { return timestamp; }
    public Vec3 asVec3() { return new Vec3(x, y, z); }

    public AxisAlignedBB offsetBox(AxisAlignedBB box, Vec3 currentPosition) {
        return box.offset(x - currentPosition.xCoord, y - currentPosition.yCoord, z - currentPosition.zCoord);
    }
}
