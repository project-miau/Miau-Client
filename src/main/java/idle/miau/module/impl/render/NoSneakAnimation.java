package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.player.Scaffold;
import idle.miau.module.setting.impl.ButtonSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

public final class NoSneakAnimation extends Module {
    private final ButtonSetting scaffoldOnly;

    public NoSneakAnimation() {
        super("NoSneakAnimation", category.render);
        registerSetting(scaffoldOnly = new ButtonSetting("Scaffold Only", false));
    }

    public static boolean isVisuallySneaking(Entity entity) {
        if (entity == null || !entity.isSneaking()) {
            return false;
        }

        NoSneakAnimation module = ModuleManager.noSneakAnimation;
        Module scaffold = ModuleManager.getModule(Scaffold.class);
        boolean scaffoldEnabled = scaffold != null && scaffold.isEnabled();
        Minecraft mc = Minecraft.getMinecraft();
        boolean self = mc != null && entity == mc.thePlayer;
        return !shouldSuppress(
                module != null && module.isEnabled(),
                module != null && module.scaffoldOnly != null && module.scaffoldOnly.isToggled(),
                scaffoldEnabled,
                self);
    }

    static boolean shouldSuppress(
            boolean enabled, boolean scaffoldOnly, boolean scaffoldEnabled, boolean self) {
        return enabled && self && (!scaffoldOnly || scaffoldEnabled);
    }
}
