package com.eastcompany.eastsub.eLogics.stick.display;

import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.UUID;

public interface IDisplayManager {
    int spawnTextAt(Player player, Location loc, TextComponent text, int length);
    void clearPlayerDisplays(Player player);
    void destroySpecificEntity(Player player, int entityId);
    void removePlayerData(UUID uuid); // 追加
}