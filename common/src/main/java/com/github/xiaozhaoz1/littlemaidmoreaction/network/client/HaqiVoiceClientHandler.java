package com.github.xiaozhaoz1.littlemaidmoreaction.network.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.client.PecoHaqiSoundPlayer;
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
 * 对主人哈气语音 — 客户端播放处理 (2026-09-21 由 {@code HaqiOwnerVoicePacket} 外移)。
 *
 * <p><b>为什么独立成类</b>: 包定义 (record + 编解码 + 发送) 不应把
 * {@code net.minecraft.client.*} 与客户端声音播放逻辑带进同文件 ——
 * 「服务端包引客户端」属层边界问题 (network → client)。
 * 现由本类 (客户端专用子包, 与 {@code defense/client}、{@code compat/create/client} 同模式)
 * 承担播放, 包定义只留数据 + 发送/处理入口。
 *
 * <p>服务端不能直接播 peco 包声音 (ogg 只在客户端文件系统, TLM 自定义声音包机制),
 * 也无法指定文件 (TLM {@code SoundCache.getBuffer} 全随机) ⇒ 服务端只传 maidId + volume,
 * 本类按 11 文件子集随机读取并播放。
 *
 * <p>行为与迁移前逐字一致: 无世界 (主菜单/未进服) 或 maidId 找不到实体 ⇒ **静默跳过**。
 */
@OnlyIn(Dist.CLIENT)
public final class HaqiVoiceClientHandler {

    private HaqiVoiceClientHandler() {}

    /** 客户端: 按 maidId 定位女仆并播放 peco 声音包 idle 子集 */
    public static void play(int maidId, float volume) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.getEntity(maidId) instanceof EntityMaid maid) {
            PecoHaqiSoundPlayer.play(maid, volume);
        }
    }
}
