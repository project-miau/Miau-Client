package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

public final class NoArmorOverlay extends Module {
    private static final int ALL_MODE = 0;
    private static final String[] MODES = {"All", "Self"};

    private final SliderSetting mode;

    public NoArmorOverlay() {
        super("NoArmorOverlay", category.render);
        registerSetting(mode = new SliderSetting("Mode", ALL_MODE, MODES));
    }

    public static boolean shouldHide(EntityLivingBase entity) {
        NoArmorOverlay module = ModuleManager.noArmorOverlay;
        return module != null && shouldHide(
                module.isEnabled(),
                (int) module.mode.getInput(),
                entity instanceof EntityPlayer,
                entity == Minecraft.getMinecraft().thePlayer);
    }

    static boolean shouldHide(boolean enabled, int selectedMode, boolean player, boolean self) {
        return enabled && player && (selectedMode == ALL_MODE || self);
    }
}
