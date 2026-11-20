package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.DescriptionSetting;

public class AntiShuffle extends Module {
    private static String shuffleStr = "§k";

    public AntiShuffle() {
        super("AntiShuffle", Module.category.render, 0);
        setDescription("Removes obfuscation (" + shuffleStr + "hey" + "§" + "r).");
    }

    public static String removeObfuscation(String s) {
        return s.replace(shuffleStr, "");
    }
}
