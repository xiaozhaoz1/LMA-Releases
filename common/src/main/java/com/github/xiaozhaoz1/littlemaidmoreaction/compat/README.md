# compat — 兼容层 (可选 mod 门控 + 各家族适配)

**作用**: 与**可选 mod** 的适配: 模块开关、安装探测、以及各家族 (Create / CBC / PatPat / YSM / AI) 的任务与实体。
**依赖方向**: 允许依赖 task/* + vanilla + api; **不被** vanilla 依赖 (ArchGuard 守)。所有入口必须**先过门控**。

## 一、门控体系 (顶层 4 类)
| 类 | 公开面 | 语义 |
|---|---|---|
`CompatToggle` | `isModuleEnabled(id)` / `setModuleEnabled(id,v)` / `disabledModules()` | **模块开关** (JSON `config/littlemaidmoreaction/compat_toggles.json`, `{"disabled":["create"]}`); 缺文件=全启用; **注册期生效** (改后重启) |
`CompatRegistry` | `registerModule(...)` / `scanAllCompatEarly()` / `onEnqueue(...)` | **模块注册表 + 早期扫描**: 在 `TaskRegistry` static 块**之前**加载开关 (顺序敏感!) |
`NumenCompat` | `isInstalled()` | Numen (AI 对话) 安装探测 → 门控 `ai_control` 任务 |
`YsmCompat` | `isInstalled()` / `isOpenYsm()` / `isPipelineReady()` | YSM (是, 史蒂夫模型) 探测与管线就绪判定 |

**门控双条件约定**: 模块开关 (`CompatToggle`) **且** mod 已加载 (`ModList.isLoaded`) —— 任务注册与对应界面注册**必须同条件** (否则"任务在而屏缺")。

## 二、家族适配
| 目录 | 类数 | 内容 |
|---|---|---|
`create/` | 22 | **Create (机械动力) 家族**: `block/`(发电皮带方块) `client/`(屏注册与渲染) `render/` `task/`(曲柄 crank / 搅拌 mix / 压块 press / 发电 power / 跑步带 running_belt / **便携装配 assembly**: `MaidAssemblyTask/Pipeline/Service/Menu/Screen/Inventory/Network/EventHandler`) |
`createbigcannons/` | 2 | **CBC (火炮)**: `cannon_load` (找炮架→开闩→装弹→合闩) |
`patpat/` | 2 | **PatPat (抚摸)**: `PatPatCompat` + `PatPatReactionClient` (客户端反应) |
`ysm/` | 3 | **YSM 模型**: `YsmAnimInjector` / `YsmOutput` / `YsmReloadListener` (动画注入 + 资源重载) |
`ai/` | 16 | **AI 工具面**: `AiToolRegistration` + `tool/*` (女仆可被 AI 调用的工具集; `GatedMaidTool` 走 `AiControlGate` 权限) |

## 三、compat 的运行期测试机制 (本会话新增, 见 docs/lessons #286~#288)
```
【问题】Create/CBC 原为 compileOnly(运行时门控) ⇒ dev/gametest 无这些 mod ⇒ 任务未注册 ⇒
        compat 相关测试全走"未注册→跳过→succeed" = **假绿**
【开关】gradle/compat-runtime.gradle + 各节点 build.gradle 末尾 apply from:
        ./gradlew -PcompatRuntime=true :forge:1.20.1:runGameTestServer     (默认关, 零副作用)
【正解】jar 装进本地 maven repo **libs-maven/** + 用**坐标**声明 (mod 配置名按节点探测: forge=modRuntimeOnly, neoforge=runtimeOnly)
        ⇒ 坐标依赖会被 ForgeGradle **重映射** ⇒ 第三方 mod 的 SRG mixin refmap 在 dev 可定位
【三条死路(实测)】① runtimeOnly files(...) → JarJar 当 source 且不重映射 ⇒ mixin 崩
                  ② modRuntimeOnly files(...) / dependencies.add(cfg, files) → "Cannot convert ... DependencyConstraint"
                  ③ run/mods 目录复制 → dev 不扫描
【已知限制】neoforge 开态**不可行**: Create 6.0.10 要求 NeoForge ≥21.1.219, 本 dev = 21.1.62 (本地无新版缓存)
```

## 四、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **门控顺序** | 模块扫描必须在**任务注册之前** (`CompatRegistry.scanAllCompatEarly` 先于 `TaskRegistry` static 块) — 否则禁用模块的任务仍会注册。|
**B** | **注册与界面同条件** | 任务注册门控条件 ≠ 界面注册条件 ⇒ "任务在但屏打不开" (装配屏教训, 现已在门控不成立时打 WARN)。|
**C** | **禁用即"任务不存在"** | 模块禁用 = 该任务**不注册** ⇒ 任何"跳过式通过"的测试都是假绿 (基线表已标注); 相关功能测试需 `-PcompatRuntime=true` 或实机。|
**D** | **jar 必须走 maven 坐标** | 松散 `files(...)` **不会被重映射** ⇒ 第三方 mod 的 mixin refmap (SRG 名) 在 dev 崩 (Create/CBC 实测)。|
**E** | **兼容代码不得反向被依赖** | vanilla/task 不得 import compat (ArchGuard 守); 需要"兼容能力"时用门控 + 接口, 不要让底层认识上层。|

---

## 五、子包逐类明细 (ai / ysm / patpat / createbigcannons)

> **维护约定**: 这四个子包**无独立 README**, 明细集中在本节 (守护 `ReadmeDriftGuardTest` 以 `"R"` 递归模式校验本 README 覆盖其全部类)。

### `ai/` (16) — AI 工具面 (供 Numen 等 AI 调用女仆)
| 类 | 行数 | 干什么 |
|---|---|---|
`AiToolRegistration` | 38 | 工具注册入口 (把下面 15 个工具挂进 AI 工具表) |
`tool/GatedMaidTool` | 19 | **权限门控基类**: 调 `AiControlGate` 判定"该女仆是否授权 AI 操控" |
`tool/CollectItemsTool` | 56 | 捡起附近物品 |
`tool/MineBlockTool` | 56 | 挖方块 |
`tool/PlaceHold`→(`InteractBlockTool`) | 59 | 与方块交互 (右键) |
`tool/InteractEntityTool` | 65 | 与实体交互 |
`tool/MeleeAttackTool` | 67 | 近战攻击 |
`tool/MoveToTool` | 63 | 走到坐标 |
`tool/LookAroundTool` | 51 | 环顾 (扫描周围) |
`tool/ScanBlocksTool` | 94 | 扫方块 |
`tool/ScanNearbyEntitiesTool` | 63 | 扫实体 |
`tool/ReadBlueprintTool` | 62 | 读蓝图 |
`tool/SwitchLmaTaskTool` | 60 | **切换 LMA 任务** (AI 指挥任务切换) |
`tool/GetSelfStatusTool` | 55 | 读自身状态 (血/饱食/背包) |
`tool/GetWorldInfoTool` | 41 | 读世界信息 (时间/天气/群系) |
`tool/WaitTicksTool` | 61 | 等待 N tick |

### `ysm/` (3) — YSM (是, 史蒂夫模型) 适配
| 类 | 行数 | 干什么 |
|---|---|---|
`YsmAnimInjector` | 304 | 把 LMA 动画**注入** YSM 模型 (最大) |
`YsmReloadListener` | 184 | 客户端资源重载时热合并 (ISS 动画) |
`YsmOutput` | 114 | YSM 侧输出原语 (播放/停止动画) |

### `patpat/` (2) — PatPat (抚摸) 适配
| 类 | 行数 | 干什么 |
|---|---|---|
`PatPatCompat` | 96 | 探测 PatPat + 抚摸事件 → 触发女仆反应 |
`PatPatReactionClient` | 75 | 客户端反应 (表情/气泡) |

### `createbigcannons/` (2) — CBC (Create Big Cannons) 火炮装填
| 类 | 行数 | 干什么 |
|---|---|---|
`task/CannonLoadPipeline` | 483 | **装填相位机** (SEARCHING/MOVING/OPENING/CLEARING/LOADING/CLOSING 6 态) |
`task/CannonLoadService` | 430 | 炮架识别 / 开合闩 / 弹药装载 / 清理残留炮弹 |
> ⚠ **CBC 运行期依赖**: 其强制前置 `ritchiesprojectilelib` **不在 libs/** 时, 开 compatRuntime 会 **Mod Loading 失败** (已记录: 需从游戏目录复制; 见 `compat/README.md` §三)。
