package com.github.xiaozhaoz1.littlemaidmoreaction.init;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.IEventBus;
//?} else {
import net.neoforged.bus.api.IEventBus;
//?}
//? if 1.20.1 {
import net.minecraftforge.registries.DeferredRegister;
//?} else {
import net.neoforged.neoforge.registries.DeferredRegister;
//?}
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}
//? if 1.20.1 {
import net.minecraftforge.registries.RegistryObject;
//?} else {
import java.util.function.Supplier;
//?}

/**
 * LMA 方块注册 (v40; v75.1 running_belt 双平台化)。
 *
 * <p>第一个 LMA 方块 — MaidPowerBeltBlock。用于女仆跑步发电任务。
 */
public final class LmaBlocks {

    public static final DeferredRegister<Block> BLOCKS =
//? if 1.20.1 {
            DeferredRegister.create(ForgeRegistries.BLOCKS, LittleMaidMoreAction.MOD_ID);
//?} else {
            DeferredRegister.create(BuiltInRegistries.BLOCK, LittleMaidMoreAction.MOD_ID);
//?}

    // Create 兼容方块 — 运行时无 Create 时跳过注册 (MaidPowerBeltBlock 父类在 Create jar)
//? if 1.20.1 {
    public static final RegistryObject<MaidPowerBeltBlock> MAID_POWER_BELT =
            com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")
                    && net.minecraftforge.fml.ModList.get().isLoaded("create")
                    ? BLOCKS.register("maid_power_belt",
                            () -> new MaidPowerBeltBlock(Block.Properties.of()
                                    .sound(SoundType.WOOL).strength(2.0f).noOcclusion()))
                    : null;
//?} else {
    public static final Supplier<MaidPowerBeltBlock> MAID_POWER_BELT =
            com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")
                    && net.neoforged.fml.ModList.get().isLoaded("create")
                    ? BLOCKS.register("maid_power_belt",
                            () -> new MaidPowerBeltBlock(Block.Properties.of()
                                    .sound(SoundType.WOOL).strength(2.0f).noOcclusion()))
                    : null;
//?}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    private LmaBlocks() {}
}
