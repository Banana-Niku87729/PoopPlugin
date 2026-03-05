package com.example.poopplugin;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * /join <ワールドコード> コマンド
 * ワールドコードを使って誰でも参加できる
 */
public class JoinCommand implements CommandExecutor, TabCompleter {

    private final PoopPlugin plugin;
    private final WorldManager wm;

    public JoinCommand(PoopPlugin plugin) {
        this.plugin = plugin;
        this.wm     = plugin.getWorldManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cプレイヤーのみ使用できます。");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§c使用法: /join <ワールドコード>");
            return true;
        }

        String code = args[0].toLowerCase();
        wm.joinWorld(player, code);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // ワールドコードは公開情報なのでタブ補完提供しない（セキュリティ上）
        return Collections.emptyList();
    }
}
