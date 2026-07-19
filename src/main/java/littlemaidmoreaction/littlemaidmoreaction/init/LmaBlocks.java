package littlemaidmoreaction.littlemaidmoreaction.init;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * LMA 方块注册 (v40)。
 *
 * <p>第一个 LMA 方块 — MaidPowerBeltBlock。用于女仆跑步发电任务。
 */
public final class LmaBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, LittleMaidMoreAction.MOD_ID);

    public static final RegistryObject<MaidPowerBeltBlock> MAID_POWER_BELT =
            BLOCKS.register("maid_power_belt",
                    () -> new MaidPowerBeltBlock(Block.Properties.of()
                            .sound(SoundType.WOOL).strength(2.0f).noOcclusion()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    private LmaBlocks() {}
}
