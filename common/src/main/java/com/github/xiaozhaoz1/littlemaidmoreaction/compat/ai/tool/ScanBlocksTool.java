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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 工具: 扫描方块 (v73) — 指定 block_id + 半径, 返回匹配方块位置列表
 * (vanilla 三重循环, TempAdaptPipeline.findHeatSource 同款模式)。
 * 权限: AI 操控任务开启。
 */
public final class ScanBlocksTool implements GatedMaidTool<ScanBlocksTool.Result> {

    private static final String BLOCK_ID = "block_id";
    private static final String RADIUS = "radius";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf(BLOCK_ID).forGetter(Result::blockId),
            Codec.INT.optionalFieldOf(RADIUS, 8).forGetter(Result::radius)
    ).apply(i, Result::new));

    @Override public String id() { return "scan_blocks"; }

    @Override public String summary(EntityMaid maid) {
        return "Scan nearby blocks matching the given block id (e.g. minecraft:iron_ore) and return their positions.";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(BLOCK_ID, StringParameter.create().setDescription("Block id, e.g. minecraft:iron_ore"));
        root.addProperties(RADIUS, IntegerParameter.create().setDescription("Scan radius (default 8)"), false);
        return root;
    }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        ResourceLocation rl = ResourceLocation.tryParse(result.blockId());
        if (rl == null) {
            return callback.addToolResult("Invalid block id '%s'".formatted(result.blockId()), toolCallId);
        }
//? if 1.20.1 {
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
//?} else {
        Block block = BuiltInRegistries.BLOCK.get(rl);   // 1.21: getValue 删 → get (错题 5)
//?}
        if (block == Blocks.AIR && !result.blockId().equals("minecraft:air")) {
            return callback.addToolResult("Unknown block id '%s'".formatted(result.blockId()), toolCallId);
        }

        int radius = Math.max(1, Math.min(result.radius(), 32));
        BlockPos center = maid.blockPosition();
        List<String> found = new ArrayList<>();
        int vert = 4;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int y = -vert; y <= vert; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    mp.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (maid.level().getBlockState(mp).is(block)) {
                        found.add("(%d, %d, %d)".formatted(mp.getX(), mp.getY(), mp.getZ()));
                        if (found.size() >= 10) break;
                    }
                }
                if (found.size() >= 10) break;
            }
            if (found.size() >= 10) break;
        }
        return callback.addToolResult(found.isEmpty()
                ? "No %s found within %d blocks".formatted(result.blockId(), radius)
                : "Found %d %s: %s".formatted(found.size(), result.blockId(), String.join(", ", found)),
                toolCallId);
    }

    public record Result(String blockId, int radius) {}
}
