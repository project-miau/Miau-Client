package idle.miau.performance;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;

public final class MiauFrameStats {
    private static final int WINDOW = 1000;
    private static final int QUERY_COUNT = 8;
    private static final long[] frameTimes = new long[WINDOW];
    private static final boolean[] pending = new boolean[QUERY_COUNT];
    private static final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean() instanceof ThreadMXBean
            ? (ThreadMXBean) ManagementFactory.getThreadMXBean() : null;
    private static final OperatingSystemMXBean systemBean = ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean
            ? (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean() : null;
    private static int frameCount;
    private static int frameIndex;
    private static int querySlot;
    private static int[] starts;
    private static int[] ends;
    private static boolean enabled;
    private static boolean issued;
    private static long frameStart;
    private static long windowStart;
    private static long gpuNanos;
    private static long lastAllocated;
    private static GLCapabilities queryCapabilities;
    private static boolean arbTimerQuery;
    private static String frameLine;
    private static String gpuSuffix;
    private static String allocationLine;
    private static String cpuLine;

    private MiauFrameStats() {
    }

    public static void beginFrame(boolean debug) {
        long now = System.nanoTime();
        if (!debug) {
            enabled = false;
            return;
        }

        if (!enabled) {
            enabled = true;
            frameCount = 0;
            frameIndex = 0;
            frameStart = now;
            windowStart = now;
            gpuNanos = 0L;
            lastAllocated = allocatedBytes();
            frameLine = null;
            gpuSuffix = null;
            allocationLine = null;
            cpuLine = null;
            Arrays.fill(pending, false);
            initializeQueries();
        } else {
            frameTimes[frameIndex] = now - frameStart;
            frameIndex = (frameIndex + 1) % WINDOW;
            frameCount = Math.min(frameCount + 1, WINDOW);
            frameStart = now;
        }

        if (starts != null) {
            collectGpu();
            querySlot = (querySlot + 1) % QUERY_COUNT;
            issued = !pending[querySlot];
            if (issued) {
                queryCounter(starts[querySlot]);
            }
        }

        if (now - windowStart >= 1_000_000_000L) {
            publish(now - windowStart);
            windowStart = now;
        }
    }

    public static void endFrame() {
        if (enabled && issued) {
            queryCounter(ends[querySlot]);
            pending[querySlot] = true;
            issued = false;
        }
    }

    private static void initializeQueries() {
        GLCapabilities capabilities = GL.getCapabilities();
        if (!capabilities.OpenGL33 && !capabilities.GL_ARB_timer_query) {
            starts = null;
            ends = null;
            return;
        }
        if (starts != null && queryCapabilities == capabilities) {
            return;
        }
        arbTimerQuery = !capabilities.OpenGL33;
        starts = new int[QUERY_COUNT];
        ends = new int[QUERY_COUNT];
        GL15C.glGenQueries(starts);
        GL15C.glGenQueries(ends);
        queryCapabilities = capabilities;
    }

    private static void collectGpu() {
        for (int i = 0; i < QUERY_COUNT; i++) {
            if (pending[i] && GL15C.glGetQueryObjecti(ends[i], GL15C.GL_QUERY_RESULT_AVAILABLE) != 0) {
                gpuNanos += queryResult(ends[i]) - queryResult(starts[i]);
                pending[i] = false;
            }
        }
    }

    private static void queryCounter(int query) {
        if (arbTimerQuery) {
            ARBTimerQuery.glQueryCounter(query, GL33C.GL_TIMESTAMP);
        } else {
            GL33C.glQueryCounter(query, GL33C.GL_TIMESTAMP);
        }
    }

    private static long queryResult(int query) {
        return arbTimerQuery
                ? ARBTimerQuery.glGetQueryObjectui64(query, GL15C.GL_QUERY_RESULT)
                : GL33C.glGetQueryObjectui64(query, GL15C.GL_QUERY_RESULT);
    }

    private static void publish(long windowNanos) {
        if (frameCount > 0) {
            long[] sorted = Arrays.copyOf(frameTimes, frameCount);
            Arrays.sort(sorted);
            long total = 0L;
            for (long time : sorted) {
                total += time;
            }
            frameLine = String.format(Locale.ROOT, "Frame: %.1f avg, %.1f p99, %.1f max ms",
                    total / (double) frameCount / 1_000_000.0,
                    sorted[(int) Math.ceil(frameCount * 0.99) - 1] / 1_000_000.0,
                    sorted[frameCount - 1] / 1_000_000.0);
        }

        if (starts != null) {
            gpuSuffix = " GPU: " + Math.min(100, Math.round(gpuNanos * 100.0 / windowNanos)) + "%";
            gpuNanos = 0L;
        }

        long allocated = allocatedBytes();
        if (allocated >= 0L && lastAllocated >= 0L) {
            allocationLine = "Allocation rate: "
                    + Math.round((allocated - lastAllocated) * 1_000_000_000.0 / windowNanos / 1048576.0)
                    + "MB/s";
        }
        lastAllocated = allocated;

        if (systemBean != null) {
            double cpu = systemBean.getProcessCpuLoad();
            cpuLine = cpu >= 0.0 ? "Process CPU: " + Math.round(cpu * 100.0) + "%" : null;
        }
    }

    private static long allocatedBytes() {
        return threadBean != null && threadBean.isThreadAllocatedMemorySupported()
                && threadBean.isThreadAllocatedMemoryEnabled()
                ? threadBean.getTotalThreadAllocatedBytes() : -1L;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String getFrameLine() {
        return frameLine;
    }

    public static String getGpuSuffix() {
        return gpuSuffix;
    }

    public static String getAllocationLine() {
        return allocationLine;
    }

    public static String getCpuLine() {
        return cpuLine;
    }
}
