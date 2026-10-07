# api — 对外契约层 (供外部 mod / KubeJS / 平台入口)

**作用**: LMA 对外**稳定接口**: 门面、扩展点、常量、纯数据与注册钩子。**改动 = 破坏兼容**, 必须谨慎。
**依赖方向**: 仅 task/(只读) + vanilla/ + 原版; **被** 平台入口、外部 mod、KubeJS 脚本消费。

## 一、组成 (11 根包类 + 4 子包接口/访问点 = 本表 15 项; api 树合计 26 类)
| 类 | 对外契约 |
|---|---|
`LMAT` | **统一门面** (外部注册任务/被动任务/查询任务类型等; `register(...)` 系列) |
`MoreActionAPI` | 次级门面 (供脚本/外部读状态) |
`AdvancementSenseApi` | 成就感知 API (注册/查询) |
`AnimationDurationManager` | 动画时长表 (读侧) — 含 DEBUG 自检表 `FALLBACK_ANIMATIONS` (v79.70: 只校验 haqi/maimeng); 注册侧 `AnimationResourceRegistrar` 已于 2026-09-21 迁入 `client/` (纯客户端专用) |
`AnimationPresetNames` | **动画预设清单 + config 目录对照 JAR 清理** (v79.70, 错题 #356): `SHIPPED` (随包 2 个: haqi/maimeng) + `syncConfigDir(Path)` (删除目录里**不在 JAR 内**的 `*.animation.json` — config 该目录为 LMA 专属 ⇒ 用户裁定直接删) — 纯常量类 (零 MC/FML 依赖, 供单元测试断言"清单 ↔ resources 一致"与清理幂等) ✓ |
`VanillaConstants` | **常量** (到达距离 `ARRIVE_DIST_SQR` 等 — vanilla 层唯一允许依赖的 api 项) |
`VanillaOutputRegistry` | **Output 注册中心** (聚合 `ItemOutputProvider`, 对标原版 `BrewingRecipeRegistry` 的 Registry 模式) |
`VanillaInputRegistry` | 背包/输入读取的统一入口 (纯读; 被服务与管线共用) |
`SlotLayout` | 槽位布局常量 (熔炉等容器槽位编号) |
`TaskResult` | 任务结果枚举 (`SUCCESS/FAILED/CONTINUE`) |
`MaidCodexScreenOpener` | 屏打开钩子 (平台入口赋值, 避免 api→screen 硬依赖) |
`NavigationMemoryProvider` | **导航 Memory 类型供应接口** (2026-09-21 接口倒置): 职责 = 给 `api/navigation/NavigationMemory` 提供 NAV_TARGET / NAV_START_TICK 两个 `MemoryModuleType` 的**延迟取值器**; 输入 = 无 (实现方自持 holder); 输出 = `Supplier<MemoryModuleType<BlockPos>>` + `Supplier<MemoryModuleType<Long>>`; 连接 = 实现方 `adapter.LmaMemoryModuleRegistry`, 在 `register(modBus)` 内调 `NavigationMemory.install(PROVIDER)` 注入; 陷阱 = ① 必须传 **Supplier** 而非取好值的类型 (注入发生在 mod 构造期, 注册尚未发生 ⇒ 构造期取值必炸), 取值留到调用点 (项目统一「holder/supplier 共享」纪律); ② 未注入时 `NavigationMemory` 抛 `IllegalStateException` 显式报错 (不静默) ✓ |
`YsmAnimationProvider` | **YSM 动画输出门面** (2026-09-21 接口倒置): 职责 = 让核心层 (HaqiPipeline / AnimExecute) 播放/停止 YSM 轮盘动画而不 import compat; 输入 = `EntityMaid` + 动画名; 输出 = 无 (世界副作用: TLM `playRouletteAnim`/`stopRouletteAnim`); 连接 = 实现方 `compat.ysm.YsmOutput` (静态块自注册), 未注入 = **静默 no-op** (兼容层缺席属正常状态, 与 `MaidCodexScreenOpener` 同模式); 陷阱 = ① 语义依赖调用方先判 `maid.isYsmModel()` (非 YSM 模型不播); ② 停止语义是 no-op 而非回退到 ISS 动画 — 停 ISS 仍由调用方清 `lma_anim_mode` 闭环 (见 HaqiPipeline.onCleanup) ✓ |
`StickBindProvider` | **木棍标记/绑定能力 SPI** (2026-09-21 接口倒置, 住 `api/input/`): 3 方法 = `markItemId()` / `bindItemId()` (config 原始串, 消费方自行回退木棍) + `taskTypeOf(uidPath)` (纯函数); 实现方 = `init.LmaTaskBinding`; 消费方 = `vanilla/input/world/StickBindUtil` |
`StickBindAccess` | `StickBindProvider` 的**静态访问点** (同包成对): `install(provider)` (幂等; 注入在 `LmaRegistrar.init()`, 双平台单点) + 3 委托; **未注入 ⇒ 抛 `IllegalStateException`** (用户裁定 fail-fast, 不静默 — 缺失 = 注册链断裂, 应暴露); 陷阱 = 它是 `vanilla → api` 的**已登记契约例外** (见 `ArchGuardTest.ALLOWED_API`) ✓ |

## 二、连接链
```
外部 mod / KubeJS ──→ LMAT (门面) ──→ TaskRegistry (任务注册)
平台入口 ──→ NavigationMemoryProvider (实现方 adapter 注册期注入) / MaidCodexScreenOpener (钩子赋值)
内部层 ──→ VanillaConstants / VanillaInputRegistry / SlotLayout / TaskResult (只读常量与结果类型)
```
**边界**: api **不反向依赖**业务层实现; 需要"上层能力"时用**钩子/接口**(如 `MaidCodexScreenOpener`) 由入口注入。

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **对外契约不能破坏性改动** | 外部 mod/KubeJS 可能已依赖 ⇒ 改名/改签名需保留旧入口 (deprecated 转发) 并记 changelog。|
**B** | **vanilla 层只能依赖 `api.VanillaConstants`** | 其它 api 项对 vanilla 是越层 (ArchGuard 守) — 需要共享常量请加到 `VanillaConstants`。|
**C** | **钩子必须判空** | `MaidCodexScreenOpener` 由平台入口赋值 (可能未设/纯服务端) ⇒ 调用前判空。|
**D** | **api 里不要放业务** | 只放契约/常量/纯数据; 逻辑放 task/service, 否则外部依赖会随内部重构而崩。|

---

## 四、子包明细 (11 类) — 对外契约的"可插拔面"

> 这些子包**无独立 README**, 明细在本节 (守护 `ReadmeDriftGuardTest` 以递归模式校验)。

### `input/` (2) — **SPI**: 让外部提供"读背包"实现
| 类 | 行数 | 契约 |
|---|---|---|
`InventoryReaderProvider` | 19 | 提供"读某容器/背包内容"的实现 (外部/兼容层注册, 内部按 provider 取用) |
`InventorySpaceProvider` | 18 | 提供"剩余空间查询"的实现 |

### `output/` (1) — **SPI**: 输出能力注册
| 类 | 行数 | 契约 |
|---|---|---|
`ItemOutputProvider` | 20 | 提供"把物品输出到某目标"的实现; **被 `VanillaOutputRegistry` 聚合** (对标原版 `BrewingRecipeRegistry` 的 Registry 模式) |

### `navigation/` (2) — 导航记忆与工具
| 类 | 行数 | 契约 |
|---|---|---|
`NavigationMemory` | 53 | **导航记忆键**注册/写入 (`NAV_TARGET` 等) — 与 TLM brain memory 并行的一套 |
`NavigationUtil` | 36 | 导航工具: `navigateTo(maid,target)` / `arrived(maid,target)` / `keepAlive(world,maid)` (含 `TaskStateManager.heartbeat` 续命) |

### `pathing/` (4) — 自寻路 API (LMA 侧)
| 类 | 行数 | 契约 |
|---|---|---|
`PathingApi` | 270 | **自寻路对外 API** (直写 WALK_TARGET/路径查询; 与 `usesBrainNavigation()=false` 的任务配套) |
`ChunkWorkArea` | 146 | **区块工作区** (空置域 3×3 预载/释放; 内置区块缓存) |
`NavProgressGuard` | 110 | **导航进展守护** (卡住检测/超时复位; 被 `LmaFlowCoordinationBehavior` 与管线共用) |
`NavSkipSet` | 74 | 跳过集合 (记录"去不了的点", 防止反复重试) |

### `nbt/` (1) `sense/` (1)
| 类 | 行数 | 契约 |
|---|---|---|
`NbtCodecs` | 51 | NBT 编解码工具 (跨层共用的读写写法) |
`SenseApi` | 205 | **感知 API**: 环境信号查询/订阅 (被动感知层对外的稳定面) |

**⚠ 契约纪律**: 本节全部是**对外可插拔面** — 接口/SPI 只能**新增** default 实现或新方法, 不得破坏性改动 (外部 mod/KubeJS 可能已实现)。
