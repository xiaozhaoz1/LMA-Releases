# task/quest — 任务/成就树 (数据 + 布局 + 加载)

**作用**: "任务树/成就树"的**数据模型、加载与布局**: 从定义加载成树 → 读进度 → 算布局 → 供 GUI 渲染。
**依赖方向**: task/data (进度读) + resource/JSON; 被 `screen/LmaQuestScreen` 消费。

## 一、类明细 (4 类)
| 类 | 行数 | 职责 |
|---|---|---|
`QuestTreeLoader` | 162 | **加载器**: 定义 (JSON/资源) → `QuestNode` 树; 校验/兜底 |
`QuestTreeLayout` | 83 | **布局计算 (纯函数)**: 树 → 节点坐标 (可 JVM 测, 无 MC 依赖) |
`QuestNode` | 65 | 节点数据 (id/名称/前置/进度条件) |
`QuestProgressReader` | 35 | **进度读取** (从女仆数据/成就读已完成状态) |

## 二、连接链
```
定义 (资源) → QuestTreeLoader → QuestNode 树
进度: QuestProgressReader (读女仆数据/成就) → 节点完成态
布局: QuestTreeLayout (纯函数) → 坐标
渲染: screen/LmaQuestScreen (消费树+布局+进度)
```

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **布局保持纯函数** | `QuestTreeLayout` 不要引入 MC 类型 (现在无 MC 依赖, 可单测) — 渲染相关留在 Screen。|
**B** | **加载失败要降级** | 定义缺失/格式错 ⇒ 空树 + WARN, 不要让 GUI 崩 (参 `storage/README.md` 陷阱 D 同族)。|
**C** | **进度读取与任务系统解耦** | 进度来源可以是成就/数据键; 改动进度口径要同步 `QuestProgressReader` 与 GUI 展示, 避免"显示已完成但判定未完成"。|
