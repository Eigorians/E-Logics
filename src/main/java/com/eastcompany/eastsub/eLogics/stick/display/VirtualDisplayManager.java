package com.eastcompany.eastsub.eLogics.stick.display;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VirtualDisplayManager implements IDisplayManager{

    // プレイヤーのUUIDをキーにして、表示中のEntityIDリストを保持
    private final Map<UUID, List<Integer>> activeEntities = new ConcurrentHashMap<>();

    /**
     * 指定したプレイヤーの頭上にテキストを表示する
     * @param player 対象プレイヤー
     * @param text 表示したい文字
     */
    public int spawnTextAt(Player player, Location loc, TextComponent text, int lengh) {
        int entityId = ThreadLocalRandom.current().nextInt(200000, 300000);
        UUID entityUuid = UUID.randomUUID();

        // コマンドブロックの中心（+0.5）かつ、少し上（+1.2）に配置
        Vector3d position = new Vector3d(
                loc.getX() + 0.5,
                loc.getY() + 1.2,
                loc.getZ() + 0.5
        );

        // 1. スポーンパケット
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(entityUuid),
                EntityTypes.TEXT_DISPLAY,
                position,
                0f, 0f, 0f, 0,
                Optional.of(new Vector3d(0, 0, 0))
        );

        // 2. メタデータパケット (中身は前のコードと同じ)
        List<EntityData<?>> metadata = new ArrayList<>();

// --- テキスト内容 ---
        metadata.add(new EntityData(23, EntityDataTypes.ADV_COMPONENT,
                text));

// --- 【重要】Billboard設定 (Index 15) ---
// 0: Fixed, 1: Center (常にプレイヤーを向く), 2: Vertical, 3: Horizontal
        metadata.add(new EntityData(15, EntityDataTypes.BYTE, (byte) 3));

// --- 重なり対策：背景色 (Index 25) ---
// 完全透明(0)ではなく、少し黒(例: 0x40000000)を入れると、重なった時に手前の文字が浮き出ます
        metadata.add(new EntityData(25, EntityDataTypes.INT, 0x60000000)); // 不透明度約37%の黒

// --- 文字サイズと改行 ---
        metadata.add(new EntityData(12, EntityDataTypes.VECTOR3F, new com.github.retrooper.packetevents.util.Vector3f(0.5f, 0.5f, 0.5f)));
        metadata.add(new EntityData(24, EntityDataTypes.INT, lengh));

        WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);

        // 送信
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaPacket);

        activeEntities.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(entityId);
        return entityId; // IDを返すように変更
    }
    /**
     * プレイヤーに表示されているすべてのフェイクエンティティを消去する
     */
    public void clearPlayerDisplays(Player player) {
        List<Integer> ids = activeEntities.remove(player.getUniqueId());
        if (ids != null && !ids.isEmpty()) {
            // int[] 型に変換して送信
            int[] idArray = ids.stream().mapToInt(i -> i).toArray();
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(idArray);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroyPacket);
        }
    }

    public void destroySpecificEntity(Player player, int entityId) {
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(entityId);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroy);
    }

    /**
     * 特定のプレイヤーがログアウトした時に呼ぶ（メモリリーク防止）
     */
    public void removePlayerData(UUID uuid) {
        activeEntities.remove(uuid);
    }


}