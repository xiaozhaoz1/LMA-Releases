package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 女仆卸载统一清理注册表 (v79.29 Phase 1) — 收敛分散在各模块的实体离开清理。
 *
 * <p>静态缓存 (maidId/UUID key) 模块注册清理回调, 实体离开世界时
 * {@link #runAll} 统一执行 (EntityCleanupListener + Extension.ServerEvents 双监听点,
 * 幂等 remove, 双调用无重复副作用)。
 *
 * <p>注册 (static 块): ChainHarvestExecute / PathingApi / GameTickPipelineManager /
 * MaidData PL 缓存 / EnvSenseBroadcaster / TlmTaskMonitor /
 */
public final class MaidUnloadRegistry {

    /**
     * 清理回调表 — {@link java.util.concurrent.CopyOnWriteArrayList}。
     * v79.63 并发收口 (评审 P1-5 同族): 原 ArrayList 的 register 与 runAll 遍历可并发 —
     * 各模块在自己的静态初始化里登记 (EngineGuard / PassiveDispatcher / FarmExecute / 各管线),
     * 类初始化时机由首次使用触发 (可能落在不同线程); 遍历中登记 = CME。
     * 写极少读较多 → CopyOnWrite 正合适 (登记后只遍历)。
     */
    private static final List<Consumer<EntityMaid>> HANDLERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private MaidUnloadRegistry() {}

    static {
        register(maid -> com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute.clearMaidState(maid));
        // v79.63: 改用 clearMaidState (三表全清) — clearNav 故意保留 attempt/CD (超时重试语义),
        // 卸载只清 NAV_START 会让 NAV_ATTEMPT/NAV_CD 永久残留 (错题 #272 同族: 键复用静默串扰)
        register(maid -> com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi.clearMaidState(maid));
        // 错题 P-5: DangerGuard STATES (UUID key) 原只有模式切换/FAILED 两清理点,
        // 堵护中卸载 → 永久残留 — 补卸载登记 (红线 #8 协议)
        register(maid -> com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.DangerGuardCoordinator.clear(maid));
        // v79.58: SelfRescueState 自救上下文 (per-maid 内存态, 卸载清理)
        register(maid -> com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.SelfRescueState.onMaidUnload(maid));
        // v79.62.5: UnstuckCoordinator 已删除 (用户裁定 — 卡住靠 TLM 导航自行传送, 无状态需清理)
        // v79.61x: GMPM 被动 mask 缓存已删 (脱管线) — 冷却表经 registerCache 声明式清理
        register(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData::flushAllPl);
        // 环境感知/白名单/监控/假人 (原 Extension.ServerEvents 手写 4 处)
        // v79.63 键类型统一 (见 task.data.MaidKey): 前两条改传实体 (内部用 UUID 键);
        // 仍保留实体 ID 形参的仅 FakePlayerManager (MC API 热路径例外: 需 level.getEntity(id) 回投;
        // AutoCropHandler 已删 — 原为第 4 个例外, 另 PathingApi.NAV_* 已改为 UUID 键)
        register(maid -> com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSenseBroadcaster.onMaidUnload(maid));
        register(maid -> com.github.xiaozhaoz1.littlemaidmoreaction.adapter.TlmTaskMonitor.onMaidLeave(maid));
        register(maid -> com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.FakePlayerManager.onMaidUnload(maid.getId()));
        // 便携装配背包 CACHE — 已改声明式 registerCache (MaidAssemblyInventory 静态初始化自登记, 批 3c)
    }

    /** 注册清理回调 (模块静态初始化期) */
    public static void register(Consumer<EntityMaid> handler) {
        HANDLERS.add(handler);
    }

    /**
     * 注册 per-maid 键控缓存 — 卸载时自动按键清理 (v79.61 批 3c C3, 声明式清理:
     * 缓存构造期声明即挂卸载, 消灭"新增缓存忘清理"红线)。
     * 仅适用纯 map 型缓存 (键=maid UUID/id); 多键+NBT 语义的清理仍走 {@link #register} 显式登记。
     */
    public static <K, V> Map<K, V> registerCache(Map<K, V> cache, java.util.function.Function<EntityMaid, K> keyOf) {
        register(maid -> cache.remove(keyOf.apply(maid)));
        return cache;
    }

    /** 实体离开世界 — 统一执行全部清理 (幂等) */
    public static void runAll(EntityMaid maid) {
        for (Consumer<EntityMaid> h : HANDLERS) {
            try {
                h.accept(maid);
            } catch (Exception ex) {
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                        "[MaidUnload] 清理异常: {}", ex.toString());
            }
        }
    }
}
