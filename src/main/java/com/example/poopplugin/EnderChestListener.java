package com.example.poopplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;

public class EnderChestListener implements Listener {

    private final PoopPlugin plugin;

    public EnderChestListener(PoopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.ENDER_CHEST) {
                Player player = event.getPlayer();
                String worldName = player.getWorld().getName().toLowerCase();

                // クリエイティブワールドではアクセス不可
                if (worldName.equalsIgnoreCase("creative")) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "クリエイティブワールドではエンダーチェストを開けません。");
                    return;
                }

                // Hardcoreワールド(hardcore1, hardcore2, hardcore3)ではアクセス不可
                if (worldName.matches("hardcore[123]")) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "ハードコアワールドではエンダーチェストを開けません...");
                    return;
                }

                // バニラのエンダーチェストを開くのをキャンセルし、カスタムを開く
                event.setCancelled(true);

                // 権利や他のプラグインとの兼ね合いでスニーク中は設置できる可能性も考慮すべきだが
                // ここではエンダーチェストへのインタラクトを全てカスタムGUIに置き換える

                // 開く音を鳴らす(オプション)
                // player.playSound(player.getLocation(),
                // org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);

                plugin.getEnderChestManager().openEnderChest(player);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals("Shared Ender Chest")) {
            if (event.getPlayer() instanceof Player) {
                Player player = (Player) event.getPlayer();
                plugin.getEnderChestManager().saveInventory(player, event.getInventory());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getEnderChestManager().cleanup(event.getPlayer());
    }
}