package com.github.xiaozhaoz1.littlemaidmoreaction.network.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

/**
 * 女仆表情气泡 — 客户端**落地转发** (2026-09-21 架构修环: 由"直连 chatbubble"改为 **sink 注册**)。
 *
 * <p><b>为什么要 sink</b> (铁律 §1): `network/` 不放业务、`chatbubble/` 是表现层 (允许 `chatbubble→network`) ⇒
 * 反向边 `network→chatbubble` 会构成包级环 ✗。故本类只做"解析 maidId → 交实体"，
 * 具体构造气泡由 **chatbubble 侧** 在客户端初始化时注册 impl (`chatbubble/MaidEmojiClientSink.install()`),
 * 本层**不 import 表现层** ✓ (同项目"门面 + 实现方自注册"既有模式)。
 *
 * <p>无世界 (主菜单) / 找不到实体 / sink 未注册 (未装客户端初始化) ⇒ 静默跳过 ✓。
 */
@OnlyIn(Dist.CLIENT)
public final class MaidBubbleClientHandler {

    /** 落地接口: 由 chatbubble 侧实现 (byte = 表情类型 id, 避免本层认识表现层类型) */
    @FunctionalInterface
    public interface Sink {
        void apply(EntityMaid maid, byte emojiType);
    }

    private static volatile Sink sink;

    private MaidBubbleClientHandler() {}

    /** 客户端初始化时由 chatbubble 侧调用 (幂等) */
    public static void install(Sink impl) {
        sink = impl;
    }

    /** 客户端: 解析实体后转交已注册的 sink */
    public static void apply(int maidId, byte emojiType) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.getEntity(maidId) instanceof EntityMaid maid && sink != null) {
            sink.apply(maid, emojiType);
        }
    }
}
