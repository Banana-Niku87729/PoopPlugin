package com.example.poopplugin;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * /settings コマンド
 * 現在いるワールドの設定GUIを開く。
 * admin権限を持つプレイヤーのみ変更可能。
 */
public class SettingsCommand implements CommandExecutor, Listener {

    private final PoopPlugin plugin;
    private final WorldManager wm;

    // GUI内アクション識別用キー
    private static final String KEY_ACTION = "settings_action";

    public SettingsCommand(PoopPlugin plugin) {
        this.plugin = plugin;
        this.wm     = plugin.getWorldManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cプレイヤーのみ使用できます。");
            return true;
        }

        WorldManager.OwnWorld ow = wm.getWorldByName(player.getWorld().getName());
        if (ow == null) {
            player.sendMessage("§cこのワールドはown管理下にありません。");
            return true;
        }

        if (!wm.hasPermission(player, ow, "admin")) {
            player.sendMessage("§cこのワールドの設定を変更する権限がありません。");
            return true;
        }

        openSettingsGUI(player, ow);
        return true;
    }

    // ─── メインGUI ──────────────────────────────────────────

    public void openSettingsGUI(Player player, WorldManager.OwnWorld ow) {
        Inventory inv = Bukkit.createInventory(null, 54,
                "§8§l» §bSettings §8— §7" + ow.worldName);

        WorldManager.RankTier tier = wm.getTierByOwner(ow.ownerUUID);
        WorldManager.TierConfig cfg = WorldManager.TIER_CONFIGS.get(tier);
        long sizeBytes = wm.getWorldSize(ow.worldName);

        // ─ ワールド情報 (slot 4) ─
        inv.setItem(4, makeItem(Material.GRASS_BLOCK, "§a§l" + ow.worldName,
                Arrays.asList(
                        "§7オーナー: §f" + ow.ownerName,
                        "§7コード: §b" + ow.code,
                        "§7ランク: §f" + tierName(tier),
                        "§7サイズ: §f" + formatSize(sizeBytes) + " §7/ §f" + formatSize(cfg.maxSizeBytes),
                        "§7同接: §f" + getCurrentPlayers(ow) + " §7/ §f" +
                                (cfg.maxConcurrent < 0 ? "∞" : cfg.maxConcurrent),
                        "§7参加上限: §f" + (cfg.maxJoin < 0 ? "∞" : cfg.maxJoin)
                )));

        // ─ ワールドコード変更 (slot 20) ─
        inv.setItem(20, makeAction(Material.NAME_TAG, "§e§lワールドコード変更",
                Arrays.asList(
                        "§7現在: §b" + ow.code,
                        "",
                        "§eクリックしてチャットで新しいコードを入力"
                ), "change_code"));

        // ─ デフォルトゲームモード変更 (slot 22) ─
        Material gmMat = switch (ow.defaultGameMode) {
            case CREATIVE   -> Material.DIAMOND_SWORD;
            case ADVENTURE  -> Material.COMPASS;
            case SPECTATOR  -> Material.ENDER_EYE;
            default         -> Material.WOODEN_SWORD;
        };
        List<String> gmLore = new ArrayList<>(Arrays.asList(
                "§7現在: §f" + ow.defaultGameMode.name(),
                ""
        ));
        if (tier == WorldManager.RankTier.DIARRHEA) {
            gmLore.add("§c無料ランクでは変更できません");
        } else {
            gmLore.add("§eクリックで切り替え (Survival → Creative → Adventure)");
        }
        inv.setItem(22, makeAction(gmMat, "§b§lデフォルトゲームモード", gmLore, "change_gm"));

        // ─ 権限管理 (slot 29) ─
        inv.setItem(29, makeAction(Material.PLAYER_HEAD, "§d§l権限管理",
                Arrays.asList(
                        "§7メンバーの権限を管理します",
                        "",
                        "§eクリックで権限一覧を開く"
                ), "manage_perms"));

        // ─ BANリスト (slot 31) ─
        inv.setItem(31, makeAction(Material.BARRIER, "§c§lBANリスト",
                Arrays.asList(
                        "§7BANされたプレイヤーを確認",
                        "§7BAN中: §f" + ow.bannedPlayers.size() + "人",
                        "",
                        "§eクリックで一覧を開く"
                ), "manage_bans"));

        // ─ 閉じる (slot 49) ─
        inv.setItem(49, makeAction(Material.DARK_OAK_DOOR, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます"), "close"));

        // 装飾
        ItemStack glass = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }

        player.openInventory(inv);
    }

    // ─── 権限管理GUI ──────────────────────────────────────────

    private void openPermGUI(Player player, WorldManager.OwnWorld ow) {
        Inventory inv = Bukkit.createInventory(null, 54,
                "§8§l» §d権限管理 §8— §7" + ow.worldName);

        int slot = 10;
        for (Map.Entry<UUID, String> e : ow.memberPermissions.entrySet()) {
            if (slot >= 44) break;
            UUID uuid = e.getKey();
            String perm = e.getValue();
            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : uuid.toString().substring(0, 8) + "...";

            Material mat = switch (perm) {
                case "admin" -> Material.DIAMOND;
                case "mod"   -> Material.IRON_INGOT;
                default      -> Material.STONE;
            };

            ItemStack item = makeItem(mat, "§f" + name,
                    Arrays.asList(
                            "§7UUID: §8" + uuid.toString().substring(0, 8) + "...",
                            "§7権限: §e" + perm,
                            "",
                            "§aクリック: §fmod に変更",
                            "§cShift+クリック: §fmember に変更"
                    ));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, KEY_ACTION),
                        PersistentDataType.STRING,
                        "perm:" + uuid
                );
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
            if ((slot - 9) % 9 == 8) slot += 2;
        }

        inv.setItem(49, makeAction(Material.DARK_OAK_DOOR, "§c戻る",
                Collections.singletonList("§7設定GUIに戻る"), "back_to_settings"));

        player.openInventory(inv);
    }

    // ─── BANリストGUI ─────────────────────────────────────────

    private void openBanGUI(Player player, WorldManager.OwnWorld ow) {
        Inventory inv = Bukkit.createInventory(null, 54,
                "§8§l» §cBANリスト §8— §7" + ow.worldName);

        int slot = 10;
        for (UUID uuid : ow.bannedPlayers) {
            if (slot >= 44) break;
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8) + "...";

            ItemStack item = makeItem(Material.BARRIER, "§c" + name,
                    Arrays.asList(
                            "§7UUID: §8" + uuid.toString().substring(0, 8) + "...",
                            "",
                            "§eクリック: §fBANを解除"
                    ));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, KEY_ACTION),
                        PersistentDataType.STRING,
                        "unban:" + uuid
                );
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
            if ((slot - 9) % 9 == 8) slot += 2;
        }

        if (ow.bannedPlayers.isEmpty()) {
            inv.setItem(22, makeItem(Material.GREEN_TERRACOTTA, "§aBAN中のプレイヤーはいません",
                    Collections.emptyList()));
        }

        inv.setItem(49, makeAction(Material.DARK_OAK_DOOR, "§c戻る",
                Collections.singletonList("§7設定GUIに戻る"), "back_to_settings"));

        player.openInventory(inv);
    }

    // ─── クリックイベント ─────────────────────────────────────

    // コード入力待ち状態
    private final Map<UUID, WorldManager.OwnWorld> awaitingCodeInput = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith("§8§l» §bSettings") &&
            !title.startsWith("§8§l» §d権限管理") &&
            !title.startsWith("§8§l» §cBANリスト")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, KEY_ACTION), PersistentDataType.STRING);
        if (action == null) return;

        WorldManager.OwnWorld ow = wm.getWorldByName(
                title.contains("§8— §7") ? title.split("§8— §7")[1] : "");

        if (title.startsWith("§8§l» §bSettings")) {
            ow = wm.getWorldByName(event.getView().getTitle().split("§8— §7")[1]);
        } else if (title.startsWith("§8§l» §d権限管理") ||
                   title.startsWith("§8§l» §cBANリスト")) {
            ow = wm.getWorldByName(event.getView().getTitle().split("§8— §7")[1]);
        }
        if (ow == null) return;

        final WorldManager.OwnWorld finalOw = ow;

        switch (action) {
            case "close" -> player.closeInventory();
            case "back_to_settings" -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openSettingsGUI(player, finalOw), 1L);
            }
            case "change_code" -> {
                player.closeInventory();
                player.sendMessage("§e新しいワールドコードをチャットで入力してください（英小文字・数字4〜16文字）:");
                player.sendMessage("§7キャンセルするには §ecancel §7と入力");
                awaitingCodeInput.put(player.getUniqueId(), ow);
            }
            case "change_gm" -> {
                if (!wm.hasPermission(player, finalOw, "admin")) return;
                GameMode next = switch (finalOw.defaultGameMode) {
                    case SURVIVAL  -> GameMode.CREATIVE;
                    case CREATIVE  -> GameMode.ADVENTURE;
                    default        -> GameMode.SURVIVAL;
                };
                wm.changeDefaultGameMode(player, finalOw, next);
                Bukkit.getScheduler().runTaskLater(plugin, () -> openSettingsGUI(player, finalOw), 2L);
            }
            case "manage_perms" -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openPermGUI(player, finalOw), 1L);
            }
            case "manage_bans" -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openBanGUI(player, finalOw), 1L);
            }
            default -> {
                if (action.startsWith("unban:")) {
                    UUID targetUUID = UUID.fromString(action.substring(6));
                    @SuppressWarnings("deprecation")
                    OfflinePlayer op = Bukkit.getOfflinePlayer(targetUUID);
                    String name = op.getName() != null ? op.getName() : targetUUID.toString().substring(0, 8);
                    wm.unbanPlayerFromWorld(player, finalOw, targetUUID, name);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openBanGUI(player, finalOw), 2L);
                } else if (action.startsWith("perm:")) {
                    UUID targetUUID = UUID.fromString(action.substring(5));
                    @SuppressWarnings("deprecation")
                    OfflinePlayer op = Bukkit.getOfflinePlayer(targetUUID);
                    String name = op.getName() != null ? op.getName() : targetUUID.toString().substring(0, 8);
                    // Shiftクリックでmember、普通クリックでmod
                    String newPerm = event.isShiftClick() ? "member" : "mod";
                    wm.setMemberPermission(player, finalOw, targetUUID, name, newPerm);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openPermGUI(player, finalOw), 2L);
                }
            }
        }
    }

    // チャット入力受付
    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        WorldManager.OwnWorld ow = awaitingCodeInput.get(player.getUniqueId());
        if (ow == null) return;

        event.setCancelled(true);
        String input = event.getMessage().trim().toLowerCase();

        if (input.equals("cancel")) {
            player.sendMessage("§7コード変更をキャンセルしました。");
            awaitingCodeInput.remove(player.getUniqueId());
            return;
        }

        awaitingCodeInput.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            wm.changeWorldCode(player, ow, input);
        });
    }

    // ─── ユーティリティ ─────────────────────────────────────

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeAction(Material mat, String name, List<String> lore, String action) {
        ItemStack item = makeItem(mat, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, KEY_ACTION),
                    PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int getCurrentPlayers(WorldManager.OwnWorld ow) {
        World w = Bukkit.getWorld(ow.worldName);
        return w == null ? 0 : w.getPlayers().size();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String tierName(WorldManager.RankTier tier) {
        return switch (tier) {
            case POOP     -> "§5POOP";
            case STOOL    -> "§9Stool";
            case DIARRHEA -> "§7Diarrhea";
        };
    }
}
