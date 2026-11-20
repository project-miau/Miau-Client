package idle.miau.command;

import idle.miau.Miau;
import idle.miau.utility.IMinecraftInstance;
import idle.miau.utility.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class Command implements IMinecraftInstance {
    private final String name;
    private final String[] aliases;
    private final String description;
    private final CommandCategory category;
    private final String[] usages;

    protected Command(String name, String... aliases) {
        this(name, "No description available.", CommandCategory.GENERAL, new String[] {name}, aliases);
    }

    protected Command(String name, String description, CommandCategory category, String[] usages, String... aliases) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Command name cannot be empty");
        this.name = name.trim().toLowerCase(Locale.ROOT);
        this.description = description == null || description.trim().isEmpty() ? "No description available." : description.trim();
        this.category = category == null ? CommandCategory.GENERAL : category;
        this.usages = sanitizeUsages(usages);
        Set<String> unique = new LinkedHashSet<>();
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null) {
                    String clean = alias.trim().toLowerCase(Locale.ROOT);
                    if (!clean.isEmpty() && !clean.equals(this.name)) unique.add(clean);
                }
            }
        }
        this.aliases = unique.toArray(new String[0]);
    }

    private String[] sanitizeUsages(String[] values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) for (String value : values) if (value != null && !value.trim().isEmpty()) result.add(value.trim());
        if (result.isEmpty()) result.add(name);
        return result.toArray(new String[0]);
    }

    public final String getName() { return name; }
    public final String[] getAliases() { return aliases.clone(); }
    public final String getDescription() { return description; }
    public final CommandCategory getCategory() { return category; }
    public final String[] getUsages() { return usages.clone(); }

    public final boolean matches(String label) {
        if (label == null) return false;
        if (name.equalsIgnoreCase(label)) return true;
        for (String alias : aliases) if (alias.equalsIgnoreCase(label)) return true;
        return false;
    }

    public abstract void execute(CommandInput input);
    public List<String> suggest(CommandInput input) { return Collections.emptyList(); }
    public int getSuggestionArgumentStart(CommandInput input) { return Math.max(0, input.argumentCount() - 1); }
    protected final void replyWithHeader(String message) { replyRaw(message); }
    protected final void reply(String message) { replyRaw(message); }
    protected final void syntaxError() {
        replyRaw("&cInvalid syntax. &7Use &b" + CommandManager.PREFIX + "help " + name + "&7.");
    }
    protected final String prefixed(String commandName) { return CommandManager.PREFIX + commandName; }

    protected final List<String> filterSuggestions(CommandInput input, List<String> options) {
        String query = input.argumentCount() == 0 ? "" : input.getArgument(input.argumentCount() - 1);
        String lowered = query == null ? "" : query.toLowerCase(Locale.ROOT);
        Set<String> matches = new LinkedHashSet<>();
        if (options != null) for (String option : options) if (option != null && option.toLowerCase(Locale.ROOT).startsWith(lowered)) matches.add(option);
        return new ArrayList<>(matches);
    }
    protected final List<String> filterSuggestions(CommandInput input, String... options) {
        List<String> values = new ArrayList<>();
        if (options != null) Collections.addAll(values, options);
        return filterSuggestions(input, values);
    }
    private void replyRaw(String message) { Utils.sendMessage(formatOutput(message)); }
    private String formatOutput(String message) { return Miau.commandManager != null ? Miau.commandManager.formatOutput(message) : message; }
}
