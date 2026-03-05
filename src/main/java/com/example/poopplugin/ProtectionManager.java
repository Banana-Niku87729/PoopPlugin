package com.example.poopplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 保護エリアを管理するクラス
 */
public class ProtectionManager {

    private final PoopPlugin plugin;
    private final Map<String, ProtectedRegion> regions;
    private File regionsFile;
    private FileConfiguration regionsConfig;

    public ProtectionManager(PoopPlugin plugin) {
        this.plugin = plugin;
        this.regions = new HashMap<>();
        loadRegions();
    }

    /**
     * 保護エリアのデータを読み込み
     */
    private void loadRegions() {
        regionsFile = new File(plugin.getDataFolder(), "regions.yml");

        if (!regionsFile.exists()) {
            try {
                regionsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("regions.ymlの作成に失敗しました: " + e.getMessage());
            }
        }

        regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);

        // データの読み込み
        if (regionsConfig.contains("regions")) {
            for (String regionName : regionsConfig.getConfigurationSection("regions").getKeys(false)) {
                String path = "regions." + regionName;

                String worldName = regionsConfig.getString(path + ".world");
                double x1 = regionsConfig.getDouble(path + ".pos1.x");
                double y1 = regionsConfig.getDouble(path + ".pos1.y");
                double z1 = regionsConfig.getDouble(path + ".pos1.z");
                double x2 = regionsConfig.getDouble(path + ".pos2.x");
                double y2 = regionsConfig.getDouble(path + ".pos2.y");
                double z2 = regionsConfig.getDouble(path + ".pos2.z");

                UUID ownerUUID = UUID.fromString(regionsConfig.getString(path + ".owner"));
                List<String> memberStrings = regionsConfig.getStringList(path + ".members");
                Set<UUID> members = new HashSet<>();
                for (String memberStr : memberStrings) {
                    members.add(UUID.fromString(memberStr));
                }

                int priority = regionsConfig.getInt(path + ".priority", 0);

                Map<String, Boolean> flags = new HashMap<>();
                if (regionsConfig.contains(path + ".flags")) {
                    for (String flag : regionsConfig.getConfigurationSection(path + ".flags").getKeys(false)) {
                        flags.put(flag, regionsConfig.getBoolean(path + ".flags." + flag));
                    }
                }

                Location pos1 = new Location(Bukkit.getWorld(worldName), x1, y1, z1);
                Location pos2 = new Location(Bukkit.getWorld(worldName), x2, y2, z2);

                ProtectedRegion region = new ProtectedRegion(regionName, pos1, pos2, ownerUUID);
                region.setMembers(members);
                region.setPriority(priority);
                region.setFlags(flags);

                regions.put(regionName.toLowerCase(), region);
            }
        }

        plugin.getLogger().info(regions.size() + "個の保護エリアを読み込みました");
    }

    /**
     * 保護エリアのデータを保存
     */
    public void saveRegions() {
        regionsConfig.set("regions", null);

        for (Map.Entry<String, ProtectedRegion> entry : regions.entrySet()) {
            String regionName = entry.getKey();
            ProtectedRegion region = entry.getValue();
            String path = "regions." + regionName;

            regionsConfig.set(path + ".world", region.getPos1().getWorld().getName());
            regionsConfig.set(path + ".pos1.x", region.getPos1().getX());
            regionsConfig.set(path + ".pos1.y", region.getPos1().getY());
            regionsConfig.set(path + ".pos1.z", region.getPos1().getZ());
            regionsConfig.set(path + ".pos2.x", region.getPos2().getX());
            regionsConfig.set(path + ".pos2.y", region.getPos2().getY());
            regionsConfig.set(path + ".pos2.z", region.getPos2().getZ());

            regionsConfig.set(path + ".owner", region.getOwner().toString());

            List<String> memberStrings = new ArrayList<>();
            for (UUID member : region.getMembers()) {
                memberStrings.add(member.toString());
            }
            regionsConfig.set(path + ".members", memberStrings);

            regionsConfig.set(path + ".priority", region.getPriority());

            for (Map.Entry<String, Boolean> flag : region.getFlags().entrySet()) {
                regionsConfig.set(path + ".flags." + flag.getKey(), flag.getValue());
            }
        }

        try {
            regionsConfig.save(regionsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("regions.ymlの保存に失敗しました: " + e.getMessage());
        }
    }

    /**
     * 保護エリアを作成
     */
    public boolean createRegion(String name, Location pos1, Location pos2, Player owner) {
        if (regions.containsKey(name.toLowerCase())) {
            return false;
        }

        ProtectedRegion region = new ProtectedRegion(name, pos1, pos2, owner.getUniqueId());
        regions.put(name.toLowerCase(), region);
        saveRegions();
        return true;
    }

    /**
     * 保護エリアを削除
     */
    public boolean deleteRegion(String name) {
        if (regions.remove(name.toLowerCase()) != null) {
            saveRegions();
            return true;
        }
        return false;
    }

    /**
     * 保護エリアを取得
     */
    public ProtectedRegion getRegion(String name) {
        return regions.get(name.toLowerCase());
    }

    /**
     * すべての保護エリアを取得
     */
    public Collection<ProtectedRegion> getAllRegions() {
        return regions.values();
    }

    /**
     * 指定位置の保護エリアを取得（優先度順）
     */
    public List<ProtectedRegion> getRegionsAt(Location location) {
        List<ProtectedRegion> result = new ArrayList<>();

        for (ProtectedRegion region : regions.values()) {
            if (region.contains(location)) {
                result.add(region);
            }
        }

        // 優先度でソート（高い方が優先）
        result.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));

        return result;
    }

    /**
     * プレイヤーが指定位置で建築できるかチェック
     */
    public boolean canBuild(Player player, Location location) {
        if (player.hasPermission("poopplugin.protect.bypass")) {
            return true;
        }

        List<ProtectedRegion> regionsAt = getRegionsAt(location);

        if (regionsAt.isEmpty()) {
            return true;
        }

        // 最も優先度が高い保護エリアでチェック
        ProtectedRegion topRegion = regionsAt.get(0);
        return topRegion.canBuild(player);
    }

    /**
     * プレイヤーが指定位置で相互作用できるかチェック
     */
    public boolean canInteract(Player player, Location location) {
        if (player.hasPermission("poopplugin.protect.bypass")) {
            return true;
        }

        List<ProtectedRegion> regionsAt = getRegionsAt(location);

        if (regionsAt.isEmpty()) {
            return true;
        }

        ProtectedRegion topRegion = regionsAt.get(0);
        return topRegion.canInteract(player);
    }

    /**
     * プレイヤーが所有する保護エリアを取得
     */
    public List<ProtectedRegion> getPlayerRegions(UUID playerUUID) {
        List<ProtectedRegion> result = new ArrayList<>();

        for (ProtectedRegion region : regions.values()) {
            if (region.getOwner().equals(playerUUID)) {
                result.add(region);
            }
        }

        return result;
    }
}