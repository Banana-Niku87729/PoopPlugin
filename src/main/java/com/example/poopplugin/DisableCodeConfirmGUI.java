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
 * /own <WorldName> disabled で表示するカスタムコード無効化確認GUI
 */
public class DisableCodeConfirmGUI implements Listener {

    private static PoopPlugin pluginInstance;
    private static final Map<UUID, String> pendingDisable = new HashMap<>();

    public static void init(PoopPlugin plugin) {
        pluginInstance = plugin;
        Bukkit.getPluginManager().registerEvents(new DisableCodeConfirmGUI(), plugin);
    }

    public static void open(Player player, WorldManager.OwnWorld ow, JavaPlugin plugin) {
        Inventory inv = Bukkit.createInventory(null, 27,
                "§6§l⚠ カスタムコード無効化の確認");

        String autoCode = (ow.autoCode != null && !ow.autoCode.isEmpty())
                ? ow.autoCode : "（新規生成）";

        ItemStack info = makeItem(Material.NAME_TAG,
                "§e§lカスタムコードを無効化",
                Arrays.asList(
                        "§7ワールド: §f" + ow.worldName,
                        "§7現在のコード: §b" + ow.code,
                        "§7無効化後のコード: §b" + autoCode,
                        "",
                        "§e現在のカスタムコードは使用できなくなります。",
                        "§e無効化後は自動生成コードでアクセスできます。",
                        "",
                        (!ow.hasCustomCode ? "§c§lカスタムコードは設定されていません。" : "")
                ));
        inv.setItem(13, info);

        // 実行ボタン
        Material confirmMat = ow.hasCustomCode ? Material.ORANGE_CONCRETE : Material.GRAY_CONCRETE;
        ItemStack confirm = makeItem(confirmMat,
                ow.hasCustomCode ? "§6§l✔ 無効化する" : "§7§l（カスタムコードなし）",
                ow.hasCustomCode
                        ? Arrays.asList("§7クリックしてカスタムコードを無効化します。")
                        : Arrays.asList("§cカスタムコードが設定されていないため無効化できません。"));
        inv.setItem(11, confirm);

        ItemStack cancel = makeItem(Material.GREEN_CONCRETE,
                "§a§l✖ キャンセル",
                Collections.singletonList("§7クリックしてキャンセルします。"));
        inv.setItem(15, cancel);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        pendingDisable.put(player.getUniqueId(), ow.code);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals("§6§l⚠ カスタムコード無効化の確認")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.ORANGE_CONCRETE) {
            String code = pendingDisable.remove(player.getUniqueId());
            player.closeInventory();
            if (code == null) { player.sendMessage("§c操作がタイムアウトしました。"); return; }
            WorldManager.OwnWorld ow = pluginInstance.getWorldManager().getWorldByCode(code);
            if (ow == null) { player.sendMessage("§cワールドが見つかりません。"); return; }
            pluginInstance.getWorldManager().disableCustomCode(player, ow);

        } else if (clicked.getType() == Material.GREEN_CONCRETE) {
            pendingDisable.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage("§aキャンセルしました。");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getView().getTitle().equals("§6§l⚠ カスタムコード無効化の確認")) {
            pendingDisable.remove(player.getUniqueId());
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
