# screen — 客户端屏 (通用 UI 基建 + 域屏)

**作用**: 所有**客户端 Screen**: 配置入口屏、任务相关屏、图鉴/农田/属性等域屏, 以及通用 UI 基建 (打开入口登记表/背景/生命周期日志)。
**依赖方向**: 原版客户端 + TLM 客户端 API + task/api + config + chatbubble; **仅客户端加载** (dedicated server 不加载本包)。

## 一、组成 (21 类, 按用途分组)
### 1. 入口与基建
| 类 | 职责 |
|---|---|
`ScreenRegistry` | **打开入口登记表** (`registerOpener`/`open`/`create`) — 配置屏统一打开路径; 未注册即 warn (`ScreenLifecycleLog.openFailed`) |
`MaidGuiRegistry` | 把 LMA 入口挂到 **TLM 女仆 GUI** 的按钮上 (调用 `ScreenRegistry.open("lma_config", …)`) |
`LMAConfigScreen` | **主配置屏**: 按钮由 `ScreenRegistry.configButtons()` 数据驱动 (v79.51 6 按钮) |
`PanoramaBackground` | 通用全景背景 (被 **45 处**引用 = 全模组屏背景) |
`ScreenLifecycleLog` | **屏生命周期日志** (v79.63): `requested/openFailed/constructed/compatConstructed` — 定位"界面打不开" (兼容屏走门控重载, 无 mod 不执行) |
`ButtonEntry` | 屏按钮条目 record |

`MarkTarget` | **当前交付目标** (客户端态): 手持标记/绑定工具右键女仆时记录该女仆的任务类型 ⇒
木棍右键容器时 `FarmContainerScreen` 据此**只列该任务需要的角色按钮** (用户裁定 B); 空 = 未知 ⇒ 回退全列 |

### 2. 配置类屏
`CompatConfigScreen` (兼容模块开关) · `ClothSettingsScreen` (Cloth Config 桥: 调试/连锁采集/环境感知/右键交互/**杂项**/任务自定义入口/好感度乘区/防御塔) · `MaidOtherSettingsScreen` (其它设置) · `TaskSettingsScreen` (每任务 cloth 子屏: 该任务自己的全局默认值)

**配置项归属判据 (v79.63.21 用户裁定)** — 新增任何配置项前先过这一关:
- **任务自己的参数** ⇒ `ClothSettingsScreen`「任务自定义」→ 该任务 `TaskSettingsScreen` 子屏
  (例: `bell_ring` 音量/音调/间隔 · `void_excavation` 区块数/销毁名单/关寻路 · `dam_fill` 默认区块数)
- **真全局参数** (不属于任何单个任务) ⇒ `ClothSettingsScreen`「**杂项**」分类 (例: 发电皮带 `running_belt.stress`)
- **感知域参数** ⇒ `ClothSettingsScreen`「环境感知」分类 (例: `env_sense.rare_biome_*`)
- ⚠ 两个屏都是**手写逐项枚举, 无循环兜底** ⇒ 新 define 极易变成"有 TOML 键、无 GUI 入口"
  (2026-09-16 实测: `running_belt.stress` / `dam_fill.default_chunks` / `env_sense.rare_biome_*` 曾长期无入口)
  ⇒ 新增 define 后**必须同步本屏**, 并重跑 `grep -c "eb.start" <屏>` 回写 `ConfigConsistencyTest` 注释条目数。

### 3. 任务相关屏
`MaidListScreen` (**女仆列表** — 农田任务选女仆/区域入口) · `MaidListButton` · `MaidFarmRegionScreen` / `MaidFarmRegionDetailScreen` (农田区域管理) · `SeedPickerScreen` (种子选择) · `FarmContainerScreen` (农田容器; **仅平台源引用**) · `TaskTreeScreen` (任务树: 读 `pipeline.steps()` 展示步骤) · `LmaQuestScreen` (任务/成就树)

### 4. 信息屏
`MaidCodexScreen` (图鉴) · `MaidAttributeScreen` (属性面板, 读 `vanilla/MaidAttrRegistry`)

## 二、连接链
```
TLM 女仆 GUI → MaidGuiRegistry 按钮 → ScreenRegistry.open("lma_config", parent) → LMAConfigScreen
                                                      → ScreenRegistry.configButtons() 数据驱动按钮
任务设置页 (TLM 任务栏) → pipeline.getConfigGuiProvider(maid) → TaskConfigGuiFactory → task/gui/XConfigScreen
主配置项的屏幕: TaskSettingsScreen 直接读写 config/*
"界面打不开"排查: 看 [Screen] 日志 (open 请求 / 打开失败 / 已打开) — 三态分别对应 入口/登记表/渲染
```
**注**: `task/gui/*` 是**任务 per-maid 配置屏** (走 `TaskConfigActionPacket`); 本包是**全局/域屏**。两者不要混。

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **改 GUI 必须给详细坐标** (用户铁律) | 任何屏的布局改动都要写明 x/y/宽高, 否则无法复核。|
**B** | **Simi 屏 vs 原版屏的尺寸 API 不同** | Create/Catnip 屏 (`AbstractSimiContainerScreen`) 用 `setWindowSize(w,h)`; 原版 `AbstractContainerScreen` 用 `imageWidth/imageHeight`。**(本轮曾把两者搞混, 误判为渲染缺陷。)** |
**C** | **1.21 的 `renderBackground` 多 3 个参数** | `renderBackground(g)` (1.20.1) vs `renderBackground(g,mx,my,pt)` (1.21.1) — 必须 Stonecutter 双分支; `super.render` 之后才 `renderTooltip`。|
**D** | **兼容屏必须门控** | 引用 Create 等可选 mod 的屏/日志必须先过 `CompatToggle.isModuleEnabled(...)` (用户裁定: 别人没装该 mod 时**不执行**)。|
**E** | **屏注册失败是静默的** | 菜单能开但屏没注册 ⇒ 什么都不显示。新增屏必须**双平台注册** (forge `MenuScreens.register` / neoforge `RegisterMenuScreensEvent`), 并在门控不成立时**留 WARN**。|
