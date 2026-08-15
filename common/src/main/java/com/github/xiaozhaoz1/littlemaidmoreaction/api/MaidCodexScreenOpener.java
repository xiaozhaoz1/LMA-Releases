package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import java.util.Map;

/**
 * 图鉴屏打开门面 (v79.50 修: MaidCodexScreenPacket 字节码引用 Screen → DEDICATED_SERVER
 * RuntimeDistCleaner 拦截 — 主类字节码禁客户端类铁律)。
 * <p>客户端入口注入实现 ({@code LmaForgeClientEntry/LmaNeoForgeClientEntry}),
 * 服务端无注入 (null 安全), 与 {@code LmaNetwork.ISender} 同模式。</p>
 */
public interface MaidCodexScreenOpener {

    /** 客户端: 打开图鉴屏 (击杀计数随包直发, 零 C2S) */
    void openCodex(Map<String, Integer> counts);
}
