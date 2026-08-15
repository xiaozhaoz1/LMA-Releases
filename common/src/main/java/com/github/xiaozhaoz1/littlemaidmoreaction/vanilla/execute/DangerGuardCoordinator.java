package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * v79.26.7: 危险方块堵护协调器 — 挖矿开脉前检查目标 6 侧液体 (岩浆/水),
 * 有 → 用白名单方块把液体位置堵上再挖 (用户裁定: "岩浆等危险需要堵上方块")。
 *
 * <p>语义移植自 Baritone {@code MineProcess.plausibleToBreak} +
 * {@code MovementHelper.avoidAdjacentBreaking}: 挖的方块邻接液体 (岩浆会流出来烫伤
 * 女仆 / 水会冲走掉落物) 时不可直接挖。Baritone 直接放弃该目标; 用户裁定 LMA 版本
 * 升级为堵方块处理 — 堵住液体源后继续挖 (堵完 → DONE, 调用方开脉)。
 *
 * <p>检查 6 侧 (Baritone 只查上+四侧; LMA 加下方 — 目标矿下方是岩浆池时挖掉矿,
 * 掉落物掉进岩浆烧毁, 先垫住防掉落物损失)。岩浆优先 (烫伤), 水兜底 (冲掉落物)。
 * <p>v79.53 更正: 原"防摔落由 {@link BlockUpCoordinator} 垫柱负责"为死引用 —
 * 垫柱链 v79.26.8e 已删, BlockUpCoordinator 仅剩堵护放置链; 挖穿逐格下落无坠落伤害,
 * 防摔落无独立机制属现状语义。
 *
 * <p>放置链复用 {@link BlockUpCoordinator#placeMaterial} (换主手 + 假人放置 + 工具
 * 恢复 + 诚实消耗); 从液体格任一实心邻格点击朝向液体 (块落液体位置)。
 */
public final class DangerGuardCoordinator {

    /** tick 返回: RUNNING=堵护中 (调用方 CONTINUE) / DONE=已安全 (下轮开脉) / FAILED=放弃 (无方块, 调用方跳过) */
    public enum Phase { RUNNING, DONE, FAILED }

    private static final ConcurrentMap<UUID, State> STATES = new ConcurrentHashMap<>();
    /** 堵护看门狗 (tick) — v79.53: 240→60 对齐跳过集 TTL (原 240 与 SKIP_TTL=60 不匹配:
     *  堵护 240t 超时 FAILED → skip 60t 重试 → 每 60t 循环空转; 6 侧液体最多 6 块 60t 足够) */
    private static final int TIMEOUT = 60;

    private record State(BlockPos target, long startTick) {}

    private DangerGuardCoordinator() {}

    /** 每 tick 驱动 — 调用方 (ChainHarvestExecute.tryStartVein) 开脉前接入 */
    public static Phase tick(ServerLevel world, EntityMaid maid, BlockPos target) {
        UUID uid = maid.getUUID();
        long now = world.getGameTime();
        State st = STATES.get(uid);
        if (st == null || !st.target().equals(target)) {
            st = new State(target, now);
            STATES.put(uid, st);
        }
        if (now - st.startTick() > TIMEOUT) {
            STATES.remove(uid);
            return Phase.FAILED;
        }
        BlockPos danger = findDanger(world, target);
        if (danger == null) {
            // 已无液体 → 安全, 调用方开脉
            STATES.remove(uid);
            return Phase.DONE;
        }
        if (!blockDanger(world, maid, danger)) {
            STATES.remove(uid);
            return Phase.FAILED; // 背包无方块/无实心邻格可点 → 放弃 (调用方跳过目标)
        }
        return Phase.RUNNING; // 堵了 1 个 → 下轮重检 (多液体逐轮堵)
    }

    /** 任务取消/模式切换清理 */
    public static void clear(EntityMaid maid) {
        STATES.remove(maid.getUUID());
    }

    /** 目标 6 侧液体扫描 — 岩浆优先 (烫伤), 水兜底 (冲掉落物); 无 → null */
    @Nullable
    static BlockPos findDanger(ServerLevel world, BlockPos target) {
        BlockPos lava = null;
        BlockPos water = null;
        for (Direction dir : Direction.values()) {
            BlockPos p = target.relative(dir);
            BlockState s = world.getBlockState(p);
            if (s.getFluidState().isEmpty()) continue;
            if (s.getFluidState().is(Fluids.LAVA) || s.getFluidState().is(Fluids.FLOWING_LAVA)) {
                if (lava == null) lava = p;
            } else if (water == null) {
                water = p;
            }
        }
        return lava != null ? lava : water;
    }

    /** 往液体位置放方块 — 从任一实心邻格点击朝向液体 (块落液体格); 液体已被别的东西
     *  占住 (非可替换) = 已堵住 → true (下轮重检消失); 全邻格不可点 → false。
     *  <p>放置复查说明 (P-6): placeMaterial 返回 true 仅表示 useOn 被接受, 不复查目标格 —
     *  若假阳性 (事件吞交互/目标被占), 液体仍在 → 下轮 findDanger 重检继续堵, 240t 看门狗
     *  超时 FAILED (调用方跳过), 自愈语义成立, 无需本层复查。 */
    private static boolean blockDanger(ServerLevel world, EntityMaid maid, BlockPos danger) {
        if (!world.getBlockState(danger).canBeReplaced()) return true;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = danger.relative(dir);
            BlockState ns = world.getBlockState(neighbor);
            if (ns.isAir() || ns.canBeReplaced() || !ns.isSolid()) continue;
            return BlockUpCoordinator.placeMaterial(world, maid, neighbor, dir.getOpposite());
        }
        return false;
    }
}
