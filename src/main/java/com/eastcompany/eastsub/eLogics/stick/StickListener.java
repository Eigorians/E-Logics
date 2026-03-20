package com.eastcompany.eastsub.eLogics.stick;

import com.eastcompany.eastsub.eLogics.ELogics;
import com.eastcompany.eastsub.eLogics.ai.CommandAnalysis;
import com.eastcompany.eastsub.eLogics.ai.PDCUtils;
import com.eastcompany.eastsub.eLogics.util.CommandBlockHistory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StickListener implements Listener {
    private final StickManager manager;
    private final ELogics plugin;

    /** 現在AI解析が進行中のブロック座標を保持するセット */
    private final Set<Location> processingBlocks = new HashSet<>();

    public StickListener(StickManager manager, ELogics plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /**
     * ブロック破壊の保護
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockProtect(BlockBreakEvent event) {
        if (manager.isIntelliStick(event.getPlayer().getInventory().getItemInMainHand())) {
                event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakRecord(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof org.bukkit.block.CommandBlock cb) {
            CommandBlockHistory.add(
                    block.getLocation(),
                    cb.getCommand(),
                    block.getBlockData().clone()
            );
        }
    }

    /**
     * メインの解析処理
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();

        if (!manager.isIntelliStick(event.getItem())) return;

        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {

            // 1. 視線先100ブロックを探索
            RayTraceResult res = player.rayTraceBlocks(100, FluidCollisionMode.NEVER);

            if (res != null && res.getHitBlock() != null) {
                Block targetBlock = res.getHitBlock();

                // 2. ヒットしたのがコマンドブロックだった場合
                if (targetBlock.getState() instanceof CommandBlock cb) {
                    String currentCmd = cb.getCommand();

                    // コマンドが空なら何もしない
                    if (currentCmd.trim().isEmpty()) {
                        player.sendActionBar(Component.text("§c[!] コマンドが設定されていません。"));
                        return;
                    }

                    // 3. PDCから保存済みの解析データをロード
                    CommandAnalysis savedAnalysis = PDCUtils.loadAnalysis(plugin, targetBlock, currentCmd);

                    if (savedAnalysis != null) {
                        // 保存済みデータがある場合は即座に表示
                        List<CommandAnalysis> list = List.of(savedAnalysis);
                        displayResult(player, currentCmd, list);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
                        return;
                    }
                }
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            BlockFace face = event.getBlockFace();
                Block targetPlace = clickedBlock.getRelative(face);
                Location restoreLoc = targetPlace.getLocation();
                CommandBlockHistory.RemovedCommandBlock record = CommandBlockHistory.findAndRemove(restoreLoc);
                if (record != null) {
                    event.setCancelled(true);
                    targetPlace.setBlockData(record.blockData());
                    if (targetPlace.getState() instanceof CommandBlock cb) {
                        cb.setCommand(record.command());
                        cb.update(true);
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.1f, 1.2f);
                        return;
                    }
                }
        }

        // --- 右クリック（遠隔操作/テレポート） ---
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            RayTraceResult res = player.rayTraceBlocks(100, FluidCollisionMode.NEVER);
            if (res != null && res.getHitBlock() != null) {
                Block block = res.getHitBlock();
                if (block.getState() instanceof CommandBlock) {
                    Location originalLoc = player.getLocation().clone();
                    Vector direction = originalLoc.getDirection().multiply(-1.5);
                    Location targetLoc = block.getLocation().add(0.5, 0.5, 0.5).add(direction);
                    targetLoc.setY(targetLoc.getY() - 1.6);
                    targetLoc.setDirection(originalLoc.getDirection());

                    player.teleport(targetLoc);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.teleport(originalLoc);
                    }, 10L);
                    return;
                }
            }
        }

        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {

            Location loc = (clickedBlock != null) ? clickedBlock.getLocation() : player.getLocation();
            plugin.getSelectionManager().setPos1(player,loc);
            player.sendActionBar("§e[E-Logics] §fPos1を設定しました: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {

            Location loc = (clickedBlock != null) ? clickedBlock.getLocation() : player.getLocation();
            plugin.getSelectionManager().setPos2(player,loc);
            player.sendActionBar("§e[E-Logics] §fPos2を設定しました: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        }
    }

    private void displayResult(Player player, String command ,List<CommandAnalysis> results) {
        player.sendMessage("");
        for (int i = 0; i < results.size(); i++) {
            CommandAnalysis res = results.get(i);
            String prefix = (i == 0) ? "§e§l▶ " : "§7  ┗ ";
            player.sendMessage(prefix + "§f§l" + res.feature);
            player.sendMessage(Component.text("    " + command, NamedTextColor.AQUA));
            player.sendMessage("    §7" + res.logic);
            if (results.size() > 1) player.sendMessage("");
        }
    }

    private List<Block> getCommandChain(Block start) {
        List<Block> chain = new ArrayList<>();
        Block current = start;
        for (int i = 0; i < 20; i++) {
            chain.add(current);
            if (current.getBlockData() instanceof Directional dir) {
                Block next = current.getRelative(dir.getFacing());
                if (next.getType() == Material.CHAIN_COMMAND_BLOCK) {
                    current = next;
                    continue;
                }
            }
            break;
        }
        return chain;
    }

    /*
    private void runAnalysis(Player player, Block targetBlock) {
        if (!(targetBlock.getState() instanceof CommandBlock cb)) return;
        Location blockLoc = targetBlock.getLocation();

        // 1. 重複解析チェック
        if (processingBlocks.contains(blockLoc)) {
            player.sendActionBar(Component.text("§e[!] 解析進行中です..."));
            return;
        }

        String currentCmd = cb.getCommand();
        if (currentCmd.trim().isEmpty()) {
            PDCUtils.clearAnalysis(plugin, targetBlock);
            return;
        }

        // 2. 解析開始 - ブロックをロックする
        processingBlocks.add(blockLoc);
        player.sendActionBar(Component.text("§b[E-Logics AI] §7解析中..."));

        List<Block> chainBlocks = getCommandChain(targetBlock);
        List<String> cmdStrings = chainBlocks.stream()
                .map(b -> ((CommandBlock) b.getState()).getCommand()).toList();

        // 非同期解析実行
        plugin.getAIManager().analyzeChain(cmdStrings, 0).thenAccept(results -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (results == null || results.isEmpty()) {
                        player.sendMessage("§c[E-Logics AI] 解析に失敗しました。");
                        return;
                    }

                    for (int i = 0; i < results.size(); i++) {
                        if (i < chainBlocks.size()) {
                            PDCUtils.saveAnalysis(plugin, chainBlocks.get(i), results.get(i));
                        }
                    }

                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.2f);

                    // 左クリック時やデバッグ時に結果を表示
                    if (ELogics.debug) {
                        displayResult(player, cb.getCommand() ,results);
                    } else {
                        player.sendActionBar(Component.text("§a[E-Logics AI] §f解析完了"));
                    }
                } finally {
                    processingBlocks.remove(blockLoc);
                }
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> processingBlocks.remove(blockLoc));
            return null;
        });
    }
    */
}