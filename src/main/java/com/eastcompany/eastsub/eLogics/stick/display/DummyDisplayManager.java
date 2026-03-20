package com.eastcompany.eastsub.eLogics.stick.display;

import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.UUID;

public class DummyDisplayManager implements IDisplayManager {
    @Override public int spawnTextAt(Player p, Location l, TextComponent t, int len) { return -1; }
    @Override public void clearPlayerDisplays(Player p) {}
    @Override public void destroySpecificEntity(Player p, int id) {}
    @Override public void removePlayerData(UUID uuid) {}
}