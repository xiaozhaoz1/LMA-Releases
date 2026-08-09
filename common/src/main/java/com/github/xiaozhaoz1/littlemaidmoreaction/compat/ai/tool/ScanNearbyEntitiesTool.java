package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * AI 工具: 附近实体清单 (v77.6) — EntityScan, id/类型/距离/hp/分类, 按距离排序。
 */
public final class ScanNearbyEntitiesTool implements ITool<ScanNearbyEntitiesTool.Result> {

    private static final String RADIUS = "radius";
    private static final String FILTER = "type_filter";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf(RADIUS, 32).forGetter(Result::radius),
            Codec.STRING.optionalFieldOf(FILTER, "all").forGetter(Result::typeFilter)
    ).apply(i, Result::new));

    @Override public String id() { return "scan_nearby_entities"; }

    @Override public String summary(EntityMaid maid) {
        return "List entities within a radius, sorted by distance. type_filter: hostile/passive/player/all. "
                + "Each entry has type, distance, hp, category.";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(RADIUS, IntegerParameter.create().setDescription("Search radius (1-64, default 32)"), false);
        root.addProperties(FILTER, StringParameter.create().setDescription("hostile/passive/player/all (default all)"), false);
        return root;
    }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        if (!(maid.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
            return callback.addToolResult("服务端不可用", toolCallId);
        }
        int radius = Math.max(1, Math.min(64, result.radius()));
        String filter = switch (result.typeFilter()) {
            case "hostile", "passive", "player" -> result.typeFilter();
            default -> "all";
        };
        var scan = com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EntityScan.scanNearby(
                sl, maid.getX(), maid.getY(), maid.getZ(), radius, filter);
        StringBuilder sb = new StringBuilder();
        for (var e : scan.entities()) {
            sb.append(String.format("%s %.1f格 hp=%.0f [%s]\n", e.type(), e.distance(), e.hp(), e.category()));
        }
        if (scan.truncated()) sb.append("... (更多实体, 已截断)");
        if (sb.length() == 0) sb.append("附近无匹配实体");
        return callback.addToolResult(sb.toString(), toolCallId);
    }

    public record Result(int radius, String typeFilter) {}
}
