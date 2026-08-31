package com.github.xiaozhaoz1.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
//?}

/**
 * v79.62 挖空置域木棒标记 — 右键方块记起点 + 右键女仆启动.
 *
 * <p>① 木棒右键方块: 把该方块 pos 记到木棒 NBT {@code void_start} (气泡提示"起点已标记").
 * ② 木棒右键女仆 (潜行? 否): 读木棒 void_start → 写入女仆 PD start → 启动 void_excavation.
 *
 * <p>复用 StickBindUtil 标记物品判断 (config 驱动, 默认木棍).
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class VoidExcavationSetupHandler {

    private VoidExcavationSetupHandler() {}

    /** 木棒 NBT 起点键 */
    private static final String KEY_START = "void_start";

    /** ① 木棒右键方块 → 记起点 (非潜行, 非容器)。
     *  v79.62.1 修提示两次: 容器走 ArmTransfer/farm 绑定菜单 (本 handler 跳过, 只记普通方块起点) */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity().isShiftKeyDown()) return;   // shift 留给其他
        // v79.62.1 修提示两次: neoforge RightClickBlock 主/副手各触发一次 → 只处理主手
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (StickBindUtil.isContainer(event.getLevel(), event.getPos())) return;   // 容器→绑定菜单
        ItemStack held = StickBindUtil.getStickStack(event.getEntity());
        if (held == null) return;
        BlockPos pos = event.getPos();
//? if 1.20.1 {
        CompoundTag tag = held.getOrCreateTag();
//?} else {
        CustomData cd = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cd.copyTag();
//?}
        NbtCodecs.writeBlockPos(tag, KEY_START, pos);
//? if !1.20.1 {
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
//?}
        event.getEntity().sendSystemMessage(
                Component.literal("§a已标记: " + pos.toShortString()));
        // 取消原版方块交互 (开箱子等)
        event.setCanceled(true);
    }

    /** ② 木棒右键女仆 → 读起点 → 启动 void_excavation */
    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide) return;
        ItemStack held = StickBindUtil.getStickStack(player);
        if (held == null) return;
        // v79.62.1 任务类型门控 (用户裁定: 共用 take/deposit 按钮 — 右键空置域任务女仆才写入空置域,
        // 搬运/farm 由各自 SetupHandler 处理, 本 handler 不抢)
        String maidTask = com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry
                .extractTaskType(maid.getTask().getUid().getPath());
        // v79.62.2 填坝排水独立任务: 右键 dam_fill 任务女仆 → 写 dam_fill cfg + 启动 dam_fill
        // (标记点/输入箱与空置域共用木棒: void_start = 起点, take = 输入箱)
        if ("dam_fill".equals(maidTask)) {
            setupDamFill(player, maid, held);
            event.setCanceled(true);
            return;
        }
        if (!"void_excavation".equals(maidTask)) {
            player.sendSystemMessage(Component.literal("§e女仆当前任务: "
                    + (maidTask == null ? "idle" : maidTask) + " — 切到「挖空置域」或「填坝排水」任务再右键写入"));
            return;
        }
//? if 1.20.1 {
        CompoundTag tag = held.getOrCreateTag();
//?} else {
        CustomData cd = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cd.copyTag();
//?}
        if (!tag.contains(KEY_START)) {
            player.sendSystemMessage(Component.literal("§c请先用木棍右键一个方块标记起点"));
            return;
        }
        BlockPos start = NbtCodecs.readBlockPos(tag, KEY_START);
        if (start == null) {
            player.sendSystemMessage(Component.literal("§c起点无效，请重新标记"));
            return;
        }
        // 写女仆持久进度 cfg (v79.62.1: 挖空超长任务 — 进度存 lma_cfg 持久 NBT,
        // onCleanup 不清, 重启继续; 不存 PL 内存态 — onCleanup 会清)
        CompoundTag pd = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        NbtCodecs.writeBlockPos(pd, "start", start);
        // 默认区块数 (ClothConfig 全局)
        int size = com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.VOID_DEFAULT_CHUNKS.get();
        pd.putInt("size", size);
        // v79.62.1 用户裁定: cfg 只存标记点 — 运行时状态 (y/x/z/scan/curCX 等) 全放 PD (内存),
        // 由 pipeline tick 初始化 (pd.contains 检查 → start.getY() 起步), 不写持久 NBT (防卡顿).
        // 容器标记交付 (复用搬运 take/deposit): take→输入箱(工具), deposit→输出箱(方块)
        // 未标记 → 提示 (可不用容器, 方块积背包/工具自带)
        BlockPos take = NbtCodecs.readBlockPos(tag, "take");
        BlockPos deposit = NbtCodecs.readBlockPos(tag, "deposit");
        StringBuilder note = new StringBuilder("§a已启动挖空置域: 起点 " + start.toShortString()
                + " 区域 " + size + "×" + size + " 区块");
        if (take != null) {
            NbtCodecs.writeBlockPos(pd, "input", take);
            note.append("\n§7输入箱(工具): ").append(take.toShortString());
            // v79.62.1 保留标记 (用户裁定: 方便给下一个女仆用同一标记; 内存数据不持久)
        }
        if (deposit != null) {
            NbtCodecs.writeBlockPos(pd, "output", deposit);
            note.append("\n§7输出箱(方块): ").append(deposit.toShortString());
            // v79.62.1 保留标记 (同 take)
        }
        player.sendSystemMessage(Component.literal(note.toString()));
        // v79.62.1 保留木棒标记 (起点 + 容器标记不清除 — 给下一个女仆用; 内存数据不持久保存)
//? if !1.20.1 {
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
//?}
        // v79.62.1 修光标残留 (用户实证: 新任务 pd 有旧 x/y/z → cursor 用旧值 (111,-62,-33)
        // 而非标记区块角 (112,-63,-33) → 挖空气不推进). 启动前清 PD 运行时状态 → tick 重新认领.
        {
            var fpl = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, "void_excavation");
            fpl.remove("curCX"); fpl.remove("curCZ");
            fpl.remove("x"); fpl.remove("y"); fpl.remove("z");
            fpl.remove("claimWait"); fpl.remove("wait"); fpl.remove("digTicks");
            fpl.remove("navTimeout"); fpl.remove("navCd"); fpl.remove("wasHome"); fpl.remove("origWorkPos");
        }
        // 启动任务
        TaskDispatcher.submit(maid, "void_excavation", null, 0);
        event.setCanceled(true);
    }

    /** ③ v79.62.2 填坝排水启动 — 右键 dam_fill 任务女仆: 读木棒 void_start(起点) + take(输入箱)
     *  → 写 lma_cfg_dam_fill (start/size/input) → 启动 dam_fill.
     *  标记点/输入箱与空置域共用木棒键 (用户裁定: 两个任务独立写入各自 cfg, 互不覆盖). */
    private static void setupDamFill(Player player, EntityMaid maid, ItemStack held) {
//? if 1.20.1 {
        CompoundTag tag = held.getOrCreateTag();
//?} else {
        CustomData cd = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cd.copyTag();
//?}
        if (!tag.contains(KEY_START)) {
            player.sendSystemMessage(Component.literal("§c请先用木棍右键一个方块标记起点"));
            return;
        }
        BlockPos start = NbtCodecs.readBlockPos(tag, KEY_START);
        if (start == null) {
            player.sendSystemMessage(Component.literal("§c起点无效，请重新标记"));
            return;
        }
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "dam_fill");
        NbtCodecs.writeBlockPos(cfg, "start", start);
        int size = com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.VOID_DEFAULT_CHUNKS.get();
        cfg.putInt("size", size);
        // 输入箱 = 木棒 take (与空置域输入箱同键; dam 装重力方块)
        BlockPos take = NbtCodecs.readBlockPos(tag, "take");
        StringBuilder note = new StringBuilder("§a已启动填坝排水: 起点 " + start.toShortString()
                + " 区域 " + size + "×" + size + " 区块");
        // stonecutter 会破坏字符串字面量 \n → 用 (char)10 拼接 (v79.62.2 实证)
        if (take != null) {
            NbtCodecs.writeBlockPos(cfg, "input", take);
            note.append((char) 10).append("§7输入箱(重力方块): ").append(take.toShortString());
        } else {
            note.append((char) 10).append("§7未标记输入箱 — 女仆将等待重力方块");
        }
        player.sendSystemMessage(Component.literal(note.toString()));
        // 启动前清 dam_fill PD 运行时状态 (防旧游标残留)
        var fpd = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, "dam_fill");
        fpd.remove("wx"); fpd.remove("wz");
        fpd.remove("rowX"); fpd.remove("rowZ"); fpd.remove("rowDir");
        fpd.remove("topY");
        TaskDispatcher.submit(maid, "dam_fill", null, 0);
    }
}
