package idle.miau.diagnostic;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.SliderSetting;
import keystrokesmod.script.Script;
import keystrokesmod.script.ScriptManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class MiauCrashDiagnostics {
    private static final Logger LOGGER = LogManager.getLogger("MiauCrashDiagnostics");
    private static final int BREADCRUMB_LIMIT = 64;
    private static final Breadcrumb[] BREADCRUMBS = new Breadcrumb[BREADCRUMB_LIMIT];
    private static final AtomicInteger BREADCRUMB_INDEX = new AtomicInteger(0);
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);

    private static volatile boolean cleanShutdown = false;

    private MiauCrashDiagnostics() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                record("CRASH", "Uncaught exception on [" + thread.getName() + "]: " + throwable);
                handleUncaughtException(thread, throwable);
            } catch (Throwable loggingError) {
                System.err.println("Failed to log crash diagnostics: " + loggingError.getMessage());
            } finally {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                } else {
                    throwable.printStackTrace(System.err);
                    System.exit(-1);
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!cleanShutdown) {
                record("JVM", "Abnormal JVM shutdown hook triggered");
                dumpDiagnosticSummaryToLog();
            }
        }, "Shutdown"));

        record("SYSTEM", "Miau diagnostic subsystem initialized");
    }

    public static void markCleanShutdown() {
        cleanShutdown = true;
    }

    public static void record(String category, String message) {
        if (category == null || message == null) {
            return;
        }
        int index = Math.abs(BREADCRUMB_INDEX.getAndIncrement() % BREADCRUMB_LIMIT);
        BREADCRUMBS[index] = new Breadcrumb(
                System.currentTimeMillis(),
                Thread.currentThread().getName(),
                category,
                message
        );
    }

    public static void recordModuleToggle(String moduleName, boolean enabled) {
        record("MODULE", moduleName + (enabled ? " -> ENABLED" : " -> DISABLED"));
    }

    public static void enhance(CrashReport report) {
        if (report == null) {
            return;
        }
        try {
            populateClientCategory(report);
            populateModulesCategory(report);
            populateBreadcrumbsCategory(report);
            populateMemoryCategory(report);
            populateThreadCategory(report);
            populateRootCauseCategory(report);
        } catch (Throwable t) {
            LOGGER.error("Failed to enhance crash report with Miau diagnostics", t);
        }
    }

    private static void populateClientCategory(CrashReport report) {
        CrashReportCategory category = report.makeCategory("Miau Client Status");
        category.addCrashSectionCallable("Client Identifier", () -> "Miau Client 1.8.9");
        category.addCrashSectionCallable("Runtime Mode", () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return "Headless / Uninitialized";
            if (mc.theWorld == null) return "Main Menu / GUI";
            return mc.isIntegratedServerRunning() ? "Singleplayer (Integrated)" : "Multiplayer";
        });

        category.addCrashSectionCallable("Server Connection", () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.theWorld == null) return "None";
            if (mc.isIntegratedServerRunning()) return "Local Integrated Server";
            ServerData serverData = mc.getCurrentServerData();
            if (serverData == null) return "Unknown Remote Server";
            return String.format(Locale.ROOT, "%s (%s) [ping=%dms]",
                    serverData.serverIP, serverData.serverName, serverData.pingToServer);
        });

        category.addCrashSectionCallable("Player Telemetry", () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return "Not in-game";
            EntityPlayerSP p = mc.thePlayer;
            double speedBps = Math.hypot(p.motionX, p.motionZ) * 20.0;
            return String.format(Locale.ROOT,
                    "%s [id=%d, pos=(%.2f, %.2f, %.2f), motion=(%.3f, %.3f, %.3f), bps=%.2f, onGround=%b, inWater=%b, inWeb=%b, sneak=%b, sprint=%b, health=%.1f/%.1f, food=%d]",
                    p.getName(), p.getEntityId(),
                    p.posX, p.posY, p.posZ,
                    p.motionX, p.motionY, p.motionZ,
                    speedBps, p.onGround, p.isInWater(), p.getIsInWeb(),
                    p.isSneaking(), p.isSprinting(),
                    p.getHealth(), p.getMaxHealth(),
                    p.getFoodStats().getFoodLevel());
        });

        category.addCrashSectionCallable("Held Item", () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return "None";
            ItemStack held = mc.thePlayer.getHeldItem();
            return held == null ? "Empty Hand" : held.toString();
        });

        category.addCrashSectionCallable("Active Potions", () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return "None";
            Collection<PotionEffect> effects = mc.thePlayer.getActivePotionEffects();
            if (effects == null || effects.isEmpty()) return "None";
            StringBuilder sb = new StringBuilder();
            for (PotionEffect effect : effects) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(effect.getEffectName())
                        .append(" (amp=").append(effect.getAmplifier())
                        .append(", dur=").append(effect.getDuration()).append(")");
            }
            return sb.toString();
        });

        category.addCrashSectionCallable("Scripting Engine", () -> {
            ScriptManager sm = Miau.scriptManager;
            if (sm == null || sm.scripts == null) return "Inactive / Not loaded";
            int running = 0;
            List<String> names = new ArrayList<>();
            for (Map.Entry<Script, Module> entry : sm.scripts.entrySet()) {
                Script s = entry.getKey();
                Module m = entry.getValue();
                boolean isRunning = m != null && m.isEnabled();
                if (isRunning) running++;
                names.add(s.name + (s.error ? "[ERROR]" : (isRunning ? "[ON]" : "[OFF]")));
            }
            return String.format(Locale.ROOT, "Total=%d, Running=%d -> %s",
                    sm.scripts.size(), running, names);
        });
    }

    private static void populateModulesCategory(CrashReport report) {
        CrashReportCategory category = report.makeCategory("Miau Active Modules");
        category.addCrashSectionCallable("Enabled Modules", () -> {
            ModuleManager mm = Miau.moduleManager;
            if (mm == null || mm.modules == null) return "ModuleManager not loaded";

            List<Module> enabledList = new ArrayList<>();
            for (Module m : mm.modules) {
                if (m.isEnabled()) {
                    enabledList.add(m);
                }
            }

            if (enabledList.isEmpty()) {
                return "0 enabled modules (Total registered: " + mm.modules.size() + ")";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(enabledList.size()).append(" enabled / ").append(mm.modules.size()).append(" total\n");
            for (Module m : enabledList) {
                sb.append("\t\t- ").append(m.getName());
                if (m.getSettings() != null && !m.getSettings().isEmpty()) {
                    sb.append(" [");
                    int count = 0;
                    for (Setting s : m.getSettings()) {
                        String summary = formatSetting(s);
                        if (summary != null) {
                            if (count++ > 0) sb.append(", ");
                            sb.append(summary);
                        }
                    }
                    sb.append("]");
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        });
    }

    private static String formatSetting(Setting s) {
        if (s == null) return null;
        if (s instanceof SliderSetting) {
            SliderSetting slider = (SliderSetting) s;
            if (slider.isString && slider.getOptions() != null) {
                int idx = (int) slider.getInput();
                String val = (idx >= 0 && idx < slider.getOptions().length) ? slider.getOptions()[idx] : String.valueOf(idx);
                return slider.getName() + "=" + val;
            }
            return slider.getName() + "=" + String.format(Locale.ROOT, "%.2f", slider.getInput());
        } else if (s instanceof ButtonSetting) {
            ButtonSetting button = (ButtonSetting) s;
            if (button.isToggled()) {
                return button.getName() + "=true";
            }
        } else if (s instanceof MultiSelectSetting) {
            MultiSelectSetting multi = (MultiSelectSetting) s;
            return multi.getName() + "=(" + multi.getSelectedCount() + " sel)";
        }
        return null;
    }

    private static void populateBreadcrumbsCategory(CrashReport report) {
        CrashReportCategory category = report.makeCategory("Miau Breadcrumbs (Recent Activity)");
        category.addCrashSectionCallable("Recent Action History", () -> {
            List<Breadcrumb> list = getOrderedBreadcrumbs();
            if (list.isEmpty()) {
                return "No breadcrumbs recorded";
            }
            StringBuilder sb = new StringBuilder();
            for (Breadcrumb b : list) {
                sb.append("\n\t\t").append(b.toString());
            }
            return sb.toString();
        });
    }

    private static void populateMemoryCategory(CrashReport report) {
        CrashReportCategory category = report.makeCategory("Miau Extended Memory & GC");
        category.addCrashSectionCallable("Heap Allocation", () -> {
            Runtime rt = Runtime.getRuntime();
            long total = rt.totalMemory();
            long free = rt.freeMemory();
            long used = total - free;
            long max = rt.maxMemory();
            double pctUsed = (double) used / (double) max * 100.0;
            return String.format(Locale.ROOT,
                    "Used: %d MB (%.1f%%) | Free: %d MB | Total: %d MB | Max: %d MB",
                    used / 1048576L, pctUsed, free / 1048576L, total / 1048576L, max / 1048576L);
        });

        category.addCrashSectionCallable("Non-Heap / Metaspace", () -> {
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
            return String.format(Locale.ROOT,
                    "Used: %d MB | Committed: %d MB",
                    nonHeap.getUsed() / 1048576L, nonHeap.getCommitted() / 1048576L);
        });

        category.addCrashSectionCallable("Direct Buffer Memory", () -> {
            try {
                Class<?> pd = Class.forName("io.netty.util.internal.PlatformDependent");
                java.lang.reflect.Method m = pd.getMethod("usedDirectMemory");
                long used = (Long) m.invoke(null);
                return String.format(Locale.ROOT, "%d MB (%d bytes)", used / 1048576L, used);
            } catch (Throwable ignored) {
                return "Unavailable";
            }
        });

        category.addCrashSectionCallable("Garbage Collectors", () -> {
            List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
            StringBuilder sb = new StringBuilder();
            for (GarbageCollectorMXBean gc : gcs) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(gc.getName())
                        .append(": count=").append(gc.getCollectionCount())
                        .append(", time=").append(gc.getCollectionTime()).append("ms");
            }
            return sb.toString();
        });
    }

    private static void populateThreadCategory(CrashReport report) {
        CrashReportCategory category = report.makeCategory("Miau Thread & Concurrency");
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        category.addCrashSectionCallable("Thread Summary", () ->
                String.format(Locale.ROOT, "Active: %d, Peak: %d, Daemon: %d",
                        threadBean.getThreadCount(), threadBean.getPeakThreadCount(), threadBean.getDaemonThreadCount()));

        category.addCrashSectionCallable("Deadlock Detector", () -> {
            long[] deadlocked = threadBean.findDeadlockedThreads();
            if (deadlocked == null || deadlocked.length == 0) {
                return "No deadlocks detected";
            }
            StringBuilder sb = new StringBuilder("DEADLOCK DETECTED on " + deadlocked.length + " threads: ");
            ThreadInfo[] infos = threadBean.getThreadInfo(deadlocked, 10);
            for (ThreadInfo info : infos) {
                if (info != null) {
                    sb.append("\n\t\tThread '").append(info.getThreadName()).append("' (id=").append(info.getThreadId())
                            .append(", state=").append(info.getThreadState()).append(")");
                }
            }
            return sb.toString();
        });
    }

    private static void populateRootCauseCategory(CrashReport report) {
        Throwable cause = report.getCrashCause();
        if (cause == null) return;

        CrashReportCategory category = report.makeCategory("Miau Root Cause Analysis");
        category.addCrashSectionCallable("Diagnosis", () -> diagnoseThrowable(cause));
    }

    private static String diagnoseThrowable(Throwable t) {
        if (t == null) return "No cause provided";
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            String name = cur.getClass().getName();
            String msg = cur.getMessage();
            if (cur instanceof BootstrapMethodError || name.contains("BootstrapMethodError")) {
                sb.append("BOOTSTRAP FAILURE: Dynamic callsite linking failed. Check member access/resolution: ").append(msg).append("\n");
            } else if (cur instanceof NoSuchMethodException || cur instanceof NoSuchMethodError) {
                sb.append("MISSING METHOD: Class or method was renamed, stripped, or unavailable: ").append(msg).append("\n");
            } else if (cur instanceof NoSuchFieldException || cur instanceof NoSuchFieldError) {
                sb.append("MISSING FIELD: Field reflection or accessor resolution failed: ").append(msg).append("\n");
            } else if (cur instanceof IllegalAccessError || cur instanceof IllegalAccessException) {
                sb.append("ACCESS VIOLATION: Caller lacks visibility to package-private/private member: ").append(msg).append("\n");
            } else if (cur instanceof VerifyError) {
                sb.append("BYTECODE VERIFY ERROR: Invalid bytecode frame or StackMapTable rejection: ").append(msg).append("\n");
            } else if (cur instanceof OutOfMemoryError) {
                sb.append("OUT OF MEMORY: JVM exhausted available heap or native metaspace memory.\n");
            }
            cur = cur.getCause();
        }
        return sb.length() == 0 ? "Standard exception flow" : sb.toString().trim();
    }

    private static List<Breadcrumb> getOrderedBreadcrumbs() {
        List<Breadcrumb> list = new ArrayList<>();
        int current = BREADCRUMB_INDEX.get();
        int start = Math.max(0, current - BREADCRUMB_LIMIT);
        for (int i = start; i < current; i++) {
            Breadcrumb b = BREADCRUMBS[i % BREADCRUMB_LIMIT];
            if (b != null) {
                list.add(b);
            }
        }
        return list;
    }

    private static void dumpDiagnosticSummaryToLog() {
        try {
            LOGGER.error("====================== MIAU CRASH DIAGNOSTICS ======================");
            List<Breadcrumb> crumbs = getOrderedBreadcrumbs();
            if (!crumbs.isEmpty()) {
                LOGGER.error("--- Recent Breadcrumbs (Last {} actions) ---", crumbs.size());
                for (Breadcrumb b : crumbs) {
                    LOGGER.error("{}", b);
                }
            }

            ModuleManager mm = Miau.moduleManager;
            if (mm != null && mm.modules != null) {
                List<String> enabled = new ArrayList<>();
                for (Module m : mm.modules) {
                    if (m.isEnabled()) enabled.add(m.getName());
                }
                LOGGER.error("--- Enabled Modules ({}) ---: {}", enabled.size(), enabled);
            }

            Runtime rt = Runtime.getRuntime();
            LOGGER.error("--- Memory Status ---: Used={}MB, Free={}MB, Total={}MB, Max={}MB",
                    (rt.totalMemory() - rt.freeMemory()) / 1048576L,
                    rt.freeMemory() / 1048576L,
                    rt.totalMemory() / 1048576L,
                    rt.maxMemory() / 1048576L);
            LOGGER.error("====================================================================");
        } catch (Throwable t) {
            System.err.println("Failed to dump diagnostic summary: " + t.getMessage());
        }
    }

    private static void handleUncaughtException(Thread thread, Throwable throwable) {
        try {
            CrashReport report = CrashReport.makeCrashReport(throwable, "Uncaught exception on thread " + thread.getName());
            enhance(report);
            Minecraft mc = Minecraft.getMinecraft();
            File mcDir = (mc != null && mc.mcDataDir != null) ? mc.mcDataDir : new File(".");
            File crashDir = new File(mcDir, "crash-reports");
            File crashFile = new File(crashDir, "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT).format(new Date()) + "-client.txt");
            report.saveToFile(crashFile);
            LOGGER.fatal("Saved uncaught thread crash report to: {}", crashFile.getAbsolutePath());
        } catch (Throwable t) {
            LOGGER.fatal("Failed to save emergency uncaught crash report", t);
        }
    }

    private static final class Breadcrumb {
        private final long time;
        private final String thread;
        private final String category;
        private final String message;

        private Breadcrumb(long time, String thread, String category, String message) {
            this.time = time;
            this.thread = thread;
            this.category = category;
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "[%s] [%s] [%s] %s",
                    TIME_FORMAT.format(new Date(time)), thread, category, message);
        }
    }
}
