package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService;
import net.minecraft.server.level.ServerLevel;

/**
 * 偷吃 Token 触发 (2026-09-21 家族归位配套) — 与 {@link SelfRescueTrigger} / {@code HaqiTrigger} 同款
 * "sense 触发口"形态: `TaskTickHandler` 主循环内联调用, 便宜门控先行 (配置 → 冷却未到 ⇒ **零世界访问** ✓)。
 *
 * <p><b>为什么加这一层</b> (用户裁定): token 家族是**饰品家族** (与 `bauble/WildKitsuneMilk` 同形:
 * 饰品 + 绑定 + 物品 + 交互事件), 归位 `bauble/token/` ✓; 其"每 6000t 一次判定"的行为由本触发口驱动
 * ⇒ **引擎 (`task/runtime`) 不再点名具体家族** (与 `SelfRescueTrigger → task/service/harvest`、
 * `JiuhuMilkPipeline → bauble/WildKitsuneMilk` 同一手法 ✓ 不发明新法 ✓)。
 */
public final class TokenStealTrigger {

    private TokenStealTrigger() {}

    /** 单女仆判定 — TaskTickHandler 主循环内联调用 (每 tick; 冷却/门控全部在服务内自持) */
    public static void tick(ServerLevel level, EntityMaid maid, long now) {
        TokenStealService.tick(level, maid, now);
    }
}
