package com.github.xiaozhaoz1.littlemaidmoreaction.task.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 成就进度读取门面 (M1) — 双平台差异消化在这里, UI 零平台分支。
 *
 * <p>读取当前玩家 (客户端) 的原版成就进度: 1.21.1 走 {@code ClientAdvancements} 内部 progress
 * (经 Listener 回调); 1.20.1 同样走 Listener 但对象是 Advancement。
 * 本门面提供 {@link #isDone(String)} — 用 stonecutter 条件化实现, 上层零平台判断。
 */
public final class QuestProgressReader {

    private QuestProgressReader() {}

    /** 当前客户端玩家是否已完成某成就 (id 如 "minecraft:story/mine_stone") */
    public static boolean isDone(String advancementId) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return false;
        return readProgress(conn, advancementId) != null;
    }

    /** 读进度 (双平台差异) */
    @Nullable
    private static AdvancementProgress readProgress(ClientPacketListener conn, String id) {
        // 1.21.1: conn.getAdvancements().get(ResourceLocation) → AdvancementHolder → getOrStartProgress? 客户端无公开进度 map
        // 1.20.1: conn.getAdvancements().getAdvancement(ResourceLocation) → Advancement → ...
        return null; // 占位 — 双平台实现见 stonecutter 分支
    }
}
