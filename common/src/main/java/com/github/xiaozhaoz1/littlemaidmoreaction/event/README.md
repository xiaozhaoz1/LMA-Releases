# event — 游戏事件层 (自动注册的 handler)

**作用**: 订阅 **TLM / 原版**游戏事件, 把外部动作翻译成 LMA 行为 (任务提交/绑定/成就/闲聊触发等)。
**依赖方向**: TLM API + 原版事件 + `task/runtime`(Dispatcher) + `task/data`; **被 FML 用注解自动注册**, 不被业务层调用。

> ⚠ **本包最容易踩空**: 类都是"**注解自动注册**"型 —— 静态扫描看不到它的"调用方"。

## 一、明细表 (事件 → handler → 干什么)
| 类 | 订阅事件 | 注册 | 干什么 |
|---|---|---|---|
`ArmTransferSetupHandler` | `RightClickBlock`(原版, 客户端) + `InteractMaidEvent`(TLM, 服务端) | 自动 @GAME | 木棍/绑定物右键女仆 → **设置搬运任务** (客户端并行发包, 服务端消费本次右键) |
`BlockInteractSetupHandler` | 同上 | 自动 @GAME | 右键流程 → **绑定方块交互目标** (`pos`) |
`VoidExcavationSetupHandler` | 同上 | 自动 @GAME | 右键流程 → **设置/重启挖空置域任务** |
`FeedMaidWatermelonHandler` | `InteractMaidEvent` | 自动 @GAME | 喂女仆西瓜 (回血/好感) |
`FriedChickenComboHandler` | `AdvancementEvent.Finish` | 自动 @GAME | 炸鸡成就 → 连击/额外效果 |
`GomokuInstantWinHandler` | `RightClickBlock` | 自动 @GAME | 五子棋即时胜利检测 |
`MaidCodexKillListener` | `LivingDeathEvent` | 自动 @GAME | 击杀 → 图鉴解锁计数 |
`MaidCreeperAvoidHandler` | `EntityJoinLevelEvent` | 自动 @GAME | 女仆避苦力怕 (上线时贴行为) |
`MaidDamageListener` | `MaidDamageEvent`(TLM) | 自动 @GAME | 女仆受伤 → 自救状态记录 (`SelfRescueState`) |
`StickBindUtil` | — **(工具类, 非 handler)** | 工具 (被平台入口引用) | 木棍/标记物判定 + 任务类型校验 + 容器判定; **`isMarkItem`/`isBindItem` 是"配置驱动的判定"** (本会话已改为由客户端入口注入给 `DebugSelectionCoordinator`, 断掉 vanilla→event 越层) |

## 二、连接链
```
【右键三件套 (最常用)】
客户端 RightClickBlock → (本地判定: 手持物/潜行) → 发包 ─┐
服务端 InteractMaidEvent → 同一 handler 的服务端分支 ←──┘  → TaskDispatcher.submit(maid, task, target, count)
   ↑ 两侧**成对**: 客户端判"能不能点", 服务端判"点了做什么" — 改一个必须同步另一个
【被动/信息类】事件 → 直接写数据 (图鉴计数/伤害状态) 或调 service
【工具类】StickBindUtil 被平台入口 (client/forge/neoforge) 与本包各 SetupHandler 共用
```

## 三、已知陷阱 (每条附事故)
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **"零引用"≠死代码** | 本包全靠 `@EventBusSubscriber` 自动注册 ⇒ 静态扫描看不到调用方。**死代码清理时必须排除本包** (实测曾误列 `FeedMaidWatermelonHandler`/`MaidCreeperAvoidHandler`/`VoidExcavationSetupHandler`, 复核后保留)。|
**B** | **neoforge 的 GAME 总线自动订阅曾实测失效** | 关键 GAME 监听需在**平台入口构造器手动注册** (`NeoForge.EVENT_BUS.addListener`); 新增 handler 必须**双平台各跑一次**验证真的被调用 (加临时日志)。|
**C** | **客户端/服务端成对** | `RightClickBlock`(客户端) + `InteractMaidEvent`(服务端) 是**一对**, 只改一侧 = "点了没反应"或"重复提交"。|
**D** | **副作用要可重放安全** | 事件 handler 可能因区块加载/重连重复触发 ⇒ 写数据前先判状态 (如恢复路径按 `FlowTaskData` 状态决定续跑/重提交)。|
**E** | **不要在 handler 里做重活** | 遍历世界/大批方块操作应交给 service/pipeline (GMPM 有节拍与看门狗), handler 只做"翻译 + 提交"。|
