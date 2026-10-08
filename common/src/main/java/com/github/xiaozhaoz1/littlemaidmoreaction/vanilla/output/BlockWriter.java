package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 方块写原语 (写侧; 2026-09-21 新建 — io 判定第 1 批, 见 build-logs/LIST-io-primitive-gap.md)。
 *
 * <p><b>为什么存在</b>: `vanilla/output` 原有 11 类全是 实体/容器/物品/声音, **没有任何方块写原语** ✗ ⇒
 * service/pipeline 只能直接 `world.setBlock(...)` (实测 12 处跨 4 文件: MLG 自救 · 空置域 · 农田 · 填坝) ⇒
 * 本次收口到本原语 (铁律 §1: `task/pipeline` 不放 IO 原语 ⇒ 委托 vanilla ✓)。
 *
 * <p><b>为什么只有两个方法</b> (错题 #183: 契约工具 0 调用方 = 写侧漂移温床 ✗):
 * 只提供**有真实调用方**的档位 —— 实测 12 处全部要求"常规放置语义" (邻居更新 + 发客户端, 即 flag 3);
 * "静默写/不触发物理" (flag 2) **无调用方** ⇒ 刻意不加 ✓ (需要时按错题纪律先有用例再补)。
 *
 * <p><b>flag 依据</b> (联网核实): `Block.UPDATE_NEIGHBORS|UPDATE_CLIENTS` = 3 ⇒ 常规放置;
 * flag 1 只更新不broadcast ⇒ 会导致"隐形方块" ✗ (社区实证), 故本原语固定用 3 ✓。
 */
public final class BlockWriter {

    private BlockWriter() {}

    /** 放置方块 (常规语义: 邻居更新 + 发客户端 = flag 3) */
    public static void place(Level level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
    }

    /** 清成空气 (常规语义 flag 3; 无掉落 — 与原 `world.destroyBlock(pos, false)` / `setBlock(..., AIR, 3)` 等价口径) */
    /** @return 是否真的改变了方块状态 (等价原 `destroyBlock` 的"是否移除"口径) */
    public static boolean clear(Level level, BlockPos pos) {
        return level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
    }
}
