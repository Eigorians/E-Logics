package com.eastcompany.eastsub.eLogics.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;

import java.util.Set;

public class CommandHighlighter {

    private static final Set<String> COMMAND_NAMES = Bukkit.getCommandMap().getKnownCommands().keySet();

    public static TextComponent format(String command) {
        if (command == null || command.isEmpty()) return Component.empty();

        TextComponent.Builder builder = Component.text();
        int length = command.length();
        StringBuilder buffer = new StringBuilder();
        int tokenIndex = 0;

        for (int i = 0; i < length; i++) {
            char c = command.charAt(i);

            // JSON/NBTの開始({)を見つけたら、バッファを書き出してからJSONとして処理
            if (c == '{' || (c == '[' && !isAfterAt(command, i))) {
                // 溜まっていたバッファを通常のトークンとして処理
                if (buffer.length() > 0) {
                    builder.append(formatToken(buffer.toString(), tokenIndex++));
                    buffer.setLength(0);
                }

                // 閉じ括弧までの範囲を特定してJSONパースに投げる
                int end = findMatchingBracket(command, i);
                if (end != -1) {
                    String jsonPart = command.substring(i, end + 1);
                    builder.append(CommandJsonParser.parseAndHighlight(jsonPart));
                    i = end;
                    continue;
                }
            }

            // スペース区切り
            if (c == ' ') {
                if (buffer.length() > 0) {
                    builder.append(formatToken(buffer.toString(), tokenIndex++));
                    buffer.setLength(0);
                }
                builder.append(Component.text(" ", NamedTextColor.WHITE));
            } else {
                buffer.append(c);
            }
        }

        if (buffer.length() > 0) {
            builder.append(formatToken(buffer.toString(), tokenIndex));
        }

        return builder.build();
    }

    private static Component formatToken(String token, int index) {
        String lowerToken = token.toLowerCase();

        // 1. run は黄色
        if (lowerToken.equals("run")) {
            return Component.text(token, NamedTextColor.YELLOW);
        }
        // 2. minecraft: 始まりは濃い緑
        if (lowerToken.startsWith("minecraft:")) {
            return Component.text(token, NamedTextColor.LIGHT_PURPLE);
        }
        if (isMaterial(lowerToken.toUpperCase())) {
            return Component.text(token, NamedTextColor.LIGHT_PURPLE);
        }
        // 3. セレクター (@e[type=arrow,...]) の詳細解析
        if (token.startsWith("@")) {
            return formatSelector(token);
        }

        // --- 以下、既存のロジック ---
        TextColor color = NamedTextColor.WHITE;
        if (token.startsWith("/")) {
            String cmd = token.substring(1).toLowerCase();
            if (COMMAND_NAMES.contains(cmd)) color = NamedTextColor.YELLOW;
        } else if (COMMAND_NAMES.contains(lowerToken)) {
            color = NamedTextColor.YELLOW;
        } else if (token.contains("~") || token.contains("^") || token.matches("-?\\d+(\\.\\d+)?")) {
            color = NamedTextColor.GREEN;
        }

        return Component.text(token, color);
    }

    /**
     * セレクター文字列を解析し、type=の値などを色分けする
     */
    private static Component formatSelector(String selector) {
        // 括弧がない場合はそのまま水色
        if (!selector.contains("[")) {
            return Component.text(selector, NamedTextColor.AQUA);
        }

        TextComponent.Builder builder = Component.text();
        // 例: @e[type=arrow,nbt={...}]
        // まず @e[ までを切り出し
        int bracketIndex = selector.indexOf('[');
        builder.append(Component.text(selector.substring(0, bracketIndex + 1), NamedTextColor.AQUA));

        // 引数部分 (type=arrow,nbt={...})
        String content = selector.substring(bracketIndex + 1, selector.length() - 1);

        // カンマで分割（ただしNBT内のカンマで割らないよう注意が必要ですが、
        // 簡易的には "=" の前後で判定します）
        String[] parts = content.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                builder.append(Component.text(kv[0] + "=", NamedTextColor.AQUA));

                // "type=" の後であれば赤、それ以外は水色（または白）
                if (kv[0].trim().equalsIgnoreCase("type")) {
                    builder.append(Component.text(kv[1], NamedTextColor.LIGHT_PURPLE));
                } else {
                    builder.append(Component.text(kv[1], NamedTextColor.AQUA));
                }
            } else {
                builder.append(Component.text(part, NamedTextColor.AQUA));
            }

            if (i < parts.length - 1) {
                builder.append(Component.text(",", NamedTextColor.WHITE));
            }
        }

        builder.append(Component.text("]", NamedTextColor.AQUA));
        return builder.build();
    }


    private static boolean isAfterAt(String str, int index) {
        // @e[ のようなセレクターの括弧かどうかを判定
        if (index == 0) return false;
        for (int i = index - 1; i >= 0; i--) {
            char c = str.charAt(i);
            if (c == '@') return true;
            if (c == ' ') return false;
        }
        return false;
    }

    private static boolean isMaterial(String name) {
        try {
            // EnumのvalueOfで判定。大文字にする必要があるため
            return org.bukkit.Material.valueOf(name.toUpperCase()) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int findMatchingBracket(String str, int start) {
        int depth = 0;
        char open = str.charAt(start);
        char close = (open == '{') ? '}' : ']';

        for (int i = start; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}