package com.example.poopplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ToggleCommand implements CommandExecutor, TabCompleter {

    private final PoopPlugin plugin;

    public ToggleCommand(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage("§c使用方法: /toggle <viewplatform|denyplatformicon> <true|false>");
            return true;
        }

        String option = args[0].toLowerCase();
        String value = args[1].toLowerCase();

        if (!value.equals("true") && !value.equals("false")) {
            sender.sendMessage("§c値は 'true' または 'false' を指定してください。");
            return true;
        }

        boolean enabled = value.equals("true");

        switch (option) {
            case "viewplatform":
                plugin.getConfigManager().setViewPlatform(player, enabled);
                if (enabled) {
                    sender.sendMessage("§aプラットフォームアイコンの表示を有効にしました。");
                } else {
                    sender.sendMessage("§cプラットフォームアイコンの表示を無効にしました。");
                }
                // 表示を更新
                plugin.getPlatformListener().updateAllPlayerDisplayNames();
                break;

            case "denyplatformicon":
                plugin.getConfigManager().setDenyPlatformIcon(player, enabled);
                if (enabled) {
                    sender.sendMessage("§c他のプレイヤーからあなたのプラットフォームアイコンが見えなくなりました。");
                } else {
                    sender.sendMessage("§a他のプレイヤーからあなたのプラットフォームアイコンが見えるようになりました。");
                }
                // 表示を更新
                plugin.getPlatformListener().updateAllPlayerDisplayNames();
                break;

            default:
                sender.sendMessage("§c不明なオプション: " + option);
                sender.sendMessage("§e使用可能なオプション: viewplatform, denyplatformicon");
                return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("viewplatform", "denyplatformicon"));
        } else if (args.length == 2) {
            completions.addAll(Arrays.asList("true", "false"));
        }

        // 入力に基づいてフィルタリング
        List<String> result = new ArrayList<>();
        String input = args[args.length - 1].toLowerCase();
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(input)) {
                result.add(completion);
            }
        }

        return result;
    }
}