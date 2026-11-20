package idle.miau.baritone;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.command.IBaritoneChatControl;
import baritone.api.event.events.*;
import baritone.api.event.events.type.EventState;
import baritone.api.utils.gui.BaritoneToast;
import baritone.utils.PlayerMovementInput;
import idle.miau.event.JumpEvent;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.PostPlayerInputEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.helper.RotationPriority;
import baritone.api.utils.Rotation;
import net.minecraft.util.MovementInput;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.event.StrafeEvent;
import idle.miau.mcp.McpRuntime;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.MovementInputFromOptions;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class BaritoneBridge {
    private static BaritoneBridge instance;
    private final Minecraft minecraft;
    private final Baritone core;
    private boolean active;
    private WorldClient activeWorld;
    private boolean wasDead;
    private final ConcurrentLinkedQueue<PendingPacket> postPackets = new ConcurrentLinkedQueue<>();

    private BaritoneBridge() {
        minecraft = Minecraft.getMinecraft();
        core = (Baritone) BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    public static synchronized void initialize() {
        if (!moduleEnabled()) return;
        if (instance == null) {
            instance = new BaritoneBridge();
            McpRuntime.EVENTS.register(instance);
            Runtime.getRuntime().addShutdownHook(new Thread(instance::shutdown, "Miau-Baritone-Shutdown"));
        }
        if (instance.active) return;
        instance.active = true;
        instance.worldChanged(instance.minecraft.theWorld, EventState.POST);
    }

    public static void disable() {
        if (instance == null || !instance.active) return;
        instance.active = false;
        cancel();
        instance.postPackets.clear();
        instance.wasDead = false;
        instance.core.getMemoryBehavior().onTick(TickEvent.createNextProvider().apply(EventState.PRE, TickEvent.Type.OUT));
        instance.worldChanged(null, EventState.POST);
        instance.core.bsi = null;
    }

    private static boolean moduleEnabled() {
        return ModuleManager.baritone != null && ModuleManager.baritone.isEnabled();
    }

    public static boolean isCommand(String text) {
        if (text == null) return false;
        if (instance == null) return text.startsWith("#") || text.startsWith(IBaritoneChatControl.FORCE_COMMAND_PREFIX);
        String prefix = BaritoneAPI.getSettings().prefix.value;
        return (BaritoneAPI.getSettings().prefixControl.value && !prefix.isEmpty() && text.startsWith(prefix)) || text.startsWith(IBaritoneChatControl.FORCE_COMMAND_PREFIX);
    }

    public static boolean handleChat(String text) {
        if (text == null) return false;
        boolean prefixed = isCommand(text);
        if (!moduleEnabled()) {
            if (prefixed) {
                idle.miau.utility.Utils.sendMessage("&cEnable the Baritone module first.");
                return true;
            }
            return false;
        }
        initialize();
        if (!prefixed && !BaritoneAPI.getSettings().chatControl.value && !BaritoneAPI.getSettings().chatControlAnyway.value) return false;
        ChatEvent event = new ChatEvent(text);
        instance.core.getGameEventHandler().onSendChatMessage(event);
        if (!prefixed && !event.isCancelled()) return false;
        return true;
    }

    public static void execute(String command) {
        if (!moduleEnabled()) {
            idle.miau.utility.Utils.sendMessage("&cEnable the Baritone module first.");
            return;
        }
        initialize();
        handleChat(IBaritoneChatControl.FORCE_COMMAND_PREFIX + command);
    }

    public static String[] autocomplete(String text) {
        if (ModuleManager.baritone == null || !ModuleManager.baritone.isEnabled()) return new String[0];
        if (!isCommand(text)) return new String[0];
        TabCompleteEvent event = new TabCompleteEvent(text);
        instance.core.getGameEventHandler().onPreTabComplete(event);
        if (event.completions == null) return new String[0];
        String before = text.substring(0, text.lastIndexOf(' ') + 1);
        return Arrays.stream(event.completions).map(value -> before + value).toArray(String[]::new);
    }

    public static String[] suggestArguments(String command) {
        if (instance == null || ModuleManager.baritone == null || !ModuleManager.baritone.isEnabled()) return new String[0];
        String text = IBaritoneChatControl.FORCE_COMMAND_PREFIX + command;
        int start = Math.max(IBaritoneChatControl.FORCE_COMMAND_PREFIX.length(), text.lastIndexOf(' ') + 1);
        return Arrays.stream(autocomplete(text)).map(value -> value.substring(Math.min(start, value.length()))).toArray(String[]::new);
    }

    private boolean enabled() {
        return active && moduleEnabled();
    }

    public static String status() {
        if (instance == null || !instance.enabled()) return "Idle";
        if (instance.core.getBuilderProcess().isActive()) return "Building";
        if (instance.core.getMineProcess().isActive()) return "Mining";
        if (instance.core.getFollowProcess().isActive()) return "Following";
        if (instance.core.getExploreProcess().isActive()) return "Exploring";
        if (instance.core.getFarmProcess().isActive()) return "Farming";
        return instance.core.getPathingBehavior().isPathing() ? "Pathing" : "Idle";
    }

    public static void cancel() {
        if (instance == null) return;
        instance.core.getPathingBehavior().cancelEverything();
        instance.core.getLookBehavior().clearTarget();
        instance.core.getInputOverrideHandler().clearAllKeys();
        instance.core.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
        EntityPlayerSP player = instance.minecraft.thePlayer;
        if (player != null && player.movementInput instanceof PlayerMovementInput) {
            player.movementInput = new MovementInputFromOptions(instance.minecraft.gameSettings);
        }
    }

    public static void beforePlayerUpdate(EntityPlayerSP player) {
        if (instance != null && instance.enabled() && player == instance.minecraft.thePlayer) {
            PlayerUpdateEvent event = new PlayerUpdateEvent(EventState.PRE);
            instance.core.getLookBehavior().setRotationOverridden(!RotationHelper.get().isRotationOwner(instance));
            instance.core.getGameEventHandler().onPlayerUpdate(event);
        }
    }

    public static void afterPlayerUpdate(EntityPlayerSP player) {
        if (instance != null && instance.enabled() && player == instance.minecraft.thePlayer) {
            instance.core.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.POST));
        }
    }

    public static boolean sprintKeyDown(EntityPlayerSP player, boolean physical) {
        if (instance == null || !instance.enabled() || player != instance.minecraft.thePlayer) return physical;
        if (instance.core.getPathingControlManager().mostRecentInControl()
                .filter(p -> p instanceof baritone.api.process.IInputControlProcess
                        && ((baritone.api.process.IInputControlProcess)p).isControllingInput()).isPresent()) {
            return instance.core.getInputOverrideHandler().isInputForcedDown(baritone.api.utils.input.Input.SPRINT);
        }
        SprintStateEvent event = new SprintStateEvent();
        instance.core.getGameEventHandler().onPlayerSprintState(event);
        return event.getState() == null ? physical : event.getState();
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onTick(idle.miau.mcp.event.game.TickEvent.ClientTickEvent event) {
        if (event.phase != idle.miau.mcp.event.game.TickEvent.Phase.START) return;
        if (!enabled()) return;
        PendingPacket pending;
        while ((pending = postPackets.poll()) != null) {
            if (pending.world == minecraft.theWorld && pending.world != null) {
                core.getGameEventHandler().onReceivePacket(packetEvent(EventState.POST, pending.packet));
                populatedChunks(pending.packet);
            }
        }
        boolean dead = minecraft.thePlayer != null && minecraft.theWorld != null && minecraft.thePlayer.getHealth() <= 0;
        if (dead && !wasDead && core.getWorldProvider().getCurrentWorld() != null) core.getGameEventHandler().onPlayerDeath();
        wasDead = dead;
        if (minecraft.isGamePaused()) return;
        TickEvent.Type type = minecraft.thePlayer != null && minecraft.theWorld != null ? TickEvent.Type.IN : TickEvent.Type.OUT;
        if (ModuleManager.fightBot != null && ModuleManager.fightBot.isEnabled()) ModuleManager.fightBot.beforeBaritoneTick();
        if (!enabled()) return;
        core.getGameEventHandler().onTick(TickEvent.createNextProvider().apply(EventState.PRE, type));
        if (ModuleManager.fightBot != null && ModuleManager.fightBot.isEnabled()) ModuleManager.fightBot.afterBaritoneTick();
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onSend(SendPacketEvent event) {
        if (!enabled() || minecraft.theWorld == null || minecraft.thePlayer == null) return;
        core.getGameEventHandler().onSendPacket(packetEvent(EventState.PRE, event.getPacket()));
        if (event.getPacket() instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement placement
                && placement.getPlacedBlockDirection() >= 0 && placement.getPlacedBlockDirection() < 6) {
            core.getGameEventHandler().onBlockInteract(new BlockInteractEvent(placement.getPosition(), BlockInteractEvent.Type.USE));
        } else if (event.getPacket() instanceof net.minecraft.network.play.client.C07PacketPlayerDigging digging
                && digging.getStatus() == net.minecraft.network.play.client.C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) {
            core.getGameEventHandler().onBlockInteract(new BlockInteractEvent(digging.getPosition(), BlockInteractEvent.Type.START_BREAK));
        }
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onReceive(ReceivePacketEvent event) {
        if (!enabled() || minecraft.theWorld == null || minecraft.thePlayer == null) return;
        Packet<?> packet = event.getPacket();
        core.getGameEventHandler().onReceivePacket(packetEvent(EventState.PRE, packet));
        if (packet instanceof S21PacketChunkData chunk && chunk.func_149274_i() && chunk.getExtractedSize() == 0) {
            core.getGameEventHandler().onChunkEvent(new ChunkEvent(EventState.PRE, ChunkEvent.Type.UNLOAD, chunk.getChunkX(), chunk.getChunkZ()));
        }
        postPackets.add(new PendingPacket(minecraft.theWorld, packet));
    }

    private PacketEvent packetEvent(EventState state, Packet<?> packet) {
        return new PacketEvent(minecraft.getNetHandler() == null ? null : minecraft.getNetHandler().getNetworkManager(), state, packet);
    }

    private void populatedChunks(Packet<?> packet) {
        if (packet instanceof S21PacketChunkData chunk) {
            if (!chunk.func_149274_i() || chunk.getExtractedSize() != 0) populated(chunk.getChunkX(), chunk.getChunkZ(), chunk.func_149274_i());
        } else if (packet instanceof S26PacketMapChunkBulk bulk) {
            for (int i = 0; i < bulk.getChunkCount(); i++) populated(bulk.getChunkX(i), bulk.getChunkZ(i), true);
        } else if (packet instanceof S23PacketBlockChange change) {
            populated(change.getBlockPosition().getX() >> 4, change.getBlockPosition().getZ() >> 4, false);
        } else if (packet instanceof S22PacketMultiBlockChange change) {
            for (S22PacketMultiBlockChange.BlockUpdateData block : change.getChangedBlocks()) {
                populated(block.getPos().getX() >> 4, block.getPos().getZ() >> 4, false);
                break;
            }
        }
    }

    private void populated(int x, int z, boolean full) {
        core.getGameEventHandler().onChunkEvent(new ChunkEvent(EventState.POST,
                full ? ChunkEvent.Type.POPULATE_FULL : ChunkEvent.Type.POPULATE_PARTIAL, x, z));
    }

    @Subscribe
    public void onWorldUnload(idle.miau.mcp.event.world.WorldEvent.Unload event) {
        if (!enabled()) return;
        cancel();
        postPackets.clear();
        wasDead = false;
        core.getMemoryBehavior().onTick(TickEvent.createNextProvider().apply(EventState.PRE, TickEvent.Type.OUT));
        worldChanged(null, EventState.POST);
    }

    @Subscribe
    public void onWorldLoad(idle.miau.mcp.event.world.WorldEvent.Load event) {
        if (!enabled()) return;
        worldChanged((WorldClient) event.world, EventState.POST);
    }

    private void worldChanged(WorldClient world, EventState state) {
        if (activeWorld == world) return;
        core.getGameEventHandler().onWorldEvent(new WorldEvent(world, state));
        activeWorld = world;
        if (world != null && world.getChunkProvider() instanceof baritone.utils.accessor.IChunkProviderClient chunks) {
            chunks.loadedChunks().values().forEach(chunk -> core.getWorldProvider().getCurrentWorld().getCachedWorld().queueForPacking(chunk));
        }
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onStrafe(StrafeEvent event) {
        if (!enabled()) return;
        if (minecraft.thePlayer != null && minecraft.thePlayer.movementInput instanceof PlayerMovementInput) event.setYaw(movementFacingYaw());
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onJump(JumpEvent event) {
        if (!enabled() || event.entity != minecraft.thePlayer) return;
        if (minecraft.thePlayer.movementInput instanceof PlayerMovementInput) {
            RotationMoveEvent rotation = new RotationMoveEvent(RotationMoveEvent.Type.JUMP, event.getYaw());
            core.getGameEventHandler().onPlayerRotationMove(rotation);
            event.setYaw(rotation.getYaw());
        }
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (enabled() && minecraft.theWorld != null && minecraft.thePlayer != null) {
            core.getGameEventHandler().onRenderPass(new RenderEvent(event.partialTicks));
        }
    }

    @Subscribe
    public void onRenderHud(idle.miau.mcp.event.game.TickEvent.RenderTickEvent event) {
        if (enabled() && event.phase == idle.miau.mcp.event.game.TickEvent.Phase.END) BaritoneToast.render();
    }

    @Subscribe(priority = Priority.LOW)
    public void onClientRotation(ClientRotationEvent event) {
        if (!enabled() || minecraft.thePlayer == null || minecraft.currentScreen != null) return;
        if (!core.getLookBehavior().isRotationRequested()) return;
        Rotation next = core.getLookBehavior().peekTargetRotation();
        if (next == null || !event.trySetRotation(this, RotationPriority.MOVEMENT, next.getYaw(), next.getPitch(), true)) return;
        event.setRotationAlreadyQuantized(true);
        event.setMovementYawCorrection(true);
    }

    private float movementFacingYaw() {
        Float yaw = RotationHelper.get().getServerYaw();
        return RotationHelper.get().isActive() && yaw != null ? yaw : minecraft.thePlayer.rotationYaw;
    }

    public static void updateMovementInput(MovementInput input) {
        PrePlayerInputEvent event = new PrePlayerInputEvent(input.moveForward, input.moveStrafe, input.jump, input.sneak, 0.3D);
        McpRuntime.EVENTS.post(event);
        float scale = event.isSneak() ? (float)event.getSneakSlowDownMultiplier() : 1.0F;
        input.moveForward = event.getForward() * scale;
        input.moveStrafe = event.getStrafe() * scale;
        input.jump = event.isJump();
        input.sneak = event.isSneak();
        if (instance != null && instance.enabled() && instance.minecraft.thePlayer != null) {
            RotationMoveEvent rotation = new RotationMoveEvent(RotationMoveEvent.Type.MOTION_UPDATE, instance.minecraft.thePlayer.rotationYaw);
            instance.core.getGameEventHandler().onPlayerRotationMove(rotation);
            float[] corrected = MovementFrame.rebase(input.moveForward, input.moveStrafe, rotation.getYaw(), instance.movementFacingYaw());
            input.moveForward = corrected[0];
            input.moveStrafe = corrected[1];
            RotationHelper.get().setServerRelativeMovementInputs(true);
        }
        McpRuntime.EVENTS.post(new PostPlayerInputEvent());
    }

    private void shutdown() {
        core.getPathingBehavior().cancelEverything();
        core.getWorldProvider().closeWorld();
        if (Baritone.getExecutor() instanceof java.util.concurrent.ExecutorService executor) executor.shutdown();
    }

    private record PendingPacket(WorldClient world, Packet<?> packet) {}
}
