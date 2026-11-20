package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.StairsUtils;
import idle.miau.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.client.Minecraft;
import net.minecraft.block.BlockDeadBush;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import idle.miau.mcp.event.client.DrawBlockHighlightEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class BlockOverlay extends Module {
    private static final String[] COLOR_MODES = {"HUD", "Static", "Gradient", "Fade", "Chroma"};
    private static final double PADDING = 0.002;

    private final GroupSetting faceGroup;
    private final ButtonSetting faceVisible;
    private final SliderSetting faceColorMode;
    private final ColorSetting faceColor;
    private final ColorSetting faceColor2;
    private final SliderSetting faceFadeSpeed;
    private final SliderSetting faceChromaSpeed;

    private final GroupSetting outlineGroup;
    private final ButtonSetting outlineVisible;
    private final SliderSetting outlineColorMode;
    private final ColorSetting outlineColor;
    private final ColorSetting outlineColor2;
    private final SliderSetting outlineFadeSpeed;
    private final SliderSetting outlineChromaSpeed;
    private final SliderSetting thickness;

    private final GroupSetting optionsGroup;
    private final ButtonSetting persistence;
    private final ButtonSetting depthless;
    private final ButtonSetting barriers;
    private final ButtonSetting hidePlants;

    public BlockOverlay() {
        super("BlockOverlay", category.render);
        setDescription("Customizes the outline and fill of targeted blocks.");
        this.registerSetting(faceGroup = new GroupSetting("Face"));
        this.registerSetting(faceVisible = new ButtonSetting(faceGroup, "Face Visible", true));
        this.registerSetting(faceColorMode = new SliderSetting(faceGroup, "Face Color mode", 0, COLOR_MODES));
        this.registerSetting(faceColor = new ColorSetting(faceGroup, "Face Color", 0, 0, 0, 100));
        this.registerSetting(faceColor2 = new ColorSetting(faceGroup, "Face Color 2", 255, 255, 255, 100));
        this.registerSetting(faceFadeSpeed = new SliderSetting(faceGroup, "Face Fade speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(faceChromaSpeed = new SliderSetting(faceGroup, "Face Chroma speed", 5.5, 1.0, 10.0, 0.5));

        this.registerSetting(outlineGroup = new GroupSetting("Outline"));
        this.registerSetting(outlineVisible = new ButtonSetting(outlineGroup, "Outline Visible", true));
        this.registerSetting(outlineColorMode = new SliderSetting(outlineGroup, "Outline Color mode", 0, COLOR_MODES));
        this.registerSetting(outlineColor = new ColorSetting(outlineGroup, "Outline Color", 0, 0, 0, 255));
        this.registerSetting(outlineColor2 = new ColorSetting(outlineGroup, "Outline Color 2", 255, 255, 255, 255));
        this.registerSetting(outlineFadeSpeed = new SliderSetting(outlineGroup, "Outline Fade speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(outlineChromaSpeed = new SliderSetting(outlineGroup, "Outline Chroma speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(thickness = new SliderSetting(outlineGroup, "Thickness", 2.0, 1.0, 10.0, 0.5));

        this.registerSetting(optionsGroup = new GroupSetting("Options"));
        this.registerSetting(persistence = new ButtonSetting(optionsGroup, "Persistence", false));
        this.registerSetting(depthless = new ButtonSetting(optionsGroup, "Depthless", false));
        this.registerSetting(barriers = new ButtonSetting(optionsGroup, "Barriers", false));
        this.registerSetting(hidePlants = new ButtonSetting(optionsGroup, "Hide plants", false));
    }

    @Override
    public void guiUpdate() {
        int oMode = (int) faceColorMode.getInput();
        faceColor2.setVisible(oMode == 2 || oMode == 3, this);
        faceFadeSpeed.setVisible(oMode == 3, this);
        faceChromaSpeed.setVisible(oMode == 4, this);

        int olMode = (int) outlineColorMode.getInput();
        outlineColor2.setVisible(olMode == 2 || olMode == 3, this);
        outlineFadeSpeed.setVisible(olMode == 3, this);
        outlineChromaSpeed.setVisible(olMode == 4, this);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onDrawBlockHighlight(DrawBlockHighlightEvent e) {
        e.setCanceled(true);
        if (!Utils.nullCheck()) return;
        if (!persistence.isToggled() && mc.thePlayer.isSpectator()) return;
        BlockPos pos = getFocusedBlock();
        if (pos == null) return;
        boolean showOverlay = faceVisible.isToggled();
        boolean showOutline = outlineVisible.isToggled();
        if (!showOverlay && !showOutline) return;

        renderCustomBlockOverlay(pos, null, showOverlay, showOutline);
    }

    private void renderCustomBlockOverlay(BlockPos pos, EnumFacing side, boolean showOverlay, boolean showOutline) {
        AxisAlignedBB box = BlockUtils.getBlockSelectionBox(pos);
        if (box == null) return;
        box = box.expand(PADDING, PADDING, PADDING);
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;

        int overlayStart = 0, overlayEnd = 0, outlineStart = 0, outlineEnd = 0;
        if (showOverlay) {
            overlayStart = computeStart((int) faceColorMode.getInput(), faceColor, faceColor2, faceFadeSpeed.getInput(), faceChromaSpeed.getInput());
            overlayEnd = computeEnd((int) faceColorMode.getInput(), faceColor, faceColor2, faceFadeSpeed.getInput(), faceChromaSpeed.getInput());
        }
        if (showOutline) {
            outlineStart = computeStart((int) outlineColorMode.getInput(), outlineColor, outlineColor2, outlineFadeSpeed.getInput(), outlineChromaSpeed.getInput());
            outlineEnd = computeEnd((int) outlineColorMode.getInput(), outlineColor, outlineColor2, outlineFadeSpeed.getInput(), outlineChromaSpeed.getInput());
        }

        GL11.glPushMatrix();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        boolean depthDisabled = depthless.isToggled();
        if (depthDisabled) GlStateManager.disableDepth();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        if (showOutline) GL11.glLineWidth((float) thickness.getInput());
        GL11.glShadeModel(GL11.GL_SMOOTH);

        try {
            drawOverlayGeometry(mc, pos, box, side, vx, vy, vz, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline);
        } finally {
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glLineWidth(2.0f);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            if (depthDisabled) GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GL11.glPopMatrix();
        }
    }

    public static void renderBlockOutline(BlockPos pos, int outlineArgbStart, int outlineArgbEnd, float lineWidth, boolean depthless) {
        Minecraft m = Minecraft.getMinecraft();
        if (m.theWorld == null || pos == null) {
            return;
        }
        AxisAlignedBB box = BlockUtils.getBlockSelectionBox(pos);
        if (box == null) {
            return;
        }
        box = box.expand(PADDING, PADDING, PADDING);
        double vx = m.getRenderManager().viewerPosX, vy = m.getRenderManager().viewerPosY, vz = m.getRenderManager().viewerPosZ;

        GL11.glPushMatrix();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        if (depthless) {
            GlStateManager.disableDepth();
        }
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(lineWidth);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        try {
            drawOverlayGeometry(m, pos, box, null, vx, vy, vz, 0, 0, outlineArgbStart, outlineArgbEnd, false, true);
        } finally {
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glLineWidth(2.0f);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            if (depthless) {
                GlStateManager.enableDepth();
            }
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GL11.glPopMatrix();
        }
    }

    private static void drawOverlayGeometry(Minecraft mc, BlockPos pos, AxisAlignedBB paddedWorldBox, EnumFacing side, double vx, double vy, double vz, int overlayStart, int overlayEnd, int outlineStart, int outlineEnd, boolean showOverlay, boolean showOutline) {
        AxisAlignedBB renderBox = paddedWorldBox.offset(-vx, -vy, -vz);
        IBlockState state = mc.theWorld.getBlockState(pos);
        if (state.getBlock() instanceof BlockStairs) {
            StairsUtils.drawStairs(pos, state, paddedWorldBox, side, vx, vy, vz, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline, BlockOverlay::drawFace);
        } else if (side != null) {
            drawFace(renderBox, side, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline);
        } else {
            for (EnumFacing face : EnumFacing.values()) {
                drawFace(renderBox, face, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline);
            }
        }
    }

    private BlockPos getFocusedBlock() {
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        if (pos == null) return null;
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block == Blocks.air) return null;
        if (block == Blocks.barrier && !barriers.isToggled()) return null;
        if (hidePlants.isToggled() && (block instanceof BlockTallGrass || block instanceof BlockFlower || block instanceof BlockDeadBush || block instanceof BlockDoublePlant)) return null;
        return pos;
    }

    private static int interpolate(int c1, int c2, double pct) {
        Color a = new Color(c1, true), b = new Color(c2, true);
        double inv = 1.0 - pct;
        return new Color((int)(a.getRed() * pct + b.getRed() * inv), (int)(a.getGreen() * pct + b.getGreen() * inv), (int)(a.getBlue() * pct + b.getBlue() * inv), (int)(a.getAlpha() * pct + b.getAlpha() * inv)).getRGB();
    }

    private static int computeStart(int colorMode, ColorSetting color1, ColorSetting color2, double fadeSpeed, double chromaSpeed) {
        switch (colorMode) {
            case 0:
                return Utils.mergeAlpha(ArrayListModule.getHudColor(0.0D), color1.getAlpha());
            case 1:
            case 2:
                return color1.getColor();
            case 3:
                double pct = Math.sin(System.currentTimeMillis() / (1100.0 - fadeSpeed * 100.0)) * 0.5 + 0.5;
                return interpolate(color1.getColor(), color2.getColor(), pct);
            case 4:
                return Utils.mergeAlpha(Utils.getChroma((long) chromaSpeed), color1.getAlpha());
            default:
                return Utils.mergeAlpha(ArrayListModule.getHudColor(0.0D), color1.getAlpha());
        }
    }

    private static int computeEnd(int colorMode, ColorSetting color1, ColorSetting color2, double fadeSpeed, double chromaSpeed) {
        switch (colorMode) {
            case 2:
                return color2.getColor();
            case 3:
                double pct = Math.sin((System.currentTimeMillis() + 500L) / (1100.0 - fadeSpeed * 100.0)) * 0.5 + 0.5;
                return interpolate(color1.getColor(), color2.getColor(), pct);
            default:
                return computeStart(colorMode, color1, color2, fadeSpeed, chromaSpeed);
        }
    }

    private static void drawFace(AxisAlignedBB box, EnumFacing face, int os, int oe, int ls, int le, boolean overlay, boolean outline) {
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer wr = ts.getWorldRenderer();
        if (overlay) {
            wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
            addFaceVertices(wr, face, box, os, oe);
            ts.draw();
        }
        if (outline) {
            wr.begin(1, DefaultVertexFormats.POSITION_COLOR);
            addFaceOutlineVertices(wr, face, box, ls, le);
            ts.draw();
        }
    }

    private static void addFaceOutlineVertices(WorldRenderer wr, EnumFacing face, AxisAlignedBB box, int start, int end) {
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
        switch (face) {
            case UP:
                v(wr, minX, maxY, maxZ, start); v(wr, maxX, maxY, maxZ, end);
                v(wr, maxX, maxY, maxZ, end);   v(wr, maxX, maxY, minZ, start);
                v(wr, maxX, maxY, minZ, start); v(wr, minX, maxY, minZ, end);
                v(wr, minX, maxY, minZ, end);   v(wr, minX, maxY, maxZ, start);
                break;
            case DOWN:
                v(wr, maxX, minY, maxZ, start); v(wr, minX, minY, maxZ, end);
                v(wr, minX, minY, maxZ, end);   v(wr, minX, minY, minZ, start);
                v(wr, minX, minY, minZ, start); v(wr, maxX, minY, minZ, end);
                v(wr, maxX, minY, minZ, end);   v(wr, maxX, minY, maxZ, start);
                break;
            case NORTH:
                v(wr, maxX, maxY, minZ, start); v(wr, maxX, minY, minZ, end);
                v(wr, maxX, minY, minZ, end);   v(wr, minX, minY, minZ, start);
                v(wr, minX, minY, minZ, start); v(wr, minX, maxY, minZ, end);
                v(wr, minX, maxY, minZ, end);   v(wr, maxX, maxY, minZ, start);
                break;
            case SOUTH:
                v(wr, minX, maxY, maxZ, start); v(wr, minX, minY, maxZ, end);
                v(wr, minX, minY, maxZ, end);   v(wr, maxX, minY, maxZ, start);
                v(wr, maxX, minY, maxZ, start); v(wr, maxX, maxY, maxZ, end);
                v(wr, maxX, maxY, maxZ, end);   v(wr, minX, maxY, maxZ, start);
                break;
            case EAST:
                v(wr, maxX, maxY, minZ, start); v(wr, maxX, maxY, maxZ, end);
                v(wr, maxX, maxY, maxZ, end);   v(wr, maxX, minY, maxZ, start);
                v(wr, maxX, minY, maxZ, start); v(wr, maxX, minY, minZ, end);
                v(wr, maxX, minY, minZ, end);   v(wr, maxX, maxY, minZ, start);
                break;
            case WEST:
                v(wr, minX, maxY, maxZ, start); v(wr, minX, maxY, minZ, end);
                v(wr, minX, maxY, minZ, end);   v(wr, minX, minY, minZ, start);
                v(wr, minX, minY, minZ, start); v(wr, minX, minY, maxZ, end);
                v(wr, minX, minY, maxZ, end);   v(wr, minX, maxY, maxZ, start);
                break;
        }
    }

    private static void v(WorldRenderer wr, double x, double y, double z, int color) {
        wr.pos(x, y, z).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF).endVertex();
    }

    private static void addFaceVertices(WorldRenderer wr, EnumFacing face, AxisAlignedBB box, int start, int end) {
        switch (face) {
            case UP:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                break;
            case DOWN:
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                break;
            case NORTH:
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                break;
            case SOUTH:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                break;
            case EAST:
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                break;
            case WEST:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
                break;
        }
    }
}
