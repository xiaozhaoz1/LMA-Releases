# ai — AI 上下文 (供 Numen 等读取)

**作用**: 把女仆/世界/任务的状态**整理成 AI 可读的上下文** (只读快照), 供 AI 决策 (工具在 [compat/ai](../compat/README.md) §五)。
**依赖方向**: 原版 + TLM + task/data (读); 不写世界 (执行走 `compat/ai/tool/*`)。

## 一、类明细 (6 类)
| 类 | 行数 | 提供什么上下文 |
|---|---|---|
`context/LmaDetailContext` | 266 | **女仆详细上下文** (最大: 状态/背包/装备/周围) |
`scanner/NearbyBlockScanner` | 252 | **附近方块扫描** (供 AI "看"世界; 有界扫描, 注意性能) |
`context/LmaEnvSenseContext` | 115 | 环境感知上下文 (时间/天气/群系/温度) |
`context/MaidTaskContext` | 91 | **任务上下文**: 读 `FlowTaskData` + `pipeline.steps()` (展示当前任务与步骤) |
`context/OwnerFoodTracker` | 57 | 主人食物追踪 (喂食相关决策) |
`context/LmaBlocksContext` | 42 | 方块/物品词表上下文 (让 AI 知道可用方块名) |

## 二、连接链 (与 compat/ai 的分工)
```
【读】ai/context/*  ← 只读: 把状态整理成文本/结构 → 交给 AI (Numen)
【写】compat/ai/tool/* ← 执行: AI 决策后调用工具 (受 AiControlGate 权限门控)
⇒ 分工: 本包"告诉 AI 现状", compat/ai "让 AI 动手"。
```

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **上下文是只读快照** | 本包不得修改世界/女仆状态 (执行一律走 `compat/ai/tool`)。|
**B** | **上下文要控制体量** | AI 上下文有 token 成本 ⇒ 只给决策必需的字段; 大列表要截断/摘要 (`MaidTaskContext` 读 `steps()` 就是"摘要"先例)。|
**C** | **扫描注意性能** | `NearbyBlockScanner` 有界扫描, 不要每 tick 全范围扫 (参 `vanilla/README.md` §三-D)。|
**D** | **门控**: 只有装了 Numen 才有 AI 消费方 | 本包可常驻, 但**入口**应判 `NumenCompat.isInstalled()` (参 `compat/README.md` §一)。|
