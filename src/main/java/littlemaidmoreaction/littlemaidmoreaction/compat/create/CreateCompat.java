package littlemaidmoreaction.littlemaidmoreaction.compat.create;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Create 机械动力兼容 — 机械臂搬运任务。
 *
 * <p>仅读 NBT, 不影响机械臂原有功能 (不 cancel 事件)。
 * Create 机械臂右键容器时在 ItemStack NBT 存坐标,
 * 右键女仆时读取并传递。</p>
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class CreateCompat {
    private static final String ARM_ID = "create:mechanical_arm";
    public static final String KEY_TAKE = "take_pos";
    public static final String KEY_DEPOSIT = "deposit_pos";

    private CreateCompat() {}

    // ── ① 右键容器: 只读坐标, 不拦截 Create 正常功能 ──

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!isArm(held)) return;
        if (event.getLevel().isClientSide()) return;

        BlockPos pos = event.getPos();
        if (!isContainer(event.getLevel(), pos)) return;

        // 仅存坐标到 NBT, 不 cancel 事件 — Create 机械臂正常运作
        CompoundTag tag = held.getOrCreateTag();
        BlockPos curTake = read(tag, KEY_TAKE);
        BlockPos curDep = read(tag, KEY_DEPOSIT);

        if (pos.equals(curTake)) {
            tag.remove(KEY_TAKE);
            tag.put(KEY_DEPOSIT, NbtUtils.writeBlockPos(pos));
            event.getEntity().sendSystemMessage(comp("§e[女仆搬运] 放物点: " + pos.toShortString()));
        } else if (pos.equals(curDep)) {
            tag.remove(KEY_DEPOSIT);
            tag.put(KEY_TAKE, NbtUtils.writeBlockPos(pos));
            event.getEntity().sendSystemMessage(comp("§b[女仆搬运] 取物点: " + pos.toShortString()));
        } else {
            if (curTake != null) tag.remove(KEY_TAKE);
            tag.put(KEY_TAKE, NbtUtils.writeBlockPos(pos));
            event.getEntity().sendSystemMessage(comp("§b[女仆搬运] 取物点: " + pos.toShortString()));
        }
    }

    // ── ② 右键女仆: 读坐标 + 启动 ──

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        ItemStack held = player.getMainHandItem();
        if (!isArm(held)) { held = player.getOffhandItem(); if (!isArm(held)) return; }
        if (maid.level().isClientSide) return;

        CompoundTag tag = held.getOrCreateTag();
        BlockPos src = read(tag, KEY_TAKE);
        BlockPos dst = read(tag, KEY_DEPOSIT);

        if (src == null) { player.sendSystemMessage(comp("§c请先右键容器标记取物点")); return; }
        if (dst == null) { player.sendSystemMessage(comp("§c请右键另一个容器(或同一容器)标记放物点")); return; }
        if (maid.getAvailableInv(false).getSlots() <= 0) { player.sendSystemMessage(comp("§c女仆没有背包")); return; }

        var data = maid.getPersistentData();
        data.put("lma_arm_source", NbtUtils.writeBlockPos(src));
        data.put("lma_arm_target", NbtUtils.writeBlockPos(dst));
        data.putString("lma_flow_task", "arm_transfer");
        data.putString("lma_arm_phase", "NAV_SOURCE");

        tag.remove(KEY_TAKE);
        tag.remove(KEY_DEPOSIT);

        player.sendSystemMessage(comp("§a女仆开始搬运: " + src.toShortString() + " → " + dst.toShortString()));
    }

    // ── 工具 ──

    private static BlockPos read(CompoundTag tag, String key) {
        return tag.contains(key) ? NbtUtils.readBlockPos(tag.getCompound(key)) : null;
    }

    private static boolean isArm(ItemStack stack) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.toString().equals(ARM_ID);
    }

    private static boolean isContainer(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be == null) return false;
        for (var d : net.minecraft.core.Direction.values()) {
            if (be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).isPresent()) return true;
        }
        return false;
    }

    private static net.minecraft.network.chat.Component comp(String s) {
        return net.minecraft.network.chat.Component.literal(s);
    }
}
