package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 经验修装备 (v35.4 原子化版)。
 *
 * <p>每 tick 修一件: 主手↛副手↛头盔↛胸甲↛护腿↛靴子↛饰品栏↛背包
 * <p>消耗经验: repair = max(5, min(curDmg, exp/10)), cost = max(1, repair/2)
 */
@RuleAction
public final class RepairItemAction implements IAction {

    @Override public String id() { return "repair_item"; }
    @Override public String displayName() { return "经验修装备"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        EntityMaid m = ctx.maid();
        if (m.getExperience() <= 0) return;

        if (tryRepair(m, readMainhand(m))) return;
        if (tryRepair(m, readOffhand(m)))  return;
        if (tryRepair(m, readArmor(m, EquipmentSlot.HEAD)))  return;
        if (tryRepair(m, readArmor(m, EquipmentSlot.CHEST))) return;
        if (tryRepair(m, readArmor(m, EquipmentSlot.LEGS)))  return;
        if (tryRepair(m, readArmor(m, EquipmentSlot.FEET)))  return;
        if (tryRepairBauble(m)) return;
        if (tryRepairBackpack(m)) return;
    }

    // ═══ 读取 (input) ═══

    private static ItemStack readMainhand(EntityMaid m) { return MaidStateReader.getMainhand(m); }
    private static ItemStack readOffhand(EntityMaid m)  { return MaidStateReader.getOffhand(m); }
    private static ItemStack readArmor(EntityMaid m, EquipmentSlot slot) { return m.getItemBySlot(slot); }

    private static ItemStack readBaubleSlot(EntityMaid m, int i) { return m.getMaidBauble().getStackInSlot(i); }
    private static int readBaubleSlots(EntityMaid m) { return m.getMaidBauble().getSlots(); }

    private static ItemStack readBackpackSlot(EntityMaid m, int i) { return m.getAvailableBackpackInv().getStackInSlot(i); }
    private static int readBackpackSlots(EntityMaid m) { return m.getAvailableBackpackInv().getSlots(); }

    // ═══ 检查 + 修复 (compute + output) ═══

    /** 尝试修复单个 ItemStack — 成功返回 true */
    private static boolean tryRepair(EntityMaid m, ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || !stack.isDamaged()) return false;
        return doRepair(m, stack);
    }

    /** 遍历饰品栏 */
    private static boolean tryRepairBauble(EntityMaid m) {
        int slots = readBaubleSlots(m);
        for (int i = 0; i < slots; i++) {
            ItemStack s = readBaubleSlot(m, i);
            if (!s.isEmpty() && s.isDamageableItem() && s.isDamaged()) return doRepair(m, s);
        }
        return false;
    }

    /** 遍历背包 */
    private static boolean tryRepairBackpack(EntityMaid m) {
        int slots = readBackpackSlots(m);
        for (int i = 0; i < slots; i++) {
            ItemStack s = readBackpackSlot(m, i);
            if (!s.isEmpty() && s.isDamageableItem() && s.isDamaged()) return doRepair(m, s);
        }
        return false;
    }

    /** 消耗经验, 修复耐久 */
    private static boolean doRepair(EntityMaid m, ItemStack stack) {
        int exp = m.getExperience();
        int curDmg = stack.getDamageValue();
        int repair = Math.max(5, Math.min(curDmg, exp / 10));
        int cost = Math.max(1, repair / 2);
        if (exp < cost) return false;
        m.setExperience(exp - cost);
        stack.setDamageValue(Math.max(0, curDmg - repair));
        return true;
    }
}
