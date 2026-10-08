# compat/create/task/assembly — 便携装配 (maid_assembly 任务)

**作用**: Create 门控的主动任务 `maid_assembly` (**便携装配**) 全链 — 任务桥 + FSM 管线 + 领域服务 + **per-maid 库存** + 菜单/屏/网络 + 木棍右键开屏。
**依赖方向**: `task/api` + `task/data` + `task/runtime` + `adapter`(`LmaFlowCoordinationBehavior`) + `vanilla/input/item` + 根包 (`LmaMenus`/`LittleMaidMoreAction`)。
**门控/注册**: **Create 专属** ⇒ 注册在 `TaskRegistryManifest.CREATE` (`CompatToggle` + `ModList` 门控内): `new TaskSpec("maid_assembly", MaidAssemblyPipeline::new)`。
**任务类型**: `maid_assembly` (TLM 任务栏条目; `IMaidTask` 桥见 `MaidAssemblyTask`)。

## 一、类明细 (8 类)

| 类 | 行数 | 是什么 | 谁在用 |
|---|---|---|---|
| `MaidAssemblyTask.java` | 76 | TLM `IMaidTask` 桥 (任务栏条目; `WorkEatBehavior`/`NearbyCollectBehavior` 由 `DefaultBehaviorBrain` 自动注入) | TLM 任务栏 |
| `MaidAssemblyPipeline.java` | 356 | **FSM 管线** (任务主体: 相位推进/装配编排) | GMPM 每 tick |
| `MaidAssemblyService.java` | 238 | 领域服务 (材料消耗/产物装配推进等业务算法) | Pipeline |
| `MaidAssemblyInventory.java` | 198 | **per-maid 库存单例** (`of(maid)`; 12 槽: 机器 0-7 / 材料 8 / 中间 9 / 产物 10-11; 静态 `CACHE`) | Pipeline/Service/Menu |
| `MaidAssemblyMenu.java` | 163 | 菜单 (原版 176px 宽; 锁定状态经 `clickMenuButton` + `DataSlot` 同步) | Screen |
| `MaidAssemblyScreen.java` | 142 | 屏 (**全部代码绘制**; 与服务端 DataSlot 同步) | 玩家 |
| `MaidAssemblyNetwork.java` | 65 | 网络层 (`MenuProvider` 打开 + 数据包) | EventHandler / 客户端 |
| `MaidAssemblyEventHandler.java` | 94 | **木棍右键女仆 → 开装配屏** (事件入口) | 事件总线 |

## 二、连接链

```
开屏: 木棍右键女仆 → MaidAssemblyEventHandler → MaidAssemblyNetwork.openGui
        → MaidAssemblyMenu (+MaidAssemblyScreen) ↔ MaidAssemblyInventory
任务: TLM 任务栏 maid_assembly → TaskRegistryManifest.CREATE → MaidAssemblyPipeline(FSM)
        → MaidAssemblyService (领域算法) → MaidAssemblyInventory (per-maid 槽位)
卸载: MaidAssemblyInventory.CACHE 由 MaidUnloadRegistry **声明式清理** (registerCache, 构造期登记)
```

## 三、已知陷阱

| # | 陷阱 | 规则 |
|---|---|---|
| **A** | **静态 CACHE 必须声明式登记卸载** | `MaidAssemblyInventory.CACHE` 是 per-UUID 静态表 ⇒ 必须 `MaidUnloadRegistry.registerCache(...)` 注册清理 (v79.29 曾泄漏: 女仆卸载后缓存留驻) — **禁手写监听** ✗ |
| **B** | **锁定/状态只走 Menu + DataSlot** | 锁定按钮由 `clickMenuButton` 包 → 服务端改状态 → DataSlot 同步回屏; **不要在 Screen 里直接改状态** (客户端预测与服务端分叉 ✗) |
| **C** | **槽位索引是契约** | `MACHINE_SLOTS=8`(0-7) / `MATERIAL_SLOT=8` / `INTERMEDIATE_SLOT=9` / `OUTPUT1_SLOT=10` / `OUTPUT2_SLOT=11` — 改动必须**同步三处**: Menu (槽位布局) / Screen (绘制坐标) / Service (取放逻辑) |
| **D** | **规格表 import 包路径易写错** | 本包路径深 (`compat.create.task.assembly`) — 在 `TaskRegistryManifest` 里写错包路径**编译即炸** (错题 **#204**); 加任务时复制现有行/用补全 |
| **E** | **Create 未装时不得加载** | 注册与开屏入口都在 `CompatToggle` + `ModList` 门控内; 新增类**不要**在静态块裸注册, 也不要被非门控类静态引用 ✗ (NoClassDefFoundError) |
| **F** | **屏是"代码绘制"** | 本屏不依赖 TLM 基类纹理 ⇒ 布局坐标自管; 改尺寸/行数要同时核 Menu 的槽位坐标 (原版 176px 面板) ✓ |

> **相关错题**: #204 (规格表包路径写错) · #290 (enum-FSM 全量审查 — 本管线在列) · #136/#137/#138 (GUI 双平台语义差异: 3D 预览参数 / § 码 / 颜色 API)。
> **维护约定**: 新增类/改槽位布局/改开屏入口 ⇒ 必须同步本表与"连接链" — README 是跨会话契约, 注释会被重构删掉。
