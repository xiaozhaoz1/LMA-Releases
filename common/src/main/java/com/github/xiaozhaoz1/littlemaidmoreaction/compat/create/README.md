# compat/create — Create (机械动力) 家族

**作用**: LMA 与 **Create** 的适配: 6 个女仆任务 (曲柄/发电/压块/搅拌/跑步带/便携装配) + 发电皮带方块 + 便携装配 GUI。
**依赖方向**: 允许 Create API + task/* + vanilla + api; **不得**被 vanilla/task 反向依赖。
**门控 (双条件, 与任务注册/界面注册/方块注册同条件)**: `CompatToggle.isModuleEnabled("create")` **且** `ModList.isLoaded("create")` —— 任一不满足 ⇒ 任务不注册、屏不注册、方块为 null。

## 一、类清单 (22 类, 按目录)
| 路径 | 行数 | 职责 |
|---|---|---|
### `block/` — 发电皮带方块
`MaidPowerBeltBlock` | 433 | 方块: 放置/交互/红石/朝向; 女仆站上去跑步发电 |
`MaidPowerBeltBlockEntity` | 442 | 方块实体: 动能输出 (Create kinetic network)、转速/RPM、状态同步 |
### `client/` `render/`
`CreateCompatClient` | 88 | **客户端注册**: 方块渲染器 (`EntityRenderersEvent`) + **便携装配屏** (`MenuScreens.register` / `RegisterMenuScreensEvent`, 双平台分支) |
`MaidPowerBeltRenderer` | 120 | 方块实体渲染 |
### `task/` — 5 个"机器任务" (每个 = 管线 + 服务)
| 任务 | 管线 | 服务 | 干什么 |
|---|---|---|---|
`crank` | CrankPipeline 114 | CrankService 79 | **转曲柄**: 走到曲柄 → 转动手柄驱动机械 |
`mix` | MixPipeline 111 | MixService 136 | **搅拌**: 找搅拌盆 → 按 Mixing 配方作业 |
`power` | PowerPipeline 123 | PowerService 122 | **发电**: 驱动发电机 |
`press` | PressPipeline 121 | PressService 260 | **压块**: 按 Pressing 配方作业 (服务最重) |
`running_belt` | RunningBeltPipeline 218 | RunningBeltService 203 | **跑步带**: 站上传送带跑步发电 (与 `block/` 的皮带方块配套) |
### `task/assembly/` — 便携装配 (8 类, 唯一带**独立 GUI** 的任务)
| 类 | 行数 | 职责 |
|---|---|---|
`MaidAssemblyTask` | 76 | TLM 任务适配 (createBrainTasks 挂 `LmaFlowCoordinationBehavior`) |
`MaidAssemblyPipeline` | 356 | **相位机** (IDLE/TRY_START/ADVANCE/STRIKE/EAT_RESET) + **硬门控 `hasFood`** + 装配推进 |
`MaidAssemblyService` | 238 | 机器识别 (`MachineKind`)、配方查找 (Pressing/Mixing/**Deployer**)、执行与产出 |
`MaidAssemblyInventory` | 198 | **装配容器** (ItemStackHandler): 槽 0-7 机器 / 8 材料 / 9 中间 / 10-11 产出; 服务端共享单例 + **客户端独立实例** (女仆反查失败也能开屏) |
`MaidAssemblyMenu` | 153 | 菜单 (12 槽 + 玩家背包 + DataSlot 材料锁同步) |
`MaidAssemblyScreen` | 142 | 装配屏 (Simi 屏: `setWindowSize`; 拖拽/槽位渲染) |
`MaidAssemblyNetwork` | 65 | MenuProvider + maidId 传参 (`NetworkHooks.openScreen` / `player.openMenu`) |
`MaidAssemblyEventHandler` | 94 | **木棍右键女仆开 GUI** (`InteractMaidEvent`, 仅当任务 = maid_assembly) + 魂符收放时库存 NBT 持久化 |

## 二、连接链
```
【任务侧】TaskRegistryManifest.CREATE (6 个任务, 门控) → TaskRegistry 注册
   → 管线 (task/*) 调服务 (CrankService 等) → Create 方块实体/配方 API (Pressing/Mixing/Deployer)
【装配 GUI 侧】玩家木棍右键女仆 → MaidAssemblyEventHandler → MaidAssemblyNetwork.openGui
   → 服务端 MaidAssemblyMenu (容器 = MaidAssemblyInventory.of(maid) 共享单例)
   → 客户端同构造 → MaidAssemblyScreen (CreateCompatClient 注册)
   → 玩家拖入机器+材料 → 管线 tick 读槽位 → MaidAssemblyService 找配方 → 产出到 10/11 槽
【方块侧】MaidPowerBeltBlock/BE ↔ 跑步带任务 (RunningBeltService) ↔ Create 动能网络
```

## 三、已知陷阱 (每条附事故)
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **装配管线有食物硬门控** | `MaidAssemblyPipeline.tickTryStart`: `if (!hasFood(maid)) return State.IDLE;` —— 女仆**副手或背包必须有食物**, 否则**永不开始装配** (用户实测"拖入材料不干活")。且 `enableWorkEat=true` 会**边工作边吃** (TLM 同款 30t/个、不可中断) ⇒ 测试/使用要给**足量**食物。|
**B** | **客户端女仆反查失败要能降级开屏** | 客户端菜单构造时反查 `level().getEntity(maidId)` 可能为 null (未加载/死亡) ⇒ `MaidAssemblyInventory` 必须容忍 null (跳过 NBT 读取, 内容靠**槽位同步**), 屏本身**不引用 maid**。原实现抛异常 ⇒ "界面打不开"。|
**C** | **屏注册门控失败原本静默** | 门控 (模块开关 + ModList) 不成立时屏不注册 ⇒ "菜单开了但没界面"。**现已在 else 分支打 WARN** (双平台), 并注明"任务注册门控必须与本处同条件"。|
**D** | **Create 依赖是 compileOnly + 运行期门控** | 编译期有 API, 运行期可能没有 ⇒ 所有 Create 类型引用必须在**门控之后**; 门控项可能为 null (`LmaBlocks.MAID_POWER_BELT == null` ⇒ 跳过渲染/交互注册)。|
**E** | **dev 里跑 compat 任务需要运行期 jar** | 见 `compat/README.md` §三: `libs-maven` + 坐标 + `-PcompatRuntime=true` (松散 `files()` 不会被重映射 ⇒ 第三方 mod mixin 崩)。**无 Create 时相关 gametest 走"跳过式通过" = 假绿**。|
**F** | **双平台 API 差异** | `ModList` (`net.minecraftforge.fml` vs `net.neoforged.fml`)、菜单数据读取 (`IForgeMenuType` vs `IMenuTypeExtension`)、屏 (`MenuScreens.register` vs `RegisterMenuScreensEvent`) ⇒ **Stonecutter 双分支**, 且两个分支都要验。|
**G** | **装配库存的持久化有两条路** | 常规走 NBT (`saveToNBT`); **魂符收放**走 `MaidAndItemTransformEvent` (forge 写 `ForgeData` / neoforge 直写 PersistentData —— 平台分支已实测修正, 见 lessons)。改持久化要同时看这两条。|
