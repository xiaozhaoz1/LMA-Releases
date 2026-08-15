package com.github.xiaozhaoz1.littlemaidmoreaction.ai.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvScanner;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSenseBroadcaster;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;

/**
 * v63: 环境感知 AI 上下文 — 暴露环境数据给 TLM Agent。
 *
 * <p>注册到 TLM GameContextRegister，AI 可查询当前女仆的环境状态。
 * promptContext=false — 按需查询，不自动注入每条消息。
 */
public final class LmaEnvSenseContext {

    public static final String CATEGORY = "lma_envsense";
    private static final String SUMMARY = "环境感知数据: 温度/天气/时间/附近实体.";

    private LmaEnvSenseContext() {}

    public static void registerAll(GameContextRegister register) {
        register.registerCategory(CATEGORY, SUMMARY, false);
        register.registerContext(CATEGORY, new EnvContext());
        register.registerContext(CATEGORY, new NearbyEntitiesContext());
        // Numen 感知原语 — 语义网格 + 实体清单
        register.registerContext(CATEGORY, new LookAroundContext());
        register.registerContext(CATEGORY, new NearbyEntitiesDetailContext());
    }

    /** 女仆环境: 温度/天气/时间/光照 */
    private static final class EnvContext extends AbstractMaidContext {
        EnvContext() {
            super("maid_environment", "当前女仆所在环境: 维度, 时间, 天气, 温度, 光照.");
        }

        @Override
        public String getValue(EntityMaid maid) {
            EnvSnapshot snap = EnvSenseBroadcaster.getSnapshot(maid);
            if (snap == null || snap.world() == null) {
                return "环境数据尚未就绪";
            }
            var w = snap.world();
            StringBuilder sb = new StringBuilder();
            sb.append("维度: ").append(w.dimension());
            sb.append(", 时间: ").append(w.timeSegment());
            sb.append(", 天气: ");
            if (w.thundering()) sb.append("雷暴");
            else if (w.raining()) sb.append("下雨(").append(w.precipitation()).append(")");
            else sb.append("晴");
            sb.append(", 温度: ").append(String.format("%.2f", w.temperature()));
            sb.append(" (").append(w.tempCategory()).append(")");
            sb.append(", 光照: ").append(w.lightAtMaid());
            sb.append(", 月相: ").append(w.moonPhase());
            return sb.toString();
        }
    }

    /** 附近实体统计 */
    private static final class NearbyEntitiesContext extends AbstractMaidContext {
        NearbyEntitiesContext() {
            super("maid_nearby_entities", "女仆周围实体: 怪物/友好生物/其他女仆数量.");
        }

        @Override
        public String getValue(EntityMaid maid) {
            EnvSnapshot snap = EnvSenseBroadcaster.getSnapshot(maid);
            if (snap == null) return "实体数据尚未就绪";
            int monsters = snap.entities(EnvScanner.CAT_MONSTER).size();
            int friendlies = snap.entities(EnvScanner.CAT_FRIENDLY).size();
            int maids = snap.entities(EnvScanner.CAT_MAID).size();
            return String.format("附近怪物: %d, 友好生物: %d, 其他女仆: %d",
                    monsters, friendlies, maids);
        }
    }

    /** 自我中心语义网格 (LookAroundGrid — 字符地形图, LLM 空间推理) */
    private static final class LookAroundContext extends AbstractMaidContext {
        LookAroundContext() {
            super("maid_look_around", "女仆周围地形图: 字符网格, 每格一个方块, 移动可行性语义.");
        }

        @Override
        public String getValue(EntityMaid maid) {
            if (!(maid.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
                return "地形数据不可用";
            }
            return com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.LookAroundGrid.render(sl, maid.blockPosition(), 8);
        }
    }

    /** 附近实体清单 (EntityScan — id/距离/hp/分类, 20 上限) */
    private static final class NearbyEntitiesDetailContext extends AbstractMaidContext {
        NearbyEntitiesDetailContext() {
            super("maid_nearby_entities_detail", "女仆周围实体清单: id/类型/距离/hp/分类 (按距离排序).");
        }

        @Override
        public String getValue(EntityMaid maid) {
            if (!(maid.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
                return "实体清单不可用";
            }
            var result = com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EntityScan.scanNearby(
                    sl, maid.getX(), maid.getY(), maid.getZ(), 32, "all");
            StringBuilder sb = new StringBuilder();
            for (var e : result.entities().stream().limit(10).toList()) {
                sb.append(String.format("%s %.1f格 hp=%.0f [%s]\n",
                        e.type(), e.distance(), e.hp(), e.category()));
            }
            if (result.truncated()) sb.append("... (更多实体, 已截断)");
            return sb.length() == 0 ? "附近无实体" : sb.toString();
        }
    }
}
