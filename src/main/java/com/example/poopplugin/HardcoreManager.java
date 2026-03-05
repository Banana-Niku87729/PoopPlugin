package com.example.poopplugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class HardcoreManager {

    private final PoopPlugin plugin;
    private File configFile;
    private FileConfiguration config;
    private ProtocolManager protocolManager;

    private Set<String> hardcoreWorlds;
    private String hubWorldName;
    private Map<UUID, Set<String>> playerDeaths;
    private Set<UUID> hardcorePlayers;

    // NMSリフレクション用
    private Class<?> packetGameStateChangeClass;
    private Class<?> gameStateClass;
    private Constructor<?> packetConstructor;
    private Object[] gameStateEnums;

    public HardcoreManager(PoopPlugin plugin) {
        this.plugin = plugin;
        this.hardcoreWorlds = new HashSet<>();
        this.playerDeaths = new HashMap<>();
        this.hardcorePlayers = new HashSet<>();

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
            initializeNMSReflection();
            plugin.getLogger().info("ProtocolLibが検出されました。ハードコアハート表示が有効です。");
        } else {
            plugin.getLogger().warning("ProtocolLibが見つかりません。ハードコアハート表示は無効です。");
        }

        loadConfig();
    }

    /**
     * NMSリフレクションの初期化
     */
    private void initializeNMSReflection() {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            plugin.getLogger().info("検出されたサーバーバージョン: " + version);

            // 1.21+ では net.minecraft パッケージ直接
            try {
                // PacketPlayOutGameStateChange を探す
                packetGameStateChangeClass = Class.forName("net.minecraft.network.protocol.game.PacketPlayOutGameStateChange");
                plugin.getLogger().info("PacketPlayOutGameStateChangeクラスを発見");
            } catch (ClassNotFoundException e) {
                // ClientboundGameEventPacket も試す
                packetGameStateChangeClass = Class.forName("net.minecraft.network.protocol.game.ClientboundGameEventPacket");
                plugin.getLogger().info("ClientboundGameEventPacketクラスを発見");
            }

            // GameStateの内部クラスを探す
            for (Class<?> innerClass : packetGameStateChangeClass.getDeclaredClasses()) {
                if (innerClass.isEnum() || innerClass.getSimpleName().contains("Type") || innerClass.getSimpleName().contains("State")) {
                    gameStateClass = innerClass;
                    gameStateEnums = gameStateClass.getEnumConstants();
                    plugin.getLogger().info("GameStateクラスを発見: " + gameStateClass.getSimpleName() + " (定数数: " + gameStateEnums.length + ")");
                    break;
                }
            }

            // コンストラクタを探す
            for (Constructor<?> constructor : packetGameStateChangeClass.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 2 && params[1] == float.class) {
                    packetConstructor = constructor;
                    plugin.getLogger().info("適切なコンストラクタを発見");
                    break;
                }
            }

            if (packetConstructor == null) {
                plugin.getLogger().warning("適切なコンストラクタが見つかりませんでした");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("NMSリフレクション初期化エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "hardcore.yml");

        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.saveResource("hardcore.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        hardcoreWorlds = new HashSet<>(config.getStringList("hardcore-worlds"));
        hubWorldName = config.getString("hub-world", "world");

        if (config.contains("player-deaths")) {
            for (String uuidStr : config.getConfigurationSection("player-deaths").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Set<String> deaths = new HashSet<>(config.getStringList("player-deaths." + uuidStr));
                    playerDeaths.put(uuid, deaths);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無効なUUIDをスキップしました: " + uuidStr);
                }
            }
        }

        plugin.getLogger().info("ハードコア設定を読み込みました。登録ワールド数: " + hardcoreWorlds.size());
    }

    public void saveConfig() {
        config.set("hardcore-worlds", new ArrayList<>(hardcoreWorlds));
        config.set("hub-world", hubWorldName);

        for (Map.Entry<UUID, Set<String>> entry : playerDeaths.entrySet()) {
            config.set("player-deaths." + entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("ハードコア設定の保存に失敗しました: " + e.getMessage());
        }
    }

    public boolean addHardcoreWorld(String worldName) {
        if (hardcoreWorlds.add(worldName)) {
            saveConfig();
            return true;
        }
        return false;
    }

    public boolean removeHardcoreWorld(String worldName) {
        if (hardcoreWorlds.remove(worldName)) {
            saveConfig();
            return true;
        }
        return false;
    }

    public boolean isHardcoreWorld(String worldName) {
        return hardcoreWorlds.contains(worldName);
    }

    public void setHubWorld(String worldName) {
        this.hubWorldName = worldName;
        saveConfig();
    }

    public String getHubWorldName() {
        return hubWorldName;
    }

    public Set<String> getHardcoreWorlds() {
        return new HashSet<>(hardcoreWorlds);
    }

    public boolean hasPlayerDiedInWorld(UUID playerId, String worldName) {
        Set<String> deaths = playerDeaths.get(playerId);
        return deaths != null && deaths.contains(worldName);
    }

    public void recordPlayerDeath(UUID playerId, String worldName) {
        playerDeaths.computeIfAbsent(playerId, k -> new HashSet<>()).add(worldName);
        saveConfig();
    }

    public void resetPlayerDeathInWorld(UUID playerId, String worldName) {
        Set<String> deaths = playerDeaths.get(playerId);
        if (deaths != null) {
            deaths.remove(worldName);
            if (deaths.isEmpty()) {
                playerDeaths.remove(playerId);
            }
            saveConfig();
        }
    }

    public void resetAllPlayerDeaths(UUID playerId) {
        playerDeaths.remove(playerId);
        saveConfig();
    }

    public boolean teleportToHub(Player player) {
        World hubWorld = Bukkit.getWorld(hubWorldName);
        if (hubWorld == null) {
            plugin.getLogger().warning("Hubワールド '" + hubWorldName + "' が見つかりません!");
            return false;
        }

        Location spawnLocation = hubWorld.getSpawnLocation();
        player.teleport(spawnLocation);
        return true;
    }

    public void updateHardcoreHearts(Player player) {
        if (protocolManager == null) {
            return;
        }

        String worldName = player.getWorld().getName();
        boolean shouldBeHardcore = isHardcoreWorld(worldName) &&
                !hasPlayerDiedInWorld(player.getUniqueId(), worldName);

        if (shouldBeHardcore) {
            sendHardcorePacket(player, true);
            hardcorePlayers.add(player.getUniqueId());
            plugin.getLogger().info(player.getName() + " にハードコアハートを送信しました");
        } else {
            sendHardcorePacket(player, false);
            hardcorePlayers.remove(player.getUniqueId());
            plugin.getLogger().info(player.getName() + " に通常ハートを送信しました");
        }
    }

    /**
     * ハードコアハートの状態を送信
     * 完全NMS実装版
     */
    private void sendHardcorePacket(Player player, boolean enable) {
        if (protocolManager == null) return;

        try {
            // NMSで直接パケットを作成
            if (packetConstructor != null && gameStateEnums != null && gameStateEnums.length > 11) {
                // Reason 11: ENABLE_RESPAWN_SCREEN (ハードコアハートの切り替え)
                Object gameStateReason = gameStateEnums[11];
                float value = enable ? 1.0F : 0.0F;

                Object nmsPacket = packetConstructor.newInstance(gameStateReason, value);

                // ProtocolLibでラップして送信
                PacketContainer container = PacketContainer.fromPacket(nmsPacket);
                protocolManager.sendServerPacket(player, container);

                plugin.getLogger().info("NMSパケット送信成功: " + player.getName() + " -> " + (enable ? "ハードコア" : "通常"));
                return;
            }

            // フォールバック: ProtocolLibの標準方法を試す
            fallbackProtocolLibMethod(player, enable);

        } catch (Exception e) {
            plugin.getLogger().warning("ハードコアパケット送信エラー: " + e.getMessage());
            e.printStackTrace();

            // 最終フォールバック
            try {
                fallbackProtocolLibMethod(player, enable);
            } catch (Exception ex) {
                plugin.getLogger().severe("全てのパケット送信方法が失敗しました");
            }
        }
    }

    /**
     * ProtocolLibの標準メソッドを使用したフォールバック
     */
    private void fallbackProtocolLibMethod(Player player, boolean enable) throws Exception {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.GAME_STATE_CHANGE);

        // 様々な方法を試す
        try {
            // 方法1: GameStateIDsを使用
            var gameStateIds = packet.getGameStateIDs();
            if (gameStateIds.size() > 0) {
                gameStateIds.write(0, 11);
                packet.getFloat().write(0, enable ? 1.0F : 0.0F);
                protocolManager.sendServerPacket(player, packet);
                plugin.getLogger().info("フォールバック方法1成功");
                return;
            }
        } catch (Exception ignored) {}

        try {
            // 方法2: 直接Modifierを使用
            var modifier = packet.getModifier();
            if (modifier.size() >= 2) {
                modifier.write(0, gameStateEnums != null && gameStateEnums.length > 11 ? gameStateEnums[11] : 11);
                modifier.write(1, enable ? 1.0F : 0.0F);
                protocolManager.sendServerPacket(player, packet);
                plugin.getLogger().info("フォールバック方法2成功");
                return;
            }
        } catch (Exception ignored) {}

        throw new Exception("全てのフォールバック方法が失敗");
    }

    public Set<String> getPlayerDeaths(UUID playerId) {
        return playerDeaths.getOrDefault(playerId, new HashSet<>());
    }

    public boolean canEnterHardcoreWorld(Player player, String worldName) {
        if (!isHardcoreWorld(worldName)) {
            return true;
        }
        return !hasPlayerDiedInWorld(player.getUniqueId(), worldName);
    }

    public boolean isPlayerInHardcoreMode(UUID playerId) {
        return hardcorePlayers.contains(playerId);
    }

    public boolean isProtocolLibAvailable() {
        return protocolManager != null;
    }
}