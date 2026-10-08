package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 环境扫描器 (v37→v63→v79.6x) — 世界读原语已全部迁 vanilla (B 批分层收口):
 * scanEntities+CAT_*→EntityScanner / structuresAt→StructureScanCache /
 * biome 摘要→WorldStateReader / direction* 死 API 删 (v79.62: scanSnowBlocks 随
 * snow_shovel 移除)。
 * 本类收敛为 {@link EnvSnapshot.WorldInfo} 快照组装 (EnvRules 信号阈值属 task 域, 留此)。
 */
public final class EnvScanner {

    private EnvScanner() {}

    /** 读取世界状态快照（轻量 — 温度/天气/时间直读，无方块遍历） */
    public static EnvSnapshot.WorldInfo readWorld(ServerLevel level, BlockPos center) {
        // v79.61x: WorldInfoCache 分区缓存 — per-dim 天气/时间 (每轮 1 次) + per-chunk
        // 温度/亮度/biome (同区块女仆共享) — 广播计算 N×readWorld → 1×+快照组装
        return WorldInfoCache.get(level, center);
    }
}
