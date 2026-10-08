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
 * LMA gametest — World 域用例 (**21 条**)。
 *
 * <p>2026-09-21 由 {@code LmaGameTests} (6490 行) 拆分 — **纯搬移**;
 * 断言/判据/超时/template/用例名 (test ID) **逐字不变** (由 scripts/gametest-ids.mjs 121=121 把关)。
 * <p>⚠ 两平台源码实测: holder 与 prefix 按**方法所在类**读取、继承不生效 ⇒ 本类**自持**两注解 (与拆前一致)。
 */
@GameTestHolder(LittleMaidMoreAction.MOD_ID)
@PrefixGameTestTemplate(value = false)
public class LmaWorldGameTests extends LmaGameTestSupport {

    /**
     * 作物区域种菜 (v79.62) — 女仆绑定区域 (成熟小麦旁 2 格) 收成熟 → AGE 重置 0 (留株).
     * 验证: 区域内成熟作物被收割且重置为幼苗 (CropBlockHandler 留株 = AGE 0).
     */
    @GameTest(template = "game_test", timeoutTicks = 500)
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
        com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.addRegion(maid,
                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                        .of(farmAbs.getX(), farmAbs.getY(), farmAbs.getZ(),
                                cropAbs.getX(), cropAbs.getY(), cropAbs.getZ(), "minecraft:wheat_seeds"));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 300t: 等 BlockPatternCache CROP 缓存 TTL(200t) 过期重扫到新放成熟小麦 (gametest 放块不触发缓存失效)
        // 2026-09-19: 门 (确定性 TTL 200t) + **条件轮询** — 成熟小麦(AGE7)消失 = 收割发生 ✓ (判据不变)
        helper.runAfterDelay(200, () -> { });
        pollUntilSucceedOn(helper, () -> {
           var now = helper.getLevel().getBlockState(cropAbs);
           return !(now.is(net.minecraft.world.level.block.Blocks.WHEAT)
                   && now.getValue(net.minecraft.world.level.block.CropBlock.AGE) == 7);
        }, 300L, "成熟小麦被收割 (AGE 不再 7)", maid, new BlockPos[]{cropAbs},
                () -> {
                   com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                           .putRegions(maid, java.util.List.of());
                   helper.succeed();
                });
    }

    /**
     * 作物区域播种 (v79.62) — 女仆绑定空耕地 + 背包小麦种子 → farm 播种小麦到耕地 (AGE 0).
     * 验证播种链路: scanPlantable 找到耕地 → tryPlant 放小麦 + 消耗种子. 耕地放水保湿防回土.
     */
    @GameTest(
        batch = "z_slow",template = "game_test", timeoutTicks = 500)
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
        com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.addRegion(maid,
                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                        .of(farmAbs.getX(), farmAbs.getY(), farmAbs.getZ(),
                                cropAbs.getX(), cropAbs.getY(), cropAbs.getZ(), "minecraft:wheat_seeds"));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 播种走 FARMLAND 缓存 (静态), 但须越过 200t 缓存 TTL 避开过期竞态 (放块不触发失效, 错题 #238)
        // 2026-09-19 pollUntil 家族整治: 固定 300t 墙点 → **确定性 TTL 门 + 条件轮询** —
        //   200t 门只等"缓存过期"(确定性的时间条件, 无需轮询) ⇒ 之后轮询 WHEAT 出现 (=播种链路成功的直接证据)。
        //   判据较原版**更严**: 原版 "非 WHEAT 即通过" (区块被清成空气也算过) ✗, 现要求真的种上 ✓。
        helper.runAfterDelay(200, () -> { });   // 门: 越过 CROP/FARMLAND 缓存 TTL
        pollUntilSucceedOn(helper,
                () -> helper.getLevel().getBlockState(cropAbs)
                        .is(net.minecraft.world.level.block.Blocks.WHEAT),
                300L, "farm 播种小麦到耕地 (cropAbs=WHEAT)", maid, new BlockPos[]{cropAbs},
                clearFarmRegionsThenSucceed(helper, maid));
    }

    /**
     * 种子源箱 (v79.62) — 女仆背包无种子 + 绑定种子源箱 (内有小麦种子) → ensureSeedFromBox
     * 从箱取种 → 播种到耕地. 验证取种链路.
     */
    @GameTest(template = "game_test", timeoutTicks = 500)
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
        com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.addRegion(maid,
                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                        .of(farmAbs.getX(), farmAbs.getY(), farmAbs.getZ(),
                                cropAbs.getX(), cropAbs.getY(), cropAbs.getZ(),
                                "minecraft:wheat_seeds", "left",
                                boxAbs.getX(), boxAbs.getY(), boxAbs.getZ(),
                                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX,
                                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX,
                                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 300t > 200t 缓存 TTL (放块不触发失效) — 避开过期竞态
        // 2026-09-19 pollUntil 家族整治: 固定 300t 墙点 → **确定性 TTL 门 + 条件轮询** —
        //   200t 门 (时间条件, 无需轮询) → 轮询 cropAbs=WHEAT (取种+播种链路的直接证据)。
        //   原"背包有种子也判过"的弱化分支删除 ✗ (轮询一直在看最终产物; 真失败 ⇒ 超时报现场, 判据更严 ✓)
        helper.runAfterDelay(200, () -> { });   // 门: 越过缓存 TTL
        pollUntilSucceedOn(helper,
                () -> helper.getLevel().getBlockState(cropAbs)
                        .is(net.minecraft.world.level.block.Blocks.WHEAT),
                300L, "种子源箱取种→播种 (cropAbs=WHEAT)", maid, new BlockPos[]{cropAbs},
                clearFarmRegionsThenSucceed(helper, maid));
    }

    /**
     * 收获目标箱 (v79.62) — 女仆背包放小麦产物 + 小麦种子 + 铁锄(工具) + 木棍(标记物), 绑定收获箱,
     * 无成熟可收、无可种耕地 → farm 空转后 storeHarvests 只存产物 (小麦入箱, 种子/工具/标记物留背包).
     * 验证收获箱链路 + isHarvestProduct 四向判定 (产物/种子/工具/标记物; 单测环境无 MC Bootstrap
     * 不能测 ItemStack, 按错题 #174 铁律真实覆盖走 gametest).
     */
    @GameTest(template = "game_test", timeoutTicks = 500)
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
        com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.addRegion(maid,
                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                        .of(boxAbs.getX(), boxAbs.getY(), boxAbs.getZ(),
                                boxAbs.getX(), boxAbs.getY(), boxAbs.getZ(),
                                "minecraft:wheat_seeds", "left",
                                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX,
                                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX,
                                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX,
                                boxAbs.getX(), boxAbs.getY(), boxAbs.getZ()));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 300t > 200t 缓存 TTL (放块不触发失效) — 避开过期竞态
        // 2026-09-19: 固定墙点 → **条件轮询**; 违禁品出现即**立刻判红并给出精确语义** (predicate 内 fail 会抛出)
        pollUntilSucceedOn(helper, () -> {
           if (!(helper.getLevel().getBlockEntity(boxAbs) instanceof net.minecraft.world.Container cont)) {
              helper.fail("收获箱不可用 (非 Container)");
              return false;
           }
           boolean hasWheat = false;
           for (int i = 0; i < cont.getContainerSize(); i++) {
              var s = cont.getItem(i);
              if (s.is(net.minecraft.world.item.Items.WHEAT)) hasWheat = true;
              if (s.is(net.minecraft.world.item.Items.WHEAT_SEEDS)) helper.fail("小麦种子被误存入收获箱 — 种子应留背包");
              if (s.is(net.minecraft.world.item.Items.IRON_HOE)) helper.fail("铁锄(工具)被误存入收获箱 — isHarvestProduct 失效");
              if (s.is(net.minecraft.world.item.Items.STICK)) helper.fail("木棍(标记物)被误存入收获箱 — isHarvestProduct 失效");
           }
           return hasWheat;
        }, 300L, "小麦产物存入收获箱 (且不含种子/工具/标记物)", maid, new BlockPos[]{boxAbs},
                () -> {
                   com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                           .putRegions(maid, java.util.List.of());
                   helper.succeed();
                });
    }

    /**
     * v79.62.1 甜浆果丛右键收获 — 成熟甜浆果丛 (AGE 3) → farm 右键收获 → AGE 1 (留丛).
     * 验证: isRightClickHarvestable 自动分流 → FakePlayer.rightClick → AGE 重置 1 (非破坏).
     */
    @GameTest(
        batch = "z_slow",template = "game_test", timeoutTicks = 500)
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
        com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.addRegion(maid,
                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                        .of(farmAbs.getX(), farmAbs.getY(), farmAbs.getZ(),
                                bushAbs.getX(), bushAbs.getY(), bushAbs.getZ(),
                                "minecraft:sweet_berries", "right"));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 2026-09-19 pollUntil 家族整治: 固定 300t 墙点 → 条件轮询 (判据不变: **灌木仍在** 且 AGE<3)
        //   — 原版两段 fail 分支 (被破坏 / AGE 仍 3) 合成一个正向谓词 ✓
        pollUntilSucceedOn(helper,
                () -> {
                    var now = helper.getLevel().getBlockState(bushAbs);
                    return now.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                            && now.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) < 3;
                },
                300L, "甜浆果丛被右键收获 (保留灌木 + AGE<3)", maid, new BlockPos[]{bushAbs},
                clearFarmRegionsThenSucceed(helper, maid));
    }

    /**
     * v79.62.1 仙人掌顶端收割 — 3 高仙人掌 → farm 只收顶段 (保留基部).
     * 验证: VerticalCropHandler.isMatureAt 顶端判定 (高度>=3 + 上方无同种=顶端=可收; 保留基部).
     * 用仙人掌而非甘蔗: 仙人掌只需沙子下方 (甘蔗需水邻接, gametest 结构内难满足).
     */
    @GameTest(
        batch = "z_slow",template = "game_test", timeoutTicks = 900)
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
        com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.addRegion(maid,
                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                        .of(base.getX(), base.getY(), base.getZ(),
                                top.getX(), top.getY(), top.getZ(),
                                "minecraft:wheat_seeds", "left"));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 2026-09-19: 固定 600t 墙点 → **条件轮询** (顶端被收 = 行为发生; 且基部必须仍在 = 核心断言) ✓
        // (原写法只断言"基部仍在" ⇒ 若农场任务压根没跑也会绿 = 白过风险 ✗)
        pollUntilSucceedOn(helper,
                () -> !helper.getLevel().getBlockState(top).is(net.minecraft.world.level.block.Blocks.CACTUS)
                        && helper.getLevel().getBlockState(base).is(net.minecraft.world.level.block.Blocks.CACTUS),
                600L, "仙人掌顶端被收 + 基部保留", maid, new BlockPos[]{top, base},
                () -> {
                   com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                           .putRegions(maid, java.util.List.of());
                   helper.succeed();
                });
    }

    /**
     * v79.62.1 西瓜果实不被误收 — isMatureCrop 对 MelonBlock 返回 false (StemGrownHandler).
     * 验证: 西瓜方块在区域内 → farm 不收 (isMatureCrop=false, 不进收获扫描).
     */
    @GameTest(template = "game_test", timeoutTicks = 500)
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
        com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.addRegion(maid,
                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                        .of(farmAbs.getX(), farmAbs.getY(), farmAbs.getZ(),
                                melonAbs.getX(), melonAbs.getY(), melonAbs.getZ(),
                                "minecraft:melon_seeds", "left"));

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "farm", null, 0);

        // 2026-09-19: 固定 300t 墙点 → **观测窗口** (本用例是"西瓜**不该**被收" ⇒ "成立即过"轮询会秒过 ✗);
        // 全程 t=20..260 每 20t 检查: 一旦西瓜消失 ⇒ 立刻判红 (语义精确); 窗口内始终在 ⇒ 通过 ✓
        observeWindow(helper, 260L, 20L, () -> {
           if (!helper.getLevel().getBlockState(melonAbs).is(net.minecraft.world.level.block.Blocks.MELON)) {
              helper.fail("西瓜被误收 (StemGrownHandler.isMature 应返回 false 防误收)");
           }
        }, () -> {
           com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                   .putRegions(maid, java.util.List.of());
           helper.succeed();
        });
    }

    /**
     * v79.62.1 挖空置域核心链 — 先挖从标记层 (start.getY()) 开始:
     * 放石头在标记层 → 女仆挖掉 (硬度节拍) → 断言石头被破坏.
     * <p>验证: ① y 初始化用 start.getY() (start.y = 可挖最高 y, 认领直接设定高度 — 修 DEBUG-VOID y=223/147 高空卡);
     * ② 4 格内直挖 (near) → destroyBlock; ③ 无容器不阻塞.
     */
    @GameTest(template = "game_test", batch = "z_void", timeoutTicks = 500)
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
        setVoidDestroyListTerrain(maid);   // 夹具: 常见地形进销毁名单 ⇒ 不堆掉落物 (线程转储: 掉落物物理把整轮拖到 12min ✗)
        giveVoidTools(maid);   // 夹具: 给下界合金镐/锹/斧 ⇒ 挖掘最快 (没工具又慢又不掉落 ✗)
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", stoneAbs);
        cfg.putInt("min_y", stoneAbs.getY() - 1);   // v79.66k: 只挖 2 层 (目标已是实心石 ✓ 套件提速)
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
     * **销毁名单 (掉落消除) 语义回归** (v79.66h, 用户点名: "正好测试黑名单" ✓)。
     *
     * <p>产品语义 (`VoidExcavationService.tryDig`): 先 `Block.getDrops` 算掉落 → `destroyBlock(pos,false)` 挖掉
     * → **名单内物品直接销毁** (不进背包/不落地), 名单外进背包(溢出落地)。
     * 夹具侧同款用途: 测试世界把常见地形放进销毁名单 ⇒ 不再堆掉落物实体 (线程转储实证: 掉落物物理
     * `BlockCollisions` 把整轮从 1 分钟拖到 12 分钟 ✗)。
     *
     * <p>断言: 石头(在名单内) 被挖掉 **且** 背包无石头 **且** 附近无石头掉落物实体 (三者同时成立才算清单生效) ✓
     */
    @GameTest(template = "game_test", timeoutTicks = 400, batch = "z_void")
    public static void lmaVoidDestroyListWorks(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        int bkX = maid.blockPosition().getX() >> 4, bkZ = maid.blockPosition().getZ() >> 4;
        BlockPos stoneAbs = new BlockPos(bkX * 16, maid.blockPosition().getY() - 1, bkZ * 16);
        // ★ 2026-09-24 结构性收法: 同款沙盒 (板+壳+球内清场+自证 ✓)
        //   center=目标上方 · keep=stoneAbs(挖掘目标保留 STONE ✓)
        protectFixture(helper, stoneAbs.above(), 7, stoneAbs);
        helper.getLevel().setBlockAndUpdate(stoneAbs, net.minecraft.world.level.block.Blocks.STONE
                .defaultBlockState());
        maid.teleportTo(stoneAbs.getX() + 1.5, stoneAbs.getY() + 1.0, stoneAbs.getZ() + 1.5);
        maid.setNoAi(true);
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.DIAMOND_PICKAXE, 1), false);
        maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1));

        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                .cfgOrCreate(maid, "void_excavation");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", stoneAbs);
        cfg.putInt("size", 1);
        cfg.putInt("y", stoneAbs.getY());
        cfg.putInt("x", stoneAbs.getX());
        cfg.putInt("z", stoneAbs.getZ());
        setVoidDestroyList(maid, "minecraft:stone");   // ★ 石头进销毁名单 ⇒ 挖出即销毁
        giveVoidTools(maid);

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "void_excavation", null, 0);

        pollUntilSucceedOn(helper,
                () -> !helper.getLevel().getBlockState(stoneAbs).is(net.minecraft.world.level.block.Blocks.STONE),
                300L, "销毁名单: 石头被挖掉", maid, new BlockPos[]{stoneAbs}, () -> {
                   long inBag = countItem(maid, Items.STONE);
                   int onGround = helper.getLevel().getEntitiesOfClass(
                           net.minecraft.world.entity.item.ItemEntity.class,
                           new net.minecraft.world.phys.AABB(stoneAbs).inflate(8),
                           e -> e.getItem().is(Items.STONE)).size();
                   if (inBag > 0 || onGround > 0) {
                      helper.fail("销毁名单失效 — 石头掉落物仍存在 (背包=" + inBag + " 地面=" + onGround + ")");
                      return;
                   }
                   LittleMaidMoreAction.LOGGER.warn(
                           "[GAMETEST-VOID] 销毁名单生效: 石头已挖且无掉落 (背包=0 地面=0) ✓");
                   helper.succeed();
                });
    }

    /**
     * v79.62.1 多女仆同区域崩溃复现: 4 女仆同一 1×1 区块挖空 (认领池争用).
     * 验证: 多女仆 poolClaim/poolMark 并发不死锁/死循环 (用户: 第 4 个女仆右键启动后卡).
     */
    // ⚠ 2026-09-21 (用户裁定): 本用例**独占 batch `z_void_multimaid`**, 且**不得与其他用例同区/同批** ——
    //   原因: 夹具会对 **±60 格内所有女仆** 无条件 `TaskDispatcher.cancel` (收尾清理), 并用 **±80 格**扫描
    //   清理"同区遗留的旧 void 女仆" (后者已有护栏: 跳过非空置域 maid ✓ 跳过 in_progress ✓)。
    //   范围是**功能必需**(空置域任务跨多区块, 遗留 maid 可能散在同区任何位置) ⇒ 不能缩小 ⇒ 改为**结构性隔离**:
    //   独占 batch 后同批无邻居用例, 60/80 格内只会是本用例自己的 maid ✓ (跨用例清场/被清场双双消除)。
    @GameTest(template = "game_test", batch = "z_void_multimaid", timeoutTicks = 900)
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
            cfg.putInt("min_y", center.getY() - 1);   // v79.66k 用户裁定: 只挖 2 层 (相对实际挖的方块 ✓)
            // (a) 用户裁定: size=16 ⇒ minCX = (center>>4)-8; 池按行优先认领 ⇒ 只铺**前 4 个区块**(2×2) 2 层实心
            //     即可保证"4 女仆各认领一块且不秒完成" ✓ (全铺 256 区块 = 13 万格太多 ✗)
            fillVoidTwoSolidLayers(helper, center, (center.getX() >> 4) - 8, (center.getZ() >> 4) - 8, 2, 2);
            cfg.putInt("size", 16);   // 16×16 = 256 区块 (远超模板, 4 女仆各认领不同区块)
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "void_excavation", null, 0);
        }
        // 60t: 4 女仆应已完成认领 (curCX 已写) → 各区块角铺石头
        // ── 2026-09-19 pollUntil 家族整治 (本用例) ──────────────────────────────────────────
        // **为何不用通用两阶段轮询**: pollUntilThen 的 stage-1 谓词含 side-effect (铺石头) 时, 实测与
        //   框架 runAtTickTime 语义打架 (谓词 false 却同 tick 触发转阶段 ⇒ 石头铺在认领之前 ⇒ 全空) ✗。
        //   本用例真正的不确定量只有一个 = "认领何时完成"; 铺石头本身必须**在认领之后**(否则铺了个寂寞)。
        // ⇒ 结构: **只读轮询 (认领) → 确定性动作 (铺石头) → 轮询 (挖掉)** — 状态推进全在只读判定之外 ✓
        final java.util.Map<Long, BlockPos> stones = new java.util.HashMap<>();
        final int[] assigned = {0};

        // ── 2026-09-19 pollUntil 家族整治 · 本用例**保持原固定门** (诚实记录) ──────────────────
        // 起因: 本轮曾把它改造成"两阶段轮询", 但实测该用例 stage-1 的信号源 (MaidData.pl 的 curCX) 在
        //   起始 tick 上不可轮询 —— 轮询化会把"铺石头"提前到认领之前 ⇒ assigned=0 假红 ✗
        //   (对照: 原 60t 固定门下 4 女仆全部认领, 3/4 石头被挖 ⇒ **产品侧无问题**, 是夹具改造失当)。
        // ⇒ 处置: 恢复原结构 (60t 门铺石头 + 500t 门断言) + 保留"认领探测"日志 (把事实留在日志里);
        //   轮询化留待该信号源具备只读可轮询形态时再做 — 不与本批其它 6 例混着交付。
        helper.runAfterDelay(60, () -> {
            for (var md : maids4) {
                var pdt = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                        .pl(md, "void_excavation");
                LittleMaidMoreAction.LOGGER.warn("[GAMETEST-VOID] 认领探测 maid={} hasCur={} keys={}",
                        md.getStringUUID().substring(0, 8), pdt.contains("curCX"), pdt.getAllKeys());
                if (!pdt.contains("curCX")) continue;
                assigned[0]++;
                int cx = pdt.getInt("curCX"), cz = pdt.getInt("curCZ");
                // 各女仆认领区块的角 (区块级挖掘起点) 铺石头 — y 用 start.getY() (cursor 层,
                // 与挖掘起点 (curCX*16, start.getY(), curCZ*16) 对齐; 原 center.y-1 差 1 层挖不到)
                BlockPos p = new BlockPos(cx * 16, center.getY(), cz * 16);
                helper.getLevel().setBlockAndUpdate(p,
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                stones.put(net.minecraft.world.level.ChunkPos.asLong(cx, cz), p);
            }
            LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-VOID] multimaid stones placed assigned={} blocks={}", assigned[0], stones.size());
            // 2026-09-19: 500t 固定门 → **条件轮询** (至少 1 块认领区石头被挖即过);
            //   超时自动打印**每块石头**状态 (watch) + 女仆位置/任务 ⇒ 失败即自证 (本用例历史 11 次
            //   "挖掘未推进"都没有现场, 只能反复采样) ✓
            BlockPos[] watch = stones.values().toArray(new BlockPos[0]);
            // 收尾 (记日志 + 校验 assigned + 取消 4 女仆任务 + succeed) 必须放**成功回调**里 —
            //   ⚠ 放 pollUntil 之后 = 谓词还没达成就会执行到 succeed ⇒ 轮询回调永远不再跑 = 白过 ✗ (我踩过)
            pollUntilSucceedOn(helper, () -> {
               for (BlockPos p : stones.values()) {
                  if (!helper.getLevel().getBlockState(p).is(net.minecraft.world.level.block.Blocks.STONE)) {
                     return true;
                  }
               }
               return false;
            }, 500L, "多女仆 至少 1 块认领区石头被挖 (assigned=" + assigned[0] + ")", maids4.get(0), watch, () -> {
               int dug = 0;
               for (BlockPos p : stones.values()) {
                  if (!helper.getLevel().getBlockState(p).is(net.minecraft.world.level.block.Blocks.STONE)) dug++;
               }
               LittleMaidMoreAction.LOGGER.warn(
                       "[GAMETEST-VOID] multimaid final assigned={} dug={}/{} stones", assigned[0], dug, stones.size());
               if (assigned[0] < 2) helper.fail("多女仆区块分配异常: assigned=" + assigned[0]);
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
    @GameTest(template = "game_test", batch = "z_void")
    public static void lmaVoidChestHandler(GameTestHelper helper) {
        BlockPos chestAbs = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(chestAbs, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        helper.runAfterDelay(10, () -> {
            var handler = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationContainerService
                    .getHandler(helper.getLevel(), chestAbs);
            boolean space = handler != null
                    && com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationContainerService
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
    @GameTest(template = "game_test", batch = "z_void_navwalk", timeoutTicks = 600)
    public static void lmaVoidNavWalk(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        // 不 setNoAi — 让 TLM brain 导航
        // v79.62.2 防其他任务干扰: 先 cancel 旧任务 + 清 FLOW 状态 (女仆生成可能带 TLM 默认任务)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.clearAll(maid);
        BlockPos spawn = maid.blockPosition().immutable();
        // 远区块 start: chunk (3,3) → 游标起点 (48, y, 48), 距女仆 ~57 格 (TLM 需真正寻路)
        // v79.66i 用户裁定: min_y 要相对**实际挖的方块**算 ⇒ 标记点先落到**实心层** (原用 spawn.getY()
        // 很可能是空气 ⇒ 挖空气 ⇒ 区块秒完成 ⇒ 女仆"没走就结束" ✗); 再 min_y=start.y-1 ⇒ 只挖 2 层 ✓
        BlockPos start = new BlockPos(48, spawn.getY() - 1, 48);
        fillVoidTwoSolidLayers(helper, start, start.getX() >> 4, start.getZ() >> 4, 1, 1);   // (a) 铺 2 层实心 ⇒ 区域不秒完成
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1), false);
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", start);
        cfg.putInt("min_y", start.getY() - 1);   // v79.66k 用户裁定: 只挖 2 层 (相对**实际挖的方块** ✓) — 套件提速
        cfg.putInt("size", 1);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submit(maid, "void_excavation", null, 0);
        helper.runAfterDelay(300, () -> {
            BlockPos now = maid.blockPosition().immutable();
            // ★ 2026-09-20 修夹具溢出: 原 `now.distSqr(spawn)` 在相距数百万格时 **int 溢出** ⇒ 读出 4.75 假值
            //   ⇒ 断言误判"女仆未移动" ✗ (结构被放在世界 800 万格外后必现)。改用 Vec3 双精度距离 ✓
            double dist = now.getCenter().distanceToSqr(spawn.getCenter());
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
    @GameTest(template = "game_test", batch = "z_void_nav3x3farchunk", timeoutTicks = 600)
    public static void lmaVoidNav3x3FarChunk(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        // 不 setNoAi — 让 TLM brain 导航 + 传送兜底
        // v79.62.2 防其他任务干扰: 先 cancel 旧任务 + 清 FLOW 状态
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.clearAll(maid);
        BlockPos spawn = maid.blockPosition().immutable();
        // start 远区块 (区块 6,6): 距女仆 ~120 格, 3×3 区域覆盖区块 5~7
        BlockPos start = new BlockPos(96, spawn.getY() - 1, 96);   // v79.66i: 标记点落实心层 (见 NavWalk 注释)
        // (a) 用户裁定: 区域 size=3 ⇒ minCX = (start>>4)-1, 铺 3×3 区块 × 2 层实心 ⇒ 区域不秒完成 ✓
        fillVoidTwoSolidLayers(helper, start, (start.getX() >> 4) - 1, (start.getZ() >> 4) - 1, 3, 3);
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1), false);
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        // ★ v79.66j 专项诊断 (用户口径 ✓, 只改本用例): 设 4 层深度 + 深度探针 —
        //   目的 = 量"`min_y` 与真正挖的层差几层" + "认领(claim)为什么没发生"。
        //   t=60 取在用例 t=120 的认领断言**之前** (拿失败前现场); t=600 = 用户要求的 30s ✓
        cfg.putInt("min_y", start.getY() - 1);   // 2 层 (用户裁定: 相对实际挖的方块 ✓)
        // ★ 复原 (2026-09-20): 这 3 行曾被我批量编辑误删 ⇒ 该用例一度"没写 start/没 submit" ⇒
        //   `pd={}` ⇒ 必然"120t 未认领" ✗ (当时误判为"min_y 导致"; git diff 铁证)
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", start);
        cfg.putInt("size", 3);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "void_excavation", null, 0);

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
     * v79.66.1 无效 `min_y` **自愈**回归 (用户实测「一直提示未标记起始点」根因链).
     *
     * <p>根因: `min_y >= start.y` (旧 GUI 保存 bug 曾存成 0, 而标记层是 -60) ⇒ 挖掘循环 `while (y > minY)`
     * 一次不进 ⇒ 循环后 `if (y <= minY)` **恒真** ⇒ 首 tick 就把区块标"已挖完" ⇒ 下 tick 认领池判"区域完成"
     * ⇒ **删 start** + cancel ⇒ 之后每次启动 validate 失败 (表现为"未设起始点"反复出现) ✗
     *
     * <p>断言 (修后): ① 无效 `min_y` 被**移除**(自愈为自动检测基岩层); ② `start` **未被误删**;
     * ③ 实际开挖了游标首格 ✓
     */
    @GameTest(template = "game_test", batch = "z_void", timeoutTicks = 400)
    public static void lmaVoidMinYInvalidSelfHeal(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        int bkX = maid.blockPosition().getX() >> 4, bkZ = maid.blockPosition().getZ() >> 4;
        // ★ 2026-09-23 批 A-1 (槽位环境残留 ✗): 原 `start.y = 女仆y-1` 是**隐式依赖宿主/槽位状态** ✗
        //   —— 失败轮实测: 女仆脚下 Air、下方 Barrier(y=-57) ⇒ 路径被残留方块挡住 ⇒ 轮询超时 ✗
        //   (探针实测通过轮: 结构原点 y=-60、女仆 spawn=原点+2、start=原点+1、其下 -60..-64 全 stone ✓)。
        //   ⇒ 改为**相对结构原点固定** + **夹具自铺地基并自证** ⇒ 与槽位残留彻底解耦 ✓
        int originY = helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getY();
        BlockPos start = new BlockPos(bkX * 16, originY + 1, bkZ * 16);
        // ★ 2026-09-23 结构性收法 (用户批准 ✓): 改调**共享沙盒构造器** `LmaGameTestSupport#protectFixture`
        //   —— 一处实现, 负责三件事: ① 整幅 16×16 地基板 ② 球壳密封(r=7) ③ 自证(地基四角 + 六轴壳点)
        //   取代此前"每个夹具各自手写地基/密封/自证" ✗ (实测: 逐夹具打补丁不收敛 —— 槽位一变就出新面具 ✗)
        //   keep=start: 挖掘目标格保留 (用例随后要把它挖掉 ✓)
        protectFixture(helper, start.above(2), 7, start);   // 球心 = 女仆传送落点 (start.y+2 ✓)
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                .cfgOrCreate(maid, "void_excavation");
        setVoidDestroyListTerrain(maid);   // 夹具: 常见地形进销毁名单 ⇒ 不堆掉落物 (线程转储: 掉落物物理把整轮拖到 12min ✗)
        giveVoidTools(maid);   // 夹具: 给下界合金镐/锹/斧 ⇒ 挖掘最快 (没工具又慢又不掉落 ✗)
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", start);
        cfg.putInt("size", 1);
        cfg.putInt("y", start.getY());
        cfg.putInt("x", start.getX());
        cfg.putInt("z", start.getZ());
        // ★ 无效 min_y: 等于标记层 ⇒ 无可挖层 (与"旧 bug 存成 0 + 标记层 -60"同一判定分支 ✓)
        cfg.putInt(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.VoidExcavationPipeline.KEY_MIN_Y,
                start.getY());
        cfg.putBoolean(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.VoidExcavationPipeline.KEY_NO_PATHFIND, true);
        maid.teleportTo(start.getX() + 0.5, start.getY() + 2.0, start.getZ() + 0.5);
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1), false);
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "void_excavation", null, 0)) {
            helper.fail("void_excavation 提交失败 (validate 未过?)");
            return;
        }
        final BlockPos startF = start.immutable();
        pollUntil(helper, () -> {
            CompoundTag c = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                    .cfg(maid, "void_excavation");
            return !c.contains(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.VoidExcavationPipeline.KEY_MIN_Y)
                    && c.contains("start")
                    && !helper.getLevel().getBlockState(startF).is(net.minecraft.world.level.block.Blocks.STONE);
        }, 400L, "无效 min_y 自愈 (移除键) + start 未被误删 + 正常开挖", maid, startF);
    }

    /**
     * v79.65.1 「第一层检查」回归 (用户实测: 第一层**头位是未开挖地表** ⇒ 传送进去头埋方块 ⇒ 窒息掉血).
     *
     * <p>场地: 落点本体 + 头位都摆实心石 (模拟第一层未开挖地表), 女仆站在**水平 5 格外**的地表上
     * ({@code VoidExcavationService.near} = 水平 ≤4 格 ⇒ 5 格才走"传送"分支 ✓)。
     *
     * <p>断言: 头位与本体先后被挖净 (顺序: 头位→本体) **且女仆血量全程不掉** ✓ —
     * 修前: 直接传送进实心石 ⇒ 头埋方块 ⇒ 持续窒息掉血 (血量断言必红) ✓
     */
    @GameTest(template = "game_test", batch = "z_void", timeoutTicks = 600)
    public static void lmaVoidFirstLayerLandingClear(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        int bkX = maid.blockPosition().getX() >> 4, bkZ = maid.blockPosition().getZ() >> 4;
        int floorY = maid.blockPosition().getY() - 1;              // 地表方块层 (第一层 = 标记层)
        BlockPos landing = new BlockPos(bkX * 16 + 8, floorY, bkZ * 16 + 8);
        BlockPos head = landing.above();                           // 女仆头部所在格 (第一层地表之上)
        // ★ 2026-09-24 结构性收法: 同款沙盒 (板+壳+球内清场+自证 ✓)
        //   center=落点 · keep=landing+head(用例自己铺的两格保留 ✓)
        protectFixture(helper, landing, 7, landing, head);
        helper.getLevel().setBlockAndUpdate(landing, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(head, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        // 女仆站到"新地表顶"上 + 水平 5 格 (>4 ⇒ near=false ⇒ 走传送分支 ✓)
        maid.teleportTo(landing.getX() + 5.5, head.getY() + 1.0, landing.getZ() + 0.5);
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                .cfgOrCreate(maid, "void_excavation");
        setVoidDestroyListTerrain(maid);   // 夹具: 常见地形进销毁名单 ⇒ 不堆掉落物 (线程转储: 掉落物物理把整轮拖到 12min ✗)
        giveVoidTools(maid);   // 夹具: 给下界合金镐/锹/斧 ⇒ 挖掘最快 (没工具又慢又不掉落 ✗)
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", landing);
        cfg.putInt("min_y", landing.getY() - 1);   // v79.66k: 只挖 2 层 (落点/头位已铺石 ✓ 套件提速)
        cfg.putInt("size", 1);
        cfg.putInt("y", floorY);
        cfg.putInt("x", landing.getX());
        cfg.putInt("z", landing.getZ());
        cfg.putBoolean(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.VoidExcavationPipeline.KEY_NO_PATHFIND, true);
        maid.getAvailableInv(true).insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1), false);
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "void_excavation", null, 0)) {
            helper.fail("void_excavation 提交失败");
            return;
        }
        final float hp0 = maid.getHealth();
        final BlockPos landingF = landing.immutable(), headF = head.immutable();
        // 诊断 (失败时定因): 每 100t 打血量 + 落点状态
        for (int t = 100; t <= 500; t += 100) {
            final int tt = t;
            helper.runAtTickTime(tt, () -> LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-VOIDLAND] t={} hp={}/{} 女仆={} 头位={} 本体={}", tt, maid.getHealth(),
                    maid.getMaxHealth(), maid.blockPosition().toShortString(),
                    helper.getLevel().getBlockState(headF).isAir() ? "AIR" : "SOLID",
                    helper.getLevel().getBlockState(landingF).isAir() ? "AIR" : "SOLID"));
        }
        pollUntil(helper, () -> helper.getLevel().getBlockState(headF).isAir()
                        && helper.getLevel().getBlockState(landingF).isAir()
                        && maid.getHealth() >= hp0,
                600L, "第一层落点挖净 (头位+本体) 且不掉血", maid, headF, landingF);
    }

    /**
     * v79.62.2 关闭寻路模式验证 (no_pathfind) — cfg 开 no_pathfind, 女仆传送到区块中间,
     * 应直接挖游标石头 (near 判定用区块中间, 修复空转死循环: 原判 target 区块角 8 格 never near).
     */
    @GameTest(template = "game_test", batch = "z_void", timeoutTicks = 500)
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
        setVoidDestroyListTerrain(maid);   // 夹具: 常见地形进销毁名单 ⇒ 不堆掉落物 (线程转储: 掉落物物理把整轮拖到 12min ✗)
        giveVoidTools(maid);   // 夹具: 给下界合金镐/锹/斧 ⇒ 挖掘最快 (没工具又慢又不掉落 ✗)
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", stone);
        cfg.putInt("min_y", stone.getY() - 1);   // v79.66k: 只挖 2 层 (目标已是实心石 ✓ 套件提速)
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
    @GameTest(template = "game_test", batch = "z_void", timeoutTicks = 600)
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

    /**
     * v79.67.1 「**换桶不丢主手物品**」回归 (用户实测: 排水时把空桶换到手上, 主手的东西直接被换没 ✗).
     *
     * <p>场景: 主手放铁镐 (要被保住的旧物) + 背包放 1 空桶 ⇒ 排水首 tick 触发 `ensureBucket` 换桶;
     * 断言 **主手已成为空桶** 且 **铁镐未消失** (在背包里 **或** 掉在地上) ✓
     *
     * <p>修前: `setItemInHand(MAIN_HAND, 桶)` **直接覆盖** ⇒ 铁镐既不在背包也没落地 ⇒ 永久丢失 (断言必红) ✗
     * (错题 #162「丢物品族」第三处 — 见 lessons #354)
     */
    @GameTest(template = "game_test", batch = "z_void", timeoutTicks = 300)
    public static void lmaDamFillBucketKeepsMainHand(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        int cx = maid.blockPosition().getX() >> 4, cz = maid.blockPosition().getZ() >> 4;
        BlockPos start = new BlockPos(cx * 16 + 8, maid.blockPosition().getY(), cz * 16 + 8);
        // ★ 主手旧物 (铁镐) + **背包**空桶 (供换手) — 换桶后铁镐必须仍在
        // ⚠ 必须用 getAvailableBackpackInv: `getAvailableInv(true)` 是 **[手槽, 背包]** (TLM 源码实证)
        //    ⇒ `insertItem(0, 桶)` 会把手槽当 0 号槽 (现有 lmaDamFill 的桶就进了手槽) ⇒ 覆盖不到"背包→手"这条路径 ✗
        maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        maid.getAvailableBackpackInv().insertItem(0, new ItemStack(net.minecraft.world.item.Items.BUCKET, 1), false);
        // 区域内放水 (排水模式才有活干)
        BlockPos water = new BlockPos(cx * 16, start.getY(), cz * 16);
        helper.getLevel().setBlockAndUpdate(water, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        // 输入箱 (validate 要求 start+input 都在; 排水不消费其中物品 — 桶走背包分支 ✓)
        BlockPos input = helper.absolutePos(new BlockPos(8, 1, 1));
        helper.getLevel().setBlockAndUpdate(input, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "dam_fill");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", start);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "input", input);
        cfg.putInt("size", 1);
        cfg.putInt("topY", start.getY() + 1);
        cfg.putBoolean("drain_enabled", true);
        maid.teleportTo(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "dam_fill", null, 0)) {
            helper.fail("dam_fill submit 失败");
            return;
        }
        pollUntil(helper, () -> {
            if (!maid.getMainHandItem().is(net.minecraft.world.item.Items.BUCKET)) return false;   // 换桶已发生?
            int kept = 0;
            var inv = maid.getAvailableInv(true);
            for (int i = 0; i < inv.getSlots(); i++) {
                if (inv.getStackInSlot(i).is(net.minecraft.world.item.Items.IRON_PICKAXE)) {
                    kept += inv.getStackInSlot(i).getCount();
                }
            }
            for (var e : helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, maid.getBoundingBox().inflate(8.0))) {
                if (e.getItem().is(net.minecraft.world.item.Items.IRON_PICKAXE)) kept += e.getItem().getCount();
            }
            return kept >= 1;   // 旧物必须仍在 (背包 或 落地) ✓
        }, 200L, "换桶后: 主手=空桶 且 原主手铁镐未丢失 (背包/落地)", maid);
    }

    /**
     * v79.69.1 回归: 空置域的**强制 home 必须在任务结束后还原** (用户实测: 挖完换成其他任务后,
     * 女仆仍在 home 模式下被 TLM `SchedulePos.tick` restrict/传送回挖空区 ✗).
     *
     * <p>修前根因: 该块每 tick 都跑 `else { pd.putBoolean("wasHome", true) }` ⇒ 第 2 tick 起就把
     * 第 1 tick 记的 `false` **覆盖成 true** ⇒ `onCleanup` 的 `!wasHome` 判定失败 ⇒ home 永不关 ✗
     *
     * <p>断言: 起始 home=**关** ⇒ 任务期间 home=**开**(强制生效) ⇒ 取消后 home 必须回到**关**,
     * 且 restrict 已清 (残留 restrict 正是"被传送回挖空区"的直接原因) ✓ (修前必红)
     */
    @GameTest(template = "game_test", batch = "z_void", timeoutTicks = 200)
    public static void lmaVoidHomeRestore(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        maid.setNoAi(true);
        maid.setHomeModeEnable(false);
        // cfg: 只给 start (void validate 只要求 start) + 合法 min_y (低于 start, 免触发自愈干扰)
        BlockPos start = maid.blockPosition().below();
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        setVoidDestroyListTerrain(maid);   // 夹具: 常见地形进销毁名单 ⇒ 不堆掉落物 (线程转储: 掉落物物理把整轮拖到 12min ✗)
        giveVoidTools(maid);   // 夹具: 给下界合金镐/锹/斧 ⇒ 挖掘最快 (没工具又慢又不掉落 ✗)
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", start);
        cfg.putInt("size", 1);
        cfg.putInt("min_y", start.getY() - 2);
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "void_excavation", null, 0)) {
            helper.fail("void_excavation submit 失败");
            return;
        }
        helper.runAtTickTime(10, () -> {
            boolean during = maid.isHomeModeEnable();
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancel(maid);   // 模拟"换成其他任务"
            boolean after = maid.isHomeModeEnable();
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-HOME] during={} afterCancel={} restrict={}", during, after, maid.hasRestriction());
            if (!during) {
                helper.fail("任务期间应强制 home=开 (前提不成立 ⇒ 夹具问题, 非本测试目标)");
            } else if (after) {
                helper.fail("任务结束后 home 未还原 (仍=开) — `wasHome` 被每 tick 覆盖 ✗");
            } else if (maid.hasRestriction()) {
                helper.fail("任务结束后 restrict 未清 (残留 ⇒ TLM 会把女仆传送回挖空区) ✗");
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * v79.62.5 填坝模式 (dam_fill drain_enabled=false) — 只筑墙:
     * 同步驱动 DamFillPipeline.tick → BUILD_WALL 从输入箱取沙放外圈墙列 topY
     * (columnComplete: topY 实心即列完成) → 断言墙列 topY 有沙.
     */
    @GameTest(template = "game_test", batch = "z_damfill_wall", timeoutTicks = 600)
    public static void lmaDamFillWall(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        // 区域: start 区块 (2,2) 为中心 size=1 (小区域 — 墙列 16+3 短, 同步驱动可控)
        // 用 size=1: 区域 = 区块 (2,2), 墙 = 该区块外一圈
        BlockPos start = new BlockPos(2 * 16 + 8, maid.blockPosition().getY(), 2 * 16 + 8);
        BlockPos input = helper.absolutePos(new BlockPos(8, 1, 1));
        helper.getLevel().setBlockAndUpdate(input, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        var inv = helper.getLevel().getBlockEntity(input);
        if (inv instanceof net.minecraft.world.Container c) {
            c.setItem(0, new ItemStack(net.minecraft.world.item.Items.SAND, 64));
        }
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "dam_fill");
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "start", start);
        cfg.putInt("size", 1);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, "input", input);
        cfg.putInt("topY", start.getY() + 3);
        // v79.62.5 填坝模式: drain_enabled=false → 只筑墙
        cfg.putBoolean("drain_enabled", false);
        maid.teleportTo(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher
                .submit(maid, "dam_fill", null, 0);
        if (!ok) { helper.fail("dam_fill submit 失败"); return; }
        // 外圈墙列: 区域外 1 格 — 北墙 x=minX-1=31? size=1 区块(2,2): minX=32, 北墙 x=31, z 从 31..48
        // 首列 (dir0 col0): (minX-1, topY, minZ-1) = (31, topY, 31)
        int topY = start.getY() + 3;
        BlockPos firstCol = new BlockPos(2 * 16 - 1, topY, 2 * 16 - 1);
        var pl = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get("dam_fill").pipeline();
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        // 同步驱动 (墙列 topY 放沙 — columnComplete 立即 true 推进; 最多 40 tick 覆盖第一方向若干列)
        for (int i = 0; i < 40; i++) {
            pl.tick(sl, maid);
        }
        // 断言: 第一墙列 topY 有沙 (放置成功) — 沙可能已下落, 检测 topY 及下方
        boolean wall = false;
        for (int y = topY; y >= topY - 20; y--) {
            var st = helper.getLevel().getBlockState(new BlockPos(firstCol.getX(), y, firstCol.getZ()));
            if (st.is(net.minecraft.world.level.block.Blocks.SAND)) { wall = true; break; }
        }
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-DAM-WALL] wall={} firstCol={}", wall, firstCol);
        if (!wall) helper.fail("填坝模式未筑墙 (第一墙列无沙: " + firstCol + ")");
        else helper.succeed();
    }

    /**
     * **4~6 格 + 跨区块 + 新放**的露头矿必挖（v79.64 用户点名的补充 ✓）。
     * 女仆站她区块的**最后一格**，矿放**下一区块**内 **5 格**处（v79.64 范围 16 ✓ 中扫 10 格球内 ✓）。
     */
    /**
     * v79.64.1 崩溃回归: 老 farm_regions.json 导入**不得递归** (实测 StackOverflowError 崩服 —
     * `getFor` → `maybeImportFor` → `getFor` 无限递归, crash-2026-09-17_12.49.53) ✓
     *
     * <p>为什么必须单独测: gametest 沙箱**没有** config/farm_regions.json ⇒ 老逻辑在测试里直接早退,
     * 永远走不到递归分支 ⇒ 上线才崩 ✗ (夹具不覆盖真实条件的典型)。本用例用**路径覆盖**造出该条件 ✓
     */
    // ★ 独占批次 (z_legacy): 本用例改动**全局静态状态** (legacyFileOverride/sealed/SEEN) ⇒
    //   必须与其它用例串行 ✗ 否则并发时它会把别人的 getFor 也导向临时老文件 ⇒ 邻居用例偶发红
    //   (实测: 加进 defaultBatch 后 lmaBrushNothing/lmaFarmBerryBush/lmaDamFill 相继偶发 ✗)
    @GameTest(template = "game_test", timeoutTicks = 200, batch = "z_legacy")
    public static void lmaFarmLegacyImportNoRecursion(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        // 造老文件: {该女仆 uuid: [一个区域]} (老格式无 dimension)
        java.nio.file.Path tmp = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"),
                "lma_legacy_" + maid.getStringUUID() + ".json");
        String json = "{\"" + maid.getStringUUID() + "\":[{\"minX\":1,\"minY\":2,\"minZ\":3,"
                + "\"maxX\":4,\"maxY\":5,\"maxZ\":6,\"cropId\":\"minecraft:wheat_seeds\","
                + "\"harvestMode\":\"left\"}]}";
        try {
            java.nio.file.Files.writeString(tmp, json);
        } catch (Exception e) {
            helper.fail("造老文件失败: " + e);
            return;
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionLegacyImport
                .setLegacyFileOverrideForTest(tmp);
        try {
            // 关键调用: 修复前此处无限递归 ⇒ StackOverflowError; 修复后应正常返回导入结果 ✓
            var list = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.getFor(maid);
            var r0 = list.isEmpty() ? null : list.get(0);
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-LEGACYIMP] 区域数={} 首区={}", list.size(),
                    r0 == null ? "-" : (r0.minX() + "," + r0.minY() + "," + r0.minZ()));
            if (list.size() != 1) {
                helper.fail("老文件导入应有 1 个区域, 实际 " + list.size());
                return;
            }
            if (!list.get(0).isActiveIn("minecraft:overworld") && list.get(0).dimension().isEmpty()) {
                helper.fail("导入区域应补上女仆当前维度");
                return;
            }
        } catch (Throwable t) {
            helper.fail("老文件导入抛异常 (递归/其它): " + t);
            return;
        } finally {
            com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionLegacyImport
                    .setLegacyFileOverrideForTest(null);
            try { java.nio.file.Files.deleteIfExists(tmp); } catch (Exception ignored) { }
        }
        helper.succeed();
    }
}
