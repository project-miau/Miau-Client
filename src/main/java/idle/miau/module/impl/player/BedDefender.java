package idle.miau.module.impl.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.MouseEvent;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class BedDefender extends Module {
    private static final double REACH = 4.5D;
    private static final double GRID_INSET = 0.05D;
    private static final double GRID_STEP = 0.2D;
    private static final double GRID_JITTER = 0.2D;
    private static final int GRID_SIZE = (int) Math.round(1.0D / GRID_STEP);
    private static final List<DefensePattern> PATTERNS = loadPatterns();
    private static final String[] PATTERN_NAMES = patternNames();

    private final ButtonSetting onlyTopBeds;
    private final ButtonSetting bedwarsOnly;
    private final SliderSetting delayAfterSwap;
    private final SliderSetting delayAfterAim;
    private final SliderSetting sneakHoldTicks;
    private final SliderSetting fov;
    private final SliderSetting defenseMode;
    private final ButtonSetting debugLogs;
    private final Map<String, Integer> inventoryCache = new HashMap<String, Integer>();

    private DefensePattern defense;
    private BlockPos origin;
    private BlockPos renderTarget;
    private PendingPlacement pendingPlacement;
    private int direction;
    private int stepIndex;
    private int swapTicks;
    private int aimTicks;
    private int sneakTicksRemaining;
    private float serverYaw;
    private float serverPitch;
    private boolean started;
    private boolean forcedSneak;

    public BedDefender() {
        super("BedDefender", category.player);
        setDescription("Automatically Defenses your beds.");
        registerSetting(onlyTopBeds = new ButtonSetting("Only Top Beds", true));
        registerSetting(bedwarsOnly = new ButtonSetting("Bedwars Only", true));
        registerSetting(delayAfterSwap = new SliderSetting(
                "Delay After Swap", " ticks", 0.0D, 0.0D, 10.0D, 1.0D));
        registerSetting(delayAfterAim = new SliderSetting(
                "Delay After Aiming", " ticks", 0.0D, 0.0D, 10.0D, 1.0D));
        registerSetting(sneakHoldTicks = new SliderSetting(
                "Sneak Hold Ticks", " ticks", 5.0D, 0.0D, 20.0D, 1.0D));
        registerSetting(fov = new SliderSetting("FOV", 180.0D, 0.0D, 180.0D, 1.0D));
        registerSetting(defenseMode = new SliderSetting("Defense", 2, PATTERN_NAMES));
        registerSetting(debugLogs = new ButtonSetting("Debug Logs", false));
    }

    @Override
    public void onEnable() {
        defense = null;
        resetRuntimeState(true);
        if (!Utils.nullCheck() || PATTERNS.isEmpty()) {
            disable();
            return;
        }
        int selected = MathHelper.clamp_int((int) defenseMode.getInput(), 0, PATTERNS.size() - 1);
        defense = PATTERNS.get(selected);
        if (!hasAnyRequiredBlock(defense)) {
            debug("No required blocks in hotbar");
            disable();
            return;
        }
        serverYaw = RotationUtils.serverRotations[0];
        serverPitch = RotationUtils.serverRotations[1];
        aimTicks = (int) delayAfterAim.getInput();
    }

    @Override
    public void onDisable() {
        defense = null;
        resetRuntimeState(true);
    }

    @Subscribe
    public void onClientRotation(ClientRotationEvent event) {
        if (!Utils.nullCheck() || defense == null || mc.currentScreen != null) {
            return;
        }
        float[] rotations = updateTargetAndRotation();
        if (rotations == null) {
            return;
        }
        event.setRotation(rotations[0], rotations[1]);
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        updateSneak();
        PendingPlacement placement = pendingPlacement;
        pendingPlacement = null;
        if (placement == null || defense == null || placement.stepIndex != stepIndex
                || !mc.theWorld.isAirBlock(placement.target)) {
            return;
        }
        int slot = findMatchingSlot(placement.block);
        if (slot < 0) {
            disable();
            return;
        }
        mc.thePlayer.inventory.currentItem = slot;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (!matchesBlock(held, placement.block)) {
            return;
        }
        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, placement.support, placement.face, placement.hitVec);
        if (placed) {
            mc.thePlayer.swingItem();
            stepIndex++;
            debug("Placed step " + stepIndex + " of " + defense.steps.size());
        }
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        if (!(event.getPacket() instanceof C03PacketPlayer)) {
            return;
        }
        C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
        if (packet.getRotating()) {
            serverYaw = packet.getYaw();
            serverPitch = packet.getPitch();
        }
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (renderTarget != null) {
            RenderUtils.renderBlock(renderTarget, 0x6600FF00, true, true);
        }
    }

    @Subscribe
    public void onMouse(MouseEvent event) {
        if (event.button == 0 || event.button == 1) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        resetRuntimeState(true);
    }

    private float[] updateTargetAndRotation() {
        if (!started && !initializeBed()) {
            disable();
            return null;
        }
        while (stepIndex < defense.steps.size()) {
            BlockPos target = targetFor(defense.steps.get(stepIndex));
            if (mc.theWorld.isAirBlock(target)) {
                break;
            }
            stepIndex++;
        }
        if (stepIndex >= defense.steps.size()) {
            debug("Defense complete");
            disable();
            return null;
        }

        DefenseStep step = defense.steps.get(stepIndex);
        BlockPos target = targetFor(step);
        renderTarget = target;
        float[] direct = attemptPlacement(serverYaw, serverPitch, target, step.block);
        if (direct != null) {
            return direct;
        }

        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        float maxYaw = (float) fov.getInput();
        float maxPitch = Math.min(maxYaw, 90.0F);
        float cameraYaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw);
        float currentYaw = MathHelper.wrapAngleTo180_float(serverYaw);
        List<RotationCandidate> candidates = new ArrayList<RotationCandidate>();

        for (EnumFacing face : EnumFacing.values()) {
            BlockPos support = target.offset(face.getOpposite());
            Block supportBlock = BlockUtils.getBlock(support);
            if (BlockUtils.replaceable(support)) {
                continue;
            }
            if (onlyTopBeds.isToggled() && supportBlock instanceof BlockBed && face != EnumFacing.UP) {
                continue;
            }
            appendFaceCandidates(candidates, eye, support, face, currentYaw, serverPitch,
                    cameraYaw, mc.thePlayer.rotationPitch, maxYaw, maxPitch);
        }

        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble(candidate -> candidate.cost));
        for (RotationCandidate candidate : candidates) {
            float yaw = unwrapYaw(candidate.yaw, serverYaw);
            float[] result = attemptPlacement(yaw, candidate.pitch, target, step.block);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private boolean initializeBed() {
        int range = bedwarsOnly.isToggled() ? 25 : 16;
        BlockPos bed = findNearestBed(range);
        if (bed == null) {
            debug("No bed in range");
            return false;
        }
        origin = bed;
        direction = directionForMetadata(BlockUtils.getBlock(bed).getMetaFromState(
                mc.theWorld.getBlockState(bed)));
        started = true;
        debug("Using " + defense.name + " at " + bed.getX() + "," + bed.getY() + "," + bed.getZ());
        return true;
    }

    private void appendFaceCandidates(
            List<RotationCandidate> candidates, Vec3 eye, BlockPos support, EnumFacing face,
            float currentYaw, float currentPitch, float cameraYaw, float cameraPitch,
            float maxYaw, float maxPitch) {
        double jitter = GRID_STEP * GRID_JITTER;
        for (int row = 0; row <= GRID_SIZE; row++) {
            boolean leftToRight = (row & 1) == 0;
            double v = clamp01(row * GRID_STEP + randomOffset(jitter));
            for (int column = 0; column <= GRID_SIZE; column++) {
                double raw = clamp01(column * GRID_STEP + randomOffset(jitter));
                double u = leftToRight ? raw : 1.0D - raw;
                Vec3 point = facePoint(support, face, u, v);
                float[] rotations = RotationUtils.getRotationsFromEye(
                        eye, point.xCoord, point.yCoord, point.zCoord);
                float yaw = MathHelper.wrapAngleTo180_float(rotations[0]);
                float pitch = rotations[1];
                if (Math.abs(MathHelper.wrapAngleTo180_float(yaw - cameraYaw)) > maxYaw
                        || Math.abs(pitch - cameraPitch) > maxPitch || Math.abs(pitch) > 90.0F) {
                    continue;
                }
                double cost = Math.abs(MathHelper.wrapAngleTo180_float(yaw - currentYaw))
                        + Math.abs(pitch - currentPitch) + (face == EnumFacing.UP ? -0.25D : 0.0D);
                candidates.add(new RotationCandidate(cost, yaw, pitch));
            }
        }
    }

    private float[] attemptPlacement(float yaw, float pitch, BlockPos target, String requiredBlock) {
        MovingObjectPosition hit = RotationUtils.rayCastBlock(REACH, yaw, pitch);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.getBlockPos() == null || hit.sideHit == null
                || !hit.getBlockPos().offset(hit.sideHit).equals(target)) {
            return null;
        }
        Block supportBlock = BlockUtils.getBlock(hit.getBlockPos());
        if (onlyTopBeds.isToggled() && supportBlock instanceof BlockBed
                && hit.sideHit != EnumFacing.UP) {
            return null;
        }

        int slot = findMatchingSlot(requiredBlock);
        if (slot < 0) {
            debug("Missing " + requiredBlock);
            disable();
            return null;
        }
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            swapTicks = (int) delayAfterSwap.getInput();
        }
        if (swapTicks > 0) {
            swapTicks--;
            return new float[]{yaw, pitch};
        }
        if (supportBlock instanceof BlockBed && !mc.thePlayer.isSneaking()) {
            forceSneak();
            return new float[]{yaw, pitch};
        }
        float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(yaw - serverYaw));
        float pitchDelta = Math.abs(pitch - serverPitch);
        if (aimTicks > 0 || yawDelta > 25.0F || pitchDelta > 25.0F) {
            if (aimTicks > 0) {
                aimTicks--;
            }
            return new float[]{yaw, pitch};
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (!matchesBlock(held, requiredBlock)
                || !BlockUtils.canPlaceBlockOnSide(held, hit.getBlockPos(), hit.sideHit)) {
            return null;
        }
        aimTicks = (int) delayAfterAim.getInput();
        pendingPlacement = new PendingPlacement(
                stepIndex, requiredBlock, target, hit.getBlockPos(), hit.sideHit, hit.hitVec);
        return new float[]{yaw, pitch};
    }

    private void forceSneak() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
        forcedSneak = true;
        sneakTicksRemaining = (int) sneakHoldTicks.getInput();
    }

    private void updateSneak() {
        if (!forcedSneak) {
            return;
        }
        if (sneakTicksRemaining > 0) {
            sneakTicksRemaining--;
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        forcedSneak = false;
    }

    private BlockPos targetFor(DefenseStep step) {
        BlockPos offset = rotateOffset(step.offset, direction);
        return origin.add(offset.getX(), offset.getY(), offset.getZ());
    }

    private boolean hasAnyRequiredBlock(DefensePattern pattern) {
        for (DefenseStep step : pattern.steps) {
            if (findMatchingSlot(step.block) >= 0) {
                return true;
            }
        }
        return false;
    }

    private int findMatchingSlot(String blockName) {
        Integer cached = inventoryCache.get(blockName);
        if (cached != null && cached >= 0 && cached < 9
                && matchesBlock(mc.thePlayer.inventory.mainInventory[cached], blockName)) {
            return cached;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (matchesBlock(mc.thePlayer.inventory.mainInventory[slot], blockName)) {
                inventoryCache.put(blockName, slot);
                return slot;
            }
        }
        inventoryCache.remove(blockName);
        return -1;
    }

    private static boolean matchesBlock(ItemStack stack, String blockName) {
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock
                && ((ItemBlock) stack.getItem()).getBlock() == blockForName(blockName);
    }

    private BlockPos findNearestBed(int range) {
        BlockPos center = mc.thePlayer.getPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minimumY = Math.max(0, center.getY() - range);
        int maximumY = Math.min(255, center.getY() + range);
        for (int x = center.getX() - range; x <= center.getX() + range; x++) {
            for (int z = center.getZ() - range; z <= center.getZ() + range; z++) {
                if (!mc.theWorld.isBlockLoaded(cursor.set(x, center.getY(), z), false)) {
                    continue;
                }
                for (int y = minimumY; y <= maximumY; y++) {
                    cursor.set(x, y, z);
                    if (!(mc.theWorld.getBlockState(cursor).getBlock() instanceof BlockBed)) {
                        continue;
                    }
                    double distance = cursor.distanceSq(center);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = new BlockPos(x, y, z);
                    }
                }
            }
        }
        return nearest;
    }

    private void resetRuntimeState(boolean releaseSneak) {
        if (releaseSneak && forcedSneak && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
        origin = null;
        renderTarget = null;
        pendingPlacement = null;
        direction = 0;
        stepIndex = 0;
        swapTicks = 0;
        aimTicks = 0;
        sneakTicksRemaining = 0;
        started = false;
        forcedSneak = false;
        inventoryCache.clear();
    }

    private void debug(String message) {
        if (debugLogs.isToggled()) {
            Utils.sendMessage("&7[Bed Defender] " + message);
        }
    }

    static int patternCount() {
        return PATTERNS.size();
    }

    static int patternStepCount(String name) {
        for (DefensePattern pattern : PATTERNS) {
            if (pattern.name.equals(name)) {
                return pattern.steps.size();
            }
        }
        return 0;
    }

    static int directionForMetadata(int metadata) {
        switch (metadata & 11) {
            case 0:
            case 10:
                return 0;
            case 2:
            case 8:
                return 1;
            case 1:
            case 11:
                return 2;
            case 3:
            case 9:
                return 3;
            default:
                return 0;
        }
    }

    static BlockPos rotateOffset(BlockPos offset, int direction) {
        int x = offset.getX();
        int y = offset.getY();
        int z = offset.getZ();
        switch (direction) {
            case 1:
                return new BlockPos(-x, y, -z);
            case 2:
                return new BlockPos(-z, y, x);
            case 3:
                return new BlockPos(z, y, -x);
            default:
                return offset;
        }
    }

    private static Vec3 facePoint(BlockPos support, EnumFacing face, double u, double v) {
        double low = GRID_INSET + 0.001D;
        double high = 1.0D - GRID_INSET - 0.001D;
        if (face == EnumFacing.UP || face == EnumFacing.DOWN) {
            return new Vec3(support.getX() + u,
                    support.getY() + (face == EnumFacing.UP ? high : low), support.getZ() + v);
        }
        if (face == EnumFacing.NORTH || face == EnumFacing.SOUTH) {
            return new Vec3(support.getX() + u, support.getY() + v,
                    support.getZ() + (face == EnumFacing.SOUTH ? high : low));
        }
        return new Vec3(support.getX() + (face == EnumFacing.EAST ? high : low),
                support.getY() + v, support.getZ() + u);
    }

    private static float unwrapYaw(float yaw, float previousYaw) {
        return previousYaw + MathHelper.wrapAngleTo180_float(yaw - previousYaw);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double randomOffset(double amount) {
        return ThreadLocalRandom.current().nextDouble(-amount, amount);
    }

    private static String[] patternNames() {
        if (PATTERNS.isEmpty()) {
            return new String[]{"Unavailable"};
        }
        String[] names = new String[PATTERNS.size()];
        for (int index = 0; index < PATTERNS.size(); index++) {
            names[index] = PATTERNS.get(index).name;
        }
        return names;
    }

    private static byte[] decodeBedDefenses(byte[] data) {
        if (data.length >= 4 && (data[0] & 0xFF) == 0xBD && (data[1] & 0xFF) == 0xDF
                && (data[2] & 0xFF) == 0x01 && (data[3] & 0xFF) == 0x00) {
            byte[] decrypted = new byte[data.length - 4];
            long key = 0x4D4941554245444CL;
            for (int i = 0; i < decrypted.length; i++) {
                key = key * 6364136223846793005L + 1442695040888963407L;
                decrypted[i] = (byte) (data[4 + i] ^ (key >>> 32));
            }
            return decrypted;
        }
        return data;
    }

    private static List<DefensePattern> loadPatterns() {
        InputStream stream = BedDefender.class.getResourceAsStream("/assets/miau/bed_defenses.json");
        if (stream == null) {
            return Collections.emptyList();
        }
        try (InputStream input = stream) {
            byte[] decoded = decodeBedDefenses(input.readAllBytes());
            JsonElement parsed = new JsonParser().parse(new String(decoded, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                return Collections.emptyList();
            }
            List<DefensePattern> patterns = new ArrayList<DefensePattern>();
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonArray()) {
                    continue;
                }
                List<DefenseStep> steps = new ArrayList<DefenseStep>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject object = element.getAsJsonObject();
                    String block = string(object, "block");
                    if (!isSupportedBlockName(block)) {
                        continue;
                    }
                    steps.add(new DefenseStep(block, new BlockPos(
                            integer(object, "x"), integer(object, "y"), integer(object, "z"))));
                }
                if (!steps.isEmpty()) {
                    patterns.add(new DefensePattern(entry.getKey(), Collections.unmodifiableList(steps)));
                }
            }
            return Collections.unmodifiableList(patterns);
        }
        catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : 0;
    }

    private static Block blockForName(String name) {
        if ("end_stone".equalsIgnoreCase(name)) {
            return Blocks.end_stone;
        }
        if ("wool".equalsIgnoreCase(name)) {
            return Blocks.wool;
        }
        if ("stained_glass".equalsIgnoreCase(name)) {
            return Blocks.stained_glass;
        }
        if ("planks".equalsIgnoreCase(name)) {
            return Blocks.planks;
        }
        return null;
    }

    private static boolean isSupportedBlockName(String name) {
        return "end_stone".equalsIgnoreCase(name)
                || "wool".equalsIgnoreCase(name)
                || "stained_glass".equalsIgnoreCase(name)
                || "planks".equalsIgnoreCase(name);
    }

    private static final class DefensePattern {
        private final String name;
        private final List<DefenseStep> steps;

        private DefensePattern(String name, List<DefenseStep> steps) {
            this.name = name;
            this.steps = steps;
        }
    }

    private static final class DefenseStep {
        private final String block;
        private final BlockPos offset;

        private DefenseStep(String block, BlockPos offset) {
            this.block = block;
            this.offset = offset;
        }
    }

    private static final class RotationCandidate {
        private final double cost;
        private final float yaw;
        private final float pitch;

        private RotationCandidate(double cost, float yaw, float pitch) {
            this.cost = cost;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class PendingPlacement {
        private final int stepIndex;
        private final String block;
        private final BlockPos target;
        private final BlockPos support;
        private final EnumFacing face;
        private final Vec3 hitVec;

        private PendingPlacement(
                int stepIndex, String block, BlockPos target, BlockPos support,
                EnumFacing face, Vec3 hitVec) {
            this.stepIndex = stepIndex;
            this.block = block;
            this.target = target;
            this.support = support;
            this.face = face;
            this.hitVec = hitVec;
        }
    }
}
