package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.Subscribe;

import java.awt.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Xray extends Module {
    private SliderSetting range;
    private SliderSetting rate;
    private ButtonSetting iron;
    private ButtonSetting gold;
    private ButtonSetting diamond;
    private ButtonSetting emerald;
    private ButtonSetting lapis;
    private ButtonSetting redstone;
    private ButtonSetting coal;
    private ButtonSetting spawner;
    private ButtonSetting obsidian;

    private final Set<BlockPos> blocks = ConcurrentHashMap.newKeySet();
    private long lastCheck = 0L;
    private boolean scanning;
    private int scanRange;
    private int scanX;
    private int scanY;
    private int scanZ;
    private int centerX;
    private int centerY;
    private int centerZ;

    public Xray() {
        super("Xray", category.render);
        setDescription("Highlights ores.");
        this.registerSetting(range = new SliderSetting("Range", 20, 5, 50, 1));
        this.registerSetting(rate = new SliderSetting("Rate", " second", 0.5, 0.1, 3.0, 0.1));
        this.registerSetting(coal = new ButtonSetting("Coal", true));
        this.registerSetting(diamond = new ButtonSetting("Diamond", true));
        this.registerSetting(emerald = new ButtonSetting("Emerald", true));
        this.registerSetting(gold = new ButtonSetting("Gold", true));
        this.registerSetting(iron = new ButtonSetting("Iron", true));
        this.registerSetting(lapis = new ButtonSetting("Lapis", true));
        this.registerSetting(obsidian = new ButtonSetting("Obsidian", true));
        this.registerSetting(redstone = new ButtonSetting("Redstone", true));
        this.registerSetting(spawner = new ButtonSetting("Spawner", true));
    }

    @Override
    public void onDisable() {
        this.blocks.clear();
        this.scanning = false;
        this.lastCheck = 0L;
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            scanning = false;
            return;
        }
        long now = System.currentTimeMillis();
        if (!scanning) {
            if (now - lastCheck < rate.getInput() * 1000.0D) {
                return;
            }
            lastCheck = now;
            scanRange = (int) range.getInput();
            centerX = (int) mc.thePlayer.posX;
            centerY = (int) mc.thePlayer.posY;
            centerZ = (int) mc.thePlayer.posZ;
            scanX = -scanRange;
            scanY = scanRange;
            scanZ = -scanRange;
            scanning = true;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int budget = 4096;
        while (scanning && budget-- > 0) {
            int blockY = centerY + scanY;
            if (blockY >= 0) {
                mutablePos.set(centerX + scanX, blockY, centerZ + scanZ);
                if (!blocks.contains(mutablePos)) {
                    Block blockState = BlockUtils.getBlock(mutablePos);
                    if (blockState != null && canBreak(blockState)) {
                        blocks.add(new BlockPos(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ()));
                    }
                }
            }
            advanceScanCursor();
        }
    }

    private void advanceScanCursor() {
        if (++scanZ <= scanRange) {
            return;
        }
        scanZ = -scanRange;
        if (++scanX <= scanRange) {
            return;
        }
        scanX = -scanRange;
        if (--scanY < Math.max(-scanRange, -centerY)) {
            scanning = false;
        }
    }

    @Subscribe
    public void onEntityJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            this.blocks.clear();
            this.scanning = false;
            this.lastCheck = 0L;
        }
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent ev) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (!this.blocks.isEmpty()) {
            Iterator<BlockPos> iterator = blocks.iterator();
            while (iterator.hasNext()) {
                BlockPos blockPos = iterator.next();
                Block block = BlockUtils.getBlock(blockPos);
                if (block == null || !canBreak(block)) {
                    iterator.remove();
                    continue;
                }
                this.drawBox(blockPos);
            }
        }
    }

    private void drawBox(BlockPos p) {
        if (p == null) {
            return;
        }
        int[] rgb = this.getColor(BlockUtils.getBlock(p));
        if (rgb[0] + rgb[1] + rgb[2] != 0) {
            RenderUtils.renderBlock(p, (new Color(rgb[0], rgb[1], rgb[2])).getRGB(), false, true);
        }
    }

    private int[] getColor(Block b) {
        int red = 0;
        int green = 0;
        int blue = 0;
        if (b.equals(Blocks.iron_ore)) {
            red = 255;
            green = 255;
            blue = 255;
        }
        else if (b.equals(Blocks.gold_ore)) {
            red = 255;
            green = 255;
        }
        else if (b.equals(Blocks.diamond_ore)) {
            green = 220;
            blue = 255;
        }
        else if (b.equals(Blocks.emerald_ore)) {
            red = 35;
            green = 255;
        }
        else if (b.equals(Blocks.lapis_ore)) {
            green = 50;
            blue = 255;
        }
        else if (b.equals(Blocks.redstone_ore)) {
            red = 255;
        }
        else if (b.equals(Blocks.mob_spawner)) {
            red = 30;
            blue = 135;
        }

        return new int[]{red, green, blue};
    }

    public boolean canBreak(Block block) {
        return (iron.isToggled() && block.equals(Blocks.iron_ore)) ||
                (gold.isToggled() && block.equals(Blocks.gold_ore)) ||
                (diamond.isToggled() && block.equals(Blocks.diamond_ore)) ||
                (emerald.isToggled() && block.equals(Blocks.emerald_ore)) ||
                (lapis.isToggled() && block.equals(Blocks.lapis_ore)) ||
                (redstone.isToggled() && block.equals(Blocks.redstone_ore)) ||
                (coal.isToggled() && block.equals(Blocks.coal_ore)) ||
                (spawner.isToggled() && block.equals(Blocks.mob_spawner)) ||
                (obsidian.isToggled() && block.equals(Blocks.obsidian));
    }
}
