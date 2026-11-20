
package org.lwjgl.opengl;

import idle.miau.Miau;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Display {

    private static final DisplayMode initial_mode;
    private static final long window;
    private static DisplayMode current_mode;
    private static String title = "Minecraft 1.8.9";
    private static boolean created;
    private static boolean resized;
    private static boolean focused;
    private static boolean vsync;
    private static boolean fullscreen;
    private static int windowedX;
    private static int windowedY;
    private static int windowedWidth;
    private static int windowedHeight;
    private static int windowWidth = 1;
    private static int windowHeight = 1;
    private static int framebufferWidth = 1;
    private static int framebufferHeight = 1;
    private static boolean visible;
    private static final GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
    private static GLFWWindowSizeCallback windowSizeCallback;
    private static GLFWFramebufferSizeCallback framebufferSizeCallback;
    private static GLFWWindowFocusCallback windowFocusCallback;
    private static GLFWWindowIconifyCallback windowIconifyCallback;


    static {
        glfwSetErrorCallback(errorCallback);

        if (!glfwInit())
            throw new RuntimeException("Failed to initialize GLFW");

        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        int bpp = vidMode.redBits() + vidMode.greenBits() + vidMode.blueBits();
        current_mode = initial_mode = new DisplayMode(vidMode.width(), vidMode.height(), bpp, vidMode.refreshRate());
        windowedWidth = current_mode.getWidth();
        windowedHeight = current_mode.getHeight();

        
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE);


        window = glfwCreateWindow(current_mode.getWidth(), current_mode.getHeight(), title, NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        refreshWindowMetrics();
        glfwMakeContextCurrent(window);

        GL.createCapabilities();
    }

    public static DisplayMode getDisplayMode() {
        return current_mode;
    }

    public static void setDisplayMode(DisplayMode displayMode) {
        current_mode = displayMode;
        if (!fullscreen) {
            windowedWidth = displayMode.getWidth();
            windowedHeight = displayMode.getHeight();
            windowedX = (initial_mode.getWidth() - displayMode.getWidth()) / 2;
            windowedY = (initial_mode.getHeight() - displayMode.getHeight()) / 2;
            glfwSetWindowSize(window, windowedWidth, windowedHeight);
            glfwSetWindowPos(window, windowedX, windowedY);
            refreshWindowMetrics();
            resized = true;
        }
    }

    public static void setFullscreen(boolean fullscreen) {
        if (Display.fullscreen == fullscreen) {
            return;
        }
        Display.fullscreen = fullscreen;
        if (fullscreen) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer x = stack.mallocInt(1);
                IntBuffer y = stack.mallocInt(1);
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                glfwGetWindowPos(window, x, y);
                glfwGetWindowSize(window, width, height);
                windowedX = x.get(0);
                windowedY = y.get(0);
                windowedWidth = width.get(0);
                windowedHeight = height.get(0);
            }
            GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            current_mode = new DisplayMode(mode.width(), mode.height(),
                    mode.redBits() + mode.greenBits() + mode.blueBits(), mode.refreshRate());
            glfwSetWindowMonitor(window, glfwGetPrimaryMonitor(), 0, 0,
                    mode.width(), mode.height(), mode.refreshRate());
        } else {
            current_mode = new DisplayMode(windowedWidth, windowedHeight);
            glfwSetWindowMonitor(window, NULL, windowedX, windowedY,
                    windowedWidth, windowedHeight, GLFW_DONT_CARE);
        }
        refreshWindowMetrics();
        resized = true;
    }

    public static void setResizable(boolean resizable) {
        glfwSetWindowAttrib(window, GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
    }

    public static void setTitle(String titleIn) {
        title = titleIn;
        glfwSetWindowTitle(window, titleIn);
    }

    public static int setIcon(ByteBuffer[] byteBuffers) {
        int count = Math.min(byteBuffers.length, 2);
        GLFWImage.Buffer images = GLFWImage.malloc(count);
        ByteBuffer[] directPixels = new ByteBuffer[count];
        try {
            for (int index = 0; index < count; index++) {
                int size = index == 0 ? 16 : 32;
                ByteBuffer source = byteBuffers[index].duplicate();
                directPixels[index] = BufferUtils.createByteBuffer(source.remaining());
                directPixels[index].put(source).flip();
                images.get(index).set(size, size, directPixels[index]);
            }
            glfwSetWindowIcon(window, images);
        } finally {
            images.free();
        }
        return count;
    }


    public static void setVSyncEnabled(boolean vsyncIn) {
        if (glfwGetCurrentContext() != 0) {
            glfwSwapInterval(vsyncIn ? GLFW_TRUE : GLFW_FALSE);
        }
        vsync = vsyncIn;
    }

    public static void destroy() {
        if (!created) {
            return;
        }
        // NanoVG owns context resources; release them before GLFW detaches
        // and destroys the current context. The service shutdown is idempotent.
        try {
            if (Miau.notificationService != null) {
                Miau.notificationService.shutdown();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
        }
        Mouse.destroy();
        Keyboard.destroy();
        glfwSetWindowSizeCallback(window, null);
        glfwSetFramebufferSizeCallback(window, null);
        glfwSetWindowFocusCallback(window, null);
        glfwSetWindowIconifyCallback(window, null);
        if (windowSizeCallback != null) windowSizeCallback.free();
        if (framebufferSizeCallback != null) framebufferSizeCallback.free();
        if (windowFocusCallback != null) windowFocusCallback.free();
        if (windowIconifyCallback != null) windowIconifyCallback.free();
        glfwMakeContextCurrent(NULL);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null);
        errorCallback.free();
        created = false;
    }

    public static DisplayMode[] getAvailableDisplayModes() {
        GLFWVidMode.Buffer m = glfwGetVideoModes(glfwGetPrimaryMonitor());
        ArrayList<DisplayMode> modes = new ArrayList<>();
        while (m.hasRemaining()) {
            GLFWVidMode vidMode = m.get();
            int bpp = vidMode.redBits() + vidMode.greenBits() + vidMode.blueBits();
            modes.add(new DisplayMode(vidMode.width(), vidMode.height(), bpp, vidMode.refreshRate()));
        }
        return modes.toArray(new DisplayMode[modes.size()]);
    }

    public static DisplayMode getDesktopDisplayMode() {
        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        int bpp = vidMode.redBits() + vidMode.greenBits() + vidMode.blueBits();
        return new DisplayMode(vidMode.width(), vidMode.height(), bpp, vidMode.refreshRate());
    }

    public static boolean isCreated() {
        return created;
    }

    public static boolean isCloseRequested() {
        return glfwWindowShouldClose(window);
    }

    public static void sync(int fps) {
        Sync.sync(fps);
    }

    public static void update() {
        if (!created) {
            return;
        }
        resized = false;
        if (visible && glfwGetWindowAttrib(window, GLFW_ICONIFIED) == GLFW_FALSE) {
            glfwSwapBuffers(window);
        }
        Mouse.poll_scrollY = 0;
        glfwPollEvents();
        Mouse.poll();
    }

    public static boolean wasResized() {
        return resized;
    }

    public static int getWidth() {
        return framebufferWidth;
    }

    public static int getHeight() {
        return framebufferHeight;
    }

    public static boolean isActive() {
        // The window is created hidden, so the cached value is initially
        // false and normally depends on a later focus callback. Query GLFW
        // directly as well so a delayed/dropped compositor callback cannot
        // leave all mouse polling disabled for the lifetime of the client.
        return created
                ? glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE
                : focused;
    }

    public static boolean isVisible() {
        return visible;
    }

    public static long getWindow() {
        return window;
    }

    public static int getWindowWidth() {
        return windowWidth;
    }

    public static int getWindowHeight() {
        return windowHeight;
    }

    private static void refreshWindowMetrics() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            glfwGetWindowSize(window, width, height);
            if (width.get(0) > 0 && height.get(0) > 0) {
                windowWidth = width.get(0);
                windowHeight = height.get(0);
            }
            width.clear();
            height.clear();
            glfwGetFramebufferSize(window, width, height);
            if (width.get(0) > 0 && height.get(0) > 0) {
                framebufferWidth = width.get(0);
                framebufferHeight = height.get(0);
            }
        }
    }

    public static void releaseContext() {
        glfwMakeContextCurrent(NULL);
    }

    public static void create() throws LWJGLException {
        create(new PixelFormat(), new ContextAttribs());
    }

    public static void create(PixelFormat pf) throws LWJGLException {
        create(pf, new ContextAttribs());
    }

    public static void create(PixelFormat pf, ContextAttribs attributes) throws LWJGLException {
        created = true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); 
            IntBuffer pHeight = stack.mallocInt(1); 

            
            glfwGetWindowSize(window, pWidth, pHeight);

            
            glfwSetWindowPos(
                    window,
                    (initial_mode.getWidth() - pWidth.get(0)) / 2,
                    (initial_mode.getHeight() - pHeight.get(0)) / 2
            );
        }
        windowSizeCallback = new GLFWWindowSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                if (width > 0 && height > 0) {
                    windowWidth = width;
                    windowHeight = height;
                    if (!fullscreen) {
                        current_mode = new DisplayMode(width, height);
                        windowedWidth = width;
                        windowedHeight = height;
                    }
                }
                resized = true;
            }
        };
        framebufferSizeCallback = new GLFWFramebufferSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                if (width > 0 && height > 0) {
                    framebufferWidth = width;
                    framebufferHeight = height;
                }
                resized = true;
            }
        };
        windowFocusCallback = new GLFWWindowFocusCallback() {
            @Override
            public void invoke(long l, boolean focused) {
                Display.focused = focused;
                Mouse.poll_xPos = Mouse.last_x;
                Mouse.poll_yPos = Mouse.last_y;
                if (!focused) {
                    Keyboard.releaseAllKeys();
                    Mouse.releaseAllButtons();
                }
            }
        };
        windowIconifyCallback = new GLFWWindowIconifyCallback() {
            @Override
            public void invoke(long window, boolean iconified) {
                visible = !iconified;
                if (iconified) {
                    Keyboard.releaseAllKeys();
                    Mouse.releaseAllButtons();
                }
            }
        };
        glfwSetWindowSizeCallback(window, windowSizeCallback);
        glfwSetFramebufferSizeCallback(window, framebufferSizeCallback);
        glfwSetWindowFocusCallback(window, windowFocusCallback);
        glfwSetWindowIconifyCallback(window, windowIconifyCallback);

        Keyboard.create();
        Keyboard.pollGLFW();
        Mouse.create();
        Mouse.pollGLFW();
        focused = glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE;

        
        if (glfwRawMouseMotionSupported())
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);

        refreshWindowMetrics();
        glfwSwapInterval(vsync ? GLFW_TRUE : GLFW_FALSE);
        glfwShowWindow(window);
        visible = true;
    }
}
