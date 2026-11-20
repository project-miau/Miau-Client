package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.utility.config.online.OnlineConfigEntry;
import idle.miau.utility.config.online.OnlineConfigService;
import idle.miau.utility.config.online.OnlineConfigSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OfficialConfig extends Command {
    private final OnlineConfigService service = new OnlineConfigService();
    private final List<OnlineConfigEntry> cachedEntries = new ArrayList<OnlineConfigEntry>();
    private boolean cacheLoaded;
    private boolean fetching;
    private int request;

    public OfficialConfig() {
        super("officialconfig", "Load official configurations from the Miau servers.", CommandCategory.CONFIG,
                new String[] {"officialconfig", "officialconfig list", "officialconfig load <name>"},
                "oc");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            handleList();
            return;
        }

        String subCommand = input.getArgument(0).toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "list":
            case "ls":
                handleList();
                return;
            case "load":
            case "l":
                handleLoad(input);
                return;
            default:
                syntaxError();
        }
    }

    @Override
    public List<String> suggest(CommandInput input) {
        if (input.argumentCount() == 1) {
            return filterSuggestions(input, "list", "ls", "load", "l");
        }
        if (isLoadCommand(input.getArgument(0)) && input.argumentCount() == 2) {
            return filterSuggestions(input, cachedNames());
        }
        return new ArrayList<String>();
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        if (isLoadCommand(input.getArgument(0))) {
            return 1;
        }
        return super.getSuggestionArgumentStart(input);
    }

    private void handleList() {
        if (fetching) {
            replyWithHeader("&7Already fetching official configs, please wait.");
            return;
        }
        if (cacheLoaded) {
            replyWithHeader("&7Cached list, use again after configs change to refresh.");
        }
        fetch(new Runnable() {
            @Override
            public void run() {
                printEntries();
            }
        });
    }

    private void handleLoad(CommandInput input) {
        if (input.argumentCount() < 2) {
            syntaxError();
            return;
        }
        final String loadName = input.joinArguments(1);
        if (fetching) {
            replyWithHeader("&7Still fetching official configs, please wait.");
            return;
        }
        fetch(new Runnable() {
            @Override
            public void run() {
                loadMatched(loadName);
            }
        });
    }

    private void fetch(final Runnable onFetched) {
        if (cacheLoaded) {
            onFetched.run();
            return;
        }
        fetching = true;
        final int currentRequest = ++request;
        replyWithHeader("&7Fetching official configs...");
        service.list(
                OnlineConfigSource.OFFICIAL,
                entries -> {
                    if (currentRequest != request) {
                        return;
                    }
                    cachedEntries.clear();
                    cachedEntries.addAll(entries);
                    cachedEntries.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
                    cacheLoaded = true;
                    fetching = false;
                    onFetched.run();
                },
                error -> {
                    if (currentRequest != request) {
                        return;
                    }
                    fetching = false;
                    replyWithHeader("&cFailed to fetch official configs: &7" + error);
                }
        );
    }

    private void printEntries() {
        if (cachedEntries.isEmpty()) {
            replyWithHeader("&7No official configs found.");
            return;
        }
        replyWithHeader("&b" + cachedEntries.size() + " &7official config"
                + (cachedEntries.size() == 1 ? "" : "s") + ":");
        for (OnlineConfigEntry entry : cachedEntries) {
            String detail = " &7" + entry.getName() + " &7by " + entry.getAuthor()
                    + " | " + entry.getDownloads() + " loads";
            if (!entry.getType().isEmpty()) {
                detail += " | " + entry.getType();
            }
            reply(detail);
        }
    }

    private void loadMatched(String loadName) {
        OnlineConfigEntry exact = null;
        List<OnlineConfigEntry> partial = new ArrayList<OnlineConfigEntry>();
        for (OnlineConfigEntry entry : cachedEntries) {
            if (entry.getName().equalsIgnoreCase(loadName)) {
                exact = entry;
                break;
            }
            if (entry.getName().toLowerCase(Locale.ROOT).startsWith(loadName.toLowerCase(Locale.ROOT))) {
                partial.add(entry);
            }
        }

        if (exact == null && partial.size() == 1) {
            exact = partial.get(0);
        }
        if (exact == null && partial.size() > 1) {
            replyWithHeader("&cMultiple official configs match &b" + loadName + "&c:");
            for (OnlineConfigEntry entry : partial) {
                reply(" &7" + entry.getName());
            }
            return;
        }
        if (exact == null) {
            replyWithHeader("&7Official config &b" + loadName + " &7does not exist.");
            return;
        }

        final OnlineConfigEntry target = exact;
        replyWithHeader("&7Loading official config: &b" + target.getName());
        service.load(
                OnlineConfigSource.OFFICIAL,
                target,
                loaded -> {
                    if (loaded) {
                        replyWithHeader("&7Loaded official config: &b" + target.getName());
                    } else {
                        replyWithHeader("&cFailed to apply official config: &b" + target.getName());
                    }
                },
                error -> replyWithHeader("&cFailed to load official config: &7" + error)
        );
    }

    private List<String> cachedNames() {
        List<String> names = new ArrayList<String>();
        for (OnlineConfigEntry entry : cachedEntries) {
            names.add(entry.getName());
        }
        return names;
    }

    private boolean isLoadCommand(String subCommand) {
        return "load".equalsIgnoreCase(subCommand) || "l".equalsIgnoreCase(subCommand);
    }
}
