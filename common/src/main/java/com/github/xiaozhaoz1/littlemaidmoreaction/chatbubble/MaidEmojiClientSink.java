package com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble;

import com.github.xiaozhaoz1.littlemaidmoreaction.network.client.MaidBubbleClientHandler;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

/**
 * 表情气泡 — **客户端落地实现** (2026-09-21 架构修环引入)。
 *
 * <p><b>作用</b>: 把"构造气泡数据 + 挂到女仆头上"这段**表现层逻辑**从 `network/client/MaidBubbleClientHandler`
 * 收回本层 (铁律 §1: `chatbubble/` 是表现层, 允许 `chatbubble→network`; 反向边会构成包级环 ✗)。
 * 客户端初始化时调用 {@link #install()} 注册进去, 网络层只持一个 `byte emojiType` 的转发接口 ✓。
 *
 * <p><b>调用点</b>: `init/LittleMaidMoreActionExtension` 的客户端初始化 (`onClientSetup`) — 只跑在客户端 ✓;
 * 未注册时 handler 静默跳过 (主菜单/无世界同理) ✓。
 */
@OnlyIn(Dist.CLIENT)
public final class MaidEmojiClientSink {

    private MaidEmojiClientSink() {}

    /** 客户端初始化时调用 (幂等: 重复 install 覆盖同一实现) */
    public static void install() {
        MaidBubbleClientHandler.install((maid, emojiType) ->
                maid.getChatBubbleManager().addChatBubble(
                        MaidEmojiBubbleData.create(MaidEmojiType.byId(emojiType))));
    }
}
