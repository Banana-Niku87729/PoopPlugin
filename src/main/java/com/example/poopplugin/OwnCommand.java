package com.example.poopplugin;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * /own コマンドハンドラ
 *
 * サブコマンド一覧:
 *   /own create <WorldName>         - ワールド作成
 *   /own list                       - 自分のワールド一覧GUI
 *   /own ban <Player>               - 現在のワールドからBANs
 *   /own <WorldName> ban <Player>   - 指定ワールドからBAN
 *   /own unban <Player>             - 現在のワールドのBAN解除
 *   /own <WorldName> unban <Player> - 指定ワールドのBAN解除
 *   /own give [WorldName] <Player> <Item> [量] - アイテム付与
 *   /own gamemode [WorldName] <Player> <mode> - ゲームモード変更
 */
public class OwnCommand implements CommandExecutor, TabCompleter {

    private final PoopPlugin plugin;
    private final WorldManager wm;

    public OwnCommand(PoopPlugin plugin) {
        this.plugin = plugin;
        this.wm     = plugin.getWorldManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cプレイヤーのみ使用できます。");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "list"   -> handleList(player);
            case "ban"    -> handleBan(player, args, null);
            case "unban"  -> handleUnban(player, args, null);
            case "give"       -> handleGive(player, args, null);
            case "gamemode", "gm" -> handleGamemode(player, args, null);
            default -> {
                // /own <WorldName> ban/unban/give/gamemode ...
                String worldName = args[0];
                WorldManager.OwnWorld ow = wm.getWorldByName(worldName);
                if (ow == null) {
                    // ワールドコードでも検索
                    ow = wm.getWorldByCode(worldName);
                }
                if (ow == null) {
                    player.sendMessage("§cサブコマンドまたはワールド名が不明です: §e" + args[0]);
                    sendHelp(player);
                    return true;
                }
                if (args.length < 2) {
                    sendHelp(player);
                    return true;
                }
                String sub2 = args[1].toLowerCase();
                switch (sub2) {
                    case "ban"      -> handleBan(player, Arrays.copyOfRange(args, 1, args.length), ow);
                    case "unban"    -> handleUnban(player, Arrays.copyOfRange(args, 1, args.length), ow);
                    case "give"     -> handleGive(player, Arrays.copyOfRange(args, 1, args.length), ow);
                    case "gamemode","gm" -> handleGamemode(player, Arrays.copyOfRange(args, 1, args.length), ow);
                    case "delete"   -> handleDelete(player, ow);
                    case "disabled" -> handleDisableCode(player, ow);
                    default         -> { player.sendMessage("§c不明なサブコマンド: §e" + sub2); sendHelp(player); }
                }
            }
        }
        return true;
    }

    // ─── create ──────────────────────────────────────────────

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c使用法: /own create <ワールド名> [default|flat]");
            return;
        }
        String worldName = args[1];
        String worldType = (args.length >= 3) ? args[2].toLowerCase() : "default";
        // "default" は "normal" として扱う
        if (worldType.equals("default") || !worldType.equals("flat")) worldType = "normal";

        WorldManager.OwnWorld ow = wm.createWorld(player, worldName, worldType);
        if (ow == null) return;

        final String finalType = worldType;
        // MultiverseCore が使えるか確認
        boolean hasMV = Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null;

        if (hasMV) {
            String mvType = "flat".equals(finalType) ? "flat" : "normal";
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "mv create " + worldName + " " + mvType);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World w = Bukkit.getWorld(worldName);
                if (w != null) Bukkit.unloadWorld(w, true);
            }, 60L);
        } else {
            // フォールバック: Bukkit API
            Bukkit.getScheduler().runTask(plugin, () -> {
                WorldCreator creator = new WorldCreator(worldName);
                if ("flat".equals(finalType)) {
                    creator.type(WorldType.FLAT);
                } else {
                    creator.type(WorldType.NORMAL);
                }
                creator.createWorld();
                World w = Bukkit.getWorld(worldName);
                if (w != null) Bukkit.unloadWorld(w, true);
            });
        }
    }

    // ─── list ────────────────────────────────────────────────

    private void handleList(Player player) {
        OwnListGUI.open(player, plugin);
    }

    // ─── ban / unban ─────────────────────────────────────────

    private void handleBan(Player player, String[] args, WorldManager.OwnWorld owContext) {
        // args[0] = "ban", args[1] = playerName
        if (args.length < 2) {
            player.sendMessage("§c使用法: /own ban <プレイヤー名>");
            return;
        }
        String targetName = args[1];

        WorldManager.OwnWorld ow;
        if (owContext != null) {
            ow = owContext;
            // 権限確認
            if (!wm.hasPermission(player, ow, "admin")) {
                player.sendMessage("§cこの操作にはadmin権限が必要です。");
                return;
            }
        } else {
            // 現在いるワールドのOwnWorld
            ow = wm.getWorldByName(player.getWorld().getName());
            if (ow == null) {
                player.sendMessage("§cあなたは現在、own管理ワールドにいません。");
                return;
            }
            if (!wm.hasPermission(player, ow, "admin")) {
                player.sendMessage("§cこの操作にはadmin権限が必要です。");
                return;
            }
        }

        // 対象プレイヤー解決
        Player target = Bukkit.getPlayer(targetName);
        UUID targetUUID;
        if (target != null) {
            targetUUID = target.getUniqueId();
        } else {
            // オフラインプレイヤーも許可（UUIDが必要）
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
            if (!op.hasPlayedBefore()) {
                player.sendMessage("§cプレイヤー §e" + targetName + " §cが見つかりません。");
                return;
            }
            targetUUID = op.getUniqueId();
        }

        wm.banPlayerFromWorld(player, ow, targetUUID, targetName);
    }

    private void handleUnban(Player player, String[] args, WorldManager.OwnWorld owContext) {
        if (args.length < 2) {
            player.sendMessage("§c使用法: /own unban <プレイヤー名>");
            return;
        }
        String targetName = args[1];

        WorldManager.OwnWorld ow;
        if (owContext != null) {
            ow = owContext;
        } else {
            ow = wm.getWorldByName(player.getWorld().getName());
            if (ow == null) {
                player.sendMessage("§cあなたは現在、own管理ワールドにいません。");
                return;
            }
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
        UUID targetUUID = op.getUniqueId();

        wm.unbanPlayerFromWorld(player, ow, targetUUID, targetName);
    }

    // ─── give ────────────────────────────────────────────────

    private void handleGive(Player player, String[] args, WorldManager.OwnWorld owContext) {
        // args: give <targetPlayer> <material> [amount]
        if (args.length < 3) {
            player.sendMessage("§c使用法: /own give <プレイヤー> <アイテム> [数量]");
            return;
        }

        WorldManager.OwnWorld ow;
        if (owContext != null) {
            ow = owContext;
        } else {
            ow = wm.getWorldByName(player.getWorld().getName());
            if (ow == null) {
                player.sendMessage("§cあなたは現在、own管理ワールドにいません。");
                return;
            }
        }

        if (!wm.hasPermission(player, ow, "admin")) {
            player.sendMessage("§cこの操作にはadmin権限が必要です。");
            return;
        }

        String targetName = args[1];
        String matName    = args[2].toUpperCase();
        int amount = 1;
        if (args.length >= 4) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); }
            catch (NumberFormatException e) { player.sendMessage("§c数量は整数で指定してください。"); return; }
        }

        Material mat = Material.getMaterial(matName);
        if (mat == null || !mat.isItem()) {
            player.sendMessage("§c不明なアイテム: §e" + matName);
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§cプレイヤー §e" + targetName + " §cはオンラインではありません。");
            return;
        }

        target.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amount));
        player.sendMessage("§a" + targetName + " に §e" + matName + " x" + amount + " §aを付与しました。");
        target.sendMessage("§a管理者から §e" + matName + " x" + amount + " §aを受け取りました。");
    }

    // ─── gamemode ────────────────────────────────────────────

    private void handleGamemode(Player player, String[] args, WorldManager.OwnWorld owContext) {
        // args: gamemode <targetPlayer> <mode>
        if (args.length < 3) {
            player.sendMessage("§c使用法: /own gamemode <プレイヤー> <survival|creative|adventure|spectator>");
            return;
        }

        WorldManager.OwnWorld ow;
        if (owContext != null) {
            ow = owContext;
        } else {
            ow = wm.getWorldByName(player.getWorld().getName());
            if (ow == null) {
                player.sendMessage("§cあなたは現在、own管理ワールドにいません。");
                return;
            }
        }

        if (!wm.hasPermission(player, ow, "admin")) {
            player.sendMessage("§cこの操作にはadmin権限が必要です。");
            return;
        }

        String targetName = args[1];
        String modeName   = args[2].toUpperCase();

        GameMode mode;
        try { mode = GameMode.valueOf(modeName); }
        catch (IllegalArgumentException e) {
            // 短縮形対応
            mode = switch (modeName.toLowerCase()) {
                case "s", "0"  -> GameMode.SURVIVAL;
                case "c", "1"  -> GameMode.CREATIVE;
                case "a", "2"  -> GameMode.ADVENTURE;
                case "sp","3"  -> GameMode.SPECTATOR;
                default -> null;
            };
            if (mode == null) {
                player.sendMessage("§c不明なゲームモード: §e" + modeName);
                return;
            }
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§cプレイヤー §e" + targetName + " §cはオンラインではありません。");
            return;
        }

        // クリエイティブ/アドベンチャー許可チェック
        WorldManager.RankTier tier = wm.getTierByOwner(ow.ownerUUID);
        WorldManager.TierConfig cfg = WorldManager.TIER_CONFIGS.get(tier);
        if (!cfg.allowCreative && mode != GameMode.SURVIVAL) {
            player.sendMessage("§c現在のランクではサバイバル以外のゲームモードは使用できません。");
            return;
        }

        target.setGameMode(mode);
        player.sendMessage("§a" + targetName + " のゲームモードを §e" + mode.name() + " §aに変更しました。");
        target.sendMessage("§aゲームモードが §e" + mode.name() + " §aに変更されました。");
    }

    // ─── delete ──────────────────────────────────────────────

    private void handleDelete(Player player, WorldManager.OwnWorld ow) {
        // 確認GUIを開く
        DeleteConfirmGUI.open(player, ow, plugin);
    }

    // ─── disabled (カスタムコード無効化) ─────────────────────

    private void handleDisableCode(Player player, WorldManager.OwnWorld ow) {
        // 確認GUIを開く
        DisableCodeConfirmGUI.open(player, ow, plugin);
    }

    // ─── ヘルプ ──────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage("§e§l■ /own コマンド一覧");
        player.sendMessage("§e/own create <名前> [default|flat] §7- ワールドを作成");
        player.sendMessage("§e/own list §7- ワールド一覧GUIを開く");
        player.sendMessage("§e/own ban <プレイヤー> §7- 現在のワールドからBAN");
        player.sendMessage("§e/own <ワールド> ban <プレイヤー> §7- 指定ワールドからBAN");
        player.sendMessage("§e/own unban <プレイヤー> §7- BAN解除");
        player.sendMessage("§e/own give <プレイヤー> <アイテム> [量] §7- アイテム付与");
        player.sendMessage("§e/own gamemode <プレイヤー> <モード> §7- ゲームモード変更");
        player.sendMessage("§e/own <ワールド> delete §7- ワールドを削除（確認GUI）");
        player.sendMessage("§e/own <ワールド> disabled §7- カスタムコードを無効化（確認GUI）");
    }

    // ─── TabCompleter ─────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            result.addAll(Arrays.asList("create", "list", "ban", "unban", "give", "gamemode"));
            // 自分のワールド名も候補に
            for (WorldManager.OwnWorld ow : wm.getWorldsByOwner(player.getUniqueId())) {
                result.add(ow.worldName);
            }
            return filter(result, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("ban") || sub.equals("unban")) {
                for (Player p : Bukkit.getOnlinePlayers()) result.add(p.getName());
            } else if (sub.equals("give") || sub.equals("gamemode")) {
                for (Player p : Bukkit.getOnlinePlayers()) result.add(p.getName());
            } else if (sub.equals("create")) {
                // 第2引数はワールド名なので補完なし
            } else {
                // /own <WorldName> → サブコマンド
                result.addAll(Arrays.asList("ban", "unban", "give", "gamemode", "delete", "disabled"));
            }
            return filter(result, args[1]);
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("create")) {
                result.addAll(Arrays.asList("default", "flat"));
            } else if (sub.equals("gamemode")) {
                result.addAll(Arrays.asList("survival", "creative", "adventure", "spectator"));
            } else if (sub.equals("ban") || sub.equals("unban")) {
                String sub2 = args[1].toLowerCase();
                if (sub2.equals("ban") || sub2.equals("unban")) {
                    for (Player p : Bukkit.getOnlinePlayers()) result.add(p.getName());
                } else if (sub2.equals("gamemode")) {
                    result.addAll(Arrays.asList("survival", "creative", "adventure", "spectator"));
                }
            }
            return filter(result, args[2]);
        }

        return result;
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> r = new ArrayList<>();
        for (String s : list) if (s.toLowerCase().startsWith(prefix.toLowerCase())) r.add(s);
        return r;
    }
}
