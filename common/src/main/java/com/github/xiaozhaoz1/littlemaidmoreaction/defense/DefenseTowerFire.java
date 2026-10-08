package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuShoot;
import com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 防御塔**火控子系统** (v79.63 自 {@link DefenseGarageKitBlockEntity} 抽出, 该类因而 595 → 约 400 行)。
 *
 * <p><b>职责</b>: 视线判定 · 武器分流 (弓/弩 → 箭, 御币 → 弹幕) · 弹药查找与消耗 ·
 * 箭/弹幕实体生成 · 主人解析 · 武器耐久扣除。**不含** tick 节律/相位/索敌/目标选择 (仍在 BE 与
 * {@link DefenseTowerLogic}) — 那些是"要不要打、打谁", 本类是"怎么打出去"。
 *
 * <p><b>无状态</b>: 所有状态 (弹药容器/伤害/主人) 由调用方传入, 本类不持有字段 → 便于单测与复用
 * (未来若加"女仆操作的塔"或"炮台"可共用)。
 *
 * <p><b>弹道</b>: 瞄准与下坠补偿在 {@link DefenseTowerLogic#aimAt} (纯函数, 已单测);
 * 本类只负责把算出的方向喂给投射物。
 */
public final class DefenseTowerFire {

    /** 火控诊断开关 (排查用, 默认关): `-Dlma.towerDiag=true` (与 BE 同开关) */
    private static final boolean FIRE_DIAG = Boolean.getBoolean("lma.towerDiag") || System.getenv("LMA_TOWER_DIAG") != null;

    private DefenseTowerFire() {}

    /** 武器槽 (槽0) 自动判定攻击方式: 弓/弩→ARROW, 御币→DANMAKU, 其他/空→NONE.
     * ⚠ 御币 extends ProjectileWeaponItem (双平台实证) — 必须先判 isGohei, 否则御币被误判 ARROW */
    public static DefenseTowerLogic.ShootMode weaponMode(net.minecraft.world.SimpleContainer ammo) {
        ItemStack w = ammo.getItem(0);
        if (w.isEmpty()) return DefenseTowerLogic.ShootMode.NONE;
        if (ItemHakureiGohei.isGohei(w)) return DefenseTowerLogic.ShootMode.DANMAKU;
        if (w.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) return DefenseTowerLogic.ShootMode.ARROW;
        return DefenseTowerLogic.ShootMode.NONE;
    }

    /** 塔是否还能开火 (对应攻击方式有弹药) */
    public static boolean hasAmmoFor(net.minecraft.world.SimpleContainer ammo, DefenseTowerLogic.ShootMode mode) {
        return switch (mode) {
            case ARROW -> findArrow(ammo) >= 0;
            case DANMAKU -> findGohei(ammo) >= 0;
            case NONE -> false;
        };
    }

    /**
     * 开火 (调用方已过 owner 在线 + 相位 + 冷却 + 索敌/LOS 闸门): 按武器自动分流。
     *
     * @return true = 真的射出 (调用方据此更新 lastFireTick + setChanged)
     */
    public static boolean fire(ServerLevel level, BlockPos towerPos, Vec3 origin, LivingEntity target,
                               net.minecraft.world.SimpleContainer ammo, double damageOverride, UUID ownerUuid) {
        return switch (weaponMode(ammo)) {
            case ARROW -> fireArrow(level, towerPos, origin, target, ammo, damageOverride, ownerUuid);   // v79.63.11: 传覆盖值 (原来箭塔忽略配置 ✗)
            case DANMAKU -> fireDanmaku(level, origin, target, ammo, damageOverride, ownerUuid);
            case NONE -> false;
        };
    }

/** 视线判定 (原版 LivingEntity.hasLineOfSight 同款 clip: 起点已抬高到塔顶上方, 下 1 格不自挡) */
    static boolean hasLineOfSight(ServerLevel level, Vec3 from, LivingEntity target) {
        Vec3 to = target.getEyePosition();
//? if 1.20.1 {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(
                from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, null));
//?} else {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(
                from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
//?}
        boolean miss = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
        // 视线诊断 (排查「候选有却 no LOS ⇒ 不开火」; **上限 40 条**防刷屏 — 见 lessons #340 日志体积纪律)
        if (FIRE_DIAG && !miss && LOS_DIAG_COUNT.getAndIncrement() < 40) {
            String hitBlock = hit.getBlockPos() == null ? "-"
                    : level.getBlockState(hit.getBlockPos()).getBlock().toString();
            LittleMaidMoreAction.LOGGER.warn("[TOWER-LOS] BLOCKED from=({},{},{}) to=({},{},{}) target={} hit={} block={} state={} at=({},{},{})",
                    f2(from.x), f2(from.y), f2(from.z), f2(to.x), f2(to.y), f2(to.z),
                    target.getType().toShortString(), hit.getType(),
                    hit.getBlockPos() == null ? "-" : hit.getBlockPos().toShortString(), hitBlock,
                    f2(hit.getLocation().x), f2(hit.getLocation().y), f2(hit.getLocation().z));
        }
        return miss;
    }

    /** 诊断用格式化 (2 位小数) */
    private static String f2(double v) { return String.format("%.2f", v); }

    /** 视线诊断计数 (上限 40 条 ⇒ 不刷屏) */
    private static final java.util.concurrent.atomic.AtomicInteger LOS_DIAG_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();
    /** 耐久诊断计数 (上限 40 条) */
    private static final java.util.concurrent.atomic.AtomicInteger DMG_DIAG_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();

/**
     * 箭模式: 消耗 1 支箭 + 需要弓. 伤害 = 弓的力量附魔 + 箭类型 (用户裁定: 不自定义).
     * 创建走 {@link net.minecraft.world.item.ArrowItem#createArrow} 多态 — 药水箭/光灵箭/自定义箭
     * 各自生成正确实体与效果 (对齐原版 ProjectileWeaponItem.createProjectile; 之前直接 new Arrow
     * 导致 1.20.1 药水效果丢失 / 光灵箭双平台失效).
     * - 1.21.1: 传弓 (firedFromWeapon) → EnchantmentHelper.modifyDamage 自动算附魔
     * - 1.20.1: 手动算 (BowItem 公式: baseDamage + powerLevel*0.5 + 0.5)
     */
    private static boolean fireArrow(ServerLevel level, BlockPos towerPos, Vec3 origin, LivingEntity target,
                                      net.minecraft.world.SimpleContainer ammo, double damageOverride, UUID ownerUuid) {
        int bowSlot = findBow(ammo);
        int arrowSlot = findArrow(ammo);
        if (FIRE_DIAG && (bowSlot < 0 || arrowSlot < 0 || level.getGameTime() % 100 == 17)) {   // ⚠ 必须整体括起: 否则 || 优先级会让门控失效
            LittleMaidMoreAction.LOGGER.warn("[TOWER-DIAG] fireArrow pos={} bowSlot={} arrowSlot={} s0={} s1={}",
                    towerPos, bowSlot, arrowSlot, ammo.getItem(0).getItem(), ammo.getItem(1).getItem());
        }
        if (bowSlot < 0 || arrowSlot < 0) return false;
        ItemStack bow = ammo.getItem(bowSlot);
        ItemStack arrowStack = ammo.getItem(arrowSlot);
        // createArrow 需要 shooter (owner) — 在线门控已保证 owner 存在
        ServerPlayer owner = resolveOwner(level, ownerUuid);
        if (owner == null) return false;
        // 箭物品 (非 ArrowItem 回退普通箭 — 对齐原版)
        net.minecraft.world.item.ArrowItem arrowItem =
                arrowStack.getItem() instanceof net.minecraft.world.item.ArrowItem ai ? ai
                        : (net.minecraft.world.item.ArrowItem) net.minecraft.world.item.Items.ARROW;
        // 多态创建 (药水箭 POTION_CONTENTS / 光灵箭 SpectralArrow 由物品自己处理)
        net.minecraft.world.entity.projectile.AbstractArrow arrow;
//? if 1.20.1 {
        arrow = arrowItem.createArrow(level, arrowStack, owner);
        arrow.setPos(origin.x, origin.y, origin.z);      // createArrow 生成在射手眼睛 → 移回塔口
        // 1.20.1 BowItem 公式: baseDamage + 力量等级*0.5 + 0.5 (字节码实证)
        double damage = 2.0;
        int power = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.POWER_ARROWS, bow);
        if (power > 0) damage = 2.0 + power * 0.5 + 0.5;
        // ★ v79.63.11 (用户实测「防御塔伤害为什么不对」): **覆盖值/全局配置对箭塔也生效** ✓
        //   原来箭塔只用写死公式 (2.0 + 弓威力) ⇒ 设置里改伤害对箭塔**完全无效** ✗ (只有弹幕塔读配置 ✗)。
        //   现口径统一: damageOverride(单塔) 优先 > 全局 DAMAGE > 原版公式 ✓
        //   ※ 箭的**类型**(普通/光灵/药水/mod箭) 仍由容器决定 ✓ (createArrow 多态: 实体+效果 ✓);
        //     原版机制里箭种本身不改基础伤害 (伤害来自弓的蓄力/力量附魔 ✓) — 这点与本行不冲突 ✓
        // v79.64.4: 决策抽到 DefenseTowerLogic.effectiveArrowDamage (双平台共享 + 单测锁定, 防再次漏配 ✓);
        // vanilla = 上式 2.0 + 力量 的公式 (全局默认 2.0 > 0 ⇒ 实际总是走到"全局/覆盖"分支, 行为不变 ✓)
        damage = DefenseTowerLogic.effectiveArrowDamage(damageOverride,
                com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.DAMAGE.get(), damage);
        arrow.setBaseDamage(damage);
//?} else {
        // 1.21.1: 传弓 → createArrow 内部 ammo.copyWithCount(1) (单支拾取) + 命中时 modifyDamage 算附魔
        arrow = arrowItem.createArrow(level, arrowStack, owner, bow);
        arrow.setPos(origin.x, origin.y, origin.z);
        // ★ v79.64.4 补齐 (用户裁定「补齐」): 1.20.1 分支有「单塔覆盖 > 全局默认」的伤害口径, 而 1.21.1
        //   分支**整块缺失** ⇒ neo 上箭塔**完全忽略配置** (单塔 GUI 覆盖与全局默认都无效 — v79.63.11
        //   那次修只进了 forge 分支) ✗ 现补齐同口径: 生效值 = 单塔覆盖 (非 NaN 且 > 0) > 全局 DAMAGE ✓
        //   ※ 全局默认 2.0 = 原版箭基础伤害 ⇒ **未改配置者行为不变** ✓ (仅"改过配置"才生效)
        //   ※ 差异备案: 1.20.1 把力量附魔写进公式 (有覆盖时力量不再叠加); 1.21.1 由原版在**命中时**
        //     {@code EnchantmentHelper.modifyDamage} 叠加 ⇒ neo 上力量仍会追加 (更接近原版) ✓
        double arrowDamage = DefenseTowerLogic.effectiveArrowDamage(damageOverride,
                com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.DAMAGE.get(), 2.0);
        arrow.setBaseDamage(arrowDamage);
//?}
        // 瞄准头部 (getEyePosition — 用户裁定) + 弹道下坠补偿
        // 初速 3.0 = 原版弓满蓄; 飞行时间按直线 3D 距离估 (向下/向上射也准)
        // 弹道纯函数 (v79.63 抽到 DefenseTowerLogic — 可单测): 瞄眼位 + 下坠补偿
        Vec3 dir = DefenseTowerLogic.aimAt(origin, target.getEyePosition());
        arrow.shoot(dir.x, dir.y, dir.z, (float) DefenseTowerLogic.ARROW_SPEED, 0.0f);
        arrow.setOwner(owner);
        level.addFreshEntity(arrow);
        ammo.removeItem(arrowSlot, 1);
        // 武器扣耐久 (原版 ProjectileWeaponItem.shoot 每发 hurtAndBreak(1); owner null 时跳过 —
        // 主人在线门控已保证 owner 非 null, 但防极端情况)
        if (owner != null) {
//? if 1.20.1 {
            bow.hurtAndBreak(1, owner, (e) -> {});
//?} else {
            bow.hurtAndBreak(1, owner, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
//?}
            if (bow.isEmpty()) {
                ammo.setItem(bowSlot, ItemStack.EMPTY);
            }
        }
        // 耐久诊断 (排查「射了却不掉耐久」; 上限 40 条)
        if (FIRE_DIAG && DMG_DIAG_COUNT.getAndIncrement() < 40) {
//? if 1.20.1 {
            boolean infinite = owner != null && owner.getAbilities().instabuild;
            LittleMaidMoreAction.LOGGER.warn("[TOWER-DMG] fired={} slot={} item={} dmg={}/{} ownerNull={} instabuild={}",
                    towerPos, bowSlot, bow.getItem(), bow.getDamageValue(), bow.getMaxDamage(), owner == null, infinite);
//?} else {
            boolean infinite = owner != null && owner.hasInfiniteMaterials();
            LittleMaidMoreAction.LOGGER.warn("[TOWER-DMG] fired={} slot={} item={} dmg={}/{} ownerNull={} infinite={} instabuild={}",
                    towerPos, bowSlot, bow.getItem(), bow.getDamageValue(), bow.getMaxDamage(), owner == null, infinite,
                    owner == null ? "n/a" : owner.getAbilities().instabuild);
//?}
        }
        return true;
    }

/** 武器槽找投射武器 (槽0 — 弓/弩 ProjectileWeaponItem) */
    static int findBow(net.minecraft.world.SimpleContainer ammo) {
        ItemStack s = ammo.getItem(0);
        return (!s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) ? 0 : -1;
    }

/** 弹幕模式: 御币 hurtAndBreak(2) (双倍消耗, 耐久 1200 → 600 发); 走 TLM DanmakuShoot */
    private static boolean fireDanmaku(ServerLevel level, Vec3 origin, LivingEntity target,
                                        net.minecraft.world.SimpleContainer ammo, double damage, UUID ownerUuid) {
        int slot = findGohei(ammo);
        if (slot < 0) return false;
        ItemStack gohei = ammo.getItem(slot);
        if (!ItemHakureiGohei.isGohei(gohei)) return false;
        ServerPlayer owner = resolveOwner(level, ownerUuid);
        if (owner == null) return false;   // 裁定 ⑩ 前置已在线; 二次防 null thrower
        // 弹幕 thrower = 塔主人 (伤害归属 + InitDamage.danmakuDamage 需要 thrower.level)
        DanmakuShoot.create()
                .setWorld(level)
                .setThrower(owner)
                .setTarget(target)
                .setRandomColor()
                .setRandomType()
                .setDamage((float) damage)
                .setGravity(0.01f)
                .setVelocity(0.6f)
                .setInaccuracy(0.2f)
                .aimedShot();
        // 双倍消耗: hurtAndBreak(2) — 双平台签名差异 (#253 教训)
        int broken = 2;
//? if 1.20.1 {
        gohei.hurtAndBreak(broken, owner, (e) -> {});
//?} else {
        gohei.hurtAndBreak(broken, owner, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
//?}
        if (gohei.isEmpty()) {
            ammo.setItem(slot, ItemStack.EMPTY);
        }
        return true;
    }

/** 主人在线解析 (tick 前置已判, 此处保底) */
    /** 主人在线解析 (tick 前置已判, 此处保底) */
    static ServerPlayer resolveOwner(ServerLevel level, UUID ownerUuid) {
        if (ownerUuid == null || level == null) return null;
        return level.getServer().getPlayerList().getPlayer(ownerUuid);
    }

/** 弹药槽找箭 (槽1-9 — 任意 ArrowItem: 普通箭/药水箭/光灵箭等) */
    static int findArrow(net.minecraft.world.SimpleContainer ammo) {
        for (int i = 1; i < ammo.getContainerSize(); i++) {
            ItemStack s = ammo.getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.ArrowItem) return i;
        }
        return -1;
    }

/** 武器槽找御币 (槽0) */
    static int findGohei(net.minecraft.world.SimpleContainer ammo) {
        return ItemHakureiGohei.isGohei(ammo.getItem(0)) ? 0 : -1;
    }}
