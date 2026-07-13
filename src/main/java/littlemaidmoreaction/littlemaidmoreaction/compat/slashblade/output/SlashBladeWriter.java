package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.output;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;

/** SlashBlade 拔刀剑输出原语 */
public final class SlashBladeWriter {
    private SlashBladeWriter() {}

    private static boolean isSlashBlade(EntityMaid maid) {
        return maid.getMainHandItem().getItem() instanceof ItemSlashBlade;
    }

    public static void addProudSoul(EntityMaid maid, int amount) {
        if (!isSlashBlade(maid)) return;
        maid.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .ifPresent(s -> s.setProudSoulCount(s.getProudSoulCount() + amount));
    }
    public static void chargeSA(EntityMaid maid, int elapsed) {
        if (!isSlashBlade(maid)) return;
        maid.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .ifPresent(s -> s.doChargeAction(maid, elapsed));
    }
    public static void setCombo(EntityMaid maid, String combo) {
        if (!isSlashBlade(maid)) return;
        var rl = net.minecraft.resources.ResourceLocation.tryParse(combo);
        if (rl != null) maid.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .ifPresent(s -> { try { s.updateComboSeq(maid, rl); } catch (Exception ignored) {} });
    }
    public static void repairKatana(EntityMaid maid, int exp) {
        var stack = maid.getMainHandItem(); if (stack.isEmpty() || exp <= 0) return;
        if (!(stack.getItem() instanceof ItemSlashBlade)) return;
        stack.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            if (state.isBroken() || state.getDamage() > 0) {
                int dmg = state.getDamage();
                int repair = Math.max(5, Math.min(dmg, exp / 10));
                int cost = Math.max(1, repair / 3);
                if (exp >= cost) {
                    maid.setExperience(exp - cost);
                    int newDmg = Math.max(0, dmg - repair);
                    state.setDamage(newDmg);
                    if (newDmg <= 0) { state.setBroken(false); state.setDamage(0); }
                }
            }
        });
    }
}
