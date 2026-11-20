package idle.miau.utility.system;

import java.awt.AWTError;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.Display;

public final class ClipboardBridge {
    private static final ClipboardBridge SYSTEM =
            new ClipboardBridge(new GlfwBackend(), new AwtBackend());

    private final Backend primary;
    private final Backend fallback;
    private String inProcessValue = "";

    ClipboardBridge(Backend primary, Backend fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    public static String getString() {
        return SYSTEM.readString();
    }

    public static void setString(String value) {
        SYSTEM.writeString(value);
    }

    synchronized String readString() {
        String value = read(primary);
        if (value == null) {
            value = read(fallback);
        }
        if (value != null) {
            inProcessValue = value;
            return value;
        }
        return inProcessValue;
    }

    synchronized void writeString(String value) {
        if (value == null) {
            return;
        }

        inProcessValue = value;
        if (!write(primary, value)) {
            write(fallback, value);
        }
    }

    private static String read(Backend backend) {
        try {
            return backend.read();
        } catch (Exception | AWTError | LinkageError ignored) {
            return null;
        }
    }

    private static boolean write(Backend backend, String value) {
        try {
            return backend.write(value);
        } catch (Exception | AWTError | LinkageError ignored) {
            return false;
        }
    }

    interface Backend {
        String read() throws Exception;

        boolean write(String value) throws Exception;
    }

    private static final class GlfwBackend implements Backend {
        @Override
        public String read() {
            if (!isAvailableOnCurrentThread()) {
                return null;
            }
            return GLFW.glfwGetClipboardString(Display.getWindow());
        }

        @Override
        public boolean write(String value) {
            if (!isAvailableOnCurrentThread()) {
                return false;
            }
            GLFW.glfwSetClipboardString(Display.getWindow(), value);
            return true;
        }

        private static boolean isAvailableOnCurrentThread() {
            Minecraft minecraft = Minecraft.getMinecraft();
            return minecraft != null
                    && minecraft.isCallingFromMinecraftThread()
                    && Display.isCreated();
        }
    }

    private static final class AwtBackend implements Backend {
        @Override
        public String read() throws Exception {
            Transferable contents =
                    Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return "";
            }
            Object value = contents.getTransferData(DataFlavor.stringFlavor);
            return value instanceof String ? (String) value : "";
        }

        @Override
        public boolean write(String value) {
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(value), null);
            return true;
        }
    }
}
