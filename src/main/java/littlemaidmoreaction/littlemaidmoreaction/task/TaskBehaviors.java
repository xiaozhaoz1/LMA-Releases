package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.item.ItemStack;
import littlemaidmoreaction.littlemaidmoreaction.task.behavior.NearbyCollectBehavior;
import littlemaidmoreaction.littlemaidmoreaction.task.behavior.WorkEatBehavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Brain行为构建器 — 从 Pipeline 配置创建 Behavior 列表. */
public final class TaskBehaviors {

    private TaskBehaviors() {}

    /** 构建器 */
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        int nextPrio = 3;
        final List<Pair<Integer, BehaviorControl<? super EntityMaid>>> list = new ArrayList<>();

        /** 加吃食物 (如果 enableWorkEat) */
        public Builder eat(TaskPipeline pl) {
            if (pl.enableWorkEat()) list.add(Pair.of(nextPrio++, new WorkEatBehavior()));
            return this;
        }

        /** 加附近收集 (collectFilter非null则启用) */
        public Builder collect(TaskPipeline pl) {
            return collect(pl::collectFilter, Set.of(), "backpack", 3);
        }

        /** 加附近收集 (自定义) */
        public Builder collect(Function<EntityMaid, Predicate<ItemStack>> filter,
                                Set<String> containerBlocks, String dest, int radius) {
            list.add(Pair.of(nextPrio++, new NearbyCollectBehavior(filter, containerBlocks, dest, radius)));
            return this;
        }

        public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> build() { return list; }
    }
}
