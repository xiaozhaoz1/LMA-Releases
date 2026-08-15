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
     * 被动 tick 预算 — 超预算时环形轮转 (每 tick 恰 budget 个被动管线被驱动)。
     * 注册 2 个计数被动 (showInBar=false), budget=1, 手动驱动 tickPassiveFor 断言轮转。
     */
    @GameTest(template = "game_test")
    public static void lmaPassiveBudget(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        if (maid == null) return;

        int[] tickA = {0};
        int[] tickB = {0};
        var pa = new com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline() {
            @Override public String taskType() { return "__passive_a"; }
            @Override public void tick(net.minecraft.server.level.ServerLevel w, EntityMaid m) { tickA[0]++; }
        };
        var pb = new com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline() {
            @Override public String taskType() { return "__passive_b"; }
            @Override public void tick(net.minecraft.server.level.ServerLevel w, EntityMaid m) { tickB[0]++; }
        };
        com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.registerPassive("__passive_a", pa);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.registerPassive("__passive_b", pb);

        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submitPassive(maid, "__passive_a");
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submitPassive(maid, "__passive_b");

        // TST-5 修复: 预算修改 + 断言包 try/finally — helper.fail 抛异常 (GameTestHelperException)
        // 时 finally 仍执行恢复, 消除"fail 提前 return 跳过恢复"的跨测试污染; 恢复值从
        // 配置默认值读取 (getDefault) 而非硬编码 2 (配置默认变化时测试不失效)
        var budget = com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig.PASSIVE_TICK_BUDGET;
        try {
            // budget=1 → 每 tick 恰 1 个被驱动, 环形轮转覆盖两个
            budget.set(1);
            var passives = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.passiveTasksList();
            net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) maid.level();
            long base = sl.getGameTime();
            int prevTotal = 0;
            for (int i = 0; i < 4; i++) {
                com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager
                        .tickPassiveFor(sl, maid, passives, base + i);
                int total = tickA[0] + tickB[0];
                if (total - prevTotal != 1) {
                    helper.fail("budget=1 时每 tick 应恰驱动 1 个被动, 实际 " + (total - prevTotal));
                    return;
                }
                prevTotal = total;
            }
            if (tickA[0] == 0 || tickB[0] == 0) {
                helper.fail("环形轮转应覆盖两个被动 (a=" + tickA[0] + ", b=" + tickB[0] + ")");
                return;
            }
        } finally {
            // 恢复默认预算 (防跨测试影响) — fail 抛异常时也执行
            budget.set(budget.getDefault());
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
     * 状态机分支由 StructureSenseStateTest 28 例覆盖 (gametest 测试层无自然结构,
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
        StructureSense.detect(level, player);
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
}
