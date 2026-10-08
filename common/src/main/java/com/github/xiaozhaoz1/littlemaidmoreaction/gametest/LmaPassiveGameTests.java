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
 * LMA gametest — Passive 域用例 (**15 条**)。
 *
 * <p>2026-09-21 由 {@code LmaGameTests} (6490 行) 拆分 — **纯搬移**;
 * 断言/判据/超时/template/用例名 (test ID) **逐字不变** (由 scripts/gametest-ids.mjs 121=121 把关)。
 * <p>⚠ 两平台源码实测: holder 与 prefix 按**方法所在类**读取、继承不生效 ⇒ 本类**自持**两注解 (与拆前一致)。
 */
@GameTestHolder(LittleMaidMoreAction.MOD_ID)
@PrefixGameTestTemplate(value = false)
public class LmaPassiveGameTests extends LmaGameTestSupport {

    /**
     * 被动驱动分派 (v79.63 Drive 化改写 — 原 v79.61x「未归类被动零驱动」语义已废弃,
     * 那正是评审 P0-1 死链的机制本身: 注册了但没进任何手写集合 = 静默不 tick)。
     *
     * <p>现契约: 每条被动显式声明 {@code Drive}, 引擎按声明分派 —
     * <ul>
     *   <li>{@code GMPM_PASSIVE} → 只被 {@code tickPassiveFor} 驱动</li>
     *   <li>{@code STANDALONE_PASSIVE} → 只被 {@code tickStandalonePassives} 驱动</li>
     * </ul>
     * 本测试 = 驱动分派正确性的闸门 (drive 声明丢失/分派写错 → 立刻红)。
     */
    @GameTest(template = "game_test")
    public static void lmaPassiveTickIsolation(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        int[] tickGmpm = {0};
        var pg = new com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline() {
            @Override public String taskType() { return "__passive_gmpm"; }
            @Override public void tick(net.minecraft.server.level.ServerLevel w, EntityMaid m) { tickGmpm[0]++; }
        };
        int[] tickStandalone = {0};
        var ps = new com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline() {
            @Override public String taskType() { return "__passive_std"; }
            @Override public void tick(net.minecraft.server.level.ServerLevel w, EntityMaid m) { tickStandalone[0]++; }
        };
        // 显式驱动声明 (新契约: 不再靠引擎侧手写集合)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.registerPassive(
                "__passive_gmpm", pg, com.github.xiaozhaoz1.littlemaidmoreaction.task.TaskRegistryManifest.Drive.GMPM_PASSIVE);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.registerPassive(
                "__passive_std", ps, com.github.xiaozhaoz1.littlemaidmoreaction.task.TaskRegistryManifest.Drive.STANDALONE_PASSIVE);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submitPassive(maid, "__passive_gmpm");
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submitPassive(maid, "__passive_std");

        var passives = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.passiveTasksList();
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        long base = sl.getGameTime();
        // 两通道各驱动 5 tick — 各自只应驱动自己桶内的条目
        for (int i = 0; i < 5; i++) {
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                    .tickPassiveFor(sl, maid, passives, base + i);
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                    .tickStandalonePassives(sl, maid, passives);
        }
        if (tickGmpm[0] != 5) {
            helper.fail("GMPM 桶条目应由 tickPassiveFor 驱动 5 次, 实际 " + tickGmpm[0]
                    + " (驱动分派错/开关判定错)");
            return;
        }
        if (tickStandalone[0] != 5) {
            helper.fail("STANDALONE 桶条目应由 tickStandalonePassives 驱动 5 次, 实际 " + tickStandalone[0]
                    + " (驱动分派错/坐下判定错)");
            return;
        }
        // 反向隔离: 两桶互不串道 — GMPM 条目不应被独立心跳多驱动
        if (tickGmpm[0] + tickStandalone[0] != 10) {
            helper.fail("驱动串道: 总 tick 数应为 10, 实际 " + (tickGmpm[0] + tickStandalone[0]));
            return;
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancelPassive(maid, "__passive_gmpm");
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancelPassive(maid, "__passive_std");
        helper.succeed();
    }

    /**
     * v79.62.5 节日触发端到端 (festival 纯触发) — 注入测试节日表 + FESTIVAL_ENTER
     * → trigger: 当天首收送礼+气泡+写去重键; 同天再 trigger 静默 (EpochDay 去重).
     * 礼物: 主人 (mock ServerPlayer) 旁 spawn 食物.
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaFestivalTrigger(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        // 注入测试节日 (今天必命中 — 用当前日期构造)
        java.time.LocalDate today = java.time.LocalDate.now();
        com.github.xiaozhaoz1.littlemaidmoreaction.storage.FestivalTable.setFestivals(
                java.util.List.of(new com.github.xiaozhaoz1.littlemaidmoreaction.storage.FestivalTable.Festival(
                        "test_fest", "测试节", today.getMonthValue(), today.getDayOfMonth(),
                        "测试节日快乐", false, java.util.List.of("minecraft:cake"))));
        // 主人 (mock ServerPlayer 注册 — milk 测试同款)
        final net.minecraft.server.level.ServerPlayer srvPlayer;
//? if 1.20.1 {
        srvPlayer = new net.minecraft.server.level.ServerPlayer(
                ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer(),
                (net.minecraft.server.level.ServerLevel) helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "fest-owner")) {
//?} else {
        srvPlayer = new net.minecraft.server.level.ServerPlayer(
                ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer(),
                (net.minecraft.server.level.ServerLevel) helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "fest-owner"),
                net.minecraft.server.level.ClientInformation.createDefault()) {
//?}
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        net.minecraft.network.Connection conn = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
//? if 1.20.1 {
        ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer().getPlayerList()
                .placeNewPlayer(conn, srvPlayer);
//?} else {
        try {
            ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer().getPlayerList()
                    .placeNewPlayer(conn, srvPlayer,
                            net.minecraft.server.network.CommonListenerCookie.createInitial(srvPlayer.getGameProfile(), false));
        } catch (RuntimeException ignored) {
            // TLM sync_data payload 噪音 — 玩家已注册
        }
//?}
        net.minecraft.server.level.ServerPlayer owner = ((net.minecraft.server.level.ServerLevel) helper.getLevel())
                .getServer().getPlayerList().getPlayer(srvPlayer.getUUID());
        if (owner == null) { helper.fail("mock 玩家注册失败"); return; }
        maid.setOwnerUUID(owner.getUUID());
        // 首次 trigger → 送礼 + 去重键写入
        var task = new com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.FestivalPassiveTask();
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        task.trigger(sl, maid, com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals.ENV_FESTIVAL_ENTER);
        long stored = maid.getPersistentData().getLong(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.FESTIVAL_DAY);
        // 同天再 trigger → 去重 (stored 不变)
        task.trigger(sl, maid, com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals.ENV_FESTIVAL_ENTER);
        long stored2 = maid.getPersistentData().getLong(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.FESTIVAL_DAY);
        boolean giftInBag = owner.getInventory().countItem(net.minecraft.world.item.Items.CAKE) > 0;
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-FEST] stored={} stored2={} epoch={} 礼物进主人背包={} (测试节日池只有 cake ✓)",
                stored, stored2, today.toEpochDay(), giftInBag);
        if (!giftInBag) { helper.fail("礼物没进主人背包 ✗ (v79.64 应为 sp.addItem 进包 ✓ 只有满包才掉地上)"); return; }
        if (stored != today.toEpochDay()) helper.fail("首次触发未写去重键 (stored=" + stored + ")");
        else if (stored2 != stored) helper.fail("同天去重失败 (stored2=" + stored2 + " != " + stored + ")");
        else helper.succeed();
    }

    /**
     * v79.62.5 节日去重纯函数 (DailyDedup) — 首触发/同天去重/跨天重触发.
     */
    @GameTest(template = "game_test")
    public static void lmaFestivalDedup(GameTestHelper helper) {
        java.time.LocalDate today = java.time.LocalDate.now();
        // 从未触发 (0) → 应触发
        boolean first = com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.DailyDedup.shouldAnnounce(0, today);
        // 同天 → 静默
        boolean same = com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.DailyDedup.shouldAnnounce(today.toEpochDay(), today);
        // 昨天 → 应触发 (跨天)
        boolean yesterday = com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.DailyDedup.shouldAnnounce(today.toEpochDay() - 1, today);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-FEST-DEDUP] first={} same={} yesterday={}", first, same, yesterday);
        if (!first || same || !yesterday) helper.fail("DailyDedup 逻辑错 (first=" + first + " same=" + same + " yesterday=" + yesterday + ")");
        else helper.succeed();
    }

    /**
     * v79.62.5 稀有群系判定纯函数 — 白名单 5 群系 true, 普通群系/null false.
     */
    @GameTest(template = "game_test")
    public static void lmaRareBiomeIsRare(GameTestHelper helper) {
        boolean mushroom = com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask
                .isRareBiome("minecraft:mushroom_fields");
        boolean deepDark = com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask
                .isRareBiome("minecraft:deep_dark");
        boolean lush = com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask
                .isRareBiome("minecraft:lush_caves");
        boolean drip = com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask
                .isRareBiome("minecraft:dripstone_caves");
        boolean shore = com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask
                .isRareBiome("minecraft:mushroom_field_shore");
        boolean plains = com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask
                .isRareBiome("minecraft:plains");
        boolean nullId = com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask
                .isRareBiome(null);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-RARE] mush={} dark={} lush={} drip={} shore={} plains={} null={}",
                mushroom, deepDark, lush, drip, shore, plains, nullId);
        if (!mushroom || !deepDark || !lush || !drip || !shore) helper.fail("白名单群系应判稀有");
        else if (plains || nullId) helper.fail("普通群系/null 不应判稀有");
        else helper.succeed();
    }

    /**
     * 稀有群系触发守卫 — {@code RareBiomePassiveTask.trigger} 的行为必须与**当前群系是否稀有**严格对应。
     *
     * <p><b>v79.63 修 flake</b>: 原版断言"测试模板不应是稀有群系"作**前提** → 但 gametest 世界的
     * {@code level-seed} 为空 (每次 rm -rf run/gametest 后按随机种子生成), 结构落点群系随之变化;
     * 实测同一份代码两次 run 分别得到 {@code savanna} (过) 与 {@code deep_dark} (挂), 即
     * 「测试前提依赖随机世界」= 环境依赖 flake (评审同类问题)。
     * 现改为**双向断言** (环境无关, 且覆盖更全):
     * 稀有群系 → 必须写去重键; 非稀有群系 → 必须静默 (不写键)。
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaRareBiomeTrigger(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        // 当前群系 (随机种子世界 — 可能稀有也可能普通, 断言改为与它一致)
        String biomeId = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader
                .getBiome((net.minecraft.server.level.ServerLevel) maid.level(), maid.blockPosition());
        boolean rare = com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask
                .isRareBiome(biomeId);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-RARE-TRIGGER] biome={} rare={}", biomeId, rare);

        var task = new com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask();
        task.trigger((net.minecraft.server.level.ServerLevel) maid.level(), maid,
                com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals.ENV_RARE_BIOME);
        boolean wrote = maid.getPersistentData().contains(
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.RARE_BIOME_LAST);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-RARE-TRIGGER] wrote={}", wrote);

        if (rare && !wrote) {
            helper.fail("稀有群系 (" + biomeId + ") trigger 应走去重键 (气泡分支) — 未写");
        } else if (!rare && wrote) {
            helper.fail("非稀有群系 (" + biomeId + ") trigger 不应写去重键 — 已写");
        } else {
            helper.succeed();
        }
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
     * v79.62.5 酒狐奶喂食闭环 — 主人 (注册 ServerPlayer) 掉血 <70% + 女仆背包酒狐奶桶
     * + enabled → tick 应喂奶: 主人得 DAMAGE_RESISTANCE + REGENERATION buff, 奶消耗返空桶,
     * 节流键写入. owner 绑定用 setOwnerUUID (跳过 TLM tame 的 sync_data payload — neoforge 炸).
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaMilkFeed(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        // 构造注册 ServerPlayer (haqi owner 测试同款: isCreative=false + placeNewPlayer)
        final net.minecraft.server.level.ServerPlayer srvPlayer;
//? if 1.20.1 {
        srvPlayer = new net.minecraft.server.level.ServerPlayer(
                ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer(),
                (net.minecraft.server.level.ServerLevel) helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "milk-owner")) {
//?} else {
        srvPlayer = new net.minecraft.server.level.ServerPlayer(
                ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer(),
                (net.minecraft.server.level.ServerLevel) helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "milk-owner"),
                net.minecraft.server.level.ClientInformation.createDefault()) {
//?}
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        net.minecraft.network.Connection conn = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
//? if 1.20.1 {
        ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer().getPlayerList()
                .placeNewPlayer(conn, srvPlayer);
//?} else {
        try {
            ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getServer().getPlayerList()
                    .placeNewPlayer(conn, srvPlayer,
                            net.minecraft.server.network.CommonListenerCookie.createInitial(srvPlayer.getGameProfile(), false));
        } catch (RuntimeException ignored) {
            // TLM sync_data payload 噪音 — 玩家已注册 (事件在 addPlayer 后派发)
        }
//?}
        net.minecraft.server.level.ServerPlayer player = ((net.minecraft.server.level.ServerLevel) helper.getLevel())
                .getServer().getPlayerList().getPlayer(srvPlayer.getUUID());
        if (player == null) { helper.fail("mock 玩家注册后无法取回"); return; }
        // owner 绑定: setOwnerUUID 直接绑 (跳过 tame 流程的 sync_data payload — neoforge 炸)
        maid.setOwnerUUID(player.getUUID());
        // 主人站女仆 8 格内 (JiuhuMilkPipeline 距离判 64 distSqr)
        player.moveTo(maid.getX() + 1, maid.getY(), maid.getZ());
        // 清创造能力 (GameTestServer 默认 creative: invulnerable + instabuild 均 true —
        // instabuild=true 会让 JiuhuMilkPipeline 跳过返空桶, 需清才验证真实玩家路径)
        player.getAbilities().invulnerable = false;
        player.getAbilities().instabuild = false;
        // 掉血到 50% (<70% 触发)
        player.setHealth(player.getMaxHealth() * 0.5f);
        // 背包放酒狐奶桶
        var inv = maid.getAvailableInv(true);
        inv.insertItem(0, new ItemStack(
                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.KitsuneMilkItems.TAMED_MILK_BUCKET.get(), 1), false);
        // 启用 jiuhu_milk (per-maid pipelineConfig enabled)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "jiuhu_milk")
                .putBoolean("enabled", true);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submitPassive(maid, "jiuhu_milk");
        // tick 驱动 — v79.63 起**必须走引擎通道** (原直调 pl.tick 掩盖了"该管线无驱动"的死链:
        // jiuhu_milk 曾不在任何驱动集合 → 测试绿而生产从不 tick, 错题 #157 同型复发;
        // 现在若驱动声明丢失/分派错桶, 本测试立刻红)
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        var passives = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.passiveTasksList();
        // 注: 同一 game tick 内重复调用 → 第 1 次执行喂奶, 其余被 100t 前置节流 (顺带验证节流生效)
        for (int i = 0; i < 5; i++) {
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                    .tickStandalonePassives(sl, maid, passives);
        }
        // 断言: 主人有抗性 buff + 奶消耗 (桶空/返空桶) + 节流键
        boolean hasResist = player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
        boolean hasRegen = player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
        boolean milkGone = true;
        boolean bucketBack = false;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (st.is(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.KitsuneMilkItems.TAMED_MILK_BUCKET.get())) milkGone = false;
            if (st.is(net.minecraft.world.item.Items.BUCKET)) bucketBack = true;
        }
        CompoundTag pd = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, "jiuhu_milk");
        boolean throttled = pd.getLong("lma_milk_last_feed") > 0;
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-MILK] resist={} regen={} milkGone={} bucketBack={} throttled={}",
                hasResist, hasRegen, milkGone, bucketBack, throttled);
        if (!hasResist || !hasRegen) helper.fail("主人未获得奶 buff (resist=" + hasResist + " regen=" + hasRegen + ")");
        else if (!milkGone) helper.fail("奶桶未被消耗");
        else if (!throttled) helper.fail("节流键未写入");
        else helper.succeed();
    }

    /**
     * v79.62.2 自动点火验证 (被动 torch_light) — 女仆背包火把 + DARKNESS 信号
     * → onSignal 应把火把放入副手 (lightUp 生效).
     */
    @GameTest(template = "game_test")
    public static void lmaTorchLight(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        maid.getAvailableBackpackInv().insertItem(0, new ItemStack(net.minecraft.world.item.Items.TORCH, 4), false);
        // 构造 DARKNESS 快照 (亮度过低)
        var worldInfo = new com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot.WorldInfo(
                false, false, false, 0, 0, "", "NONE", 18000L, "NIGHT", "", "");
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
     * v79.62.2 刷扫验证 (brush) — 放可疑沙砾 + 女仆背包刷子 + 旁,
     * tick 应刷扫 (findSuspicious 找到 + brush 消耗刷子耐久).
     */
    @GameTest(timeoutTicks = 200,
        batch = "z_slow",template = "game_test")
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
    @GameTest(template = "game_test")
    public static void lmaExplorerMap(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        maid.setNoAi(true);
        clearMaidTaskState(maid);
        // 构造填充地图 (FILLED_MAP — 无实际宝藏装饰时 readTreasurePos 可能 null → 只验证管线不崩)
        var map = new ItemStack(net.minecraft.world.item.Items.FILLED_MAP, 1);
        maid.getAvailableInv(true).insertItem(0, map, false);
        // v79.63: 走引擎通道 (原直调 pl.tick 掩盖「explorer_map 无驱动」死链 — 评审 P0-1)
        // 前置: 该管线为 per-maid 开关型? 否 — explorer_map 走全局 TaskToggle (缺省开), 直接提交即可
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submitPassive(maid, "explorer_map");
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        var passives = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.passiveTasksList();
        try {
            // 引擎入口 (GMPM 通道) — 含 drive 分派 + 开关判定 + EngineGuard 隔离。
            // 注: 同一 game tick 内重复调用 → 第 1 次过 100t 扫描节流, 其余被节流 (顺带验证节流生效)
            for (int i = 0; i < 5; i++) {
                com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                        .tickPassiveFor(sl, maid, passives, i * 100L);
            }
        } catch (Exception ex) {
            helper.fail("explorer_map tick 异常: " + ex);
            return;
        }
        // v79.63.5 (用户裁定): 无装饰地图 ⇒ 走"这张图没有宝藏标记"分支 + 记 announced
        //   ⇒ 本用例从"只验证不崩"升级为**真断言** (原为空白填图 ⇒ 恒 null ⇒ 假绿) ✓
        String announced = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                .pl(maid, "explorer_map").getString("explorer_announced");
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-MAP] tick ok, announced={}", announced == null || announced.isEmpty() ? "空" : "已写");
        if (announced == null || announced.isEmpty()) {
            helper.fail("无标记地图未走\"没有宝藏标记\"分支 (announced 为空)");
        } else {
            helper.succeed();
        }
    }

   @GameTest(
        batch = "z_slow",
      template = "game_test",
      timeoutTicks = 300
   )
   public static void lmaBrushComplete(GameTestHelper helper) {
      BlockPos sus = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(sus.below(), Blocks.STONE.defaultBlockState());
      helper.getLevel().setBlockAndUpdate(sus, Blocks.SUSPICIOUS_GRAVEL.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         BlockPos maidAbs = helper.absolutePos(new BlockPos(3, 2, 3));
         maid.moveTo((double)maidAbs.getX() + 0.5, (double)maidAbs.getY(), (double)maidAbs.getZ() + 0.5, maid.getYRot(), maid.getXRot());
         maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.BRUSH, 1), false);
         if (helper.getLevel().getBlockEntity(sus) instanceof BrushableBlockEntity b) {
//? if 1.20.1 {
            b.setLootTable(ResourceLocation.fromNamespaceAndPath("minecraft", "archaeology/desert_well"), 1L);
//?} else {
            b.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("minecraft", "archaeology/desert_well")), 1L);
//?}
         }

         boolean ok = TaskDispatcher.submit(maid, "brush", null, 0);
         if (!ok) {
            helper.fail("brush submit 失败");
         } else {
            helper.runAfterDelay(
               150L,
               () -> {
                  boolean stillSuspicious = helper.getLevel().getBlockState(sus).is(Blocks.SUSPICIOUS_GRAVEL);
                  long lootCount = 0L;

                  for (Item item : new Item[]{Items.BRICK, Items.EMERALD, Items.STICK, Items.SUSPICIOUS_STEW}) {
                     lootCount += countItem(maid, item);
                  }

                  int dmg = maid.getAvailableInv(true).getStackInSlot(0).getDamageValue();
                  BlockState stateNow = helper.getLevel().getBlockState(sus);
                  LittleMaidMoreAction.LOGGER
                     .warn(
                        "[GAMETEST-BRUSH-DONE] stillSuspicious={} loot={} dmg={} nowState={}",
                        new Object[]{stillSuspicious, lootCount, dmg, stateNow.getBlock()}
                     );
                  if (stillSuspicious) {
                     helper.fail("刷扫未完成 (方块仍可疑态)");
                  }

                  if (lootCount < 1L) {
                     helper.fail("刷扫完成应产出考古掉落 (loot=0)");
                  }

                  if (dmg != 1) {
                     helper.fail("刷扫完成应消耗 1 耐久 (dmg=" + dmg + ")");
                  }

                  helper.succeed();
               }
            );
         }
      }
   }

   @GameTest(
        batch = "z_slow",
      template = "game_test",
      timeoutTicks = 120
   )
   public static void lmaBrushNothing(GameTestHelper helper) {
      BlockPos plain = helper.absolutePos(new BlockPos(3, 1, 3));
      helper.getLevel().setBlockAndUpdate(plain.below(), Blocks.STONE.defaultBlockState());
      helper.getLevel().setBlockAndUpdate(plain, Blocks.GRAVEL.defaultBlockState());
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.BRUSH, 1), false);
         boolean ok = TaskDispatcher.submit(maid, "brush", null, 0);
         if (!ok) {
            helper.fail("brush submit 失败");
         } else {
            // ── 2026-09-19 改造: 固定 60t 单点断言 → **观测窗口 + 到点总结** ──
            // 为什么不用 pollUntil: 本用例判据是"**持续**什么都不该发生"(耐久 0 + 无掉落 + 方块不变),
            // 而 pollUntil 是"成立即过"⇒ t=0 就成立会秒过 ✗ (等于没测)。正解 = 观测整段窗口, 逐 tick 记违规,
            // 到点总结 (与 brush 管线的节拍/负载无关 ⇒ 不再有时序假红)。
            // ── 观测盒 = **本结构精确边界** (2026-09-21; 原 `inflate(4)` 病根) ──
            // 病根链: 原 `new AABB(plain).inflate(4)` (半径 4) → 先按裁定收为"结构边界 +1 缓冲" → **仍红**,
            //   自证载荷实测: `首物品=1 pointed_dripstone@279,-45,237  盒=[279,230..297,248]` —— 该坐标
            //   x=279 正是 **盒的 minX (= origin-1, 缓冲圈)**, 即物品在**本结构之外**一格;
            //   来源 = **早前 batch 遗留在地面的掉落物** (物品不立即消失, 各 batch 顺序跑但共用同一世界 ⇒
            //   并行/前序测试的掉落物会留在结构缝隙) ⇒ 实测推翻"邻结构间距 ≥4 格"的前提 ✗
            // 现口径: 盒 = [origin, origin+16) **精确结构体**, 不含任何缓冲 ⇒ 结构外物品一律不计 ✓
            // 判据不变 (dmg / drops / changed 三信号): 若刷子**真**误处理, 掉落物必出现在被刷方块处 = 结构内 ⇒ 仍被捕获 ✓
            // 并发隔离另加: 本用例已独占 `batch = "z_brush"` (项目 gametest README 规则 3) ✓
            final BlockPos structOrigin = helper.absolutePos(BlockPos.ZERO);
            final AABB ownBox = new AABB(
                    structOrigin.getX(), structOrigin.getY(), structOrigin.getZ(),
                    structOrigin.getX() + 16, structOrigin.getY() + 16, structOrigin.getZ() + 16);
            // ── 测前清场: 丢弃本结构盒内的**遗留**物品实体 (2026-09-21 实测驱动) ──
            // 为什么必须做: 结构槽位跨 batch 复用, 而框架清的是方块/结构、**不清物品实体** ⇒
            //   前序 batch (如收获/挖空类) 掉在本槽的物品会残留 ⇒ 本用例"持续无掉落"断言必被污染 ✗
            //   (实测载荷: 首物品=1 azalea@285,-47,231 全在盒内; 同批已独占 z_brush 也照样中招 ⇒ 来源是**遗留**非并行)
            // 先例: 防御塔用例同样"测前清框内 Enemy" (gametest README 规则 3 / 陷阱 L) ✓
            // 断言力不变: 清场只去遗留; 本用例窗口内若真掉东西 (刷子误处理), 仍被 drops>0 捕获 ✓
            int clearedLeftover = 0;
            for (ItemEntity leftover : helper.getLevel().getEntitiesOfClass(ItemEntity.class, ownBox)) {
                leftover.discard();
                clearedLeftover++;
            }
            if (clearedLeftover > 0) {
                LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BRUSH-NONE] 测前清场: 丢弃盒内遗留物品 {} 个", clearedLeftover);
            }
            final int[] violations = {0};
            final String[] firstBad = {"-"};
            final long[] lastDmg = {0};
            final long[] lastDrops = {0};
            for (long tt = 10L; tt <= 100L; tt += 10L) {
               final long t = tt;
               helper.runAtTickTime(t, () -> {
                  int dmg = maid.getAvailableInv(true).getStackInSlot(0).getDamageValue();
                  java.util.List<ItemEntity> nearInBox = helper.getLevel().getEntitiesOfClass(ItemEntity.class, ownBox);
                  // 2026-09-21 二次收紧 (实测驱动): 仅统计**被刷方块附近 (≤2 格)** 的掉落物 —
                  //   实测载荷 `首物品=1 stone_button@264,-60,231` (盒内**边缘** z=minZ, 距沙砾 3+ 格) 证明:
                  //   结构盒即使精确到边界, 外来物品仍会在**窗口内**飘/被推入边界格 ✗ (清场只在 t=0 生效)
                  //   物理依据: 刷子**真**误处理该方块 ⇒ 掉落必出现在**方块处**(≤1 格) ⇒ 2 格余量足够; 边界飘入物离 3+ 格 ⇒ 排除 ✓
                  final double dropR2 = 4.0;   // 2² — 距方块中心
                  final double cx = plain.getX() + 0.5, cy = plain.getY() + 0.5, cz = plain.getZ() + 0.5;
                  long drops = nearInBox.stream()
                          .filter((ie) -> ie.distanceToSqr(cx, cy, cz) <= dropR2)
                          .count();
                  boolean changed = !helper.getLevel().getBlockState(plain).is(Blocks.GRAVEL);
                  lastDmg[0] = dmg;
                  lastDrops[0] = drops;
                  if (dmg != 0 || drops > 0L || changed) {
                     violations[0]++;
                     if ("-".equals(firstBad[0])) {
                        // 失败信息自证 (gametest README checklist 4): 带首个**近方块**违规物品的身份与坐标
                        String first = "-";
                        for (ItemEntity ie : nearInBox) {
                           if (ie.distanceToSqr(cx, cy, cz) <= dropR2) {
                              first = ie.getItem() + "@" + ie.blockPosition().toShortString() + " d=" + String.format("%.1f", Math.sqrt(ie.distanceToSqr(cx, cy, cz)));
                              break;
                           }
                        }
                        firstBad[0] = "t=" + t + " dmg=" + dmg + " drops=" + drops + " 方块被改变=" + changed
                                + " 首物品=" + first + " 盒=[" + (int) ownBox.minX + "," + (int) ownBox.minZ
                                + ".." + (int) ownBox.maxX + "," + (int) ownBox.maxZ + "]";
                     }
                  }
               });
            }
            helper.runAtTickTime(110L, () -> {
               // 几何自证 (每轮都打, 通过轮也给数据): 盒 / 沙砾 / 女仆 / 结构原点 — 用于判定"物品是否真在本结构内"
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BRUSH-GEO] box=[{},{},{}..{},{},{}] plain={} maid={} origin={}",
                       (int) ownBox.minX, (int) ownBox.minY, (int) ownBox.minZ,
                       (int) ownBox.maxX, (int) ownBox.maxY, (int) ownBox.maxZ,
                       plain.toShortString(), maid.blockPosition().toShortString(), structOrigin.toShortString());
               LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BRUSH-NONE] 窗口结束: 违规={} 首违={} 末次dmg={} 末次drops={}",
                       violations[0], firstBad[0], lastDmg[0], lastDrops[0]);
               if (violations[0] > 0) {
                  helper.fail("普通沙砾被误处理 (违规 " + violations[0] + " 次, 首次: " + firstBad[0] + ")");
               } else {
                  helper.succeed();
               }
            });
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 200
   )
   public static void lmaExplorerMapTreasure(GameTestHelper helper) {
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         ItemStack map = new ItemStack(Items.FILLED_MAP, 1);
         BlockPos target = new BlockPos(12345, 0, -89);
//? if 1.20.1 {
         MapItemSavedData.addTargetDecoration(map, target, "+", net.minecraft.world.level.saveddata.maps.MapDecoration.Type.RED_X);
//?} else {
         MapItemSavedData.addTargetDecoration(map, target, "+", net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X);
//?}
         maid.getAvailableInv(true).insertItem(0, map, false);
         TaskPipeline pl = TaskRegistry.get("explorer_map").pipeline();
         ServerLevel sl = (ServerLevel)maid.level();
         pl.tick(sl, maid);
         CompoundTag pd = MaidData.pl(maid, "explorer_map");
         String announced = pd.getString("explorer_announced");
         LittleMaidMoreAction.LOGGER.warn("[GAMETEST-MAP-TREASURE] announced={}", announced);
         if (announced.isEmpty()) {
            helper.fail("藏宝图坐标未读出 (RED_X 目标装饰读取失败)");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 200
   )
   public static void lmaTorchLightRecover(GameTestHelper helper) {
      EntityMaid maid = spawnMaid(helper);
      if (maid != null) {
         maid.setNoAi(true);
         clearMaidTaskState(maid);
         maid.getAvailableBackpackInv().insertItem(0, new ItemStack(Items.TORCH, 4), false);
         WorldInfo worldInfo = new WorldInfo(false, false, false, 0, 0, "", "NONE", 18000L, "NIGHT", "", "");
         EnvSnapshot snap = new EnvSnapshot(((ServerLevel)maid.level()).getGameTime(), Map.of(), worldInfo);
         TorchLightPipeline pipe = (TorchLightPipeline)TaskRegistry.get("torch_light").pipeline();
         pipe.onSignal(maid, snap, "env:DARKNESS");
         boolean lit = maid.getOffhandItem().is(Items.TORCH);
         if (!lit) {
            helper.fail("点火失败 (onSignal 未放火把)");
         } else {
            TaskDispatcher.submitPassive(maid, "torch_light");
            ServerLevel sl = (ServerLevel)maid.level();
            pipe.tick(sl, maid);
            int brightness = LightQuery.getBrightness(sl, maid.blockPosition());
            ItemStack offNow = maid.getOffhandItem();
            boolean torchInBackpack = false;
            var inv = maid.getAvailableBackpackInv();

            for (int i = 0; i < inv.getSlots(); i++) {
               if (inv.getStackInSlot(i).is(Items.TORCH)) {
                  torchInBackpack = true;
                  break;
               }
            }

            boolean passed = false;
            if (brightness >= 9) {
               passed = offNow.isEmpty() && torchInBackpack;
            } else {
               passed = offNow.is(Items.TORCH);
            }

            LittleMaidMoreAction.LOGGER
               .warn("[GAMETEST-TORCH-RECOVER] brightness={} offNow={} inBackpack={} passed={}", new Object[]{brightness, offNow, torchInBackpack, passed});
            if (!passed) {
               helper.fail("恢复逻辑错误: 亮度=" + brightness + " offhand=" + offNow + " inBackpack=" + torchInBackpack);
            } else {
               helper.succeed();
            }
         }
      }
   }

    /**
     * **被动任务"无启动者"死链修复验证** (v79.63.4 用户实测「扔探险家地图给女仆没反应」):
     * 背包放地图 ⇒ **不手动 submitPassive** ⇒ 引擎周期检查 (开关开 + 未运行) 应**自动启动** explorer_map。
     *
     * <p>根因: 两个被动 tick 通道原先只 tick **已 in_progress** 的任务 ⇒ 没有自启动者的任务型被动
     * (explorer_map / jiuhu_milk) 注册了、有驱动器, 却**永不运行** ✗ (旧用例手动提交 ⇒ 掩盖死链)。
     */
    @GameTest(
        template = "game_test",
        timeoutTicks = 100
    )
    public static void lmaExplorerMapAutoStart(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        // 用户场景: 地图进她背包 (不手动提交任务)
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.FILLED_MAP, 1), false);
        ServerLevel sl = (ServerLevel) maid.level();
        var passives = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.passiveTasksList();
        // 引擎周期检查 (now=0 ⇒ 命中 PASSIVE_CHECK_INTERVAL 周期块 ⇒ 触发"开关开且未运行 ⇒ 自动启动")
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                .tickPassiveFor(sl, maid, passives, 0L);
        String state = maid.getPersistentData().getString(
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.passiveKey("explorer_map"));
        // 第二次调用: 已在运行 ⇒ 应真正 tick 到管线 (读背包/去重), 不抛异常
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                .tickPassiveFor(sl, maid, passives, 100L);
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-MAP-AUTO] explorer_map state={}", state);
        if (com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.STATE_IN_PROGRESS.equals(state)) {
            helper.succeed();
        } else {
            helper.fail("explorer_map 未被自动启动 (state=" + state + ") — 被动任务无启动者的死链未修");
        }
    }
}
