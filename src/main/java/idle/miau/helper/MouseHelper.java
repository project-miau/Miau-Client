package idle.miau.helper;

import idle.miau.Miau;
import idle.miau.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import idle.miau.mcp.event.client.MouseEvent;
import idle.miau.mcp.event.Subscribe;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class MouseHelper {
    private static Minecraft mc = Minecraft.getMinecraft();

    private static List<Long> a = new ArrayList();
    private static List<Long> b = new ArrayList();

    public static long LL = 0L;
    public static long LR = 0L;

    private static int cachedWheelDelta = 0;
    private static boolean wheelDeltaCached = false;

    public static void updateWheelCache() {
        cachedWheelDelta = Mouse.getDWheel();
        wheelDeltaCached = true;
    }

    public static boolean isScrollDown(int key) {
        if (!wheelDeltaCached) {
            cachedWheelDelta = Mouse.getDWheel();
            wheelDeltaCached = true;
        }

        if (cachedWheelDelta != 0) {
            if (key == 1069) {
                return cachedWheelDelta > 0;
            }
            else if (key == 1070) {
                return cachedWheelDelta < 0;
            }
        }
        return false;
    }

    public static void clearWheelCache() {
        wheelDeltaCached = false;
        cachedWheelDelta = 0;
    }

    @Subscribe
    public void onMouse(MouseEvent e) {
        if (!e.buttonstate) {
            return;
        }
        if (e.button == 0) {
            aL();
            if (Miau.DEBUG && mc.objectMouseOver != null) {
                Entity en = mc.objectMouseOver.entityHit;
                if (en == null || !(en instanceof EntityLivingBase)) {
                    return;
                }

                Utils.printInfo((EntityLivingBase) en);
            }
        } else if (e.button == 1) {
            aR();
        }
    }

    public static void aL() {
        a.add(LL = System.currentTimeMillis());
    }

    public static void aR() {
        b.add(LR = System.currentTimeMillis());
    }

    public static int f() {
        a.removeIf(o -> o < System.currentTimeMillis() - 1000L);
        return a.size();
    }

    public static int i() {
        b.removeIf(o -> o < System.currentTimeMillis() - 1000L);
        return b.size();
    }
}
