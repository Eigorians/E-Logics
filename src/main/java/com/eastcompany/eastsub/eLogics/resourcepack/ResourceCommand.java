package com.eastcompany.eastsub.eLogics.resourcepack;

import com.eastcompany.eastsub.eLogics.ELogics;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class ResourceCommand implements BasicCommand {

    private final ELogics plugin;
    private final PackManager packManager;

    public ResourceCommand(ELogics plugin, PackManager packManager) {
        this.plugin = plugin;
        this.packManager = packManager;
    }

    // コマンドの登録処理
    public void register(Commands commands) {
        commands.register("resources", "リソースパックを送信します", this);
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (!stack.getSender().hasPermission("resources.admin")) {
            stack.getSender().sendMessage(Component.text("権igenがありません。", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            stack.getSender().sendMessage(Component.text("使用法: /resources [send|set] ...", NamedTextColor.YELLOW));
            return;
        }

        // --- SET コマンド ---
        if (args[0].equalsIgnoreCase("set")) {
            String inputUrl = args[1];
            packManager.setAndCalculate(inputUrl);
            stack.getSender().sendMessage(Component.text("URLを登録し、ハッシュ計算を開始しました。", NamedTextColor.GREEN));
            stack.getSender().sendMessage(Component.text("修正後のURL: " + packManager.getUrl(), NamedTextColor.GRAY));
            return;
        }

        // --- SEND コマンド ---
        if (args[0].equalsIgnoreCase("send")) {
            String url = packManager.getUrl();
            byte[] hash = packManager.getHash();

            if (url == null || hash == null) {
                stack.getSender().sendMessage(Component.text("ハッシュが未生成です。URLを確認してください。", NamedTextColor.RED));
                return;
            }

            String target = args[1];
            if (target.equalsIgnoreCase("@a")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.setResourcePack(url, hash);
                }
                stack.getSender().sendMessage(Component.text("全員に送信しました。", NamedTextColor.GREEN));
            } else {
                Player p = Bukkit.getPlayer(target);
                if (p != null) {
                    p.setResourcePack(url, hash);
                    stack.getSender().sendMessage(Component.text(target + " に送信しました。", NamedTextColor.GREEN));
                } else {
                    stack.getSender().sendMessage(Component.text("プレイヤーが見つかりません。", NamedTextColor.RED));
                }
            }
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (args.length == 1) return List.of("send", "set");
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            List<String> suggestions = new java.util.ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            suggestions.add("@a");
            return suggestions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return List.of("https://github.com/...");
        }
        return List.of();
    }
}