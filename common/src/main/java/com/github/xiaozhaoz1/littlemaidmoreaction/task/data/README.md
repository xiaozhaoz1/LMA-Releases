# task/data — 任务数据层 (NBT 读写唯一入口)

**作用**: 所有 per-maid / per-task 的持久化与运行态数据的**唯一读写入口** (NBT 挂在女仆 `PersistentData`)。
分层契约: task/data(必读, 129+ 文件) → task/data/README.md(必读) → task/runtime/README.md(运行态) → task/pipeline/README.md(流程) → task/TASK-ARCHITECTURE.md(架构)。

## 一、组成 (10 类)
| 类 | 职责 | 引用面 |
|---|---|---|
`MaidData` | **NBT 根入口** (`root(maid)`) + per-task 配置 `cfg` / **`cfgOrCreate`** | 131 |
`TaskKeys` | **所有键名字符串常量** (57 个 public, 含 `CFG_PREFIX`) | 155 |
`DataKey` | 类型化键描述 + `CLEAR_ALL_KEYS` (任务终结要清的键清单) | 53 |
`FlowTaskData` | 流程态读写 (`task/state/tick/counter/maxCount` + `clearAll`) | 76 |
`TaskMetaData` | 元信息 (目标 `target` / GUI 初始化 / TLM 开关标记) | 35 |
`TaskToggle` | 任务可见性开关 (TLM 任务栏显示/隐藏, `task_toggles.json`) | 60 |
`MaidKey` | 女仆键位/绑定数据 | 14 |
`TaskDataSchema` | 数据键 schema (校验/迁移) | 8 |
`PipelineContext` | 传给 `validate(...)` 的上下文 (target/count) | 101 |
`PipelineResult` | `validate` 返回值 (`ok/failed` + 气泡文案) | 104 |

## 二、连接链与纪律
```
读写:  任意层 → MaidData.root(maid) → NBT (键必须取自 TaskKeys/DataKey, 不要字面量)
写配置: pipelineConfig(maid) = MaidData.cfgOrCreate(maid, taskType)   ← 必须用 cfgOrCreate!
清理:   任务终结 (complete/fail/cancel) → TaskStateManager.clearAll → FlowTaskData.clearAll
        └ 按 DataKey.CLEAR_ALL_KEYS + 旧键兜底清 (见 clearAll 实现)
卸载:   女仆卸载/区块卸载 → MaidUnloadRegistry 注册的清理器 (参 task/runtime)
```
**三处刻意的"实体 id"例外** (历史原因, 不要照抄): `EntityScanCache.queryCache` · `FakePlayerManager.TASKS` · `ScanScheduler.ownerId`。
**其余一律**: per-maid 键 = **UUID**; per-区块键 = 维度级 `clearDimension`; per-玩家键 = 自带 sweep; 资源类键 = WeakHashMap。

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **`cfg` 写不落盘** | `MaidData.cfg(...)` 对**不存在的 key 返回临时 tag (非引用)** ⇒ 写它 = **静默丢**。写路径一律 `cfgOrCreate` (本轮修: 钟间隔改了不生效)。|
**B** | **键名散写** | 新键必须加到 `TaskKeys`(或 `DataKey`), 并评估是否进 `CLEAR_ALL_KEYS` — 否则任务终结后残留 (老存档"幽灵状态"多源于此)。|
**C** | **int id 键会串** | 实体 id 会复用 ⇒ per-maid 状态用 UUID; 三处例外有各自注释说明原因, 不要扩散。|
**D** | **schema 变更要迁移** | 改键的类型/含义 → 走 `TaskDataSchema` 迁移或写兼容读, 否则老存档崩/静默归零。|
