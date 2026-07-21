package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

/**
 * LMA TaskDataKey 定义 (v43)。
 *
 * <p>使用 TLM 标准的 TaskDataRegister 注册，Codec 序列化。
 * 当前底层兼容 PersistentData (v43)，TaskDataKey 完整迁移留待 v44。
 *
 * <p>注册入口: {@code LittleMaidMoreActionExtension#registerTaskData}
 */
public final class LmaTaskDataKeys {

    public static TaskDataKey<String> FLOW_TASK;
    public static TaskDataKey<String> FLOW_STATE;
    public static TaskDataKey<Long>   FLOW_TICK;
    public static TaskDataKey<Long>   FLOW_START_TIME;
    public static TaskDataKey<String> FLOW_TASK_ID;
    public static TaskDataKey<Long>   FLOW_MAX_COUNT;
    public static TaskDataKey<Long>   FLOW_COUNTER;
    public static TaskDataKey<String> FLOW_CACHED;

    /** 在 LittleMaidMoreActionExtension.registerTaskData() 中调用 */
    public static void registerAll(TaskDataRegister register) {
        FLOW_TASK       = register.register(rl("flow_task"),       Codec.STRING);
        FLOW_STATE      = register.register(rl("flow_state"),      Codec.STRING);
        FLOW_TICK       = register.register(rl("flow_tick"),       Codec.LONG);
        FLOW_START_TIME = register.register(rl("flow_start_time"), Codec.LONG);
        FLOW_TASK_ID    = register.register(rl("flow_task_id"),    Codec.STRING);
        FLOW_MAX_COUNT  = register.register(rl("flow_max_count"),  Codec.LONG);
        FLOW_COUNTER    = register.register(rl("flow_counter"),    Codec.LONG);
        FLOW_CACHED     = register.register(rl("flow_cached"),     Codec.STRING);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("lma", path);
    }

    private LmaTaskDataKeys() {}
}
