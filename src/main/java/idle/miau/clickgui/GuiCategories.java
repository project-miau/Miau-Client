package idle.miau.clickgui;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.impl.world.Weather;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class GuiCategories {
    private static final List<Module.category> ALL = Collections.unmodifiableList(
            Arrays.stream(Module.category.values()).filter(value -> value != Module.category.fun)
                    .collect(Collectors.toList()));
    private static final List<Module.category> MODULES = Collections.unmodifiableList(
            ALL.stream().filter(value -> value != Module.category.configs
                            && value != Module.category.official_config && value != Module.category.scripts
                            && value != Module.category.world && value != Module.category.bedwar_utils)
                    .collect(Collectors.toList()));

    private GuiCategories() {}

    public static List<Module.category> all() {
        return ALL;
    }

    public static List<Module.category> modules() {
        return MODULES;
    }

    public static String title(Module.category category) {
        return Arrays.stream(category.name().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static Module.category displayCategory(Module module) {
        if (module.moduleCategory() == Module.category.world) {
            if (module instanceof AntiBot) {
                return Module.category.combat;
            }
            return module instanceof Weather ? Module.category.render : Module.category.player;
        }
        return module.moduleCategory() == Module.category.bedwar_utils
                ? Module.category.player : module.moduleCategory();
    }

    public static List<Module> moduleEntries(Module.category category) {
        return Miau.moduleManager == null || !MODULES.contains(category) ? Collections.emptyList()
                : Miau.moduleManager.getModules().stream()
                        .filter(module -> displayCategory(module) == category).collect(Collectors.toList());
    }
}
