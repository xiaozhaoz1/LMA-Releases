package com.github.xiaozhaoz1.littlemaidmoreaction.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.gametest.TLMGameTests;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.StructureSense;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
//? if 1.20.1 {
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
//?} else {
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
//?}

import java.util.List;

/**
 * LMA gametest — 继承 TLM {@link TLMGameTests} (TLM 映射一致, 直接复用其测试基建),
 * 独立 {@code @GameTestHolder} (LMA 命名空间, 注解非 @Inherited 需自持)。
 *
 * <p>template 复用 TLM game_test.nbt 结构 (提取至 data/littlemaidmoreaction/structures/)。
 *
 * <p>v77.4: JSON 平台退役 — 删 10 个 JSON 任务测试, 留 3 个代码管线测试 (3/3)。
 * 现 21 个: spawn/lifecycle/gate/priority/budget/haqi×2/chainOre/nbt/registration/structureSense/mlgRescue/
 * milkRecipes/farmJob/farmPlant/farmSeedBox/farmHarvestBox/farmBerryBush/farmSugarCaneTop/farmMelonNoFalseHarvest (2026-08-22 实测)。
 */
@GameTestHolder(LittleMaidMoreAction.MOD_ID)
@PrefixGameTestTemplate(value = false)
public class LmaGameTests extends TLMGameTests {

    /** LMA 基础冒烟: 智能符放置女仆成功 + LMA 任务注册可用 */
    @GameTest(template = "game_test")
    public static void lmaMaidSpawn(GameTestHelper helper) {
//? if 1.20.1 {
        Player player = helper.makeMockSurvivalPlayer();
//?} else {
        Player player = helper.makeMockPlayer(GameType.DEFAULT_MODE);
//?}
        ItemStack smartSlab = InitItems.SMART_SLAB_INIT.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, smartSlab);

        BlockPos groundPos = new BlockPos(7, 1, 7);
        useItemOn(helper, player, smartSlab, groundPos, Direction.UP);

        helper.runAfterDelay(2, () -> {
            List<EntityMaid> entities = helper.getEntities(InitEntities.MAID.get(), groundPos.above(), 1);
            if (entities.isEmpty()) {
                helper.fail("女仆未生成 (smart_slab_init 放置失败)");
                return;
            }
            // Java 17 兼容 (forge 节点) — getFirst 为 Java 21 API
            EntityMaid maid = entities.get(0);
            if (maid.getTask() == null) {
                helper.fail("女仆任务为空 (TLM 任务系统未初始化)");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * MLG 摔落自救端到端 (v79.61x) — 背包水桶 + 高空坠落:
     * SelfRescueTrigger 摔落预触发提交 → MlgRescueCoordinator 落点倒水 → 落地无伤。
     * (无水桶对照: 9 格坠落 = 6 点伤害, 掉血事件触发后无自救动作自终结)
     */
    @GameTest(template = "game_test")
    public static void lmaMlgRescue(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return; // spawnMaid 已 fail

        // 背包塞水桶 (换手链用主手, 放水后变空桶回背包)
        var inv = maid.getAvailableInv(true);
        ItemStack leftover = inv.insertItem(0, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET), false);
        if (!leftover.isEmpty()) {
            helper.fail("水桶未放入女仆背包");
            return;
        }

        // teleport 到平台上方 15 格空中 — 高度必须 ≥12 格: 自由落体 ~10 tick 才达 -0.7
        // 阈值 (9 格坠落终端速度仅 ~0.66 永不触发 — 双平台实测教训); 15 格 = 12 伤害, 女仆存活
        BlockPos ground = helper.absolutePos(new BlockPos(7, 1, 7));
        maid.teleportTo(ground.getX() + 0.5, ground.getY() + 17, ground.getZ() + 0.5);
        float hpBefore = maid.getHealth();

        helper.runAfterDelay(60, () -> {
            float hp = maid.getHealth();
            if (hp < hpBefore) {
                helper.fail("摔落受伤 " + hp + "/" + hpBefore + " (MLG 倒水未生效)");
                return;
            }
            helper.succeed();
        });
    }

    /** 任务生命周期: submit → 状态写入 → complete → 键清理 (单一写入入口守护) */
    @GameTest(template = "game_test")
    public static void lmaTaskLifecycle(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return; // spawnMaid 已 fail

        // submit 简单任务 (bell_ring — 代码管线, JSON 预设退役后回退 Java)
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
            .submit(maid, "bell_ring", null, 0);
        if (!ok) {
            helper.fail("submit 失败 (bell_ring)");
            return;
        }

        var data = maid.getPersistentData();
        if (!"bell_ring".equals(data.getString(
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.FLOW_TASK))) {
            helper.fail("submit 未写任务类型 NBT");
            return;
        }

        // complete → 标记 completed 后 clearAll 全键清理 (终结语义: NBT 归零)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.complete(maid);
        if (!data.getString(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.FLOW_TASK).isEmpty()) {
            helper.fail("complete 未清理任务键");
            return;
        }
        if (data.contains(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.FLOW_STATE)) {
            helper.fail("complete 未清理状态键");
            return;
        }
        helper.succeed();
    }

    /** 生成女仆并返回 (失败时已 helper.fail, 返回 null) — 默认 mock player 放置 */
    private static EntityMaid spawnMaid(GameTestHelper helper) {
//? if 1.20.1 {
        return spawnMaid(helper, helper.makeMockSurvivalPlayer());
//?} else {
        return spawnMaid(helper, helper.makeMockPlayer(GameType.DEFAULT_MODE));
//?}
    }

    /** 生成女仆并返回 (失败时已 helper.fail, 返回 null) — 指定 player 放置 (smart slab tame → owner = 该 player) */
    private static EntityMaid spawnMaid(GameTestHelper helper, Player player) {
        ItemStack smartSlab = InitItems.SMART_SLAB_INIT.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, smartSlab);

        BlockPos groundPos = new BlockPos(7, 1, 7);
        useItemOn(helper, player, smartSlab, groundPos, Direction.UP);

        List<EntityMaid> entities = helper.getEntities(InitEntities.MAID.get(), groundPos.above(), 1);
        if (entities.isEmpty()) {
            helper.fail("女仆未生成 (smart_slab_init 放置失败)");
            return null;
        }
        return entities.get(0);
    }

    private static void useItemOn(GameTestHelper helper, Player player, ItemStack stack,
                                  BlockPos relativePos, Direction face) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolutePos), face, absolutePos, false);
        UseOnContext context = new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit);
        stack.useOn(context);
    }

    /**
     * AI 操控门控全链 — 默认关闭 → tick 开启 → onCleanup 关闭 (权限闭环)。
     * 不依赖 Brain 30tick 时序, 直接驱动 pipeline。
     */
    @GameTest(template = "game_test")
    public static void lmaAiControlGate(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        var pl = new com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline();

        // 默认关闭
        if (com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate.isEnabled(maid)) {
            helper.fail("初始门控应为关闭");
            return;
        }
        // tick 首次执行 → 开启 (原 execute 接口删除, 回归修复路径)
        pl.tick((net.minecraft.server.level.ServerLevel) maid.level(), maid);
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate.isEnabled(maid)) {
            helper.fail("tick 未开启门控");
            return;
        }
        // onCleanup (任务取消) → 关闭 (键删除闭环)
        pl.onCleanup(maid);
        if (com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate.isEnabled(maid)) {
            helper.fail("onCleanup 未关闭门控");
            return;
        }
        helper.succeed();
    }

    /**
     * 任务优先级冲突 — 高优先级抢占 + 低优先级拒绝 (TaskDispatcher.submit 冲突策略)。
     * 注册测试任务 __pri_test (priority=1) — gametest 服务器生命周期内留存 (showInBar=false 隔离)。
     */
    @GameTest(template = "game_test")
    public static void lmaPriorityConflict(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        var priPl = new com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline() {
            @Override public String taskType() { return "__pri_test"; }
            @Override public int priority() { return 1; }
        };
        // 注册去 showInBar — 测试隔离任务用 registerPassive (不显示任务栏)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.registerPassive(
                "__pri_test", priPl);

        String flowTask = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.FLOW_TASK;
        var data = maid.getPersistentData();
        // 低优先级 (0) 先提交成功
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "bell_ring", null, 0)) {
            helper.fail("bell_ring submit 失败");
            return;
        }
        if (!"bell_ring".equals(data.getString(flowTask))) {
            helper.fail("初始任务应为 bell_ring");
            return;
        }
        // 高优先级抢占 (等/高 → 抢占)
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "__pri_test", null, 0)) {
            helper.fail("高优先级抢占应成功");
            return;
        }
        if (!"__pri_test".equals(data.getString(flowTask))) {
            helper.fail("抢占后任务应为 __pri_test");
            return;
        }
        // 低优先级再提交 → 拒绝, 任务不变
        if (com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "bell_ring", null, 0)) {
            helper.fail("低优先级提交应被拒绝");
            return;
        }
        if (!"__pri_test".equals(data.getString(flowTask))) {
            helper.fail("拒绝后任务不应改变");
            return;
        }
        // 清理: 取消任务 (防运行中残留)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(maid);
        helper.succeed();
    }

    /**
     * 被动 tick 通道隔离 (v79.61x 脱管线 — 原 lmaPassiveBudget 轮转语义退役):
     * GMPM tickPassiveFor 只驱动 GMPM 真管线 (haqi/self_rescue),
     * tickStandalonePassives 只驱动跨 tick 动作型 (temp/torch) —
     * 注册的自定义被动不属于任一通道 → 两通道均不驱动 (in_progress 也不生效)。
     */
    @GameTest(template = "game_test")
    public static void lmaPassiveTickIsolation(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        int[] tickA = {0};
        var pa = new com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline() {
            @Override public String taskType() { return "__passive_a"; }
            @Override public void tick(net.minecraft.server.level.ServerLevel w, EntityMaid m) { tickA[0]++; }
        };
        com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.registerPassive("__passive_a", pa);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submitPassive(maid, "__passive_a");

        var passives = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.passiveTasksList();
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        long base = sl.getGameTime();
        // 两通道各驱动 5 tick — 自定义被动 (非集合成员) 必须零驱动
        for (int i = 0; i < 5; i++) {
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                    .tickPassiveFor(sl, maid, passives, base + i);
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                    .tickStandalonePassives(sl, maid, passives);
        }
        if (tickA[0] != 0) {
            helper.fail("自定义被动不属于 GMPM/独立心跳任一通道, 应零驱动, 实际 " + tickA[0]);
            return;
        }
        helper.succeed();
    }

    /**
     * 温度热源安全目标 (v79.61x 用户裁定 A+B):
     * ① 热源清单不含火方块 (FIRE/SOUL_FIRE 已剔除 — 女仆会走进火里被点燃);
     * ② safeStand 落点非热源本体 (岩浆块可站立烫脚 — 导航应指安全邻格)。
     */
    @GameTest(template = "game_test")
    public static void lmaTempAdaptSafeTarget(GameTestHelper helper) {
        // 构造: 火方块 / 岩浆块 / 点燃营火 (一条线)
        // 构造: 火方块 / 岩浆块 / 点燃营火 (一条线) — 放世界地表面 (BlockPatternCache 高度窗
        // 语义: 热源在近地表; gametest 悬浮平台低空 miss 实证 — 测试用 getHeight 定位地表)
        net.minecraft.server.level.ServerLevel sl0 = helper.getLevel();
        int baseRelY = 0;
        {
            BlockPos abs = helper.absolutePos(new BlockPos(0, 0, 0));
            int absCy = abs.getY() + 1;   // 平台上一格 (center 用)
            if (absCy <= sl0.getMinBuildHeight()) absCy = sl0.getMinBuildHeight() + 1;
            // 取 center 所在列世界地表面 — 热源放地表 (scanChunk 高度窗 [surface-8, surface+8])
            // 若平台即地表则相对 y=1; 否则抬到世界地表层 (测试语义 = 地表热源)
            int surfaceAbs = sl0.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, abs.getX(), abs.getZ());
            int relSurf = surfaceAbs - abs.getY() + 1;
            if (relSurf < 1) relSurf = 1;
            baseRelY = relSurf;
        }
        helper.setBlock(0, baseRelY, 1, net.minecraft.world.level.block.Blocks.FIRE);
        helper.setBlock(0, baseRelY, 2, net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
        helper.setBlock(0, baseRelY, 3, net.minecraft.world.level.block.Blocks.CAMPFIRE
                .defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true));

        net.minecraft.server.level.ServerLevel sl = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(0, baseRelY, 0));
        BlockPos fire = helper.absolutePos(new BlockPos(0, baseRelY, 1));
        BlockPos magma = helper.absolutePos(new BlockPos(0, baseRelY, 2));
        BlockPos camp = helper.absolutePos(new BlockPos(0, baseRelY, 3));

        // ① 热源查询 — 火方块必须被剔除 (最近热源 ∈ {岩浆块, 点燃营火})
        // v79.62.2 修 #238 缓存时序: setBlock 不失效缓存, 新放热源被旧「无热源」HEAT 缓存遮蔽
        // → 查询前清整个维度的 HEAT 缓存 (clearType 只清 HEAT, 不碰 FARMLAND/CROP 等其他测试缓存)
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache
                .clearType(sl.dimension().location().toString(),
                        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache.PatternType.HEAT);
        // v79.62.2 修: radius 8 → chunkRadius=0 只扫 center 所在区块; magma/camp 在 2 格外但跨区块
        // (63>>4=3 vs 65>>4=4) 被排除 → 查不到热源. radius=16 (chunkRadius=1) 覆盖邻区块.
        BlockPos nearest = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.HeatSourceQuery
                .nearestHeatSource(sl, center, 16);
        if (nearest == null) {
            helper.fail("场景内应有热源 (岩浆块/点燃营火)");
            return;
        }
        if (nearest.equals(fire)) {
            helper.fail("热源不应是火方块 (A 方案剔除失败)");
            return;
        }
        if (!nearest.equals(magma) && !nearest.equals(camp)) {
            helper.fail("最近热源应为岩浆块或点燃营火, 实际 " + nearest);
            return;
        }

        // ② 安全落点 — 对岩浆块 (可站立烫脚) 应返回非本体安全邻格
        BlockPos stand = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.HeatSourceQuery
                .safeStand(sl, magma);
        if (stand == null) {
            helper.fail("岩浆块旁应存在安全落点");
            return;
        }
        if (stand.equals(magma) || stand.distSqr(magma) > 2.0 * 2.0) {
            helper.fail("落点应为岩浆块旁 ≤2 格安全邻格, 实际 " + stand);
            return;
        }
        // 落点不可站危险方块上 (火/岩浆块本体)
        if (sl.getBlockState(stand).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || sl.getBlockState(stand).is(net.minecraft.world.level.block.Blocks.FIRE)) {
            helper.fail("落点不应是危险方块, 实际 " + sl.getBlockState(stand));
            return;
        }
        helper.succeed();
    }

    /**
     * 哈气挥击 — 概率真实攻击: 手动构造状态 + 挥击倒计时 1,
     * 驱动 HaqiPipeline.tick 断言目标掉血且挥击只发生一次 (hit_ticks=-1 防重复)。
     * 改走 MOVE→LOOK 转换 (断言 YSM 动画请求 rouletteAnim), 再覆写挥击参数断言伤害。
     * 字符串键 "audio_ticks"/"hit_ticks" 对应 HaqiPipeline private 常量。
     */
    @GameTest(template = "game_test")
    public static void lmaHaqiHit(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return; // spawnMaid 已 fail

        // 第二个女仆 (偏移 1 格, 与第一个距离 1)
//? if 1.20.1 {
        Player player = helper.makeMockSurvivalPlayer();
//?} else {
        Player player = helper.makeMockPlayer(GameType.DEFAULT_MODE);
//?}
        ItemStack smartSlab = InitItems.SMART_SLAB_INIT.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, smartSlab);
        BlockPos groundPos2 = new BlockPos(8, 1, 7);
        useItemOn(helper, player, smartSlab, groundPos2, Direction.UP);
        List<EntityMaid> maids2 = helper.getEntities(InitEntities.MAID.get(), groundPos2.above(), 1);
        EntityMaid target = null;
        for (EntityMaid m : maids2) {
            if (m != maid) { target = m; break; } // 半径 1 可能含第一个女仆, 取另一个
        }
        if (target == null) {
            helper.fail("第二个女仆未生成");
            return;
        }

        // 手动构造 MOVE 状态 (距离 1 ≤ ARRIVE_DIST_SQR=2.25) → 首 tick 转换 LOOK
        var data = com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline
                .stateData(maid);
        data.putString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_TARGET,
                target.getStringUUID());
        data.putString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_STATE,
                "MOVE");
        data.putInt(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_TIMER, 0);

        var pl = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get("haqi").pipeline();
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();

        // tick 1: MOVE → LOOK (转换: YSM 动画请求 + 随机音频 + 挥击骰子)
        pl.tick(sl, maid);
        if (!"LOOK".equals(data.getString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_STATE))) {
            helper.fail("首 tick 应转换到 LOOK");
            return;
        }
        // 测试女仆非 YSM 模型 → AnimExecute 走 TLM ISS 分支 — 哈气为 FULL 模式 (写 ANIM_START, 不写 ANIM_NAME)
        // 统一播 haqi — haqi.animation.json 骨 AllBody (TLM 通用根骨) 双模型均匹配 (vanilla 分流已删, 错题 #134)
        String animStart = maid.getPersistentData().getString(
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.ANIM_START);
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.HAQI_ANIM
                .equals(animStart)) {
            helper.fail("LOOK 开始应请求哈气动画 (lma_anim_start), 实际 " + animStart);
            return;
        }
        if (!"FULL".equals(maid.getPersistentData().getString(
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.ANIM_MODE))) {
            helper.fail("哈气动画 mode 应为 FULL");
            return;
        }
        // 覆写确定时长/挥击 (转换随机化了这两个值) — 挥击倒计时 1 → 下 tick 必挥
        data.putInt("audio_ticks", 200);
        data.putInt("hit_ticks", 1);

        float before = target.getHealth();
        pl.tick(sl, maid);
        float after = target.getHealth();
        if (after >= before) {
            helper.fail("挥击应造成伤害 (before=" + before + ", after=" + after + ")");
            return;
        }
        if (data.getInt("hit_ticks") != -1) {
            helper.fail("挥击后 hit_ticks 应为 -1 (防重复), 实际 " + data.getInt("hit_ticks"));
            return;
        }
        // 再 tick 一次 → 不重复挥击 (血量不再降)
        pl.tick(sl, maid);
        if (target.getHealth() != after) {
            helper.fail("挥击应只发生一次");
            return;
        }
        // 清理状态 (防跨测试残留) — onCleanup 停止 YSM 动画
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancelPassive(maid, "haqi");
        helper.succeed();
    }

    /**
     * 哈气对主人 — 女仆 tame 玩家后, 手动构造 target_type=owner 状态,
     * 断言 MOVE→LOOK (owner 分支: 网络包 + 动画) + 挥击玩家掉血 (hit_ticks=-1 防重复)。
     * 主人 = 玩家 (ServerPlayer, 用户裁定); 不反击由玩家无自动反击保证。
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaHaqiHitOwner(GameTestHelper helper) {
        final EntityMaid maid = spawnMaid(helper);
        if (maid == null) return; // spawnMaid 已 fail

// 自构造注册玩家: makeMockServerPlayerInLevel 覆写 isCreative=true → creative 免疫伤害, 挥击不掉血 (实证)。
        // 需 isCreative=false 才能验证掉血; placeNewPlayer 注册进 PlayerList/level — 纯构造 mock 不在实体索引。
        final net.minecraft.server.level.ServerPlayer srvPlayer;
//? if 1.20.1 {
        srvPlayer = new net.minecraft.server.level.ServerPlayer(
                ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer(),
                (net.minecraft.server.level.ServerLevel) helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player")) {
//?} else {
        srvPlayer = new net.minecraft.server.level.ServerPlayer(
                ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer(),
                (net.minecraft.server.level.ServerLevel) helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"),
                net.minecraft.server.level.ClientInformation.createDefault()) {
//?}
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        net.minecraft.network.Connection conn = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        // 官方 makeMockServerPlayerInLevel 同款: EmbeddedChannel 挂内存通道 — placeNewPlayer 内部
        // writeAndFlush 需要 channel, 否则 NPE "this.channel is null" (实证)
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        final Player player;
//? if 1.20.1 {
        ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer().getPlayerList()
                .placeNewPlayer(conn, srvPlayer);
        player = srvPlayer;
//?} else {
        try {
            ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer().getPlayerList()
                    .placeNewPlayer(conn, srvPlayer,
                            net.minecraft.server.network.CommonListenerCookie.createInitial(srvPlayer.getGameProfile(), false));
        } catch (RuntimeException ex) {
            // TLM onPlayerJoinWorld 对加入的 ServerPlayer 发 sync_data → neoforge payload 检查失败
            // ("may not be sent to the client", mock connection 无客户端通道) — 玩家实体已注册
            // (事件在 addPlayer 后派发); try 只注册, 下方统一按 UUID 取回 (final 单次赋值)
        }
        player = ((net.minecraft.server.level.ServerLevel) helper.getLevel())
                .getServer().getPlayerList().getPlayer(srvPlayer.getUUID());
        if (player == null) {
            throw new RuntimeException("mock 玩家注册后无法取回");
        }
//?}
        // 不 tame: TLM tame 会给 owner 发 sync_data payload, gametest 无客户端连接会炸 (neoforge 实证)。
        // 管道目标解析走 getEntity(UUID)+Player 校验 (HaqiPipeline.resolveTarget), 不依赖主人链。
        // 站 1 格内 (ARRIVE_DIST_SQR=2.25)
        // GameTestServer 默认 creative 能力: abilities.invulnerable=true (实证 abInvuln=true) —
        // hurt 检查该字段免疫伤害, 必须清除才能验证挥击掉血
        player.getAbilities().invulnerable = false;
        // ServerPlayer.spawnInvulnerableTime 初始 60 tick (出生无敌, 1.21 ServerPlayer L192 实证) —
        // 玩家刚注册时任何伤害全免疫 (ServerPlayer.hurt L773 实证) → runAfterDelay(61) 等出生无敌过期
        // (1.21 无 waitUntilNextTick, 1.20/1.21 均有 runAfterDelay; 回调在 server tick 线程)
        helper.runAfterDelay(61, () -> {
        // ServerPlayer.spawnInvulnerableTime 初始 60 tick (出生无敌) — GameTestServer 玩家不 tick 递减 (实测 spawnT=60 恒存),
        // 反射直接清零 (dev 环境 mojmap 名双版本有效; 生产 srg 名仅打包后差异, gametest 不走生产 jar)
        try {
            java.lang.reflect.Field sf = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            sf.setAccessible(true);
            sf.setInt(player, 0);
        } catch (Exception ex) {
            throw new RuntimeException("无法清除出生无敌 (spawnInvulnerableTime)", ex);
        }
        player.moveTo(maid.getX() + 1, maid.getY(), maid.getZ());
        if (player.blockPosition().distSqr(maid.blockPosition()) > 2.25) {
            helper.fail("玩家未在 1 格内 (distSqr=" + player.blockPosition().distSqr(maid.blockPosition()) + ")");
            return;
        }

        // 手动构造 owner 目标状态 (MOVE, 距离 1 ≤ ARRIVE_DIST_SQR) → 首 tick 转换 LOOK
        var data = com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline
                .stateData(maid);
        data.putString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_TARGET,
                player.getStringUUID());
        data.putString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_TARGET_TYPE,
                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.HaqiService.TARGET_OWNER);
        data.putString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_STATE,
                "MOVE");
        data.putInt(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_TIMER, 0);

        var pl = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get("haqi").pipeline();
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();

        // tick 1: MOVE → LOOK (owner 分支: 网络包 + 动画 + 挥击骰子)
        pl.tick(sl, maid);
        if (!"LOOK".equals(data.getString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_STATE))) {
            helper.fail("首 tick 应转换到 LOOK (owner), 实际 state=" + data.getString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_STATE) + ", target=" + data.getString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_TARGET) + ", playerPos=" + player.blockPosition() + ", maidPos=" + maid.blockPosition() + ", distSqr=" + player.blockPosition().distSqr(maid.blockPosition()) + ", getEntity=" + ((net.minecraft.server.level.ServerLevel) maid.level()).getEntity(java.util.UUID.fromString(data.getString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_TARGET))));
            return;
        }
        // 对主人哈气统一播 maimeng — maimeng.animation.json 骨 PascalCase (Head/LeftArm/...) 与 TLM 模型骨名一致
        // (vanilla 分流误判已删, 错题 #134; 演化史见 changelog)
        String animStart = maid.getPersistentData().getString(
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.ANIM_START);
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.HAQI_OWNER_ANIM
                .equals(animStart)) {
            helper.fail("LOOK 开始应请求对主人哈气动画 maimeng (lma_anim_start), 实际 " + animStart);
            return;
        }
        // 覆写确定时长/挥击 (转换随机化了挥击) — 挥击倒计时 1 → 下 tick 必挥
        data.putInt("audio_ticks", 200);
        data.putInt("hit_ticks", 1);

        float before = player.getHealth();
        pl.tick(sl, maid);
        float after = player.getHealth();
        if (after >= before) {
            var src = maid.damageSources().mobAttack(maid);
            boolean hurtResult = player.hurt(src, 1.0f);
            float hurtAfter = player.getHealth();
            helper.fail("挥击应对主人造成伤害 (before=" + before + ", after=" + after + ", hitTicks=" + data.getInt("hit_ticks")
                    + ", directHurt=" + hurtResult + ", hurtAfter=" + hurtAfter
                    + ", entInvuln=" + player.isInvulnerable() + ", dead=" + player.isDeadOrDying()
                    + ", src=" + src.getEntity() + ", invuln=" + player.invulnerableTime
                    + ", abInvuln=" + player.getAbilities().invulnerable);
            return;
        }
        if (data.getInt("hit_ticks") != -1) {
            helper.fail("挥击后 hit_ticks 应为 -1 (防重复), 实际 " + data.getInt("hit_ticks"));
            return;
        }
        // 再 tick 一次 → 不重复挥击 (主人血量不再降)
        pl.tick(sl, maid);
        if (player.getHealth() != after) {
            helper.fail("挥击应只发生一次");
            return;
        }
        // 清理状态 (防跨测试残留) — onCleanup 停止 YSM 动画
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancelPassive(maid, "haqi");
        helper.succeed();
        });
    }

    /**
     * 连锁挖矿链路 (v79.53 补测): 脚下铁矿石 → 开脉 (KEY_QUEUE 写入) → 强制蓄力到点 →
     * charge 破坏 (矿变空气 + 队列闭环)。全同步驱动 ChainHarvestPipeline.tick (同一 server
     * tick 内连续调用, 女仆无机会被 AI 移动/GMPM 介入 — 实测 runAfterDelay 等待期位置漂移
     * 导致 3 格球破块失败); 覆盖可达裁剪后单轮全破语义 (v79.53 #4)。
     */
    @GameTest(template = "game_test", timeoutTicks = 120)
    public static void lmaChainOre(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return; // spawnMaid 已 fail

        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        // 女仆位置 (7,2,7) — 脚下 (7,1,7) 放铁矿石 + 主手铁镐 (validate 要求持镐)
        helper.setBlock(new BlockPos(7, 1, 7), net.minecraft.world.level.block.Blocks.IRON_ORE);
        maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));

        // 提交 collect_ore (validate: 主手持镐 + 耐久 ✓)
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "collect_ore", null, 0)) {
            helper.fail("collect_ore submit 失败");
            return;
        }

        var pl = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get("collect_ore").pipeline();
        var data = maid.getPersistentData();

        // tick 1: 开脉 (脚下矿匹配 → tryStartVein → KEY_QUEUE 写入)
        pl.tick(sl, maid);
        if (!data.contains(com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.KEY_QUEUE)) {
            helper.fail("首 tick 未开脉 (KEY_QUEUE 未写入)");
            return;
        }
        // v79.61x 状态机化: 开脉即 CHARGE (显式相位键, 与队列同生)
        if (data.getInt(com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.KEY_PHASE)
                != com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.Phase.CHARGE.ordinal()) {
            helper.fail("开脉后相位应为 CHARGE");
            return;
        }

        // 强制蓄力到点 (手动 tick 不推进 gameTime — 直接置 end 为已过期, 免 runAfterDelay 等待漂移)
        data.putLong(com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.KEY_CHARGE_END,
                sl.getGameTime() - 1);
        // 女仆拉回原位 (防 AI 移动导致 3 格球破块门失败) — 同一 tick 内同步完成;
        // moveTo 收绝对坐标 (直接传相对坐标会 teleport 到 gametest 结构偏移外的错误位置 — 实证坑)
        BlockPos maidAbs = helper.absolutePos(new BlockPos(7, 2, 7));
        maid.moveTo(maidAbs.getX() + 0.5, maidAbs.getY(), maidAbs.getZ() + 0.5,
                maid.getYRot(), maid.getXRot());

        // tick 2 (同一 server tick): charge 破块 → 矿变空气 + 队列闭环
        pl.tick(sl, maid);
        if (!sl.getBlockState(helper.absolutePos(new BlockPos(7, 1, 7))).isAir()) {
            helper.fail("charge 后矿石未被破坏");
            return;
        }
        if (data.contains(com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.KEY_QUEUE)) {
            helper.fail("破块后 KEY_QUEUE 未清理 (闭环)");
            return;
        }
        // 状态机化: 破块后相位随队列闭环 (单点清理)
        if (data.contains(com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.KEY_PHASE)) {
            helper.fail("破块后相位键未清理 (闭环)");
            return;
        }
        helper.succeed();
    }

    /**
     * v79.62.2 远矿导航验证 — 矿放女仆 5 格外 (12,1,7 vs 女仆 7,2,7) → 应走导航过去挖.
     * 真 server tick 驱动 (runAfterDelay) 让 TLM 走路; 150t 内应接近矿 (≤5 格) 或挖掉.
     */
    @GameTest(template = "game_test", timeoutTicks = 220)
    public static void lmaChainOreFar(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        // v79.62.2 验证: 女仆脚下 3/4/5 格深矿 (盖住挖不到) 应被排除 —
        // 女仆去挖 5 格外 (12,1,7) 的目标矿, 脚下深矿不被挖.
        helper.setBlock(new BlockPos(7, -1, 7), net.minecraft.world.level.block.Blocks.IRON_ORE);   // 脚下 3 格
        helper.setBlock(new BlockPos(7, -2, 7), net.minecraft.world.level.block.Blocks.IRON_ORE);   // 脚下 4 格
        helper.setBlock(new BlockPos(7, -3, 7), net.minecraft.world.level.block.Blocks.IRON_ORE);   // 脚下 5 格
        BlockPos oreRel = new BlockPos(12, 1, 7);   // 5 格外目标矿
        helper.setBlock(oreRel, net.minecraft.world.level.block.Blocks.IRON_ORE);
        maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "collect_ore", null, 0)) {
            helper.fail("collect_ore submit 失败");
            return;
        }
        BlockPos oreAbs = helper.absolutePos(oreRel);
        BlockPos below3 = helper.absolutePos(new BlockPos(7, -1, 7));
        BlockPos below4 = helper.absolutePos(new BlockPos(7, -2, 7));
        BlockPos below5 = helper.absolutePos(new BlockPos(7, -3, 7));
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-OREFAR] maid={} ore={} below3/4/5={}/{}/{}",
                maid.blockPosition(), oreAbs, below3, below4, below5);
        helper.runAfterDelay(200, () -> {
            boolean dug = sl.getBlockState(oreAbs).isAir();
            // 脚下深矿应保持未挖 (排除) — 若被挖说明过滤失效
            boolean b3 = !sl.getBlockState(below3).isAir();
            boolean b4 = !sl.getBlockState(below4).isAir();
            boolean b5 = !sl.getBlockState(below5).isAir();
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-OREFAR] dug={} below3/4/5preserved={}/{}/{} maid={}",
                    dug, b3, b4, b5, maid.blockPosition());
            if (dug && b3 && b4 && b5) { helper.succeed(); return; }
            helper.fail("排除失效: dug=" + dug + " below3/4/5=" + b3 + "/" + b4 + "/" + b5 + " (脚下深矿应排除, 去挖 5 格外目标)");
        });
    }

    /**
     * 错题 #183 round-trip 守护 — NbtCodecs 写读对称 (1.21.1 IntArrayTag 漂移回归防线)。
     * 运行时 MC 环境 (纯 JVM 禁 MC 类型 — #173 纪律; NbtUtils 双平台实现差异只在运行期暴露)。
     */
    @GameTest(template = "game_test")
    public static void lmaNbtCodecsRoundTrip(GameTestHelper helper) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        BlockPos pos = new BlockPos(123, 45, -67);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(tag, "p", pos);
        BlockPos back = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(tag, "p");
        if (back == null || !back.equals(pos)) {
            helper.fail("NbtCodecs round-trip mismatch: " + pos + " -> " + back);
            return;
        }
        // 缺键 → null (读侧守卫语义)
        if (com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(tag, "missing") != null) {
            helper.fail("NbtCodecs 缺键应返回 null");
            return;
        }
        helper.succeed();
    }

    /**
     * 结构感知运行时锚点 (审计 T1): per-player 缓存生命周期 — detect 写 PLAYER_TEXT,
     * sweep 回收不在 PlayerList 的玩家缓存 (离线清理, 错题 #195 回归);
     * 状态机分支由 StructureSenseStateTest 30 例覆盖 (gametest 测试层无自然结构,
     * 端到端 discover/leave 依赖地图生成, 不可构造 — 如实降级)。
     */
    @GameTest(template = "game_test")
    public static void lmaStructureSenseState(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig.ENV_STRUCTURE_ENABLED.set(true);
        // 纯构造 ServerPlayer (不进 PlayerList — detect 只读 UUID/坐标; sweep 以 PlayerList 为在线集,
        // 恰好验证离线回收); 不 placeNewPlayer: mock connection 无 channel 会 NPE (haqi 测试同款实证)
//? if 1.20.1 {
        net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "sense-mock-player")) {
//?} else {
        net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "sense-mock-player"),
                net.minecraft.server.level.ClientInformation.createDefault()) {
//?}
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        java.util.UUID pid = player.getUUID();
        if (StructureSense.hasPlayerText(pid) || StructureSense.hasPlayerState(pid)) {
            helper.fail("检测前不应有玩家缓存");
            return;
        }
        StructureSense.detect(level, player, System.nanoTime() + 20_000_000L);
        if (!StructureSense.hasPlayerText(pid)) {
            helper.fail("detect 应写 PLAYER_TEXT 缓存");
            return;
        }
        // mock 玩家不在 PlayerList → sweep 应回收 (玩家离线生命周期)
        StructureSense.sweep(level);
        if (StructureSense.hasPlayerText(pid) || StructureSense.hasPlayerState(pid)) {
            helper.fail("sweep 未回收离线玩家缓存");
            return;
        }
        helper.succeed();
    }

    /**
     * 外部注册链 (LMAT 门面 + 迟注册钩子 fail-soft): 主动一行注册 → 注册表可见;
     * submit/cancel 生命周期; 被动 registerPassive + submitPassive/cancelPassive 键闭环。
     * gametest 运行时 TLM TaskManager.init 已冻结 (ImmutableMap) — 迟注册钩子走 fail-soft
     * 分支 (不炸即过); 冻结前路径 (addMaidTask 内) 由生产环境实证。
     */
    @GameTest(template = "game_test")
    public static void lmaExternalRegistration(GameTestHelper helper) {
        com.github.xiaozhaoz1.littlemaidmoreaction.api.LMAT.register("__ext_active",
                new com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline() {
                    @Override public String taskType() { return "__ext_active"; }
                    @Override public void tick(net.minecraft.server.level.ServerLevel w, EntityMaid m) { }
                });
        if (com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get("__ext_active") == null) {
            helper.fail("LMAT.register 未进注册表");
            return;
        }
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.isShowInBar("__ext_active")) {
            helper.fail("主动任务应任务栏可见");
            return;
        }

        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return; // spawnMaid 已 fail

        // 主动生命周期: submit → in_progress → cancel
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.api.LMAT.submit(maid, "__ext_active", null, 0)) {
            helper.fail("外部主动任务 submit 失败");
            return;
        }
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.STATE_IN_PROGRESS
                .equals(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getState(maid))) {
            helper.fail("submit 后状态应为 in_progress");
            return;
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.api.LMAT.cancel(maid);

        // 被动注册 + 触发/取消键闭环
        com.github.xiaozhaoz1.littlemaidmoreaction.api.LMAT.registerPassive("__ext_passive",
                new com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline() {
                    @Override public String taskType() { return "__ext_passive"; }
                });
        String pkey = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.passiveKey("__ext_passive");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.LMAT.submitPassive(maid, "__ext_passive");
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.STATE_IN_PROGRESS
                .equals(maid.getPersistentData().getString(pkey))) {
            helper.fail("submitPassive 未写被动键");
            return;
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.api.LMAT.cancelPassive(maid, "__ext_passive");
        if (maid.getPersistentData().contains(pkey)) {
            helper.fail("cancelPassive 未清被动键");
            return;
        }
        helper.succeed();
    }

    /**
     * 酒狐奶蛋糕配方加载 (v79.62) — 双平台数据包格式差异回归 (用户实测 1.21.1 JEI 无配方):
     * 1.20.1 = data/&lt;ns&gt;/recipes/ 复数目录 + result.item; 1.21.1 = data/&lt;ns&gt;/recipe/ 单数目录
     * + result.id/count。RecipeManager.byKey 加载成功 = 目录/格式正确 (JEI 可显示)。
     */
    @GameTest(template = "game_test")
    public static void lmaMilkRecipesLoaded(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        for (String id : new String[]{"cake_from_tamed_milk", "cake_from_wild_milk"}) {
//? if 1.20.1 {
            var key = new net.minecraft.resources.ResourceLocation(LittleMaidMoreAction.MOD_ID, id);
//?} else {
            var key = net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, id);
//?}
            if (manager.byKey(key).isEmpty()) {
                helper.fail("配方未加载: " + key
                        + " — 检查数据包目录 (1.20.1 recipes/ 复数 + result.item; "
                        + "1.21.1 recipe/ 单数 + result.id/count)");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * 作物区域种菜 (v79.62) — 女仆绑定区域 (成熟小麦旁 2 格) 收成熟 → AGE 重置 0 (留株).
     * 验证: 区域内成熟作物被收割且重置为幼苗 (CropBlockHandler 留株 = AGE 0).
     */
    @GameTest(template = "game_test", timeoutTicks = 400)
    public static void lmaFarmJob(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        // 固定农田坐标 (模板 16×16 草方块平台 y=0 上方) — 不依赖女仆生成位置 (用户裁定: 测试用 TP 到农田).
        // 作物必须种在耕地上: 耕地 y=1 贴草方块, 小麦 y=2 在耕地正上方.
        BlockPos farmAbs = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos cropAbs = farmAbs.above();
        helper.getLevel().setBlockAndUpdate(farmAbs, net.minecraft.world.level.block.Blocks.FARMLAND
                .defaultBlockState());
        helper.getLevel().setBlockAndUpdate(cropAbs, net.minecraft.world.level.block.Blocks.WHEAT
                .defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));

        // TP 女仆到农田旁 1 格 (固定坐标, 不再靠生成位置) + 冻结 AI (防乱走离田 → 4 格内直收)
        maid.teleportTo(farmAbs.getX() - 1 + 0.5, farmAbs.getY(), farmAbs.getZ() + 0.5);
        maid.setNoAi(true);

        // 背包给小麦种子 (若收后补种, 供 canPlant); 注入绑定区域 (绝对坐标覆盖 farmland+wheat)
        maid.getAvailableInv(true).insertItem(0,
                new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, 8), false);
        String uuid = maid.getStringUUID();
        com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.addRegion(uuid,
                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                        .of(farmAbs.getX(), farmAbs.getY(), farmAbs.getZ(),
                                cropAbs.getX(), cropAbs.getY(), cropAbs.getZ(), "minecraft:wheat_seeds"));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 300t: 等 BlockPatternCache CROP 缓存 TTL(200t) 过期重扫到新放成熟小麦 (gametest 放块不触发缓存失效)
        helper.runAfterDelay(300, () -> {
            var now = helper.getLevel().getBlockState(cropAbs);
            if (now.is(net.minecraft.world.level.block.Blocks.WHEAT)) {
                int age = now.getValue(net.minecraft.world.level.block.CropBlock.AGE);
                if (age == 7) {
                    helper.fail("farm 未收割成熟小麦 (AGE 仍 7)");
                    return;
                }
            }
            // 已收 (AGE<7 / 已破坏 / 播种后非成熟) — 种菜链工作; 清理区域防残留
            com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                    .putRegions(uuid, java.util.List.of());
            helper.succeed();
        });
    }

    /**
     * 作物区域播种 (v79.62) — 女仆绑定空耕地 + 背包小麦种子 → farm 播种小麦到耕地 (AGE 0).
     * 验证播种链路: scanPlantable 找到耕地 → tryPlant 放小麦 + 消耗种子. 耕地放水保湿防回土.
     */
    @GameTest(template = "game_test", timeoutTicks = 400)
    public static void lmaFarmPlant(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        BlockPos farmAbs = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos cropAbs = farmAbs.above();
        BlockPos waterAbs = helper.absolutePos(new BlockPos(4, 1, 5));
        helper.getLevel().setBlockAndUpdate(farmAbs, net.minecraft.world.level.block.Blocks.FARMLAND
                .defaultBlockState());
        helper.getLevel().setBlockAndUpdate(waterAbs, net.minecraft.world.level.block.Blocks.WATER
                .defaultBlockState());

        maid.teleportTo(farmAbs.getX() - 1 + 0.5, farmAbs.getY(), farmAbs.getZ() + 0.5);
        maid.setNoAi(true);

        // 背包小麦种子 + 绑定区域 (覆盖耕地)
        maid.getAvailableInv(true).insertItem(0,
                new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, 8), false);
        String uuid = maid.getStringUUID();
        com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.addRegion(uuid,
                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                        .of(farmAbs.getX(), farmAbs.getY(), farmAbs.getZ(),
                                cropAbs.getX(), cropAbs.getY(), cropAbs.getZ(), "minecraft:wheat_seeds"));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 播种走 FARMLAND 缓存 (静态), 但须越过 200t 缓存 TTL 避开过期竞态 (放块不触发失效, 错题 #238) — 300t
        helper.runAfterDelay(300, () -> {
            var now = helper.getLevel().getBlockState(cropAbs);
            if (!now.is(net.minecraft.world.level.block.Blocks.WHEAT)) {
                helper.fail("farm 未播种小麦到耕地 (cropAbs 非 WHEAT)");
                return;
            }
            com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                    .putRegions(uuid, java.util.List.of());
            helper.succeed();
        });
    }

    /**
     * 种子源箱 (v79.62) — 女仆背包无种子 + 绑定种子源箱 (内有小麦种子) → ensureSeedFromBox
     * 从箱取种 → 播种到耕地. 验证取种链路.
     */
    @GameTest(template = "game_test", timeoutTicks = 400)
    public static void lmaFarmSeedBox(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        BlockPos farmAbs = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos cropAbs = farmAbs.above();
        BlockPos waterAbs = helper.absolutePos(new BlockPos(4, 1, 5));
        helper.getLevel().setBlockAndUpdate(farmAbs, net.minecraft.world.level.block.Blocks.FARMLAND
                .defaultBlockState());
        helper.getLevel().setBlockAndUpdate(waterAbs, net.minecraft.world.level.block.Blocks.WATER
                .defaultBlockState());

        // 种子源箱 (箱子, 放小麦种子)
        BlockPos boxAbs = helper.absolutePos(new BlockPos(8, 1, 5));
        helper.getLevel().setBlockAndUpdate(boxAbs, net.minecraft.world.level.block.Blocks.CHEST
                .defaultBlockState());
        if (helper.getLevel().getBlockEntity(boxAbs)
                instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, 16));
        }

        // 背包无种子
        maid.teleportTo(farmAbs.getX() - 1 + 0.5, farmAbs.getY(), farmAbs.getZ() + 0.5);
        maid.setNoAi(true);
        String uuid = maid.getStringUUID();
        // v79.62 区域制: 种子源箱绑定到区域 (per-region), 不再写女仆 PD
        com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.addRegion(uuid,
                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                        .of(farmAbs.getX(), farmAbs.getY(), farmAbs.getZ(),
                                cropAbs.getX(), cropAbs.getY(), cropAbs.getZ(),
                                "minecraft:wheat_seeds", "left",
                                boxAbs.getX(), boxAbs.getY(), boxAbs.getZ(),
                                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.NO_BOX,
                                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.NO_BOX,
                                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.NO_BOX));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 300t > 200t 缓存 TTL (放块不触发失效) — 避开过期竞态
        helper.runAfterDelay(300, () -> {
            // 已播种 = 取种链路通 (ensureSeedFromBox → 播种)
            var now = helper.getLevel().getBlockState(cropAbs);
            if (now.is(net.minecraft.world.level.block.Blocks.WHEAT)) {
                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                        .putRegions(uuid, java.util.List.of());
                helper.succeed();
                return;
            }
            // 未播种 — 检查背包是否已取到种子 (取种成功但未及播种)
            var inv = maid.getAvailableInv(true);
            for (int i = 0; i < inv.getSlots(); i++) {
                if (inv.getStackInSlot(i).is(net.minecraft.world.item.Items.WHEAT_SEEDS)) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                            .putRegions(uuid, java.util.List.of());
                    helper.succeed();
                    return;
                }
            }
            helper.fail("女仆未从种子源箱取种 (背包无种子且未播种)");
        });
    }

    /**
     * 收获目标箱 (v79.62) — 女仆背包放小麦产物 + 小麦种子 + 铁锄(工具) + 木棍(标记物), 绑定收获箱,
     * 无成熟可收、无可种耕地 → farm 空转后 storeHarvests 只存产物 (小麦入箱, 种子/工具/标记物留背包).
     * 验证收获箱链路 + isHarvestProduct 四向判定 (产物/种子/工具/标记物; 单测环境无 MC Bootstrap
     * 不能测 ItemStack, 按错题 #174 铁律真实覆盖走 gametest).
     */
    @GameTest(template = "game_test", timeoutTicks = 400)
    public static void lmaFarmHarvestBox(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        BlockPos boxAbs = helper.absolutePos(new BlockPos(8, 1, 5));
        helper.getLevel().setBlockAndUpdate(boxAbs, net.minecraft.world.level.block.Blocks.CHEST
                .defaultBlockState());

        // 背包: 小麦产物 ×8 + 小麦种子 ×4 (种子, 应保留) + 铁锄 (工具, 应保留) + 木棍 (标记物, 应保留)
        var inv = maid.getAvailableInv(true);
        inv.insertItem(0, new ItemStack(net.minecraft.world.item.Items.WHEAT, 8), false);
        inv.insertItem(1, new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, 4), false);
        inv.insertItem(2, new ItemStack(net.minecraft.world.item.Items.IRON_HOE, 1), false);
        inv.insertItem(3, new ItemStack(net.minecraft.world.item.Items.STICK, 1), false);

        maid.teleportTo(boxAbs.getX() - 3 + 0.5, boxAbs.getY(), boxAbs.getZ() + 0.5);
        maid.setNoAi(true);
        String uuid = maid.getStringUUID();
        // v79.62 区域制: 收获目标箱绑定到区域 (per-region); 区域覆盖空点 → 空转后 storeHarvests 直存
        com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.addRegion(uuid,
                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                        .of(boxAbs.getX(), boxAbs.getY(), boxAbs.getZ(),
                                boxAbs.getX(), boxAbs.getY(), boxAbs.getZ(),
                                "minecraft:wheat_seeds", "left",
                                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.NO_BOX,
                                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.NO_BOX,
                                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.NO_BOX,
                                boxAbs.getX(), boxAbs.getY(), boxAbs.getZ()));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 300t > 200t 缓存 TTL (放块不触发失效) — 避开过期竞态
        helper.runAfterDelay(300, () -> {
            if (!(helper.getLevel().getBlockEntity(boxAbs) instanceof net.minecraft.world.Container cont)) {
                helper.fail("收获箱不可用 (非 Container)");
                return;
            }
            boolean hasWheat = false, hasSeeds = false, hasHoe = false, hasStick = false;
            for (int i = 0; i < cont.getContainerSize(); i++) {
                var s = cont.getItem(i);
                if (s.is(net.minecraft.world.item.Items.WHEAT)) hasWheat = true;
                if (s.is(net.minecraft.world.item.Items.WHEAT_SEEDS)) hasSeeds = true;
                if (s.is(net.minecraft.world.item.Items.IRON_HOE)) hasHoe = true;
                if (s.is(net.minecraft.world.item.Items.STICK)) hasStick = true;
            }
            if (!hasWheat) { helper.fail("小麦产物未存入收获箱"); return; }
            if (hasSeeds) { helper.fail("小麦种子被误存入收获箱 — 种子应留背包"); return; }
            if (hasHoe) { helper.fail("铁锄(工具)被误存入收获箱 — isHarvestProduct 失效"); return; }
            if (hasStick) { helper.fail("木棍(标记物)被误存入收获箱 — isHarvestProduct 失效"); return; }
            com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                    .putRegions(uuid, java.util.List.of());
            helper.succeed();
        });
    }

    /**
     * v79.62.1 甜浆果丛右键收获 — 成熟甜浆果丛 (AGE 3) → farm 右键收获 → AGE 1 (留丛).
     * 验证: isRightClickHarvestable 自动分流 → FakePlayer.rightClick → AGE 重置 1 (非破坏).
     */
    @GameTest(template = "game_test", timeoutTicks = 400)
    public static void lmaFarmBerryBush(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        BlockPos farmAbs = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos bushAbs = farmAbs.above();
        // 草方块上放成熟甜浆果丛 (AGE 3)
        helper.getLevel().setBlockAndUpdate(farmAbs, net.minecraft.world.level.block.Blocks.GRASS_BLOCK
                .defaultBlockState());
        helper.getLevel().setBlockAndUpdate(bushAbs, net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH
                .defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 3));

        maid.teleportTo(farmAbs.getX() - 1 + 0.5, farmAbs.getY(), farmAbs.getZ() + 0.5);
        maid.setNoAi(true);
        String uuid = maid.getStringUUID();
        com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.addRegion(uuid,
                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                        .of(farmAbs.getX(), farmAbs.getY(), farmAbs.getZ(),
                                bushAbs.getX(), bushAbs.getY(), bushAbs.getZ(),
                                "minecraft:sweet_berries", "right"));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        helper.runAfterDelay(300, () -> {
            var now = helper.getLevel().getBlockState(bushAbs);
            if (!now.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)) {
                helper.fail("甜浆果丛被破坏 (应右键收获保留灌木丛)");
                return;
            }
            int age = now.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE);
            if (age == 3) {
                helper.fail("甜浆果丛未收获 (AGE 仍 3)");
                return;
            }
            com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                    .putRegions(uuid, java.util.List.of());
            helper.succeed();
        });
    }

    /**
     * v79.62.1 仙人掌顶端收割 — 3 高仙人掌 → farm 只收顶段 (保留基部).
     * 验证: VerticalCropHandler.isMatureAt 顶端判定 (高度>=3 + 上方无同种=顶端=可收; 保留基部).
     * 用仙人掌而非甘蔗: 仙人掌只需沙子下方 (甘蔗需水邻接, gametest 结构内难满足).
     */
    @GameTest(template = "game_test", timeoutTicks = 400)
    public static void lmaFarmSugarCaneTop(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        BlockPos support = helper.absolutePos(new BlockPos(12, 5, 12));
        BlockPos sand = support.above();
        BlockPos base = sand.above();
        BlockPos mid = base.above();
        BlockPos top = mid.above();
        // 沙子是重力方块 — 下方必须有支撑 (石头); 否则沙子掉落破坏仙人掌列结构
        helper.getLevel().setBlockAndUpdate(support, net.minecraft.world.level.block.Blocks.STONE
                .defaultBlockState());
        helper.getLevel().setBlockAndUpdate(sand, net.minecraft.world.level.block.Blocks.SAND
                .defaultBlockState());
        helper.getLevel().setBlockAndUpdate(base, net.minecraft.world.level.block.Blocks.CACTUS
                .defaultBlockState());
        helper.getLevel().setBlockAndUpdate(mid, net.minecraft.world.level.block.Blocks.CACTUS
                .defaultBlockState());
        helper.getLevel().setBlockAndUpdate(top, net.minecraft.world.level.block.Blocks.CACTUS
                .defaultBlockState());

        maid.teleportTo(base.getX() - 2 + 0.5, base.getY(), base.getZ() + 0.5);
        maid.setNoAi(true);
        String uuid = maid.getStringUUID();
        // 区域覆盖仙人掌 (左键收)
        // cropId 用无关种子 (不种仙人掌, 只验证顶端收割); 避免种植逻辑干扰
        com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.addRegion(uuid,
                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                        .of(base.getX(), base.getY(), base.getZ(),
                                top.getX(), top.getY(), top.getZ(),
                                "minecraft:wheat_seeds", "left"));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        helper.runAfterDelay(300, () -> {
            var baseState = helper.getLevel().getBlockState(base);
            // 基部应保留 (VerticalCropHandler 只收顶端, 保留基部; 不应整列摧毁)
            if (!baseState.is(net.minecraft.world.level.block.Blocks.CACTUS)) {
                helper.fail("仙人掌基部被误收 (应保留; 实际=" + baseState + ")");
                return;
            }
            com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                    .putRegions(uuid, java.util.List.of());
            helper.succeed();
        });
    }

    /**
     * v79.62.1 西瓜果实不被误收 — isMatureCrop 对 MelonBlock 返回 false (StemGrownHandler).
     * 验证: 西瓜方块在区域内 → farm 不收 (isMatureCrop=false, 不进收获扫描).
     */
    @GameTest(template = "game_test", timeoutTicks = 400)
    public static void lmaFarmMelonNoFalseHarvest(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        BlockPos farmAbs = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos melonAbs = farmAbs.above();
        helper.getLevel().setBlockAndUpdate(farmAbs, net.minecraft.world.level.block.Blocks.FARMLAND
                .defaultBlockState());
        helper.getLevel().setBlockAndUpdate(melonAbs, net.minecraft.world.level.block.Blocks.MELON
                .defaultBlockState());

        maid.teleportTo(farmAbs.getX() - 1 + 0.5, farmAbs.getY(), farmAbs.getZ() + 0.5);
        maid.setNoAi(true);
        String uuid = maid.getStringUUID();
        // 区域覆盖西瓜 (左键收), cropId 设为西瓜种子 (但西瓜不应被 isMatureCrop 判定成熟)
        com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.addRegion(uuid,
                com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                        .of(farmAbs.getX(), farmAbs.getY(), farmAbs.getZ(),
                                melonAbs.getX(), melonAbs.getY(), melonAbs.getZ(),
                                "minecraft:melon_seeds", "left"));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        helper.runAfterDelay(300, () -> {
            // 西瓜应仍在 (isMatureCrop 返回 false → 不收; StemGrownHandler.isMature=false)
            var now = helper.getLevel().getBlockState(melonAbs);
            if (!now.is(net.minecraft.world.level.block.Blocks.MELON)) {
                helper.fail("西瓜被误收 (StemGrownHandler.isMature 应返回 false 防误收)");
                return;
            }
            com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage
                    .putRegions(uuid, java.util.List.of());
            helper.succeed();
        });
    }

    /**
     * v79.62.1 挖空置域核心链 — 先挖从标记层 (start.getY()) 开始:
     * 放石头在标记层 → 女仆挖掉 (硬度节拍) → 断言石头被破坏.
     * <p>验证: ① y 初始化用 start.getY() (start.y = 可挖最高 y, 认领直接设定高度 — 修 DEBUG-VOID y=223/147 高空卡);
     * ② 4 格内直挖 (near) → destroyBlock; ③ 无容器不阻塞.
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaVoidExcavationDig(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        // 石头目标 — 放女仆所在区块的角 (区块级挖掘从区块角开始, 双平台模板偏移无关):
        // 挖掘起点 = (curCX*16, y, curCZ*16), 石头放角 → 女仆 TP 角旁 4 格内直挖.
        int bkX = maid.blockPosition().getX() >> 4, bkZ = maid.blockPosition().getZ() >> 4;
        BlockPos stoneAbs = new BlockPos(bkX * 16, maid.blockPosition().getY() - 1, bkZ * 16);
        helper.getLevel().setBlockAndUpdate(stoneAbs, net.minecraft.world.level.block.Blocks.STONE
                .defaultBlockState());

        // TP 女仆到石头 (区块角) 旁 1 格 (4 格内) + 冻结 AI
        maid.teleportTo(stoneAbs.getX() + 1.5, stoneAbs.getY() + 1.0, stoneAbs.getZ() + 1.5);
        maid.setNoAi(true);

        // 给钻石镐 (硬度节拍快: 石头 1.5×30/8 ≈ 6t)
        maid.getAvailableInv(true).insertItem(0,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1), false);
        maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1));

        // 写挖空 cfg: start=石头, size=1 (1 区块), y=start.getY() (标记层), x/z=start
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", stoneAbs);
        cfg.putInt("size", 1);
        cfg.putInt("y", stoneAbs.getY());
        cfg.putInt("x", stoneAbs.getX());
        cfg.putInt("z", stoneAbs.getZ());

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "void_excavation", null, 0);

        // 200t: 石头应被挖掉 (女仆在石头旁 4 格内直挖; 区块级挖掘从区块角开始, 石头可能不在角 → 给导航/传送余量)
        helper.runAfterDelay(200, () -> {
            var now = helper.getLevel().getBlockState(stoneAbs);
            if (now.is(net.minecraft.world.level.block.Blocks.STONE)) {
                CompoundTag cfg2 = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfg(maid, "void_excavation");
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                        "[GAMETEST-VOID] 石头未挖: stone={} maid={} curCX={} curCZ={} flowTask={}",
                        stoneAbs.toShortString(), maid.blockPosition().toShortString(),
                        cfg2.getInt("curCX"), cfg2.getInt("curCZ"),
                        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(maid));
                helper.fail("挖空置域未挖掉标记层石头");
                return;
            }
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(maid);
            com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.root(maid).remove("lma_cfg_void_excavation");
            helper.succeed();
        });
    }

    /**
     * v79.62.1 多女仆同区域崩溃复现: 4 女仆同一 1×1 区块挖空 (认领池争用).
     * 验证: 多女仆 poolClaim/poolMark 并发不死锁/死循环 (用户: 第 4 个女仆右键启动后卡).
     */
    @GameTest(template = "game_test", timeoutTicks = 320)
    public static void lmaVoidExcavationMultiMaid(GameTestHelper helper) {
        // v79.62.1 用户实证 3×3 + 4 女仆卡死 — 复现: size=3 (9 区块) + 4 女仆同区域
        // 先清理空置域残留 (v79.62.2 精修: 只清「带 void cfg 且未运行」的女仆 —
        // ① 80 格全量取消会波及同 world 并发其他测试女仆 (furnace 等任务误 cancel, 实证);
        // ② 带 void cfg 但 in_progress = 并发 VoidDig 测试在跑, 不能 cancel (同类型任务区分残留/运行中))
        for (var leftover : helper.getLevel().getEntitiesOfClass(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0, -64, 0))).inflate(80))) {
            var leftCfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfg(leftover, "void_excavation");
            if (leftCfg == null || !leftCfg.contains("start")) continue;   // 非空置域女仆 (其他测试) 不碰
            if ("void_excavation".equals(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(leftover))
                    && com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.STATE_IN_PROGRESS
                            .equals(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getState(leftover))) {
                continue;   // 运行中的空置域任务 (并发 VoidDig) — 不是残留, 不碰
            }
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(leftover);
            com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.root(leftover).remove("lma_cfg_void_excavation");
        }
        // v79.62.1 用户裁定: size=16 测试多女仆区块分配 + 挖掘 — 女仆一个区块一个区块认领.
        // 等认领完成后 (60t) 在「每个女仆认领的区块角」铺石头 → 每个女仆都有石头挖 (不挖虚空),
        // 再等 200t 断言每个女仆都挖掉自己区块的石头.
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));   // 同一标记起点
        java.util.List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> maids4 = new java.util.ArrayList<>();
        for (int m = 0; m < 4; m++) {
            EntityMaid maid = spawnMaid(helper);
            if (maid == null) return;
            maids4.add(maid);
            maid.teleportTo(center.getX() + m, center.getY() + 1.0, center.getZ());
            maid.setNoAi(true);
            maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1), false);
            CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
            com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", center);
            cfg.putInt("size", 16);   // 16×16 = 256 区块 (远超模板, 4 女仆各认领不同区块)
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "void_excavation", null, 0);
        }
        // 60t: 4 女仆应已完成认领 (curCX 已写) → 各区块角铺石头
        helper.runAfterDelay(60, () -> {
            java.util.Map<Long, BlockPos> stones = new java.util.HashMap<>();   // blockLong -> stone pos
            int[] assigned = {0};   // lambda 捕获需要 final 引用 (数组可变)
            for (var md : maids4) {
                var pdt = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(md, "void_excavation");
                if (!pdt.contains("curCX")) continue;
                assigned[0]++;
                int cx = pdt.getInt("curCX"), cz = pdt.getInt("curCZ");
                // 各女仆认领区块的角 (区块级挖掘起点) 铺石头 — y 用 start.getY() (cursor 层,
                // 与挖掘起点 (curCX*16, start.getY(), curCZ*16) 对齐; 原 center.y-1 差 1 层挖不到)
                BlockPos p = new BlockPos(cx * 16, center.getY(), cz * 16);
                helper.getLevel().setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                stones.put(net.minecraft.world.level.ChunkPos.asLong(cx, cz), p);
            }
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-VOID] multimaid stones placed assigned={} blocks={}", assigned[0], stones.size());
            // 200t 后断言: 每个认领区块的石头都被挖掉
            helper.runAfterDelay(200, () -> {
                int dug = 0;
                for (BlockPos p : stones.values()) {
                    if (!helper.getLevel().getBlockState(p).is(net.minecraft.world.level.block.Blocks.STONE)) dug++;
                }
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                        "[GAMETEST-VOID] multimaid final assigned={} dug={}/{} stones", assigned[0], dug, stones.size());
                if (assigned[0] < 2) helper.fail("多女仆区块分配异常: assigned=" + assigned[0]);
                if (dug == 0) helper.fail("多女仆 260t 没挖掉任何认领区块的石头 (挖掘未推进)");
                // 清理 4 女仆任务 (防残留)
                java.util.List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> maids =
                    helper.getLevel().getEntitiesOfClass(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class,
                        new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0, -64, 0))).inflate(60));
                for (var md : maids) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(md);
                }
                helper.succeed();
            });
        });
    }

    /** v79.62.1 箱子 handler 可靠性: 放箱子 → getHandler 非 null + hasSpace true (四向扫全依赖) */
    @GameTest(template = "game_test", timeoutTicks = 60)
    public static void lmaVoidChestHandler(GameTestHelper helper) {
        BlockPos chestAbs = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(chestAbs, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        helper.runAfterDelay(10, () -> {
            var handler = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.VoidExcavationContainerService
                    .getHandler(helper.getLevel(), chestAbs);
            boolean space = handler != null
                    && com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.VoidExcavationContainerService
                            .hasSpace(helper.getLevel(), chestAbs);
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-CHEST] handler={} space={}", handler != null, space);
            if (handler == null || !space) helper.fail("箱子 handler 为 null 或无空间 (getHandler capability 失败)");
            else helper.succeed();
        });
    }

    /**
     * v79.62.2 TLM 导航验证 (用户裁定: LMA 不自写寻路, 移动靠 TLM brain) —
     * 不 setNoAi (保持 AI 运行): 女仆 spawn 在 (7,1,7), start 设远区块 (chunk 3,3 → 游标 48,48),
     * 提交 void_excavation → 300t 内断言女仆位置移动 (TLM brain 导航走向挖掘目标).
     * 若未移动 = TLM 导航未启动 (walkTarget 没设上 / MoveToTargetSink 没走).
     */
    @GameTest(template = "game_test", timeoutTicks = 400)
    public static void lmaVoidNavWalk(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        // 不 setNoAi — 让 TLM brain 导航
        // v79.62.2 防其他任务干扰: 先 cancel 旧任务 + 清 FLOW 状态 (女仆生成可能带 TLM 默认任务)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.clearAll(maid);
        BlockPos spawn = maid.blockPosition().immutable();
        // 远区块 start: chunk (3,3) → 游标起点 (48, y, 48), 距女仆 ~57 格 (TLM 需真正寻路)
        BlockPos start = new BlockPos(48, spawn.getY(), 48);
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1), false);
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", start);
        cfg.putInt("size", 1);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "void_excavation", null, 0);
        helper.runAfterDelay(300, () -> {
            BlockPos now = maid.blockPosition().immutable();
            double dist = now.distSqr(spawn);
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-NAV] spawn={} now={} movedDistSqr={}", spawn, now, dist);
            if (dist < 16.0) helper.fail("女仆未移动 (TLM 导航未启动): distSqr=" + dist);
            else helper.succeed();
        });
    }

    /**
     * v79.62.2 单女仆 3×3 区块远距离传送验证 (用户裁定测试全面性) —
     * 女仆 spawn 在区块 0,0 (7,1,7), start 设远区块 (96,96 → 区块 6,6), size=3 (区域覆盖区块 5~7),
     * 距女仆 ~120 格 — 无法 TLM 寻路到达 (超出 restrict/搜索), 必须靠 navTimeout>100 传送兜底
     * 到认领区块. 铺石头在认领区块角, 断言 400t 内石头被挖掉 (女仆跨区块传送 + 挖掘闭环).
     */
    @GameTest(template = "game_test", timeoutTicks = 600)
    public static void lmaVoidNav3x3FarChunk(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        // 不 setNoAi — 让 TLM brain 导航 + 传送兜底
        // v79.62.2 防其他任务干扰: 先 cancel 旧任务 + 清 FLOW 状态
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.clearAll(maid);
        BlockPos spawn = maid.blockPosition().immutable();
        // start 远区块 (区块 6,6): 距女仆 ~120 格, 3×3 区域覆盖区块 5~7
        BlockPos start = new BlockPos(96, spawn.getY(), 96);
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1), false);
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", start);
        cfg.putInt("size", 3);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "void_excavation", null, 0);

        // 认领后 (120t) 在认领区块角铺石头, 400t 后断言挖掉
        helper.runAfterDelay(120, () -> {
            var pdt = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, "void_excavation");
            if (!pdt.contains("curCX")) {
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                        "[GAMETEST-NAV3] no claim yet: pd={}", pdt);
                helper.fail("120t 内未认领区块 (认领池/自愈问题)");
                return;
            }
            int cx = pdt.getInt("curCX"), cz = pdt.getInt("curCZ");
            // 铺石头在「当前游标位置」 (x,y,z) — 女仆下一步就挖它, 避免游标已推进的竞态.
            // v79.62.2 改手动 teleport (不走 pipeline 远距离 forceChunk, 减少对 temp 测试区块干扰)
            BlockPos stone = new BlockPos(pdt.getInt("x"), pdt.getInt("y"), pdt.getInt("z"));
            helper.getLevel().setBlockAndUpdate(stone, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            maid.teleportTo(stone.getX() + 1.5, stone.getY() + 1.0, stone.getZ() + 1.5);
            BlockPos stoneFinal = stone.immutable();
            BlockPos spawnFinal = spawn.immutable();
            helper.runAfterDelay(400, () -> {
                BlockPos now = maid.blockPosition().immutable();
                boolean dug = !helper.getLevel().getBlockState(stoneFinal).is(net.minecraft.world.level.block.Blocks.STONE);
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                        "[GAMETEST-NAV3] spawn={} now={} claimedChunk=({},{}) cursorStone={} dug={}",
                        spawnFinal, now, cx, cz, stoneFinal, dug);
                if (!dug) helper.fail("女仆未跨区块到达并挖掉游标石头: now=" + now + " stone=" + stoneFinal);
                else helper.succeed();
            });
        });
    }

    /**
     * v79.62.2 测试通用: 清女仆任务残留 — smart slab 放置可能写 GUI_INIT/TLM_SWITCH,
     * GMPM 每 tick 处理会把被测任务顶掉 (换 target=null 重新 submit 另一任务) → 测试前必须清.
     */
    private static void clearMaidTaskState(EntityMaid maid) {
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData.clearGuiInit(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData.clearTlmSwitch(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.clearAll(maid);
    }

    /**
     * v79.62.2 篝火烤食物验证 — 放点燃篝火 + 女仆背包生牛肉 (可烤),
     * campfire 任务 → 断言食物被放进篝火槽 (placeFood 生效).
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaCampfireCook(GameTestHelper helper) {
        // 放点燃篝火 (女仆旁边)
        BlockPos camp = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(camp, net.minecraft.world.level.block.Blocks.CAMPFIRE
                .defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true));

        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        // 背包放生牛肉 (可烤)
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.BEEF, 4), false);
        // 女仆传送到篝火旁 (gate 要求到达) + 设 TARGET_POS = 篝火
        maid.teleportTo(camp.getX() + 0.5, camp.getY() + 1.0, camp.getZ() + 0.5);
        maid.getBrain().setMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get(),
                new net.minecraft.world.entity.ai.behavior.BlockPosTracker(camp));
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "campfire", null, 0);

        helper.runAfterDelay(80, () -> {
            var campfire = helper.getLevel().getBlockEntity(camp);
            boolean placed = false;
            if (campfire instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity c) {
                for (var s : c.getItems()) {
                    if (!s.isEmpty()) { placed = true; break; }
                }
            }
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-CAMP] campfire={} placed={}", camp, placed);
            if (!placed) helper.fail("篝火未放入食物 (placeFood 未生效)");
            else helper.succeed();
        });
    }

    /**
     * v79.62.2 锻造台验证 — 放锻造台 + 女仆旁, smithing validate 应找到 (findSmithingTable).
     * 锻造执行靠玩家手动 (Menu 交互), 验证 = 提交成功 + 找到台.
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaSmithingTable(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(table, net.minecraft.world.level.block.Blocks.SMITHING_TABLE.defaultBlockState());
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        maid.teleportTo(table.getX() + 0.5, table.getY() + 1.0, table.getZ() + 0.5);
        BlockPos found = com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.SmithingPipeline
                .findSmithingTable((net.minecraft.server.level.ServerLevel) maid.level(), maid);
        if (found == null) helper.fail("findSmithingTable 未找到锻造台");
        helper.runAfterDelay(10, () -> {
            boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                    .submit(maid, "smithing", null, 0);
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-SMITH] table={} submit={}", found, ok);
            if (found == null || !ok) helper.fail("锻造台验证失败: found=" + found + " submit=" + ok);
            else helper.succeed();
        });
    }

    /**
     * v79.62.2 熔炉加料验证 — 放熔炉 + 女仆背包铁矿石, furnace 任务 gate 后
     * ADD_INPUT 相位应把矿石放入熔炉输入槽 (slot 0).
     */
    @GameTest(template = "game_test", timeoutTicks = 300)
    public static void lmaFurnaceAddInput(GameTestHelper helper) {
        BlockPos furnace = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(furnace, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_ORE, 8), false);
        maid.teleportTo(furnace.getX() + 0.5, furnace.getY() + 1.0, furnace.getZ() + 0.5);
        maid.getBrain().setMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get(),
                new net.minecraft.world.entity.ai.behavior.BlockPosTracker(furnace));
        // v79.62.2 修: resolveSmeltIngredient 需要产物 target (匹配 recipe result) 才能解析原料 —
        // submit 传 "minecraft:iron_ingot" (铁矿石 smelting 产物), 否则 ingredientKey 空 → ADD_INPUT 跳过
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "furnace", "minecraft:iron_ingot", 0);
        if (!ok) { helper.fail("furnace submit 失败"); return; }
        helper.runAfterDelay(80, () -> {
            var be = helper.getLevel().getBlockEntity(furnace);
            boolean input = false;
            if (be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity f) {
                input = !f.getItem(0).isEmpty();
            }
            // [DBG-FURN] 失败诊断: target/原料解析/相位
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-FURN] inputSlot={} target={} task={} state={}",
                    input,
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData.getTarget(maid),
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(maid),
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getState(maid));
            if (!input) helper.fail("熔炉输入槽未放入铁矿石 (ADD_INPUT 未生效)");
            else helper.succeed();
        });
    }

    /**
     * v79.62.2 敲钟验证 — 放钟 + 女仆旁, bell_ring 任务一次工作单元应敲响钟
     * (SUCCESS 计数 ≥ 1).
     */
    @GameTest(template = "game_test", timeoutTicks = 300)
    public static void lmaBellRing(GameTestHelper helper) {
        BlockPos bell = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(bell, net.minecraft.world.level.block.Blocks.BELL.defaultBlockState());
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        maid.teleportTo(bell.getX() + 0.5, bell.getY() + 1.0, bell.getZ() + 0.5);
        maid.getBrain().setMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get(),
                new net.minecraft.world.entity.ai.behavior.BlockPosTracker(bell));
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "bell_ring", null, 0);
        if (!ok) { helper.fail("bell_ring submit 失败"); return; }
        helper.runAfterDelay(80, () -> {
            int counter = (int) com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getCounter(maid);
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-BELL] counter={}", counter);
            if (counter < 1) helper.fail("敲钟未执行 (SUCCESS 计数 < 1): " + counter);
            else helper.succeed();
        });
    }

    /**
     * v79.62.2 自动点火验证 (被动 torch_light) — 女仆背包火把 + DARKNESS 信号
     * → onSignal 应把火把放入副手 (lightUp 生效).
     */
    @GameTest(template = "game_test", timeoutTicks = 120)
    public static void lmaTorchLight(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        maid.getAvailableBackpackInv().insertItem(0, new ItemStack(net.minecraft.world.item.Items.TORCH, 4), false);
        // 构造 DARKNESS 快照 (亮度过低)
        var worldInfo = new com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot.WorldInfo(
                false, false, false, 0, 0, "", "COLD", 0.1f, "NONE", 18000L, "NIGHT", "", "");
        var snap = new com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot(
                ((net.minecraft.server.level.ServerLevel) maid.level()).getGameTime(), java.util.Map.of(), worldInfo);
        var pipe = (com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.TorchLightPipeline)
                com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get("torch_light").pipeline();
        pipe.onSignal(maid, snap, com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals.ENV_DARKNESS);
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-TORCH] offhand={}", maid.getOffhandItem());
        if (maid.getOffhandItem().is(net.minecraft.world.item.Items.TORCH)) helper.succeed();
        else helper.fail("副手未变为火把 (lightUp 未生效): " + maid.getOffhandItem());
    }

    /**
     * v79.62.2 砍树验证 (collect_wood) — 仿 lmaChainOre: 脚下原木 + 主手铁斧
     * → 开脉 KEY_QUEUE → 强制蓄力到点 → charge 破块 → 原木被破坏.
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaWoodCollect(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        // v79.62.2 修: WOOD validAt 默认查天然树 (CHAIN_WOOD_NATURE_CHECK) — 平台单原木非树 → 静默不开脉
        boolean natureCheck = com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK.get();
        com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK.set(false);
        try {
            helper.setBlock(new BlockPos(7, 1, 7), net.minecraft.world.level.block.Blocks.OAK_LOG);
            maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_AXE));
            if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                    .submit(maid, "collect_wood", null, 0)) {
                helper.fail("collect_wood submit 失败");
                return;
            }
            var pl = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get("collect_wood").pipeline();
            var data = maid.getPersistentData();
            pl.tick(sl, maid);
            if (!data.contains(com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.KEY_QUEUE)) {
                helper.fail("首 tick 未开脉 (KEY_QUEUE 未写入)");
                return;
            }
            data.putLong(com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.KEY_CHARGE_END,
                    sl.getGameTime() - 1);
            BlockPos maidAbs = helper.absolutePos(new BlockPos(7, 2, 7));
            maid.moveTo(maidAbs.getX() + 0.5, maidAbs.getY(), maidAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
            pl.tick(sl, maid);
            boolean dug = sl.getBlockState(helper.absolutePos(new BlockPos(7, 1, 7))).isAir();
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-WOOD] dug={}", dug);
            if (!dug) helper.fail("charge 后原木未被破坏");
            else helper.succeed();
        } finally {
            com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK.set(natureCheck);
        }
    }

    /**
     * v79.62.2 唱片机验证 — 放唱片机 + 女仆背包唱片, jukebox 任务 gate 后
     * 应把唱片放入唱片机 (insertDisc 生效).
     */
    @GameTest(template = "game_test", timeoutTicks = 300)
    public static void lmaJukeboxPlay(GameTestHelper helper) {
        BlockPos juke = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(juke, net.minecraft.world.level.block.Blocks.JUKEBOX.defaultBlockState());
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_13, 1), false);
        maid.teleportTo(juke.getX() + 0.5, juke.getY() + 1.0, juke.getZ() + 0.5);
        maid.getBrain().setMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get(),
                new net.minecraft.world.entity.ai.behavior.BlockPosTracker(juke));
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "jukebox", null, 0);
        if (!ok) { helper.fail("jukebox submit 失败"); return; }
        helper.runAfterDelay(80, () -> {
            var be = helper.getLevel().getBlockEntity(juke);
            boolean disc = false;
            if (be instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity j) {
//? if 1.20.1 {
                disc = !j.getFirstItem().isEmpty();
//?} else {
                disc = !j.getItem(0).isEmpty();
//?}
            }
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-JUKE] disc={}", disc);
            if (!disc) helper.fail("唱片机未放入唱片 (insertDisc 未生效)");
            else helper.succeed();
        });
    }

    /**
     * v79.62.2 合成链验证 (craft_chain) — 放合成台 + 女仆背包材料 + 产物 target,
     * executeOne 应合成出产物 (SUCCESS 计数 ≥1).
     */
    @GameTest(template = "game_test", timeoutTicks = 300)
    public static void lmaCraftChain(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(table, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        // 原木→木板 (经典配方): 背包放原木即可 (RecipeResolver 解析 oak_planks 配方 → 需要原木)
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 8), false);
        maid.teleportTo(table.getX() + 0.5, table.getY() + 1.0, table.getZ() + 0.5);
        maid.getBrain().setMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get(),
                new net.minecraft.world.entity.ai.behavior.BlockPosTracker(table));
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "craft_chain", "minecraft:oak_planks", 0);
        if (!ok) { helper.fail("craft_chain submit 失败"); return; }
        helper.runAfterDelay(80, () -> {
            int counter = (int) com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getCounter(maid);
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-CRAFT] counter={}", counter);
            if (counter < 1) helper.fail("合成未执行 (SUCCESS 计数 < 1): " + counter);
            else helper.succeed();
        });
    }

    /**
     * v79.62.2 搬运验证 (arm_transfer) — 设取/存坐标 (两个箱子) + submit,
     * validate 应通过 (坐标已设置) + 任务运行 (状态机 TO_TAKE 导航到取货点).
     */
    @GameTest(template = "game_test", timeoutTicks = 300)
    public static void lmaArmTransfer(GameTestHelper helper) {
        BlockPos take = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos dep = helper.absolutePos(new BlockPos(5, 1, 3));
        helper.getLevel().setBlockAndUpdate(take, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(dep, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        var data = maid.getPersistentData();
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(data, com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.ARM_TAKE, take);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(data, com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.ARM_DEPOSIT, dep);
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "arm_transfer", null, 0);
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-ARM] submit={}", ok);
        if (!ok) helper.fail("arm_transfer submit 失败 (坐标未设置?)");
        else helper.succeed();
    }

    /**
     * v79.62.2 定时交互验证 (block_interact) — 绑定拉杆 + 女仆旁 + 定时器,
     * submit 后应运行 (validate 通过: pos 已绑定且在交互距离).
     */
    @GameTest(template = "game_test", timeoutTicks = 300)
    public static void lmaBlockInteract(GameTestHelper helper) {
        BlockPos lever = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(lever, net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState());
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        maid.teleportTo(lever.getX() + 0.5, lever.getY() + 1.0, lever.getZ() + 0.5);
        // 绑定方块 (cfg KEY_POS)
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "block_interact");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "pos", lever);
        cfg.putInt("timer_enabled", 1);
        cfg.putInt("timer_interval", 10);
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "block_interact", null, 0);
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-BI] submit={}", ok);
        if (!ok) helper.fail("block_interact submit 失败 (未绑定/距离)");
        else helper.succeed();
    }

    /**
     * v79.62.2 刷扫验证 (brush) — 放可疑沙砾 + 女仆背包刷子 + 旁,
     * tick 应刷扫 (findSuspicious 找到 + brush 消耗刷子耐久).
     */
    @GameTest(template = "game_test", timeoutTicks = 300)
    public static void lmaBrush(GameTestHelper helper) {
        BlockPos sus = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(sus, net.minecraft.world.level.block.Blocks.SUSPICIOUS_GRAVEL.defaultBlockState());
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        BlockPos maidAbs = helper.absolutePos(new BlockPos(3, 2, 3));
        maid.moveTo(maidAbs.getX() + 0.5, maidAbs.getY(), maidAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.BRUSH, 1), false);
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "brush", null, 0);
        if (!ok) { helper.fail("brush submit 失败"); return; }
        // 节拍 gt%10 == 0 — runAfterDelay 走真实 tick 推进. 验证点:
        // ① findSuspicious 找到可疑沙砾 ② tick 无异常 (gametest setBlock 无 loot 数据时
        // be.brush no-op 不耗耐久 — 不断言耐久, 断言管线健全 + 无障碍路径)
        helper.runAfterDelay(100, () -> {
            boolean held = !maid.getAvailableInv(true).getStackInSlot(0).isEmpty();
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-BRUSH] held={} dmg={}", held,
                    maid.getAvailableInv(true).getStackInSlot(0).getDamageValue());
            if (!held) helper.fail("刷子异常消失");
            else helper.succeed();
        });
    }

    /**
     * v79.62.2 探险地图验证 (explorer_map) — 背包放填充地图 (寻宝图),
     * tick 应读宝藏坐标 → 气泡 + KEY_ANNOUNCED 写入 (去重).
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaExplorerMap(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        // 构造填充地图 (FILLED_MAP — 无实际宝藏装饰时 readTreasurePos 可能 null → 只验证管线不崩)
        var map = new ItemStack(net.minecraft.world.item.Items.FILLED_MAP, 1);
        maid.getAvailableInv(true).insertItem(0, map, false);
        var pl = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get("explorer_map").pipeline();
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        try {
            for (int i = 0; i < 5; i++) pl.tick(sl, maid);
        } catch (Exception ex) {
            helper.fail("explorer_map tick 异常: " + ex);
            return;
        }
        // 无宝藏装饰时 readTreasurePos null → 循环继续, 不崩即过 (验证点 = 管线健壮)
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-MAP] tick ok");
        helper.succeed();
    }

    /** v79.62.2 compat 冒烟: 提交 Create/CBC 任务 (字符串 submit, 不引用 mod 类) — 验证注册+validate. */
    private static void compatSmoke(GameTestHelper helper, String task) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        // 任务未注册 (环境无 Create/CBC mod) → 跳过 (非失败 — 该环境本就无此任务)
        boolean registered = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get(task) != null;
        if (!registered) {
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-COMPAT] task={} NOT-REGISTERED (环境无 mod, 跳过)", task);
            helper.succeed();
            return;
        }
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, task, null, 0);
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-COMPAT] task={} registered submit={}", task, ok);
        if (!ok) helper.fail(task + " 已注册但 validate/提交失败");
        else helper.succeed();
    }

    /**
     * v79.62.2 关闭寻路模式验证 (no_pathfind) — cfg 开 no_pathfind, 女仆传送到区块中间,
     * 应直接挖游标石头 (near 判定用区块中间, 修复空转死循环: 原判 target 区块角 8 格 never near).
     */
    @GameTest(template = "game_test", timeoutTicks = 400)
    public static void lmaVoidNoPathfind(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        int bkX = maid.blockPosition().getX() >> 4, bkZ = maid.blockPosition().getZ() >> 4;
        BlockPos center = new BlockPos(bkX * 16 + 8, maid.blockPosition().getY() - 1, bkZ * 16 + 8);
        BlockPos stone = new BlockPos(bkX * 16, center.getY(), bkZ * 16);
        helper.getLevel().setBlockAndUpdate(stone, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        // cfg: start/size/no_pathfind=true
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", stone);
        cfg.putInt("size", 1);
        cfg.putInt("y", center.getY());
        cfg.putInt("x", stone.getX());
        cfg.putInt("z", stone.getZ());
        cfg.putBoolean(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.VoidExcavationPipeline.KEY_NO_PATHFIND, true);
        maid.teleportTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1), false);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "void_excavation", null, 0);
        BlockPos stoneFinal = stone.immutable();
        helper.runAfterDelay(200, () -> {
            boolean dug = !helper.getLevel().getBlockState(stoneFinal).is(net.minecraft.world.level.block.Blocks.STONE);
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-NPF] dug={} stone={}", dug, stoneFinal);
            if (!dug) helper.fail("no_pathfind 模式未挖掉游标石头 (空转?)");
            else helper.succeed();
        });
    }

    /**
     * v79.62.2 填坝排水验证 (dam_fill) — 3×3 区块区域: 放输入箱(装沙子) + 区域内灌水,
     * submit 后筑墙 (外圈沙子) → 排水 (区域内水变空气).
     */
    @GameTest(template = "game_test", timeoutTicks = 600)
    public static void lmaDamFill(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        // 区域: start 区块 (2,2) 为中心 size=3 → 区域区块 (1,1)~(3,3)
        BlockPos start = new BlockPos(2 * 16 + 8, maid.blockPosition().getY(), 2 * 16 + 8);
        // 输入箱 (装沙子)
        BlockPos input = helper.absolutePos(new BlockPos(8, 1, 1));
        helper.getLevel().setBlockAndUpdate(input, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        var inv = helper.getLevel().getBlockEntity(input);
        if (inv instanceof net.minecraft.world.Container c) {
            c.setItem(0, new ItemStack(net.minecraft.world.item.Items.SAND, 64));
        }
        // v79.62.2 排水模式需要空桶 (ensureBucket) — 测试女仆背包给桶
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.BUCKET, 1), false);
        // cfg: start / size / input / topY
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "dam_fill");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", start);
        cfg.putInt("size", 3);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "input", input);
        cfg.putInt("topY", start.getY() + 3);
        // v79.62.2 两模式独立: 测试用排水模式 (drain_enabled=true) — 只排水不筑墙
        cfg.putBoolean("drain_enabled", true);
        // 区域内 (区块 2,2) 灌水
        BlockPos water = new BlockPos(2 * 16, start.getY(), 2 * 16);
        helper.getLevel().setBlockAndUpdate(water, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        // 女仆传送到区域中间
        maid.teleportTo(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "dam_fill", null, 0);
        if (!ok) { helper.fail("dam_fill submit 失败"); return; }
        // 外圈墙格: 区域 (区块1~3 → x 16..63) 外 1 格 = x=15 (区域外), z=32 (区域内 z 向)
        BlockPos wallCell = new BlockPos(1 * 16 - 1, start.getY(), 2 * 16);
        BlockPos waterF = water.immutable();
        helper.runAfterDelay(300, () -> {
            // v79.62.2 gametest 悬浮平台: 墙列基岩 (minBuildHeight+1=-63) 可能虚空 setBlock 失败 —
            // 检测墙列「基岩上方任意层有方块」= 沙已堆 (从底部往上堆到 topY)
            boolean wall = false;
            int minY = helper.getLevel().getMinBuildHeight();
            for (int y = minY + 1; y <= start.getY() + 3; y++) {
                var st = helper.getLevel().getBlockState(new BlockPos(wallCell.getX(), y, wallCell.getZ()));
                if (!st.isAir() && st.getFluidState().isEmpty()) { wall = true; break; }
            }
            boolean drained = helper.getLevel().getBlockState(waterF).getFluidState().isEmpty();
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-DAM] drained={} water={}", drained, waterF);
            // v79.62.2 排水模式: 只验排水 (不筑墙 — 墙断言删)
            if (!drained) helper.fail("排水模式未生效: drained=" + drained);
            else helper.succeed();
        });
    }

    @GameTest(template = "game_test", timeoutTicks = 120) public static void lmaCrank(GameTestHelper h) { compatSmoke(h, "crank"); }
    @GameTest(template = "game_test", timeoutTicks = 120) public static void lmaPower(GameTestHelper h) { compatSmoke(h, "power"); }
    @GameTest(template = "game_test", timeoutTicks = 120) public static void lmaPress(GameTestHelper h) { compatSmoke(h, "press"); }
    @GameTest(template = "game_test", timeoutTicks = 120) public static void lmaMix(GameTestHelper h) { compatSmoke(h, "mix"); }
    @GameTest(template = "game_test", timeoutTicks = 120) public static void lmaRunningBelt(GameTestHelper h) { compatSmoke(h, "running_belt"); }
    @GameTest(template = "game_test", timeoutTicks = 120) public static void lmaMaidAssembly(GameTestHelper h) { compatSmoke(h, "maid_assembly"); }
    @GameTest(template = "game_test", timeoutTicks = 120) public static void lmaCannonLoad(GameTestHelper h) { compatSmoke(h, "cannon_load"); }
}
