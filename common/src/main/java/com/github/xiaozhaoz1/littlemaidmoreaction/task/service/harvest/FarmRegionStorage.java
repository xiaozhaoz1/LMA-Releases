package com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 单女仆作物区域存储 (v79.64 用户裁定: **数据跟女仆实体走**) —
 * 落在该女仆 NBT 的 {@code lma_cfg_farm.regions} (ListTag), 走 {@link MaidData#cfgOrCreate}。
 *
 * <p><b>为什么不再用全局文件</b> (lessons #342): 旧设计把区域放
 * {@code config/littlemaidmoreaction/farm_regions.json} = {@code Map<uuid, List<FarmRegion>>} ⇒
 * ① 删女仆/收魂符后 uuid 条目**永久留文件** (只涨不消) ② 每次编辑**全量重写整份文件** (所有女仆)
 * ③ 记录无维度 ⇒ 跨维度坐标串世界 ④ 与项目 per-maid 惯例 (其它任务全走 {@code lma_cfg_<type>}) 不一致。
 * 对齐 TLM 的做法 (任务信息挂实体: {@code EntityMaid.getData(TaskDataKey)} → {@code MaidTaskDataMaps}
 * 写进女仆自己的 NBT) ⇒ 数据随实体消失、无孤儿、无全量重写 ✓。
 *
 * <p><b>维度语义</b> (用户裁定): 每个区域记 {@code dimension} — 女仆换维度后该区域**暂停**
 * ({@link FarmRegion#isActiveIn}), 不做坐标串世界。
 *
 * <p>纯 JVM: 记录 + 编解码逻辑与 MC 实体无关的部分可单测 (NBT 编解码为纯数据操作)。
 */
public final class FarmRegionStorage {

    /** 无箱哨兵 — Integer.MIN_VALUE (MC 世界坐标远达不到; 不能用 -1, 负坐标合法) */
    public static final int NO_BOX = Integer.MIN_VALUE;

    /** 女仆 NBT 配置键 (TaskConfigurable 同族: lma_cfg_<taskType>) — 本任务 taskType = "farm" */
    private static final String CFG_TASK = "farm";
    /** config 内的区域列表键 */
    private static final String KEY_REGIONS = "regions";

    private FarmRegionStorage() {}

    // ── 区域记录 (纯数据) ──

    /**
     * 单个种植区域 — 纯数据 (AABB 端点 + 指定作物 + 收获方式 + 本区域种子箱/目标箱坐标 + 维度).
     *
     * <p>v79.64: +{@code dimension} (维度 id, 空串 = 未指定/旧数据兜底). 女仆跨维度时区域
     * {@link #isActiveIn} 为 false ⇒ 该区域暂停 (用户裁定: 不做跨维度坐标串世界).
     */
    public record FarmRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             String cropId, String harvestMode,
                             Integer seedX, Integer seedY, Integer seedZ,
                             Integer harvestX, Integer harvestY, Integer harvestZ,
                             String name, String dimension) {

        /** 紧凑构造: 可空字段归一 (dimension/name/cropId) — 防下游 NPE */
        public FarmRegion {
            if (name == null) name = "";
            if (dimension == null) dimension = "";
            if (cropId == null) cropId = "";
        }

        /** 14 参便捷构造 (v79.62.1 加 name 前兼容) — name/dimension 默认空 */
        public FarmRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          String cropId, String harvestMode,
                          Integer seedX, Integer seedY, Integer seedZ,
                          Integer harvestX, Integer harvestY, Integer harvestZ) {
            this(minX, minY, minZ, maxX, maxY, maxZ, cropId, harvestMode,
                    seedX, seedY, seedZ, harvestX, harvestY, harvestZ, "", "");
        }

        /** 显示名 — name 空则「区域」 (v79.62.1 列表/详情界面用) */
        public String displayName() {
            return name.isBlank() ? "区域" : name;
        }

        /** 收获方式: "left" = 左键摧毁 (默认) / "right" = 右键交互 (FakePlayer, 右键收获作物/果树果实) */
        public boolean isRightHarvest() { return "right".equals(harvestMode); }

        /** 本区域是否绑定了种子源箱 */
        public boolean hasSeedBox() { return seedX != null; }

        /** 本区域是否绑定了收获目标箱 */
        public boolean hasHarvestBox() { return harvestX != null; }

        /** 是否包含某方块坐标 (闭区间) */
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /** 该区域在当前维度是否有效 (维度空串 = 视为有效, 兼容旧数据/测试构造) */
        public boolean isActiveIn(String currentDimensionId) {
            return dimension.isBlank() || dimension.equals(currentDimensionId);
        }

        /** 检查 region 合法性 (端点顺序 + 收获方式合法; cropId 可空 = 只收不种) — 纯逻辑可测 */
        public boolean isValid() {
            return minX <= maxX && minY <= maxY && minZ <= maxZ
                    && ("left".equals(harvestMode) || "right".equals(harvestMode));
        }
    }

    // ── 读 ──

    /**
     * 按 uuid 在指定维度找女仆 (网络包 C→S 用: 包只带 uuid) — 找不到返回 null
     * (女仆未加载/已收魂符 ⇒ 调用方直接放弃)。
     *
     * <p>用遍历而非 {@code level.getEntity(UUID)}: 后者在 1.20.1 不存在 (只有实体 id 重载,
     * 传 UUID 会被当成 int 解析)。本路径只在玩家操作农田 GUI/绑定交付时触发, 成本可接受。
     */
    public static EntityMaid findMaid(net.minecraft.server.level.ServerLevel level, String maidUuid) {
        for (var e : level.getAllEntities()) {
            if (e instanceof EntityMaid maid && maid.getStringUUID().equals(maidUuid)) return maid;
        }
        return null;
    }

    /** 读取某女仆的种植区域列表 (无 → 空列表)。
     *  <p>首次读取时顺带做一次**老全局文件导入** (v79.64 迁移, 幂等; 见 {@link FarmRegionLegacyImport})。 */
    public static List<FarmRegion> getFor(EntityMaid maid) {
        FarmRegionLegacyImport.maybeImportFor(maid);
        return readNbt(maid);
    }

    /**
     * **纯 NBT 读取** (不触发任何导入逻辑) — v79.64.1 抽出:
     * 导入流程内部要判"该女仆已有区域吗", 若直接调 {@link #getFor} 就会**回调导入** ⇒ 无限递归 ⇒ StackOverflow 崩服 ✗
     * (实测 crash-2026-09-17_12.49.53)。对外读走 {@link #getFor}; 导入/内部写前检查走本方法 ✓
     */
    public static List<FarmRegion> readNbt(EntityMaid maid) {
        CompoundTag cfg = MaidData.cfg(maid, CFG_TASK);
        if (!cfg.contains(KEY_REGIONS)) return List.of();
        ListTag list = cfg.getList(KEY_REGIONS, Tag.TAG_COMPOUND);
        List<FarmRegion> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            FarmRegion r = decode(list.getCompound(i));
            if (r != null && r.isValid()) out.add(r);
        }
        return out;
    }

    /** 是否有区域 (任务 validate 用) */
    public static boolean hasAny(EntityMaid maid) {
        return !getFor(maid).isEmpty();
    }

    // ── 写 ──

    /** 覆盖式写入某女仆的区域列表 (GUI 写侧入口); 无效条目丢弃 */
    public static void putRegions(EntityMaid maid, List<FarmRegion> regions) {
        ListTag list = new ListTag();
        for (FarmRegion r : regions) {
            if (r != null && r.isValid()) list.add(encode(r));
        }
        CompoundTag cfg = MaidData.cfgOrCreate(maid, CFG_TASK);
        cfg.put(KEY_REGIONS, list);
    }

    /** 追加单个区域 (绑定包/添加按钮) */
    public static void addRegion(EntityMaid maid, FarmRegion region) {
        if (region == null || !region.isValid()) return;
        List<FarmRegion> list = new ArrayList<>(getFor(maid));
        list.add(region);
        putRegions(maid, list);
    }

    /** 删除某女仆的第 index 个区域 */
    public static boolean removeRegion(EntityMaid maid, int index) {
        List<FarmRegion> list = new ArrayList<>(getFor(maid));
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        putRegions(maid, list);
        return true;
    }

    /** 更新某区域 (cropId + 收获方式; name/dimension 不变) */
    public static boolean updateRegion(EntityMaid maid, int index, String cropId, String harvestMode) {
        List<FarmRegion> list = new ArrayList<>(getFor(maid));
        if (index < 0 || index >= list.size()) return false;
        FarmRegion o = list.get(index);
        FarmRegion n = new FarmRegion(o.minX(), o.minY(), o.minZ(), o.maxX(), o.maxY(), o.maxZ(),
                cropId, harvestMode, o.seedX(), o.seedY(), o.seedZ(),
                o.harvestX(), o.harvestY(), o.harvestZ(), o.name(), o.dimension());
        if (!n.isValid()) return false;
        list.set(index, n);
        putRegions(maid, list);
        return true;
    }

    /** 全字段更新 (名称/作物/收获方式/箱子; 维度不变) — 区域详情屏保存 */
    public static boolean updateRegionFull(EntityMaid maid, int index, String name, String cropId,
                                           String harvestMode, Integer seedX, Integer seedY, Integer seedZ,
                                           Integer harvestX, Integer harvestY, Integer harvestZ) {
        List<FarmRegion> list = new ArrayList<>(getFor(maid));
        if (index < 0 || index >= list.size()) return false;
        FarmRegion o = list.get(index);
        FarmRegion n = new FarmRegion(o.minX(), o.minY(), o.minZ(), o.maxX(), o.maxY(), o.maxZ(),
                cropId, harvestMode, seedX, seedY, seedZ, harvestX, harvestY, harvestZ, name, o.dimension());
        if (!n.isValid()) return false;
        list.set(index, n);
        putRegions(maid, list);
        return true;
    }

    // ── 纯数据构造 (归一化端点) ──

    /** 纯数据转 region (gui/选区用) — 保证端点归一化; 收获方式默认 left; 无箱 */
    public static FarmRegion of(int x1, int y1, int z1, int x2, int y2, int z2, String cropId) {
        return of(x1, y1, z1, x2, y2, z2, cropId, "left");
    }

    /** 端点归一化 + 收获方式 + 无箱; dimension 由调用方再补 (导入/绑定时已知当前维度) */
    public static FarmRegion of(int x1, int y1, int z1, int x2, int y2, int z2,
                                String cropId, String harvestMode) {
        return of(x1, y1, z1, x2, y2, z2, cropId, harvestMode,
                NO_BOX, NO_BOX, NO_BOX, NO_BOX, NO_BOX, NO_BOX);
    }

    /** 端点归一化 + 收获方式 + 单一箱 (v79.62.1 便捷; 另一个箱为无箱) */
    public static FarmRegion of(int x1, int y1, int z1, int x2, int y2, int z2,
                                String cropId, String harvestMode,
                                int seedX, int seedY, int seedZ) {
        return of(x1, y1, z1, x2, y2, z2, cropId, harvestMode,
                seedX, seedY, seedZ, NO_BOX, NO_BOX, NO_BOX);
    }

    /** 全参: 端点归一化 (反向选择修正) + 无箱哨兵 → null */
    public static FarmRegion of(int x1, int y1, int z1, int x2, int y2, int z2,
                                String cropId, String harvestMode,
                                int seedX, int seedY, int seedZ,
                                int harvestX, int harvestY, int harvestZ) {
        return new FarmRegion(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2),
                cropId, harvestMode,
                seedX == NO_BOX ? null : seedX, seedY == NO_BOX ? null : seedY, seedZ == NO_BOX ? null : seedZ,
                harvestX == NO_BOX ? null : harvestX, harvestY == NO_BOX ? null : harvestY,
                harvestZ == NO_BOX ? null : harvestZ,
                "", "");
    }

    // ── NBT 编解码 (纯数据, 可单测) ──

    static CompoundTag encode(FarmRegion r) {
        CompoundTag t = new CompoundTag();
        t.putInt("minX", r.minX());
        t.putInt("minY", r.minY());
        t.putInt("minZ", r.minZ());
        t.putInt("maxX", r.maxX());
        t.putInt("maxY", r.maxY());
        t.putInt("maxZ", r.maxZ());
        t.putString("cropId", r.cropId());
        t.putString("harvestMode", r.harvestMode());
        t.putString("name", r.name());
        t.putString("dimension", r.dimension());
        if (r.hasSeedBox()) {
            t.putInt("seedX", r.seedX());
            t.putInt("seedY", r.seedY());
            t.putInt("seedZ", r.seedZ());
        }
        if (r.hasHarvestBox()) {
            t.putInt("harvestX", r.harvestX());
            t.putInt("harvestY", r.harvestY());
            t.putInt("harvestZ", r.harvestZ());
        }
        return t;
    }

    static FarmRegion decode(CompoundTag t) {
        try {
            return new FarmRegion(
                    t.getInt("minX"), t.getInt("minY"), t.getInt("minZ"),
                    t.getInt("maxX"), t.getInt("maxY"), t.getInt("maxZ"),
                    t.getString("cropId"), t.getString("harvestMode"),
                    t.contains("seedX") ? t.getInt("seedX") : null,
                    t.contains("seedY") ? t.getInt("seedY") : null,
                    t.contains("seedZ") ? t.getInt("seedZ") : null,
                    t.contains("harvestX") ? t.getInt("harvestX") : null,
                    t.contains("harvestY") ? t.getInt("harvestY") : null,
                    t.contains("harvestZ") ? t.getInt("harvestZ") : null,
                    t.getString("name"), t.getString("dimension"));
        } catch (Exception e) {
            return null;   // 坏条目跳过 (铁律: 用户手改/旧数据不应让整体失效)
        }
    }
}
