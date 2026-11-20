package idle.miau.module.impl.misc.disablers;

import idle.miau.event.PreMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.PushOutOfBlockEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.PacketUtils;
import idle.miau.utility.SelectivePacketBlink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.BlockWeb;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public final class Miniblox extends DisablerMode {
    private static final int SEARCH_RADIUS = 500;

    private final SliderSetting silentTicks;
    private final SliderSetting teleportDelay;
    private final ButtonSetting silentAcceptTeleports;
    private final SelectivePacketBlink blink = new SelectivePacketBlink();

    private double baseSpeed;
    private boolean teleported;
    private int lastTeleportTick;

    public Miniblox(DisablerModule owner) {
        super("Miniblox", owner);
        owner.registerSetting(silentTicks = new SliderSetting(
                "Silent Ticks", 7, 1, 15, 1));
        owner.registerSetting(teleportDelay = new SliderSetting(
                "Teleport Delay", 20, 10, 25, 1));
        owner.registerSetting(silentAcceptTeleports = new ButtonSetting(
                "Silent Accept Teleports", true));
        setSettingVisibility(false);
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        BlockPos block = findNearestSolidBlock(SEARCH_RADIUS);
        if (block != null) {
            teleported = true;
            lastTeleportTick = mc.thePlayer.ticksExisted;
            teleportToBlock(block);
        }
        baseSpeed = 0.0D;
    }

    @Override
    public void onDisable() {
        blink.flush();
    }

    @Override
    public void onGuiUpdate() {
        setSettingVisibility(owner.isActiveMode(this));
    }

    @Override
    public void onPushOutOfBlock(PushOutOfBlockEvent event) {
        event.setCanceled(true);
    }

    @Override
    public void onPreUpdate(PreUpdateEvent event) {
        blink.refresh(1000L, false);
    }

    @Override
    public void onPreMotion(PreMotionEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        int currentTick = mc.thePlayer.ticksExisted;
        int delay = (int) teleportDelay.getInput();
        if (teleported && currentTick - lastTeleportTick >= delay) {
            teleported = false;
        }

        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        if (mc.thePlayer.capabilities.allowFlying) {
            lastTeleportTick = currentTick;
        }

        if (currentTick % delay != 0 || mc.thePlayer.capabilities.allowFlying) {
            return;
        }

        BlockPos block = findNearestSolidBlock(SEARCH_RADIUS);
        if (block != null) {
            teleported = true;
            lastTeleportTick = currentTick;
            teleportToBlock(block);
        } else {
            PacketUtils.sendPacketNoEvent(new C04PacketPlayerPosition(
                    sin * 9.5D,
                    mc.thePlayer.posY,
                    cos * 9.5D,
                    mc.thePlayer.onGround));
        }
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        blink.capture(event);
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        blink.capture(event);
        if (!(event.getPacket() instanceof S08PacketPlayerPosLook)
                || mc.thePlayer == null) {
            return;
        }

        int elapsed = mc.thePlayer.ticksExisted - lastTeleportTick;
        if (elapsed > (int) silentTicks.getInput()
                && !mc.thePlayer.capabilities.allowFlying) {
            event.setCanceled(true);
        }
        baseSpeed = getBaseMoveSpeed();

        if (elapsed <= 0
                || mc.thePlayer.capabilities.allowFlying
                || !silentAcceptTeleports.isToggled()) {
            return;
        }

        event.setCanceled(true);
        S08PacketPlayerPosLook teleport = (S08PacketPlayerPosLook) event.getPacket();
        C06PacketPlayerPosLook response = new C06PacketPlayerPosLook(
                teleport.getX(),
                teleport.getY(),
                teleport.getZ(),
                teleport.getYaw(),
                teleport.getPitch(),
                mc.thePlayer.onGround);
        PacketUtils.sendPacketNoEvent(response);
        PacketUtils.sendPacketNoEvent(new C06PacketPlayerPosLook(
                teleport.getX(),
                teleport.getY(),
                teleport.getZ(),
                teleport.getYaw(),
                teleport.getPitch(),
                mc.thePlayer.onGround));
    }

    @Override
    public void onWorldUnload(idle.miau.mcp.event.world.WorldEvent.Unload event) {
        blink.clear();
        teleported = false;
        lastTeleportTick = 0;
        baseSpeed = 0.0D;
    }

    private void teleportToBlock(BlockPos block) {
        PacketUtils.sendPacketNoEvent(new C04PacketPlayerPosition(
                block.getX() + 0.5D,
                block.getY(),
                block.getZ() + 0.5D,
                mc.thePlayer.onGround));
        PacketUtils.sendPacketNoEvent(new C04PacketPlayerPosition(
                mc.thePlayer.posX + mc.thePlayer.motionX,
                mc.thePlayer.posY + mc.thePlayer.motionY,
                mc.thePlayer.posZ + mc.thePlayer.motionZ,
                mc.thePlayer.onGround));
    }

    private BlockPos findNearestSolidBlock(int radius) {
        double playerX = mc.thePlayer.posX;
        double playerZ = mc.thePlayer.posZ;
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        double nearestDistance = Double.MAX_VALUE;
        BlockPos nearest = null;

        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                BlockPos position = new BlockPos(
                        MathHelper.floor_double(playerX) + offsetX,
                        playerY,
                        MathHelper.floor_double(playerZ) + offsetZ);
                Block block = mc.theWorld.getBlockState(position).getBlock();
                if (!block.getMaterial().isSolid()
                        || !block.isFullBlock()
                        || block instanceof BlockLeaves
                        || block instanceof BlockSnow
                        || block instanceof BlockWeb) {
                    continue;
                }
                double distance = mc.thePlayer.getDistance(
                        position.getX() + 0.5D,
                        position.getY(),
                        position.getZ() + 0.5D);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = position;
                }
            }
        }
        return nearest;
    }

    private double getBaseMoveSpeed() {
        return applySpeedPotion(0.2873D);
    }

    private double applySpeedPotion(double speed) {
        if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
            speed *= 1.0D + 0.2D
                    * (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed)
                    .getAmplifier() + 1);
        }
        return speed;
    }

    private void setSettingVisibility(boolean visible) {
        silentTicks.setVisible(visible, owner);
        teleportDelay.setVisible(visible, owner);
        silentAcceptTeleports.setVisible(visible, owner);
    }
}
