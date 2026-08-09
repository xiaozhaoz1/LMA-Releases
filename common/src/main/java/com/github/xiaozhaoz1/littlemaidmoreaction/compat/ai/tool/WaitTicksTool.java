package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.CompletableFuture;

/**
 * AI 工具: 等待 (v73) — 异步延迟后回传 (onCallAsync + server 调度器,
 * LLMCallback.runOnServerThread 回主线程模式)。只读性质, 无权限门控。
 */
public final class WaitTicksTool implements ITool<WaitTicksTool.Result> {

    private static final String TICKS = "ticks";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf(TICKS, 20).forGetter(Result::ticks)
    ).apply(i, Result::new));

    @Override public String id() { return "wait_ticks"; }

    @Override public String summary(EntityMaid maid) {
        return "Wait for the given number of ticks before continuing. Use when you need to wait (e.g. for a task to finish).";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(TICKS, IntegerParameter.create().setDescription("Ticks to wait (default 20)"), false);
        return root;
    }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        return callback.addToolResult("Waiting %d ticks".formatted(result.ticks()), toolCallId);
    }

    @Override
    public CompletableFuture<LLMCallback> onCallAsync(String toolCallId, Result result,
                                                      LLMCallback callback,
                                                      com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient client) {
        EntityMaid maid = callback.getMaid();
        if (!(maid.level() instanceof ServerLevel level)) {
            return CompletableFuture.completedFuture(callback.addToolResult("Failed: not on server", toolCallId));
        }
        int ticks = Math.max(1, Math.min(result.ticks(), 1200));
        CompletableFuture<LLMCallback> future = new CompletableFuture<>();
        // 服务端主线程调度 (tell 已保证主线程 — 无需 runOnServerThread)
        level.getServer().tell(new net.minecraft.server.TickTask(ticks,
                () -> future.complete(callback.addToolResult("Waited %d ticks".formatted(ticks), toolCallId))));
        return future;
    }

    public record Result(int ticks) {}
}
