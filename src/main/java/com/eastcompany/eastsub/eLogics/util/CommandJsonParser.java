package com.eastcompany.eastsub.eLogics.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandJsonParser {

    public static Component parseAndHighlight(@NotNull String input) {
        return highlightSyntax(input);
    }

    private static Component highlightSyntax(String json) {
        TextComponent.Builder builder = Component.text();
        List<String> tokens = tokenize(json);

        Deque<JsonState> stack = new ArrayDeque<>();
        stack.push(new JsonState());

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (token.equals("{")) {
                stack.push(new JsonState());
                analyzeObjectStyles(tokens, i, stack.peek());
                builder.append(Component.text(token, NamedTextColor.WHITE));
            }
            else if (token.equals("}")) {
                if (stack.size() > 1) stack.pop();
                builder.append(Component.text(token, NamedTextColor.WHITE));
            }
            else if (token.equals(":") || token.equals(",") || token.equals("[") || token.equals("]")) {
                builder.append(Component.text(token, NamedTextColor.GRAY));
            }
            else {
                if (isJsonKey(tokens, i)) {
                    builder.append(Component.text(token, NamedTextColor.GRAY));
                } else {
                    // --- 値（Value）の処理 ---
                    String lastKey = getPrevKey(tokens, i);
                    JsonState current = stack.peek();

                    // デフォルトは白
                    TextColor color = NamedTextColor.WHITE;

                    // 数値判定 (1b, 1f, 10L, 0.5d, または単なる 100)
                    if (isNumberValue(token)) {
                        color = NamedTextColor.GREEN;
                    }
                    // text系フィールドのスタイル適用
                    else if (isTextField(lastKey)) {
                        color = current.color != null ? current.color : NamedTextColor.WHITE;
                    }
                    // colorフィールド自体のプレビュー
                    else if (lastKey.equalsIgnoreCase("color")) {
                        TextColor mapped = NamedTextColor.NAMES.value(token.replace("\"", "").toLowerCase());
                        if (mapped != null) color = mapped;
                    }

                    TextComponent.Builder t = Component.text().content(token).color(color);
                    if (isTextField(lastKey)) {
                        if (current.bold) t.decoration(TextDecoration.BOLD, true);
                        if (current.italic) t.decoration(TextDecoration.ITALIC, true);
                    }
                    builder.append(t.build());
                }
            }
        }
        return builder.build();
    }

    /**
     * SNBTの数値型サフィックスを考慮した数値判定
     */
    private static boolean isNumberValue(String token) {
        // 1b, 12s, 100L, 0.5f, 1.0d, または単純な 123
        // 大文字小文字を区別せず、数字で始まってサフィックス(bslfd)で終わる、あるいは純粋な数字
        return token.matches("^-?\\d+(\\.\\d+)?[bslfdBSLFD]?$");
    }

    // --- 以下、既存のユーティリティ ---

    private static void analyzeObjectStyles(List<String> tokens, int startIdx, JsonState state) {
        int depth = 0;
        for (int i = startIdx; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equals("{")) depth++;
            if (t.equals("}")) depth--;
            if (depth == 0) break;

            if (depth == 1 && isJsonKey(tokens, i)) {
                String key = t.replace("\"", "").toLowerCase();
                String valueToken = (i + 2 < tokens.size()) ? tokens.get(i + 2) : "";
                String val = valueToken.replace("\"", "");

                switch (key) {
                    case "color" -> state.color = NamedTextColor.NAMES.value(val.toLowerCase());
                    case "bold" -> state.bold = val.equalsIgnoreCase("true");
                    case "italic" -> state.italic = val.equalsIgnoreCase("true");
                }
            }
        }
    }

    private static List<String> tokenize(String json) {
        List<String> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("\"(?:\\\\\"|[^\"])*\"|\\{|\\}|\\[|\\]|:|\\,|[a-zA-Z0-9\\._+-]+").matcher(json);
        while (m.find()) tokens.add(m.group());
        return tokens;
    }

    private static boolean isJsonKey(List<String> tokens, int index) {
        return index < tokens.size() - 1 && tokens.get(index + 1).equals(":");
    }

    private static String getPrevKey(List<String> tokens, int index) {
        if (index >= 2 && tokens.get(index - 1).equals(":")) {
            return tokens.get(index - 2).replace("\"", "");
        }
        return "";
    }

    private static boolean isTextField(String key) {
        return key.equalsIgnoreCase("text") || key.equalsIgnoreCase("name") || key.equalsIgnoreCase("string");
    }

    private static class JsonState {
        TextColor color = null;
        boolean bold = false;
        boolean italic = false;
    }
}