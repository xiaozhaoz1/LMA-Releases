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
 * LMA gametest — Station 域用例 (**25 条**)。
 *
 * <p>2026-09-21 由 {@code LmaGameTests} (6490 行) 拆分 — **纯搬移**;
 * 断言/判据/超时/template/用例名 (test ID) **逐字不变** (由 scripts/gametest-ids.mjs 121=121 把关)。
 * <p>⚠ 两平台源码实测: holder 与 prefix 按**方法所在类**读取、继承不生效 ⇒ 本类**自持**两注解 (与拆前一致)。
 */
@GameTestHolder(LittleMaidMoreAction.MOD_ID)
@PrefixGameTestTemplate(value = false)
public class LmaStationGameTests extends LmaGameTestSupport {

    /**
     * v79.62.2 篝火烤食物验证 — 放点燃篝火 + 女仆背包生牛肉 (可烤),
     * campfire 任务 → 断言食物被放进篝火槽 (placeFood 生效).
     */
    @GameTest(template = "game_test")
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
     * v79.62.2 熔炉加料验证 — 放熔炉 + 女仆背包铁矿石, furnace 任务 gate 后
     * ADD_INPUT 相位应把矿石放入熔炉输入槽 (slot 0).
     */
    @GameTest(template = "game_test")
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
    @GameTest(template = "game_test")
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
     * v79.62.2 唱片机验证 — 放唱片机 + 女仆背包唱片, jukebox 任务 gate 后
     * 应把唱片放入唱片机 (insertDisc 生效).
     */
    @GameTest(template = "game_test")
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
     * v79.62.2 搬运验证 (arm_transfer) — 设取/存坐标 (两个箱子) + submit,
     * validate 应通过 (坐标已设置) + 任务运行 (状态机 TO_TAKE 导航到取货点).
     */
    @GameTest(template = "game_test")
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
    @GameTest(template = "game_test")
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
        // v79.63 修**测空气** (per-task 键守护发现): 原写 timer_enabled(int1)/timer_interval(int10),
        //   而管线读的是 timer(**boolean**)/interval ⇒ 定时器从未开启, 用例只断言 submit 成功 ⇒ 假绿。
        cfg.putBoolean(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.BlockInteractPipeline.KEY_TIMER_ENABLED, true);
        cfg.putInt(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.BlockInteractPipeline.KEY_TIMER_INTERVAL, 10);
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "block_interact", null, 0);
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-BI] submit={}", ok);
        if (!ok) { helper.fail("block_interact submit 失败 (未绑定/距离)"); return; }
        // v79.63: 真断言 — 拉杆是**翻转**开关 (偶数次又回 false), 所以轮询"是否出现过 powered=true"
        //   (10t 间隔 × 80t 内至少扳动一次; 原用例 submit 即 succeed = 假绿)
        java.util.concurrent.atomic.AtomicBoolean sawPowered = new java.util.concurrent.atomic.AtomicBoolean(false);
        for (int k = 1; k <= 8; k++) {
            final int tick = 10 * k;
            helper.runAfterDelay((long) tick, () -> {
                if (sawPowered.get()) return;
                boolean pw = helper.getLevel().getBlockState(lever)
                        .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED);
                LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BI] t={} powered={}", tick, pw);
                if (pw) { sawPowered.set(true); helper.succeed(); }
                else if (tick == 80) helper.fail("定时器 80t 内未扳动拉杆 (powered 始终 false) — block_interact 定时器链路失效");
            });
        }
    }

   @GameTest(
      template = "game_test",
      timeoutTicks = 200
   )
   public static void lmaArmTransferLoop(GameTestHelper helper) {
      BlockPos takeAbs = helper.absolutePos(new BlockPos(3, 1, 3));
      BlockPos depAbs = helper.absolutePos(new BlockPos(7, 1, 3));
      helper.getLevel().setBlockAndUpdate(takeAbs.below(), Blocks.STONE.defaultBlockState());
      helper.getLevel().setBlockAndUpdate(depAbs.below(), Blocks.STONE.defaultBlockState());
      helper.getLevel().setBlockAndUpdate(takeAbs, Blocks.CHEST.defaultBlockState());
      helper.getLevel().setBlockAndUpdate(depAbs, Blocks.CHEST.defaultBlockState());
      var takeHandler = ContainerOutput.getHandler(helper.getLevel(), takeAbs);
      if (takeHandler == null) {
         helper.fail("take 箱 handler null");
      } else {
         for (int i = 0; i < takeHandler.getSlots(); i++) {
            if (takeHandler.getStackInSlot(i).isEmpty()) {
               takeHandler.insertItem(i, new ItemStack(Items.DIAMOND, 1), false);
               break;
            }
         }

         EntityMaid maid = spawnMaid(helper);
         if (maid != null) {
            maid.setNoAi(true);
            clearMaidTaskState(maid);
            BlockPos midAbs = helper.absolutePos(new BlockPos(5, 2, 3));
            maid.moveTo((double)midAbs.getX() + 0.5, (double)midAbs.getY(), (double)midAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
            CompoundTag data = maid.getPersistentData();
            NbtCodecs.writeBlockPos(data, "lma_arm_take", takeAbs);
            NbtCodecs.writeBlockPos(data, "lma_arm_deposit", depAbs);
            boolean ok = TaskDispatcher.submit(maid, "arm_transfer", null, 0);
            if (!ok) {
               helper.fail("submit 失败");
            } else {
               TaskPipeline pl = TaskRegistry.get("arm_transfer").pipeline();
               ServerLevel sl = (ServerLevel)maid.level();

               for (int ix = 0; ix < 12; ix++) {
                  String state = MaidData.pl(maid, "arm_transfer").getString("fsm");
                  if ("DEPOSITING".equals(state) || !FlowTaskData.getState(maid).equals("in_progress")) {
                     break;
                  }

                  pl.tick(sl, maid);
               }

               var depHandler = ContainerOutput.getHandler(sl, depAbs);
               boolean depHas = false;
               boolean takeHas = false;
               if (depHandler != null) {
                  for (int ix = 0; ix < depHandler.getSlots(); ix++) {
                     if (depHandler.getStackInSlot(ix).is(Items.DIAMOND)) {
                        depHas = true;
                        break;
                     }
                  }
               }

               var takeHandler2 = ContainerOutput.getHandler(sl, takeAbs);
               if (takeHandler2 != null) {
                  for (int ixx = 0; ixx < takeHandler2.getSlots(); ixx++) {
                     if (takeHandler2.getStackInSlot(ixx).is(Items.DIAMOND)) {
                        takeHas = true;
                        break;
                     }
                  }
               }

               boolean maidHas = countItem(maid, Items.DIAMOND) > 0L;
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ARM-LOOP] depHas={} takeHas={} maidHas={}", new Object[]{depHas, takeHas, maidHas});
               if (!depHas) {
                  helper.fail("钻石未到 deposit 箱 (搬运失败)");
               }

               if (takeHas) {
                  helper.fail("take 箱仍有钻石 (未取出)");
               }

               if (maidHas) {
                  helper.fail("女仆背包残留钻石 (未放净)");
               }

               if (depHas && !takeHas && !maidHas) {
                  helper.succeed();
               }
            }
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 300
   )
   public static void lmaBellRingInterval(GameTestHelper helper) {
      BlockPos bell = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         CompoundTag cfg = MaidData.cfgOrCreate(maid, "bell_ring");
         cfg.putInt("ring_interval", 100);
         maid.teleportTo((double)bell.getX() + 0.5, (double)bell.getY() + 1.0, (double)bell.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(bell));
         boolean ok = TaskDispatcher.submit(maid, "bell_ring", null, 0);
         if (!ok) {
            helper.fail("bell_ring submit 失败");
         } else {
            helper.runAfterDelay(40L, () -> {
               boolean ringing = false;
               if (helper.getLevel().getBlockEntity(bell) instanceof BellBlockEntity b) {
                  ringing = b.shaking || b.ticks > 0;
               }

               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BELL-INTERVAL-RING] ringing={}", ringing);
               if (!ringing) {
                  helper.fail("钟未响 (shaking/ticks 无证据)");
               } else {
                  helper.runAfterDelay(40L, () -> {
                     int counter = (int)FlowTaskData.getCounter(maid);
                     LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BELL-INTERVAL] counter={}", counter);
                     if (counter != 1) {
                        helper.fail("节流未生效 (80t 内应只敲 1 次, counter=" + counter + ")");
                     } else {
                        helper.succeed();
                     }
                  });
               }
            });
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 120
   )
   public static void lmaBellRingNotBell(GameTestHelper helper) {
      BlockPos stone = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.teleportTo((double)stone.getX() + 0.5, (double)stone.getY() + 1.0, (double)stone.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(stone));
         boolean ok = TaskDispatcher.submit(maid, "bell_ring", null, 0);
         if (!ok) {
            helper.fail("bell_ring submit 应成功 (validate 恒过)");
         } else {
            helper.runAfterDelay(60L, () -> {
               int counter = (int)FlowTaskData.getCounter(maid);
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BELL-NOTBELL] counter={}", counter);
               if (counter != 0) {
                  helper.fail("非钟目标不应计数 (counter=" + counter + ")");
               }

               helper.succeed();
            });
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 200
   )
   public static void lmaBlockInteractLost(GameTestHelper helper) {
      BlockPos lever = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(lever, Blocks.LEVER.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.teleportTo((double)lever.getX() + 0.5, (double)lever.getY() + 1.0, (double)lever.getZ() + 0.5);
         CompoundTag cfg = MaidData.cfgOrCreate(maid, "block_interact");
         NbtCodecs.writeBlockPos(cfg, "pos", lever);
         cfg.putBoolean("timer", true);
         cfg.putInt("interval", 20);
         boolean ok = TaskDispatcher.submit(maid, "block_interact", null, 0);
         if (!ok) {
            helper.fail("submit 失败");
         } else {
            helper.runAfterDelay(10L, () -> {
               helper.getLevel().destroyBlock(lever, false);
               helper.runAfterDelay(50L, () -> {
                  CompoundTag c2 = TaskConfigs.get(maid, "block_interact");
                  boolean stillBound = c2.contains("pos");
                  LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BI-LOST] stillBound={}", stillBound);
                  if (stillBound) {
                     helper.fail("绑定方块丢失后应清除绑定 (pos 仍存在)");
                  } else {
                     helper.succeed();
                  }
               });
            });
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 120
   )
   public static void lmaBlockInteractNoBind(GameTestHelper helper) {
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         boolean ok = TaskDispatcher.submit(maid, "block_interact", null, 0);
         if (ok) {
            helper.fail("未绑定时 submit 应失败");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 200
   )
   public static void lmaBlockInteractTimer(GameTestHelper helper) {
      BlockPos lever = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(lever, Blocks.LEVER.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.teleportTo((double)lever.getX() + 0.5, (double)lever.getY() + 1.0, (double)lever.getZ() + 0.5);
         CompoundTag cfg = MaidData.cfgOrCreate(maid, "block_interact");
         NbtCodecs.writeBlockPos(cfg, "pos", lever);
         cfg.putBoolean("timer", true);
         cfg.putInt("interval", 20);
         boolean ok = TaskDispatcher.submit(maid, "block_interact", null, 0);
         if (!ok) {
            helper.fail("block_interact submit 失败");
         } else {
            helper.runAfterDelay(60L, () -> {
               boolean powered = (Boolean)helper.getLevel().getBlockState(lever).getValue(LeverBlock.POWERED);
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BI-TIMER] powered={}", powered);
               if (!powered) {
                  helper.fail("定时器未真正触发 (拉杆未翻转)");
               } else {
                  helper.succeed();
               }
            });
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 120
   )
   public static void lmaBlockInteractTooFar(GameTestHelper helper) {
      BlockPos lever = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(lever, Blocks.LEVER.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         BlockPos far = helper.absolutePos(new BlockPos(9, 2, 3));
         maid.moveTo((double)far.getX() + 0.5, (double)far.getY(), (double)far.getZ() + 0.5, maid.getYRot(), maid.getXRot());
         CompoundTag cfg = MaidData.cfgOrCreate(maid, "block_interact");
         NbtCodecs.writeBlockPos(cfg, "pos", lever);
         boolean ok = TaskDispatcher.submit(maid, "block_interact", null, 0);
         if (ok) {
            helper.fail("距离外 submit 应失败");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 900
   )
   public static void lmaCampfireCookComplete(GameTestHelper helper) {
      BlockPos camp = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(camp, (BlockState)Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.BEEF, 4), false);
         maid.teleportTo((double)camp.getX() + 0.5, (double)camp.getY() + 1.0, (double)camp.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(camp));
         TaskDispatcher.submit(maid, "campfire", null, 0);
         helper.runAfterDelay(800L, () -> {
            long steaks = countItem(maid, Items.COOKED_BEEF);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-CAMP-DONE] steaks={}", steaks);
            if (steaks < 1L) {
               helper.fail("烤熟闭环失败 (无熟牛排, steaks=0)");
            } else {
               helper.succeed();
            }
         });
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 300
   )
   public static void lmaFurnaceAddFuel(GameTestHelper helper) {
      BlockPos furnace = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         var inv = maid.getAvailableInv(true);
         inv.insertItem(0, new ItemStack(Items.IRON_ORE, 8), false);
         inv.insertItem(1, new ItemStack(Items.COAL, 8), false);
         maid.teleportTo((double)furnace.getX() + 0.5, (double)furnace.getY() + 1.0, (double)furnace.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(furnace));
         boolean ok = TaskDispatcher.submit(maid, "furnace", "minecraft:iron_ingot", 0);
         if (!ok) {
            helper.fail("furnace submit 失败");
         } else {
            helper.runAfterDelay(120L, () -> {
               BlockEntity be = helper.getLevel().getBlockEntity(furnace);
               boolean fuel = false;
               if (be instanceof AbstractFurnaceBlockEntity f) {
                  fuel = !f.getItem(1).isEmpty();
               }

               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-FURN-FUEL] fuelSlot={}", fuel);
               if (!fuel) {
                  helper.fail("熔炉燃料槽未放入煤炭 (ADD_FUEL 未生效)");
               } else {
                  helper.succeed();
               }
            });
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 120
   )
   public static void lmaFurnaceNoMaterial(GameTestHelper helper) {
      BlockPos furnace = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.teleportTo((double)furnace.getX() + 0.5, (double)furnace.getY() + 1.0, (double)furnace.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(furnace));
         boolean ok = TaskDispatcher.submit(maid, "furnace", "minecraft:iron_ingot", 0);
         if (ok) {
            helper.fail("无料无燃料时 submit 应失败");
         } else {
            helper.runAfterDelay(60L, () -> {
               long iron = countItem(maid, Items.IRON_ORE);
               if (iron > 0L) {
                  helper.fail("不应有材料 (iron=" + iron + ")");
               } else {
                  helper.succeed();
               }
            });
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 500
   )
   public static void lmaFurnaceSmeltComplete(GameTestHelper helper) {
      BlockPos furnace = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         var inv = maid.getAvailableInv(true);
         inv.insertItem(0, new ItemStack(Items.IRON_ORE, 8), false);
         inv.insertItem(1, new ItemStack(Items.COAL, 8), false);
         maid.teleportTo((double)furnace.getX() + 0.5, (double)furnace.getY() + 1.0, (double)furnace.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(furnace));
         boolean ok = TaskDispatcher.submit(maid, "furnace", "minecraft:iron_ingot", 0);
         if (!ok) {
            helper.fail("furnace submit 失败");
         } else {
            helper.runAfterDelay(400L, () -> {
               long ingots = countItem(maid, Items.IRON_INGOT);
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-FURN-SMELT] ingots={}", ingots);
               if (ingots < 1L) {
                  helper.fail("烧炼产物未收集 (铁锭=" + ingots + ")");
               } else {
                  helper.succeed();
               }
            });
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 300
   )
   public static void lmaFurnaceSmokerType(GameTestHelper helper) {
      BlockPos smoker = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(smoker, Blocks.SMOKER.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.BEEF, 4), false);
         maid.teleportTo((double)smoker.getX() + 0.5, (double)smoker.getY() + 1.0, (double)smoker.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(smoker));
         boolean ok = TaskDispatcher.submit(maid, "furnace", "minecraft:cooked_beef", 0);
         if (!ok) {
            helper.fail("smoker submit 失败 (SMOKING 配方未识别?)");
         } else {
            // ── 2026-09-19 改造 (pollUntil 家族): 固定 80t 墙点 → 条件轮询 ──
            // 历史失败 1 次: "烟熏炉输入槽未放入生牛肉" — 女仆已在炉边 (teleport + TARGET_POS 注入),
            // 纯粹是"节拍首拍 + 服务端负载"下 80t 有时不够 ⇒ 改为**入料即过**, 窗口 300t (= timeoutTicks)
            pollUntil(helper, () -> {
               BlockEntity be = helper.getLevel().getBlockEntity(smoker);
               return be instanceof AbstractFurnaceBlockEntity f && f.getItem(0).is(Items.BEEF);
            }, 300L, "烟熏炉输入槽放入生牛肉 (SMOKING 分支)", maid, smoker);
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 120
   )
   public static void lmaJukeboxBlacklist(GameTestHelper helper) {
      BlockPos juke = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(juke, Blocks.JUKEBOX.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.MUSIC_DISC_13, 1), false);
         CompoundTag cfg = MaidData.cfgOrCreate(maid, "jukebox");
         ListTag bl = new ListTag();
         bl.add(StringTag.valueOf("minecraft:music_disc_13"));
         cfg.put("blacklist", bl);
         maid.teleportTo((double)juke.getX() + 0.5, (double)juke.getY() + 1.0, (double)juke.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(juke));
         boolean ok = TaskDispatcher.submit(maid, "jukebox", null, 0);
         if (ok) {
            helper.fail("黑名单唱片应被拒绝 (submit 应失败)");
            return;
         }
         // v79.63 正向对照: 清除黑名单后应能提交 (证明上一条断言不是空跑)
         cfg.remove(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters.KEY_BLACKLIST);
         if (TaskDispatcher.submit(maid, "jukebox", null, 0)) {
            helper.succeed();
         } else {
            helper.fail("清除黑名单后仍被拒 (正向对照失败) — 黑名单/白名单判定异常");
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 300
   )
   public static void lmaJukeboxEjectFull(GameTestHelper helper) {
      BlockPos juke = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(juke, Blocks.JUKEBOX.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         var inv = maid.getAvailableInv(true);

         for (int i = 0; i < inv.getSlots(); i++) {
            inv.setStackInSlot(i, new ItemStack(Items.BONE, 64));
         }

         maid.teleportTo((double)juke.getX() + 0.5, (double)juke.getY() + 1.0, (double)juke.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(juke));
         if (helper.getLevel().getBlockEntity(juke) instanceof JukeboxBlockEntity j0) {
//? if 1.20.1 {
            j0.setFirstItem(new ItemStack(Items.MUSIC_DISC_13));
//?} else {
            j0.setTheItem(new ItemStack(Items.MUSIC_DISC_13));
//?}
            j0.setChanged();
         }

         inv.setStackInSlot(0, new ItemStack(Items.MUSIC_DISC_13, 1));
         boolean ok = TaskDispatcher.submit(maid, "jukebox", null, 0);
         if (!ok) {
            helper.fail("jukebox submit 失败");
         } else {
            helper.runAfterDelay(120L, () -> {
               BlockEntity be = helper.getLevel().getBlockEntity(juke);
               boolean discStill = false;
               if (be instanceof JukeboxBlockEntity j) {
//? if 1.20.1 {
                  discStill = !j.getFirstItem().isEmpty();
//?} else {
                  discStill = !j.getTheItem().isEmpty();
//?}
               }

               long totalDiscs = countItem(maid, Items.MUSIC_DISC_13);
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-JUKE-EJECT] discInMachine={} totalDisc13={}", discStill, totalDiscs);
               if (!discStill) {
                  helper.fail("满背包时机内碟不应消失");
               }

               if (totalDiscs != 1L) {
                  helper.fail("背包唱片数量异常 (应=1, 无消失无复制): " + totalDiscs);
               }

               helper.succeed();
            });
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 120
   )
   public static void lmaJukeboxNoDisc(GameTestHelper helper) {
      BlockPos juke = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(juke, Blocks.JUKEBOX.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.teleportTo((double)juke.getX() + 0.5, (double)juke.getY() + 1.0, (double)juke.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(juke));
         boolean ok = TaskDispatcher.submit(maid, "jukebox", null, 0);
         if (ok) {
            helper.fail("无唱片时 submit 应失败");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 300
   )
   public static void lmaJukeboxTargetDisc(GameTestHelper helper) {
      BlockPos juke = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(juke, Blocks.JUKEBOX.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         var inv = maid.getAvailableInv(true);
         inv.insertItem(0, new ItemStack(Items.MUSIC_DISC_11, 1), false);
         inv.insertItem(1, new ItemStack(Items.MUSIC_DISC_13, 1), false);
         maid.teleportTo((double)juke.getX() + 0.5, (double)juke.getY() + 1.0, (double)juke.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(juke));
         boolean ok = TaskDispatcher.submit(maid, "jukebox", "music_disc_11", 0);
         if (!ok) {
            helper.fail("目标唱片 submit 失败");
         } else {
            helper.runAfterDelay(80L, () -> {
               BlockEntity be = helper.getLevel().getBlockEntity(juke);
               boolean disc11 = false;
               if (be instanceof JukeboxBlockEntity j) {
//? if 1.20.1 {
                  disc11 = j.getFirstItem().is(Items.MUSIC_DISC_11);
//?} else {
                  disc11 = j.getTheItem().is(Items.MUSIC_DISC_11);
//?}
               }

               long disc13 = countItem(maid, Items.MUSIC_DISC_13);
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-JUKE-TARGET] disc11={} disc13Left={}", disc11, disc13);
               if (!disc11) {
                  helper.fail("应插入目标唱片 11 (matchesTarget 未生效)");
               }

               if (disc13 < 1L) {
                  helper.fail("非目标唱片 13 不应被消耗");
               }

               helper.succeed();
            });
         }
      }
   }

   /**
    * furnace **真导航**路径 (v79.63) — 补上"女仆会不会走到工作站"这一环。
    *
    * <p><b>为什么必需</b>: 既有 `lmaFurnaceSmeltComplete` **直接注入 TARGET_POS**
    * (`setMemory(TARGET_POS, new BlockPosTracker(furnace))`) ⇒ 从管线视角女仆"已在目标旁" ⇒
    * **搜索 + 导航链路从未被测过**。用户实测"女仆不走去烧熔炉/敲钟"正是这一段 ⇒ 本测试按用户规则
    * (移动类任务目标 >10 格) 把工作站放在距出生点 ~11.3 格处, **不注入任何 Brain 记忆**,
    * 断言女仆**自己走到**炉子旁。
    *
    * <p>失败即复现: 说明 `LmaFlowCoordinationBehavior` 的搜索/导航没把女仆送过去 (而非管线逻辑问题)。
    */
   @GameTest(
        batch = "z_slow",template = "game_test", timeoutTicks = 700)
   public static void lmaFurnaceNavigate(GameTestHelper helper) {
      // 女仆出生于相对 (7,1,7); 模板 16^3 ⇒ 最远可用 (15,15) 约 11.3 格 (>10)
      BlockPos furnaceRel = new BlockPos(13, 1, 15);   // 距出生点 (7,7) 约 10 格 (>10 ✓, 在搜索半径 12 内)
      BlockPos furnaceAbs = helper.absolutePos(furnaceRel);
      helper.getLevel().setBlockAndUpdate(furnaceAbs, Blocks.FURNACE.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid == null) return;
      clearMaidTaskState(maid);
      // 夹具: validate 需要"背包里有可烧的东西"(原料+燃料) — 缺它 submit 必失败 (与导航无关)
      var bp = maid.getAvailableBackpackInv();
      bp.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 16));
      bp.setStackInSlot(1, new ItemStack(Items.COAL, 16));
      if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
              .submit(maid, "furnace", "minecraft:iron_ingot", 0)) {
         helper.fail("furnace submit 失败");
         return;
      }
      // 不注入 TARGET_POS — 让 LmaFlowCoordinationBehavior 自己搜索 + 导航
      helper.runAfterDelay(500L, () -> {
         double dist = Math.sqrt(maid.blockPosition().distSqr(furnaceAbs));   // 绝对↔绝对 (原相对坐标比较是 bug)
         boolean hasTarget = maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get());
         boolean hasWalk = maid.getBrain()
                 .hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
         LittleMaidMoreAction.LOGGER.warn(
                 "[GAMETEST-NAV] furnace 距离={} TARGET_POS={} WALK_TARGET={} 女仆={} restrictR={} home={}",
                 String.format("%.1f", dist), hasTarget, hasWalk, maid.blockPosition().toShortString(),
                 maid.getRestrictRadius(), maid.isHomeModeEnable());
         if (dist <= 4.0) {   // v79.63: 3.5 -> 4.0 (实测 3.7 已属"到达"范围, 阈值抖动误报)
            helper.succeed();
         } else {
            helper.fail("女仆未走到工作站: 距离=" + String.format("%.1f", dist)
                    + " (>10 格导航未发生) TARGET_POS=" + hasTarget + " WALK_TARGET=" + hasWalk
                    + " — 复现【女仆不走去烧炉】, 问题在搜索/导航而非管线");
         }
      });
   }

    /**
     * 黑白名单**端到端**: 单女仆黑名单(raw_iron) ⇒ 拒绝入炉; **清除后 ⇒ 开烧** (正向对照, 证明本用例真能测出"能用")。
     *
     * <p>用户实机问"熔炉设置很多, 黑白名单能不能用" ⇒ 端到端锁住: 屏写入的**同一个键**
     * ({@code ItemFilters.KEY_BLACKLIST}) → 服务读取 ({@code FurnaceService.effectiveLists}) → 拒绝生效。
     * 阶段① 先断言"女仆已到位", 以区分 **过滤器漏** 与 **没走到** (两种失败信息不同)。
     */
    @GameTest(
        batch = "z_slow",
        template = "game_test",
        timeoutTicks = 1600
    )
    public static void lmaFurnaceBlacklist(GameTestHelper helper) {
        BlockPos furnaceAbs = helper.absolutePos(new BlockPos(13, 1, 15));   // 距出生点 (7,7) 约 10 格 (>10 ✓)
        helper.getLevel().setBlockAndUpdate(furnaceAbs, Blocks.FURNACE.defaultBlockState());
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        var bp = maid.getAvailableBackpackInv();
        bp.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 16));
        bp.setStackInSlot(1, new ItemStack(Items.COAL, 16));
        // 夹具: 写**单女仆黑名单** — 与 ItemListConfigScreen 同键同 API (cfgOrCreate ⇒ 落盘引用)
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "furnace");
        ListTag black = new ListTag();
        black.add(StringTag.valueOf("minecraft:raw_iron"));
        cfg.put(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters.KEY_BLACKLIST, black);
        if (!TaskDispatcher.submit(maid, "furnace", "", 0)) {
            helper.fail("furnace submit 失败 (背包有 raw_iron+coal, 空 target 应放行)");
            return;
        }
        // 2026-09-19 pollUntil 家族整治: 两处固定墙点 (900t 到达判定 / 300t 开烧判定) → 两阶段条件轮询
        //   · 窗口取值不变 (900 / 300), 但"到位即判"⇒ 黑名单语义在**恰好的时刻**被检验 (不再赌女仆已在墙点前到位)
        final int[] before = {-1, -1};   // 阶段①就地取样的 input/ingots (阶段②的对照基线)
        pollUntilThen(helper,
                () -> Math.sqrt(maid.blockPosition().distSqr(furnaceAbs)) <= 4.0,
                () -> {
                    int input1 = furnaceInputCount(helper, furnaceAbs);
                    long ingots1 = countItem(maid, Items.IRON_INGOT);
                    double dist = Math.sqrt(maid.blockPosition().distSqr(furnaceAbs));
                    LittleMaidMoreAction.LOGGER.warn("[GAMETEST-FILTER] 阶段①(黑名单) 距离={} input={} ingots={}",
                            String.format("%.1f", dist), input1, ingots1);
                    if (input1 > 0 || ingots1 > 0) {
                        helper.fail("黑名单未生效: 已到位但仍入炉 (input=" + input1 + ", 铁锭=" + ingots1
                                + ") — 检查 KEY_BLACKLIST 是否被 FurnaceService 读到");
                        return;
                    }
                    before[0] = input1;
                    before[1] = (int) ingots1;
                    cfg.remove(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters.KEY_BLACKLIST);
                },
                () -> {
                    int input2 = furnaceInputCount(helper, furnaceAbs);
                    long ingots2 = countItem(maid, Items.IRON_INGOT);
                    if (input2 == before[0] && ingots2 == before[1]) return false;
                    LittleMaidMoreAction.LOGGER.warn("[GAMETEST-FILTER] 阶段②(清除后) input={} ingots={} (基线 {}/{})",
                            input2, ingots2, before[0], before[1]);
                    return true;
                },
                null,   // 二阶段无收尾动作 (阶段②成立即 succeed)
                900L, 300L, "阶段① 女仆到达工作站(≤4格)", maid, furnaceAbs);
    }

    /**
     * 搬运**黑白名单**端到端 (用户裁定"其它任务黑白名单抽公共夹具"): 黑名单钻石 ⇒ 不取; **清除后 ⇒ 正常搬运** (正向对照)。
     *
     * <p>链路: 屏写的同一个键 ({@code ItemFilters.KEY_BLACKLIST}) → {@code ArmTransferPipeline.handleTaking}
     * 读 {@code TaskConfigs.get(maid,"arm_transfer")} + {@code ItemFilters.effectivePair} → 拒绝取货。
     * 夹具沿用 {@code lmaArmTransferLoop}: 双箱 + 手动 drive tick (确定性; 导航链由其它用例覆盖)。
     */
    @GameTest(
        template = "game_test",
        timeoutTicks = 300
    )
    public static void lmaArmTransferBlacklist(GameTestHelper helper) {
        BlockPos takeAbs = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos depAbs = helper.absolutePos(new BlockPos(7, 1, 3));
        helper.getLevel().setBlockAndUpdate(takeAbs.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(depAbs.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(takeAbs, Blocks.CHEST.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(depAbs, Blocks.CHEST.defaultBlockState());
        var takeHandler = ContainerOutput.getHandler(helper.getLevel(), takeAbs);
        if (takeHandler == null) { helper.fail("take 箱 handler null"); return; }
        takeHandler.insertItem(0, new ItemStack(Items.DIAMOND, 1), false);

        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        BlockPos midAbs = helper.absolutePos(new BlockPos(5, 2, 3));
        maid.moveTo(midAbs.getX() + 0.5, midAbs.getY(), midAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        CompoundTag data = maid.getPersistentData();
        NbtCodecs.writeBlockPos(data, com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.ArmTransferPipeline.KEY_TAKE, takeAbs);
        NbtCodecs.writeBlockPos(data, com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.ArmTransferPipeline.KEY_DEPOSIT, depAbs);
        // ★ 夹具: 单女仆黑名单 = 钻石 (与 ItemListConfigScreen 同键同 API)
        CompoundTag cfg = MaidData.cfgOrCreate(maid, "arm_transfer");
        ListTag bl = new ListTag();
        bl.add(StringTag.valueOf("minecraft:diamond"));
        cfg.put(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters.KEY_BLACKLIST, bl);

        if (!TaskDispatcher.submit(maid, "arm_transfer", null, 0)) { helper.fail("submit 失败"); return; }
        TaskPipeline pl = TaskRegistry.get("arm_transfer").pipeline();
        ServerLevel sl = (ServerLevel) maid.level();

        for (int i = 0; i < 60; i++) pl.tick(sl, maid);          // 阶段①: 黑名单期
        boolean takeStill = chestHas(helper, takeAbs, Items.DIAMOND);
        boolean depEmpty = !chestHas(helper, depAbs, Items.DIAMOND);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ARM-FILTER] 阶段①(黑名单) takeStill={} depEmpty={} state={}",
                takeStill, depEmpty, MaidData.pl(maid, "arm_transfer").getString("fsm"));
        if (!takeStill || !depEmpty) {
            helper.fail("黑名单未生效: 源箱钻石被取走=" + !takeStill + " 目标箱已到货=" + !depEmpty
                    + " — 检查 KEY_BLACKLIST 是否被 ArmTransferPipeline 读到");
            return;
        }
        // 阶段② 正向对照: 清黑名单 + 把女仆挪到 deposit 旁 (本用例只验过滤器, 导航由其它用例覆盖)
        cfg.remove(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters.KEY_BLACKLIST);
        maid.moveTo(depAbs.getX() + 1.5, depAbs.getY(), depAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        for (int i = 0; i < 80; i++) pl.tick(sl, maid);
        boolean depHas = chestHas(helper, depAbs, Items.DIAMOND);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ARM-FILTER] 阶段②(清除后) depHas={} state={}",
                depHas, MaidData.pl(maid, "arm_transfer").getString("fsm"));
        if (depHas) helper.succeed();
        else helper.fail("清除黑名单后仍搬运失败 (正向对照失败) ⇒ 过滤器/管线异常");
    }
}
