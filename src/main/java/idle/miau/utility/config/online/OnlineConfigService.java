package idle.miau.utility.config.online;

import idle.miau.Miau;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class OnlineConfigService {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Miau-Online-Configs");
        thread.setDaemon(true);
        return thread;
    });

    private final Minecraft mc = Minecraft.getMinecraft();
    private final OnlineConfigClient client;

    public OnlineConfigService() {
        this(new OnlineConfigClient());
    }

    OnlineConfigService(OnlineConfigClient client) {
        this.client = client;
    }

    public void list(
            OnlineConfigSource source,
            Consumer<List<OnlineConfigEntry>> success,
            Consumer<String> failure
    ) {
        EXECUTOR.execute(() -> {
            try {
                List<OnlineConfigEntry> entries = client.list(source);
                mc.addScheduledTask(() -> success.accept(entries));
            } catch (Exception exception) {
                String error = message(exception);
                mc.addScheduledTask(() -> failure.accept(error));
            }
        });
    }

    public void load(
            OnlineConfigSource source,
            OnlineConfigEntry entry,
            Consumer<Boolean> success,
            Consumer<String> failure
    ) {
        EXECUTOR.execute(() -> {
            try {
                String configJson = client.load(source, entry.getId());
                mc.addScheduledTask(() -> success.accept(
                        Miau.configManager != null
                                && Miau.configManager.applyExternalConfig(configJson)
                ));
            } catch (Exception exception) {
                String error = message(exception);
                mc.addScheduledTask(() -> failure.accept(error));
            }
        });
    }

    static String message(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.trim().isEmpty()) {
            return "Unknown error";
        }
        value = value.replaceAll("\\s+", " ").trim();
        return value.length() > 80 ? value.substring(0, 80) + "..." : value;
    }
}
