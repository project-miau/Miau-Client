package idle.miau.utility;

import net.minecraft.network.Packet;

public class TimedPacket {

    private final Packet<?> packet;
    private final CooldownTimer time;
    private final long millis;

    public TimedPacket(Packet<?> packet) {
        this.packet = packet;
        this.time = new CooldownTimer();
        this.millis = System.currentTimeMillis();
    }

    public TimedPacket(final Packet<?> packet, final long millis) {
        this.packet = packet;
        this.millis = millis;
        this.time = new CooldownTimer();
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public CooldownTimer getCold() {
        return getTime();
    }

    public CooldownTimer getTime() {
        return time;
    }

    public long getMillis() {
        return millis;
    }

    public long getMs() {
        return millis;
    }

}
