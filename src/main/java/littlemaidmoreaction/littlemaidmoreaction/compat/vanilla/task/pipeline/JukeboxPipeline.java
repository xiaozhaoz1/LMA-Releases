package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.*;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.ProgressNotifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 唱片机管道 (Phase 2) — 扫描唱片 → 写规则 → JukeboxInteractAction 处理导航+弹出入碟。
 */
public final class JukeboxPipeline implements TaskPipeline {

    @Override public String taskType() { return "jukebox"; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return execute(level, maid, ctx);
    }

    @Override
    public PipelineResult execute(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        LittleMaidMoreAction.LOGGER.info("[V16] [Jukebox] ====== START: target={}", !target.isEmpty() ? target : "(any)");

        // Scan inventory for music discs
        var inv = maid.getAvailableInv(true);
        List<ItemStack> discs = new ArrayList<>();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.is(ItemTags.MUSIC_DISCS)) {
                discs.add(s.copy());
            }
        }

        if (discs.isEmpty()) {
            LittleMaidMoreAction.LOGGER.info("[V16] [Jukebox] no music discs found");
            ProgressNotifier.notify(maid, ProgressNotifier.NO_DISC);
            return PipelineResult.failed(ProgressNotifier.NO_DISC);
        }

        // Filter by target if specified
        String chosenName;
        if (!target.isEmpty()) {
            Optional<ItemStack> match = discs.stream()
                .filter(d -> d.getDescriptionId().contains(target) || d.getItem().toString().contains(target))
                .findFirst();
            if (match.isEmpty()) {
                ProgressNotifier.notify(maid, "背包里没有" + target + "唱片");
                return PipelineResult.failed("背包里没有" + target + "唱片");
            }
            chosenName = match.get().getHoverName().getString();
        } else {
            chosenName = discs.get(0).getHoverName().getString();
        }
        LittleMaidMoreAction.LOGGER.info("[V16] [Jukebox] found {} discs, selected: {}", discs.size(), chosenName);

        // ★ v18: Brain Behavior 直接执行，不写规则文件
        String taskId = ctx.taskId();
        String msg = ProgressNotifier.nowPlaying(chosenName);
        ProgressNotifier.notify(maid, msg);
        LittleMaidMoreAction.LOGGER.info("[V16] [Jukebox] ====== END: {}", msg);
        return PipelineResult.ok(msg);
    }
}
