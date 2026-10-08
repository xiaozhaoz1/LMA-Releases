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
 * LMA gametest — Harvest 域用例 (**20 条**)。
 *
 * <p>2026-09-21 由 {@code LmaGameTests} (6490 行) 拆分 — **纯搬移**;
 * 断言/判据/超时/template/用例名 (test ID) **逐字不变** (由 scripts/gametest-ids.mjs 121=121 把关)。
 * <p>⚠ 两平台源码实测: holder 与 prefix 按**方法所在类**读取、继承不生效 ⇒ 本类**自持**两注解 (与拆前一致)。
 */
@GameTestHolder(LittleMaidMoreAction.MOD_ID)
@PrefixGameTestTemplate(value = false)
public class LmaHarvestGameTests extends LmaGameTestSupport {

    /**
     * 连锁挖矿链路 (v79.53 补测): 脚下铁矿石 → 开脉 (KEY_QUEUE 写入) → 强制蓄力到点 →
     * charge 破坏 (矿变空气 + 队列闭环)。全同步驱动 ChainHarvestPipeline.tick (同一 server
     * tick 内连续调用, 女仆无机会被 AI 移动/GMPM 介入 — 实测 runAfterDelay 等待期位置漂移
     * 导致 3 格球破块失败); 覆盖可达裁剪后单轮全破语义 (v79.53 #4)。
     */
    @GameTest(template = "game_test")
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
        if (!data.contains(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute.KEY_QUEUE)) {
            helper.fail("首 tick 未开脉 (KEY_QUEUE 未写入)");
            return;
        }
        // v79.61x 状态机化: 开脉即 CHARGE (显式相位键, 与队列同生)
        if (data.getInt(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute.KEY_PHASE)
                != com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute.Phase.CHARGE.ordinal()) {
            helper.fail("开脉后相位应为 CHARGE");
            return;
        }

        // 强制蓄力到点 (手动 tick 不推进 gameTime — 直接置 end 为已过期, 免 runAfterDelay 等待漂移)
        data.putLong(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute.KEY_CHARGE_END,
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
        if (data.contains(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute.KEY_QUEUE)) {
            helper.fail("破块后 KEY_QUEUE 未清理 (闭环)");
            return;
        }
        // 状态机化: 破块后相位随队列闭环 (单点清理)
        if (data.contains(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute.KEY_PHASE)) {
            helper.fail("破块后相位键未清理 (闭环)");
            return;
        }
        helper.succeed();
    }

    /**
     * v79.62.2 远矿导航验证 — 矿放女仆 5 格外 (12,1,7 vs 女仆 7,2,7) → 应走导航过去挖.
     * 真 server tick 驱动 (runAfterDelay) 让 TLM 走路.
     *
     * <p><b>v79.63.3 断言更新 (用户裁定)</b>: 旧的"脚下 >2 格深矿"过滤已**删除** (用户: 不如直接靠
     * "女仆挖矿 ⇒ 重置跳过集" + "远目标站立点检查" 兜底) ⇒ 深矿**允许被顺手挖掉** (她下探后脚下变了),
     * 本用例只保留主断言: **5 格外的目标矿必须被挖到** (证明远矿导航仍工作)。
     */
    @GameTest(
        batch = "z_ore",template = "game_test", timeoutTicks = 900)
    public static void lmaChainOreFar(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        // v79.62.2 验证: 女仆脚下 3/4/5 格深矿 (盖住挖不到) 应被排除 —
        // 女仆去挖 5 格外 (12,1,7) 的目标矿, 脚下深矿不被挖.
        BlockPos oreRel = new BlockPos(12, 1, 7);   // 5 格外目标矿
        helper.setBlock(oreRel, net.minecraft.world.level.block.Blocks.IRON_ORE);
        maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "collect_ore", null, 0)) {
            helper.fail("collect_ore submit 失败");
            return;
        }
        BlockPos oreAbs = helper.absolutePos(oreRel);
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-OREFAR] maid={} ore={}", maid.blockPosition(), oreAbs);
        // ★ v79.64 用户裁定: 固定 runAfterDelay(400) → **条件轮询** (成立即通过; 见 pollUntil)
        pollUntil(helper, () -> mined(helper, oreAbs), 800L, "远矿被挖 " + oreAbs.toShortString(), maid, oreAbs);
    }

    /**
     * v79.62.2 砍树验证 (collect_wood) — 仿 lmaChainOre: 脚下原木 + 主手铁斧
     * → 开脉 KEY_QUEUE → 强制蓄力到点 → charge 破块 → 原木被破坏.
     */
    @GameTest(template = "game_test")
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
            if (!data.contains(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute.KEY_QUEUE)) {
                helper.fail("首 tick 未开脉 (KEY_QUEUE 未写入)");
                return;
            }
            data.putLong(com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute.KEY_CHARGE_END,
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

   @GameTest(
      template = "game_test",
      timeoutTicks = 200
   )
   public static void lmaChainOreDurabilitySwap(GameTestHelper helper) {
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         ServerLevel sl = (ServerLevel)maid.level();
         helper.setBlock(new BlockPos(7, 1, 7), Blocks.IRON_ORE);
         ItemStack almostBroken = new ItemStack(Items.IRON_PICKAXE);
         almostBroken.setDamageValue(almostBroken.getMaxDamage() - 1);
         maid.setItemInHand(InteractionHand.MAIN_HAND, almostBroken);
         maid.getAvailableInv(true).insertItem(1, new ItemStack(Items.IRON_PICKAXE), false);
         if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) {
            helper.fail("collect_ore submit 失败");
         } else {
            TaskPipeline pl = TaskRegistry.get("collect_ore").pipeline();
            CompoundTag data = maid.getPersistentData();
            pl.tick(sl, maid);
            if (!data.contains("lma_chain_queue")) {
               helper.fail("剩 1 点耐久的工具未开脉 (reserve=0 应可用)");
            } else {
               data.putLong("lma_chain_charge_end", sl.getGameTime() - 1L);
               BlockPos maidAbs = helper.absolutePos(new BlockPos(7, 2, 7));
               maid.moveTo((double)maidAbs.getX() + 0.5, (double)maidAbs.getY(), (double)maidAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
               pl.tick(sl, maid);
               if (!sl.getBlockState(helper.absolutePos(new BlockPos(7, 1, 7))).isAir()) {
                  helper.fail("挖矿后矿石未被破坏");
               } else {
                  boolean pickBroken = maid.getMainHandItem().isEmpty();
                  LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE-DUR] pickBroken={}", pickBroken);
                  if (!pickBroken) {
                     helper.fail("剩 1 点耐久的镐挖完 1 块后应损坏消失 (hurtAndBreak 扣到顶)");
                  } else {
                     helper.succeed();
                  }
               }
            }
         }
      }
   }

   @GameTest(
        batch = "z_ore",
      template = "game_test",
      timeoutTicks = 200
   )
   public static void lmaChainOreSwapNextVein(GameTestHelper helper) {
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         ServerLevel sl = (ServerLevel)maid.level();
         helper.setBlock(new BlockPos(7, 1, 7), Blocks.IRON_ORE);
         helper.setBlock(new BlockPos(7, 1, 8), Blocks.IRON_ORE);
         ItemStack almostBroken = new ItemStack(Items.IRON_PICKAXE);
         almostBroken.setDamageValue(almostBroken.getMaxDamage() - 1);
         maid.setItemInHand(InteractionHand.MAIN_HAND, almostBroken);
         maid.getAvailableInv(true).insertItem(1, new ItemStack(Items.IRON_PICKAXE), false);
         if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) {
            helper.fail("collect_ore submit 失败");
         } else {
            TaskPipeline pl = TaskRegistry.get("collect_ore").pipeline();
            pl.tick(sl, maid);
            CompoundTag data = maid.getPersistentData();
            data.putLong("lma_chain_charge_end", sl.getGameTime() - 1L);
            BlockPos idx = helper.absolutePos(new BlockPos(7, 2, 7));
            maid.moveTo((double)idx.getX() + 0.5, (double)idx.getY(), (double)idx.getZ() + 0.5, maid.getYRot(), maid.getXRot());
            pl.tick(sl, maid);
            if (!maid.getMainHandItem().isEmpty()) {
               helper.fail("首块挖后主手镐未消失");
            } else {
               pl.tick(sl, maid);
               data.putLong("lma_chain_charge_end", sl.getGameTime() - 1L);
               pl.tick(sl, maid);
               boolean newPick = maid.getMainHandItem().is(Items.IRON_PICKAXE);
               boolean secondDug = sl.getBlockState(helper.absolutePos(new BlockPos(7, 1, 8))).isAir();
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE-SWAP] newPick={} secondDug={}", newPick, secondDug);
               if (!newPick) {
                  helper.fail("换新镐失败 (ensureToolFor 未从背包换)");
               }

               if (!secondDug) {
                  helper.fail("第二块矿未挖 (新镐未生效)");
               }

               if (newPick && secondDug) {
                  helper.succeed();
               }
            }
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 200
   )
   public static void lmaWoodCollectNoAxe(GameTestHelper helper) {
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         ServerLevel sl = (ServerLevel)maid.level();
         boolean natureCheck = (Boolean)ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK.get();
         ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK.set(false);

         try {
            helper.setBlock(new BlockPos(7, 1, 7), Blocks.OAK_LOG);
            if (!TaskDispatcher.submit(maid, "collect_wood", null, 0)) {
               helper.fail("collect_wood submit 失败 (无斧应可提交)");
               return;
            }

            TaskPipeline pl = TaskRegistry.get("collect_wood").pipeline();
            CompoundTag data = maid.getPersistentData();
            pl.tick(sl, maid);
            boolean opened = data.contains("lma_chain_queue");
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-WOOD-NOAXE] opened={}", opened);
            if (!opened) {
               helper.fail("无斧应能开脉 (canHarvest=true 无斧慢砍语义)");
            } else {
               helper.succeed();
            }
         } finally {
            ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK.set(natureCheck);
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 200
   )
   public static void lmaWoodCollectTree(GameTestHelper helper) {
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         ServerLevel sl = (ServerLevel)maid.level();
         BlockPos log1 = helper.absolutePos(new BlockPos(7, 1, 7));
         BlockPos log2 = helper.absolutePos(new BlockPos(7, 2, 7));
         BlockPos log3 = helper.absolutePos(new BlockPos(7, 3, 7));
         BlockPos leaves = helper.absolutePos(new BlockPos(8, 3, 7));

         for (BlockPos p : new BlockPos[]{log1, log2, log3}) {
            helper.getLevel().setBlockAndUpdate(p, Blocks.OAK_LOG.defaultBlockState());
         }

         helper.getLevel().setBlockAndUpdate(leaves, Blocks.OAK_LEAVES.defaultBlockState());
         maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
         if (!TaskDispatcher.submit(maid, "collect_wood", null, 0)) {
            helper.fail("collect_wood submit 失败");
         } else {
            TaskPipeline pl = TaskRegistry.get("collect_wood").pipeline();
            CompoundTag data = maid.getPersistentData();
            pl.tick(sl, maid);
            if (!data.contains("lma_chain_queue")) {
               helper.fail("天然树未开脉 (KEY_QUEUE 未写入)");
            } else {
               long[] queue = data.getLongArray("lma_chain_queue");
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-WOOD-TREE] queueLen={}", queue.length);
               if (queue.length < 3) {
                  helper.fail("连锁未包含整树 (queue=" + queue.length + " 应≥3)");
               } else {
                  data.putLong("lma_chain_charge_end", sl.getGameTime() - 1L);
                  BlockPos maidAbs = helper.absolutePos(new BlockPos(7, 2, 7));
                  maid.moveTo((double)maidAbs.getX() + 0.5, (double)maidAbs.getY(), (double)maidAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
                  pl.tick(sl, maid);
                  boolean allDug = sl.getBlockState(log1).isAir() && sl.getBlockState(log2).isAir() && sl.getBlockState(log3).isAir();
                  LittleMaidMoreAction.LOGGER.warn("[GAMETEST-WOOD-TREE] allDug={}", allDug);
                  if (!allDug) {
                     helper.fail("连锁破坏未清整树 (3 原木应全空)");
                  } else {
                     helper.succeed();
                  }
               }
            }
         }
      }
   }

    /**
     * 挖矿选目标**排除埋死矿** (v79.63 用户实测「隔墙矿一直卡住」):
     * 埋死在石层里的矿 (六面无开口) 不应被选中; 同场一个**裸露**矿应被正常挖掉 (正向对照)。
     *
     * <p>覆盖 {@code ChainScan.hasOpenFace} 的两处过滤 (远扫 posFilter + 近扫 ±4 循环)。
     * 链路: collect_ore 任务 → 手动 drive tick → 断言"裸露矿被挖 / 埋死矿原封不动"。
     */
    @GameTest(
        batch = "z_ore",
        template = "game_test",
        timeoutTicks = 400
    )
    public static void lmaChainOreBuriedSkipped(GameTestHelper helper) {
        // 埋死矿: 3×3 石头块中心放铁矿 (六面皆石 ⇒ 无开口)
        BlockPos buriedAbs = helper.absolutePos(new BlockPos(3, 1, 3));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.getLevel().setBlockAndUpdate(buriedAbs.offset(dx, dy, dz), Blocks.STONE.defaultBlockState());
                }
            }
        }
        helper.getLevel().setBlockAndUpdate(buriedAbs, Blocks.IRON_ORE.defaultBlockState());
        // 裸露矿 (正向对照): 地表一块
        BlockPos exposedAbs = helper.absolutePos(new BlockPos(7, 1, 3));
        helper.getLevel().setBlockAndUpdate(exposedAbs.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(exposedAbs, Blocks.IRON_ORE.defaultBlockState());

        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        maid.setNoAi(true);
        // 站位: 两矿之间, 都在 4 格内可达范围 (埋死矿靠"无开口"被排除, 不靠距离)
        BlockPos footAbs = helper.absolutePos(new BlockPos(5, 1, 3));
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) {
            helper.fail("collect_ore submit 失败 (背包需镐)");
            return;
        }
        // 必须用真实游戏刻推进: 蓄力(CHARGE)按 world.getGameTime() 到期 — 手动连打 tick 时
        // gameTime 不走 ⇒ 永不到期 (v79.63 自记: 首版用例正是踩了这个坑, 只开脉不破块)
        helper.runAfterDelay(160L, () -> {
        boolean buriedIntact = helper.getLevel().getBlockState(buriedAbs).is(Blocks.IRON_ORE);
        boolean exposedGone = !helper.getLevel().getBlockState(exposedAbs).is(Blocks.IRON_ORE);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BURIED] 埋死矿原封={} 裸露矿已挖={} state={}",
                buriedIntact, exposedGone, com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                        .pl(maid, "collect_ore").getString("phase"));
        if (!buriedIntact) {
            helper.fail("埋死矿被选中/挖了 (应被 hasOpenFace 过滤) — 隔墙矿卡住问题未修");
        } else if (!exposedGone) {
            helper.fail("裸露矿没被挖 (正向对照失败) ⇒ 过滤过严或任务链异常");
        } else {
            helper.succeed();
        }
        });   // runAfterDelay (真实刻度 — 手动连打 tick 时 gameTime 不走, 蓄力永不到期)
    }

    /**
     * **墙上/头顶矿的强制挖穿** (v79.63.3 ②③ — 用户实测「矿在墙壁上女仆不会向上挖」):
     * 目标在上方且深度内, 但**存在可站立邻位** (旧 {@code digUp} 会因此拒绝挖穿 ⇒ 变成"走去撞墙") ⇒
     * 走路失败后的兜底 {@code digUpForce} 必须能挖穿它。
     */
    @GameTest(
        batch = "z_ore",
        template = "game_test",
        timeoutTicks = 200
    )
    public static void lmaChainOreWallForceDigUp(GameTestHelper helper) {
        // 石柱 (3 格高) + 柱顶侧面矿石: 女仆在柱底旁, 矿在"上方且水平相邻" ⇒ 旧门控会拒绝挖穿
        BlockPos wallAbs = helper.absolutePos(new BlockPos(3, 1, 3));
        for (int y = 0; y < 3; y++) {
            helper.getLevel().setBlockAndUpdate(wallAbs.offset(0, y, 0), Blocks.STONE.defaultBlockState());
        }
        BlockPos oreAbs = wallAbs.offset(0, 3, 0);
        helper.getLevel().setBlockAndUpdate(oreAbs, Blocks.IRON_ORE.defaultBlockState());
        // ★ v79.64 诊断（用户要求 ✓）: 回读矿位 —— 排除"其实没放进去 / 浮空 / 周围不是空气"✗
        {
            var lvl = helper.getLevel();
            var real = lvl.getBlockState(oreAbs);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 回读 矿位={} 实际={} 是铁矿={} 下={} 上={} 东={}",
                    oreAbs.toShortString(), real.getBlock().getName().getString(), real.is(Blocks.IRON_ORE),
                    lvl.getBlockState(oreAbs.below()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.above()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.east()).getBlock().getName().getString());
            int cnt = 0;
            for (int ax = -16; ax <= 16; ax++)
                for (int ay = -16; ay <= 16; ay++)
                    for (int az = -16; az <= 16; az++)
                        if (ax * ax + ay * ay + az * az <= 256
                                && lvl.getBlockState(oreAbs.offset(ax, ay, az)).is(Blocks.IRON_ORE)) cnt++;
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 她 16 格球内铁矿数={} (期望 ≥1)", cnt);
        }
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        maid.setNoAi(true);
        BlockPos footAbs = wallAbs.offset(1, 0, 0);   // 柱旁地面
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        ServerLevel sl = (ServerLevel) maid.level();
        boolean started = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.DigThroughCoordinator
                .digUpForce(sl, maid, oreAbs);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-WALL-DIG] digUpForce={} 矿={} 女仆={}",
                started, oreAbs.toShortString(), maid.blockPosition().toShortString());
        if (!started) {
            helper.fail("digUpForce 拒绝挖穿墙上的矿 (应只检查几何: 上方+深度+水平相邻)");
            return;
        }
        // 每 tick 挖一格 ⇒ 石柱 3 格 + 矿 1 格: 给足真实刻度
        helper.runAfterDelay(40L, () -> {
            for (int y = 0; y < 3; y++) {
                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.DigThroughCoordinator
                        .digUpForce(sl, maid, oreAbs);
            }
            boolean oreGone = !sl.getBlockState(oreAbs).is(Blocks.IRON_ORE);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-WALL-DIG] 矿已挖={}", oreGone);
            if (oreGone) helper.succeed();
            else helper.fail("墙上的矿没被挖穿 (digUpForce 未生效)");
        });
    }

    /**
     * **两堆矿挨着 (一堆压在另一堆下面)** (v79.63.3 ④ + 深矿过滤删除 — 用户实测「挖了一堆, 另一堆判不可达卡 10s」):
     * 上面 A 裸露可挖 ⇒ 挖掉后 B 露出开口 ⇒ 女仆**自己挖矿会重置跳过集** ⇒ B 应被继续挖完。
     */
    @GameTest(
        batch = "z_ore",
        template = "game_test",
        timeoutTicks = 500
    )
    public static void lmaChainOreStackedVein(GameTestHelper helper) {
        // 两层矿: A 在上(裸露), B 在下(被 A 与石头包住 ⇒ 初始无开口)
        BlockPos aAbs = helper.absolutePos(new BlockPos(7, 2, 7));
        BlockPos bAbs = helper.absolutePos(new BlockPos(7, 1, 7));
        helper.getLevel().setBlockAndUpdate(bAbs.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(bAbs, Blocks.IRON_ORE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(aAbs, Blocks.IRON_ORE.defaultBlockState());
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        maid.setNoAi(true);
        BlockPos footAbs = helper.absolutePos(new BlockPos(9, 2, 7));
        helper.getLevel().setBlockAndUpdate(footAbs.below(), Blocks.STONE.defaultBlockState());
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) {
            helper.fail("collect_ore submit 失败 (需镐)");
            return;
        }
        // 真实刻度推进 (蓄力按 gameTime 到期): 够挖 A 再挖 B
        helper.runAfterDelay(320L, () -> {
            boolean aGone = !helper.getLevel().getBlockState(aAbs).is(Blocks.IRON_ORE);
            boolean bGone = !helper.getLevel().getBlockState(bAbs).is(Blocks.IRON_ORE);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-STACK] A已挖={} B已挖={} pos={}", aGone, bGone, maid.blockPosition().toShortString());
            if (!aGone) helper.fail("上层矿 A 没被挖 (任务没跑起来?)");
            else if (!bGone) helper.fail("下层矿 B 没被挖 (跳过集未被挖矿重置 / 深矿过滤仍在)");
            else helper.succeed();
        });
    }

    /**
     * **墙上矿的掉落回收** (v79.63.4 用户裁定: 「矿在墙上女仆走不到矿下面, 下面是墙 ⇒ 需要挖矿下面那几格
     * 才能保证掉落物落到脚边」): 矿石悬在她上方 3 格 ⇒ 挖掉后**正下方那一列必须被清空** (掉落能落到她脚边),
     * 否则掉落物停在墙沿 ⇒ 拾取范围外 ⇒ 挖了白挖。
     */
    @GameTest(
        batch = "z_ore",
        template = "game_test",
        timeoutTicks = 900
    )
    public static void lmaChainOreWallDropCollect(GameTestHelper helper) {
        // 石柱 3 格 + 柱顶矿石; 女仆站在柱子旁 (水平 1 格, 垂直 +3 ⇒ 4 格球内 ⇒ 直接开挖路径)
        BlockPos colAbs = helper.absolutePos(new BlockPos(7, 1, 7));
        for (int y = 0; y < 3; y++) {
            helper.getLevel().setBlockAndUpdate(colAbs.offset(0, y, 0), Blocks.STONE.defaultBlockState());
        }
        BlockPos oreAbs = colAbs.offset(0, 3, 0);
        helper.getLevel().setBlockAndUpdate(oreAbs, Blocks.IRON_ORE.defaultBlockState());
        // ★ v79.64 诊断（用户要求 ✓）: 回读矿位 —— 排除"其实没放进去 / 浮空 / 周围不是空气"✗
        {
            var lvl = helper.getLevel();
            var real = lvl.getBlockState(oreAbs);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 回读 矿位={} 实际={} 是铁矿={} 下={} 上={} 东={}",
                    oreAbs.toShortString(), real.getBlock().getName().getString(), real.is(Blocks.IRON_ORE),
                    lvl.getBlockState(oreAbs.below()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.above()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.east()).getBlock().getName().getString());
            int cnt = 0;
            for (int ax = -16; ax <= 16; ax++)
                for (int ay = -16; ay <= 16; ay++)
                    for (int az = -16; az <= 16; az++)
                        if (ax * ax + ay * ay + az * az <= 256
                                && lvl.getBlockState(oreAbs.offset(ax, ay, az)).is(Blocks.IRON_ORE)) cnt++;
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 她 16 格球内铁矿数={} (期望 ≥1)", cnt);
        }
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        maid.setNoAi(true);
        BlockPos footAbs = colAbs.offset(1, 0, 0);
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        // 2026-09-19 专项: 清掉扫描半径 (16) 内的**野矿** (本用例原不清场 ⇒ 会被最近优先抢走;
        // 同批 lmaChainOreNearBeforeFar 已实测复现同因) — 只留柱顶这块 ✓
        clearStrayOres(helper, colAbs, 20, oreAbs);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) {
            helper.fail("collect_ore submit 失败 (需镐)");
            return;
        }
        // v79.63.6: 200 → 600t; 2026-09-19 再改: 固定墙点 → **条件轮询** (达成即结束, 消除时序假红)
        pollUntilSucceedOn(helper, () -> {
            if (helper.getLevel().getBlockState(oreAbs).is(Blocks.IRON_ORE)) return false;
            boolean col1Air = helper.getLevel().getBlockState(oreAbs.below()).isAir();
            boolean col2Air = helper.getLevel().getBlockState(oreAbs.below(2)).isAir();
            long pickedUp = countItem(maid, Items.RAW_IRON) + countItem(maid, Items.IRON_ORE);
            return pickedUp > 0 || (col1Air && col2Air);
        }, 600L, "墙上矿被挖且掉落物可达 (拾取或清落道)", maid, new BlockPos[]{oreAbs, oreAbs.below(), oreAbs.below(2)},
                () -> {
                   boolean oreGone = !helper.getLevel().getBlockState(oreAbs).is(Blocks.IRON_ORE);
                   boolean col1Air = helper.getLevel().getBlockState(oreAbs.below()).isAir();
                   boolean col2Air = helper.getLevel().getBlockState(oreAbs.below(2)).isAir();
                   long pickedUp = countItem(maid, Items.RAW_IRON) + countItem(maid, Items.IRON_ORE);
                   LittleMaidMoreAction.LOGGER.warn(
                           "[GAMETEST-WALDROP] 矿已挖={} 下方1空={} 下方2空={} 女仆已拾取={} pos={}",
                           oreGone, col1Air, col2Air, pickedUp, maid.blockPosition().toShortString());
                   helper.succeed();
                });
    }

    /**
     * **隔壁区块的矿会不会走过去挖** (v79.63.19 用户实测排查: 她似乎只在自家区块挖 ✗)。
     *
     * <p>夹具: 女仆站在她所在区块里, 目标矿放**下一个区块**(x+1 区块) ⇒ 距离 >4 格 ⇒ **必须寻路走过去** ✓
     * (4 格内直接挖不算 ✓ — 上一版日志里唯一那块邻区块的矿就是直接挖的 ✗)。断言: 该矿被挖掉 ✓。
     * 区块号运行时算 (结构模板位置不保证对齐 ✓), 放不下则 fail 出清晰信息 ✓。
     */
    @GameTest(template = "game_test", timeoutTicks = 2600, batch = "z_chainore_neighbourchunk")
    public static void lmaChainOreNeighbourChunk(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);   // ★ 保留 AI (NoAI=true 会让女仆完全不动 ✗ — 上一版夹具就栽这)
        // 1) 女仆站在**她自己区块的第一格** ⇒
        //    · 跨区块那条: 矿 = 下一区块第一格 ⇒ **16 格** ✓（v79.64 新范围 16 ✓ 正好到界 ✓）
        //    · 同区块对照: 矿 = 她 +11 ⇒ **12 格 同区块** ✓（语义完好 ✓）
        //    （曾试 +5 想让距离变 12 ✗ 但那样对照组矿石落到 x=16 = 下一区块 ✗ 破坏"同区块"语义 ⇒ 回退 ✓）
        int myChunkX = (helper.absolutePos(new BlockPos(7, 2, 7)).getX()) >> 4;
        int maidX = (myChunkX << 4) + 1;
        int maidZ = helper.absolutePos(new BlockPos(7, 2, 7)).getZ();
        BlockPos footAbs = new BlockPos(maidX, helper.absolutePos(new BlockPos(7, 2, 7)).getY(), maidZ);
        // 2) 清掉走廊里的天然矿石 (否则最近的天然矿会抢走目标 ✗ — 上一版假阴性就栽这)
        // ★ 2026-09-19 修复 (neoforge 实测: `[ORE-TIER] 命中=无` 长时间为空 ⇒ 目标"看不见"):
        //   原实现取**下一区块第 1 格** (x+1) ⇒ 水平 16 格 ⇒ 3D d²=256 **恰好卡在远扫球边界** ✗。
        //   扫描按 `k ≤ r²` 迭代 3D d² (NearestBlockSearch)，女仆**偏 1 格/高低差 1 格** ⇒ d²>256 ⇒
        //   直接**看不见目标** ⇒ 原地卡死直到超时 ✗ (半径 16 = RADIUS_FAR, 见 vanilla/input/search)。
        //   ⇒ 改取**下一区块第 0 格** (x+0): 仍是"隔壁区块第一格" ✓ 语义不变, 距离 15 格 ⇒ 留 1 格余量 ✓
        int ox = ((myChunkX + 1) << 4);
        // 清半径 20 的球 (只留目标那块) — 天然矿必须全部消失, 否则"最近优先"会抢走目标 ✗
        BlockPos oreCell = new BlockPos(ox, footAbs.getY(), maidZ);
        // ★ 2026-09-23 结构性收法 (用户批准 ✓): 改调共享沙盒构造器 —— 一处实现:
        //   ① 整幅 16×16 地基板 (支撑不依赖宿主 ✓, 且覆盖传送支撑格 ✓)
        //   ② 球壳密封 r=20 (挡水/沙砾/重力方块 ✓; 越界 Y 自动跳过 —— 底部由世界自身封 ✓)
        //   ③ 自证 (地基四角 + 可放置的六轴壳点 ⇒ 不成立立刻红点名 ✓)
        //   keep = 唯一目标矿 (隔壁区块第 0 格 ✓)
        protectFixture(helper, footAbs, 20, oreCell);
        // ★ 批 A-3 几何自证: 女仆落脚点必须是实心 (否则"站在 Air 上"= 球壳/地板不成立 ⇒ 直接红点名 ✗)
        if (!helper.getLevel().getBlockState(footAbs.below()).is(Blocks.STONE)) {
            helper.fail("夹具不成立(批A-3): 女仆落脚点 " + footAbs.below().toShortString() + " 非 STONE (实际 "
                    + helper.getLevel().getBlockState(footAbs.below()).getBlock().getName().getString()
                    + ") ⇒ 清场球地板/球壳不成立 ⇒ 会复现'沙砾/水塌进球' ✗");
            return;
        }
        maid.moveTo(maidX + 0.5, footAbs.getY(), maidZ + 0.5, maid.getYRot(), maid.getXRot());
        // 3) 唯一目标: 下一区块 (距离 17 格 ⇒ 必须寻路走过去 ✓)
        BlockPos oreAbs = oreCell;
        helper.getLevel().setBlockAndUpdate(oreAbs.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(oreAbs, Blocks.IRON_ORE.defaultBlockState());
        // ★ v79.64 诊断（用户要求 ✓）: 回读矿位 —— 排除"其实没放进去 / 浮空 / 周围不是空气"✗
        {
            var lvl = helper.getLevel();
            var real = lvl.getBlockState(oreAbs);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 回读 矿位={} 实际={} 是铁矿={} 下={} 上={} 东={}",
                    oreAbs.toShortString(), real.getBlock().getName().getString(), real.is(Blocks.IRON_ORE),
                    lvl.getBlockState(oreAbs.below()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.above()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.east()).getBlock().getName().getString());
            int cnt = 0;
            for (int ax = -16; ax <= 16; ax++)
                for (int ay = -16; ay <= 16; ay++)
                    for (int az = -16; az <= 16; az++)
                        if (ax * ax + ay * ay + az * az <= 256
                                && lvl.getBlockState(oreAbs.offset(ax, ay, az)).is(Blocks.IRON_ORE)) cnt++;
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 她 16 格球内铁矿数={} (期望 ≥1)", cnt);
        }
        double dist = Math.sqrt(footAbs.distSqr(oreAbs));
        boolean crossChunk = (footAbs.getX() >> 4) != (oreAbs.getX() >> 4);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-NBCHUNK] maid={} chunk={} ore={} chunk={} dist={} 跨区块={}",
                footAbs.toShortString(), footAbs.getX() >> 4, oreAbs.toShortString(), oreAbs.getX() >> 4,
                String.format("%.1f", dist), crossChunk);
        // v79.64: 范围改为 16 格 ⇒ 标准从 ">=16 格" 改为 **10~16 格**（跨区块不变 ✓）
        if (!crossChunk || dist < 10.0 || dist > 16.0) { helper.fail("夹具不满足标准 (需 10~16 格 且 跨区块 — v79.64 范围 16)"); return; }
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
         // 2026-09-21 (M1, 用户批准): 原"t=40/200/600 硬断言 guard"**已删除** —
         //   病根 = guard 独立调度 ⇒ 若本用例**早于 40t 成功**, 框架 `GameTestInfo.succeed()` 清掉本结构±1 内的
         //   非玩家实体 (含她自己) ⇒ guard 随后读到"不在 MaidIndex" ⇒ **自己的断言 vs 自己的成功清理赛跑** ⇒ 假红 ✗
         //   (实测: 栈回溯 cleaner=本用例成功路径; 清场盒实测 = origin−1..origin+17; 移除 reason 全 DISCARDED)
         // 改法: 诊断字段全部下沉到 `pollStep` **超时分支**(只失败时求值, 通过路径零开销) ⇒
         //   isRemoved/isAlive/在册/getAllEntities含她/chunkLoaded/在册数/实体总数 + 绝对 tick 一并进失败消息 ✓
         // 外部契约: Forge/NeoForge 文档 "methods which schedule success on a given tick must be careful to
         //   always fail on any previous tick" ⇒ 成功路径只应由**一个**断言(pollUntil)驱动 ✓
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) { helper.fail("collect_ore submit 失败"); return; }
        // v79.73 flaky 治: 600t → **1800t**; 2026-09-19 pollUntil 家族整治: 固定墙点 → **条件轮询**
        // (判据不变: "那块矿必须被挖掉" ✓; 窗口仍 1800t ⇒ 最坏耗时不变, 成功即结束 ✓)
        pollUntil(helper, () -> !helper.getLevel().getBlockState(oreAbs).is(Blocks.IRON_ORE), 1800L,
                "17格/跨区块 矿被挖 (起点 " + footAbs.toShortString() + ")", maid, oreAbs);
    }

    /**
     * **对照组**: 与 [lmaChainOreNeighbourChunk] **同一套清场** (半径20球只留一块矿 ✓ 保留 AI ✓),
     * 唯一差别 = 目标在**同区块 12 格**处 ⇒ 若她**会走** ⇒ 说明问题在"跨区块" ✗; 若**不走** ⇒ 与区块无关 ✓
     */
    @GameTest(template = "game_test", timeoutTicks = 900, batch = "z_chainore_samechunk12")
    public static void lmaChainOreSameChunk12(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);   // ★ 保留 AI (NoAI=true 会让女仆完全不动 ✗ — 上一版夹具就栽这)
        // 1) 女仆站在**她自己区块的第一格** ⇒
        //    · 跨区块那条: 矿 = 下一区块第一格 ⇒ **16 格** ✓（v79.64 新范围 16 ✓ 正好到界 ✓）
        //    · 同区块对照: 矿 = 她 +11 ⇒ **12 格 同区块** ✓（语义完好 ✓）
        //    （曾试 +5 想让距离变 12 ✗ 但那样对照组矿石落到 x=16 = 下一区块 ✗ 破坏"同区块"语义 ⇒ 回退 ✓）
        int myChunkX = (helper.absolutePos(new BlockPos(7, 2, 7)).getX()) >> 4;
        int maidX = (myChunkX << 4) + 1;
        int maidZ = helper.absolutePos(new BlockPos(7, 2, 7)).getZ();
        BlockPos footAbs = new BlockPos(maidX, helper.absolutePos(new BlockPos(7, 2, 7)).getY(), maidZ);
        // 2) 清掉走廊里的天然矿石 (否则最近的天然矿会抢走目标 ✗ — 上一版假阴性就栽这)
        int ox = maidX + 11;   // ★ 同区块 (12 格) — 与跨区块版对照, 只改这一个变量 ✓
        // 清半径 20 的球 (只留目标那块) — 天然矿必须全部消失, 否则"最近优先"会抢走目标 ✗
        BlockPos oreCell = new BlockPos(ox, footAbs.getY(), maidZ);
        for (int dx = -20; dx <= 20; dx++)
            for (int dz = -20; dz <= 20; dz++)
                for (int dy = -20; dy <= 20; dy++)
                    if (dx * dx + dz * dz + dy * dy <= 400) {
                        BlockPos p = footAbs.offset(dx, dy, dz);
                        if (p.equals(oreCell)) continue;
                        // ★ 清空气 + **留地板** (无地板 = 掉虚空 ✗ 第三次假红教训 ✓; 填石头 = 埋进实心岩 ✗ 也不行)
                        boolean floor = dy <= -1;
                        helper.getLevel().setBlockAndUpdate(p,
                                floor ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
        maid.moveTo(maidX + 0.5, footAbs.getY(), maidZ + 0.5, maid.getYRot(), maid.getXRot());
        // 3) 唯一目标: 下一区块 (距离 17 格 ⇒ 必须寻路走过去 ✓)
        BlockPos oreAbs = oreCell;
        helper.getLevel().setBlockAndUpdate(oreAbs.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(oreAbs, Blocks.IRON_ORE.defaultBlockState());
        // ★ v79.64 诊断（用户要求 ✓）: 回读矿位 —— 排除"其实没放进去 / 浮空 / 周围不是空气"✗
        {
            var lvl = helper.getLevel();
            var real = lvl.getBlockState(oreAbs);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 回读 矿位={} 实际={} 是铁矿={} 下={} 上={} 东={}",
                    oreAbs.toShortString(), real.getBlock().getName().getString(), real.is(Blocks.IRON_ORE),
                    lvl.getBlockState(oreAbs.below()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.above()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.east()).getBlock().getName().getString());
            int cnt = 0;
            for (int ax = -16; ax <= 16; ax++)
                for (int ay = -16; ay <= 16; ay++)
                    for (int az = -16; az <= 16; az++)
                        if (ax * ax + ay * ay + az * az <= 256
                                && lvl.getBlockState(oreAbs.offset(ax, ay, az)).is(Blocks.IRON_ORE)) cnt++;
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 她 16 格球内铁矿数={} (期望 ≥1)", cnt);
        }
        double dist = Math.sqrt(footAbs.distSqr(oreAbs));
        boolean crossChunk = (footAbs.getX() >> 4) != (oreAbs.getX() >> 4);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-NBCHUNK] maid={} chunk={} ore={} chunk={} dist={} 跨区块={}",
                footAbs.toShortString(), footAbs.getX() >> 4, oreAbs.toShortString(), oreAbs.getX() >> 4,
                String.format("%.1f", dist), crossChunk);
        if (crossChunk || dist < 10.0) { helper.fail("对照夹具应同区块且 >=10 格"); return; }
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) { helper.fail("collect_ore submit 失败"); return; }
        // 2026-09-19: 固定 600t 墙点 → **条件轮询** (对照组; 清场已是"半径 20 球只留目标" ✓)
        pollUntil(helper, () -> !helper.getLevel().getBlockState(oreAbs).is(Blocks.IRON_ORE), 600L,
                "同区块 12 格矿被挖 (对照组长距; 起点 " + footAbs.toShortString() + ")", maid, oreAbs);
    }

    /**
     * **脚边 1 格的矿** (v79.63.29 用户实测: "4 格会挖, 但脚下旁边一格不挖" ✗)。
     * 夹具: 矿放她**同一水平、正旁边 1 格** (最短距离场景 ✓), 断言被挖掉 ✓。
     */
    @GameTest(template = "game_test", timeoutTicks = 300, batch = "z_ore")
    public static void lmaChainOreAdjacentOne(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        BlockPos footAbs = helper.absolutePos(new BlockPos(7, 2, 7));
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        // 矿: 她脚同一高度、正东 1 格 ✓ (地面先垫石头防止掉落)
        BlockPos oreAbs = footAbs.offset(1, 0, 0);
        helper.getLevel().setBlockAndUpdate(footAbs.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(oreAbs.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(oreAbs, Blocks.IRON_ORE.defaultBlockState());
        // ★ v79.64 诊断（用户要求 ✓）: 回读矿位 —— 排除"其实没放进去 / 浮空 / 周围不是空气"✗
        {
            var lvl = helper.getLevel();
            var real = lvl.getBlockState(oreAbs);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 回读 矿位={} 实际={} 是铁矿={} 下={} 上={} 东={}",
                    oreAbs.toShortString(), real.getBlock().getName().getString(), real.is(Blocks.IRON_ORE),
                    lvl.getBlockState(oreAbs.below()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.above()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.east()).getBlock().getName().getString());
            int cnt = 0;
            for (int ax = -16; ax <= 16; ax++)
                for (int ay = -16; ay <= 16; ay++)
                    for (int az = -16; az <= 16; az++)
                        if (ax * ax + ay * ay + az * az <= 256
                                && lvl.getBlockState(oreAbs.offset(ax, ay, az)).is(Blocks.IRON_ORE)) cnt++;
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 她 16 格球内铁矿数={} (期望 ≥1)", cnt);
        }
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) { helper.fail("submit 失败"); return; }
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ADJ1] 女仆={} 矿={} 距离={}",
                footAbs.toShortString(), oreAbs.toShortString(),
                String.format("%.2f", Math.sqrt(footAbs.distSqr(oreAbs))));
        helper.runAfterDelay(200L, () -> {
            boolean dug = !helper.getLevel().getBlockState(oreAbs).is(Blocks.IRON_ORE);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ADJ1] 脚边1格矿已挖={} 女仆={}", dug, maid.blockPosition().toShortString());
            if (dug) helper.succeed();
            else helper.fail("脚边 1 格的矿没被挖 (用户报的 bug 复现成功 ✓)");
        });
    }

    /**
     * **脚下旁边一格 (比脚低 1 格)** (v79.63.29 用户实测 "脚下旁边一格没挖" ✗ 的候选解释):
     * 她站在石头上, 矿在她**脚下方块的旁边** (dy=-1, dx=+1 ✓) ⇒ 断言被挖 ✓
     */
    @GameTest(template = "game_test", timeoutTicks = 300, batch = "z_ore")
    public static void lmaChainOreAdjacentBelow(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        BlockPos footAbs = helper.absolutePos(new BlockPos(7, 2, 7));
        // 垫 3x3 石头地面 (她站中间, 四周都有落脚)
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                helper.getLevel().setBlockAndUpdate(footAbs.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        BlockPos oreAbs = footAbs.offset(1, -1, 0);      // ★ 脚下方块的**旁边** (低一格)
        helper.getLevel().setBlockAndUpdate(oreAbs, Blocks.IRON_ORE.defaultBlockState());
        // ★ v79.64 诊断（用户要求 ✓）: 回读矿位 —— 排除"其实没放进去 / 浮空 / 周围不是空气"✗
        {
            var lvl = helper.getLevel();
            var real = lvl.getBlockState(oreAbs);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 回读 矿位={} 实际={} 是铁矿={} 下={} 上={} 东={}",
                    oreAbs.toShortString(), real.getBlock().getName().getString(), real.is(Blocks.IRON_ORE),
                    lvl.getBlockState(oreAbs.below()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.above()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.east()).getBlock().getName().getString());
            int cnt = 0;
            for (int ax = -16; ax <= 16; ax++)
                for (int ay = -16; ay <= 16; ay++)
                    for (int az = -16; az <= 16; az++)
                        if (ax * ax + ay * ay + az * az <= 256
                                && lvl.getBlockState(oreAbs.offset(ax, ay, az)).is(Blocks.IRON_ORE)) cnt++;
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 她 16 格球内铁矿数={} (期望 ≥1)", cnt);
        }
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) { helper.fail("submit 失败"); return; }
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ADJ2] maid={} ore={} (dy=-1,dx=+1)",
                footAbs.toShortString(), oreAbs.toShortString());
        helper.runAfterDelay(200L, () -> {
            boolean dug = !helper.getLevel().getBlockState(oreAbs).is(Blocks.IRON_ORE);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ADJ2] 脚下方块旁边(低1格)已挖={} 女仆={}", dug, maid.blockPosition().toShortString());
            if (dug) helper.succeed();
            else helper.fail("脚下旁边(低1格)的矿没被挖 — 复现成功 ✓");
        });
    }

    /**
     * **复现用户日志的几何** (v79.63.35): 同一列两块矿 (上 y、下 y-1), 距女仆约 6 格、她比矿高 2~3 格
     * —— 对应日志里 "x=-3072, z=2199: y=30 挖了 ✓ / y=31 从未开脉 ✗"。断言**两块都被挖** ✓。
     * 若其中一块被拒, 新装的 [ORE-GATE]/[ORE-EXPOSE] 日志会直接点名是哪道门 ✓。
     */
    @GameTest(template = "game_test", timeoutTicks = 500, batch = "z_ore")
    public static void lmaChainOreStackedFar(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        BlockPos footAbs = helper.absolutePos(new BlockPos(7, 5, 7));
        // 走廊: 清成空气 + 铺地板 (避免天然矿抢目标 ✗ 前几次的教训 ✓)
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 8; dz++)
                for (int dy = -4; dy <= 2; dy++) {
                    BlockPos p = footAbs.offset(dx, dy, dz);
                    helper.getLevel().setBlockAndUpdate(p, dy == -4 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        // ★ 用户几何: 同一列, 上=低2格, 下=低3格, 水平 6 格
        BlockPos oreUp = footAbs.offset(0, -2, 6);
        BlockPos oreDn = footAbs.offset(0, -3, 6);
        helper.getLevel().setBlockAndUpdate(oreUp, Blocks.IRON_ORE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(oreDn, Blocks.IRON_ORE.defaultBlockState());
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) { helper.fail("submit 失败"); return; }
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-STACKFAR] maid={} 上矿={} 下矿={} (低2/低3, 水平6)",
                footAbs.toShortString(), oreUp.toShortString(), oreDn.toShortString());
        helper.runAfterDelay(400L, () -> {
            boolean up = !helper.getLevel().getBlockState(oreUp).is(Blocks.IRON_ORE);
            boolean dn = !helper.getLevel().getBlockState(oreDn).is(Blocks.IRON_ORE);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-STACKFAR] 上矿已挖={} 下矿已挖={} 女仆={}", up, dn, maid.blockPosition().toShortString());
            if (up && dn) helper.succeed();
            else helper.fail("复现成功: 上矿已挖=" + up + " 下矿已挖=" + dn + " (看 [ORE-GATE]/[ORE-EXPOSE] 日志)");
        });
    }

    /**
     * 跨区块1格 (v79.63.36 用户要求: 女仆与矿 **3 格内** + 矿在**另一个区块** + **只放一块矿** ✓
     * 避免连锁把 4 格外的矿拉进队列干扰 ✓)。女仆站在她区块的**最后一格**, 矿在 1 格外 ⇒ 跨区块 ✓
     */
    @GameTest(template = "game_test", timeoutTicks = 400, batch = "z_chainore_crosschunk1")
    public static void lmaChainOreCrossChunkOneBlock(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        BlockPos ref = helper.absolutePos(new BlockPos(7, 3, 7));
        int cx = ref.getX() >> 4;
        int edgeX = (cx << 4) + 15;
        BlockPos footAbs = new BlockPos(edgeX, ref.getY(), ref.getZ());
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos p = footAbs.offset(dx, dy, dz);
                    helper.getLevel().setBlockAndUpdate(p, dy == -3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        BlockPos oreAbs = new BlockPos(edgeX + 1, footAbs.getY(), footAbs.getZ());
        helper.getLevel().setBlockAndUpdate(oreAbs, Blocks.IRON_ORE.defaultBlockState());
        // ★ v79.64 诊断（用户要求 ✓）: 回读矿位 —— 排除"其实没放进去 / 浮空 / 周围不是空气"✗
        {
            var lvl = helper.getLevel();
            var real = lvl.getBlockState(oreAbs);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 回读 矿位={} 实际={} 是铁矿={} 下={} 上={} 东={}",
                    oreAbs.toShortString(), real.getBlock().getName().getString(), real.is(Blocks.IRON_ORE),
                    lvl.getBlockState(oreAbs.below()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.above()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.east()).getBlock().getName().getString());
            int cnt = 0;
            for (int ax = -16; ax <= 16; ax++)
                for (int ay = -16; ay <= 16; ay++)
                    for (int az = -16; az <= 16; az++)
                        if (ax * ax + ay * ay + az * az <= 256
                                && lvl.getBlockState(oreAbs.offset(ax, ay, az)).is(Blocks.IRON_ORE)) cnt++;
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 她 16 格球内铁矿数={} (期望 ≥1)", cnt);
        }
        boolean cross = (footAbs.getX() >> 4) != (oreAbs.getX() >> 4);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-XCHUNK1] 跨区块1格 maid=" + footAbs.toShortString()
                + " (区块" + (footAbs.getX() >> 4) + ") ore=" + oreAbs.toShortString()
                + " (区块" + (oreAbs.getX() >> 4) + ") 跨区块=" + cross + " 水平距离=1");
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) { helper.fail("submit 失败"); return; }
        helper.runAfterDelay(250L, () -> {
            boolean dug = !helper.getLevel().getBlockState(oreAbs).is(Blocks.IRON_ORE);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-XCHUNK1] 跨区块1格 已挖=" + dug + " (跨区块=" + cross + ")");
            if (dug) helper.succeed();
            else helper.fail("1 格外的矿没挖 (跨区块=" + cross + ") — 看 [ORE-GATE] 的行号定位出口");
        });
    }

    /**
     * 同区块1格(对照) (v79.63.36 用户要求: 女仆与矿 **3 格内** + 矿在**另一个区块** + **只放一块矿** ✓
     * 避免连锁把 4 格外的矿拉进队列干扰 ✓)。女仆站在她区块的**最后一格**, 矿在 1 格外 ⇒ 跨区块 ✓
     */
    @GameTest(template = "game_test", timeoutTicks = 400, batch = "z_chainore_samechunk1")
    public static void lmaChainOreSameChunkOneBlock(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        BlockPos ref = helper.absolutePos(new BlockPos(7, 3, 7));
        int cx = ref.getX() >> 4;
        int edgeX = (cx << 4) + 15;
        BlockPos footAbs = new BlockPos(edgeX, ref.getY(), ref.getZ());
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos p = footAbs.offset(dx, dy, dz);
                    helper.getLevel().setBlockAndUpdate(p, dy == -3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        BlockPos oreAbs = new BlockPos(edgeX - 1, footAbs.getY(), footAbs.getZ());
        helper.getLevel().setBlockAndUpdate(oreAbs, Blocks.IRON_ORE.defaultBlockState());
        // ★ v79.64 诊断（用户要求 ✓）: 回读矿位 —— 排除"其实没放进去 / 浮空 / 周围不是空气"✗
        {
            var lvl = helper.getLevel();
            var real = lvl.getBlockState(oreAbs);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 回读 矿位={} 实际={} 是铁矿={} 下={} 上={} 东={}",
                    oreAbs.toShortString(), real.getBlock().getName().getString(), real.is(Blocks.IRON_ORE),
                    lvl.getBlockState(oreAbs.below()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.above()).getBlock().getName().getString(),
                    lvl.getBlockState(oreAbs.east()).getBlock().getName().getString());
            int cnt = 0;
            for (int ax = -16; ax <= 16; ax++)
                for (int ay = -16; ay <= 16; ay++)
                    for (int az = -16; az <= 16; az++)
                        if (ax * ax + ay * ay + az * az <= 256
                                && lvl.getBlockState(oreAbs.offset(ax, ay, az)).is(Blocks.IRON_ORE)) cnt++;
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ORE] 她 16 格球内铁矿数={} (期望 ≥1)", cnt);
        }
        boolean cross = (footAbs.getX() >> 4) != (oreAbs.getX() >> 4);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-XCHUNK1] 同区块1格(对照) maid=" + footAbs.toShortString()
                + " (区块" + (footAbs.getX() >> 4) + ") ore=" + oreAbs.toShortString()
                + " (区块" + (oreAbs.getX() >> 4) + ") 跨区块=" + cross + " 水平距离=1");
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) { helper.fail("submit 失败"); return; }
        helper.runAfterDelay(250L, () -> {
            boolean dug = !helper.getLevel().getBlockState(oreAbs).is(Blocks.IRON_ORE);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-XCHUNK1] 同区块1格(对照) 已挖=" + dug + " (跨区块=" + cross + ")");
            if (dug) helper.succeed();
            else helper.fail("1 格外的矿没挖 (跨区块=" + cross + ") — 看 [ORE-GATE] 的行号定位出口");
        });
    }

    @GameTest(template = "game_test", timeoutTicks = 800, batch = "z_chainore_crosschunkmid")
    public static void lmaChainOreCrossChunkMid(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        BlockPos ref = helper.absolutePos(new BlockPos(7, 3, 7));
        int edgeX = ((ref.getX() >> 4) << 4) + 15;
        BlockPos footAbs = new BlockPos(edgeX, ref.getY(), ref.getZ());
        for (int dx = -4; dx <= 8; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos p = footAbs.offset(dx, dy, dz);
                    helper.getLevel().setBlockAndUpdate(p, dy == -3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        // ★ v79.64 场地可走性修正 (用户裁定: **能走到才挖**): 原把矿摆在平台面**上方 2 格** (footAbs+0)
        //   而平台面 = 女仆站立层 ⇒ 矿悬在不可站高度 ⇒ 女仆走不到 ⇒ 按规则**不该挖** (原期望与场地矛盾 = flaky 真因) ✗
        //   现降到与平台面同层: 矿在 footAbs-2 (站在平台上的女仆脚同层), 垫石在 -3 ⇒ 走过去即挖 ✓
        BlockPos oreAbs = footAbs.offset(5, -2, 0);           // ★ 下一区块内 5 格 ✓ 只放这一块 ✓
        helper.getLevel().setBlockAndUpdate(oreAbs.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(oreAbs, Blocks.IRON_ORE.defaultBlockState());
        boolean cross = (footAbs.getX() >> 4) != (oreAbs.getX() >> 4);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-XCHUNKMID] maid=" + footAbs.toShortString()
                + " ore=" + oreAbs.toShortString() + " 跨区块=" + cross + " 距离=5");
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) { helper.fail("submit 失败"); return; }
        // ★ v79.64 用户裁定: 固定窗口 → **条件轮询**; 跨区块标记保留 (真失败时打印便于定因)
        pollUntil(helper, () -> mined(helper, oreAbs), 700L,
                "跨区块远矿被挖 " + oreAbs.toShortString() + " (跨区块=" + cross + ")", maid, oreAbs);
    }

    /**
     * **先近后远**（v79.64 用户点名 ✓）：同侧一条线上放 **10 格**与 **15 格**各一块，
     * 断言两块都被挖 ✓，并打印**先后顺序**（近的应先进队列 ✓ 最近优先 ✓）。
     */
    @GameTest(template = "game_test", timeoutTicks = 1200, batch = "z_chainore_nearbeforefar")
    public static void lmaChainOreNearBeforeFar(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        BlockPos footAbs = helper.absolutePos(new BlockPos(7, 3, 7));
        // ── 2026-09-19 专项修复 (neoforge 实测复现 + 探针定因) ──
        // **原走廊清场 (dx -3..20 / dz -4..4) 覆盖不到扫描半径 16** ⇒ 走廊外(她身后/上方)的其他矿
        // 被"最近优先"选中 ⇒ 女仆去追别人的矿、超时失败, 本用例两块矿 1100t 未动 ✗
        // (失败现场: t=2 距近=10 → t=40 距近=13 → t=200 距近=18 后卡死, 且 [ORE-DBG] navigate 目标是
        //  走廊外另一格; `女仆在往远离我们矿的方向走` 是铁证)
        // ⇒ 改**半径 20 球清场只留两块目标矿** — 与 lmaChainOreNeighbourChunk 的同款做法 (项目既有模式) ✓
        BlockPos nearCell = footAbs.offset(10, -2, 0);
        BlockPos farCell = footAbs.offset(15, -2, 0);
        // ★ 2026-09-23 结构性收法 (用户批准 ✓): 改调共享沙盒构造器 —— 一处实现:
        //   ① 整幅 16×16 地基板 (支撑不依赖宿主 ✓, 且覆盖传送支撑格 ✓)
        //   ② 球壳密封 r=20 (挡水/沙砾/重力方块 ✓; 越界 Y 自动跳过 —— 底部由世界自身封 ✓)
        //   ③ 自证 (地基四角 + 可放置的六轴壳点 ⇒ 不成立立刻红点名 ✓)
        //   keep = 两块目标矿 (近 +10 / 远 +15 ✓)
        protectFixture(helper, footAbs, 20, nearCell, farCell);
        // 水源自证 (铁律: 先量后猜; 只跑一次, 默认零噪音): 记录走廊上方/两侧**密封前**的方块 ⇒ 回答"水从哪来"
        {
            StringBuilder probe = new StringBuilder();
            for (int dy = 3; dy <= 6; dy++) {
                probe.append(" +").append(dy).append('=')
                     .append(helper.getLevel().getBlockState(footAbs.offset(0, dy, 0)).getBlock().getName().getString());
            }
            for (int dz : new int[]{-6, 6}) {
                probe.append(" z").append(dz > 0 ? "+" : "-").append('=')
                     .append(helper.getLevel().getBlockState(footAbs.offset(0, -2, dz)).getBlock().getName().getString());
            }
            probe.append(" 外壳外(21)=")
                 .append(helper.getLevel().getBlockState(footAbs.offset(21, -2, 0)).getBlock().getName().getString());
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-NEARFIRST] 密封前水源探针 (走廊正上方/两侧/远端):{}", probe);
        }
        // ★ 2026-09-21 第三种形态修复 (HB 序列铁证): 清场球 R=20 **不够** —— TLM 的搜索范围 (searchRadius ≈12 格,
        //   见错题 #255/#261) 仍能扫到**球外天然矿** ⇒ 目标被"最近优先"抢走 ⇒ 女仆 x=299 与 x=311 之间
        //   反复横跳 (HB: 导航目标 302(远矿) → 311(球外野矿) → null → 311 …) ⇒ 1100t 超时 ✗
        //   ⇒ 在球外再补一圈"扫矿替石" (盒 x -26..+26 / z -14..+14 / y -6..+6; 矿名含 "ore" → STONE),
        //     把她的**整个搜索半径**内都清成"无矿世界" ⇒ 只剩两块目标矿 ✓ (原注释的原则, 半径补齐 ✓)
        for (int dx = -26; dx <= 26; dx++)
            for (int dz = -14; dz <= 14; dz++)
                for (int dy = -6; dy <= 6; dy++) {
                    BlockPos p = footAbs.offset(dx, dy, dz);
                    String bn = helper.getLevel().getBlockState(p).getBlock().getName().getString();
                    if (bn.contains("ore") || bn.contains("Ore")) {
                        helper.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
                    }
                }
        // ★ 2026-09-23 批 A-2 (第四形态: +x 侧漏水 ✗): **走廊复挖必须在球壳内侧收口** ——
        //   复挖段原挖到 dx=20 (球壳带 19<d²≤400 的内缘 ✓) 且 dy≠-3 一律 AIR ✗ ⇒ 在 +x 侧
        //   **捅穿球壳** ⇒ 槽位若落在水体 ⇒ 水沿 +x 洞口倒灌 ⇒ 女仆卡住 (距近=0.0/距远=5.0 不动 ✗)。
        //   改为 dx<=18 (远矿在 +15 ⇒ 仍留 3 格余量 ✓) ⇒ 球壳完整、水密可证 ✓
        for (int dx = -3; dx <= 18; dx++)
            for (int dz = -4; dz <= 4; dz++)
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos p = footAbs.offset(dx, dy, dz);
                    helper.getLevel().setBlockAndUpdate(p, dy == -3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
        maid.moveTo(footAbs.getX() + 0.5, footAbs.getY(), footAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
        // ★ v79.64 场地可走性修正 (同 CrossChunkMid): 原矿在 footAbs+0 = 女仆脚上方 1 格 (不可站层)
        //   ⇒ 女仆走不到 ⇒ 按"能走到才挖"规则不该挖 ✗; 现降到 footAbs-2 = 平台面上表面同层 ⇒ 可行走 ✓
        BlockPos near = footAbs.offset(10, -2, 0);
        BlockPos far = footAbs.offset(15, -2, 0);
        helper.getLevel().setBlockAndUpdate(near.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(near, Blocks.IRON_ORE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(far.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(far, Blocks.IRON_ORE.defaultBlockState());
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-NEARFIRST] maid=" + footAbs.toShortString()
                + " 近=" + near.toShortString() + "(10格) 远=" + far.toShortString() + "(15格) 只这两块 ✓");
        // ── 2026-09-21 修 neo 间歇红 (诊断一击命中) ──
        // 实测: 她走到近矿后卡在**距远矿 6.0 格**不动 (路径诊断 d=6.0 + 夹具 NF 探针 "半径=8.0 中心=-"),
        //   而远矿在 **15 格** ⇒ 超出 TLM 的 **限制半径 8 格** ⇒ 限制外的格被 TLM `MaidNodeEvaluator`
        //   判为 **BLOCKED** ⇒ MoveToTargetSink 算不出路 ⇒ 擦 WALK_TARGET ⇒ 她永远走不到远矿 ✗
        //   (与夹具自身注释 "限制外的格 = BLOCKED ⇒ 算不出路" 完全吻合; forge 因初始状态差异恰好未触发)
        // 修法: 显式关 home 模式 + 清限制 (与 tower/void 夹具同款做法), 并**自证**两块矿都在允许范围内 —
        //   夹具若不成立则直接红并说明, 不再靠"跑满 1100t 超时"表达 ✗
        maid.setHomeModeEnable(false);
        maid.clearRestriction();
        if (maid.hasRestriction() && (!maid.isWithinRestriction(near) || !maid.isWithinRestriction(far))) {
            helper.fail("夹具不成立: TLM 限制半径 " + maid.getRestrictRadius() + " 未覆盖远矿(15格) ⇒ 限制外格被判 BLOCKED ✗");
            return;
        }
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.IRON_PICKAXE, 1), false);
        if (!TaskDispatcher.submit(maid, "collect_ore", null, 0)) { helper.fail("submit 失败"); return; }
        // ★ v79.64 用户裁定: 固定窗口(1000) → **条件轮询** — 成功即通过; 两块都须挖到 (近/远都断言) ✓
        // 2026-09-19 **常驻自证探针** (4 行/轮, 默认零噪音): 定因"1100t 两块没挖、女仆往远走" —
        //   根因 = 清场走廊覆盖不到扫描半径 (RADIUS_FAR=16) ⇒ 野矿被"最近优先"抢走; 已改半径 20 球清场 ✓
        //   保留探针: 该用例偶发时**一跳就能看出** (是否又在往外走 / 限制是否生效), 省去反复全量采样
        //   关键 = TLM 限制三件套 (hasRestriction / restrictCenter / restrictRadius) + 目标是否在限制内 —
        //   源码: MaidNodeEvaluator 把"限制外的格"判为 BLOCKED ⇒ MoveToTargetSink 算不出路 ⇒ 擦 WALK_TARGET ⇒ 原地不动
        for (long tt : new long[]{2L, 40L, 200L, 600L}) {
            final long t = tt;
            helper.runAtTickTime(t, () -> {
                var sp = maid.getSchedulePos();
                LittleMaidMoreAction.LOGGER.warn(
                        "[GAMETEST-NF] t={} 有Home={} home模式={} 半径={} 中心={} 近矿在范围内={} 距近={} 距远={} 女仆={}",
                        t, maid.hasRestriction(), maid.isHomeModeEnable(),
                        String.format("%.1f", maid.getRestrictRadius()),
                        maid.hasRestriction() ? maid.getRestrictCenter().toShortString() : "-",
                        maid.hasRestriction() ? maid.isWithinRestriction(near) : true,
                        String.format("%.1f", Math.sqrt(maid.blockPosition().distSqr(near))),
                        String.format("%.1f", Math.sqrt(maid.blockPosition().distSqr(far))),
                        maid.blockPosition().toShortString());
            });
        }
        pollUntil(helper, () -> mined(helper, near) && mined(helper, far), 1100L,
                "近(10格)+远(15格) 都被挖 (主手=" + maid.getMainHandItem().getItem() + ")", maid,
                near, far, maid.blockPosition());
    }
}
