package littlemaidmoreaction.littlemaidmoreaction.init;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * LMA 方块实体注册 (v40)。
 */
public final class LmaBlockEntityTypes {

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LittleMaidMoreAction.MOD_ID);

    public static final RegistryObject<BlockEntityType<MaidPowerBeltBlockEntity>> MAID_POWER_BELT =
            TYPES.register("maid_power_belt",
                    () -> BlockEntityType.Builder.of(
                            MaidPowerBeltBlockEntity::new, LmaBlocks.MAID_POWER_BELT.get()).build(null));

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    private LmaBlockEntityTypes() {}
}
