package com.example.poopplugin;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * /own <WorldName> delete で表示する削除確認GUI
 */
public class DeleteConfirmGUI implements Listener {

    private static PoopPlugin pluginInstance;
    // player UUID → 確認対象ワールドコード
    private static final Map<UUID, String> pendingDelete = new HashMap<>();

    public static void init(PoopPlugin plugin) {
        pluginInstance = plugin;
        Bukkit.getPluginManager().registerEvents(new DeleteConfirmGUI(), plugin);
    }

    public static void open(Player player, WorldManager.OwnWorld ow, JavaPlugin plugin) {
        Inventory inv = Bukkit.createInventory(null, 27,
                "§4§l⚠ ワールド削除の確認");

        // 情報アイテム (center)
        ItemStack info = makeItem(Material.TNT,
                "§c§l" + ow.worldName + " を削除",
                Arrays.asList(
                        "§7コード: §b" + ow.code,
                        "§7オーナー: §f" + ow.ownerName,
                        "",
                        "§c§lこの操作は取り消せません！",
                        "§cワールドのデータはすべて削除されます。",
                        "§cワールド内のプレイヤーはHubに移動されます。"
                ));
        inv.setItem(13, info);

        // 確認ボタン (slot 11)
        ItemStack confirm = makeItem(Material.RED_CONCRETE,
                "§c§l✔ 削除する",
                Arrays.asList("§7クリックしてワールドを完全削除します。"));
        inv.setItem(11, confirm);

        // キャンセルボタン (slot 15)
        ItemStack cancel = makeItem(Material.GREEN_CONCRETE,
                "§a§l✖ キャンセル",
                Arrays.asList("§7クリックしてキャンセルします。"));
        inv.setItem(15, cancel);

        // ガラスで埋める
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        pendingDelete.put(player.getUniqueId(), ow.code);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals("§4§l⚠ ワールド削除の確認")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.RED_CONCRETE) {
            // 削除実行
            String code = pendingDelete.remove(player.getUniqueId());
            player.closeInventory();
            if (code == null) { player.sendMessage("§c操作がタイムアウトしました。"); return; }
            WorldManager.OwnWorld ow = pluginInstance.getWorldManager().getWorldByCode(code);
            if (ow == null) { player.sendMessage("§cワールドが見つかりません。"); return; }
            pluginInstance.getWorldManager().deleteWorld(player, ow);

        } else if (clicked.getType() == Material.GREEN_CONCRETE) {
            pendingDelete.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage("§a削除をキャンセルしました。");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getView().getTitle().equals("§4§l⚠ ワールド削除の確認")) {
            pendingDelete.remove(player.getUniqueId());
        }
    }

    private static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
