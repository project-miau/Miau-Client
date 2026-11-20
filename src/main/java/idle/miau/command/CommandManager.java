package idle.miau.command;

import idle.miau.Miau;
import idle.miau.command.impl.Binds;
import idle.miau.command.impl.Bind;
import idle.miau.command.impl.Cname;
import idle.miau.command.impl.Debug;
import idle.miau.command.impl.Enemy;
import idle.miau.command.impl.Friend;
import idle.miau.command.impl.Hide;
import idle.miau.command.impl.Script;
import idle.miau.command.impl.ScriptEditorCommand;
import idle.miau.command.impl.ModuleSettingsCommand;
import idle.miau.command.impl.Name;
import idle.miau.command.impl.Nick;
import idle.miau.command.impl.Ping;
import idle.miau.command.impl.Configs;
import idle.miau.command.impl.OfficialConfig;
import idle.miau.command.impl.ShowAll;
import idle.miau.command.impl.Toggle;
import idle.miau.command.impl.Track;
import idle.miau.command.impl.Unbind;
import idle.miau.module.ModuleManager;
import idle.miau.utility.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommandManager {
    public static final String PREFIX = ".";
    private static final int MAX_SUGGESTIONS = 100;

    private final List<Command> commands = new ArrayList<>();
    private final Map<String, Command> commandsByLabel = new LinkedHashMap<>();

    public final Track trackCommand;

    public CommandManager() {
        register(new idle.miau.command.impl.BaritoneCommand());
        register(new Ping());
        register(new Name());
        register(new Nick());
        register(new Toggle());
        register(new Bind());
        register(new Unbind());
        register(new Binds());
        register(new Cname());
        register(new Debug());
        register(new Friend());
        register(new Enemy());
        register(trackCommand = new Track());
        register(new Configs());
        register(new OfficialConfig());
        register(new ShowAll());
        register(new Hide());
        register(new Script());
        register(new ScriptEditorCommand());
        registerModuleSettingCommands();
    }

    private void registerModuleSettingCommands() {
        for (idle.miau.module.Module module : ModuleManager.modules) {
            String label = ModuleSettingsCommand.normalize(module == null ? null : module.getName());
            if (module != null && !module.getSettings().isEmpty() && getCommand(label) == null) {
                register(new ModuleSettingsCommand(module));
            }
        }
    }

    public boolean handleChatMessage(String message) {
        if (!isCommand(message)) {
            return false;
        }

        ParsedCommand parsed = parse(message, false);
        if (parsed == null) {
            return true;
        }

        if (parsed.command == null) {
            String label = parsed.input == null ? "" : parsed.input.getLabel();
            Utils.sendMessage(formatOutput("&cNo command " + label + " found."));
            return true;
        }

        try {
            parsed.command.execute(parsed.input);
        } catch (RuntimeException exception) {
            Utils.sendMessage(formatOutput("&cCommand failed safely. &7Check the debug log for details."));
            if (Miau.DEBUG) {
                exception.printStackTrace();
            }
        }
        return true;
    }

    public void refreshScriptCommands() {
        commands.removeIf(command -> command instanceof ModuleSettingsCommand
                && ((ModuleSettingsCommand) command).isScriptCommand());
        commandsByLabel.values().removeIf(command -> command instanceof ModuleSettingsCommand
                && ((ModuleSettingsCommand) command).isScriptCommand());
        registerModuleSettingCommands();
    }

    public String redactForHistory(String message) {
        ParsedCommand parsed = parse(message, false);
        if (parsed != null && parsed.command instanceof ModuleSettingsCommand
                && ((ModuleSettingsCommand) parsed.command).containsSensitiveValue(parsed.input)) {
            return PREFIX + parsed.input.getLabel() + " " + parsed.input.getArgument(0) + " <redacted>";
        }
        return message;
    }

    public String[] getAutoComplete(String message) {
        if (!isCommand(message)) {
            return new String[0];
        }

        ParsedCommand parsed = parse(message, true);
        if (parsed == null) {
            return prefixSuggestions("");
        }

        if (parsed.command == null) {
            return prefixSuggestions(parsed.input.getLabel());
        }

        String[] labelSuggestions = getCommandLabelSuggestions(message, parsed);
        if (labelSuggestions.length > 0) {
            return labelSuggestions;
        }

        List<String> suggestions;
        try {
            suggestions = parsed.command.suggest(parsed.input);
        } catch (RuntimeException exception) {
            if (Miau.DEBUG) exception.printStackTrace();
            return new String[0];
        }
        return buildAutoCompleteSuggestions(parsed, suggestions);
    }

    public String[] getPreviewSuggestions(String message) {
        if (!isCommand(message)) {
            return new String[0];
        }

        ParsedCommand parsed = parse(message, true);
        if (parsed == null) {
            return prefixSuggestions("");
        }

        if (parsed.command == null) {
            return prefixSuggestions(parsed.input.getLabel());
        }

        String[] labelSuggestions = getCommandLabelSuggestions(message, parsed);
        if (labelSuggestions.length > 0) {
            return labelSuggestions;
        }

        List<String> suggestions;
        try {
            suggestions = parsed.command.suggest(parsed.input);
        } catch (RuntimeException exception) {
            if (Miau.DEBUG) exception.printStackTrace();
            return new String[0];
        }
        if (suggestions == null || suggestions.isEmpty()) {
            return new String[0];
        }

        Set<String> preview = new LinkedHashSet<>();
        for (String suggestion : suggestions) {
            if (suggestion != null && !suggestion.isEmpty()) {
                preview.add(suggestion);
                if (preview.size() >= MAX_SUGGESTIONS) break;
            }
        }
        return preview.toArray(new String[0]);
    }

    private String[] getCommandLabelSuggestions(String message, ParsedCommand parsed) {
        if (parsed == null || parsed.input.argumentCount() != 0 || endsWithWhitespace(message)) {
            return new String[0];
        }

        String[] suggestions = prefixSuggestions(parsed.input.getLabel());
        for (String suggestion : suggestions) {
            if (!suggestion.equalsIgnoreCase(message)) {
                return suggestions;
            }
        }

        return new String[0];
    }

    public boolean isEnabled() {
        return true;
    }

    public boolean isCommand(String message) {
        return message != null
                && message.length() > PREFIX.length()
                && message.startsWith(PREFIX)
                && Character.isLetterOrDigit(message.charAt(PREFIX.length()));
    }

    public String getPrefix() {
        return PREFIX;
    }

    public String formatOutput(String message) {
        return message;
    }

    public List<Command> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    public Command getCommand(String label) {
        if (label == null) return null;
        return commandsByLabel.get(label.toLowerCase(Locale.ROOT));
    }

    private void register(Command command) {
        if (command == null) throw new IllegalArgumentException("Cannot register a null command");
        registerLabel(command.getName(), command);
        for (String alias : command.getAliases()) registerLabel(alias, command);
        commands.add(command);
    }

    private void registerLabel(String label, Command command) {
        String normalized = label.toLowerCase(Locale.ROOT);
        Command conflict = commandsByLabel.putIfAbsent(normalized, command);
        if (conflict != null && conflict != command) {
            throw new IllegalStateException("Duplicate command label '" + label + "' used by " + conflict.getName() + " and " + command.getName());
        }
    }

    private ParsedCommand parse(String message, boolean preserveTrailingArgument) {
        String body = stripPrefix(message);
        if (body == null) {
            return null;
        }

        String normalizedBody = trimLeadingWhitespace(body);
        if (normalizedBody.isEmpty()) {
            return null;
        }

        String[] tokens = tokenize(normalizedBody, preserveTrailingArgument && endsWithWhitespace(body));
        if (tokens.length == 0) {
            return null;
        }

        String label = tokens[0];
        String[] arguments = tokens.length > 1 ? Arrays.copyOfRange(tokens, 1, tokens.length) : new String[0];
        return new ParsedCommand(findCommand(label), new CommandInput(message, label, arguments));
    }

    private String[] prefixSuggestions(String query) {
        Set<String> suggestions = new LinkedHashSet<>();
        String loweredQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        Set<String> seen = new LinkedHashSet<>();

        for (Command command : commands) {
            if (command.getName().toLowerCase(Locale.ROOT).startsWith(loweredQuery)
                    && seen.add(command.getName().toLowerCase(Locale.ROOT))) {
                suggestions.add(getPrefix() + command.getName());
            }

            for (String alias : command.getAliases()) {
                if (alias.toLowerCase(Locale.ROOT).startsWith(loweredQuery)
                        && seen.add(alias.toLowerCase(Locale.ROOT))) {
                    suggestions.add(getPrefix() + alias);
                }
            }
            if (suggestions.size() >= MAX_SUGGESTIONS) break;
        }

        return suggestions.toArray(new String[0]);
    }

    private Command findCommand(String label) {
        return getCommand(label);
    }

    private String[] buildAutoCompleteSuggestions(ParsedCommand parsed, List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return new String[0];
        }

        Set<String> normalized = new LinkedHashSet<>();
        int replacementStart = Math.max(0, Math.min(parsed.input.argumentCount(), parsed.command.getSuggestionArgumentStart(parsed.input)));

        for (String suggestion : suggestions) {
            if (suggestion == null || suggestion.isEmpty()) {
                continue;
            }

            StringBuilder completed = new StringBuilder(getPrefix()).append(parsed.input.getLabel());
            for (int index = 0; index < replacementStart; index++) {
                completed.append(' ').append(parsed.input.getArgument(index));
            }

            if (completed.length() > 0) {
                completed.append(' ');
            }
            completed.append(suggestion);
            normalized.add(completed.toString());
            if (normalized.size() >= MAX_SUGGESTIONS) break;
        }

        return normalized.toArray(new String[0]);
    }

    private String stripPrefix(String message) {
        if (!isCommand(message)) {
            return null;
        }
        return message.substring(getPrefix().length());
    }

    private static String trimLeadingWhitespace(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return value.substring(index);
    }

    private static boolean endsWithWhitespace(String value) {
        return !value.isEmpty() && Character.isWhitespace(value.charAt(value.length() - 1));
    }

    private static String[] tokenize(String value, boolean preserveTrailingEmptyArgument) {
        List<String> tokens = new ArrayList<>();
        int length = value.length();
        int index = 0;

        while (index < length) {
            while (index < length && Character.isWhitespace(value.charAt(index))) {
                index++;
            }

            if (index >= length) {
                break;
            }

            int start = index;
            while (index < length && !Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            tokens.add(value.substring(start, index));
        }

        if (preserveTrailingEmptyArgument) {
            tokens.add("");
        }

        return tokens.toArray(new String[0]);
    }

    private static final class ParsedCommand {
        private final Command command;
        private final CommandInput input;

        private ParsedCommand(Command command, CommandInput input) {
            this.command = command;
            this.input = input;
        }
    }
}
