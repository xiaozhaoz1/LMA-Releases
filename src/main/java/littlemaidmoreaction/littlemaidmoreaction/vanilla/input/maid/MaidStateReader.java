package littlemaidmoreaction.littlemaidmoreaction.vanilla.input.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IReader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.StringJoiner;

@SuppressWarnings("unused")
public final class MaidStateReader implements IReader<EntityMaid> {
    private MaidStateReader() {}

    @Override public String category() { return "maid"; }
    @Override public Class<EntityMaid> sourceType() { return EntityMaid.class; }
    @Override public <T> T read(EntityMaid m, String property, Class<T> type) {
        throw new UnsupportedOperationException("Use static methods directly");
    }
    @Override public Number readNumber(EntityMaid m, String property) {
        return switch (property) {
            case "health" -> m.getHealth();
            case "max_health" -> m.getMaxHealth();
            default -> throw new IllegalArgumentException("Unknown property: " + property);
        };
    }

    // === 基础数值 ===
    public static float getHealth(EntityMaid m) { return m.getHealth(); }
    public static float getHealthRatio(EntityMaid m) { return m.getHealth() / m.getMaxHealth(); }
    public static float getMaxHealth(EntityMaid m) { return m.getMaxHealth(); }
    public static int getHunger(EntityMaid m) { return m.getHunger(); }
    public static int getFavorability(EntityMaid m) { return m.getFavorability(); }
    public static int getExperience(EntityMaid m) { return m.getExperience(); }
    public static float getLuck(EntityMaid m) { return m.getLuck(); }

    // === 布尔状态 ===
    public static boolean isOnFire(EntityMaid m) { return m.isOnFire(); }
    public static boolean isInWater(EntityMaid m) { return m.isInWater(); }
    public static boolean isInLava(EntityMaid m) { return m.isInLava(); }
    public static boolean isInRain(EntityMaid m) { return m.level().isRainingAt(m.blockPosition()); }
    public static boolean isSitting(EntityMaid m) { return m.isMaidInSittingPose(); }
    public static boolean isSleeping(EntityMaid m) { return m.isSleeping(); }
    public static boolean isSprinting(EntityMaid m) { return m.isSprinting(); }
    public static boolean isSwimming(EntityMaid m) { return m.isSwimming(); }
    public static boolean isSneaking(EntityMaid m) { return m.isCrouching(); }
    public static boolean isRiding(EntityMaid m) { return m.isPassenger(); }
    public static boolean isBlocking(EntityMaid m) { return m.isBlocking(); }
    public static boolean isBaby(EntityMaid m) { return m.isBaby(); }
    public static boolean isTamed(EntityMaid m) { return m.getOwnerUUID() != null; }
    public static boolean isOwnedBy(EntityMaid m, net.minecraft.world.entity.player.Player p) {
        return p != null && p.getUUID().equals(m.getOwnerUUID());
    }
    public static boolean isInvulnerable(EntityMaid m) { return m.getIsInvulnerable(); }
    public static boolean isInvisible(EntityMaid m) { return m.isInvisible(); }
    public static boolean isGlowing(EntityMaid m) { return m.isCurrentlyGlowing(); }
    public static boolean isSwinging(EntityMaid m) { return m.isSwingingArms(); }
    public static boolean isAiming(EntityMaid m) { return m.isAiming(); }
    public static boolean isBegging(EntityMaid m) { return m.isBegging(); }
    public static boolean isRideable(EntityMaid m) { return m.isRideable(); }
    public static boolean isStruckByLightning(EntityMaid m) { return m.isStruckByLightning(); }
    public static boolean canClimb(EntityMaid m) { return m.isCanClimb(); }
    public static boolean canBrainMove(EntityMaid m) { return m.canBrainMoving(); }
    public static boolean hasTarget(EntityMaid m) { return m.getTarget() != null; }
    public static boolean hasWeapon(EntityMaid m) { return !m.getMainHandItem().isEmpty(); }
    public static boolean hasShield(EntityMaid m) { return m.isBlocking(); }
    public static boolean hasAnyEffect(EntityMaid m) { return !m.getActiveEffects().isEmpty(); }

    // === 装备/物品 ===
    public static ItemStack getMainhand(EntityMaid m) { return m.getMainHandItem(); }
    public static String getMainhandId(EntityMaid m) {
        var s = m.getMainHandItem(); return s.isEmpty() ? "air" : ForgeRegistries.ITEMS.getKey(s.getItem()).toString();
    }
    public static ItemStack getOffhand(EntityMaid m) { return m.getOffhandItem(); }
    public static String getOffhandId(EntityMaid m) {
        var s = m.getOffhandItem(); return s.isEmpty() ? "air" : ForgeRegistries.ITEMS.getKey(s.getItem()).toString();
    }
    public static float getArmor(EntityMaid m) { return m.getArmorValue(); }
    public static float getArmorToughness(EntityMaid m) {
        return (float) m.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
    }
    public static float getAttackDamage(EntityMaid m) {
        return (float) m.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }
    public static float getMovementSpeed(EntityMaid m) {
        return (float) m.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }
    public static boolean hasHelmet(EntityMaid m) { return !m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(); }
    public static boolean hasChestplate(EntityMaid m) { return !m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(); }
    public static boolean hasLeggings(EntityMaid m) { return !m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty(); }
    public static boolean hasBoots(EntityMaid m) { return !m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty(); }

    // === 主人 ===
    public static float getOwnerDistance(EntityMaid m) {
        var o = m.getOwner(); return o != null ? m.distanceTo(o) : Float.MAX_VALUE;
    }
    public static float getOwnerHealth(EntityMaid m) {
        var o = m.getOwner(); return o != null ? o.getHealth() : 0f;
    }
    public static float getOwnerHealthRatio(EntityMaid m) {
        var o = m.getOwner(); return o != null ? o.getHealth() / o.getMaxHealth() : 0f;
    }
    public static boolean isOwnerNearby(EntityMaid m) {
        var o = m.getOwner(); return o != null && m.distanceTo(o) < 16;
    }
    public static boolean ownerHasAttackTarget(EntityMaid m) {
        var o = m.getOwner(); return o != null && o.getLastHurtMob() != null;
    }
    public static ItemStack getOwnerHoldingItem(EntityMaid m) {
        var o = m.getOwner(); return o != null ? o.getMainHandItem() : ItemStack.EMPTY;
    }
    public static String getOwnerHoldingItemId(EntityMaid m) {
        var o = m.getOwner(); return o != null ? ForgeRegistries.ITEMS.getKey(o.getMainHandItem().getItem()).toString() : "none";
    }

    // === 属性 ===
    public static String getModelId(EntityMaid m) { return m.getModelId(); }
    public static float getRestrictRadius(EntityMaid m) { return m.getRestrictRadius(); }
    public static boolean hasRestriction(EntityMaid m) { return m.hasRestriction(); }
    public static float getFallDistance(EntityMaid m) { return m.fallDistance; }
    public static int getAirSupply(EntityMaid m) { return m.getAirSupply(); }
    public static int getMaxAirSupply(EntityMaid m) { return m.getMaxAirSupply(); }
    public static boolean isUsingItem(EntityMaid m) { return m.isUsingItem(); }

    // === 任务 ===
    public static String getTaskUid(EntityMaid m) { return m.getTask().getUid().toString(); }
    public static boolean isHomeMode(EntityMaid m) { return m.isHomeModeEnable(); }
    public static String getSchedule(EntityMaid m) { return m.getSchedule().name(); }

    // === 主人装备 ===
    public static float getOwnerArmor(EntityMaid m) {
        var o = m.getOwner(); return o != null ? o.getArmorValue() : 0f;
    }
    public static String getOwnerOffhandId(EntityMaid m) {
        var o = m.getOwner(); return o != null ? ForgeRegistries.ITEMS.getKey(o.getOffhandItem().getItem()).toString() : "none";
    }

    // === Phase 4: 背包/拾取/音效/日程 ===
    public static boolean isPickup(EntityMaid m) { return m.isPickup(); }
    public static String getSoundPackId(EntityMaid m) { return m.getSoundPackId(); }
    public static String getBackpackType(EntityMaid m) { return m.getMaidBackpackType().getId().toString(); }
    public static int getBackpackSlots(EntityMaid m) { return m.getMaidBackpackType().getAvailableMaxContainerIndex(); }
    public static String getBackpackFluid(EntityMaid m) { return m.getBackpackFluid(); }
    public static boolean hasBackpack(EntityMaid m) { return m.hasBackpack(); }
    public static boolean hasFishingHook(EntityMaid m) { return m.hasFishingHook(); }
    public static boolean isHoldingProjectile(EntityMaid m) {
        var i = m.getMainHandItem().getItem();
        return i instanceof BowItem || i instanceof CrossbowItem || i instanceof TridentItem;
    }
    public static double getAttackSpeed(EntityMaid m) { return m.getAttributeValue(Attributes.ATTACK_SPEED); }
    public static boolean isWithinRestriction(EntityMaid m) { return m.isWithinRestriction(); }
    public static boolean onHurt(EntityMaid m) { return m.onHurt(); }
    public static String getScheduleDetail(EntityMaid m) { return m.getScheduleDetail().getName(); }

    // === Phase 4: 饰品/效果/诅咒 ===
    public static String getBaubleIds(EntityMaid m) {
        var bauble = m.getMaidBauble();
        var sj = new StringJoiner(",");
        for (int i = 0; i < bauble.getSlots(); i++) {
            var s = bauble.getStackInSlot(i);
            if (!s.isEmpty()) {
                var key = ForgeRegistries.ITEMS.getKey(s.getItem());
                if (key != null) sj.add(key.toString());
            }
        }
        return sj.toString();
    }
    public static boolean hasEffect(EntityMaid m, String effectId) {
        for (var eff : m.getActiveEffects()) {
            var key = ForgeRegistries.MOB_EFFECTS.getKey(eff.getEffect());
            if (key != null && key.toString().equals(effectId)) return true;
        }
        return false;
    }
    public static boolean hasCurse(EntityMaid m) {
        for (var s : m.getArmorSlots()) if (hasCurseOn(s)) return true;
        return hasCurseOn(m.getMainHandItem());
    }
    private static boolean hasCurseOn(ItemStack s) {
        return EnchantmentHelper.hasBindingCurse(s) || EnchantmentHelper.hasVanishingCurse(s);
    }

    // === Phase 7: TLM EntityMaid 编目补充 (8 新方法) ===
    /** 寻路到指定坐标是否可达 */
    public static boolean canPathReachPos(EntityMaid m, BlockPos pos) { return m.canPathReach(pos); }
    /** 寻路到指定实体是否可达 */
    public static boolean canPathReachEntity(EntityMaid m, LivingEntity target) { return m.canPathReach(target); }
    /** 女仆能否看到目标（考虑任务类型的视距） */
    public static boolean canSee(EntityMaid m, LivingEntity target) { return m.canSee(target); }
    /** 任务实体搜索水平半径（单位格） */
    public static float getSearchRadius(EntityMaid m) { return m.searchRadius(); }
    /** 是否可举盾 */
    public static boolean canUseShield(EntityMaid m) { return m.canUseShield(); }
    /** 是否为结构生成的女仆 */
    public static boolean isStructureSpawn(EntityMaid m) { return m.isStructureSpawn(); }
    /** 女仆能否破坏指定方块（依赖任务配置） */
    public static boolean canDestroyBlock(EntityMaid m, BlockPos pos) { return m.canDestroyBlock(pos); }
    /** 女仆能否放置方块到指定位置（依赖任务配置） */
    public static boolean canPlaceBlock(EntityMaid m, BlockPos pos) { return m.canPlaceBlock(pos); }

    // === Phase 7: 位置/方向 编目 ===
    /** 女仆脚下方块坐标 */
    public static BlockPos getBlockPos(EntityMaid m) { return m.blockPosition(); }
    /** 女仆精确坐标（眼睛高度） */
    public static Vec3 getPosition(EntityMaid m) { return m.position(); }
    /** 女仆视线方向单位向量 */
    public static Vec3 getLookAngle(EntityMaid m) { return m.getLookAngle(); }
    /** 女仆眼睛高度坐标 */
    public static Vec3 getEyePosition(EntityMaid m) { return m.getEyePosition(); }

    // === Phase 7: 进阶状态编目 ===
    /** 活动范围中心点 */
    public static BlockPos getRestrictCenter(EntityMaid m) { return m.getRestrictCenter(); }
    /** Brain 搜索起始点（有限制则返回限制中心，否则返回当前位置） */
    public static BlockPos getBrainSearchPos(EntityMaid m) { return m.getBrainSearchPos(); }
    /** 近战攻击范围平方值 */
    public static double getMeleeAttackRangeSqr(EntityMaid m, LivingEntity target) { return m.getMeleeAttackRangeSqr(target); }
    /** 死亡掉落经验值 */
    public static int getExperienceReward(EntityMaid m) { return m.getExperienceReward(); }
    /** 音调高度 */
    public static float getVoicePitch(EntityMaid m) { return m.getVoicePitch(); }
    /** 是否为有效食物 */
    public static boolean isFood(EntityMaid m, ItemStack stack) { return m.isFood(stack); }
    /** 背包展示物品 */
    public static ItemStack getBackpackShowItem(EntityMaid m) { return m.getBackpackShowItem(); }
    /** 双手动画物品数组 */
    public static ItemStack[] getHandItemsForAnimation(EntityMaid m) { return m.getHandItemsForAnimation(); }
    /** 是否可被拴绳拴住 */
    public static boolean canBeLeashed(EntityMaid m, net.minecraft.world.entity.player.Player player) { return m.canBeLeashed(player); }
    /** 女仆是否能拾取指定实体 */
    public static boolean canPickup(EntityMaid m, net.minecraft.world.entity.Entity entity) { return m.canPickup(entity); }

    // === NBT/PersistentData ===
    /** 女仆 PersistentData — 模组自定义NBT存储 */
    public static net.minecraft.nbt.CompoundTag getPersistentData(EntityMaid m) { return m.getPersistentData(); }
    /** 读 PersistentData Int */
    public static int getPersistentInt(EntityMaid m, String key, int def) { return m.getPersistentData().contains(key) ? m.getPersistentData().getInt(key) : def; }
    /** 写 PersistentData Int（无副作用：只写，不触发网络同步） */
    // 写入操作在 MaidStateWriter

    // === Phase 11: TLM API 编目 (SchedulePos, FavorabilityManager, Config) ===
    /** 工作地点 */
    public static BlockPos getWorkPos(EntityMaid m) { return m.getSchedulePos().getWorkPos(); }
    /** 休息地点 */
    public static BlockPos getIdlePos(EntityMaid m) { return m.getSchedulePos().getIdlePos(); }
    /** 睡觉地点 */
    public static BlockPos getSleepPos(EntityMaid m) { return m.getSchedulePos().getSleepPos(); }
    /** 日程是否已配置 */
    public static boolean isScheduleConfigured(EntityMaid m) { return m.getSchedulePos().isConfigured(); }
    /** 最近的日程位置 */
    public static BlockPos getNearestSchedulePos(EntityMaid m) { return m.getSchedulePos().getNearestPos(m); }
    /** 好感度等级 */
    public static int getFavorLevel(EntityMaid m) { return m.getFavorabilityManager().getLevel(); }
    /** 好感度等级百分比 (0.0~1.0) */
    public static double getFavorLevelPercent(EntityMaid m) { return m.getFavorabilityManager().getLevelPercent(); }
    /** 下一级所需点数 */
    public static int getNextLevelPoint(EntityMaid m) { return m.getFavorabilityManager().nextLevelPoint(); }
    /** 背包是否可见 */
    public static boolean isShowBackpack(EntityMaid m) { return m.getConfigManager().isShowBackpack(); }
    /** 聊天气泡是否可见 */
    public static boolean isChatBubbleShow(EntityMaid m) { return m.getConfigManager().isChatBubbleShow(); }

    // === v34: ConfigManager 补全 ===
    public static boolean isShowBackItem(EntityMaid m) { return m.getConfigManager().isShowBackItem(); }
    public static float getSoundFreq(EntityMaid m) { return m.getConfigManager().getSoundFreq(); }
    public static String getPickupType(EntityMaid m) { return m.getConfigManager().getPickupType().name().toLowerCase(); }
    public static boolean isOpenDoor(EntityMaid m) { return m.getConfigManager().isOpenDoor(); }
    public static boolean isOpenFenceGate(EntityMaid m) { return m.getConfigManager().isOpenFenceGate(); }
    public static boolean isActiveClimbing(EntityMaid m) { return m.getConfigManager().isActiveClimbing(); }

    // === v34: AI Task 标志 ===
    public static boolean isEnablePanic(EntityMaid m) { return m.getTask().enablePanic(m); }
    public static boolean isEnableEating(EntityMaid m) { return m.getTask().enableEating(m); }
    public static boolean isEnableLookAndRandomWalk(EntityMaid m) { return m.getTask().enableLookAndRandomWalk(m); }

    // === v34: 导航补全 ===
    public static String getSearchDimension(EntityMaid m) { return m.searchDimension().toString(); }

    // === v34: Owner 补全 ===
    public static java.util.UUID getOwnerUUID(EntityMaid m) { return m.getOwnerUUID(); }
    public static String getOwnerName(EntityMaid m) {
        var o = m.getOwner(); return o != null ? o.getScoreboardName() : "";
    }
    public static net.minecraft.world.item.ItemStack getTamedItem(EntityMaid m) { return new net.minecraft.world.item.ItemStack(m.getTamedItem().getItems()[0].getItem()); }
    public static net.minecraft.world.item.ItemStack getTemptationItem(EntityMaid m) { return new net.minecraft.world.item.ItemStack(m.getTemptationItem().getItems()[0].getItem()); }

    // === v34: TLM 自定义属性 ===
    public static double getUseItemSpeed(EntityMaid m) { return m.getAttributeValue(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_USE_ITEM_SPEED.get()); }
    public static double getCrossbowAttackSpeed(EntityMaid m) { return m.getAttributeValue(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_CROSSBOW_ATTACK_SPEED.get()); }
    public static double getGunAttackSpeed(EntityMaid m) { return m.getAttributeValue(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_GUN_ATTACK_SPEED.get()); }
    public static double getShootCooldown(EntityMaid m) { return m.getAttributeValue(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_SHOOT_COOLDOWN.get()); }
    public static double getTridentCooldown(EntityMaid m) { return m.getAttributeValue(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_TRIDENT_COOLDOWN.get()); }
    public static double getPickupRange(EntityMaid m) { return m.getAttributeValue(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_PICKUP_RANGE.get()); }
    public static double getPassiveUseShieldTick(EntityMaid m) { return m.getAttributeValue(com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_PASSIVE_USE_SHIELD_TICK.get()); }

    // === v34: NBT/PersistentData 读 ===
    public static float getPersistentFloat(EntityMaid m, String key, float def) {
        var d = m.getPersistentData(); return d.contains(key) ? d.getFloat(key) : def;
    }
    public static long getPersistentLong(EntityMaid m, String key, long def) {
        var d = m.getPersistentData(); return d.contains(key) ? d.getLong(key) : def;
    }
    public static String getPersistentString(EntityMaid m, String key, String def) {
        var d = m.getPersistentData(); return d.contains(key) ? d.getString(key) : def;
    }
    public static boolean getPersistentBoolean(EntityMaid m, String key, boolean def) {
        var d = m.getPersistentData(); return d.contains(key) ? d.getBoolean(key) : def;
    }

    // === v34: 统计 ===
    public static boolean hasKillRecord(EntityMaid m) { return m.getKillRecordManager() != null; }
    public static boolean hasGameRecord(EntityMaid m) { return m.getGameRecordManager() != null; }
}
