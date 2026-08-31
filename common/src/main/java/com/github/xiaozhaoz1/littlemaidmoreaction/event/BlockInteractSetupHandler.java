package com.github.xiaozhaoz1.littlemaidmoreaction.event;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;
//? if !1.20.1 {
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
//?}

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.BlockInteractPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.TaskConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
//?} else {
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

/**
 * 木棍标记交互方块 + 右键女仆绑定 block_interact 任务。
 *
 * <p><b>与 ArmTransferSetupHandler 分离</b>:
 * <ul>
 *   <li>ArmTransfer 处理 isContainer()==true 的方块</li>
 *   <li>BlockInteract 处理 isContainer()==false 的方块</li>
 *   <li>各自检查 taskType, 互不干扰</li>
 * </ul>
 * 木棍获取/容器判断/任务类型门控统一见 {@link StickBindUtil} (v67.1)。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class BlockInteractSetupHandler {

    /** 木棍 NBT key — 存储绑定的方块坐标 */
    static final String STICK_KEY = TaskKeys.BIND_POS;

    private BlockInteractSetupHandler() {}

    // ── ① 木棍右键非容器方块: 标记交互目标 ──

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!StickBindUtil.isMarkItem(held)) return;
        if (event.getLevel().isClientSide()) return;

        BlockPos pos = event.getPos();
        // 跳过容器 — ArmTransferSetupHandler 处理
        if (StickBindUtil.isContainer(event.getLevel(), pos)) return;

//? if 1.20.1 {
        CompoundTag tag = held.getOrCreateTag();
//?} else {
CustomData _cd = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
CompoundTag tag = _cd.copyTag();
//?}
        tag.put(STICK_KEY, NbtUtils.writeBlockPos(pos));
        event.getEntity().sendSystemMessage(
            Component.literal("§a已标记交互方块: " + pos.toShortString()
                + " §7(右键有 block_interact 任务的女仆绑定)"));
        //? if !1.20.1 {
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?}
    }

    // ── ② 木棍右键女仆: pos转存到maid → submit ──

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        ItemStack held = StickBindUtil.getStickStack(player);
        if (held == null) return;
        if (maid.level().isClientSide) return;

        if (!StickBindUtil.checkTaskType(maid, "block_interact", player)) return;

//? if 1.20.1 {
        CompoundTag tag = held.getOrCreateTag();
//?} else {
CustomData _cd = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
CompoundTag tag = _cd.copyTag();
//?}
        if (!tag.contains(STICK_KEY)) {
            player.sendSystemMessage(
                Component.literal("§c请先用木棍右键一个交互方块(按钮/拉杆/门等)"));
        //? if !1.20.1 {
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?}
            return;
        }
//? if 1.20.1 {
        BlockPos pos = NbtUtils.readBlockPos(tag.getCompound(STICK_KEY));
//?} else {
        // 2026-08-16 实测修: 写侧 tag.put(STICK_KEY, NbtUtils.writeBlockPos(pos)) 在 1.21.1 存的是
        // LongTag 直接作 STICK_KEY 值 (javap: writeBlockPos→Tag); 原 getCompound("pos") 读空 → 0,0,0。
        // 用官方 readBlockPos(CompoundTag,key) 兼容 LongTag 存储, 兜底女仆位不再 0,0,0
        BlockPos pos = NbtUtils.readBlockPos(tag, STICK_KEY).orElse(maid.blockPosition());
//?}

        // 写入 pipelineConfig (跨任务持久) — 2026-08-16 修: cfgOrCreate (getCompound 对不存在 key
        // 返回临时空 tag 不落盘 → 首次绑定丢失 → GUI/按键读不到; cfgOrCreate 先 put 再取引用)
        // v79.55 (错题 #183): NbtCodecs 双平台格式契约, 与读侧 BlockInteractPipeline.readPos 同款
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                .cfgOrCreate(maid, "block_interact");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, BlockInteractPipeline.KEY_POS, pos);

        TaskDispatcher.submit(maid, "block_interact", null, 0);

        tag.remove(STICK_KEY); // 清理木棍
        event.setCanceled(true);
        player.sendSystemMessage(
            Component.literal("§a女仆已绑定交互方块: " + pos.toShortString()
                + " §7(按键手动触发)"));
        //? if !1.20.1 {
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?}
    }
}
