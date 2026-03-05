package com.example.poopplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /protectコマンドの実装
 */
public class ProtectCommand implements CommandExecutor, TabCompleter {

    private final PoopPlugin plugin;
    private final Map<UUID, Location> pos1Selections;
    private final Map<UUID, Location> pos2Selections;

    public ProtectCommand(PoopPlugin plugin) {
        this.plugin = plugin;
        this.pos1Selections = new HashMap<>();
        this.pos2Selections = new HashMap<>();
    }

    /**
     * pos1選択マップを取得
     */
    public Map<UUID, Location> getPos1Selections() {
        return pos1Selections;
    }

    /**
     * pos2選択マップを取得
     */
    public Map<UUID, Location> getPos2Selections() {
        return pos2Selections;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "item":
            case "wand":
                return handleItem(player);

            case "pos1":
                return handlePos1(player);

            case "pos2":
                return handlePos2(player);

            case "create":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用法: /protect create <名前>");
                    return true;
                }
                return handleCreate(player, args[1]);

            case "delete":
            case "remove":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用法: /protect delete <名前>");
                    return true;
                }
                return handleDelete(player, args[1]);

            case "addmember":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "使用法: /protect addmember <エリア名> <プレイヤー>");
                    return true;
                }
                return handleAddMember(player, args[1], args[2]);

            case "removemember":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "使用法: /protect removemember <エリア名> <プレイヤー>");
                    return true;
                }
                return handleRemoveMember(player, args[1], args[2]);

            case "flag":
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "使用法: /protect flag <エリア名> <フラグ> <true|false>");
                    return true;
                }
                return handleFlag(player, args[1], args[2], args[3]);

            case "info":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用法: /protect info <名前>");
                    return true;
                }
                return handleInfo(player, args[1]);

            case "list":
                return handleList(player, args);

            case "priority":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "使用法: /protect priority <エリア名> <優先度>");
                    return true;
                }
                return handlePriority(player, args[1], args[2]);

            case "tp":
            case "teleport":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用法: /protect tp <エリア名>");
                    return true;
                }
                return handleTeleport(player, args[1]);

            default:
                sendHelp(player);
                return true;
        }
    }

    private boolean handlePos1(Player player) {
        Location loc = player.getLocation();
        pos1Selections.put(player.getUniqueId(), loc);
        player.sendMessage(ChatColor.GREEN + "位置1を設定しました: " + formatLocation(loc));
        return true;
    }

    private boolean handleItem(Player player) {
        // 範囲選択の杖を渡す
        player.getInventory().addItem(SelectionWand.createWand());
        player.sendMessage(ChatColor.GREEN + "範囲選択の杖を受け取りました!");
        player.sendMessage(ChatColor.GRAY + "左クリック: 位置1 | 右クリック: 位置2");
        return true;
    }

    private boolean handlePos2(Player player) {
        Location loc = player.getLocation();
        pos2Selections.put(player.getUniqueId(), loc);
        player.sendMessage(ChatColor.GREEN + "位置2を設定しました: " + formatLocation(loc));
        return true;
    }

    private boolean handleCreate(Player player, String name) {
        UUID uuid = player.getUniqueId();

        if (!pos1Selections.containsKey(uuid) || !pos2Selections.containsKey(uuid)) {
            player.sendMessage(ChatColor.RED + "先に /protect pos1 と /protect pos2 で範囲を選択してください");
            return true;
        }

        Location pos1 = pos1Selections.get(uuid);
        Location pos2 = pos2Selections.get(uuid);

        if (!pos1.getWorld().equals(pos2.getWorld())) {
            player.sendMessage(ChatColor.RED + "位置1と位置2は同じワールドである必要があります");
            return true;
        }

        ProtectionManager manager = plugin.getProtectionManager();

        if (manager.createRegion(name, pos1, pos2, player)) {
            player.sendMessage(ChatColor.GREEN + "保護エリア '" + name + "' を作成しました");

            // 選択をクリア
            pos1Selections.remove(uuid);
            pos2Selections.remove(uuid);
        } else {
            player.sendMessage(ChatColor.RED + "その名前の保護エリアは既に存在します");
        }

        return true;
    }

    private boolean handleDelete(Player player, String name) {
        ProtectionManager manager = plugin.getProtectionManager();
        ProtectedRegion region = manager.getRegion(name);

        if (region == null) {
            player.sendMessage(ChatColor.RED + "保護エリア '" + name + "' が見つかりません");
            return true;
        }

        if (!region.isOwner(player.getUniqueId()) && !player.hasPermission("poopplugin.protect.admin")) {
            player.sendMessage(ChatColor.RED + "この保護エリアを削除する権限がありません");
            return true;
        }

        manager.deleteRegion(name);
        player.sendMessage(ChatColor.GREEN + "保護エリア '" + name + "' を削除しました");

        return true;
    }

    private boolean handleAddMember(Player player, String regionName, String targetName) {
        ProtectionManager manager = plugin.getProtectionManager();
        ProtectedRegion region = manager.getRegion(regionName);

        if (region == null) {
            player.sendMessage(ChatColor.RED + "保護エリア '" + regionName + "' が見つかりません");
            return true;
        }

        if (!region.isOwner(player.getUniqueId()) && !player.hasPermission("poopplugin.protect.admin")) {
            player.sendMessage(ChatColor.RED + "この保護エリアにメンバーを追加する権限がありません");
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "プレイヤー '" + targetName + "' が見つかりません");
            return true;
        }

        region.addMember(target.getUniqueId());
        manager.saveRegions();

        player.sendMessage(ChatColor.GREEN + target.getName() + " を保護エリア '" + regionName + "' のメンバーに追加しました");
        target.sendMessage(ChatColor.GREEN + "保護エリア '" + regionName + "' のメンバーに追加されました");

        return true;
    }

    private boolean handleRemoveMember(Player player, String regionName, String targetName) {
        ProtectionManager manager = plugin.getProtectionManager();
        ProtectedRegion region = manager.getRegion(regionName);

        if (region == null) {
            player.sendMessage(ChatColor.RED + "保護エリア '" + regionName + "' が見つかりません");
            return true;
        }

        if (!region.isOwner(player.getUniqueId()) && !player.hasPermission("poopplugin.protect.admin")) {
            player.sendMessage(ChatColor.RED + "この保護エリアからメンバーを削除する権限がありません");
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "プレイヤー '" + targetName + "' が見つかりません");
            return true;
        }

        region.removeMember(target.getUniqueId());
        manager.saveRegions();

        player.sendMessage(ChatColor.GREEN + target.getName() + " を保護エリア '" + regionName + "' のメンバーから削除しました");

        return true;
    }

    private boolean handleFlag(Player player, String regionName, String flag, String value) {
        ProtectionManager manager = plugin.getProtectionManager();
        ProtectedRegion region = manager.getRegion(regionName);

        if (region == null) {
            player.sendMessage(ChatColor.RED + "保護エリア '" + regionName + "' が見つかりません");
            return true;
        }

        if (!region.isOwner(player.getUniqueId()) && !player.hasPermission("poopplugin.protect.admin")) {
            player.sendMessage(ChatColor.RED + "この保護エリアのフラグを変更する権限がありません");
            return true;
        }

        boolean flagValue;
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("allow")) {
            flagValue = true;
        } else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("deny")) {
            flagValue = false;
        } else {
            player.sendMessage(ChatColor.RED + "値は true または false である必要があります");
            return true;
        }

        region.setFlag(flag.toLowerCase(), flagValue);
        manager.saveRegions();

        player.sendMessage(ChatColor.GREEN + "保護エリア '" + regionName + "' のフラグ '" + flag + "' を " +
                (flagValue ? "有効" : "無効") + " にしました");

        return true;
    }

    private boolean handleInfo(Player player, String name) {
        ProtectionManager manager = plugin.getProtectionManager();
        ProtectedRegion region = manager.getRegion(name);

        if (region == null) {
            player.sendMessage(ChatColor.RED + "保護エリア '" + name + "' が見つかりません");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "========== " + region.getName() + " ==========");
        player.sendMessage(ChatColor.YELLOW + "オーナー: " + ChatColor.WHITE +
                Bukkit.getOfflinePlayer(region.getOwner()).getName());

        if (!region.getMembers().isEmpty()) {
            List<String> memberNames = region.getMembers().stream()
                    .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                    .collect(Collectors.toList());
            player.sendMessage(ChatColor.YELLOW + "メンバー: " + ChatColor.WHITE + String.join(", ", memberNames));
        }

        player.sendMessage(ChatColor.YELLOW + "優先度: " + ChatColor.WHITE + region.getPriority());
        player.sendMessage(ChatColor.YELLOW + "体積: " + ChatColor.WHITE + region.getVolume() + " ブロック");
        player.sendMessage(ChatColor.YELLOW + "位置1: " + ChatColor.WHITE + formatLocation(region.getPos1()));
        player.sendMessage(ChatColor.YELLOW + "位置2: " + ChatColor.WHITE + formatLocation(region.getPos2()));

        player.sendMessage(ChatColor.YELLOW + "フラグ:");
        for (Map.Entry<String, Boolean> entry : region.getFlags().entrySet()) {
            player.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " +
                    (entry.getValue() ? ChatColor.GREEN + "有効" : ChatColor.RED + "無効"));
        }

        return true;
    }

    private boolean handleList(Player player, String[] args) {
        ProtectionManager manager = plugin.getProtectionManager();

        List<ProtectedRegion> regions;
        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            regions = new ArrayList<>(manager.getAllRegions());
        } else {
            regions = manager.getPlayerRegions(player.getUniqueId());
        }

        if (regions.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "保護エリアがありません");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "========== 保護エリア一覧 ==========");
        for (ProtectedRegion region : regions) {
            player.sendMessage(ChatColor.YELLOW + "- " + region.getName() +
                    ChatColor.GRAY + " (優先度: " + region.getPriority() + ")");
        }

        return true;
    }

    private boolean handlePriority(Player player, String regionName, String priorityStr) {
        if (!player.hasPermission("poopplugin.protect.admin")) {
            player.sendMessage(ChatColor.RED + "優先度を変更する権限がありません");
            return true;
        }

        ProtectionManager manager = plugin.getProtectionManager();
        ProtectedRegion region = manager.getRegion(regionName);

        if (region == null) {
            player.sendMessage(ChatColor.RED + "保護エリア '" + regionName + "' が見つかりません");
            return true;
        }

        try {
            int priority = Integer.parseInt(priorityStr);
            region.setPriority(priority);
            manager.saveRegions();
            player.sendMessage(ChatColor.GREEN + "保護エリア '" + regionName + "' の優先度を " + priority + " に設定しました");
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "優先度は数値で指定してください");
        }

        return true;
    }

    private boolean handleTeleport(Player player, String name) {
        ProtectionManager manager = plugin.getProtectionManager();
        ProtectedRegion region = manager.getRegion(name);

        if (region == null) {
            player.sendMessage(ChatColor.RED + "保護エリア '" + name + "' が見つかりません");
            return true;
        }

        if (!region.isOwner(player.getUniqueId()) &&
                !region.isMember(player.getUniqueId()) &&
                !player.hasPermission("poopplugin.protect.admin")) {
            player.sendMessage(ChatColor.RED + "この保護エリアにテレポートする権限がありません");
            return true;
        }

        // エリアの中心にテレポート
        Location pos1 = region.getPos1();
        Location pos2 = region.getPos2();

        double centerX = (pos1.getX() + pos2.getX()) / 2;
        double centerY = Math.max(pos1.getY(), pos2.getY());
        double centerZ = (pos1.getZ() + pos2.getZ()) / 2;

        Location teleportLoc = new Location(pos1.getWorld(), centerX, centerY + 1, centerZ);
        player.teleport(teleportLoc);
        player.sendMessage(ChatColor.GREEN + "保護エリア '" + name + "' にテレポートしました");

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "========== 保護コマンド ==========");
        player.sendMessage(ChatColor.YELLOW + "/protect item" + ChatColor.GRAY + " - 範囲選択の杖を取得");
        player.sendMessage(ChatColor.YELLOW + "/protect pos1" + ChatColor.GRAY + " - 位置1を設定");
        player.sendMessage(ChatColor.YELLOW + "/protect pos2" + ChatColor.GRAY + " - 位置2を設定");
        player.sendMessage(ChatColor.YELLOW + "/protect create <名前>" + ChatColor.GRAY + " - 保護エリアを作成");
        player.sendMessage(ChatColor.YELLOW + "/protect delete <名前>" + ChatColor.GRAY + " - 保護エリアを削除");
        player.sendMessage(ChatColor.YELLOW + "/protect addmember <エリア> <プレイヤー>" + ChatColor.GRAY + " - メンバー追加");
        player.sendMessage(ChatColor.YELLOW + "/protect removemember <エリア> <プレイヤー>" + ChatColor.GRAY + " - メンバー削除");
        player.sendMessage(ChatColor.YELLOW + "/protect flag <エリア> <フラグ> <値>" + ChatColor.GRAY + " - フラグ設定");
        player.sendMessage(ChatColor.YELLOW + "/protect info <名前>" + ChatColor.GRAY + " - エリア情報を表示");
        player.sendMessage(ChatColor.YELLOW + "/protect list [all]" + ChatColor.GRAY + " - エリア一覧を表示");
        player.sendMessage(ChatColor.YELLOW + "/protect tp <名前>" + ChatColor.GRAY + " - エリアにテレポート");
        player.sendMessage(ChatColor.GRAY + "フラグ: build, interact, pvp, mob-spawn, explosion, fire-spread");
    }

    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("item", "wand", "pos1", "pos2", "create", "delete", "addmember",
                    "removemember", "flag", "info", "list", "priority", "tp"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("info") ||
                    args[0].equalsIgnoreCase("addmember") || args[0].equalsIgnoreCase("removemember") ||
                    args[0].equalsIgnoreCase("flag") || args[0].equalsIgnoreCase("priority") ||
                    args[0].equalsIgnoreCase("tp")) {

                ProtectionManager manager = plugin.getProtectionManager();
                for (ProtectedRegion region : manager.getAllRegions()) {
                    completions.add(region.getName());
                }
            } else if (args[0].equalsIgnoreCase("list")) {
                completions.add("all");
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("addmember") || args[0].equalsIgnoreCase("removemember")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            } else if (args[0].equalsIgnoreCase("flag")) {
                completions.addAll(Arrays.asList("build", "interact", "pvp", "mob-spawn",
                        "explosion", "fire-spread"));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("flag")) {
            completions.addAll(Arrays.asList("true", "false", "allow", "deny"));
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}