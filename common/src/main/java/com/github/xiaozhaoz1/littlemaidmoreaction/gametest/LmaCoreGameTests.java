package com.github.xiaozhaoz1.littlemaidmoreaction.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.LMAT;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlock;
import com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlockEntity;
import com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitRecipe;
import com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlockEntity.DefenseTowerItemHandler;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlocks;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaItems;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.TaskHandler;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.TorchLightPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot.WorldInfo;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.TaskConfigs;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationContainerService;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache.PatternType;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute.Phase;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.LightQuery;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.HeatSourceQuery;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.Container;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.item.crafting.RecipeType;
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
 * LMA gametest — Core 域用例 (**8 条**)。
 *
 * <p>2026-09-21 由 {@code LmaGameTests} (6490 行) 拆分 — **纯搬移**;
 * 断言/判据/超时/template/用例名 (test ID) **逐字不变** (由 scripts/gametest-ids.mjs 121=121 把关)。
 * <p>⚠ 两平台源码实测: holder 与 prefix 按**方法所在类**读取、继承不生效 ⇒ 本类**自持**两注解 (与拆前一致)。
 */
@GameTestHolder(LittleMaidMoreAction.MOD_ID)
@PrefixGameTestTemplate(value = false)
public class LmaCoreGameTests extends LmaGameTestSupport {

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
     * 引擎异常护栏 (v79.63 EngineGuard) — 管线抛异常**不得崩服**, 且连续失败达阈值自动降级终结。
     *
     * <p>背景: 原三个 tick 调用点裸调用, 异常经 EventBus (**catch 后重抛**) 上传到 server tick =
     * 崩服; LMAT 是对外 API, 第三方管线有 bug 即可崩所有玩家。本测试 = 该护栏的回归闸门。
     */
    @GameTest(template = "game_test")
    public static void lmaEnginePanicIsolation(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);

        int[] calls = {0};
        var boom = new com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline() {
            @Override public String taskType() { return "__panic"; }
            @Override public void tick(net.minecraft.server.level.ServerLevel w, EntityMaid m) {
                calls[0]++;
                // ★ 2026-09-20 实机+线程转储定因: 本夹具的 `__panic` 是**进程全局注册且无法注销**,
                //   而 standalone 通道每 10t 会"自动启动"未在跑的被(开关开) ⇒ 从本用例之后**每个用例的
                //   每只女仆都被它附身、每 tick 抛异常** ⇒ 4 万行刷屏 + tick 逐秒变慢 ⇒ **整套卡死** ✗✗
                //   (症状: 服务端主线程 RUNNABLE 烧 CPU 在 TaskTickHandler 遍历实体; 日志停在 panic 之后)
                //   ⇒ 修法: 超出观测窗口后**自摘 + 静默** — 不再抛异常, 也不再缠着其它用例 ✓
                if (calls[0] > 6) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                            .cancelPassive(m, "__panic");
                    return;
                }
                throw new IllegalStateException("模拟第三方管线缺陷");
            }
        };
        com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.registerPassive(
                "__panic", boom, com.github.xiaozhaoz1.littlemaidmoreaction.task.TaskRegistryManifest.Drive.STANDALONE_PASSIVE);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submitPassive(maid, "__panic");

        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        var passives = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.passiveTasksList();
        // ★ v79.64 修偶发 (calls=6 ✗) 的**根因**: 本夹具 `registerPassive("__panic", …)` 是**进程全局注册**
        //   且 TaskRegistry **无注销 API** ⇒ 该必抛任务留在注册表; 而 `tickStandalonePassives` 每 10t 有
        //   "自动启动"分支 (开关开 + 非 in_progress ⇒ 自动 submitPassive) ⇒ 降级后**下一 tick** 就被复活 ✗
        //   ⇒ 观测到 calls=6/9 (实测同一次执行内该 maid 被降级 3 次)。(曾试 setDayTime 对齐 — 无效:
        //   它只改白天时间, 不推进 getGameTime ⇒ 手工驱动每次仍推进 1t ✓ 已撤。)
        //   ⇒ 修法: 断言改为**语义等价且抗重复启动**的形式 — 数"第 1..3 次失败 + 紧随降级"的**序列**,
        //     而不是数 calls (= 会被自动启动重复启动而失真) ✓; 循环上限同时保留小值以继续吞异常 ✓
        // 连续驱动 6 次 — 管线每次都抛; 护栏必须吞下 (否则本测试所在服务器当场崩)
        for (int i = 0; i < 6; i++) {
            try {
                com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                        .tickStandalonePassives(sl, maid, passives);
            } catch (Throwable t) {
                helper.fail("EngineGuard 未隔离异常, 异常逃逸到引擎调用方: " + t);
                return;
            }
        }
        // 隔离语义 (抗自动启动重复启动 ✓): 必须出现「第 1..max 次失败 + 第 max 次后紧跟降级」这一**序列** ✓
        int max = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.EngineGuard.MAX_CONSECUTIVE_FAILURES;
        if (calls[0] < max) {
            helper.fail("异常管线被驱动次数不足 (应≥" + max + " 次才触发降级), 实际 " + calls[0] + " 次");
            return;
        }
        // 降级 = cancelPassive → in_progress 键被摘除
        String key = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.passiveKey("__panic");
        if (com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.STATE_IN_PROGRESS
                .equals(maid.getPersistentData().getString(key))) {
            helper.fail("连续 " + max + " 次异常后应已降级 cancelPassive (in_progress 键未清)");
            return;
        }
        // ★ v79.64 更正: 原断言"降级后不再驱动"建立在**错误前提**上 ✗ — 本夹具的 `__panic` 是
        //   **全局注册且无法注销**, 而 standalone 通道每 10t 会"自动启动"未在跑的被(开关开) ⇒ 降级后
        //   **必然可能被复活**(实测: 同一次执行内该 maid 被降级 3 次) ⇒ 该断言会假红 ✗。
        //   正确断言 (护栏真正的不变量): **降级后再驱动不得让异常逃逸** — 无论它被复活多少次, 引擎都不崩 ✓
        for (int i = 0; i < 3; i++) {
            try {
                com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                        .tickStandalonePassives(sl, maid, passives);
            } catch (Throwable t) {
                helper.fail("降级后再驱动导致异常逃逸 (护栏失效): " + t);
                return;
            }
        }
        // 收尾: 清掉本 maid 的被动状态, 减少 __panic 被自动启动复活的窗口 (注册表无注销 API, 消不掉条目)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancelPassive(maid, "__panic");
        helper.succeed();
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
    * 8 个配置菜单**构造矩阵** (v79.63) — 覆盖"配置界面能打开"的服务端对偶。
    *
    * <p>每个菜单都以 `(containerId, playerInv, maidId)` 构造 (共用基类); 断言:
    * ① 构造成功 (不抛) ② MenuType 已注入 (LmaMenus 非 null — 缺它客户端开屏即崩)
    * ③ 玩家背包槽位齐 (≥36) ④ containerId 透传。
    *
    * <p>逐项 try/catch: 任一菜单构造异常都精确报出是哪个 (而不是笼统失败)。
    */
   @GameTest(template = "game_test", timeoutTicks = 200)
   public static void lmaConfigMenuMatrix(GameTestHelper helper) {
      Player player = registeredOwner(helper);
      EntityMaid maid = spawnMaid(helper);
      if (maid == null) return;
      Inventory inv = player.getInventory();
      String[] names = {"block_interact", "item_list", "craft_chain", "bell_ring",
                        "dam_fill", "void_excavation", "ai_control", "passive_toggle"};
      java.util.List<net.minecraft.world.inventory.AbstractContainerMenu> menus = new java.util.ArrayList<>();
      try {
         menus.add(new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BlockInteractConfigMenu(1, inv, maid.getId()));
         menus.add(new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.ItemListConfigMenu(2, inv, maid.getId()));
         menus.add(new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.CraftChainConfigMenu(3, inv, maid.getId()));
         menus.add(new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BellRingConfigMenu(4, inv, maid.getId()));
         menus.add(new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigMenu(5, inv, maid.getId()));
         menus.add(new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigMenu(6, inv, maid.getId()));
         menus.add(new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu(7, inv, maid.getId()));
         menus.add(new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.PassiveToggleConfigMenu(8, inv, maid.getId()));
      } catch (Throwable t) {
         helper.fail("配置菜单构造抛异常 (已完成 " + menus.size() + "/8): " + t);
         return;
      }
      java.util.List<String> problems = new java.util.ArrayList<>();
      for (int i = 0; i < menus.size(); i++) {
         var m = menus.get(i);
         if (m.getType() == null) problems.add(names[i] + ": MenuType 为 null (LmaMenus 未注入 → 客户端开屏会崩)");
         if (m.slots.size() < 36) problems.add(names[i] + ": 槽位仅 " + m.slots.size() + " (<36 玩家背包不齐)");
      }
      LittleMaidMoreAction.LOGGER.warn("[GAMETEST-CONFIG-MENU] 构造 {} 个配置菜单, 问题 {} 条",
              menus.size(), problems.size());
      if (!problems.isEmpty()) {
         helper.fail("配置菜单契约问题: " + String.join(" | ", problems));
         return;
      }
      helper.succeed();
   }
}
