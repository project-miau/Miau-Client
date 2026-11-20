package idle.miau.module.impl.client;

import idle.miau.module.Module;
import net.minecraft.client.renderer.OpenGlHelper;

public final class LowGraphics extends Module {
    public LowGraphics() {
        super("Low Graphics", category.other);
        setDescription("Reduces visual effects to improve performance.");
    }

    @Override
    public void onEnable() {
        OpenGlHelper.setReducedRenderMode(true);
    }

    @Override
    public void onDisable() {
        OpenGlHelper.setReducedRenderMode(false);
    }
}
