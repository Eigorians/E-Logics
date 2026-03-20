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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CommandBlock;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * IntelliStick関連のコマンド実行を管理するクラス。
 * ロジックは CommandChainSerializer と RulebookHandler に委譲している。
 */
public class StickCommandExecutor {

    private final StickManager manager;
    private final SelectionManager selectionManager;

    public StickCommandExecutor(ELogics plugin, StickManager manager, SelectionManager selectionManager) {
        this.manager = manager;
        this.selectionManager = selectionManager;

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // --- 1. /intellistick (is, istick) ---
            var intelliStickRoot = Commands.literal("intellistick")
                    .requires(ctx -> ctx.getSender().hasPermission("elogics.admin"))
                    .executes(this::executeGive);

            // /is copy [radius] - AI解析保存用
            intelliStickRoot.then(Commands.literal("copy")
                    .executes(ctx -> executeCopy(ctx, 20))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                            .executes(ctx -> executeCopy(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))
            );

            // /is rulebook [radius] - AI仕様書作成用
            intelliStickRoot.then(Commands.literal("rulebook")
                    .executes(ctx -> executeRulebook(ctx, 20))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                            .executes(ctx -> executeRulebook(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))
            );

            // /is getbook - 記入用の本を取得
            intelliStickRoot.then(Commands.literal("getbook")
                    .executes(this::executeGetBook));

            commands.register(intelliStickRoot.build(), "IntelliStick操作コマンド", List.of("is", "istick"));

            // --- 2. /saveAnalysis - 解析結果の書き戻し用 ---
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
            player.sendMessage(Component.text("[E-Logics] ", NamedTextColor.AQUA).append(Component.text("IntelliStick を支給しました。", NamedTextColor.WHITE)));
        }
        return 1;
    }

    private int executeCopy(CommandContext<CommandSourceStack> ctx, int radius) {
        if (!(ctx.getSource().getExecutor() instanceof Player player)) return 0;

        Location[] range = calculateRange(player, radius);
        int[] countOut = {0};
        String json = CommandChainSerializer.serialize(range[0], range[1], countOut);

        if (countOut[0] > 0) {
            String prompt = "### 指示: 解析データ保存用\n"
                    + "以下のデータを `/saveAnalysis " + player.getWorld().getName() + ",x,y,z|機能名|解説文 ;; ...` の形式で出力してください。\n\n"
                    + "### データ\n" + json;

            sendCopyMessage(player, countOut[0], "解析保存用データをコピー", prompt, NamedTextColor.GREEN);
        } else {
            player.sendMessage(Component.text("[E-Logics] 範囲内にコマンドブロックが見つかりませんでした。", NamedTextColor.RED));
        }
        return 1;
    }

    private int executeRulebook(CommandContext<CommandSourceStack> ctx, int radius) {
        if (!(ctx.getSource().getExecutor() instanceof Player player)) return 0;

        Location[] range = calculateRange(player, radius);
        int[] countOut = {0};
        String json = CommandChainSerializer.serialize(range[0], range[1], countOut);

        if (countOut[0] > 0) {
            String prompt = RulebookHandler.getPrompt(json);
            sendCopyMessage(player, countOut[0], "仕様書作成用データをコピー", prompt, NamedTextColor.GOLD);
        } else {
            player.sendMessage(Component.text("[E-Logics] 範囲内にコマンドブロックが見つかりませんでした。", NamedTextColor.RED));
        }
        return 1;
    }

    private int executeGetBook(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            RulebookHandler.giveGuideBook(player);
            player.sendMessage(Component.text("[E-Logics] ", NamedTextColor.AQUA).append(Component.text("解説記入用の本を支給しました。", NamedTextColor.WHITE)));
        }
        return 1;
    }

    private int executeBatchSave(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        String rawData = StringArgumentType.getString(ctx, "data");

        // AIの出力の揺れを考慮したセパレータ分割
        String[] entries = rawData.split("\\s+;;\\s+");
        int successCount = 0;

        for (String entry : entries) {
            try {
                String[] parts = entry.split("\\|", 3);
                if (parts.length < 3) continue;

                String[] locParts = parts[0].split(",");
                if (locParts.length < 4) continue;

                World world = Bukkit.getWorld(locParts[0].trim());
                if (world == null) continue;

                int x = Integer.parseInt(locParts[1].trim());
                int y = Integer.parseInt(locParts[2].trim());
                int z = Integer.parseInt(locParts[3].trim());

                Block block = world.getBlockAt(x, y, z);
                if (!(block.getState() instanceof CommandBlock)) continue;

                CommandAnalysis res = new CommandAnalysis();
                res.feature = parts[1].trim();
                res.logic = parts[2].trim();

                PDCUtils.saveAnalysis(ELogics.getInstance(), block, res);
                successCount++;
            } catch (Exception ignored) {}
        }

        sender.sendMessage(Component.text("[E-Logics] ", NamedTextColor.AQUA)
                .append(Component.text(successCount + " 個の解析結果をブロックのPDCに保存しました。", NamedTextColor.WHITE)));
        return 1;
    }

    // --- ユーティリティメソッド ---

    private Location[] calculateRange(Player player, int radius) {
        Location p1 = selectionManager.getPos1(player);
        Location p2 = selectionManager.getPos2(player);

        if (p1 != null && p2 != null && p1.getWorld().equals(p2.getWorld())) {
            return new Location[]{
                    new Location(p1.getWorld(), Math.min(p1.x(), p2.x()), Math.min(p1.y(), p2.y()), Math.min(p1.z(), p2.z())),
                    new Location(p1.getWorld(), Math.max(p1.x(), p2.x()), Math.max(p1.y(), p2.y()), Math.max(p1.z(), p2.z()))
            };
        }
        // 選択範囲がない場合はプレイヤー周囲の半径
        return new Location[]{
                player.getLocation().clone().subtract(radius, radius, radius),
                player.getLocation().clone().add(radius, radius, radius)
        };
    }

    private void sendCopyMessage(Player player, int count, String label, String content, NamedTextColor color) {
        player.sendMessage(Component.text()
                .append(Component.text("[E-Logics] ", NamedTextColor.AQUA))
                .append(Component.text(count + " 個のブロックを抽出しました。 "))
                .append(Component.text("[" + label + "]", color, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard(content))
                        .hoverEvent(Component.text("クリックしてAI用プロンプトをコピー"))));
    }
}