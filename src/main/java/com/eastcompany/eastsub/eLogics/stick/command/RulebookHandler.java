package com.eastcompany.eastsub.eLogics.stick.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import java.util.List;

public class RulebookHandler {

    public static String getPrompt(String json) {
        return "### 指示: システム仕様書の作成\n"
                + "提供されたMinecraftのコマンド構造JSONを解析し、以下の構成で解説を作成してください。\n"
                + "1. 全体の目的と流れ\n2. 主要な座標とその役割\n3. 実行条件の解説\n"
                + "※マイクラ内の「本」に貼り付けやすいよう、1ページあたり300文字程度を目安に簡潔にまとめてください。\n\n"
                + "### データ\n" + json;
    }

    public static void giveGuideBook(Player player) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.displayName(Component.text("E-Logics Rulebook Template", NamedTextColor.YELLOW, TextDecoration.BOLD));
        meta.lore(List.of(Component.text("AIの回答をここに貼り付けてください。", NamedTextColor.GRAY)));
        book.setItemMeta(meta);
        player.getInventory().addItem(book);
    }
}