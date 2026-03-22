package com.eastcompany.eastsub.eLogics.stick.display;

import com.eastcompany.eastsub.eLogics.ELogics;
import com.eastcompany.eastsub.eLogics.ai.CommandAnalysis;
import com.eastcompany.eastsub.eLogics.ai.PDCUtils;
import com.eastcompany.eastsub.eLogics.util.CommandHighlighter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CommandBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StickDisplayListener implements Listener {

    private final ELogics plugin;
    private IDisplayManager displayManager;
    private final int SCAN_RADIUS = 8;

    // プレイヤーごとに「場所 -> (EntityID と 最後に表示したコマンド文字列)」を保持
    private final Map<UUID, Map<Location, DisplayInfo>> activeDisplays = new ConcurrentHashMap<>();

    // 内部データ保持用クラス
    private static class DisplayInfo {
        int entityId;
        String lastCommand;
        DisplayInfo(int id, String cmd) { this.entityId = id; this.lastCommand = cmd; }
    }

    public StickDisplayListener(ELogics plugin) {
        this.plugin = plugin;

        // DependencyCheckerで外部プラグイン(PacketEvents)の有無を確認
        if (DependencyChecker.isPacketEventsAvailable()) {
            plugin.getLogger().info("PacketEvents detected. Enabling Virtual Display feature.");

            // 【重要】インターフェース型の変数に、実体(VirtualDisplayManager)を代入する
            this.displayManager = new VirtualDisplayManager();
        } else {
            plugin.getLogger().warning("PacketEvents not found. Virtual Display feature will be disabled.");

            // 【重要】実体がない場合は、何もしないダミークラスを代入する
            this.displayManager = new DummyDisplayManager();
        }
        startDisplayTask();
    }

    private void startDisplayTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if(plugin.getStickManager() != null) {
                        if (plugin.getStickManager().isIntelliStick(player.getInventory().getItemInMainHand())) {
                            updatePlayerDisplays(player);
                        } else {
                            clearAll(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void updatePlayerDisplays(Player player) {
        UUID uuid = player.getUniqueId();
        Map<Location, DisplayInfo> currentDisplays = activeDisplays.computeIfAbsent(uuid, k -> new HashMap<>());

        // 1. 周囲をスキャン
        Map<Location, String> scannedBlocks = scanNearby(player);

        // 2. 範囲外になったものを削除
        currentDisplays.entrySet().removeIf(entry -> {
            if (!scannedBlocks.containsKey(entry.getKey())) {
                displayManager.destroySpecificEntity(player, entry.getValue().entityId);
                return true;
            }
            return false;
        });

        // 3. 新規追加 または 書き換えの検知
        scannedBlocks.forEach((loc, cmd) -> {

            if (cmd.startsWith("/saveAnalysis")) {
                // もし既に表示されていた場合は削除してスキップ
                DisplayInfo oldInfo = currentDisplays.remove(loc);
                if (oldInfo != null) {
                    displayManager.destroySpecificEntity(player, oldInfo.entityId);
                }
                return;
            }

            DisplayInfo info = currentDisplays.get(loc);

            if (info == null || !info.lastCommand.equals(cmd)) {
                if (info != null) {
                    displayManager.destroySpecificEntity(player, info.entityId);
                }

                // --- 位置計算：y座標はいじらず、水平方向のみ判定 ---
                Location displayLoc = loc.clone();

                int lengh = 80;

                // 真上のブロックを確認
                Block blockAbove = loc.getBlock().getRelative(0, 1, 0);

                if (!blockAbove.isPassable()) {
                    org.bukkit.block.BlockFace face = getRelativeFace(player.getLocation(), loc);
                    displayLoc.add(face.getModX(), -1, face.getModZ());
                    lengh = 300;
                }
                // ----------------------------------------------

                TextComponent coloredCmd = CommandHighlighter.format("/" + cmd);
                TextComponent finalComponent;
                CommandAnalysis cached = PDCUtils.loadAnalysis(plugin, loc.getBlock(), cmd);

                if (cached != null) {
                    finalComponent = Component.text(cached.feature + "\n\n", NamedTextColor.GOLD).append(coloredCmd);
                } else {
                    finalComponent = coloredCmd;
                }

                int newId = displayManager.spawnTextAt(player, displayLoc, finalComponent, lengh);
                currentDisplays.put(loc, new DisplayInfo(newId, cmd));
            }
        });
    }

    private void clearAll(Player player) {
        Map<Location, DisplayInfo> displays = activeDisplays.remove(player.getUniqueId());
        if (displays != null) {
            displayManager.clearPlayerDisplays(player);
        }
    }

    private Map<Location, String> scanNearby(Player player) {
        Map<Location, String> found = new HashMap<>();
        Location pLoc = player.getLocation().getBlock().getLocation();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    Block b = pLoc.clone().add(x, y, z).getBlock();
                    if (isCommandBlock(b.getType())) {
                        CommandBlock cb = (CommandBlock) b.getState();
                        String cmd = cb.getCommand();
                        if (!cmd.isEmpty()) found.put(b.getLocation(), cmd);
                    }
                }
            }
        }
        return found;
    }

    private boolean isCommandBlock(Material t) {
        return t == Material.COMMAND_BLOCK || t == Material.CHAIN_COMMAND_BLOCK || t == Material.REPEATING_COMMAND_BLOCK;
    }

    private org.bukkit.block.BlockFace getRelativeFace(Location plrLoc, Location blockLoc) {
        // ブロックの中心(0.5)とプレイヤーの座標の差
        double dx = plrLoc.getX() - (blockLoc.getX() + 0.5);
        double dz = plrLoc.getZ() - (blockLoc.getZ() + 0.5);
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? org.bukkit.block.BlockFace.EAST : org.bukkit.block.BlockFace.WEST;
        } else {
            return dz > 0 ? org.bukkit.block.BlockFace.SOUTH : org.bukkit.block.BlockFace.NORTH;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clearAll(e.getPlayer());
        displayManager.removePlayerData(e.getPlayer().getUniqueId());
    }
}