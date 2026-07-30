package com.rootrecord.minecraft.rootappreciation;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootAppreciationPlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;
    private AppreciationConfig config;
    private AppreciationStore store;
    private AppreciationTokenItem tokens;
    private AppreciationService service;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_APPRECIATION_CONFIG, "root-appreciation.yml");
        yaml.load();
        config = new AppreciationConfig(yaml.config());
        tokens = new AppreciationTokenItem(this);

        String prefix = "root_";
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(this, yaml.config());
        if (db != null && db.tablePrefix() != null && !db.tablePrefix().isBlank()) {
            prefix = db.tablePrefix();
        }
        store = new AppreciationStore(this, prefix);
        store.initSchema();
        service = new AppreciationService(this);

        ThanksCommand thanks = new ThanksCommand(this);
        bind("thanks", thanks, thanks);
        bind("bonus", new BonusCommand(this), null);
        getServer().getPluginManager().registerEvents(new AppreciationJoinListener(this), this);

        getLogger().info("Root-Appreciation enabled — Appreciation Tokens (/thanks /bonus).");
    }

    public void reloadAll() {
        yaml.load();
        config = new AppreciationConfig(yaml.config());
        getLogger().info("Root-Appreciation reloaded.");
    }

    private void bind(String name, org.bukkit.command.CommandExecutor exec, org.bukkit.command.TabCompleter tab) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(exec);
        if (tab != null) {
            cmd.setTabCompleter(tab);
        }
    }

    public RootRecordYamlConfig yaml() {
        return yaml;
    }

    public AppreciationConfig config() {
        return config;
    }

    public AppreciationStore store() {
        return store;
    }

    public AppreciationTokenItem tokens() {
        return tokens;
    }

    public AppreciationService service() {
        return service;
    }

    public String msg(String key) {
        String prefix = yaml.config().getString("messages.prefix", "");
        String body = yaml.config().getString("messages." + key, key);
        return colorize(prefix + body);
    }

    public String colorize(String input) {
        return input == null ? "" : input.replace('&', '\u00A7');
    }
}
