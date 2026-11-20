package net.minecraft.client.resources;

import com.google.common.collect.ImmutableSet;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.optifine.reflect.ReflectorCompat;

public class DefaultResourcePack implements IResourcePack
{
    public static final Set<String> defaultResourceDomains = ImmutableSet.<String>of("minecraft", "realms", "miau");
    private final Map<String, File> mapAssets;

    public DefaultResourcePack(Map<String, File> mapAssetsIn)
    {
        this.mapAssets = mapAssetsIn;
    }

    public InputStream getInputStream(ResourceLocation location) throws IOException
    {
        InputStream inputstream = this.getResourceStream(location);

        if (inputstream != null)
        {
            return inputstream;
        }
        else
        {
            InputStream inputstream1 = this.getInputStreamAssets(location);

            if (inputstream1 != null)
            {
                return inputstream1;
            }
            else
            {
                throw new FileNotFoundException(location.getResourcePath());
            }
        }
    }

    public InputStream getInputStreamAssets(ResourceLocation location) throws IOException, FileNotFoundException
    {
        File file1 = (File)this.mapAssets.get(location.toString());
        return file1 != null && file1.isFile() ? new FileInputStream(file1) : null;
    }

    private InputStream getResourceStream(ResourceLocation location)
    {
        String s = "/assets/" + location.getResourceDomain() + "/" + location.getResourcePath();
        InputStream inputstream = ReflectorCompat.getOptiFineResourceStream(s);
        return inputstream != null ? inputstream : DefaultResourcePack.class.getResourceAsStream(s);
    }

    public boolean resourceExists(ResourceLocation location)
    {
        return this.getResourceStream(location) != null || this.mapAssets.containsKey(location.toString());
    }

    public Set<String> getResourceDomains()
    {
        return defaultResourceDomains;
    }

    public <T extends IMetadataSection> T getPackMetadata(IMetadataSerializer metadataSerializer, String metadataSectionName) throws IOException
    {
        try
        {
            InputStream inputstream = new FileInputStream((File)this.mapAssets.get("pack.mcmeta"));
            return AbstractResourcePack.readMetadata(metadataSerializer, inputstream, metadataSectionName);
        }
        catch (RuntimeException var4)
        {
            return (T)((IMetadataSection)null);
        }
        catch (FileNotFoundException var5)
        {
            return (T)((IMetadataSection)null);
        }
    }

    public BufferedImage getPackImage() throws IOException
    {
        InputStream inputstream = DefaultResourcePack.class.getResourceAsStream("/" + (new ResourceLocation("pack.png")).getResourcePath());

        if (inputstream == null)
        {
            inputstream = DefaultResourcePack.class.getResourceAsStream("/assets/minecraft/textures/misc/unknown_pack.png");
        }

        BufferedImage bufferedimage = TextureUtil.readBufferedImage(inputstream);
        if (bufferedimage != null)
        {
            return bufferedimage;
        }

        BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < fallback.getWidth(); ++x)
        {
            for (int y = 0; y < fallback.getHeight(); ++y)
            {
                int shade = ((x + y) & 1) == 0 ? 0xFF7F7F7F : 0xFF5F5F5F;
                fallback.setRGB(x, y, shade);
            }
        }
        return fallback;
    }

    public String getPackName()
    {
        return "Default";
    }
}
