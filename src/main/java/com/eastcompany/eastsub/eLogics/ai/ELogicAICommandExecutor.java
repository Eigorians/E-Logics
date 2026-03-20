package com.eastcompany.eastsub.eLogics.ai;

import com.eastcompany.eastsub.eLogics.ELogics;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.List;

public class ELogicAICommandExecutor {

    private final ELogics plugin;

    public ELogicAICommandExecutor(ELogics plugin) {
        this.plugin = plugin;

        // Paper Lifecycle API でコマンド登録
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(
                    Commands.literal("elogicai")
                            .requires(ctx -> ctx.getSender().hasPermission("elogics.admin"))
                            .then(Commands.literal("set")
                                    .then(Commands.argument("api-key", StringArgumentType.string())
                                            .executes(this::setApiKey)))
                            .build(),
                    "E-Logics AIの設定を行います",
                    List.of("eai")
            );
        });
    }

    private int setApiKey(CommandContext<CommandSourceStack> ctx) {
        String apiKey = StringArgumentType.getString(ctx, "api-key");

        // Configに保存
        plugin.getConfig().set("ai.api-key", apiKey);
        plugin.saveConfig();

        ctx.getSource().getSender().sendMessage("§b[E-Logics AI] §fAPIキーを保存しました。");
        return 1;
    }
}