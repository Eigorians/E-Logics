package com.eastcompany.eastsub.eLogics.util;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.LinkedList;

public class CommandBlockHistory {
    private static final LinkedList<RemovedCommandBlock> history = new LinkedList<>();
    private static final int MAX_HISTORY = 30;

    public record RemovedCommandBlock(Location location, String command, BlockData blockData) {}

    public static void add(Location loc, String cmd, BlockData data) {
        // 同じ座標の古いデータがあれば消してから追加（重複防止）
        history.removeIf(h -> h.location().equals(loc));
        if (history.size() >= MAX_HISTORY) history.removeFirst();

        history.add(new RemovedCommandBlock(loc, cmd, data));
    }

    public static RemovedCommandBlock findAndRemove(Location loc) {
        for (RemovedCommandBlock record : history) {
            if (record.location().equals(loc)) {
                history.remove(record);
                return record;
            }
        }
        return null;
    }
}