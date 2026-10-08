package com.github.xiaozhaoz1.littlemaidmoreaction.compat.patpat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.PatPatReactionPacket;
import net.lopymine.patpat.client.manager.PatPatClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PatPat 抚摸检测 (客户端, v79.61x) — 轮询 PatPat 客户端状态表替代交互事件。
 *
 * <p><b>为什么不用 EntityInteractEvent</b>: PatPat 抚摸按键 (Shift+右键) 取消
 * {@code KeyMapping.click} (PatPat KeybindingMixin 实证) → 抚摸期间服务端交互事件不 fire,
 * 与 PatPat 视觉互斥。故与 PatPat 渲染同源: 直接读 {@link PatPatClientManager#getPatEntity}
 * 状态表 (本地+远程抚摸共享同一张表) → 命中即发 C2S 包。
 *
 * <p><b>节流两级</b>: 本类 per-maid 客户端节流 (降载, 同 600t) + 服务端权威 600t
 * ({@link PatPatCompat#onReaction} 三道校验)。非主人抚摸也会在客户端状态表可见,
 * 但服务端 owner 校验会拒掉 → 无副作用。
 *
 * <p><b>轮询间隔</b>: 20t (1 秒) — 抚摸动画持续期间多次命中无妨, CD 节流主导。
 * 仅客户端类 (OnlyIn + 只被客户端入口引用), 专用服务器不加载。
 */
@OnlyIn(Dist.CLIENT)
public final class PatPatReactionClient {

    private PatPatReactionClient() {}

    /** 轮询间隔 (tick, 1 秒) */
    private static final int POLL_INTERVAL = 20;

    /** 检测半径 (格) — PatPat 抚摸距离 ≈ 原版交互 3 格, 8 格覆盖玩家周边 */
    private static final double DETECT_RADIUS = 8.0;

    /** 客户端节流表 (maid UUID → 绝对截止 tick) — 仅降载, 非权威; 断线清空 */
    private static final Map<UUID, Long> CLIENT_CD = new HashMap<>();

    private static int pollCounter = 0;

    /** 由客户端入口注册 (forge TickEvent.ClientTickEvent / neoforge ClientTickEvent.Post) 驱动 */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!PatPatCompat.isInstalled()) return;
        if (++pollCounter % POLL_INTERVAL != 0) return;

        long now = mc.level.getGameTime();
        AABB box = mc.player.getBoundingBox().inflate(DETECT_RADIUS);
        for (EntityMaid maid : mc.level.getEntitiesOfClass(EntityMaid.class, box)) {
            // PatPat 状态表命中 = 正在被抚摸 (本地或远程玩家, 表共享)
            if (PatPatClientManager.getPatEntity(maid) == null) continue;
            UUID uuid = maid.getUUID();
            Long cdUntil = CLIENT_CD.get(uuid);
            if (cdUntil != null && cdUntil > now) continue;
            CLIENT_CD.put(uuid, now + PatPatCompat.REACTION_CD_TICKS);
            PatPatReactionPacket.sendToServer(maid.getId());
        }
    }

    /** 退出世界 → 清客户端节流表 (防跨世界残留; 由入口 LoggingOut 监听调用) */
    public static void clearCache() {
        CLIENT_CD.clear();
    }
}
