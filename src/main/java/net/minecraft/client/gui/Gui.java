package net.minecraft.client.gui;

import idle.miau.performance.MiauDrawCalls;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class Gui
{
    public static final ResourceLocation optionsBackground = new ResourceLocation("textures/gui/options_background.png");
    public static final ResourceLocation statIcons = new ResourceLocation("textures/gui/container/stats_icons.png");
    public static final ResourceLocation icons = new ResourceLocation("textures/gui/icons.png");
    protected float zLevel;

     


    protected void drawHorizontalLine(int startX, int endX, int y, int color)
    {
        if (endX < startX)
        {
            int i = startX;
            startX = endX;
            endX = i;
        }

        drawRect(startX, y, endX + 1, y + 1, color);
    }

     


    protected void drawVerticalLine(int x, int startY, int endY, int color)
    {
        if (endY < startY)
        {
            int i = startY;
            startY = endY;
            endY = i;
        }

        drawRect(x, startY + 1, x + 1, endY, color);
    }

     


    public static void drawRect(int left, int top, int right, int bottom, int color)
    {
        if (left < right)
        {
            int i = left;
            left = right;
            right = i;
        }

        if (top < bottom)
        {
            int j = top;
            top = bottom;
            bottom = j;
        }

        float f3 = (float)(color >> 24 & 255) / 255.0F;
        float f = (float)(color >> 16 & 255) / 255.0F;
        float f1 = (float)(color >> 8 & 255) / 255.0F;
        float f2 = (float)(color & 255) / 255.0F;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(f, f1, f2, f3);
        worldrenderer.begin(7, DefaultVertexFormats.POSITION);
        worldrenderer.pos((double)left, (double)bottom, 0.0D).endVertex();
        worldrenderer.pos((double)right, (double)bottom, 0.0D).endVertex();
        worldrenderer.pos((double)right, (double)top, 0.0D).endVertex();
        worldrenderer.pos((double)left, (double)top, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

     



    protected void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor)
    {
        float f = (float)(startColor >> 24 & 255) / 255.0F;
        float f1 = (float)(startColor >> 16 & 255) / 255.0F;
        float f2 = (float)(startColor >> 8 & 255) / 255.0F;
        float f3 = (float)(startColor & 255) / 255.0F;
        float f4 = (float)(endColor >> 24 & 255) / 255.0F;
        float f5 = (float)(endColor >> 16 & 255) / 255.0F;
        float f6 = (float)(endColor >> 8 & 255) / 255.0F;
        float f7 = (float)(endColor & 255) / 255.0F;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldrenderer.pos((double)right, (double)top, (double)this.zLevel).color(f1, f2, f3, f).endVertex();
        worldrenderer.pos((double)left, (double)top, (double)this.zLevel).color(f1, f2, f3, f).endVertex();
        worldrenderer.pos((double)left, (double)bottom, (double)this.zLevel).color(f5, f6, f7, f4).endVertex();
        worldrenderer.pos((double)right, (double)bottom, (double)this.zLevel).color(f5, f6, f7, f4).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

     


    public void drawCenteredString(FontRenderer fontRendererIn, String text, int x, int y, int color)
    {
        fontRendererIn.drawStringWithShadow(text, (float)(x - fontRendererIn.getStringWidth(text) / 2), (float)y, color);
    }

     


    public void drawString(FontRenderer fontRendererIn, String text, int x, int y, int color)
    {
        fontRendererIn.drawStringWithShadow(text, (float)x, (float)y, color);
    }

     


    public void drawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height)
    {
        float f = 0.00390625F;
        float f1 = 0.00390625F;
        drawTexturedQuad(
                (double)x,
                (double)y,
                (double)(x + width),
                (double)(y + height),
                (double)this.zLevel,
                (double)((float)textureX * f),
                (double)((float)textureY * f1),
                (double)((float)(textureX + width) * f),
                (double)((float)(textureY + height) * f1));
    }

     


    public void drawTexturedModalRect(float xCoord, float yCoord, int minU, int minV, int maxU, int maxV)
    {
        float f = 0.00390625F;
        float f1 = 0.00390625F;
        drawTexturedQuad(
                (double)xCoord,
                (double)yCoord,
                (double)(xCoord + (float)maxU),
                (double)(yCoord + (float)maxV),
                (double)this.zLevel,
                (double)((float)minU * f),
                (double)((float)minV * f1),
                (double)((float)(minU + maxU) * f),
                (double)((float)(minV + maxV) * f1));
    }

     


    public void drawTexturedModalRect(int xCoord, int yCoord, TextureAtlasSprite textureSprite, int widthIn, int heightIn)
    {
        drawTexturedQuad(
                (double)xCoord,
                (double)yCoord,
                (double)(xCoord + widthIn),
                (double)(yCoord + heightIn),
                (double)this.zLevel,
                (double)textureSprite.getMinU(),
                (double)textureSprite.getMinV(),
                (double)textureSprite.getMaxU(),
                (double)textureSprite.getMaxV());
    }

    private static void drawTexturedQuad(double left, double top, double right, double bottom,
                                         double z, double minU, double minV, double maxU, double maxV)
    {
        if (OpenGlHelper.isReducedRenderMode())
        {
            drawTexturedQuadImmediate(left, top, right, bottom, z, minU, minV, maxU, maxV);
            return;
        }

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(left, bottom, z).tex(minU, maxV).endVertex();
        worldrenderer.pos(right, bottom, z).tex(maxU, maxV).endVertex();
        worldrenderer.pos(right, top, z).tex(maxU, minV).endVertex();
        worldrenderer.pos(left, top, z).tex(minU, minV).endVertex();
        tessellator.draw();
    }

    private static void drawTexturedQuadImmediate(double left, double top, double right, double bottom,
                                                   double z, double minU, double minV, double maxU, double maxV)
    {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(minU, maxV);
        GL11.glVertex3d(left, bottom, z);
        GL11.glTexCoord2d(maxU, maxV);
        GL11.glVertex3d(right, bottom, z);
        GL11.glTexCoord2d(maxU, minV);
        GL11.glVertex3d(right, top, z);
        GL11.glTexCoord2d(minU, minV);
        GL11.glVertex3d(left, top, z);
        MiauDrawCalls.glEnd();
    }

     


    public static void drawModalRectWithCustomSizedTexture(int x, int y, float u, float v, int width, int height, float textureWidth, float textureHeight)
    {
        float f = 1.0F / textureWidth;
        float f1 = 1.0F / textureHeight;
        drawTexturedQuad(
                (double)x,
                (double)y,
                (double)(x + width),
                (double)(y + height),
                0.0D,
                (double)(u * f),
                (double)(v * f1),
                (double)((u + (float)width) * f),
                (double)((v + (float)height) * f1));
    }

     


    public static void drawScaledCustomSizeModalRect(int x, int y, float u, float v, int uWidth, int vHeight, int width, int height, float tileWidth, float tileHeight)
    {
        float f = 1.0F / tileWidth;
        float f1 = 1.0F / tileHeight;
        drawTexturedQuad(
                (double)x,
                (double)y,
                (double)(x + width),
                (double)(y + height),
                0.0D,
                (double)(u * f),
                (double)(v * f1),
                (double)((u + (float)uWidth) * f),
                (double)((v + (float)vHeight) * f1));
    }
}
