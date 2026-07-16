package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.VanillaInputRegistry;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.*;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.ProgressNotifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * 祭坛合成管道 (Phase 2) — 检查材料 → 写规则 → PlaceAltarItemAction 处理导航+合成。
 */
public final class AltarCraftPipeline implements TaskPipeline {

    @Override public String taskType() { return "altar_craft"; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return execute(level, maid, ctx);
    }

    @Override
    public PipelineResult execute(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        LittleMaidMoreAction.LOGGER.info("[V16] [AltarCraft] ====== START: target={}", ctx.target());

        Item targetItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(target));
        if (targetItem == null) {
            ProgressNotifier.notify(maid, "无效的目标物品: " + target);
            return PipelineResult.failed("无效的目标物品: " + target);
        }

        Map<Item, Integer> allItems = VanillaInputRegistry.readAllItems(maid);
        int have = allItems.getOrDefault(targetItem, 0);

        LittleMaidMoreAction.LOGGER.info("[V16] [AltarCraft] inventory: {} x{} (need at least 1 for altar recipe)", targetItem, have);
        if (have <= 0) {
            String msg = ProgressNotifier.missing(target, 1);
            ProgressNotifier.notify(maid, msg);
            return PipelineResult.failed(msg);
        }

        // ★ v18: Brain Behavior 直接执行，不写规则文件
        String taskId = ctx.taskId();
        String msg = "祭坛合成任务已启动: " + target;
        ProgressNotifier.notify(maid, msg);
        LittleMaidMoreAction.LOGGER.info("[V16] [AltarCraft] ====== END: {}", msg);
        return PipelineResult.ok(msg);
    }
}
