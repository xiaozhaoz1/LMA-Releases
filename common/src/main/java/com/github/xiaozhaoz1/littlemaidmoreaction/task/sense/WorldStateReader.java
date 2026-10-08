package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 世界状态数值读取 (v77.6 移植自 Numen GetWorldInfoTool) — 维度/tick/天气摘要。
 *
 * <p>EnvSense 只有 18 个边沿信号; 本读取器补数值面 (LLM/AI 决策需要具体数值)。
 * 纯读取, 无副作用。
 *
 * <p>v79.50b (P-13): isDay/isRaining/isThundering 3 方法与
 * {@code vanilla/input/world/WorldStateReader} 重复且全项目零引用 → 删;
 * describe() 保留 (GetWorldInfoTool 调用), 天气判定内联不依赖被删方法。
 */
public final class WorldStateReader {

    private WorldStateReader() {}

    /** 世界状态摘要 (AI 可读文本) */
    public static String describe(ServerLevel level, BlockPos pos) {
        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();
        boolean day = !level.isNight();
        return String.format("dimension=%s tick=%d day=%s weather=%s",
                level.dimension().location(),
                level.getGameTime(),
                day ? "day" : "night",
                thundering ? "thunder" : (raining ? "rain" : "clear"));
    }
}
