package com.eastcompany.eastsub.eLogics.showhide;

import com.eastcompany.eastsub.eLogics.ELogics;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

public class HideShowCommandExecutor {

    public HideShowCommandExecutor(ELogics plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // --- 1. /hide [viewers] [targets] ---
            var hideRoot = Commands.literal("hide")
                    .requires(ctx -> ctx.getSender().hasPermission("elogics.admin"))
                    .then(Commands.argument("viewers", ArgumentTypes.players()) // 複数対応のためsを付与
                            .then(Commands.argument("targets", ArgumentTypes.entities())
                                    .executes(ctx -> {
                                        // 観測者リストを取得 (List<Player> が返る)
                                        List<Player> viewers = ctx.getArgument("viewers", PlayerSelectorArgumentResolver.class)
                                                .resolve(ctx.getSource());

                                        // 対象リストを取得
                                        Collection<? extends Entity> targets = ctx.getArgument("targets", EntitySelectorArgumentResolver.class)
                                                .resolve(ctx.getSource());

                                        // 各観測者に対して非表示処理を実行
                                        for (Player viewer : viewers) {
                                            toggleVisibility(viewer, targets, false);
                                        }
                                        return 1;
                                    })));

            // --- 2. /show [viewers] [targets] ---
            var showRoot = Commands.literal("show")
                    .requires(ctx -> ctx.getSender().hasPermission("elogics.admin"))
                    .then(Commands.argument("viewers", ArgumentTypes.players())
                            .then(Commands.argument("targets", ArgumentTypes.entities())
                                    .executes(ctx -> {
                                        List<Player> viewers = ctx.getArgument("viewers", PlayerSelectorArgumentResolver.class)
                                                .resolve(ctx.getSource());

                                        Collection<? extends Entity> targets = ctx.getArgument("targets", EntitySelectorArgumentResolver.class)
                                                .resolve(ctx.getSource());

                                        for (Player viewer : viewers) {
                                            toggleVisibility(viewer, targets, true);
                                        }
                                        return 1;
                                    })));

            commands.register(hideRoot.build(), "指定したプレイヤーから対象を隠す", List.of());
            commands.register(showRoot.build(), "指定したプレイヤーに対象を表示する", List.of());
        });
    }

    private int toggleVisibility(Player viewer, Collection<? extends Entity> targets, boolean show) {
        if (targets.isEmpty()) return 0;

        int count = 0;
        for (Entity target : targets) {
            if (viewer.equals(target)) continue;

            if (show) viewer.showEntity(ELogics.getInstance(), target);
            else viewer.hideEntity(ELogics.getInstance(), target);
            count++;
        }

        String action = show ? "§a表示§7" : "§c非表示§7";
        viewer.sendMessage(Component.text("§b[E-Logics] §7" + count + " 体のエンティティを" + action + "にしました。"));
        return 1;
    }
}