package littlemaidmoreaction.littlemaidmoreaction.adapter;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

/**
 * LMA 自定义 MemoryModuleType 注册中心 (v12.7 P0)。
 *
 * <p>导航目标使用 Brain Memory — 不跨会话持久化，Activity 切换时自动清除。
 */
public final class LmaMemoryModuleRegistry {

    public static final DeferredRegister<MemoryModuleType<?>> REGISTER =
            DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, LittleMaidMoreAction.MOD_ID);

    /** 导航目标方块坐标 (无 Codec → 不持久化 → 女仆卸载自动清除) */
    public static final RegistryObject<MemoryModuleType<BlockPos>> NAV_TARGET =
            REGISTER.register("lma_nav_target",
                    () -> new MemoryModuleType<>(Optional.empty()));

    /** 导航启动 tick — 用于超时检测 */
    public static final RegistryObject<MemoryModuleType<Long>> NAV_START_TICK =
            REGISTER.register("lma_nav_start_tick",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private LmaMemoryModuleRegistry() {}
}
