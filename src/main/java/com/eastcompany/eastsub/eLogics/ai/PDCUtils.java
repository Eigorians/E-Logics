package com.eastcompany.eastsub.eLogics.ai;

import com.eastcompany.eastsub.eLogics.ELogics;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PDCUtils {
    // 既存のキーに加えて、コマンド保存用のキーを追加
    private static final String KEY_FEATURE = "ai_feature";
    private static final String KEY_LOGIC = "ai_logic";
    private static final String KEY_COMMAND_HASH = "ai_command_content"; // 追加

    public static void saveAnalysis(ELogics plugin, Block block, CommandAnalysis res) {
        if (block.getState() instanceof org.bukkit.block.CommandBlock cb) {
            PersistentDataContainer pdc = cb.getPersistentDataContainer();
            NamespacedKey keyFeature = new NamespacedKey(plugin, KEY_FEATURE);
            NamespacedKey keyLogic = new NamespacedKey(plugin, KEY_LOGIC);
            NamespacedKey keyCmd = new NamespacedKey(plugin, KEY_COMMAND_HASH);

            pdc.set(keyFeature, PersistentDataType.STRING, res.feature);
            pdc.set(keyLogic, PersistentDataType.STRING, res.logic);
            // 現在のコマンド文字列を保存
            pdc.set(keyCmd, PersistentDataType.STRING, cb.getCommand());

            cb.update();
        }
    }

    public static void clearAnalysis(ELogics plugin, Block block) {
        if (block.getState() instanceof TileState state) {
            PersistentDataContainer pdc = state.getPersistentDataContainer();
            pdc.remove(new NamespacedKey(plugin, "ai_feature"));
            pdc.remove(new NamespacedKey(plugin, "ai_logic"));
            state.update();
        }
    }

    public static CommandAnalysis loadAnalysis(ELogics plugin, Block block, String currentCmd) {
        if (block.getState() instanceof org.bukkit.block.CommandBlock cb) {
            PersistentDataContainer pdc = cb.getPersistentDataContainer();

            // 保存されているコマンドを取得
            String savedCmd = pdc.get(new NamespacedKey(plugin, KEY_COMMAND_HASH), PersistentDataType.STRING);

            // 【重要】保存されたコマンドと現在のコマンドが一致しない場合はnullを返す
            if (savedCmd == null || !savedCmd.equals(currentCmd)) {
                return null;
            }

            String feature = pdc.get(new NamespacedKey(plugin, KEY_FEATURE), PersistentDataType.STRING);
            if (feature == null) return null;

            CommandAnalysis analysis = new CommandAnalysis();
            analysis.feature = feature;
            analysis.logic = pdc.get(new NamespacedKey(plugin, KEY_LOGIC), PersistentDataType.STRING);
            return analysis;
        }
        return null;
    }
}