package com.example.poopplugin;

import net.md_5.bungee.api.chat.*;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.Map;

/**
 * /rank_buy コマンド
 *
 * 実行すると購入用メールアドレスを発行し、購入手順を表示する。
 * メールアドレスのユーザー名部分はクリックでコピー可能（Java版のみ）。
 */
public class RankBuyCommand implements CommandExecutor {

    private final PoopPlugin plugin;

    public RankBuyCommand(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ使用できます。");
            return true;
        }

        Player player = (Player) sender;
        RankManager rankManager = plugin.getRankManager();

        // セッションを作成（または更新）し、メールアドレスを取得
        String email = rankManager.createOrRefreshSession(player);

        // ── メッセージ送信 ───────────────────────────────────────
        player.sendMessage("");
        player.sendMessage("§6§l■ランク購入のご検討ありがとうございます");
        player.sendMessage("");

        // 購入リンク（クリックで開く）
        TextComponent linkLine = new TextComponent("§f購入リンク: ");
        TextComponent link = new TextComponent("§b§nhttps://ko-fi.com/poopmc");
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://ko-fi.com/poopmc"));
        link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§7クリックして購入ページを開く").create()));
        linkLine.addExtra(link);
        player.spigot().sendMessage(linkLine);

        player.sendMessage("");
        player.sendMessage("§f購入する際はメールアドレス入力フォームにて");

        // メールアドレス（クリックでコピー）
        TextComponent emailLine = new TextComponent("§e§l" + email);
        emailLine.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, email));
        emailLine.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§7クリックしてコピー").create()));
        player.spigot().sendMessage(emailLine);

        player.sendMessage("§fを入力してください。");
        player.sendMessage("");
        player.sendMessage("§c正しく入力できていない場合、アカウントにランクが反映されません。");
        player.sendMessage("§7※Javaユーザーの場合はメールアドレス部分をクリックすることでコピーできます。");
        player.sendMessage("§e15分以内に決済を完了するようお願いします。");
        player.sendMessage("");
        player.sendMessage("§7また、表示されているメールアドレスは購入用です、購入後は破棄される上、");
        player.sendMessage("§7届いたメールもシステムによって即削除されます。");
        player.sendMessage("");

        // 利用可能なランク一覧を表示
        player.sendMessage("§6§l【購入可能なランク】");
        for (Map.Entry<String, String> entry : RankManager.getRankProducts().entrySet()) {
            player.sendMessage("§7  商品ID: §f" + entry.getKey() + " §7→ ランク: §a" + entry.getValue());
        }
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);

        return true;
    }
}
