package com.eastcompany.eastsub.eLogics.stick.command;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.data.Directional;
import java.util.HashSet;
import java.util.Set;

public class CommandChainSerializer {

    public static String serialize(Location min, Location max, int[] countOut) {
        StringBuilder sb = new StringBuilder();
        Set<Block> visited = new HashSet<>();
        int count = 0;

        sb.append("{\n  \"chains\": [\n");
        boolean firstChain = true;

        // 起点ブロックの走査
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = min.getWorld().getBlockAt(x, y, z);
                    if (isRoot(block.getType()) && !visited.contains(block)) {
                        if (!firstChain) sb.append(",\n");
                        sb.append("    { \"type\": \"ROOT_START\", \"blocks\": [\n");
                        count += collectChain(block, visited, sb);
                        sb.append("\n    ] }");
                        firstChain = false;
                    }
                }
            }
        }
        sb.append("\n  ]\n}");
        countOut[0] = count;
        return sb.toString();
    }

    private static int collectChain(Block start, Set<Block> visited, StringBuilder sb) {
        int c = 0;
        Block curr = start;
        while (curr != null && isCommandBlock(curr.getType()) && !visited.contains(curr)) {
            visited.add(curr);
            if (curr.getState() instanceof CommandBlock cb && !cb.getCommand().isEmpty()) {
                if (c > 0) sb.append(",\n");
                boolean cond = curr.getBlockData() instanceof org.bukkit.block.data.type.CommandBlock d && d.isConditional();
                String facing = (curr.getBlockData() instanceof Directional d) ? d.getFacing().name() : "UP";

                sb.append(String.format(
                        "      { \"pos\": [%d,%d,%d], \"type\": \"%s\", \"face\": \"%s\", \"cond\": %b, \"cmd\": \"%s\" }",
                        curr.getX(), curr.getY(), curr.getZ(), curr.getType().name(), facing, cond,
                        cb.getCommand().replace("\\", "\\\\").replace("\"", "\\\"")
                ));
                c++;
            }
            curr = (curr.getBlockData() instanceof Directional d) ? curr.getRelative(d.getFacing()) : null;
            if (curr != null && curr.getType() != Material.CHAIN_COMMAND_BLOCK) curr = null;
        }
        return c;
    }

    private static boolean isRoot(Material m) { return m == Material.COMMAND_BLOCK || m == Material.REPEATING_COMMAND_BLOCK; }
    private static boolean isCommandBlock(Material m) { return isRoot(m) || m == Material.CHAIN_COMMAND_BLOCK; }
}