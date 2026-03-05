package com.example.poopplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CoinCommand implements CommandExecutor, TabCompleter {

    private final PoopPlugin plugin;

    public CoinCommand(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        WatermarkManager manager = plugin.getWatermarkManager();

        if (args.length == 0) {
            // 自分のCoin残高を表示
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
                return true;
            }
            Player player = (Player) sender;
            int coins = manager.getCoins(player);
            sender.sendMessage("§6あなたのCoin残高: §e" + coins + " Coin");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add":
                return handleAdd(sender, args, manager);

            case "remove":
                return handleRemove(sender, args, manager);

            case "set":
                return handleSet(sender, args, manager);

            case "give":
                return handleGive(sender, args, manager);

            case "take":
                return handleTake(sender, args, manager);

            case "check":
                return handleCheck(sender, args, manager);

            default:
                sender.sendMessage("§c無効なサブコマンドです。");
                sender.sendMessage("§7使用方法:");
                sender.sendMessage("§7  /coin - 自分の残高を確認");
                sender.sendMessage("§7  /coin add <プレイヤー> <金額> - Coinを追加");
                sender.sendMessage("§7  /coin remove <プレイヤー> <金額> - Coinを削除");
                sender.sendMessage("§7  /coin set <プレイヤー> <金額> - Coinを設定");
                sender.sendMessage("§7  /coin give <プレイヤー> <金額> - 自分のCoinを送る");
                sender.sendMessage("§7  /coin take <プレイヤー> <金額> - 相手からCoinを受け取る");
                sender.sendMessage("§7  /coin check <プレイヤー> - 他プレイヤーの残高を確認");
                return true;
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args, WatermarkManager manager) {
        if (!sender.hasPermission("poopplugin.coin.admin")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /coin add <プレイヤー> <金額>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cプレイヤーが見つかりません。");
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage("§c金額は1以上の数値を指定してください。");
                return true;
            }

            manager.addCoins(target, amount);
            sender.sendMessage("§a" + target.getName() + " に §e" + amount + " Coin §aを追加しました。");
            target.sendMessage("§a" + amount + " Coin §aが追加されました! 現在の残高: §e" + manager.getCoins(target) + " Coin");
            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage("§c金額は数値で指定してください。");
            return true;
        }
    }

    private boolean handleRemove(CommandSender sender, String[] args, WatermarkManager manager) {
        if (!sender.hasPermission("poopplugin.coin.admin")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /coin remove <プレイヤー> <金額>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cプレイヤーが見つかりません。");
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage("§c金額は1以上の数値を指定してください。");
                return true;
            }

            if (manager.removeCoins(target, amount)) {
                sender.sendMessage("§a" + target.getName() + " から §e" + amount + " Coin §aを削除しました。");
                target.sendMessage("§c" + amount + " Coin §aが削除されました! 現在の残高: §e" + manager.getCoins(target) + " Coin");
            } else {
                sender.sendMessage("§c残高が不足しています。");
            }
            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage("§c金額は数値で指定してください。");
            return true;
        }
    }

    private boolean handleSet(CommandSender sender, String[] args, WatermarkManager manager) {
        if (!sender.hasPermission("poopplugin.coin.admin")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /coin set <プレイヤー> <金額>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cプレイヤーが見つかりません。");
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            if (amount < 0) {
                sender.sendMessage("§c金額は0以上の数値を指定してください。");
                return true;
            }

            manager.setCoins(target, amount);
            sender.sendMessage("§a" + target.getName() + " のCoinを §e" + amount + " Coin §aに設定しました。");
            target.sendMessage("§aあなたのCoin残高が §e" + amount + " Coin §aに設定されました!");
            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage("§c金額は数値で指定してください。");
            return true;
        }
    }

    private boolean handleGive(CommandSender sender, String[] args, WatermarkManager manager) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /coin give <プレイヤー> <金額>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cプレイヤーが見つかりません。");
            return true;
        }

        if (target.equals(player)) {
            sender.sendMessage("§c自分自身にCoinを送ることはできません。");
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage("§c金額は1以上の数値を指定してください。");
                return true;
            }

            if (!manager.hasCoins(player, amount)) {
                sender.sendMessage("§c残高が不足しています。現在の残高: §e" + manager.getCoins(player) + " Coin");
                return true;
            }

            manager.removeCoins(player, amount);
            manager.addCoins(target, amount);

            player.sendMessage("§a" + target.getName() + " に §e" + amount + " Coin §aを送りました! 残高: §e" + manager.getCoins(player) + " Coin");
            target.sendMessage("§a" + player.getName() + " から §e" + amount + " Coin §aを受け取りました! 残高: §e" + manager.getCoins(target) + " Coin");
            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage("§c金額は数値で指定してください。");
            return true;
        }
    }

    private boolean handleTake(CommandSender sender, String[] args, WatermarkManager manager) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /coin take <プレイヤー> <金額>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cプレイヤーが見つかりません。");
            return true;
        }

        if (target.equals(player)) {
            sender.sendMessage("§c自分自身からCoinを受け取ることはできません。");
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage("§c金額は1以上の数値を指定してください。");
                return true;
            }

            if (!manager.hasCoins(target, amount)) {
                sender.sendMessage("§c" + target.getName() + " の残高が不足しています。");
                return true;
            }

            manager.removeCoins(target, amount);
            manager.addCoins(player, amount);

            player.sendMessage("§a" + target.getName() + " から §e" + amount + " Coin §aを受け取りました! 残高: §e" + manager.getCoins(player) + " Coin");
            target.sendMessage("§c" + player.getName() + " に §e" + amount + " Coin §cを取られました! 残高: §e" + manager.getCoins(target) + " Coin");
            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage("§c金額は数値で指定してください。");
            return true;
        }
    }

    private boolean handleCheck(CommandSender sender, String[] args, WatermarkManager manager) {
        if (args.length < 2) {
            sender.sendMessage("§c使用方法: /coin check <プレイヤー>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cプレイヤーが見つかりません。");
            return true;
        }

        int coins = manager.getCoins(target);
        sender.sendMessage("§6" + target.getName() + " のCoin残高: §e" + coins + " Coin");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("add", "remove", "set", "give", "take", "check"));
        } else if (args.length == 2) {
            // プレイヤー名の補完
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));
        } else if (args.length == 3) {
            completions.addAll(Arrays.asList("10", "100", "1000", "10000"));
        }

        return completions;
    }
}