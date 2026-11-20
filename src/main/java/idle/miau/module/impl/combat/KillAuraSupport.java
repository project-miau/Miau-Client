package idle.miau.module.impl.combat;

import idle.miau.event.PreAttackEvent;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.movement.NoSlow;
import idle.miau.module.impl.player.AutoSoup;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.CombatAim;
import idle.miau.utility.ModuleActionService;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import idle.miau.utility.inventory.SilentHotbar;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.lwjgl.input.Mouse;

import java.util.concurrent.ThreadLocalRandom;

final class KillAuraSupport {
    private static final double CLOSEST_POINT_WEIGHT = 0.9D;

    private KillAuraSupport() {}

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    static void order(SliderSetting min, SliderSetting max) {
        if (min.getInput() > max.getInput()) {
            double value = min.getInput();
            min.setValue(max.getInput());
            max.setValue(value);
        }
    }

    static float[] playerRotation() {
        return new float[] {mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch};
    }

    static float[] currentRotation() {
        return KillAuraLifecycle.wasRotating ? RotationUtils.serverRotations : playerRotation();
    }

    static float clampPitch(float pitch) {
        return MathHelper.clamp_float(pitch, -90.0F, 90.0F);
    }

    static float[] fixSensitivity(float yaw, float pitch, float lastYaw, float lastPitch) {
        float sensitivity = mc().gameSettings.mouseSensitivity * 0.6F + 0.2F;
        double step = (double) (sensitivity * sensitivity * sensitivity) * 1.2D;
        float yawStep = (float) (Math.round((yaw - lastYaw) / step) * step);
        float pitchStep = (float) (Math.round((pitch - lastPitch) / step) * step);
        float fixedYaw = lastYaw + yawStep;
        if (Math.abs(yaw - lastYaw) >= step) {
            fixedYaw += (float) (ThreadLocalRandom.current().nextInt(-3, 4) * step);
        }
        return new float[] {fixedYaw, clampPitch(lastPitch + pitchStep)};
    }

    static void setRotation(float yaw, float pitch) {
        mc().thePlayer.rotationYaw = yaw;
        mc().thePlayer.rotationPitch = pitch;
    }

    static double absYawDifference(Entity entity, boolean server) {
        float yaw = server ? RotationUtils.serverRotations[0] : mc().thePlayer.rotationYaw;
        return Math.abs(MathHelper.wrapAngleTo180_double(yawTo(entity.posX, entity.posZ) - yaw));
    }

    static float yawTo(double x, double z) {
        double angle =
                Math.atan2(x - mc().thePlayer.posX, z - mc().thePlayer.posZ) * (double) 57.29578F;
        return (float) (angle * -1.0D);
    }

    static MovingObjectPosition rayTrace(
            Entity entity, double range, double x, double y, double z, float yaw, float pitch) {
        MovingObjectPosition hit = CombatAim.trace(entity, yaw, pitch, range);
        if (hit == null || mc().getRenderViewEntity() == null) {
            return hit;
        }
        Vec3 eyes = new Vec3(x, y + mc().thePlayer.getEyeHeight(), z);
        if (entity == mc().getRenderViewEntity().ridingEntity
                && !entity.canRiderInteract()
                && !entity.getEntityBoundingBox()
                        .expand(
                                entity.getCollisionBorderSize(),
                                entity.getCollisionBorderSize(),
                                entity.getCollisionBorderSize())
                        .isVecInside(eyes)) {
            return null;
        }
        return eyes.distanceTo(hit.hitVec) < range
                ? new MovingObjectPosition(entity, hit.hitVec)
                : null;
    }

    static boolean mouseDown(int button) {
        return Mouse.isCreated() && Mouse.isButtonDown(button);
    }

    static int heldHotbarKey() {
        for (int slot = 0; slot < 9; slot++) {
            if (Utils.isBindDown(mc().gameSettings.keyBindsHotbar[slot])) {
                return slot;
            }
        }
        return -1;
    }

    static int currentSlot() {
        return mc().thePlayer.inventory.currentItem;
    }

    static boolean isSword(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSword;
    }

    static boolean holdingSword() {
        return isSword(mc().thePlayer.getHeldItem());
    }

    static boolean holdingWeapon() {
        return holdingSword();
    }

    static boolean isBlocking() {
        if (KillAura.instance != null && KillAura.instance.isFakeBlocking() && holdingSword()) {
            return true;
        }
        return mc().thePlayer.isUsingItem()
                && mc().thePlayer.getItemInUse() != null
                && mc().thePlayer.getItemInUse().getItemUseAction() == EnumAction.BLOCK;
    }

    static void attack(Entity entity, boolean packetSwing) {
        swing(packetSwing, false);
        if (mc().playerController.attackEntityWithResult(mc().thePlayer, entity)) {
            KillAura.attackingEntity =
                    entity instanceof EntityLivingBase ? (EntityLivingBase) entity : null;
        }
    }

    static void swing(boolean packetOnly, boolean resetEquip) {
        if (packetOnly) {
            KillAuraPackets.send(new C0APacketAnimation());
        } else {
            mc().thePlayer.swingItem();
            if (resetEquip) {
                mc().getItemRenderer().resetEquippedProgress();
            }
        }
    }

    static boolean holdAttack(Entity entity) {
        HitSelect select = (HitSelect) ModuleManager.getModule(HitSelect.class);
        if (select == null || !select.isEnabled()) {
            return false;
        }
        PreAttackEvent event =
                new PreAttackEvent(new MovingObjectPosition(entity, entity.getPositionVector()));
        select.onPreAttack(event);
        return event.isCanceled();
    }

    static boolean eligiblePlayer(EntityPlayer player) {
        return player != null && mc().thePlayer != null && player != mc().thePlayer && !player.isDead
                && player.deathTime == 0 && player.getHealth() > 0 && !player.isSpectator()
                && KillAura.attackPlayers != null && KillAura.attackPlayers.isToggled()
                && (KillAura.aimInvis.isToggled() || !isInvisibleUnarmoured(player))
                && !isFriend(player) && !idle.miau.module.impl.world.AntiBot.isBot(player)
                && (!KillAura.ignoreTeammates.isToggled() || !Utils.isTeammate(player));
    }

    static boolean isFriend(EntityPlayer player) {
        return Utils.isFriended(player);
    }

    static boolean isMob(Entity entity) {
        return entity instanceof EntityMob
                || entity instanceof EntityGolem
                || entity instanceof EntitySlime;
    }

    static boolean scaffoldActive() {
        return ModuleManager.scaffold != null && ModuleManager.scaffold.isEnabled();
    }

    static boolean scaffoldSwaps() {
        return SilentHotbar.INSTANCE.getCurrentSlot() != currentSlot();
    }

    static boolean blockInActive() {
        return ModuleManager.blockIn != null && ModuleManager.blockIn.isActive();
    }

    static boolean bedAuraActive() {
        return ModuleManager.bedAura != null
                && ModuleManager.bedAura.isEnabled()
                && ModuleManager.bedAura.getAuraTargetPos() != null;
    }

    static boolean bedAuraAllows() {
        return ModuleManager.bedAura != null && ModuleManager.bedAura.isPrioritizingKillAura();
    }

    static boolean slotFree(boolean attack) {
        return !itemActionActive()
                && (ModuleManager.scaffold == null || ModuleManager.scaffold.allowKillAura())
                && (!bedAuraActive()
                        || !ModuleManager.bedAura.isActivelyMining()
                        || bedAuraAllows());
    }

    static boolean itemActionActive() {
        Object owner = ModuleActionService.owner();
        return owner instanceof AutoRod || owner instanceof AutoProjectile || owner instanceof AutoSoup;
    }

    static Block getBlock(BlockPos position) {
        return mc().theWorld.getBlockState(position).getBlock();
    }

    static void slowdown() {
        float multiplier = NoSlow.getSlowed();
        mc().thePlayer.movementInput.moveForward *= multiplier;
        mc().thePlayer.movementInput.moveStrafe *= multiplier;
    }

    public static float[] toVec(Vec3 vec3) {
        double x = mc().thePlayer.posX;
        double y = mc().thePlayer.posY + (double) mc().thePlayer.getEyeHeight();
        double z = mc().thePlayer.posZ;
        if (vec3.xCoord == x && vec3.yCoord == y && vec3.zCoord == z) {
            return playerRotation();
        }
        return fromDelta(vec3.xCoord - x, vec3.yCoord - y, vec3.zCoord - z);
    }

    public static float[] toEntityBox(Entity entity) {
        if (entity == null) {
            return null;
        }
        return toBox(entity, entity.getEntityBoundingBox());
    }

    public static float[] toBox(Entity entity, AxisAlignedBB box) {
        Vec3 eyes;
        if (entity == null) {
            return null;
        }
        float f = entity.getCollisionBorderSize();
        if ((box = box.expand(f, f, f)).isVecInside(eyes = mc().thePlayer.getPositionEyes(1.0f))) {
            return new float[] {mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch};
        }
        Vec3 p = aimPoint(eyes, box);
        return fromDelta(p.xCoord - eyes.xCoord, p.yCoord - eyes.yCoord, p.zCoord - eyes.zCoord);
    }

    public static Vec3 aimPoint(Vec3 eyes, AxisAlignedBB box) {
        double cx = (box.minX + box.maxX) / 2.0;
        double cy = box.minY + (box.maxY - box.minY) * 0.825;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double px = MathHelper.clamp_double(eyes.xCoord, box.minX + 0.03, box.maxX - 0.03);
        double py = MathHelper.clamp_double(eyes.yCoord, box.minY + 0.03, box.maxY - 0.03);
        double pz = MathHelper.clamp_double(eyes.zCoord, box.minZ + 0.03, box.maxZ - 0.03);
        return new Vec3(
                cx + (px - cx) * CLOSEST_POINT_WEIGHT,
                cy + (py - cy) * CLOSEST_POINT_WEIGHT,
                cz + (pz - cz) * CLOSEST_POINT_WEIGHT);
    }

    public static Vec3 visiblePoint(Entity entity) {
        Vec3 eyes = mc().thePlayer.getPositionEyes(1.0f);
        float f = entity.getCollisionBorderSize();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(f, f, f);
        if (box.isVecInside(eyes)) {
            return eyes;
        }
        Vec3 p = aimPoint(eyes, box);
        if (CombatAim.clearLine(eyes, p)) {
            return p;
        }
        return fallbackPoint(entity, eyes);
    }

    public static Vec3 fallbackPoint(Entity entity, Vec3 eyes) {
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;
        double eye = entity.getEyeHeight();
        double h1 = eye - 0.2;
        double h2 = eye - 0.9;
        double h3 = eye - 1.5;
        double o = 0.28;
        Vec3[] points = {
            new Vec3(x, y + (eye + 0.2), z),
            new Vec3(x, y + (eye - 0.6), z),
            new Vec3(x, y + h2, z),
            new Vec3(x, y + (eye - 1.2), z),
            new Vec3(x, y + h3, z),
            new Vec3(x + o, y + h1, z + o),
            new Vec3(x + o, y + h1, z - o),
            new Vec3(x - o, y + h1, z + o),
            new Vec3(x - o, y + h1, z - o),
            new Vec3(x + o, y + h2, z + o),
            new Vec3(x + o, y + h2, z - o),
            new Vec3(x - o, y + h2, z + o),
            new Vec3(x - o, y + h2, z - o),
            new Vec3(x + o, y + h3, z + o),
            new Vec3(x + o, y + h3, z - o),
            new Vec3(x - o, y + h3, z + o),
            new Vec3(x - o, y + h3, z - o),
        };
        for (Vec3 p : points) {
            if (CombatAim.clearLine(eyes, p)) {
                return p;
            }
        }
        return null;
    }

    public static boolean canSee(Entity entity) {
        Vec3 eyes = mc().thePlayer.getPositionEyes(1.0f);
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;
        double h = (double) entity.getEyeHeight() - 0.2;
        if (CombatAim.clearLine(eyes, new Vec3(x + 0.3, y + h, z))) {
            return true;
        }
        if (CombatAim.clearLine(eyes, new Vec3(x - 0.3, y + h, z))) {
            return true;
        }
        if (CombatAim.clearLine(eyes, new Vec3(x, y + h, z + 0.3))) {
            return true;
        }
        if (CombatAim.clearLine(eyes, new Vec3(x, y + h, z - 0.3))) {
            return true;
        }
        for (double d = (double) entity.getEyeHeight() + 0.2; d > 0.0; d -= 0.2) {
            if (!CombatAim.clearLine(eyes, new Vec3(x, y + d, z))) continue;
            return true;
        }
        return false;
    }

    public static float[] fromDelta(double dx, double dy, double dz) {
        double d4 = Math.sqrt(dx * dx + dz * dz);
        float f = (float) (Math.atan2(dz, dx) * (double) 57.29578f) - 90.0f;
        float f2 = (float) (-(Math.atan2(dy, d4) * (double) 57.29578f));
        float[] current = currentRotation();
        f = current[0] + MathHelper.wrapAngleTo180_float(f - current[0]);
        f2 = clampPitch(current[1] + MathHelper.wrapAngleTo180_float(f2 - current[1]));
        return new float[] {f, f2};
    }

    public static boolean rayHits(
            Entity entity, AxisAlignedBB box, float yaw, float pitch, double range) {
        if (entity == null || !entity.canBeCollidedWith()) {
            return false;
        }
        Vec3 from = mc().thePlayer.getPositionEyes(1.0f);
        Vec3 dir = RotationUtils.getVectorForRotation(pitch, yaw);
        Vec3 to = from.addVector(dir.xCoord * range, dir.yCoord * range, dir.zCoord * range);
        float f = entity.getCollisionBorderSize();
        box = box.expand(f, f, f);
        return box.calculateIntercept(from, to) != null;
    }

    public static boolean rayHits(Entity entity, float yaw, float pitch, double range) {
        if (entity == null) {
            return false;
        }
        return rayHits(entity, entity.getEntityBoundingBox(), yaw, pitch, range);
    }

    public static boolean rayHits(Entity entity, double range) {
        float[] r = toEntityBox(entity);
        return rayHits(entity, r[0], r[1], range);
    }

    public static boolean isInvisibleUnarmoured(EntityLivingBase entity) {
        if (!entity.isInvisible()) {
            return false;
        }
        for (int i = 0; i <= 4; ++i) {
            if (entity.getEquipmentInSlot(i) == null) continue;
            return false;
        }
        return true;
    }

    public static boolean inFov(float fov, double x, double z) {
        fov = (float) ((double) fov * 0.5);
        double d =
                MathHelper.wrapAngleTo180_double(
                        (double) ((mc().thePlayer.rotationYaw - yawTo(x, z)) % 360.0f));
        if (d > 0.0) {
            return d < (double) fov;
        }
        return d > (double) (-fov);
    }

    public static boolean inFov(float fov, Entity entity) {
        if (fov >= 360.0f) {
            return true;
        }
        Vec3 eyes = mc().thePlayer.getPositionEyes(1.0f);
        float f = entity.getCollisionBorderSize();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(f, f, f);
        if (box.isVecInside(eyes)) {
            return true;
        }
        return inFov(fov, entity.posX, entity.posZ);
    }

    public static boolean isThrowable(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        Item item = itemStack.getItem();
        return item == Items.fire_charge
                || item == Items.ender_pearl
                || item == Items.ender_eye
                || item == Items.snowball
                || item == Items.egg
                || item == Items.experience_bottle
                || item == Items.nether_star
                || item == Items.potionitem && ItemPotion.isSplash(item.getMetadata(itemStack));
    }

    public static boolean sameItem(ItemStack a, ItemStack b) {
        Item item = a == null ? null : a.getItem();
        Item item2 = b == null ? null : b.getItem();
        return item == item2;
    }

    public static boolean isContainer(Block block) {
        return block instanceof BlockContainer
                || block == Blocks.crafting_table
                || block == Blocks.anvil;
    }

    public static boolean isInteractable(Block block) {
        if (isContainer(block) || block == Blocks.bed || block == Blocks.lever) {
            return true;
        }
        if (block instanceof BlockTrapDoor
                || block instanceof BlockFence
                || block instanceof BlockFenceGate) {
            return true;
        }
        if (!(block instanceof BlockDoor)) {
            return false;
        }
        return block != Blocks.iron_door;
    }
}
