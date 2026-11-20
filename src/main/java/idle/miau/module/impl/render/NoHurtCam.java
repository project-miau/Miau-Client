package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.SliderSetting;

public class NoHurtCam extends Module {
    public SliderSetting multiplier;
    public NoHurtCam() {
        super("NoHurtCam", category.render);
        this.registerSetting(new DescriptionSetting("Default is 14x multiplier."));
        this.registerSetting(multiplier = new SliderSetting("Multiplier", 14, -40, 40, 1));
    }
}
