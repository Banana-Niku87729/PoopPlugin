package com.example.poopplugin;

import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

/**
 * プレイヤーログイン時に保留ランクがあれば付与するリスナー。
 */
public class RankLoginListener implements Listener {

    private final PoopPlugin plugin;

    public RankLoginListener(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getRankManager().applyPendingRank(player);
    }
}
