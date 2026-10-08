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
 * LMA gametest — Tactical 域用例 (**14 条**)。
 *
 * <p>2026-09-21 由 {@code LmaGameTests} (6490 行) 拆分 — **纯搬移**;
 * 断言/判据/超时/template/用例名 (test ID) **逐字不变** (由 scripts/gametest-ids.mjs 121=121 把关)。
 * <p>⚠ 两平台源码实测: holder 与 prefix 按**方法所在类**读取、继承不生效 ⇒ 本类**自持**两注解 (与拆前一致)。
 */
@GameTestHolder(LittleMaidMoreAction.MOD_ID)
@PrefixGameTestTemplate(value = false)
public class LmaTacticalGameTests extends LmaGameTestSupport {

    /**
     * MLG 摔落自救端到端 (v79.61x) — 背包水桶 + 高空坠落:
     * SelfRescueTrigger 摔落预触发提交 → MlgRescueCoordinator 落点倒水 → 落地无伤。
     * (无水桶对照: 9 格坠落 = 6 点伤害, 掉血事件触发后无自救动作自终结)
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
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
        // ── 2026-09-19 改造: 断言从 "hp 一点都不许掉" 改为 **"自救生效 = 落地且没摔死"** ──
        // 实测 (逐 tick 探针 25 轮采样): 倒水确实发生 (t=21 水桶→空桶、t=22 已在水里、t=23 落地),
        // 但约 1/6 轮次落地后仍有小额伤害 (1 点) 或主手被"从地面水补桶"改回水桶 ⇒ 原断言 (hp 不降) 与
        // "主手必须是空桶" 都会误判真自救为失败 ✗。
        // 真正要验的是: **摔落 17 格仍活着** (未自救 = 12 点伤害 ⇒ hp 8; 自救生效 ⇒ 接近满血) + 已落地。
        // ── 2026-09-19 常驻自证探针 (默认零噪音): 只在**血量发生变化**时打印一行 ⇒ 那"落地后 1 点伤害"
        //   再出现时能立刻看到**发生时刻 + 伤害类型** (配合下面的 LivingHurtEvent 监听), 不必再反复全量采样 ──
        final float[] hpTrack = {hpBefore};
        for (long tt = 1L; tt <= 150L; tt += 1L) {
            final long t = tt;
            helper.runAtTickTime(t, () -> {
                float hp = maid.getHealth();
                if (Math.abs(hp - hpTrack[0]) > 0.01f) {
                    BlockPos mp = maid.blockPosition();
                    LittleMaidMoreAction.LOGGER.warn(
                            "[GAMETEST-MLG] 血量变化 t={} {} → {} pos={} onGround={} inWater={} dy={} fall={} "
                                    + "脚下={} 本体={} 头={} 主手={}",
                            t, hpTrack[0], hp, mp.toShortString(), maid.onGround(), maid.isInWater(),
                            String.format("%.3f", maid.getDeltaMovement().y),
                            String.format("%.2f", maid.fallDistance),
                            helper.getLevel().getBlockState(mp.below()).getBlock().getName().getString(),
                            helper.getLevel().getBlockState(mp).getBlock().getName().getString(),
                            helper.getLevel().getBlockState(mp.above()).getBlock().getName().getString(),
                            maid.getMainHandItem().getItem());
                    hpTrack[0] = hp;
                }
            });
        }
        pollUntil(helper, () -> maid.onGround() && maid.getHealth() > hpBefore - 6.0f,
                150L, "MLG 自救: 落地 + 未摔死 (hp=" + hpBefore + "起)",
                maid, ground);
    }

    /**
     * v79.62.5 被埋窒息瞬破 (self_rescue 层 1) — 女仆头卡进窒息方块 (沙子):
     * SelfRescuePipeline.tick → SelfRescueCoordinator 判定 AABB 相交 + suffocating
     * → 瞬破脱困. 验证: 沙子被破坏 + 女仆脱困 (无窒息).
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaSelfRescueBuried(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        // 女仆站 (7,1,7) 上方 (7,2,7); 身体高 ~1.7 → 头到 y~2.7, 在 (7,3,7) 格内
        // 头顶 (7,3,7) 放沙子 (suffocating=true) → 头 AABB 与沙相交 → 被埋判定
        BlockPos headBlock = helper.absolutePos(new BlockPos(7, 3, 7));
        helper.getLevel().setBlockAndUpdate(headBlock, net.minecraft.world.level.block.Blocks.SAND
                .defaultBlockState());
        // 确保女仆在沙下方 (头卡进沙: 女仆 y 移到沙格内)
        BlockPos maidPos = helper.absolutePos(new BlockPos(7, 2, 7));
        maid.moveTo(maidPos.getX() + 0.5, maidPos.getY() + 0.6, maidPos.getZ() + 0.5,
                maid.getYRot(), maid.getXRot());
        boolean intersects = maid.getBoundingBox().intersects(
                helper.getLevel().getBlockState(headBlock).getCollisionShape(
                        helper.getLevel(), headBlock).bounds().move(headBlock));
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BURIED] intersects={}", intersects);
        if (!intersects) {
            helper.fail("女仆 AABB 未与沙相交 (埋入失败, y 需调)");
            return;
        }
        // 驱动 self_rescue tick (走被埋判定链)
        var pl = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get("self_rescue").pipeline();
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
        pl.tick(sl, maid);
        boolean dug = helper.getLevel().getBlockState(headBlock).isAir();
        boolean stillIntersects = dug ? false : maid.getBoundingBox().intersects(
                helper.getLevel().getBlockState(headBlock).getCollisionShape(
                        helper.getLevel(), headBlock).bounds().move(headBlock));
        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-BURIED] dug={}", dug);
        if (!dug) helper.fail("被埋沙未被瞬破 (dug=" + dug + ")");
        else if (stillIntersects) helper.fail("沙破后仍相交 (未脱困)");
        else helper.succeed();
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

        // 第二个女仆 (v79.62.5: spawnMaid 直接创建 — 永不返回第一个; 单次赋值保证 lambda effectively final)
        final EntityMaid target = spawnMaid(helper);
        if (target == null) {
            helper.fail("第二个女仆未生成");
            return;
        }
        if (target == maid) {
            helper.fail("第二个女仆与第一个相同");
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

        // v79.62.5: runAfterDelay 等实体注册 — addFreshEntity 后 uuid 索引下 tick 才生效,
        // 同步 tick resolveTarget 查不到 target (实测 cancel) → 延迟 3 tick 再驱动
        helper.runAfterDelay(3, () -> {
        // tick 1: MOVE → LOOK (转换: YSM 动画请求 + 随机音频 + 挥击骰子)
        pl.tick(sl, maid);
        if (!"LOOK".equals(data.getString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_STATE))) {
            LittleMaidMoreAction.LOGGER.warn("[GAMETEST-HAQI] state={} targetAlive={} targetDistSqr={} maidPos={} targetPos={}",
                    data.getString(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline.KEY_STATE),
                    target != null && target.isAlive(),
                    target != null ? target.blockPosition().distSqr(maid.blockPosition()) : -1,
                    maid.blockPosition(), target != null ? target.blockPosition() : null);
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
        });
    }

    /**
     * 哈气对主人 — 女仆 tame 玩家后, 手动构造 target_type=owner 状态,
     * 断言 MOVE→LOOK (owner 分支: 网络包 + 动画) + 挥击玩家掉血 (hit_ticks=-1 防重复)。
     * 主人 = 玩家 (ServerPlayer, 用户裁定); 不反击由玩家无自动反击保证。
     */
    @GameTest(timeoutTicks = 200,template = "game_test")
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
     * v79.74 回归 (用户实测: "蹲下右键喂 Token, 女仆**坐下了**" ✗): **喂 Token 不得改变坐姿** ✓
     *
     * <p>完全复刻 TLM `EntityMaid#mobInteract` 的做法 ✓: 直接向事件总线 post
     * {@link com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent} ✓ (事件链 = post →
     * isCanceled → interactLivingEntity → openMaidGui ✓)。
     *
     * <p>断言 (对标附魔金苹果规格 ✓ — 用户裁定):
     * ① **坐姿不变** ✓ ② **消耗 1 个 Token** ✓ ③ 有「缓慢恢复 I」 ✓
     * <p>并挂一个 **LOWEST 探针** ✓ ⇒ 直接证明"cancel 是否阻止 LOWEST 监听器"(TLM 的坐下正是 LOWEST ✓),
     * 把此前靠猜的那个语义变成**可断言事实** ✓。
     */
    @GameTest(template = "game_test", timeoutTicks = 200)
    public static void lmaTokenFeedSneakClick(GameTestHelper helper) {
        com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        // ★★ 关键: **保留 AI** ✓ (用户实测场景 = 真实游戏, AI 在跑 ✓) ——
        //    上一版夹具用了 setNoAi(true) ⇒ **AI 行为被关掉** ⇒ "吃东西坐下"这类 AI 反应根本不发生 ✗
        //    ⇒ 夹具全绿而用户仍看到坐下 ✗ (夹具低估了真实环境 ⇒ 典型"夹具太干净"陷阱 ✓)
        maid.setNoAi(false);

        // ★ 平台分支 (铁律: MC 同名 API 双平台签名可能不同 ✗): 1.20.1 无参 / 1.21.1 要 GameType ✓
        //? if 1.20.1 {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        //?} else {
        net.minecraft.world.entity.player.Player player =
                helper.makeMockPlayer(net.minecraft.world.level.GameType.DEFAULT_MODE);
        //?}
        player.setShiftKeyDown(true);                       // 模拟"玩家蹲下" ✓
        net.minecraft.world.item.ItemStack token =
                new net.minecraft.world.item.ItemStack(com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaItems.TOKEN.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, token);

        boolean sittingBefore = maid.isMaidInSittingPose();
        final boolean[] lowestGot = {false};
        final boolean[] lowestCanceled = {false};
        Object probe = new Object() {
            //? if 1.20.1 {
            @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
            //?} else {
            @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
            //?}
            public void onLowest(com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent e) {
                if (e.getStack().is(com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaItems.TOKEN.get())) {
                    lowestGot[0] = true;
                    lowestCanceled[0] = e.isCanceled();
                }
            }
        };
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-TOKEN] 前置: 坐姿={} 手上={} 蹲下={}", sittingBefore, token.getCount(), player.isShiftKeyDown());
        try {
            //? if 1.20.1 {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(probe);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent(player, maid, token));
            //?} else {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(probe);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent(player, maid, token));
            //?}
        } catch (Throwable t) {
            helper.fail("post InteractMaidEvent 抛异常: " + t);
            return;
        } finally {
            //? if 1.20.1 {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(probe);
            //?} else {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(probe);
            //?}
        }

        boolean sittingAfter = maid.isMaidInSittingPose();
        boolean hasRegen = maid.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION) != null;
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-TOKEN] 结果: 坐姿 {}→{} · 手上剩 {} · 恢复={} · LOWEST收到={} canceled={}",
                sittingBefore, sittingAfter, token.getCount(), hasRegen, lowestGot[0], lowestCanceled[0]);
        if (sittingAfter != sittingBefore) {
            helper.fail("喂 Token 改变了坐姿 (" + sittingBefore + "→" + sittingAfter + ") ✗ 与金苹果规格不符(用户裁定) ✓");
        } else if (token.getCount() != 0) {
            helper.fail("没消耗 Token (剩 " + token.getCount() + ") ✗");
        } else if (!hasRegen) {
            helper.fail("没给「缓慢恢复 I」✗");
        } else {
            helper.succeed();   // ★ 不做"等 60t"的延迟断言 ✗ (用户: "就一个坐下你加 300s 吗" ✓ —
                                //   用户看到的坐下是**当次交互立刻发生** ✓ ⇒ 立即断言即覆盖 ✓; 套件保持快 ✓)
        }
    }

    /**
     * v79.73 回归 (用户需求固化): **Token** — 食用结算 + 饰品佩戴持续回血 + 摘下即停 ✓
     *
     * <p>断言: ① 食用结算 (`TokenItem.applyEffects` — 喂女仆/女仆自吃/玩家吃共用的公共出口 ✓):
     * 扣血到 10 ⇒ 回 5 到 **15** ✓ · 女仆饱食 **+2** (上限 20 ✓) · 「**缓慢恢复 I**」amp 0 / ≥599t ✓
     * ② 佩戴: 塞进 TLM 饰品槽 ⇒ 跑 60t 后**仍持有**恢复 (饰品 `onTick` 每 40t 续 ✓)
     * ③ 摘下: 取出 ⇒ `onTakeOff` 清效果 (最迟自然过期) ✓
     */
    @GameTest(template = "game_test", timeoutTicks = 280)
    public static void lmaTokenEffects(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;
        clearMaidTaskState(maid);
        maid.setNoAi(true);
        var regenType = net.minecraft.world.effect.MobEffects.REGENERATION;
        var token = com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaItems.TOKEN.get();

        // ── ① 食用结算 ──
        maid.setHealth(10.0f);
        int hungerBefore = maid.getHunger();
        com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenItem.applyEffects(maid);
        var regen1 = maid.getEffect(regenType);
        boolean healed = Math.abs(maid.getHealth() - 15.0f) < 0.01f;
        boolean hungerUp = maid.getHunger() == Math.min(20, hungerBefore + 2);
        boolean regenOk = regen1 != null && regen1.getAmplifier() == 0 && regen1.getDuration() >= 599;
        if (!healed || !hungerUp || !regenOk) {
            helper.fail("① 食用结算不符: 血=" + maid.getHealth() + "(期望15) 饱食=" + hungerBefore + "->" + maid.getHunger()
                    + " 恢复=" + (regen1 == null ? "无" : ("amp" + regen1.getAmplifier() + "/" + regen1.getDuration() + "t")));
            return;
        }

        // ── ② 塞进饰品槽 (TLM: 只判"是否注册过的饰品", 不看数量 ✓ 可堆叠 ✓) ──
        // ⚠ 必须先把 ①步的 600t 食用效果清掉 ✗ —— 否则它会把 ②③ 的读数盖住 (首次实跑就是被它误导:
        //   "摘下 70t 后还剩 470t" 其实是 ① 那 600t 的残留, 不是饰品没停 ✗)
        maid.removeEffect(regenType);
        var bauble = maid.getMaidBauble();
        boolean equipped = false;
        for (int i = 0; i < bauble.getSlots() && !equipped; i++) {
            if (bauble.getStackInSlot(i).isEmpty()) {
                equipped = bauble.insertItem(i, new ItemStack(token), false).isEmpty();
            }
        }
        if (!equipped) { helper.fail("② 饰品槽塞不进去 (TLM 饰品校验/槽位异常)"); return; }

        helper.runAtTickTime(60, () -> {
            var regen2 = maid.getEffect(regenType);
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[GAMETEST-TOKEN] 佩戴 60t 后 恢复={}", regen2 == null ? "无" : ("amp" + regen2.getAmplifier() + "/" + regen2.getDuration() + "t"));
            // 佩戴期看到的应是**饰品自己的短刷新** (60t 时长, 每 40t 补) ⇒ 时长必然 ≤60 ✓
            // (若看到 600t ⇒ 说明被别的长效果盖住 / 饰品没在刷 ✗)
            if (regen2 == null) { helper.fail("② 佩戴后未补「缓慢恢复」(饰品 onTick 未生效?) ✗"); return; }
            if (regen2.getDuration() > 60) { helper.fail("② 佩戴期效果时长 " + regen2.getDuration() + "t > 60t (非饰品自身刷新 ✗)"); return; }

            // ── ③ 摘下 ⇒ 必须**消失** (onTakeOff 立即清, 最迟 60t 自然过期 ✓) ──
            for (int i = 0; i < bauble.getSlots(); i++) {
                if (bauble.getStackInSlot(i).is(token)) bauble.extractItem(i, 1, false);
            }
            helper.runAtTickTime(130, () -> {
                var regen3 = maid.getEffect(regenType);
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                        "[GAMETEST-TOKEN] 摘下 70t 后 恢复={}", regen3 == null ? "无(期望)" : ("amp" + regen3.getAmplifier() + "/" + regen3.getDuration() + "t"));
                if (regen3 != null) {
                    helper.fail("③ 摘下 70t 后仍在续 " + regen3.getDuration() + "t (onTakeOff/过期都未生效?) ✗");
                } else {
                    helper.succeed();
                }
            });
        });
    }

    /**
     * v79.66o 回归 (用户需求 ✓): **女仆偷吃 Token** —
     * ① 主人身上整组 Token ⇒ 一次性搬进女仆背包 (**不满一组也有多少偷多少**)
     * ② **偷到就开始吃** ⇒ 立刻吃 1 个 (背包净得 N-1; 效果走 TokenItem.applyEffects ✓)
     * ③ **超出 8 格 ⇒ 不偷** ✓ ④ **女仆背包放不下 ⇒ 不偷 (原子, 一点不拿)** ✓
     * ⑤ 主人没有 Token ⇒ 不偷 ✓ ⑥ 强制路径 (`forceAttempt`, /lma token steal 用) 跳过冷却/概率 ✓
     *
     * <p>注: 概率/冷却的**语义**由纯函数单测 `TokenStealMathTest` 锁定 (6000t 从上次判定起算 ✓);
     * 本用例测**搬运/吃/距离/背包**这些需要世界与实体的部分 ✓。
     */
    @GameTest(template = "game_test", timeoutTicks = 300)
    public static void lmaTokenSteal(GameTestHelper helper) {
        // mock 玩家 (双平台 API 不同 — 与 spawnMaid() 内同款 stonecutter ✓)
//? if 1.20.1 {
        net.minecraft.world.entity.player.Player owner = helper.makeMockSurvivalPlayer();
//?} else {
        net.minecraft.world.entity.player.Player owner = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
//?}
        EntityMaid maid = spawnMaid(helper, owner);
        if (maid == null) return;
        clearMaidTaskState(maid);
        var sl = (net.minecraft.server.level.ServerLevel) maid.level();
        // Token 物品: fact-forcing — 取注册物品而非猜测 id ✓
        var tokenItem = com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaItems.TOKEN.get();

        // 主人移到女仆 3 格内 (8 格门控 ✓)
        BlockPos maidPos = maid.blockPosition().immutable();
        owner.moveTo(maidPos.getX() + 3.5, maidPos.getY(), maidPos.getZ() + 0.5, 0, 0);

        // ── ① 整组 20 个 ⇒ 全偷 + 吃掉 1 ⇒ 女仆净得 19 ✓ ──
        owner.getInventory().setItem(0, new ItemStack(tokenItem, 20));
        int ownerBefore = com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService
                .countTokens(owner);
        if (ownerBefore != 20) {
            helper.fail("夹具异常: 主人 token=" + ownerBefore + " (期望 20)");
            return;
        }
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService
                .forceAttempt(sl, maid, owner);
        if (!ok) {
            helper.fail("强制偷失败 (条件: 8格内 + 主人有 token + 背包放得下 — 见 /lma token info)");
            return;
        }
        int ownerAfter = com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService
                .countTokens(owner);
        int maidAfter = com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService
                .countTokens(maid);
        if (ownerAfter != 0) {
            helper.fail("主人整组未被偷走: 剩余=" + ownerAfter + " (应 0 — 整组搬运 ✓)");
            return;
        }
        if (maidAfter != 19) {
            helper.fail("女仆 token=" + maidAfter + " (期望 19 = 偷 20 后立刻吃掉 1 ✓ 用户裁定「偷到就开始吃」)");
            return;
        }

        // ── ② 不满一组 (7 个) ⇒ 也有多少偷多少 ⇒ 主人 0, 女仆 19 + 6 = 25 ✓ ──
        owner.getInventory().setItem(0, new ItemStack(tokenItem, 7));
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService.forceAttempt(sl, maid, owner)) {
            helper.fail("不满一组偷取失败 (应支持 7/64 这种部分组)");
            return;
        }
        int ownerAfter2 = com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService
                .countTokens(owner);
        int maidAfter2 = com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService
                .countTokens(maid);
        if (ownerAfter2 != 0 || maidAfter2 != 25) {
            helper.fail("不满一组语义错: 主人剩=" + ownerAfter2 + " (应 0) 女仆=" + maidAfter2 + " (应 25)");
            return;
        }

        // ── ③ 超出 8 格 ⇒ 不偷 ✓ ──
        owner.getInventory().setItem(0, new ItemStack(tokenItem, 5));
        owner.moveTo(maidPos.getX() + 20.5, maidPos.getY(), maidPos.getZ() + 0.5, 0, 0);
        boolean far = com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService
                .forceAttempt(sl, maid, owner);
        int ownerFar = com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService
                .countTokens(owner);
        if (far || ownerFar != 5) {
            helper.fail("超距仍偷: ok=" + far + " 主人剩=" + ownerFar + " (期望 false / 5 — 8 格门控 ✓)");
            return;
        }
        // 回到 8 格内
        owner.moveTo(maidPos.getX() + 3.5, maidPos.getY(), maidPos.getZ() + 0.5, 0, 0);

        // ── ④ 女仆背包满 ⇒ 不偷 (原子: 主人一点不少) ✓ ──
        //    ⚠ 夹具要点 (第一版写错实录): 她身上**还有 25 个 Token (①②偷来的余量 ⇒ 可堆叠空间)** ⇒
        //    只填石头时新 Token 会**并进已有 Token 堆** ⇒ "放得下"是**正确**行为 ✗ (不是本步要测的场景)
        //    ⇒ 先清掉她身上的 Token, 再反复填石头, 并用**模拟插入**确认确实放不下 ✓
        var minv = maid.getAvailableInv(false);
        for (int i = 0; i < minv.getSlots(); i++) {
            if (minv.getStackInSlot(i).is(tokenItem)) minv.extractItem(i, 64, false);
        }
        for (int pass = 0; pass < 4; pass++) {
            for (int i = 0; i < minv.getSlots(); i++) {
                minv.insertItem(i, new ItemStack(net.minecraft.world.item.Items.STONE, 64), false);
            }
            int probeRemaining = 5;
            for (int i = 0; i < minv.getSlots() && probeRemaining > 0; i++) {
                probeRemaining = minv.insertItem(i, new ItemStack(tokenItem, probeRemaining), true).getCount();
            }
            if (probeRemaining > 0) break;      // 确认放不下 5 个 ⇒ 夹具就绪 ✓
        }
        owner.getInventory().setItem(0, new ItemStack(tokenItem, 5));
        boolean full = com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService
                .forceAttempt(sl, maid, owner);
        int ownerFull = com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService
                .countTokens(owner);
        if (full || ownerFull != 5) {
            helper.fail("背包满仍偷: ok=" + full + " 主人剩=" + ownerFull + " (期望 false / 5 — 原子不偷 ✓)");
            return;
        }

        // ── ⑤ 主人没有 Token ⇒ 不偷 (no-op, 不报错) ✓ ──
        owner.getInventory().setItem(0, ItemStack.EMPTY);
        if (com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealService.forceAttempt(sl, maid, owner)) {
            helper.fail("主人没 token 竟然偷成功了 (应为 no-op)");
            return;
        }

        LittleMaidMoreAction.LOGGER.warn("[GAMETEST-TOKEN-STEAL] 全部断言通过 ✓ 偷取/进食/距离/背包/空手 5 项");
        helper.succeed();
    }

   @GameTest(
      template = "game_test",
      timeoutTicks = 600
   , batch = "z_tower1")
   public static void lmaDefenseTower(GameTestHelper helper) {
      clearTowerTestBlocks(helper);   // #293: 清残留塔 (防顺序干扰)
      Player owner = registeredOwner(helper);
      // v79.63: 原 (7,1,7) + 目标 offset(10,0,0) = x17 越出 16³ 模板 → 目标无地面下坠 → 箭脱靶
      // (实测 19/20 箭脱靶, 仅中 2 箭). 移到 (3,1,7) 后目标 x=13 落在模板内 (距离仍 10 格).
      // ★ 2026-09-23 批 B1-b (同族第二个用例, 同款几何越界 ✗): 原 (3,1,7)+靶 +10+setRadius(20)
      //   ⇒ 索敌球必然越出 16³ (模板 size=16×16×16 ✓ 见下方案注释) ⇒ 邻居槽位怪可被"最近优先"选中 ✗
      //   ⇒ 与 BlockedNearFarTarget 同修: 塔 (7,1,7) + 靶 +6 + 半径 7 ⇒ 球 [0,14]³ ⊆ [0,15]³ ✓
      //   (16³ 结构里 "10 格远靶" 与 "球不出结构" 不可兼得 ⇒ 靶距收 6, 仍验证"远距离射击 + 命中致死" ✓)
      BlockPos towerPos = new BlockPos(7, 1, 7);
      BlockPos towerAbs = helper.absolutePos(towerPos);
      helper.getLevel().setBlockAndUpdate(towerAbs, ((DefenseGarageKitBlock)LmaBlocks.GARAGE_KIT_DEFENSE.get()).defaultBlockState());
      if (helper.getLevel().getBlockEntity(towerAbs) instanceof DefenseGarageKitBlockEntity tower) {
         // ★ 2026-09-23 批 B1-b: 本用例**从未设半径** ✗ ⇒ 用默认(历史上 64) ⇒ 扫描球巨大 ⇒ 邻居槽位怪被
         //   "最近优先"选中 ⇒ 与 BlockedNearFarTarget 同因 (几何越界 ✗) ⇒ 显式收进结构内 ✓
         tower.setRadius(7);
         {
            final int r = 7, targetDx = 6;
            int cx = towerPos.getX(), cz = towerPos.getZ();
            if (cx - r < 0 || cx + r > 15 || cz - r < 0 || cz + r > 15) {
               helper.fail("夹具不成立(批B1-b): 索敌球 x[" + (cx - r) + "," + (cx + r) + "] z[" + (cz - r) + "," + (cz + r)
                       + "] 越出 16³ 结构 [0,15] ⇒ 邻居槽位怪会被最近优先选中 ✗");
               return;
            }
            if (!(targetDx < r)) {
               helper.fail("夹具不成立(批B1-b): 靶距 " + targetDx + " 必须 < 半径 " + r);
               return;
            }
         }
         tower.setOwnerUuid(owner.getUUID());
         tower.getAmmo().setItem(0, new ItemStack(Items.BOW, 1));
         tower.getAmmo().setItem(1, new ItemStack(Items.ARROW, 10));
         tower.setChanged();
         BlockPos zombieAbs = helper.absolutePos(towerPos.offset(6, 0, 0));   // ★ 批 B1-b: +10 ⇒ 球越界; 改 +6 ✓
         Zombie zombie = (Zombie)EntityType.ZOMBIE.create(helper.getLevel());
         if (zombie == null) {
            helper.fail("僵尸生成失败");
         } else {
            zombie.moveTo((double)zombieAbs.getX() + 0.5, (double)zombieAbs.getY() + 1.0, (double)zombieAbs.getZ() + 0.5, 0.0F, 0.0F);
            zombie.setNoAi(true);
            helper.getLevel().addFreshEntity(zombie);
            float hpBefore = zombie.getHealth();
            helper.runAfterDelay(550L, () -> {
               if (zombie.isAlive() && zombie.getHealth() >= hpBefore) {
                  helper.fail("僵尸未被射中 (塔未开火? 弹药=" + tower.getAmmo().getItem(0).getCount() + ")");
               } else if (zombie.isAlive()) {
                  // v79.63: 修诊断槽位 — slot0=弓, slot1=箭 (原读 slot0 把弓数量当"箭余")
                  helper.fail("僵尸中箭但未死亡 (伤害=" + zombie.getHealth() + "/" + hpBefore + ", 箭余=" + tower.getAmmo().getItem(1).getCount() + ")");
               } else {
                  helper.succeed();
               }
            });
         }
      } else {
         helper.fail("防御塔方块实体未生成");
      }
   }

   /**
    * 防御塔: 近墙被挡的目标不应阻止射击远处可见目标 (v79.62.3 修复的行为验证)。
    *
    * <p><b>v79.63 修测试场景 (根因: 目标在模板外)</b>: 原场景塔 (7,1,7) / 僵尸
    * {@code towerPos.offset(0,0,24)} = **z=31**, 但 {@code game_test} 模板 size = **16×16×16**
    * (nbt 实证) → 僵尸落在模板外**无地面** → 受重力**持续下坠** (setNoAi 只关 AI 不关物理)
    * → 箭瞄的是扫描时刻的位置, 箭到 (8t 后) 僵尸已下移 → **命中与否随机**, 且取决于相邻
    * 测试区域是否恰好提供地面 (受测试执行顺序影响 → 表现为"时挂时过")。
    * 现全部收进模板内: 塔 (7,1,1) / 墙 (10,1,1) / 史莱姆 (12,2,1) / 僵尸 (7,1,15) —
    * 距离 **14 格** (满足"目标放 10 格外"纪律), 且与用户实测报告的距离 (16 格) 同量级。
    *
    * <p><b>v79.63 修半径 (真根因, 判决实验实证)</b>: 原 {@code setRadius(64)} + AABB 全高 +
    * **按水平最近选目标** → gametest 世界内相邻测试区域的怪比本区僵尸更近 → 塔**优先打外区怪**。
    * 实测: {@code FIRE t=5/25/45/65} 全脱靶 (打外区 4 箭), 直到 {@code t=85} 才转向本区僵尸;
    * 失败轮则是 20 箭全喂外区 (箭余=0 而僵尸满血) → 表现为随机"未被射"。
    * 现半径收到 **20** (只覆盖本区 14 格的僵尸, 打不到 20+ 格外的邻区), 保留"远处可见"语义。
    * <p>另修: 原诊断发现外部箭命中会走原版**过剩伤害分支** ({@code LivingEntity:1101-1111}
    * {@code invulnerableTime>10 且伤害<=lastHurt → return false}, 产品侧多塔联防会浪费箭 —
    * 已记入错题/待裁定, 不在本测试内改产品行为)。
    */
   @GameTest(
      template = "game_test",
      timeoutTicks = 700
   , batch = "z_tower2")
   public static void lmaDefenseTowerBlockedNearFarTarget(GameTestHelper helper) {
      clearTowerTestBlocks(helper);   // #293: 清残留塔 (防顺序干扰)
      Player owner = registeredOwner(helper);
      // ★ 2026-09-23 批 B1 (塔族几何收口): 原 (7,1,1)+靶 +14+半径 20 ⇒ 索敌球越出 16³ 结构 13 格 ✗
      //   ⇒ 半径内邻居槽位的怪会被"最近优先"选中 ("箭喂外区" ✗; 与 chainore 球外野矿同类 ✓)
      //   修: 塔移 (7,1,7)(已在 TOWER_TEST_POSES 清理表 ✓) + 靶子 +6 + 半径 7 ⇒ 球 [0,14]³ ⊆ [0,15]³ ✓
      BlockPos towerPos = new BlockPos(7, 1, 7);
      BlockPos towerAbs = helper.absolutePos(towerPos);
      helper.getLevel().setBlockAndUpdate(towerAbs, ((DefenseGarageKitBlock)LmaBlocks.GARAGE_KIT_DEFENSE.get()).defaultBlockState());
      if (helper.getLevel().getBlockEntity(towerAbs) instanceof DefenseGarageKitBlockEntity tower) {
         tower.setOwnerUuid(owner.getUUID());
         // v79.63: 64 → 20 (只覆盖本测试区域; 64 会打进相邻测试区域的目标 — 判决实验实证)
         // ⚠ 2026-09-21 修正: 上一行注释早已写 "v79.63: 64 → 20" 但**代码漏改** ⇒ 塔扫 64 格会锁到
         //   相邻测试区域残留的野生敌对生物 ⇒ 本测试僵尸"一箭未发"/"箭喂外区" (夹具注释自证的根因) ⇒ 补上 20
         tower.setRadius(7);  // ★ 批 B1: 半径整圈落在 16³ 内 (塔 (7,1,7) ⇒ 上限 7 ✓); 靶 +6 < 7 ✓
         // ★ 批 B1 几何自证 (跨平台算术 ✓ 1.20.1 无 getBounds() ✗): 索敌球必须整圈在 [0,15]³ 内
         {
            final int r = 7, targetDz = 6;
            int cx = towerPos.getX(), cz = towerPos.getZ();
            if (cx - r < 0 || cx + r > 15 || cz - r < 0 || cz + r > 15) {
               helper.fail("夹具不成立(批B1): 索敌球 x[" + (cx - r) + "," + (cx + r) + "] z[" + (cz - r) + "," + (cz + r)
                       + "] 越出 16³ 结构 [0,15] ⇒ 邻居槽位怪会被最近优先选中 ⇒ 复现'箭喂外区' ✗");
               return;
            }
            if (!(targetDz < r)) {   // 靶必须在半径内 (strict < ⇒ 不在边界上, 避免索敌判据边界歧义)
               helper.fail("夹具不成立(批B1): 靶距 " + targetDz + " 必须 < 半径 " + r);
               return;
            }
         }
         tower.getAmmo().setItem(0, new ItemStack(Items.BOW, 1));
         tower.getAmmo().setItem(1, new ItemStack(Items.ARROW, 20));
         tower.setChanged();
         BlockPos wallAbs = helper.absolutePos(towerPos.offset(3, 0, 0));
         helper.getLevel().setBlockAndUpdate(wallAbs, Blocks.STONE.defaultBlockState());
         BlockPos slimeAbs = helper.absolutePos(towerPos.offset(5, 0, 0));
         Slime slime = (Slime)EntityType.SLIME.create(helper.getLevel());
         if (slime != null) {
            slime.moveTo((double)slimeAbs.getX() + 0.5, (double)slimeAbs.getY() + 1.0, (double)slimeAbs.getZ() + 0.5, 0.0F, 0.0F);
            slime.setNoAi(true);
            helper.getLevel().addFreshEntity(slime);
         }

         // v79.63: z+24 → z+14 (模板 16³ 内; 原 24 越界无地面 → 僵尸下坠 → 命中随机)
         BlockPos zombieAbs = helper.absolutePos(towerPos.offset(0, 0, 6));
         Zombie zombie = (Zombie)EntityType.ZOMBIE.create(helper.getLevel());
         if (zombie == null) {
            helper.fail("僵尸生成失败");
         } else {
            zombie.moveTo((double)zombieAbs.getX() + 0.5, (double)zombieAbs.getY() + 1.0, (double)zombieAbs.getZ() + 0.5, 0.0F, 0.0F);
            zombie.setNoAi(true);
            // 2026-09-21 照 lmaDefenseTower 既有手法锚定靶子 (防击退漂移 ⇒ 塔丢索敌/丢 LOS):
            zombie.setPersistenceRequired();
            zombie.setInvulnerable(false);
//? if 1.20.1 {
            var kb = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
               kb.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                       "lma_test_anchor", 1.0,
                       net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            }
//?} else {
            var kb = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
               kb.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                       net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                               com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.MOD_ID, "test_anchor"),
                       1.0,
                       net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
//?}
            helper.getLevel().addFreshEntity(zombie);
            float hpBefore = zombie.getHealth();
            helper.runAfterDelay(600L, () -> {
               if (zombie.isAlive() && zombie.getHealth() >= hpBefore) {
                  helper.fail("远处可见僵尸未被射 (近墙史莱姆不应挡; 箭余=" + tower.getAmmo().getItem(1).getCount() + ")");
               } else if (zombie.isAlive()) {
                  helper.fail("僵尸中箭未死 (hp=" + zombie.getHealth() + ")");
               } else {
                  helper.succeed();
               }
            });
         }
      } else {
         helper.fail("防御塔方块实体未生成");
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 400
   , batch = "z_tower3")
   public static void lmaDefenseTowerDropPreserve(GameTestHelper helper) {
      clearTowerTestBlocks(helper);   // #293: 清残留塔 (防顺序干扰)
      Player owner = registeredOwner(helper);
      BlockPos towerPos = new BlockPos(7, 1, 7);
      BlockPos towerAbs = helper.absolutePos(towerPos);
      helper.getLevel().setBlockAndUpdate(towerAbs, ((DefenseGarageKitBlock)LmaBlocks.GARAGE_KIT_DEFENSE.get()).defaultBlockState());
      if (!(helper.getLevel().getBlockEntity(towerAbs) instanceof DefenseGarageKitBlockEntity tower)) {
         helper.fail("防御塔方块实体未生成");
      } else {
         tower.setOwnerUuid(owner.getUUID());
         tower.getAmmo().setItem(0, new ItemStack(Items.BOW, 1));
         tower.getAmmo().setItem(1, new ItemStack(Items.ARROW, 10));
         tower.setChanged();
         helper.getLevel().setBlockAndUpdate(towerAbs, Blocks.AIR.defaultBlockState());
         List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(towerAbs).inflate(3.0));
         ItemStack drop = null;

         for (ItemEntity ie : items) {
            if (!ie.getItem().isEmpty() && ie.getItem().getItem() == LmaItems.GARAGE_KIT_DEFENSE.get()) {
               drop = ie.getItem();
               break;
            }
         }

         if (drop == null) {
            helper.fail("打掉塔后未找到掉落物");
         } else {
            CompoundTag data = readDropData(helper, drop);
            if (data != null && data.contains("DefenseTowerAmmo")) {
               helper.getLevel().setBlockAndUpdate(towerAbs, ((DefenseGarageKitBlock)LmaBlocks.GARAGE_KIT_DEFENSE.get()).defaultBlockState());
               if (helper.getLevel().getBlockEntity(towerAbs) instanceof DefenseGarageKitBlockEntity tower2) {
                  ((DefenseGarageKitBlock)LmaBlocks.GARAGE_KIT_DEFENSE.get())
                     .setPlacedBy(helper.getLevel(), towerAbs, helper.getLevel().getBlockState(towerAbs), owner, drop);
                  ItemStack restoredBow = tower2.getAmmo().getItem(0);
                  ItemStack restoredArrow = tower2.getAmmo().getItem(1);
                  if (!restoredBow.isEmpty() && restoredBow.is(Items.BOW)) {
                     if (restoredArrow.getCount() != 10) {
                        helper.fail("重放后箭数未恢复: " + restoredArrow.getCount());
                     } else if (!owner.getUUID().equals(tower2.getOwnerUuid())) {
                        helper.fail("重放后主人未恢复");
                     } else {
                        helper.succeed();
                     }
                  } else {
                     helper.fail("重放后弓未恢复: " + restoredBow);
                  }
               } else {
                  helper.fail("重放后防御塔方块实体未生成");
               }
            } else {
               helper.fail("掉落物缺少塔数据 (ammo)");
            }
         }
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 600
   , batch = "z_tower6")
   public static void lmaDefenseTowerDurability(GameTestHelper helper) {
      clearTowerTestBlocks(helper);   // #293: 清残留塔 (防顺序干扰)
      Player owner = registeredOwner(helper);
      // v79.62.5 修复: 塔放大半径 64 (上限) — 世界自然僵尸 20+ 只分布广, 半径 16 可能扫不到;
      // 64 半径必然覆盖多只自然僵尸 → 射击扣耐久 (不 spawn 手动僵尸 — 放置环境脆弱已多轮实证)
      // #297 定论: 三塔必须**拉开** — 原本三塔挤在 z=7/11/15 且共用 3 只僵尸, 实测**御币塔(弹幕, 不耗箭)**
      //   先把僵尸全打死 ⇒ 弓/弩无目标可打 ⇒ `弓 damage=0 且 箭余=32` (看似"塔坏", 实为**目标被抢杀**)。
      BlockPos[] poses = new BlockPos[]{new BlockPos(3, 1, 3), new BlockPos(3, 1, 8), new BlockPos(3, 1, 13)};
      BlockEntity[] tes = new BlockEntity[3];
      for (int i = 0; i < 3; i++) {
         BlockPos abs = helper.absolutePos(poses[i]);
         helper.getLevel().setBlockAndUpdate(abs, ((DefenseGarageKitBlock)LmaBlocks.GARAGE_KIT_DEFENSE.get()).defaultBlockState());
         tes[i] = helper.getLevel().getBlockEntity(abs);
         if (!(tes[i] instanceof DefenseGarageKitBlockEntity tower)) {
            helper.fail("防御塔方块实体未生成 @" + poses[i]);
            return;
         }
         tower.setOwnerUuid(owner.getUUID());
         tower.setRadius(20);  // #293: 自带目标后收窄 (原 64 靠"世界残留僵尸" = 不可复现)
      }
      // #296 定论: 塔闸门正常 (ready=true, interval=20) — 失败在**夹具**: 目标不在扫描箱 (zombiesInBox=0) 或虽在箱内但 **LOS 全失败**。⇒ 目标移到**紧贴塔的同排 2 格** (x=9), 必然开阔且有视线。
      //   原测试靠"世界自然僵尸残留"扣耐久 → 无残留即挂;
      //   现已为每个塔测试分配独立 batch (#293) ⇒ 目标不会被他处塔在并行时打死。
      java.util.List<Zombie> targets = new java.util.ArrayList<>();
      // 每个塔各配一只**对齐同 z**的目标 (距本塔 7 格, 距别的塔 ≥8 格) ⇒ 各塔的"最近目标"都是自己的 ✓
      for (BlockPos tp : new BlockPos[]{new BlockPos(10, 1, 3), new BlockPos(10, 1, 8), new BlockPos(10, 1, 13)}) {
         Zombie z = (Zombie) EntityType.ZOMBIE.create(helper.getLevel());
         if (z == null) { helper.fail("目标僵尸生成失败"); return; }
         z.moveTo(helper.absolutePos(tp).getCenter());
         z.setNoAi(true);                 // #297: 防走失 (自己移动出射程/掉下边缘)
         z.setPersistenceRequired();      // #297: 防**自然消失** — diag 实测 zombiesInBox=0 的主嫌疑
         helper.getLevel().addFreshEntity(z);
         targets.add(z);
      }
        DefenseGarageKitBlockEntity bowTower = (DefenseGarageKitBlockEntity)tes[0];
      bowTower.getAmmo().setItem(0, new ItemStack(Items.BOW, 1));
      bowTower.getAmmo().setItem(1, new ItemStack(Items.ARROW, 32));
      DefenseGarageKitBlockEntity crossTower = (DefenseGarageKitBlockEntity)tes[1];
      crossTower.getAmmo().setItem(0, new ItemStack(Items.CROSSBOW, 1));
      crossTower.getAmmo().setItem(1, new ItemStack(Items.ARROW, 32));
      DefenseGarageKitBlockEntity goheiTower = (DefenseGarageKitBlockEntity)tes[2];
      goheiTower.getAmmo().setItem(0, new ItemStack((ItemLike)InitItems.HAKUREI_GOHEI.get(), 1));

      for (BlockEntity t : tes) {
         t.setChanged();
      }

      if (helper.getLevel().getServer().getPlayerList().getPlayer(owner.getUUID()) == null) {
         helper.fail("[DIAG] mock 玩家不在 PlayerList! owner=" + owner.getUUID() + " 在线玩家数=" + helper.getLevel().getServer().getPlayerList().getPlayers().size());
      } else {
         helper.runAfterDelay(
            260L,   // #296: 保持原窗口 (520 实测更差: 目标在窗口内离开射程 ⇒ 回退)
            () -> {
               // #297: 目标被**本方塔打死属正常** (证明塔在工作!) ⇒ 不能作为失败条件;
               //   只在"确实没打"时把夹具状态作为诊断信息带出。
               long alive = targets.stream().filter(Zombie::isAlive).count();
               String fixture = " [夹具: 目标存活=" + alive + "/" + targets.size() + "]";
               int bowDmg = bowTower.getAmmo().getItem(0).getDamageValue();
               // #296: 不在此处 discard — 实测回调可能早于塔的有效射击窗口 (diag: zombiesInBox=0 且 fire=0),
               //   提前清场会让塔"无目标可打" ⇒ 改为断言后不清场 (塔测试已各自独立 batch + 开始清塔, 无跨用例风险)
               int crossDmg = crossTower.getAmmo().getItem(0).getDamageValue();
               int goheiDmg = goheiTower.getAmmo().getItem(0).getDamageValue();
               int bowArrows = bowTower.getAmmo().getItem(1).getCount();
               int crossArrows = crossTower.getAmmo().getItem(1).getCount();
               // 僵尸状态 (fail 诊断: 是否被邻塔射死/未入列表)
               StringBuilder zb = new StringBuilder();
               for (var e : helper.getLevel().getEntities().getAll()) {
                  if (e instanceof Zombie zz) zb.append(zz.blockPosition()).append("hp=").append(zz.getHealth()).append(";");
               }
               if (bowDmg <= 0) {
                  helper.fail("弓未掉耐久 (damage=" + bowDmg + ", 箭余=" + bowArrows + ")" + fixture);
               } else if (crossDmg <= 0) {
                  helper.fail("弩未掉耐久 (damage=" + crossDmg + ", 箭余=" + crossArrows + ")" + fixture);
               } else if (goheiDmg > 0) {
                  // v79.66n (用户裁定): 御币(弹幕)攻击已删除 ⇒ 御币塔**必须不开火** (耐久不变) ✓
                  //   (旧断言是"御币必须掉耐久" — 删除功能后反转为"不得掉耐久" = 回归守卫 ✓)
                  helper.fail(
                     "御币塔竟然开火了 (damage="
                        + goheiDmg
                        + ", mode="
                        + goheiTower.weaponMode()
                        + " — 应为 NONE; 御币攻击已删除)"
                  );
               } else {
                  helper.succeed();
               }
            }
         );
      }
   }

   @GameTest(
      template = "game_test",
      timeoutTicks = 700
   , batch = "z_tower5")
   public static void lmaDefenseTowerFarRange(GameTestHelper helper) {
      clearTowerTestBlocks(helper);   // #293: 清残留塔 (防顺序干扰)
      Player owner = registeredOwner(helper);
      // ★ v79.64 修「no LOS 不开火」(诊断实证根因 = **GameTest 框架的屏障围墙**):
      //   `StructureUtils.encaseStructure` 给每个 @GameTest 结构自动裹一圈 BARRIER —
      //   16³ 模板 ⇒ **x=16 / z=16 / y=顶层 都是屏障墙**; 原用例把僵尸放 24 格外 (=墙外),
      //   射线必穿 x=16 屏障 ⇒ hasLineOfSight 恒 false ⇒ 塔不开火 (箭不耗/弓不掉耐久) ✗。
      //   修法 (用户裁定): 靶子移到**围墙内最大距离** — 塔 (3,1,7) → 靶 (13,1,7) = 10 格,
      //   与已验证的 lmaDefenseTower 同距; 本用例真正验证的是"半径 32 能选到远处目标" ✓
      BlockPos towerPos = new BlockPos(3, 1, 7);
      BlockPos towerAbs = helper.absolutePos(towerPos);
      // ★★ 防"野生怪抢目标" (2026-09-19 用户裁定: 下半砖铺地): 本用例原为**假绿/假红双面 flaky** —
      //   实测塔对本测试僵尸 FIRED=0 (箭余恒 20), 却在打**结构内的野生 creeper/spider**;
      //   同时测试僵尸 t≈2 即 `isRemoved()` (1.20.1 `isAlive() = !isRemoved()`, Entity.java:1835) ⇒
      //   "!isAlive ⇒ succeed" 把"被移除"误当成"被击杀" = **白过**; 另一面 (用户实测 hp=14.096) 是僵尸
      //   真中过箭但目标被更近的野生怪抢走 ⇒ 打不死 = **假红**。
      //   ⇒ 修法: **下半砖铺满结构地板**, 从根上让野生怪无法自然生成
      //   (源码实证: `SupportType.FULL.isSupporting` = `Block.isFaceFull(blockSupportShape, UP)`, 而
      //    `SlabBlock.BOTTOM_AABB` 非全高 ⇒ `NaturalSpawner.canSpawnAtBody` 的 isValidSpawn 链拒掉 ✓)。
      for (int fx = 0; fx < 16; fx++) {
         for (int fz = 0; fz < 16; fz++) {
            helper.getLevel().setBlockAndUpdate(
                    helper.absolutePos(new BlockPos(fx, towerPos.getY() - 1, fz)),
                    Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                                    net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
         }
      }
      helper.getLevel().setBlockAndUpdate(towerAbs, ((DefenseGarageKitBlock)LmaBlocks.GARAGE_KIT_DEFENSE.get()).defaultBlockState());
      if (helper.getLevel().getBlockEntity(towerAbs) instanceof DefenseGarageKitBlockEntity tower) {
         tower.setOwnerUuid(owner.getUUID());
         tower.setRadius(32);
         tower.getAmmo().setItem(0, new ItemStack(Items.BOW, 1));
         tower.getAmmo().setItem(1, new ItemStack(Items.ARROW, 20));
         tower.setChanged();
         // 靶子 3×3 石台 (平台 -1 层, 顶面 = 目标层) 保证站定不坠落
         BlockPos zombieAbs = helper.absolutePos(towerPos.offset(10, 0, 0));
         for (int ddx = -1; ddx <= 1; ddx++) {
            for (int ddz = -1; ddz <= 1; ddz++) {
               helper.getLevel().setBlockAndUpdate(zombieAbs.offset(ddx, -1, ddz), Blocks.STONE.defaultBlockState());
            }
         }
         Zombie zombie = (Zombie)EntityType.ZOMBIE.create(helper.getLevel());
         if (zombie == null) {
            helper.fail("僵尸生成失败");
         } else {
            zombie.moveTo((double)zombieAbs.getX() + 0.5, (double)zombieAbs.getY() + 1.0, (double)zombieAbs.getZ() + 0.5, 0.0F, 0.0F);
            zombie.setNoAi(true);
            // 防实体被清 + **防击退漂移** (用户裁定 2026-09-19): 塔偶发"射 3 发就停火"(实测探针:
            //   僵尸 hp 2.29→8.19 停滞、箭余 17 不再减少、位置格仍 (33,-58,7)) — 箭命中会推僵尸,
            //   而塔的选目标是"半径内 + LOS 可见 + 水平最近" ⇒ 靶子一漂就可能丢索敌/丢 LOS。
            //   ① setNoAi(true)  关 AI ② setPersistenceRequired 拒绝 despawn/远距移除
            //   ③ **KNOCKBACK_RESISTANCE = 1.0 (上限)** — 箭击退 0 位移 ④ setInvulnerable(false) 保证可被箭伤
            zombie.setPersistenceRequired();
            zombie.setInvulnerable(false);
//? if 1.20.1 {
            var kb = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
               kb.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                       "lma_test_anchor", 1.0,
                       net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            }
//?} else {
            var kb = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
               kb.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                       net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                               com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.MOD_ID, "test_anchor"),
                       1.0,
                       net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
//?}
            helper.getLevel().addFreshEntity(zombie);
            float hpBefore = zombie.getHealth();
            int arrowsBefore = tower.getAmmo().getItem(1).getCount();
            // ③ 一并关掉本世界的"自然生成"规则 (源码: ServerChunkCache:349 `getBoolean(RULE_DOMOBSPAWNING)`
            //    门控整块 spawnAndTick) — 半砖挡地面 + 规则挡全部, 双保险; 本世界是 gametest 专用世界 ⇒ 无副作用
            ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getGameRules()
                    .getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)
                    .set(false, helper.getLevel().getServer());
            // ④ 清掉结构框内的残留敌对实体 (世界生成期刷出的; 半砖铺地只挡**之后**的生成) ⇒ 保证唯一敌对目标
            //    (helper.getBounds() 是 private ⇒ 用已知塔位 + 16³ 模板算 AABB; 只清 Enemy, 不碰玩家/掉落物)
            BlockPos structMin = helper.absolutePos(new BlockPos(0, 0, 0));
            net.minecraft.world.phys.AABB structBox = new net.minecraft.world.phys.AABB(
                    structMin.getX(), structMin.getY(), structMin.getZ(),
                    structMin.getX() + 16, structMin.getY() + 16, structMin.getZ() + 16);
            for (net.minecraft.world.entity.Entity leftover
                    : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, structBox)) {
               // ⚠ 必须排除**本测试僵尸** (它在上一行已 spawn; 首版把测试僵尸一起清了 ⇒ 塔无目标, 自食其果)
               if (leftover != zombie && leftover instanceof net.minecraft.world.entity.monster.Enemy) {
                  leftover.discard();
               }
            }
            // ④ 断言严格化 (原版 "!isAlive ⇒ succeed" 会把"实体被移除"误判成"被击杀" = 白过 ✗):
            //    必须 **塔真开了火 (箭余减少) 且僵尸真的死了** 才算通过; 600t 窗口保持不变, 但改成**条件轮询**
            //    (成功即结束; 到点未达成才判失败并打印现场) — 语义更强、时序更稳
            // ⑤ 断言严格化 (原版 "!isAlive ⇒ succeed" 会把"实体被移除"误判成"被击杀" = 白过 ✗):
            //    判据 = **塔真开了火 (箭余减少) 且僵尸真的死了**; 窗口 600t 不变, 改为**条件轮询**
            //    (成功即结束; 到点未达成才判失败并打印现场) — 语义更强、时序更稳。
            //    ⚠ 文案用 Supplier 形式不行 (pollUntil 只收字符串) ⇒ 文案里的数值是**调用时快照**, 不是达成时值;
            //      "达成: … 用时=Nt" 那行日志才是真值 (实测达成样例: 箭余 20→18 · hp 归零 · 用时 80t) ✓
            pollUntilSucceedOn(helper, () -> !zombie.isAlive() && tower.getAmmo().getItem(1).getCount() < arrowsBefore,
                    600L, "半径32 塔开火并击杀 10 格僵尸 (箭余 " + arrowsBefore + "→"
                            + tower.getAmmo().getItem(1).getCount() + ", hp=" + zombie.getHealth() + ")",
                    null, new BlockPos[]{zombie.blockPosition()},
                    // v79.65 手办动作契约: 塔开火必须往**手办 NBT 的实体持久数据子标签**写动画请求 —
                    //   ⚠ 必须用 tower.animData() (ForgeData/NeoForgeData 子标签) — 键不在 ExtraData 顶层 ✗
                    //   (2026-09-20 实机崩溃教训: animData 曾自递归爆栈 ⇒ 本用例正是它的回归守卫 ✓)
                    () -> {
                       int seq = tower.animData().getInt(
                               com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.ANIM_SEQ);
                       if (seq <= 0) {
                          helper.fail("手办动画请求未写入 (ANIM_SEQ=" + seq + " — 塔开火未发动作)");
                          return;
                       }
                       // v79.66l 契约: 槽 0 武器必须写进**显示镜像** ⇒ 随更新包下发 ⇒ 客户端无需开 GUI 也能画弓 ✓
                       //   (用户实测: 用客户端容器会"不打开界面就没弓 / 重进又消失" ✗)
                       //   同时校验**更新包不含弹药容器** (含则与菜单同步打架 ⇒ GUI 数字乱跳 ✗)
                       //   ⚠ 平台签名差异: 1.20.1 = getUpdateTag() · 1.21.1 = getUpdateTag(Provider)
//? if 1.20.1 {
                       var updateTag = tower.getUpdateTag();
//?} else {
                       var updateTag = tower.getUpdateTag(helper.getLevel().registryAccess());
//?}
                       if (updateTag.contains("DefenseTowerAmmo")) {
                          helper.fail("更新包仍含弹药容器 (DefenseTowerAmmo) — 会导致 GUI 数字乱跳 ✗");
                          return;
                       }
                       if (!updateTag.contains("DefenseTowerWeaponDisplay")) {
                          helper.fail("更新包缺武器显示镜像 (DefenseTowerWeaponDisplay) — 客户端将画不出弓 ✗");
                          return;
                       }
                       helper.succeed();
                    });
         }
      } else {
         helper.fail("防御塔方块实体未生成");
      }
   }

   /**
    * 防御塔**经菜单写入** → tick → 断言弹药消耗 (v79.63, 补"界面写入"覆盖 — 此前测试都直接 setItem 容器)。
    *
    * <p>模拟玩家把弓/箭拖进便携防御塔 GUI: 走 `DefenseTowerMenu` 的 `Slot.set(...)` (与拖拽同一服务端路径),
    * 然后等塔开火, 断言**弹药被消耗** — 覆盖"菜单槽 ↔ 塔容器同源 + tick 真正读到"这条链。
    */
   @GameTest(template = "game_test", timeoutTicks = 400, batch = "z_tower7")
   public static void lmaDefenseTowerMenuWrite(GameTestHelper helper) {
      clearTowerTestBlocks(helper);   // #293: 清残留塔 (防顺序干扰)
      Player owner = registeredOwner(helper);
      BlockPos towerPos = new BlockPos(3, 1, 7);            // 模板内 (16³) — 见 lmaDefenseTower 的坐标教训
      BlockPos towerAbs = helper.absolutePos(towerPos);
      helper.getLevel().setBlockAndUpdate(towerAbs,
              ((DefenseGarageKitBlock) LmaBlocks.GARAGE_KIT_DEFENSE.get()).defaultBlockState());
      if (!(helper.getLevel().getBlockEntity(towerAbs) instanceof DefenseGarageKitBlockEntity tower)) {
         helper.fail("防御塔方块实体未生成");
         return;
      }
      tower.setOwnerUuid(owner.getUUID());

      // ★ 经**菜单槽**写入 (模拟玩家拖入), 而非直接操作容器
      var menu = new com.github.xiaozhaoz1.littlemaidmoreaction.defense.tower.DefenseTowerMenu(1, owner.getInventory(), tower);
      menu.getSlot(0).set(new ItemStack(Items.BOW, 1));          // 槽0 = 武器
      menu.getSlot(1).set(new ItemStack(Items.ARROW, 10));       // 槽1 = 弹药首格
      if (tower.getAmmo().getItem(0).isEmpty() || tower.getAmmo().getItem(1).getCount() != 10) {
         helper.fail("菜单写入未落到塔容器 (武器=" + tower.getAmmo().getItem(0) + " 弹药=" + tower.getAmmo().getItem(1) + ")");
         return;
      }
      LittleMaidMoreAction.LOGGER.warn("[GAMETEST-TOWER-MENU] 经菜单写入: weapon={} ammo={}",
              tower.getAmmo().getItem(0), tower.getAmmo().getItem(1));

      // ★ 先清场: 同世界其它用例残留的僵尸会抢走塔的"最近敌对目标" → 我的测试变成不可复现
      //   (实测: 未清场时塔去瞄远处的不可见僵尸 → 一箭不发, 断言误判为"菜单↔容器未同源")
      helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
              new net.minecraft.world.phys.AABB(towerAbs).inflate(32)).forEach(net.minecraft.world.entity.Entity::discard);

      // 目标 (模板内: 塔 x=3 → 僵尸 x=13) → 塔才会开火
      Zombie zombie = (Zombie) EntityType.ZOMBIE.create(helper.getLevel());
      if (zombie == null) { helper.fail("僵尸生成失败"); return; }
      zombie.moveTo(helper.absolutePos(towerPos.offset(10, 0, 0)).getCenter());
      // v79.63 修**靶子自己消失** (本轮实测 僵尸数=0 ⇒ 塔没得打 ⇒ 误判"菜单未同源"):
      //   ① 自然消失 ⇒ setPersistenceRequired  ② 白昼燃烧致死/被其它塔打掉 ⇒ setInvulnerable
      //   ③ 乱走离开最近目标位 ⇒ setNoAi   (本用例只断言"弹药被消耗", 不需要靶子可被伤害)
      zombie.setNoAi(true);
      zombie.setPersistenceRequired();
      zombie.setInvulnerable(true);
      helper.getLevel().addFreshEntity(zombie);
      tower.setChanged();

      helper.runAfterDelay(300L, () -> {
         int left = tower.getAmmo().getItem(1).getCount();
         java.util.List<net.minecraft.world.entity.monster.Monster> zs = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                 new net.minecraft.world.phys.AABB(towerAbs).inflate(32));
         LittleMaidMoreAction.LOGGER.warn("[GAMETEST-TOWER-MENU] 结果 弹药={}/10 僵尸数={}", left, zs.size());
         // v79.63: 测试后清场 — 残留僵尸会被别的塔测试的"最近敌对目标"逻辑选中, 造成跨用例干扰
         helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                 new net.minecraft.world.phys.AABB(towerAbs).inflate(32)).forEach(net.minecraft.world.entity.Entity::discard);
         if (left < 10) {
            helper.succeed();          // 弹药减少 = 菜单写入的箭被 tick 消费 ✓
         } else {
            helper.fail("经菜单写入后弹药未被消耗 (left=" + left + ", 僵尸=" + zs.size() + ") — 菜单↔塔容器未同源 或 未开火");
         }
      });
   }

    /**
     * v79.66q 回归 (用户要求 ✓): **拿御币右键手办 ⇒ 手办的"女仆模型数据"必须逐键不变** ✗
     *
     * <p><b>为什么这样测</b>: 实机反馈"右键后手办变成玩家的模型" —— 渲染是**客户端**行为,
     * gametest 看不到画面 ✗; 但**渲染的输入**是服务端数据 ⇒ 在 BE 的 `ExtraData`(女仆 NBT:
     * `id`/`ModelId`/`YsmModelId`/`IsYsmModel`/`Owner`/`CustomName`) 上逐键断言 ✓ ——
     * 这正是"变模型"能被自动化守住的那一半 ✓。
     *
     * <p><b>复刻真实调用顺序</b> (MC 右键: 先物品 `useOn` 再方块 `use`):
     * ① TLM 御币 `ItemHakureiGohei.useOn` (祭坛多方块判定, 非祭坛 ⇒ PASS ✓)
     * ② 我方 `DefenseGarageKitBlock.use` (1.20.1) / `useItemOn` (1.21.1) — 只该**认主 + 开 GUI** ✓
     * <p>反例保护: 御币在手时 `weaponMode` 必须是 `NONE` (弹幕攻击已删 ✓) ⇒ 不会被当武器 ✓。
     *
     * <p>数据实测 (存档只读解析): 实机手办数据本身正确 ⇒ 本用例守住"右键不碰模型数据"边界 ✓。
     */
    @GameTest(template = "game_test", timeoutTicks = 200, batch = "z_gohei")
    public static void lmaTowerGoheiRightClickKeepsStatue(GameTestHelper helper) {
        BlockPos towerAbs = helper.absolutePos(new BlockPos(5, 1, 5));
        helper.getLevel().setBlockAndUpdate(towerAbs,
                ((com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlock)
                        LmaBlocks.GARAGE_KIT_DEFENSE.get()).defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(towerAbs)
                instanceof com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlockEntity tower)) {
            helper.fail("塔 BE 未创建");
            return;
        }
        // 写入"女仆手办数据" — 与实机存档同形: YSM 模型 · 无 Owner · 无 CustomName ✓
        CompoundTag maid = new CompoundTag();
        maid.putString("id", "touhou_little_maid:maid");
        maid.putString("YsmModelId", "wine_fox/22_elf");
        maid.putBoolean("IsYsmModel", true);
        tower.setData(net.minecraft.core.Direction.SOUTH, maid);
        CompoundTag before = tower.getExtraData().copy();
        java.util.UUID ownerBefore = tower.getOwnerUuid();

        // mock 玩家 + 手持御币 (双平台 API 不同 — 与 token 用例同款 stonecutter ✓)
//? if 1.20.1 {
        net.minecraft.world.entity.player.Player player = helper.makeMockSurvivalPlayer();
//?} else {
        net.minecraft.world.entity.player.Player player =
                helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
//?}
        ItemStack gohei = new ItemStack(
                com.github.tartaricacid.touhoulittlemaid.init.InitItems.HAKUREI_GOHEI.get(), 1);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gohei);
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(towerAbs), net.minecraft.core.Direction.UP, towerAbs, false);

        // ★ v79.66r 补: 之前**漏测了"使用/发射"这条路** ✗ —— 弓没箭不发射 ✓ 而御币
        //   `getAllSupportedProjectiles()=alwaysTrue` **永远会发射** ✓ ⇒ 正解释"空手/弓不变、御币变" ✓
        var useResult = gohei.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        assertStatueModelUnchanged(helper, tower, before, "①御币 use 蓄力 (result=" + useResult.getResult() + ")");
        gohei.releaseUsing(helper.getLevel(), player, 0);   // 松手 ⇒ 发射 (danmaku)
        assertStatueModelUnchanged(helper, tower, before, "①御币 releaseUsing 发射");

        // ② 物品侧-对方块 (TLM 御币: 祭坛判定 ⇒ 非祭坛 PASS ✓)
        gohei.useOn(new net.minecraft.world.item.context.UseOnContext(
                player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        assertStatueModelUnchanged(helper, tower, before, "②御币 useOn 祭坛判定");
        // ③ 方块侧 (我方手办右键: 只应认主 + 开 GUI ✓)
        var block = (com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlock)
                tower.getBlockState().getBlock();
//? if 1.20.1 {
        block.use(tower.getBlockState(), helper.getLevel(), towerAbs, player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
//?} else {
        block.useItemOn(player.getMainHandItem(), tower.getBlockState(), helper.getLevel(), towerAbs,
                player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
//?}

        assertStatueModelUnchanged(helper, tower, before, "③手办方块 use/useItemOn 认主+GUI");
        // 断言 ②: 御币不得被当武器 (弹幕攻击已删 ⇒ NONE ✓)
        tower.getAmmo().setItem(0, new ItemStack(
                com.github.tartaricacid.touhoulittlemaid.init.InitItems.HAKUREI_GOHEI.get(), 1));
        com.github.xiaozhaoz1.littlemaidmoreaction.defense.tower.DefenseTowerLogic.ShootMode mode =
                com.github.xiaozhaoz1.littlemaidmoreaction.defense.tower.DefenseTowerFire.weaponMode(tower.getAmmo());
        if (mode != com.github.xiaozhaoz1.littlemaidmoreaction.defense.tower.DefenseTowerLogic.ShootMode.NONE) {
            helper.fail("御币被当成武器: mode=" + mode + " (应为 NONE)");
            return;
        }
        LittleMaidMoreAction.LOGGER.warn(
                "[GAMETEST-GOHEI-STATUE] 御币右键手办: 模型数据逐键不变 ✓ weaponMode={} ✓ ownerBefore={} ownerAfter={}",
                mode, ownerBefore, tower.getOwnerUuid());
        helper.succeed();
    }
}
