package net.minecraft.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSoundMinecartRiding;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiCommandBlock;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.GuiHopper;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.inventory.GuiBeacon;
import net.minecraft.client.gui.inventory.GuiBrewingStand;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiDispenser;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiScreenHorseInventory;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.command.server.CommandBlockLogic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook;
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;
import net.minecraft.potion.Potion;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovementInput;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.World;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.PostSprintUpdateEvent;
import idle.miau.event.PostUpdateEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PreSuperLivingUpdateEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.PushOutOfBlockEvent;
import idle.miau.mcp.McpRuntime;
import idle.miau.mcp.accessor.IAccessorEntityPlayerSP;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.Autoblock;
import idle.miau.module.impl.combat.SprintReset;
import idle.miau.module.impl.movement.NoSlow;
import idle.miau.module.impl.movement.Sprint;
import idle.miau.module.impl.movement.Timer;
import idle.miau.module.impl.combat.TimerRange;
import idle.miau.mcp.callback.CallbackInfo;
import idle.miau.utility.ModuleUtils;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;

public class EntityPlayerSP extends AbstractClientPlayer implements IAccessorEntityPlayerSP {
   public final NetHandlerPlayClient sendQueue;
   private final StatFileWriter statWriter;
   private double lastReportedPosX;
   private double lastReportedPosY;
   private double lastReportedPosZ;
   private float lastReportedYaw;
   private float lastReportedPitch;
   private boolean serverSneakState;
   private boolean serverSprintState;
   private int positionUpdateTicks;
   private boolean hasValidHealth;
   private String clientBrand;
   public MovementInput movementInput;
   protected Minecraft mc;
   protected int sprintToggleTimer;
   public int sprintingTicksLeft;
   public float renderArmYaw;
   public float renderArmPitch;
   public float prevRenderArmYaw;
   public float prevRenderArmPitch;
   private int horseJumpPowerCounter;
   private float horseJumpPower;
   public float timeInPortal;
   public float prevTimeInPortal;

   public EntityPlayerSP(Minecraft mcIn, World worldIn, NetHandlerPlayClient netHandler, StatFileWriter statFile) {
      super(worldIn, netHandler.getGameProfile());
      this.sendQueue = netHandler;
      this.statWriter = statFile;
      this.mc = mcIn;
      this.dimension = 0;
   }

   public boolean attackEntityFrom(DamageSource source, float amount) {
      return false;
   }

   public void heal(float healAmount) {
   }

   public void mountEntity(Entity entityIn) {
      super.mountEntity(entityIn);
      if(entityIn instanceof EntityMinecart) {
         this.mc.getSoundHandler().playSound(new MovingSoundMinecartRiding(this, (EntityMinecart)entityIn));
      }

   }

   public void onUpdate() {
      CallbackInfo callbackInfo1 = new CallbackInfo("onUpdate", true);
      this.mimeow$onUpdatePre(callbackInfo1);
      if(!callbackInfo1.isCancelled()) {
         if(this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0D, this.posZ))) {
            super.onUpdate();
            idle.miau.baritone.BaritoneBridge.beforePlayerUpdate(this);
            if(this.isRiding()) {
               this.sendQueue.addToSendQueue(new C05PacketPlayerLook(this.rotationYaw, this.rotationPitch, this.onGround));
               this.sendQueue.addToSendQueue(new C0CPacketInput(this.moveStrafing, this.moveForward, this.movementInput.jump, this.movementInput.sneak));
            } else {
               this.onUpdateWalkingPlayer();
            }
            idle.miau.baritone.BaritoneBridge.afterPlayerUpdate(this);
         }

         this.mimeow$onUpdatePost(new CallbackInfo("onUpdate", false));
      }
   }

   public EntityItem dropOneItem(boolean dropAll) {
      Action c07packetplayerdigging$action = dropAll?Action.DROP_ALL_ITEMS:Action.DROP_ITEM;
      this.sendQueue.addToSendQueue(new C07PacketPlayerDigging(c07packetplayerdigging$action, BlockPos.ORIGIN, EnumFacing.DOWN));
      return null;
   }

   protected void joinEntityItemWithWorld(EntityItem itemIn) {
   }

   public void sendChatMessage(String message) {
      if (idle.miau.baritone.BaritoneBridge.handleChat(message)) return;
      this.sendQueue.addToSendQueue(new C01PacketChatMessage(message));
   }

   public void swingItem() {
      if(TimerRange.isFreezeActionLockActive()) {
         return;
      }
      super.swingItem();
      this.sendQueue.addToSendQueue(new C0APacketAnimation());
   }

   public void respawnPlayer() {
      this.sendQueue.addToSendQueue(new C16PacketClientStatus(EnumState.PERFORM_RESPAWN));
   }

   protected void damageEntity(DamageSource damageSrc, float damageAmount) {
      if(!this.isEntityInvulnerable(damageSrc)) {
         this.setHealth(this.getHealth() - damageAmount);
      }

   }

   public void closeScreen() {
      this.sendQueue.addToSendQueue(new C0DPacketCloseWindow(this.openContainer.windowId));
      this.closeScreenAndDropStack();
   }

   public void closeScreenAndDropStack() {
      this.inventory.setItemStack((ItemStack)null);
      super.closeScreen();
      this.mc.displayGuiScreen((GuiScreen)null);
   }

   public void setPlayerSPHealth(float health) {
      if(this.hasValidHealth) {
         float f = this.getHealth() - health;
         if(f <= 0.0F) {
            this.setHealth(health);
            if(f < 0.0F) {
               this.hurtResistantTime = this.maxHurtResistantTime / 2;
            }
         } else {
            this.lastDamage = f;
            this.setHealth(this.getHealth());
            this.hurtResistantTime = this.maxHurtResistantTime;
            this.damageEntity(DamageSource.generic, f);
            this.hurtTime = this.maxHurtTime = 10;
         }
      } else {
         this.setHealth(health);
         this.hasValidHealth = true;
      }

   }

   public void addStat(StatBase stat, int amount) {
      if(stat != null && stat.isIndependent) {
         super.addStat(stat, amount);
      }

   }

   public void sendPlayerAbilities() {
      this.sendQueue.addToSendQueue(new C13PacketPlayerAbilities(this.capabilities));
   }

   public boolean isUser() {
      return true;
   }

   protected void sendHorseJump() {
      this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, net.minecraft.network.play.client.C0BPacketEntityAction.Action.RIDING_JUMP, (int)(this.getHorseJumpPower() * 100.0F)));
   }

   public void sendHorseInventory() {
      this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, net.minecraft.network.play.client.C0BPacketEntityAction.Action.OPEN_INVENTORY));
   }

   public void setClientBrand(String brand) {
      this.clientBrand = brand;
   }

   public String getClientBrand() {
      return this.clientBrand;
   }

   public StatFileWriter getStatFileWriter() {
      return this.statWriter;
   }

   public void addChatComponentMessage(IChatComponent chatComponent) {
      this.mc.ingameGUI.getChatGUI().printChatMessage(chatComponent);
   }

   protected boolean pushOutOfBlocks(double x, double y, double z) {
      PushOutOfBlockEvent event = new PushOutOfBlockEvent();
      McpRuntime.EVENTS.post(event);
      if(event.isCanceled()) {
         return false;
      }
      if(this.noClip) {
         return false;
      } else {
         BlockPos blockpos = new BlockPos(x, y, z);
         double d0 = x - (double)blockpos.getX();
         double d1 = z - (double)blockpos.getZ();
         if(!this.isOpenBlockSpace(blockpos)) {
            int i = -1;
            double d2 = 9999.0D;
            if(this.isOpenBlockSpace(blockpos.west()) && d0 < d2) {
               d2 = d0;
               i = 0;
            }

            if(this.isOpenBlockSpace(blockpos.east()) && 1.0D - d0 < d2) {
               d2 = 1.0D - d0;
               i = 1;
            }

            if(this.isOpenBlockSpace(blockpos.north()) && d1 < d2) {
               d2 = d1;
               i = 4;
            }

            if(this.isOpenBlockSpace(blockpos.south()) && 1.0D - d1 < d2) {
               d2 = 1.0D - d1;
               i = 5;
            }

            float f = 0.1F;
            if(i == 0) {
               this.motionX = (double)(-f);
            }

            if(i == 1) {
               this.motionX = (double)f;
            }

            if(i == 4) {
               this.motionZ = (double)(-f);
            }

            if(i == 5) {
               this.motionZ = (double)f;
            }
         }

         return false;
      }
   }

   private boolean isOpenBlockSpace(BlockPos pos) {
      return !this.worldObj.getBlockState(pos).getBlock().isNormalCube() && !this.worldObj.getBlockState(pos.up()).getBlock().isNormalCube();
   }

   public void setSprinting(boolean sprinting) {
      super.setSprinting(sprinting);
      this.sprintingTicksLeft = sprinting?600:0;
   }

   public void setXPStats(float currentXP, int maxXP, int level) {
      this.experience = currentXP;
      this.experienceTotal = maxXP;
      this.experienceLevel = level;
   }

   public void addChatMessage(IChatComponent component) {
      this.mc.ingameGUI.getChatGUI().printChatMessage(component);
   }

   public boolean canCommandSenderUseCommand(int permLevel, String commandName) {
      return permLevel <= 0;
   }

   public BlockPos getPosition() {
      return new BlockPos(this.posX + 0.5D, this.posY + 0.5D, this.posZ + 0.5D);
   }

   public void playSound(String name, float volume, float pitch) {
      this.worldObj.playSound(this.posX, this.posY, this.posZ, name, volume, pitch, false);
   }

   public boolean isServerWorld() {
      return true;
   }

   public boolean isRidingHorse() {
      return this.ridingEntity != null && this.ridingEntity instanceof EntityHorse && ((EntityHorse)this.ridingEntity).isHorseSaddled();
   }

   public float getHorseJumpPower() {
      return this.horseJumpPower;
   }

   public void openEditSign(TileEntitySign signTile) {
      this.mc.displayGuiScreen(new GuiEditSign(signTile));
   }

   public void openEditCommandBlock(CommandBlockLogic cmdBlockLogic) {
      this.mc.displayGuiScreen(new GuiCommandBlock(cmdBlockLogic));
   }

   public void displayGUIBook(ItemStack bookStack) {
      Item item = bookStack.getItem();
      if(item == Items.writable_book) {
         this.mc.displayGuiScreen(new GuiScreenBook(this, bookStack, true));
      }

   }

   public void displayGUIChest(IInventory chestInventory) {
      String s = chestInventory instanceof IInteractionObject?((IInteractionObject)chestInventory).getGuiID():"minecraft:container";
      if("minecraft:chest".equals(s)) {
         this.mc.displayGuiScreen(new GuiChest(this.inventory, chestInventory));
      } else if("minecraft:hopper".equals(s)) {
         this.mc.displayGuiScreen(new GuiHopper(this.inventory, chestInventory));
      } else if("minecraft:furnace".equals(s)) {
         this.mc.displayGuiScreen(new GuiFurnace(this.inventory, chestInventory));
      } else if("minecraft:brewing_stand".equals(s)) {
         this.mc.displayGuiScreen(new GuiBrewingStand(this.inventory, chestInventory));
      } else if("minecraft:beacon".equals(s)) {
         this.mc.displayGuiScreen(new GuiBeacon(this.inventory, chestInventory));
      } else if(!"minecraft:dispenser".equals(s) && !"minecraft:dropper".equals(s)) {
         this.mc.displayGuiScreen(new GuiChest(this.inventory, chestInventory));
      } else {
         this.mc.displayGuiScreen(new GuiDispenser(this.inventory, chestInventory));
      }

   }

   public void displayGUIHorse(EntityHorse horse, IInventory horseInventory) {
      this.mc.displayGuiScreen(new GuiScreenHorseInventory(this.inventory, horseInventory, horse));
   }

   public void displayGui(IInteractionObject guiOwner) {
      String s = guiOwner.getGuiID();
      if("minecraft:crafting_table".equals(s)) {
         this.mc.displayGuiScreen(new GuiCrafting(this.inventory, this.worldObj));
      } else if("minecraft:enchanting_table".equals(s)) {
         this.mc.displayGuiScreen(new GuiEnchantment(this.inventory, this.worldObj, guiOwner));
      } else if("minecraft:anvil".equals(s)) {
         this.mc.displayGuiScreen(new GuiRepair(this.inventory, this.worldObj));
      }

   }

   public void displayVillagerTradeGui(IMerchant villager) {
      this.mc.displayGuiScreen(new GuiMerchant(this.inventory, villager, this.worldObj));
   }

   public void onCriticalHit(Entity entityHit) {
      this.mc.effectRenderer.emitParticleAtEntity(entityHit, EnumParticleTypes.CRIT);
   }

   public void onEnchantmentCritical(Entity entityHit) {
      this.mc.effectRenderer.emitParticleAtEntity(entityHit, EnumParticleTypes.CRIT_MAGIC);
   }

   public boolean isSneaking() {
      boolean flag = this.movementInput != null?this.movementInput.sneak:false;
      return flag && !this.sleeping;
   }

   public void updateEntityActionState() {
      super.updateEntityActionState();
      if(this.isCurrentViewEntity()) {
         this.moveStrafing = this.movementInput.moveStrafe;
         this.moveForward = this.movementInput.moveForward;
         this.isJumping = this.movementInput.jump;
         this.prevRenderArmYaw = this.renderArmYaw;
         this.prevRenderArmPitch = this.renderArmPitch;
         this.renderArmPitch = (float)((double)this.renderArmPitch + (double)(this.rotationPitch - this.renderArmPitch) * 0.5D);
         this.renderArmYaw = (float)((double)this.renderArmYaw + (double)(this.rotationYaw - this.renderArmYaw) * 0.5D);
      }

   }

   protected boolean isCurrentViewEntity() {
      return this.mc.getRenderViewEntity() == this;
   }

   private void mimeow$onUpdatePre(CallbackInfo c) {
      if(!Utils.isLocalPlayerSubUpdate()
              && Timer.shouldSkipBaseLocalUpdate()) {
         this.syncPrevRenderStateToCurrent();
         c.cancel();
      } else if(!Utils.isLocalPlayerSubUpdate()) {
         if(this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0D, this.posZ))) {
            RotationUtils.prevRenderPitch = RotationUtils.renderPitch;
            RotationUtils.prevRenderYaw = RotationUtils.renderYaw;
            McpRuntime.EVENTS.post(new PreUpdateEvent());
         }

      }
   }

   private void mimeow$onUpdatePost(CallbackInfo c) {
      if(!Utils.isLocalPlayerSubUpdate()) {
         if(this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0D, this.posZ))) {
            McpRuntime.EVENTS.post(new PostUpdateEvent());
         }

         int extraUpdates = Timer.consumeExtraLocalUpdatesForBaseTick();
         if(extraUpdates > 0) {
            InterpolationState interpolationState = this.captureInterpolationState();

            for(int i = 0; i < extraUpdates; ++i) {
               Utils.beginLocalPlayerSubUpdate();

               try {
                  this.onUpdate();
               } finally {
                  Utils.endLocalPlayerSubUpdate();
               }
            }

            this.restoreInterpolationState(interpolationState);
         }
      }
   }


   public void onUpdateWalkingPlayer() {
      PreMotionEvent.setRotations = false;
      PreMotionEvent.setRenderYaw(false);
      RotationUtils.setFakeRotations = false;
      PreMotionEvent preMotionEvent = new PreMotionEvent(this.posX, this.getEntityBoundingBox().minY, this.posZ, this.rotationYaw, this.rotationPitch, this.onGround, this.isSprinting(), this.isSneaking());
      McpRuntime.EVENTS.post(preMotionEvent);
      RotationUtils.serverRotations = new float[]{preMotionEvent.getYaw(), preMotionEvent.getPitch()};
      boolean flag = preMotionEvent.isSprinting();
      if(flag != this.serverSprintState) {
         if(!Utils.isLocalPlayerSubUpdate()) {
            if(flag) {
               this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, net.minecraft.network.play.client.C0BPacketEntityAction.Action.START_SPRINTING));
            } else {
               this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, net.minecraft.network.play.client.C0BPacketEntityAction.Action.STOP_SPRINTING));
            }

            this.serverSprintState = flag;
         }
      }

      boolean flag1 = preMotionEvent.isSneaking();
      if(flag1 != this.serverSneakState) {
         if(!Utils.isLocalPlayerSubUpdate()) {
            if(flag1) {
               this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, net.minecraft.network.play.client.C0BPacketEntityAction.Action.START_SNEAKING));
            } else {
               this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, net.minecraft.network.play.client.C0BPacketEntityAction.Action.STOP_SNEAKING));
            }

            this.serverSneakState = flag1;
         }
      }

      if(this.isCurrentViewEntity()) {
         if(PreMotionEvent.setRenderYaw()) {
            RotationUtils.setRenderYaw(preMotionEvent.getYaw());
         }

         RotationUtils.renderPitch = preMotionEvent.getPitch();
         RotationUtils.renderYaw = preMotionEvent.getYaw();
         if(RotationUtils.setFakeRotations) {
            RotationUtils.renderPitch = RotationUtils.fakeRotations[1].floatValue();
            RotationUtils.renderYaw = RotationUtils.fakeRotations[0].floatValue();
            RotationUtils.setRenderYaw(RotationUtils.renderYaw);
         }

         RotationUtils.setFakeRotations = false;
         double d0 = preMotionEvent.getPosX() - this.lastReportedPosX;
         double d1 = preMotionEvent.getPosY() - this.lastReportedPosY;
         double d2 = preMotionEvent.getPosZ() - this.lastReportedPosZ;
         double d3 = (double)(preMotionEvent.getYaw() - this.lastReportedYaw);
         double d4 = (double)(preMotionEvent.getPitch() - this.lastReportedPitch);
         boolean flag2 = d0 * d0 + d1 * d1 + d2 * d2 > 9.0E-4D || this.positionUpdateTicks >= 20;
         boolean flag3 = d3 != 0.0D || d4 != 0.0D;
         if(this.ridingEntity == null) {
            if(flag2 && flag3) {
               this.sendQueue.addToSendQueue(new C06PacketPlayerPosLook(preMotionEvent.getPosX(), preMotionEvent.getPosY(), preMotionEvent.getPosZ(), preMotionEvent.getYaw(), preMotionEvent.getPitch(), preMotionEvent.isOnGround()));
            } else if(flag2) {
               this.sendQueue.addToSendQueue(new C04PacketPlayerPosition(preMotionEvent.getPosX(), preMotionEvent.getPosY(), preMotionEvent.getPosZ(), preMotionEvent.isOnGround()));
            } else if(flag3) {
               this.sendQueue.addToSendQueue(new C05PacketPlayerLook(preMotionEvent.getYaw(), preMotionEvent.getPitch(), preMotionEvent.isOnGround()));
            } else {
               this.sendQueue.addToSendQueue(new C03PacketPlayer(preMotionEvent.isOnGround()));
            }
         } else {
            this.sendQueue.addToSendQueue(new C06PacketPlayerPosLook(this.motionX, -999.0D, this.motionZ, preMotionEvent.getYaw(), preMotionEvent.getPitch(), preMotionEvent.isOnGround()));
            flag2 = false;
         }

         ++this.positionUpdateTicks;
         if(flag2) {
            this.lastReportedPosX = preMotionEvent.getPosX();
            this.lastReportedPosY = preMotionEvent.getPosY();
            this.lastReportedPosZ = preMotionEvent.getPosZ();
            this.positionUpdateTicks = 0;
         }

         if(flag3) {
            this.lastReportedYaw = preMotionEvent.getYaw();
            this.lastReportedPitch = preMotionEvent.getPitch();
         }
      }

      McpRuntime.EVENTS.post(new PostMotionEvent());
   }

   public void onLivingUpdate() {
      if(this.sprintingTicksLeft > 0) {
         --this.sprintingTicksLeft;
         if(this.sprintingTicksLeft == 0) {
            this.setSprinting(false);
         }
      }

      if(this.sprintToggleTimer > 0) {
         --this.sprintToggleTimer;
      }

      this.prevTimeInPortal = this.timeInPortal;
      if(this.inPortal) {
         if(this.mc.currentScreen != null && !this.mc.currentScreen.doesGuiPauseGame()) {
            this.mc.displayGuiScreen((GuiScreen)null);
         }

         if(this.timeInPortal == 0.0F) {
            this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("portal.trigger"), this.rand.nextFloat() * 0.4F + 0.8F));
         }

         this.timeInPortal += 0.0125F;
         if(this.timeInPortal >= 1.0F) {
            this.timeInPortal = 1.0F;
         }

         this.inPortal = false;
      } else if(this.isPotionActive(Potion.confusion) && this.getActivePotionEffect(Potion.confusion).getDuration() > 60 && (ModuleManager.antiDebuff == null || !ModuleManager.antiDebuff.canRemoveNausea(Potion.confusion))) {
         this.timeInPortal += 0.006666667F;
         if(this.timeInPortal > 1.0F) {
            this.timeInPortal = 1.0F;
         }
      } else {
         if(this.timeInPortal > 0.0F) {
            this.timeInPortal -= 0.05F;
         }

         if(this.timeInPortal < 0.0F) {
            this.timeInPortal = 0.0F;
         }
      }

      if(this.timeUntilPortal > 0) {
         --this.timeUntilPortal;
      }

      boolean flag = this.movementInput.jump;
      boolean flag1 = this.movementInput.sneak;
      float f = 0.8F;
      boolean flag2 = this.movementInput.moveForward >= f;
      this.movementInput.updatePlayerMoveState();
      boolean stopSprint = ModuleManager.noSlow == null || !ModuleManager.noSlow.isEnabled() || !NoSlow.isNewGrimMode();
      Autoblock autoblock = ModuleManager.autoblock;
      boolean suppressUsingItemSlowdown = autoblock != null
              && autoblock.shouldSuppressUsingItemSlowdown(this);
      if(this.isUsingItem() && !this.isRiding() && !suppressUsingItemSlowdown) {
         MovementInput var10000 = this.movementInput;
         float slowed = NoSlow.getSlowed();
         var10000.moveStrafe *= slowed;
         var10000 = this.movementInput;
         var10000.moveForward *= slowed;
         if(stopSprint) {
            this.sprintToggleTimer = 0;
         }
      }

      this.pushOutOfBlocks(this.posX - (double)this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ + (double)this.width * 0.35D);
      this.pushOutOfBlocks(this.posX - (double)this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ - (double)this.width * 0.35D);
      this.pushOutOfBlocks(this.posX + (double)this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ - (double)this.width * 0.35D);
      this.pushOutOfBlocks(this.posX + (double)this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ + (double)this.width * 0.35D);
      boolean flag3 = (float)this.getFoodStats().getFoodLevel() > 6.0F || this.capabilities.allowFlying;
      boolean effectiveSprintKeyDown = idle.miau.baritone.BaritoneBridge.sprintKeyDown(this, this.mc.gameSettings.keyBindSprint.isKeyDown());
      if(this.onGround && !flag1 && !flag2 && this.movementInput.moveForward >= f && !this.isSprinting() && flag3 && (!this.isUsingItem() || !stopSprint) && !this.isPotionActive(Potion.blindness)) {
         if(this.sprintToggleTimer <= 0 && !effectiveSprintKeyDown) {
            this.sprintToggleTimer = 7;
         } else {
            this.setSprinting(true);
         }
      }

      if(!this.isSprinting() && effectiveSprintKeyDown && (this.movementInput.moveForward != 0.0F || this.movementInput.moveStrafe != 0.0F) && this.movementInput.moveForward >= f && flag3 && (!this.isUsingItem() && !this.mc.thePlayer.isBlocking() || !stopSprint) && !this.isPotionActive(Potion.blindness)) {
         this.setSprinting(true);
      }

      if(this.isSprinting() && (this.movementInput.moveForward < f || !flag3 || this.isCollidedHorizontally || ModuleUtils.setSlow || this.movementInput.moveForward == 0.0F && this.movementInput.moveStrafe == 0.0F || this.mc.gameSettings.keyBindSneak.isKeyDown() || SprintReset.breakSprint())) {
         this.setSprinting(false);
      }

      McpRuntime.EVENTS.post(new PostSprintUpdateEvent());

      if(SprintReset.startSprint()) {
         this.setSprinting(true);
      }

      Sprint sprintMod = ModuleManager.sprint;
      if(!this.isSprinting() && !SprintReset.breakSprint() && sprintMod != null && sprintMod.isEnabled() && (this.movementInput.moveForward != 0.0F || this.movementInput.moveStrafe != 0.0F) && !this.mc.gameSettings.keyBindSneak.isKeyDown() && !this.isPotionActive(Potion.blindness)) {
         boolean force = false;
         if(sprintMod.allowWhileBackwards() && this.movementInput.moveForward < 0.0F) {
            force = true;
         }

         if(sprintMod.allowWhileSideways() && this.movementInput.moveForward == 0.0F && this.movementInput.moveStrafe != 0.0F) {
            force = true;
         }

         if(sprintMod.allowWhileUsingItem() && this.isUsingItem()) {
            force = true;
         }

         if(force) {
            this.setSprinting(true);
         }
      }

      if(this.capabilities.allowFlying) {
         if(this.mc.playerController.isSpectatorMode()) {
            if(!this.capabilities.isFlying) {
               this.capabilities.isFlying = true;
               this.sendPlayerAbilities();
            }
         } else if(!flag && this.movementInput.jump) {
            if(this.flyToggleTimer == 0) {
               this.flyToggleTimer = 7;
            } else {
               this.capabilities.isFlying = !this.capabilities.isFlying;
               this.sendPlayerAbilities();
               this.flyToggleTimer = 0;
            }
         }
      }

      if(this.capabilities.isFlying && this.isCurrentViewEntity()) {
         if(this.movementInput.sneak) {
            this.motionY -= (double)(this.capabilities.getFlySpeed() * 3.0F);
         }

         if(this.movementInput.jump) {
            this.motionY += (double)(this.capabilities.getFlySpeed() * 3.0F);
         }
      }

      if(this.isRidingHorse()) {
         if(this.horseJumpPowerCounter < 0) {
            ++this.horseJumpPowerCounter;
            if(this.horseJumpPowerCounter == 0) {
               this.horseJumpPower = 0.0F;
            }
         }

         if(flag && !this.movementInput.jump) {
            this.horseJumpPowerCounter = -10;
            this.sendHorseJump();
         } else if(!flag && this.movementInput.jump) {
            this.horseJumpPowerCounter = 0;
            this.horseJumpPower = 0.0F;
         } else if(flag) {
            ++this.horseJumpPowerCounter;
            if(this.horseJumpPowerCounter < 10) {
               this.horseJumpPower = (float)this.horseJumpPowerCounter * 0.1F;
            } else {
               this.horseJumpPower = 0.8F + 2.0F / (float)(this.horseJumpPowerCounter - 9) * 0.1F;
            }
         }
      } else {
         this.horseJumpPower = 0.0F;
      }

      McpRuntime.EVENTS.post(new PreSuperLivingUpdateEvent());
      super.onLivingUpdate();
      if(this.onGround && this.capabilities.isFlying && !this.mc.playerController.isSpectatorMode()) {
         this.capabilities.isFlying = false;
         this.sendPlayerAbilities();
      }

   }

   public boolean isServerSprinting() {
      return this.serverSprintState;
   }

   public boolean getServerSprintState() {
      return this.serverSprintState;
   }

   public void setServerSprintState(boolean state) {
      this.serverSprintState = state;
   }

   public boolean getServerSneakState() {
      return this.serverSneakState;
   }

   public void setServerSneakState(boolean state) {
      this.serverSneakState = state;
   }

   private InterpolationState captureInterpolationState() {
      return new InterpolationState(this.prevPosX, this.prevPosY, this.prevPosZ, this.lastTickPosX, this.lastTickPosY, this.lastTickPosZ, this.prevRotationYaw, this.prevRotationPitch, this.prevRotationYawHead, this.prevRenderYawOffset, this.prevCameraYaw, this.prevCameraPitch, this.prevDistanceWalkedModified, this.prevLimbSwingAmount, this.prevSwingProgress, this.prevChasingPosX, this.prevChasingPosY, this.prevChasingPosZ);
   }

   private void restoreInterpolationState(InterpolationState state) {
      if(state != null) {
         this.prevPosX = state.prevPosX;
         this.prevPosY = state.prevPosY;
         this.prevPosZ = state.prevPosZ;
         this.lastTickPosX = state.lastTickPosX;
         this.lastTickPosY = state.lastTickPosY;
         this.lastTickPosZ = state.lastTickPosZ;
         this.prevRotationYaw = state.prevRotationYaw;
         this.prevRotationPitch = state.prevRotationPitch;
         this.prevRotationYawHead = state.prevRotationYawHead;
         this.prevRenderYawOffset = state.prevRenderYawOffset;
         this.prevCameraYaw = state.prevCameraYaw;
         this.prevCameraPitch = state.prevCameraPitch;
         this.prevDistanceWalkedModified = state.prevDistanceWalkedModified;
         this.prevLimbSwingAmount = state.prevLimbSwingAmount;
         this.prevSwingProgress = state.prevSwingProgress;
         this.prevChasingPosX = state.prevChasingPosX;
         this.prevChasingPosY = state.prevChasingPosY;
         this.prevChasingPosZ = state.prevChasingPosZ;
      }
   }

   private void syncPrevRenderStateToCurrent() {
      this.prevPosX = this.posX;
      this.prevPosY = this.posY;
      this.prevPosZ = this.posZ;
      this.lastTickPosX = this.posX;
      this.lastTickPosY = this.posY;
      this.lastTickPosZ = this.posZ;
      this.prevRotationYaw = this.rotationYaw;
      this.prevRotationPitch = this.rotationPitch;
      this.prevRotationYawHead = this.rotationYawHead;
      this.prevRenderYawOffset = this.renderYawOffset;
      this.prevCameraYaw = this.cameraYaw;
      this.prevCameraPitch = this.cameraPitch;
      this.prevDistanceWalkedModified = this.distanceWalkedModified;
      this.prevLimbSwingAmount = this.limbSwingAmount;
      this.prevSwingProgress = this.swingProgress;
      this.prevChasingPosX = this.chasingPosX;
      this.prevChasingPosY = this.chasingPosY;
      this.prevChasingPosZ = this.chasingPosZ;
      this.prevTimeInPortal = this.timeInPortal;
   }

   private static final class InterpolationState {
      private final double prevPosX;
      private final double prevPosY;
      private final double prevPosZ;
      private final double lastTickPosX;
      private final double lastTickPosY;
      private final double lastTickPosZ;
      private final float prevRotationYaw;
      private final float prevRotationPitch;
      private final float prevRotationYawHead;
      private final float prevRenderYawOffset;
      private final float prevCameraYaw;
      private final float prevCameraPitch;
      private final float prevDistanceWalkedModified;
      private final float prevLimbSwingAmount;
      private final float prevSwingProgress;
      private final double prevChasingPosX;
      private final double prevChasingPosY;
      private final double prevChasingPosZ;

      private InterpolationState(double prevPosX, double prevPosY, double prevPosZ,
            double lastTickPosX, double lastTickPosY, double lastTickPosZ,
            float prevRotationYaw, float prevRotationPitch, float prevRotationYawHead,
            float prevRenderYawOffset, float prevCameraYaw, float prevCameraPitch,
            float prevDistanceWalkedModified, float prevLimbSwingAmount,
            float prevSwingProgress, double prevChasingPosX,
            double prevChasingPosY, double prevChasingPosZ) {
         this.prevPosX = prevPosX;
         this.prevPosY = prevPosY;
         this.prevPosZ = prevPosZ;
         this.lastTickPosX = lastTickPosX;
         this.lastTickPosY = lastTickPosY;
         this.lastTickPosZ = lastTickPosZ;
         this.prevRotationYaw = prevRotationYaw;
         this.prevRotationPitch = prevRotationPitch;
         this.prevRotationYawHead = prevRotationYawHead;
         this.prevRenderYawOffset = prevRenderYawOffset;
         this.prevCameraYaw = prevCameraYaw;
         this.prevCameraPitch = prevCameraPitch;
         this.prevDistanceWalkedModified = prevDistanceWalkedModified;
         this.prevLimbSwingAmount = prevLimbSwingAmount;
         this.prevSwingProgress = prevSwingProgress;
         this.prevChasingPosX = prevChasingPosX;
         this.prevChasingPosY = prevChasingPosY;
         this.prevChasingPosZ = prevChasingPosZ;
      }
   }

   public int getPositionUpdateTicks() {
      return this.positionUpdateTicks;
   }

   public void resetSprintToggleTimer() {
      this.sprintToggleTimer = 0;
   }

   public double getLastReportedPosX() {
      return this.lastReportedPosX;
   }

   public double getLastReportedPosY() {
      return this.lastReportedPosY;
   }

   public double getLastReportedPosZ() {
      return this.lastReportedPosZ;
   }

   public float getLastReportedYaw() {
      return this.lastReportedYaw;
   }
}
