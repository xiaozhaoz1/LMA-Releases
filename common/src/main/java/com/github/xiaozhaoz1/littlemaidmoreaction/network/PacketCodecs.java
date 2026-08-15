package com.github.xiaozhaoz1.littlemaidmoreaction.network;

//? if !1.20.1 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.BiConsumer;
import java.util.function.Function;
//?}

/**
 * StreamCodec 工厂 (批次 A3) — neoforge STREAM_CODEC 样板收敛: 双平台统一的 static
 * encode/decode 签名 (NetworkCodecRoundTripTest 实证) → 1 行 StreamCodec。
 *
 * <p>纯委托 — 逐字节走 encode/decode 本体 (协议字节不变, 测试依赖零影响)。
 * 1.20.1 无 StreamCodec (1.20.5+ 概念) — 全类 stonecutter !1.20.1 分支。</p>
 */
//? if !1.20.1 {
public final class PacketCodecs {

    /** 双平台 static encode/decode 引用 → StreamCodec (参数序: encode(msg, buf) / decode(buf)) */
    public static <T> StreamCodec<ByteBuf, T> wrap(BiConsumer<T, FriendlyByteBuf> encode,
                                                   Function<FriendlyByteBuf, T> decode) {
        return StreamCodec.of(
                (ByteBuf buf, T msg) -> encode.accept(msg, (FriendlyByteBuf) buf),
                (ByteBuf buf) -> decode.apply((FriendlyByteBuf) buf));
    }

    private PacketCodecs() {
    }
}
//?} else {
//?}
