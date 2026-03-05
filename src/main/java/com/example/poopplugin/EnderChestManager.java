package com.example.poopplugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EnderChestManager {

    private final PoopPlugin plugin;
    private File enderChestFile;
    private FileConfiguration enderChestConfig;
    private final Map<UUID, Inventory> enderChests;

    public EnderChestManager(PoopPlugin plugin) {
        this.plugin = plugin;
        this.enderChests = new HashMap<>();
        setupConfig();
    }

    private void setupConfig() {
        enderChestFile = new File(plugin.getDataFolder(), "enderchests.yml");

        if (!enderChestFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                enderChestFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("enderchests.ymlの作成に失敗しました: " + e.getMessage());
            }
        }

        enderChestConfig = YamlConfiguration.loadConfiguration(enderChestFile);
    }

    public void saveConfig() {
        try {
            enderChestConfig.save(enderChestFile);
        } catch (IOException e) {
            plugin.getLogger().severe("enderchests.ymlの保存に失敗しました: " + e.getMessage());
        }
    }

    public Inventory getEnderChest(Player player) {
        UUID uuid = player.getUniqueId();

        // メモリにあればそれを返す
        if (enderChests.containsKey(uuid)) {
            return enderChests.get(uuid);
        }

        // なければロード
        Inventory inv = Bukkit.createInventory(null, 27, "Shared Ender Chest");
        loadInventory(player, inv);
        enderChests.put(uuid, inv);
        return inv;
    }

    public void openEnderChest(Player player) {
        player.openInventory(getEnderChest(player));
    }

    public void saveInventory(Player player, Inventory inv) {
        UUID uuid = player.getUniqueId();
        enderChestConfig.set("chests." + uuid, inv.getContents());
        saveConfig();
    }

    private void loadInventory(Player player, Inventory inv) {
        UUID uuid = player.getUniqueId();
        if (enderChestConfig.contains("chests." + uuid)) {
            @SuppressWarnings("unchecked")
            List<ItemStack> contentList = (List<ItemStack>) enderChestConfig.getList("chests." + uuid);
            if (contentList != null) {
                ItemStack[] contents = contentList.toArray(new ItemStack[0]);
                inv.setContents(contents);
            }
        }
    }

    public void cleanup(Player player) {
        // メモリ節約のため、オフラインになったら削除するならここ
        // 今回は常に保持でもいいが、サーバー負荷を考慮して消すのが一般的
        UUID uuid = player.getUniqueId();

        // 念の為保存してから削除
        if (enderChests.containsKey(uuid)) {
            saveInventory(player, enderChests.get(uuid));
            enderChests.remove(uuid);
        }
    }
}
