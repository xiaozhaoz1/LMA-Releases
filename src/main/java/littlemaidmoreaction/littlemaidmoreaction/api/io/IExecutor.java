package littlemaidmoreaction.littlemaidmoreaction.api.io;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * 统一任务执行器接口 — 替代 TaskRegistry.TaskExecutor 函数式接口。
 *
 * <p>每个 Executor 执行一个 tick 的工作，支持多 tick 持续执行 (返回 CONTINUE)。
 * 增加生命周期钩子 onStop/onComplete，替代静态 HashMap 手动清理。</p>
 *
 * <p>标准调用签名: {@code execute(world, maid, pos, data)}</p>
 */
@FunctionalInterface
public interface IExecutor {
    /** 执行器唯一标识 */
    default String id() { return getClass().getSimpleName(); }

    /**
     * 执行一 tick 工作。
     *
     * @param world 服务端世界
     * @param maid  执行任务的女仆
     * @param pos   目标方块位置
     * @param data  跨 tick 持久数据 (CompoundTag from maid PersistentData)
     * @return SUCCESS(完成) / CONTINUE(继续) / FAILED(失败)
     */
    TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos, CompoundTag data);

    /** 带附加上下文的重载 (子类可覆写) */
    default TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos,
                                CompoundTag data, Object context) {
        return execute(world, maid, pos, data);
    }

    /** 任务被抢占/停止时清理 */
    default void onStop(EntityMaid maid) {}

    /** 任务成功完成回调 */
    default void onComplete(ServerLevel world, EntityMaid maid, BlockPos pos) {}
}
