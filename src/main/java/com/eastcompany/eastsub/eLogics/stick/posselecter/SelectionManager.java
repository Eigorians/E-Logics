package com.eastcompany.eastsub.eLogics.stick.posselecter;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    private final Map<UUID, Selection> customSelections = new HashMap<>();

    /**
     * Pos1を保存する
     */
    public void setPos1(Player player, Location loc) {
        Selection sel = customSelections.computeIfAbsent(player.getUniqueId(), k -> new Selection());
        sel.setPos1(loc);
    }

    /**
     * Pos2を保存する
     */
    public void setPos2(Player player, Location loc) {
        Selection sel = customSelections.computeIfAbsent(player.getUniqueId(), k -> new Selection());
        sel.setPos2(loc);
    }

    public Location getPos1(Player player) {
        Selection sel = customSelections.get(player.getUniqueId());
        return (sel != null) ? sel.getPos1() : null;
    }

    public Location getPos2(Player player) {
        Selection sel = customSelections.get(player.getUniqueId());
        return (sel != null) ? sel.getPos2() : null;
    }

    /**
     * 両方の座標がセットされているか確認
     */
    public boolean hasCompleteSelection(Player player) {
        Selection sel = customSelections.get(player.getUniqueId());
        return sel != null && sel.isComplete();
    }
}