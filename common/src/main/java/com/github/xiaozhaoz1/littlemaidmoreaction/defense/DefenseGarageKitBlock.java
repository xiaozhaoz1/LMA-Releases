package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import com.github.tartaricacid.touhoulittlemaid.block.BlockGarageKit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 防御塔方块 (v79.62.3) — shape 对齐原版 {@link BlockGarageKit#BLOCK_AABB} (手办碰撞),
 * SoundType.MUD + noOcclusion 同原版; 挂 EntityBlock + MenuProvider (右键打开塔 GUI)。
 *
 * <p>放置时: ① NBT 全继承原版手办 (ExtraData — 女仆模型/附属扩展); ② ownerUuid 写入 (裁定 ⑨);
 * ③ 同步塔自有个性化默认 (radius/shootMode 由 BE 自持 -1/ARROW 语义, 不显式写)。
 */
public class DefenseGarageKitBlock extends Block implements EntityBlock {

    public DefenseGarageKitBlock() {
        super(Properties.of()
                .sound(net.minecraft.world.level.block.SoundType.MUD)
                .strength(1, 2)
                .noOcclusion());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DefenseGarageKitBlockEntity(pos, state);
    }

    /** 放置: 恢复潜影盒式塔数据 (若有) + 写主人 UUID + 继承手办 NBT (ExtraData) */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity te = level.getBlockEntity(pos);
        if (!(te instanceof DefenseGarageKitBlockEntity tower)) return;
        // 潜影盒式恢复: 掉落物物品带完整塔数据 (弹药+主人+设置+手办) → 原样恢复
        net.minecraft.nbt.CompoundTag towerData = readTowerData(stack);
        if (towerData != null) {
//? if 1.20.1 {
            tower.loadTowerFromItem(towerData, level.registryAccess());
//?} else {
            tower.loadTowerFromItem(towerData, level.registryAccess());
//?}
            return;   // 完整恢复 — 不再覆盖主人/手办
        }
        if (placer instanceof ServerPlayer sp) {
            tower.setOwnerUuid(sp.getUUID());
        }
        // 继承原版手办 ExtraData (与 ItemGarageKit.getMaidData 同源键 "EntityInfo")
        net.minecraft.nbt.CompoundTag entityInfo = readEntityInfo(stack);
        if (entityInfo != null) {
            tower.setData(
                    placer != null ? placer.getDirection().getOpposite() : net.minecraft.core.Direction.SOUTH,
                    entityInfo);
        }
    }

    /** 从物品读潜影盒式塔数据 (双平台 NBT 差异) */
    @Nullable
    private static net.minecraft.nbt.CompoundTag readTowerData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
//? if 1.20.1 {
        if (stack.hasTag() && stack.getTag().contains(DefenseGarageKitBlockEntity.EXTRA_DATA_KEY,
                net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return stack.getTag().getCompound(DefenseGarageKitBlockEntity.EXTRA_DATA_KEY);
        }
        return null;
//?} else {
        var custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (custom != null) {
            net.minecraft.nbt.CompoundTag tag = custom.copyTag();
            if (tag.contains(DefenseGarageKitBlockEntity.EXTRA_DATA_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                return tag.getCompound(DefenseGarageKitBlockEntity.EXTRA_DATA_KEY);
            }
        }
        return null;
//?}
    }

    /** 读取物品的手办 NBT (双平台 ItemStack NBT 差异: 1.20.1 getTag / 1.21.1 组件) */
    @Nullable
    private static net.minecraft.nbt.CompoundTag readEntityInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
//? if 1.20.1 {
        if (stack.hasTag()) {
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag.contains("EntityInfo", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                return tag.getCompound("EntityInfo");
            }
        }
        return null;
//?} else {
        var custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (custom != null) {
            net.minecraft.nbt.CompoundTag tag = custom.copyTag();
            if (tag.contains("EntityInfo", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                return tag.getCompound("EntityInfo");
            }
        }
        return null;
//?}
    }

    /** 右键: 打开塔 GUI (仅主人 — stillValid 内校验; 非主人/无主由菜单拒绝) */
//? if 1.20.1 {
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity te = level.getBlockEntity(pos);
            if (te instanceof DefenseGarageKitBlockEntity tower) {
                if (tower.getOwnerUuid() == null) {
                    tower.setOwnerUuid(player.getUUID());
                }
                // 必须 NetworkHooks.openScreen + buf 写 BlockPos (玩家 openMenu 不写 buffer →
                // 客户端 buf.readBlockPos() 读到空, 塔拿不到; MaidAssemblyNetwork 同款先例)
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    net.minecraftforge.network.NetworkHooks.openScreen(sp, tower.getMenuProvider(),
                            buf -> buf.writeBlockPos(pos));
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
//?} else {
    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                               BlockPos pos, Player player, InteractionHand hand,
                                                               BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity te = level.getBlockEntity(pos);
            if (te instanceof DefenseGarageKitBlockEntity tower) {
                if (tower.getOwnerUuid() == null) {
                    tower.setOwnerUuid(player.getUUID());
                }
                // neoforge: vanilla openMenu + buf 写 BlockPos (MaidAssemblyNetwork 同款先例)
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.openMenu(tower.getMenuProvider(), buf -> buf.writeBlockPos(pos));
                }
                return net.minecraft.world.ItemInteractionResult.SUCCESS;
            }
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
//?}

    /** 渲染: 必须 ENTITYBLOCK_ANIMATED 才会调用 BlockEntityRenderer (手办女仆模型) */
    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** 拆除: 掉落物品 — 潜影盒式 (完整塔数据: 弹药+主人+设置+手办, 打掉不丢东西) */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving) {
            BlockEntity te = level.getBlockEntity(pos);
            if (te instanceof DefenseGarageKitBlockEntity tower) {
                ItemStack drop = new ItemStack(com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlocks.GARAGE_KIT_DEFENSE.get());
                // 完整塔数据 → 物品 NBT (武器/弹药/主人/设置/手办)
                net.minecraft.nbt.CompoundTag full = tower.saveTowerToItem(level.registryAccess());
                writeTowerData(drop, full);
                // 兼容: 也写手办 EntityInfo (其他附属读取原版键)
                if (tower.getExtraData() != null && !tower.getExtraData().isEmpty()) {
                    writeEntityInfo(drop, tower.getExtraData().copy());
                }
                net.minecraft.world.level.block.Block.popResource(level, pos, drop);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** 塔数据写物品 NBT (双平台) */
    private static void writeTowerData(ItemStack stack, net.minecraft.nbt.CompoundTag data) {
//? if 1.20.1 {
        stack.getOrCreateTag().put(DefenseGarageKitBlockEntity.EXTRA_DATA_KEY, data);
//?} else {
        var existing = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag tag = existing != null ? existing.copyTag() : new net.minecraft.nbt.CompoundTag();
        tag.put(DefenseGarageKitBlockEntity.EXTRA_DATA_KEY, data);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
//?}
    }

    /** 手办 NBT 回写物品 (双平台 ItemStack NBT 差异) */
    private static void writeEntityInfo(ItemStack stack, net.minecraft.nbt.CompoundTag entityInfo) {
//? if 1.20.1 {
        stack.getOrCreateTag().put("EntityInfo", entityInfo);
//?} else {
        var existing = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag tag = existing != null ? existing.copyTag() : new net.minecraft.nbt.CompoundTag();
        tag.put("EntityInfo", entityInfo);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
//?}
    }
}
