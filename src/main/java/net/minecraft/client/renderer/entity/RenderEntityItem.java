package net.minecraft.client.renderer.entity;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import idle.miau.mcp.hook.ClientHooks;
import idle.miau.module.impl.render.ItemPhysics;
import idle.miau.mcp.callback.CallbackInfo;

public class RenderEntityItem extends Render<EntityItem> {
   private final RenderItem itemRenderer;
   private Random field_177079_e = new Random();
   private static final Random itemPhysics$random = new Random();
   private static long itemPhysics$lastNano = System.nanoTime();
   private static double itemPhysics$rotation;

   public RenderEntityItem(RenderManager renderManagerIn, RenderItem p_i46167_2_) {
      super(renderManagerIn);
      this.itemRenderer = p_i46167_2_;
      this.shadowSize = 0.15F;
      this.shadowOpaque = 0.75F;
   }

   private int func_177077_a(EntityItem itemIn, double p_177077_2_, double p_177077_4_, double p_177077_6_, float p_177077_8_, IBakedModel p_177077_9_) {
      ItemStack itemstack = itemIn.getEntityItem();
      Item item = itemstack.getItem();
      if(item == null) {
         return 0;
      } else {
         boolean flag = p_177077_9_.isGui3d();
         int i = this.func_177078_a(itemstack);
         float f = 0.25F;
         boolean staticItem = idle.miau.module.impl.other.Patcher.isStaticItemsActive();
         float f1 = staticItem ? 0.0F : MathHelper.sin(((float)itemIn.getAge() + p_177077_8_) / 10.0F + itemIn.hoverStart) * 0.1F + 0.1F;
         float f2 = p_177077_9_.getItemCameraTransforms().getTransform(TransformType.GROUND).scale.y;
         GlStateManager.translate((float)p_177077_2_, (float)p_177077_4_ + f1 + 0.25F * f2, (float)p_177077_6_);
         if(!staticItem && (flag || this.renderManager.options != null)) {
            float f3 = (((float)itemIn.getAge() + p_177077_8_) / 20.0F + itemIn.hoverStart) * 57.295776F;
            GlStateManager.rotate(f3, 0.0F, 1.0F, 0.0F);
         }

         if(!flag) {
            float f6 = -0.0F * (float)(i - 1) * 0.5F;
            float f4 = -0.0F * (float)(i - 1) * 0.5F;
            float f5 = -0.046875F * (float)(i - 1) * 0.5F;
            GlStateManager.translate(f6, f4, f5);
         }

         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         return i;
      }
   }

   private int func_177078_a(ItemStack stack) {
      if (idle.miau.module.impl.other.Patcher.isUnstackedItemsActive()) {
         return 1;
      }
      int i = 1;
      if(stack.stackSize > 48) {
         i = 5;
      } else if(stack.stackSize > 32) {
         i = 4;
      } else if(stack.stackSize > 16) {
         i = 3;
      } else if(stack.stackSize > 1) {
         i = 2;
      }

      return i;
   }

   public void doRender(EntityItem entity, double x, double y, double z, float entityYaw, float partialTicks) {
      ItemStack stack = entity.getEntityItem();
      if(stack == null || stack.getItem() == null || stack.stackSize <= 0) {
         return;
      }
      if (idle.miau.patcher.culling.EntityCulling.renderItem(entity)) {
         return;
      }
      CallbackInfo callbackInfo18 = new CallbackInfo("doRender", true);
      this.mimeow$itemPhysics$doRender(entity, x, y, z, entityYaw, partialTicks, callbackInfo18);
      if(!callbackInfo18.isCancelled()) {
         ItemStack itemstack = entity.getEntityItem();
         this.field_177079_e.setSeed(187L);
         boolean flag = false;
         if(this.bindEntityTexture(entity)) {
            this.renderManager.renderEngine.getTexture(this.getEntityTexture(entity)).setBlurMipmap(false, false);
            flag = true;
         }

         GlStateManager.enableRescaleNormal();
         GlStateManager.alphaFunc(516, 0.1F);
         GlStateManager.enableBlend();
         GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
         GlStateManager.pushMatrix();
         IBakedModel ibakedmodel = this.itemRenderer.getItemModelMesher().getItemModel(itemstack);
         int i = this.func_177077_a(entity, x, y, z, partialTicks, ibakedmodel);

         for(int j = 0; j < i; ++j) {
            if(ibakedmodel.isGui3d()) {
               GlStateManager.pushMatrix();
               if(j > 0) {
                  float f = (this.field_177079_e.nextFloat() * 2.0F - 1.0F) * 0.15F;
                  float f1 = (this.field_177079_e.nextFloat() * 2.0F - 1.0F) * 0.15F;
                  float f2 = (this.field_177079_e.nextFloat() * 2.0F - 1.0F) * 0.15F;
                  GlStateManager.translate(f, f1, f2);
               }

               GlStateManager.scale(0.5F, 0.5F, 0.5F);
               ibakedmodel.getItemCameraTransforms().applyTransform(TransformType.GROUND);
               this.itemRenderer.renderItem(itemstack, ibakedmodel);
               GlStateManager.popMatrix();
            } else {
               GlStateManager.pushMatrix();
               ibakedmodel.getItemCameraTransforms().applyTransform(TransformType.GROUND);
               this.itemRenderer.renderItem(itemstack, ibakedmodel);
               GlStateManager.popMatrix();
               float f3 = ibakedmodel.getItemCameraTransforms().ground.scale.x;
               float f4 = ibakedmodel.getItemCameraTransforms().ground.scale.y;
               float f5 = ibakedmodel.getItemCameraTransforms().ground.scale.z;
               GlStateManager.translate(0.0F * f3, 0.0F * f4, 0.046875F * f5);
            }
         }

         GlStateManager.popMatrix();
         GlStateManager.disableRescaleNormal();
         GlStateManager.disableBlend();
         this.bindEntityTexture(entity);
         if(flag) {
            this.renderManager.renderEngine.getTexture(this.getEntityTexture(entity)).restoreLastBlurMipmap();
         }

         super.doRender(entity, x, y, z, entityYaw, partialTicks);
      }
   }

   protected ResourceLocation getEntityTexture(EntityItem entity) {
      return TextureMap.locationBlocksTexture;
   }

   private void mimeow$itemPhysics$doRender(EntityItem entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
      if(ItemPhysics.instance != null) {
         ci.cancel();
         Minecraft mc = Minecraft.getMinecraft();
         RenderEntityItem self = this;
         double speed = ItemPhysics.instance.rotationSpeed.getInput();
         itemPhysics$rotation = (double)(System.nanoTime() - itemPhysics$lastNano) / 2500000.0D * speed;
         if(!mc.isGamePaused()) {
            itemPhysics$lastNano = System.nanoTime();
         } else {
            itemPhysics$rotation = 0.0D;
         }

         ItemStack stack = entity.getEntityItem();
         if(stack != null && stack.getItem() != null) {
            int seed = Item.getIdFromItem(stack.getItem()) + stack.getMetadata();
            itemPhysics$random.setSeed((long)seed);
            this.bindTexture(TextureMap.locationBlocksTexture);
            this.getRenderManager().renderEngine.getTexture(TextureMap.locationBlocksTexture).setBlurMipmap(false, false);
            GlStateManager.enableRescaleNormal();
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.pushMatrix();
            IBakedModel model = mc.getRenderItem().getItemModelMesher().getItemModel(stack);
            boolean is3D = model.isGui3d();
            int count = itemPhysics$getModelCount(stack);
            GlStateManager.translate((float)x, (float)y, (float)z);
            if(is3D) {
               GlStateManager.scale(0.5F, 0.5F, 0.5F);
            }

            GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(entity.rotationYaw, 0.0F, 0.0F, 1.0F);
            if(is3D) {
               GlStateManager.translate(0.0D, 0.0D, -0.08D);
            } else {
               GlStateManager.translate(0.0D, 0.0D, -0.04D);
            }

            if(!entity.onGround) {
               double rot = itemPhysics$rotation * 2.0D;
               entity.rotationPitch += (float)rot;
            } else if(!is3D) {
               entity.rotationPitch = 0.0F;
            }

            if(is3D || mc.getRenderManager().options != null) {
               GlStateManager.rotate(entity.rotationPitch, 1.0F, 0.0F, 0.0F);
            }

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            for(int k = 0; k < count; ++k) {
               GlStateManager.pushMatrix();
               if(!is3D) {
                  model = ClientHooks.handleCameraTransforms(model, TransformType.GROUND);
                  mc.getRenderItem().renderItem(stack, model);
                  GlStateManager.popMatrix();
                  GlStateManager.translate(0.0F, 0.0F, 0.05375F);
               } else {
                  if(k > 0) {
                     float ox = (itemPhysics$random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                     float oy = (itemPhysics$random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                     float oz = (itemPhysics$random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                     GlStateManager.translate(self.getRenderManager().options != null && self.getRenderManager().options.fancyGraphics?ox:0.0F, self.getRenderManager().options != null && self.getRenderManager().options.fancyGraphics?oy:0.0F, oz);
                  }

                  model = ClientHooks.handleCameraTransforms(model, TransformType.GROUND);
                  mc.getRenderItem().renderItem(stack, model);
                  GlStateManager.popMatrix();
               }
            }

            GlStateManager.popMatrix();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableBlend();
            self.bindTexture(TextureMap.locationBlocksTexture);
            self.getRenderManager().renderEngine.getTexture(TextureMap.locationBlocksTexture).restoreLastBlurMipmap();
         }
      }
   }

   private static int itemPhysics$getModelCount(ItemStack stack) {
      return stack.stackSize > 48?5:(stack.stackSize > 32?4:(stack.stackSize > 16?3:(stack.stackSize > 1?2:1)));
   }
}
