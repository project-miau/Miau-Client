package idle.miau.utility.config;

public class Config {
    private final ConfigModule module;
    private final int bind;
    private String configName;

    public Config(String configName, int bind) {
        this.configName = configName;
        this.bind = bind;
        this.module = new ConfigModule(this, configName, bind);
        this.module.ignoreOnSave = true;
    }

    public ConfigModule getModule() {
        return module;
    }

    public int getBind() {
        return bind;
    }

    public String getName() {
        return configName;
    }

    public void setName(String configName) {
        this.configName = configName;
        this.module.setConfigName(configName);
    }
}
