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
 * LMA gametest 共享基类 — 装全部**辅助/夹具方法**与**共享字段** (无 {@code @GameTest} 用例)。
 *
 * <p>继承 TLM {@code TLMGameTests} 复用其测试基建 ({@code useItemOn} 等); 7 个测试类继承本类。
 * <p>2026-09-21 由 {@code LmaGameTests} (6490 行) 拆出 — **纯搬移, 未改任何方法体**。
 * <p>⚠ **唯一机械改动 (已披露)**: 原 {@code private static} 辅助方法在基类中放宽为
 * {@code protected static} — 否则 7 个子类无法继承调用 (放宽可见性**不改变行为**)。
 * <p>⚠ 本类**不带** {@code @GameTestHolder}/{@code @PrefixGameTestTemplate}:
 * 两平台源码实测 ({@code ForgeGameTestHooks:107} / {@code GameTestHooks:92}) 只按 **方法所在类** 取注解、
 * 且 {@code clazz.getDeclaredMethods()} 不收集继承方法 ⇒ 基类无需、也不应带。
 */
public abstract class LmaGameTestSupport extends TLMGameTests {

    /** 生成女仆并返回 (失败时已 helper.fail, 返回 null) — 默认 mock player 放置 */
    protected static EntityMaid spawnMaid(GameTestHelper helper) {
//? if 1.20.1 {
        return spawnMaid(helper, helper.makeMockSurvivalPlayer());
//?} else {
        return spawnMaid(helper, helper.makeMockPlayer(GameType.DEFAULT_MODE));
//?}
    }

    /** 生成女仆并返回 (失败时已 helper.fail, 返回 null) — 指定 player 放置
     *  v79.62.3: 并发竞态防护 — 同 tick 多测试 spawn 时 useOn 偶发失败, 3 次重试 + 横向换位自愈
     *  v79.62.5: 直接实体创建 — 绕过 smart slab 的 cap 上限/tame sync_data 崩溃 (世界 maid 堆积 151+
     *  导致 useOn 生成失败/拿错 maid; 参照 ItemSmartSlab.spawnNewMaid 流程, tame 改 setOwnerUUID 防崩溃) */
    /** spawn 并发信号量 (v79.62.5 用户裁定: 并发调成 3) — 并行 batch 跑测试时限制同时 spawn 数 */
    protected static final java.util.concurrent.Semaphore SPAWN_GATE = new java.util.concurrent.Semaphore(3);

    /** 生成女仆并返回 (失败时已 helper.fail, 返回 null) — 指定 player 放置。
     *  v79.62.3: 并发竞态防护 — 同 tick 多测试 spawn 时 useOn 偶发失败, 3 次重试 + 横向换位自愈
     *  v79.62.5: 直接实体创建 — 绕过 smart slab 的 cap 上限/tame sync_data 崩溃 (世界 maid 堆积 151+
     *  导致 useOn 生成失败/拿错 maid; 参照 ItemSmartSlab.spawnNewMaid 流程, tame 改 setOwnerUUID 防崩溃) */
    protected static EntityMaid spawnMaid(GameTestHelper helper, Player player) {
        SPAWN_GATE.acquireUninterruptibly();
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                // 重试换位置 — 同位置重复失败多为上方 2 格残留碰撞, 横向挪 1 格重放
                BlockPos groundPos = new BlockPos(7 + (attempt % 2), 1, 7);
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
                EntityMaid maid = InitEntities.MAID.get().create(level);
                if (maid == null) { continue; }
                // 同原版 spawnNewMaid: tame (uuid 可查性 — setOwnerUUID 不加 owner 绑定, resolveTarget
                // 反查需实体完整注册; 实测仅 addFreshEntity 后 getEntity(uuid) 可能 null) + finalizeSpawn
                maid.tame(player);
//? if 1.20.1 {
                maid.finalizeSpawn(level, level.getCurrentDifficultyAt(helper.absolutePos(groundPos)),
                        net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null, null);
//?} else {
                maid.finalizeSpawn(level, level.getCurrentDifficultyAt(helper.absolutePos(groundPos)),
                        net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null);
//?}
                BlockPos spawnAbs = helper.absolutePos(groundPos.above());
                maid.moveTo(spawnAbs.getX() + 0.5, spawnAbs.getY(), spawnAbs.getZ() + 0.5, 0, 0);
                level.addFreshEntity(maid);
                diagSpawnAndWatch(helper, maid);   // 正式诊断 (2026-09-21): SPAWN 一行 + 每 100t 心跳
                return maid;
            }
            helper.fail("女仆未生成 (直接创建失败, 3 次尝试)");
            return null;
        } finally {
            SPAWN_GATE.release();
        }
    }

    protected static net.minecraft.world.InteractionResult useItemOn(GameTestHelper helper, Player player, ItemStack stack,
                                  BlockPos relativePos, Direction face) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolutePos), face, absolutePos, false);
        UseOnContext context = new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit);
        return stack.useOn(context);
    }

   /**
    * 条件轮询断言 (v79.64 用户裁定: **"定时断言" → "条件轮询"**) — 每 `STEP` tick 检查一次, **成立即通过**。
    *
    * <p><b>为什么</b>: 原采矿类用例把断言写在固定 `runAfterDelay(N)` 墙点上 ✗, 而"走到矿旁→挖穿"的耗时
    * 是浮动的 (走路路径 / 多层扫描节流 5t·100t·250t / 导航看门狗 5s 重试) ⇒ 同一份代码时而 N 内挖成、
    * 时而不成 ⇒ **时序 flaky** (错题 #270「全绿不可复现」同源)。轮询让**成功即结束** ⇒ 更稳也更快,
    * 只有真失败才等到 `maxTicks` ✓ (纯调大窗口只是降低 flake 概率, 治标 ✗)。
    *
    * <p><b>实现说明</b>: 不用 `helper.succeedWhen(...)` — 实测其 criterion 只被求值一次就判过 ✗
    * (框架 sequence 语义与预期不符) ⇒ 改用 `runAtTickTime` **自己排下一次检查** ✓, 语义完全可控。
    * 达成时打印**实际耗时** (校准窗口用), 超时打印现场 (女仆位置/观察点方块) 便于定因 ✓。
    *
    * <p>{@code onSuccess} (可空) = 达成后就地收尾再 succeed ✓ (如"清区域防残留") —— 固定墙点版在墙点回调里
    * 就是这么干的 ⇒ 轮询版必须保留同一动作 (成功即结束会绕过后续代码 ✗)。两种签名刻意分开:
    * {@link #pollUntil} (varargs, 旧调用点零改动) 与 {@link #pollUntilSucceedOn} (带收尾, watch 走数组参数)
    * —— 若把 {@code Runnable} 加在 varargs 之后会与"把 Runnable 当 BlockPos"... 的重载解析撞车 ✗。
    */
   /** 非女仆主体用例 (塔/方块/纯状态断言): 无 maid 可打 ⇒ 传 null (超时诊断会打印 "无女仆主体") */
   protected static void pollUntil(GameTestHelper helper, java.util.function.BooleanSupplier ok, long maxTicks,
                                 String what, EntityMaid maid, BlockPos... watch) {
      pollStep(helper, ok, null,
              next -> helper.runAtTickTime(helper.getTick() + 20L, next),
              new long[]{maxTicks, helper.getTick()}, what, maid, watch);
   }

   /** 单阶段 + **收尾动作** (watch 用数组参数, 避免与 {@link #pollUntil} 的 varargs 解析歧义) */
   protected static void pollUntilSucceedOn(GameTestHelper helper, java.util.function.BooleanSupplier ok, long maxTicks,
                                          String what, EntityMaid maid, BlockPos[] watch, Runnable onSuccess) {
      pollStep(helper, ok, onSuccess,
              next -> helper.runAtTickTime(helper.getTick() + 20L, next),
              new long[]{maxTicks, helper.getTick()}, what, maid, watch);
   }

   /**
    * **两阶段**条件轮询 (2026-09-19 pollUntil 家族整治): 第一阶段 {@code firstOk} 成立 ⇒ 执行
    * {@code onFirstOk} (就地摆下一阶段场景, 如"清黑名单再开烧" / "按认领区块铺石头") ⇒ 用**独立的**
    * 二阶段谓词 {@code secondOk} 继续轮询 (自带截止窗口 {@code maxTicks2}) ⇒ 成立后 {@code onSecondOk}
    * 收尾并 succeed ({@code null} = 直接 succeed)。
    *
    * <p><b>为什么需要</b>: 原两阶段用例是 `runAfterDelay(N) { 断言; runAfterDelay(M) { 断言 } }`
    * 的金字塔 — 两处墙点都赌固定 tick ✗ (错题 #270)。串成轮询后每阶段各带自己的截止窗口
    * (**取值与原墙点一致** ⇒ 最坏耗时不变, 仍在 `@GameTest(timeoutTicks)` 预算内), 但成功即结束 ✓。
    *
    * <p>⚠️ 二阶段谓词必须**独立传入** (不能复用 {@code firstOk} — 它此刻已成立 ⇒ 会立刻再成立 ⇒ 空转 ✗);
    * 转阶段时打印 `[GAMETEST-POLL] 转阶段: {}` 便于日志定因 ✓。
    * <p>⚠️ 阶段切换靠 {@code nextScheduler} 改写**续排目标**实现 (不再由 pollStep 自己续排一阶段) ✓。
    */
   protected static void pollUntilThen(GameTestHelper helper, java.util.function.BooleanSupplier firstOk,
                                     Runnable onFirstOk, java.util.function.BooleanSupplier secondOk,
                                     Runnable onSecondOk, long maxTicks1, long maxTicks2, String what,
                                     EntityMaid maid, BlockPos... watch) {
      if (firstOk == null || secondOk == null || onFirstOk == null) {
         throw new IllegalStateException("[GAMETEST-POLL] pollUntilThen 传参为 null (谓词/回调): " + what);
      }
      // ⚠️ 前置保险 (2026-09-19 加, 用户裁定): 阶段①在**起始 tick 就成立** ⇒ 直接 fail 并指名道姓。
      //    依据: 两阶段用例的前提是"阶段① = 某过程已完成"; t=0 即为真意味着 (a) 该信号源不代表"过程完成"
      //    (读的是尚未写入的内存态 / 上例残留), 或 (b) 夹具几何本就满足 ⇒ 若照常转阶段, 会把"摆场景"提前到
      //    前提条件之前, 最终以别的形态假红 (实测过一次: 石头铺在认领之前 ⇒ assigned=0)。
      //    正常路径零行为变化: 合法用例的阶段① t=0 必为 false (如"女仆已走到 10 格外的工作站") ✓
      if (firstOk.getAsBoolean()) {
         helper.fail("[GAMETEST-POLL] 前置条件在起始 tick 已成立 (夹具/信号源异常, 非产品缺陷): " + what);
         return;
      }
      final long start = helper.getTick();
      pollStep(helper, firstOk, null, next -> {
         onFirstOk.run();
         final long start2 = helper.getTick();
         LittleMaidMoreAction.LOGGER.warn("[GAMETEST-POLL] 转阶段: {} (前段用时={}t)", what, start2 - start);
         // 二阶段必须在**下一 tick** 起 (不可同步直调 ✗ — 那会在同一 tick 用刚成立的 firstOk 语义再判一次 ⇒ 秒过);
         // 二阶段自身也要能续排 (谓词未成立时) ⇒ 排程器显式给出, 不可传 null ✗
         helper.runAtTickTime(helper.getTick() + 20L, () -> pollStep(helper, secondOk, onSecondOk,
                 next2 -> helper.runAtTickTime(helper.getTick() + 20L, next2),
                 new long[]{maxTicks2, start2}, what + " (第二阶段)", maid, watch));
      }, new long[]{maxTicks1, start}, what, maid, watch);
   }

   /**
    * 轮询单步 (2026-09-19 重构: **续排由调用方给** {@code nextScheduler}) — 成立 → onSuccess(可空) → succeed;
    * 到期未成 → 诊断 + fail; 否则交给 {@code nextScheduler} 排下一次 (单阶段自续; 两阶段则改排二阶段)。
    *
    * <p>为什么这么改: 旧实现把"续排下一次"写死在本方法里 (靠额外参数 {@code ok2} 在两阶段时改写谓词) ✗ ⇒
    * 两处排程 (成功分支排二阶段 + 本方法续排一阶段) 会在"一阶段刚成立"后并存 ⇒ 二阶段已开始时一阶段仍在跑
    * (谓词立刻再成立 ⇒ 空转/传参错位) ✗。抽出续排后每个阶段只有**一个**排程来源 ✓。
    */
   /**
    * 正式诊断 (2026-09-21, 用户批准"探针铺满"; **同日收窄**): 关键节点写入**内存缓冲**, **失败时 dump**;
    * 通过路径**零日志开销** (只留一行摘要)。见 {@link #diagPush}/{@link #diagDump}/{@link #diagPassedAndClear}。
    * <p>字段: owner(归属用例, 取调用栈里**第 2 个**我方 gametest 帧 = 用例方法) · uuid · 结构 origin ·
    * **结构清场盒(±1)实际值** · maid 绝对 pos · 在册/isRemoved/isAlive/任务/导航目标。
    */
   private static final java.util.ArrayDeque<String> DIAG = new java.util.ArrayDeque<>();
   private static final int DIAG_CAP = 4000;

   protected static void diagPush(String line) {
      if (DIAG.size() >= DIAG_CAP) DIAG.pollFirst();
      DIAG.addLast(line);
   }

   /** 失败时把缓冲全量 dump (由 pollStep 超时分支与夹具 fail 前调用) */
   protected static void diagDump(String why) {
      LittleMaidMoreAction.LOGGER.error("[DIAG-DUMP] {} — 缓冲 {} 条:", why, DIAG.size());
      for (String l : DIAG) LittleMaidMoreAction.LOGGER.error("[DIAG] {}", l);
      DIAG.clear();
   }

   /** 通过: 清缓冲 + 一行摘要 (替代原先 300+ 条无条件日志; 收窄理由见 2026-09-21 用户裁定) */
   protected static void diagPassedAndClear(String what) {
      if (!DIAG.isEmpty()) LittleMaidMoreAction.LOGGER.info("[DIAG] {} 通过 ⇒ 诊断抑制 (丢弃 {} 条)", what, DIAG.size());
      DIAG.clear();
   }

   protected static void diagSpawnAndWatch(GameTestHelper helper, EntityMaid maid) {
      String owner = "?";
      int seen = 0;
      for (StackTraceElement e : new Throwable().getStackTrace()) {
         if (e.getClassName().contains(".littlemaidmoreaction.gametest.")) {
            if (++seen == 2) { owner = e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1) + "." + e.getMethodName(); break; }
         }
      }
      BlockPos o = helper.absolutePos(BlockPos.ZERO);
      diagPush("SPAWN owner=" + owner + " uuid=" + maid.getUUID() + " origin=" + o.toShortString()
              + " 清场盒=[" + (o.getX() - 1) + "," + (o.getZ() - 1) + ".." + (o.getX() + 17) + "," + (o.getZ() + 17) + "]"
              + " pos=" + maid.blockPosition().toShortString());
      diagHeartbeat(helper, maid, owner, 0);
   }

   private static void diagHeartbeat(GameTestHelper helper, EntityMaid maid, String owner, int n) {
      helper.runAtTickTime(helper.getTick() + 100L, () -> {
         diagPush("HB t=" + helper.getTick() + " owner=" + owner + " uuid=" + maid.getUUID()
                 + " pos=" + maid.blockPosition().toShortString()
                 + " 在册=" + com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.MaidIndex.contains(maid)
                 + " removed=" + maid.isRemoved() + " alive=" + maid.isAlive()
                 + " 任务=" + com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(maid)
                 + " 导航目标=" + com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi.navTarget(maid));
         if (n < 40) diagHeartbeat(helper, maid, owner, n + 1);   // ≤4000t
      });
   }

   protected static void pollStep(GameTestHelper helper, java.util.function.BooleanSupplier ok, Runnable onSuccess,
                                final java.util.function.Consumer<Runnable> nextScheduler,
                                final long[] timing, final String what, final EntityMaid maid,
                                final BlockPos[] watch) {
      if (nextScheduler == null) {
         throw new IllegalStateException("[GAMETEST-POLL] nextScheduler 为 null — 排程器必须显式给出: " + what);
      }
      if (ok.getAsBoolean()) {
         LittleMaidMoreAction.LOGGER.warn("[GAMETEST-POLL] 达成: {} 用时={}t", what, helper.getTick() - timing[1]);
         // 诊断收窄 (2026-09-21): succeed 收尾信息只进缓冲; 通过即清 (失败时才在 [DIAG-DUMP] 里看到)
         BlockPos so = helper.absolutePos(BlockPos.ZERO);
         diagPush("SUCCEED what=" + what + " tick=" + helper.getTick()
                 + " 清场盒=[" + (so.getX() - 1) + "," + (so.getZ() - 1) + ".." + (so.getX() + 17) + "," + (so.getZ() + 17) + "]");
         diagPassedAndClear(what);
         if (onSuccess != null) onSuccess.run();
         helper.succeed();
         return;
      }
      long elapsed = helper.getTick() - timing[1];
      if (elapsed >= timing[0]) {
         StringBuilder sb = new StringBuilder();
         for (BlockPos bp : watch) {
            sb.append(' ').append(bp.toShortString()).append('=')
              .append(helper.getLevel().getBlockState(bp).getBlock().getName().getString());
         }
         // 现场诊断 (一次性): 观察点的"是否裸露" + 任务是否还在跑 — 判"产品问题 vs 夹具几何"
         StringBuilder diag = new StringBuilder();
         for (BlockPos bp : watch) {
            boolean air = false;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
               if (helper.getLevel().getBlockState(bp.relative(d)).isAir()) { air = true; break; }
            }
            diag.append(' ').append(bp.toShortString()).append("[裸露=").append(air).append(']');
         }
         // maid 可空 (非女仆主体用例: 塔/方块/纯状态断言) ⇒ 超时诊断退化打印, 不 NPE (2026-09-19)
         String task = maid != null
                 ? com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(maid)
                 : "(无女仆主体)";
         Object maidPos = maid != null ? maid.blockPosition() : "(无女仆主体)";
         // 2026-09-21 (M1): 女仆诊断字段**只在失败时求值** (通过路径零开销) — 供"任务不动"类失败一眼定因:
         //   在册/移除/存活/实体表/区块加载/在册数 六项 + tick ⇒ 取代原先独立挂 t=40/200/600 的硬断言
         //   (那种 guard 会与"本用例早于 40t 成功 ⇒ 框架 succeed 清场 discard 她自己"赛跑 ⇒ 假红 ✗)
         String maidDiag;
         if (maid != null && maid.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            boolean inAll = false;
            int entCount = 0;
            for (var e : sl.getAllEntities()) { entCount++; if (e == maid) inAll = true; }
            maidDiag = "uuid=" + maid.getUUID() + " isRemoved=" + maid.isRemoved() + " isAlive=" + maid.isAlive()
                    + " 在册=" + com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.MaidIndex.contains(maid)
                    + " getAllEntities含她=" + inAll
                    + " chunkLoaded=" + sl.isLoaded(maid.blockPosition())
                    + " 在册数=" + com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.MaidIndex.size(sl)
                    + " 实体总数=" + entCount;
         } else {
            maidDiag = "(无女仆主体)";
         }
         LittleMaidMoreAction.LOGGER.error("[GAMETEST-POLL] 超时未达成: {} 经过={}t 绝对tick={} 女仆={} 任务={} 观察点:{}",
                 what, elapsed, helper.getTick(), maidPos, task, sb);
         LittleMaidMoreAction.LOGGER.error("[GAMETEST-POLL] 现场:{}", diag);
         LittleMaidMoreAction.LOGGER.error("[GAMETEST-POLL] 女仆诊断:{}", maidDiag);
         diagDump("pollUntil 超时: " + what);   // 失败 ⇒ 把内存缓冲的诊断全量 dump (通过路径零日志开销)
         // 正式诊断 (2026-09-21): 路径诊断 — 女仆→每个观察点的距离 + 连线上 5 个采样点的方块
         //   (判"她走不到": 悬空/无地板/被墙挡 ⇒ 一眼分辨, 免再插桩)
         if (maid != null) {
            StringBuilder trace = new StringBuilder();
            for (BlockPos bp : watch) {
               double d = Math.sqrt(maid.blockPosition().distSqr(bp));
               trace.append(' ').append(bp.toShortString()).append(" d=").append(String.format("%.1f", d)).append(" 路径:");
               for (int s = 1; s <= 5; s++) {
                  BlockPos p = new BlockPos(
                          maid.blockPosition().getX() + (bp.getX() - maid.blockPosition().getX()) * s / 6,
                          maid.blockPosition().getY() + (bp.getY() - maid.blockPosition().getY()) * s / 6,
                          maid.blockPosition().getZ() + (bp.getZ() - maid.blockPosition().getZ()) * s / 6);
                  trace.append(' ').append(helper.getLevel().getBlockState(p).getBlock().getName().getString());
               }
               // 2026-09-21 追加: 观察点自身 + 6 邻接 (判"矿被埋/被挡/可站层") — 失败时求值 ⇒ 零通过开销
               trace.append(" [自身=").append(helper.getLevel().getBlockState(bp).getBlock().getName().getString()).append(" 上下:");
               for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[]{
                       net.minecraft.core.Direction.UP, net.minecraft.core.Direction.DOWN}) {
                  trace.append(' ').append(dir.name().charAt(0)).append('=')
                          .append(helper.getLevel().getBlockState(bp.relative(dir)).getBlock().getName().getString());
               }
               trace.append(']');
            }
            LittleMaidMoreAction.LOGGER.error("[GAMETEST-POLL] 路径诊断:{}", trace);
            LittleMaidMoreAction.LOGGER.error("[GAMETEST-POLL] 女仆脚下: {} 上={} 下={} (y={})",
                    helper.getLevel().getBlockState(maid.blockPosition()).getBlock().getName().getString(),
                    helper.getLevel().getBlockState(maid.blockPosition().above()).getBlock().getName().getString(),
                    helper.getLevel().getBlockState(maid.blockPosition().below()).getBlock().getName().getString(),
                    maid.blockPosition().getY());
         }
         helper.fail("轮询超时未达成: " + what + " (经过 " + elapsed + "t, 绝对tick=" + helper.getTick() + ")"
                 + " | 女仆诊断: " + maidDiag);
         return;
      }
      nextScheduler.accept(() -> pollStep(helper, ok, onSuccess, nextScheduler, timing, what, maid, watch));
   }

   /** 该格是否已不是铁矿石 (= 被挖掉/替换) — 采矿类用例共用判据 */
   protected static boolean mined(GameTestHelper helper, BlockPos pos) {
      return !helper.getLevel().getBlockState(pos).is(Blocks.IRON_ORE);
   }

   /**
    * **观测窗口** — 治"**持续**什么都不该发生"类断言 (2026-09-19)。
    *
    * <p>为什么不能用 {@code pollUntil}: 那是"成立即过" ⇒ 对"不该发生"的判据 t=0 就成立 ⇒ **秒过 = 没测** ✗。
    * 正解 = 观测整段窗口: 每 {@code step} 跑一次 {@code probe} (probe 内违规时直接 {@code helper.fail} ⇒ 立刻判红,
    * 语义精确), 到 {@code ticks} 仍无违规 ⇒ 调 {@code onDone} 收尾成功 ✓。
    * 用法见 {@code lmaFarmMelonNoFalseHarvest} / {@code lmaBrushNothing}。
    */
   protected static void observeWindow(GameTestHelper helper, long ticks, long step, Runnable probe, Runnable onDone) {
      for (long t = step; t <= ticks; t += step) {
         helper.runAtTickTime(t, probe);
      }
      helper.runAtTickTime(ticks + step, onDone);
   }

   /**
    * 清掉**扫描半径内**的所有矿石 (方块注册名以 {@code _ore} 结尾 — 覆盖原版/深板岩/mod 矿),
    * 只保留 {@code keep} 指定的目标矿。
    *
    * <p><b>为什么需要 (2026-09-19 专项实测)</b>: 采矿管线按"**最近优先**"选目标 (半径 16),
    * 而用例原先只清一小段走廊 ⇒ **走廊外的矿会被抢走** ⇒ 女仆跑去挖别人的矿、超时失败
    * (实测现场: t=2 距目标 10 格 → t=40 距 13 → t=200 距 18 卡死, 且 {@code [ORE-DBG] navigate}
    * 指向走廊外另一格)。同款做法见 {@code lmaChainOreNeighbourChunk} 的"半径 20 球只留目标"。
    *
    * <p>只把**矿石格**换成石头 (不动地形/不掉落物) ⇒ 最小侵入、可安全复用于任何采矿用例 ✓
    */
   protected static void clearStrayOres(GameTestHelper helper, BlockPos center, int radius, BlockPos... keep) {
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
               BlockPos p = center.offset(dx, dy, dz);
               boolean isKeep = false;
               for (BlockPos k : keep) {
                  if (p.equals(k)) { isKeep = true; break; }
               }
               if (isKeep) continue;
               var st = helper.getLevel().getBlockState(p);
               if (st.isAir()) continue;   // 绝大多数是空气 ⇒ 省掉注册表查询
               if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock())
                       .getPath().endsWith("_ore")) {
                  helper.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
               }
            }
         }
      }
   }

   /**
    * 农田类用例收尾 (2026-09-19): 清掉该女仆的绑定区域 ⇒ {@code succeed}。
    *
    * <p>固定墙点版是在墙点回调里先 {@code putRegions(maid, List.of())} 再 {@code succeed} ✓;
    * 轮询化的成功路径由帮手收尾 ⇒ 把"清理"挂进成功回调, 保持与旧版**同样的清理动作** ✓
    * (同时也防"成功即结束"过早返回把区域留在盘上 ✗)。
    */
   protected static Runnable clearFarmRegionsThenSucceed(GameTestHelper helper, EntityMaid maid) {
      return () -> {
         com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                 .putRegions(maid, java.util.List.of());
         helper.succeed();
      };
   }

    /**
     * v79.62.2 测试通用: 清女仆任务残留 — smart slab 放置可能写 GUI_INIT/TLM_SWITCH,
     * GMPM 每 tick 处理会把被测任务顶掉 (换 target=null 重新 submit 另一任务) → 测试前必须清.
     */
    /**
     * 挖空夹具通用: 往某女仆的**销毁名单** (`void_excavation.destroy_list`, 键 = **物品 id**) 写入条目。
     *
     * <p><b>为什么夹具要用它</b> (2026-09-20 用户建议 + 线程转储实证 ✓): 挖空走
     * `VoidExcavationService.tryDig` — 先 `Block.getDrops` 再 `destroyBlock(pos,false)`，名单内物品**直接销毁**、
     * 名单外进背包(溢出落地)。而测试世界被挖出的大量掉落物实体会让**原版 `BlockCollisions` 物理**把整轮
     * 从 1 分钟拖到 12 分钟 ⇒ 夹具把常见地形物品放进销毁名单 ⇒ 不再堆积掉落物 ✓
     * 同时**天然覆盖销毁名单这条产品路径**（另有 {@code lmaVoidDestroyListWorks} 断言其语义）。
     */
    protected static void setVoidDestroyList(EntityMaid maid, String... itemIds) {
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                .cfgOrCreate(maid, "void_excavation");
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (String id : itemIds) list.add(net.minecraft.nbt.StringTag.valueOf(id));
        cfg.put("destroy_list", list);
    }

    /** 挖空夹具默认销毁名单 = 常见地形掉落 (测试世界不再堆掉落物实体; 语义与产品一致: 名单内挖出即销毁) */
    protected static void setVoidDestroyListTerrain(EntityMaid maid) {
        setVoidDestroyList(maid, "minecraft:stone", "minecraft:cobblestone", "minecraft:dirt",
                "minecraft:grass_block", "minecraft:sand", "minecraft:sandstone", "minecraft:gravel",
                "minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:tuff",
                "minecraft:andesite", "minecraft:diorite", "minecraft:granite");
    }

    /**
     * 挖空夹具: 给**合适工具** (用户裁定 ✓) —— 挖空按方块换镐/锹/斧 (`VoidExcavationService.ensureToolFor`)，
     * 但**工具必须在她背包里**才能换；没工具时挖掘极慢 (且部分方块无掉落) ⇒ 整轮被拖慢 ✗。
     * 用**下界合金**三件套 ⇒ 挖掘最快 (石头 1.5 硬度 ≈ 0.15s/格)，测试只关心"挖空流程"而非工具经济 ✓
     */
    protected static void giveVoidTools(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        inv.insertItem(0, new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE, 1), false);
        inv.insertItem(1, new ItemStack(net.minecraft.world.item.Items.NETHERITE_SHOVEL, 1), false);
        inv.insertItem(2, new ItemStack(net.minecraft.world.item.Items.NETHERITE_AXE, 1), false);
        maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE, 1));
    }

    /**
     * 挖空夹具: 在标记区域的指定区块铺 **2 层实心石头** (用户裁定 (a) ✓, 2026-09-20 实证)。
     *
     * <p><b>为什么必须铺</b>: 设 `min_y = start.y - 1`(只挖 2 层) 后, 若目标区域**没有实心方块**
     * (gametest 结构在世界 800 万格外 ⇒ 那些区块基本是空气), 则 2 层几乎瞬间挖完 ⇒ 整区域被
     * `poolMark(2)` ⇒ `poolClaim` 返回 null ⇒ 管线走"区域完成 → `cfg.remove(start)` + `cancel(maid)`"
     * ⇒ 用例的"认领/行走"断言必然失败 ✗ (实测日志: `[VOID] 认领失败=区域已完成 → cancel start=96,-59,96 size=3`)。
     * 铺石头后: 每区块 512 格实心 ⇒ 区域长期"进行中" ⇒ 断言窗口内稳定 ✓, 且**只用挖 2 层** ✓。
     *
     * @param fromCX/fromCZ 起始区块坐标 (含), chunksX/chunksZ 覆盖多少个区块
     */
    protected static void fillVoidTwoSolidLayers(GameTestHelper helper, BlockPos start,
                                               int fromCX, int fromCZ, int chunksX, int chunksZ) {
        var lvl = helper.getLevel();
        for (int cx = fromCX; cx < fromCX + chunksX; cx++) {
            for (int cz = fromCZ; cz < fromCZ + chunksZ; cz++) {
                // ★ 用户裁定 (2026-09-20): **小块铺石** — 每区块只铺角上 4×4×2 = 32 格 (原 16×16×2 = 512 ✗)。
                //   原理: 区块"挖完"= 游标扫过整区块 (**空气也要逐格走** ⇒ 天然 ~512t) ⇒ 只要区块里有任何
                //   实心块挡住"几 tick 秒完成"的极端即可 ✓; 全铺会让铺块+挖掘都变贵 (实测 3×3 ≈4600 格
                //   ⇒ 套件仍 5~9 分钟 ✗) ⇒ 成本降到 1/16 ✓
                for (int dx = 0; dx < 4; dx++) {
                    for (int dz = 0; dz < 4; dz++) {
                        for (int y = start.getY() - 1; y <= start.getY(); y++) {
                            lvl.setBlockAndUpdate(new BlockPos((cx << 4) + dx, y, (cz << 4) + dz),
                                    Blocks.STONE.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    protected static void clearMaidTaskState(EntityMaid maid) {

        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData.clearGuiInit(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData.clearTlmSwitch(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.clearAll(maid);
        // ★ 2026-09-20 总根因修复 ("不走"家族: 女仆原地卡到超时, 扫描命中却 WALKING 无位移):
        //   TLM 源码实证 — `hasRestriction() == isHomeModeEnable()`, 而 `isWithinRestriction(pos)` 之外
        //   会被 `MaidNodeEvaluator` 判为 **BLOCKED** ⇒ 女仆带着 home 模式(restrict 残留, 如中心在世界原点
        //   距离 700 万格) 时, **寻路对所有格子失败 ⇒ 一步不走** ✗ (实测日志: `[ORE-DBG] TLM限制: work=
        //   BlockPos{0,0,0}(距离7422822)` + `navigate=WALKING` 但 1800t 零位移)。
        //   测试女仆被传送到各处 ⇒ 必须**关 home 模式并清 restrict** (要测 home 的用例会自行再开 ✓)。
        maid.setHomeModeEnable(false);
        maid.clearRestriction();
    }

   /**
    * 清场约定 (#293, 组合修法) — 清理塔测试用过的**固定塔位** (5 处, 共 5 次方块写)。
    *
    * <p><b>两道防线缺一不可</b>:
    * <ol>
    *   <li><b>独立 batch</b> (每个塔测试一个) — 防**并行**: 同 batch 内用例并发跑, 塔会互射对方目标;</li>
    *   <li><b>本清场</b> — 防**顺序残留**: 塔方块不会自动消失, 前面批次的塔仍会射击后面用例的目标。</li>
    * </ol>
    * <p><b>为什么是固定清单</b>: ±64 立方体逐格 getBlockEntity (≈81 万次/调用) 曾把测试服务器拖垮 (#293)。
    * 塔位是已知常量 ⇒ 5 次写即可。
    */
   protected static final BlockPos[] TOWER_TEST_POSES = {
      new BlockPos(3, 1, 7), new BlockPos(7, 1, 1), new BlockPos(7, 1, 7), new BlockPos(7, 1, 11), new BlockPos(7, 1, 15),
      new BlockPos(3, 1, 3), new BlockPos(3, 1, 8), new BlockPos(3, 1, 13)
   };


   /**
    * **共享沙盒夹具构造器** (2026-09-23, 用户批准的结构性收法 ✓) —— 一次调用给夹具**自足场景** ✓。
    *
    * <p><b>为什么需要</b>: 逐夹具打补丁不收敛 ✗ (槽位一变就出新面具; 实测根因只有两条在反复换装):
    * ① 球外**水/沙砾/重力方块**塌落倒灌 ✗; ② 女仆/目标**脚下支撑**依赖宿主 ✗。合并到一处实现 ⇒ 三个族
    * (chainore / void / tower) 的几何敏感用例统一调用 ✓ (联网参考: Forge GameTests 文档 + GameTestHelper 文档
    * 的"夹具自己拥有场景"原则 ✓)。
    *
    * <p><b>做三件事</b>:
    * <ol>
    *   <li>**整幅地基板**: 以结构原点为基准, 在 {@code workY-1 .. workY-2} 铺满 **16×16** STONE
    *       (⇒ 支撑永不依赖宿主/槽位 ✓; 也覆盖"传送支撑格低一格"的坑 ✓);</li>
    *   <li>**球壳密封**: 以 {@code center} 为心, {@code (r-1)² < d² ≤ r²} 的外层一圈 → STONE
    *       (⇒ 挡水/沙/重力方块 ✓; 半径由调用方给, 兼顾"目标在结构外"的用例 ✓);</li>
    *   <li>**自证**: 抽查四角地基 + 六轴壳点 ⇒ 非 STONE 立刻红并点名 (夹具不成立不再靠跑满超时表达 ✓)。</li>
    * </ol>
    * 不影响调用方: {@code center} 与 {@code keep} 格子**跳过** (留给用例摆目标 ✓)。
    *
    * @param helper 测试助手
    * @param center 密封球心 (**绝对坐标**; 通常 = 女仆落脚点)
    * @param r      密封半径 (外圈在内侧留 {@code r-1} 格空腔)
    * @param keep   要保留原样的格子 (目标矿/落点等, **绝对坐标**)
    */
   protected static void protectFixture(GameTestHelper helper, net.minecraft.core.BlockPos center, int r,
                                        net.minecraft.core.BlockPos... keep) {
      var lvl = helper.getLevel();
      // ★ 越界保护 (2026-09-23 实测 bug ✗): 壳的 ±Y 轴点在 r=20 时会低于世界底 (minBuildHeight=-64) ⇒
      //   setBlockAndUpdate **静默失败** ⇒ 自证必红 (实测: 壳点 y=-77 ✗) ⇒ 放置与自证都**跳过越界格** ✓
      //   (底部本就由世界自身实心层封住 ⇒ 壳只需管住**侧面与上方** ✓)
      int minY = lvl.getMinBuildHeight(), maxY = lvl.getMaxBuildHeight() - 1;
      java.util.Set<net.minecraft.core.BlockPos> keepSet = new java.util.HashSet<>(java.util.List.of(keep));
      // ★ 锚点 = **center**(不是结构原点 ✗): 实测坑 —— chainore NeighbourChunk 故意把女仆放到
      //   **隔壁区块**(结构原点 +16 ⇒ 在 16×16 足迹之外 ✗) ⇒ 锚原点会漏她的支撑 ✗ ⇒ 锚 center ✓
      net.minecraft.core.BlockPos origin = center;   // 变量名沿用; 下面按 ±16 铺 (center 为心, 覆盖 33×33 ✓)
      // ⓪ **球内清场** (2026-09-23 实测补 ✓): 原型夹具是"清场球(内部 AIR) + 地板 + 球壳"三件套 ✗,
      //   而本助手首版只做了"板 + 壳" ⇒ **球内残留物(其它测试的 Barrier / 天然地形)原样留着** ✗
      //   ⇒ 实测 neo r2: `路径: Barrier Air Air Air Air` + 女仆脚下 Stone ⇒ 板在、路被残留 Barrier 挡死 ✗
      //   ⇒ 语义对齐原型: d ≤ (r-1)² 内 —— dy≤-1 填 STONE(地板 ✓), 其余填 AIR(清场 ✓); keep 格跳过 ✓
      for (int dx = -r + 1; dx <= r - 1; dx++) {
         for (int dz = -r + 1; dz <= r - 1; dz++) {
            for (int dy = -r + 1; dy <= r - 1; dy++) {
               int d2 = dx * dx + dz * dz + dy * dy;
               if (d2 > (r - 1) * (r - 1)) continue;
               net.minecraft.core.BlockPos p = center.offset(dx, dy, dz);
               if (keepSet.contains(p)) continue;
               if (p.getY() < minY || p.getY() > maxY) continue;
               lvl.setBlockAndUpdate(p, dy <= -1
                       ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                       : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
         }
      }
      // ① 整幅地基板 (结构 16×16 足迹, 两层厚)
      for (int dx = -16; dx <= 16; dx++) {
         for (int dz = -16; dz <= 16; dz++) {
            for (int dy = -1; dy >= -2; dy--) {
               net.minecraft.core.BlockPos p = origin.offset(dx, dy, dz);
               if (keepSet.contains(p)) continue;
               lvl.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
         }
      }
      // ② 球壳密封
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            for (int dy = -r; dy <= r; dy++) {
               int d2 = dx * dx + dz * dz + dy * dy;
               if (d2 > r * r || d2 <= (r - 1) * (r - 1)) continue;   // 只留外层一圈
               net.minecraft.core.BlockPos p = center.offset(dx, dy, dz);
               if (p.getY() < minY || p.getY() > maxY) continue;   // 越界: 世界外不可写 ✓ 底部由世界自身封 ✓
               if (keepSet.contains(p)) continue;
               lvl.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
         }
      }
      // ③ 自证 (地基四角 + 六轴壳点)
      java.util.List<String> bad = new java.util.ArrayList<>();
      for (int[] c : new int[][]{{-16, -16}, {16, -16}, {-16, 16}, {16, 16}}) {   // center 为心的四角 ✓
         net.minecraft.core.BlockPos p = origin.offset(c[0], -1, c[1]);
         if (!lvl.getBlockState(p).is(net.minecraft.world.level.block.Blocks.STONE)) {
            bad.add("地基角" + p.toShortString());
         }
      }
      for (int[] d : new int[][]{{r, 0, 0}, {-r, 0, 0}, {0, r, 0}, {0, -r, 0}, {0, 0, r}, {0, 0, -r}}) {
         net.minecraft.core.BlockPos p = center.offset(d[0], d[1], d[2]);
         if (p.getY() < minY || p.getY() > maxY) { continue; }   // 越界壳点: 放置时已跳过 ⇒ 自证也跳过 ✓
         if (!lvl.getBlockState(p).is(net.minecraft.world.level.block.Blocks.STONE)) {
            bad.add("壳点" + p.toShortString());
         }
      }
      if (!bad.isEmpty()) {
         helper.fail("[夹具自证/protectFixture] 沙盒不成立: " + bad
                 + " ⇒ 沙盒构造器未生效(被后续改写?) ⇒ 会复现'塌落/倒灌/悬空'类假红 ✗");
      }
   }

   protected static void clearTowerTestBlocks(GameTestHelper helper) {
      for (BlockPos p : TOWER_TEST_POSES) {
         helper.getLevel().setBlockAndUpdate(helper.absolutePos(p), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
      }
   }

    /** v79.62.2 compat 冒烟: 提交 Create/CBC 任务 (字符串 submit, 不引用 mod 类) — 验证注册+validate. */
    protected static void compatSmoke(GameTestHelper helper, String task) {
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

   protected static void assertSlotPos(GameTestHelper helper, Slot slot, int expectX, int expectY, String name) {
      if (slot == null) {
         helper.fail("槽位缺失: " + name);
      } else {
         if (slot.x != expectX || slot.y != expectY) {
            helper.fail("槽位坐标漂移: " + name + " 实际(" + slot.x + "," + slot.y + ") 预期(" + expectX + "," + expectY + ") — Menu/Screen 坐标铁律");
         }
      }
   }

   protected static long countItem(EntityMaid maid, Item item) {
      long n = 0L;
      var inv = maid.getAvailableInv(true);

      for (int i = 0; i < inv.getSlots(); i++) {
         ItemStack st = inv.getStackInSlot(i);
         if (st.is(item)) {
            n += (long)st.getCount();
         }
      }

      for (ItemEntity e : maid.level().getEntitiesOfClass(ItemEntity.class, maid.getBoundingBox().inflate(8.0))) {
         if (e.getItem().is(item)) {
            n += (long)e.getItem().getCount();
         }
      }

      return n;
   }

   /**
    * mock 主人 (per-dimension **缓存复用** v79.64, 用户 2026-09-16 裁定) — UUID + getInventory() 是塔族/菜单族唯一需求。
    *
    * <p><b>为什么必须容错</b>: neo 侧 TLM 1.21.1 `EntityJoinWorldEvent.onPlayerJoinWorld` **无条件**对每个
    * 进入世界的 `ServerPlayer` 发 `SyncDataPackage` ⇒ mock 连接 (EmbeddedChannel, 未完成 play 阶段注册) 上
    * `PacketDistributor.sendToPlayer` 抛 `UnsupportedOperationException: Payload touhou_little_maid:sync_data may not be sent`
    * ⇒ 异常从 `placeNewPlayer` **中途**冒出 ⇒ 原版末尾的 `players.add(..)` / `playersByUUID.put(..)` **全被跳过**
    * ⇒ 主人**根本没注册** (塔的 `isOwnerOnline` = `getPlayerList().getPlayer(uuid) != null` 因此恒 false)。
    * (1.20.1 的 TLM 无此 handler ⇒ forge 侧不触发, 天然全绿 ✓)
    *
    * <p><b>修法</b>: 缓存 (同维度只造一次) + 捕获该单例异常后**手工补齐原版那两步**
    * ⇒ 主人在 playerList 里 ⇒ 塔 resolveOwner/契约测试语义贴近生产 ✓ (只动夹具, 不碰 defense 产品代码 ✓)
    */
   protected static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, ServerPlayer> MOCK_OWNERS =
           new HashMap<>();

   protected static Player registeredOwner(GameTestHelper helper) {
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = helper.getLevel().dimension();
      ServerPlayer cached = MOCK_OWNERS.get(dim);
      if (cached != null && cached.level() == helper.getLevel()) return cached;

      ServerPlayer srvPlayer;
//? if 1.20.1 {
      srvPlayer = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "test-mock-player")) {
         @Override public boolean isSpectator() { return false; }
         @Override public boolean isCreative() { return false; }
      };
//?} else {
      srvPlayer = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "test-mock-player"), net.minecraft.server.level.ClientInformation.createDefault()) {
         @Override public boolean isSpectator() { return false; }
         @Override public boolean isCreative() { return false; }
      };
//?}
      Connection conn = new Connection(PacketFlow.SERVERBOUND);
      new EmbeddedChannel(new ChannelHandler[]{conn});
//? if 1.20.1 {
      try {
         helper.getLevel().getServer().getPlayerList().placeNewPlayer(conn, srvPlayer);
      } catch (UnsupportedOperationException tlmJoinPayload) {
         registerOwnerFallback(helper, srvPlayer, tlmJoinPayload);
      }
//?} else {
      try {
         helper.getLevel().getServer().getPlayerList().placeNewPlayer(conn, srvPlayer, net.minecraft.server.network.CommonListenerCookie.createInitial(srvPlayer.getGameProfile(), false));
      } catch (UnsupportedOperationException tlmJoinPayload) {
         registerOwnerFallback(helper, srvPlayer, tlmJoinPayload);
      }
//?}
      // ★ v79.64 修「塔射了却不掉耐久」(诊断实证: dmg=0/384 · infinite=true · instabuild=true):
      //   新造的 ServerPlayer 按**服务器默认游戏模式**(测试服=创造) 设 abilities ⇒ instabuild=true
      //   ⇒ 原版 `ItemStack.hurtAndBreak` 的 `!player.hasInfiniteMaterials()` 闸门为假 ⇒ **不扣耐久** ✗
      //   (这是**原版语义** — 创造玩家用工具本就不掉耐久; 不是塔的 bug) ⇒ 夹具必须给**生存式**主人 ✓
      //   ⚠ 不能用 setGameMode(SURVIVAL) — 它内部 `connection.send(...)` 而 mock 的 connection 是 null ⇒ NPE ✗
      //   1.20.1: instabuild 即 `hasInfiniteMaterials()` 全量; 1.21: 同源 (Player L1484 `return this.abilities.instabuild`) ✓
      srvPlayer.getAbilities().instabuild = false;
      srvPlayer.getAbilities().mayfly = false;
      srvPlayer.getAbilities().flying = false;
      MOCK_OWNERS.put(dim, srvPlayer);
      return srvPlayer;
   }

   /**
    * 容错注册 — 原版 `PlayerList.placeNewPlayer` 末尾两步 (在该事件之后) 的**等价子集** (v79.64)。
    *
    * <p><b>只补 `playersByUUID`, 故意不进 `players`</b> ✓ (2026-09-16 实测裁定):
    * ① 塔的 `isOwnerOnline` / `DefenseTowerFire.resolveOwner` 走 `getPlayer(uuid)` = `playersByUUID.get(uuid)` (1.21 源码 L899),
    *    与 `players` 列表无关 ⇒ 补 UUID 表即满足全部消费方 ✓;
    * ② 反例实证: 进了 `players` 后, 服务端 `broadcastAll` 会遍历它 `connection.send(..)` (L549-553) — 而 mock 的
    *    `connection` 在异常路径上**是 null** (从没构造 `ServerGamePacketListenerImpl`) ⇒ 抛 NPE / 网络噪音
    *    ⇒ **污染其它无关用例** ✗ (实测第二跑冒出 `lmachainorenearbeforefar` / `lmaenginepanicisolation` 两例新失败 ✓)。
    *
    * <p>步骤 (private 字段) 走**反射**: 同名 ≠ 可访问 (直接写字段名首轮编译即被拒 ✓) 且无公开 putter;
    * dev 环境为 mojmap 名 (gametest 不走生产 SRG jar ✓) — 与 `spawnInvulnerableTime` 反射同范式 ✓。
    */
   protected static void registerOwnerFallback(GameTestHelper helper, ServerPlayer p, RuntimeException cause) {
      try {
         Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
         f.setAccessible(true);
         @SuppressWarnings("unchecked")
         Map<UUID, ServerPlayer> byUuid = (Map<UUID, ServerPlayer>) f.get(helper.getLevel().getServer().getPlayerList());
         byUuid.put(p.getUUID(), p);
      } catch (ReflectiveOperationException reflectFail) {
         LittleMaidMoreAction.LOGGER.warn("[GAMETEST] mock 主人 UUID 表反射写入失败 (owner 解析将不命中): {}", reflectFail.toString());
      }
      LittleMaidMoreAction.LOGGER.warn(
              "[GAMETEST] mock 主人注册被 TLM join 包打断 (neo 无客户端连接): {} — 已只补 playersByUUID (owner 可解析, 不进 players 免污染) (非产品缺陷)",
              cause.getMessage());
   }

   protected static CompoundTag readDropData(GameTestHelper helper, ItemStack stack) {
//? if 1.20.1 {
      return stack.hasTag() ? stack.getTag().getCompound("DefenseTowerExtraData") : null;
//?} else {
      var cd = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
      return cd.copyTag().contains("DefenseTowerExtraData") ? cd.copyTag().getCompound("DefenseTowerExtraData") : null;
//?}
   }

    /** v79.62.5 恢复用 mock player (stonecutter 双版本) — smithing/defense 测试用 */
    protected static net.minecraft.world.entity.player.Player mockPlayer(GameTestHelper helper) {
//? if 1.20.1 {
        return helper.makeMockSurvivalPlayer();
//?} else {
        return helper.makeMockPlayer(GameType.DEFAULT_MODE);
//?}
    }

    /** 读熔炉输入槽数量 (0 = 未入料; -1 = 方块实体缺失) — 供过滤器用例断言 */
    protected static int furnaceInputCount(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockEntity(pos)
                instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity f
                ? f.getItem(0).getCount() : -1;
    }

    /** 容器内是否含某物品 — 过滤器用例共用夹具断言 */
    protected static boolean chestHas(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
        var h = ContainerOutput.getHandler(helper.getLevel(), pos);
        if (h == null) return false;
        for (int i = 0; i < h.getSlots(); i++) {
            if (h.getStackInSlot(i).is(item)) return true;
        }
        return false;
    }

    /**
     * 手办模型数据**逐步断言**助手 (v79.66r): 每调用一个交互入口后就查一次
     * ⇒ **失败信息直接点名是哪一步改的** ✓ (键 = 客户端渲染会读的那些 ✓)
     */
    protected static void assertStatueModelUnchanged(GameTestHelper helper,
            com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlockEntity tower,
            CompoundTag before, String step) {
        CompoundTag after = tower.getExtraData();
        for (String k : new String[]{"id", "ModelId", "YsmModelId", "IsYsmModel", "Owner", "CustomName"}) {
            String b = before.contains(k) ? before.get(k).toString() : "(无)";
            String a = after.contains(k) ? after.get(k).toString() : "(无)";
            if (!b.equals(a)) {
                helper.fail("【" + step + "】改变了手办模型数据 键=" + k + " 前=" + b + " 后=" + a
                        + " ⇒ 这就是「手办变模型」的写入点 ✗");
                return;
            }
        }
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-GOHEI-STATUE] 步骤[{}] 后模型数据仍一致 ✓", step);
    }

    // ⚠ 2026-09-18 已删除用例 `lmaVoidUnloadRestoresSchedule` (v79.69.2 加的"卸载还原调度坐标"回归):
    //   该特性因引入 neo 回归 (`lmavoidexcavationmultimaid` dug=0/4) **整体回滚** ⇒ 用例失去被测对象。
    //   修法重做时同步重建该用例 (断言: 任务结束后 work/idle/sleep 还原 + 标记清除) — 见错题 #357
}
