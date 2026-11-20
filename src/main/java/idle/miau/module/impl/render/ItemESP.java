package idle.miau.module.impl.render;

import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

public class ItemESP extends Module {
    private final SliderSetting opacity;
    private final ButtonSetting outline;
    private final ButtonSetting itemCount;
    private final ButtonSetting autoScale;
    private final ButtonSetting emeralds;
    private final ButtonSetting diamonds;
    private final ButtonSetting gold;
    private final ButtonSetting iron;

    public ItemESP() {
        super("ItemESP", category.render);
        setDescription("Highlights dropped items.");
        this.registerSetting(opacity = new SliderSetting("opacity", "%", 25.0, 0.0, 100.0, 1.0));
        this.registerSetting(outline = new ButtonSetting("outline", false));
        this.registerSetting(itemCount = new ButtonSetting("item-count", true));
        this.registerSetting(autoScale = new ButtonSetting("auto-scale", true));
        this.registerSetting(emeralds = new ButtonSetting("emeralds", true));
        this.registerSetting(diamonds = new ButtonSetting("diamonds", true));
        this.registerSetting(gold = new ButtonSetting("gold", true, "Render gold"));
        this.registerSetting(iron = new ButtonSetting("iron", true, "Render iron"));
    }

    @Subscribe
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!Utils.nullCheck() || mc.theWorld == null || mc.getRenderViewEntity() == null) {
            return;
        }

        Map<ItemGroup, Integer> groups = collectGroups(event.partialTicks);
        List<Map.Entry<ItemGroup, Integer>> sortedGroups = new ArrayList<>(groups.entrySet());
        sortedGroups.sort(Comparator.comparingInt(entry -> itemPriority(entry.getKey().itemId)));
        for (Map.Entry<ItemGroup, Integer> entry : sortedGroups) {
            renderGroup(entry.getKey(), entry.getValue());
        }
    }

    private Map<ItemGroup, Integer> collectGroups(float partialTicks) {
        Map<ItemGroup, Integer> groups = new LinkedHashMap<>();
        for (Entity entity : loadedEntitiesFarthestFirst()) {
            if (!(entity instanceof EntityItem)
                    || entity.ticksExisted < 3
                    || (!entity.ignoreFrustumCheck
                    && !RenderUtils.isInViewFrustum(
                    entity.getEntityBoundingBox().expand(0.125, 0.125, 0.125)))) {
                continue;
            }

            EntityItem entityItem = (EntityItem) entity;
            ItemStack stack = entityItem.getEntityItem();
            if (stack == null || stack.stackSize <= 0 || !shouldHighlight(stack.getItem())) {
                continue;
            }

            double x = RenderUtils.lerpDouble(entityItem.posX, entityItem.lastTickPosX, partialTicks);
            double y = RenderUtils.lerpDouble(entityItem.posY, entityItem.lastTickPosY, partialTicks);
            double z = RenderUtils.lerpDouble(entityItem.posZ, entityItem.lastTickPosZ, partialTicks);
            ItemGroup group = new ItemGroup(Item.getIdFromItem(stack.getItem()), x, y, z);
            groups.merge(group, stack.stackSize, Integer::sum);
        }
        return groups;
    }

    private List<Entity> loadedEntitiesFarthestFirst() {
        List<Entity> entities = new ArrayList<>(mc.theWorld.loadedEntityList);
        entities.sort((first, second) -> {
            double firstDistance = mc.getRenderManager().getDistanceToCamera(
                    first.posX, first.posY, first.posZ);
            double secondDistance = mc.getRenderManager().getDistanceToCamera(
                    second.posX, second.posY, second.posZ);
            int distanceOrder = Double.compare(secondDistance, firstDistance);
            return distanceOrder != 0
                    ? distanceOrder
                    : first.getUniqueID().toString().compareTo(second.getUniqueID().toString());
        });
        return entities;
    }

    private boolean shouldHighlight(Item item) {
        ItemKind kind = itemKind(item);
        if (kind == null) {
            return false;
        }
        switch (kind) {
            case EMERALD:
                return emeralds.isToggled();
            case DIAMOND:
                return diamonds.isToggled();
            case GOLD:
                return gold.isToggled();
            case IRON:
                return iron.isToggled();
            default:
                return false;
        }
    }

    private void renderGroup(ItemGroup group, int count) {
        int color = itemColor(group.itemId);
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        double x = group.x - mc.getRenderManager().viewerPosX;
        double y = group.y - mc.getRenderManager().viewerPosY;
        double z = group.z - mc.getRenderManager().viewerPosZ;
        double distance = mc.getRenderViewEntity().getDistance(group.x, group.y, group.z);
        double scale = boxScale(distance, autoScale.isToggled());
        AxisAlignedBB box = new AxisAlignedBB(
                x - scale * 0.5,
                y,
                z - scale * 0.5,
                x + scale * 0.5,
                y + scale,
                z + scale * 0.5);

        RenderUtils.enableRenderState();
        try {
            int alpha = (int) (opacity.getInput() * 255.0 / 100.0);
            if (alpha > 0) {
                RenderUtils.drawFilledBox(box, red, green, blue, alpha);
            }
            if (outline.isToggled()) {
                drawOutline(box, red, green, blue);
            }
        } finally {
            GlStateManager.resetColor();
            RenderUtils.disableRenderState();
        }

        if (itemCount.isToggled()) {
            drawCount(count, x, y, z, distance, scale);
        }
    }

    private void drawOutline(AxisAlignedBB box, int red, int green, int blue) {
        GL11.glLineWidth(1.5F);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        try {
            RenderGlobal.drawOutlinedBoundingBox(box, red, green, blue, 255);
        } finally {
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(1.0F);
        }
    }

    private void drawCount(
            int count,
            double x,
            double y,
            double z,
            double distance,
            double boxScale) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y + boxScale * 0.5, z);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            float flip = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
            GlStateManager.rotate(mc.getRenderManager().playerViewX, flip, 0.0F, 0.0F);
            double scale = fontScale(distance, autoScale.isToggled());
            GlStateManager.scale(scale, scale, 1.0);
            GlStateManager.disableDepth();
            String text = Integer.toString(count);
            float textX = -mc.fontRendererObj.getStringWidth(text) * 0.5F + 0.5F;
            float textY = -mc.fontRendererObj.FONT_HEIGHT * 0.5F + 0.5F;
            drawOutlinedString(text, textX, textY);
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.resetColor();
            GlStateManager.popMatrix();
        }
    }

    private void drawOutlinedString(String text, float x, float y) {
        mc.fontRendererObj.drawString(text, x + 1.0F, y, 0, false);
        mc.fontRendererObj.drawString(text, x - 1.0F, y, 0, false);
        mc.fontRendererObj.drawString(text, x, y + 1.0F, 0, false);
        mc.fontRendererObj.drawString(text, x, y - 1.0F, 0, false);
        mc.fontRendererObj.drawString(text, x, y, 0xFFFFFFFF, false);
    }

    static double boxScale(double distance, boolean autoScale) {
        double scaledDistance = Math.max(6.0, autoScale ? distance : 6.0);
        return 0.5 + 0.375 * ((scaledDistance - 6.0) / 28.0);
    }

    static double fontScale(double distance, boolean autoScale) {
        double scaledDistance = Math.max(6.0, autoScale ? distance : 6.0);
        return -0.04375 - 0.0328125 * ((scaledDistance - 6.0) / 28.0);
    }

    static int itemPriority(int itemId) {
        ItemKind kind = itemKind(Item.getItemById(itemId));
        return kind == null ? 0 : kind.priority;
    }

    static int itemColor(int itemId) {
        ItemKind kind = itemKind(Item.getItemById(itemId));
        return kind == null ? 0xFFAAAAAA : kind.color;
    }

    static ItemKind itemKind(Item item) {
        if (item == null) {
            return null;
        }
        Block block = Block.getBlockFromItem(item);
        if (item == Items.emerald
                || block == Blocks.emerald_block
                || block == Blocks.emerald_ore) {
            return ItemKind.EMERALD;
        }
        if (item == Items.diamond
                || item == Items.diamond_sword
                || item == Items.diamond_pickaxe
                || item == Items.diamond_shovel
                || item == Items.diamond_axe
                || item == Items.diamond_hoe
                || item == Items.diamond_helmet
                || item == Items.diamond_chestplate
                || item == Items.diamond_leggings
                || item == Items.diamond_boots
                || block == Blocks.diamond_block
                || block == Blocks.diamond_ore) {
            return ItemKind.DIAMOND;
        }
        if (item == Items.gold_ingot
                || item == Items.gold_nugget
                || item == Items.golden_apple
                || block == Blocks.gold_block
                || block == Blocks.gold_ore) {
            return ItemKind.GOLD;
        }
        if (item == Items.iron_ingot
                || block == Blocks.iron_block
                || block == Blocks.iron_ore) {
            return ItemKind.IRON;
        }
        return null;
    }

    enum ItemKind {
        IRON(1, 0xFFFFFFFF),
        GOLD(2, 0xFFFFFF55),
        DIAMOND(3, 0xFF55FFFF),
        EMERALD(4, 0xFF55FF55);

        private final int priority;
        private final int color;

        ItemKind(int priority, int color) {
            this.priority = priority;
            this.color = color;
        }
    }

    static final class ItemGroup {
        private final int hashCode;
        final int itemId;
        final double x;
        final double y;
        final double z;

        ItemGroup(int itemId, double x, double y, double z) {
            this.itemId = itemId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hashCode = Objects.hash(itemId, (int) x, (int) y, (int) z);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof ItemGroup)) {
                return false;
            }
            ItemGroup other = (ItemGroup) object;
            return itemId == other.itemId
                    && (int) x == (int) other.x
                    && (int) y == (int) other.y
                    && (int) z == (int) other.z;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
