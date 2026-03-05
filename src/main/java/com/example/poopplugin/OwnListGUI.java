package com.example.poopplugin;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * /own list で表示するワールド一覧 GUI
 * CommandPanel 形式を模したシンプルなインベントリ GUI
 *
 * 各スロットにワールドアイテムを配置し、
 * クリックで /own joinWorld を実行（スリープ解除→テレポート）
 */
public class OwnListGUI implements Listener {

    private static PoopPlugin pluginInstance;
    private static final Map<UUID, Long> openedInventories = new WeakHashMap<>();

    public static void init(PoopPlugin plugin) {
        pluginInstance = plugin;
        Bukkit.getPluginManager().registerEvents(new OwnListGUI(), plugin);
    }

    public static void open(Player player, JavaPlugin plugin) {
        List<WorldManager.OwnWorld> worlds =
                pluginInstance.getWorldManager().getWorldsByOwner(player.getUniqueId());

        int rows = Math.max(1, (int) Math.ceil((worlds.size() + 1) / 9.0) + 1);
        rows = Math.min(rows, 6);

        Inventory inv = Bukkit.createInventory(null, rows * 9,
                "§8§l» §eMyWorlds §8— §7" + player.getName());

        WorldManager wm = pluginInstance.getWorldManager();
        WorldManager.RankTier tier = wm.getTier(player);
        WorldManager.TierConfig cfg = WorldManager.TIER_CONFIGS.get(tier);

        // ─ ヘッダー情報アイテム ─
        ItemStack info = makeItem(Material.PAPER,
                "§e§lワールド情報",
                Arrays.asList(
                        "§7ランク: §f" + tierDisplayName(tier),
                        "§7スロット: §f" + worlds.size() + " / " + cfg.slots,
                        "§7最大サイズ: §f" + formatSize(cfg.maxSizeBytes),
                        "§7同接上限: §f" + (cfg.maxConcurrent < 0 ? "無制限" : cfg.maxConcurrent),
                        "§7コマンド: §f" + (cfg.allowCommands ? "§a使用可" : "§c使用不可")
                ));
        inv.setItem(4, info);

        // ─ 各ワールドアイテム ─
        int slot = 9;
        for (WorldManager.OwnWorld ow : worlds) {
            long sizeBytes = pluginInstance.getWorldManager().getWorldSize(ow.worldName);
            boolean sleeping = ow.sleeping;

            Material mat = sleeping ? Material.GRAY_BED : Material.GREEN_BED;
            List<String> lore = new ArrayList<>(Arrays.asList(
                    "§7コード: §b" + ow.code,
                    "§7状態: " + (sleeping ? "§7スリープ" : "§aアクティブ"),
                    "§7デフォルトGM: §f" + ow.defaultGameMode.name(),
                    "§7サイズ: §f" + formatSize(sizeBytes) + " §7/ §f" + formatSize(cfg.maxSizeBytes),
                    "",
                    "§eクリックで参加"
            ));

            // オンライン人数
            World w = Bukkit.getWorld(ow.worldName);
            if (w != null) {
                lore.add(2, "§7現在人数: §f" + w.getPlayers().size());
            }

            ItemStack item = makeItem(mat, "§a§l" + ow.worldName, lore);

            // NBTに worldCode を埋め込む（クリック時に使用）
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(pluginInstance, "world_code"),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        ow.code
                );
                item.setItemMeta(meta);
            }

            if (slot < rows * 9) inv.setItem(slot++, item);
        }

        // 空スロット（追加可能な数だけ）
        int remaining = cfg.slots - worlds.size();
        for (int i = 0; i < remaining && slot < rows * 9; i++) {
            ItemStack empty = makeItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "§7空きスロット",
                    Collections.singletonList("§7/own create <名前> でワールドを作成"));
            inv.setItem(slot++, empty);
        }

        // ─ 閉じるボタン ─
        ItemStack close = makeItem(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7クリックでGUIを閉じる"));
        inv.setItem(rows * 9 - 1, close);

        player.openInventory(inv);
        openedInventories.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getClickedInventory();
        if (inv == null) return;

        String title = event.getView().getTitle();
        if (!title.startsWith("§8§l» §eMyWorlds")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // 閉じるボタン
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // WorldCode 取得
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String code = meta.getPersistentDataContainer().get(
                new NamespacedKey(pluginInstance, "world_code"),
                org.bukkit.persistence.PersistentDataType.STRING
        );
        if (code == null) return;

        player.closeInventory();
        pluginInstance.getWorldManager().joinWorld(player, code);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openedInventories.remove(player.getUniqueId());
    }

    // ─── ユーティリティ ─────────────────────────────────────

    private static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String tierDisplayName(WorldManager.RankTier tier) {
        return switch (tier) {
            case POOP      -> "§5POOP (Ultimate)";
            case STOOL     -> "§9Stool (VIP)";
            case DIARRHEA  -> "§7Diarrhea (無料)";
        };
    }
}
