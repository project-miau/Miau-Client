package idle.miau.script.api;

import idle.miau.performance.MiauDrawCalls;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.mcp.accessor.IAccessorMinecraft;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MiauScriptApi {
    private MiauScriptApi() {
    }

    public static final class Vec3 {
        public double x;
        public double y;
        public double z;

        public Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vec3(net.minecraft.util.Vec3 value) {
            this(value == null ? 0.0D : value.xCoord,
                    value == null ? 0.0D : value.yCoord,
                    value == null ? 0.0D : value.zCoord);
        }

        public Vec3(BlockPos value) {
            this(value == null ? 0.0D : value.getX(),
                    value == null ? 0.0D : value.getY(),
                    value == null ? 0.0D : value.getZ());
        }

        public Vec3 offset(double x, double y, double z) {
            return new Vec3(this.x + x, this.y + y, this.z + z);
        }

        public double distanceTo(Vec3 other) {
            return other == null ? Double.POSITIVE_INFINITY : Math.sqrt(distanceToSq(other));
        }

        public double distanceToSq(Vec3 other) {
            if (other == null) {
                return Double.POSITIVE_INFINITY;
            }
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }

        net.minecraft.util.Vec3 unwrap() {
            return new net.minecraft.util.Vec3(x, y, z);
        }

        public static Vec3 of(net.minecraft.util.Vec3 vec) {
            return vec == null ? null : new Vec3(vec.xCoord, vec.yCoord, vec.zCoord);
        }

        public static Vec3 of(BlockPos pos) {
            return pos == null ? null : new Vec3(pos.getX(), pos.getY(), pos.getZ());
        }

        public static Vec3 convert(BlockPos pos) {
            return of(pos);
        }

        public static BlockPos getBlockPos(Vec3 position) {
            return position == null ? null : new BlockPos(position.x, position.y, position.z);
        }

        public static net.minecraft.util.Vec3 getVec3(Vec3 position) {
            return position == null ? null : position.unwrap();
        }

        public Vec3 offset(Vec3 other) {
            return other == null ? this : offset(other.x, other.y, other.z);
        }

        public Vec3 translate(Vec3 other) {
            return offset(other);
        }

        public Vec3 translate(double x, double y, double z) {
            return offset(x, y, z);
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public boolean equals(Vec3 other) {
            return other != null && x == other.x && y == other.y && z == other.z;
        }

        public Vec3 floor() {
            return new Vec3(Math.floor(x), Math.floor(y), Math.floor(z));
        }

        public Vec3 ceil() {
            return new Vec3(Math.ceil(x), Math.ceil(y), Math.ceil(z));
        }

        public Vec3 inverse() {
            return new Vec3(-x, -y, -z);
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof Vec3)) return false;
            Vec3 other = (Vec3) value;
            return Double.compare(x, other.x) == 0
                    && Double.compare(y, other.y) == 0
                    && Double.compare(z, other.z) == 0;
        }

        @Override
        public int hashCode() {
            long result = Double.doubleToLongBits(x);
            result = 31L * result + Double.doubleToLongBits(y);
            result = 31L * result + Double.doubleToLongBits(z);
            return (int) (result ^ result >>> 32);
        }

        @Override
        public String toString() {
            return "Vec3{" + x + ", " + y + ", " + z + "}";
        }
    }

    public static final class Entity {
        public net.minecraft.entity.Entity entity;
        public String type;
        public int entityId;
        public boolean isLiving;
        public boolean isPlayer;
        public boolean isUser;

        public Entity(net.minecraft.entity.Entity handle) {
            this.entity = handle;
            this.type = handle == null ? "" : handle.getClass().getSimpleName();
            this.entityId = handle == null ? 0 : handle.getEntityId();
            this.isLiving = handle instanceof net.minecraft.entity.EntityLivingBase;
            this.isPlayer = handle instanceof net.minecraft.entity.player.EntityPlayer;
            this.isUser = handle != null && handle == Minecraft.getMinecraft().thePlayer;
        }

        public static Entity convert(net.minecraft.entity.Entity handle) {
            return handle == null ? null : new Entity(handle);
        }

        public static void clearCache() {
        }

        public boolean isValid() {
            return entity != null && !isDead();
        }

        public int getId() {
            return entityId;
        }

        public String getType() {
            return type;
        }

        public boolean isPlayer() {
            return isPlayer;
        }

        public boolean isLiving() {
            return isLiving;
        }

        public double getX() {
            return entity == null ? 0.0D : entity.posX;
        }

        public double getY() {
            return entity == null ? 0.0D : entity.posY;
        }

        public double getZ() {
            return entity == null ? 0.0D : entity.posZ;
        }

        public double getPrevX() {
            return entity == null ? 0.0D : entity.prevPosX;
        }

        public double getPrevY() {
            return entity == null ? 0.0D : entity.prevPosY;
        }

        public double getPrevZ() {
            return entity == null ? 0.0D : entity.prevPosZ;
        }

        public double getMotionX() {
            return entity == null ? 0.0D : entity.motionX;
        }

        public double getMotionY() {
            return entity == null ? 0.0D : entity.motionY;
        }

        public double getMotionZ() {
            return entity == null ? 0.0D : entity.motionZ;
        }

        public Vec3 getPosition() {
            return entity == null ? null : new Vec3(entity.posX, entity.posY, entity.posZ);
        }

        public Vec3 getLastPosition() {
            return entity == null ? null : new Vec3(entity.prevPosX, entity.prevPosY, entity.prevPosZ);
        }

        public Vec3 getBlockPosition() {
            return entity == null ? null : Vec3.of(entity.getPosition());
        }

        public float getYaw() {
            return entity == null ? 0.0F : entity.rotationYaw;
        }

        public float getPitch() {
            return entity == null ? 0.0F : entity.rotationPitch;
        }

        public void setYaw(float yaw) {
            if (entity != null) entity.rotationYaw = yaw;
        }

        public void setPitch(float pitch) {
            if (entity != null) entity.rotationPitch = pitch;
        }

        public float getEyeHeight() {
            return entity == null ? 0.0F : entity.getEyeHeight();
        }

        public float getHeight() {
            return entity == null ? 0.0F : entity.height;
        }

        public float getWidth() {
            return entity == null ? 0.0F : entity.width;
        }

        public int getTicksExisted() {
            return entity == null ? 0 : entity.ticksExisted;
        }

        public boolean isDead() {
            return entity == null || entity.isDead
                    || isLiving && ((net.minecraft.entity.EntityLivingBase) entity).deathTime > 0;
        }

        public boolean onGround() {
            return entity != null && entity.onGround;
        }

        public boolean isOnGround() {
            return onGround();
        }

        public boolean isMoving() {
            if (!(entity instanceof net.minecraft.client.entity.EntityPlayerSP)) {
                return false;
            }
            net.minecraft.client.entity.EntityPlayerSP player =
                    (net.minecraft.client.entity.EntityPlayerSP) entity;
            return player.movementInput != null
                    && (player.movementInput.moveForward != 0.0F
                    || player.movementInput.moveStrafe != 0.0F);
        }

        public boolean isSneaking() {
            return entity != null && entity.isSneaking();
        }

        public boolean isSprinting() {
            return entity != null && entity.isSprinting();
        }

        public boolean isInvisible() {
            return entity != null && entity.isInvisible();
        }

        public boolean isRiding() {
            return entity != null && entity.isRiding();
        }

        public boolean isInWater() {
            return entity != null && entity.isInWater();
        }

        public boolean isInLava() {
            return entity != null && entity.isInLava();
        }

        public boolean isCollided() {
            return entity != null && entity.isCollided;
        }

        public boolean isCollidedHorizontally() {
            return entity != null && entity.isCollidedHorizontally;
        }

        public boolean isCollidedVertically() {
            return entity != null && entity.isCollidedVertically;
        }

        public float getFallDistance() {
            return entity == null ? 0.0F : entity.fallDistance;
        }

        public ItemStack getHeldItem() {
            if (!(entity instanceof net.minecraft.entity.EntityLivingBase)) return null;
            return ItemStack.of(((net.minecraft.entity.EntityLivingBase) entity).getHeldItem());
        }

        public boolean isHoldingBlock() {
            ItemStack stack = getHeldItem();
            return stack != null && stack.isBlock && stack.stackSize > 0;
        }

        public boolean allowEditing() {
            return entity instanceof net.minecraft.entity.player.EntityPlayer
                    && ((net.minecraft.entity.player.EntityPlayer) entity).capabilities.allowEdit;
        }

        public boolean isHoldingWeapon() {
            return entity instanceof net.minecraft.entity.EntityLivingBase
                    && Utils.holdingWeapon((net.minecraft.entity.EntityLivingBase) entity);
        }

        public float getAbsorption() {
            return entity instanceof net.minecraft.entity.EntityLivingBase
                    ? ((net.minecraft.entity.EntityLivingBase) entity).getAbsorptionAmount() : -1.0F;
        }

        public double distanceToGround() {
            return entity == null ? -1.0D : Utils.distanceToGround(entity);
        }

        public Entity getRidingEntity() {
            return entity == null ? null : convert(entity.ridingEntity);
        }

        public Entity getRiddenByEntity() {
            return entity == null ? null : convert(entity.riddenByEntity);
        }

        public NetworkPlayer getNetworkPlayer() {
            if (!(entity instanceof net.minecraft.entity.player.EntityPlayer)) {
                return null;
            }
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.getNetHandler() == null) {
                return null;
            }
            return NetworkPlayer.convert(minecraft.getNetHandler().getPlayerInfo(entity.getUniqueID()));
        }

        public int getExperienceLevel() {
            return entity instanceof net.minecraft.entity.player.EntityPlayer
                    ? ((net.minecraft.entity.player.EntityPlayer) entity).experienceLevel : 0;
        }

        public float getExperience() {
            return entity instanceof net.minecraft.entity.player.EntityPlayer
                    ? ((net.minecraft.entity.player.EntityPlayer) entity).experience : 0.0F;
        }

        public String getUUID() {
            return entity == null || entity.getUniqueID() == null
                    ? "" : entity.getUniqueID().toString();
        }

        public String getCustomNameTag() {
            return entity == null ? "" : entity.getCustomNameTag();
        }

        public double getBPS() {
            if (!isLiving || entity == null) return 0.0D;
            double x = entity.posX - entity.prevPosX;
            double z = entity.posZ - entity.prevPosZ;
            return Math.sqrt(x * x + z * z) * 20.0D;
        }

        public String getFacing() {
            return entity == null || entity.getHorizontalFacing() == null
                    ? "" : entity.getHorizontalFacing().name();
        }

        public boolean isSleeping() {
            return entity instanceof net.minecraft.entity.player.EntityPlayer
                    && ((net.minecraft.entity.player.EntityPlayer) entity).isPlayerSleeping();
        }

        public boolean isBurning() {
            return entity != null && entity.isBurning();
        }

        public int getStackSize() {
            if (entity instanceof net.minecraft.entity.item.EntityItem) {
                net.minecraft.item.ItemStack stack =
                        ((net.minecraft.entity.item.EntityItem) entity).getEntityItem();
                return stack == null ? -1 : stack.stackSize;
            }
            ItemStack stack = getHeldItem();
            return stack == null ? -1 : stack.stackSize;
        }

        public boolean isConsuming() {
            return entity != null && Utils.isConsuming(entity);
        }

        public int getMaxHurtTime() {
            return isLiving ? ((net.minecraft.entity.EntityLivingBase) entity).maxHurtTime : -1;
        }

        public String getName() {
            return entity == null ? "" : entity.getName();
        }

        public String getDisplayName() {
            return entity == null || entity.getDisplayName() == null
                    ? getName() : entity.getDisplayName().getUnformattedText();
        }

        public int getHurtTime() {
            return isLiving ? ((net.minecraft.entity.EntityLivingBase) entity).hurtTime : -1;
        }

        public float getHealth() {
            return isLiving ? ((net.minecraft.entity.EntityLivingBase) entity).getHealth() : -1.0F;
        }

        public float getMaxHealth() {
            return isLiving ? ((net.minecraft.entity.EntityLivingBase) entity).getMaxHealth() : -1.0F;
        }

        public double distanceTo(Vec3 position) {
            return entity == null || position == null ? Double.MAX_VALUE
                    : entity.getDistance(position.x, position.y, position.z);
        }

        public double distanceToSq(Vec3 position) {
            return entity == null || position == null ? Double.MAX_VALUE
                    : entity.getDistanceSq(position.x, position.y, position.z);
        }

        public double distanceTo(Entity other) {
            return entity == null || other == null || other.entity == null
                    ? Double.POSITIVE_INFINITY : entity.getDistanceToEntity(other.entity);
        }

        public boolean isUsingItem() {
            return entity instanceof net.minecraft.entity.player.EntityPlayer
                    && ((net.minecraft.entity.player.EntityPlayer) entity).isUsingItem();
        }

        public boolean isBlocking() {
            return entity instanceof net.minecraft.entity.player.EntityPlayer
                    && ((net.minecraft.entity.player.EntityPlayer) entity).isBlocking();
        }

        public double getSpeed() {
            double x = getMotionX();
            double z = getMotionZ();
            return Math.sqrt(x * x + z * z);
        }

        public int getSwingProgress() {
            return isLiving ? ((net.minecraft.entity.EntityLivingBase) entity).swingProgressInt : -1;
        }

        public float getSwingProgressFloat() {
            return isLiving ? ((net.minecraft.entity.EntityLivingBase) entity).swingProgress : 0.0F;
        }

        public float getPrevSwingProgress() {
            return isLiving ? ((net.minecraft.entity.EntityLivingBase) entity).prevSwingProgress : 0.0F;
        }

        public int getFireResistance() {
            return entity == null ? 0 : entity.fireResistance;
        }

        public float getPrevYaw() {
            return entity == null ? 0.0F : entity.prevRotationYaw;
        }

        public float getPrevPitch() {
            return entity == null ? 0.0F : entity.prevRotationPitch;
        }

        public void setPrevYaw(float yaw) {
            if (entity != null) entity.prevRotationYaw = yaw;
        }

        public void setPrevPitch(float pitch) {
            if (entity != null) entity.prevRotationPitch = pitch;
        }

        public boolean isCreative() {
            return entity instanceof net.minecraft.entity.player.EntityPlayer
                    && ((net.minecraft.entity.player.EntityPlayer) entity).capabilities.isCreativeMode;
        }

        public int getHunger() {
            return entity instanceof net.minecraft.entity.player.EntityPlayer
                    ? ((net.minecraft.entity.player.EntityPlayer) entity).getFoodStats().getFoodLevel() : 0;
        }

        public float getSaturation() {
            return entity instanceof net.minecraft.entity.player.EntityPlayer
                    ? ((net.minecraft.entity.player.EntityPlayer) entity).getFoodStats().getSaturationLevel()
                    : 0.0F;
        }

        public float getAir() {
            return entity == null ? 0.0F : entity.getAir();
        }

        public boolean isInLiquid() {
            return entity != null && !entity.isOffsetPositionInLiquid(0.0D, 0.0D, 0.0D);
        }

        public boolean isOnLadder() {
            return entity != null && Utils.onLadder(entity);
        }

        public boolean isOnEdge() {
            return entity != null && Utils.onEdge(entity);
        }

        public Entity getFisher() {
            if (entity instanceof net.minecraft.entity.projectile.EntityFishHook) {
                return convert(((net.minecraft.entity.projectile.EntityFishHook) entity).angler);
            }
            return null;
        }

        public List<Object[]> getPotionEffects() {
            List<Object[]> result = new ArrayList<Object[]>();
            if (entity instanceof net.minecraft.entity.EntityLivingBase) {
                for (net.minecraft.potion.PotionEffect effect
                        : ((net.minecraft.entity.EntityLivingBase) entity).getActivePotionEffects()) {
                    result.add(new Object[]{effect.getPotionID(), effect.getEffectName(),
                            effect.getAmplifier(), effect.getDuration()});
                }
            }
            return result;
        }

        public ItemStack getArmorInSlot(int slot) {
            return entity instanceof net.minecraft.entity.player.EntityPlayer && slot >= 0 && slot < 4
                    ? ItemStack.of(((net.minecraft.entity.player.EntityPlayer) entity).inventory.armorInventory[slot])
                    : null;
        }

        public Vec3 getMotion() {
            return entity == null ? null : new Vec3(entity.motionX, entity.motionY, entity.motionZ);
        }

        public void setMotion(double x, double y, double z) {
            if (entity != null) {
                entity.motionX = x;
                entity.motionY = y;
                entity.motionZ = z;
            }
        }

        public Vec3 getServerPosition() {
            return entity == null ? null : new Vec3(
                    entity.serverPosX / 32.0D,
                    entity.serverPosY / 32.0D,
                    entity.serverPosZ / 32.0D);
        }

        public void setPosition(Vec3 position) {
            if (entity != null && position != null) {
                entity.setPosition(position.x, position.y, position.z);
            }
        }

        public void moveTo(Vec3 position) {
            if (position != null) moveTo(position.x, position.y, position.z);
        }

        public void moveTo(double x, double y, double z) {
            if (entity != null) entity.setPosition(x, y, z);
        }

        public void moveTo(Vec3 position, float yaw, float pitch) {
            if (position != null) moveTo(position.x, position.y, position.z, yaw, pitch);
        }

        public void moveTo(double x, double y, double z, float yaw, float pitch) {
            if (entity != null) entity.setPositionAndRotation(x, y, z, yaw, pitch);
        }

        public void moveTo(Vec3 position, float yaw, float pitch, int increments) {
            if (position != null) moveTo(position.x, position.y, position.z, yaw, pitch, increments);
        }

        public void moveTo(double x, double y, double z, float yaw, float pitch, int increments) {
            if (entity != null) entity.setPositionAndRotation2(x, y, z, yaw, pitch, increments, false);
        }

        public void setPosition(double x, double y, double z) {
            if (entity != null) entity.setPosition(x, y, z);
        }

        public void setSprinting(boolean sprinting) {
            if (entity != null) entity.setSprinting(sprinting);
        }

        public void setSneaking(boolean sneaking) {
            if (entity != null) entity.setSneaking(sneaking);
        }
    }

    public static final class ItemStack {
        private final net.minecraft.item.ItemStack handle;
        public net.minecraft.item.ItemStack itemStack;
        public String type;
        public String name;
        public String displayName;
        public int stackSize;
        public int maxStackSize;
        public int durability;
        public int maxDurability;
        public boolean isBlock;
        public int meta;

        public ItemStack(net.minecraft.item.ItemStack handle) {
            this.handle = handle;
            this.itemStack = handle;
            this.stackSize = handle == null ? 0 : handle.stackSize;
            this.maxStackSize = handle == null || handle.getItem() == null
                    ? 0 : handle.getMaxStackSize();
            this.meta = handle == null ? 0 : handle.getMetadata();
            this.durability = handle == null ? 0 : handle.getItemDamage();
            this.maxDurability = handle == null || handle.getItem() == null
                    ? 0 : handle.getMaxDamage();
            this.isBlock = handle != null && handle.getItem() instanceof ItemBlock;
            if (isBlock) {
                this.name = registryPath(((ItemBlock) handle.getItem()).getBlock());
            } else {
                this.name = handle == null || handle.getItem() == null
                        ? null
                        : stripNamespace(handle.getItem().getUnlocalizedName());
            }
            this.type = this.name;
            this.displayName = handle == null ? "" : handle.getDisplayName();
        }

        public ItemStack(net.minecraft.item.ItemStack handle, byte ignored) {
            this(handle);
        }

        public ItemStack(String itemName) {
            this(createRawStack(itemName));
        }

        private static net.minecraft.item.ItemStack createRawStack(String itemName) {
            if (itemName == null || itemName.trim().isEmpty()) {
                return null;
            }
            String value = itemName.trim();
            int meta = 0;
            int separator = value.lastIndexOf(':');
            if (separator > 0 && separator < value.length() - 1) {
                try {
                    meta = Integer.parseInt(value.substring(separator + 1));
                    value = value.substring(0, separator);
                }
                catch (NumberFormatException ignored) {
                }
            }
            net.minecraft.item.Item item = net.minecraft.item.Item.getByNameOrId(value);
            return item == null ? null : new net.minecraft.item.ItemStack(item, 1, meta);
        }

        static ItemStack of(net.minecraft.item.ItemStack stack) {
            return stack == null ? null : new ItemStack(stack);
        }

        public static ItemStack convert(net.minecraft.item.ItemStack stack) {
            return of(stack);
        }

        public String getName() {
            return name;
        }

        public int getStackSize() {
            return stackSize;
        }

        public int getCount() {
            return stackSize;
        }

        public int getMaxStackSize() {
            return maxStackSize;
        }

        public String getType() {
            return type;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isEmpty() {
            return stackSize <= 0 || handle == null;
        }

        public int getMetadata() {
            return meta;
        }

        public int getMeta() {
            return meta;
        }

        public int getDurability() {
            return durability;
        }

        public int getMaxDurability() {
            return maxDurability;
        }

        public List<String> getTooltip() {
            List<String> result = new ArrayList<String>();
            if (handle == null || Minecraft.getMinecraft().thePlayer == null) {
                return result;
            }
            return handle.getTooltip(Minecraft.getMinecraft().thePlayer, false);
        }

        public List<Object[]> getEnchantments() {
            return new ArrayList<Object[]>();
        }

        public net.minecraft.item.ItemStack getRaw() {
            return handle;
        }

        @Override
        public String toString() {
            return displayName == null ? String.valueOf(name) : displayName;
        }
    }

    public static final class Block {
        private final IBlockState state;
        private final net.minecraft.block.Block block;
        public String type;
        public String name;
        public boolean interactable;
        public int variant;
        public double height;
        public double width;
        public double length;
        public double x;
        public double y;
        public double z;

        public Block(net.minecraft.block.Block block, BlockPos position) {
            this(block == null ? null : block.getDefaultState(), position);
        }

        public Block(IBlockState state) {
            this(state, BlockPos.ORIGIN);
        }

        public Block(IBlockState state, BlockPos position) {
            this.state = state;
            this.block = state == null ? null : state.getBlock();
            this.x = position == null ? 0.0D : position.getX();
            this.y = position == null ? 0.0D : position.getY();
            this.z = position == null ? 0.0D : position.getZ();
            this.type = block == null ? "BlockAir" : block.getClass().getSimpleName();
            this.name = registryPath(block);
            this.interactable = block != null && BlockUtils.isInteractable(block);
            this.variant = state == null || block == null ? 0 : block.getMetaFromState(state);
            this.height = block == null ? 0.0D : block.getBlockBoundsMaxY() - block.getBlockBoundsMinY();
            this.width = block == null ? 0.0D : block.getBlockBoundsMaxX() - block.getBlockBoundsMinX();
            this.length = block == null ? 0.0D : block.getBlockBoundsMaxZ() - block.getBlockBoundsMinZ();
        }

        public Block(int x, int y, int z) {
            this(stateAt(x, y, z), new BlockPos(x, y, z));
        }

        public Block(String name) {
            this((net.minecraft.block.Block) net.minecraft.block.Block.blockRegistry.getObject(
                    name == null ? new net.minecraft.util.ResourceLocation("air")
                            : new net.minecraft.util.ResourceLocation(name)), BlockPos.ORIGIN);
        }

        public Block(Vec3 position) {
            this(position == null ? 0 : (int) Math.floor(position.x),
                    position == null ? 0 : (int) Math.floor(position.y),
                    position == null ? 0 : (int) Math.floor(position.z));
        }

        public String getId() {
            Object key = block == null ? null : net.minecraft.block.Block.blockRegistry.getNameForObject(block);
            return key == null ? "minecraft:air" : String.valueOf(key);
        }

        public int getNumericId() {
            return block == null ? 0 : net.minecraft.block.Block.getIdFromBlock(block);
        }

        public int getMeta() {
            return variant;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public boolean isInteractable() {
            return interactable;
        }

        public int getVariant() {
            return variant;
        }

        public double getHeight() {
            return height;
        }

        public double getWidth() {
            return width;
        }

        public double getLength() {
            return length;
        }

        public boolean isAir() {
            return block == null || block.getMaterial() == net.minecraft.block.material.Material.air;
        }

        public boolean isSolid() {
            return block != null && BlockUtils.isSolid(block);
        }

        public float getHardness() {
            return block == null ? 0.0F : block.getBlockHardness(Minecraft.getMinecraft().theWorld, new BlockPos(x, y, z));
        }

        public Vec3 getPosition() {
            return new Vec3(x, y, z);
        }

        private static IBlockState stateAt(int x, int y, int z) {
            Minecraft minecraft = Minecraft.getMinecraft();
            return minecraft == null || minecraft.theWorld == null
                    ? null : minecraft.theWorld.getBlockState(new BlockPos(x, y, z));
        }
    }

    public static final class NetworkPlayer {
        private final net.minecraft.client.network.NetworkPlayerInfo info;

        public NetworkPlayer(net.minecraft.client.network.NetworkPlayerInfo info) {
            this.info = info;
        }

        public String getCape() {
            if (info == null || info.getLocationCape() == null) {
                return null;
            }
            return info.getLocationCape().toString();
        }

        public String getDisplayName() {
            if (info == null) {
                return "";
            }
            net.minecraft.util.IChatComponent display = info.getDisplayName();
            return display == null ? getName() : display.getUnformattedText();
        }

        public String getName() {
            return info == null || info.getGameProfile() == null
                    ? null : info.getGameProfile().getName();
        }

        public int getPing() {
            return info == null ? 0 : info.getResponseTime();
        }

        public String getSkinData() {
            if (info == null || info.getLocationSkin() == null) {
                return null;
            }
            return info.getLocationSkin().toString();
        }

        public String getUUID() {
            return info == null || info.getGameProfile() == null
                    || info.getGameProfile().getId() == null
                    ? null : info.getGameProfile().getId().toString();
        }

        public static NetworkPlayer convert(net.minecraft.client.network.NetworkPlayerInfo info) {
            return info == null ? null : new NetworkPlayer(info);
        }

        public static void clearCache() {
        }
    }

    public static final class TileEntity {
        public String type;
        public String name;
        private final net.minecraft.tileentity.TileEntity tileEntity;

        public TileEntity(net.minecraft.tileentity.TileEntity tileEntity) {
            this.tileEntity = tileEntity;
            net.minecraft.block.Block block = tileEntity == null ? null : tileEntity.getBlockType();
            this.type = block == null ? "BlockAir" : block.getClass().getSimpleName();
            this.name = registryPath(block);
        }

        public Vec3 getPosition() {
            return tileEntity == null ? null : Vec3.of(tileEntity.getPos());
        }

        public Object[] getSkullData() {
            if (!(tileEntity instanceof net.minecraft.tileentity.TileEntitySkull)) {
                return null;
            }
            net.minecraft.tileentity.TileEntitySkull skull =
                    (net.minecraft.tileentity.TileEntitySkull) tileEntity;
            Object[] result = new Object[]{skull.getSkullType(), skull.getSkullRotation(), null, null, null};
            com.mojang.authlib.GameProfile profile = skull.getPlayerProfile();
            if (profile != null) {
                result[2] = profile.getName();
                result[3] = profile.getId();
            }
            return result;
        }
    }

    public static final class Image {
        public String url;
        public java.awt.image.BufferedImage bufferedImage;
        public int height;
        public int width;
        public int textureId;
        public boolean cached;

        public Image(String url, boolean load) {
            this.url = url;
        }

        public float[] getDimensions() {
            return new float[]{width, height};
        }

        public boolean isLoaded() {
            return bufferedImage != null;
        }

        public static void clearCache() {
        }

        @Override
        public String toString() {
            return url == null ? "" : url;
        }
    }

    public static class CPacket {
        final Packet<?> handle;
        public Packet<?> packet;
        public final String name;

        public CPacket(Packet<?> handle) {
            this.handle = handle;
            this.packet = handle;
            this.name = handle == null ? "" : handle.getClass().getSimpleName();
        }

        public String getName() {
            return name;
        }

        public Packet<?> getRaw() {
            return handle;
        }

        public Packet<?> convert() {
            return handle;
        }
    }

    public static final class C02 extends CPacket {
        public Entity entity;
        public final String action;
        public Vec3 hitVec;

        public C02(Entity entity, String action, Vec3 hitVec) {
            super(createPacket(entity, action, hitVec));
            this.entity = entity;
            this.action = action;
            this.hitVec = hitVec;
        }

        public C02(C02PacketUseEntity packet) {
            super(packet);
            this.action = packet.getAction() == null ? null : packet.getAction().name();
            this.hitVec = Vec3.of(packet.getHitVec());
            this.entity = null;
        }

        @Override
        public C02PacketUseEntity convert() {
            return handle instanceof C02PacketUseEntity
                    ? (C02PacketUseEntity) handle : null;
        }

        private static C02PacketUseEntity createPacket(Entity entity, String action, Vec3 hitVec) {
            if (entity == null || entity.entity == null) {
                return null;
            }
            C02PacketUseEntity.Action value;
            try {
                value = C02PacketUseEntity.Action.valueOf(action == null ? "ATTACK" : action.toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored) {
                value = C02PacketUseEntity.Action.ATTACK;
            }
            return value == C02PacketUseEntity.Action.INTERACT_AT && hitVec != null
                    ? new C02PacketUseEntity(entity.entity, hitVec.unwrap())
                    : new C02PacketUseEntity(entity.entity, value);
        }
    }

    public static final class C03 extends CPacket {
        public final boolean moving;
        public final Vec3 position;
        public final float yaw;
        public final float pitch;

        public C03(C03PacketPlayer packet) {
            super(packet);
            this.moving = packet.isMoving();
            this.position = moving
                    ? new Vec3(packet.getPositionX(), packet.getPositionY(), packet.getPositionZ())
                    : null;
            this.yaw = packet.getYaw();
            this.pitch = packet.getPitch();
        }

        public C03(boolean ground) {
            this(new C03PacketPlayer(ground));
        }

        public C03(Vec3 position, boolean ground) {
            this(position == null ? new C03PacketPlayer(ground)
                    : new C03PacketPlayer.C04PacketPlayerPosition(position.x, position.y, position.z, ground));
        }

        public C03(float yaw, float pitch, boolean ground) {
            this(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, ground));
        }

        public C03(Vec3 position, float yaw, float pitch, boolean ground) {
            this(new C03PacketPlayer.C06PacketPlayerPosLook(
                    position == null ? 0.0D : position.x,
                    position == null ? 0.0D : position.y,
                    position == null ? 0.0D : position.z,
                    yaw, pitch, ground));
        }

        @Override
        public C03PacketPlayer convert() {
            return handle instanceof C03PacketPlayer ? (C03PacketPlayer) handle : null;
        }
    }

    public static final class C07 extends CPacket {
        public final String status;
        public final Vec3 position;

        public C07(Vec3 position, String status, String facing) {
            this(new C07PacketPlayerDigging(parseAction(status),
                    position == null ? BlockPos.ORIGIN : Vec3.getBlockPos(position),
                    parseFacing(facing)));
        }

        public C07(C07PacketPlayerDigging packet) {
            super(packet);
            this.status = packet.getStatus() == null ? null : packet.getStatus().name();
            this.position = Vec3.of(packet.getPosition());
        }

        @Override
        public C07PacketPlayerDigging convert() {
            return handle instanceof C07PacketPlayerDigging
                    ? (C07PacketPlayerDigging) handle : null;
        }

        private static C07PacketPlayerDigging.Action parseAction(String status) {
            try {
                return C07PacketPlayerDigging.Action.valueOf(
                        status == null ? "RELEASE_USE_ITEM" : status.toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored) {
                return C07PacketPlayerDigging.Action.RELEASE_USE_ITEM;
            }
        }

        private static EnumFacing parseFacing(String facing) {
            try {
                return EnumFacing.valueOf(facing == null ? "DOWN" : facing.toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored) {
                return EnumFacing.DOWN;
            }
        }
    }

    public static final class C08 extends CPacket {
        public final int direction;
        public final Vec3 position;
        public final ItemStack itemStack;

        public C08(ItemStack itemStack, Vec3 position, int direction, Vec3 offset) {
            this(new C08PacketPlayerBlockPlacement(
                    position == null ? BlockPos.ORIGIN : Vec3.getBlockPos(position),
                    direction,
                    itemStack == null ? null : itemStack.getRaw(),
                    offset == null ? 0.0F : (float) offset.x,
                    offset == null ? 0.0F : (float) offset.y,
                    offset == null ? 0.0F : (float) offset.z));
        }

        public C08(C08PacketPlayerBlockPlacement packet) {
            super(packet);
            this.direction = packet.getPlacedBlockDirection();
            this.position = Vec3.of(packet.getPosition());
            this.itemStack = ItemStack.of(packet.getStack());
        }

        public Vec3 getOffset() {
            if (!(handle instanceof C08PacketPlayerBlockPlacement)) {
                return null;
            }
            C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) handle;
            return new Vec3(packet.getPlacedBlockOffsetX(), packet.getPlacedBlockOffsetY(),
                    packet.getPlacedBlockOffsetZ());
        }

        @Override
        public C08PacketPlayerBlockPlacement convert() {
            return handle instanceof C08PacketPlayerBlockPlacement
                    ? (C08PacketPlayerBlockPlacement) handle : null;
        }
    }

    public static final class C0B extends CPacket {
        public final String action;

        public C0B(String action, int horsePower) {
            this(Minecraft.getMinecraft().thePlayer == null ? null : new C0BPacketEntityAction(
                    Minecraft.getMinecraft().thePlayer,
                    parseAction(action), horsePower));
        }

        public C0B(C0BPacketEntityAction packet) {
            super(packet);
            this.action = packet.getAction() == null ? null : packet.getAction().name();
        }

        public int horsePower;

        @Override
        public C0BPacketEntityAction convert() {
            return handle instanceof C0BPacketEntityAction
                    ? (C0BPacketEntityAction) handle : null;
        }

        private static C0BPacketEntityAction.Action parseAction(String action) {
            try {
                return C0BPacketEntityAction.Action.valueOf(
                        action == null ? "START_SPRINTING" : action.toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored) {
                return C0BPacketEntityAction.Action.START_SPRINTING;
            }
        }
    }

    public static final class C01 extends CPacket {
        public String message;

        public C01(String message) {
            this(new net.minecraft.network.play.client.C01PacketChatMessage(message));
        }

        public C01(net.minecraft.network.play.client.C01PacketChatMessage packet) {
            super(packet);
            this.message = packet == null ? null : packet.getMessage();
        }

        @Override
        public net.minecraft.network.play.client.C01PacketChatMessage convert() {
            return handle instanceof net.minecraft.network.play.client.C01PacketChatMessage
                    ? (net.minecraft.network.play.client.C01PacketChatMessage) handle : null;
        }
    }

    public static final class C09 extends CPacket {
        public int slot;

        public C09(int slot) {
            this(new net.minecraft.network.play.client.C09PacketHeldItemChange(slot));
        }

        public C09(net.minecraft.network.play.client.C09PacketHeldItemChange packet, boolean ignored) {
            this(packet);
        }

        public C09(net.minecraft.network.play.client.C09PacketHeldItemChange packet) {
            super(packet);
            this.slot = packet == null ? 0 : packet.getSlotId();
        }

        @Override
        public net.minecraft.network.play.client.C09PacketHeldItemChange convert() {
            return handle instanceof net.minecraft.network.play.client.C09PacketHeldItemChange
                    ? (net.minecraft.network.play.client.C09PacketHeldItemChange) handle : null;
        }
    }

    public static final class C0A extends CPacket {
        public C0A() {
            this(new net.minecraft.network.play.client.C0APacketAnimation());
        }

        public C0A(net.minecraft.network.play.client.C0APacketAnimation packet) {
            super(packet);
        }

        @Override
        public net.minecraft.network.play.client.C0APacketAnimation convert() {
            return handle instanceof net.minecraft.network.play.client.C0APacketAnimation
                    ? (net.minecraft.network.play.client.C0APacketAnimation) handle : null;
        }
    }

    public static final class C0D extends CPacket {
        public int windowId;

        public C0D(int windowId) {
            this(new net.minecraft.network.play.client.C0DPacketCloseWindow(windowId));
        }

        public C0D(net.minecraft.network.play.client.C0DPacketCloseWindow packet) {
            super(packet);
            this.windowId = packet == null ? 0 : packet.getWindowId();
        }

        @Override
        public net.minecraft.network.play.client.C0DPacketCloseWindow convert() {
            return handle instanceof net.minecraft.network.play.client.C0DPacketCloseWindow
                    ? (net.minecraft.network.play.client.C0DPacketCloseWindow) handle : null;
        }
    }

    public static final class C0E extends CPacket {
        public int windowId;
        public int slot;
        public int button;
        public int mode;
        public short action;
        public ItemStack itemStack;

        public C0E(int windowId, int slot, int button, int mode, ItemStack itemStack) {
            this(new net.minecraft.network.play.client.C0EPacketClickWindow(
                    windowId, slot, button, mode,
                    itemStack == null ? null : itemStack.getRaw(), (short) 0));
        }

        public C0E(net.minecraft.network.play.client.C0EPacketClickWindow packet) {
            super(packet);
            this.windowId = packet == null ? 0 : packet.getWindowId();
            this.slot = packet == null ? 0 : packet.getSlotId();
            this.button = packet == null ? 0 : packet.getUsedButton();
            this.mode = packet == null ? 0 : packet.getMode();
            this.action = packet == null ? 0 : packet.getActionNumber();
            this.itemStack = packet == null ? null : ItemStack.of(packet.getClickedItem());
        }

        @Override
        public net.minecraft.network.play.client.C0EPacketClickWindow convert() {
            return handle instanceof net.minecraft.network.play.client.C0EPacketClickWindow
                    ? (net.minecraft.network.play.client.C0EPacketClickWindow) handle : null;
        }
    }

    public static final class C0F extends CPacket {
        public int windowId;
        public short uid;

        public C0F(int windowId, short uid) {
            this(new net.minecraft.network.play.client.C0FPacketConfirmTransaction(windowId, uid, true));
        }

        public C0F(net.minecraft.network.play.client.C0FPacketConfirmTransaction packet) {
            super(packet);
            this.windowId = packet == null ? 0 : packet.getWindowId();
            this.uid = packet == null ? 0 : packet.getUid();
        }

        @Override
        public net.minecraft.network.play.client.C0FPacketConfirmTransaction convert() {
            return handle instanceof net.minecraft.network.play.client.C0FPacketConfirmTransaction
                    ? (net.minecraft.network.play.client.C0FPacketConfirmTransaction) handle : null;
        }
    }

    public static final class C10 extends CPacket {
        public int slot;
        public ItemStack itemStack;

        public C10(int slot, ItemStack itemStack) {
            this(new net.minecraft.network.play.client.C10PacketCreativeInventoryAction(
                    slot, itemStack == null ? null : itemStack.getRaw()));
        }

        public C10(net.minecraft.network.play.client.C10PacketCreativeInventoryAction packet) {
            super(packet);
            this.slot = packet == null ? 0 : packet.getSlotId();
            this.itemStack = packet == null ? null : ItemStack.of(packet.getStack());
        }

        @Override
        public net.minecraft.network.play.client.C10PacketCreativeInventoryAction convert() {
            return handle instanceof net.minecraft.network.play.client.C10PacketCreativeInventoryAction
                    ? (net.minecraft.network.play.client.C10PacketCreativeInventoryAction) handle : null;
        }
    }

    public static final class C13 extends CPacket {
        public boolean invulnerable;
        public boolean flying;
        public boolean allowFlying;
        public boolean creativeMode;
        public float flySpeed;
        public float walkSpeed;

        public C13(boolean invulnerable, boolean flying, boolean allowFlying,
                   boolean creativeMode, float flySpeed, float walkSpeed) {
            this(createAbilities(invulnerable, flying, allowFlying, creativeMode, flySpeed, walkSpeed));
        }

        public C13(net.minecraft.network.play.client.C13PacketPlayerAbilities packet) {
            super(packet);
            this.invulnerable = packet != null && packet.isInvulnerable();
            this.flying = packet != null && packet.isFlying();
            this.allowFlying = packet != null && packet.isAllowFlying();
            this.creativeMode = packet != null && packet.isCreativeMode();
            this.flySpeed = 0.05F;
            this.walkSpeed = 0.1F;
        }

        @Override
        public net.minecraft.network.play.client.C13PacketPlayerAbilities convert() {
            return handle instanceof net.minecraft.network.play.client.C13PacketPlayerAbilities
                    ? (net.minecraft.network.play.client.C13PacketPlayerAbilities) handle : null;
        }

        private static net.minecraft.network.play.client.C13PacketPlayerAbilities createAbilities(
                boolean invulnerable, boolean flying, boolean allowFlying, boolean creativeMode,
                float flySpeed, float walkSpeed) {
            net.minecraft.entity.player.PlayerCapabilities capabilities =
                    new net.minecraft.entity.player.PlayerCapabilities();
            capabilities.disableDamage = invulnerable;
            capabilities.isFlying = flying;
            capabilities.allowFlying = allowFlying;
            capabilities.isCreativeMode = creativeMode;
            capabilities.setFlySpeed(flySpeed);
            capabilities.setPlayerWalkSpeed(walkSpeed);
            return new net.minecraft.network.play.client.C13PacketPlayerAbilities(capabilities);
        }
    }

    public static final class C16 extends CPacket {
        public String status;

        public C16(String status) {
            this(new net.minecraft.network.play.client.C16PacketClientStatus(parseStatus(status)));
        }

        public C16(net.minecraft.network.play.client.C16PacketClientStatus packet) {
            super(packet);
            this.status = packet == null || packet.getStatus() == null ? null : packet.getStatus().name();
        }

        @Override
        public net.minecraft.network.play.client.C16PacketClientStatus convert() {
            return handle instanceof net.minecraft.network.play.client.C16PacketClientStatus
                    ? (net.minecraft.network.play.client.C16PacketClientStatus) handle : null;
        }

        private static net.minecraft.network.play.client.C16PacketClientStatus.EnumState parseStatus(String status) {
            try {
                return net.minecraft.network.play.client.C16PacketClientStatus.EnumState.valueOf(
                        status == null ? "REQUEST_STATS" : status.toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored) {
                return net.minecraft.network.play.client.C16PacketClientStatus.EnumState.REQUEST_STATS;
            }
        }
    }

    public static class SPacket {
        final Packet<?> handle;
        public Packet<?> packet;
        public final String name;

        public SPacket(Packet<?> handle) {
            this.handle = handle;
            this.packet = handle;
            this.name = handle == null ? "" : handle.getClass().getSimpleName();
        }

        public String getName() {
            return name;
        }

        public Packet<?> getRaw() {
            return handle;
        }

        public Packet<?> convert() {
            return handle;
        }
    }

    public static final class S23 extends SPacket {
        public final Vec3 position;

        public S23(S23PacketBlockChange packet) {
            super(packet);
            this.position = Vec3.of(packet.getBlockPosition());
            this.block = new Block(packet.getBlockState(), packet.getBlockPosition());
        }

        public S23(Vec3 position) {
            super(null);
            this.position = position;
            this.block = null;
        }

        public S23(S23PacketBlockChange packet, byte ignored) {
            this(packet);
        }

        public final Block block;

        @Override
        public S23PacketBlockChange convert() {
            return handle instanceof S23PacketBlockChange
                    ? (S23PacketBlockChange) handle : null;
        }
    }

    public static final class S02 extends SPacket {
        public byte type;
        public String message;

        public S02(net.minecraft.network.play.server.S02PacketChat packet) {
            super(packet);
        }

        public S02(byte type, String message) {
            super(null);
            this.type = type;
            this.message = message;
        }
    }

    public static final class S04 extends SPacket {
        public int entityId;
        public int slot;
        public ItemStack item;

        public S04(net.minecraft.network.play.server.S04PacketEntityEquipment packet) {
            super(packet);
        }

        public S04(int entityId, int slot, ItemStack item) {
            super(null);
            this.entityId = entityId;
            this.slot = slot;
            this.item = item;
        }
    }

    public static final class S06 extends SPacket {
        public float health;
        public float saturation;
        public int food;

        public S06(net.minecraft.network.play.server.S06PacketUpdateHealth packet) {
            super(packet);
        }

        public S06(float health, float saturation, int food) {
            super(null);
            this.health = health;
            this.saturation = saturation;
            this.food = food;
        }
    }

    public static final class S08 extends SPacket {
        public Vec3 position;
        public float yaw;
        public float pitch;
        public java.util.Set<String> enumFlags;

        public S08(net.minecraft.network.play.server.S08PacketPlayerPosLook packet) {
            super(packet);
        }

        public S08(Vec3 position, float yaw, float pitch, java.util.Set<String> enumFlags) {
            super(null);
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
            this.enumFlags = enumFlags;
        }
    }

    public static final class S0B extends SPacket {
        public int entityId;
        public int type;

        public S0B(net.minecraft.network.play.server.S0BPacketAnimation packet) {
            super(packet);
        }

        public S0B(int entityId, int type) {
            super(null);
            this.entityId = entityId;
            this.type = type;
        }
    }

    public static final class S12 extends SPacket {
        public int entityId;
        public Vec3 motion;

        public S12(net.minecraft.network.play.server.S12PacketEntityVelocity packet) {
            super(packet);
        }

        public S12(int entityId, Vec3 motion) {
            super(null);
            this.entityId = entityId;
            this.motion = motion;
        }
    }

    public static final class S14 extends SPacket {
        public int entityId;
        public byte posX;
        public byte posY;
        public byte posZ;
        public byte yaw;
        public byte pitch;
        public boolean onGround;
        public boolean rotating;

        public S14(net.minecraft.network.play.server.S14PacketEntity packet) {
            super(packet);
        }

        public S14(int entityId, byte posX, byte posY, byte posZ, byte yaw, byte pitch, boolean onGround) {
            super(null);
            this.entityId = entityId;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.yaw = yaw;
            this.pitch = pitch;
            this.onGround = onGround;
            this.rotating = true;
        }

        public S14(int entityId, byte posX, byte posY, byte posZ, boolean onGround) {
            this(entityId, posX, posY, posZ, (byte) 0, (byte) 0, onGround);
            this.rotating = false;
        }

        public S14(int entityId, byte posX, byte posY, boolean onGround) {
            this(entityId, posX, posY, (byte) 0, onGround);
        }
    }

    public static final class S25 extends SPacket {
        public int entityId;
        public Block block;
        public int progress;

        public S25(net.minecraft.network.play.server.S25PacketBlockBreakAnim packet) {
            super(packet);
        }

        public S25(int entityId, Block block, int progress) {
            super(null);
            this.entityId = entityId;
            this.block = block;
            this.progress = progress;
        }

        public S25(int entityId, Vec3 position, int progress) {
            this(entityId, position == null ? null : new Block(position), progress);
        }
    }

    public static final class S27 extends SPacket {
        public float strength;
        public Vec3 position;
        public Vec3 motion;
        public List<Vec3> affectedBlockPositions;

        public S27(net.minecraft.network.play.server.S27PacketExplosion packet) {
            super(packet);
        }

        public S27(float strength, Vec3 position, Vec3 motion, List<Vec3> affectedBlockPositions) {
            super(null);
            this.strength = strength;
            this.position = position;
            this.motion = motion;
            this.affectedBlockPositions = affectedBlockPositions;
        }
    }

    public static final class S29 extends SPacket {
        public String sound;
        public Vec3 position;
        public float volume;
        public float pitch;

        public S29(net.minecraft.network.play.server.S29PacketSoundEffect packet) {
            super(packet);
        }

        public S29(String sound, Vec3 position, float volume, float pitch) {
            super(null);
            this.sound = sound;
            this.position = position;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public static final class S2A extends SPacket {
        public String type;
        public Vec3 position;
        public Vec3 offset;
        public float speed;
        public int count;
        public boolean longDistance;
        public int[] args;

        public S2A(net.minecraft.network.play.server.S2APacketParticles packet) {
            super(packet);
        }

        public S2A(String type, boolean longDistance, Vec3 position, Vec3 offset,
                   float speed, int count, int[] args) {
            super(null);
            this.type = type;
            this.longDistance = longDistance;
            this.position = position;
            this.offset = offset;
            this.speed = speed;
            this.count = count;
            this.args = args;
        }
    }

    public static final class S2F extends SPacket {
        public int windowId;
        public int slot;
        public ItemStack itemStack;

        public S2F(net.minecraft.network.play.server.S2FPacketSetSlot packet) {
            super(packet);
        }

        public S2F(int windowId, int slot, ItemStack itemStack) {
            super(null);
            this.windowId = windowId;
            this.slot = slot;
            this.itemStack = itemStack;
        }
    }

    public static final class S3A extends SPacket {
        public String[] matches;

        public S3A(net.minecraft.network.play.server.S3APacketTabComplete packet) {
            super(packet);
        }

        public S3A(String[] matches) {
            super(null);
            this.matches = matches;
        }
    }

    public static final class S3E extends SPacket {
        public String name;
        public String displayName;
        public String prefix;
        public String suffix;
        public String nametagVisibility;
        public java.util.Collection<String> playerList;
        public int action;
        public int friendlyFlags;
        public int color;

        public S3E(net.minecraft.network.play.server.S3EPacketTeams packet) {
            super(packet);
        }

        public S3E(Packet<?> packet, String name, String displayName, String prefix, String suffix,
                   String nametagVisibility, java.util.Collection<String> playerList,
                   int action, int friendlyFlags, int color) {
            super(packet);
            this.name = name;
            this.displayName = displayName;
            this.prefix = prefix;
            this.suffix = suffix;
            this.nametagVisibility = nametagVisibility;
            this.playerList = playerList;
            this.action = action;
            this.friendlyFlags = friendlyFlags;
            this.color = color;
        }
    }

    public static final class S45 extends SPacket {
        public String type;
        public String message;

        public S45(net.minecraft.network.play.server.S45PacketTitle packet) {
            super(packet);
        }

        public S45(String type, String message, int fadeIn, int stay, int fadeOut) {
            super(null);
            this.type = type;
            this.message = message;
        }
    }

    public static final class S48 extends SPacket {
        public String url;
        public String hash;

        public S48(net.minecraft.network.play.server.S48PacketResourcePackSend packet) {
            super(packet);
        }

        public S48(String url, String hash) {
            super(null);
            this.url = url;
            this.hash = hash;
        }
    }

    public static CPacket wrapClientPacket(Packet<?> packet) {
        if (packet instanceof net.minecraft.network.play.client.C01PacketChatMessage) return new C01((net.minecraft.network.play.client.C01PacketChatMessage) packet);
        if (packet instanceof C02PacketUseEntity) return new C02((C02PacketUseEntity) packet);
        if (packet instanceof C03PacketPlayer) return new C03((C03PacketPlayer) packet);
        if (packet instanceof C07PacketPlayerDigging) return new C07((C07PacketPlayerDigging) packet);
        if (packet instanceof C08PacketPlayerBlockPlacement) return new C08((C08PacketPlayerBlockPlacement) packet);
        if (packet instanceof C0BPacketEntityAction) return new C0B((C0BPacketEntityAction) packet);
        if (packet instanceof net.minecraft.network.play.client.C09PacketHeldItemChange) return new C09((net.minecraft.network.play.client.C09PacketHeldItemChange) packet);
        if (packet instanceof net.minecraft.network.play.client.C0APacketAnimation) return new C0A((net.minecraft.network.play.client.C0APacketAnimation) packet);
        if (packet instanceof net.minecraft.network.play.client.C0DPacketCloseWindow) return new C0D((net.minecraft.network.play.client.C0DPacketCloseWindow) packet);
        if (packet instanceof net.minecraft.network.play.client.C0EPacketClickWindow) return new C0E((net.minecraft.network.play.client.C0EPacketClickWindow) packet);
        if (packet instanceof net.minecraft.network.play.client.C0FPacketConfirmTransaction) return new C0F((net.minecraft.network.play.client.C0FPacketConfirmTransaction) packet);
        if (packet instanceof net.minecraft.network.play.client.C10PacketCreativeInventoryAction) return new C10((net.minecraft.network.play.client.C10PacketCreativeInventoryAction) packet);
        if (packet instanceof net.minecraft.network.play.client.C13PacketPlayerAbilities) return new C13((net.minecraft.network.play.client.C13PacketPlayerAbilities) packet);
        if (packet instanceof net.minecraft.network.play.client.C16PacketClientStatus) return new C16((net.minecraft.network.play.client.C16PacketClientStatus) packet);
        return new CPacket(packet);
    }

    public static SPacket wrapServerPacket(Packet<?> packet) {
        if (packet instanceof net.minecraft.network.play.server.S02PacketChat) return new S02((net.minecraft.network.play.server.S02PacketChat) packet);
        if (packet instanceof net.minecraft.network.play.server.S04PacketEntityEquipment) return new S04((net.minecraft.network.play.server.S04PacketEntityEquipment) packet);
        if (packet instanceof net.minecraft.network.play.server.S06PacketUpdateHealth) return new S06((net.minecraft.network.play.server.S06PacketUpdateHealth) packet);
        if (packet instanceof net.minecraft.network.play.server.S08PacketPlayerPosLook) return new S08((net.minecraft.network.play.server.S08PacketPlayerPosLook) packet);
        if (packet instanceof net.minecraft.network.play.server.S0BPacketAnimation) return new S0B((net.minecraft.network.play.server.S0BPacketAnimation) packet);
        if (packet instanceof net.minecraft.network.play.server.S12PacketEntityVelocity) return new S12((net.minecraft.network.play.server.S12PacketEntityVelocity) packet);
        if (packet instanceof net.minecraft.network.play.server.S14PacketEntity) return new S14((net.minecraft.network.play.server.S14PacketEntity) packet);
        if (packet instanceof S23PacketBlockChange) return new S23((S23PacketBlockChange) packet);
        if (packet instanceof net.minecraft.network.play.server.S25PacketBlockBreakAnim) return new S25((net.minecraft.network.play.server.S25PacketBlockBreakAnim) packet);
        if (packet instanceof net.minecraft.network.play.server.S27PacketExplosion) return new S27((net.minecraft.network.play.server.S27PacketExplosion) packet);
        if (packet instanceof net.minecraft.network.play.server.S29PacketSoundEffect) return new S29((net.minecraft.network.play.server.S29PacketSoundEffect) packet);
        if (packet instanceof net.minecraft.network.play.server.S2APacketParticles) return new S2A((net.minecraft.network.play.server.S2APacketParticles) packet);
        if (packet instanceof net.minecraft.network.play.server.S2FPacketSetSlot) return new S2F((net.minecraft.network.play.server.S2FPacketSetSlot) packet);
        if (packet instanceof net.minecraft.network.play.server.S3APacketTabComplete) return new S3A((net.minecraft.network.play.server.S3APacketTabComplete) packet);
        if (packet instanceof net.minecraft.network.play.server.S3EPacketTeams) return new S3E((net.minecraft.network.play.server.S3EPacketTeams) packet);
        if (packet instanceof net.minecraft.network.play.server.S45PacketTitle) return new S45((net.minecraft.network.play.server.S45PacketTitle) packet);
        if (packet instanceof net.minecraft.network.play.server.S48PacketResourcePackSend) return new S48((net.minecraft.network.play.server.S48PacketResourcePackSend) packet);
        return new SPacket(packet);
    }

    public static final class PlayerState {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public boolean onGround;
        public boolean isSprinting;
        public boolean isSneaking;

        public PlayerState() {
        }

        public PlayerState(PreMotionEvent event, byte ignored) {
            if (event != null) {
                x = event.getPosX();
                y = event.getPosY();
                z = event.getPosZ();
                yaw = event.getYaw();
                pitch = event.getPitch();
                onGround = event.isOnGround();
                isSprinting = event.isSprinting();
                isSneaking = event.isSneaking();
            }
        }

        public PlayerState(Object[] values) {
            if (values == null) {
                return;
            }
            x = number(values, 0, 0.0D);
            y = number(values, 1, 0.0D);
            z = number(values, 2, 0.0D);
            yaw = (float) number(values, 3, 0.0D);
            pitch = (float) number(values, 4, 0.0D);
            onGround = bool(values, 5);
            isSprinting = bool(values, 6);
            isSneaking = bool(values, 7);
        }

        public Object[] asArray() {
            return new Object[]{x, y, z, yaw, pitch, onGround, isSprinting, isSneaking};
        }

        public boolean equals(PlayerState other) {
            return other != null && x == other.x && y == other.y && z == other.z
                    && yaw == other.yaw && pitch == other.pitch
                    && onGround == other.onGround && isSprinting == other.isSprinting
                    && isSneaking == other.isSneaking;
        }

        private static double number(Object[] values, int index, double fallback) {
            return index < values.length && values[index] instanceof Number
                    ? ((Number) values[index]).doubleValue() : fallback;
        }

        private static boolean bool(Object[] values, int index) {
            return index < values.length && values[index] instanceof Boolean
                    && (Boolean) values[index];
        }
    }

    public static final class MovementInput {
        public float forward;
        public float strafe;
        public boolean jump;
        public boolean sneak;
        public double sneakSlowDownMultiplier = 0.3D;

        public MovementInput() {
        }

        public MovementInput(float forward, float strafe, boolean jump, boolean sneak,
                             double sneakSlowDownMultiplier) {
            this.forward = forward;
            this.strafe = strafe;
            this.jump = jump;
            this.sneak = sneak;
            this.sneakSlowDownMultiplier = sneakSlowDownMultiplier;
        }

        public MovementInput(float forward, float strafe, boolean jump, boolean sneak) {
            this(forward, strafe, jump, sneak, 0.3D);
        }

        public MovementInput(PrePlayerInputEvent event, byte ignored) {
            this(event == null ? 0.0F : event.getForward(),
                    event == null ? 0.0F : event.getStrafe(),
                    event != null && event.isJump(),
                    event != null && event.isSneak(),
                    event == null ? 0.3D : event.getSneakSlowDownMultiplier());
        }

        public MovementInput(Object[] values) {
            this(values == null ? 0.0F : (float) number(values, 0, 0.0D),
                    values == null ? 0.0F : (float) number(values, 1, 0.0D),
                    values != null && bool(values, 2),
                    values != null && bool(values, 3),
                    values == null ? 0.3D : number(values, 4, 0.3D));
        }

        public Object[] asArray() {
            return new Object[]{forward, strafe, jump, sneak};
        }

        public boolean equals(MovementInput other) {
            return other != null && forward == other.forward && strafe == other.strafe
                    && jump == other.jump && sneak == other.sneak;
        }

        private static double number(Object[] values, int index, double fallback) {
            return index < values.length && values[index] instanceof Number
                    ? ((Number) values[index]).doubleValue() : fallback;
        }

        private static boolean bool(Object[] values, int index) {
            return index < values.length && values[index] instanceof Boolean
                    && (Boolean) values[index];
        }
    }

    public static final class Message {
        public final net.minecraft.util.ChatComponentText component;

        public Message(String text) {
            this.component = new net.minecraft.util.ChatComponentText(color(text));
        }

        public Message append(String text) {
            component.appendText(text == null ? "" : text);
            return this;
        }

        public void appendStyle(String text, String color, String clickAction, String clickValue) {
            append(text);
        }

        public List<Message> getSiblings() {
            List<Message> result = new ArrayList<Message>();
            for (net.minecraft.util.IChatComponent sibling : component.getSiblings()) {
                result.add(new Message(sibling.getUnformattedText()));
            }
            return result;
        }

        public String getStyle() {
            return component.getChatStyle() == null
                    ? "" : component.getChatStyle().getFormattingCode();
        }

        public String getText() {
            return component.getUnformattedText();
        }

        @Override
        public String toString() {
            return getText();
        }

        public net.minecraft.util.IChatComponent unwrap() {
            return component;
        }
    }

    public static final class ClientApi {
        private final Minecraft mc = Minecraft.getMinecraft();

        public long nanoTime() {
            return System.nanoTime();
        }

        public int getFPS() {
            return Minecraft.getDebugFPS();
        }

        public void chat(String message) {
            if (mc.thePlayer != null) {
                mc.thePlayer.sendChatMessage(message == null ? "" : message);
            }
        }

        public void sendChatMessage(String message) {
            chat(message);
        }

        public boolean allowFlying() {
            return mc.thePlayer != null && mc.thePlayer.capabilities.allowFlying;
        }

        public void setAllowFlying(boolean allowed) {
            if (mc.thePlayer != null) {
                mc.thePlayer.capabilities.allowFlying = allowed;
            }
        }

        public boolean isDiagonal() {
            return mc.thePlayer != null
                    && mc.thePlayer.movementInput != null
                    && mc.thePlayer.movementInput.moveForward != 0.0F
                    && mc.thePlayer.movementInput.moveStrafe != 0.0F;
        }

        public void print(Object value) {
            print(value == null ? "null" : String.valueOf(value));
        }

        public Entity getPlayer() {
            return mc.thePlayer == null ? null : new Entity(mc.thePlayer);
        }

        public long time() {
            return System.currentTimeMillis();
        }

        public String getUser() {
            return mc.thePlayer == null ? "" : mc.thePlayer.getName();
        }

        public int getUID() {
            return mc.thePlayer == null ? 0 : mc.thePlayer.getEntityId();
        }

        public String getUUID() {
            return mc.thePlayer == null || mc.thePlayer.getUniqueID() == null
                    ? null : mc.thePlayer.getUniqueID().toString();
        }

        public boolean addEnemy(String name) {
            return name != null && Utils.addEnemy(name);
        }

        public boolean removeEnemy(String name) {
            return name != null && Utils.removeEnemy(name);
        }

        public boolean addFriend(String name) {
            return name != null && Utils.addFriend(name);
        }

        public boolean removeFriend(String name) {
            return name != null && Utils.removeFriend(name);
        }

        public boolean isFriend(String name) {
            return name != null && Utils.isFriended(name);
        }

        public boolean isEnemy(String name) {
            return name != null && Utils.isEnemy(name);
        }

        public void async(Runnable runnable) {
            if (runnable == null) {
                return;
            }
            Thread thread = new Thread(runnable, "Mimeow-Script");
            thread.setDaemon(true);
            thread.start();
        }

        public boolean isSinglePlayer() {
            return mc.isSingleplayer();
        }

        public boolean isSpectator() {
            return mc.thePlayer != null && mc.thePlayer.isSpectator();
        }

        public boolean isFlying() {
            return mc.thePlayer != null && mc.thePlayer.capabilities.isFlying;
        }

        public void setFlying(boolean flying) {
            if (mc.thePlayer != null) {
                mc.thePlayer.capabilities.isFlying = flying;
            }
        }

        public boolean allowEditing() {
            return mc.thePlayer != null && mc.thePlayer.capabilities.allowEdit;
        }

        public void attack(Entity entity) {
            if (mc.playerController != null && mc.thePlayer != null
                    && entity != null && entity.entity != null) {
                mc.playerController.attackEntity(mc.thePlayer, entity.entity);
            }
        }

        public String getTitle() {
            return mc.ingameGUI == null ? "" : mc.ingameGUI.getDisplayedTitle();
        }

        public String getSubTitle() {
            return mc.ingameGUI == null ? "" : mc.ingameGUI.getDisplayedSubTitle();
        }

        public void setTabName(String username, String tabName) {
            if (mc.getNetHandler() == null || username == null) {
                return;
            }
            for (net.minecraft.client.network.NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                if (info != null && info.getGameProfile() != null
                        && username.equalsIgnoreCase(info.getGameProfile().getName())) {
                    info.setDisplayName(tabName == null ? null
                            : new net.minecraft.util.ChatComponentText(color(tabName)));
                    return;
                }
            }
        }

        public void removePotionEffect(int potionId) {
            if (mc.thePlayer != null) {
                mc.thePlayer.removePotionEffect(potionId);
            }
        }

        public void setTimer(float timer) {
            mc.getTimer().timerSpeed = timer;
        }

        public void setTimer(double timer) {
            setTimer((float) timer);
        }

        public void setMotion(double x, double y, double z) {
            if (mc.thePlayer != null) {
                mc.thePlayer.motionX = x;
                mc.thePlayer.motionY = y;
                mc.thePlayer.motionZ = z;
            }
        }

        public void setSpeed(double speed) {
            if (mc.thePlayer != null) {
                Utils.setSpeed(speed);
            }
        }

        public void setSpeed(double speed, boolean checkMoving) {
            if (mc.thePlayer != null) {
                Utils.setSpeed(speed, checkMoving);
            }
        }

        public void setRotations(float yaw, float pitch) {
            RotationHelper.get().setRotations(yaw, pitch);
        }

        public void setYaw(float yaw) {
            RotationHelper.get().setYaw(yaw);
        }

        public void setPitch(float pitch) {
            RotationHelper.get().setPitch(pitch);
        }

        public float[] getRotationsToEntity(Entity entity) {
            return entity == null || entity.entity == null
                    ? null : Utils.getRotationsOld(entity.entity);
        }

        public String getServerIP() {
            return mc.getCurrentServerData() == null ? "" : mc.getCurrentServerData().serverIP;
        }

        public String getScreen() {
            return mc.currentScreen == null ? "" : mc.currentScreen.getClass().getSimpleName();
        }

        public boolean isCreative() {
            return mc.thePlayer != null && mc.thePlayer.capabilities.isCreativeMode;
        }

        public void setJump(boolean value) {
            if (mc.thePlayer != null && mc.thePlayer.movementInput != null) {
                mc.thePlayer.movementInput.jump = value;
            }
        }

        public boolean isSneak() {
            return mc.thePlayer != null && mc.thePlayer.isSneaking();
        }

        public boolean isMoving() {
            return mc.thePlayer != null && (Math.abs(mc.thePlayer.movementInput.moveForward) > 1.0E-3F
                    || Math.abs(mc.thePlayer.movementInput.moveStrafe) > 1.0E-3F);
        }

        public float getForward() {
            return mc.thePlayer == null ? 0.0F : mc.thePlayer.movementInput.moveForward;
        }

        public float getStrafe() {
            return mc.thePlayer == null ? 0.0F : mc.thePlayer.movementInput.moveStrafe;
        }

        public Vec3 getMotion() {
            return mc.thePlayer == null ? null : new Vec3(mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ);
        }

        public void setForward(float value) {
            if (mc.thePlayer != null) mc.thePlayer.movementInput.moveForward = value;
        }

        public void setStrafe(float value) {
            if (mc.thePlayer != null) mc.thePlayer.movementInput.moveStrafe = value;
        }

        public void setSneak(boolean value) {
            if (mc.thePlayer != null) mc.thePlayer.movementInput.sneak = value;
        }

        public void setSneaking(boolean value) {
            setSneak(value);
        }

        public void setSprinting(boolean value) {
            if (mc.thePlayer != null) mc.thePlayer.setSprinting(value);
        }

        public int[] getDisplaySize() {
            ScaledResolution resolution = new ScaledResolution(mc);
            return new int[]{resolution.getScaledWidth(), resolution.getScaledHeight(), resolution.getScaleFactor()};
        }

        public void print(String text) {
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(color(text)));
            }
        }

        public void print(Message message) {
            if (mc.thePlayer != null && message != null) {
                mc.thePlayer.addChatMessage(message.unwrap());
            }
        }

        public void log(Object value) {
            print(value);
        }

        public void playSound(String sound, float volume, float pitch) {
            if (mc.thePlayer != null) mc.thePlayer.playSound(sound, volume, pitch);
        }

        public void ping() {
            playSound("note.pling", 1.0F, 1.0F);
        }

        public void swing() {
            if (mc.thePlayer != null) mc.thePlayer.swingItem();
        }

        public void swingReset() {
            if (mc.thePlayer != null) {
                mc.thePlayer.swingProgressInt = -1;
                mc.thePlayer.isSwingInProgress = true;
            }
        }

        public void jump() {
            if (mc.thePlayer != null) {
                mc.thePlayer.jump();
            }
        }

        public void setJumping(boolean value) {
            setJump(value);
        }

        public boolean isJump() {
            return mc.thePlayer != null && mc.thePlayer.movementInput != null
                    && mc.thePlayer.movementInput.jump;
        }

        public boolean isRiding() {
            return mc.thePlayer != null && mc.thePlayer.isRiding();
        }

        public void setRidingEntity(Entity entity) {
            if (mc.thePlayer != null) {
                mc.thePlayer.mountEntity(entity == null ? null : entity.entity);
            }
        }

        public void multiplyMotion(double factor) {
            if (mc.thePlayer != null) {
                mc.thePlayer.motionX *= factor;
                mc.thePlayer.motionY *= factor;
                mc.thePlayer.motionZ *= factor;
            }
        }

        public void sleep(long milliseconds) {
            if (milliseconds <= 0L) return;
            try {
                Thread.sleep(milliseconds);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        public void sleep(int milliseconds) {
            sleep((long) milliseconds);
        }

        public void disconnect() {
            if (mc.theWorld != null) {
                mc.theWorld.sendQuittingDisconnectingPacket();
            }
            mc.loadWorld(null);
            mc.displayGuiScreen(null);
        }

        public void closeScreen() {
            mc.displayGuiScreen(null);
        }

        public boolean inFocus() {
            return mc.currentScreen == null && mc.inGameHasFocus;
        }

        public void dropItem(boolean dropStack) {
            if (mc.thePlayer != null) {
                mc.thePlayer.dropOneItem(dropStack);
            }
        }

        public void setRenderArmPitch(float value) {
            if (mc.thePlayer != null) mc.thePlayer.renderArmPitch = value;
        }

        public float getRenderArmPitch() {
            return mc.thePlayer == null ? 0.0F : mc.thePlayer.renderArmPitch;
        }

        public void setRenderArmYaw(float value) {
            if (mc.thePlayer != null) mc.thePlayer.renderArmYaw = value;
        }

        public float getRenderArmYaw() {
            return mc.thePlayer == null ? 0.0F : mc.thePlayer.renderArmYaw;
        }

        public float getEquippedProgress() {
            return mc.getItemRenderer() == null ? 0.0F : mc.getItemRenderer().getEquippedProgress();
        }

        public List<String[]> getResourcePacks() {
            List<String[]> result = new ArrayList<String[]>();
            if (mc.getResourcePackRepository() == null) {
                return result;
            }
            for (net.minecraft.client.resources.ResourcePackRepository.Entry entry
                    : mc.getResourcePackRepository().getRepositoryEntries()) {
                result.add(new String[]{entry.getResourcePackName(), entry.getResourcePackName()});
            }
            return result;
        }

        public long getTotalMemory() {
            return Runtime.getRuntime().totalMemory();
        }

        public long getFreeMemory() {
            return Runtime.getRuntime().freeMemory();
        }

        public long getMaxMemory() {
            return Runtime.getRuntime().maxMemory();
        }

        public String getRecordPlaying() {
            return mc.ingameGUI == null ? "" : mc.ingameGUI.getRecordPlaying();
        }

        public void setItemInUseCount(int count) {
            if (mc.thePlayer != null) mc.thePlayer.setItemInUseCount(count);
        }

        public int getItemInUseCount() {
            return mc.thePlayer == null ? 0 : mc.thePlayer.getItemInUseCount();
        }

        public int getItemInUseDuration() {
            return mc.thePlayer == null ? 0 : mc.thePlayer.getItemInUseDuration();
        }

        public void enableMovementFix() {
            RotationHelper.get().forceMovementFix = true;
        }

        public void disableMovementFix() {
            RotationHelper.get().forceMovementFix = false;
        }

        public boolean isMovementFixActive() {
            return RotationHelper.get().fixMovement();
        }

        public boolean isRotationActive() {
            return RotationHelper.get().isActive();
        }

        public void sendPacket(CPacket packet) {
            if (packet != null && mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(packet.handle);
            }
        }

        public void sendPacketNoEvent(CPacket packet) {
            if (packet != null) {
                idle.miau.utility.PacketUtils.sendPacketNoEvent(packet.handle);
            }
        }

        public void processPacket(SPacket packet) {
            if (packet != null && packet.handle != null && mc.getNetHandler() != null) {
                ((Packet) packet.handle).processPacket(mc.getNetHandler());
            }
        }

        public void processPacketNoEvent(SPacket packet) {
            processPacket(packet);
        }

        public float getServerDirection(PlayerState state) {
            return state == null ? 0.0F : state.yaw;
        }

        public float[] getRotationsToBlock(Vec3 position) {
            if (mc.thePlayer == null || position == null) {
                return null;
            }
            return idle.miau.utility.RotationUtils.getRotationsFromEye(
                    mc.thePlayer.getPositionEyes(1.0F), position.x, position.y, position.z);
        }

        public Float getServerYaw() {
            Float yaw = RotationHelper.get().getServerYaw();
            return yaw == null ? idle.miau.utility.RotationUtils.serverRotations[0] : yaw;
        }

        public Float getServerPitch() {
            Float pitch = RotationHelper.get().getServerPitch();
            return pitch == null ? idle.miau.utility.RotationUtils.serverRotations[1] : pitch;
        }

        public Object[] raycastBlock(double distance) {
            if (mc.thePlayer == null) return null;
            return raycastBlock(distance, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        }

        public Object[] raycastBlock(double distance, float yaw, float pitch) {
            if (mc.thePlayer == null || mc.theWorld == null) return null;
            net.minecraft.util.Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
            float yawRad = (float) Math.toRadians(-yaw) - (float) Math.PI;
            float pitchRad = (float) Math.toRadians(-pitch);
            float cosYaw = MathHelper.cos(yawRad);
            float sinYaw = MathHelper.sin(yawRad);
            float cosPitch = -MathHelper.cos(pitchRad);
            float sinPitch = MathHelper.sin(pitchRad);
            net.minecraft.util.Vec3 look = new net.minecraft.util.Vec3(
                    sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(
                    eyes,
                    eyes.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance),
                    false,
                    false,
                    true);
            if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                    || hit.getBlockPos() == null || hit.hitVec == null || hit.sideHit == null) {
                return null;
            }
            BlockPos pos = hit.getBlockPos();
            Vec3 local = new Vec3(
                    hit.hitVec.xCoord - pos.getX(),
                    hit.hitVec.yCoord - pos.getY(),
                    hit.hitVec.zCoord - pos.getZ());
            return new Object[]{Vec3.of(pos), local, hit.sideHit.name()};
        }

        public Object[] raycastEntity(double distance) {
            if (mc.thePlayer == null) {
                return null;
            }
            return raycastEntity(distance, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        }

        public Object[] raycastEntity(double distance, float yaw, float pitch) {
            if (mc.thePlayer == null || mc.theWorld == null || mc.getRenderViewEntity() == null) {
                return null;
            }
            net.minecraft.util.Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
            float yawRadians = (float) Math.toRadians(-yaw) - (float) Math.PI;
            float pitchRadians = (float) Math.toRadians(-pitch);
            float cosYaw = MathHelper.cos(yawRadians);
            float sinYaw = MathHelper.sin(yawRadians);
            float cosPitch = -MathHelper.cos(pitchRadians);
            float sinPitch = MathHelper.sin(pitchRadians);
            net.minecraft.util.Vec3 look = new net.minecraft.util.Vec3(
                    sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
            net.minecraft.util.Vec3 end = eyes.addVector(
                    look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
            net.minecraft.entity.Entity pointed = null;
            net.minecraft.util.Vec3 hitVec = null;
            double bestDistance = distance;
            AxisAlignedBB search = mc.getRenderViewEntity().getEntityBoundingBox()
                    .addCoord(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance)
                    .expand(1.0D, 1.0D, 1.0D);
            for (Object value : mc.theWorld.getEntitiesWithinAABBExcludingEntity(
                    mc.getRenderViewEntity(), search)) {
                if (!(value instanceof net.minecraft.entity.Entity)) {
                    continue;
                }
                net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) value;
                if (!(entity instanceof net.minecraft.entity.EntityLivingBase)
                        || !entity.canBeCollidedWith()
                        || ((net.minecraft.entity.EntityLivingBase) entity).deathTime != 0) {
                    continue;
                }
                float border = entity.getCollisionBorderSize();
                AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
                MovingObjectPosition hit = box.calculateIntercept(eyes, end);
                if (box.isVecInside(eyes)) {
                    pointed = entity;
                    hitVec = hit == null ? eyes : hit.hitVec;
                    bestDistance = 0.0D;
                }
                else if (hit != null) {
                    double candidateDistance = eyes.distanceTo(hit.hitVec);
                    if (candidateDistance < bestDistance) {
                        pointed = entity;
                        hitVec = hit.hitVec;
                        bestDistance = candidateDistance;
                    }
                }
            }
            if (pointed == null || hitVec == null) {
                return null;
            }
            Vec3 relative = new Vec3(
                    hitVec.xCoord - pointed.posX,
                    hitVec.yCoord - pointed.posY,
                    hitVec.zCoord - pointed.posZ);
            return new Object[]{new Entity(pointed), relative, eyes.squareDistanceTo(hitVec)};
        }

        public boolean placeBlock(Vec3 support, String face, Vec3 hit) {
            if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null
                    || support == null || face == null || hit == null) return false;
            EnumFacing facing;
            try {
                facing = EnumFacing.valueOf(face.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            BlockPos pos = new BlockPos(support.x, support.y, support.z);
            return mc.playerController.onPlayerRightClick(
                    mc.thePlayer,
                    mc.theWorld,
                    mc.thePlayer.getHeldItem(),
                    pos,
                    facing,
                    hit.unwrap());
        }

        public boolean canPlaceBlock(ItemStack stack, Vec3 position, String face) {
            if (stack == null || stack.getRaw() == null || !(stack.getRaw().getItem() instanceof ItemBlock)
                    || position == null || mc.thePlayer == null || mc.theWorld == null) {
                return false;
            }
            EnumFacing facing;
            try {
                facing = EnumFacing.valueOf(face == null ? "DOWN" : face.toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored) {
                facing = EnumFacing.DOWN;
            }
            return ((ItemBlock) stack.getRaw().getItem()).canPlaceBlockOnSide(
                    mc.theWorld, new BlockPos(position.x, position.y, position.z), facing,
                    mc.thePlayer, stack.getRaw());
        }

        public void clickBlock(Vec3 position, String face) {
            if (mc.playerController == null || position == null) {
                return;
            }
            EnumFacing facing;
            try {
                facing = EnumFacing.valueOf(face == null ? "DOWN" : face.toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored) {
                facing = EnumFacing.DOWN;
            }
            mc.playerController.clickBlock(new BlockPos(position.x, position.y, position.z), facing);
        }
    }

    public static final class WorldApi {
        private final Minecraft mc = Minecraft.getMinecraft();

        public boolean isLoaded() {
            return exists();
        }

        public Block getBlockAt(Vec3 pos) {
            return pos == null ? null : getBlockAt((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
        }

        public Block getBlockAt(int x, int y, int z) {
            return mc.theWorld == null ? null : new Block(
                    mc.theWorld.getBlockState(new BlockPos(x, y, z)), new BlockPos(x, y, z));
        }

        public Block getBlock(int x, int y, int z) {
            return getBlockAt(x, y, z);
        }

        public Block getBlockAt(double x, double y, double z) {
            return getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        }

        public long getTime() {
            return mc.theWorld == null ? 0L : mc.theWorld.getWorldTime();
        }

        public long getTotalTime() {
            return mc.theWorld == null ? 0L : mc.theWorld.getTotalWorldTime();
        }

        public boolean exists() {
            return mc.theWorld != null;
        }

        public List<Entity> getEntities() {
            List<Entity> result = new ArrayList<>();
            if (mc.theWorld != null) {
                for (net.minecraft.entity.Entity entity : mc.theWorld.loadedEntityList) {
                    result.add(Entity.convert(entity));
                }
            }
            return result;
        }

        public List<Entity> getPlayerEntities() {
            List<Entity> result = new ArrayList<>();
            if (mc.theWorld != null) {
                for (net.minecraft.entity.player.EntityPlayer player : mc.theWorld.playerEntities) {
                    result.add(Entity.convert(player));
                }
            }
            return result;
        }

        public Entity getEntityById(int entityId) {
            return mc.theWorld == null ? null : Entity.convert(mc.theWorld.getEntityByID(entityId));
        }

        public Entity getEntity(int entityId) {
            return getEntityById(entityId);
        }

        public Entity getPlayer(String name) {
            if (mc.theWorld == null || name == null) {
                return null;
            }
            for (net.minecraft.entity.player.EntityPlayer player : mc.theWorld.playerEntities) {
                if (player != null && name.equalsIgnoreCase(player.getName())) {
                    return Entity.convert(player);
                }
            }
            return null;
        }

        public List<NetworkPlayer> getNetworkPlayers() {
            List<NetworkPlayer> result = new ArrayList<NetworkPlayer>();
            if (mc.getNetHandler() != null) {
                for (net.minecraft.client.network.NetworkPlayerInfo info
                        : mc.getNetHandler().getPlayerInfoMap()) {
                    NetworkPlayer player = NetworkPlayer.convert(info);
                    if (player != null) {
                        result.add(player);
                    }
                }
            }
            return result;
        }

        public List<String> getScoreboard() {
            List<String> result = new ArrayList<String>();
            if (mc.theWorld == null || mc.theWorld.getScoreboard() == null) {
                return result;
            }
            for (net.minecraft.scoreboard.Score score : mc.theWorld.getScoreboard().getScores()) {
                result.add(score.getPlayerName() + ": " + score.getScorePoints());
            }
            return result;
        }

        public String getTabHeader() {
            net.minecraft.client.gui.GuiPlayerTabOverlay tab =
                    mc.ingameGUI == null ? null : mc.ingameGUI.getTabList();
            return tab == null || tab.getHeader() == null ? "" : tab.getHeader().getUnformattedText();
        }

        public String getTabFooter() {
            net.minecraft.client.gui.GuiPlayerTabOverlay tab =
                    mc.ingameGUI == null ? null : mc.ingameGUI.getTabList();
            return tab == null || tab.getFooter() == null ? "" : tab.getFooter().getUnformattedText();
        }

        public boolean isValidEntity(Entity entity) {
            return entity != null && entity.isValid();
        }

        public Entity spawnMob(String type, double x, double y, double z) {
            return null;
        }

        public Entity spawnMob(String type, Vec3 position) {
            return position == null ? null : spawnMob(type, position.x, position.y, position.z);
        }

        public boolean removeClientEntity(Entity entity) {
            return false;
        }

        public void clearClientEntities() {
        }

        public void playSound(String sound, float volume, float pitch, double x, double y, double z) {
            if (mc.theWorld != null && sound != null) {
                mc.theWorld.playSound(x, y, z, sound, volume, pitch, false);
            }
        }

        public String getTitleText() {
            return mc.ingameGUI == null ? "" : mc.ingameGUI.getDisplayedTitle();
        }

        public void setTitleText(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            if (mc.ingameGUI != null) {
                mc.ingameGUI.displayTitle(title, subtitle, fadeIn, stay, fadeOut);
            }
        }

        public void clearTitleText() {
            if (mc.ingameGUI != null) {
                mc.ingameGUI.displayTitle(null, null, -1, -1, -1);
            }
        }

        public List<TileEntity> getTileEntities() {
            List<TileEntity> result = new ArrayList<TileEntity>();
            if (mc.theWorld != null) {
                for (net.minecraft.tileentity.TileEntity tileEntity : mc.theWorld.loadedTileEntityList) {
                    result.add(new TileEntity(tileEntity));
                }
            }
            return result;
        }

        public Map<String, List<String>> getTeams() {
            Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
            if (mc.theWorld == null || mc.theWorld.getScoreboard() == null) {
                return result;
            }
            for (net.minecraft.scoreboard.ScorePlayerTeam team : mc.theWorld.getScoreboard().getTeams()) {
                result.put(team.getRegisteredName(), new ArrayList<String>(team.getMembershipCollection()));
            }
            return result;
        }

        public String getDimension() {
            return mc.theWorld == null || mc.theWorld.provider == null
                    ? "" : mc.theWorld.provider.getDimensionName();
        }

        public int getDimensionId() {
            return mc.theWorld == null || mc.theWorld.provider == null
                    ? 0 : mc.theWorld.provider.getDimensionId();
        }
    }

    public static final class InventoryApi {
        private final Minecraft mc = Minecraft.getMinecraft();

        public int getSlot() {
            return mc.thePlayer == null ? 0 : mc.thePlayer.inventory.currentItem;
        }

        public void setSlot(int slot) {
            if (mc.thePlayer == null || slot < 0 || slot > 8) return;
            mc.thePlayer.inventory.currentItem = slot;
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(new net.minecraft.network.play.client.C09PacketHeldItemChange(slot));
            }
        }

        public ItemStack getStackInSlot(int slot) {
            if (mc.thePlayer == null || slot < 0 || slot >= mc.thePlayer.inventory.getSizeInventory()) return null;
            return ItemStack.of(mc.thePlayer.inventory.getStackInSlot(slot));
        }

        public int getSize() {
            return mc.thePlayer == null ? 0 : mc.thePlayer.inventory.getSizeInventory();
        }

        public String getChest() {
            return mc.thePlayer == null || mc.thePlayer.openContainer == null
                    ? "" : mc.thePlayer.openContainer.getClass().getSimpleName();
        }

        public String getContainer() {
            return getChest();
        }

        public int getChestSize() {
            return mc.thePlayer == null || mc.thePlayer.openContainer == null
                    ? 0 : mc.thePlayer.openContainer.inventorySlots.size();
        }

        public void click(int slot, int button, int clickType) {
            if (mc.thePlayer != null && mc.playerController != null && mc.thePlayer.openContainer != null) {
                mc.playerController.windowClick(mc.thePlayer.openContainer.windowId,
                        slot, button, clickType, mc.thePlayer);
            }
        }

        public ItemStack getStackInChestSlot(int slot) {
            if (mc.thePlayer == null || mc.thePlayer.openContainer == null
                    || slot < 0 || slot >= mc.thePlayer.openContainer.inventorySlots.size()) {
                return null;
            }
            return ItemStack.of(mc.thePlayer.openContainer.inventorySlots.get(slot).getStack());
        }

        public ItemStack getStackInCraftingSlot(int slot) {
            return getStackInChestSlot(slot);
        }

        public ItemStack getCraftResult() {
            return getStackInChestSlot(0);
        }

        public List<String> getBookContents() {
            return new ArrayList<String>();
        }

        public void open() {
            if (mc.thePlayer != null) {
                mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiInventory(mc.thePlayer));
            }
        }
    }

    public static final class KeybindsApi {
        private final Minecraft mc = Minecraft.getMinecraft();

        private KeyBinding binding(String name) {
            switch (name.toLowerCase(Locale.ROOT)) {
                case "forward": return mc.gameSettings.keyBindForward;
                case "back": return mc.gameSettings.keyBindBack;
                case "left": return mc.gameSettings.keyBindLeft;
                case "right": return mc.gameSettings.keyBindRight;
                case "jump": return mc.gameSettings.keyBindJump;
                case "sneak": return mc.gameSettings.keyBindSneak;
                case "sprint": return mc.gameSettings.keyBindSprint;
                case "use": return mc.gameSettings.keyBindUseItem;
                case "attack": return mc.gameSettings.keyBindAttack;
                case "drop": return mc.gameSettings.keyBindDrop;
                default: return null;
            }
        }

        public void setPressed(String name, boolean pressed) {
            KeyBinding binding = binding(name);
            if (binding != null) KeyBinding.setKeyBindState(binding.getKeyCode(), pressed);
        }

        public void setPressed(int keyCode, boolean pressed) {
            if (keyCode >= 1000) return;
            KeyBinding.setKeyBindState(keyCode, pressed);
        }

        public boolean isPressed(String name) {
            KeyBinding binding = binding(name);
            return binding != null && binding.isKeyDown();
        }

        public boolean isPressed(int keyCode) {
            return isKeyDown(keyCode);
        }

        public int getKeyCode(String name) {
            if (name == null) return 0;
            String normalized = name.trim().toUpperCase(Locale.ROOT);
            if (normalized.startsWith("MOUSE")) {
                try {
                    return 1000 + Integer.parseInt(normalized.substring(5));
                }
                catch (NumberFormatException ignored) {
                    return 0;
                }
            }
            KeyBinding binding = binding(normalized);
            return binding == null ? Keyboard.getKeyIndex(normalized) : binding.getKeyCode();
        }

        public int getKeycode(String name) {
            return getKeyCode(name);
        }

        public int getKeyIndex(String name) {
            return getKeyCode(name);
        }

        public int[] getMousePosition() {
            return new int[]{Mouse.getX(), Mouse.getY()};
        }

        public int getScroll() {
            return Mouse.getDWheel();
        }

        public boolean isKeyDown(int keyCode) {
            if (keyCode >= 1000) {
                return Mouse.isButtonDown(keyCode - 1000);
            }
            return keyCode > 0 && Keyboard.isKeyDown(keyCode);
        }

        public boolean isMouseDown(int button) {
            return button >= 0 && Mouse.isButtonDown(button);
        }

        public void onTick(String name) {
            KeyBinding binding = binding(name);
            if (binding != null) {
                KeyBinding.onTick(binding.getKeyCode());
            }
        }

        public void leftClick() {
            if (mc instanceof IAccessorMinecraft) {
                ((IAccessorMinecraft) mc).callClickMouse();
            }
        }

        public void rightClick() {
            if (mc instanceof IAccessorMinecraft) {
                ((IAccessorMinecraft) mc).callRightClickMouse();
            }
        }
    }

    public static final class RenderApi {
        private final Minecraft mc = Minecraft.getMinecraft();

        public Vec3 getPosition() {
            return new Vec3(
                    mc.getRenderManager().viewerPosX,
                    mc.getRenderManager().viewerPosY,
                    mc.getRenderManager().viewerPosZ);
        }

        public double[] getRotations() {
            net.minecraft.entity.Entity entity = mc.getRenderViewEntity();
            return entity == null ? null : new double[]{entity.rotationYaw, entity.rotationPitch};
        }

        public int getFontWidth(String text) {
            return mc.fontRendererObj.getStringWidth(text);
        }

        public void text(String text, float x, float y, float scale, int color, boolean shadow) {
            GL11.glPushMatrix();
            GL11.glScalef(scale, scale, 1.0F);
            if (shadow) mc.fontRendererObj.drawStringWithShadow(text, x / scale, y / scale, color);
            else mc.fontRendererObj.drawString(text, (int) (x / scale), (int) (y / scale), color);
            GL11.glPopMatrix();
        }

        public void text2d(String text, float x, float y, float scale, int color, boolean shadow) {
            GlStateManager.pushMatrix();
            if (scale != 1.0F) GlStateManager.scale(scale, scale, scale);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            mc.fontRendererObj.drawString(text, x / scale, y / scale, color, shadow);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }

        public void block(Vec3 position, int color, boolean outline, boolean shade) {
            if (position == null) return;
            int argb = (color & 0xFF000000) == 0 ? 0x66000000 | (color & 0xFFFFFF) : color;
            RenderUtils.renderBlock(new BlockPos(position.x, position.y, position.z), argb, outline, shade);
        }

        public void block(int x, int y, int z, int color, boolean outline, boolean shade) {
            block(new Vec3(x, y, z), color, outline, shade);
        }

        public int getThemeRGB(String name) {
            return idle.miau.module.impl.render.ArrayListModule.getHudColor(0.0D);
        }

        public void entity(Entity entity, int type, float expand, boolean damage, boolean shade) {
            if (entity != null && entity.entity != null) {
                RenderUtils.renderEntity(entity.entity, type, expand, 0.0D, 0xFFFFFFFF, damage);
            }
        }

        public void entityGui(Entity entity, int x, int y, float scale, float partialTicks, int color) {
            entity(entity, 0, scale, false, false);
        }

        public void resetEquippedProgress() {
            if (mc.getItemRenderer() != null) {
                mc.getItemRenderer().resetEquippedProgress();
            }
        }

        public void tracer(Entity entity, float lineWidth, int color, float partialTicks) {
            if (entity != null && entity.entity != null) {
                RenderUtils.drawTracerLine(entity.entity, color, lineWidth, partialTicks);
            }
        }

        public void showGui() {
        }

        public void item(ItemStack stack, float x, float y, float scale) {
            if (stack != null && stack.getRaw() != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(x, y, 0.0F);
                GL11.glScalef(scale, scale, scale);
                RenderUtils.renderItemAndEffectIntoGui3D(stack.getRaw(), 0, 0);
                GL11.glPopMatrix();
            }
        }

        public void image(Image image, float x, float y, float width, float height) {
        }

        public Vec3 worldToScreen(double x, double y, double z, int scaleFactor, float partialTicks) {
            RenderUtils.ProjectionContext context = new RenderUtils.ProjectionContext();
            double[] output = new double[3];
            return RenderUtils.projectTo2D(context, x, y, z, output)
                    ? new Vec3(output[0], output[1], output[2]) : null;
        }

        public void roundedRect(float x, float y, float width, float height, float radius, int color) {
            RenderUtils.drawRoundedRectangle(x, y, width, height, radius, color);
        }

        public void gradientRect(float x, float y, float width, float height, int left, int right) {
            RenderUtils.drawRoundedGradientRect(x, y, width, height, 0.0F, left, left, right, right);
        }

        public void gradientRect(float x, float y, float width, float height,
                                 int topLeft, int topRight, int bottomLeft, int bottomRight) {
            RenderUtils.drawRoundedGradientRect(x, y, width, height, 0.0F,
                    topLeft, topRight, bottomLeft, bottomRight);
        }

        public double[] getCameraRotations() {
            return getRotations();
        }

        public int getFontHeight() {
            return mc.fontRendererObj.FONT_HEIGHT;
        }

        public void text3d(String text, Vec3 position, float scale, boolean shadow,
                           boolean depth, boolean centered, int color) {
            if (position == null) {
                return;
            }
            text(text, (float) position.x, (float) position.y, scale, color, shadow);
        }

        public void rect(float x, float y, float width, float height, int color) {
            RenderUtils.drawRect(x, y, x + width, y + height, color);
        }

        public void line2D(double x, double y, double x2, double y2, float width, int color) {
            float red = ((color >>> 16) & 255) / 255.0F;
            float green = ((color >>> 8) & 255) / 255.0F;
            float blue = (color & 255) / 255.0F;
            float alpha = ((color >>> 24) & 255) / 255.0F;
            GL11.glPushMatrix();
            GL11.glLineWidth(width);
            GL11.glColor4f(red, green, blue, alpha);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2d(x, y);
            GL11.glVertex2d(x2, y2);
            MiauDrawCalls.glEnd();
            GL11.glPopMatrix();
        }

        public void line3D(Vec3 from, Vec3 to, float width, int color) {
            if (from == null || to == null) {
                return;
            }
            line3D(from.x, from.y, from.z, to.x, to.y, to.z, width, color);
        }

        public void line3D(double x, double y, double z, double x2, double y2, double z2,
                           float width, int color) {
            float red = ((color >>> 16) & 255) / 255.0F;
            float green = ((color >>> 8) & 255) / 255.0F;
            float blue = (color & 255) / 255.0F;
            float alpha = ((color >>> 24) & 255) / 255.0F;
            GL11.glPushMatrix();
            GL11.glTranslated(-mc.getRenderManager().viewerPosX,
                    -mc.getRenderManager().viewerPosY, -mc.getRenderManager().viewerPosZ);
            GL11.glLineWidth(width);
            GL11.glColor4f(red, green, blue, alpha);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(x, y, z);
            GL11.glVertex3d(x2, y2, z2);
            MiauDrawCalls.glEnd();
            GL11.glPopMatrix();
        }

        public boolean isInView(Entity entity) {
            return entity != null && entity.entity != null && RenderUtils.isInViewFrustum(entity.entity);
        }
    }

    public static final class GlApi {
        public void pushState() { GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); }
        public void popState() { GL11.glPopAttrib(); }
        public void push() { GL11.glPushMatrix(); }
        public void pop() { GL11.glPopMatrix(); }
        public void begin(int mode) { GL11.glBegin(mode); }
        public void end() { MiauDrawCalls.glEnd(); }
        public void vertex2(float x, float y) { GL11.glVertex2f(x, y); }
        public void vertex2(double x, double y) { GL11.glVertex2d(x, y); }
        public void vertex3(float x, float y, float z) { GL11.glVertex3f(x, y, z); }
        public void vertex3(double x, double y, double z) { GL11.glVertex3d(x, y, z); }
        public void translate(float x, float y, float z) { GL11.glTranslatef(x, y, z); }
        public void translate(double x, double y, double z) { GL11.glTranslated(x, y, z); }
        public void rotate(float angle, float x, float y, float z) { GL11.glRotatef(angle, x, y, z); }
        public void rotate(double angle, double x, double y, double z) { GL11.glRotated(angle, x, y, z); }
        public void scale(float x, float y, float z) { GL11.glScalef(x, y, z); }
        public void color(double r, double g, double b, double a) { GL11.glColor4d(r, g, b, a); }
        public void color(float r, float g, float b, float a) { GL11.glColor4f(r, g, b, a); }
        public void color(int r, int g, int b, int a) { GL11.glColor4ub((byte) r, (byte) g, (byte) b, (byte) a); }
        public void depthFunc(int function) { GL11.glDepthFunc(function); }
        public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
            GL11.glColorMask(red, green, blue, alpha);
        }
        public void polygonOffset(float factor, float units) { GL11.glPolygonOffset(factor, units); }
        public void polygonOffset(boolean enabled) { state(GL11.GL_POLYGON_OFFSET_FILL, enabled); }
        public void texCoord2(float x, float y) { GL11.glTexCoord2f(x, y); }
        public void texCoord2(double x, double y) { GL11.glTexCoord2d(x, y); }
        public void bindTexture(String location) {
            if (location != null && Minecraft.getMinecraft().getTextureManager() != null) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(
                        new net.minecraft.util.ResourceLocation(location));
            }
        }
        public void bindTextureId(int texture) { GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); }
        public int getBoundTexture() { return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); }
        public void lineWidth(float width) { GL11.glLineWidth(width); }
        public void lineWidth(double width) { GL11.glLineWidth((float) width); }
        public void normal(float x, float y, float z) { GL11.glNormal3f(x, y, z); }
        public void normal(double x, double y, double z) { GL11.glNormal3d(x, y, z); }
        public void depthMask(boolean enabled) { GL11.glDepthMask(enabled); }
        public void blend(boolean enabled) { state(GL11.GL_BLEND, enabled); if (enabled) GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); }
        public void texture2d(boolean enabled) { state(GL11.GL_TEXTURE_2D, enabled); }
        public void alpha(boolean enabled) { state(GL11.GL_ALPHA_TEST, enabled); }
        public void cull(boolean enabled) { state(GL11.GL_CULL_FACE, enabled); }
        public void depth(boolean enabled) { state(GL11.GL_DEPTH_TEST, enabled); }
        public void lineSmooth(boolean enabled) { state(GL11.GL_LINE_SMOOTH, enabled); }
        public void lighting(boolean enabled) { state(GL11.GL_LIGHTING, enabled); }
        public void enable(int capability) { GL11.glEnable(capability); }
        public void disable(int capability) { GL11.glDisable(capability); }
        public void scissor(int x, int y, int width, int height) { GL11.glScissor(x, y, width, height); }
        public void scissor(boolean enabled) { state(GL11.GL_SCISSOR_TEST, enabled); }
        public void disableItemLighting() { }
        public void enableItemLighting(boolean enabled) { }
        public void resetColor() { GL11.glColor4f(1F, 1F, 1F, 1F); }
        private void state(int capability, boolean enabled) { if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability); }
    }

    public static final class UtilApi {
        public String color(String text) { return MiauScriptApi.color(text); }
        public double randomDouble(double min, double max) { return min + Math.random() * (max - min); }

        public String strip(String text) {
            return text == null ? "" : text.replaceAll("\\u00a7[0-9a-fk-or]", "");
        }

        public double round(double value, int decimals) {
            double factor = Math.pow(10.0D, Math.max(0, decimals));
            return Math.round(value * factor) / factor;
        }

        public int randomInt(int min, int max) {
            if (max < min) {
                int swap = min;
                min = max;
                max = swap;
            }
            return min + (int) Math.floor(Math.random() * (max - min + 1));
        }
    }

    public static final class BridgeApi {
        private static final Map<String, Object> VALUES = new ConcurrentHashMap<>();
        public void add(String key) { VALUES.put(key, Boolean.TRUE); }
        public void add(String key, Object value) { VALUES.put(key, value); }
        public Object get(String key) { return VALUES.get(key); }
        public boolean has(String key) { return VALUES.containsKey(key); }
        public void remove(String key) { VALUES.remove(key); }
        public void clear() { VALUES.clear(); }
    }

    public static final class ModulesApi {
        private Module owner;
        private GroupSetting group;
        private final List<Setting> registeredSettings = new ArrayList<>();
        private final Map<String, ButtonSetting> buttons = new LinkedHashMap<>();
        private final Map<String, SliderSetting> sliders = new LinkedHashMap<>();
        private final Map<String, ColorSetting> colors = new LinkedHashMap<>();
        private final Map<String, KeySetting> keys = new LinkedHashMap<>();
        private final Map<String, GroupSetting> groups = new LinkedHashMap<>();

        public ModulesApi() {
            this(null);
        }

        public ModulesApi(Module owner) {
            this.owner = owner;
            this.group = null;
        }

        public ModulesApi(Module owner, String groupName) {
            this.owner = owner;
            this.group = new GroupSetting(groupName);
            groups.put(groupName, group);
            registeredSettings.add(group);
            owner.registerSetting(group);
        }

        public void bind(Module owner) {
            this.owner = owner;
        }

        public void registerGroup(String name) {
            if (name == null || name.trim().isEmpty()) {
                return;
            }
            GroupSetting value = new GroupSetting(name.trim());
            groups.put(name.trim(), value);
            registeredSettings.add(value);
            owner.registerSetting(value);
        }

        public void registerDescription(String text) {
            DescriptionSetting setting = new DescriptionSetting(text);
            registeredSettings.add(setting);
            owner.registerSetting(setting);
        }

        public void registerButton(String name, boolean value) {
            ButtonSetting setting = new ButtonSetting(group, name, value);
            buttons.put(name, setting);
            registeredSettings.add(setting);
            owner.registerSetting(setting);
            addToGroup(group, setting);
        }

        public void registerButton(String groupName, String name, boolean value) {
            GroupSetting parent = findGroup(groupName);
            ButtonSetting setting = parent == null
                    ? new ButtonSetting(name, value) : new ButtonSetting(parent, name, value);
            buttons.put(name, setting);
            registeredSettings.add(setting);
            owner.registerSetting(setting);
            addToGroup(parent, setting);
        }

        public void registerKey(String name, int key) {
            registerKey(null, name, key);
        }

        public void registerKey(String groupName, String name, int key) {
            GroupSetting parent = findGroup(groupName);
            KeySetting setting = parent == null
                    ? new KeySetting(name, key) : new KeySetting(parent, name, key);
            keys.put(name, setting);
            registeredSettings.add(setting);
            owner.registerSetting(setting);
            addToGroup(parent, setting);
        }

        public void registerSlider(String name, String suffix, double value, double min, double max, double step) {
            SliderSetting setting = new SliderSetting(group, name, suffix, value, min, max, step);
            sliders.put(name, setting);
            registeredSettings.add(setting);
            owner.registerSetting(setting);
            addToGroup(group, setting);
        }

        public void registerSlider(String name, double value, double min, double max, double step) {
            registerSlider(name, "", value, min, max, step);
        }

        public void registerSlider(String groupName, String name, String suffix,
                                   double value, double min, double max, double step) {
            GroupSetting parent = findGroup(groupName);
            SliderSetting setting = parent == null
                    ? new SliderSetting(name, suffix, value, min, max, step)
                    : new SliderSetting(parent, name, suffix, value, min, max, step);
            sliders.put(name, setting);
            registeredSettings.add(setting);
            owner.registerSetting(setting);
            addToGroup(parent, setting);
        }

        public void registerSlider(String name, String suffix, double value, String[] options) {
            SliderSetting setting = new SliderSetting(group, name, suffix, (int) value, options);
            sliders.put(name, setting);
            registeredSettings.add(setting);
            owner.registerSetting(setting);
            addToGroup(group, setting);
        }

        public void registerSlider(String name, int value, String[] options) {
            registerSlider(name, "", value, options);
        }

        public void registerSlider(String name, String suffix, int value, String[] options) {
            SliderSetting setting = new SliderSetting(group, name, suffix, value, options);
            sliders.put(name, setting);
            registeredSettings.add(setting);
            owner.registerSetting(setting);
            addToGroup(group, setting);
        }

        public void registerSlider(String groupName, String name, String suffix,
                                   int value, String[] options) {
            GroupSetting parent = findGroup(groupName);
            SliderSetting setting = new SliderSetting(parent, name, suffix, value, options);
            sliders.put(name, setting);
            registeredSettings.add(setting);
            owner.registerSetting(setting);
            addToGroup(parent, setting);
        }

        public void registerColor(String name, int red, int green, int blue) {
            registerColor(group, name, red, green, blue, 255, false);
        }

        public void registerColor(String name, int red, int green, int blue, int alpha) {
            registerColor(group, name, red, green, blue, alpha, true);
        }

        public void registerColor(String groupName, String name, int red, int green, int blue) {
            registerColor(findGroup(groupName), name, red, green, blue, 255, false);
        }

        public void registerColor(String groupName, String name, int red, int green, int blue, int alpha) {
            registerColor(findGroup(groupName), name, red, green, blue, alpha, true);
        }

        private void registerColor(GroupSetting parent, String name, int red, int green, int blue,
                                   int alpha, boolean hasAlpha) {
            ColorSetting setting = parent == null
                    ? hasAlpha
                        ? new ColorSetting(name, red, green, blue, alpha)
                        : new ColorSetting(name, red, green, blue)
                    : hasAlpha
                        ? new ColorSetting(parent, name, red, green, blue, alpha)
                        : new ColorSetting(parent, name, red, green, blue);
            colors.put(name, setting);
            registeredSettings.add(setting);
            owner.registerSetting(setting);
            addToGroup(parent, setting);
        }

        public void setSettingsVisible(boolean visible) {
            for (Setting setting : registeredSettings) {
                setting.setVisible(visible, owner);
            }
        }

        public void setSliderVisible(String name, boolean visible) {
            SliderSetting setting = sliders.get(name);
            if (setting != null) setting.setVisible(visible, owner);
        }

        public boolean getButton(String script, String name) {
            ButtonSetting setting = buttons.get(name);
            return setting != null && setting.isToggled();
        }

        public double getSlider(String script, String name) {
            SliderSetting setting = sliders.get(name);
            return setting == null ? 0.0 : setting.getInput();
        }

        public int getColor(String script, String name) {
            ColorSetting setting = colors.get(name);
            return setting == null ? 0 : setting.getColor();
        }

        public void setColor(String script, String name, int red, int green, int blue) {
            ColorSetting setting = colors.get(name);
            if (setting != null) {
                setting.setColor(red, green, blue);
            }
        }

        public void setColor(String script, String name, int red, int green, int blue, int alpha) {
            ColorSetting setting = colors.get(name);
            if (setting != null) {
                setting.setColor(red, green, blue, alpha);
            }
        }

        public double getSliderMin(String script, String name) {
            SliderSetting setting = sliders.get(name);
            return setting == null ? 0.0D : setting.getMin();
        }

        public double getSliderMax(String script, String name) {
            SliderSetting setting = sliders.get(name);
            return setting == null ? 0.0D : setting.getMax();
        }

        public String getSliderString(String script, String name) {
            SliderSetting setting = sliders.get(name);
            if (setting == null || setting.getOptions() == null || setting.getOptions().length == 0) {
                return "";
            }
            String[] options = setting.getOptions();
            int index = (int) Math.round(setting.getInput());
            index = Math.max(0, Math.min(index, options.length - 1));
            return options[index];
        }

        public void setButton(String script, String name, boolean value) {
            ButtonSetting setting = buttons.get(name);
            if (setting != null) {
                setting.setEnabled(value);
            }
        }

        public void setSlider(String script, String name, double value) {
            SliderSetting setting = sliders.get(name);
            if (setting != null) {
                setting.setValueRawWithEvent(value);
            }
        }

        public boolean getKeyPressed(String script, String name) {
            KeySetting setting = keys.get(name);
            return setting != null && setting.isPressed();
        }

        public void setKey(String script, String name, int key) {
            KeySetting setting = keys.get(name);
            if (setting != null) {
                setting.setKey(key);
            }
        }

        public Map<String, Object> getSettings(String name) {
            Map<String, Object> result = new LinkedHashMap<>();
            Module module = module(name);
            if (module == null) {
                return result;
            }
            for (Setting setting : module.getSettings()) {
                if (setting instanceof SliderSetting) {
                    result.put(setting.getName(), ((SliderSetting) setting).getInput());
                }
                else if (setting instanceof ButtonSetting) {
                    result.put(setting.getName(), ((ButtonSetting) setting).isToggled());
                }
                else if (setting instanceof ColorSetting) {
                    result.put(setting.getName(), ((ColorSetting) setting).getColor());
                }
            }
            return result;
        }

        public Map<String, List<String>> getCategories() {
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (Module module : ModuleManager.modules) {
                String category = module.moduleCategory().name();
                List<String> names = result.get(category);
                if (names == null) {
                    names = new ArrayList<>();
                    result.put(category, names);
                }
                names.add(module.getName());
            }
            return result;
        }

        public boolean isEnabled(String name) {
            Module module = ModuleManager.getModule(name);
            return module != null && module.isEnabled();
        }

        public void enable(String name) {
            Module module = module(name);
            if (module != null && !module.isEnabled()) module.enable();
        }

        public void disable(String name) {
            Module module = module(name);
            if (module != null && module.isEnabled()) module.disable();
        }

        public Entity getKillAuraTarget() {
            return idle.miau.module.impl.combat.KillAura.target == null
                    ? null : Entity.convert(idle.miau.module.impl.combat.KillAura.target);
        }

        public Entity getAimAssistTarget() {
            if (ModuleManager.aimAssist == null || !ModuleManager.aimAssist.isEnabled()) {
                return null;
            }
            return Entity.convert(ModuleManager.aimAssist.getTarget());
        }

        public Entity getLagRangeTarget() {
            if (ModuleManager.lagRange == null || !ModuleManager.lagRange.isEnabled()) {
                return null;
            }
            return Entity.convert(ModuleManager.lagRange.getLagRangeTarget());
        }

        public Entity getBacktrackTarget() {
            if (ModuleManager.backtrack == null || !ModuleManager.backtrack.isEnabled()) {
                return null;
            }
            return Entity.convert(ModuleManager.backtrack.getBacktrackTarget());
        }

        public Vec3 getLagRangePosition() {
            if (ModuleManager.lagRange == null || !ModuleManager.lagRange.isEnabled()
                    || ModuleManager.lagRange.getLagRangePosition() == null) {
                return null;
            }
            return Vec3.of(ModuleManager.lagRange.getLagRangePosition());
        }

        public Vec3 getBacktrackPosition() {
            if (ModuleManager.backtrack == null || !ModuleManager.backtrack.isEnabled()
                    || ModuleManager.backtrack.getBacktrackPosition() == null) {
                return null;
            }
            return Vec3.of(ModuleManager.backtrack.getBacktrackPosition());
        }

        public Vec3 getBedAuraPosition() {
            if (ModuleManager.bedAura == null || !ModuleManager.bedAura.isEnabled()
                    || ModuleManager.bedAura.getAuraTargetPos() == null) {
                return null;
            }
            return Vec3.of(ModuleManager.bedAura.getAuraTargetPos());
        }

        public float[] getBedAuraProgress() {
            return ModuleManager.bedAura == null || !ModuleManager.bedAura.isEnabled()
                    ? new float[]{0.0F, 0.0F}
                    : new float[]{ModuleManager.bedAura.getAuraBreakProgress(), 0.0F};
        }

        public boolean isScaffolding() {
            Module scaffold = ModuleManager.getModule("Scaffold");
            return scaffold != null && scaffold.isEnabled();
        }

        public boolean isTowering() {
            return false;
        }

        public boolean isHidden(String name) {
            Module value = ModuleManager.getModule(name);
            return value != null && value.isHidden();
        }

        public Vec3 getWhitelistedBedPos() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null || mc.theWorld == null) return null;
            BlockPos center = mc.thePlayer.getPosition();
            BlockPos nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int minY = Math.max(0, center.getY() - 25);
            int maxY = Math.min(255, center.getY() + 25);
            for (int x = center.getX() - 25; x <= center.getX() + 25; x++) {
                for (int z = center.getZ() - 25; z <= center.getZ() + 25; z++) {
                    if (!mc.theWorld.isBlockLoaded(cursor.set(x, center.getY(), z), false)) continue;
                    for (int y = minY; y <= maxY; y++) {
                        cursor.set(x, y, z);
                        if (!(mc.theWorld.getBlockState(cursor).getBlock() instanceof BlockBed)) continue;
                        double distance = cursor.distanceSq(center);
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearest = new BlockPos(x, y, z);
                        }
                    }
                }
            }
            return Vec3.of(nearest);
        }

        private Module module(String name) {
            return owner.getName().equalsIgnoreCase(name) ? owner : ModuleManager.getModule(name);
        }

        private GroupSetting findGroup(String name) {
            if (name == null || name.trim().isEmpty()) {
                return group;
            }
            return groups.get(name.trim());
        }

        private static void addToGroup(GroupSetting group, Setting setting) {
            if (group != null && setting != null) {
                group.addSetting(setting);
            }
        }
    }

    public static final class Json {
        private final JsonElement value;

        public Json(String json) {
            this(new JsonParser().parse(json));
        }

        private Json(JsonElement value) {
            this.value = value;
        }

        public Json object() {
            if (!value.isJsonObject()) throw new IllegalStateException("Not a JSON object");
            return this;
        }

        public Map<String, Json> map() {
            Map<String, Json> result = new LinkedHashMap<>();
            JsonObject object = value.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                result.put(entry.getKey(), new Json(entry.getValue()));
            }
            return result;
        }

        public List<Json> array(String key) {
            JsonArray array = value.getAsJsonObject().getAsJsonArray(key);
            List<Json> result = new ArrayList<>();
            if (array != null) for (JsonElement element : array) result.add(new Json(element));
            return result;
        }

        public String get(String key, String defaultValue) {
            JsonElement element = value.getAsJsonObject().get(key);
            return element == null || element.isJsonNull() ? defaultValue : element.getAsString();
        }
    }

    private static String registryPath(net.minecraft.block.Block block) {
        Object key = net.minecraft.block.Block.blockRegistry.getNameForObject(block);
        return key == null ? "air" : stripNamespace(key.toString());
    }

    private static String stripNamespace(String value) {
        if (value == null) return null;
        int colon = value.indexOf(':');
        String result = colon >= 0 ? value.substring(colon + 1) : value;
        if (result.startsWith("tile.")) result = result.substring(5);
        if (result.startsWith("item.")) result = result.substring(5);
        return result;
    }

    private static String color(String text) {
        return text == null ? null : text.replace('&', '\u00a7');
    }
}
