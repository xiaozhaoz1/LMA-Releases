package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StructBox;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 结构发现专用类 (v79.58 合集 → v79.60 per-player → v79.61 状态机语义 → v79.6x enter 气泡+聊天)。
 *
 * <p>v79.61 (用户裁定重设计): 三信息节点 —
 * <ul>
 *   <li>发现: 主人首次扫到结构 (NEAR 环带) → 随机 1 个附近主人女仆气泡 (≤enter 格 "附近有X" / 远 "{方向}方向有X")</li>
 *   <li>进入: d≤enter (首扫即 IN / NEAR→IN) → "到X啦" 气泡+聊天 (v79.6x 用户裁定: IN 进入即提示, 停留恒静默)</li>
 *   <li>刷新: 主人在结构外 (enter&lt;d≤leave) 每 refreshTicks 重发方向气泡, 上限 refreshMax 次 (含首次)</li>
 *   <li>离开: d&gt;leave → leave 信号 — 不提示 (静默, 信号层保留供未来 LLM 上下文)</li>
 * </ul>
 * 走回 (离开后 ≤leave) 重新发现提醒。距离一律指距结构边界盒 (v79.6x BB 语义: 在盒内 = 0)。
 *
 * <p>状态机 per-player per-structure: {@link Phase} {OUT/NEAR/IN}, OUT 即剪枝 (条目删除);
 * bubble = 该结构当前是最近 (允许气泡+提醒预算推进), 相位迁移不受 bubble 影响 —
 * enter 提示对全部白名单结构照发 (v79.6x 用户裁定: 进了哪个就提示哪个, 不区分最近);
 * displaced (被挤下最近) 冻结预算不消耗提醒数; leave 静默信号照发 (LLM 上下文完整)。
 *
 * <p>信号 id: {@code env:structure:{registryId}:discover|refresh|enter|leave} — 管线通配订阅
 * (dispatch 前缀匹配支持, 见 EnvSenseBroadcaster.dispatchToPipelines)。文案 emit 侧按玩家位置
 * 算好存缓存 (女仆共享零计算)。同轮 ≥2 结构 discover 合并为一条气泡 (合并信号
 * {@code _multi:discover} 先发, 个体 discover 照发供信号层消费 — showTrigger 100t 节流只显示合并条)。
 *
 * <p>过滤: 白名单 (registry id, 支持 {@code minecraft:village_*} 通配, 空=全部) + 最近开关
 * (仅最近结构允许气泡, 状态机仍跟踪全部白名单结构)。
 *
 * <p>生命周期: 全内存 per-player 缓存, 每轮清旧写新不落盘; 玩家下线由 {@link #sweep}
 * 懒清理 (每广播轮对照全服在线玩家集 getPlayerList().getPlayers(), 不在线的 playerId 自动回收 — 200t 内残留, 零事件零新类)。
 */
public final class StructureSense {

    /** 结构信号前缀 (动态 String 信号 — 管线通配订阅 env:structure:*) */
    public static final String PREFIX = "env:structure:";

    /** 玩家距结构中心的相位 (距离平方域判定) */
    enum Phase { OUT, NEAR, IN }

    /** 信号后缀类型 — discover/refresh/enter 气泡+聊天, leave 静默 (LLM 预留) */
    enum SignalKind { DISCOVER, REFRESH, ENTER, LEAVE }

    /** per-structure 状态: 相位 + 提醒节拍 (lastRemind=上次提醒 tick, count=已提醒次数含首次) */
    record StructState(Phase phase, long lastRemind, int count) {}

    /** 状态机参数快照 (配置值注入, 纯 JVM 可测 — 错题 #174 铁律: 不触 PassiveTaskConfig) */
    record StructConfig(int enterDist, int leaveDist, int refreshTicks, int refreshMax) {
        /** 构造 + 钳制不变量: enter < leave (配置越界兜底) */
        static StructConfig of(int enterDist, int leaveDist, int refreshTicks, int refreshMax) {
            int e = Math.min(enterDist, leaveDist - 1);
            return new StructConfig(e, leaveDist, refreshTicks, refreshMax);
        }
        int enterSqr() { return enterDist * enterDist; }
        int leaveSqr() { return leaveDist * leaveDist; }
    }

    /** 单步结果: next==null = 剪枝 (删除条目) */
    record StructStep(StructState next, Set<SignalKind> signals) {}

    /** playerId → 结构状态 (相位机; 内存态, 懒清理) */
    private static final Map<UUID, Map<String, StructState>> PLAYER_STATE = new HashMap<>();
    /** playerId → 气泡文案缓存 (discover/refresh/enter + 合并条, emit 侧算好 Component — 客户端按语言翻译 v79.6x) */
    private static final Map<UUID, Map<String, Component>> PLAYER_TEXT = new HashMap<>();
    /** playerId → 上次结构扫描 tick (per-player 节流 — ThrottleUtil 是 per-maid PD 键不可用) */
    private static final Map<UUID, Long> PLAYER_LAST = new HashMap<>();

    private StructureSense() {}

    // ── 纯函数 (JVM 可测) ──

    /** 相对玩家朝向的 6 向方位 (用户裁定: 前后左右上下, 非 8 罗盘方位) */
    enum RelativeDir { FRONT, BACK, LEFT, RIGHT, UP, DOWN }

    /** far 语气模板池 (lang 键 — 结构名/方向/语气词全双语 v79.6x) */
    static final List<String> TIP_FAR_KEYS = List.of(
            "structure_sense.tip.far.0", "structure_sense.tip.far.1", "structure_sense.tip.far.2",
            "structure_sense.tip.far.3", "structure_sense.tip.far.4", "structure_sense.tip.far.5");
    /** near 语气模板池 */
    static final List<String> TIP_NEAR_KEYS = List.of(
            "structure_sense.tip.near.0", "structure_sense.tip.near.1", "structure_sense.tip.near.2",
            "structure_sense.tip.near.3", "structure_sense.tip.near.4", "structure_sense.tip.near.5");
    /** enter 语气模板池 (v79.6x 用户裁定: 进入结构提示 — 气泡+聊天共用; 村庄走专属键) */
    static final List<String> TIP_ENTER_KEYS = List.of(
            "structure_sense.tip.enter.0", "structure_sense.tip.enter.1", "structure_sense.tip.enter.2");
    /** 村庄 enter 专属模板 (用户裁定示例 "到村庄啦, 来收集小麦做蛋糕吧") */
    static final String TIP_ENTER_VILLAGE = "structure_sense.tip.enter.village";

    /**
     * 相对方位纯函数 — 玩家朝向 yaw (MC: 0=南 +Z, 90=西 -X) 参数注入, JVM 可测。
     * 垂直差 > 水平距离 → 上/下; 否则前/后/左/右 (横向 |lat| 与纵向 |fwd| 分桶)。
     */
    static RelativeDir relativeDir(double dx, double dz, double dy, float yaw) {
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (Math.abs(dy) > horiz) return dy > 0 ? RelativeDir.UP : RelativeDir.DOWN;
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);   // 前向
        double rx = -fz, rz = fx;                         // 右向 (面向南时右=西)
        double fwd = dx * fx + dz * fz;
        double lat = dx * rx + dz * rz;
        if (Math.abs(lat) > Math.abs(fwd)) return lat > 0 ? RelativeDir.RIGHT : RelativeDir.LEFT;
        return fwd >= 0 ? RelativeDir.FRONT : RelativeDir.BACK;
    }

    /** 相对方位 → lang 键 (纯) */
    static String dirKeyOf(RelativeDir dir) {
        return "structure_sense.dir." + dir.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 结构 registry id → lang 键 (纯)。精确变体键优先 (村庄 5 变种等 — 代码层区分,
     * 中文值可相同); 未知变体走前缀 fallback 归组 (未来/新 mod 结构不静默丢)。
     */
    static String structureKeyOf(String structureId) {
        String name = structureId.contains(":") ? structureId.substring(structureId.indexOf(':') + 1) : structureId;
        String exact = switch (name) {
            case "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga",
                 "mineshaft", "mineshaft_mesa",
                 "ocean_ruin_cold", "ocean_ruin_warm",
                 "shipwreck", "shipwreck_beached",
                 "trail_ruins", "ruined_portal", "pillager_outpost",
                 "desert_pyramid", "jungle_temple", "swamp_hut", "igloo",
                 "stronghold", "fortress", "end_city", "monument", "mansion",
                 "buried_treasure", "ancient_city", "trial_chambers"
                    -> "structure_sense.structure." + name;
            default -> null;
        };
        if (exact != null) return exact;
        // fallback 前缀归组 (未知变体)
        if (name.startsWith("village_")) return "structure_sense.structure.village";
        if (name.startsWith("mineshaft")) return "structure_sense.structure.mineshaft";
        if (name.startsWith("ocean_ruin")) return "structure_sense.structure.ocean_ruin";
        if (name.startsWith("shipwreck")) return "structure_sense.structure.shipwreck";
        return null;
    }

    /** enter 模板键 (纯): 村庄族走专属键 (含 flavour 文案), 其余随机通用池 */
    static String enterTipKey(String structKey) {
        if (structKey.startsWith("structure_sense.structure.village")) return TIP_ENTER_VILLAGE;
        return TIP_ENTER_KEYS.get(ThreadLocalRandom.current().nextInt(TIP_ENTER_KEYS.size()));
    }

    /** 气泡文案组件 (纯) — far 模板两参 [方向, 结构名], near 模板一参 [结构名]; 客户端按语言翻译 */
    static Component tipComponent(String tipKey, @javax.annotation.Nullable String dirKey, String structKey) {
        Component struct = Component.translatable(structKey);
        if (dirKey == null) return Component.translatable(tipKey, struct);
        return Component.translatable(tipKey, Component.translatable(dirKey), struct);
    }

    /** 距离² → 相位 */
    static Phase phaseOf(double distSqr, int enterSqr, int leaveSqr) {
        if (distSqr <= enterSqr) return Phase.IN;
        if (distSqr <= leaveSqr) return Phase.NEAR;
        return Phase.OUT;
    }

    /**
     * 单结构状态机单步 — prev==null 表示 UNKNOWN (从未跟踪)。
     * bubble = 该结构当前是最近 (允许气泡+预算推进); 相位迁移不受 bubble 影响。
     */
    static StructStep structStep(StructState prev, Phase cur, long now, boolean bubble, StructConfig cfg) {
        if (prev == null) {
            return switch (cur) {
                // v79.6x 用户裁定: 首扫即 IN 只发 ENTER ("到X啦") — 不再并 DISCOVER ("附近有X" 两连弹冗余)
                case IN -> new StructStep(new StructState(Phase.IN, bubble ? now : 0L, bubble ? 1 : 0),
                        EnumSet.of(SignalKind.ENTER));
                case NEAR -> new StructStep(new StructState(Phase.NEAR, bubble ? now : 0L, bubble ? 1 : 0),
                        bubble ? EnumSet.of(SignalKind.DISCOVER) : EnumSet.noneOf(SignalKind.class));
                case OUT -> new StructStep(null, EnumSet.noneOf(SignalKind.class));   // 首扫 OUT (距结构中心 > leaveDist): 静默不记
            };
        }
        return switch (cur) {
            case OUT -> new StructStep(null, EnumSet.of(SignalKind.LEAVE));           // 离开: leave+剪枝 (bubble 无关)
            case IN -> {
                if (prev.phase() != Phase.IN)
                    yield new StructStep(new StructState(Phase.IN, prev.lastRemind(), prev.count()),
                            EnumSet.of(SignalKind.ENTER));                            // NEAR→IN: enter 照发
                yield new StructStep(prev, EnumSet.noneOf(SignalKind.class));         // IN 停留静默
            }
            case NEAR -> {
                if (prev.phase() == Phase.IN)                                        // IN→NEAR: 静默, 预算重置
                    yield new StructStep(new StructState(Phase.NEAR, now, 0), EnumSet.noneOf(SignalKind.class));
                if (!bubble)                                                         // displaced: 冻结预算 (count/lastRemind 不消耗)
                    yield new StructStep(prev, EnumSet.noneOf(SignalKind.class));
                if (prev.count() == 0 && prev.lastRemind() == 0)                     // 从未气泡的 displaced NEAR 转正: 首信号 DISCOVER 非 REFRESH
                    yield new StructStep(new StructState(Phase.NEAR, now, 1), EnumSet.of(SignalKind.DISCOVER));
                if (now - prev.lastRemind() >= cfg.refreshTicks() && prev.count() < cfg.refreshMax())
                    yield new StructStep(new StructState(Phase.NEAR, now, prev.count() + 1),
                            EnumSet.of(SignalKind.REFRESH));
                yield new StructStep(prev, EnumSet.noneOf(SignalKind.class));
            }
        };
    }

    /** 信号后缀 → 类型 (管线解析复用; 未知 → null) */
    static SignalKind kindOf(String suffix) {
        return switch (suffix) {
            case "discover" -> SignalKind.DISCOVER;
            case "refresh" -> SignalKind.REFRESH;
            case "enter" -> SignalKind.ENTER;
            case "leave" -> SignalKind.LEAVE;
            default -> null;
        };
    }

    /** 气泡文案 — 旧 8 方位硬编码版已删 (v79.6x: 6 向 + 语气词 + lang 双语, 见 buildTexts/tipComponent) */

    /** 白名单过滤 + 最近开关 — 纯逻辑可 JVM 测 (参数注入, 不触 MC config); 最近 = 距边界盒最近 */
    static Map<String, StructBox> filterPure(Map<String, StructBox> found, BlockPos center,
                                            List<? extends String> whitelist, boolean nearestOnly) {
        Map<String, StructBox> out = new LinkedHashMap<>();
        for (Map.Entry<String, StructBox> e : found.entrySet()) {
            if (whitelist != null && !whitelist.isEmpty() && !matchList(e.getKey(), whitelist)) continue;
            out.put(e.getKey(), e.getValue());
        }
        if (nearestOnly && !out.isEmpty()) {
            String nearest = null;
            double best = Double.MAX_VALUE;
            for (Map.Entry<String, StructBox> e : out.entrySet()) {
                double d = e.getValue().distSqrFrom(center.getX(), center.getY(), center.getZ());
                if (d < best) {
                    best = d;
                    nearest = e.getKey();
                }
            }
            Map<String, StructBox> single = new LinkedHashMap<>();
            single.put(nearest, out.get(nearest));
            return single;
        }
        return out;
    }

    /**
     * 白名单匹配: 精确 id 或任意前缀通配 (尾部 "*") — 纯可测。
     * 支持路径级通配 {@code minecraft:village_*} (ItemFilters 只有 modid:* namespace 级);
     * {@code minecraft:*} (原语义) 与 {@code *} (全部) 自然兼容。
     */
    static boolean matchList(String id, List<? extends String> list) {
        for (String entry : list) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            if (e.endsWith("*")) {
                String prefix = e.substring(0, e.length() - 1);
                if (id.startsWith(prefix)) return true;
            } else if (e.equals(id)) {
                return true;
            }
        }
        return false;
    }

    // ── 公共入口 ──

    /**
     * 检测入口 — EnvSenseBroadcaster.broadcast 每轮对每个在线玩家调用:
     * 节流 → 门控前置 (无主人女仆不扫) → 区块缓存摊薄扫描 (预算 P) → 清旧写新缓存 → 状态机推进 + emit。
     * 无主人女仆 → 本轮不扫不推进 (信号必须有接收者, 女仆走近后首次 discover 是期望行为)。
     */
    public static void detect(ServerLevel level, ServerPlayer player, long deadlineNanos) {
        if (!PassiveTaskConfig.ENV_STRUCTURE_ENABLED.get()) return;
        UUID pid = player.getUUID();
        long now = level.getGameTime();
        long last = PLAYER_LAST.getOrDefault(pid, 0L);
        if (last != 0 && now >= last && now - last < PassiveTaskConfig.ENV_STRUCTURE_INTERVAL.get()) return;   // 时钟回退守卫 (对齐 ThrottleMath)
        PLAYER_LAST.put(pid, now);

        // v79.6x 预算方案 P ①: 门控前置 — 无附近女仆不扫 (实测省 ~50% 轮次)
        EntityMaid target = pickMaid(level, player);
        if (target == null) {
            PLAYER_TEXT.put(pid, Map.of());   // 保持 detect 写 PLAYER_TEXT 语义 (gametest 缓存生命周期锚点)
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.debug(
                    "[LMA/Structure] no owner maid near player={} (radius={})",  // 2026-08-16 降噪: 周期刷屏 INFO→DEBUG
                    player.getGameProfile().getName(), PassiveTaskConfig.ENV_STRUCTURE_SIGNAL_RADIUS.get());
            return;
        }

        BlockPos center = player.blockPosition();
        // v79.6x 预算方案 P ②③: 区块级共享缓存 + 摊薄螺旋 + 墙钟预算 (vanilla/input/world 工具 API)
        Map<String, StructBox> found = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StructureScanCache.GLOBAL
                .scanAround(level, center, PassiveTaskConfig.ENV_STRUCTURE_RADIUS.get(), now, deadlineNanos);
        // v79.6x 诊断日志 (结构气泡无痕排查) — 每玩家每 60s 一条
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.debug(
                "[LMA/Structure] detect player={} center={} found={}",  // 2026-08-16 降噪: 周期刷屏 INFO→DEBUG
                player.getGameProfile().getName(), center.toShortString(), found.size());
        // 清旧写新缓存 (内存不落盘; Component 双语 — 玩家朝向注入方向词 v79.6x)
        PLAYER_TEXT.put(pid, buildTexts(center, found,
                PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST.get(), player.getYRot()));

        StructConfig cfg = StructConfig.of(
                PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST.get(),
                PassiveTaskConfig.ENV_STRUCTURE_LEAVE_DIST.get(),
                PassiveTaskConfig.ENV_STRUCTURE_REFRESH_TICKS.get(),
                PassiveTaskConfig.ENV_STRUCTURE_REFRESH_MAX.get());
        List<? extends String> whitelist = PassiveTaskConfig.ENV_STRUCTURE_WHITELIST.get();
        Map<String, StructBox> whitelisted = filterPure(found, center, whitelist, false);
        // bubble 集: 允许气泡+预算的结构 (NEAREST_ONLY 只取最近 1 个; 状态机仍跟踪全部白名单结构)
        Set<String> bubbleIds = PassiveTaskConfig.ENV_STRUCTURE_NEAREST_ONLY.get()
                ? filterPure(found, center, whitelist, true).keySet()
                : whitelisted.keySet();
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.debug(
                "[LMA/Structure] targetMaid={} whitelisted={} bubble={}",  // 2026-08-16 降噪: 周期刷屏 INFO→DEBUG
                target.getId(), whitelisted.keySet(), bubbleIds);

        Map<String, StructState> states = PLAYER_STATE.computeIfAbsent(pid, k -> new HashMap<>());
        // 推进集 = whitelisted ∪ states (副本迭代, remove 时防 CME)
        Set<String> tracked = new HashSet<>(whitelisted.keySet());
        tracked.addAll(states.keySet());
        List<Map.Entry<String, StructBox>> discovers = new ArrayList<>();   // 本轮 discover 收集 (合并气泡 MED-3)
        for (String id : tracked) {
            StructBox box = whitelisted.get(id);
            if (box == null) {
                // 消失路径: 移出扫描半径/白名单中途被改 — 曾跟踪则 leave (静默) + 剪枝
                if (states.containsKey(id)) {
                    EnvSenseBroadcaster.emit(level, target, PREFIX + id + ":leave");
                }
                states.remove(id);
                continue;
            }
            StructState prev = states.get(id);
            Phase cur = phaseOf(box.distSqrFrom(center.getX(), center.getY(), center.getZ()), cfg.enterSqr(), cfg.leaveSqr());
            StructStep step = structStep(prev, cur, now, bubbleIds.contains(id), cfg);
            if (step.next() == null) states.remove(id);
            else states.put(id, step.next());
            for (SignalKind kind : step.signals()) {
                if (kind == SignalKind.DISCOVER) {
                    discovers.add(Map.entry(id, box));   // 延迟 emit: 同轮多结构合并气泡 (MED-3)
                    continue;
                }
                EnvSenseBroadcaster.emit(level, target,
                        PREFIX + id + ":" + kind.name().toLowerCase(Locale.ROOT));
                // v79.6x 聊天栏提示 (refresh 方向重发 / enter 到达 — 玩家不用一直盯着女仆气泡)
                if (kind == SignalKind.REFRESH || kind == SignalKind.ENTER) {
                    Map<String, Component> texts = PLAYER_TEXT.get(pid);
                    Component text = texts == null ? null : texts.get(id + ":" + kind.name().toLowerCase(Locale.ROOT));
                    if (text != null) player.sendSystemMessage(text);
                }
            }
        }
        // MED-3: 同轮 ≥2 结构 discover → 先发合并气泡 (showTrigger 100t 节流只显示第一条), 个体 discover 照发供信号层消费
        Component merged = buildMergedText(center, discovers, cfg.enterDist(), player.getYRot());
        if (merged != null) {
            PLAYER_TEXT.get(pid).put("_multi:discover", merged);
            EnvSenseBroadcaster.emit(level, target, PREFIX + "_multi:discover");
            player.sendSystemMessage(merged);   // v79.6x 聊天栏: 合并条发一次
        } else {
            // 单结构 discover 聊天栏 (无合并时逐个发)
            Map<String, Component> texts = PLAYER_TEXT.get(pid);
            for (Map.Entry<String, StructBox> d : discovers) {
                Component text = texts == null ? null : texts.get(d.getKey() + ":discover");
                if (text != null) player.sendSystemMessage(text);
            }
        }
        for (Map.Entry<String, StructBox> d : discovers) {
            EnvSenseBroadcaster.emit(level, target, PREFIX + d.getKey() + ":discover");
        }
        if (states.isEmpty()) PLAYER_STATE.remove(pid);
    }

    /**
     * 懒清理 — EnvSenseBroadcaster.broadcast 每轮调用:
     * 不在线玩家缓存自动回收 (结构状态全 player 维度, 无 per-maid 残留)。
     */
    public static void sweep(ServerLevel level) {
        Set<UUID> online = new HashSet<>();
        // 全服在线集 (getPlayerList) 而非 level.players() — 多维度在线玩家按单维度扫会被误清
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            online.add(p.getUUID());
        }
        PLAYER_STATE.keySet().removeIf(u -> !online.contains(u));
        PLAYER_TEXT.keySet().removeIf(u -> !online.contains(u));
        PLAYER_LAST.keySet().removeIf(u -> !online.contains(u));
    }

    // ── 调试入口 (v79.6x /lma structure 指令 — 直接触发信号效果, 绕过节流与状态机) ──

    /**
     * /lma structure fire 实现 — 扫结构→建文案→选女仆→发射信号 (气泡+聊天真实链路),
     * 不推进状态机不写节流 (纯效果触发, 调试几分钟才变的现象用)。
     */
    public static String debugFire(ServerLevel level, ServerPlayer player, String kindSuffix) {
        SignalKind kind = kindOf(kindSuffix);
        if (kind == null) return "未知信号: " + kindSuffix + " (可用 discover/refresh/enter/leave)";
        BlockPos center = player.blockPosition();
        Map<String, StructBox> found = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StructureScanCache.GLOBAL
                .scanAround(level, center, PassiveTaskConfig.ENV_STRUCTURE_RADIUS.get(),
                        level.getGameTime(), System.nanoTime() + 20_000_000L);
        PLAYER_TEXT.put(player.getUUID(), buildTexts(center, found,
                PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST.get(), player.getYRot()));
        EntityMaid target = pickMaid(level, player);
        if (target == null) return "无附近主人女仆 (信号半径 " + PassiveTaskConfig.ENV_STRUCTURE_SIGNAL_RADIUS.get() + " 格)";
        Map<String, StructBox> whitelisted = filterPure(found, center,
                PassiveTaskConfig.ENV_STRUCTURE_WHITELIST.get(), false);
        if (whitelisted.isEmpty()) return "附近无白名单结构 (共扫到 " + found.size() + " 个)";
        String kindName = kind.name().toLowerCase(Locale.ROOT);
        Map<String, Component> texts = PLAYER_TEXT.get(player.getUUID());
        for (String id : whitelisted.keySet()) {
            EnvSenseBroadcaster.emit(level, target, PREFIX + id + ":" + kindName);
            if (kind == SignalKind.DISCOVER || kind == SignalKind.REFRESH || kind == SignalKind.ENTER) {
                Component text = texts == null ? null : texts.get(id + ":" + kindName);
                if (text != null) player.sendSystemMessage(text);
            }
        }
        return "已发射 " + kindName + " ×" + whitelisted.size() + " → 女仆 " + target.getName().getString()
                + " (结构: " + String.join(", ", whitelisted.keySet()) + ")";
    }

    /** /lma structure state — 状态机 + 文案缓存快照 */
    public static String debugState(java.util.UUID pid) {
        Map<String, StructState> states = PLAYER_STATE.get(pid);
        Map<String, Component> texts = PLAYER_TEXT.get(pid);
        if ((states == null || states.isEmpty()) && (texts == null || texts.isEmpty())) return "无缓存";
        StringBuilder sb = new StringBuilder();
        if (states != null && !states.isEmpty()) {
            for (var e : states.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue().phase())
                        .append(" last=").append(e.getValue().lastRemind())
                        .append(" count=").append(e.getValue().count()).append("\n");
            }
        }
        if (texts != null && !texts.isEmpty()) {
            sb.append("文案键: ").append(String.join(", ", texts.keySet()));
        }
        return sb.toString().trim();
    }

    /** /lma structure reset — 清当前玩家缓存 (下次 detect 视为首次发现) */
    public static String debugReset(java.util.UUID pid) {
        PLAYER_STATE.remove(pid);
        PLAYER_TEXT.remove(pid);
        PLAYER_LAST.remove(pid);
        return "已清空结构缓存 (下次探测视为首次)";
    }

        // ── gametest 锚点 (审计 T1; 测试钩子 — 仅供 gametest 断言缓存生命周期) ──
    public static boolean hasPlayerState(UUID pid) { return PLAYER_STATE.containsKey(pid); }
    public static boolean hasPlayerText(UUID pid) { return PLAYER_TEXT.containsKey(pid); }

    /**
     * onSignal 查文案 — 女仆主人 UUID → 缓存 → 结构 id + 信号后缀 → 气泡 Component。
     * 文案 emit 侧按玩家位置/朝向算好 (女仆共享零计算); leave 无文案 (静默信号)。
     */
    public static Component textFor(UUID owner, String structId, String kind) {
        Map<String, Component> texts = PLAYER_TEXT.get(owner);
        if (texts == null) return null;
        return texts.get(structId + ":" + kind);
    }

    /** MED-3: Component 列表用「、」连接 (纯 JVM 可测 — 仅构造组件树, 不触语言加载) */
    static Component joinComponents(List<Component> parts) {
        if (parts.isEmpty()) return null;
        net.minecraft.network.chat.MutableComponent out = parts.get(0).copy();
        for (int i = 1; i < parts.size(); i++) {
            out.append(Component.literal("、"));
            out.append(parts.get(i));
        }
        return out;
    }

    /**
     * MED-3: 同轮多结构 discover 合并文案 — 不足 2 个有文案的结构返回 null (走逐条气泡)。
     * 合并句用固定模板 (near.0/far.0), 与单条同 lang 双语体系 (v79.6x)。
     */
    static Component buildMergedText(BlockPos center, List<Map.Entry<String, StructBox>> discovers,
                                     int enterDist, float yaw) {
        List<Component> parts = new ArrayList<>();
        double enterSqr = (double) enterDist * enterDist;
        for (Map.Entry<String, StructBox> d : discovers) {
            String structKey = structureKeyOf(d.getKey());
            if (structKey == null) continue;
            StructBox box = d.getValue();
            boolean near = box.distSqrFrom(center.getX(), center.getY(), center.getZ()) <= enterSqr;
            String tipKey = near ? TIP_NEAR_KEYS.get(0) : TIP_FAR_KEYS.get(0);
            String dirKey = near ? null : dirKeyOf(relativeDir(
                    box.centerX() - (center.getX() + 0.5),
                    box.centerZ() - (center.getZ() + 0.5),
                    box.centerY() - center.getY(), yaw));
            parts.add(tipComponent(tipKey, dirKey, structKey));
        }
        if (parts.size() < 2) return null;
        return joinComponents(parts);
    }

    // ── 内部 ──

    /**
     * 文案表构建 (v79.6x): discover/refresh 同形文案各一键 — 随机语气模板 (near/far 池) +
     * 相对玩家朝向 6 向方向词 + 结构名, 全走 lang 键 → Component.translatable (客户端双语)。
     * 未知结构 (structureKeyOf null) 跳过。
     */
    private static Map<String, Component> buildTexts(BlockPos center, Map<String, StructBox> found,
                                                       int enterDist, float yaw) {
        Map<String, Component> texts = new HashMap<>();
        double enterSqr = (double) enterDist * enterDist;
        for (Map.Entry<String, StructBox> e : found.entrySet()) {
            String structKey = structureKeyOf(e.getKey());
            if (structKey == null) continue;  // 未映射结构 — 无气泡 (信号已消费, 与旧 labelOf 语义一致)
            StructBox box = e.getValue();
            boolean near = box.distSqrFrom(center.getX(), center.getY(), center.getZ()) <= enterSqr;
            List<String> pool = near ? TIP_NEAR_KEYS : TIP_FAR_KEYS;
            String tipKey = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            String dirKey = near ? null : dirKeyOf(relativeDir(
                    box.centerX() - (center.getX() + 0.5),
                    box.centerZ() - (center.getZ() + 0.5),
                    box.centerY() - center.getY(), yaw));
            Component text = tipComponent(tipKey, dirKey, structKey);
            texts.put(e.getKey() + ":discover", text);
            texts.put(e.getKey() + ":refresh", text);
            // v79.6x: enter 独立文案 (气泡+聊天共用 — "到X啦"; 村庄专属模板含 flavour)
            texts.put(e.getKey() + ":enter", tipComponent(enterTipKey(structKey), null, structKey));
        }
        return texts;
    }

    /**
     * 门控 + 选女仆: 玩家 SIGNAL_RADIUS 格内的主人女仆中选 1 个 (随机/最近, 配置);
     * 无主人女仆 → null (不发信号)。MC 实体绑定, 不纯 JVM 测。
     * v79.6x 优化: AABB 范围粗筛 (getEntitiesOfClass) 替代全维度 getAllEntities,
     * distanceToSqr 精筛保留圆形半径语义 (行为零变化)。
     */
    private static EntityMaid pickMaid(ServerLevel level, ServerPlayer player) {
        int radius = PassiveTaskConfig.ENV_STRUCTURE_SIGNAL_RADIUS.get();
        double r2 = (double) radius * radius;
        UUID owner = player.getUUID();
        AABB box = new AABB(player.blockPosition()).inflate(radius);
        List<EntityMaid> near = new ArrayList<>();
        for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class, box,
                m -> m.isAlive() && owner.equals(m.getOwnerUUID()))) {
            if (maid.distanceToSqr(player) <= r2) near.add(maid);   // 圆形精筛 (角落粗筛超出部分裁掉)
        }
        if (near.isEmpty()) return null;
        if (PassiveTaskConfig.ENV_STRUCTURE_RANDOM_MAID.get()) {
            return near.get(level.random.nextInt(near.size()));
        }
        EntityMaid best = near.get(0);
        for (int i = 1; i < near.size(); i++) {
            if (near.get(i).distanceToSqr(player) < best.distanceToSqr(player)) best = near.get(i);
        }
        return best;
    }

    // labelOf (中文硬编码) 已删 — v79.6x 结构名/方向词/语气词全走 lang 键 (structureKeyOf/dirKeyOf),
    // Component.translatable 客户端按语言翻译 (中英对照)。
}
