package idle.miau.utility;

import idle.miau.management.LagPacketPolicy;
import net.minecraft.network.Packet;
import java.math.BigInteger;
import java.util.LinkedList;

public class BlinkUtils implements IMinecraftInstance {
    private static final int MAX_BUFFERED_PACKETS = 4096;
    private static final LinkedList<Packet<?>> playerBuffer = new LinkedList<>();
    private static final int misMatch_Type = -302;
    private static boolean movingPacketStat = false;
    public static boolean transactionStat = false;
    public static boolean keepAliveStat = false;
    private static boolean actionStat = false;
    private static boolean abilitiesStat = false;
    private static boolean invStat = false;
    private static boolean interactStat = false;
    private static boolean otherPacket = false;

    private static final boolean[] packetToggleStat = new boolean[26];

    static {
        setBlinkState(true, true, false, false, false, false, false, false, false, false, false);
        clearPacket(null, false, -1);
    }

    public static synchronized void releasePacket(String packetType, boolean onlySelected, int amount, int minBuff) {
        int count = 0;
        if (packetType == null) {
            count = -1;
            for (Packet<?> packet : playerBuffer) {
                int packetID = 0;
                try {
                    String simpleName = packet.getClass().getSimpleName();
                    if (simpleName.length() >= 3) {
                        packetID = new BigInteger(simpleName.substring(1, 3), 16).intValue();
                    }
                } catch (Exception ignored) {}
                if (packetID >= 0 && packetID < packetToggleStat.length) {
                    if (packetToggleStat[packetID] || !onlySelected) {
                        PacketUtils.sendPacketNoEvent(packet);
                    }
                } else if (!onlySelected) {
                    PacketUtils.sendPacketNoEvent(packet);
                }
            }
        } else {
            LinkedList<Packet<?>> tempBuffer = new LinkedList<>();
            for (Packet<?> packet : playerBuffer) {
                String className = packet.getClass().getSimpleName();
                if (className.equalsIgnoreCase(packetType)) {
                    tempBuffer.add(packet);
                }
            }
            while (tempBuffer.size() > minBuff && (count < amount || amount <= 0)) {
                PacketUtils.sendPacketNoEvent(tempBuffer.pop());
                count++;
            }
        }
        clearPacket(packetType, onlySelected, count);
    }

    public static synchronized void clearPacket(String packetType, boolean onlySelected, int amount) {
        if (packetType == null) {
            LinkedList<Packet<?>> tempBuffer = new LinkedList<>();
            for (Packet<?> packet : playerBuffer) {
                int packetID = 0;
                try {
                    String simpleName = packet.getClass().getSimpleName();
                    if (simpleName.length() >= 3) {
                        packetID = new BigInteger(simpleName.substring(1, 3), 16).intValue();
                    }
                } catch (Exception ignored) {}
                if (packetID >= 0 && packetID < packetToggleStat.length) {
                    if (!packetToggleStat[packetID] && onlySelected) {
                        tempBuffer.add(packet);
                    }
                }
            }
            playerBuffer.clear();
            playerBuffer.addAll(tempBuffer);
        } else {
            int count = 0;
            LinkedList<Packet<?>> tempBuffer = new LinkedList<>();
            for (Packet<?> packet : playerBuffer) {
                String className = packet.getClass().getSimpleName();
                if (!className.equalsIgnoreCase(packetType)) {
                    tempBuffer.add(packet);
                } else {
                    count++;
                    if (count > amount) {
                        tempBuffer.add(packet);
                    }
                }
            }
            playerBuffer.clear();
            playerBuffer.addAll(tempBuffer);
        }
    }

    public static synchronized boolean pushPacket(Packet<?> packet) {
        if (LagPacketPolicy.isOutboundConnectionCritical(packet)) {
            return false;
        }
        int packetID = 0;
        try {
            String simpleName = packet.getClass().getSimpleName();
            if (simpleName.length() >= 3) {
                packetID = new BigInteger(simpleName.substring(1, 3), 16).intValue();
            }
        } catch (Exception ignored) {}
        if (packetID >= 0 && packetID < packetToggleStat.length) {
            if (packetToggleStat[packetID] && !isBlacklisted(packet.getClass().getSimpleName())) {
                if (playerBuffer.size() >= MAX_BUFFERED_PACKETS) {
                    Packet<?> oldest = playerBuffer.poll();
                    if (oldest != null) {
                        PacketUtils.sendPacketNoEvent(oldest);
                    }
                }
                playerBuffer.add(packet);
                return true;
            }
        }
        return false;
    }

    private static boolean isBlacklisted(String packetType) {
        switch (packetType) {
            case "C00Handshake":
            case "C00PacketLoginStart":
            case "C00PacketServerQuery":
            case "C01PacketChatMessage":
            case "C01PacketEncryptionResponse":
            case "C01PacketPing":
                return true;
            default:
                return false;
        }
    }

    public static synchronized void setBlinkState(boolean off, boolean release, boolean all, boolean packetMoving, boolean packetTransaction, boolean packetKeepAlive, boolean packetAction, boolean packetAbilities, boolean packetInventory, boolean packetInteract, boolean other) {
        if (release) {
            releasePacket(null, false, -1, 0);
        }
        movingPacketStat = (packetMoving && !off) || all;
        transactionStat = (packetTransaction && !off) || all;
        keepAliveStat = (packetKeepAlive && !off) || all;
        actionStat = (packetAction && !off) || all;
        abilitiesStat = (packetAbilities && !off) || all;
        invStat = (packetInventory && !off) || all;
        interactStat = (packetInteract && !off) || all;
        otherPacket = (other && !off) || all;
        if (all) {
            for (int i = 0; i < packetToggleStat.length; i++) {
                packetToggleStat[i] = true;
            }
        } else {
            for (int i = 0; i < packetToggleStat.length; i++) {
                switch (i) {
                    case 0x00: packetToggleStat[i] = keepAliveStat; break;
                    case 0x01: case 0x11: case 0x12: case 0x14: case 0x15: case 0x17: case 0x18: case 0x19: packetToggleStat[i] = otherPacket; break;
                    case 0x03: case 0x04: case 0x05: case 0x06: packetToggleStat[i] = movingPacketStat; break;
                    case 0x0F: packetToggleStat[i] = transactionStat; break;
                    case 0x02: case 0x09: case 0x0A: case 0x0B: packetToggleStat[i] = actionStat; break;
                    case 0x0C: case 0x13: packetToggleStat[i] = abilitiesStat; break;
                    case 0x0D: case 0x0E: case 0x10: case 0x16: packetToggleStat[i] = invStat; break;
                    case 0x07: case 0x08: packetToggleStat[i] = interactStat; break;
                }
            }
        }
    }

    public static synchronized void discardPackets() {
        playerBuffer.clear();
    }

    public static synchronized int bufferSize(String packetType) {
        if (packetType == null) {
            return playerBuffer.size();
        } else {
            int packetCount = 0;
            boolean flag = false;
            for (Packet<?> packet : playerBuffer) {
                String className = packet.getClass().getSimpleName();
                if (className.equalsIgnoreCase(packetType)) {
                    flag = true;
                    packetCount++;
                }
            }
            return flag ? packetCount : misMatch_Type;
        }
    }
}
