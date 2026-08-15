package com.github.xiaozhaoz1.littlemaidmoreaction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网络发送抽象 (平台中性) — loader 入口构造时注入实现。
 * <p>forge: SimpleChannel + PacketDistributor | neoforge: PayloadRegistrar (阶段 3)
 * <p>M-4: 默认 no-op sender 丢包 WARN 一次 (注入失败诊断); 注入统一走 {@link #setSender} 记状态日志。
 * 独立 logger 名 — 不引 LittleMaidMoreAction.LOGGER (其静态初始化链含 FMLPaths, 纯 JVM 单测
 * 加载本类会连带触发 MC 类加载, 违反单测铁律)。
 */
public final class LmaNetwork {
    public interface ISender {
        void sendToServer(Object msg);

        void sendToAll(Object msg);

        void sendToTrackingEntity(Entity entity, Object msg);

        void sendToPlayer(ServerPlayer player, Object msg);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("littlemaidmoreaction-network");
    /** 丢包 WARN 只打一次 (防刷屏 — 注入前连发 N 包也只 1 条诊断) */
    private static final AtomicBoolean DROP_WARNED = new AtomicBoolean();

    private static void warnDrop(String method, Object msg) {
        if (DROP_WARNED.compareAndSet(false, true)) {
            String type = msg == null ? "null" : msg.getClass().getSimpleName();
            LOGGER.warn("[LMA/Network] sender 未注入 (no-op 默认) — {} {} 被静默丢弃; "
                    + "检查注入点 (forge: LittleMaidMoreAction ctor / neoforge: LmaNeoForgeEntry ctor)", method, type);
        }
    }

    /** loader 注入; 注入前调用 = 空操作 (仅编译/未初始化期) */
    public static ISender sender = new ISender() {
        @Override
        public void sendToServer(Object msg) {
            warnDrop("sendToServer", msg);
        }

        @Override
        public void sendToAll(Object msg) {
            warnDrop("sendToAll", msg);
        }

        @Override
        public void sendToTrackingEntity(Entity entity, Object msg) {
            warnDrop("sendToTrackingEntity", msg);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, Object msg) {
            warnDrop("sendToPlayer", msg);
        }
    };

    /** 注入统一入口 (M-4) — 记录注入状态诊断日志 */
    public static void setSender(ISender impl) {
        sender = impl;
        LOGGER.info("[LMA/Network] sender 注入完成: {}", impl.getClass().getSimpleName());
    }

    private LmaNetwork() {
    }
}
