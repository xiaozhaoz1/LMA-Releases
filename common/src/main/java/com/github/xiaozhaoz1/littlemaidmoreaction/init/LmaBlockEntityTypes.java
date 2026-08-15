package com.github.xiaozhaoz1.littlemaidmoreaction.init;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * LMA 方块实体注册 (v40)。
 */
public final class LmaBlockEntityTypes {

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
//? if 1.20.1 {
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LittleMaidMoreAction.MOD_ID);
//?} else {
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, LittleMaidMoreAction.MOD_ID);
//?}

    // Create 兼容方块实体 (双平台化) — 与 LmaBlocks.MAID_POWER_BELT 同步门控 (无 Create 时为 null)
//? if 1.20.1 {
    public static final RegistryObject<BlockEntityType<MaidPowerBeltBlockEntity>> MAID_POWER_BELT =
            LmaBlocks.MAID_POWER_BELT != null
                    ? TYPES.register("maid_power_belt",
                            () -> BlockEntityType.Builder.of(
                                    MaidPowerBeltBlockEntity::new, LmaBlocks.MAID_POWER_BELT.get()).build(null))
                    : null;
//?} else {
    public static final Supplier<BlockEntityType<MaidPowerBeltBlockEntity>> MAID_POWER_BELT =
            LmaBlocks.MAID_POWER_BELT != null
                    ? TYPES.register("maid_power_belt",
                            () -> BlockEntityType.Builder.of(
                                    MaidPowerBeltBlockEntity::new, LmaBlocks.MAID_POWER_BELT.get()).build(null))
                    : null;
//?}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    private LmaBlockEntityTypes() {}
}
