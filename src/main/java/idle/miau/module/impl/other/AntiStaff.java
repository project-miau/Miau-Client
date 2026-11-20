package idle.miau.module.impl.other;

import com.mojang.authlib.GameProfile;
import idle.miau.Miau;
import idle.miau.event.DisconnectEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.notifications.MiauNotificationService;
import idle.miau.utility.Utils;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S38PacketPlayerListItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AntiStaff extends Module {
    private static final String[] MODES = {"3FMC", "General", "LuckyVN"};
    private static final Set<String> MIAU_STAFF = Set.of(
            "vinghgaming",
            "cheesethesylveon",
            "thanhhau",
            "sennekoi",
            "lasgana",
            "novapev4",
            "khoaho01623");
    private static final Set<String> LUCKYVN_STAFF = Set.of("minh_dat", "appledepzai");

    public final SliderSetting mode = new SliderSetting("Mode", 0, MODES);
    public final ButtonSetting ghostDetect = new ButtonSetting("Ghost Detect", true);
    public final ButtonSetting autoLeave = new ButtonSetting("Auto Leave", false);

    private final Set<Integer> validEntities = new HashSet<>();
    private final Set<Integer> flaggedGhost = new HashSet<>();
    private final Set<UUID> flaggedVanish = new HashSet<>();
    private final Map<UUID, String> knownNames = new HashMap<>();

    public AntiStaff() {
        super("AntiStaff", category.other);
        setDescription("Detects possible staff activity and can leave the server.");
        registerSetting(mode);
        registerSetting(ghostDetect);
        registerSetting(autoLeave);
    }

    @Override
    public void onEnable() {
        reset();
        if (mc != null && mc.getNetHandler() != null) {
            for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                GameProfile profile = info.getGameProfile();
                if (profile.getId() != null && profile.getName() != null) {
                    knownNames.put(profile.getId(), profile.getName());
                }
            }
        }
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    @Subscribe
    public void onWorld(WorldEvent event) {
        reset();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        reset();
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (!isEnabled() || mc == null) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof S38PacketPlayerListItem) {
            handlePlayerList((S38PacketPlayerListItem) packet);
        } else if (packet instanceof S0CPacketSpawnPlayer) {
            validEntities.add(((S0CPacketSpawnPlayer) packet).getEntityID());
        } else if (packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                validEntities.remove(id);
                flaggedGhost.remove(id);
            }
        } else if (packet instanceof S14PacketEntity) {
            checkEntity(((S14PacketEntity) packet).getEntityId());
        } else if (packet instanceof S18PacketEntityTeleport) {
            checkEntity(((S18PacketEntityTeleport) packet).getEntityId());
        }
    }

    private void handlePlayerList(S38PacketPlayerListItem packet) {
        NetHandlerPlayClient handler = mc.getNetHandler();
        for (S38PacketPlayerListItem.AddPlayerData data : packet.getEntries()) {
            if (data == null || data.getProfile() == null) {
                continue;
            }
            UUID id = data.getProfile().getId();
            if (id == null || mc.thePlayer != null && id.equals(mc.thePlayer.getUniqueID())) {
                continue;
            }

            String name = resolveName(data.getProfile(), handler);
            switch (packet.getAction()) {
                case ADD_PLAYER:
                    if (name != null) {
                        knownNames.put(id, name);
                    }
                    flaggedVanish.remove(id);
                    if (isStaff(name)) {
                        alert(MiauNotificationService.TYPE_ANTICHEAT,
                                "Staff Online!", name + " joined the server",
                                "&c&l[STAFF] &r&f" + name + " &ajoined the server!");
                        triggerAutoLeave();
                    }
                    break;
                case REMOVE_PLAYER:
                    if (name != null) {
                        knownNames.put(id, name);
                    }
                    if (isStaff(name)) {
                        alert(MiauNotificationService.TYPE_SUCCESS,
                                "Staff Left", name + " left the server",
                                "&a[STAFF] &f" + name + " &cleft the server.");
                    }
                    break;
                case UPDATE_LATENCY:
                case UPDATE_GAME_MODE:
                case UPDATE_DISPLAY_NAME:
                    if (isGeneral() && handler != null && handler.getPlayerInfo(id) == null) {
                        alertVanish(id, name, "received a player update while hidden");
                    }
                    break;
            }
            if (!isEnabled()) {
                return;
            }
        }
    }

    private boolean isGeneral() {
        return (int) mode.getInput() == 1;
    }

    private boolean isStaff(String name) {
        if (name == null || isGeneral()) {
            return false;
        }
        Set<String> staff = (int) mode.getInput() == 2 ? LUCKYVN_STAFF : MIAU_STAFF;
        return staff.contains(name.toLowerCase(Locale.ROOT));
    }

    private String resolveName(GameProfile profile, NetHandlerPlayClient handler) {
        if (profile.getName() != null) {
            return profile.getName();
        }
        if (handler != null) {
            NetworkPlayerInfo info = handler.getPlayerInfo(profile.getId());
            if (info != null && info.getGameProfile().getName() != null) {
                return info.getGameProfile().getName();
            }
        }
        if (mc.theWorld != null) {
            EntityPlayer player = mc.theWorld.getPlayerEntityByUUID(profile.getId());
            if (player != null) {
                return player.getGameProfile().getName();
            }
        }
        return knownNames.get(profile.getId());
    }

    private void checkEntity(int entityId) {
        if (mc.theWorld == null || mc.thePlayer == null || entityId == mc.thePlayer.getEntityId()) {
            return;
        }

        Entity entity = mc.theWorld.getEntityByID(entityId);
        if (entity != null) {
            NetHandlerPlayClient handler = mc.getNetHandler();
            if (isGeneral() && entity instanceof EntityPlayer && handler != null
                    && handler.getPlayerInfo(entity.getUniqueID()) == null) {
                EntityPlayer player = (EntityPlayer) entity;
                alertVanish(player.getUniqueID(), player.getGameProfile().getName(),
                        "moved while hidden from the player list");
            }
            return;
        }

        if (!ghostDetect.isToggled() || validEntities.contains(entityId) || !flaggedGhost.add(entityId)) {
            return;
        }
        alert(MiauNotificationService.TYPE_ANTICHEAT,
                "Ghost Entity!", "Vanish detected (ID: " + entityId + ")",
                "&c&l[GHOST] &r&fVanish entity detected &7(ID: " + entityId + ")");
        triggerAutoLeave();
    }

    private void alertVanish(UUID id, String name, String reason) {
        if (!flaggedVanish.add(id)) {
            return;
        }
        String playerName = name == null ? id.toString().substring(0, 8) : name;
        alert(MiauNotificationService.TYPE_ANTICHEAT,
                "Vanish Detected!", playerName + " " + reason,
                "&c&l[VANISH] &r&f" + playerName + " &c" + reason + "!");
        triggerAutoLeave();
    }

    private void alert(int type, String title, String description, String chatMessage) {
        if (Miau.notificationService != null) {
            Miau.notificationService.enqueue(title + ": " + description, "Staff", type);
        }
        Utils.sendMessage(chatMessage);
    }

    private void triggerAutoLeave() {
        if (autoLeave.isToggled() && mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage("/hub");
            disable();
        }
    }

    private void reset() {
        validEntities.clear();
        flaggedGhost.clear();
        flaggedVanish.clear();
        knownNames.clear();
    }
}
