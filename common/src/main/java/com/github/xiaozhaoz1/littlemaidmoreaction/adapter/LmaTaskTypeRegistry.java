package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LMA 任务类型注册中心 — 从 TaskRegistry 读取已知类型，创建并注册 typed IMaidTask。
 *
 * <p>在 {@code addMaidTask()} 阶段调用 {@link #scanAndRegister(TaskManager)}：
 * <ol>
 *   <li>遍历 TaskRegistry 已知任务类型 (规则引擎 task_type 动态注册已裁撤)</li>
 *   <li>每种 task_type 创建一个 {@link LmaTypedFlowTask}</li>
 *   <li>注册到 TLM TaskManager</li>
 *   <li>最后注册泛用 {@link LmaFlowTask} 作为 fallback</li>
 * </ol>
 *
 * <h3>任务复杂度分类</h3>
 * <ul>
 *   <li><b>简单任务</b> — 无需参数/有默认行为，可从 GUI 直接触发 (bell_ring, jukebox)</li>
 *   <li><b>复杂任务</b> — 需要配方/物品/目标，必须 AI 先设定内容</li>
 * </ul>
 */
public final class LmaTaskTypeRegistry {

    /** typed tasks: task_type → IMaidTask */
    private static final Map<String, IMaidTask> TYPED = new ConcurrentHashMap<>();

    /** 简单任务类型 — 无需参数即可执行 */
    private static final Set<String> SIMPLE_TASKS = ConcurrentHashMap.newKeySet();

    /** 任务专属图标 (LMAT.registerTask 聚合注册) — getIcon 优先于此表 */
    private static final Map<String, Item> TASK_ICONS = new ConcurrentHashMap<>();

    /** 图标映射: 关键词 → Item */
    private static final Map<String, Item> ICON_MAP = Map.ofEntries(
        Map.entry("craft",     Items.CRAFTING_TABLE),
        Map.entry("furnace",   Items.FURNACE),
        Map.entry("smelt",     Items.FURNACE),
        Map.entry("brewing",   Items.BREWING_STAND),
        Map.entry("brew",      Items.BREWING_STAND),
        Map.entry("bell",      Items.BELL),
        Map.entry("jukebox",   Items.JUKEBOX),
        Map.entry("farm",      Items.IRON_HOE),
        Map.entry("harvest",   Items.IRON_HOE),
        Map.entry("crop",      Items.IRON_HOE),
        Map.entry("collect",   Items.IRON_PICKAXE),
        Map.entry("mine",      Items.IRON_PICKAXE),
        Map.entry("gather",    Items.IRON_PICKAXE),
        Map.entry("patrol",    Items.IRON_SWORD),
        Map.entry("guard",     Items.IRON_SWORD),
        Map.entry("arm",       Items.PISTON),
        Map.entry("transfer",  Items.PISTON),
        Map.entry("bed",       Items.RED_BED),
        Map.entry("sleep",     Items.RED_BED),
        Map.entry("rest",      Items.RED_BED),
        Map.entry("interact",  Items.STICK),
        Map.entry("block",     Items.STICK),
        Map.entry("assembly",  Items.CRAFTING_TABLE)
    );

    private static final ItemStack DEFAULT_ICON = Items.CRAFTING_TABLE.getDefaultInstance();

    /** 从 TaskRegistry 读取已知任务类型 */
    private static Set<String> getKnownTaskTypes() {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.taskTypes();
    }

    /** 使用自定义 IMaidTask 的任务类型 — scanAndRegister 跳过 */
    private static final Set<String> CUSTOM_TASK_TYPES = Set.of("maid_assembly");

    /** 最近一次 scanAndRegister 的 TaskManager — 迟注册钩子 onTaskRegistered 补注册目标 */
    private static volatile TaskManager lastManager;

    /** 已知简单任务类型 (启动时初始化 + 可运行时注册) */
    static {
        SIMPLE_TASKS.add("bell_ring");
        SIMPLE_TASKS.add("jukebox");
        SIMPLE_TASKS.add("block_interact");
    }

    private LmaTaskTypeRegistry() {}

    // ── 注册入口 ──

    /**
     * 扫描已知任务类型，创建 typed tasks 并注册到 TaskManager。
     * 调用时机: {@code LittleMaidMoreActionExtension.addMaidTask()}
     * 注册纯 TaskRegistry 驱动 — 规则引擎 task_type 动态注册已随事件链裁撤移除。
     */
    public static void scanAndRegister(TaskManager manager) {
        lastManager = manager; // 记录供迟注册钩子 — 外部 Mod 晚于 LMA 注册时即时补注册
        for (String known : getKnownTaskTypes()) {
            // brewing 跳过特例已删 (2026-08-11c): 旧口径 farm/brewing 已删任务残留 —
            // LMA-MAIN 零注册 "brewing" (grep 实证), TLM 1.20.1 无 brewing 任务 (仅 MAID_BREWING 音效),
            // 守卫永不命中; 外部 mod 真注册 brewing 反而会被静默吞掉 (TaskManager 无 uid 冲突)
            if (CUSTOM_TASK_TYPES.contains(known)) continue; // 使用自定义 IMaidTask
            // TaskRegistry.showInBar 控制 TLM 任务栏可见性
            if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.isShowInBar(known)) continue;
            registerIfNew(known);
        }

        // 注册所有 typed tasks 到 TLM
        int count = 0;
        for (IMaidTask task : TYPED.values()) {
            manager.add(task);
            count++;
        }

        // 注册泛用 fallback
        manager.add(LmaFlowTask.get());

        LittleMaidMoreAction.LOGGER.info("[LMA] Registered {} typed flow tasks + 1 fallback to TLM", count);
    }

    /**
     * 迟注册钩子 — 经 LMAT 门面注册的主动任务 (register/registerTask) 调用。
     *
     * <p>时机问题: {@link #scanAndRegister(TaskManager)} 只在 LMA 的 {@code addMaidTask}
     * 运行一次, 外部 Mod 的注册顺序不保证 — 晚于 LMA 注册的任务类型不在扫描快照内,
     * TLM 任务栏将缺失其 typed IMaidTask。本钩子在 manager 已知后即时补注册,
     * 消除顺序依赖; 早于 LMA 注册的任务走扫描路径 (taskTypes() 已含), 钩子幂等跳过。
     *
     * <p>被动任务不注册 (showInBar=false 不进任务栏, 与扫描口径一致)。
     *
     * <p>fail-soft: TLM TaskManager.init 末尾把 TASK_MAP/TASK_INDEX 冻结为
     * ImmutableMap/ImmutableList (TLM 源码实证), 注册晚于 init 时 add 抛
     * UnsupportedOperationException — 捕获降 WARN, 任务仍可经 LMAT.submit 驱动。
     */
    public static void onTaskRegistered(String taskType) {
        TaskManager manager = lastManager;
        if (manager == null) return; // scanAndRegister 尚未运行 — 后续扫描会覆盖
        if (CUSTOM_TASK_TYPES.contains(taskType)) return;
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.isShowInBar(taskType)) return;
        if (TYPED.containsKey(taskType)) return; // 扫描期已注册 — 防 TLM 重复 add
        registerIfNew(taskType);
        IMaidTask task = TYPED.get(taskType);
        if (task != null) {
            try {
                manager.add(task);
                LittleMaidMoreAction.LOGGER.info("[LMA] Late-registered typed task to TLM: {}", task.getUid());
            } catch (RuntimeException e) {
                LittleMaidMoreAction.LOGGER.warn(
                    "[LMA] TLM TaskManager 已冻结, 任务栏注册过晚 ({}): 请在 TLM addMaidTask 阶段注册 — {}",
                    taskType, e.toString());
            }
        }
    }

    private static void registerIfNew(String taskType) {
        if (taskType == null || taskType.isEmpty() || TYPED.containsKey(taskType)) return;
        if (CUSTOM_TASK_TYPES.contains(taskType)) return; // 双防护
        LmaTypedFlowTask task = new LmaTypedFlowTask(taskType);
        TYPED.put(taskType, task);
        LittleMaidMoreAction.LOGGER.debug("[LMA] Registered typed task: {}", task.getUid());
    }

    // ── 查询 ──

    /** 是否为简单任务 (无需 AI 内容即可执行) — LmaTaskGuiHandler GUI 启用条件判定 */
    public static boolean isSimple(String taskType) {
        return taskType != null && SIMPLE_TASKS.contains(taskType);
    }

    /** 从 ResourceLocation 路径提取 task_type — 委托纯类 {@link TaskTypeUid} (同包, 纯 JVM 可测) */
    public static String extractTaskType(String uidPath) {
        return TaskTypeUid.extractTaskType(uidPath);
    }

    // ── 图标 ──

    /**
     * 注册任务专属图标 (LMAT.registerTask 聚合调用) — keyword 经 ICON_MAP 关键词表解析
     * (如 "craft"/"bell"/"arm", 大小写不敏感); 未命中 → 不注册, getIcon 回退默认/关键词包含匹配。
     */
    public static void registerIcon(String taskType, String iconKeyword) {
        if (taskType == null || taskType.isEmpty() || iconKeyword == null) return;
        Item icon = ICON_MAP.get(iconKeyword.toLowerCase(Locale.ROOT));
        if (icon != null) TASK_ICONS.put(taskType, icon);
    }

    /** 根据 task_type 获取图标 (专属表优先 → 硬编码特例 → 关键词包含匹配 → 默认) */
    public static ItemStack getIcon(String taskType) {
        if (taskType == null || taskType.isEmpty()) return DEFAULT_ICON;
        Item custom = TASK_ICONS.get(taskType);
        if (custom != null) return custom.getDefaultInstance();
        switch (taskType) {
            case "collect_wood": return Items.IRON_AXE.getDefaultInstance();
            case "collect_ore": return Items.IRON_PICKAXE.getDefaultInstance();
            default: break;
        }
        String lower = taskType.toLowerCase(Locale.ROOT);
        for (var entry : ICON_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue().getDefaultInstance();
            }
        }
        return DEFAULT_ICON;
    }

}
