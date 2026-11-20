package idle.miau.utility;

import idle.miau.event.*;
import idle.miau.module.impl.combat.KillAura;
import idle.miau.module.impl.movement.LongJump;
import net.minecraft.block.Block;
import idle.miau.module.ModuleManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.BlockPos;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;

public class ModuleUtils implements IMinecraftInstance {
    public static boolean isBreaking;
    public static boolean threwFireball, threwFireballLow;
    private int isBreakingTick;
    public static long MAX_EXPLOSION_DIST_SQ = 10;
    private long FIREBALL_TIMEOUT = 500L, fireballTime = 0;
    public static int inAirTicks, groundTicks, stillTicks;
    public static int fadeEdge;
    public static double offsetValue = 0.0000000000201;
    public static boolean isAttacking;
    private int attackingTicks;
    public static int configTicks = -1;
    public static boolean lastTickOnGround, lastTickPos1;
    private boolean thisTickOnGround, thisTickPos1;
    public static boolean firstDamage;

    public static boolean isBlocked;

    public static boolean damage;
    private int damageTicks;
    private boolean lowhopAir;
    private static boolean allowFriction;

    public static boolean canSlow, didSlow, setSlow;

    @Subscribe
    public void onSendPacketNoEvent(NoEventPacketEvent e) {
        handleAllPacket(e.getPacket());
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent e) {
        handleAllPacket(e.getPacket());

        if (e.getPacket() instanceof C07PacketPlayerDigging) {
            isBreaking = true;
        }

        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement && Utils.holdingFireball()) {
            if (Utils.isBindDown(mc.gameSettings.keyBindUseItem)) {
                fireballTime = System.currentTimeMillis();
                threwFireball = true;
                if (mc.thePlayer.rotationPitch > 50F) {
                    threwFireballLow = true;
                }
            }
        }

    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!Utils.nullCheck() || e.isCanceled()) {
            return;
        }
        if (e.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion s27 = (S27PacketExplosion) e.getPacket();
            if (threwFireball) {
                if ((mc.thePlayer.getPosition().distanceSq(s27.getX(), s27.getY(), s27.getZ()) <= MAX_EXPLOSION_DIST_SQ)) {
                    ModuleManager.velocity.setSuppressed(false);
                    threwFireball = false;
                    e.setCanceled(false);
                }
            }
        }
    }

    private void handleAllPacket(Packet<?> packet) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement && Utils.holdingSword() && !BlockUtils.isInteractable(mc.objectMouseOver) && !isBlocked) {
            isBlocked = true;
        }
        else if (packet instanceof C07PacketPlayerDigging && isBlocked) {
            if (((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                isBlocked = false;
            }
        }
        else if (packet instanceof C09PacketHeldItemChange && isBlocked) {
            isBlocked = false;
        }
        if (packet instanceof C02PacketUseEntity) {
            isAttacking = true;
            attackingTicks = 5;
        }
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent e) {
        if (damage && ++damageTicks >= 8) {
            damage = firstDamage = false;
            damageTicks = 0;
        }
        configTicks++;

        if (isAttacking) {
            if (attackingTicks <= 0) {
                isAttacking = false;
            }
            else {
                --attackingTicks;
            }
        }

        if (LongJump.slotReset && ++LongJump.slotResetTicks >= 2) {
            LongJump.stopModules = false;
            LongJump.slotResetTicks = 0;
            LongJump.slotReset = false;
        }

        allowFriction = false;

        if (fireballTime > 0 && (System.currentTimeMillis() - fireballTime) > FIREBALL_TIMEOUT / 3) {
            threwFireballLow = false;
            ModuleManager.velocity.setSuppressed(false);
        }

        if (fireballTime > 0 && (System.currentTimeMillis() - fireballTime) > FIREBALL_TIMEOUT) {
            threwFireball = threwFireballLow = false;
            fireballTime = 0;
            ModuleManager.velocity.setSuppressed(false);
        }

        if (isBreaking && ++isBreakingTick >= 1) {
            isBreaking = false;
            isBreakingTick = 0;
        }
    }

    @Subscribe
    public void onPostMotion(PostMotionEvent e) {
        if (bHopBoostConditions()) {
            if (firstDamage) {
                Utils.setSpeed(Utils.getHorizontalSpeed());
                firstDamage = false;
            }
        }
    }

    private boolean bHopBoostConditions() {
        return false;
    }

    public static double applyFrictionMulti() {
        final int speedAmplifier = Utils.getSpeedAmplifier();
        if (speedAmplifier > 1 && allowFriction) {
            return 1;
        }
        return 1;
    }

    @Subscribe
    public void onPreMotion(PreMotionEvent e) {
        int simpleY = (int) Math.round((e.posY % 1) * 10000);

        lastTickOnGround = thisTickOnGround;
        thisTickOnGround = mc.thePlayer.onGround;

        lastTickPos1 = thisTickPos1;
        thisTickPos1 = mc.thePlayer.posY % 1 == 0;

        inAirTicks = mc.thePlayer.onGround ? 0 : ++inAirTicks;
        groundTicks = !mc.thePlayer.onGround ? 0 : ++groundTicks;
        stillTicks = Utils.isMoving() ? 0 : ++stillTicks;

        Block blockBelow = BlockUtils.getBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ));
        Block blockBelow2 = BlockUtils.getBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 2, mc.thePlayer.posZ));
        Block block = BlockUtils.getBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));

        if (!mc.thePlayer.onGround) {
            lowhopAir = true;
        }
        else if (lowhopAir) {
            resetLowhop();
        }

        if (canSlow && !mc.thePlayer.onGround) {
            double motionVal = 0.9 - ((double) inAirTicks / 10000) - Utils.randomizeDouble(0.00001, 0.00006);
            if (mc.thePlayer.hurtTime == 0 && inAirTicks > 4 && !setSlow) {
                mc.thePlayer.motionX *= motionVal;
                mc.thePlayer.motionZ *= motionVal;
                setSlow = true;
            }
            didSlow = true;
        }
        else if (didSlow) {
            canSlow = didSlow = false;
        }
        if (mc.thePlayer.onGround) {
            setSlow = false;
        }
    }

    private void resetLowhop() {
        lowhopAir = false;
    }

    public static void handleSlow() {
        didSlow = false;
        canSlow = true;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (ModuleManager.killAura.usesSpoofedRotations() && KillAura.target != null) {
            mc.thePlayer.prevRenderArmYaw = mc.thePlayer.rotationYaw;
            mc.thePlayer.renderArmYaw = mc.thePlayer.rotationYaw;
        }

    }
}
