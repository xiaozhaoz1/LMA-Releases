package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationPool;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationContainerService;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * v79.62 挖空置域管线 (主动任务) — 女仆把大区域从基岩上到世界高度全部挖空.
 *
 * <p><b>设计 (用户裁定)</b>:
 * <ul>
 *   <li>区域 = 起点为中心 size×size 区块 (ClothConfig 全局默认 32, 单女仆 TLM 栏可覆盖),
 *       垂直从基岩上 (y=1) 到 y=320 全挖</li>
 *   <li>逐层下降: 每层遍历 x/z 全挖 → 挖完一层 y-1 → 传送下一层 (无法到达直接传送)</li>
 *   <li>工具: 输入箱放工具, 女仆取工具换手 (ToolJudge 按方块选镐/锹/斧)</li>
 *   <li>方块: 挖出进背包 → 转存输出箱; 输出箱满 → 1×1 4方向搜索扩展容器</li>
 *   <li>液体: 放方块填掉再挖</li>
 *   <li>无工具 + 输入箱空 / 输出箱满 + 无扩展 → 回玩家旁提醒</li>
 *   <li>区块: 任务期间 setChunkForced 强制加载 (量大 + 多女仆协作)</li>
 * </ul>
 *
 * <p>PD 键: start(起点) / size(区块数) / input(输入箱) / output(输出箱) / y(当前层) /
 * x/z(当前列) / outIdx/inIdx(容器搜索方向).
 */
public final class VoidExcavationPipeline implements TaskPipeline, TaskConfigurable {

    /** 箱满/无工具等待冷却 (tick) — 提醒玩家后暂停, 防每 tick 空转 */
    private static final int WAIT_TICKS = 400;
    /** 每 tick 列处理预算 — 空气快速跳过但封顶, 防一 tick 扫整层 (32 区块高空 = 26万格) */
    private static final int COLUMNS_PER_TICK = 256;

    /** 单女仆区块数配置键 (pipelineConfig lma_cfg_void_excavation "size"; 空=全局 VOID_DEFAULT_CHUNKS) */
    public static final String KEY_SIZE = "size";

    /** 女仆卸载/死亡/收入魂符 → 释放认领区块 (v79.62.2 用户裁定: 认领池对女仆生命周期反应).
     *  否则收起/死亡后 pool 里区块仍是"进行中", 其他女仆/重启后死等 (cursor=0,0,0 + claimWait 循环). */
    // v79.62.1 单女仆掉落/寻路配置键 (用户裁定): 销毁名单, 关闭寻路开关
    /** 销毁名单 (ListTag 物品 id; 命中 → 挖出即销毁消失, 不进背包不落地) */
    public static final String KEY_DESTROY_LIST = "destroy_list";
    /** 关闭寻路开关 (开启后不 BFS 寻路, 传送站区块中间直接挖) */
    public static final String KEY_NO_PATHFIND = "no_pathfind";
    /** v79.62.2 自定义最低高度 (cfg "min_y"; 空=自动检测基岩层) — 设定了挖到该层停 */
    public static final String KEY_MIN_Y = "min_y";



    @Override public String taskType() { return "void_excavation"; }


    /** v79.62.2 修复 (用户裁定: 不能关闭 TLM 导航 — 女仆移动靠 TLM, LMA 只给坐标):
     *  让 TLM searchForDestination 能搜到目标 → 完整 BFS 寻路 (绕障/水里) 驱动女仆移动.
     *  目标 = 当前认领区块 (curCX/curCZ 16×16) 内的方块 — 限制在认领区块, 不会"找错方块".
     *  (v79.62.1 曾恒 false — 一刀切关掉 TLM 导航 → 被堵/水里走不动, 只能靠 navigateTo 直走卡死) */
    @Override public boolean isTargetBlock(ServerLevel world, BlockPos pos, BlockState state, EntityMaid maid) {
        CompoundTag pd = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, "void_excavation");
        if (!pd.contains("curCX")) return false;   // 未认领 → 无目标
        int cx = pd.getInt("curCX"), cz = pd.getInt("curCZ");
        int px = pos.getX() >> 4, pz = pos.getZ() >> 4;
        return px == cx && pz == cz;   // 当前认领区块内 → 可作 TLM 导航目标
    }
    @Override @javax.annotation.Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        // v79.62.1 用户裁定 (自绘框, 空置域专名): 用专用 VoidExcavationConfigScreen —
        // 只要销毁名单 (自绘框) 不要白名单; 名单存 pipelineConfig "destroy_list" (tryDig 读取键).
        // 关寻路 per-maid toggle 优先, 回退全局 Cloth (ActiveTaskConfig VOID_NO_PATHFIND).
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory
                .voidExcavationConfig(maid);
    }
    @Override public boolean isLongRunning() { return true; }
    @Override public List<TaskStep> steps() {
    // ⚠ 改相位/状态时必须同步本步骤声明 — steps 是**用户可见的粗粒度语义**, 与内部状态枚举**不同层**;
    //    二者无自动校验 (6 态→4 步这类多对一是正常的), 详见错题 #291。
            return List.of(new TaskStep("dig", "挖空置域", StepType.CRAFT, List.of())); }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        // v79.62.1 进度持久化后 start 在 cfg — 读持久
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        if (!cfg.contains(TaskKeys.CFG_START)) return PipelineResult.failed("未标记起始点");
        return PipelineResult.ok("");
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        // v79.62.1 进度持久化: cfg (lma_cfg_void_excavation, 持久 NBT) 存 start/size/y/x/z/箱;
        // pd (lma_pl, 内存态) 存瞬态 (wait/nav/digTicks). 挖空超长任务重启不丢进度.
        // [VOID-TICK] 诊断: pipeline tick 是否在跑 + cfg start 是否存在 — 定位期临时 WARN
        if (world.getGameTime() % 200 == 0) {
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[VOID-TICK] maid={} pos={} cfgHasStart={} state={}",
                maid.getStringUUID().substring(0, 8), maid.blockPosition().toShortString(),
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                    .cfg(maid, "void_excavation").contains(TaskKeys.CFG_START),
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getState(maid));
        }
        // [DEBUG-VOID] 状态诊断 (3×3+4女仆卡死定位) — debug 级; 运行时键 curCX/curCZ/y/x/z 读 PD (已迁瞬态, 读 cfg 恒 0)
        if (world.getGameTime() % 40 == 0) {
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.debug(
                "[DEBUG-VOID] maid={} pos={} cfgStart={} curCX={} curCZ={} y={} x={} z={} wait={}",
                maid.getStringUUID().substring(0, 8), maid.blockPosition().toShortString(),
                com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs
                    .readBlockPos(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                        .cfg(maid, "void_excavation"), TaskKeys.CFG_START),
                pipelineData(maid).getInt("curCX"),
                pipelineData(maid).getInt("curCZ"),
                pipelineData(maid).getInt("y"),
                pipelineData(maid).getInt("x"),
                pipelineData(maid).getInt("z"),
                pipelineData(maid).getInt("wait"));
        }
        // [DEBUG-VOID] 耗时诊断 (定位卡顿 tick, 定位后移除)
        long t0 = System.nanoTime();
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        CompoundTag pd = pipelineData(maid);
        if (!cfg.contains(TaskKeys.CFG_START)) {
            TaskDispatcher.cancel(maid);
            return;
        }
        // v79.62.1 用户裁定: 任务期间取消摔落伤害 (挖空置域频繁下落/传送, 不用算安全区域).
        // 每 tick 清 fallDistance — 落地前伤害结算时 fallDistance 已清零 → 无伤害.
        // (fallDistance 是伤害结算依据, 每 tick 清即永不累积出伤害)
        if (maid.fallDistance > 0) maid.fallDistance = 0;
        // [VOID-MOVE] 诊断 (女仆不会动定位): tick 是否跑 + 游标 + 女仆位置 + near — 定位期临时 WARN
        if (world.getGameTime() % 200 == 0) {
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[VOID-MOVE] tick maid={} pos={} cursor=({},{},{}) near={} wait={} claimWait={}",
                maid.getStringUUID().substring(0, 8), maid.blockPosition().toShortString(),
                pd.getInt("x"), pd.getInt("y"), pd.getInt("z"),
                pd.contains("x") && VoidExcavationService.near(maid, new BlockPos(pd.getInt("x"), pd.getInt("y"), pd.getInt("z"))),
                pd.getInt("wait"), pd.getInt("claimWait"));
        }
        BlockPos start = NbtCodecs.readBlockPos(cfg, TaskKeys.CFG_START);
        int size = readSize(maid, cfg);

        // v79.62.2 区块工作制接管: HOME 模式工作区跟随 (setWorkPos/restrictTo) 已上移到
        // LmaFlowCoordinationBehavior 的 ChunkWorkArea (含内置区块缓存), 管线不再重复做.

        // 区域边界 (起点区块为中心 size 区块, 整区块对齐)
        // v79.62.1 修: 原 maxCX=scx+half-1 在 size=1 时 maxCX<minCX 空区域 → 立即完成.
        // 正确: minCX=scx-size/2, maxCX=minCX+size-1 → 正好 size 个区块 (size=1 → 1 区块)
        int scx = start.getX() >> 4, scz = start.getZ() >> 4;
        int minCX = scx - size / 2, maxCX = minCX + size - 1;
        int minCZ = scz - size / 2, maxCZ = minCZ + size - 1;
        // ── v79.62.1 多女仆动态领取 (用户裁定: 先到先挖, 挖完领下一块, 防同区块抢挖) ──
        // 女仆认领一个区块 (PD curCX/curCZ), 挖掘范围限制在该区块内; 区块挖到底 → 释放领下一个.
        // v79.62.1 运行时状态全放 PD (内存), cfg 只存标记点 (start/size/input/output) — 用户裁定.
        int curCX = pd.getInt("curCX"), curCZ = pd.getInt("curCZ");
        if (pd.getInt("claimWait") > 0) {
            pd.putInt("claimWait", pd.getInt("claimWait") - 1);   // 认领等待冷却, 防每 tick 空转 + Brain 高频重搜
            return;
        }
        // v79.62.2 单女仆自愈: 有 curCX 但 CLAIM_OWNER 无自己 (被超时释放/onMaidUnload 漏) →
        // 释放 curCX 重新认领 (放挖掘循环前, 不在已释放区块上浪费 tick)
        if (pd.contains("curCX") && !VoidExcavationPool.hasClaim(maid)) {
            // 双检 + 释放为未挖 — 原子性由池内 releaseStaleClaim 保证 (v79.63 锁封装进池)
            int hcx = pd.getInt("curCX"), hcz = pd.getInt("curCZ");
            if (VoidExcavationPool.releaseStaleClaim(maid, world, start, size, hcx, hcz)) {
                pd.remove("curCX");
                pd.remove("curCZ");
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.debug(
                    "[VOID-MOVE] SELF-HEAL release maid={} block=({},{})",
                    maid.getStringUUID().substring(0, 8), hcx, hcz);
            }
        }
        if (!pd.contains("curCX")) {
            VoidExcavationPool.BlockClaim claimed = VoidExcavationPool.poolClaim(maid, world, start, size, minCX, maxCX, minCZ, maxCZ);
            if (claimed == null) {
                // 全部区块挖完 → 完成 (区块预载由 behavior stop() 释放; 清进度)
                MaidChatBubbleApi.showComplete(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.void.done"));
                cfg.remove("start");
                TaskDispatcher.cancel(maid);
                return;
            }
            if (!claimed.claimed) {
                // v79.62.1 修崩溃: 有区块在挖但无未挖 → 等待 (别人挖完再领), 本 tick 不挖.
                // v79.6x 加冷却: 等 10t 再重试认领 (防每 tick 空转 + Brain 高频重搜)
                pd.putInt("claimWait", 10);
                return;
            }
            curCX = claimed.cx;
            curCZ = claimed.cz;
            pd.putInt("curCX", curCX);
            pd.putInt("curCZ", curCZ);
            // 新区块: y 从区块内地表 (标记层同高, 先挖标记层) 开始
            pd.putInt("y", start.getY());
            pd.putInt("x", curCX * 16);
            pd.putInt("z", curCZ * 16);
            // v79.62.2 修「无导航没自动传送」: 认领新区块清 navCd/navTimeout —
            // 旧冷却残留会让无导航模式等 100t 才传送 (用户实证: 手动放进区块中心才开始挖)
            pd.putInt("navCd", 0);
            pd.putInt("navTimeout", 0);
        }
        // v79.62.2 方案 A (用户裁定): pipeline 自管 home/workPos/restrict — 跟随认领区块中心
        // ⚠ 2026-09-18 回滚注记 (错题 #357): 曾试「劫持前把 SchedulePos 三点原点写进 cfg (持久) + 任务结束/
        //   卸载/重进还原」以修「空置域已关闭仍被拉回」— 根因查清 (adaptToChunk 改的 SchedulePos **会落盘**,
        //   而卸载路径只释放认领不还原 ⇒ 三点永久留坑) 但该修法实测引入 neo 回归 (`lmavoidexcavationmultimaid`
        //   dug=0/4, forge 绿) ⇒ **整体回滚**; 后续修法需专项定位 SchedulePos 写入时机与 SchedulePos.tick 的
        //   restrict 抖动 (避免同类回归), 详见错题 #357。
        // v79.62.2 区块工作制 (ChunkWorkArea 方法, pipeline 内部调用):
        // 认领区块中心 = (curCX*16+8, y, curCZ*16+8), 数据在管道内直接交互.
        com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.ChunkWorkArea.State cw = VoidExcavationPool.chunkWorkState(maid);
        // v79.62.2 修拉回 (TLM 源码实证): SchedulePos.tick 每 40t, 女仆超出 restrict 范围
        // (距 workPos/12 >(12+4)²) → 设 WALK_TARGET 走回 restrict 中心. restrict 中心 = workPos =
        // 区块中心. anchorY 必须用游标 y (挖深同步) — 固定 start.getY() 时女仆挖深 15 格 3D 超 12
        // → 每 40t 被导航回地表区块中心 = 用户实证「走两三格被拉回」. 游标 y 同步 → 3D 只差水平 ≤11.3 永不超.
        com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.ChunkWorkArea
                .adaptToChunk(world, maid, cw, curCX, curCZ, pd.getInt("y"));
        // v79.62.2 用户裁定: 不预传区块中间 (通用 y 层找落点会找错层 — 地表已挖空时
        // findStandPos 落到坑底, 与游标 y 脱节 → "女仆没传到要挖的方块上").
        // 让循环内分支处理: 开导航 → near/else (100t 传 target.above()); 关导航 → 传区块中间开挖.
        // 当前区块边界 (挖掘范围限制在本区块)
        int minX = curCX * 16, maxX = curCX * 16 + 15;
        int minZ = curCZ * 16, maxZ = curCZ * 16 + 15;
        // 世界高度边界 (v79.62.1 修: 高版本基岩层 y 负 — 动态取, 不硬编码 1/320)
        int maxY = VoidExcavationService.maxY(world);   // 最高可挖层 (getMaxBuildHeight-1)
        // v79.62.2 自定义最低高度: cfg min_y 设定了挖到该层停 (含); 空=自动检测基岩层
        int minY = cfg.contains(KEY_MIN_Y) ? cfg.getInt(KEY_MIN_Y) : VoidExcavationService.minY(world);
        // ★ v79.66.1 自愈 (用户实测「一直提示未标记起始点」根因): min_y 若**不低于**标记层 start.y ⇒
        //   挖掘循环 `while (y > minY)` 一次都不进 (如 min_y=0 而 start.y=-60 ✗), 随后循环后的
        //   `if (y <= minY)` **恒真** ⇒ 首 tick 就把区块标"已挖完"(poolMark 2) ⇒ 下 tick 认领池判"区域完成"
        //   ⇒ **删掉 start** + cancel ⇒ 之后每次启动 validate 都失败 (表现为"未设起始点"反复出现) ✗✗
        //   (旧 GUI 保存 bug 曾把 min_y 存成 0, 该坏值留在 NBT 里; 新保存已由 sendSetInt 修 ✓)
        //   现口径: min_y 必须 **< start.y** 才有意义 (等于/高于 = 无可挖层); 无效则**移除本键自愈**为
        //   "自动检测基岩层" ⇒ 女仆可正常开挖 ✓ (保留 start, 不再误删用户的标记 ✓)
        if (cfg.contains(KEY_MIN_Y) && minY >= start.getY()) {
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[VOID] min_y={} 不低于标记层 start.y={} ⇒ 无可挖层, 已自愈为自动检测基岩层 maid={}",
                minY, start.getY(), maid.getStringUUID().substring(0, 8));
            cfg.remove(KEY_MIN_Y);
            minY = VoidExcavationService.minY(world);
        }
        // v79.62.2 强制 home (用户裁定: home = 工作范围, 不开 home 女仆跟随玩家更不行):
        // 挖空是远距离任务 — 强制 home 模式 (防 follow 拉回玩家旁). wasHome 记录, onCleanup 恢复.
        // ★ v79.69.1 修「任务结束后 home 不还原」(用户实测: 挖完换任务后, 女仆仍被 TLM restrict 传送回挖空区 ✗):
        //   原写法 `if (!isHome) {记 false; 开} else {记 true}` **每 tick 都跑** ⇒ 第 2 tick 起她已是 home
        //   ⇒ 走 else 把 false **覆盖成 true** ⇒ onCleanup 的 `!wasHome` 判定失败 ⇒ home 永不关 ✗
        //   ⇒ 改为「**只记一次**」(与 `DamFillPipeline.ensureHomeMode` 同款 ✓; 任务期间仍持续强制 home ✓)
        if (!pd.contains("wasHome")) {
            pd.putBoolean("wasHome", maid.isHomeModeEnable());
        }
        if (!maid.isHomeModeEnable()) {
            maid.setHomeModeEnable(true);
        }
        // v79.62.2 区块工作制接管: 3×3 梯级预加载已并入 behavior ChunkWorkArea 内置区块缓存.

        // 箱满/无工具等待冷却 — 玩家补给期间跳过全流程 (防每 tick 空转)
        int wait = pd.getInt("wait");
        if (wait > 0) {
            pd.putInt("wait", wait - 1);
            return;
        }

        // 当前层 (用户裁定: 挖空置域 = 从标记层 start.getY() 往下挖).
        // v79.6x 越界复位: 旧版「补挖高处」会把 y 写到标记层以上并随女仆 NBT 持久化,
        // 重进后游标悬空在标记层上方 (实测 y=-54/-55 > -61) → 拉回标记层重挖.
        int y = pd.contains("y") ? pd.getInt("y") : start.getY();
        if (start != null && y > start.getY()) y = start.getY();
        // v79.62.2 用户裁定: start.y = 女仆可挖的最高 y (认领直接设定高度) — 不补扫高处,
        // 不算量, 天然支持挖地底 (只挖 start 以下的区域). 挖掘只从标记层往下, 不拉回.
        int x = pd.getInt("x");
        int z = pd.getInt("z");

        // 容器检查 (工具/输出) — 缺则回玩家提醒 (箱位置读持久 cfg)
        BlockPos input = NbtCodecs.readBlockPos(cfg, "input");
        BlockPos output = NbtCodecs.readBlockPos(cfg, "output");
        // v79.62.1 修问题4: 输入/输出箱区块也强制加载 (原只在女仆 3×3 预加载, 女仆挖远后
        // 箱区块未加载 → getHandler hasChunk 守卫 null → 存不进/取不出 → 提示背包满).
        // 箱区块异步 forceChunk (ChunkBuilder 后台生成, 不阻塞主线程)
        if (input != null) {
            com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationChunkManager
                    .forceChunk(world, input.getX() >> 4, input.getZ() >> 4, true);
        }
        if (output != null) {
            com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationChunkManager
                    .forceChunk(world, output.getX() >> 4, output.getZ() >> 4, true);
        }
        boolean contOk = handleContainers(world, maid, pd, cfg, input, output);
        if (world.getGameTime() % 200 == 0) {
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[VOID-DBG] containers ok={} input={} output={} wait={} y={} x={} z={}",
                contOk, input, output, pd.getInt("wait"), pd.getInt("y"), pd.getInt("x"), pd.getInt("z"));
        }
        if (!contOk) {
            return;   // 已提醒 (无工具/箱满) — 等玩家补给
        }

        // 逐层挖掘 — v79.62.1 导航优先: 先走到目标列, 到达才挖 (走不到 100t 兜底传送)
        // 活跃心跳 — 每 tick 一次 (防 GMPM 看门狗误杀长任务)
        advance(world, maid);
        // [DEBUG-VOID] 挖掘入口诊断 (3×3+5女仆卡死, 定位后移除)
        if (world.getGameTime() % 40 == 0) {
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.debug(
                "[DEBUG-VOID] dig-entry maid={} x={} y={} z={} curCX={} curCZ={} input={} output={}",
                maid.getStringUUID().substring(0, 8), x, y, z, pd.getInt("curCX"), pd.getInt("curCZ"),
                input, output);
        }
        // 列预算 — 空气快速跳过但封顶 (防一 tick 扫整层卡服)
        if (world.getGameTime() % 200 == 0) {
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[VOID-DBG] enter-loop y={} minY={} x={} z={} curCX={} curCZ={} nearTarget={}",
                y, minY, x, z, pd.getInt("curCX"), pd.getInt("curCZ"),
                VoidExcavationService.near(maid, new BlockPos(x, y, z)));
        }
        int columns = 0;
        // v79.62.1 用户裁定: 最下层基岩排除 (y > minY — 基岩层整层不挖, 挖了女仆掉虚空)
        while (y > minY && columns < COLUMNS_PER_TICK) {
            columns++;
            BlockPos target = new BlockPos(x, y, z);
            // v79.62.2 修「女仆一直走不挖」: 空气格检查提前到 near 判定之前 —
            // 女仆在坑里(y低) 游标扫到已挖空的地表空气格(y高) 时, 若先走 near 导航分支会
            // 反复导航到空气格(走不到) 卡死; 空气格应直接跳过推进 (不导航).
            if (world.hasChunk(x >> 4, z >> 4)) {
                BlockState airCheck = world.getBlockState(target);
                // v79.62.2 液体提前处理 (修: 原液体格不是空气 → 不走空气跳过 → near=false 时
                // else 导航分支 return, 走不到 ④ 液体特判 → 液体处理失效. 提前空气化+推进)
                if (!airCheck.getFluidState().isEmpty()) {
                    // v79.62.2 液体相邻扫描: 清当前格 + 相邻格液体 (水平 4 向 + 上下) —
                    // 防相邻区块/相邻格液体流进已挖空区 (用户裁定: 遇液体相邻扫描直接处理)
                    clearLiquidAt(world, maid, target);
                    clearAdjacentLiquids(world, maid, target, 8);
                    x++;
                    if (x > maxX) { x = minX; z++; }
                    if (z > maxZ) { z = minZ; y--; }
                    pd.putInt("y", y); pd.putInt("x", x); pd.putInt("z", z);
                    continue;
                }
                if (airCheck.isAir()) {
                    // 空气格跳过 (v79.62.2 修发呆: 去掉 16 格限距 — 空气格无方块可挖, 跳过
                    // 不需要女仆靠近; 原限距 return 不推进 → 新层首格空气且女仆在区块另一头时
                    // 永远卡首格 = 用户实证「新层首格空女仆发呆」)
                    x++;
                    if (x > maxX) { x = minX; z++; }
                    if (z > maxZ) { z = minZ; y--; }
                    pd.putInt("y", y); pd.putInt("x", x); pd.putInt("z", z);
                    continue;
                }
            }
            // ③ 站立/挖掘距离 (v79.62.1 用户裁定: 4 格内都能挖 (TLM destroyBlock 无距离限制),
            // 优先旁边 8 格有支撑的安全位, 不站正上方 (挖了掉下去))
            // v79.62.1 层差大 (探层/挖到下一层) → 传送到目标层站上层挖下层 (自然逐层下降)
            // 修 4×4 卡死: 原安全位 null 无限跳过 → 女仆原地不动;
            // 层差大时站 target.above() (上层) 挖 target (下层), 挖空后女仆落下一层继续 (逐层下降语义)
            // v79.6x 修漏挖: 导航/到达判定提到 hasChunk 之前 — 导航只需 target 坐标不需 blockState,
            // 未加载区块也能导航 (女仆走过去, 3×3 预加载跟随把区块带进来); 原「区块未加载就跳列」整列漏挖.
            boolean noPathfind = cfg.contains(KEY_NO_PATHFIND)
                    ? cfg.getBoolean(KEY_NO_PATHFIND)
                    : com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.VOID_NO_PATHFIND.get();
            if (noPathfind) {
                // v79.62.1 关闭寻路模式 (用户裁定): 不区分 near — 不在区块中间直接传送到
                // 要挖区块中间 (curCX*16+8, curCZ*16+8), 站定后 cursor 循环挖 (TLM destroyBlock
                // 无距离限制, 区块内任意格都能挖). 避免 BFS 寻路卡顿 (spark: 寻路 57%).
                // v79.62.2 修空转: near 判定必须用「区块中间」— 原判 target(区块角) 在女仆
                // 传送落位中心后距角 8 格永不 near → teleport 空转死循环 (用户日志实证: 女仆
                // 站 -3176,62,2216=区块中间不动, 无任何 VOID-NAV).
                BlockPos centerPos = new BlockPos(curCX * 16 + 8, y, curCZ * 16 + 8);
                if (centerPos.getY() > maxY) centerPos = new BlockPos(centerPos.getX(), maxY, centerPos.getZ());
                if (!VoidExcavationService.near(maid, centerPos)) {
                    // ★ v79.65.1 「第一层检查」(用户实测: 第一层的**头位是未开挖地表** ⇒ 直接传送 = 头埋
                    //   实心方块 ⇒ 窒息持续掉血 ✗; 后续层头位已在上层挖空 ⇒ 只有第一层中招 ✓):
                    //   传送前先挖净落点 2 格 (头位 → 本体), 挖穿后才传送 ✓
                    if (ensureLandingClear(world, maid, pd, centerPos)) return;   // 本 tick 有动作, 下 tick 复查
                    // v79.62.2 关导航: 传区块中间 (站定不动开挖) — 100t 传送冷却 (防每 tick 重传)
                    int navCd = pd.getInt("navCd");
                    if (navCd > 0) {
                        pd.putInt("navCd", navCd - 1);
                        return;   // 冷却中, 等站稳
                    }
                    VoidExcavationService.teleportToVoid(world, maid, centerPos);
                    // 清 TLM 走位/导航记忆 — 关闭寻路 = 女仆不动 (只站区块中间挖)
                    maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                    maid.getBrain().eraseMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
                    maid.getNavigation().stop();
                    pd.putInt("navCd", 100);   // 传送冷却 100t
                    return;   // 传送后下 tick 从区块中间挖
                }
                // 已在区块中间 → 直接挖 (不导航, destroyBlock 无距离限制)
                pd.putInt("navTimeout", 0);
                pd.putInt("navCd", 0);
            } else if (VoidExcavationService.near(maid, target)) {
                // 4 格内 → 挖 (正常移动由 TLM 负责: isTargetBlock→searchForDestination→MaidMoveToBlockTask 走)
                pd.putInt("navTimeout", 0);
                pd.putInt("navCd", 0);
            } else {
                // v79.62.2 根治 (用户裁定: LMA 不自写寻路, 只给坐标, 移动靠 TLM):
                // searchForDestination 的 BFS 预筛选不可靠 (checkPathReach 被堵/水里失败)
                // → 不依赖它. pipeline 直接给 TLM 坐标: 每 tick 设 TARGET_POS + WALK_TARGET,
                // TLM MoveToTargetSink (CORE 活动) 读 WALK_TARGET → maid.getNavigation().moveTo() 走.
                // 100t 没到 → 传送兜底 (被堵/水里走不到).
                BlockPos navTarget = target.above();
                if (navTarget.getY() > maxY) navTarget = new BlockPos(navTarget.getX(), maxY, navTarget.getZ());
                // 直接设 TARGET_POS (让 behavior tick 不因 mem.isEmpty 提前返回) + WALK_TARGET (MoveToTargetSink 走)
                maid.getBrain().setMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get(),
                        new net.minecraft.world.entity.ai.behavior.BlockPosTracker(navTarget));
                net.minecraft.world.entity.ai.behavior.BehaviorUtils
                        .setWalkAndLookTargetMemories(maid, navTarget, 1.0F, 0);
                int navTimeout = pd.getInt("navTimeout") + 1;
                // v79.62.2 用户裁定改回: 100t 没到 → 传送到目标上 (navTarget=target.above(), 站上层挖下层)
                if (navTimeout > 100) {
                    // ★ v79.65.1 「第一层检查」同款: 导航路径传送到 navTarget (= target.above()), 第一层时
                    //   该格是**未开挖地表** ⇒ 先挖净落点再传, 防窒息 ✓
                    if (ensureLandingClear(world, maid, pd, navTarget)) {
                        pd.putInt("navTimeout", navTimeout);
                        return;   // 落点未净空 ⇒ 本 tick 不传送, 下 tick 复查
                    }
                    boolean tel = VoidExcavationService.teleportToVoid(world, maid, navTarget);
                    if (world.getGameTime() % 200 == 0) {
                        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                            "[VOID-NAV] teleport maid={} target={} drop={} tel={} navTimeout={}",
                            maid.getStringUUID().substring(0, 8), target.toShortString(), navTarget.toShortString(),
                            tel, navTimeout);
                    }
                    if (tel) navTimeout = 0;   // 传送成功 → 重置
                } else if (world.getGameTime() % 200 == 0) {
                    // [VOID-NAV] 诊断: WALK_TARGET 是否设上 (MoveToTargetSink 该走)
                    com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                        "[VOID-NAV] set-walk maid={} target={} navTimeout={} walk={} nav={}",
                        maid.getStringUUID().substring(0, 8), target.toShortString(), navTimeout,
                        maid.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET),
                        maid.getBrain().hasMemoryValue(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get()));
                }
                pd.putInt("navTimeout", navTimeout);
                return;   // 本 tick 移动交给 MoveToTargetSink, 不再推进
            }
            // v79.6x 修漏挖: 到位后目标区块仍未加载 → 保光标等 3×3 预加载补上 (不推进 x/z/y, 防整列漏挖)
            if (!world.hasChunk(x >> 4, z >> 4)) {
                if (world.getGameTime() % 40 == 0) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                        "[VOID-MOVE] no-chunk target={} block=({},{})", target.toShortString(), x >> 4, z >> 4);
                }
                pd.putInt("y", y); pd.putInt("x", x); pd.putInt("z", z);
                return;
            }
            BlockState ts = world.getBlockState(target);
            if (world.getGameTime() % 40 == 0) {
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[VOID-MOVE] dig target={} state={} air={} fluid={}",
                    target.toShortString(), ts.isAir() ? "AIR" : ts.getBlock().getDescriptionId(),
                    ts.isAir(), !ts.getFluidState().isEmpty());
            }
            // 边界液体密封 (v79.62.2 用户裁定: 检测女仆 4 格内的区域外液体, 防流入已挖空区)
            sealBoundaryLiquid(world, maid, output, minX, maxX, minZ, maxZ);
            // ① 空气格跳过 (v79.62.1 修: 逐层横挖 — 空气格横向跳过不降层,
            // 一层可能有空气也可能有方块, 整层挖完才降 y; 原「空气就 y--」会漏挖同层方块 → 悬空)
            if (ts.isAir()) {
                // v79.62.1 修"女仆追不上 cursor": 空气快跳若使 cursor 离女仆过远 → 停下
                // (先传送/导航让女仆到附近, 再继续推进; 防 cursor 一 tick 飞走, 女仆永远追).
                // v79.62.1 修卡住: 限距用水平距离 (同 near — 女仆站 target.above() 挖下层,
                // 垂直差 1 是正常姿态; 原 3D distSqr 含垂直 → 水平 4 格+垂直 1 = 17 > 16 → 永不推进).
                BlockPos next = new BlockPos(x + 1, y, z);
                if (x >= maxX) next = new BlockPos(minX, y, z + 1);
                if (z >= maxZ && x >= maxX) next = new BlockPos(minX, y - 1, minZ);
                double dx = maid.getX() - (next.getX() + 0.5);
                double dz = maid.getZ() - (next.getZ() + 0.5);
                if (dx * dx + dz * dz > 16.0 * 16.0) {
                    return;   // 下一步水平距女仆 >16 格 → 本 tick 停, 下 tick 先传/走到位再推进
                }
                x++;
                if (x > maxX) { x = minX; z++; }
                if (z > maxZ) { z = minZ; y--; }   // 本层挖完 → 下一层
                pd.putInt("y", y); pd.putInt("x", x); pd.putInt("z", z);   // 进度写 PD
                continue;
            }
            // v79.62.1 用户裁定加回: 背包满 → 转存输出箱; 转存后仍满 (输出箱满+无扩展)
            // → 停挖站原地提醒等待 (不继续挖积压; 转存=背包满 OR 每 20t 定期已覆盖).
            if (VoidExcavationContainerService.backpackFull(maid)) {
                flushOutput(world, maid, pd, cfg, output);
                if (VoidExcavationContainerService.backpackFull(maid)) {
                    remindPlayer(maid, "背包满了，请补充输出箱或清理背包");
                    pd.putInt("wait", WAIT_TICKS);
                    return;   // 停挖等玩家补给
                }
            }
            // ④ 液体特判 (v79.62.2 用户裁定: 液体不填方块 — 直接替换成空气 + 破坏粒子,
            // 破坏特效即时反馈; 不再依赖背包/输出箱方块, 无方块也不卡住)
            if (!ts.getFluidState().isEmpty()) {
                // v79.62.2 液体: 清当前格 + 相邻扫描 (防相邻区块液体流过来) + 清缓存
                clearLiquidAt(world, maid, target);
                clearAdjacentLiquids(world, maid, target, 8);
                pd.putInt("digTicks", 0);
                pd.putInt("navTimeout", 0);
                pd.putInt("navCd", 0);
                x++;
                if (x > maxX) { x = minX; z++; }
                if (z > maxZ) { z = minZ; y--; }
                pd.putInt("y", y); pd.putInt("x", x); pd.putInt("z", z);   // 进度写 PD
                continue;   // 本格清完, 推进游标
            }
            // ⑤ 到达 → 按挖掘节拍挖一格 (v79.62.1 硬度公式: 方块硬度×30/工具速度)
            pd.putInt("navTimeout", 0);
            int digTicks = pd.getInt("digTicks") + 1;
            int interval = VoidExcavationService.digIntervalTicks(world, maid, target, ts);
            if (digTicks < interval) {
                pd.putInt("digTicks", digTicks);
                return;   // 节拍未到, 等
            }
            digTicks = 0;
            if (VoidExcavationService.tryDig(world, maid, target)) {
                advance(world, maid);
                VoidExcavationPool.claimHeartbeat(maid, world.getGameTime());   // v79.62.2 每挖一格更新认领心跳 (防慢速误杀)
                x++;
                if (x > maxX) { x = minX; z++; }
                if (z > maxZ) { z = minZ; y--; }
            } else {
                // 挖失败 (基岩等不可挖) → 跳过该列
                x++;
                if (x > maxX) { x = minX; z++; }
                if (z > maxZ) { z = minZ; y--; }
            }
            pd.putInt("digTicks", digTicks);
            pd.putInt("y", y); pd.putInt("x", x); pd.putInt("z", z);   // 进度写 PD (内存, 零 NBT)
            // v79.62.1 修"第二个女仆不清理": 转存 = 背包满 OR 每 20t 定期 —
            // 原只背包满时转存, 输出箱满但背包未满时方块一直积 (不触发满提示 → 积压).
            // 定期转存 (20t ≈ 1 秒) 防积压; 背包满检查在 ② 已做, 这里背包满时也会转存.
            if (world.getGameTime() % 20 == 0 || VoidExcavationContainerService.backpackFull(maid)) {
                flushOutput(world, maid, pd, cfg, output);
            }
            return;   // 每 tick 至少处理一列后收手
        }

        // 保存进度 (PD 内存 — 重启从标记层重挖, 空气快跳)
        pd.putInt("y", y);
        pd.putInt("x", x);
        pd.putInt("z", z);
        // v79.6x 用户裁定: 挖空置域 = 从标记层往下挖到底 (y<=minY) 即完成 — 撤「补挖高处」
        // v79.62.2 用户裁定: start.y = 可挖最高 y (认领直接设定高度), 不补扫高处 — 支持只挖地下区域.
        if (y <= minY) {
            VoidExcavationPool.poolMark(world, start, size, pd.getInt("curCX"), pd.getInt("curCZ"), 2);   // 本区块已挖完
            VoidExcavationPool.releaseClaim(maid);   // 清认领归属 (原子)
            pd.remove("curCX");
            pd.remove("curCZ");
            pd.putInt("y", start.getY());   // 新区块从标记层开始 (tick 开头覆盖为区块内)
            return;
        }
        // [DEBUG-VOID] 耗时诊断 (定位卡顿 tick, 定位后移除)
        long elapsedUs = (System.nanoTime() - t0) / 1000;
        if (elapsedUs > 2000) {   // >2ms 才打 (正常 tick 应 <1ms)
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER
                    .debug("[DEBUG-VOID] tick {}us x={} y={} z={} wait={} digT={}", elapsedUs, x, y, z,
                            pd.getInt("wait"), pd.getInt("digTicks"));
        }
    }

    /** 挖掘心跳 — 活跃任务定时 (防 GMPM 看门狗误杀长任务).
     *  <p>v79.62.1 改只 heartbeat: keepAlive 会 setNavTarget(当前位置) 覆盖导航目标,
     *  干扰"走不到再传送"的导航优先设计 — 心跳只防看门狗, 不碰导航记忆. */
    /**
     * 「第一层检查」— 传送落点必须先成为 **2 格净空** (v79.65.1, 用户实测).
     *
     * <p><b>为什么</b>: 女仆站姿 = 脚在落点格、头在其上 1 格 (1.8 格高). 逐步下挖时后续层的头位已在
     * 上一层挖空 ⇒ 安全; 但**第一层 (标记层) 的头位是未开挖的地表** ⇒ 直接传送 = 头埋实心方块
     * ⇒ **窒息持续掉血** ✗ (用户实测现象) ✓
     *
     * <p><b>语义 (用户裁定)</b>: 先挖**头位** (落点 +1), 再挖**本体** (落点) — "第一层挖掉两块"后女仆
     * 即可安全传送站位 ✓. 走既有 {@link VoidExcavationService#tryDig} 通道 (工具/耐久/销毁名单/背包
     * 一致 ✓), 沿用同一挖掘节拍 {@code digTicks} (不额外加速) ✓; 清不掉 (基岩/屏障) 则跳过, 不阻塞进度 ✓
     *
     * @return true = 本 tick 有动作 (挖了一格或节拍等待) ⇒ 调用方**不要传送**, 下 tick 复查;
     *         false = 落点已 2 格净空 ⇒ 可安全传送 ✓
     */
    private static boolean ensureLandingClear(ServerLevel world, EntityMaid maid, CompoundTag pd, BlockPos landing) {
        for (BlockPos p : new BlockPos[]{landing.above(), landing}) {   // 顺序: 头位 → 本体 (用户裁定)
            if (p.getY() > VoidExcavationService.maxY(world) || p.getY() < world.getMinBuildHeight()) continue;
            if (!world.hasChunk(p.getX() >> 4, p.getZ() >> 4)) return false;   // 区块未加载: 交上层预加载/传送门控
            BlockState st = world.getBlockState(p);
            if (st.isAir()) continue;
            if (st.is(net.minecraft.world.level.block.Blocks.BEDROCK) || !maid.canDestroyBlock(p)) continue;
            int digTicks = pd.getInt("digTicks") + 1;
            int interval = VoidExcavationService.digIntervalTicks(world, maid, p, st);
            if (digTicks < interval) {
                pd.putInt("digTicks", digTicks);
                return true;   // 节拍未到 ⇒ 本 tick 不传送
            }
            pd.putInt("digTicks", 0);
            VoidExcavationService.tryDig(world, maid, p);
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[VOID-LAND] 第一层检查: 挖净传送落点 {} ({}) 女仆={}",
                    p.toShortString(), p.equals(landing) ? "本体" : "头位", maid.blockPosition().toShortString());
            return true;   // 挖了一格 ⇒ 下 tick 复查 (头位挖完才轮到本体 ✓)
        }
        return false;
    }

    private static void advance(ServerLevel world, EntityMaid maid) {
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateManager.heartbeat(maid, world.getGameTime());
    }

    /** 区域边界液体密封 (v79.62.2 用户裁定: 检测女仆 4 格内的区域外液体, 防流入已挖空区).
     *  <p>以女仆位置为中心扫水平 ±4 格: 格子在区域外 (边界外 1 格密封层) 且是液体 →
     *  直接空气化 + 破坏粒子 (v79.62.2 改: 不再依赖输出箱方块, 液体蒸发掉).
     *  每 tick 检 (预算封顶防卡), 不是游标驱动 — 女仆挖到哪, 附近区域外液体就持续被清. */
    /** v79.62.2 清单格液体 → 空气 + 破坏粒子 + 挥手 + 清 WATER 缓存 (防旧缓存读到水) */
    private static void clearLiquidAt(ServerLevel world, EntityMaid maid, BlockPos p) {
        if (!world.hasChunk(p.getX() >> 4, p.getZ() >> 4)) return;
        net.minecraft.world.level.block.state.BlockState liq = world.getBlockState(p);
        if (liq.getFluidState().isEmpty()) return;
        world.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        world.levelEvent(2001, p, net.minecraft.world.level.block.Block.getId(liq));
        maid.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache
                .clearType(world.dimension().location().toString(),
                        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache.PatternType.WATER);
    }

    /** v79.62.2 液体相邻扫描: 清当前格四周 (水平 4 向 + 上下) 的液体 — 防相邻区块/格液体
     *  流进已挖空区 (用户裁定: 遇液体相邻扫描直接处理). budget 封顶防卡. */
    private static void clearAdjacentLiquids(ServerLevel world, EntityMaid maid, BlockPos center, int budget) {
        int[][] offs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] o : offs) {
            if (budget <= 0) break;
            BlockPos p = new BlockPos(center.getX() + o[0], center.getY() + o[1], center.getZ() + o[2]);
            clearLiquidAt(world, maid, p);
            budget--;
        }
    }

    private static void sealBoundaryLiquid(ServerLevel world, EntityMaid maid,
                                           BlockPos output, int minX, int maxX, int minZ, int maxZ) {
        BlockPos base = maid.blockPosition();
        int budget = 16;   // 每 tick 封顶 (防大范围液体一次扫爆)
        for (int dx = -4; dx <= 4 && budget > 0; dx++) {
            for (int dz = -4; dz <= 4 && budget > 0; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos p = new BlockPos(base.getX() + dx, base.getY(), base.getZ() + dz);
                // v79.62.2 区域内外都清: 已挖区/流入液体 (游标已过不回头) 由女仆经过时清掉 —
                // 原只清区域外密封层 → 区域内新放/流入液体没人管 (用户实证: 新放液体女仆不处理)
                // 修卡顿: 邻格区块未加载 → 跳过 (getFluidState 触发生成会卡主线程)
                if (!world.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
                if (world.getFluidState(p).isEmpty()) continue;
                // v79.62.2 用户裁定: 液体直接空气化 + 破坏粒子 + 清缓存 (不再依赖输出箱方块填充)
                clearLiquidAt(world, maid, p);
                budget--;
            }
        }
    }

    // (releaseChunks 已删 v79.62.1 — 区块只强制当前 1 个, 无批量释放)

    /** 容器检查 + 转存 — 返回 false = 需要玩家补给 (无工具/箱满), 已提醒 */
    private static boolean handleContainers(ServerLevel world, EntityMaid maid, CompoundTag pd, CompoundTag cfg,
                                            BlockPos input, BlockPos output) {
        // v79.62.1 无箱标记提醒 (用户裁定): 未标记输入箱/输出箱 → 全局限频气泡提醒
        // (不阻止挖掘 — 无输出箱时方块积背包, 无输入箱用自带工具/空手)
        if (input == null) {
            if (com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.ThrottleUtil
                    .shouldFire(maid, "void_no_input", 1200)) {
                MaidChatBubbleApi.showInfo(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.void.no_input_box"));
            }
        }
        if (output == null) {
            if (com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.ThrottleUtil
                    .shouldFire(maid, "void_no_output", 1200)) {
                MaidChatBubbleApi.showInfo(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.void.no_output_box"));
            }
        }
        // 工具: 先看背包 (全背包有工具 → 直接用, 不取箱) → 没有再取输入箱
        // (v79.62.1 用户裁定: 背包优先, 箱兜底; 防反复换手)
        if (input != null && !hasToolInInv(maid)) {
            if (VoidExcavationContainerService.takeToolFromInput(world, maid, input)) {
                // v79.6x 撤扩展扫描 — 取到工具即可
            } else {
                // v79.6x 用户裁定: 撤扩展扫描 — 只用标记输入箱, 没工具就提醒等补给
                remindPlayer(maid, "输入箱没有工具了，请补工具");
                pd.putInt("wait", WAIT_TICKS);
                return false;
            }
        }
        // v79.6x 用户裁定: 输出箱满也不停挖 — 只气泡提醒, 不 return false (溢出方块掉地上)
        if (output != null && !VoidExcavationContainerService.hasSpace(world, output)) {
            remindPlayer(maid, "输出箱满了，请清空或加容器");
        }
        return true;
    }

    /** 输出箱转存 (背包方块 → 输出箱) — 满则搜扩展容器并递归存入, 直到无可搜.
     *  v79.62.1 修缓存更新 (用户裁定): 放入后清 outNoExpand — 箱子被写入了空间变化,
     *  缓存必须刷新 (否则永久 noExpand 不重试主箱 → 背包积压不清理).
     *  @return true = 背包仍有剩 (输出箱全满无扩展), false = 全部转存成功. */
    private static boolean flushOutput(ServerLevel world, EntityMaid maid, CompoundTag pd, CompoundTag cfg, BlockPos output) {
        if (output == null) return false;
        int remaining = VoidExcavationContainerService.depositBlocks(world, maid, output);
        // v79.6x 撤扩展扫描 — 只存标记输出箱, 存不下就提醒 (下次 tick handleContainers 再判)
        if (remaining > 0) {
            // v79.6x 输出箱满不停挖 (提醒即可, 不 wait)
            remindPlayer(maid, "输出箱满了，请清空或加容器");
            return true;
        }
        return false;
    }

    /** 全背包是否有可用工具 (镐/锹/斧 — damageable item 含工具/武器) */
    private static boolean hasToolInInv(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.isDamageableItem()) return true;
        }
        return false;
    }

    /** 回玩家旁提醒 (气泡 + 主人聊天) — 1200t 节流防刷屏 + 首次必发 */
    private static void remindPlayer(EntityMaid maid, String msg) {
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.ThrottleUtil
                .shouldFire(maid, "void_excavation_remind", 1200)) {
            return;
        }
        MaidChatBubbleApi.showInfo(maid, msg);
        var owner = maid.getOwner();
        if (owner instanceof ServerPlayer sp) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("msg.littlemaidmoreaction.maid_prefix").append(msg));
        }
        // v79.62.1 修"挖完区块依旧会传送": 提醒不再传送到玩家旁 (挖空是远距离任务,
        // 传回玩家会打断挖掘/瞬移回起点; 女仆原地等待玩家来补给, 不传送)
    }

    /** 区块数 — v79.63.10 起**统一读全局** VOID_DEFAULT_CHUNKS (旧版 per-maid size 键已无写入点,
     *  NBT 残留值会覆盖全局 ⇒ 用户实测"全局设 2×2 仍挖 1×1" ✗; 将来加单女仆 UI 再恢复读键 ✓) */
    private static int readSize(EntityMaid maid, CompoundTag cfg) {
        // ★ v79.63.10 (用户实测: 全局设 2×2 却仍是 1×1): KEY_SIZE 是**旧版单女仆键** — 全仓已无任何写入点 ✗,
        //   但女仆 NBT 里的**残留值**优先级更高 ⇒ 实际挖 1×1 ✗ (用户改全局无效)。现统一读全局 ✓
        //   (将来若加单女仆 UI, 恢复此处读 KEY_SIZE 即可 ✓)
        // v79.63.16 回滚 (我 v79.63.10 改成"只读全局" ✗ ⇒ 把**单女仆 size 这条路弄没了** ✗, gametest lmavoid* 两条红 ✓):
        //   恢复单女仆优先 (文档语义 ✓), 缺省读全局 ✓; 用户"全局改了没生效"的真因是**残留值** ✗
        //   ⇒ 正解 = 屏上给**可编辑输入框** (能看能改能清 ✓ v79.63.16)
        if (cfg.contains(KEY_SIZE)) return cfg.getInt(KEY_SIZE);
        return com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.VOID_DEFAULT_CHUNKS.get();
    }

    @Override
    public void onCleanup(EntityMaid maid) {
        // (回滚注记见 tick 内 adaptToChunk 处 — 调度坐标还原尝试已撤销, 错题 #357)
        // v79.62.2 区块工作制恢复 (ChunkWorkArea 方法) — 还原 workPos + 释放预载 + 清状态
        if (maid.level() instanceof ServerLevel world) {
            com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.ChunkWorkArea.State cw = VoidExcavationPool.removeChunkWorkState(maid);
            if (cw != null) com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.ChunkWorkArea.restoreFromChunk(world, maid, cw);
        }
        CompoundTag pdTmp = pipelineData(maid);
        // v79.62.1 释放认领区块 (防残留占坑) — PD curCX/curCZ 认领中的区块释放 (未挖完)
        CompoundTag cfgTmp = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfg(maid, "void_excavation");
        // v79.6x 释放输入/输出箱强制加载 (每 tick force, 任务结束必须释放, 防永久强载泄漏)
        if (cfgTmp != null && maid.level() instanceof ServerLevel worldBox) {
            BlockPos inTmp = NbtCodecs.readBlockPos(cfgTmp, "input");
            BlockPos outTmp = NbtCodecs.readBlockPos(cfgTmp, "output");
            if (inTmp != null) com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationChunkManager
                    .forceChunk(worldBox, inTmp.getX() >> 4, inTmp.getZ() >> 4, false);
            if (outTmp != null) com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationChunkManager
                    .forceChunk(worldBox, outTmp.getX() >> 4, outTmp.getZ() >> 4, false);
        }
        // v79.6x 修取消复活: 任务结束但 cfg 仍含 start = 用户取消(非完成) → 清 start 防 TlmEventAdapter 重启自动恢复
        if (cfgTmp != null && cfgTmp.contains(TaskKeys.CFG_START)) {
            cfgTmp.remove("start");
        }
        if (pdTmp.contains("curCX") && maid.level() instanceof ServerLevel world2) {
            BlockPos stTmp = NbtCodecs.readBlockPos(cfgTmp, TaskKeys.CFG_START);
            int szTmp = readSize(maid, cfgTmp);
            if (stTmp != null) VoidExcavationPool.poolMark(world2, stTmp, szTmp, pdTmp.getInt("curCX"), pdTmp.getInt("curCZ"), 0);   // 释放为未挖 (可再领)
            VoidExcavationPool.releaseClaim(maid);   // 清认领归属 (原子, 防残留)
        }
        // 恢复原 workPos (挖空期间 workPos 跟随女仆, 任务结束还原玩家设置) — PD 存
        if (pdTmp.contains("origWorkPos")) {
            BlockPos orig = NbtCodecs.readBlockPos(pdTmp, "origWorkPos");
            if (orig != null) maid.getSchedulePos().setWorkPos(orig);
        }
        // v79.62.2 挖完恢复 home (用户裁定: 挖时强制 home, 挖完换回原状态)
        if (pdTmp.contains("wasHome") && !pdTmp.getBoolean("wasHome")) {
            maid.setHomeModeEnable(false);
        }
        // 清瞬态 (PL), 保留持久进度 (cfg) — 重启从上次位置继续
        TaskPipeline.super.onCleanup(maid);
    }
}
