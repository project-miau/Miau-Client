package net.optifine.reflect;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapData;

public class ReflectorCompat
{
    public static Object EVENT_RESULT_ALLOW = Reflector.getFieldValue(Reflector.Event_Result_ALLOW);
    public static Object EVENT_RESULT_DENY = Reflector.getFieldValue(Reflector.Event_Result_DENY);
    public static Object EVENT_RESULT_DEFAULT = Reflector.getFieldValue(Reflector.Event_Result_DEFAULT);

    public static void OptionalLoaderClientHandler_trackBrokenTexture(ResourceLocation loc, String message)
    {
        if (!Reflector.OptionalLoaderClientHandler_trackBrokenTexture.exists())
        {
            Object object = Reflector.call(Reflector.OptionalLoaderClientHandler_instance, new Object[0]);
            Reflector.call(object, Reflector.OptionalLoaderClientHandler_trackBrokenTexture, new Object[] {loc, message});
        }
    }

    public static void OptionalLoaderClientHandler_trackMissingTexture(ResourceLocation loc)
    {
        if (!Reflector.OptionalLoaderClientHandler_trackMissingTexture.exists())
        {
            Object object = Reflector.call(Reflector.OptionalLoaderClientHandler_instance, new Object[0]);
            Reflector.call(object, Reflector.OptionalLoaderClientHandler_trackMissingTexture, new Object[] {loc});
        }
    }

    public static void putLaunchBlackboard(String key, Object value)
    {
        Map map = (Map)Reflector.getFieldValue(Reflector.Launch_blackboard);

        if (map != null)
        {
            map.put(key, value);
        }
    }

    public static boolean renderFirstPersonHand(RenderGlobal renderGlobal, float partialTicks, int pass)
    {
        return !Reflector.CompatHooksClient_renderFirstPersonHand.exists() ? false : Reflector.callBoolean(Reflector.CompatHooksClient_renderFirstPersonHand, new Object[] {renderGlobal, Float.valueOf(partialTicks), Integer.valueOf(pass)});
    }

    public static InputStream getOptiFineResourceStream(String path)
    {
        if (!Reflector.OptiFineClassTransformer_instance.exists())
        {
            return null;
        }
        else
        {
            Object object = Reflector.getFieldValue(Reflector.OptiFineClassTransformer_instance);

            if (object == null)
            {
                return null;
            }
            else
            {
                if (path.startsWith("/"))
                {
                    path = path.substring(1);
                }

                byte[] abyte = (byte[])((byte[])Reflector.call(object, Reflector.OptiFineClassTransformer_getOptiFineResource, new Object[] {path}));

                if (abyte == null)
                {
                    return null;
                }
                else
                {
                    InputStream inputstream = new ByteArrayInputStream(abyte);
                    return inputstream;
                }
            }
        }
    }

    public static boolean blockHasTileEntity(IBlockState state)
    {
        Block block = state.getBlock();
        return !Reflector.CompatBlock_hasTileEntity.exists() ? block.hasTileEntity() : Reflector.callBoolean(block, Reflector.CompatBlock_hasTileEntity, new Object[] {state});
    }

    public static boolean isItemDamaged(ItemStack stack)
    {
        return !Reflector.CompatItem_showDurabilityBar.exists() ? stack.isItemDamaged() : Reflector.callBoolean(stack.getItem(), Reflector.CompatItem_showDurabilityBar, new Object[] {stack});
    }

    public static boolean armorHasOverlay(ItemArmor itemArmor, ItemStack itemStack)
    {
        int i = itemArmor.getColor(itemStack);
        return i != -1;
    }

    public static MapData getMapData(ItemMap itemMap, ItemStack stack, World world)
    {
        return Reflector.CompatHooksClient.exists() ? ((ItemMap)stack.getItem()).getMapData(stack, world) : itemMap.getMapData(stack, world);
    }

    public static String[] getCompatModIds()
    {
        if (!Reflector.Loader.exists())
        {
            return new String[0];
        }
        else
        {
            Object object = Reflector.call(Reflector.Loader_instance, new Object[0]);
            List list = (List)Reflector.call(object, Reflector.Loader_getActiveModList, new Object[0]);

            if (list == null)
            {
                return new String[0];
            }
            else
            {
                List<String> list1 = new ArrayList();

                for (Object object1 : list)
                {
                    if (Reflector.ModContainer.isInstance(object1))
                    {
                        String s = Reflector.callString(object1, Reflector.ModContainer_getModId, new Object[0]);

                        if (s != null)
                        {
                            list1.add(s);
                        }
                    }
                }

                String[] astring = (String[])((String[])list1.toArray(new String[list1.size()]));
                return astring;
            }
        }
    }

    public static boolean canEntitySpawn(EntityLiving entityliving, World world, float x, float y, float z)
    {
        Object object = Reflector.call(Reflector.CompatEventFactory_canEntitySpawn, new Object[] {entityliving, world, Float.valueOf(x), Float.valueOf(y), Float.valueOf(z)});
        return object == EVENT_RESULT_ALLOW || object == EVENT_RESULT_DEFAULT && entityliving.getCanSpawnHere() && entityliving.isNotColliding();
    }

    public static boolean doSpecialSpawn(EntityLiving entityliving, World world, float x, int y, float z)
    {
        return Reflector.CompatEventFactory_doSpecialSpawn.exists() ? Reflector.callBoolean(Reflector.CompatEventFactory_doSpecialSpawn, new Object[] {entityliving, world, Float.valueOf(x), Integer.valueOf(y), Float.valueOf(z)}): false;
    }
}
