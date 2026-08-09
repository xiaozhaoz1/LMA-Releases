package com.github.xiaozhaoz1.littlemaidmoreaction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 网络发送抽象 (平台中性) — loader 入口构造时注入实现。
 * <p>forge: SimpleChannel + PacketDistributor | neoforge: PayloadRegistrar (阶段 3)</p>
 */
public final class LmaNetwork {
    public interface ISender {
        void sendToServer(Object msg);

        void sendToAll(Object msg);

        void sendToTrackingEntity(Entity entity, Object msg);

        void sendToPlayer(ServerPlayer player, Object msg);
    }

    /** loader 注入; 注入前调用 = 空操作 (仅编译/未初始化期) */
    public static ISender sender = new ISender() {
        @Override
        public void sendToServer(Object msg) {
        }

        @Override
        public void sendToAll(Object msg) {
        }

        @Override
        public void sendToTrackingEntity(Entity entity, Object msg) {
        }

        @Override
        public void sendToPlayer(ServerPlayer player, Object msg) {
        }
    };

    private LmaNetwork() {
    }
}
