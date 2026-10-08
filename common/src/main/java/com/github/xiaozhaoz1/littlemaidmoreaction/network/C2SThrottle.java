package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleMath;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S 服务端防刷节流 (NET-H1 修复, v79.50) — 全维度扫描类包 (MaidListQueryPacket /
 * MaidEnvSenseTogglePacket) 到达服务端主线程时, 同 player 每 {@code intervalTicks}
 * 最多放行 1 次, 超频包直接丢弃, 消灭 O(全维度实体数) 遍历的 CPU 放大 DoS。
 *
 * <p>状态存 <b>Player PersistentData</b> (键 {@code lma_c2s_<key>}, 时间戳自过期语义
 * — 残留无害, 无静态 map 无需 MaidUnloadRegistry 登记)。判定复用 {@link ThrottleMath}
 * 纯函数 (last==0 / last>now 时钟回绕 → 放行, 与 ThrottleUtil 语义逐字一致)。
 */
public final class C2SThrottle {

    private static final String PREFIX = "lma_c2s_";

    private C2SThrottle() {}

    /**
     * 是否放行 (放行则更新时间戳)。
     *
     * @param key           节流标识 (如 "maid_list_query" / "envsense_toggle")
     * @param intervalTicks 冷却间隔 (tick)
     * @return true = 放行 (本次调用标记, 下次从此刻起算); false = 冷却中 (调用方丢弃)
     */
    public static boolean allow(ServerPlayer player, String key, long intervalTicks) {
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(PREFIX + key);
        if (ThrottleMath.isCoolingDown(now, last, intervalTicks)) {
            return false;
        }
        player.getPersistentData().putLong(PREFIX + key, now);
        return true;
    }
}
