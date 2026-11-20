package net.minecraft.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.event.ClickEvent;
import net.minecraft.src.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class ScreenShotHelper
{
    private static final Logger logger = LogManager.getLogger();
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
    private static IntBuffer pixelBuffer;
    private static int[] pixelValues;
    private static final ThreadPoolExecutor screenshotWriter = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(2),
            new ThreadFactory()
            {
                public Thread newThread(Runnable runnable)
                {
                    Thread thread = new Thread(runnable, "Screenshot Writer");
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    public static IChatComponent saveScreenshot(File gameDirectory, int width, int height, Framebuffer buffer)
    {
        return saveScreenshot(gameDirectory, (String)null, width, height, buffer);
    }

    public static IChatComponent saveScreenshot(File gameDirectory, String screenshotName, int width, int height, Framebuffer buffer)
    {
        try
        {
            File file1 = new File(gameDirectory, "screenshots");
            file1.mkdir();
            Minecraft minecraft = Minecraft.getMinecraft();
            int originalWidth = width;
            int originalHeight = height;
            int i = Config.getGameSettings().guiScale;
            ScaledResolution scaledresolution = new ScaledResolution(minecraft);
            int j = scaledresolution.getScaleFactor();
            int k = Config.getScreenshotSize();
            boolean flag = OpenGlHelper.isFramebufferEnabled() && k > 1;

            if (flag)
            {
                Config.getGameSettings().guiScale = j * k;
                resize(width * k, height * k);
                GlStateManager.pushMatrix();
                GlStateManager.clear(16640);
                minecraft.getFramebuffer().bindFramebuffer(true);
                minecraft.entityRenderer.updateCameraAndRender(Config.renderPartialTicks, System.nanoTime());
            }

            if (OpenGlHelper.isFramebufferEnabled())
            {
                width = buffer.framebufferTextureWidth;
                height = buffer.framebufferTextureHeight;
            }

            int l = width * height;

            if (pixelBuffer == null || pixelBuffer.capacity() < l)
            {
                pixelBuffer = BufferUtils.createIntBuffer(l);
                pixelValues = new int[l];
            }

            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            pixelBuffer.clear();

            if (OpenGlHelper.isFramebufferEnabled())
            {
                GlStateManager.bindTexture(buffer.framebufferTexture);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer)pixelBuffer);
            }
            else
            {
                GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer)pixelBuffer);
            }

            pixelBuffer.get(pixelValues);
            final int[] capturedPixels = new int[l];
            System.arraycopy(pixelValues, 0, capturedPixels, 0, l);
            final int capturedWidth = width;
            final int capturedHeight = height;
            final boolean framebufferEnabled = OpenGlHelper.isFramebufferEnabled();
            final int framebufferWidth = buffer.framebufferWidth;
            final int framebufferHeight = buffer.framebufferHeight;
            final int framebufferTextureWidth = buffer.framebufferTextureWidth;
            final int framebufferTextureHeight = buffer.framebufferTextureHeight;

            if (flag)
            {
                minecraft.getFramebuffer().unbindFramebuffer();
                GlStateManager.popMatrix();
                Config.getGameSettings().guiScale = i;
                resize(originalWidth, originalHeight);
            }

            File file2;
            boolean reservedFile = false;

            if (screenshotName == null)
            {
                file2 = reserveTimestampedPNGFile(file1);
                reservedFile = true;
            }
            else
            {
                file2 = new File(file1, screenshotName);
            }

            file2 = file2.getCanonicalFile();
            final File outputFile = file2;
            final boolean deleteOnFailure = reservedFile;

            try
            {
                screenshotWriter.execute(new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            TextureUtil.processPixelValues(capturedPixels, capturedWidth, capturedHeight);
                            BufferedImage image = createImage(capturedPixels, capturedWidth, capturedHeight,
                                    framebufferEnabled, framebufferWidth, framebufferHeight,
                                    framebufferTextureWidth, framebufferTextureHeight);

                            if (!ImageIO.write(image, "png", outputFile))
                            {
                                throw new IOException("No PNG image writer is available");
                            }
                        }
                        catch (final Exception exception)
                        {
                            if (deleteOnFailure)
                            {
                                outputFile.delete();
                            }
                            logger.warn((String)"Couldn't save screenshot", (Throwable)exception);
                            reportAsyncFailure(exception);
                        }
                    }
                });
            }
            catch (RejectedExecutionException exception)
            {
                if (reservedFile)
                {
                    file2.delete();
                }
                return new ChatComponentTranslation("screenshot.failure", new Object[] {"screenshot queue is full"});
            }

            IChatComponent ichatcomponent = new ChatComponentText(file2.getName());
            ichatcomponent.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file2.getAbsolutePath()));
            ichatcomponent.getChatStyle().setUnderlined(Boolean.valueOf(true));
            return new ChatComponentTranslation("screenshot.success", new Object[] {ichatcomponent});
        }
        catch (Exception exception)
        {
            logger.warn((String)"Couldn\'t save screenshot", (Throwable)exception);
            return new ChatComponentTranslation("screenshot.failure", new Object[] {exception.getMessage()});
        }
    }

    private static BufferedImage createImage(int[] pixels, int width, int height, boolean framebufferEnabled,
                                             int framebufferWidth, int framebufferHeight,
                                             int framebufferTextureWidth, int framebufferTextureHeight)
    {
        if (framebufferEnabled)
        {
            BufferedImage image = new BufferedImage(framebufferWidth, framebufferHeight, BufferedImage.TYPE_INT_RGB);
            int sourceY = framebufferTextureHeight - framebufferHeight;
            image.setRGB(0, 0, framebufferWidth, framebufferHeight, pixels,
                    sourceY * framebufferTextureWidth, framebufferTextureWidth);
            return image;
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static void reportAsyncFailure(final Exception exception)
    {
        final Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(new Runnable()
        {
            public void run()
            {
                if (minecraft.ingameGUI != null)
                {
                    minecraft.ingameGUI.getChatGUI().printChatMessage(
                            new ChatComponentTranslation("screenshot.failure", new Object[] {exception.getMessage()}));
                }
            }
        });
    }

    private static synchronized File reserveTimestampedPNGFile(File gameDirectory) throws IOException
    {
        String s = dateFormat.format(new Date()).toString();
        int i = 1;

        while (true)
        {
            File file1 = new File(gameDirectory, s + (i == 1 ? "" : "_" + i) + ".png");

            if (file1.createNewFile())
            {
                return file1;
            }

            ++i;
        }
    }

    private static void resize(int p_resize_0_, int p_resize_1_)
    {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.displayWidth = Math.max(1, p_resize_0_);
        minecraft.displayHeight = Math.max(1, p_resize_1_);

        if (minecraft.currentScreen != null)
        {
            ScaledResolution scaledresolution = new ScaledResolution(minecraft);
            minecraft.currentScreen.onResize(minecraft, scaledresolution.getScaledWidth(), scaledresolution.getScaledHeight());
        }

        updateFramebufferSize();
    }

    private static void updateFramebufferSize()
    {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.getFramebuffer().createBindFramebuffer(minecraft.displayWidth, minecraft.displayHeight);

        if (minecraft.entityRenderer != null)
        {
            minecraft.entityRenderer.updateShaderGroupSize(minecraft.displayWidth, minecraft.displayHeight);
        }
    }
}
