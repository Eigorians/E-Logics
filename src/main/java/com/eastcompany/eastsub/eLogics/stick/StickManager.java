package com.eastcompany.eastsub.eLogics.stick;

import com.eastcompany.eastsub.eLogics.ELogics;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class StickManager {

    private final ELogics plugin;
    private final NamespacedKey key;

    public StickManager(ELogics plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "intellistick_id");
    }

    /**
     * E-Logics専用の解析棒を生成
     */
    public ItemStack getIntelliStick() {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();

        if (meta != null) {
            meta.displayName(
                    Component.text("IntelliStick")
                            .color(NamedTextColor.AQUA)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false) // デフォルトの斜体を解除
            );
            meta.lore(Arrays.asList(
                    Component.text("[E-Logics Developer Tool]", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),

                    Component.text()
                            .append(Component.text("Right-Click: ", NamedTextColor.WHITE))
                            .append(Component.text("Inspect CommandBlock", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false)
                            .build(),

                    Component.text()
                            .append(Component.text("Left-Click: ", NamedTextColor.WHITE))
                            .append(Component.text("Copy Logic (Upcoming)", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false)
                            .build()
            ));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            stick.setItemMeta(meta);
        }
        return stick;
    }

    /**
     * 指定したアイテムがIntelliStickかどうか判定
     */
    public boolean isIntelliStick(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}