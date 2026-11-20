package idle.miau.module.impl.minigames;

import idle.miau.event.PreUpdateEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.event.UseItemEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import idle.miau.mcp.event.client.ClientChatReceivedEvent;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.Subscribe;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkyWars extends Module {
    public ButtonSetting strengthIndicator;
    public ButtonSetting onlyAuraHostileMobs;
    public ButtonSetting renderTimeWarp;

    public final Map<EntityPlayer, Long> strengthPlayers = new HashMap<>();
    private final Map<String, SpawnEggInfo> entitySpawnQueue = new LinkedHashMap<>();
    private final Map<Vec3, Long> timeWarpPositions = new LinkedHashMap<>();
    public final List<Integer> spawnedMobs = new ArrayList<>();

    private static final int STRENGTH_COLOR = new Color(255, 0, 0).getRGB();
    private static final int TIME_WARP_COLOR = new Color(210, 0, 255, 64).getRGB();

    private static final String[] KILL_MESSAGES = {" by ", " to ", " with ", " of ", " from ", " knight ", " for "};

    private boolean thrownPearl;

    public static boolean isSkyWarsTeams = false;

    public SkyWars() {
        super("Sky Wars", category.minigames);
        setDescription("Some Utils for SkyWars.");
        this.registerSetting(onlyAuraHostileMobs = new ButtonSetting("Only aura hostile mobs", true));
        this.registerSetting(renderTimeWarp = new ButtonSetting("Render time warp", true));
        this.registerSetting(strengthIndicator = new ButtonSetting("Strength indicator", true));
    }

    @Override
    public void onDisable() {
        this.clear();
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent e) {
        if (!strengthIndicator.isToggled() || !Utils.nullCheck() || strengthPlayers.isEmpty() || Utils.getSkyWarsStatus() != 2) {
            return;
        }
        int customMode = getCustomMode();
        if (customMode == 2) {
            return;
        }
        isSkyWarsTeams = customMode == 1;
        long duration = isSkyWarsTeams ? 2000 : 5000;
        ArrayList<EntityPlayer> keysList = new ArrayList<>(strengthPlayers.keySet());
        for (EntityPlayer entityPlayer : keysList) {
            long storedTime = strengthPlayers.get(entityPlayer);
            long timePassed = System.currentTimeMillis() - storedTime;
            if (timePassed < duration && !AntiBot.isBot(entityPlayer)) {
                continue;
            }
            strengthPlayers.remove(entityPlayer);
        }
    }

    @Subscribe
    public void onChat(ClientChatReceivedEvent e) {
        if (e.type == 2 || !Utils.nullCheck()) {
            return;
        }
        String stripped = Utils.stripColor(e.message.getUnformattedText());
        if (stripped.isEmpty()) {
            return;
        }
        if (stripped.equals("You will be warped back in 3 seconds!") && thrownPearl) {
            timeWarpPositions.put(new Vec3(mc.thePlayer.lastTickPosX, mc.thePlayer.lastTickPosY, mc.thePlayer.lastTickPosZ), System.currentTimeMillis());
            thrownPearl = false;
            return;
        }
        if (strengthIndicator.isToggled() && Utils.getSkyWarsStatus() == 2) {
            if (getCustomMode() == 2) {
                return;
            }
            if (stripped.endsWith(".") && Arrays.stream(KILL_MESSAGES).anyMatch(stripped::contains)) {
                String[] parts = stripped.split(" ");
                for (String part : parts) {
                    if (!part.endsWith(".")) {
                        continue;
                    }
                    String name = part.substring(0, part.length() - 1);
                    for (EntityPlayer entity : mc.theWorld.playerEntities) {
                        if (!entity.getName().trim().equals(name) || entity == mc.thePlayer) {
                            continue;
                        }
                        strengthPlayers.put(entity, System.currentTimeMillis());
                        break;
                    }
                }
            }
        }
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || Utils.getSkyWarsStatus() != 2) {
            return;
        }
        if (strengthIndicator.isToggled()) {
            for (EntityPlayer entityPlayer : strengthPlayers.keySet()) {
                if (AntiBot.isBot(entityPlayer)) {
                    continue;
                }
                RenderUtils.renderEntity(entityPlayer, 2, 0, 0, STRENGTH_COLOR, false);
            }
        }
        if (renderTimeWarp.isToggled()) {
            Iterator<Map.Entry<Vec3, Long>> iterator = this.timeWarpPositions.entrySet().iterator();
            long currentTime = System.currentTimeMillis();

            while (iterator.hasNext()) {
                Map.Entry<Vec3, Long> entry = iterator.next();
                Vec3 position = entry.getKey();
                long timeThrown = entry.getValue();

                if (currentTime - timeThrown >= 3050) {
                    iterator.remove();
                }
                else {
                    RenderUtils.drawPlayerBoundingBox(position, TIME_WARP_COLOR);
                }
            }
        }
    }

    @Subscribe
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            clear();
        }
        else {
            if (e.entity != null) {
                if (Utils.getSkyWarsStatus() != 2) {
                    return;
                }
                String entityClassName = e.entity.getClass().getSimpleName();
                if (entitySpawnQueue.containsKey(entityClassName)) {
                    Vec3 spawnPosition = new Vec3(e.entity.posX, e.entity.posY, e.entity.posZ);
                    SpawnEggInfo eggInfo = entitySpawnQueue.get(entityClassName);
                    if (eggInfo.spawnPos.distanceTo(spawnPosition) > 3 || Utils.timeBetween(mc.thePlayer.ticksExisted, eggInfo.tickSpawned) > 60) {
                        return;
                    }
                    if (!entitySpawnQueue.remove(entityClassName, eggInfo)) {
                        return;
                    }
                    spawnedMobs.add(e.entity.getEntityId());
                }
            }
        }
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) e.getPacket();
            if (p.getPlacedBlockDirection() != 255 && p.getStack() != null && p.getStack().getItem() != null) {
                if (!(p.getStack().getItem() instanceof ItemMonsterPlacer)) {
                    return;
                }
                Class<? extends Entity> oclass = EntityList.getClassFromID(p.getStack().getMetadata());
                if (oclass == null) {
                    return;
                }
                entitySpawnQueue.put(oclass.getSimpleName(), new SpawnEggInfo(p.getPosition(), mc.thePlayer.ticksExisted));
            }
        }
    }

    @Subscribe
    public void onUseItem(UseItemEvent e) {
        if (e.usedItemStack != null && e.usedItemStack.getItem() instanceof ItemEnderPearl && Utils.getSkyWarsStatus() == 2) {
            ItemStack stack = e.usedItemStack;
            if (Utils.stripString(stack.getDisplayName()).equals("Time Warp Pearl")) {
                thrownPearl = true;
            }
            else {
                if (stack.getDisplayName().startsWith("§b§l")) {
                    List<String> toolTip = stack.getTooltip(mc.thePlayer, true);
                    if (toolTip != null && toolTip.size() > 1 && Utils.stripString(toolTip.get(1)).contains("Teleports you back to your")) {
                        thrownPearl = true;
                    }
                }
            }
        }
    }

    private void clear() {
        strengthPlayers.clear();
        spawnedMobs.clear();
        entitySpawnQueue.clear();
        timeWarpPositions.clear();
        thrownPearl = false;
    }

    public static boolean onlyAuraHostiles() {
        return ModuleManager.skyWars != null && ModuleManager.skyWars.isEnabled() && ModuleManager.skyWars.onlyAuraHostileMobs.isToggled() && Utils.getSkyWarsStatus() == 2;
    }

    public int getCustomMode() {
        List<String> sidebar = Utils.getSidebarLines();
        if (sidebar.isEmpty()) {
            return -1;
        }
        for (String line : sidebar) {
            line = Utils.stripColor(line);
            if (line.startsWith("Teams left: ")) {
                return 1;
            }
            else if (line.startsWith("Lab: ") || line.startsWith("Mode: Mini")) {
                return 2;
            }
        }
        return -1;
    }

    public static class SpawnEggInfo {
        public Vec3 spawnPos;
        public int tickSpawned;

        public SpawnEggInfo(BlockPos spawnPos, int tickSpawned) {
            this.spawnPos = new Vec3(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
            this.tickSpawned = tickSpawned;
        }
    }
}
