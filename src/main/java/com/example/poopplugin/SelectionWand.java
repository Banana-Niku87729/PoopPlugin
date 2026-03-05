package com.example.poopplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * 範囲選択用の魔法の棒
 */
public class SelectionWand {

    public static final String WAND_NAME = ChatColor.GOLD + "範囲選択の杖";

    /**
     * 範囲選択用の杖を作成
     */
    public static ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(WAND_NAME);
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "左クリック: " + ChatColor.YELLOW + "位置1を選択",
                    ChatColor.GRAY + "右クリック: " + ChatColor.YELLOW + "位置2を選択",
                    ChatColor.DARK_GRAY + "保護エリア作成用の魔法の杖"
            ));

            // エンチャントの光を追加（見た目のみ）
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            wand.setItemMeta(meta);
        }

        return wand;
    }

    /**
     * アイテムが範囲選択の杖かチェック
     */
    public static boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        return meta.getDisplayName().equals(WAND_NAME);
    }
}