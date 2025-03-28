package cn.whohh.firstjointp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

public final class FirstJoinTeleport extends JavaPlugin implements Listener {

    private Location spawnLocation;
    private PlayerDataManager dataManager;
    private boolean teleportEnabled;

    @Override
    public void onEnable() {
        // 加载配置
        saveDefaultConfig();
        loadConfiguration();

        // 初始化数据管理器
        this.dataManager = new PlayerDataManager(this);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        // 注册命令
        this.getCommand("fjt-reload").setExecutor((sender, command, label, args) -> {
            reloadConfig();
            loadConfiguration();
            sender.sendMessage("配置已重载");
            return true;
        });
    }

    private void loadConfiguration() {
        FileConfiguration config = getConfig();

        // 读取传送坐标
        this.spawnLocation = new Location(
                Bukkit.getWorld(config.getString("spawn.world", "world")),
                config.getDouble("spawn.x", 0),
                config.getDouble("spawn.y", 64),
                config.getDouble("spawn.z", 0),
                (float) config.getDouble("spawn.yaw", 0),
                (float) config.getDouble("spawn.pitch", 0));

        this.teleportEnabled = config.getBoolean("teleport-enabled", true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!teleportEnabled)
            return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 使用新版Folia调度API
        player.getScheduler().run(this, task -> {
            if (!dataManager.hasPlayerJoinedBefore(playerId)) {
                player.teleportAsync(spawnLocation).thenAccept(success -> {
                    if (success) {
                        getLogger().info(player.getName() + " 已被传送到出生点");
                        dataManager.savePlayerData(playerId);
                    }
                });
            }
        }, null); // 新增null参数和0L延迟
    }

    @Override
    public void onDisable() {
        // 关闭时保存数据
        try {
            dataManager.saveDataFile();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "保存数据文件时出错", e);
        }
        HandlerList.unregisterAll((JavaPlugin) this);
    }

    // 数据管理类
    private static class PlayerDataManager {
        private final FirstJoinTeleport plugin;
        private final FileConfiguration dataConfig;

        public PlayerDataManager(FirstJoinTeleport plugin) {
            this.plugin = plugin;
            this.dataConfig = plugin.getConfig();
        }

        public boolean hasPlayerJoinedBefore(UUID playerId) {
            return dataConfig.contains("players." + playerId.toString());
        }

        public void savePlayerData(UUID playerId) {
            dataConfig.set("players." + playerId.toString(), true);
            try {
                saveDataFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "保存玩家数据失败", e);
            }
        }

        public void saveDataFile() throws IOException {
            plugin.saveConfig();
        }
    }
}