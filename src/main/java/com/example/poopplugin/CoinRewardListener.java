package com.example.poopplugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

public class CoinRewardListener implements Listener {

    private final PoopPlugin plugin;

    // 通知に使う色コード
    private static final String COLOR_GAIN  = "§6";  // 金色（獲得）
    private static final String COLOR_LOSE  = "§c";  // 赤色（減算）
    private static final String COLOR_LABEL = "§e";  // 黄色（Coin文字）

    public CoinRewardListener(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    // 敵を倒したら +10 Coin
    // =========================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        // 倒したのがプレイヤー、かつ倒された対象がプレイヤー以外
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity().getType() == EntityType.PLAYER) return;

        WatermarkManager wm = plugin.getWatermarkManager();
        int reward = 10;
        wm.addCoins(killer, reward);

        sendCoinNotification(killer, "+" + reward, "敵を倒した");
    }

    // =========================================================
    // 実績を解除したら +100 Coin
    // =========================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Advancement advancement = event.getAdvancement();

        // レシピ解除・ルートなど通知が出ない実績を除外
        // 表示名がないもの（内部用）はスキップ
        String key = advancement.getKey().getKey();
        if (key.startsWith("recipes/") || key.startsWith("root")) return;

        WatermarkManager wm = plugin.getWatermarkManager();
        int reward = 100;
        wm.addCoins(player, reward);

        sendCoinNotification(player, "+" + reward, "実績解除");
    }

    // =========================================================
    // 食べ物を食べたら +1 Coin
    // =========================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerEat(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        // 食べ物かどうかチェック（薬ビンなど除外したい場合も対応しやすくするため type チェック）
        if (!item.getType().isEdible()) return;

        // 薬ビン（POTION）は飲み物なので除外
        if (item.getType() == Material.POTION) return;

        Player player = event.getPlayer();
        WatermarkManager wm = plugin.getWatermarkManager();
        int reward = 1;
        wm.addCoins(player, reward);

        sendCoinNotification(player, "+" + reward, "食事");
    }

    // =========================================================
    // 不死のトーテムが発動したら +500 Coin
    // =========================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemUse(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        WatermarkManager wm = plugin.getWatermarkManager();
        int reward = 500;
        wm.addCoins(player, reward);

        sendCoinNotification(player, "+" + reward, "不死のトーテム発動");
    }

    // =========================================================
    // 死亡したら -100 Coin
    // =========================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        WatermarkManager wm = plugin.getWatermarkManager();

        int penalty = 100;
        int current = wm.getCoins(player);

        if (current >= penalty) {
            wm.removeCoins(player, penalty);
            // 死亡後にリスポーンしてから通知（リスポーン前は画面が暗い）
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                sendCoinLossNotification(player, penalty, "死亡");
            }, 20L); // 1秒後（リスポーン後）
        } else {
            // 残高が足りない場合は0にする
            int actual = current; // 実際に減った量
            wm.setCoins(player, 0);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                sendCoinLossNotification(player, actual, "死亡（残高不足のため全額）");
            }, 20L);
        }
    }

    // =========================================================
    // 通知メソッド
    // =========================================================

    /**
     * Coin獲得通知をアクションバー＋チャットで送信
     */
    private void sendCoinNotification(Player player, String changeStr, String reason) {
        WatermarkManager wm = plugin.getWatermarkManager();
        int total = wm.getCoins(player);

        // アクションバー
        String actionBarMsg = COLOR_GAIN + "✦ Coin " + changeStr
                + COLOR_LABEL + "  (" + reason + ")  §7合計: " + COLOR_LABEL + total + " Coin";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(actionBarMsg));

        // チャット
        player.sendMessage(COLOR_GAIN + "[Coin] " + changeStr + " Coin §7(" + reason + ")"
                + "  §7合計: " + COLOR_LABEL + total + " Coin");
    }

    /**
     * Coin減算通知をアクションバー＋チャットで送信
     */
    private void sendCoinLossNotification(Player player, int amount, String reason) {
        // プレイヤーがオンラインでない場合（ログアウト等）は無視
        if (!player.isOnline()) return;

        WatermarkManager wm = plugin.getWatermarkManager();
        int total = wm.getCoins(player);

        // アクションバー
        String actionBarMsg = COLOR_LOSE + "▼ Coin -" + amount
                + COLOR_LABEL + "  (" + reason + ")  §7合計: " + COLOR_LABEL + total + " Coin";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(actionBarMsg));

        // チャット
        player.sendMessage(COLOR_LOSE + "[Coin] -" + amount + " Coin §7(" + reason + ")"
                + "  §7合計: " + COLOR_LABEL + total + " Coin");
    }
}