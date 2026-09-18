package com.github.xiaozhaoz1.littlemaidmoreaction.event;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.DataKey;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
//? if !1.20.1 {
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
//?}

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.ArmTransferPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
 * 木棍标记容器 + 右键女仆启动 arm_transfer (v53: 移出 compat/create)。
 * 木棍获取/容器判断/任务类型门控统一见 {@link StickBindUtil} (v67.1)。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class ArmTransferSetupHandler {

    private ArmTransferSetupHandler() {}

    // ── ① 木棍右键容器: 打开统一绑定菜单 (v79.62 — 4 角色: 种子/收获/取出/放入, 全局统一)
    // 客户端开屏在 LmaForgeClientEntry / LmaNeoForgeClientEntry (RightClickBlock 客户端监听,
    // common 类禁引 Minecraft — 主类可客户端类规则)。
    // v79.62 修复: 服务端必须取消容器打开 — 客户端 setCanceled 拦不住 ServerboundUseItemOnPacket,
    // 否则原版容器菜单(ClientboundOpenScreenPacket)会盖掉绑定屏 (用户实测「闪一下变成打开箱子」)。 ──

    /** 服务端: 标记物品(非潜行)右键容器 → 取消原版容器打开 (绑定屏已由客户端打开) */
    @SubscribeEvent
    public static void onRightClickContainer(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity().isShiftKeyDown()) return;   // shift 留给选区标记
        if (!StickBindUtil.isMarkItem(event.getItemStack())) return;
        if (!StickBindUtil.isContainer(event.getLevel(), event.getPos())) return;
        event.setCanceled(true);
    }

    // ── ② 木棍右键女仆: 交付容器绑定 → 按任务启动 (arm_transfer: 取/放; farm: 种子源/收获目标) ──

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        ItemStack held = StickBindUtil.getStickStack(player);
        if (held == null) return;
        if (maid.level().isClientSide) return;

//? if 1.20.1 {
        CompoundTag tag = held.getOrCreateTag();
//?} else {
CustomData _cd = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
CompoundTag tag = _cd.copyTag();
//?}
        String taskType = com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry
                .extractTaskType(maid.getTask().getUid().getPath());

        // farm 交付 (v79.62 区域制): 区域绑定走 FarmRegionBindPacket (客户端选区+木棍箱标记) —
        // 服务端 InteractMaidEvent 只消费本次右键 (客户端已并行发绑定包), 不在此建区域。
        // 木棍上无箱标记 → 提示先标记 (客户端无选区也不会发包)。
        if ("farm".equals(taskType)) {
            boolean hasBox = tag.contains("farm_seed") || tag.contains("farm_harvest");
            if (!hasBox) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("msg.littlemaidmoreaction.arm.need_seed_harvest"));
                return;
            }
            // 本次右键已由客户端发送 FarmRegionBindPacket — 事件仅置位防止 TLM 打开女仆界面
            event.setCanceled(true);
            return;
        }

        // arm_transfer 交付 (原逻辑)
        if (!StickBindUtil.checkTaskType(maid, "arm_transfer", player)) return;
        BlockPos takePos = readPos(tag, "take");
        BlockPos depositPos = readPos(tag, "deposit");

        if (takePos == null) { player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("msg.littlemaidmoreaction.arm.need_take")); return; }
        if (depositPos == null) { player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("msg.littlemaidmoreaction.arm.need_deposit")); return; }
        if (maid.getAvailableInv(false).getSlots() <= 0) { player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("msg.littlemaidmoreaction.arm.no_backpack")); return; }

        var data = maid.getPersistentData();
        //? if !1.20.1 {
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?}
        // v79.55 (错题 #183): 原直调 NbtUtils.writeBlockPos — 1.21.1 返回 IntArrayTag 强转 CompoundTag 必 CCE;
        // 改走 NbtCodecs (双平台格式契约, 与读侧 ArmTransferPipeline.readPos 同款)
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(data, DataKey.ARM_TAKE.key(), takePos);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(data, DataKey.ARM_DEPOSIT.key(), depositPos);

        TaskDispatcher.submit(maid, "arm_transfer", null, 0);

        tag.remove("take");
        tag.remove("deposit");

        event.setCanceled(true);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("msg.littlemaidmoreaction.arm.started", takePos.toShortString(), depositPos.toShortString()));
    }

    // ── 工具 ──

    /**
     * 读木棍标记 — 统一 {@link NbtCodecs} (v79.64.3).
     *
     * <p>原实现手工双平台解析 ({@code NbtUtils.readBlockPos(getCompound(key))} / {@code getCompound(key).getLong("pos")}),
     * 与写入侧 ({@link com.github.xiaozhaoz1.littlemaidmoreaction.network.FarmContainerBindPacket} 用 NbtCodecs) **各写一套** ⇒
     * 就是「绑定坐标丢失」(错题 #348) 的同款隐患 (重复实现必漂移; 且 1.20.1 分支缺"坏数据→null"校验, 会静默得 (0,0,0)) ✗
     */
    private static BlockPos readPos(CompoundTag tag, String key) {
        return NbtCodecs.readBlockPos(tag, key);
    }

    private static net.minecraft.network.chat.Component comp(String s) {
        return net.minecraft.network.chat.Component.literal(s);
    }
}
