package com.example.poopplugin;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

/**
 * OwnWorld に関するイベントリスナー
 * - コマンド使用制限（Diarrheaランクワールド）
 * - プレイヤー移動のトラッキング（AntiAFK用）
 * - プレイヤー参加時のゲームモード適用・BANチェック
 * - プレイヤー退出時のスリープ候補チェック
 */
public class OwnWorldListener implements Listener {

    private final PoopPlugin plugin;
    private final WorldManager wm;

    public OwnWorldListener(PoopPlugin plugin) {
        this.plugin = plugin;
        this.wm     = plugin.getWorldManager();
    }

    // ─── コマンド制限 ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        WorldManager.OwnWorld ow = wm.getWorldByName(player.getWorld().getName());
        if (ow == null) return;

        WorldManager.TierConfig cfg = wm.getTierConfig(ow);
        if (cfg.allowCommands) return; // コマンド制限なし

        // オーナー・admin権限者は除外
        if (wm.hasPermission(player, ow, "admin")) return;
        if (player.hasPermission("poopplugin.world.admin")) return;

        // 許可コマンドリスト（最低限）
        String cmd = event.getMessage().split(" ")[0].toLowerCase().replace("/", "");
        if (cmd.equals("settings") || cmd.equals("join") || cmd.equals("own")) return;

        event.setCancelled(true);
        player.sendMessage("§cこのワールドではコマンドの使用が制限されています。");
    }

    // ─── 移動トラッキング ─────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        WorldManager.OwnWorld ow = wm.getWorldByName(player.getWorld().getName());
        if (ow == null) return;

        // 実際に移動した場合のみ更新（ブロック単位で判定）
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) return;

        wm.updatePlayerMove(player);
    }

    // ─── ワールド切替時の処理 ─────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World newWorld = player.getWorld();
        WorldManager.OwnWorld ow = wm.getWorldByName(newWorld.getName());
        if (ow == null) return;

        // BANチェック
        if (ow.bannedPlayers.contains(player.getUniqueId())) {
            String hub = plugin.getConfig().getString("hub-world", "world");
            World hubWorld = Bukkit.getWorld(hub);
            if (hubWorld != null) player.teleport(hubWorld.getSpawnLocation());
            player.sendMessage("§cあなたはこのワールドからBANされています。");
            return;
        }

        // ゲームモード適用
        player.setGameMode(ow.defaultGameMode);

        // 同接チェック
        WorldManager.TierConfig cfg = wm.getTierConfig(ow);
        int cur = newWorld.getPlayers().size();
        if (cur > cfg.maxConcurrent) {
            player.sendMessage("§cこのワールドは同接上限に達しています。");
            String hub = plugin.getConfig().getString("hub-world", "world");
            World hubWorld = Bukkit.getWorld(hub);
            if (hubWorld != null) player.teleport(hubWorld.getSpawnLocation());
            return;
        }
        if (cfg.maxJoin > 0 && cur > cfg.maxJoin) {
            player.sendMessage("§cこのワールドは参加人数の上限に達しています。");
            String hub = plugin.getConfig().getString("hub-world", "world");
            World hubWorld = Bukkit.getWorld(hub);
            if (hubWorld != null) player.teleport(hubWorld.getSpawnLocation());
        }
    }

    // ─── プレイヤー退出時 ─────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 移動データクリーンアップ（WorldManagerのafkマップは内部でやっているが念のため）
        // 特に何もしなくてよい（WorldManagerのスリープチェッカーが自動処理）
    }
}
