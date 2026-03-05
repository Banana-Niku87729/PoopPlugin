package com.example.poopplugin;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;

import java.util.*;

/**
 * /account [ユーザー名] コマンド
 * 自分または他プレイヤーのアカウント情報を表示する。
 *
 * /pa open account でパネルを開く（PanelAnnouncer 連携用エイリアス）
 *
 * 使用法:
 *   /account              → 自分のアカウント情報GUI
 *   /account <ユーザー名> → 他人のアカウント情報GUI（閲覧のみ）
 *   /pa open account      → 自分のアカウント情報GUIを開く（/accountと同じ）
 */
public class AccountCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String TITLE_PREFIX = "§8§l» §dAccount §8—";
    private final PoopPlugin plugin;
    private final WorldManager wm;

    public AccountCommand(PoopPlugin plugin) {
        this.plugin = plugin;
        this.wm     = plugin.getWorldManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ─── /account [ユーザー名] ────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cプレイヤーのみ使用できます。");
            return true;
        }

        // /pa open account への対応
        // plugin.yml 等で /pa が別コマンドの場合は直接ここには来ないが、
        // PoopPlugin.java で "pa" コマンドから open account を受け取った際に
        // このメソッドを呼び出すことができる
        if (label.equalsIgnoreCase("pa")) {
            // /pa open account
            if (args.length >= 2
                    && args[0].equalsIgnoreCase("open")
                    && args[1].equalsIgnoreCase("account")) {
                openAccountGUI(player, player, false);
            } else {
                player.sendMessage("§c使用法: /pa open account");
            }
            return true;
        }

        // /account [ユーザー名]
        if (args.length == 0) {
            openAccountGUI(player, player, false);
            return true;
        }

        String targetName = args[0];
        // 自分自身
        if (targetName.equalsIgnoreCase(player.getName())) {
            openAccountGUI(player, player, false);
            return true;
        }

        // 他プレイヤー
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage("§cプレイヤー §e" + targetName + " §cは存在しません。");
            return true;
        }
        openAccountGUI(player, target, true);
        return true;
    }

    // ─── GUI 表示 ─────────────────────────────────────────────

    /**
     * @param viewer  GUIを閲覧するプレイヤー
     * @param target  情報を表示する対象（OfflinePlayer）
     * @param readonly true=閲覧のみ
     */
    public void openAccountGUI(Player viewer, OfflinePlayer target, boolean readonly) {
        String title = TITLE_PREFIX + " §7" + target.getName();
        Inventory inv = Bukkit.createInventory(null, 54, title);

        WorldManager.RankTier tier = getRankTierOf(target);
        WorldManager.TierConfig cfg = WorldManager.TIER_CONFIGS.get(tier);
        List<WorldManager.OwnWorld> worlds = wm.getWorldsByOwner(target.getUniqueId());

        // ── ヘッダー: プレイヤー情報 (slot 4) ──
        List<String> profileLore = new ArrayList<>(Arrays.asList(
                "§7UUID: §f" + target.getUniqueId(),
                "§7ランク: §f" + tierDisplayName(tier),
                "§7最終ログイン: §f" + (target.getLastSeen() > 0
                        ? new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm").format(new java.util.Date(target.getLastSeen()))
                        : "不明"),
                "§7オンライン: §f" + (target.isOnline() ? "§aオンライン" : "§cオフライン")
        ));
        if (!readonly) {
            profileLore.add("");
            profileLore.add("§7自分のアカウント情報です。");
        }
        inv.setItem(4, makeSkullItem(target, "§d§l" + target.getName(), profileLore));

        // ── ワールド情報 (slot 19) ──
        inv.setItem(19, makeItem(Material.GRASS_BLOCK,
                "§a§lMyWorlds",
                Arrays.asList(
                        "§7スロット: §f" + worlds.size() + " §7/ §f" + cfg.slots,
                        "§7最大サイズ: §f" + formatSize(cfg.maxSizeBytes),
                        "§7同接上限: §f" + (cfg.maxConcurrent < 0 ? "無制限" : cfg.maxConcurrent + "人"),
                        "",
                        "§eクリックでワールド一覧を開く" + (readonly ? "" : "")
                ), "worlds"));

        // ── ランク情報 (slot 21) ──
        inv.setItem(21, makeItem(rankMaterial(tier),
                "§b§lランク情報",
                Arrays.asList(
                        "§7現在: §f" + tierDisplayName(tier),
                        "§7コマンド: §f" + (cfg.allowCommands ? "§a有効" : "§c無効"),
                        "§7クリエイティブ: §f" + (cfg.allowCreative ? "§a有効" : "§c無効"),
                        "§7スリープ遅延: §f" + (cfg.sleepDelayMs / 1000) + "秒"
                ), "rank"));

        // ── アカウント統計 (slot 23) ──
        long firstPlayed = target.getFirstPlayed();
        inv.setItem(23, makeItem(Material.BOOK,
                "§e§l統計情報",
                Arrays.asList(
                        "§7初回ログイン: §f" + (firstPlayed > 0
                                ? new java.text.SimpleDateFormat("yyyy/MM/dd").format(new java.util.Date(firstPlayed))
                                : "不明"),
                        "§7保有ワールド数: §f" + worlds.size()
                ), null));

        // ── 閲覧中の他人ワールドを一覧表示 (row 4+) ──
        int slot = 36;
        for (WorldManager.OwnWorld ow : worlds) {
            if (slot >= 54) break;
            boolean sleeping = ow.sleeping;
            Material mat = sleeping ? Material.GRAY_BED : Material.GREEN_BED;
            List<String> wlore = new ArrayList<>(Arrays.asList(
                    "§7コード: §b" + (readonly ? "§7（非表示）" : ow.code),
                    "§7状態: " + (sleeping ? "§7スリープ" : "§aアクティブ"),
                    "§7デフォルトGM: §f" + ow.defaultGameMode.name(),
                    "§7タイプ: §f" + (ow.worldType != null ? ow.worldType : "normal")
            ));
            if (!readonly) {
                wlore.add("");
                wlore.add("§eクリックで参加");
            }

            ItemStack witem = makeItem(mat, "§f" + ow.worldName, wlore,
                    readonly ? null : ow.code);
            inv.setItem(slot++, witem);
        }

        // ── 閉じるボタン (slot 49) ──
        inv.setItem(49, makeItem(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7クリックでGUIを閉じる"), null));

        viewer.openInventory(inv);
    }

    // ─── クリックイベント ─────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // アクション取得
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "account_action"),
                org.bukkit.persistence.PersistentDataType.STRING);

        if (action == null) return;

        if (action.equals("worlds")) {
            // ワールド一覧GUIを開く
            player.closeInventory();
            OwnListGUI.open(player, plugin);
            return;
        }

        // ワールドコード → 参加
        WorldManager.OwnWorld ow = wm.getWorldByCode(action);
        if (ow != null) {
            player.closeInventory();
            wm.joinWorld(player, action);
        }
    }

    // ─── ユーティリティ ──────────────────────────────────────

    private ItemStack makeItem(Material mat, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            if (action != null) {
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "account_action"),
                        org.bukkit.persistence.PersistentDataType.STRING, action);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        return makeItem(mat, name, lore, null);
    }

    @SuppressWarnings("deprecation")
    private ItemStack makeSkullItem(OfflinePlayer target, String name, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta =
                (org.bukkit.inventory.meta.SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(name);
            meta.setLore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private WorldManager.RankTier getRankTierOf(OfflinePlayer op) {
        Player online = op.getPlayer();
        if (online != null) return wm.getTier(online);
        return WorldManager.RankTier.DIARRHEA;
    }

    private static String tierDisplayName(WorldManager.RankTier tier) {
        return switch (tier) {
            case POOP     -> "§5POOP (Ultimate)";
            case STOOL    -> "§9Stool (VIP)";
            case DIARRHEA -> "§7Diarrhea (無料)";
        };
    }

    private static Material rankMaterial(WorldManager.RankTier tier) {
        return switch (tier) {
            case POOP     -> Material.NETHERITE_INGOT;
            case STOOL    -> Material.GOLD_INGOT;
            case DIARRHEA -> Material.IRON_INGOT;
        };
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) result.add(p.getName());
            String prefix = args[0].toLowerCase();
            result.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            return result;
        }
        return Collections.emptyList();
    }
}
