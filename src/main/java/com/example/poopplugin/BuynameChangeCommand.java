package com.example.poopplugin;

import net.md_5.bungee.api.chat.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/**
 * /buyname_change <ユーザー名> コマンド
 *
 * 購入用メールアドレスのユーザー名部分を変更する。
 * 既存セッションがあれば古い名前を破棄し、新しいメールアドレスを発行する。
 * 新規セッションの有効期限も15分。
 */
public class BuynameChangeCommand implements CommandExecutor {

    private final PoopPlugin plugin;

    public BuynameChangeCommand(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ使用できます。");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage("§c使用法: /buyname_change <ユーザー名>");
            return true;
        }

        String newUsername = args[0];

        // ユーザー名のバリデーション（英数字・アンダースコアのみ許可）
        if (!newUsername.matches("[a-zA-Z0-9_]{1,32}")) {
            player.sendMessage("§cユーザー名には英数字とアンダースコアのみ使用できます（最大32文字）。");
            return true;
        }

        RankManager rankManager = plugin.getRankManager();
        String newEmail = rankManager.changeSessionName(player, newUsername);

        // ── メッセージ送信 ───────────────────────────────────────
        player.sendMessage("");
        player.sendMessage("§a§l■購入用ユーザー名を変更しました。");
        player.sendMessage("§7すでに /rank_buy を実行していた場合、過去のユーザー名は破棄されました。");
        player.sendMessage("");
        player.sendMessage("§f新しい購入用メールアドレス:");

        // メールアドレス（クリックでコピー）
        TextComponent emailLine = new TextComponent("§e§l" + newEmail);
        emailLine.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, newEmail));
        emailLine.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§7クリックしてコピー").create()));
        player.spigot().sendMessage(emailLine);

        player.sendMessage("");
        player.sendMessage("§7このメールアドレスは §e15分間 §7有効です。");
        player.sendMessage("§c期限切れの場合は再度 /rank_buy を実行してください。");
        player.sendMessage("");

        return true;
    }
}
