package idle.miau.module.impl.minigames;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import idle.miau.mcp.event.client.MouseEvent;
import idle.miau.mcp.event.Subscribe;
import org.lwjgl.input.Mouse;

import java.util.Arrays;
import java.util.List;

public class SumoFences extends Module {
    public SliderSetting fenceHeight;
    public SliderSetting blockType;

    private int updateTicks;

    private final List<String> MAPS = Arrays.asList("Sumo", "Space Mine", "White Crystal", "Fort");

    private IBlockState fenceState;
    private final List<BlockPos> f_p = Arrays.asList(new BlockPos(9, 65, -2), new BlockPos(9, 65, -1), new BlockPos(9, 65, 0), new BlockPos(9, 65, 1), new BlockPos(9, 65, 2), new BlockPos(9, 65, 3), new BlockPos(8, 65, 3), new BlockPos(8, 65, 4), new BlockPos(8, 65, 5), new BlockPos(7, 65, 5), new BlockPos(7, 65, 6), new BlockPos(7, 65, 7), new BlockPos(6, 65, 7), new BlockPos(5, 65, 7), new BlockPos(5, 65, 8), new BlockPos(4, 65, 8), new BlockPos(3, 65, 8), new BlockPos(3, 65, 9), new BlockPos(2, 65, 9), new BlockPos(1, 65, 9), new BlockPos(0, 65, 9), new BlockPos(-1, 65, 9), new BlockPos(-2, 65, 9), new BlockPos(-3, 65, 9), new BlockPos(-3, 65, 8), new BlockPos(-4, 65, 8), new BlockPos(-5, 65, 8), new BlockPos(-5, 65, 7), new BlockPos(-6, 65, 7), new BlockPos(-7, 65, 7), new BlockPos(-7, 65, 6), new BlockPos(-7, 65, 5), new BlockPos(-8, 65, 5), new BlockPos(-8, 65, 4), new BlockPos(-8, 65, 3), new BlockPos(-9, 65, 3), new BlockPos(-9, 65, 2), new BlockPos(-9, 65, 1), new BlockPos(-9, 65, 0), new BlockPos(-9, 65, -1), new BlockPos(-9, 65, -2), new BlockPos(-9, 65, -3), new BlockPos(-8, 65, -3), new BlockPos(-8, 65, -4), new BlockPos(-8, 65, -5), new BlockPos(-7, 65, -5), new BlockPos(-7, 65, -6), new BlockPos(-7, 65, -7), new BlockPos(-6, 65, -7), new BlockPos(-5, 65, -7), new BlockPos(-5, 65, -8), new BlockPos(-4, 65, -8), new BlockPos(-3, 65, -8), new BlockPos(-3, 65, -9), new BlockPos(-2, 65, -9), new BlockPos(-1, 65, -9), new BlockPos(0, 65, -9), new BlockPos(1, 65, -9), new BlockPos(2, 65, -9), new BlockPos(3, 65, -9), new BlockPos(3, 65, -8), new BlockPos(4, 65, -8), new BlockPos(5, 65, -8), new BlockPos(5, 65, -7), new BlockPos(6, 65, -7), new BlockPos(7, 65, -7), new BlockPos(7, 65, -6), new BlockPos(7, 65, -5), new BlockPos(8, 65, -5), new BlockPos(8, 65, -4), new BlockPos(8, 65, -3), new BlockPos(9, 65, -3));

    private final String[] blockTypes = new String[] { "Oak fence", "Leaves", "Glass", "Barrier" };

    public SumoFences() {
        super("SumoFences", category.minigames, 0);
        setDescription("Fences for Hypixel sumo.");
        this.fenceState = Blocks.oak_fence.getDefaultState();
        this.registerSetting(new DescriptionSetting("Fences for Hypixel sumo."));
        this.registerSetting(fenceHeight = new SliderSetting("Fence height", 4.0D, 1.0D, 6.0D, 1.0D));
        this.registerSetting(blockType = new SliderSetting("Block type", 0, blockTypes));
    }

    public void onEnable() {
        updateTicks = 0;
    }

    public void onDisable() {
        updateTicks = 0;
        if (!Utils.nullCheck()) {
            return;
        }
        for (BlockPos p : f_p) {
            for (int i = 0; (double) i < fenceHeight.getInput(); ++i) {
                BlockPos p2 = new BlockPos(p.getX(), p.getY() + i, p.getZ());
                if (mc.theWorld.getBlockState(p2).getBlock() == this.fenceState.getBlock()) {
                    mc.theWorld.setBlockState(p2, Blocks.air.getDefaultState());
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck() || ++updateTicks < 10) {
            return;
        }
        updateTicks = 0;
        if (!isSumo()) {
            return;
        }
        for (BlockPos p : f_p) {
            for (int i = 0; (double) i < fenceHeight.getInput(); ++i) {
                BlockPos p2 = new BlockPos(p.getX(), p.getY() + i, p.getZ());
                if (mc.theWorld.getBlockState(p2).getBlock() == Blocks.air) {
                    mc.theWorld.setBlockState(p2, fenceState);
                }
            }
        }
    }

    @Subscribe
    public void onMouse(MouseEvent e) {
        if (e.buttonstate && (e.button == 0 || e.button == 1) && Utils.nullCheck() && this.isSumo()) {
            MovingObjectPosition over = mc.objectMouseOver;
            if (over != null && over.typeOfHit == MovingObjectType.BLOCK) {
                int x = over.getBlockPos().getX();
                int z = over.getBlockPos().getZ();

                for (BlockPos pos : f_p) {
                    if (pos.getX() == x && pos.getZ() == z) {
                        e.setCanceled(true);
                        if (e.button == 0) {
                            Utils.setSwinging();
                        }
                        Mouse.poll();
                        break;
                    }
                }
            }
        }
    }

    private boolean isSumo() {
        if (Utils.isHypixel()) {
            for (String l : Utils.getScoreBoardOld()) {
                String s = Utils.stripColor(l);
                if (s.startsWith("Map:")) {
                    if (this.MAPS.contains(s.substring(5))) {
                        return true;
                    }
                } else if (s.equals("Mode: Sumo Duel")) {
                    return true;
                }
            }
        }

        return false;
    }

    public void guiUpdate() {
        switch ((int) blockType.getInput()) {
            case 0:
                this.fenceState = Blocks.oak_fence.getDefaultState();
                break;
            case 1:
                this.fenceState = Blocks.leaves.getDefaultState();
                break;
            case 2:
                this.fenceState = Blocks.glass.getDefaultState();
                break;
            case 3:
                this.fenceState = Blocks.barrier.getDefaultState();
        }
    }
}
