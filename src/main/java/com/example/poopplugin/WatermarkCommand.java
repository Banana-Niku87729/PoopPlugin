package com.example.poopplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WatermarkCommand implements CommandExecutor, TabCompleter {

    private final PoopPlugin plugin;

    public WatermarkCommand(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        Player player = (Player) sender;
        WatermarkManager manager = plugin.getWatermarkManager();

        if (args.length == 0) {
            // 現在の設定を表示
            String currentMode = manager.getWatermarkMode(player);
            player.sendMessage("§a現在のウォーターマーク設定: §e" + currentMode);
            player.sendMessage("§7使用方法: /watermark <1|2|3|4> [カスタムテキスト]");
            return true;
        }

        String mode = args[0];

        switch (mode) {
            case "0":
                manager.setWatermarkMode(player, "default");
                player.sendMessage("§aウォーターマークを §eデフォルト §aに設定しました。");
                break;

            case "1":
                manager.setWatermarkMode(player, "1");
                player.sendMessage("§aウォーターマークを §eCoin表示 §aに設定しました。");
                break;

            case "2":
                manager.setWatermarkMode(player, "2");
                player.sendMessage("§aウォーターマークを §e時刻表示 §aに設定しました。");
                break;

            case "3":
                manager.setWatermarkMode(player, "3");
                player.sendMessage("§aウォーターマークを §eプレイヤー名 §aに設定しました。");
                break;

            case "4":
                if (args.length < 2) {
                    player.sendMessage("§cカスタムテキストを指定してください。");
                    player.sendMessage("§7例: /watermark 4 Poop is God.");
                    return true;
                }
                // カスタムテキストを結合
                StringBuilder customText = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    if (i > 1) customText.append(" ");
                    customText.append(args[i]);
                }
                manager.setCustomWatermark(player, customText.toString());
                player.sendMessage("§aカスタムウォーターマークを設定しました: §e" + customText.toString());
                break;

            default:
                player.sendMessage("§c無効なモードです。1, 2, 3, または 4 を指定してください。");
                return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("1", "2", "3", "4"));
        } else if (args.length == 2 && args[0].equals("4")) {
            completions.add("Poop");
            completions.add("is");
            completions.add("God.");
        }

        return completions;
    }
}