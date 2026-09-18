package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuShoot;
import com.github.tartaricacid.touhoulittlemaid.init.InitDamage;
import com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.EntityScanCache;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 防御塔方块实体 (v79.62.3) — 继承 TLM {@link TileEntityGarageKit} 以完整复用
 * 原版 garage_kit 的 NBT 结构 (GarageKitFacing/ExtraData — 女仆模型渲染与附属扩展兼容),
 * 塔自有字段走独立 NBT 键 (ammo ownerUuid radius shootMode damageOverride fireInterval)。
 *
 * <p>tick (每 {@link #TICK_INTERVAL} 20t 一轮, 全局节奏):
 * ① 主人不在线不攻击 (裁定 ⑩) → return;
 * ② {@link EntityScanCache#scanAround} 区块缓存索敌 (半径 = 每塔覆盖或 Cloth 默认, 零额外扫描);
 * ③ {@link DefenseTowerLogic#nearestHostile} 最近怪物 + 视线判定;
 * ④ 按模式射击: 箭 (AbstractArrow spawn, 耗 1 箭) / 弹幕 (TLM DanmakuShoot, 御币 hurtAndBreak(2));
 * ⑤ 弹药耗尽静默跳过 (下轮重试), 状态变化才 setChanged。
 *
 * <p>tick 挂载: {@link DefenseTowerTickHandler} 服务端 tick 事件全局循环 (BlockEntity 自身
 * EntityTick 需方块实现 EntityBlock 的 setBlockEntity; 塔方块已挂, 见 DefenseGarageKitBlock)。
 */
public class DefenseGarageKitBlockEntity extends TileEntityGarageKit {

    /** 全局 tick 节奏 (裁定: 20t 索敌) */
    public static final int TICK_INTERVAL = 20;

    /** 塔自有 NBT 键 (独立于 TLM ExtraData) */
    private static final String KEY_AMMO = "DefenseTowerAmmo";
    private static final String KEY_OWNER = "DefenseTowerOwner";
    private static final String KEY_RADIUS = "DefenseTowerRadius";
    private static final String KEY_MODE = "DefenseTowerMode";
    private static final String KEY_DAMAGE = "DefenseTowerDamage";
    private static final String KEY_INTERVAL = "DefenseTowerInterval";
    private static final String KEY_LAST_FIRE = "DefenseTowerLastFire";

    /** 塔容器: 武器槽(1) + 弹药槽(9) — 槽0=武器(弓/御币), 槽1-9=弹药(箭) */
    private final net.minecraft.world.SimpleContainer ammo = new net.minecraft.world.SimpleContainer(1 + 9);

    /**
     * 漏斗用物品栏 handler (槽位过滤):
     * 槽0 = 只收弓/弩/御币 (ProjectileWeaponItem 且非御币, 或御币);
     * 槽1-9 = 只收 ArrowItem (普通箭/药水箭/光灵箭/mod箭).
     * 其它物品 (如泥土) insert 返回原样 → 漏斗放不进.
     */
//? if 1.20.1 {
    public static final class DefenseTowerItemHandler extends net.minecraftforge.items.wrapper.InvWrapper {
        public DefenseTowerItemHandler(DefenseGarageKitBlockEntity tower) { super(tower.ammo); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return DefenseGarageKitBlockEntity.isValidSlotItem(slot, stack);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;
            return super.insertItem(slot, stack, simulate);
        }
    }
//?} else {
    public static final class DefenseTowerItemHandler extends net.neoforged.neoforge.items.wrapper.InvWrapper {
        public DefenseTowerItemHandler(DefenseGarageKitBlockEntity tower) { super(tower.ammo); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return DefenseGarageKitBlockEntity.isValidSlotItem(slot, stack);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;
            return super.insertItem(slot, stack, simulate);
        }
    }
//?}

    /** 槽位物品过滤 (漏斗/GUI 共用语义): 槽0=武器, 槽1-9=ArrowItem */
    static boolean isValidSlotItem(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;   // 空栈允许 (槽位清空)
        if (slot == 0) {
            // 武器: 御币 (extends ProjectileWeaponItem, 必须先判) 或 弓/弩 (ProjectileWeaponItem)
            if (com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei.isGohei(stack)) return true;
            return stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem;
        }
        // 弹药槽 1-9: 任意 ArrowItem
        return stack.getItem() instanceof net.minecraft.world.item.ArrowItem;
    }

    /** 漏斗用 handler (惰性创建) */
    private DefenseTowerItemHandler itemHandler = null;

    public DefenseTowerItemHandler getItemHandler() {
        if (itemHandler == null) itemHandler = new DefenseTowerItemHandler(this);
        return itemHandler;
    }

    /** 1.20.1: forge capability 提供 (漏斗/机械臂读取) */
//? if 1.20.1 {
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap,
            @org.jetbrains.annotations.Nullable net.minecraft.core.Direction side) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
            return net.minecraftforge.common.util.LazyOptional.of(this::getItemHandler).cast();
        }
        return super.getCapability(cap, side);
    }
//?}

    /** 塔主人 UUID (放置时写入; 空 = 无主, 不攻击且 GUI 拒绝非主人) */
    private UUID ownerUuid = null;
    /** 每塔半径覆盖 (-1 = 未覆盖, 读 Cloth 全局默认) */
    private int radiusOverride = -1;
    /** 每塔伤害覆盖 (NaN = 未覆盖, 读 Cloth 全局默认) */
    private double damageOverride = Double.NaN;
    /** 每塔射击节奏覆盖 (-1 = 未覆盖, 读 Cloth 全局默认) */
    private int intervalOverride = -1;
    /** 射击模式 (默认箭) */
    private DefenseTowerLogic.ShootMode shootMode = DefenseTowerLogic.ShootMode.ARROW;
    /** 上次开火 gameTime (射击冷却) */
    private long lastFireTick = 0;
    /** 上次扫描 gameTime (索敌节流 20t = 1s — 怪 1s 内必被扫到) */
    private long lastScanTick = 0;

    public DefenseGarageKitBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /**
     * 覆写 getType 返回 LMA 自己的 BlockEntityType — TLM 父类 TYPE 写死绑定
     * touhou_little_maid:garage_kit 方块 (neoforge isValid 校验会拒绝我们的方块, 1.20.1 不校验)。
     * 渲染器/NBT 复用仍通过父类字段与 getExtraData()/getFacing() 生效。
     */
    @Override
    public net.minecraft.world.level.block.entity.BlockEntityType<?> getType() {
        return com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlockEntityTypes.GARAGE_KIT_DEFENSE.get();
    }

    // ── 存取器 (GUI/包/方块共用) ──

    public net.minecraft.world.SimpleContainer getAmmo() { return ammo; }

    public UUID getOwnerUuid() { return ownerUuid; }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        setChanged();
    }

    public int getRadius() {
        return radiusOverride > 0 ? radiusOverride : DefenseTowerConfig.RADIUS.get();
    }

    public void setRadius(int radius) {
        this.radiusOverride = DefenseTowerLogic.clampRadius(radius);
        setChanged();
    }

    public double getDamage() {
        return Double.isNaN(damageOverride) ? DefenseTowerConfig.DAMAGE.get() : damageOverride;
    }

    public void setDamageOverride(double damage) {
        this.damageOverride = DefenseTowerLogic.clampDamage(damage);
        setChanged();
    }

    public int getFireInterval() {
        return intervalOverride > 0 ? intervalOverride : DefenseTowerConfig.FIRE_INTERVAL.get();
    }

    public DefenseTowerLogic.ShootMode getShootMode() { return shootMode; }

    public void setShootMode(DefenseTowerLogic.ShootMode mode) {
        this.shootMode = mode;
        setChanged();
    }

    /** 主人是否在线 (裁定 ⑩: 不在线不攻击) */
    public boolean isOwnerOnline() {
        if (ownerUuid == null || !(level instanceof ServerLevel sl)) return false;
        return sl.getServer().getPlayerList().getPlayer(ownerUuid) != null;
    }

    // ── NBT 闭环 ──

//? if 1.20.1 {
    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains(KEY_AMMO)) ammo.fromTag(nbt.getList(KEY_AMMO, 10));
        readTowerData(nbt, null);
    }

    @Override
    public void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put(KEY_AMMO, ammo.createTag());
        writeTowerData(nbt, null);
    }
//?} else {
    @Override
    public void loadAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains(KEY_AMMO)) ammo.fromTag(nbt.getList(KEY_AMMO, 10), registries);
        readTowerData(nbt, registries);
    }

    @Override
    public void saveAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.put(KEY_AMMO, ammo.createTag(registries));
        writeTowerData(nbt, registries);
    }
//?}

    private void readTowerData(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
        if (nbt.contains(KEY_OWNER)) ownerUuid = nbt.getUUID(KEY_OWNER);
        radiusOverride = nbt.contains(KEY_RADIUS) ? nbt.getInt(KEY_RADIUS) : -1;
        shootMode = DefenseTowerLogic.ShootMode.byId(nbt.getInt(KEY_MODE));
        damageOverride = nbt.contains(KEY_DAMAGE) ? nbt.getDouble(KEY_DAMAGE) : Double.NaN;
        intervalOverride = nbt.contains(KEY_INTERVAL) ? nbt.getInt(KEY_INTERVAL) : -1;
        lastFireTick = nbt.getLong(KEY_LAST_FIRE);
    }

    private void writeTowerData(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
        if (ownerUuid != null) nbt.putUUID(KEY_OWNER, ownerUuid);
        if (radiusOverride > 0) nbt.putInt(KEY_RADIUS, radiusOverride);
        nbt.putInt(KEY_MODE, shootMode.id());
        if (!Double.isNaN(damageOverride)) nbt.putDouble(KEY_DAMAGE, damageOverride);
        if (intervalOverride > 0) nbt.putInt(KEY_INTERVAL, intervalOverride);
        nbt.putLong(KEY_LAST_FIRE, lastFireTick);
    }

    // ── 潜影盒式落塔: 塔数据 ↔ 物品 NBT (onRemove 写 / setPlacedBy 读) ──

    /** 序列化塔全部内容 (弹药 + 主人 + 设置) 到掉落物物品 NBT — 打掉塔不丢东西 */
    public net.minecraft.nbt.CompoundTag saveTowerToItem(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
//? if 1.20.1 {
        nbt.put(KEY_AMMO, ammo.createTag());
        writeTowerData(nbt, null);
//?} else {
        nbt.put(KEY_AMMO, ammo.createTag(registries));
        writeTowerData(nbt, registries);
//?}
        // 手办数据 (extraData) 一并保存 — 掉落物即完整塔
        if (getExtraData() != null && !getExtraData().isEmpty()) {
            nbt.put(EXTRA_DATA_KEY, getExtraData().copy());
        }
        return nbt;
    }

    /** 从掉落物物品 NBT 恢复塔全部内容 (setPlacedBy 调) */
    public void loadTowerFromItem(net.minecraft.nbt.CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
        if (nbt == null) return;
//? if 1.20.1 {
        if (nbt.contains(KEY_AMMO)) ammo.fromTag(nbt.getList(KEY_AMMO, 10));
        readTowerData(nbt, null);
//?} else {
        if (nbt.contains(KEY_AMMO)) ammo.fromTag(nbt.getList(KEY_AMMO, 10), registries);
        readTowerData(nbt, registries);
//?}
        if (nbt.contains(EXTRA_DATA_KEY)) {
            setData(net.minecraft.core.Direction.SOUTH, nbt.getCompound(EXTRA_DATA_KEY));
        }
        setChanged();
    }

    /** 物品 NBT 中塔数据键 (潜影盒式) */
    public static final String EXTRA_DATA_KEY = "DefenseTowerExtraData";

    // ── 服务端 tick (由 DefenseTowerTickHandler 驱动) ──

    /** 每 tick 被 handler 调 (服务端) — 主人不在线直接返回 (不索敌不射击) */
    /**
     * 塔诊断开关 (排查用, 默认关) — 启动加 `-Dlma.towerDiag=true` 打开。
     *
     * <p>v79.63 用它定位了 `lmaDefenseTowerDurability` 偶发: 塔 tick 正常 (scan 在推进) 但
     * 断言窗口 260t 太短 — 首射实测发生在 ~310t ⇒ 窗口延长到 520t (错题 #296)。
     */
    private static final boolean TOWER_DIAG = Boolean.getBoolean("lma.towerDiag") || System.getenv("LMA_TOWER_DIAG") != null;   // 环境变量: Gradle 派生的游戏 JVM 会继承 (-D 不会)

    public void towerTick() {
        if (level == null || level.isClientSide || !(level instanceof ServerLevel sl)) return;
        if (!isOwnerOnline()) return;
        long now = sl.getGameTime();
        // TEMP-DIAG durability 排查
        if (TOWER_DIAG && now % 200 == 17) {
            LittleMaidMoreAction.LOGGER.warn("[TOWER-DIAG] pos={} ownerOnline={} scan={} fire={} mode={} slot0={} weapon={}",
                    worldPosition, isOwnerOnline(), lastScanTick, lastFireTick, shootMode,
                    ammo.getItem(0).getItem(), weaponMode());
        }
        // 相位抖动闸门 (v79.63, 错题 #277): 各塔按自身坐标错开活动 tick, 避免多塔同 tick 齐射
        // → 箭同 tick 到达 → 被原版受伤吸收窗口吞掉 (LivingEntity:1101-1111 源码实证)。
        // ⚠ 必须放在扫描闸门**之前**: 射击判定在扫描闸门之内, 若只抖动射击冷却, 会被扫描闸门
        //   重新量化回同一 tick (抖动被吞掉); 同时保留"扫描与射击在同一 tick"的既有语义。
        int phase = DefenseTowerLogic.phaseOf(
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), TICK_INTERVAL);
        if (!DefenseTowerLogic.isPhaseTick(now, phase, TICK_INTERVAL)) return;
        // 扫描节流 20t (1s) — 怪进范围 1s 内必被扫到; 射击冷却独立
        if (!DefenseTowerLogic.isReady(now, lastScanTick, TICK_INTERVAL)) return;
        lastScanTick = now;
        int interval = getFireInterval();
        if (TOWER_DIAG) {   // 决策点日志 (#296): 每次扫描必打 — 此前该闸门无任何日志
            LittleMaidMoreAction.LOGGER.warn("[TOWER-DIAG] scan pos={} now={} interval={} lastFire={} ready={} radius={}",
                    worldPosition, now, interval, lastFireTick,
                    DefenseTowerLogic.isReady(now, lastFireTick, interval), getRadius());
        }
        if (!DefenseTowerLogic.isReady(now, lastFireTick, interval)) return;

        int radius = getRadius();
        // 直扫 (不用 EntityScanCache — 其垂直窗 ±4 是女仆工作范围设计, 塔放高处扫不到地面怪)
        // AABB = 水平半径 + 全垂直高度 (塔高/低都能扫到, 3D 距离在 nearestHostile 判定)
        int minY = sl.getMinBuildHeight(), maxY = sl.getMaxBuildHeight();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                worldPosition.getX() + 0.5 - radius, minY, worldPosition.getZ() + 0.5 - radius,
                worldPosition.getX() + 0.5 + radius, maxY, worldPosition.getZ() + 0.5 + radius);
        List<LivingEntity> candidates = sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                box, DefenseTowerLogic::isHostileTarget);
        if (candidates.isEmpty()) {
            // TEMP-DIAG durability
            if (TOWER_DIAG) {   // #296: 去掉 %200 门控 — 塔只在相位 tick 决策, 与之常不相交
                long zc = sl.getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class,
                        box).size();
                LittleMaidMoreAction.LOGGER.warn("[TOWER-DIAG] no candidates pos={} zombiesInBox={} radius={} boxY=[{}..{}]",
                        worldPosition, zc, radius, minY, maxY);
            }
            return;
        }

        // 发射/视线起点: 跟随手办模型高度 (2026-09-03 用户裁定).
        // 视觉高度 = 实体高度 × 0.5 (渲染 scale); 发射点 = 方块顶(y+1) + 视觉高度×0.7 (胸口/头).
        // 1 格小手办≈头顶上方, 2/3 格高手办也跟模型走 (不再从腰部射); 默认女仆高 1.95.
        float modelH = 1.95f;
        net.minecraft.nbt.CompoundTag ed = getExtraData();
        if (!ed.isEmpty()) {
            try {
                modelH = net.minecraft.world.entity.EntityType.byString(ed.getString("id"))
                        .map(t -> t.getHeight()).orElse(1.95f);
            } catch (Exception ignore) { /* 未知 id 保持默认 */ }
        }
        double muzzleOffset = 1.0 + modelH * 0.5 * 0.7;
        Vec3 origin = new Vec3(worldPosition.getX() + 0.5,
                worldPosition.getY() + muzzleOffset, worldPosition.getZ() + 0.5);
        double radiusSq = (double) radius * radius;
        // v79.62.3 修复: 选「LOS 可见」的最近目标 — 之前 nearestHostile 只按距离选最近,
        // 若最近的怪被墙挡 (LOS fail) 整个 tick 放弃 → 远处可见的怪不打 (用户实测: 墙外史莱姆
        // 挡住 16 格外僵尸; 2026-09-04). 遍历候选, 先筛 LOS 再选水平最近.
        LivingEntity target = null;
        double bestSq = Double.MAX_VALUE;
        for (LivingEntity cand : candidates) {
            if (!DefenseTowerLogic.isHostileTarget(cand)) continue;
            double cdx = cand.getX() - origin.x;
            double cdz = cand.getZ() - origin.z;
            double cSq = cdx * cdx + cdz * cdz;
            if (cSq > radiusSq || cSq >= bestSq) continue;
            if (!DefenseTowerFire.hasLineOfSight(sl, origin, cand)) continue;
            bestSq = cSq;
            target = cand;
        }
        if (target == null) {
            if (TOWER_DIAG) {   // #296: 去掉 %100 门控
                LittleMaidMoreAction.LOGGER.warn("[TOWER-DIAG] no LOS target pos={} candidates={}",
                        worldPosition, candidates.size());
            }
            return;
        }

        // 按武器槽自动决定攻击方式 (用户裁定: 弓=射箭, 御币=弹幕, 无需模式)
        // 火控子系统 (v79.63 抽到 DefenseTowerFire): 视线已筛, 这里只负责"打出去"
        boolean fired = DefenseTowerFire.fire(sl, worldPosition, origin, target, ammo, getDamage(), ownerUuid);
        if (fired) {
            lastFireTick = now;
            setChanged();
            if (TOWER_DIAG) LittleMaidMoreAction.LOGGER.warn("[TOWER-DIAG] FIRED pos={} target={} now={}", worldPosition, target, now);
        }
    }

    

    /** 武器槽 (槽0) 自动判定攻击方式: 弓/弩→ARROW, 御币→DANMAKU, 其他/空→NONE.
     * ⚠ 御币 extends ProjectileWeaponItem (双平台实证) — 必须先判 isGohei, 否则御币被误判 ARROW
     * (fireArrow 找箭 → 御币塔无箭 → 不开火 → 不掉耐久; 2026-09-03 用户实测) */
    public DefenseTowerLogic.ShootMode weaponMode() {
        return DefenseTowerFire.weaponMode(ammo);   // 判定实现在火控子系统 (v79.63)
    }

    // ── 射击模式 ──

    

    

    

    

    

    

    /** 塔是否还能开火 (对应攻击方式有弹药) */
    public boolean hasAmmoFor(DefenseTowerLogic.ShootMode mode) {
        return DefenseTowerFire.hasAmmoFor(ammo, mode);
    }

    /** 右键菜单提供者 (DefenseTowerMenu 挂本塔) */
    public net.minecraft.world.MenuProvider getMenuProvider() {
        return new net.minecraft.world.SimpleMenuProvider(
                (id, inv, player) -> new DefenseTowerMenu(id, inv, this),
                net.minecraft.network.chat.Component.translatable("block.littlemaidmoreaction.garage_kit_defense"));
    }

    // ── 全局 tick 注册表 (DefenseTowerTickHandler 驱动; 弱引用防卸载残留) ──

    private static final java.util.Map<ServerLevel, java.util.Set<DefenseGarageKitBlockEntity>> LOADED = new java.util.WeakHashMap<>();

    /** 加载时自登记 (load/setLevel 调用) */
    public void registerForTick() {
        if (level instanceof ServerLevel sl) {
            LOADED.computeIfAbsent(sl, k -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>())).add(this);
        }
    }

    /** 卸载时移除 (setRemoved 调用) */
    public void unregisterForTick() {
        if (level instanceof ServerLevel sl) {
            java.util.Set<DefenseGarageKitBlockEntity> set = LOADED.get(sl);
            if (set != null) set.remove(this);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        registerForTick();
    }

    @Override
    public void setRemoved() {
        unregisterForTick();
        super.setRemoved();
    }

    /** tick 驱动入口 (每 20t 被 handler 调) — 遍历本维度已加载塔 */
    public static void tickLoaded(ServerLevel level) {
        java.util.Set<DefenseGarageKitBlockEntity> set = LOADED.get(level);
        if (set == null || set.isEmpty()) return;
        for (DefenseGarageKitBlockEntity tower : new java.util.ArrayList<>(set)) {
            if (!tower.isRemoved()) tower.towerTick();
        }
    }
}
