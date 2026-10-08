package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSenseBroadcaster;
import net.minecraft.server.level.ServerLevel;
//? if 1.20.1 {
import net.minecraftforge.event.TickEvent;
//?} else {
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

/**
 * v53: 通用 game-tick 驱动.
 * v61: 新增被动任务 tick (与主动任务并行).
 * v63: 新增 EnvSense 全局广播 (200tick 节流).
 * v64: 迁移 TaskEngine — TLM_SWITCH/GUI_INIT/超时看门狗 (每tick处理).
 * v79: 变薄 — 单次实体遍历, 主动/被动流程集中到 {@link GameTickPipelineManager}
 * (心跳节流 20t/看门狗容忍度/被动预算轮转)。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class TaskTickHandler {

    /** 上次广播 tick — per-dimension 节流 (静态单值多维度共享 = 轮替饥饿) */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Long>
            NEXT_BROADCAST = new java.util.HashMap<>();
    /** 对账节流: 维度 → 上次全量补册的 gameTime (每 100t 一次, 见 onServerTick 注释) */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Long> lastReconcileTick =
            new java.util.concurrent.ConcurrentHashMap<>();

    private TaskTickHandler() {}

    @SubscribeEvent
//? if 1.20.1 {
    public static void onServerTick(TickEvent.ServerTickEvent event) {
//?} else {
    public static void onServerTick(ServerTickEvent.Post event) {
//?}
//? if 1.20.1 {
        if (event.phase != TickEvent.Phase.END) return;
//?}
        // 扫描任务集中调度 (全维度共享预算, 每服务端 tick 一次)
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.scan.ScanScheduler
                .tick(event.getServer().getTickCount());
        for (ServerLevel sl : event.getServer().getAllLevels()) {
            long now = sl.getGameTime();
            // 被动清单每 level hoist 一次 (原每女仆新建 Stream 过滤)
            var passives = TaskRegistry.passiveTasksList();
            // ★ v79.66 性能修复 (线程转储实证卡死): 原 `sl.getAllEntities()` 按实体 section 遍历全实体 —
            //   实体多/分散时 (实测 gametest 118 结构相距数百万格) 主线程 RUNNABLE 烧 CPU 数分钟 ⇒ 套件卡死 ✗
            //   ⇒ 改为只遍历**在册女仆** (MaidIndex, O(女仆)) ✓ 真实服务器同样受益 (实体越多 mod 越卡 → 已修)
            // ★ v79.66g 补齐 (漏册回归): 索引靠 join 事件维护, 实测有生成路径会漏 ⇒ 漏册女仆**不被 tick**
            //   ⇒ 其用例只能跑到满超时 (整轮从 50s 涨到 8 分钟+) ✗ ⇒ **每 600t(30s) 全量对账一次**补册
            //   (每 tick 全扫是卡死主因; 1/100 频率的开销可接受 ✓; 漏册最坏晚 5s 被补齐)
            if (lastReconcileTick.getOrDefault(sl.dimension(), 0L) + 600L <= now) {
                lastReconcileTick.put(sl.dimension(), now);
                MaidIndex.reconcile(sl);
            }
            for (EntityMaid maid : MaidIndex.snapshot(sl)) {
                // 主动+被动合并单次遍历 (原双循环 — 无跨女仆耦合, 行为等价)
                GameTickPipelineManager.tickActive(sl, maid, now);
                GameTickPipelineManager.tickPassiveFor(sl, maid, passives, now);
                // v79.61x 脱管线: 跨 tick 动作型独立心跳 (temp/torch — 管线内节流自持)
                GameTickPipelineManager.tickStandalonePassives(sl, maid, passives);
                // v79.61x 摔落自救预触发 (掉血事件通道外 — 摔落中启动, 落地掉血前放水;
                // 便宜判定先行零背包扫描, 主循环内联零额外遍历)
                com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.SelfRescueTrigger.tryTrigger(maid);
                // v79.66o 偷吃 Token (用户裁定): 每 6000t 掷 5% 偷主人身上整组 Token —
                //   门控从最便宜开始 (配置 → 冷却)；冷却未到时**零世界访问** ✓
                // 2026-09-21 家族归位配套: 引擎不再点名 bauble 家族, 改走 sense 触发口
                //   (token 家族已归位 `bauble/token/`; 与 SelfRescueTrigger/HaqiTrigger 同款形态 ✓)
                com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.TokenStealTrigger.tick(sl, maid, now);
                // 拉拽看门狗 (NavWatchdog) 删 — 只用 TLM 寻路, 不干预导航
            }
            // 走路全 TLM — 无自研执行器 (PathExecutor.sweep 退役)
            // 哈气独立触发 (不依赖 EnvSense 广播 — 每 20t)
            com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiTrigger.tick(sl);
            // EnvSense 全局广播 (自节流 200tick)
            tickBroadcast(sl);
        }
    }

    /**
     * 服务器停止 (保存前) — 全女仆 PL 内存态落盘。
     * 被动任务无心跳 flush 兜底, 强制关闭场景靠本事件补齐 (TLM 参考: 实体 save 钩子时机,
     * 但 Forge/NeoForge 无实体保存事件, ServerStopping 为最近等价点)。
     */
    @SubscribeEvent
//? if 1.20.1 {
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
//?} else {
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
//?}
        for (ServerLevel sl : event.getServer().getAllLevels()) {
            // v79.61x: 世界关闭 → 清维度级世界缓存 (WorldInfoCache/BlockPatternCache 按维度键,
            // 防重开维度/多世界残留旧维度 map; 懒清理兜底但维度级需显式收口)
            String dimKey = sl.dimension().location().toString();
            com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.WorldInfoCache.clearDimension(dimKey);
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache.clearDimension(dimKey);
            for (var e : sl.getAllEntities()) {
                if (e instanceof EntityMaid maid) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.flushAllPl(maid);
                }
            }
        }
        // EnvSense 广播节流跨 session 残留修复 — 重启后 gameTime 归零, 旧值会把
        // 广播压到数天 (旧值追平); 停止时清空, 新会话立即恢复广播节奏
        NEXT_BROADCAST.clear();
        // v79.66: 停服清在册女仆索引 (防跨世界/跨 session 残留)
        MaidIndex.clearAll();
        lastReconcileTick.clear();
    }

    /** EnvSense 广播 — 按 config 间隔节流 (每维度独立节流, 防跨维度共享压榨) */
    private static void tickBroadcast(ServerLevel sl) {
        long now = sl.getGameTime();
        Long next = NEXT_BROADCAST.get(sl.dimension());
        if (next != null && now < next) return;
        int interval = PassiveTaskConfig.ENV_SCAN_INTERVAL.get();
        NEXT_BROADCAST.put(sl.dimension(), now + interval);
        EnvSenseBroadcaster.broadcast(sl);
    }
}
