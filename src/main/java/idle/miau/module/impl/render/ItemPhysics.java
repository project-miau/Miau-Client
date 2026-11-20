package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.SliderSetting;

public class ItemPhysics extends Module {
    public static ItemPhysics instance;

    public final SliderSetting rotationSpeed;

    public ItemPhysics() {
        super("ItemPhysics", category.render, 0);
        setDescription("Adds rotation and resting motion to dropped items.");
        this.registerSetting(rotationSpeed = new SliderSetting("Rotation speed", 1.0, 0.0, 5.0, 0.1));
    }

    @Override
    public void onEnable() {
        instance = this;
    }

    @Override
    public void onDisable() {
        instance = null;
    }
}
