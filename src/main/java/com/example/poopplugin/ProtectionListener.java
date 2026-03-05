package com.example.poopplugin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 保護エリアのイベントリスナー
 */
public class ProtectionListener implements Listener {

    private final PoopPlugin plugin;
    private final Map<UUID, Long> lastMessageTime;
    private final Map<UUID, String> playerRegions; // プレイヤーが現在いる保護エリア
    private static final long MESSAGE_COOLDOWN = 3000; // 3秒

    public ProtectionListener(PoopPlugin plugin) {
        this.plugin = plugin;
        this.lastMessageTime = new HashMap<>();
        this.playerRegions = new HashMap<>();
    }

    /**
     * ピストンによるブロック移動イベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        ProtectionManager manager = plugin.getProtectionManager();

        for (Block block : event.getBlocks()) {
            List<ProtectedRegion> regions = manager.getRegionsAt(block.getLocation());
            if (!regions.isEmpty()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        ProtectionManager manager = plugin.getProtectionManager();

        for (Block block : event.getBlocks()) {
            List<ProtectedRegion> regions = manager.getRegionsAt(block.getLocation());
            if (!regions.isEmpty()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * ブロック破壊イベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        ProtectionManager manager = plugin.getProtectionManager();

        if (!manager.canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            sendCooldownMessage(player, ChatColor.RED + "この保護エリアでブロックを破壊できません");
        }
    }

    /**
     * ブロック設置イベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        ProtectionManager manager = plugin.getProtectionManager();

        if (!manager.canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            sendCooldownMessage(player, ChatColor.RED + "この保護エリアでブロックを設置できません");
        }
    }

    /**
     * プレイヤーの相互作用イベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        ProtectionManager manager = plugin.getProtectionManager();

        if (!manager.canInteract(player, block.getLocation())) {
            event.setCancelled(true);
            sendCooldownMessage(player, ChatColor.RED + "この保護エリアで相互作用できません");
        }
    }

    /**
     * バケツの使用イベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        ProtectionManager manager = plugin.getProtectionManager();

        if (!manager.canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            sendCooldownMessage(player, ChatColor.RED + "この保護エリアでバケツを使用できません");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        ProtectionManager manager = plugin.getProtectionManager();

        if (!manager.canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            sendCooldownMessage(player, ChatColor.RED + "この保護エリアでバケツを使用できません");
        }
    }

    /**
     * 額縁などの破壊イベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getRemover();
        ProtectionManager manager = plugin.getProtectionManager();

        if (!manager.canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            sendCooldownMessage(player, ChatColor.RED + "この保護エリアで額縁を破壊できません");
        }
    }

    /**
     * 爆発イベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        ProtectionManager manager = plugin.getProtectionManager();

        event.blockList().removeIf(block -> {
            List<ProtectedRegion> regions = manager.getRegionsAt(block.getLocation());
            if (regions.isEmpty()) {
                return false;
            }

            ProtectedRegion topRegion = regions.get(0);
            return !topRegion.getFlag(ProtectedRegion.FLAG_EXPLOSION);
        });
    }

    /**
     * 炎の延焼イベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType().toString().contains("FIRE")) {
            ProtectionManager manager = plugin.getProtectionManager();
            List<ProtectedRegion> regions = manager.getRegionsAt(event.getBlock().getLocation());

            if (!regions.isEmpty()) {
                ProtectedRegion topRegion = regions.get(0);
                if (!topRegion.getFlag(ProtectedRegion.FLAG_FIRE_SPREAD)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * 炎の着火イベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) {
            ProtectionManager manager = plugin.getProtectionManager();
            List<ProtectedRegion> regions = manager.getRegionsAt(event.getBlock().getLocation());

            if (!regions.isEmpty()) {
                ProtectedRegion topRegion = regions.get(0);
                if (!topRegion.getFlag(ProtectedRegion.FLAG_FIRE_SPREAD)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * モブスポーンイベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL ||
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {

            ProtectionManager manager = plugin.getProtectionManager();
            List<ProtectedRegion> regions = manager.getRegionsAt(event.getLocation());

            if (!regions.isEmpty()) {
                ProtectedRegion topRegion = regions.get(0);
                if (!topRegion.getFlag(ProtectedRegion.FLAG_MOB_SPAWN)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * PVPイベント
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player damaged = (Player) event.getEntity();
        Player damager = null;

        if (event.getDamager() instanceof Player) {
            damager = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile projectile = (org.bukkit.entity.Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                damager = (Player) projectile.getShooter();
            }
        }

        if (damager == null) {
            return;
        }

        ProtectionManager manager = plugin.getProtectionManager();
        List<ProtectedRegion> regions = manager.getRegionsAt(damaged.getLocation());

        if (!regions.isEmpty()) {
            ProtectedRegion topRegion = regions.get(0);
            if (!topRegion.getFlag(ProtectedRegion.FLAG_PVP)) {
                event.setCancelled(true);
                sendCooldownMessage(damager, ChatColor.RED + "この保護エリアではPVPが無効です");
            }
        }
    }

    /**
     * プレイヤー移動イベント（greeting/farewellメッセージ用）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        ProtectionManager manager = plugin.getProtectionManager();

        List<ProtectedRegion> fromRegions = manager.getRegionsAt(from);
        List<ProtectedRegion> toRegions = manager.getRegionsAt(to);

        // エリアから出た場合
        if (!fromRegions.isEmpty() && !isInSameRegion(fromRegions, toRegions)) {
            // Farewellメッセージがあれば表示
            // 将来的に実装予定
        }

        // エリアに入った場合
        if (!toRegions.isEmpty() && !isInSameRegion(fromRegions, toRegions)) {
            // Greetingメッセージがあれば表示
            // 将来的に実装予定
        }
    }

    /**
     * 同じ保護エリア内にいるかチェック
     */
    private boolean isInSameRegion(List<ProtectedRegion> regions1, List<ProtectedRegion> regions2) {
        if (regions1.isEmpty() || regions2.isEmpty()) {
            return regions1.isEmpty() && regions2.isEmpty();
        }

        return regions1.get(0).getName().equals(regions2.get(0).getName());
    }

    /**
     * クールダウン付きでメッセージを送信
     */
    private void sendCooldownMessage(Player player, String message) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (!lastMessageTime.containsKey(uuid) ||
                currentTime - lastMessageTime.get(uuid) > MESSAGE_COOLDOWN) {

            player.sendMessage(message);
            lastMessageTime.put(uuid, currentTime);
        }
    }
}