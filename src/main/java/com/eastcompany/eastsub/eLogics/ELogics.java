package com.eastcompany.eastsub.eLogics;

import com.eastcompany.eastsub.eLogics.ai.AIManager;
import com.eastcompany.eastsub.eLogics.ai.ELogicAICommandExecutor;
import com.eastcompany.eastsub.eLogics.resourcepack.PackManager;
import com.eastcompany.eastsub.eLogics.resourcepack.ResourceCommand;
import com.eastcompany.eastsub.eLogics.showhide.HideShowCommandExecutor;
import com.eastcompany.eastsub.eLogics.stick.StickCommandExecutor;
import com.eastcompany.eastsub.eLogics.stick.StickListener;
import com.eastcompany.eastsub.eLogics.stick.StickManager;
import com.eastcompany.eastsub.eLogics.stick.display.StickDisplayListener;
import com.eastcompany.eastsub.eLogics.stick.posselecter.SelectionManager;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class ELogics extends JavaPlugin {

    private static ELogics eLogics;
    private StickManager stickManager;
    private AIManager aiManager;
    public static boolean debug = false;
    public SelectionManager selectionManager;
    private PackManager packManager;

    @Override
    public void onEnable() {
        eLogics = this;
        saveDefaultConfig();
        try {
            setupStick();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            setupPack();
        } catch (Exception e) {
            e.printStackTrace();
        }
        new HideShowCommandExecutor(this);
    }

    @Override
    public void onDisable() {
        if (aiManager != null) {
            aiManager.terminate();
        }
        PacketEvents.getAPI().terminate();
    }

    private void setupStick(){
        selectionManager = new SelectionManager();

        StickManager stickManager = new StickManager(this);
        getServer().getPluginManager().registerEvents(new StickListener( stickManager, this), this);
        new StickCommandExecutor(this,stickManager ,selectionManager);
        getLogger().info("E-Logics: All modules initialized successfully.");
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();

            this.aiManager = new AIManager(this);
        getServer().getPluginManager().registerEvents(new StickDisplayListener(this), this);
        new ELogicAICommandExecutor(this);
    }
    private void setupPack(){
        this.packManager = new PackManager(this);
        this.packManager.calculateHash();

        // PaperのLifecycleEventManagerを使用してコマンドを登録
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final var commands = event.registrar();
            // インスタンスを渡して登録
            new ResourceCommand(this, packManager).register(commands);
        });
    }

    public PackManager getPackManager() {
        return packManager;
    }

    public StickManager getStickManager() {
        return stickManager;
    }

    public AIManager getAIManager() {
        return  aiManager;
    }

    public SelectionManager getSelectionManager() {
        return  selectionManager;
    }

    public static ELogics getInstance(){
        return eLogics;
    }
}
