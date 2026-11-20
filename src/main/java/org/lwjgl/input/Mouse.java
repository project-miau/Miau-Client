
package org.lwjgl.input;

import org.lwjgl.opengl.Display;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWCursorEnterCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public class Mouse {

    private static boolean created;

    private static String[]	buttonName;
    private static final Map<String, Integer> buttonMap	= new HashMap<>(8);
    private static ByteBuffer readBuffer;
    private static ByteBuffer	buttons;
    private static final int BUFFER_SIZE = 50;
    
    private static final int BUTTON_COUNT = 8;
    
    
    public static final int EVENT_SIZE = 4 + 1 + 4 + 4 + 4 + 4 + 4;
    private static MouseEvent current_Event;
    private static boolean isGrabbed;

    private static int last_event_raw_x;
    private static int last_event_raw_y;
    private static  boolean clipMouseCoordinatesToWindow;

    private static int poll_button = 0;
    private static int poll_action = 0;
    public static int poll_xPos = 0;
    public static int poll_yPos = 0;
    public static int poll_scrollY = 0;
    private static int current_dx = 0;
    private static int current_dy = 0;
    public static int last_x = 0;
    public static int last_y = 0;
    private static boolean poll_outside;
    private static int ignoreNextMove;
    private static GLFWMouseButtonCallback mouseButtonCallback;
    private static GLFWCursorPosCallback cursorPosCallback;
    private static GLFWScrollCallback scrollCallback;
    private static GLFWCursorEnterCallback cursorEnterCallback;
    private static final DoubleBuffer cursorXBuffer = BufferUtils.createDoubleBuffer(1);
    private static final DoubleBuffer cursorYBuffer = BufferUtils.createDoubleBuffer(1);


    public static void pollGLFW(){
        if(!created) return;
        mouseButtonCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long l, int i, int i1, int i2) {
                // A cursor-position callback is not guaranteed immediately
                // after a window is shown or focused. Query GLFW at the click
                // boundary so the queued LWJGL 2 event cannot retain (0, 0)
                // or another stale position.
                refreshCursorPosition(l, false);
                poll_button = i;
                poll_action = i1;
                if (i >= 0 && i < BUTTON_COUNT) {
                    buttons.put(i, (byte)i1);
                }
                queueEvent(i, i1, poll_xPos, poll_yPos, 0);
            }
        };
        cursorPosCallback = new GLFWCursorPosCallback() {
            @Override
            public void invoke(long l, double v, double v1) {
                if(Display.isActive()){
                    updateCursorPosition(v, v1, true);
                }
            }
        };
        scrollCallback = new GLFWScrollCallback() {
            @Override
            public void invoke(long l, double v, double v1) {
                int wheel = (int)Math.round(v1 * 120.0D);
                addScrollEvent(wheel);
            }
        };
        cursorEnterCallback = new GLFWCursorEnterCallback() {
            @Override
            public void invoke(long l, boolean entered) {
                poll_outside = entered;
            }
        };
        glfwSetMouseButtonCallback(Display.getWindow(), mouseButtonCallback);
        glfwSetCursorPosCallback(Display.getWindow(), cursorPosCallback);
        glfwSetScrollCallback(Display.getWindow(), scrollCallback);
        glfwSetCursorEnterCallback(Display.getWindow(), cursorEnterCallback);
    }
    public static void createEvent(){
    }



    public static void create(){
        if(created) return;
        buttons = BufferUtils.createByteBuffer(BUTTON_COUNT);
        readBuffer = ByteBuffer.allocate(EVENT_SIZE * BUFFER_SIZE);
        readBuffer.limit(0);
        clipMouseCoordinatesToWindow = true;
        created = true;

        buttonName = new String[8];
        for (int i = 0; i < 8; i++) {
            buttonName[i] = "BUTTON" + i;
            buttonMap.put(buttonName[i], i);
        }

    }

    public static void destroy() {
        if ((mouseButtonCallback != null || cursorPosCallback != null || scrollCallback != null || cursorEnterCallback != null) && Display.isCreated()) {
            glfwSetMouseButtonCallback(Display.getWindow(), null);
            glfwSetCursorPosCallback(Display.getWindow(), null);
            glfwSetScrollCallback(Display.getWindow(), null);
            glfwSetCursorEnterCallback(Display.getWindow(), null);
        }
        if (mouseButtonCallback != null) mouseButtonCallback.free();
        if (cursorPosCallback != null) cursorPosCallback.free();
        if (scrollCallback != null) scrollCallback.free();
        if (cursorEnterCallback != null) cursorEnterCallback.free();
        mouseButtonCallback = null;
        cursorPosCallback = null;
        scrollCallback = null;
        cursorEnterCallback = null;
        created = false;
        buttons = null;
        readBuffer = null;
        current_Event = null;
        current_dx = 0;
        current_dy = 0;
        poll_scrollY = 0;
    }

    public static void poll() {
        if (!created || !Display.isCreated()) return;
        // Keep polling semantics working even when a compositor does not
        // deliver a cursor callback for a warp/focus transition.
        refreshCursorPosition(Display.getWindow(), true);
        pollButtonStates(Display.getWindow());
    }
    public static boolean next(){
        if(!created) throw new IllegalStateException("Mouse must be created before you can read events");
        if(readBuffer.hasRemaining()){
            MouseEvent event = new MouseEvent();
            event.eventButton = readBuffer.getInt();
            event.eventState = readBuffer.get() != 0;

            int new_event_x = readBuffer.getInt();
            int new_event_y = readBuffer.getInt();
            event.dx = new_event_x - last_event_raw_x;
            event.dy = new_event_y - last_event_raw_y;
            event.x = new_event_x;
            event.y = new_event_y;
            last_event_raw_x = new_event_x;
            last_event_raw_y = new_event_y;
            if(clipMouseCoordinatesToWindow) {
                event.x = Math.min(Display.getWidth() - 1, Math.max(0, event.x));
                event.y = Math.min(Display.getHeight() - 1, Math.max(0, event.y));
            }
            event.event_dwheel = readBuffer.getInt();
            event.nanoseconds = readBuffer.getLong();
            current_Event = event;
            return true;
        }
        return false;
    }


    public static int getButtonIndex(String buttonName) {
        Integer ret = buttonMap.get(buttonName);
        if (ret == null)
            return -1;
        else
            return ret;
    }

    public static String getButtonName(int button) {
        if (button >= buttonName.length || button < 0)
            return null;
        else
            return buttonName[button];
    }

    public static boolean isInsideWindow() {
        return poll_outside;
    }
    public static int getEventButton(){
        return current_Event.eventButton;
    }
    public static boolean getEventButtonState(){
        return current_Event.eventState;
    }
    public static int getEventDWheel(){
        return current_Event.event_dwheel;
    }
    public static int getEventX(){
        return current_Event.x;
    }
    public static int getEventY(){
        return current_Event.y;
    }
    public static int getEventDX(){
        return current_Event.dx;
    }
    public static int getEventDY(){
        return current_Event.dy;
    }
    public static long getEventNanoseconds(){
        return current_Event.nanoseconds;
    }
    public static boolean isCreated(){
        return created;
    }
    public static int getX(){
        return Math.min(Math.max(poll_xPos, 0), Display.getWidth() - 1);
    }
    public static int getY(){
        return Math.min(Math.max(poll_yPos, 0), Display.getHeight() - 1);
    }
    public static void setClipMouseCoordinatesToWindow(boolean clip) {
        clipMouseCoordinatesToWindow = clip;
    }
    public static boolean isButtonDown(int button){
        if (!created) throw new IllegalStateException("Mouse must be created before you can poll the button state");
        if (button >= BUTTON_COUNT || button < 0)
            return false;
        else
            return buttons.get(button) == 1;
    }
    public static boolean isGrabbed() {
        return isGrabbed;
    }

    public static void setGrabbed(boolean grab){
        if (!created) throw new IllegalStateException("Mouse must be created before you can poll the button state");
        if (isGrabbed == grab) return;
        isGrabbed = grab;
        if(!grab) glfwSetInputMode(Display.getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        else glfwSetInputMode(Display.getWindow(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        ignoreNextMove++;
        current_dx = 0;
        current_dy = 0;
    }
    public static void setCursorPosition(int x, int y){
        if (!isCreated()) throw new IllegalStateException("Mouse is not created");
        poll_xPos = x;
        poll_yPos = y;
        last_x = x;
        last_y = y;
        last_event_raw_x = x;
        last_event_raw_y = y;
        ignoreNextMove++;
        if (current_Event != null) {
            current_Event.x = x;
            current_Event.y = y;
        }
        setNativeCursorPosition(x, y);
    }
    public static int getDX(){
        int result = current_dx;
        current_dx = 0;
        return result;
    }
    public static int getDY(){
        int result = current_dy;
        current_dy = 0;
        return result;
    }

    private static int scaleToFramebuffer(double coordinate, int windowSize, int framebufferSize) {
        return (int)Math.round(coordinate * framebufferSize / Math.max(1, windowSize));
    }

    private static void refreshCursorPosition(long window, boolean queueMoveEvent) {
        cursorXBuffer.clear();
        cursorYBuffer.clear();
        glfwGetCursorPos(window, cursorXBuffer, cursorYBuffer);
        updateCursorPosition(cursorXBuffer.get(0), cursorYBuffer.get(0), queueMoveEvent);
    }

    private static void updateCursorPosition(double nativeX, double nativeY, boolean queueMoveEvent) {
        int nextX = scaleToFramebuffer(
                nativeX, Display.getWindowWidth(), Display.getWidth());
        int nextY = Display.getHeight() - scaleToFramebuffer(
                nativeY, Display.getWindowHeight(), Display.getHeight());
        if (nextX == poll_xPos && nextY == poll_yPos) return;

        last_x = poll_xPos;
        last_y = poll_yPos;
        poll_xPos = nextX;
        poll_yPos = nextY;
        if (ignoreNextMove > 0) {
            ignoreNextMove--;
            last_x = poll_xPos;
            last_y = poll_yPos;
        } else {
            current_dx += poll_xPos - last_x;
            current_dy += poll_yPos - last_y;
        }
        if (queueMoveEvent) {
            addMoveEvent(poll_xPos, poll_yPos);
        }
    }

    private static void pollButtonStates(long window) {
        for (int button = 0; button < BUTTON_COUNT; button++) {
            updatePolledButtonState(button, glfwGetMouseButton(window, button));
        }
    }

    static void updatePolledButtonState(int button, int action) {
        if (!created || button < 0 || button >= BUTTON_COUNT) return;
        int normalizedAction = action == GLFW_PRESS ? GLFW_PRESS : GLFW_RELEASE;
        if (buttons.get(button) == (byte)normalizedAction) return;

        poll_button = button;
        poll_action = normalizedAction;
        buttons.put(button, (byte)normalizedAction);
        queueEvent(button, normalizedAction, poll_xPos, poll_yPos, 0);
    }

    private static void setNativeCursorPosition(int x, int y) {
        double nativeX = (double)x * Display.getWindowWidth() / Math.max(1, Display.getWidth());
        double nativeY = (double)(Display.getHeight() - y) * Display.getWindowHeight() / Math.max(1, Display.getHeight());
        glfwSetCursorPos(Display.getWindow(), nativeX, nativeY);
    }

    public static int getDWheel() {
        int result = poll_scrollY;
        poll_scrollY = 0;
        return result;
    }

    static void addButtonEvent(int button, boolean pressed) {
        if (!created || button < 0 || button >= BUTTON_COUNT) return;
        poll_button = button;
        poll_action = pressed ? GLFW_PRESS : GLFW_RELEASE;
        buttons.put(button, (byte)poll_action);
        queueEvent(button, poll_action, poll_xPos, poll_yPos, 0);
    }

    static void addMoveEvent(int x, int y) {
        queueEvent(-1, 0, x, y, 0);
    }

    static void addScrollEvent(int wheel) {
        if (!created || wheel == 0) return;
        poll_scrollY += wheel;
        queueEvent(-1, 0, poll_xPos, poll_yPos, wheel);
    }

    public static void releaseAllButtons() {
        if (buttons == null) return;
        for (int i = 0; i < buttons.capacity(); i++) {
            buttons.put(i, (byte)0);
        }
        current_dx = 0;
        current_dy = 0;
    }

    private static void queueEvent(int button, int action, int x, int y, int wheel) {
        if (!created) return;
        readBuffer.compact();
        if (button == -1 && action == 0 && wheel == 0 && replaceLastMoveEvent(x, y)) {
            readBuffer.flip();
            poll_button = -1;
            poll_action = 0;
            return;
        }
        if (readBuffer.remaining() < EVENT_SIZE) {
            readBuffer.flip();
            readBuffer.position(Math.min(EVENT_SIZE, readBuffer.limit()));
            readBuffer.compact();
        }
        readBuffer.putInt(button);
        readBuffer.put((byte)action);
        readBuffer.putInt(x);
        readBuffer.putInt(y);
        readBuffer.putInt(wheel);
        readBuffer.putLong(System.nanoTime());
        readBuffer.flip();
        poll_button = -1;
        poll_action = 0;
    }

    private static boolean replaceLastMoveEvent(int x, int y) {
        int end = readBuffer.position();
        if (end < EVENT_SIZE) return false;
        int start = end - EVENT_SIZE;
        if (readBuffer.getInt(start) != -1 || readBuffer.get(start + 4) != 0
                || readBuffer.getInt(start + 13) != 0) {
            return false;
        }
        readBuffer.putInt(start + 5, x);
        readBuffer.putInt(start + 9, y);
        readBuffer.putLong(start + 17, System.nanoTime());
        return true;
    }


    private static final class MouseEvent {

        private int eventButton;
        private boolean eventState;

        private int dx;
        private int dy;
        private int x;
        private int y;
        private int event_dwheel;
        private long nanoseconds;

    }
}
