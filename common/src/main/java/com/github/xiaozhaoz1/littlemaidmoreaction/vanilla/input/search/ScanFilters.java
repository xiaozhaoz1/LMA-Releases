package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * 常用方块扫描过滤器 (v79.3) — 静态谓词常量, 可 and() 组合。
 */
public final class ScanFilters {

    private ScanFilters() {}

    /** 雪层 (BlockTags.SNOW — SnowLayer/PowderSnow/TopSnow, 与 EnvScanner.scanSnowBlocks 同源) */
    public static final Predicate<BlockState> SNOW =
            s -> s.is(net.minecraft.tags.BlockTags.SNOW);

    /** 红石灯 */
    public static final Predicate<BlockState> REDSTONE_LAMP =
            s -> s.is(Blocks.REDSTONE_LAMP);

    /** 水源 (fluid isSource — Numen ScanBlocksTool 流体 source 标记同义) */
    public static final Predicate<BlockState> WATER_SOURCE =
            s -> s.getFluidState().isSource();

    /** 熔岩源 */
    public static final Predicate<BlockState> LAVA_SOURCE =
            s -> s.getFluidState().is(net.minecraft.world.level.material.Fluids.LAVA)
                    && s.getFluidState().isSource();
}
