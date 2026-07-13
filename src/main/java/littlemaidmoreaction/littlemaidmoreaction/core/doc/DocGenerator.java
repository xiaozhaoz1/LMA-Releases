package littlemaidmoreaction.littlemaidmoreaction.core.doc;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.event.RuleEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 文档自动生成器 — 从注册中心和枚举反射生成介绍文档。
 *
 * <p>在模组初始化后调用 {@link #generateAll(Path)}，
 * 输出到 {@code config/littlemaidmoreaction/introduce/}：
 * <ul>
 *   <li>{@code events.md} — 29 触发事件介绍</li>
 *   <li>{@code conditions.md} — N 条件 key 介绍（含参数）</li>
 *   <li>{@code actions.md} — N 动作类型介绍（含参数）</li>
 * </ul>
 *
 * <p>全部内容从 {@link RuleEvent}、{@link ConditionRegistry}、
 * {@link ActionRegistry} 运行时反射生成，确保与代码始终一致。
 */
public final class DocGenerator {

    private DocGenerator() {}

    /** 规则 JSON 模板 — 手写常量，用户可见的入门文档 */
    private static final String RULE_TEMPLATE = """
# 规则 JSON 模板

> 存放位置: `config/littlemaidmoreaction/rules/<id>.rule.json`
> 每文件一条规则。模组首次启动自动生成内置预设。

## 完整字段

```jsonc
{
  // ── 基本信息 ──
  "id": 0,                           // 唯一 ID（自动递增）
  "name": "规则名称",                  // 规则名称（编辑器显示）
  "enabled": true,                    // 是否启用
  "compat": [],                       // ★ v9.0: 依赖的兼容模组（自动检测）

  // ── 触发 ──
  "eventId": "maid_hurt_target_pre", // 触发事件 ID（见 events.md）
  "chance": 1.0,                     // 条件满足后的执行概率 0.0~1.0
  "cooldown": 200,                   // 冷却 tick 数（20 tick = 1 秒）
  "priority": 100,                   // 优先级，同事件多条规则命中时越大越优先

  // ── 条件匹配 ──
  "matchMode": "ALL",                // ALL=全部满足 / ANY=任一满足
  "conditions": [
    // ── 比较条件 ──
    {
      "key": "damage_type",          // 条件 key（见 conditions.md）
      "op": ":=:",                   // 操作符（见 conditions.md 底部）
      "val": "melee"                 // 期望值（$开头=引用其他key）
    },
    // ── 布尔条件 ──
    { "key": "would_lethal" }        // 无 op/val = 选中即满足
  ],

  // ── 动作序列 ──
  "actions": [
    {
      "type": "play_anim",           // 动作 ID（见 actions.md）
      "params": {                    // 参数键值对（见 actions.md 各动作参数）
        "mode": "INSTANT",
        "anim": "execution",
        "auto_wait": "true"
      }
    },
    {"type": "cancel_event"},        // 无参动作
    {"type": "wait", "params": {"ticks": "20"}},
    {"type": "random", "params": {"chance": "0.3", "skip": "2"}}
  ]
}
```

## compat 字段

标记规则依赖的兼容模组。保存时自动根据条件/动作检测，也支持手动填写：

```json
{
  "id": 100,
  "name": "YSM-变身",
  "compat": ["ysm"],
  ...
}
```

兼容模组未加载时规则仍可加载和执行 — 未知条件返回 false，未知动作跳过。

## 参数值表达式

动作参数值支持 `$<key>` 语法引用条件运行时值：

```
"amount": "$health_ratio * 1.0"    → 目标生命比例 × 1.0
"amount": "$maid_health_ratio"     → 女仆生命比例
"amount": "$favorability + 100"    → 好感度 + 100
```

## 完整示例

### 处决（近战斩杀）

```json
{
  "id": 0,
  "name": "预设-处决",
  "enabled": true,
  "eventId": "maid_hurt_target_pre",
  "chance": 1.0,
  "cooldown": 200,
  "priority": 100,
  "matchMode": "ALL",
  "conditions": [
    {"key": "damage_type", "op": ":=:", "val": "melee"},
    {"key": "would_lethal"}
  ],
  "actions": [
    {"type": "cancel_event"},
    {"type": "teleport", "params": {"target": "target", "mode": "in_front", "distance": "2.0"}},
    {"type": "play_anim", "params": {"mode": "INSTANT", "anim": "execution", "auto_wait": "true"}},
    {"type": "deal_damage", "params": {"damage_type": "execution_kill"}}
  ]
}
```

### 闪避（远程侧闪 + 30% 概率嘲讽）

```json
{
  "id": 1,
  "name": "预设-闪避",
  "enabled": true,
  "eventId": "maid_attack",
  "chance": 0.1,
  "cooldown": 60,
  "priority": 50,
  "matchMode": "ALL",
  "conditions": [
    {"key": "damage_type", "op": ":=:", "val": "ranged"}
  ],
  "actions": [
    {"type": "cancel_event"},
    {"type": "teleport", "params": {"target": "self", "mode": "offset", "offset_x": "0.5"}},
    {"type": "play_anim", "params": {"mode": "INSTANT", "anim": "animation.flash1", "auto_wait": "true"}},
    {"type": "random", "params": {"chance": "0.3", "skip": "2"}},
    {"type": "play_anim", "params": {"mode": "INSTANT", "anim": "animation.Mock1", "auto_wait": "true"}}
  ]
}
```

### 弹反（持盾反制远程 + 力量II）

```json
{
  "id": 2,
  "name": "预设-弹反",
  "enabled": true,
  "eventId": "maid_attack",
  "chance": 0.15,
  "cooldown": 100,
  "priority": 75,
  "matchMode": "ALL",
  "conditions": [
    {"key": "damage_type", "op": ":=:", "val": "ranged"},
    {"key": "maid_has_shield"}
  ],
  "actions": [
    {"type": "cancel_event"},
    {"type": "play_anim", "params": {"mode": "INSTANT", "anim": "parry", "auto_wait": "true"}},
    {"type": "apply_effect", "params": {"effect_id": "minecraft:strength", "duration": "200", "amplifier": "1", "target": "self"}}
  ]
}
```
""";

    /**
     * 生成全部介绍文档（含规则模板）。
     *
     * @param introduceDir 输出目录（如 config/littlemaidmoreaction/introduce/）
     */
    public static void generateAll(Path introduceDir) {
        try {
            Files.createDirectories(introduceDir);
            generateEvents(introduceDir.resolve("events.md"));
            generateConditions(introduceDir.resolve("conditions.md"));
            generateActions(introduceDir.resolve("actions.md"));
            writeTemplate(introduceDir.resolve("rule-template.md"));
            LittleMaidMoreAction.LOGGER.info("[DocGenerator] 文档已生成到 {}", introduceDir);
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[DocGenerator] 生成失败", e);
        }
    }

    /** 写入静态规则模板（内容为手写常量，不依赖运行时注册表） */
    private static void writeTemplate(Path out) throws IOException {
        if (Files.exists(out)) return; // 不覆盖用户修改的模板
        Files.writeString(out, RULE_TEMPLATE);
    }

    // ═══════════════════════════════════════════════════════════
    // 事件文档
    // ═══════════════════════════════════════════════════════════

    private static void generateEvents(Path out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# 触发事件一览\n\n");
        sb.append("> 此文件由 DocGenerator 自动生成，与 RuleEvent 枚举保持一致。\n\n");
        sb.append("共 **").append(RuleEvent.values().length).append("** 种事件。\n\n");

        // 按分类分组
        Map<String, List<RuleEvent>> groups = new LinkedHashMap<>();
        groups.put("TLM 战斗", List.of(
            RuleEvent.MAID_ATTACK, RuleEvent.MAID_HURT, RuleEvent.MAID_DAMAGE,
            RuleEvent.MAID_HURT_TARGET_PRE, RuleEvent.MAID_HURT_TARGET_POST, RuleEvent.MAID_DEATH));
        groups.put("TLM 交互", List.of(
            RuleEvent.MAID_INTERACT, RuleEvent.MAID_TAMED, RuleEvent.MAID_EQUIP));
        groups.put("TLM 拾取", List.of(
            RuleEvent.MAID_PICKUP_ITEM_PRE, RuleEvent.MAID_PICKUP_ITEM_POST,
            RuleEvent.MAID_PICKUP_XP, RuleEvent.MAID_PICKUP_ARROW, RuleEvent.MAID_PICKUP_POWER));
        groups.put("TLM 状态", List.of(
            RuleEvent.MAID_FAVOR_CHANGE, RuleEvent.MAID_TASK_ENABLE,
            RuleEvent.MAID_AFTER_EAT, RuleEvent.MAID_PLAY_SOUND, RuleEvent.MAID_TYPE_NAME));
        groups.put("TLM 装备/物品", List.of(
            RuleEvent.MAID_BACKPACK_CHANGE, RuleEvent.MAID_BAUBLE_CHANGE, RuleEvent.MAID_FISHED));
        groups.put("TLM 转换", List.of(
            RuleEvent.MAID_TOMBSTONE, RuleEvent.MAID_CONVERT));
        groups.put("Forge 事件", List.of(
            RuleEvent.LIVING_FALL, RuleEvent.LIVING_KNOCKBACK, RuleEvent.LIVING_HEAL,
            RuleEvent.PROJECTILE_IMPACT));

        for (var entry : groups.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");
            sb.append("| eventId | 说明 | 可取消 | Forge 事件类 |\n");
            sb.append("|---------|------|--------|-------------|\n");
            for (RuleEvent ev : entry.getValue()) {
                sb.append("| `").append(ev.getEventId()).append("` | ")
                  .append(ev.getDisplayName()).append(" | ")
                  .append(ev.isCancellable() ? "✅" : "—").append(" | ")
                  .append("`").append(ev.getEventClass().getSimpleName()).append("` |\n");
            }
            sb.append('\n');
        }

        sb.append("## 伤害链\n\n");
        sb.append("```\n");
        sb.append("女仆打人: maid_hurt_target_pre → 伤害 → maid_hurt_target_post\n");
        sb.append("女仆被打: maid_attack → maid_hurt → maid_damage → maid_death\n");
        sb.append("```\n");

        Files.writeString(out, sb.toString());
    }

    // ═══════════════════════════════════════════════════════════
    // 条件文档
    // ═══════════════════════════════════════════════════════════

    private static void generateConditions(Path out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# 条件 Key 一览\n\n");
        sb.append("> 此文件由 DocGenerator 自动生成，与 @RuleCondition 注册表保持一致。\n\n");
        sb.append("共 **").append(ConditionRegistry.size()).append("** 个条件。\n\n");

        Map<ConditionCategory, List<ICondition>> grouped = ConditionRegistry.getGroupedByCategory();
        for (var entry : grouped.entrySet()) {
            ConditionCategory cat = entry.getKey();
            sb.append("## ").append(catDisplay(cat)).append("\n\n");
            sb.append("| key | 说明 | 值类型 | 静态 | 参数 |\n");
            sb.append("|-----|------|--------|------|------|\n");
            for (ICondition cond : entry.getValue()) {
                sb.append("| `").append(cond.key()).append("` | ")
                  .append(cond.displayName()).append(" | ")
                  .append(vtDisplay(cond.valueType())).append(" | ")
                  .append(cond.isStatic() ? "✅" : "—").append(" | ")
                  .append(paramsDisplay(cond.params())).append(" |\n");
            }
            sb.append('\n');
        }

        sb.append("## 值类型说明\n\n");
        sb.append("| 类型 | 可用操作符 |\n");
        sb.append("|------|-----------|\n");
        sb.append("| BOOL | 无操作符，选中即满足 |\n");
        sb.append("| NUM | `:=:` `<:` `:>:` `:!=:` `:>=:` `:<=:` |\n");
        sb.append("| STR | `:=:` `:!=:` `:contains:` `:regex:` `:in:` |\n\n");

        sb.append("## 操作符说明\n\n");
        sb.append("| Token | 含义 |\n");
        sb.append("|-------|------|\n");
        sb.append("| `:=:` | 智能相等（先试数值近似 1e-9，失败则字符串 equals） |\n");
        sb.append("| `:<:` | 小于 |\n");
        sb.append("| `:>:` | 大于 |\n");
        sb.append("| `:!=:` | 不等于 |\n");
        sb.append("| `:>=:` | 大于等于 |\n");
        sb.append("| `:<=:` | 小于等于 |\n");
        sb.append("| `:contains:` | 子串包含 |\n");
        sb.append("| `:regex:` | 正则匹配 (Pattern.find) |\n");
        sb.append("| `:in:` | 逗号分隔白名单，trim 后 equals 任一项 |\n\n");

        sb.append("## 动态 Key\n\n");
        sb.append("- `data:<key>` — 读取女仆 PersistentData\n");
        sb.append("- `$<key>` — 引用另一条件 key 的值（val 字段中以 `$` 开头）\n");

        Files.writeString(out, sb.toString());
    }

    // ═══════════════════════════════════════════════════════════
    // 动作文档
    // ═══════════════════════════════════════════════════════════

    private static void generateActions(Path out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# 动作类型一览\n\n");
        sb.append("> 此文件由 DocGenerator 自动生成，与 @RuleAction 注册表保持一致。\n\n");
        sb.append("共 **").append(ActionRegistry.size()).append("** 种动作。\n\n");

        Map<ActionCategory, List<IAction>> grouped = ActionRegistry.getGroupedByCategory();
        for (var entry : grouped.entrySet()) {
            ActionCategory cat = entry.getKey();
            sb.append("## ").append(acDisplay(cat)).append("\n\n");
            sb.append("| id | 说明 | 异步 | 取消事件 | 改状态 | 参数 |\n");
            sb.append("|----|------|------|----------|--------|------|\n");
            for (IAction action : entry.getValue()) {
                sb.append("| `").append(action.id()).append("` | ")
                  .append(action.displayName()).append(" | ")
                  .append(action.isAsync() ? "✅" : "—").append(" | ")
                  .append(action.cancelsEvent() ? "✅" : "—").append(" | ")
                  .append(action.isGameStateMutating() ? "✅" : "—").append(" | ")
                  .append(paramsDisplay(action.params())).append(" |\n");
            }
            sb.append('\n');
        }

        sb.append("## 流程控制动作\n\n");
        sb.append("| id | 说明 | 参数 |\n");
        sb.append("|----|------|------|\n");
        sb.append("| `cancel_event` | 取消当前触发事件 | 无 |\n");
        sb.append("| `wait` | 等待固定 tick | `ticks`: 等待刻数 (默认20) |\n");
        sb.append("| `wait_anim` | 等待上一动画播完 | `anim_name`: 动画名 (可选，默认取 play_anim 存储的) |\n");
        sb.append("| `repeat` | 循环跳回 | `count`: 循环次数 (默认3) |\n");
        sb.append("| `random` | 概率分支 | `chance`: 概率0-1 (默认0.5), `skip`: 未通过跳过组数 (默认1) |\n\n");

        sb.append("## $表达式\n\n");
        sb.append("动作参数值支持 `$<key>` 表达式引用条件值：\n");
        sb.append("- `$health_ratio` → 目标生命比例\n");
        sb.append("- `$health_ratio * 2` → 取值后 ×2\n");
        sb.append("- `$favorability + 100` → 取值后 +100\n");

        Files.writeString(out, sb.toString());
    }

    // ═══════════════════════════════════════════════════════════
    // 显示工具
    // ═══════════════════════════════════════════════════════════

    private static String catDisplay(ConditionCategory cat) {
        return switch (cat) {
            case MAID -> "女仆状态";
            case TARGET -> "目标相关";
            case WORLD -> "世界/环境";
            case OWNER -> "主人相关";
            case META -> "元条件";
        };
    }

    private static String acDisplay(ActionCategory cat) {
        return switch (cat) {
            case COMBAT -> "战斗";
            case MOVEMENT -> "移动";
            case VISUAL -> "视觉特效";
            case EFFECT -> "药水效果";
            case MAID -> "女仆控制";
            case MAID_EXT -> "女仆扩展";
            case WORLD -> "世界交互";
            case ITEM -> "物品";
            case CONTROL -> "流程控制";
            case MESSAGE -> "消息";
        };
    }

    private static String vtDisplay(ConditionValueType vt) {
        return switch (vt) {
            case BOOL -> "布尔";
            case NUM -> "数值";
            case STR -> "字符串";
        };
    }

    /** 将 TypedParam 列表渲染为简短描述字符串 */
    private static String paramsDisplay(List<TypedParam<?>> params) {
        if (params == null || params.isEmpty()) return "无";
        List<String> parts = new ArrayList<>();
        for (TypedParam<?> p : params) {
            String typeStr = p.accept(TYPE_VISITOR);
            String def = String.valueOf(p.defaultValue());
            parts.add(p.name() + ":" + typeStr + (def.isEmpty() ? "" : "=" + def));
        }
        return String.join(", ", parts);
    }

    /** Visitor: 将 TypedParam 子类型映射为可读字符串 */
    private static final TypedParam.TypedParamVisitor<String> TYPE_VISITOR =
        new TypedParam.TypedParamVisitor<>() {
            @Override public String visit(TypedParam.IntParam p) { return "int"; }
            @Override public String visit(TypedParam.DoubleParam p) { return "double"; }
            @Override public String visit(TypedParam.BoolParam p) { return "bool"; }
            @Override public String visit(TypedParam.StringParam p) { return "str"; }
            @Override public String visit(TypedParam.SelectParam p) {
                return "select[" + String.join("|", p.options()) + "]";
            }
        };
}
