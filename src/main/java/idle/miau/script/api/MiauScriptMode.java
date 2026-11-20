package idle.miau.script.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.PostPlayerInputEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.client.MouseEvent;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.script.api.MiauScriptApi.BridgeApi;
import idle.miau.script.api.MiauScriptApi.CPacket;
import idle.miau.script.api.MiauScriptApi.ClientApi;
import idle.miau.script.api.MiauScriptApi.Entity;
import idle.miau.script.api.MiauScriptApi.GlApi;
import idle.miau.script.api.MiauScriptApi.InventoryApi;
import idle.miau.script.api.MiauScriptApi.KeybindsApi;
import idle.miau.script.api.MiauScriptApi.ModulesApi;
import idle.miau.script.api.MiauScriptApi.PlayerState;
import idle.miau.script.api.MiauScriptApi.RenderApi;
import idle.miau.script.api.MiauScriptApi.SPacket;
import idle.miau.script.api.MiauScriptApi.UtilApi;
import idle.miau.script.api.MiauScriptApi.WorldApi;

public abstract class MiauScriptMode {
    private static final Logger LOGGER = LogManager.getLogger(MiauScriptMode.class);
    protected final String scriptName;
    protected final ClientApi client = new ClientApi();
    protected final ModulesApi modules;
    protected final WorldApi world = new WorldApi();
    protected final RenderApi render = new RenderApi();
    protected final InventoryApi inventory = new InventoryApi();
    protected final KeybindsApi keybinds = new KeybindsApi();
    protected final GlApi gl = new GlApi();
    protected final UtilApi util = new UtilApi();
    protected final BridgeApi bridge = new BridgeApi();
    private volatile boolean callbackFailed;

    protected MiauScriptMode(Module owner, String name) {
        this.scriptName = name;
        this.modules = new ModulesApi(owner, name);
    }

    public final void activate() {
        callbackFailed = false;
        try {
            onEnable();
        } catch (Throwable throwable) {
            failCallback("onEnable", throwable);
        }
    }

    public final void deactivate() {
        if (callbackFailed) return;
        try {
            onDisable();
        } catch (Throwable throwable) {
            failCallback("onDisable", throwable);
        }
    }

    protected final void initializeScript() {
        if (callbackFailed) return;
        try {
            onLoad();
        } catch (Throwable throwable) {
            failCallback("onLoad", throwable);
        }
    }

    public final void miau$preUpdate(PreUpdateEvent event) {
        if (callbackFailed) return;
        try {
            onPreUpdate();
        } catch (Throwable throwable) {
            failCallback("onPreUpdate", throwable);
        }
    }

    public final void miau$postPlayerInput(PostPlayerInputEvent event) {
        if (callbackFailed) return;
        try {
            onPostPlayerInput();
        } catch (Throwable throwable) {
            failCallback("onPostPlayerInput", throwable);
        }
    }

    public final void miau$preMotion(PreMotionEvent event) {
        if (callbackFailed) return;
        PlayerState state = new PlayerState();
        state.x = event.getPosX();
        state.y = event.getPosY();
        state.z = event.getPosZ();
        state.yaw = event.getYaw();
        state.pitch = event.getPitch();
        state.onGround = event.isOnGround();
        state.isSprinting = event.isSprinting();
        state.isSneaking = event.isSneaking();
        try {
            onPreMotion(state);
        } catch (Throwable throwable) {
            failCallback("onPreMotion", throwable);
            return;
        }
        event.setPosX(state.x);
        event.setPosY(state.y);
        event.setPosZ(state.z);
        event.setRotations(state.yaw, state.pitch);
        event.setOnGround(state.onGround);
        event.setSprinting(state.isSprinting);
        event.setSneaking(state.isSneaking);
    }

    public final void miau$postMotion(PostMotionEvent event) {
        if (callbackFailed) return;
        try {
            onPostMotion();
        } catch (Throwable throwable) {
            failCallback("onPostMotion", throwable);
        }
    }

    public final void miau$rotations(ClientRotationEvent event) {
        if (callbackFailed) return;
        try {
            float yaw = event.yaw != null
                    ? event.yaw.floatValue()
                    : 0.0F;
            float pitch = event.pitch != null
                    ? event.pitch.floatValue()
                    : 0.0F;
            float[] rotations = getRotations(yaw, pitch);
            if (rotations == null || rotations.length < 2) return;
            event.setRotation(rotations[0], rotations[1]);
            event.scriptRotations = true;
        } catch (Throwable throwable) {
            failCallback("getRotations", throwable);
        }
    }

    public final void miau$sendPacket(SendPacketEvent event) {
        if (callbackFailed) return;
        try {
            CPacket packet = MiauScriptApi.wrapClientPacket(event.getPacket());
            if (!onPacketSent(packet)) event.setCanceled(true);
        } catch (Throwable throwable) {
            failCallback("onPacketSent", throwable);
        }
    }

    public final void miau$receivePacket(ReceivePacketEvent event) {
        if (callbackFailed) return;
        try {
            SPacket packet = MiauScriptApi.wrapServerPacket(event.getPacket());
            if (!onPacketReceived(packet)) event.setCanceled(true);
        } catch (Throwable throwable) {
            failCallback("onPacketReceived", throwable);
        }
    }

    public final void miau$renderWorld(RenderWorldLastEvent event) {
        if (callbackFailed) return;
        try {
            onRenderWorld(event.partialTicks);
        } catch (Throwable throwable) {
            failCallback("onRenderWorld", throwable);
        }
    }

    public final void miau$renderTick(TickEvent.RenderTickEvent event) {
        if (callbackFailed || event.phase != TickEvent.Phase.END) return;
        try {
            onRenderTick(event.renderTickTime);
        } catch (Throwable throwable) {
            failCallback("onRenderTick", throwable);
        }
    }

    public final void miau$worldJoin(EntityJoinWorldEvent event) {
        if (callbackFailed) return;
        try {
            onWorldJoin(new Entity(event.entity));
        } catch (Throwable throwable) {
            failCallback("onWorldJoin", throwable);
        }
    }

    public final void miau$mouse(MouseEvent event) {
        if (callbackFailed) return;
        try {
            if (!onMouse(event.button, event.buttonstate, event.x, event.y)) {
                event.setCanceled(true);
            }
        } catch (Throwable throwable) {
            failCallback("onMouse", throwable);
        }
    }

    protected void onLoad() {
    }

    public final void setSettingsVisible(boolean visible) {
        modules.setSettingsVisible(visible);
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onPreUpdate() {
    }

    protected void onPostPlayerInput() {
    }

    protected void onPreMotion(PlayerState state) {
    }

    protected void onPostMotion() {
    }

    protected float[] getRotations(float yaw, float pitch) {
        return null;
    }

    protected boolean onPacketSent(CPacket packet) {
        return true;
    }

    protected boolean onPacketReceived(SPacket packet) {
        return true;
    }

    protected void onRenderWorld(float partialTicks) {
    }

    protected void onRenderTick(float partialTicks) {
    }

    protected void onWorldJoin(Entity entity) {
    }

    protected boolean onMouse(int button, boolean buttonState) {
        return true;
    }

    protected boolean onMouse(int button, boolean buttonState, int x, int y) {
        return onMouse(button, buttonState);
    }

    private void failCallback(String callback, Throwable throwable) {
        callbackFailed = true;
        LOGGER.error("[Mimeow] {} failed in {}", scriptName, callback, throwable);
    }

}
