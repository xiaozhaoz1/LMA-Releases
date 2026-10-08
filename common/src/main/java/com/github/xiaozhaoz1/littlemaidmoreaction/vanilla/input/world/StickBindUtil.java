package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.input.StickBindAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
//? if 1.20.1 {
import net.minecraftforge.common.capabilities.ForgeCapabilities;
//?} else {
import net.neoforged.neoforge.capabilities.Capabilities;
//?}
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

/**
 * 木棍标记/绑定通用工具 — ArmTransferSetupHandler / BlockInteractSetupHandler 共用。
 *
 * <p>v67.1: 提取两个 SetupHandler 重复的: 木棍获取 / 容器判断 / 任务类型门控。
 * 注意: isContainer 曾逐字复制在 2 个 Handler (v67 错题 #4) — 统一在此, 一处修改全局生效。
 *
 * <p>v67.2: 标记/绑定物品由 Cloth Config 驱动 (block_interact.mark_item / bind_item),
 * 无效物品 id 回退木棍。
 *
 * <p><b>2026-09-21 迁层 + 接口倒置</b>: 由 {@code event/} 迁入 {@code vanilla/input/world/}
 * (它是不碰事件的通用工具, 被 event/network/task 三处共用) ⇒ 但 vanilla 禁 import adapter **与** config,
 * 故原两处依赖改为经 {@link StickBindAccess} 取用 (实现在 {@code init.LmaTaskBinding},
 * 注入在 {@code init.LmaRegistrar.init()}; 未注入会抛 ISE = fail-fast)。
 */
public final class StickBindUtil {

    private StickBindUtil() {}

    /**
     * 取玩家手中的绑定物品 (主手优先, 副手次之)。
     *
     * @return 绑定物品 ItemStack, 未持有返回 null
     */
    public static ItemStack getStickStack(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!isBindItem(held)) {
            held = player.getOffhandItem();
            if (!isBindItem(held)) return null;
        }
        return held;
    }

    /** 标记物品检查 — 右键方块用 (config 驱动, 无效回退木棍; config 经 {@link StickBindAccess} 注入) */
    public static boolean isMarkItem(ItemStack stack) {
        return stack.is(resolveItem(StickBindAccess.markItemId(), Items.STICK));
    }

    /** 绑定物品检查 — 右键女仆用 (config 驱动, 无效回退木棍; config 经 {@link StickBindAccess} 注入) */
    public static boolean isBindItem(ItemStack stack) {
        return stack.is(resolveItem(StickBindAccess.bindItemId(), Items.STICK));
    }

    /** 解析配置物品 id — 无效/空回退默认物品 */
    private static Item resolveItem(String id, Item fallback) {
        if (id == null || id.isEmpty()) return fallback;
        ResourceLocation rl = ResourceLocation.tryParse(id);
//? if 1.20.1 {
        Item item = rl != null ? ForgeRegistries.ITEMS.getValue(rl) : null;
//?} else {
//? if 1.20.1 {
        Item item = rl != null ? BuiltInRegistries.ITEM.getValue(rl) : null;
//?} else {
        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
//?}
//?}
        return item != null ? item : fallback;
    }

    /**
     * 检查女仆当前任务类型 — 不匹配时返回 false (v79.62.1 修重复消息:
     *  ArmTransfer/BlockInteract 等多个 SetupHandler 都监听 InteractMaidEvent, 不匹配时
     *  各自发"不支持"提示 → 同一右键刷 3 条 (日志实证). 改为静默 — "当前任务不是本 handler
     *  的任务"是正常情况, 由匹配的 handler 处理; 不匹配无提示 (消重). */
    public static boolean checkTaskType(EntityMaid maid, String expected, Player player) {
        String taskType = StickBindAccess.taskTypeOf(maid.getTask().getUid().getPath());
        return expected.equals(taskType);
    }

    /**
     * 检查方块是否为容器 (任意方向暴露 ITEM_HANDLER 能力)。
     */
    public static boolean isContainer(LevelAccessor level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be == null) return false;
        for (Direction d : Direction.values()) {
//? if 1.20.1 {
            if (be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).isPresent()) return true;
//?} else {
            if (be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), d) != null) return true;
//?}
        }
        return false;
    }
}
