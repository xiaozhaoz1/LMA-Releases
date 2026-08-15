package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.MaidAttrRegistry;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}
//? if 1.20.1 {
import net.minecraftforge.registries.RegistryObject;
//?} else {
import java.util.function.Supplier;
//?}

/** 女仆状态写入 (v79.50b: IWriter SPI 死链删 — 接口零调用方, write() 分发面整删,
 *  static 方法编目保留) */
public final class MaidStateWriter {
    private MaidStateWriter() {}

    public static void setSitting(EntityMaid m, boolean v) { m.setInSittingPose(v); }
    public static void setInvisible(EntityMaid m, boolean v) { m.setInvisible(v); }
    public static void setGlowing(EntityMaid m, boolean v) { m.setGlowingTag(v); }
    public static void setInvulnerable(EntityMaid m, boolean v) { m.setEntityInvulnerable(v); }
    public static void setSilent(EntityMaid m, boolean v) { m.setSilent(v); }
    public static void setHome(EntityMaid m, BlockPos pos) { m.restrictTo(pos, (int)m.getRestrictRadius()); }
    public static void setHomeMode(EntityMaid m, boolean v) { m.setHomeModeEnable(v); }
    public static void setHunger(EntityMaid m, int v) { m.setHunger(v); }
    public static void setFavor(EntityMaid m, int v) { m.setFavorability(v); }
    public static void setFavorMax(EntityMaid m) { m.getFavorabilityManager().max(); }
    public static void setModel(EntityMaid m, String modelId) { m.setModelId(modelId); }

    public static void setTask(EntityMaid m, String taskUid) {
        var rl = ResourceLocation.tryParse(taskUid);
        if (rl != null) TaskManager.findTask(rl).ifPresent(m::setTask);
    }
    public static void forceTarget(EntityMaid m, LivingEntity target) { m.setTarget(target); }
    public static void forceTargetByMode(EntityMaid m, String mode) {
        LivingEntity forced = switch (mode) {
            case "owner_attacker" -> { var o = m.getOwner(); yield o != null ? o.getLastHurtByMob() : null; }
            case "owner_target" -> { var o = m.getOwner(); yield o != null ? o.getLastHurtMob() : null; }
            case "nearest_hostile" -> m.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                m.getBoundingBox().inflate(32), e -> true).stream().findFirst().orElse(null);
            case "owner" -> (LivingEntity) m.getOwner();
            default -> null;
        };
        m.setTarget(forced);
    }
    public static void clearRestriction(EntityMaid m) { m.clearRestriction(); }

    public static void setAiming(EntityMaid m, boolean v) { m.setAiming(v); }
    public static void setBegging(EntityMaid m, boolean v) { m.setBegging(v); }
    public static void setRideable(EntityMaid m, boolean v) { m.setRideable(v); }
    public static void setCanClimb(EntityMaid m, boolean v) { m.setCanClimb(v); }
    public static void setSwinging(EntityMaid m, boolean v) { m.setSwingingArms(v); }
    public static void setPickup(EntityMaid m, boolean v) { m.setPickup(v); }
    public static void swapHands(EntityMaid m) {
        var main = m.getMainHandItem(); var off = m.getOffhandItem();
        m.setItemSlot(EquipmentSlot.MAINHAND, off);
        m.setItemSlot(EquipmentSlot.OFFHAND, main);
    }
    public static void clearInventory(LivingEntity target) {
        target.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        target.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        target.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        target.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        target.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }
    public static void setBackpackShowItem(EntityMaid m, String itemId) {
        var rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return;
//? if 1.20.1 {
        var item = ForgeRegistries.ITEMS.getValue(rl);
//?} else {
        var item = BuiltInRegistries.ITEM.get(rl);
//?}
        if (item != null) m.setBackpackShowItem(new ItemStack(item));
    }
    public static void repairItem(EntityMaid m, EquipmentSlot slot, int amount) {
        var s = m.getItemBySlot(slot);
        if (!s.isEmpty()) s.setDamageValue(Math.max(0, s.getDamageValue() - amount));
    }
    public static void setSchedulePos(EntityMaid m, BlockPos pos, String mode) {
        var sp = m.getSchedulePos();
        switch (mode) {
            case "set_work" -> sp.setWorkPos(pos);
            case "set_idle" -> sp.setIdlePos(pos);
            case "set_sleep" -> sp.setSleepPos(pos);
            case "clear" -> sp.clear(m);
        }
        sp.setConfigured(true);
        m.setHomeModeEnable(true);
    }
    public static void setBauble(EntityMaid m, String itemId, int slot) {
//? if 1.20.1 {
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
//?} else {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
//?}
        if (item == null) return;
//? if 1.20.1 {
        var key = ForgeRegistries.ITEMS.getKey(item);
//?} else {
        var key = BuiltInRegistries.ITEM.getKey(item);
//?}
//? if 1.20.1 {
        if (key == null || BaubleManager.getBauble(RegistryObject.create(key, ForgeRegistries.ITEMS)) == null) return;
//?} else {
        if (key == null || BaubleManager.getBauble(net.neoforged.neoforge.registries.DeferredHolder.create(net.minecraft.core.registries.Registries.ITEM, key)) == null) return;
//?}
        var bauble = m.getMaidBauble();
        var stack = new ItemStack(item);
        if (slot >= 0 && slot < bauble.getSlots()) {
            var old = bauble.getStackInSlot(slot);
            if (!old.isEmpty()) m.spawnAtLocation(old); // 不吞旧饰品
            bauble.setStackInSlot(slot, stack);
            return;
        }
        for (int i = 0; i < bauble.getSlots(); i++) {
            if (bauble.getStackInSlot(i).isEmpty()) { bauble.setStackInSlot(i, stack); return; }
        }
        m.spawnAtLocation(stack); // 所有槽满，丢地上
    }
    public static void teleportToOwner(EntityMaid m) {
        var o = m.getOwner(); if (o != null) m.teleportTo(o.getX(), o.getY(), o.getZ());
    }

    public static void setExperience(EntityMaid m, int amount) { m.setExperience(amount); }
    public static void addExperience(EntityMaid m, int amount) { m.setExperience(m.getExperience() + amount); }
    public static void setSoundPack(EntityMaid m, String id) { m.setSoundPackId(id); }
    public static void setSchedule(EntityMaid m, String schedule) {
        var s = switch (schedule) {
            case "DAY" -> com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule.DAY;
            case "NIGHT" -> com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule.NIGHT;
            default -> com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule.ALL;
        };
        m.setSchedule(s);
    }
    public static void setPickup(EntityMaid m, boolean enabled, String type) {
        m.setPickup(enabled);
        if (enabled) {
            var cfg = m.getConfigManager();
            cfg.setPickupType(switch (type) {
                case "only_item" -> com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.ONLY_ITEM;
                case "only_xp" -> com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.ONLY_XP;
                default -> com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.ALL;
            });
        }
    }

    // === NBT/PersistentData ===
    public static void setPersistentInt(EntityMaid m, String key, int value) { m.getPersistentData().putInt(key, value); }
    public static void setPersistentFloat(EntityMaid m, String key, float value) { m.getPersistentData().putFloat(key, value); }
    public static void setPersistentLong(EntityMaid m, String key, long value) { m.getPersistentData().putLong(key, value); }
    public static void setPersistentString(EntityMaid m, String key, String value) { m.getPersistentData().putString(key, value); }
    public static void setPersistentBoolean(EntityMaid m, String key, boolean value) { m.getPersistentData().putBoolean(key, value); }
    public static void removePersistent(EntityMaid m, String key) { m.getPersistentData().remove(key); }

    // saveAndSwitchTask/restorePreviousTask 已删 (v79.54, 错题 #180): 零调用方死代码,
    // "lma_prev_task" 键写方全死 — task 恢复由 TLM TASK_TAG 原生持久化负责
    public static boolean repairHandItemWithXp(EntityMaid m) {
        var stack = m.getMainHandItem();
        if (stack.isEmpty() || !stack.isDamageableItem()) return false;
        int exp = m.getExperience(), curDmg = stack.getDamageValue();
        if (exp <= 0 || curDmg <= 0) return false;
        int repair = Math.max(5, Math.min(curDmg, exp / 10));
        int cost = Math.max(1, repair / 2);
        if (exp < cost) return false;
        m.setExperience(exp - cost);
        stack.setDamageValue(Math.max(0, curDmg - repair));
        return true;
    }

    /**
     * 全槽位经验修装备 (RepairItemAction 组合吸收) — 主手 (复用 repairHandItemWithXp)
     * → 副手 → 4 甲 → 饰品 → 背包, 单次修一件。
     */
    public static boolean repairItemWithXp(EntityMaid m) {
        if (repairHandItemWithXp(m)) return true;
        net.minecraft.world.item.ItemStack stack = findRepairable(m);
        if (stack == null) return false;
        int exp = m.getExperience();
        if (exp <= 0) return false;
        int curDmg = stack.getDamageValue();
        int repair = Math.max(5, Math.min(curDmg, exp / 10));
        int cost = Math.max(1, repair / 2);
        if (exp < cost) return false;
        m.setExperience(exp - cost);
        stack.setDamageValue(Math.max(0, curDmg - repair));
        return true;
    }

    /**
     * 自动修复原语 (v79.48) — 一次修 1 点耐久 (AutoRepairBehavior 每 ~5 秒调)。
     * 顺序: 主手 → 其余 (副手/4甲/饰品/背包, {@link #findRepairable} 复用)。
     * 消耗: max(1, 4 × 好感度消耗乘区) XP/点; 失败 (无破损/经验不足) 静默返回 false。
     */
    public static boolean repairOneWithXp(EntityMaid m) {
        if (repairOne(m, m.getMainHandItem())) return true;
        net.minecraft.world.item.ItemStack stack = findRepairable(m);
        return stack != null && repairOne(m, stack);
    }

    private static boolean repairOne(EntityMaid m, net.minecraft.world.item.ItemStack stack) {
        if (stack == null || !isRepairable(stack)) return false;
        int exp = m.getExperience();
        int cost = repairCostFor(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.MaidFavorability.costMultiplier(m));
        if (exp < cost) return false;
        m.setExperience(exp - cost);
        stack.setDamageValue(Math.max(0, stack.getDamageValue() - 1));
        return true;
    }

    /**
     * 1 点耐久消耗 (XP) — 纯函数: 4 × 消耗乘区, 至少 1。
     * 原版 Mending 1 耐久 = 2 XP; LMA 基数 4 (2 倍), 好感度 Lv3 默认 0.5 → 2 = 原版水平。
     */
    public static int repairCostFor(double costMultiplier) {
        return Math.max(1, (int) Math.round(4 * costMultiplier));
    }

    /** 查找可修装备 — 副手 → 4 甲 → 饰品 → 背包 */
    @javax.annotation.Nullable
    private static net.minecraft.world.item.ItemStack findRepairable(EntityMaid m) {
        net.minecraft.world.item.ItemStack off = m.getOffhandItem();
        if (isRepairable(off)) return off;
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            net.minecraft.world.item.ItemStack s = m.getItemBySlot(slot);
            if (isRepairable(s)) return s;
        }
        var bauble = m.getMaidBauble();
        for (int i = 0; i < bauble.getSlots(); i++) {
            net.minecraft.world.item.ItemStack s = bauble.getStackInSlot(i);
            if (isRepairable(s)) return s;
        }
        var inv = m.getAvailableBackpackInv();
        for (int i = 0; i < inv.getSlots(); i++) {
            net.minecraft.world.item.ItemStack s = inv.getStackInSlot(i);
            if (isRepairable(s)) return s;
        }
        return null;
    }

    private static boolean isRepairable(net.minecraft.world.item.ItemStack s) {
        return !s.isEmpty() && s.isDamageableItem() && s.isDamaged();
    }

    // === 物品操作 ===
    public static void giveItem(EntityMaid m, String itemId, int count, String nbtStr, String target) {
//? if 1.20.1 {
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
//?} else {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
//?}
        if (item == null) return;
        var stack = new ItemStack(item, count);
        if (!nbtStr.isEmpty()) try {
            var t = net.minecraft.nbt.TagParser.parseTag(nbtStr);
//? if 1.20.1 {
            if (t != null) stack.setTag(t);
//?} else {
            if (t != null) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(t));
//?}
        } catch (Exception e) { LittleMaidMoreAction.LOGGER.warn("giveItem: failed to parse NBT", e); }
        if ("owner".equals(target)) {
            var o = m.getOwner();
            if (o instanceof Player p && !p.addItem(stack)) p.drop(stack, false);
        } else {
            var remainder = m.getMaidInv().insertItem(0, stack, false);
            if (!remainder.isEmpty()) m.spawnAtLocation(remainder); // 放不下则丢地上
        }
    }
    public static void dropFromSlot(EntityMaid m, String source, int count) {
        ItemStack stack;
        if ("mainhand".equals(source)) { stack = m.getMainHandItem(); if (stack.isEmpty()) return; stack = extractOrCopy(m, EquipmentSlot.MAINHAND, stack, count); }
        else if ("offhand".equals(source)) { stack = m.getOffhandItem(); if (stack.isEmpty()) return; stack = extractOrCopy(m, EquipmentSlot.OFFHAND, stack, count); }
        else if (source.startsWith("inv_")) try {
            int s = Integer.parseInt(source.substring(4));
            var inv = m.getMaidInv(); if (s >= inv.getSlots()) return;
            var is = inv.getStackInSlot(s); if (is.isEmpty()) return;
            int to = (count <= 0 || count >= is.getCount()) ? is.getCount() : count;
            stack = inv.extractItem(s, to, false); if (stack.isEmpty()) return;
        } catch (NumberFormatException e) { return; }
        else return;
        m.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(m.level(), m.getX(), m.getY()+1, m.getZ(), stack));
    }
    private static ItemStack extractOrCopy(LivingEntity e, EquipmentSlot slot, ItemStack o, int count) {
        int to = (count <= 0 || count >= o.getCount()) ? o.getCount() : count;
        var r = o.copy(); r.setCount(to); o.shrink(to);
        if (o.isEmpty()) e.setItemSlot(slot, ItemStack.EMPTY);
        return r;
    }

    /** 修改女仆属性 — 通过 MaidAttrRegistry */
    public static void modifyAttribute(EntityMaid m, String attrKey, String mode, double amount) {
        var entry = MaidAttrRegistry.getDef(attrKey);
        if (entry == null) return;
        switch (entry.valueType()) {
            case "num" -> {
                double current = MaidAttrRegistry.get(m, attrKey);
                double newVal = switch (mode) { case "set" -> amount; case "multiply" -> current * amount; case "divide" -> amount != 0.0 ? current / amount : current; default -> current + amount; };
                newVal = Math.max(0.0, Math.min(1024.0, newVal));
                if ("max_health".equals(attrKey)) newVal = Math.max(1.0, newVal);
                MaidAttrRegistry.setBase(m, attrKey, newVal);
            }
            case "bool" -> MaidAttrRegistry.setBase(m, attrKey, amount != 0.0 ? 1.0 : 0.0);
        }
    }

    // === Phase 11: TLM FavorabilityManager API ===
    /** 增加好感度 */
    public static void addFavor(EntityMaid m, int amount) { m.getFavorabilityManager().add(amount); }
    /** 减少好感度（可降级） */
    public static void reduceFavor(EntityMaid m, int amount) { m.getFavorabilityManager().reduce(amount); }
    /** 好感度升至满级 */
    public static void maxFavor(EntityMaid m) { m.getFavorabilityManager().max(); }

    // === ConfigManager 写 ===
    public static void setShowBackpack(EntityMaid m, boolean v) { m.getConfigManager().setShowBackpack(v); }
    public static void setShowBackItem(EntityMaid m, boolean v) { m.getConfigManager().setShowBackItem(v); }
    public static void setChatBubbleShow(EntityMaid m, boolean v) { m.getConfigManager().setChatBubbleShow(v); }
    public static void setSoundFreq(EntityMaid m, float v) { m.getConfigManager().setSoundFreq(v); }
    public static void setPickupType(EntityMaid m, String type) {
        m.getConfigManager().setPickupType(switch (type.toLowerCase()) {
            case "only_item" -> com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.ONLY_ITEM;
            case "only_xp" -> com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.ONLY_XP;
            default -> com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.ALL;
        });
    }
    public static void setOpenDoor(EntityMaid m, boolean v) { m.getConfigManager().setOpenDoor(v); }
    public static void setOpenFenceGate(EntityMaid m, boolean v) { m.getConfigManager().setOpenFenceGate(v); }
    public static void setActiveClimbing(EntityMaid m, boolean v) { m.getConfigManager().setActiveClimbing(v); }

    // === 女仆编辑器 Writer ===
    public static void setHealth(EntityMaid m, float v) { m.setHealth(Math.min(v, m.getMaxHealth())); }
    public static void setMaxHealth(EntityMaid m, double v) { m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(Math.max(1.0, v)); }
    public static void setAttackDamage(EntityMaid m, double v) { m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(v); }
    public static void setMovementSpeed(EntityMaid m, double v) { m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(v); }
    public static void setFollowRange(EntityMaid m, double v) { m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(v); }
    public static void setKnockbackResistance(EntityMaid m, double v) { m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(v); }
    public static void setAttackSpeed(EntityMaid m, double v) { m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED).setBaseValue(v); }
    public static void setArmorToughness(EntityMaid m, double v) { m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(v); }
    public static void setBaby(EntityMaid m, boolean v) { m.setBaby(v); }
    public static void setSprinting(EntityMaid m, boolean v) { m.setSprinting(v); }
    public static void setSneaking(EntityMaid m, boolean v) { m.setShiftKeyDown(v); }
    public static void setSwimming(EntityMaid m, boolean v) { m.setSwimming(v); }
    public static void setRestrictRadius(EntityMaid m, float radius) {
        var center = m.hasRestriction() ? m.getRestrictCenter() : m.blockPosition();
        m.restrictTo(center, Math.round(radius));
    }
    // TLM 自定义属性
//? if 1.20.1 {
    public static void setUseItemSpeed(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_USE_ITEM_SPEED.get()).setBaseValue(v); }
    public static void setCrossbowAttackSpeed(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_CROSSBOW_ATTACK_SPEED.get()).setBaseValue(v); }
    public static void setGunAttackSpeed(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_GUN_ATTACK_SPEED.get()).setBaseValue(v); }
    public static void setShootCooldown(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_SHOOT_COOLDOWN.get()).setBaseValue(v); }
    public static void setTridentCooldown(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_TRIDENT_COOLDOWN.get()).setBaseValue(v); }
    public static void setPickupRange(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_PICKUP_RANGE.get()).setBaseValue(v); }
    public static void setPassiveUseShieldTick(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_PASSIVE_USE_SHIELD_TICK.get()).setBaseValue(v); }
//?} else {
    // 1.21: DeferredHolder 本身即 Holder, getAttribute(Holder) 直接传, 不 .get()
    public static void setUseItemSpeed(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_USE_ITEM_SPEED).setBaseValue(v); }
    public static void setCrossbowAttackSpeed(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_CROSSBOW_ATTACK_SPEED).setBaseValue(v); }
    public static void setGunAttackSpeed(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_GUN_ATTACK_SPEED).setBaseValue(v); }
    public static void setShootCooldown(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_SHOOT_COOLDOWN).setBaseValue(v); }
    public static void setTridentCooldown(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_TRIDENT_COOLDOWN).setBaseValue(v); }
    public static void setPickupRange(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_PICKUP_RANGE).setBaseValue(v); }
    public static void setPassiveUseShieldTick(EntityMaid m, double v) { m.getAttribute(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_PASSIVE_USE_SHIELD_TICK).setBaseValue(v); }
//?}
}
