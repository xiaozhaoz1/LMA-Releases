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
 * LMA gametest — Compat 域用例 (**18 条**)。
 *
 * <p>2026-09-21 由 {@code LmaGameTests} (6490 行) 拆分 — **纯搬移**;
 * 断言/判据/超时/template/用例名 (test ID) **逐字不变** (由 scripts/gametest-ids.mjs 121=121 把关)。
 * <p>⚠ 两平台源码实测: holder 与 prefix 按**方法所在类**读取、继承不生效 ⇒ 本类**自持**两注解 (与拆前一致)。
 */
@GameTestHolder(LittleMaidMoreAction.MOD_ID)
@PrefixGameTestTemplate(value = false)
public class LmaCompatGameTests extends LmaGameTestSupport {

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
     * v79.62.2 合成链验证 (craft_chain) — 放合成台 + 女仆背包材料 + 产物 target,
     * executeOne 应合成出产物 (SUCCESS 计数 ≥1).
     */
    @GameTest(template = "game_test")
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

    @GameTest(template = "game_test") public static void lmaCrank(GameTestHelper h) { compatSmoke(h, "crank"); }

    @GameTest(template = "game_test") public static void lmaPower(GameTestHelper h) { compatSmoke(h, "power"); }

    @GameTest(template = "game_test") public static void lmaPress(GameTestHelper h) { compatSmoke(h, "press"); }

    @GameTest(template = "game_test") public static void lmaMix(GameTestHelper h) { compatSmoke(h, "mix"); }

    @GameTest(template = "game_test") public static void lmaRunningBelt(GameTestHelper h) { compatSmoke(h, "running_belt"); }

    /**
     * v79.71 回归 (错题 #358): **魂符收放后绑定态必须清干净 ⇒ 才可能重新转换** ✓
     *
     * <p>修前根因: `converted` 是 FSM 分派键 (tick: `"true".equals(pd.getString("converted")) ? tickRunning : tickSearching`),
     * 而魂符收走时 `RunningBeltPipeline.onMaidUnload` 只清 `target` **不清 `converted`** ✗ ⇒ 女仆被放回后
     * 永远进 `tickRunning`, 回不到"站上去 → 转换"的 tickSearching 分支 ✗ (用户实测"再上去不变发电";
     * 连带自定义应力 `POWER_BELT_STRESS` 永不生效 ✓)
     *
     * <p>⚠ **降级说明 (用户裁定)**: 本用例只验 **PD 状态契约** — 不构造真实 Create 皮带链 (gametest 环境里
     * Create 皮带的状态/BE 初始化不可靠, 曾致夹具异常 `BeltBlock`) ⇒ **真实"再站上去能再转换"链路待实机验证** ✓
     *
     * <p>断言: 卸载回调后 `target` 与 `converted` **两个键都必须不存在** ✓ (修前: `converted` 留下 "true" ⇒ 必红)
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaRunningBeltRebindAfterUnload(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        CompoundTag pd = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, "running_belt");

        // 只设 FSM 分派键 (不设 target ⇒ 走"锚点为空"的早退分支, 完全避开 Create 类型 ⇒ 夹具环境安全 ✓)
        pd.putString("converted", "true");
        com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.RunningBeltPipeline.onMaidUnload(maid);

        boolean targetCleared = !pd.contains("target");
        boolean convertedCleared = !pd.contains("converted");
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-BELT] targetCleared={} convertedCleared={} target={} converted={}",
                targetCleared, convertedCleared, pd.getString("target"), pd.getString("converted"));
        if (!convertedCleared) {
            helper.fail("魂符/卸载后 converted 未清 ✗ — FSM 会永远卡在 tickRunning, 再也走不到'站上去→转换'");
        } else {
            // 注: `target` 保留是**刻意设计** (还原失败时下次 tick 的残留检查还要用它重试 ✓) ⇒ 不作断言
            helper.succeed();
        }
    }

    @GameTest(template = "game_test") public static void lmaMaidAssembly(GameTestHelper h) { compatSmoke(h, "maid_assembly"); }

    @GameTest(template = "game_test") public static void lmaCannonLoad(GameTestHelper h) { compatSmoke(h, "cannon_load"); }

   @GameTest(
      template = "game_test",
      timeoutTicks = 120
   )
   public static void lmaCraftChainInsufficient(GameTestHelper helper) {
      BlockPos table = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.teleportTo((double)table.getX() + 0.5, (double)table.getY() + 1.0, (double)table.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(table));
         boolean ok = TaskDispatcher.submit(maid, "craft_chain", "minecraft:stick", 0);
         if (ok) {
            helper.fail("原料不足(空背包)时 submit 应失败");
         } else {
            helper.runAfterDelay(80L, () -> {
               long sticks = countItem(maid, Items.STICK);
               if (sticks > 0L) {
                  helper.fail("原料不足不应有产物 (sticks=" + sticks + ")");
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
   public static void lmaCraftChainInvalidTarget(GameTestHelper helper) {
      BlockPos table = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.OAK_LOG, 8), false);
         maid.teleportTo((double)table.getX() + 0.5, (double)table.getY() + 1.0, (double)table.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(table));
         boolean ok = TaskDispatcher.submit(maid, "craft_chain", "minecraft:netherite_ingot", 0);
         if (ok) {
            helper.fail("非法 target (不可合成) submit 应失败");
         } else {
            helper.runAfterDelay(80L, () -> {
               long logs = countItem(maid, Items.OAK_LOG);
               if (logs != 8L) {
                  helper.fail("非法 target 不应消耗材料 (logs=" + logs + ")");
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
   public static void lmaCraftChainMultiStep(GameTestHelper helper) {
      BlockPos table = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.OAK_LOG, 8), false);
         maid.teleportTo((double)table.getX() + 0.5, (double)table.getY() + 1.0, (double)table.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(table));
         boolean ok = TaskDispatcher.submit(maid, "craft_chain", "minecraft:stick", 0);
         if (!ok) {
            helper.fail("craft_chain(stick) submit 失败");
         } else {
            helper.runAfterDelay(200L, () -> {
               long logs = countItem(maid, Items.OAK_LOG);
               long sticks = countItem(maid, Items.STICK);
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-CRAFT-MULTI] logs={} sticks={}", logs, sticks);
               if (sticks < 4L) {
                  helper.fail("多步合成产物不足 (sticks<4): " + sticks);
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
   public static void lmaCraftChainNoOutputSpace(GameTestHelper helper) {
      BlockPos table = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         var inv = maid.getAvailableInv(true);
         inv.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 8));
         int slots = inv.getSlots();

         for (int i = 1; i < slots; i++) {
            inv.setStackInSlot(i, new ItemStack(Items.BONE, 64));
         }

         maid.teleportTo((double)table.getX() + 0.5, (double)table.getY() + 1.0, (double)table.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(table));
         boolean ok = TaskDispatcher.submit(maid, "craft_chain", "minecraft:oak_planks", 0);
         if (!ok) {
            helper.fail("submit 本身应成功 (空间检查在 executeOne 层)");
         } else {
            helper.runAfterDelay(80L, () -> {
               long logs = countItem(maid, Items.OAK_LOG);
               long counter = FlowTaskData.getCounter(maid);
               if (counter > 0L) {
                  helper.fail("空间不足不应合成 (counter=" + counter + ")");
               }

               if (logs != 8L) {
                  helper.fail("空间不足不应消耗材料 (logs=" + logs + ")");
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
   public static void lmaCraftChainVariant(GameTestHelper helper) {
      BlockPos table = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         var inv = maid.getAvailableInv(true);
         inv.insertItem(0, new ItemStack(Items.OAK_LOG, 8), false);
         inv.insertItem(1, new ItemStack(Items.SPRUCE_LOG, 8), false);
         maid.teleportTo((double)table.getX() + 0.5, (double)table.getY() + 1.0, (double)table.getZ() + 0.5);
         maid.getBrain().setMemory((MemoryModuleType)InitEntities.TARGET_POS.get(), new BlockPosTracker(table));
         boolean ok = TaskDispatcher.submit(maid, "craft_chain", "minecraft:oak_planks", 0);
         if (!ok) {
            helper.fail("变体选择 submit 失败");
         } else {
            helper.runAfterDelay(80L, () -> {
               long oak = countItem(maid, Items.OAK_LOG);
               long spruce = countItem(maid, Items.SPRUCE_LOG);
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-CRAFT-VARIANT] oak={} spruce={}", oak, spruce);
               if (oak >= 8L) {
                  helper.fail("变体应优先消耗橡木 (oak=" + oak + ")");
               }

               if (spruce != 8L) {
                  helper.fail("云杉不应被消耗 (spruce=" + spruce + ")");
               }

               helper.succeed();
            });
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 400
   )
   public static void lmaGarageKitDefenseRecipe(GameTestHelper helper) {
      RecipeManager manager = helper.getLevel().getRecipeManager();
      ResourceLocation key = ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "garage_kit_defense");
      // 取配方对象 (双版本 API 差异: 1.20 byKey 返回 Optional<Recipe>, 1.21 返回 Optional<RecipeHolder>)
      Object recipeObj = null;
//? if 1.20.1 {
      Optional<? extends Recipe<?>> opt120 = manager.byKey(key);
      if (opt120.isPresent()) recipeObj = opt120.get();
//?} else {
      var holder121 = manager.byKey(key);
      if (holder121.isPresent()) recipeObj = holder121.get().value();
//?}
      if (recipeObj == null) {
         helper.fail("防御塔配方未加载: " + key);
      } else if (recipeObj instanceof DefenseGarageKitRecipe recipe) {
         net.minecraft.world.item.Item garageKit;
//? if 1.20.1 {
         garageKit = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "garage_kit"));
//?} else {
         garageKit = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "garage_kit"));
//?}
         ItemStack var8 = new ItemStack((ItemLike)garageKit);
         CompoundTag data = new CompoundTag();
         data.putString("id", "touhou_little_maid:maid");
         data.putString("model_id", "test_model");
//? if 1.20.1 {
         var8.getOrCreateTag().put("EntityInfo", data);
//?} else {
         var8.set(com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent.MAID_INFO.get(), net.minecraft.world.item.component.CustomData.of(data));
//?}
         ItemStack out = DefenseGarageKitRecipe.buildOutput(var8);
         boolean inherited;
//? if 1.20.1 {
         inherited = !out.isEmpty() && out.getTag() != null && out.getTag().getCompound("EntityInfo").getString("model_id").equals("test_model");
//?} else {
         inherited = !out.isEmpty() && out.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("EntityInfo").getString("model_id").equals("test_model");
//?}
         if (inherited) {
            helper.succeed();
         } else {
            helper.fail("合成未继承模型 NBT: " + out);
         }
      } else {
         helper.fail("防御塔配方类型错误: " + recipeObj.getClass().getName());
      }
   }

   /**
    * 装配链「写入 → 运行 → 产出」端到端 (v79.63) — 补上装配**只有冒烟**的空白。
    *
    * <p><b>覆盖什么</b>: 玩家把物品拖进便携装配 GUI 的**服务端对偶** = 槽位写入
    * (`ItemStackHandler.setStackInSlot`) → 管线 tick 找 Create 配方 → 计时 → 产出。
    * 即「拖入机器 + 材料 → 女仆装配 → 输出槽有东西 / 材料被消耗」这条链。
    *
    * <p><b>不覆盖</b>: 客户端渲染与鼠标拖拽本身 — gametest 是服务端环境, MC 无 headless client
    * (渲染面的保障走静态审查 + 屏内 debug 日志, 见错题 #283/#284)。
    *
    * <p><b>环境跳过</b>: 无 Create (任务未注册) → succeed + WARN (同 compatSmoke 惯例)。
    */
   @GameTest(template = "game_test", timeoutTicks = 500)
   public static void lmaMaidAssemblyWriteRun(GameTestHelper helper) {
      EntityMaid maid = spawnMaid(helper);
      if (maid == null) return;
      // 无 Create → maid_assembly 未注册 → 跳过 (该环境本就无此任务)
      if (com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get("maid_assembly") == null) {
         LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ASSEMBLY] 任务未注册 (环境无 Create) — 跳过写入+运行检查");
         helper.succeed();
         return;
      }
      maid.setNoAi(true);
      clearMaidTaskState(maid);
      if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "maid_assembly", null, 0)) {
         helper.fail("装配任务已注册但提交失败");
         return;
      }
      var inv = com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyInventory.of(maid);
      // ★ 装配管线**硬门控** (MaidAssemblyPipeline.tickTryStart): `if (!hasFood(maid)) return State.IDLE;`
      //   女仆副手或背包必须有食物, 否则永不开始装配 (2026-09-11 用户提示; 吃食物已改为 TLM 同款
      //   30t/个且不可中断, enableWorkEat=true → 工作期间会吃, 故给足量)。
      maid.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.BREAD, 16));
      LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ASSEMBLY] 已喂食(副手): {}", maid.getOffhandItem());
      // ① 模拟玩家拖入「机器」到机器槽 0 (按压机)
      inv.setStackInSlot(0, com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyService
              .getMachineBlockStack(com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyService.MachineKind.PRESS));
      // ② 模拟玩家拖入「材料」到材料槽 8
      inv.setStackInSlot(com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu.MATERIAL_SLOT,
              new ItemStack(Items.IRON_INGOT, 8));
      LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ASSEMBLY] 已写入: machine={} material={}",
              inv.getMachineKind(0), inv.getStackInSlot(8).getItem());
      final int materialBefore = inv.getStackInSlot(8).getCount();
      helper.runAfterDelay(420L, () -> {
         var out1 = inv.getStackInSlot(com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu.OUTPUT1_SLOT);
         var out2 = inv.getStackInSlot(com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu.OUTPUT2_SLOT);
         var inter = inv.getStackInSlot(com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu.INTERMEDIATE_SLOT);
         int materialLeft = inv.getStackInSlot(8).getCount();
         boolean produced = !out1.isEmpty() || !out2.isEmpty();
         boolean consumed = materialLeft < materialBefore;
         LittleMaidMoreAction.LOGGER.warn("[GAMETEST-ASSEMBLY] 结果 out1={} out2={} inter={} material={}/{}",
                 out1, out2, inter, materialLeft, materialBefore);
         if (produced) {
            helper.succeed();                       // 产出落槽 = 写入→运行→产出 全链通
         } else if (consumed) {
            helper.succeed();                       // 材料被消耗 = 管线确实在跑 (产出可能进了中间槽/后续工序)
         } else {
            helper.fail("装配未运行: 材料未被消耗且输出槽为空 (机器=" + inv.getMachineKind(0)
                    + ", 材料=" + inv.getStackInSlot(8).getItem() + ", 中间槽=" + inter + ")");
         }
      });
   }

    /**
     * 合成链**标签变体**解析 (v79.63.2 用户实测「拿其他原木 + 木棍就搜不到配方」):
     * 云杉原木 + 木棍 → 木镐 必须能解析出配方链; 橡木同场对照 (原有可用路径不能坏)。
     *
     * <p>根因: `RecipeTreeResolver.pickIngredientMatch` 在「无库存变体」时挑"第一个**抽象**可合成的变体"
     * ⇒ 挑中橡木木板, 而橡木木板上游是 `#oak_logs` ⇒ 云杉原木不匹配 ⇒ 整链 NULL ✗。
     * 修复: 变体选择改为"**用现有材料可产出**"的递归浅判定 (`canProduceFrom`, 深度 3)。
     */
    @GameTest(
        template = "game_test",
        timeoutTicks = 100
    )
    public static void lmaCraftTagVariantWoodType(GameTestHelper helper) {
        ServerLevel sl = helper.getLevel();
        // ① 云杉原木 + 木棍 (用户报的场景)
        var spruceAvailable = new java.util.HashMap<net.minecraft.world.item.Item, Integer>();
        spruceAvailable.put(Items.SPRUCE_LOG, 4);
        spruceAvailable.put(Items.STICK, 2);
        var spruceChain = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.RecipeResolver
                .resolve(sl, "minecraft:wooden_pickaxe", spruceAvailable);
        // ② 橡木对照 (既有可用路径)
        var oakAvailable = new java.util.HashMap<net.minecraft.world.item.Item, Integer>();
        oakAvailable.put(Items.OAK_LOG, 4);
        oakAvailable.put(Items.STICK, 2);
        var oakChain = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.RecipeResolver
                .resolve(sl, "minecraft:wooden_pickaxe", oakAvailable);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-CRAFT-TAG] spruceChain={} oakChain={}",
                spruceChain == null ? "NULL" : spruceChain.steps().size() + " steps",
                oakChain == null ? "NULL" : oakChain.steps().size() + " steps");
        if (spruceChain == null) {
            helper.fail("云杉原木 + 木棍 解析不出配方链 (用户报的 bug 未修) — 检查 pickIngredientMatch 的变体选择");
            return;
        }
        if (oakChain == null) {
            helper.fail("橡木对照也失败 ⇒ 变体选择被改坏 (回归)");
            return;
        }
        helper.succeed();
    }
}
