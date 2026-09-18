# task/gui — 任务配置界面 (per-task 配置屏)

**作用**: 每个可配置任务的**设置界面 + 菜单**。任务侧在 TLM 任务设置页点"设置" → 管线 `getConfigGuiProvider(maid)`
→ `TaskConfigGuiFactory` 取本包对应的 Screen/Menu → 用户改动经**包**发到服务端 → 落到该女仆的 per-task 配置。

**依赖方向**: task/api + task/data + screen(通用屏基类) + network(配置包); **不含业务逻辑** (业务在 task/pipeline + task/service)。

> **维护约定**: 新增配置项/新增任务设置屏必须同步下表 (屏 ↔ 任务 ↔ 配置键 ↔ 打开入口)。字段可由
> `grep -oE '"[a-z_]+"' task/gui/*Screen.java` 机械核对。

---

## 一、屏 ↔ 任务 ↔ 配置键

| Screen | Menu | 服务任务 | 配置键 (pipelineConfig) | 打开入口 |
|---|---|---|---|---|
| `BellRingConfigScreen` | `BellRingConfigMenu` | `bell_ring` | `ring_interval` (空则全局 `active.ring_interval_ticks`, 默认 30t) | 管线 `getConfigGuiProvider` → 工厂 |
| `BlockInteractConfigScreen` | `BlockInteractConfigMenu` | `block_interact` | `pos` (绑定方块) · `timer` · `interval` | 同上 |
| `CraftChainConfigScreen` | `CraftChainConfigMenu` | `craft_chain` | `max_products` · `target` 等 | 同上 |
| `AiControlConfigScreen` | `AiControlConfigMenu` | `ai_control` | `llm_provider` · `voice` | 同上 |
| `VoidExcavationConfigScreen` | `VoidExcavationConfigMenu` | `void_excavation` | `size` (只读显示) · `destroy_list` · `no_pathfind` · `min_y` (**空=移除键 ⇒ 自动检测基岩层**; 有值 ⇒ 挖到该层停; **必须 < 标记层 `start.y`** — 屏端拒存 + 管线自愈, v79.66.1) | 同上 |
| `DamFillConfigScreen` | `DamFillConfigMenu` | `dam_fill` | `input` · `drain_enabled` | 同上 |
| `ItemListConfigScreen` | `ItemListConfigMenu` | 多任务共用 | 黑白名单列表 (`key` + `value`) | 同上 |
| `PassiveToggleConfigScreen` | `PassiveToggleConfigMenu` | 被动任务族 | `enabled` (逐任务开关) | 同上 |
| `LmaTaskConfigScreen` | — | **通用基类** | 提供 `sendSetInt/sendAction/sendRemove` 等发送原语 | (被上面各屏继承) |

**独立容器屏 (不在此包)**: 便携装配 `MaidAssemblyScreen/Menu` (compat/create/task/assembly) · 防御塔 `DefenseTowerScreen/Menu` (defense) · 主配置 `LMAConfigScreen` (screen/)。

---

## 二、配置读写链路 (改配置相关代码前必读)

```
Screen 用户操作
  → LmaTaskConfigScreen.sendSetInt(key, value)          ← 客户端
  → TaskConfigActionPacket.send(maidId, taskType, action, payload)
  → 服务端 TaskConfigurable.handleConfigAction(maid, action, payload)
       case ACTION_SET_INT  → cfg.putInt(key, value)
       case ACTION_TOGGLE   → cfg.putBoolean(key, !…)
       case ACTION_REMOVE   → cfg.remove(key)
       case ACTION_SET_LIST → cfg.put(key, ListTag)      (逗号分隔字符串)
  → 数据落在 MaidData.cfgOrCreate(maid, taskType) = NBT `lma_cfg_<taskType>`
  → 管线运行期读同一个 pipelineConfig(maid)
```

### ⚠ payload 字段类型契约 (v79.64.4 — 强制, 有守护测试)

**必须用基类助手**, 不要屏内手写 `sendAction(ACTION_*, payload)` ✗ — 客户端 payload 的字段类型
必须与服务端 `handleConfigAction` 的**解析方式**一致, 否则**静默出错**(不是报错):

| 动作 | payload 必须 | 服务端解析 | 写错会怎样 (实测) |
|---|---|---|---|
| `ACTION_SET_INT` | `key`(String) + **`value`(int)** | `cfg.putInt(key, payload.getInt("value"))` | 传 String ⇒ **`getInt` 读得 0** ⇒ 配置被静默写成 0 ✗ (错题 #350: 空置域"保存最低高度"存成 0) |
| `ACTION_SET_LIST` | `key` + `value`(逗号分隔 **String**) | 拆分成 `ListTag` | — |
| `ACTION_SET_STRING` | `key` + `value`(String) | `cfg.putString` | — |
| `ACTION_TOGGLE` / `ACTION_REMOVE` | 仅 `key` | 取反 / 删除 | — |

⇒ 一律用 `sendSetInt / sendSetList / sendSetString / sendToggle / sendRemove` ✓;
**守护测试** `GuiActionPayloadGuardTest` 会机械拦住"屏内直接引用通用动作常量" ✗。
任务**自定义**动作 (常量 ≥16, 定义在各自管线) 不受此限, 但同样必须保证 payload 类型对称 ✓。

**⚠ 三个必须知道的坑**:

1. **写入必须 `cfgOrCreate`, 不能 `cfg`**
   `MaidData.cfg(...)` 对**不存在的 key 返回临时 CompoundTag (非引用)** ⇒ 往里写 = **静默丢**。
   历史: 2026-08-16 修过"绑定不生效", 但**统一入口 `TaskConfigurable.pipelineConfig` 当时仍用 `cfg`** ⇒
   直到 v79.63 才改走 `cfgOrCreate` (**症状: 敲钟间隔改了不生效, 退回全局默认 30t**)。
   ⇒ 新增任何"写配置"路径, **先确认拿到的是落盘引用**。
2. **配置键名是跨层契约**: Screen 写 `key` 字符串、管线读 `KEY_*` 常量、README 记名 —— 三处必须一致;
   改键名 = 老存档配置静默失效 (需迁移或保留旧键读取)。
3. **全局配置 vs 单女仆配置**: 无独立屏的任务只有全局配置 (`config/ActiveTaskConfig`); 有屏的任务"空则回落全局"
   (例 `ring_interval` 为空 → `active.ring_interval_ticks`) ⇒ **用户改全局也会影响未单独设置的女仆**。

---

## 三、看屏/加屏的流程 (checklist)

1. **照抄最像的屏** (最简单: `BellRingConfigScreen` 单值 int + 开关)。
2. **菜单**继承 `LmaTaskConfigMenu`; 屏继承 `LmaTaskConfigScreen` (自带 `sendSetInt/sendAction/...`)。
3. **管线侧**补 `getConfigGuiProvider(maid)` → `TaskConfigGuiFactory.forTask(...)` 分支。
4. **注册**: 菜单类型注册在两平台入口 (`LittleMaidMoreAction.MENU_TYPES` / `LmaNeoForgeEntry.MENU_TYPES`), 屏注册在**客户端入口** (`LmaForgeClientEntry` / `LmaNeoForgeClientEntry`), **直取注册器** (`…_MENU.get()`), **不要**经 `LmaMenus` 取值 (时序); `LmaMenus` 仅供菜单构造时 `.get()` 取类型。
5. **同步文档**: 本 README 表 + 对应 `task/pipeline/README.md` 的"配置键"列。
6. **关闭/移除语义**: 用 `ACTION_REMOVE` 而非写默认值 (才能"回落全局")。
   GUI 坐标/尺寸另有铁律: **改 GUI 必须给出详细坐标** (用户裁定)。
