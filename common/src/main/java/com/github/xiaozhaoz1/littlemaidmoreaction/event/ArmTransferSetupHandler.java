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

    // ── ① 木棍右键容器: 标记取出点/放入点 ──

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!StickBindUtil.isMarkItem(held)) return;
        if (event.getLevel().isClientSide()) return;

        BlockPos pos = event.getPos();
        if (!StickBindUtil.isContainer(event.getLevel(), pos)) return;

//? if 1.20.1 {
        CompoundTag tag = held.getOrCreateTag();
//?} else {
CustomData _cd = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
CompoundTag tag = _cd.copyTag();
//?}
        BlockPos curTake = readPos(tag, "take");
        BlockPos curDep = readPos(tag, "deposit");

        if (pos.equals(curTake)) {
            tag.remove("take");
//? if 1.20.1 {
            tag.put("deposit", NbtUtils.writeBlockPos(pos));
//?} else {
CompoundTag _c = new CompoundTag();
_c.putLong("pos", pos.asLong());
tag.put("deposit", _c);
//?}
            event.getEntity().sendSystemMessage(comp("§e放入点已标记: " + pos.toShortString()
                + " §7(右键另一个容器标记取出点 → 右键女仆开始)"));
        //? if !1.20.1 {
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?}
        } else if (pos.equals(curDep)) {
            tag.remove("deposit");
//? if 1.20.1 {
            tag.put("take", NbtUtils.writeBlockPos(pos));
//?} else {
CompoundTag _c = new CompoundTag();
_c.putLong("pos", pos.asLong());
tag.put("take", _c);
//?}
            event.getEntity().sendSystemMessage(comp("§b取出点已标记: " + pos.toShortString()
                + " §7(右键女仆开始搬运)"));
        //? if !1.20.1 {
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?}
        } else {
            tag.remove("take");
//? if 1.20.1 {
            tag.put("take", NbtUtils.writeBlockPos(pos));
//?} else {
CompoundTag _c = new CompoundTag();
_c.putLong("pos", pos.asLong());
tag.put("take", _c);
//?}
            event.getEntity().sendSystemMessage(comp("§b取出点已标记: " + pos.toShortString()
                + " §7(再右键同一容器→放入点)"));
        //? if !1.20.1 {
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?}
        }
    }

    // ── ② 木棍右键女仆: 仅在 arm_transfer 任务时启动 ──

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        ItemStack held = StickBindUtil.getStickStack(player);
        if (held == null) return;
        if (maid.level().isClientSide) return;

        if (!StickBindUtil.checkTaskType(maid, "arm_transfer", player)) return;

//? if 1.20.1 {
        CompoundTag tag = held.getOrCreateTag();
//?} else {
CustomData _cd = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
CompoundTag tag = _cd.copyTag();
//?}
        BlockPos takePos = readPos(tag, "take");
        BlockPos depositPos = readPos(tag, "deposit");

        if (takePos == null) { player.sendSystemMessage(comp("§c请先用木棍右键容器标记取出点")); return; }
        if (depositPos == null) { player.sendSystemMessage(comp("§c请再用木棍右键另一个容器标记放入点")); return; }
        if (maid.getAvailableInv(false).getSlots() <= 0) { player.sendSystemMessage(comp("§c女仆没有背包")); return; }

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
        player.sendSystemMessage(comp("§a女仆开始搬运: " + takePos.toShortString() + " → " + depositPos.toShortString()));
    }

    // ── 工具 ──

    private static BlockPos readPos(CompoundTag tag, String key) {
//? if 1.20.1 {
        return tag.contains(key) ? NbtUtils.readBlockPos(tag.getCompound(key)) : null;
//?} else {
        return tag.contains(key) ? BlockPos.of(tag.getCompound(key).getLong("pos")) : null;
//?}
    }

    private static net.minecraft.network.chat.Component comp(String s) {
        return net.minecraft.network.chat.Component.literal(s);
    }
}
