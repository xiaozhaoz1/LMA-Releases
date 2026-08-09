package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 世界状态数值读取 (v77.6 移植自 Numen GetWorldInfoTool) — 维度/tick/亮度/天气。
 *
 * <p>EnvSense 只有 18 个边沿信号; 本读取器补数值面 (LLM/AI 决策需要具体数值)。
 * 纯读取, 无副作用。
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

    public static boolean isDay(ServerLevel level) { return !level.isNight(); }
    public static boolean isRaining(ServerLevel level) { return level.isRaining(); }
    public static boolean isThundering(ServerLevel level) { return level.isThundering(); }
}
