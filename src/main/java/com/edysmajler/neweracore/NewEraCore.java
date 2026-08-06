package com.edysmajler.neweracore;

import com.crimsonwarpedcraft.cwcommons.config.bukkit.BukkitConfigManagerBuilder;
import com.crimsonwarpedcraft.cwcommons.store.DataStore;
import com.crimsonwarpedcraft.cwcommons.store.bukkit.AutoFlushTask;
import com.crimsonwarpedcraft.cwcommons.store.bukkit.BukkitDataStoreBuilder;
import com.edysmajler.neweracore.command.NewEraCoreCommand;
import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.WorldEngineFactory;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import java.io.File;
import java.io.IOException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Main entry point for NewEraCore.
 */
public class NewEraCore extends JavaPlugin {

  private PluginConfig config;
  private DataStore store;
  private BukkitTask autoFlushTask;

  @Override
  public void onLoad() {
    CommandAPI.onLoad(new CommandAPIPaperConfig(this));
  }

  @Override
  public void onEnable() {
    CommandAPI.onEnable();
    suggestPaper();
    saveDefaultConfig();

    // Load the configuration settings
    try {
      config = new BukkitConfigManagerBuilder()
          .build()
          .load(new File(getDataFolder(), "config.yml"), PluginConfig.class);
    } catch (IOException | IllegalStateException e) {
      getLogger().severe("Failed to load config: " + e.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    // Setup persistent storage
    try {
      store = new BukkitDataStoreBuilder(getName(), getDataFolder()).build();
    } catch (IOException e) {
      getLogger().severe("Failed to open data store: " + e.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    autoFlushTask = AutoFlushTask.builder(store, this).build().start();

    // Transform each chunk once, as it generates
    WorldEngine worldEngine = WorldEngineFactory.create(this, config.getWorldEngine());
    getServer().getPluginManager().registerEvents(worldEngine, this);

    // Set up in-game /neweracore command
    new NewEraCoreCommand(config, this, worldEngine).register();
  }

  @Override
  public void onDisable() {
    if (autoFlushTask != null) {
      autoFlushTask.cancel();
    }

    if (store != null) {
      try {
        store.close();
      } catch (Exception e) {
        getLogger().severe("Failed to close data store: " + e.getMessage());
      }
    }

    CommandAPI.onDisable();
  }

  /**
   * Returns the validated plugin configuration, or null before {@code onEnable} loads it.
   *
   * @return the loaded configuration
   */
  public PluginConfig getPluginConfig() {
    return config;
  }

  /**
   * Returns the persistent data store, or null before {@code onEnable} opens it.
   *
   * @return the open data store
   */
  public DataStore getStore() {
    return store;
  }

  private void suggestPaper() {
    if (isPaper()) {
      return;
    }

    getLogger().warning(getName() + " recommends using Paper.");
  }

  private boolean isPaper() {
    try {
      Class.forName("io.papermc.paper.ServerBuildInfo");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
