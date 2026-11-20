package idle.miau.performance;

import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class MiauDrawCalls {
    public enum Category {
        TERRAIN("Terrain"),
        SKY("Sky"),
        ENTITIES("Entities"),
        TILES("BEs"),
        PARTICLES("Particles"),
        TEXT("Text"),
        GUI("GUI"),
        OTHER("Other");

        private final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private static final long[] calls = new long[Category.values().length];
    private static final ArrayDeque<Category> sections = new ArrayDeque<>();
    private static Category category = Category.OTHER;
    private static Thread renderThread;
    private static int frames;
    private static long windowStart;
    private static List<String> lines = Collections.emptyList();

    private MiauDrawCalls() {
    }

    public static void beginFrame(boolean debug) {
        if (!debug) {
            renderThread = null;
            sections.clear();
            lines = Collections.emptyList();
            return;
        }

        long now = System.nanoTime();
        if (renderThread != Thread.currentThread()) {
            renderThread = Thread.currentThread();
            Arrays.fill(calls, 0L);
            frames = 0;
            windowStart = now;
            lines = Collections.singletonList("Draw calls: ...");
        } else {
            frames++;
            if (now - windowStart >= 1_000_000_000L && frames > 0) {
                lines = format(frames);
                Arrays.fill(calls, 0L);
                frames = 0;
                windowStart = now;
            }
        }

        sections.clear();
        category = Category.OTHER;
    }

    public static void startSection(String name) {
        if (renderThread != Thread.currentThread()) {
            return;
        }

        sections.push(category);
        Category next = categoryFor(name);
        if (next != null) {
            category = next;
        }
    }

    public static void endSection() {
        if (renderThread == Thread.currentThread() && !sections.isEmpty()) {
            category = sections.pop();
        }
    }

    public static Category enter(Category next) {
        Category previous = category;
        if (renderThread == Thread.currentThread()) {
            category = next;
        }
        return previous;
    }

    public static void exit(Category previous) {
        if (renderThread == Thread.currentThread()) {
            category = previous;
        }
    }

    public static List<String> getLines() {
        return lines;
    }

    private static Category categoryFor(String name) {
        switch (name) {
            case "terrain":
            case "terrain_setup":
            case "render_SOLID":
            case "render_CUTOUT":
            case "render_CUTOUT_MIPPED":
            case "render_TRANSLUCENT":
                return Category.TERRAIN;
            case "sky":
            case "clouds":
            case "weather":
                return Category.SKY;
            case "entities":
            case "entityOutlines":
                return Category.ENTITIES;
            case "blockentities":
                return Category.TILES;
            case "litParticles":
            case "particles":
                return Category.PARTICLES;
            case "gui":
            case "debug":
                return Category.GUI;
            default:
                return null;
        }
    }

    private static void count() {
        if (renderThread == Thread.currentThread()) {
            calls[category.ordinal()]++;
        }
    }

    private static List<String> format(int frameCount) {
        long total = 0L;
        for (long value : calls) {
            total += value;
        }

        List<String> result = new ArrayList<>(3);
        result.add("Draw calls: " + Math.round((double) total / frameCount) + "/frame");
        result.add(group(frameCount, Category.TERRAIN, Category.SKY, Category.ENTITIES, Category.TILES));
        result.add(group(frameCount, Category.PARTICLES, Category.TEXT, Category.GUI, Category.OTHER));
        return result;
    }

    private static String group(int frameCount, Category... categories) {
        StringBuilder result = new StringBuilder(" ");
        for (int i = 0; i < categories.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            Category current = categories[i];
            result.append(current.label).append(' ')
                    .append(Math.round((double) calls[current.ordinal()] / frameCount));
        }
        return result.toString();
    }

    public static void glDrawArrays(int mode, int first, int count) {
        count();
        GL11.glDrawArrays(mode, first, count);
    }

    public static void glCallList(int list) {
        count();
        GL11.glCallList(list);
    }

    public static void glCallLists(IntBuffer lists) {
        count();
        GL11.glCallLists(lists);
    }

    public static void glMultiDrawArrays(int mode, IntBuffer first, IntBuffer count) {
        count();
        GL14.glMultiDrawArrays(mode, first, count);
    }

    public static void glEnd() {
        count();
        GL11.glEnd();
    }
}
