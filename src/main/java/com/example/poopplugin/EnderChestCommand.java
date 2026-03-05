package com.example.poopplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class EnderChestCommand implements CommandExecutor {

    private final PoopPlugin plugin;

    public EnderChestCommand(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ使用できます!");
            return true;
        }

        Player player = (Player) sender;
        String worldName = player.getWorld().getName().toLowerCase();

        // クリエイティブワールドでは実行不可
        if (worldName.equalsIgnoreCase("creative")) {
            player.sendMessage(ChatColor.RED + "クリエイティブワールドではエンダーチェストを開けません。");
            return true;
        }

        // Hardcoreワールド(hardcore1, hardcore2, hardcore3)では実行不可
        if (worldName.matches("hardcore[123]")) {
            player.sendMessage(ChatColor.RED + "Hardcoreワールドではエンダーチェストを開けません。");
            return true;
        }

        if (!player.hasPermission("poopplugin.enderchest")) {
            player.sendMessage(ChatColor.RED + "このコマンドを使用する権限がありません!");
            return true;
        }

        // 共有エンダーチェストを開く
        plugin.getEnderChestManager().openEnderChest(player);
        player.sendMessage(ChatColor.GREEN + "エンダーチェストを開きました!");

        return true;
    }
}