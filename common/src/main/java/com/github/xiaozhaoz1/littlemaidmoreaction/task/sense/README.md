# task/sense — 环境感知层 (信号生产侧)

**作用**: 把世界/结构/环境**转换成信号**, 供被动管线消费。**⚠ 与 `task/pipeline/sense` 是两个包**:
- **本包 (`task/sense`) = 信号生产** (扫描/规则/边沿/预算/缓存/结构感知/广播)
- `task/pipeline/sense` = **信号消费** (7 个被动管线: 哈气/点火把/自救/牛奶/探险图…)

**依赖方向**: task/data + vanilla/* + api; 被 `task/pipeline/sense` 与被动派发链消费。

## 一、类明细 (14 类)
| 类 | 行数 | 职责 |
|---|---|---|
`StructureSense` | 562 | **结构感知核心** (最大: 结构识别/蓝图匹配 → 结构信号) |
`EnvSenseBroadcaster` | 419 | **信号广播器**: 每拍评估规则 → 产生/广播信号给被动管线 (感知总调度) |
`LookAroundGrid` | 138 | 环顾网格 (以女仆为中心采样的空间网格) |
`WorldInfoCache` | 106 | 世界信息缓存 (群系/时间/光照等, 避免重复查询) |
`BlueprintReader` | 105 | 蓝图读取 (结构蓝图的解析) |
`Signals` | 65 | 信号常量/构造工具 |
`EnvEdgeDetector` | 64 | **边沿检测** (状态"变"才发信号, 防每拍刷) |
`PassiveSenseRegistration` | 64 | **被动感知注册**: 注册纯触发型被动 (structure_sense/festival/rare_biome) + Drive 声明配套 |
`EnvSnapshot` | 45 | 环境快照 (**纯数据 record**: 某一刻的环境取值集合) |
`EnvSignal` | 35 | 信号枚举 (有哪些环境信号) |
`EnvSenseBudget` | 28 | **每拍预算** (限制每拍感知计算量, 防卡顿) |
`EnvRules` | 27 | 环境规则 (阈值/条件表) |
`EnvScanner` | 23 | 环境扫描入口 |
`DailyDedup` | 22 | 每日去重 (同类信号一天只发一次) |

## 二、连接链
```
每拍: EnvSenseBudget (限流) → EnvScanner/LookAroundGrid/WorldInfoCache (采样+缓存)
      → EnvRules/EnvEdgeDetector (判定: 是否"变化") → EnvSignal/EnvSnapshot
      → EnvSenseBroadcaster (广播) → 被动管线 (task/pipeline/sense/*)
注册: PassiveSenseRegistration (纯触发型被动) / TaskRegistryManifest.PASSIVE (走 GMPM 的被动)
```

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **别把本包当 `pipeline/sense`** | 两个包职责相反 (生产 vs 消费); 改被动管线行为去 `pipeline/sense`, 改"信号怎么来"在本包。|
**B** | **必须走预算与边沿** | 感知是每拍跑的 ⇒ 新增扫描/判定必须过 `EnvSenseBudget` 限流, 且用 `EnvEdgeDetector` 只在"变化"时发信号 (否则被动任务被刷屏触发)。|
**C** | **缓存要有主人** | `WorldInfoCache` 等缓存要能清 (per-maid → UUID + `MaidUnloadRegistry`; 世界级 → 维度清理), 参 `task/data/README.md` 缓存纪律。|
**D** | **信号要幂等/可重放** | 同一信号重复到达不应产生副作用叠加 (被动管线侧也要判状态)。|
