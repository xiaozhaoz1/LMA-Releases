package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.FakePlayerInteract;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.HandSwap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import java.util.Set;

/**
 * 方块放置工具 (v79.23 垫方块协调器起源) — v79.26.8e 垫柱链全删 (用户裁定:
 * "不用垫方块了, 只要挖上下能挖到的就行了"), 本类瘦身为危险堵护
 * ({@link DangerGuardCoordinator}) 复用的放置链: 柱材换主手 + 假人放置 + 工具恢复。
 *
 * <p>v79.61x 定级 (两轴表): 无跨 tick 类内状态 + 堵护域业务语义 → 单拍执行器
 * (service 单拍级), 留 execute 族 (DangerGuard 协调器调用面; 搬 task/service 会倒挂依赖)。
 */
public final class BlockUpCoordinator {

    /** 柱材白名单 (方块 id path) — 堵护/垫柱共用 */
    private static final Set<String> MATERIALS = Set.of(
            "dirt", "grass_block", "stone", "cobblestone", "deepslate",
            "cobbled_deepslate", "oak_planks", "oak_log", "sand", "gravel");

    private BlockUpCoordinator() {}

    /** 放方块 — 柱材换主手再放置 (假人主手物品点击 pos 的 face 面 → 块落 pos.relative(face)),
     *  放完恢复原工具。package-private static — 危险堵护 (DangerGuardCoordinator)
     *  复用同一放置链 (pos+face 参数化); 垫柱链删后仅堵护调用。 */
    static boolean placeMaterial(ServerLevel world, EntityMaid maid, BlockPos pos, Direction face) {
        var inv = maid.getAvailableInv(true);
        int slot = findMaterialSlot(maid);
        if (slot < 0) return false;
        // 整槽提取 + 旧主手保全 — HandSwap 三链 (槽位 → 背包 → 落地; 丢物品族 #162:
        // 原 extractItem(1) 后槽仍被剩余柱材占用, 旧工具 insertItem 塞不进 → 工具永久丢失)
        ItemStack mat = HandSwap.extractSlotStashOld(maid, slot);
        if (mat.isEmpty()) return false;
        ItemStack old = maid.getMainHandItem();
        maid.setItemInHand(InteractionHand.MAIN_HAND, mat);
        boolean ok = FakePlayerInteract.placeBlock(world, maid, pos, face);
        // 恢复: 旧工具从槽位整量回主手 (CR-1: 原 extractItem(1) 对可堆叠旧物 (count≥2) 只取 1 个 —
        // 剩余占槽 → 柱材塞回空间不足 → 余量静默丢失), 剩余柱材 (放置失败/多块) 放回槽位
        ItemStack hand = maid.getMainHandItem();
        if (!old.isEmpty()) {
            ItemStack back = inv.extractItem(slot, inv.getStackInSlot(slot).getCount(), false);
            if (!back.isEmpty()) maid.setItemInHand(InteractionHand.MAIN_HAND, back);
        }
        if (!hand.isEmpty()) {
            ItemStack remainder = inv.insertItem(slot, hand, false);
            if (!remainder.isEmpty()) HandSwap.stashOrDrop(maid, remainder);
        }
        return ok;
    }

    /** 柱材槽位 — 背包白名单方块 (getAvailableInv 全槽, 含背包; 每次调用新包装共享底层) */
    private static int findMaterialSlot(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) continue;
            String path = BuiltInRegistries.BLOCK.getKey(bi.getBlock()).getPath();
            if (MATERIALS.contains(path)) return i;
        }
        return -1;
    }
}
