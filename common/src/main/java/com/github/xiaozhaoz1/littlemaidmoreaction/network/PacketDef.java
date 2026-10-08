package com.github.xiaozhaoz1.littlemaidmoreaction.network;

/**
 * 网络包注册清单项 (批次 A — 共享包清单, R-03/R-04 根治).
 *
 * <p>双平台注册的<strong>单一事实源</strong>: id (forge SimpleChannel) / name (诊断名 =
 * 平台驱动注册 map 键) / type / direction 全在此声明一次。forge 与 neoforge 驱动循环消费
 * {@link PacketRegistry#DEFS}, 不再各自硬编码 (v79.51 前: forge 14 条 registerMessage 手写
 * + neoforge 16 条 playTo 手写, 仅注释对应 — 新包漏注册/不对称风险)。</p>
 *
 * <p>平台驱动侧 (forge: {@code ForgePacketRegistrar} / neoforge: {@code NeoNetworkHandler})
 * 各自持有 name → 类型化注册 lambda (含 handler 分派 — 双平台 handler 签名不同, common 不可
 * 引平台类型, 故 handler/codec 引用留平台驱动侧)。</p>
 *
 * @param id        forge SimpleChannel ID (唯一; neoOnly 项无 forge ID 用 -1)
 * @param name      诊断名 = 驱动注册 map 键 (唯一; 与 neoforge TYPE 字符串一致, 便于对照)
 * @param type      包类
 * @param direction 方向 (forge 注册方向据此; neoforge 据此选 playToClient/playToServer)
 * @param neoOnly   true = 仅 neoforge 注册 (Numen 兼容模块 — forge 节点编译时剥离该类)
 */
public record PacketDef(int id, String name, Class<?> type, Direction direction, boolean neoOnly) {

    public enum Direction {
        /** 客户端 → 服务端 (forge: PLAY_TO_SERVER) */
        C2S,
        /** 服务端 → 客户端 (forge: PLAY_TO_CLIENT) */
        S2C
    }

    /** forge 可见 = 非 neoOnly (驱动循环跳过 neoOnly 项) */
    public boolean forgeVisible() {
        return !neoOnly;
    }
}
