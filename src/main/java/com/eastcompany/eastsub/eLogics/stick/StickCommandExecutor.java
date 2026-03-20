package com.eastcompany.eastsub.eLogics.stick;

import com.eastcompany.eastsub.eLogics.ELogics;
import com.eastcompany.eastsub.eLogics.ai.CommandAnalysis;
import com.eastcompany.eastsub.eLogics.ai.PDCUtils;
import com.eastcompany.eastsub.eLogics.stick.posselecter.SelectionManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StickCommandExecutor {

    private final StickManager manager;
    private final SelectionManager selectionManager;

    public StickCommandExecutor(ELogics plugin, StickManager manager, SelectionManager selectionManager) {
        this.manager = manager;
        this.selectionManager = selectionManager;

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // --- 1. /intellistick (is, istick) の登録 ---
            var intelliStickRoot = Commands.literal("intellistick")
                    .requires(ctx -> ctx.getSender().hasPermission("elogics.admin"))
                    .executes(this::executeGive);

            intelliStickRoot.then(Commands.literal("copy")
                    .executes(ctx -> executeCopy(ctx, 20))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                            .executes(ctx -> executeCopy(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))
            );

            commands.register(intelliStickRoot.build(), "IntelliStick操作コマンド", List.of("is", "istick"));

            // --- 2. /saveAnalysis (独立したコマンド) の登録 ---
            var saveAnalysisRoot = Commands.literal("saveAnalysis")
                    .requires(ctx -> ctx.getSender().hasPermission("elogics.admin"))
                    .then(Commands.argument("data", StringArgumentType.greedyString())
                            .executes(this::executeBatchSave));

            commands.register(saveAnalysisRoot.build(), "AI解析結果の一括保存", List.of());
        });

    }

    private int executeGive(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            player.getInventory().addItem(manager.getIntelliStick());
            player.sendMessage("§b[E-Logics] §fIntelliStick を支給しました。");
        }
        return 1;
    }

    private int executeBatchSave(CommandContext<CommandSourceStack> ctx) {
        // Player 判定を外し、メッセージ送り先を汎用的な Sender にする
        var sender = ctx.getSource().getSender();
        String rawData = StringArgumentType.getString(ctx, "data");

        String[] entries = rawData.split("\\s+;;\\s+");
        int successCount = 0;

        for (String entry : entries) {
            try {
                String[] parts = entry.split("\\|", 3);
                if (parts.length < 3) continue;

                String[] locParts = parts[0].split(",");
                if (locParts.length < 4) continue;

                String worldName = locParts[0];
                int x = Integer.parseInt(locParts[1].trim());
                int y = Integer.parseInt(locParts[2].trim());
                int z = Integer.parseInt(locParts[3].trim());

                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                Block block = world.getBlockAt(x, y, z);
                if (!(block.getState() instanceof org.bukkit.block.CommandBlock)) continue;

                CommandAnalysis res = new CommandAnalysis();
                res.feature = parts[1].trim();
                res.logic = parts[2].trim();

                PDCUtils.saveAnalysis(ELogics.getInstance(), block, res);
                successCount++;

            } catch (Exception e) {
                sender.sendMessage("§c[E-Logics] 解析失敗: " + entry);
            }
        }

        sender.sendMessage("§b[E-Logics] §f" + successCount + " 個の解析結果を保存しました。");
        return 1;
    }

    private int executeCopy(CommandContext<CommandSourceStack> ctx, int radius) {
        if (!(ctx.getSource().getExecutor() instanceof Player player)) return 0;

        if (selectionManager == null) {
            player.sendMessage("§c[E-Logics] Error: SelectionManager is not initialized.");
            return 0;
        }

        Location p1 = selectionManager.getPos1(player);
        Location p2 = selectionManager.getPos2(player);

        Location min, max;

        // 優先順位: 1. 選択範囲(p1とp2両方) > 2. コマンド引数の半径
        if (p1 != null && p2 != null && p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage("§b[E-Logics] §f選択範囲内のコマンドを抽出します...");
            min = new Location(p1.getWorld(), Math.min(p1.x(), p2.x()), Math.min(p1.y(), p2.y()), Math.min(p1.z(), p2.z()));
            max = new Location(p1.getWorld(), Math.max(p1.x(), p2.x()), Math.max(p1.y(), p2.y()), Math.max(p1.z(), p2.z()));
        } else {
            // 片方しか選択されていない、または別ワールドの場合は半径検索にフォールバック
            player.sendMessage("§e[E-Logics] §f周囲 " + radius + " マスを検索します。");
            Location loc = player.getLocation();
            min = loc.clone().subtract(radius, radius, radius);
            max = loc.clone().add(radius, radius, radius);
        }

        searchCommandBlocks(player, min, max);
        return 1;
    }

    private void searchCommandBlocks(Player player, Location min, Location max) {
        int count = 0;
        Set<Block> visited = new HashSet<>();
        StringBuilder jsonBuilder = new StringBuilder();

        // JSONの開始
        jsonBuilder.append("{\n");
        jsonBuilder.append("  \"chains\": [\n");

        boolean firstChain = true;

        // 1. 起点ブロックの走査
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = min.getWorld().getBlockAt(x, y, z);
                    Material type = block.getType();

                    if ((type == Material.COMMAND_BLOCK || type == Material.REPEATING_COMMAND_BLOCK)
                            && !visited.contains(block)) {
                        if (!firstChain) jsonBuilder.append(",\n");
                        jsonBuilder.append("    {\n      \"chain_type\": \"START_FROM_ROOT\",\n      \"blocks\": [\n");
                        count += collectChainAsJson(block, visited, jsonBuilder);
                        jsonBuilder.append("\n      ]\n    }");
                        firstChain = false;
                    }
                }
            }
        }

        // 2. 独立したチェーンブロックの走査
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = min.getWorld().getBlockAt(x, y, z);
                    if (block.getType() == Material.CHAIN_COMMAND_BLOCK && !visited.contains(block)) {
                        if (!firstChain) jsonBuilder.append(",\n");
                        jsonBuilder.append("    {\n      \"chain_type\": \"ISOLATED_CHAIN\",\n      \"blocks\": [\n");
                        count += collectChainAsJson(block, visited, jsonBuilder);
                        jsonBuilder.append("\n      ]\n    }");
                        firstChain = false;
                    }
                }
            }
        }

        jsonBuilder.append("\n  ]\n}");

        if (count > 0) {
            // AIへの指示文を添えてメッセージを構築
            String promptHeader = "### 指示\n"
                    + "1. 以下のMinecraftコマンド構造（JSON）を解析してください。\n"
                    + "2. 解析後、各ブロックの役割を保存するためのコマンドを、以下のフォーマットに従って**1つのメッセージ内ですべて**出力してください。\n"
                    + "   フォーマット: `/saveAnalysis ワールド名,x,y,z|機能名|解説文 ;; ワールド名,x,y,z|機能名|解説文 ...` \n"
                    + "   - `ワールド名,x,y,z` は提供された `coords` と現在のワールド名を使用して正確に記述すること。\n"
                    + "   - `機能名` はそのブロックの特徴をとらえた短い役割名。\n"
                    + "   - `解説文` はそのブロックのロジックの詳細な説明。\n"
                    + "   - 各エントリーの間は必ず ` ;; ` で区切ること。\n\n"
                    + "### 解析対象データ (World: " + player.getWorld().getName() + ")\n";
            String finalOutput = promptHeader + jsonBuilder.toString();

            net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.text()
                    .append(net.kyori.adventure.text.Component.text(count + " 個のコマンドをJSON形式で抽出しました。"))
                    .append(net.kyori.adventure.text.Component.newline())
                    .append(net.kyori.adventure.text.Component.text("[クリップボードにコピー]", NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                            .hoverEvent(net.kyori.adventure.text.Component.text("§7クリップボードにコピー"))
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(finalOutput)))
                    .build();

            player.sendMessage(message);
        }
    }

    // JSON構築部分の修正案
    private int collectChainAsJson(Block startBlock, Set<Block> visited, StringBuilder sb) {
        int chainCount = 0;
        Block current = startBlock;

        while (current != null && isCommandBlock(current.getType()) && !visited.contains(current)) {
            visited.add(current);
            if (current.getState() instanceof CommandBlock cb) {
                String cmd = cb.getCommand();
                if (!cmd.isEmpty()) {
                    if (chainCount > 0) sb.append(",\n");

                    String blockType = getFullTypeName(current.getType());
                    String facing = (current.getBlockData() instanceof Directional d) ? d.getFacing().name() : "UNKNOWN";

                    // 1. 「条件付き」設定の取得 (これは確実！)
                    boolean isConditional = false;
                    if (current.getBlockData() instanceof org.bukkit.block.data.type.CommandBlock data) {
                        isConditional = data.isConditional();
                    }

                    String escapedCmd = cmd.replace("\\", "\\\\").replace("\"", "\\\"");

                    // 引数の数を logic までに調整し、activation は「AI推論用」の固定文言にするか、削除します。
                    sb.append(String.format(
                            "        { \"block_type\": \"%s\", \"coords\": [%d,%d,%d], \"facing\": \"%s\", \"logic\": \"%s\", \"cmd\": \"%s\" }",
                            blockType,
                            current.getX(), current.getY(), current.getZ(),
                            facing,
                            isConditional ? "CONDITIONAL" : "UNCONDITIONAL",
                            escapedCmd
                    ));
                    chainCount++;
                }
            }

            if (current.getBlockData() instanceof Directional directional) {
                Block next = current.getRelative(directional.getFacing());
                // CHAIN_COMMAND_BLOCK だけを連結対象にする
                current = (next.getType() == Material.CHAIN_COMMAND_BLOCK) ? next : null;
            } else {
                current = null;
            }
        }
        return chainCount;
    }

    // わかりやすい名称を返すように変更
    private String getFullTypeName(Material m) {
        return switch (m) {
            case REPEATING_COMMAND_BLOCK -> "REPEATING (Purple)";
            case CHAIN_COMMAND_BLOCK     -> "CHAIN (Green)";
            case COMMAND_BLOCK           -> "IMPULSE (Orange)";
            default -> "UNKNOWN";
        };
    }


    private boolean isCommandBlock(Material m) {
        return m == Material.COMMAND_BLOCK || m == Material.CHAIN_COMMAND_BLOCK || m == Material.REPEATING_COMMAND_BLOCK;
    }

    private String getShortTypeName(Material m) {
        if (m == Material.REPEATING_COMMAND_BLOCK) return "R";
        if (m == Material.CHAIN_COMMAND_BLOCK) return "C";
        return "I";
    }
}