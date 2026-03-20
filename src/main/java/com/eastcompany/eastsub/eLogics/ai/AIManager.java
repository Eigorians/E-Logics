package com.eastcompany.eastsub.eLogics.ai;

import com.eastcompany.eastsub.eLogics.ELogics;
import com.google.gson.*;
import okhttp3.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AIManager {
    private final ELogics plugin;
    private final OkHttpClient client;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String API_URL = "https://api.x.ai/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public AIManager(ELogics plugin) {
        this.plugin = plugin;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();
    }

    public CompletableFuture<List<CommandAnalysis>> analyzeChain(List<String> commands, int mainIndex) {
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = plugin.getConfig().getString("ai.api-key", "");
            String model = plugin.getConfig().getString("ai.model", "grok-3-mini");

            StringBuilder listText = new StringBuilder();
            for (int i = 0; i < commands.size(); i++) {
                listText.append(i).append(": ").append(commands.get(i)).append("\n");
            }

            String systemPrompt = """
                    あなたはMinecraftの仕様解析エンジンです。コマンドを解析し、以下のJSON配列のみを返してください。
                    
                    ## 命名規則 (feature):
                    - 何をどうするか特徴をとらえて命名してください。
                    - 例: playsound minecraft:ambient.xxx → 「環境音: [音の種類]」
                    - 例: effect give @p speed → 「移動速度上昇バフ」
                    
                    [
                      {
                        "feature": "15文字以内の具体的な識別名",
                        "logic": "具体的な処理内容・メモ書きのように"
                      }
                    ]""";

            JsonObject json = new JsonObject();
            json.addProperty("model", model);
            JsonArray messages = new JsonArray();

            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", systemPrompt);
            messages.add(sys);

            JsonObject usr = new JsonObject();
            usr.addProperty("role", "user");
            usr.addProperty("content", "解析対象リスト:\n" + listText.toString());
            messages.add(usr);

            json.add("messages", messages);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(gson.toJson(json), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    plugin.getLogger().warning("API Error Code: " + response.code());
                    return null;
                }
                if(response.body() == null)return null;

                String rawBody = response.body().string();

                // デバッグモード時のみ生のレスポンスを表示
                if (ELogics.debug) {
                    plugin.getLogger().info("===== [DEBUG] API RAW RESPONSE =====");
                    plugin.getLogger().info(rawBody);
                }

                JsonObject resJson = JsonParser.parseString(rawBody).getAsJsonObject();
                String content = resJson.getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();

                if (ELogics.debug) {
                    plugin.getLogger().info("===== [DEBUG] AI CONTENT FIELD =====");
                    plugin.getLogger().info(content);
                }

                int start = content.indexOf("[");
                int end = content.lastIndexOf("]");

                if (start == -1 || end == -1) {
                    plugin.getLogger().warning("JSON配列の開始'[' または 終了']' が見つかりませんでした。");
                    return null;
                }

                String jsonPart = content.substring(start, end + 1);

                if (ELogics.debug) {
                    plugin.getLogger().info("===== [DEBUG] EXTRACTED JSON ARRAY =====");
                    plugin.getLogger().info(jsonPart);
                }

                JsonArray array = JsonParser.parseString(jsonPart).getAsJsonArray();
                List<CommandAnalysis> results = new ArrayList<>();
                for (JsonElement el : array) {
                    results.add(gson.fromJson(el, CommandAnalysis.class));
                }
                return results;

            } catch (Exception e) {
                // エラー自体は重要なのでデバッグモードに関わらず出力するが、
                // スタックトレースはデバッグ時のみにするなどの調整
                plugin.getLogger().severe("解析中に深刻なエラーが発生しました: " + e.getMessage());
                if (ELogics.debug) {
                    e.printStackTrace();
                }
                return null;
            }
        });
    }

    public void terminate() {
        // 1. 待機中のリクエストをすべてキャンセル
        client.dispatcher().cancelAll();

        // 2. スレッドプールをシャットダウン
        client.dispatcher().executorService().shutdownNow();

        // 3. 接続プールを空にする
        client.connectionPool().evictAll();

        plugin.getLogger().info("AIManager connection threads have been terminated.");
    }

    // 外部からClientを触る必要がある場合（念のため）
    public OkHttpClient getClient() {
        return client;
    }
}