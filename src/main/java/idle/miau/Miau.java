package idle.miau;

import idle.miau.clickgui.ClickGui;
import idle.miau.clickgui.panel.MiauPanelClickGui;
import idle.miau.clickgui.modern.MiauClickGui;
import idle.miau.command.CommandManager;
import idle.miau.event.PostConfigLoadEvent;
import idle.miau.event.PostSetSliderEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.helper.DebugHelper;
import idle.miau.helper.MouseHelper;
import idle.miau.helper.PingHelper;
import idle.miau.helper.RotationHelper;
import idle.miau.lag.handler.LagRangePacketController;
import idle.miau.lag.handler.UnifiedLagHandler;
import idle.miau.management.LagManager;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.KillAuraLifecycle;
import idle.miau.notifications.MiauNotificationService;
import idle.miau.utility.BlockHighlightSharedHandler;
import idle.miau.utility.BadPacketsComponent;
import idle.miau.utility.EntityTickTracker;
import idle.miau.utility.Utils;
import idle.miau.utility.ModuleUtils;
import idle.miau.utility.PacketsHandler;
import idle.miau.utility.PlayerRelationsManager;
import idle.miau.utility.PlayerStateTracker;
import idle.miau.utility.ReflectionUtils;
import idle.miau.utility.BacktrackPacketController;
import idle.miau.utility.config.Config;
import idle.miau.utility.config.ConfigManager;
import idle.miau.utility.discord.DiscordPresence;
import idle.miau.utility.inventory.SilentHotbar;
import idle.miau.utility.irc.MiauIRC;
import idle.miau.module.setting.impl.SliderSetting;
import dev.idle.igam.AccountManager;
import net.minecraft.client.Minecraft;
import idle.miau.mcp.McpRuntime;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent.ClientTickEvent;
import idle.miau.mcp.event.game.TickEvent.Phase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
public class Miau {
    public static boolean DEBUG = false;

    public static Minecraft mc = Minecraft.getMinecraft();

    private static final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2, daemonFactory("Miau-Scheduled"));
    private static final ExecutorService cachedExecutor = Executors.newCachedThreadPool(daemonFactory("Miau-Worker"));

    public static ModuleManager moduleManager;
    public static ClickGui clickGui;
    public static MiauPanelClickGui panelClickGui;
    public static MiauClickGui miauClickGui;
    public static ConfigManager configManager;
    public static CommandManager commandManager;
    public static PlayerRelationsManager playerRelationsManager;
    public static Config currentConfig;
    public static PacketsHandler packetsHandler;
    public static BacktrackPacketController backtrackPacketController;
    public static LagManager lagManager;
    public static LagRangePacketController lagRangePacketController;
    public static UnifiedLagHandler lagHandler;
    public static MiauNotificationService notificationService;
    public static keystrokesmod.script.ScriptManager scriptManager;

    private static boolean firstLoad = false;

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public Miau() {
        moduleManager = new ModuleManager();
    }
    public void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(scheduledExecutor::shutdown));
        Runtime.getRuntime().addShutdownHook(new Thread(cachedExecutor::shutdown));
        Runtime.getRuntime().addShutdownHook(new Thread(MiauIRC.getInstance()::shutdown));
        Runtime.getRuntime().addShutdownHook(new Thread(
                DiscordPresence.getInstance()::shutdown,
                "Miau-Discord-RPC-Shutdown"
        ));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (ModuleManager.spotifyMod != null) ModuleManager.spotifyMod.shutdown();
            } catch (Exception ex) {
                if (DEBUG) {
                    ex.printStackTrace();
                }
            }
        }));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (scriptManager != null) {
                scriptManager.shutdown();
            }
        }, "Miau-Scripting-Shutdown"));

        McpRuntime.EVENTS.register(this);
        McpRuntime.EVENTS.register(new DebugHelper());
        McpRuntime.EVENTS.register(new MouseHelper());
        McpRuntime.EVENTS.register(RotationHelper.get());
        McpRuntime.EVENTS.register(new PingHelper());
        McpRuntime.EVENTS.register(packetsHandler = new PacketsHandler());
        McpRuntime.EVENTS.register(new ModuleUtils());
        McpRuntime.EVENTS.register(new PlayerStateTracker());
        McpRuntime.EVENTS.register(new KillAuraLifecycle());
        McpRuntime.EVENTS.register(new BadPacketsComponent());
        McpRuntime.EVENTS.register(EntityTickTracker.INSTANCE);
        McpRuntime.EVENTS.register(SilentHotbar.INSTANCE);
        McpRuntime.EVENTS.register(backtrackPacketController = new BacktrackPacketController());
        McpRuntime.EVENTS.register(lagManager = new LagManager());
        McpRuntime.EVENTS.register(lagRangePacketController = new LagRangePacketController());
        McpRuntime.EVENTS.register(lagHandler = new UnifiedLagHandler());
        notificationService = new MiauNotificationService();
        McpRuntime.EVENTS.register(notificationService);

        ReflectionUtils.setupFields();
        playerRelationsManager = new PlayerRelationsManager();
        playerRelationsManager.load();
        moduleManager.register();
        scriptManager = new keystrokesmod.script.ScriptManager();
        keystrokesmod.Raven.scriptManager = scriptManager;
        scriptManager.loadScripts();
        McpRuntime.EVENTS.register(new BlockHighlightSharedHandler());
        clickGui = new ClickGui();
        panelClickGui = new MiauPanelClickGui();
        miauClickGui = new MiauClickGui();
        configManager = new ConfigManager();
        configManager.loadConfigs();
        String selectedConfigName = configManager.getLastSelectedConfigName();
        if (configManager.getConfig(selectedConfigName) == null) {
            selectedConfigName = "default";
        }
        if (configManager.getConfig(selectedConfigName) != null) {
            configManager.loadConfig(selectedConfigName);
        }
        ReflectionUtils.setKeyBindings();
        ModuleManager.synchronizeOrganizedModules();
        notificationService.finishBoot();

        commandManager = new CommandManager();

        AccountManager.init();
    }

    @Subscribe
    public void onTick(ClientTickEvent e) {
        if (e.phase == Phase.END) {
            DiscordPresence.getInstance().update(mc);
            if (Utils.nullCheck()) {
                if (ReflectionUtils.ERROR) {
                    Utils.sendMessage("&cThere was an error, relaunch the game.");
                    ReflectionUtils.ERROR = false;
                }

                MouseHelper.updateWheelCache();

                for (Module module : new java.util.ArrayList<>(getModuleManager().getModules())) {
                    if (mc.currentScreen == null && module.canBeEnabled()) {
                        module.onKeyBind();
                    }
                    else if (mc.currentScreen instanceof ClickGui || mc.currentScreen instanceof MiauPanelClickGui || mc.currentScreen instanceof MiauClickGui) {
                        module.guiUpdate();
                        module.syncKeyBindState();
                    }
                    else {
                        module.syncKeyBindState();
                    }

                    if (module.isEnabled()) {
                        module.onUpdate();
                    }
                }
                if (mc.currentScreen instanceof ClickGui || mc.currentScreen instanceof MiauPanelClickGui || mc.currentScreen instanceof MiauClickGui) {
                    if (applyKillAuraRangeConstraints()) {
                        clickGui.onSliderChange();
                    }
                }
            }
        }
        else {
            MouseHelper.clearWheelCache();
            if (mc.currentScreen == null && Utils.nullCheck()) {
                for (Config config : Miau.configManager.configs) {
                    config.getModule().onKeyBind();
                }
            }
            else if (Utils.nullCheck()) {
                for (Config config : Miau.configManager.configs) {
                    config.getModule().syncKeyBindState();
                }
            }
        }
    }

    @Subscribe
    public void onPostConfigLoad(PostConfigLoadEvent e) {
        ModuleManager.synchronizeOrganizedModules();
        applyKillAuraRangeConstraints();
        clickGui.onSliderChange();
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onSendPacket(SendPacketEvent event) {
        if (!(event.getPacket() instanceof net.minecraft.network.play.client.C01PacketChatMessage) || commandManager == null) {
            return;
        }
        String message = ((net.minecraft.network.play.client.C01PacketChatMessage) event.getPacket()).getMessage();
        if (!commandManager.isCommand(message)) {
            return;
        }
        event.setCanceled(true);
        if (mc.isCallingFromMinecraftThread()) {
            commandManager.handleChatMessage(message);
        } else {
            mc.addScheduledTask(() -> commandManager.handleChatMessage(message));
        }
    }

    @Subscribe
    public void onPostSetSlider(PostSetSliderEvent e) {
        applyKillAuraRangeConstraints();
        clickGui.onSliderChange();
        markCurrentConfigUnsaved();
    }
    @Subscribe
    public void onEntityJoinWorld(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            firstLoad = true;
            idle.miau.utility.FrozenEntitySync.get().clearAll();
            idle.miau.utility.PacketUtils.clearEventSkips();
            if (lagManager != null) {
                lagManager.clearConnection();
            }
            if (lagRangePacketController != null) {
                lagRangePacketController.resetAll(false);
            }
        }
    }

    public static ModuleManager getModuleManager() {
        return moduleManager;
    }

    public static void markCurrentConfigUnsaved() {
        if (currentConfig != null && currentConfig.getModule() != null) {
            currentConfig.getModule().saved = false;
        }
    }

    public static ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    public static ExecutorService getCachedExecutor() {
        return cachedExecutor;
    }

    public static void handleFrozenKeybinds() {
        if (!Utils.nullCheck()) return;

        MouseHelper.updateWheelCache();

        if (mc.currentScreen == null) {
            for (Module module : getModuleManager().getModules()) {
                if (module.canBeEnabled()) {
                    module.onKeyBind();
                }
            }
        } else if (mc.currentScreen instanceof ClickGui || mc.currentScreen instanceof MiauPanelClickGui || mc.currentScreen instanceof MiauClickGui) {
            for (Module module : getModuleManager().getModules()) {
                module.guiUpdate();
                module.syncKeyBindState();
            }
        } else {
            for (Module module : getModuleManager().getModules()) {
                module.syncKeyBindState();
            }
        }
    }

    private boolean applyKillAuraRangeConstraints() {
        if (ModuleManager.killAura == null) {
            return false;
        }

        SliderSetting attackRange = ModuleManager.killAura.getAttackRangeSetting();
        SliderSetting swingRange = ModuleManager.killAura.getSwingRangeSetting();
        SliderSetting aimRange = ModuleManager.killAura.getAimRangeSetting();
        if (attackRange == null || swingRange == null || aimRange == null) {
            return false;
        }

        boolean changed = false;
        double attack = attackRange.getInput();
        double swing = swingRange.getInput();

        if (swing < attack) {
            swingRange.setValue(attack);
            swing = swingRange.getInput();
            changed = true;
        }

        if (aimRange.getInput() < swing) {
            aimRange.setValue(swing);
            changed = true;
        }

        return changed;
    }

}
