package com.example.poopplugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class WatermarkListener implements Listener {

    private final PoopPlugin plugin;

    public WatermarkListener(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // プレイヤーが参加したらデータをロード
        plugin.getWatermarkManager().loadData(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // プレイヤーが退出したらデータをクリーンアップ (メモリ解放)
        // ※ 保存は設定時に行われているため、ここで特別な保存処理は不要だが、念の為確認しても良い
        // 今回の修正で、set時に保存しているので、ここはメモリ解放のみでOK
        plugin.getWatermarkManager().cleanup(event.getPlayer());
    }
}