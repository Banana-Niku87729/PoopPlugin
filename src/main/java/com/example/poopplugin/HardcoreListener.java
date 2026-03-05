package com.example.poopplugin;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class HardcoreListener implements Listener {

    private final PoopPlugin plugin;
    private final HardcoreManager hardcoreManager;

    public HardcoreListener(PoopPlugin plugin) {
        this.plugin = plugin;
        this.hardcoreManager = plugin.getHardcoreManager();
    }

    /**
     * プレイヤーがワールドを変更したとき
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String newWorldName = player.getWorld().getName();

        // ハードコアワールドで死亡済みの場合、Hubに戻す
        if (hardcoreManager.isHardcoreWorld(newWorldName) &&
                hardcoreManager.hasPlayerDiedInWorld(player.getUniqueId(), newWorldName)) {

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    hardcoreManager.teleportToHub(player);
                    player.sendMessage("§c[ハードコア] §7このワールドでは既に死亡しているため、Hubに戻されました。");
                }
            }, 5L);
            return;
        }

        // ハードコアハートの表示を更新（少し遅延させて適用）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                hardcoreManager.updateHardcoreHearts(player);

                // ハードコアモードの通知
                if (hardcoreManager.isHardcoreWorld(newWorldName) &&
                        !hardcoreManager.hasPlayerDiedInWorld(player.getUniqueId(), newWorldName)) {
                    player.sendMessage("§c§l[ハードコアモード]");
                    player.sendMessage("§7このワールドで死亡すると、二度と入れなくなります!");
                }
            }
        }, 10L);
    }

    /**
     * プレイヤーがサーバーに参加したとき
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String worldName = player.getWorld().getName();

            // ハードコアワールドで死亡済みの場合、Hubに戻す
            if (hardcoreManager.isHardcoreWorld(worldName) &&
                    hardcoreManager.hasPlayerDiedInWorld(player.getUniqueId(), worldName)) {

                hardcoreManager.teleportToHub(player);
                player.sendMessage("§c[ハードコア] §7このワールドでは既に死亡しているため、Hubに移動しました。");
            } else {
                // ハードコアハートの表示を更新
                hardcoreManager.updateHardcoreHearts(player);

                // ハードコアモードの通知
                if (hardcoreManager.isHardcoreWorld(worldName)) {
                    player.sendMessage("§c§l[ハードコアモード] §7死亡すると二度とこのワールドに入れません!");
                }
            }
        }, 20L);
    }

    /**
     * プレイヤーが死亡したとき
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String worldName = player.getWorld().getName();

        // ハードコアワールドでの死亡の場合
        if (hardcoreManager.isHardcoreWorld(worldName)) {
            // 死亡を記録
            hardcoreManager.recordPlayerDeath(player.getUniqueId(), worldName);

            // 死亡メッセージをカスタマイズ
            String deathMessage = event.getDeathMessage();
            if (deathMessage != null) {
                event.setDeathMessage("§c[ハードコア] " + deathMessage);
            }

            // プレイヤーへのメッセージ
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage("§c§l╔════════════════════════════╗");
                    player.sendMessage("§c§l║   ハードコアモード - 死亡   ║");
                    player.sendMessage("§c§l╚════════════════════════════╝");
                    player.sendMessage("");
                    player.sendMessage("§7あなたは §f" + worldName + " §7で死亡しました。");
                    player.sendMessage("§7このワールドには §c二度と入れません§7。");
                    player.sendMessage("");
                }
            }, 10L);

            plugin.getLogger().info(player.getName() + " がハードコアワールド '" + worldName + "' で死亡しました。");
        }
    }

    /**
     * プレイヤーがリスポーンするとき
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String deathWorldName = player.getWorld().getName();

        // ハードコアワールドで死亡した場合、Hubワールドにリスポーン
        if (hardcoreManager.isHardcoreWorld(deathWorldName)) {
            org.bukkit.World hubWorld = Bukkit.getWorld(hardcoreManager.getHubWorldName());

            if (hubWorld != null) {
                event.setRespawnLocation(hubWorld.getSpawnLocation());

                // リスポーン後にメッセージを送信とハート表示を更新
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage("§e[ハードコア] §7Hubワールドにリスポーンしました。");
                        player.setGameMode(GameMode.SURVIVAL);

                        // 通常のハートに戻す
                        hardcoreManager.updateHardcoreHearts(player);
                    }
                }, 10L);
            }
        }
    }
}