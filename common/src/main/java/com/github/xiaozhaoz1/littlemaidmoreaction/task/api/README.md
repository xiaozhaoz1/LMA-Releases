# task/api — 管线接口契约 (改它影响全部管线)

**作用**: 定义"任务/管线"的**契约**: 管线接口、可配置契约、注册中心、被动信号接口、屏幕工厂、UID 规约。
**依赖方向**: 仅 task/data + config + 原版; **被**所有管线实现、`task/runtime`、`adapter`、外部 (`api/LMAT`) 依赖。

## 一、契约表 (7 类)
| 契约 | 关键方法 | 语义 / 注意 |
|---|---|---|
**`TaskPipeline`** | `taskType()` `steps()` `validate()` `tick()` `isTargetBlock()` `usesBrainNavigation()` `executeInterval()` `workPointTask()` `isLongRunning()` `priority()` `onCleanup()` | 管线主接口。默认实现即"最小可用管线"; 覆写项决定**驱动方式**(见下表) |
**`TaskConfigurable`** | `pipelineConfig(maid)` `handleConfigAction(...)` `clearPipelineConfig(maid)` `getConfigGuiProvider(maid)` | **per-task 配置契约**。`pipelineConfig` 必须返回**落盘引用** (`MaidData.cfgOrCreate`); 默认 `handleConfigAction` 处理 setInt/toggle/remove/setList |
**`TaskRegistry`** | `get(type)` `validate(...)` `register(...)` `registerPassive(...)` | **注册中心**: manifest 驱动注册 + 启动期 fail-fast (被动注册缺失/驱动漂移/纯触发缺失); 也是运行期取管线的唯一入口 |
**`TaskSignalListener`** / **`PassiveSignalSkeleton`** | `onSignal(...)` | 被动任务的**信号接口** (事件/信号触发型, 不挂 TLM 任务栏) |
**`TaskConfigGuiFactory`** | `forTask(maid, taskType)` / `createMenuProvider(...)` | 任务设置屏**工厂**: 按 taskType 返回对应 ConfigScreen/Menu (无专属屏则回退通用屏) |
**`TaskTypeUid`** | `sanitize(taskType)` | **UID 规约**: `lma:task/<sanitized>` — TLM 注册/存档识别都用它 |

## 二、驱动相关的覆写项 (改这里 = 改某类任务的行为)
| 覆写 | 效果 |
|---|---|
`isLongRunning()=true` | GMPM 心跳续命 + **看门狗超时保护** (工作站族恒 true) |
`usesBrainNavigation()` | true(默认)= 由 Brain 搜索/导航; **false = LMA 自导航** (连锁采集用: 防 Brain 抢写 WALK_TARGET) |
`executeInterval()` | 节拍 (工作站默认 30t) — 与门一起决定"多久干一次" |
`workPointTask()` | ⚠ **只是 TLM 骑乘调度语义** ("工作点任务骑乘中不脱离坐骑"), **不是导航** (导航见 `task/pipeline/README.md` §2) |
`isTargetBlock()` | 工作站类目标谓词 (被 Brain 搜索 + 工作站门用) |
`priority()` | 多任务并存时的优先级 (被动/主动分派用) |
`steps()` | **用户可见粗粒度步骤** (任务树/命令/AI 上下文展示) — 与内部状态枚举**不同层**, 改相位要手动同步 (#291) |

## 三、连接链
```
管线实现 (task/pipeline|compat/task) ──implements──> TaskPipeline (+ TaskConfigurable)
   → TaskRegistry (manifest 注册, 启动 fail-fast)
   → GMPM 每 tick 调 tick() / TaskDispatcher 管生命周期
外部/KubeJS → api/LMAT (门面) → TaskRegistry
任务设置页 → TaskConfigGuiFactory → task/gui/XConfigScreen → 包 → TaskConfigurable.handleConfigAction
                                          → pipelineConfig = MaidData.cfgOrCreate (写才落盘)
```

## 四、已知陷阱 (每条附事故)
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **`pipelineConfig` 必须落盘** | 用 `MaidData.cfg(...)` 拿到的**不存在键 = 临时 tag**, 写它静默丢 (钟间隔事故) ⇒ 统一入口已改 `cfgOrCreate`; 新写路径也必须用 `*OrCreate`。|
**B** | **`validate` 与 `tick` 必须一致** | `validate` 允许的状态, 运行时必须能处理 (熔炉"空 target"两边不一致 ⇒ 走到炉子不烧)。|
**C** | **`workPointTask()` ≠ 导航** | 它只影响 TLM 骑乘调度; 让女仆"走过去"靠工作站门/Brain (见 pipeline README)。|
**D** | **`steps()` 不是状态机** | 与状态枚举是两层词汇 (粗粒度展示 vs 细粒度相位), 无自动校验 ⇒ 改状态必须同步 `steps()`。|
**E** | **注册期门控 vs 运行期校验** | 依赖外部 mod 的任务在**注册期**就门控 (TaskRegistry), `validate` 只是冗余防御 (任务在而依赖缺 ⇒ 提交必须失败, 不得静默放行)。|
**F** | **接口新增 default 方法要保守** | 外部 (KubeJS/其它 mod) 可能已实现本接口 ⇒ 新方法必须给 default 实现, 否则破坏兼容。|
