package idle.miau.command.impl;

import org.lwjgl.input.Keyboard;
import idle.miau.Miau;
import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ModuleSettingsCommand extends Command {
    private final Module module;

    public ModuleSettingsCommand(Module module) {
        super(normalize(module.getName()), "View or change " + module.getName() + " settings.", CommandCategory.MODULES,
                buildUsages(module));
        this.module = module;
    }

    public boolean isScriptCommand() {
        return module.moduleCategory() == Module.category.scripts;
    }

    private static String[] buildUsages(Module module) {
        String command = normalize(module.getName());
        List<String> usages = new ArrayList<>();
        usages.add(command);
        for (Setting setting : module.getSettings()) {
            if (!(setting instanceof GroupSetting)) usages.add(command + " " + normalize(setting.getName()) + " <value>");
        }
        return usages.toArray(new String[0]);
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            listSettings();
            return;
        }
        Setting setting = findSetting(input.getArgument(0));
        if (setting == null) {
            reply("&cUnknown setting. &7Use &b." + getName() + "&7 to list available settings.");
            return;
        }
        if (input.argumentCount() == 1) {
            reply("&b" + setting.getName() + "&7: " + displayValue(setting));
            return;
        }
        String value = input.joinArguments(1);
        String error = setValue(setting, value);
        if (error != null) {
            reply("&c" + error);
            return;
        }
        module.guiUpdate();
        module.onCommandSettingChanged(setting);
        Miau.markCurrentConfigUnsaved();
        reply("&aSet &b" + setting.getName() + " &ato " + displayValue(setting) + "&a.");
    }

    private void listSettings() {
        replyWithHeader("&7" + module.getName() + " settings");
        for (Setting setting : module.getSettings()) {
            if (setting instanceof GroupSetting) continue;
            reply(" &b" + normalize(setting.getName()) + " &7= " + displayValue(setting));
        }
        reply("&7Usage: &b." + getName() + " <setting> <value>");
    }

    private String setValue(Setting setting, String raw) {
        try {
            if (setting instanceof TextSetting) {
                TextSetting text = (TextSetting) setting;
                text.setText(raw);
                text.submit();
                return null;
            }
            if (setting instanceof ButtonSetting) {
                ButtonSetting button = (ButtonSetting) setting;
                if (button.isMethodButton) {
                    if (!raw.equalsIgnoreCase("run") && !raw.equalsIgnoreCase("press")) return "Use 'run' for this action setting.";
                    button.runMethod(); return null;
                }
                Boolean value = parseBoolean(raw);
                if (raw.equalsIgnoreCase("toggle")) value = !button.isToggled();
                if (value == null) return "Expected on/off, true/false, yes/no, or toggle.";
                button.setEnabled(value); module.guiButtonToggled(button); return null;
            }
            if (setting instanceof SliderSetting) {
                SliderSetting slider = (SliderSetting) setting;
                if (slider.isString) {
                    String[] options = slider.getOptions();
                    int index = findOption(options, raw);
                    if (index < 0) return "Expected one of: " + String.join(", ", options);
                    slider.setValueWithEvent(index);
                } else {
                    double number = Double.parseDouble(raw);
                    if (number < slider.getMin() || number > slider.getMax()) return "Expected a number from " + slider.getMin() + " to " + slider.getMax() + ".";
                    slider.setValueWithEvent(number);
                }
                module.onSlide(slider); return null;
            }
            if (setting instanceof MultiSelectSetting) {
                return setMultiSelect((MultiSelectSetting) setting, raw);
            }
            if (setting instanceof ColorSetting) return setColor((ColorSetting) setting, raw);
            if (setting instanceof KeySetting) {
                boolean unbind = "none".equalsIgnoreCase(raw) || "unbound".equalsIgnoreCase(raw);
                int key = unbind ? 0 : Keyboard.getKeyIndex(raw.toUpperCase(Locale.ROOT));
                if (key == 0 && !unbind) return "Unknown keyboard key.";
                ((KeySetting) setting).setKey(key); return null;
            }
            if (setting instanceof StringListSetting) {
                StringListSetting list = (StringListSetting) setting;
                String[] operation = raw.split("\\s+", 2);
                if (operation[0].equalsIgnoreCase("clear")) { list.clearEntries(); return null; }
                if (operation.length < 2) return "Use add <value>, remove <value>, or clear.";
                if (operation[0].equalsIgnoreCase("add")) { if (!list.addEntry(operation[1])) return "Value is empty or already exists."; return null; }
                if (operation[0].equalsIgnoreCase("remove")) { if (!list.removeEntry(operation[1])) return "Value was not found."; return null; }
                return "Use add <value>, remove <value>, or clear.";
            }
            return "This setting type cannot be changed from chat.";
        } catch (NumberFormatException ignored) {
            return "Invalid number.";
        }
    }

    private String setColor(ColorSetting color, String raw) {
        String value = raw.trim();
        try {
            if (value.startsWith("#")) {
                long parsed = Long.parseLong(value.substring(1), 16);
                if (value.length() == 7) color.setColor((int)(parsed >> 16), (int)(parsed >> 8), (int)parsed);
                else if (value.length() == 9) color.setColor((int)(parsed >> 24), (int)(parsed >> 16), (int)(parsed >> 8), (int)parsed);
                else return "Use #RRGGBB or #RRGGBBAA.";
                return null;
            }
            String[] parts = value.split("[,\\s]+");
            if (parts.length != 3 && parts.length != 4) return "Use R G B, R G B A, or #RRGGBB.";
            int[] channels = new int[parts.length];
            for (int i = 0; i < parts.length; i++) { channels[i] = Integer.parseInt(parts[i]); if (channels[i] < 0 || channels[i] > 255) return "Color channels must be from 0 to 255."; }
            if (channels.length == 4) color.setColor(channels[0], channels[1], channels[2], channels[3]); else color.setColor(channels[0], channels[1], channels[2]);
            return null;
        } catch (NumberFormatException ignored) { return "Invalid color value."; }
    }

    private Setting findSetting(String name) {
        String wanted = normalize(name);
        for (Setting setting : module.getSettings()) {
            if (!(setting instanceof GroupSetting) && (normalize(setting.getName()).equals(wanted) || normalize(setting.getConfigKey()).equals(wanted))) return setting;
        }
        return null;
    }

    @Override
    public List<String> suggest(CommandInput input) {
        if (input.argumentCount() <= 1) {
            List<String> names = new ArrayList<>();
            for (Setting setting : module.getSettings()) if (!(setting instanceof GroupSetting)) names.add(normalize(setting.getName()));
            return filterSuggestions(input, names);
        }
        Setting setting = findSetting(input.getArgument(0));
        if (setting instanceof ButtonSetting) return filterSuggestions(input, ((ButtonSetting) setting).isMethodButton ? new String[] {"run"} : new String[] {"on", "off", "toggle"});
        if (setting instanceof SliderSetting && ((SliderSetting) setting).isString) return filterSuggestions(input, Arrays.asList(((SliderSetting) setting).getOptions()));
        if (setting instanceof MultiSelectSetting) {
            List<String> values = new ArrayList<String>(Arrays.asList(((MultiSelectSetting) setting).getOptions()));
            values.add("all");
            values.add("clear");
            return filterSuggestions(input, values);
        }
        if (setting instanceof StringListSetting) return filterSuggestions(input, "add", "remove", "clear");
        return new ArrayList<>();
    }

    @Override public int getSuggestionArgumentStart(CommandInput input) { return Math.max(0, input.argumentCount() - 1); }

    public boolean containsSensitiveValue(CommandInput input) {
        return input.argumentCount() > 1 && findSetting(input.getArgument(0)) instanceof TextSetting && ((TextSetting) findSetting(input.getArgument(0))).isSensitive();
    }

    private String displayValue(Setting setting) {
        if (setting instanceof TextSetting) return ((TextSetting) setting).isSensitive() ? (((TextSetting) setting).getText().isEmpty() ? "&8<not set>" : "&8<redacted>") : "&f" + ((TextSetting) setting).getText();
        if (setting instanceof ButtonSetting) return ((ButtonSetting) setting).isMethodButton ? "&d<action>" : (((ButtonSetting) setting).isToggled() ? "&aOn" : "&cOff");
        if (setting instanceof SliderSetting) {
            SliderSetting slider = (SliderSetting) setting;
            if (slider.isString) {
                return "&f" + slider.getOptions()[(int) slider.getInput()];
            }
            if (slider.getMinString() != null && slider.getInput() == slider.getMin()) {
                return "&f" + slider.getMinString();
            }
            return "&f" + slider.getInput() + slider.getSuffix();
        }
        if (setting instanceof MultiSelectSetting) {
            List<String> selected = ((MultiSelectSetting) setting).getSelected();
            return selected.isEmpty() ? "&8None" : "&f" + String.join(", ", selected);
        }
        if (setting instanceof ColorSetting) return String.format("&f#%06X", ((ColorSetting) setting).getRGB());
        if (setting instanceof KeySetting) {
            int key = ((KeySetting) setting).getKey();
            return "&f" + (key == 0 ? "NONE" : Keyboard.getKeyName(key));
        }
        if (setting instanceof StringListSetting) return "&f" + ((StringListSetting) setting).getEntries().size() + " entries";
        return "&8<unsupported>";
    }
    private Boolean parseBoolean(String raw) { if (raw.equalsIgnoreCase("toggle")) return null; if (raw.matches("(?i)on|true|yes|1|enable|enabled")) return true; if (raw.matches("(?i)off|false|no|0|disable|disabled")) return false; return null; }
    private String setMultiSelect(MultiSelectSetting setting, String raw) {
        String value = raw.trim();
        if (value.equalsIgnoreCase("clear") || value.equalsIgnoreCase("none")) {
            setting.clear();
            return null;
        }
        if (value.equalsIgnoreCase("all")) {
            setting.selectAll();
            return null;
        }
        String[] operation = value.split("\\s+", 2);
        if (operation.length == 2) {
            String option = findCanonicalOption(setting.getOptions(), operation[1]);
            if (option == null) return "Expected one of: " + String.join(", ", setting.getOptions());
            if (operation[0].matches("(?i)add|on|enable|enabled")) {
                setting.setSelected(option, true);
                return null;
            }
            if (operation[0].matches("(?i)remove|off|disable|disabled")) {
                setting.setSelected(option, false);
                return null;
            }
            if (operation[0].equalsIgnoreCase("toggle")) {
                setting.toggle(option);
                return null;
            }
        }
        String[] requested = value.split(",");
        List<String> resolved = new ArrayList<String>();
        for (String entry : requested) {
            String option = findCanonicalOption(setting.getOptions(), entry);
            if (option == null) return "Expected one or more of: " + String.join(", ", setting.getOptions());
            resolved.add(option);
        }
        setting.clear();
        for (String option : resolved) setting.setSelected(option, true);
        return null;
    }
    private int findOption(String[] options, String raw) { String wanted=normalize(raw); for(int i=0;i<options.length;i++) if(normalize(options[i]).equals(wanted)) return i; return -1; }
    private String findCanonicalOption(String[] options, String raw) { int index = findOption(options, raw); return index < 0 ? null : options[index]; }
    public static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
}
