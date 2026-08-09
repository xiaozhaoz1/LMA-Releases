package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 动画文件同步事件 (v79.18, forge) — 玩家加入 → 服务器推送全部自定义动画文件 (S2C)。
 * 专用服务器场景: 服务器 config/animations/ 的自定义文件客户端没有, 加入时补齐。
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class AnimFileSyncEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AnimFileSyncPacket.pushAllTo(player);
        }
    }

    private AnimFileSyncEvents() {}
}
