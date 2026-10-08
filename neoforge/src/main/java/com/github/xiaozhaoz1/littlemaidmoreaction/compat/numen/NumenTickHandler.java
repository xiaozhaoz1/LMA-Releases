package com.github.xiaozhaoz1.littlemaidmoreaction.compat.numen;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Numen 假人桥 tick 驱动 (v74) — 每 tick 调 NumenMaidBridge.tick
 * (gate 扫描 → 生命周期 + 镜像同步)。
 */
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class NumenTickHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // ModList 门控: Numen 未装则零开销
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.compat.NumenCompat.isInstalled()) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            try {
                NumenMaidBridge.tick(level);
            } catch (Exception ex) {
                LittleMaidMoreAction.LOGGER.error("[NumenBridge] tick 异常: {}", ex.toString());
            }
        }
    }

    private NumenTickHandler() {}
}
