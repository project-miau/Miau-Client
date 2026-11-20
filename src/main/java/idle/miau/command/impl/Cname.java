package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.other.NameHider;

import java.util.Collections;
import java.util.List;

public class Cname extends Command {
    public Cname() {
        super("namehider", "Set the replacement name used by Name Hider.", CommandCategory.MODULES, new String[] {"namehider <name>"});
    }

    @Override
    public void execute(CommandInput input) {
        NameHider module = getNameHiderModule();
        if (module != null && (input.argumentCount() == 0 || isSettingInput(module, input))) {
            new ModuleSettingsCommand(module).execute(input);
            return;
        }

        if (input.argumentCount() == 0) {
            syntaxError();
            return;
        }

        NameHider.setFakeName(input.joinArguments(0));
        replyWithHeader("&7Name has been set to &b" + NameHider.fakeName + "&7.");
    }

    @Override
    public List<String> suggest(CommandInput input) {
        NameHider module = getNameHiderModule();
        if (module != null && (input.argumentCount() <= 1 || isSettingInput(module, input))) {
            return new ModuleSettingsCommand(module).suggest(input);
        }
        return Collections.emptyList();
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        return input.argumentCount() <= 1 ? 0 : 1;
    }

    private NameHider getNameHiderModule() {
        if (ModuleManager.nameHider != null) {
            return ModuleManager.nameHider;
        }
        for (Module module : ModuleManager.modules) {
            if (module instanceof NameHider) {
                return (NameHider) module;
            }
        }
        return null;
    }

    private boolean isSettingInput(NameHider module, CommandInput input) {
        if (input.argumentCount() == 0) {
            return true;
        }
        String wanted = ModuleSettingsCommand.normalize(input.getArgument(0));
        for (idle.miau.module.setting.Setting setting : module.getSettings()) {
            if (!ModuleSettingsCommand.normalize(setting.getName()).equals(wanted)
                    && !ModuleSettingsCommand.normalize(setting.getConfigKey()).equals(wanted)) {
                continue;
            }
            return true;
        }
        return false;
    }
}
