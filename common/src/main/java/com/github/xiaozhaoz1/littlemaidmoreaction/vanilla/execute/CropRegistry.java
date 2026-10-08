package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 作物能力注册表 (v79.62.1 通用作物判定体系重构) — 「收获」与「种植/播种」双面:
 * <ul>
 *   <li><b>收获面</b> — 区域内找「已成熟的作物」并收获 (用户裁定: 区域内收全部成熟, 不限指定)</li>
 *   <li><b>种植面</b> — 区域内种「指定作物」(用户区域里选定的 seed item → canPlant/plant)</li>
 * </ul>
 * 内置原版作物 + {@link #registerHandler} 第三方扩展 (对齐 TLM SpecialCropManager).
 *
 * <p><b>收获分流 (v79.62.1, 参考 Harvest With Ease / Actually Harvest):</b>
 * <ol>
 *   <li>破坏模式 — destroyBlock (甘蔗/仙人掌顶端/西瓜南瓜果实)</li>
 *   <li>右键收获 — SweetBerryBush/CaveVines → FakePlayer 模拟右键 (isRightClickHarvestable=true)</li>
 *   <li>茎摘果实 — StemBlock/AttachedStemBlock → facing 找相邻果实破坏, 茎保留</li>
 *   <li>垂直作物顶端收割 — 甘蔗/仙人掌/竹只收顶端保留基部 (isMatureAt 顶端判定)</li>
 * </ol>
 *
 * <p>判定依赖 MC BlockState (非纯 JVM) — 验证走 gametest (lmaFarmJob).
 */
public final class CropRegistry {

    /** 作物处理范围 — canHarvest 判定 + 收获动作 */
    public interface CropHandler {
        /** 该方块 (state) 是否为「可收获的成熟作物」 */
        boolean isMature(BlockState state);
        /** 收获动作 (破坏/重置 age 由实现决定; 产物落包由执行方处理) */
        void harvest(ServerLevel world, BlockPos pos, BlockState state);
        /** 右键收获作物? (true → FarmExecute 走 FakePlayer.rightClick; false → destroyBlock/resetAge) */
        default boolean isRightClickHarvestable() { return false; }
        /** 位置感知的成熟判定 (需读相邻方块的作物覆写; 默认委托 isMature) */
        default boolean isMatureAt(ServerLevel level, BlockPos pos, BlockState state) {
            return isMature(state);
        }
    }

    private static final Map<Block, CropHandler> HANDLERS = new ConcurrentHashMap<>();

    /** 方块 → 种子物品 (v79.62.1 默认作物识别: 标记点作物 → 区域 cropId) */
    private static final Map<Block, net.minecraft.world.item.Item> BLOCK_TO_SEED = new ConcurrentHashMap<>();

    private CropRegistry() {}

    static {
        // 原版 CropBlock 族 (小麦/胡萝卜/土豆/甜菜 — isMaxAge, 留株收割由执行方定毁/留)
        HANDLERS.put(Blocks.CARROTS, new CropBlockHandler());
        HANDLERS.put(Blocks.POTATOES, new CropBlockHandler());
        HANDLERS.put(Blocks.WHEAT, new CropBlockHandler());
        HANDLERS.put(Blocks.BEETROOTS, new CropBlockHandler());
        // 甘蔗/竹/仙人掌 — 垂直增长 (顶端判定: 上方无同种=顶端=可收; 保留基部)
        HANDLERS.put(Blocks.SUGAR_CANE, new VerticalCropHandler(Blocks.SUGAR_CANE));
        HANDLERS.put(Blocks.BAMBOO, new VerticalCropHandler(Blocks.BAMBOO));
        HANDLERS.put(Blocks.CACTUS, new VerticalCropHandler(Blocks.CACTUS));
        // v79.62.1 海带 — 水下垂直生长 (GrowingPlantHeadBlock, 收顶端保留基部)
        HANDLERS.put(Blocks.KELP, new VerticalCropHandler(Blocks.KELP));
        HANDLERS.put(Blocks.KELP_PLANT, new VerticalCropHandler(Blocks.KELP_PLANT));
        // 下界疣 (AGE 3)
        HANDLERS.put(Blocks.NETHER_WART, new NetherWartHandler());
        // 可可豆 (AGE 2, 丛林木侧)
        HANDLERS.put(Blocks.COCOA, new CocoaHandler());
        // 西瓜/南瓜果实 — isMature 恒 false (防误收; 果实由茎摘通道处理, 不走 isMatureCrop 扫描)
        HANDLERS.put(Blocks.MELON, new StemGrownHandler());
        HANDLERS.put(Blocks.PUMPKIN, new StemGrownHandler());
        // v79.62.1 新增: 甜浆果丛 (AGE 3, 右键收获 → AGE 1, 掉浆果, 保留灌木丛)
        HANDLERS.put(Blocks.SWEET_BERRY_BUSH, new SweetBerryBushHandler());
        // v79.62.1 新增: 发光浆果藤蔓 (BERRIES, 右键收获 → BERRIES false, 掉发光浆果)
        HANDLERS.put(Blocks.CAVE_VINES, new CaveVinesHandler());       // CaveVinesHeadBlock (顶部生长点)
        HANDLERS.put(Blocks.CAVE_VINES_PLANT, new CaveVinesHandler()); // CaveVinesBlock (藤蔓体)
        

        // 方块 → 种子 (默认作物识别 — 区域绑定时标记点作物自动设为 cropId)
        BLOCK_TO_SEED.put(Blocks.WHEAT, net.minecraft.world.item.Items.WHEAT_SEEDS);
        BLOCK_TO_SEED.put(Blocks.CARROTS, net.minecraft.world.item.Items.CARROT);
        BLOCK_TO_SEED.put(Blocks.POTATOES, net.minecraft.world.item.Items.POTATO);
        BLOCK_TO_SEED.put(Blocks.BEETROOTS, net.minecraft.world.item.Items.BEETROOT_SEEDS);
        BLOCK_TO_SEED.put(Blocks.SUGAR_CANE, net.minecraft.world.item.Items.SUGAR_CANE);
        BLOCK_TO_SEED.put(Blocks.BAMBOO, net.minecraft.world.item.Items.BAMBOO);
        BLOCK_TO_SEED.put(Blocks.CACTUS, net.minecraft.world.item.Items.CACTUS);
        BLOCK_TO_SEED.put(Blocks.KELP, net.minecraft.world.item.Items.KELP);
        BLOCK_TO_SEED.put(Blocks.KELP_PLANT, net.minecraft.world.item.Items.KELP);
        BLOCK_TO_SEED.put(Blocks.NETHER_WART, net.minecraft.world.item.Items.NETHER_WART);
        BLOCK_TO_SEED.put(Blocks.COCOA, net.minecraft.world.item.Items.COCOA_BEANS);
        BLOCK_TO_SEED.put(Blocks.MELON, net.minecraft.world.item.Items.MELON_SEEDS);
        BLOCK_TO_SEED.put(Blocks.PUMPKIN, net.minecraft.world.item.Items.PUMPKIN_SEEDS);
        // v79.62.1 新增: 浆果/发光浆果 (非 ItemNameBlockItem, 是放置物品; 供 seedIdFor 识别; 紫颂花不搜集已移除)
        BLOCK_TO_SEED.put(Blocks.SWEET_BERRY_BUSH, net.minecraft.world.item.Items.SWEET_BERRIES);
        BLOCK_TO_SEED.put(Blocks.CAVE_VINES, net.minecraft.world.item.Items.GLOW_BERRIES);
        BLOCK_TO_SEED.put(Blocks.CAVE_VINES_PLANT, net.minecraft.world.item.Items.GLOW_BERRIES);
    }

    /** v79.62.1 方块 → 种子物品 id (默认作物识别; 未识别 → null) */
    @javax.annotation.Nullable
    public static String seedIdFor(Block block) {
        net.minecraft.world.item.Item it = BLOCK_TO_SEED.get(block);
        if (it == null) return null;
//? if 1.20.1 {
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it).toString();
//?} else {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).toString();
//?}
    }

    /** 注册第三方作物 handler (crop 方块 → 可收判定 + 收获动作) */
    public static void registerHandler(Block cropBlock, CropHandler handler) {
        if (cropBlock != null && handler != null) HANDLERS.put(cropBlock, handler);
    }

    /** 该 state 是否为可收获的成熟作物 — 内置 handler 命中优先; 否则 age fallback (mod 作物)
     *  <p>v79.62.1 通用判定: instanceof 类型判定 + 方块 ID + age 属性 fallback; 果实方块永不成熟 */
    public static boolean isMatureCrop(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        CropHandler h = HANDLERS.get(block);
        if (h != null) return h.isMatureAt(level, pos, state);
        // 茎/附着茎 — 支撑结构, 不能收 (破坏茎=藤根被拔不再结果)
        if (block instanceof StemBlock || block instanceof AttachedStemBlock) {
            return false;
        }
        // 紫颂植株 — 基座, 不收 (紫颂花也不搜集, 紫颂整族不纳入收获)
        if (block instanceof net.minecraft.world.level.block.ChorusPlantBlock) {
            return false;
        }
        // 紫颂花 — 不搜集 (用户裁定 2026-08-26; 防 age fallback 把 AGE=5 死花当成熟误收)
        if (block instanceof ChorusFlowerBlock) {
            return false;
        }
        // 未知作物 fallback: 找名为 "age" 的 IntegerProperty 且已到上界 (模组作物多数有 age 属性且 max 成熟)
        for (net.minecraft.world.level.block.state.properties.Property<?> p : state.getProperties()) {
            if (p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip
                    && "age".equals(ip.getName())) {
                int age = state.getValue(ip);
                int max = ip.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                return age >= max;
            }
        }
        // 无 age 属性的未知方块 — 不视为成熟作物 (避免误收)
        return false;
    }

    /** 收获一个成熟作物方块; 返回 false=不可收/未注册 */
    public static boolean harvest(ServerLevel world, BlockPos pos, BlockState state) {
        CropHandler h = HANDLERS.get(state.getBlock());
        if (h == null || !h.isMatureAt(world, pos, state)) return false;
        h.harvest(world, pos, state);
        return true;
    }

    /** v79.62.1 该方块是否为右键收获作物 — FarmExecute 据此选收获路径 */
    public static boolean isRightClickHarvestable(BlockState state) {
        CropHandler h = HANDLERS.get(state.getBlock());
        return h != null && h.isRightClickHarvestable();
    }

    /** v79.62.1 该方块是否为茎类作物 (StemBlock/AttachedStemBlock) — 茎摘通道判定 */
    public static boolean isStemCrop(BlockState state) {
        Block block = state.getBlock();
        return block instanceof StemBlock || block instanceof AttachedStemBlock;
    }

    /**
     * v79.62.1 茎摘果实 — 按 facing 找相邻果实方块破坏, 茎保留.
     * <p>AttachedStemBlock: FACING 指向果实方向 → pos.relative(facing) 破坏.
     * <p>StemBlock (AGE=7 未转): 四方向找相邻 MelonBlock/PumpkinBlock 破坏.
     * @return true=找到并破坏了果实
     */
    public static boolean harvestStemFruit(ServerLevel world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof AttachedStemBlock) {
            Direction facing = state.getValue(AttachedStemBlock.FACING);
            BlockPos fruitPos = pos.relative(facing);
            BlockState fruitState = world.getBlockState(fruitPos);
            if (fruitState.is(Blocks.MELON) || fruitState.is(Blocks.PUMPKIN)) {
                world.destroyBlock(fruitPos, false, null);
                return true;
            }
            return false;
        }
        if (state.getBlock() instanceof StemBlock) {
            // 茎 AGE=7 但未转 AttachedStemBlock: 四方向找果实
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos fruitPos = pos.relative(dir);
                BlockState fruitState = world.getBlockState(fruitPos);
                if (fruitState.is(Blocks.MELON) || fruitState.is(Blocks.PUMPKIN)) {
                    world.destroyBlock(fruitPos, false, null);
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    // ── 内置 handler ──

    /** 原版 CropBlock — isMaxAge 成熟; 收获=重置 age 0 (留株; 毁株由配置/执行方决定) */
    static final class CropBlockHandler implements CropHandler {
        @Override public boolean isMature(BlockState state) {
            return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        }
        @Override public void harvest(ServerLevel world, BlockPos pos, BlockState state) {
            world.setBlock(pos, state.setValue(CropBlock.AGE, 0), 3);
        }
    }

    /** 垂直增长作物 (甘蔗/竹/仙人掌/海带) — 参考 TLM TaskSugarCane:
     *  <p>找中间格 (下方有同种 + 下方第二格是基座) → 破坏中间格 → 上方自动掉落 → 收2格保基部1格 */
    static final class VerticalCropHandler implements CropHandler {
        private final Block self;
        VerticalCropHandler(Block self) { this.self = self; }
        @Override public boolean isMature(BlockState state) {
            return state.is(self);
        }
        @Override public boolean isMatureAt(ServerLevel level, BlockPos pos, BlockState state) {
            // TLM 做法: 自身是甘蔗 + 下方是甘蔗 + 下方第二格是基座 (非同种) = 中间格, 可收
            BlockState below = level.getBlockState(pos.below());
            BlockState below2 = level.getBlockState(pos.below(2));
            return state.is(self) && below.is(self) && !below2.is(self);
        }
        @Override public void harvest(ServerLevel world, BlockPos pos, BlockState state) {
            // 破坏中间格 — 上方甘蔗失去支撑自动掉落, 一次收2格, 基部1格保留
            world.destroyBlock(pos, false, null);
        }
    }

    /** 下界疣 — AGE 3 成熟 */
    static final class NetherWartHandler implements CropHandler {
        @Override public boolean isMature(BlockState state) {
            return state.getBlock() instanceof NetherWartBlock
                    && state.getValue(NetherWartBlock.AGE) >= 3;
        }
        @Override public void harvest(ServerLevel world, BlockPos pos, BlockState state) {
            world.setBlock(pos, state.setValue(NetherWartBlock.AGE, 0), 3);
        }
    }

    /** 可可豆 — AGE 2 成熟; destroyBlock (附着在原木侧面, 破坏取豆, 原木保留) */
    static final class CocoaHandler implements CropHandler {
        @Override public boolean isMature(BlockState state) {
            return state.getBlock() instanceof CocoaBlock
                    && state.getValue(CocoaBlock.AGE) >= 2;
        }
        @Override public void harvest(ServerLevel world, BlockPos pos, BlockState state) {
            world.destroyBlock(pos, false, null);  // 参考TLM: 破坏可可豆方块, 原木保留
        }
    }

    /** v79.62.1 西瓜/南瓜果实 — 相邻有茎才算可收 (参考 TLM TaskMelon.canHarvest) */
    static final class StemGrownHandler implements CropHandler {
        @Override public boolean isMature(BlockState state) {
            return false;  // isMatureAt 覆写, 此方法仅作 fallback
        }
        @Override public boolean isMatureAt(ServerLevel level, BlockPos pos, BlockState state) {
            // 参考TLM: 西瓜/南瓜方块四方向找相邻茎 (AttachedStemBlock), 有茎=可收果实
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockState adj = level.getBlockState(pos.relative(dir));
                if (adj.getBlock() instanceof AttachedStemBlock) return true;
            }
            return false;
        }
        @Override public void harvest(ServerLevel world, BlockPos pos, BlockState state) {
            world.destroyBlock(pos, false, null);  // 破坏果实, 茎保留继续结果
        }
    }

    /** v79.62.1 甜浆果丛 — AGE 3 成熟; 右键收获 (FakePlayer 模拟 → AGE 1, 掉浆果, 保留灌木丛) */
    static final class SweetBerryBushHandler implements CropHandler {
        @Override public boolean isMature(BlockState state) {
            // AGE>=3 才收 (用户裁定 2026-08-26: 长满 3 才摘, AGE 2 不收)
            return state.getBlock() instanceof SweetBerryBushBlock
                    && state.getValue(SweetBerryBushBlock.AGE) >= 3;
        }
        @Override public boolean isRightClickHarvestable() { return true; }
        @Override public void harvest(ServerLevel world, BlockPos pos, BlockState state) {
            // 右键收获由 FarmExecute 调 FakePlayerInteract.rightClick — 此方法仅在非右键模式 fallback
            // 手动模拟: 重置 AGE 1 + 掉浆果 (右键路径不进此方法, 此为保险)
            world.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), 3);
        }
    }

    /** v79.62.1 发光浆果藤蔓 — BERRIES=true 成熟; 右键收获 (BERRIES false, 掉发光浆果, 保留藤蔓) */
    static final class CaveVinesHandler implements CropHandler {
        @Override public boolean isMature(BlockState state) {
            return state.getBlock() instanceof CaveVines
                    && state.getValue(CaveVines.BERRIES);
        }
        @Override public boolean isRightClickHarvestable() { return true; }
        @Override public void harvest(ServerLevel world, BlockPos pos, BlockState state) {
            // 右键收获由 FarmExecute 调 FakePlayerInteract.rightClick — 此方法为保险
            if (state.hasProperty(CaveVines.BERRIES)) {
                world.setBlock(pos, state.setValue(CaveVines.BERRIES, false), 3);
            }
        }
    }

    // ── 种植面 (指定作物种子 → 基座) ──

    /** 基座是否可容纳某 seed (ItemNameBlockItem → 方块属性判定); 调用方保证 seed 非空
     *  <p>v79.62.1: IPlantable 在 1.21.1 NeoForge 已移除 → 用硬编码基座 + BlockTags.DIRT 标签替代 */
    public static boolean canPlantOn(BlockState base, ItemStack seed) {
        if (seed.isEmpty()) return false;
        if (!(seed.getItem() instanceof net.minecraft.world.item.ItemNameBlockItem item)) return false;
        Block block = item.getBlock();
        // 耕地类基座 → 作物块族
        if (block instanceof CropBlock) {
            return base.is(net.minecraft.world.level.block.Blocks.FARMLAND);
        }
        if (block == Blocks.SUGAR_CANE) {
            return base.is(Blocks.SUGAR_CANE) || base.is(Blocks.GRASS_BLOCK)
                    || base.is(Blocks.DIRT) || base.is(Blocks.SAND) || base.is(Blocks.RED_SAND)
                    || base.is(Blocks.PODZOL) || base.is(Blocks.SANDSTONE) || base.is(Blocks.RED_SANDSTONE);
        }
        if (block == Blocks.BAMBOO) {
            return base.is(Blocks.BAMBOO) || base.is(Blocks.BAMBOO_SAPLING) || base.is(Blocks.GRASS_BLOCK)
                    || base.is(Blocks.DIRT) || base.is(Blocks.SAND) || base.is(Blocks.PODZOL);
        }
        if (block == Blocks.CACTUS) {
            return base.is(Blocks.CACTUS) || base.is(Blocks.SAND) || base.is(Blocks.RED_SAND);
        }
        // 海带 — 种在水下任何方块上 (实际是放置在水里)
        if (block == Blocks.KELP) {
            return false;  // 海带只收不种 (需水下环境, 女仆不负责种)
        }
        if (block == Blocks.NETHER_WART) {
            return base.is(net.minecraft.world.level.block.Blocks.SOUL_SAND);
        }
        // 可可豆 — 种在丛林原木侧面 (参考 TLM TaskCocoa: base 是原木 → 四方向找空位)
        if (block instanceof CocoaBlock) {
            return base.is(net.minecraft.tags.BlockTags.JUNGLE_LOGS);
        }
        // 西瓜/南瓜 — 只收果实不种茎 (玩家种茎, 女仆只负责破坏果实)
        if (block == Blocks.MELON || block == Blocks.PUMPKIN) {
            return false;
        }
        // 紫颂花 — 不种也不搜集 (用户裁定 2026-08-26; 保留此分支防 DIRT 兜底误种)
        if (block instanceof net.minecraft.world.level.block.ChorusFlowerBlock) {
            return false;
        }
        // 甜浆果丛/发光浆果 — 只收不种 (放置物品, 非种子种植体系)
        if (block == net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH
                || block instanceof net.minecraft.world.level.block.CaveVines) {
            return false;
        }
        // v79.62.1: 未知种子 — BlockTags.DIRT 标签兜底 (替代已移除的 IPlantable; 双平台都有此标签)
        return base.is(net.minecraft.tags.BlockTags.DIRT);
    }
}
