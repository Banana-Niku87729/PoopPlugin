package com.example.poopplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class ConfigManager {

    private final PoopPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    public ConfigManager(PoopPlugin plugin) {
        this.plugin = plugin;
        setupConfig();
    }

    /**
     * 設定ファイルの初期化
     */
    private void setupConfig() {
        configFile = new File(plugin.getDataFolder(), "playerdata.yml");

        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("playerdata.ymlの作成に失敗しました: " + e.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * 設定を保存
     */
    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("設定の保存に失敗しました: " + e.getMessage());
        }
    }

    /**
     * プレイヤーのviewplatform設定を取得
     */
    public boolean isViewPlatformEnabled(Player player) {
        UUID uuid = player.getUniqueId();
        return config.getBoolean("players." + uuid + ".viewplatform", true);
    }

    /**
     * プレイヤーのviewplatform設定を変更
     */
    public void setViewPlatform(Player player, boolean enabled) {
        UUID uuid = player.getUniqueId();
        config.set("players." + uuid + ".viewplatform", enabled);
        saveConfig();
    }

    /**
     * プレイヤーのdenyplatformicon設定を取得
     */
    public boolean isDenyPlatformIcon(Player player) {
        UUID uuid = player.getUniqueId();
        return config.getBoolean("players." + uuid + ".denyplatformicon", false);
    }

    /**
     * プレイヤーのdenyplatformicon設定を変更
     */
    public void setDenyPlatformIcon(Player player, boolean enabled) {
        UUID uuid = player.getUniqueId();
        config.set("players." + uuid + ".denyplatformicon", enabled);
        saveConfig();
    }

    /**
     * プレイヤーのWatermarkモードを取得
     */
    public String getWatermarkMode(Player player) {
        UUID uuid = player.getUniqueId();
        return config.getString("players." + uuid + ".watermark_mode", "default");
    }

    /**
     * プレイヤーのWatermarkモードを設定
     */
    public void setWatermarkMode(Player player, String mode) {
        UUID uuid = player.getUniqueId();
        config.set("players." + uuid + ".watermark_mode", mode);
        saveConfig();
    }

    /**
     * プレイヤーのカスタムWatermarkを取得
     */
    public String getCustomWatermark(Player player) {
        UUID uuid = player.getUniqueId();
        return config.getString("players." + uuid + ".custom_watermark", "");
    }

    /**
     * プレイヤーのカスタムWatermarkを設定
     */
    public void setCustomWatermark(Player player, String text) {
        UUID uuid = player.getUniqueId();
        config.set("players." + uuid + ".custom_watermark", text);
        saveConfig();
    }

    /**
     * 設定をリロード
     */
    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }
}