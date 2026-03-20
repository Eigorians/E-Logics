package com.eastcompany.eastsub.eLogics.resourcepack;

import com.eastcompany.eastsub.eLogics.ELogics;
import org.bukkit.Bukkit;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HexFormat;

public class PackManager {
    private final ELogics plugin;
    private String url;
    private byte[] hash;

    public PackManager(ELogics plugin) {
        this.plugin = plugin;
    }

    // URLを整形して保存・ハッシュ計算するメソッド
    public void setAndCalculate(String rawUrl) {
        String processedUrl = rawUrl;

        // GitHub の変換
        if (processedUrl.contains("github.com") && processedUrl.contains("/blob/")) {
            processedUrl = processedUrl.replace("github.com", "raw.githubusercontent.com")
                    .replace("/blob/", "/");
        }
        // Gigafile の変換
        else if (processedUrl.contains("gigafile.nu")) {
            int lastSlashIndex = processedUrl.lastIndexOf("/");
            if (lastSlashIndex != -1) {
                String domain = processedUrl.substring(0, lastSlashIndex);
                String fileKey = processedUrl.substring(lastSlashIndex + 1);
                processedUrl = domain + "/download.php?file=" + fileKey;
            }
        }
        // Google Drive の変換
        else if (processedUrl.contains("drive.google.com")) {
            // URLからファイルIDを抽出する (d/ と /view の間)
            try {
                String fileId = "";
                if (processedUrl.contains("/d/")) {
                    int start = processedUrl.indexOf("/d/") + 3;
                    int end = processedUrl.indexOf("/", start);
                    if (end == -1) end = processedUrl.length();
                    fileId = processedUrl.substring(start, end);
                } else if (processedUrl.contains("id=")) {
                    int start = processedUrl.indexOf("id=") + 3;
                    int end = processedUrl.indexOf("&", start);
                    if (end == -1) end = processedUrl.length();
                    fileId = processedUrl.substring(start, end);
                }

                if (!fileId.isEmpty()) {
                    processedUrl = "https://docs.google.com/uc?export=download&id=" + fileId;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Google Drive URL の解析に失敗しました。");
            }
        }

        this.url = processedUrl;
        plugin.getConfig().set("resource-pack.url", this.url);
        plugin.saveConfig();

        calculateHash();
    }

    public void calculateHash() {
        this.url = plugin.getConfig().getString("resource-pack.url");
        if (url == null || url.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL downloadUrl = new URL(url);
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                try (InputStream is = downloadUrl.openStream()) {
                    byte[] buffer = new byte[1024];
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        digest.update(buffer, 0, n);
                    }
                }
                this.hash = digest.digest();
                String hexHashStr = HexFormat.of().formatHex(hash);
                plugin.getConfig().set("resource-pack.hash", hexHashStr);
                plugin.saveConfig();
                plugin.getLogger().info("ResourcePack Hash calculated: " + hexHashStr);
            } catch (Exception e) {
                plugin.getLogger().severe("Hash calculation failed: " + e.getMessage());
            }
        });
    }

    public String getUrl() { return url; }
    public byte[] getHash() { return hash; }
}