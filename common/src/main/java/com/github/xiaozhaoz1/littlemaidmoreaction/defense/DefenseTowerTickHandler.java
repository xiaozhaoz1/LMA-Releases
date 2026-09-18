package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if 1.20.1 {
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//?}

/**
 * 防御塔服务端 tick 驱动 (v79.62.3) — 每 20t 轮询加载区块内塔 BE 的 towerTick。
 *
 * <p>全局节流: 只在 {@link DefenseGarageKitBlockEntity#TICK_INTERVAL} 倍数 tick 处理;
 * 每轮仅遍历已加载塔 (通过 level.getChunkSource 无法枚举 BE, 用轻量方案:
 * BE 自己判断是否到轮 — 见 towerTick 内部冷却; 本 handler 只是每 20t 触发一轮扫描)。
 *
 * <p>实际驱动 = {@link ServerLevel#tickBlockEntities} 之外的自注册回调 (BE 不实现 EntityTick,
 * 全局单点驱动避免每 BE 独立 tick 注册); 塔 BE 在区块卸载时自然停止 (不扫描即休眠)。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class DefenseTowerTickHandler {

    private static int tickCounter = 0;

    private DefenseTowerTickHandler() {}

//? if 1.20.1 {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick(event.getServer().overworld());
    }
//?} else {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer().overworld());
    }
//?}

    /** 每 20t 一轮: 遍历所有维度已加载塔 */
    private static void tick(ServerLevel overworld) {
        // v79.62.3: 每 tick 触发 — 节流移到 BE 内部 (扫描 20t + 射击冷却独立), 怪 1s 内必被扫到
        if (overworld == null || overworld.getServer() == null) return;
        for (ServerLevel level : overworld.getServer().getAllLevels()) {
            // 已加载区块的塔 BE 枚举: 通过 ChunkMap 不可直接枚举 — 用 POI 无; 简化:
            // 塔 BE 无 EntityTick, 但可通过 level.getBlockEntities 按需? 性能不允许全量。
            // 实际实现: 塔方块 EntityBlock 不注册 tick; 本 handler 维护 WeakReference 注册表 —
            // 由 DefenseGarageKitBlockEntity 构造/卸载时自登记 (见 BE 静态 register/unregister)。
            DefenseGarageKitBlockEntity.tickLoaded(level);
        }
    }
}
