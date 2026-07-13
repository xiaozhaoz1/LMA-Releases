package littlemaidmoreaction.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.eventbus.api.Event;

/**
 * ★ v14: LMA 任务脑行为启动事件。
 *
 * <p>在 TLM Brain Behavior 的 {@code start()} 方法中 post，
 * 替代 {@code task_changed} 作为任务循环的触发器。
 * 每次 Brain 行为执行时（约100tick间隔）触发一次，支持持续循环。
 *
 * <p>规则监听此事件 + has_flow_task 条件 → 执行任务动作。
 */
public class LmaTaskStartEvent extends Event {
    private final EntityMaid maid;
    private final String taskType;

    public LmaTaskStartEvent(EntityMaid maid, String taskType) {
        this.maid = maid;
        this.taskType = taskType;
    }

    public EntityMaid getMaid() { return maid; }
    public String getTaskType() { return taskType; }
}
