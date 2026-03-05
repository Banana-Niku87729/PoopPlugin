package com.example.poopplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

public class CPSKickListener implements Listener {

    private final PoopPlugin plugin;
    private final Map<UUID, Queue<Long>> leftClickHistory = new HashMap<>();
    private final Map<UUID, Queue<Long>> rightClickHistory = new HashMap<>();
    private final int maxCPS = 30; // 最大CPS
    private final long timeWindow = 1000; // 1秒(ミリ秒)

    public CPSKickListener(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        Action action = event.getAction();

        // 左クリック(攻撃)のみをカウント
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            checkAndKick(player, playerId, currentTime, leftClickHistory, "左クリック");
        }
        // 右クリック(使用)のみをカウント
        else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            checkAndKick(player, playerId, currentTime, rightClickHistory, "右クリック");
        }
    }

    private void checkAndKick(Player player, UUID playerId, long currentTime,
                              Map<UUID, Queue<Long>> clickHistory, String clickType) {
        // プレイヤーのクリック履歴を取得または作成
        Queue<Long> clicks = clickHistory.computeIfAbsent(playerId, k -> new LinkedList<>());

        // 現在のクリックを追加
        clicks.add(currentTime);

        // 1秒より古いクリックを削除
        while (!clicks.isEmpty() && currentTime - clicks.peek() > timeWindow) {
            clicks.poll();
        }

        // CPSをチェック
        int currentCPS = clicks.size();

        if (currentCPS > maxCPS) {
            // キック処理
            player.kickPlayer("§c自動クリックが検出されました。\n§c1秒間に" + maxCPS + "クリック以上は許可されていません。\n§7現在のCPS(" + clickType + "): " + currentCPS);

            // ログに記録
            plugin.getLogger().warning(player.getName() + " がCPS制限違反でキックされました (" + clickType + " CPS: " + currentCPS + ")");

            // クリック履歴をクリア
            leftClickHistory.remove(playerId);
            rightClickHistory.remove(playerId);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // プレイヤーが退出したらクリック履歴を削除(メモリ節約)
        UUID playerId = event.getPlayer().getUniqueId();
        leftClickHistory.remove(playerId);
        rightClickHistory.remove(playerId);
    }

    /**
     * 全てのクリック履歴をクリア
     */
    public void clearAllHistory() {
        leftClickHistory.clear();
        rightClickHistory.clear();
    }
}