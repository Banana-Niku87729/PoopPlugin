package com.example.poopplugin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 範囲選択の杖のイベントリスナー
 */
public class WandListener implements Listener {

    private final PoopPlugin plugin;
    private final Map<UUID, Location> pos1Selections;
    private final Map<UUID, Location> pos2Selections;
    private final SelectionVisualizer visualizer;

    public WandListener(PoopPlugin plugin, Map<UUID, Location> pos1, Map<UUID, Location> pos2) {
        this.plugin = plugin;
        this.pos1Selections = pos1;
        this.pos2Selections = pos2;
        this.visualizer = new SelectionVisualizer(plugin);
    }

    /**
     * 杖での相互作用イベント
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // オフハンドでの実行を防ぐ
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        // 杖を持っているかチェック
        if (!SelectionWand.isWand(item)) {
            return;
        }

        // 杖を使っている場合は他のイベントをキャンセル
        event.setCancelled(true);

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Location blockLoc = clickedBlock.getLocation();
        UUID uuid = player.getUniqueId();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // 左クリック: 位置1を設定
            pos1Selections.put(uuid, blockLoc);
            player.sendMessage(ChatColor.AQUA + "位置1を設定しました: " +
                    ChatColor.WHITE + formatLocation(blockLoc));

            // パーティクルで視覚化
            visualizer.showPoint(player, blockLoc.clone().add(0.5, 0.5, 0.5), Particle.ELECTRIC_SPARK);

            // 両方の位置が設定されている場合は立方体を表示
            if (pos2Selections.containsKey(uuid)) {
                Location pos2 = pos2Selections.get(uuid);
                if (blockLoc.getWorld().equals(pos2.getWorld())) {
                    visualizer.showCuboid(player, blockLoc, pos2);

                    // 体積を計算して表示
                    int volume = calculateVolume(blockLoc, pos2);
                    player.sendMessage(ChatColor.GRAY + "選択範囲: " +
                            ChatColor.YELLOW + volume + " ブロック");
                }
            }

        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // 右クリック: 位置2を設定
            pos2Selections.put(uuid, blockLoc);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "位置2を設定しました: " +
                    ChatColor.WHITE + formatLocation(blockLoc));

            // パーティクルで視覚化
            visualizer.showPoint(player, blockLoc.clone().add(0.5, 0.5, 0.5), Particle.ENCHANTED_HIT);

            // 両方の位置が設定されている場合は立方体を表示
            if (pos1Selections.containsKey(uuid)) {
                Location pos1 = pos1Selections.get(uuid);
                if (blockLoc.getWorld().equals(pos1.getWorld())) {
                    visualizer.showCuboid(player, pos1, blockLoc);

                    // 体積を計算して表示
                    int volume = calculateVolume(pos1, blockLoc);
                    player.sendMessage(ChatColor.GRAY + "選択範囲: " +
                            ChatColor.YELLOW + volume + " ブロック");
                }
            }
        }
    }

    /**
     * 杖を投げ捨てることを防止
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWandDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        if (SelectionWand.isWand(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "範囲選択の杖は捨てられません!");
        }
    }

    /**
     * 位置のフォーマット
     */
    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * 2つの位置から体積を計算
     */
    private int calculateVolume(Location pos1, Location pos2) {
        int sizeX = Math.abs(pos2.getBlockX() - pos1.getBlockX()) + 1;
        int sizeY = Math.abs(pos2.getBlockY() - pos1.getBlockY()) + 1;
        int sizeZ = Math.abs(pos2.getBlockZ() - pos1.getBlockZ()) + 1;
        return sizeX * sizeY * sizeZ;
    }
}