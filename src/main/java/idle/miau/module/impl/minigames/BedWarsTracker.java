package idle.miau.module.impl.minigames;

import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.module.Module;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockObsidian;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BedWarsTracker extends Module {
    public static final String LEGACY_MODULE_NAME = "Bed Wars";
    public static final String LEGACY_CONFIG_ID = "idle.miau.module.impl.bedwar_utils.BedWars";
    public static final String INTERIM_CONFIG_ID = "idle.miau.module.impl.minigames.BedWars";

    private ButtonSetting diamondArmor;
    private ButtonSetting fireball;
    private ButtonSetting enderPearl;
    private ButtonSetting obsidian;
    private ButtonSetting shouldPing;

    private List<String> armoredPlayer = new ArrayList<>();
    private Map<String, String> lastHeldMap = new ConcurrentHashMap<>();
    private Map<BlockPos, Long> obsidianPos = new HashMap<>();
    public List<SpawnEggInfo> entitySpawnQueue = new ArrayList<>();
    public List<Integer> spawnedMobs = new ArrayList<>();

    public static class SpawnEggInfo {
        public Vec3 spawnPos;
        public int tickSpawned;

        public SpawnEggInfo(BlockPos pos, int tickSpawned) {
            this.spawnPos = new Vec3(pos.getX(), pos.getY(), pos.getZ());
            this.tickSpawned = tickSpawned;
        }
    }

    private int obsidianColor = new Color(106, 13, 173).getRGB();

    public BedWarsTracker() {
        super("BedWarsTracker", category.minigames);
        setDescription("Alerts you to important enemy purchases in BedWars.");
        this.registerSetting(new DescriptionSetting("Game alerts"));
        this.registerSetting(diamondArmor = new ButtonSetting("Diamond armor", true));
        this.registerSetting(fireball = new ButtonSetting("Fireball", false));
        this.registerSetting(obsidian = new ButtonSetting("Obsidian", true));
        this.registerSetting(enderPearl = new ButtonSetting("Ender pearl", true));
        this.registerSetting(shouldPing = new ButtonSetting("Should ping", true));
        this.closetModule = true;
    }

    public void onDisable() {
        entitySpawnQueue.clear();
        spawnedMobs.clear();
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (Utils.nullCheck() && obsidian.isToggled()) {
            if (this.obsidianPos.isEmpty()) {
                return;
            }
            try {
                Iterator<Map.Entry<BlockPos, Long>> iterator = this.obsidianPos.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<BlockPos, Long> entry = iterator.next();
                    BlockPos blockPos = entry.getKey();
                    Long receivedMs = entry.getValue();

                    if (!(mc.theWorld.getBlockState(blockPos).getBlock() instanceof BlockObsidian) && Utils.timeBetween(System.currentTimeMillis(), receivedMs) >= 500) {
                        iterator.remove();
                        continue;
                    }
                    RenderUtils.renderBlock(blockPos, obsidianColor, false, true);
                }

            }
            catch (Exception exception) {}
        }
    }

    @Subscribe
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            armoredPlayer.clear();
            lastHeldMap.clear();
            obsidianPos.clear();
            entitySpawnQueue.clear();
            spawnedMobs.clear();
        }
        else {
            if (e.entity != null && e.entity instanceof EntityIronGolem) {
                if (Utils.getBedwarsStatus() != 2) {
                    return;
                }
                Vec3 spawnPosition = new Vec3(e.entity.posX, e.entity.posY, e.entity.posZ);
                for (SpawnEggInfo eggInfo : entitySpawnQueue) {
                    if (eggInfo.spawnPos.distanceTo(spawnPosition) > 3 || Utils.timeBetween(mc.thePlayer.ticksExisted, eggInfo.tickSpawned) > 60) {
                        return;
                    }
                    if (!entitySpawnQueue.remove(eggInfo)) {
                        return;
                    }
                    spawnedMobs.add(e.entity.getEntityId());
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        if (Utils.getBedwarsStatus() == 2) {
            if (diamondArmor.isToggled() || enderPearl.isToggled() || obsidian.isToggled()) {
                for (EntityPlayer p : mc.theWorld.playerEntities) {
                    if (p == null) {
                        continue;
                    }
                    if (p == mc.thePlayer) {
                        continue;
                    }
                    if (AntiBot.isBot(p)) {
                        continue;
                    }
                    String name = p.getName();
                    ItemStack item = p.getHeldItem();
                    if (diamondArmor.isToggled()) {
                        ItemStack leggings = p.inventory.armorInventory[1];
                        if (!armoredPlayer.contains(name) && p.inventory != null && leggings != null && leggings.getItem() != null && leggings.getItem() == Items.diamond_leggings) {
                            armoredPlayer.add(name);
                            Utils.sendMessage("&eAlert: &r" + p.getDisplayName().getFormattedText() + " &7has purchased &bDiamond Armor");
                            ping();
                        }
                    }
                    if (item != null && !lastHeldMap.containsKey(name)) {
                        String itemType = getItemType(item);
                        if (itemType != null) {
                            lastHeldMap.put(name, itemType);
                            double distance = Math.round(mc.thePlayer.getDistanceToEntity(p));
                            handleAlert(itemType, p.getDisplayName().getFormattedText(), Utils.asWholeNum(distance));
                        }
                    } else if (lastHeldMap.containsKey(name)) {
                        String itemType = lastHeldMap.get(name);
                        if (!itemType.equals(getItemType(item))) {
                            lastHeldMap.remove(name);
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) e.getPacket();
            if (p.getPlacedBlockDirection() != 255 && p.getStack() != null && p.getStack().getItem() != null) {
                if (p.getStack().getItem() instanceof ItemMonsterPlacer) {
                    Class<? extends Entity> oclass = EntityList.getClassFromID(p.getStack().getMetadata());
                    if (oclass == null) {
                        return;
                    }
                    if (oclass.getSimpleName().equals("EntityIronGolem")) {
                        entitySpawnQueue.add(new SpawnEggInfo(p.getPosition(), mc.thePlayer.ticksExisted));
                    }
                }
            }
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.getPacket() instanceof S23PacketBlockChange) {
            S23PacketBlockChange p = (S23PacketBlockChange) e.getPacket();
            if (p.getBlockState() != null && p.getBlockState().getBlock() instanceof BlockObsidian && isNextToBed(p.getBlockPosition())) {
                this.obsidianPos.put(p.getBlockPosition(), System.currentTimeMillis());
            }
        }
    }

    private boolean isNextToBed(BlockPos blockPos) {
        for (EnumFacing enumFacing : EnumFacing.values()) {
            BlockPos offset = blockPos.offset(enumFacing);
            if (BlockUtils.getBlock(offset) instanceof BlockBed) {
                return true;
            }
        }
        return false;
    }

    private String getItemType(ItemStack item) {
        if (item == null || item.getItem() == null) {
            return null;
        }
        String unlocalizedName = item.getItem().getUnlocalizedName();
        if (item.getItem() instanceof ItemEnderPearl && enderPearl.isToggled()) {
            return "&7an §3Ender Pearl";
        }
        else if (unlocalizedName.contains("tile.obsidian") && obsidian.isToggled()) {
            return "§dObsidian";
        }
        else if (item.getItem() instanceof ItemFireball && fireball.isToggled()) {
            return "&7a §6Fireball";
        }
        return null;
    }

    private void handleAlert(String itemType, String name, String info) {
        String alert = "&eAlert: &r" + name + " &7is holding " + itemType + " &7(" + "§d" + info + "m" + "&7)";
        Utils.sendMessage(alert);
        ping();
    }

    private void ping() {
        if (shouldPing.isToggled()) {
            mc.thePlayer.playSound("note.pling", 1.0f, 1.0f);
        }
    }
}
