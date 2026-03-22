package com.eastcompany.eastsub.eLogics.stick.command;

import com.eastcompany.eastsub.eLogics.ELogics;
import com.eastcompany.eastsub.eLogics.ai.CommandAnalysis;
import com.eastcompany.eastsub.eLogics.ai.PDCUtils;
import com.eastcompany.eastsub.eLogics.stick.StickManager;
import com.eastcompany.eastsub.eLogics.stick.posselecter.SelectionManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

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

            // --- 1. /intellistick (is) の登録 ---
            var intelliStickRoot = Commands.literal("intellistick")
                    .requires(ctx -> ctx.getSender().hasPermission("elogics.admin"))
                    .executes(this::executeGive);

            // 解析保存用
            intelliStickRoot.then(Commands.literal("copy")
                    .executes(ctx -> executeExtract(ctx, 20, false))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                            .executes(ctx -> executeExtract(ctx, IntegerArgumentType.getInteger(ctx, "radius"), false)))
            );

            // 解説本用
            intelliStickRoot.then(Commands.literal("getrulebook")
                    .executes(ctx -> executeExtract(ctx, 20, true))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                            .executes(ctx -> executeExtract(ctx, IntegerArgumentType.getInteger(ctx, "radius"), true)))
            );

            commands.register(intelliStickRoot.build(), "IntelliStick操作コマンド", List.of("is", "istick"));

            // --- 2. /saveAnalysis の登録 ---
            var saveAnalysisRoot = Commands.literal("saveAnalysis")
                    .requires(ctx -> ctx.getSender().hasPermission("elogics.admin"))
                    .then(Commands.argument("data", StringArgumentType.greedyString())
                            .executes(this::executeBatchSave));

            commands.register(saveAnalysisRoot.build(), "AI解析結果の一括保存", List.of());

            // --- 3. /createrulebook の登録 ---
            var createRuleBookRoot = Commands.literal("createrulebook")
                    .requires(ctx -> ctx.getSender().hasPermission("elogics.admin"))
                    .then(Commands.argument("json", StringArgumentType.greedyString())
                            .executes(this::executeCreateRuleBook));

            commands.register(createRuleBookRoot.build(), "AIによる解説本の生成実行", List.of());
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

                org.bukkit.World world = Bukkit.getWorld(locParts[0]);
                if (world == null) continue;

                Block block = world.getBlockAt(Integer.parseInt(locParts[1].trim()), Integer.parseInt(locParts[2].trim()), Integer.parseInt(locParts[3].trim()));
                if (!(block.getState() instanceof org.bukkit.block.CommandBlock)) continue;

                CommandAnalysis res = new CommandAnalysis();
                res.feature = parts[1].trim();
                res.logic = parts[2].trim();

                PDCUtils.saveAnalysis(ELogics.getInstance(), block, res);
                successCount++;
            } catch (Exception ignored) {}
        }
        sender.sendMessage("§b[E-Logics] §f" + successCount + " 個の解析結果を保存しました。");
        return 1;
    }

    /**
     * AIが生成したデータから本を作成するコマンド
     */
    private int executeCreateRuleBook(CommandContext<CommandSourceStack> ctx) {
        Player target;

        if (ctx.getSource().getExecutor() instanceof Player player) {
            target = player;
        } else {
            // 2. プレイヤー以外（コマンドブロック等）の場合、実行位置から最も近いプレイヤーを探す
            Location loc = ctx.getSource().getLocation();
            target = loc.getWorld().getNearbyEntities(loc, 10.0, 10.0, 10.0).stream()
                    .filter(e -> e instanceof Player)
                    .map(e -> (Player) e)
                    .findFirst() // getNearbyEntities は近い順に並んでいることが多いですが、より厳密にするなら sorted を挟みます
                    .orElse(null);
        }
        String jsonData = StringArgumentType.getString(ctx, "json");

        try {
            // パイプで「タイトル」「著者」「本文」を分離
            String[] parts = jsonData.split("\\|", 3);
            if (parts.length < 3) {
                target.sendMessage("§c[E-Logics] 解説本のデータ形式が正しくありません。(タイトル|著者|本文)");
                return 0;
            }

            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();

            // タイトルと著者の装飾
            meta.setTitle(org.bukkit.ChatColor.translateAlternateColorCodes('&', parts[0].trim()));
            meta.setAuthor(org.bukkit.ChatColor.translateAlternateColorCodes('&', parts[1].trim()));

            // 本文の処理: 独自の [PAGE] デリミタでページを分割
            String fullContent = parts[2].trim();
            String[] pages = fullContent.split("\\[PAGE\\]");

            for (String page : pages) {
                // 独自の <br> を実際の改行に置換し、& を色コードに変換
                String formattedPage = page.replace("&n", "\n").trim();
                formattedPage = org.bukkit.ChatColor.translateAlternateColorCodes('&', formattedPage);
                meta.addPage(formattedPage);
            }

            book.setItemMeta(meta);
            target.getInventory().addItem(book);
            target.sendMessage("§b[E-Logics] §f解説本「" + meta.getTitle() + "§f」を生成しました。");

        } catch (Exception e) {
            target.sendMessage("§c[E-Logics] 本の生成中にエラーが発生しました。");
        }
        return 1;
    }

    private int executeExtract(CommandContext<CommandSourceStack> ctx, int radius, boolean isBookMode) {
        if (!(ctx.getSource().getExecutor() instanceof Player player)) return 0;

        Location p1 = selectionManager.getPos1(player);
        Location p2 = selectionManager.getPos2(player);
        Location min, max;

        if (p1 != null && p2 != null && p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage("§b[E-Logics] §f範囲スキャン中...");
            min = new Location(p1.getWorld(), Math.min(p1.x(), p2.x()), Math.min(p1.y(), p2.y()), Math.min(p1.z(), p2.z()));
            max = new Location(p1.getWorld(), Math.max(p1.x(), p2.x()), Math.max(p1.y(), p2.y()), Math.max(p1.z(), p2.z()));
        } else {
            player.sendMessage("§e[E-Logics] §f周囲 " + radius + " マスをスキャン中...");
            Location loc = player.getLocation();
            min = loc.clone().subtract(radius, radius, radius);
            max = loc.clone().add(radius, radius, radius);
        }

        CommandSearchResult result = searchCommandBlocks(player, min, max);
        if (result.count <= 0) {
            player.sendMessage("§c[E-Logics] コマンドブロックが見つかりませんでした。");
            return 1;
        }

        // --- 共通の解析ルール (チェーンのつながりを意識させる) ---
        String commonAnalysisRule =
                "### データの読み取りガイド\n"
                        + "- 各ブロックには `coords` (座標), `facing` (向き), `logic` (CONDITIONAL/UNCONDITIONAL) が含まれています。\n"
                        + "- **チェーンの判別**: 連続するブロックが向き(`facing`)の先にあり、かつ連鎖設定になっている場合は「一連の処理（チェーン）」としてその流れを解析してください。\n"
                        + "- **独立ブロックの判別**: 周囲と実行順序が繋がっていない、または独立して動作しているブロックは、単体での機能として個別に解析してください。\n"
                        + "- 解析の目的は、各ブロックが「単独で何をするか」だけでなく、「装置全体の中でどのような役割（トリガー、条件分岐、実行結果、あるいは独立したサブ機能など）を担っているか」を明らかにすることです。\n\n";

        String promptHeader;
        if (isBookMode) {
            // 解説本生成用プロンプト
            promptHeader = "### 指示\n"
                    + "1. 以下のデータを解析し、ゲームの説明書を作成してください。\n"
                    + "2. 解析結果を元に、以下のコマンドを**1行**で出力してください。\n"
                    + "   フォーマット: `/createrulebook タイトル|著者名|本文` \n"
                    + "   - **装飾ルール**: 背景が白いため、&f, &e, &7, &a の使用は禁止。&0, &1, &4, &2, &5, &6 を使用すること。\n"
                    + "   - **構造ルール**: ”通常の改行は使用せず”、 `&n`を必ず使用、ページ区切りは `[PAGE]` を使用すること。\n"
                    + "   - **内容**: ゲームの属性をプレイヤーにわかりやすく説明してください\n"
                    + "   - **出力は `/createrulebook` から始まるコマンドのみを提示すること。**\n\n"
                    + commonAnalysisRule
                    + "### 解析対象データ (World: " + player.getWorld().getName() + ")\n";
        } else {
            // 解析保存用プロンプト
            promptHeader = "### 指示\n"
                    + "1. 以下のデータを解析し、各ブロックの役割を省略なく全てデータベースに保存するためのコマンドを出力してください。\n"
                    + "2. フォーマット: `/saveAnalysis ワールド名,x,y,z|機能名|解説文 ;; ...` \n"
                    + "   - **解説文**: チェーン内での役割（例: 判定用、バフ付与用、通知用など）とロジックを簡潔に記述してください。\n"
                    + "   - 各エントリーの間は必ず ` ;; ` で区切ること。\n\n"
                    + commonAnalysisRule
                    + "### 解析対象データ (World: " + player.getWorld().getName() + ")\n";
        }

        String finalOutput = promptHeader + result.json.toString();

        Component message = Component.text()
                .append(Component.text(result.count + " 個のコマンドを抽出しました。"))
                .append(Component.newline())
                .append(Component.text("[AI用プロンプトをコピー]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard(finalOutput)))
                .build();

        player.sendMessage(message);
        return 1;
    }

    public record CommandSearchResult(StringBuilder json, int count) {}

    private CommandSearchResult searchCommandBlocks(Player player, Location min, Location max) {
        int count = 0;
        Set<Block> visited = new HashSet<>();
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n  \"chains\": [\n");
        boolean firstChain = true;

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = min.getWorld().getBlockAt(x, y, z);
                    if ((block.getType() == Material.COMMAND_BLOCK || block.getType() == Material.REPEATING_COMMAND_BLOCK) && !visited.contains(block)) {
                        if (!firstChain) jsonBuilder.append(",\n");
                        jsonBuilder.append("    {\n      \"chain_type\": \"START_FROM_ROOT\",\n      \"blocks\": [\n");
                        count += collectChainAsJson(block, visited, jsonBuilder);
                        jsonBuilder.append("\n      ]\n    }");
                        firstChain = false;
                    }
                }
            }
        }
        // 独立したチェーン
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
        return new CommandSearchResult(jsonBuilder, count);
    }

    private int collectChainAsJson(Block startBlock, Set<Block> visited, StringBuilder sb) {
        int chainCount = 0;
        Block current = startBlock;
        while (current != null && isCommandBlock(current.getType()) && !visited.contains(current)) {
            visited.add(current);
            if (current.getState() instanceof CommandBlock cb) {
                String cmd = cb.getCommand();
                if (!cmd.isEmpty()) {
                    if (chainCount > 0) sb.append(",\n");
                    boolean isConditional = current.getBlockData() instanceof org.bukkit.block.data.type.CommandBlock data && data.isConditional();
                    sb.append(String.format(
                            "        { \"block_type\": \"%s\", \"coords\": [%d,%d,%d], \"facing\": \"%s\", \"logic\": \"%s\", \"cmd\": \"%s\" }",
                            getFullTypeName(current.getType()), current.getX(), current.getY(), current.getZ(),
                            ((Directional) current.getBlockData()).getFacing().name(),
                            isConditional ? "CONDITIONAL" : "UNCONDITIONAL", cmd.replace("\\", "\\\\").replace("\"", "\\\"")
                    ));
                    chainCount++;
                }
            }
            if (current.getBlockData() instanceof Directional directional) {
                Block next = current.getRelative(directional.getFacing());
                current = (next.getType() == Material.CHAIN_COMMAND_BLOCK) ? next : null;
            } else current = null;
        }
        return chainCount;
    }

    private String getFullTypeName(Material m) {
        return switch (m) {
            case REPEATING_COMMAND_BLOCK -> "REPEATING";
            case CHAIN_COMMAND_BLOCK     -> "CHAIN";
            case COMMAND_BLOCK           -> "IMPULSE";
            default -> "UNKNOWN";
        };
    }

    private boolean isCommandBlock(Material m) {
        return m == Material.COMMAND_BLOCK || m == Material.CHAIN_COMMAND_BLOCK || m == Material.REPEATING_COMMAND_BLOCK;
    }
}