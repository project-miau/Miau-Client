package idle.miau.module.impl.world;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;

public class Weather extends Module {
    public SliderSetting time;
    public SliderSetting lightning;
    public ButtonSetting rain;

    public Weather() {
        super("Weather", category.world);
        setDescription("Changes the time and weather shown.");
        this.registerSetting(time = new SliderSetting("Time", 0, 0, 24, 0.1));
        this.registerSetting(lightning = new SliderSetting("Lightning", 0, 0, 1, 0.01));
        this.registerSetting(rain = new ButtonSetting("Rain", false));
    }
}
