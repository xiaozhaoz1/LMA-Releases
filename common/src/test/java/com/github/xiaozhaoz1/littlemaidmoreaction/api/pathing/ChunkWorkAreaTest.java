package com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ChunkWorkArea 纯函数测试 (v79.62.2 区块工作制) — 工作区块中心计算。
 * BlockPos 纯数据类无注册表 clinit 链, JVM 直测安全 (错题 #174 边界内, 同 NavProgressGuardTest)。
 */
class ChunkWorkAreaTest {

    // ── workChunkCenter (区块 16×16 中心格, Y 透传) ──

    @Test
    void chunkCenterIsMiddleOf16x16Chunk() {
        // 区块 0,0 (0..15) → 中心 (8, y, 8)
        assertEquals(new BlockPos(8, 60, 8), ChunkWorkArea.workChunkCenter(0, 0, 60));
        // 区块 1,1 (16..31) → 中心 (24, y, 24)
        assertEquals(new BlockPos(24, -30, 24), ChunkWorkArea.workChunkCenter(1, 1, -30));
        // 负区块 -1,-1 (-16..-1) → 中心 (-8, y, -8)
        assertEquals(new BlockPos(-8, 70, -8), ChunkWorkArea.workChunkCenter(-1, -1, 70));
        // Y 透传
        assertEquals(new BlockPos(8, 5, 8), ChunkWorkArea.workChunkCenter(0, 0, 5));
        // 认领区块 (5,5) → 中心 (88, y, 88)
        assertEquals(new BlockPos(88, -58, 88), ChunkWorkArea.workChunkCenter(5, 5, -58));
    }
}
