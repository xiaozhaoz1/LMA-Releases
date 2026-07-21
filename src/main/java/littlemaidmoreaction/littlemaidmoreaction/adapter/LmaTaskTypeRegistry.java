package littlemaidmoreaction.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LMA 任务类型注册中心 — 扫描规则提取 task_type，创建并注册 typed IMaidTask。
 *
 * <p>在 {@code addMaidTask()} 阶段调用 {@link #scanAndRegister(TaskManager)}：
 * <ol>
 *   <li>扫描所有已加载规则，提取 condition/action 中的 task_type 参数</li>
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
        Map.entry("rest",      Items.RED_BED)
    );

    private static final ItemStack DEFAULT_ICON = Items.CRAFTING_TABLE.getDefaultInstance();

    /** ★ v35: 从 TaskRegistry 读取已知任务类型 */
    private static Set<String> getKnownTaskTypes() {
        return littlemaidmoreaction.littlemaidmoreaction.task.TaskRegistry.taskTypes();
    }

    /** 已知简单任务类型 (启动时初始化 + 可运行时注册) */
    static {
        SIMPLE_TASKS.add("bell_ring");
        SIMPLE_TASKS.add("jukebox");
    }

    private LmaTaskTypeRegistry() {}

    // ── 注册入口 ──

    /**
     * 扫描已加载规则，创建 typed tasks 并注册到 TaskManager。
     * 调用时机: {@code LittleMaidMoreActionExtension.addMaidTask()}
     */
    public static void scanAndRegister(TaskManager manager) {
        // ★ v16: TLM-native 任务全部删除，改用 LmaTypedFlowTask (Pipeline 决策)
        //    old TLM-native: jukebox, bell_ring, furnace, craft_chain
        // ★ v14: brewing 已完全删除
        // altar_craft 保留 LmaTypedFlowTask (委派规则引擎)

        for (String known : getKnownTaskTypes()) {
            if ("brewing".equals(known)) continue;
            // v52: TaskRegistry.showInBar 控制 TLM 任务栏可见性
            if (!littlemaidmoreaction.littlemaidmoreaction.task.TaskRegistry.isShowInBar(known)) continue;
            registerIfNew(known);
        }

        // 2. 扫描规则中的 task_type（运行时动态添加的类型）
        for (RuleDef rule : RuleActionStorage.getRules()) {
            for (ConditionDef c : rule.conditions()) {
                String type = c.params().get("task_type");
                registerIfNew(type);
            }
            for (ActionStep a : rule.actions()) {
                String type = a.params().get("task_type");
                registerIfNew(type);
            }
        }

        // 3. 注册所有 typed tasks 到 TLM
        int count = 0;
        for (IMaidTask task : TYPED.values()) {
            manager.add(task);
            count++;
        }

        // 4. 注册泛用 fallback
        manager.add(LmaFlowTask.get());

        LittleMaidMoreAction.LOGGER.info("[LMA] Registered {} typed flow tasks + 1 fallback to TLM", count);
    }

    private static void registerIfNew(String taskType) {
        if (taskType == null || taskType.isEmpty() || TYPED.containsKey(taskType)) return;
        LmaTypedFlowTask task = new LmaTypedFlowTask(taskType);
        TYPED.put(taskType, task);
        LittleMaidMoreAction.LOGGER.debug("[LMA] Registered typed task: {}", task.getUid());
    }

    // ── 查询 ──

    /** ★ v16: TLM-native 任务已删除，全部走 TYPED map (LmaTypedFlowTask) 回退 */
    public static IMaidTask findByTaskType(String taskType) {
        if (taskType == null || taskType.isEmpty()) return LmaFlowTask.get();
        return TYPED.getOrDefault(taskType, LmaFlowTask.get());
    }

    /** 是否为简单任务 (无需 AI 内容即可执行) */
    public static boolean isSimple(String taskType) {
        return taskType != null && SIMPLE_TASKS.contains(taskType);
    }

    /** 运行时注册简单任务类型 (供 compat 模块使用) */
    public static void registerSimple(String taskType) {
        SIMPLE_TASKS.add(taskType);
    }

    /** 从 ResourceLocation 路径提取 task_type (如 lma:task/altar_craft → altar_craft) */
    public static String extractTaskType(String uidPath) {
        if (uidPath == null) return null;
        String prefix = "task/";
        int idx = uidPath.indexOf(prefix);
        if (idx >= 0) {
            return uidPath.substring(idx + prefix.length());
        }
        // 兼容旧格式 lma:flow_task
        if (uidPath.equals("flow_task")) return "flow_task";
        return null;
    }

    // ── 图标 ──

    /** 根据 task_type 获取图标 */
    public static ItemStack getIcon(String taskType) {
        if (taskType == null || taskType.isEmpty()) return DEFAULT_ICON;
        // v36: 精确映射优先 — 关键词 contains 对 collect_wood 会误中 "collect"→镐
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

    /** 获取已注册的 typed task 数量 */
    public static int typedCount() {
        return TYPED.size();
    }

    // ── ★ v12.7 P1: 任务关键词统一映射 (单一真相源) ──

    /** 任务关键词 → task_type 映射 (用于 AI prompt 注入) */
    public static final Map<String, String> TASK_KEYWORD_MAP = Map.ofEntries(
        Map.entry("craft", "craft_chain"),
        Map.entry("make", "craft_chain"),
        Map.entry("smelt", "furnace"),
        Map.entry("cook", "furnace"),
        Map.entry("烧", "furnace"),
        Map.entry("brew", "brewing"),
        Map.entry("potion", "brewing"),
        Map.entry("炼药", "brewing"),
        Map.entry("bell", "bell_ring"),
        Map.entry("ring", "bell_ring"),
        Map.entry("敲钟", "bell_ring"),
        Map.entry("music", "jukebox"),
        Map.entry("record", "jukebox"),
        Map.entry("唱片", "jukebox"),
        Map.entry("farm", "farm"),
        Map.entry("harvest", "farm"),
        Map.entry("种地", "farm")
    );

    /** 生成任务关键词 AI prompt 文本 (所有 AI 工具/上下文共用) */
    public static String buildTaskKeywordPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALL crafting table work = craft_chain with item_id in data. ");
        sb.append("target_count: 0=craft ALL, >0=specific. ");
        sb.append("Other: furnace/smelt→furnace, ");
        sb.append("brew→brewing, bell→bell_ring, music→jukebox, farm→farm. ");
        sb.append("Do NOT query inventory — just assign task directly.");
        return sb.toString();
    }
}
