package com.example.poopplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class HardcoreCommand implements CommandExecutor, TabCompleter {

    private final PoopPlugin plugin;
    private final HardcoreManager hardcoreManager;

    public HardcoreCommand(PoopPlugin plugin) {
        this.plugin = plugin;
        this.hardcoreManager = plugin.getHardcoreManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add":
                return handleAdd(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            case "sethub":
                return handleSetHub(sender, args);
            case "reset":
                return handleReset(sender, args);
            case "check":
                return handleCheck(sender, args);
            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("poopplugin.hardcore.admin")) {
            sender.sendMessage("§c権限がありません。");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c使用方法: /hardcore add <ワールド名>");
            return true;
        }

        String worldName = args[1];

        if (Bukkit.getWorld(worldName) == null) {
            sender.sendMessage("§cワールド '" + worldName + "' が見つかりません。");
            return true;
        }

        if (hardcoreManager.addHardcoreWorld(worldName)) {
            sender.sendMessage("§a[ハードコア] §fワールド §e" + worldName + " §fをハードコアリストに追加しました。");
        } else {
            sender.sendMessage("§c[ハードコア] §fワールド §e" + worldName + " §fは既にハードコアリストに登録されています。");
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("poopplugin.hardcore.admin")) {
            sender.sendMessage("§c権限がありません。");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c使用方法: /hardcore remove <ワールド名>");
            return true;
        }

        String worldName = args[1];

        if (hardcoreManager.removeHardcoreWorld(worldName)) {
            sender.sendMessage("§a[ハードコア] §fワールド §e" + worldName + " §fをハードコアリストから削除しました。");
        } else {
            sender.sendMessage("§c[ハードコア] §fワールド §e" + worldName + " §fはハードコアリストに登録されていません。");
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        Set<String> worlds = hardcoreManager.getHardcoreWorlds();

        if (worlds.isEmpty()) {
            sender.sendMessage("§e[ハードコア] §7ハードコアワールドは登録されていません。");
            return true;
        }

        sender.sendMessage("§e[ハードコア] §7登録されているハードコアワールド:");
        for (String world : worlds) {
            sender.sendMessage("§7 - §f" + world);
        }
        sender.sendMessage("§7Hubワールド: §f" + hardcoreManager.getHubWorldName());
        return true;
    }

    private boolean handleSetHub(CommandSender sender, String[] args) {
        if (!sender.hasPermission("poopplugin.hardcore.admin")) {
            sender.sendMessage("§c権限がありません。");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c使用方法: /hardcore sethub <ワールド名>");
            return true;
        }

        String worldName = args[1];

        if (Bukkit.getWorld(worldName) == null) {
            sender.sendMessage("§cワールド '" + worldName + "' が見つかりません。");
            return true;
        }

        hardcoreManager.setHubWorld(worldName);
        sender.sendMessage("§a[ハードコア] §fHubワールドを §e" + worldName + " §fに設定しました。");
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("poopplugin.hardcore.admin")) {
            sender.sendMessage("§c権限がありません。");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c使用方法: /hardcore reset <プレイヤー名> [ワールド名]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        UUID targetId;

        if (target != null) {
            targetId = target.getUniqueId();
        } else {
            sender.sendMessage("§cプレイヤー '" + args[1] + "' が見つかりません。");
            return true;
        }

        if (args.length >= 3) {
            // 特定のワールドのみリセット
            String worldName = args[2];
            hardcoreManager.resetPlayerDeathInWorld(targetId, worldName);
            sender.sendMessage("§a[ハードコア] §f" + args[1] + " §7の §f" + worldName + " §7での死亡記録をリセットしました。");
        } else {
            // 全てのワールドをリセット
            hardcoreManager.resetAllPlayerDeaths(targetId);
            sender.sendMessage("§a[ハードコア] §f" + args[1] + " §7の全ての死亡記録をリセットしました。");
        }
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        Player target;

        if (args.length >= 2) {
            if (!sender.hasPermission("poopplugin.hardcore.admin")) {
                sender.sendMessage("§c他のプレイヤーを確認する権限がありません。");
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cプレイヤー '" + args[1] + "' が見つかりません。");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cコンソールからはプレイヤー名を指定してください。");
                return true;
            }
            target = (Player) sender;
        }

        Set<String> deaths = hardcoreManager.getPlayerDeaths(target.getUniqueId());

        if (deaths.isEmpty()) {
            sender.sendMessage("§e[ハードコア] §f" + target.getName() + " §7はどのハードコアワールドでも死亡していません。");
        } else {
            sender.sendMessage("§e[ハードコア] §f" + target.getName() + " §7の死亡記録:");
            for (String world : deaths) {
                sender.sendMessage("§7 - §c" + world);
            }
        }
        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§e§l[ハードコアシステム] §7コマンド一覧");
        sender.sendMessage("§7/hardcore add <ワールド名> §f- ハードコアワールドを追加");
        sender.sendMessage("§7/hardcore remove <ワールド名> §f- ハードコアワールドを削除");
        sender.sendMessage("§7/hardcore list §f- ハードコアワールド一覧を表示");
        sender.sendMessage("§7/hardcore sethub <ワールド名> §f- Hubワールドを設定");
        sender.sendMessage("§7/hardcore reset <プレイヤー> [ワールド] §f- 死亡記録をリセット");
        sender.sendMessage("§7/hardcore check [プレイヤー] §f- 死亡記録を確認");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("add", "remove", "list", "sethub", "reset", "check"));
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "add":
                case "remove":
                case "sethub":
                    return Bukkit.getWorlds().stream()
                            .map(org.bukkit.World::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "reset":
                case "check":
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            return hardcoreManager.getHardcoreWorlds().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}