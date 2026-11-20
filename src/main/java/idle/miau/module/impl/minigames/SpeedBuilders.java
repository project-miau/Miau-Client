package idle.miau.module.impl.minigames;

import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.module.impl.render.HudStyle;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.Miau;
import idle.miau.event.PostConfigLoadEvent;
import idle.miau.mcp.accessor.IAccessorMinecraft;
import idle.miau.mcp.accessor.IAccessorPlayerControllerMP;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.ClientChatReceivedEvent;
import idle.miau.mcp.event.client.MouseEvent;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.*;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SpeedBuilders extends Module {

    private static final float DEFAULT_HUD_X = -5.0F;
    private static final float DEFAULT_HUD_Y = 110.0F;

    private SliderSetting placeDelay;
    private ButtonSetting antiMiss;
    private ButtonSetting autoBuild;
    private ButtonSetting autoSwap;
    private ButtonSetting hoverPlace;
    private ButtonSetting infoHud;
    private ButtonSetting renderBlocks;
    private SliderSetting renderMode;
    private ButtonSetting renderOnlyPlaceable;

    private ConcurrentHashMap<BlockPos, BuildBlockInfo> buildInfo = new ConcurrentHashMap<>();

    private BlockPos platformCenter;
    private boolean listenForPacket;
    private AutoPlacementTarget autoTarget;
    private boolean autoRotationOwned;
    private boolean autoSlotSwapped;
    private int previousAutoSlot = -1;
    private boolean renderOptionsVisible;

    public List<BlockPos> PLATFORM_POSITIONS = Arrays.asList(
            new BlockPos(45, 71, -18),
            new BlockPos(-16, 71, 45),
            new BlockPos(18, 71, 45),
            new BlockPos(45, 71, 16),
            new BlockPos(-18, 71, -45),
            new BlockPos(-45, 71, -16),
            new BlockPos(-45, 71, 18),
            new BlockPos(16, 71, -45)
    );

    private boolean doneCollecting;
    private double blockCount;
    private int lastPlaceTick = 0;
    private boolean eliminated;
    private float hudX = DEFAULT_HUD_X;
    private float hudY = DEFAULT_HUD_Y;
    private boolean hudDragging;
    private float hudDragOffsetX;
    private float hudDragOffsetY;

    public SpeedBuilders() {
        super("SpeedBuilders", category.minigames);
        setDescription("Automate finish the SpeedBuilders.");
        this.registerSetting(new DescriptionSetting("Middle click to toggle AutoBuild."));
        this.registerSetting(placeDelay = new SliderSetting("Place delay", " tick", 0.5, 0, 10, 0.5));
        this.registerSetting(antiMiss = new ButtonSetting("Anti miss", false));
        this.registerSetting(autoBuild = new ButtonSetting("AutoBuild", false,
                "setting.autoPlace", "Auto place", "autoPlace"));
        this.registerSetting(autoSwap = new ButtonSetting("Auto swap", true));
        this.registerSetting(hoverPlace = new ButtonSetting("Hover place", true));
        this.registerSetting(infoHud = new ButtonSetting("Info HUD", true));
        this.registerSetting(renderBlocks = new ButtonSetting("Render blocks", true));
        this.registerSetting(renderMode = new SliderSetting("Render mode", 0,
                new String[]{"Overlay", "Block model"}));
        this.registerSetting(renderOnlyPlaceable = new ButtonSetting("Render only placeable", false));
        updateSettingVisibility();
    }

    @Override
    public void onDisable() {
        lastPlaceTick = 0;
        hudDragging = false;
        clearAutoBuildState();
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent e) {
        int gameStatus = getGameStatus();
        if (gameStatus == -1 || platformCenter == null) {
            clearAutoBuildState();
            return;
        }
        if (gameStatus == 4) {
            doneCollecting = true;
        }
        if (gameStatus == 1 && !doneCollecting) {
            buildInfo = getBuildInfo(platformCenter);
            if (!buildInfo.isEmpty()) {
                blockCount = buildInfo.size();
            }
        }
        if (gameStatus != 2) {
            clearAutoTarget();
            return;
        }

        doneCollecting = false;
        for (Map.Entry<BlockPos, BuildBlockInfo> entry : buildInfo.entrySet()) {
            IBlockState currentState = mc.theWorld.getBlockState(entry.getKey());
            IBlockState requiredState = entry.getValue().requiredState;

            if (currentState == null || requiredState == null) {
                entry.getValue().isPlaced = false;
                continue;
            }

            entry.getValue().isPlaced = matchesRequiredState(currentState, requiredState);
        }
        if (autoTarget != null && autoTarget.infoIsPlaced(buildInfo)) {
            clearAutoTarget();
        }

        if (autoEnabled()) {
            performAutoPlacement();
            return;
        }

        clearAutoTarget();
        if (getLookInfo() != null) {
            MovingObjectPosition mop = getLookInfo();
            if (mop.sideHit != null) {
                BlockPos supportPos = mop.getBlockPos();
                BlockPos targetPos = supportPos.offset(mop.sideHit);

                BuildBlockInfo info = buildInfo.get(targetPos);
                if (info != null && !info.isPlaced) {
                    if (autoSwap.isToggled()) {
                        int requiredMeta = info.requiredState.getBlock().getMetaFromState(info.requiredState);
                        int slot = getSlot(info.requiredState.getBlock(), requiredMeta);
                        if (slot != -1 && slot != mc.thePlayer.inventory.currentItem) {
                            mc.thePlayer.inventory.currentItem = slot;
                            syncCurrentHotbarSlot();
                        }
                    }
                    if (hoverPlace.isToggled() && holdingSameBlock(info.requiredState)) {
                        if (lastPlaceTick++ < placeDelay.getInput()) {
                            return;
                        }
                        ItemStack held = mc.thePlayer.getHeldItem();
                        if (BlockUtils.canPlaceBlockOnSide(held, supportPos, mop.sideHit)
                                && correctPlaceState(info.requiredState, supportPos, mop.sideHit,
                                mop.hitVec, held)
                                && ((IAccessorMinecraft) mc).getRightClickDelayTimer() <= 0
                                && mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                                held, supportPos, mop.sideHit, mop.hitVec)) {
                            mc.thePlayer.swingItem();
                            ((IAccessorMinecraft) mc).setRightClickDelayTimer(
                                    Math.max(1, (int) placeDelay.getInput()));
                        }
                        lastPlaceTick = 0;
                    }
                }
            }
        }
    }

    @Subscribe(priority = Priority.LOW)
    public void onClientRotation(ClientRotationEvent event) {
        if (!autoEnabled() || !Utils.nullCheck() || getGameStatus() != 2
                || platformCenter == null || eliminated || mc.currentScreen != null
                || event.isRotationClaimed()) {
            clearAutoBuildState();
            return;
        }

        AutoPlacementTarget target = autoTarget;
        ItemStack targetStack = target == null ? null : stackForSlot(target.slot);
        if (target == null || !isValidAutoTarget(target, targetStack)) {
            target = findAutoPlacementTarget();
        }
        if (target == null) {
            clearAutoTarget();
            return;
        }

        if (autoSwap.isToggled() && target.slot != mc.thePlayer.inventory.currentItem) {
            if (!autoSlotSwapped) {
                previousAutoSlot = mc.thePlayer.inventory.currentItem;
                autoSlotSwapped = true;
            }
            mc.thePlayer.inventory.currentItem = target.slot;
            syncCurrentHotbarSlot();
        }

        float baseYaw = RotationUtils.serverRotations[0];
        float basePitch = RotationUtils.serverRotations[1];
        float[] applied = snapRotationToSensitivity(target.aimYaw, target.aimPitch,
                baseYaw, basePitch);

        autoTarget = target;
        autoTarget.appliedYaw = applied[0];
        autoTarget.appliedPitch = applied[1];
        autoRotationOwned = true;
        event.setYaw(applied[0]);
        event.setPitch(applied[1]);

        MovingObjectPosition trace = RotationUtils.rayTraceCustom(
                mc.playerController.getBlockReachDistance(), applied[0], applied[1]);
        autoTarget.rayMatched = matchesPlacementTrace(trace, target);
        if (autoTarget.rayMatched) {
            autoTarget.hitVec = trace.hitVec;
        }
    }

    @Subscribe
    public void onMouse(MouseEvent e) {
        if (!e.buttonstate || !Utils.nullCheck() || mc.currentScreen != null) {
            return;
        }
        if (e.button == 1 && antiMiss.isToggled() && getLookInfo() != null && getGameStatus() == 2) {
            MovingObjectPosition mop = getLookInfo();
            if (mop.sideHit != null) {
                BlockPos supportPos = mop.getBlockPos();
                BlockPos targetPos = supportPos.offset(mop.sideHit);

                BuildBlockInfo info = buildInfo.get(targetPos);
                if (info == null || !holdingSameBlock(info.requiredState)
                        || !correctPlaceState(info.requiredState, supportPos, mop.sideHit, mop.hitVec,
                        mc.thePlayer.getHeldItem())) {
                    e.setCanceled(true);
                }
            }
        }
        else if (e.button == 2) {
            autoBuild.toggle();
        }
    }

    @Subscribe
    public void onSettingTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            updateSettingVisibility();
        }
    }

    @Subscribe
    public void onPostConfigLoad(PostConfigLoadEvent event) {
        updateSettingVisibility();
    }

    private void updateSettingVisibility() {
        boolean showRenderOptions = renderBlocks != null && renderBlocks.isToggled();
        if (showRenderOptions == renderOptionsVisible) {
            return;
        }
        renderOptionsVisible = showRenderOptions;
        if (renderMode != null) {
            renderMode.setVisible(showRenderOptions, this);
        }
        if (renderOnlyPlaceable != null) {
            renderOnlyPlaceable.setVisible(showRenderOptions, this);
        }
    }

    boolean areRenderOptionsVisible() {
        return renderOptionsVisible && renderMode.visible && renderOnlyPlaceable.visible;
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent ev) {
        if (!Utils.nullCheck() || getGameStatus() != 2 || !renderBlocks.isToggled()) {
            return;
        }

        for (Map.Entry<BlockPos, BuildBlockInfo> buildData : buildInfo.entrySet()) {
            BuildBlockInfo info = buildData.getValue();
            if (info == null || info.isPlaced || info.requiredState == null) {
                continue;
            }
            BlockPos pos = buildData.getKey();
            if (renderOnlyPlaceable.isToggled()) {
                int slot = getUsableSlot(info.requiredState);
                if (slot < 0 || findPlacementFace(pos, info.requiredState, slot) == null) {
                    continue;
                }
            }
            if ((int) renderMode.getInput() == 0) {
                RenderUtils.renderBlockModel(info.requiredState, pos.getX(), pos.getY(), pos.getZ(),
                        new Color(31, 255, 22, 44).getRGB());
            } else {
                RenderUtils.renderTranslucentBlockModel(info.requiredState, pos, 0.34F);
            }
        }
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        boolean editingHud = mc.currentScreen instanceof GuiChat;
        if (!editingHud) {
            hudDragging = false;
        }
        if (!Utils.nullCheck() || (mc.currentScreen != null && !editingHud)) {
            return;
        }
        int gameStatus = getGameStatus();
        if (Miau.DEBUG) {
            renderDebugMenu(gameStatus);
        }
        if (infoHud.isToggled()) {
            List<String> lines = new ArrayList<>();
            lines.add("§6Speed Builders");
            lines.add("§7Status: §b" + getGameStatusName(gameStatus));
            if (gameStatus == 2 && !eliminated) {
                double placedCount = 0;
                for (BuildBlockInfo info : buildInfo.values()) {
                    if (info.isPlaced) placedCount++;
                }
                double percentage = 0.0;
                if (buildInfo.isEmpty()) {
                    percentage = 100.0;
                    placedCount = blockCount;
                } else if (blockCount > 0) {
                    percentage = ((placedCount) / blockCount) * 100.0;
                }
                lines.add("§7Progress: §b" + (int) placedCount + "§7/§b" + (int) blockCount + " " + Math.round(percentage) + "%");
            }
            lines.add("§7Auto: " + (autoEnabled() ? "§aENABLED" : "§cDISABLED"));

            MiauFontRenderer font = HudStyle.font();
            int padding = Math.max(2, Math.round(HudStyle.paddingX()));
            int maxWidth = 0;
            for (String line : lines) {
                int lineWidth = font.getStringWidth(line);
                if (lineWidth > maxWidth) {
                    maxWidth = lineWidth;
                }
            }

            int lineHeight = font.getFontHeight();
            int lineSpacing = 3;
            int totalHeight = lines.size() * lineHeight + (lines.size() - 1) * lineSpacing + padding * 2;
            int totalWidth = maxWidth + padding * 2;

            float hudWidth = totalWidth + 7.0F;
            float hudHeight = totalHeight - 2.0F;
            if (editingHud) {
                ScaledResolution resolution = new ScaledResolution(mc);
                float mouseX = Mouse.getX() * resolution.getScaledWidth()
                        / (float) Math.max(1, mc.displayWidth);
                float mouseY = resolution.getScaledHeight()
                        - Mouse.getY() * resolution.getScaledHeight()
                        / (float) Math.max(1, mc.displayHeight) - 1.0F;
                updateHudDrag(true, Mouse.isButtonDown(0), mouseX, mouseY, hudWidth, hudHeight);
            }

            float x = hudX;
            float y = hudY;

            if (editingHud) {
                Gui.drawRect((int) x, (int) y, (int) Math.ceil(x + hudWidth),
                        (int) Math.ceil(y + hudHeight), 0x40FFFFFF);
            }

            float textX = x + padding;
            float textY = y + padding;

            HudStyle.panelWithoutShaders(x, y, hudWidth, hudHeight, () -> {
                for (int i = 0; i < lines.size(); i++) font.drawString(lines.get(i), textX + 5,
                        textY + i * (lineHeight + lineSpacing), 0xFFFFFFFF, false);
            });
        }
    }

    void updateHudDrag(boolean editingHud, boolean mouseDown, float mouseX, float mouseY,
                       float hudWidth, float hudHeight) {
        if (!editingHud) {
            hudDragging = false;
            return;
        }
        if (hudDragging) {
            if (!mouseDown) {
                hudDragging = false;
                return;
            }
            hudX = mouseX - hudDragOffsetX;
            hudY = mouseY - hudDragOffsetY;
            return;
        }
        if (!mouseDown) {
            return;
        }
        boolean hovering = mouseX >= hudX && mouseX <= hudX + hudWidth
                && mouseY >= hudY && mouseY <= hudY + hudHeight;
        if (hovering) {
            hudDragging = true;
            hudDragOffsetX = mouseX - hudX;
            hudDragOffsetY = mouseY - hudY;
        }
    }

    public float getHudX() {
        return hudX;
    }

    public float getHudY() {
        return hudY;
    }

    public void setHudPosition(float x, float y) {
        if (Float.isFinite(x)) {
            hudX = x;
        }
        if (Float.isFinite(y)) {
            hudY = y;
        }
    }

    boolean isHudDragging() {
        return hudDragging;
    }

    private void renderDebugMenu(int gameStatus) {
        List<String> lines = Arrays.asList(
                "§6Debug",
                "§7Status: §b" + getGameStatusName(gameStatus) + " §8(" + gameStatus + ")",
                "§7Eliminated: " + (eliminated ? "§cYES" : "§aNO")
        );

        int padding = 5;
        int lineSpacing = 2;
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.fontRendererObj.getStringWidth(line));
        }

        int lineHeight = mc.fontRendererObj.FONT_HEIGHT;
        int width = maxWidth + padding * 2;
        int height = lines.size() * lineHeight + (lines.size() - 1) * lineSpacing + padding * 2;
        int x = -5;
        int y = 14;

        RenderUtils.drawRoundedRectangle(x, y, x + width, y + height, 7, Utils.mergeAlpha(Color.black.getRGB(), 120));
        for (int i = 0; i < lines.size(); i++) {
            mc.fontRendererObj.drawString(lines.get(i), x + padding + 2, y + padding + i * (lineHeight + lineSpacing), -1, true);
        }
    }

    private String getGameStatusName(int gameStatus) {
        switch (gameStatus) {
            case 1:
            case 4:
                return "Showing";
            case 2:
                return "Building";
            case 3:
                return "Judging";
            case -1:
                return "Disabled";
            default:
                return "Unknown";
        }
    }

    @Subscribe
    public void onEntityJoin(EntityJoinWorldEvent e) {
        if (!Utils.nullCheck() || e.entity == null) {
            return;
        }
        if (e.entity == mc.thePlayer) {
            buildInfo.clear();
            platformCenter = null;
            listenForPacket = false;
            doneCollecting = false;
            eliminated = false;
            clearAutoBuildState();
        }
    }

    void resetRoundState() {
        buildInfo.clear();
        platformCenter = null;
        listenForPacket = true;
        doneCollecting = false;
        blockCount = 0;
        eliminated = false;
        clearAutoBuildState();
    }

    @Subscribe
    public void onChat(ClientChatReceivedEvent e) {
        if (e.type == 2 || !Utils.nullCheck() || getGameStatus() == -1) {
            return;
        }
        String stripped = Utils.stripColor(e.message.getUnformattedText());
        if (stripped.isEmpty()) {
            return;
        }
        if (stripped.contains("Perfectly recreate the build you are shown each") || stripped.contains("The game starts in 1 second!")) {
            resetRoundState();
        }
        if (stripped.startsWith(Utils.getServerName()) && stripped.contains(" got a perfect build in ") && stripped.endsWith("s!")) {
            buildInfo.clear();
            doneCollecting = false;
            clearAutoBuildState();
        }
        if (stripped.startsWith("Player eliminated: " + Utils.getServerName()) && stripped.endsWith("%)")) {
            eliminated = true;
            clearAutoBuildState();
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent e) {
        if (listenForPacket && Utils.nullCheck() && e.getPacket() instanceof S08PacketPlayerPosLook) {
            Vec3 setPos = new Vec3(((S08PacketPlayerPosLook) e.getPacket()).getX(), ((S08PacketPlayerPosLook) e.getPacket()).getY(), ((S08PacketPlayerPosLook) e.getPacket()).getZ());
            if (platformCenter == null) {
                platformCenter = findCenter(setPos);
            }
            listenForPacket = false;
        }
    }

    public int getGameStatus() {
        List<String> sidebar = Utils.getSidebarLines();
        if (sidebar.isEmpty()) {
            return -1;
        }
        if (!Utils.stripColor(sidebar.get(0)).startsWith("BUILD BATTLE")) {
            return -1;
        }
        for (int i = 0; i < sidebar.size() - 1; i++) {
            String currentLine = Utils.stripColor(sidebar.get(i));
            String nextLine = Utils.stripColor(sidebar.get(i + 1));

            if (currentLine.startsWith("Round:")) {
                if (nextLine.startsWith("Starts In: 00:03") && Utils.stripColor(sidebar.get(i + 3)).startsWith("Theme:")) {
                    return 4;
                }
                if (nextLine.startsWith("Starts In:")) {
                    return 1;
                }
                if (nextLine.startsWith("Time Left:")) {
                    return 2;
                }
                if (nextLine.startsWith("Judging:")) {
                    return 3;
                }
            }
        }
        return 0;
    }

    public BlockPos findCenter(Vec3 position) {
        BlockPos closestPos = null;
        double closestDistSq = Double.MAX_VALUE;
        double maxDistance = 30.0;
        double maxDistSq = maxDistance * maxDistance;

        for (BlockPos pos : PLATFORM_POSITIONS) {
            double dx = pos.getX() - position.xCoord;
            double dy = pos.getY() - position.yCoord;
            double dz = pos.getZ() - position.zCoord;
            double distSq = Math.abs(dx * dx + dy * dy + dz * dz);

            if (distSq <= maxDistSq && distSq < closestDistSq) {
                closestDistSq = distSq;
                closestPos = pos;
            }
        }
        return closestPos;
    }

    public ConcurrentHashMap<BlockPos, BuildBlockInfo> getBuildInfo(BlockPos centerPos) {
        ConcurrentHashMap<BlockPos, BuildBlockInfo> blockInfo = new ConcurrentHashMap<>();
        int startX = centerPos.getX() - 3;
        int endX = centerPos.getX() + 3;
        int startZ = centerPos.getZ() - 3;
        int endZ = centerPos.getZ() + 3;

        int startY = centerPos.getY() + 1;
        int endY = startY + 30;

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = startY; y <= endY; y++) {
                    BlockPos currentPos = new BlockPos(x, y, z);
                    IBlockState state = mc.theWorld.getBlockState(currentPos);
                    if (state.getBlock() == Blocks.air) {
                        continue;
                    }
                    blockInfo.put(currentPos, new BuildBlockInfo(state));
                }
            }
        }

        return blockInfo;
    }

    public boolean autoEnabled() {
        return autoBuild.isToggled();
    }

    private boolean matchesRequiredState(IBlockState currentState, IBlockState requiredState) {
        return currentState.equals(requiredState)
                || (requiredState.getBlock() instanceof BlockLeaves
                && currentState.getBlock().equals(requiredState.getBlock()));
    }

    private void performAutoPlacement() {
        if (mc.currentScreen != null || eliminated || mc.thePlayer == null || mc.theWorld == null) {
            clearAutoTarget();
            return;
        }
        if (lastPlaceTick < placeDelay.getInput()) {
            lastPlaceTick++;
            return;
        }
        if (!(mc.playerController.getBlockReachDistance() > 0.0F)) {
            return;
        }
        if (autoTarget == null || !isValidAutoTarget(autoTarget, mc.thePlayer.getHeldItem())) {
            autoTarget = findAutoPlacementTarget();
        }
        if (autoTarget == null || !autoRotationOwned || !autoTarget.rayMatched
                || !isValidAutoTarget(autoTarget, mc.thePlayer.getHeldItem())) {
            return;
        }
        if (((IAccessorMinecraft) mc).getRightClickDelayTimer() > 0) {
            return;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (!holdingSameBlock(autoTarget.requiredState) || !correctPlaceState(
                autoTarget.requiredState, autoTarget.supportPos, autoTarget.face,
                autoTarget.hitVec, held, autoTarget.appliedYaw)) {
            clearAutoTarget();
            return;
        }
        boolean placed;
        syncCurrentHotbarSlot();
        RotationHelper.get().beginSwap(mc.thePlayer, autoTarget.appliedYaw,
                autoTarget.appliedPitch, true);
        try {
            placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held,
                    autoTarget.supportPos, autoTarget.face, autoTarget.hitVec);
        } finally {
            RotationHelper.get().endSwap(mc.thePlayer);
        }
        if (placed) {
            mc.thePlayer.swingItem();
            ((IAccessorMinecraft) mc).setRightClickDelayTimer(Math.max(1, (int) placeDelay.getInput()));
            lastPlaceTick = 0;
            autoTarget = null;
            autoRotationOwned = false;
        }
    }

    private AutoPlacementTarget findAutoPlacementTarget() {
        if (mc.thePlayer == null || mc.theWorld == null || buildInfo.isEmpty()) {
            return null;
        }
        AutoPlacementTarget best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<BlockPos, BuildBlockInfo> entry : buildInfo.entrySet()) {
            BuildBlockInfo info = entry.getValue();
            if (info == null || info.isPlaced || info.requiredState == null) {
                continue;
            }
            int slot = getUsableSlot(info.requiredState);
            if (slot < 0) {
                continue;
            }
            AutoPlacementTarget candidate = findPlacementFace(entry.getKey(), info.requiredState, slot);
            if (candidate == null) {
                continue;
            }
            double distance = candidate.hitVec.distanceTo(mc.thePlayer.getPositionEyes(1.0F));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private AutoPlacementTarget findPlacementFace(BlockPos targetPos, IBlockState requiredState, int slot) {
        if (!BlockUtils.replaceable(targetPos)) {
            return null;
        }
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double centerX = targetPos.getX() + 0.5D;
        double centerY = targetPos.getY() + 0.5D;
        double centerZ = targetPos.getZ() + 0.5D;
        double dx = centerX - eyes.xCoord;
        double dy = centerY - eyes.yCoord;
        double dz = centerZ - eyes.zCoord;
        double coarseReach = mc.playerController.getBlockReachDistance() + 1.0D;
        if (dx * dx + dy * dy + dz * dz > coarseReach * coarseReach) {
            return null;
        }
        AutoPlacementTarget best = null;
        double bestDistance = Double.MAX_VALUE;
        ItemStack placementStack = stackForSlot(slot);
        if (placementStack == null) {
            return null;
        }
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos supportPos = targetPos.offset(face.getOpposite());
            IBlockState supportState = mc.theWorld.getBlockState(supportPos);
            if (supportState == null || supportState.getBlock() == Blocks.air
                    || !BlockUtils.canPlaceBlockOnSide(placementStack, supportPos, face)) {
                continue;
            }
            Vec3 hitVec = placementHitVec(supportPos, face);
            float[] aim = rotationsToPoint(hitVec, RotationUtils.serverRotations[0],
                    RotationUtils.serverRotations[1]);
            if (!correctPlaceState(requiredState, supportPos, face, hitVec,
                    placementStack, aim[0])) {
                continue;
            }
            if (!BlockUtils.canSeeVecBlock(supportPos, mc.thePlayer.getPositionEyes(1.0F), hitVec)) {
                continue;
            }
            double distance = hitVec.distanceTo(mc.thePlayer.getPositionEyes(1.0F));
            if (distance > mc.playerController.getBlockReachDistance() || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = new AutoPlacementTarget(targetPos, supportPos, face, hitVec,
                    requiredState, slot, aim[0], aim[1]);
        }
        return best;
    }

    private ItemStack stackForSlot(int slot) {
        return slot < 0 || slot >= 9 ? null : mc.thePlayer.inventory.mainInventory[slot];
    }

    Vec3 placementHitVec(BlockPos supportPos, EnumFacing face) {
        double inset = 0.15D;
        double x = supportPos.getX() + 0.5D;
        double y = supportPos.getY() + 0.5D;
        double z = supportPos.getZ() + 0.5D;
        if (face == EnumFacing.UP) y = supportPos.getY() + 1.0D - inset;
        else if (face == EnumFacing.DOWN) y = supportPos.getY() + inset;
        else if (face == EnumFacing.NORTH) z = supportPos.getZ() + inset;
        else if (face == EnumFacing.SOUTH) z = supportPos.getZ() + 1.0D - inset;
        else if (face == EnumFacing.WEST) x = supportPos.getX() + inset;
        else if (face == EnumFacing.EAST) x = supportPos.getX() + 1.0D - inset;
        return new Vec3(x, y, z);
    }

    private float[] rotationsToPoint(Vec3 point, float baseYaw, float basePitch) {
        double x = point.xCoord - mc.thePlayer.posX;
        double y = point.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double z = point.zCoord - mc.thePlayer.posZ;
        float yaw = (float) (Math.atan2(z, x) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, Math.sqrt(x * x + z * z)) * 180.0D / Math.PI));
        return new float[]{
                baseYaw + MathHelper.wrapAngleTo180_float(yaw - baseYaw),
                RotationUtils.clampPitch(basePitch + MathHelper.wrapAngleTo180_float(pitch - basePitch))
        };
    }

    private float[] snapRotationToSensitivity(float yaw, float pitch, float baseYaw, float basePitch) {
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float gcd = sensitivity * sensitivity * sensitivity * 8.0F * 0.15F;
        if (gcd <= 0.0F) {
            return new float[]{yaw, RotationUtils.clampPitch(pitch)};
        }
        float snappedYaw = baseYaw + Math.round(MathHelper.wrapAngleTo180_float(yaw - baseYaw) / gcd) * gcd;
        float snappedPitch = basePitch + Math.round((pitch - basePitch) / gcd) * gcd;
        return new float[]{snappedYaw, RotationUtils.clampPitch(snappedPitch)};
    }

    private boolean matchesPlacementTrace(MovingObjectPosition trace, AutoPlacementTarget target) {
        return trace != null && trace.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && target.supportPos.equals(trace.getBlockPos()) && target.face == trace.sideHit;
    }

    private boolean isValidAutoTarget(AutoPlacementTarget target, ItemStack placementStack) {
        if (target == null || target.requiredState == null || target.infoIsPlaced(buildInfo)
                || placementStack == null) {
            return false;
        }
        if (!BlockUtils.replaceable(target.targetPos)
                || !stackMatchesRequiredState(placementStack, target.requiredState)) {
            return false;
        }
        return BlockUtils.canPlaceBlockOnSide(placementStack, target.supportPos, target.face)
                && correctPlaceState(target.requiredState, target.supportPos, target.face,
                target.hitVec, placementStack, target.appliedYaw);
    }

    private void clearAutoTarget() {
        autoTarget = null;
        autoRotationOwned = false;
    }

    private void clearAutoBuildState() {
        clearAutoTarget();
        lastPlaceTick = 0;
        if (autoSlotSwapped && mc.thePlayer != null && previousAutoSlot >= 0 && previousAutoSlot < 9) {
            mc.thePlayer.inventory.currentItem = previousAutoSlot;
        }
        autoSlotSwapped = false;
        previousAutoSlot = -1;
    }

    public boolean holdingSameBlock(IBlockState requiredState) {
        return mc.thePlayer != null && stackMatchesRequiredState(mc.thePlayer.getHeldItem(), requiredState);
    }

    private boolean stackMatchesRequiredState(ItemStack stack, IBlockState requiredState) {
        if (stack == null || requiredState == null || stack.stackSize <= 0) {
            return false;
        }

        Item item = stack.getItem();
        Block requiredBlock = requiredState.getBlock();

        if ((requiredBlock == Blocks.water || requiredBlock == Blocks.flowing_water) && item == Items.water_bucket) {
            return true;
        }

        if (!(item instanceof ItemBlock)) {
            return false;
        }

        Block heldBlock = ((ItemBlock) item).getBlock();
        int heldMeta = stack.getItemDamage();
        int requiredMeta = requiredBlock.getMetaFromState(requiredState);

        if (requiredBlock == Blocks.leaves || requiredBlock == Blocks.leaves2) {
            requiredMeta &= 3;
            heldMeta &= 3;
        }

        if (removeMeta(heldBlock)) {
            heldMeta = 0;
            requiredMeta = 0;
        }

        return heldBlock == requiredBlock && heldMeta == requiredMeta;
    }

    private int getUsableSlot(IBlockState requiredState) {
        if (requiredState == null || mc.thePlayer == null) {
            return -1;
        }
        int currentSlot = mc.thePlayer.inventory.currentItem;
        if (stackMatchesRequiredState(stackForSlot(currentSlot), requiredState)) {
            return currentSlot;
        }
        if (!autoSwap.isToggled()) {
            return -1;
        }
        return getSlot(requiredState.getBlock(),
                requiredState.getBlock().getMetaFromState(requiredState));
    }

    private void syncCurrentHotbarSlot() {
        if (mc.playerController instanceof IAccessorPlayerControllerMP) {
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        }
    }

    public MovingObjectPosition getLookInfo() {
        MovingObjectPosition movingObjectPosition = mc.objectMouseOver;
        if (movingObjectPosition == null || movingObjectPosition.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || movingObjectPosition.getBlockPos() == null) {
            return null;
        }
        return mc.objectMouseOver;
    }

    private int getSlot(Block block, int meta) {
        if (removeMeta(block)) {
            meta = 0;
        }
        if (block == Blocks.leaves || block == Blocks.leaves2) {
            meta &= 3;
        }

        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack != null && itemStack.getItem() instanceof ItemBlock && itemStack.stackSize > 0) {
                Block invBlock = ((ItemBlock) itemStack.getItem()).getBlock();
                int invMeta = itemStack.getItemDamage();

                if (removeMeta(block)) {
                    invMeta = 0;
                }

                if (invBlock == Blocks.leaves || invBlock == Blocks.leaves2) {
                    invMeta &= 3;
                }

                if (invBlock == block && invMeta == meta) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean removeMeta(Block block) {
        return (block instanceof BlockStairs || block instanceof BlockDoublePlant || block instanceof BlockFlower || block instanceof BlockSkull || block instanceof BlockLadder || block instanceof BlockPumpkin || block instanceof BlockCauldron || block instanceof BlockRail || block instanceof BlockRailBase || block instanceof BlockTripWireHook || block instanceof BlockTripWire || block instanceof BlockDispenser || block instanceof BlockDropper || block instanceof BlockHopper || block instanceof BlockTorch || block instanceof BlockButton || block instanceof BlockLever || block instanceof BlockTrapDoor || block instanceof BlockSlab);
    }

    private boolean correctPlaceState(IBlockState requiredState, BlockPos blockPos,
                                      EnumFacing enumFacing, Vec3 hitVec, ItemStack heldItem) {
        return correctPlaceState(requiredState, blockPos, enumFacing, hitVec, heldItem, null);
    }

    private boolean correctPlaceState(IBlockState requiredState, BlockPos blockPos,
                                      EnumFacing enumFacing, Vec3 hitVec, ItemStack heldItem,
                                      Float placementYaw) {
        if (requiredState == null || blockPos == null || enumFacing == null || hitVec == null || heldItem == null || !(heldItem.getItem() instanceof ItemBlock)) {
            return false;
        }

        if (requiredState.getBlock() instanceof BlockLeaves || requiredState.getBlock() instanceof BlockButton) {
            return true;
        }

        ItemBlock itemBlock = (ItemBlock) heldItem.getItem();
        Block block = itemBlock.getBlock();
        int meta = heldItem.getItemDamage();

        Vec3 relativeHitVec = hitVec.subtract(new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
        BlockPos placedPos = BlockUtils.replaceable(blockPos) ? blockPos : blockPos.offset(enumFacing);

        IBlockState simulatedState;
        float savedYaw = mc.thePlayer.rotationYaw;
        try {
            if (placementYaw != null) {
                mc.thePlayer.rotationYaw = placementYaw;
            }
            simulatedState = block.onBlockPlaced(mc.theWorld, placedPos, enumFacing,
                    (float) relativeHitVec.xCoord, (float) relativeHitVec.yCoord,
                    (float) relativeHitVec.zCoord, meta, mc.thePlayer);
        } finally {
            mc.thePlayer.rotationYaw = savedYaw;
        }

        if (simulatedState == null) {
            return false;
        }

        if (simulatedState.getBlock() != requiredState.getBlock()) {
            return false;
        }

        int simulatedMeta = simulatedState.getBlock().getMetaFromState(simulatedState);
        int requiredMeta = requiredState.getBlock().getMetaFromState(requiredState);
        if (simulatedMeta != requiredMeta) {
            return false;
        }
        if (simulatedState.getProperties().containsKey(BlockDirectional.FACING) && requiredState.getProperties().containsKey(BlockDirectional.FACING)) {
            EnumFacing simulatedFacing = simulatedState.getValue(BlockDirectional.FACING);
            EnumFacing requiredFacing = requiredState.getValue(BlockDirectional.FACING);
            if (simulatedFacing != requiredFacing) {
                return false;
            }
        }
        if (simulatedState.getBlock() instanceof BlockStairs && requiredState.getBlock() instanceof BlockStairs) {
            EnumFacing simulatedFacing = simulatedState.getValue(BlockStairs.FACING);
            EnumFacing requiredFacing = requiredState.getValue(BlockStairs.FACING);
            BlockStairs.EnumHalf simulatedHalf = simulatedState.getValue(BlockStairs.HALF);
            BlockStairs.EnumHalf requiredHalf = requiredState.getValue(BlockStairs.HALF);

            if (simulatedFacing != requiredFacing || simulatedHalf != requiredHalf) {
                return false;
            }
        }

        return true;
    }

    class BuildBlockInfo {
        public IBlockState requiredState;
        public boolean isPlaced;

        public BuildBlockInfo(IBlockState state) {
            this.requiredState = state;
            this.isPlaced = false;
        }
    }

    private class AutoPlacementTarget {
        private final BlockPos targetPos;
        private final BlockPos supportPos;
        private final EnumFacing face;
        private Vec3 hitVec;
        private final IBlockState requiredState;
        private final int slot;
        private final float aimYaw;
        private final float aimPitch;
        private float appliedYaw;
        private float appliedPitch;
        private boolean rayMatched;

        private AutoPlacementTarget(BlockPos targetPos, BlockPos supportPos, EnumFacing face,
                                    Vec3 hitVec, IBlockState requiredState, int slot,
                                    float aimYaw, float aimPitch) {
            this.targetPos = targetPos;
            this.supportPos = supportPos;
            this.face = face;
            this.hitVec = hitVec;
            this.requiredState = requiredState;
            this.slot = slot;
            this.aimYaw = aimYaw;
            this.aimPitch = aimPitch;
            this.appliedYaw = aimYaw;
            this.appliedPitch = aimPitch;
        }

        private boolean infoIsPlaced(Map<BlockPos, BuildBlockInfo> info) {
            BuildBlockInfo blockInfo = info.get(targetPos);
            return blockInfo == null || blockInfo.isPlaced;
        }
    }
}
