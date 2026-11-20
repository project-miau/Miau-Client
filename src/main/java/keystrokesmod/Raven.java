package keystrokesmod;

import idle.miau.Miau;
import idle.miau.clickgui.ClickGui;
import idle.miau.module.ModuleManager;
import keystrokesmod.script.ScriptManager;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class Raven {
    public static final String VERSION = "beta-17-1082626";
    public static boolean DEBUG = false;
    public static Minecraft mc = Minecraft.getMinecraft();
    public static ScriptManager scriptManager;

    public static ModuleManager getModuleManager() {
        return Miau.moduleManager;
    }

    public static ClickGui getClickGui() {
        return Miau.clickGui;
    }

    public static ScheduledExecutorService getScheduledExecutor() {
        return Miau.getScheduledExecutor();
    }

    public static ExecutorService getCachedExecutor() {
        return Miau.getCachedExecutor();
    }
}
