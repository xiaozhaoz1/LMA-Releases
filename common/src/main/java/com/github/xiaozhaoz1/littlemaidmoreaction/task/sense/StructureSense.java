package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

/**
 * 结构发现专用类 (v79.58 合集 → v79.60 per-player → v79.61 状态机语义)。
 *
 * <p>v79.61 (用户裁定重设计): 三信息节点 —
 * <ul>
 *   <li>发现: 主人首次扫到结构 → 随机 1 个附近主人女仆气泡 (≤enter 格 "附近有X" / 远 "{方向}方向有X")</li>
 *   <li>刷新: 主人在结构外 (enter&lt;d≤leave) 每 refreshTicks 重发方向气泡, 上限 refreshMax 次 (含首次)</li>
 *   <li>进入/离开: d≤enter → enter 信号, d&gt;leave → leave 信号 — 均静默不气泡, 信号层保留供未来 LLM 上下文</li>
 * </ul>
 * 走回 (离开后 ≤leave) 重新发现提醒。离开判定是距离检查 (距结构中心), 非结构边缘。
 *
 * <p>状态机 per-player per-structure: {@link Phase} {OUT/NEAR/IN}, OUT 即剪枝 (条目删除);
 * bubble = 该结构当前是最近 (允许气泡+提醒预算推进), 相位迁移不受 bubble 影响 —
 * enter/leave 对全部白名单结构照发 (LLM 上下文完整); displaced (被挤下最近) 冻结预算不消耗提醒数。
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

    /** 信号后缀类型 — discover/refresh 气泡, enter/leave 静默 (LLM 预留) */
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
    /** playerId → 气泡文案缓存 (discover/refresh 共用, emit 侧算好, 女仆共享) */
    private static final Map<UUID, Map<String, String>> PLAYER_TEXT = new HashMap<>();
    /** playerId → 上次结构扫描 tick (per-player 节流 — ThrottleUtil 是 per-maid PD 键不可用) */
    private static final Map<UUID, Long> PLAYER_LAST = new HashMap<>();

    private StructureSense() {}

    // ── 纯函数 (JVM 可测) ──

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
                case IN -> new StructStep(new StructState(Phase.IN, bubble ? now : 0L, bubble ? 1 : 0),
                        union(bubble ? EnumSet.of(SignalKind.DISCOVER) : EnumSet.noneOf(SignalKind.class),
                                EnumSet.of(SignalKind.ENTER)));
                case NEAR -> new StructStep(new StructState(Phase.NEAR, bubble ? now : 0L, bubble ? 1 : 0),
                        bubble ? EnumSet.of(SignalKind.DISCOVER) : EnumSet.noneOf(SignalKind.class));
                case OUT -> new StructStep(null, EnumSet.noneOf(SignalKind.class));   // 首扫 100-128 区间: 静默不记
            };
        }
        return switch (cur) {
            case OUT -> new StructStep(null, EnumSet.of(SignalKind.LEAVE));           // 离开: leave+剪枝 (bubble 无关)
            case IN -> {
                if (prev.phase() != Phase.IN)
                    yield new StructStep(new StructState(Phase.IN, prev.lastRemind(), prev.count()),
                            EnumSet.of(SignalKind.ENTER));                            // NEAR→IN: enter 照发
                if (prev.count() == 0 && prev.lastRemind() == 0)                      // 从未气泡的 displaced IN 转正: 首信号 DISCOVER
                    yield new StructStep(new StructState(Phase.IN, now, 1), EnumSet.of(SignalKind.DISCOVER));
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

    private static Set<SignalKind> union(Set<SignalKind> a, Set<SignalKind> b) {
        EnumSet<SignalKind> out = EnumSet.noneOf(SignalKind.class);
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    /** 气泡文案 — ≤enter 格 "附近有X"; 远 "{方向}方向有X" (纯可测) */
    static String textNearby(BlockPos center, BlockPos pos, String label, int enterDist) {
        if (pos.distSqr(center) <= (double) enterDist * enterDist) return "附近有" + label;
        return EnvScanner.directionWord(center, pos) + "方向有" + label;
    }

    /** 白名单过滤 + 最近开关 — 纯逻辑可 JVM 测 (参数注入, 不触 MC config) */
    static Map<String, BlockPos> filterPure(Map<String, BlockPos> found, BlockPos center,
                                            List<? extends String> whitelist, boolean nearestOnly) {
        Map<String, BlockPos> out = new LinkedHashMap<>();
        for (Map.Entry<String, BlockPos> e : found.entrySet()) {
            if (whitelist != null && !whitelist.isEmpty() && !matchList(e.getKey(), whitelist)) continue;
            out.put(e.getKey(), e.getValue());
        }
        if (nearestOnly && !out.isEmpty()) {
            String nearest = null;
            double best = Double.MAX_VALUE;
            for (Map.Entry<String, BlockPos> e : out.entrySet()) {
                double d = e.getValue().distSqr(center);
                if (d < best) {
                    best = d;
                    nearest = e.getKey();
                }
            }
            Map<String, BlockPos> single = new LinkedHashMap<>();
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
     * 节流 → 扫玩家附近结构 → 清旧写新缓存 → 门控 (附近主人女仆选 1 个) → 状态机推进 + emit。
     * 无主人女仆 → 本轮状态不推进 (信号必须有接收者, 女仆走近后首次 discover 是期望行为)。
     */
    public static void detect(ServerLevel level, ServerPlayer player) {
        if (!PassiveTaskConfig.ENV_STRUCTURE_ENABLED.get()) return;
        UUID pid = player.getUUID();
        long now = level.getGameTime();
        long last = PLAYER_LAST.getOrDefault(pid, 0L);
        if (last != 0 && now >= last && now - last < PassiveTaskConfig.ENV_STRUCTURE_INTERVAL.get()) return;   // 时钟回退守卫 (对齐 ThrottleMath)
        PLAYER_LAST.put(pid, now);

        BlockPos center = player.blockPosition();
        Map<String, BlockPos> found = EnvScanner.scanAllStructures(level, center,
                PassiveTaskConfig.ENV_STRUCTURE_RADIUS.get());
        // 清旧写新缓存 (内存不落盘)
        PLAYER_TEXT.put(pid, buildTexts(center, found, PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST.get()));

        // 门控: 玩家附近有主人女仆 → 才发信号 (选 1 个, 最近/随机)
        EntityMaid target = pickMaid(level, player);
        if (target == null) return;

        StructConfig cfg = StructConfig.of(
                PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST.get(),
                PassiveTaskConfig.ENV_STRUCTURE_LEAVE_DIST.get(),
                PassiveTaskConfig.ENV_STRUCTURE_REFRESH_TICKS.get(),
                PassiveTaskConfig.ENV_STRUCTURE_REFRESH_MAX.get());
        List<? extends String> whitelist = PassiveTaskConfig.ENV_STRUCTURE_WHITELIST.get();
        Map<String, BlockPos> whitelisted = filterPure(found, center, whitelist, false);
        // bubble 集: 允许气泡+预算的结构 (NEAREST_ONLY 只取最近 1 个; 状态机仍跟踪全部白名单结构)
        Set<String> bubbleIds = PassiveTaskConfig.ENV_STRUCTURE_NEAREST_ONLY.get()
                ? filterPure(found, center, whitelist, true).keySet()
                : whitelisted.keySet();

        Map<String, StructState> states = PLAYER_STATE.computeIfAbsent(pid, k -> new HashMap<>());
        // 推进集 = whitelisted ∪ states (副本迭代, remove 时防 CME)
        Set<String> tracked = new HashSet<>(whitelisted.keySet());
        tracked.addAll(states.keySet());
        List<Map.Entry<String, BlockPos>> discovers = new ArrayList<>();   // 本轮 discover 收集 (合并气泡 MED-3)
        for (String id : tracked) {
            BlockPos pos = whitelisted.get(id);
            if (pos == null) {
                // 消失路径: 移出扫描半径/白名单中途被改 — 曾跟踪则 leave (静默) + 剪枝
                if (states.containsKey(id)) {
                    EnvSenseBroadcaster.emit(level, target, PREFIX + id + ":leave");
                }
                states.remove(id);
                continue;
            }
            StructState prev = states.get(id);
            Phase cur = phaseOf(pos.distSqr(center), cfg.enterSqr(), cfg.leaveSqr());
            StructStep step = structStep(prev, cur, now, bubbleIds.contains(id), cfg);
            if (step.next() == null) states.remove(id);
            else states.put(id, step.next());
            for (SignalKind kind : step.signals()) {
                if (kind == SignalKind.DISCOVER) {
                    discovers.add(Map.entry(id, pos));   // 延迟 emit: 同轮多结构合并气泡 (MED-3)
                    continue;
                }
                EnvSenseBroadcaster.emit(level, target,
                        PREFIX + id + ":" + kind.name().toLowerCase(Locale.ROOT));
            }
        }
        // MED-3: 同轮 ≥2 结构 discover → 先发合并气泡 (showTrigger 100t 节流只显示第一条), 个体 discover 照发供信号层消费
        String merged = buildMergedText(center, discovers, cfg.enterDist());
        if (merged != null) {
            PLAYER_TEXT.get(pid).put("_multi:discover", merged);
            EnvSenseBroadcaster.emit(level, target, PREFIX + "_multi:discover");
        }
        for (Map.Entry<String, BlockPos> d : discovers) {
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

    // ── gametest 锚点 (审计 T1; 测试钩子 — 仅供 gametest 断言缓存生命周期) ──
    public static boolean hasPlayerState(UUID pid) { return PLAYER_STATE.containsKey(pid); }
    public static boolean hasPlayerText(UUID pid) { return PLAYER_TEXT.containsKey(pid); }

    /**
     * onSignal 查文案 — 女仆主人 UUID → 缓存 → 结构 id + 信号后缀 → 气泡文案。
     * 文案 emit 侧按玩家位置算好 (女仆共享零计算); enter/leave 无文案 (静默信号)。
     */
    public static String textFor(UUID owner, String structId, String kind) {
        Map<String, String> texts = PLAYER_TEXT.get(owner);
        if (texts == null) return null;
        return texts.get(structId + ":" + kind);
    }

    /** MED-3: 纯函数拼接合并文案 (JVM 可测) — near 组加 "附近有" 前缀, 远组已含方位词 */
    static String joinMergedText(List<String> near, List<String> far) {
        List<String> parts = new ArrayList<>();
        if (!near.isEmpty()) parts.add("附近有" + String.join("、", near));
        parts.addAll(far);
        return String.join("、", parts);
    }

    /**
     * MED-3: 同轮多结构 discover 合并文案 — 不足 2 个有文案的结构返回 null (走逐条气泡)。
     * 近组 "附近有X" / 远组 "{方向}方向有X" (与 textNearby 同口径)。
     */
    static String buildMergedText(BlockPos center, List<Map.Entry<String, BlockPos>> discovers, int enterDist) {
        List<String> near = new ArrayList<>();
        List<String> far = new ArrayList<>();
        for (Map.Entry<String, BlockPos> d : discovers) {
            String label = labelOf(d.getKey());
            if (label == null) continue;
            if (d.getValue().distSqr(center) <= (double) enterDist * enterDist) near.add(label);
            else far.add(EnvScanner.directionWord(center, d.getValue()) + "方向有" + label);
        }
        if (near.size() + far.size() < 2) return null;
        return joinMergedText(near, far);
    }

    // ── 内部 ──

    /** 文案表构建: discover/refresh 同形文案各一键; 未知结构 (labelOf null) 跳过 */
    private static Map<String, String> buildTexts(BlockPos center, Map<String, BlockPos> found, int enterDist) {
        Map<String, String> texts = new HashMap<>();
        for (Map.Entry<String, BlockPos> e : found.entrySet()) {
            String label = labelOf(e.getKey());
            if (label == null) continue;  // 未映射结构 — 无气泡 (信号已消费, 与 v79.58 一致)
            String text = textNearby(center, e.getValue(), label, enterDist);
            texts.put(e.getKey() + ":discover", text);
            texts.put(e.getKey() + ":refresh", text);
        }
        return texts;
    }

    /**
     * 门控 + 选女仆: 玩家 SIGNAL_RADIUS 格内的主人女仆中选 1 个 (随机/最近, 配置);
     * 无主人女仆 → null (不发信号)。MC 实体绑定, 不纯 JVM 测。
     */
    private static EntityMaid pickMaid(ServerLevel level, ServerPlayer player) {
        int radius = PassiveTaskConfig.ENV_STRUCTURE_SIGNAL_RADIUS.get();
        double r2 = (double) radius * radius;
        UUID owner = player.getUUID();
        List<EntityMaid> near = new ArrayList<>();
        for (var e : level.getAllEntities()) {
            if (!(e instanceof EntityMaid maid)) continue;
            if (!maid.isAlive()) continue;
            if (!owner.equals(maid.getOwnerUUID())) continue;
            if (maid.distanceToSqr(player) > r2) continue;
            near.add(maid);
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

    /** 结构 registry id → 气泡中文名 (前缀匹配 — 村庄变体/矿井变体归组); 未知 → null (忽略气泡) */
    public static String labelOf(String structureId) {
        String name = structureId.contains(":") ? structureId.substring(structureId.indexOf(':') + 1) : structureId;
        if (name.startsWith("village_")) return "村庄";
        if (name.startsWith("mineshaft")) return "废弃矿井";
        if (name.startsWith("ruined_portal")) return "废弃传送门";
        if (name.startsWith("shipwreck")) return "沉船";
        if (name.startsWith("ocean_ruin")) return "海底废墟";
        if (name.startsWith("pillager_outpost")) return "掠夺者前哨站";
        return switch (name) {
            case "desert_pyramid" -> "沙漠神殿";
            case "jungle_temple" -> "丛林神庙";
            case "swamp_hut" -> "沼泽小屋";
            case "igloo" -> "雪屋";
            case "stronghold" -> "要塞";
            case "fortress" -> "地狱堡垒";
            case "end_city" -> "末地城";
            case "monument" -> "海底神殿";
            case "mansion" -> "林地府邸";
            case "buried_treasure" -> "埋藏宝藏";
            case "ancient_city" -> "古城";
            case "trial_chambers" -> "试炼大厅";
            default -> null;
        };
    }
}
