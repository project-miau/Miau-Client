package keystrokesmod.script;

import idle.miau.Miau;
import idle.miau.event.*;
import idle.miau.mcp.accessor.IAccessorEntityRenderer;
import idle.miau.mcp.accessor.IAccessorMinecraft;
import idle.miau.module.Module;
import keystrokesmod.script.model.Entity;
import keystrokesmod.script.model.MovementInput;
import keystrokesmod.script.model.PlayerState;
import keystrokesmod.script.model.Vec3;
import keystrokesmod.script.packet.clientbound.SPacket;
import keystrokesmod.script.packet.serverbound.CPacket;
import keystrokesmod.script.packet.serverbound.PacketHandler;
import idle.miau.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import idle.miau.mcp.event.client.*;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import org.lwjgl.opengl.GL11;

public class ScriptEvents {
    public Module module;

    public ScriptEvents(Module module) {
        this.module = module;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onChat(ClientChatReceivedEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (Utils.stripColor(e.message.getUnformattedText()).isEmpty()) {
            return;
        }
        if (Miau.scriptManager.invokeBoolean("onChat", module, e.message.getUnformattedText(), e.type) == 0) {
            e.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onSendPacket(SendPacketEvent e) {
        if (e.isCanceled() || e.getPacket() == null) {
            return;
        }
        if (e.getPacket().getClass().getSimpleName().startsWith("S")) {
            return;
        }
        CPacket packet = PacketHandler.convertServerBound(e.getPacket());
        if (packet != null && Miau.scriptManager.invokeBoolean("onPacketSent", module, packet) == 0) {
            e.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onDispatchPacket(DispatchPacketEvent e) {
        if (e.getPacket() == null) {
            return;
        }
        if (e.getPacket().getClass().getSimpleName().startsWith("S")) {
            return;
        }
        CPacket packet = PacketHandler.convertServerBound(e.getPacket());
        Miau.scriptManager.invoke("onDispatchPacket", module, packet);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.isCanceled() || e.getPacket() == null) {
            return;
        }
        SPacket packet = PacketHandler.convertClientBound(e.getPacket());
        if (packet != null && Miau.scriptManager.invokeBoolean("onPacketReceived", module, packet) == 0) {
            e.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreAttack(PreAttackEvent e) {
        if (e.isCanceled()) {
            return;
        }
        if (Miau.scriptManager.invokeBoolean("onPreAttack", module) == 0) {
            e.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onAttack(AttackEvent e) {
        if (e.isCanceled()) {
            return;
        }
        Entity target = Entity.convert(e.target);
        Entity attacker = Entity.convert(e.attacker);
        if (Miau.scriptManager.invokeBoolean("onAttackEntity", module, target, attacker) == 0) {
            e.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onClientRotations(ClientRotationEvent e) {
        Float[] rotations = Miau.scriptManager.invokeFloatArray("getRotations", module);
        if (rotations == null || rotations.length == 0 || rotations.length > 2) {
            return;
        }
        if (rotations[0] != null) {
            e.yaw = rotations[0];
            e.scriptRotations = true;
        }
        if (rotations.length == 2 && rotations[1] != null) {
            e.pitch = rotations[1];
            e.scriptRotations = true;
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPrePlayerMovementInput(PrePlayerInputEvent e) {
        MovementInput input = new MovementInput(e, (byte) 0);
        Miau.scriptManager.invoke("onPrePlayerInput", module, input);
        e.setForward(input.forward);
        e.setSneak(input.sneak);
        e.setJump(input.jump);
        e.setStrafe(input.strafe);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onKeyTyped(KeyPressEvent e) {
        if (e.isCanceled()) {
            return;
        }
        if (Miau.scriptManager.invokeBoolean("onKeyPress", module, e.typedChar, e.keyCode) == 0) {
            e.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onKeyTyped(KeyEvent e) {
        if (e.isCanceled()) {
            return;
        }
        Miau.scriptManager.invoke("onKey", module, e.keyName, e.keyCode, e.state, e.inGui);
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(e.partialTicks, 0);
        ScriptDefaults.render.beginWorldToScreen(new ScaledResolution(mc).getScaleFactor());
        try {
            Miau.scriptManager.invoke("onRenderWorld", module, e.partialTicks);
        } finally {
            ScriptDefaults.render.endWorldToScreen();
            restoreWorldRenderState(mc, e.partialTicks);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreUpdate(PreUpdateEvent e) {
        Miau.scriptManager.invoke("onPreUpdate", module);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPostUpdate(PostUpdateEvent e) {
        Miau.scriptManager.invoke("onPostUpdate", module);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.entityRenderer.setupOverlayRendering();
        try {
            Miau.scriptManager.invoke("onRenderTick", module, e.renderTickTime);
        } finally {
            restoreOverlayRenderState(mc);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onAntiCheatFlag(AntiCheatFlagEvent e) {
        Miau.scriptManager.invoke("onAntiCheatFlag", module, e.flag, Entity.convert(e.entity));
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onGuiUpdate(GuiUpdateEvent e) {
        if (e.guiScreen == null) {
            return;
        }
        Miau.scriptManager.invoke("onGuiUpdate", module, e.guiScreen.getClass().getSimpleName(), e.opened);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent e) {
        Minecraft.getMinecraft().addScheduledTask(
                () -> Miau.scriptManager.invoke("onDisconnect", module)
        );
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreMotion(PreMotionEvent e) {
        PlayerState playerState = new PlayerState(e, (byte) 0);
        Miau.scriptManager.invoke("onPreMotion", module, playerState);
        if (e.getYaw() != playerState.yaw) {
            e.setYaw(playerState.yaw);
        }
        if (e.getPitch() != playerState.pitch) {
            e.setPitch(playerState.pitch);
        }
        e.setPosX(playerState.x);
        e.setPosY(playerState.y);
        e.setPosZ(playerState.z);
        e.setOnGround(playerState.onGround);
        e.setSprinting(playerState.isSprinting);
        e.setSneaking(playerState.isSneaking);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        Miau.scriptManager.invoke("onPrePlayerInteract", module);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == null) {
            return;
        }
        Miau.scriptManager.invoke("onWorldJoin", module, Entity.convert(e.entity));
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPostInput(PostPlayerInputEvent e) {
        Miau.scriptManager.invoke("onPostPlayerInput", module);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPostMotion(PostMotionEvent e) {
        Miau.scriptManager.invoke("onPostMotion", module);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (e.button == -1 && e.dwheel == 0) {
            return;
        }
        boolean state = e.buttonstate;
        if (e.button == -1) {
            if (e.dwheel > 0) {
                state = true;
            } else {
                state = false;
            }
        }
        if (Miau.scriptManager.invokeBoolean("onMouse", module, e.button, state, e.x, e.y) == 0) {
            e.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreRenderModel(RenderLivingEvent.Pre e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (Miau.scriptManager.invokeBoolean("onPreRenderModel", module, Entity.convert(e.entity)) == 0) {
            e.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPostRenderModel(RenderLivingEvent.Post e) {
        if (!Utils.nullCheck()) {
            return;
        }
        Miau.scriptManager.invoke("onPostRenderModel", module, Entity.convert(e.entity));
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onScreenRender(GuiScreenEvent.DrawScreenEvent.Post event) {
        Miau.scriptManager.invoke("onScreenRender", module, event.gui == null ? "" : event.gui.getClass().getSimpleName(), event.mouseX, event.mouseY, event.renderPartialTicks);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent e) {
        Miau.scriptManager.invoke("onPlayerMove", module, new Vec3(e.x, e.y, e.z));
    }

    private void restoreWorldRenderState(Minecraft mc, float partialTicks) {
        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(((IAccessorMinecraft) mc).getTimer().renderPartialTicks, 0);
        restoreCommonRenderState();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableFog();
        RenderHelper.disableStandardItemLighting();
    }

    private void restoreOverlayRenderState(Minecraft mc) {
        mc.entityRenderer.setupOverlayRendering();
        restoreCommonRenderState();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        RenderHelper.disableStandardItemLighting();
    }

    private void restoreCommonRenderState() {
        GlStateManager.resetColor();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
