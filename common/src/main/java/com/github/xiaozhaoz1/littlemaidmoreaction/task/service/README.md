# task/service — 业务算法层 (决策 + 单拍编排)

> **子包**: [harvest/](harvest/README.md) — 采集/导航安全/自救/空置域**四域协调器** (12 类, V5 2026-09-11 自 `vanilla/execute` 整簇迁入; io 原语仍在 vanilla)。

**作用**: 有**业务语义**的算法 (与 "io 原语" 的分界: 有业务语义 → 这里; 单纯读世界/写背包 → `vanilla/input|output`)。
**不持有 tick 相位机** (那是 `task/pipeline`); 服务通常是**无状态**或**自管状态 + 自注册清理**。

**依赖方向**: task/data + storage + vanilla/*; **被** task/pipeline、compat/*、adapter 调用。管线之间不互调, 服务之间允许 (如 `MaterialChecker → MaterialReport/RecipeResolver`)。

---

## 一、明细表 (输入 / 输出 / 被谁调用)

> **维护约定**: 新增服务必须补本表 (输入/输出/调用方); 调用方统计可用 `_audit/extractServiceFacts.js` 机械提取。
> ⚠ **该脚本会漏**: ① 仅作**类型**引用 (字段/泛型, 如 `Map<UUID, MaidChainState>`) ② **跨行全限定**调用 (`…\n.forceChunk(...)`)。
> ⇒ **"0 调用方" ≠ 死代码**, 必须人工复核 (本轮实测: `MaidChainState` / `VoidExcavationChunkManager` 均被正常使用)。

| 服务 | 输入 (读) | 输出 (写) | 被谁调用 |
|---|---|---|---|
**`FurnaceService`** | 熔炉 BE · 女仆背包 (`VanillaInputRegistry`) · 黑白名单 · 配方表 (SMELTING/SMOKING/BLASTING) | 炉子槽位 (产物取出/原料入/燃料入) · 气泡 | **FurnacePipeline** (validate + 三相) |
**`JukeboxService`** | 唱片机 BE · 女仆背包 | 唱片槽 (插入/弹出) | **JukeboxPipeline** |
**`CraftService`** | 配方 · 材料 | 合成产物 · 背包 | **CraftChainPipeline** |
**`RecipeResolver`** / **`MaterialChecker`** / **`MaterialReport`** | 配方树 · 背包 | (纯计算: 解析结果 / 够不够 / 缺什么) | **CraftChainPipeline** (`Checker` 调 `Report`) |
**`BlockInteractService`** | 目标方块 · 女仆 | 对方块执行交互 | **BlockInteractPipeline** · compat/ai `InteractBlockTool` |
**`ArmTransferService`** | 源/目标容器 | 物品搬运 (取出/存入 + 数量计算) | **ArmTransferPipeline** · `vanilla/output/container` |
**`FarmExecute`** | 农田区域 (storage) · 世界 | 收/种/存箱 (委托 `FarmItems` + `FarmRegionContainers` + `CropQuery`) | **FarmPipeline** · `screen/SeedPickerScreen` |
**`FarmItems`** | 作物/种子 id · ItemStack | (纯判定: 产物/种子解析、跨区域归属) | `FarmExecute` · `FarmRegionContainers` |
**`FarmRegionContainers`** | 区域箱 · 女仆背包 | 取种/存产物/回存多余种子 | `FarmExecute` (唯一) |
**`HarvestTarget`** | 方块/作物**目标定义** (要采什么) | (纯数据/判定: 目标是否匹配) | `ChainHarvestPipeline` · `task/service/harvest/ChainHarvestExecute` · `harvest/ChainScan` |
**`HaqiService`** | 目标实体 · 挥击参数 | 伤害 + 音效 | sense 的 **HaqiPipeline/HaqiTrigger** |
**`AnimExecute`** | 动作类型 | 播放 TLM 动画 | **HaqiPipeline** |
**`MaidFavorability`** | 好感度等级 | (纯计算: 工作效率倍率/工时) | MaidAssemblyService · Mix/PressPipeline · 行为类 (WorkEat/AutoRepair) |
**`ItemFilters`** | 黑白名单配置 (per-maid 或全局) | (纯判定: 是否允许) | **7 处**: FurnacePipeline · JukeboxPipeline · ArmTransferPipeline · ItemListConfigScreen · TaskConfigGuiFactory … |
**`TaskConfigs`** | 女仆 per-task 配置 | (纯读: 取值+默认) | ArmTransferPipeline · BlockInteractPipeline · FurnaceService · gametest |
**`AiControlGate`** | 女仆 | AI 操控**权限开/关** (键删除闭环) | **AiControlPipeline** (onCleanup 收回) · compat/ai `GatedMaidTool` |
**`VoidExcavationPool`** | 区块认领池 (BLOCK_POOL/CHUNK_WORK_STATE/SCAN_CACHE) | 认领/心跳/释放 + `resetPool` + `onMaidUnload` | **VoidExcavationPipeline** · 入口 (`LittleMaidMoreAction.resetPool`) |
**`VoidExcavationService`** | 空置域范围 (minY/maxY) | 挖块 · **传送** · 判定 `near` | VoidExcavationPipeline · **DamFillPipeline** (复用范围/挖块) |
**`VoidExcavationContainerService`** | 输入/输出容器 | 取工具 · 存方块 · 填补料 | VoidExcavationPipeline |
**`VoidExcavationChunkManager`** | 区块坐标 | 强制加载/释放 + 区域边界计算 | VoidExcavationPipeline (跨行全限定调用) · VoidExcavationService |

**harvest 子包** (采集/导航安全/自救 四域) 见 [harvest/README.md](harvest/README.md); 其中 `ChainHarvestExecute` 是被最多外部调用者引用的服务 (13 处, 含 `TlmEventAdapter`/`NavigationUtil`/GMPM)。

---

## 二、怎么连接 (三条链)

### 1. 管线 → 服务 (主链)
```
GMPM 每 tick → pipeline.tick(...)
    → 管线判定 (相位/门/节拍)  ⊂ task/pipeline
    → 调 service 做**单拍业务** (读世界/背包 → 算 → 写世界/背包)
    → 返回结果 (本次是否成功/是否有意义) → 管线负责计数/转相/完成
```
**分界**: 服务**不决定"什么时候做"** (那是管线); 管线**不做具体世界读写** (那是服务/io 原语)。

### 2. 状态归属 (谁持状态, 谁清)
| 状态 | 持有者 | 清理 |
|---|---|---|
采集链状态 | `ChainHarvestExecute.STATES` (per-UUID `MaidChainState`) | `clearChainData/clearMaidState` ← `TlmEventAdapter`·GMPM |
空置域认领池/扫描缓存 | `VoidExcavationPool` (static) | **`resetPool`** (服务器退出) + **`onMaidUnload`** (MaidUnloadRegistry 自注册) |
危险/自救 | `DangerGuardCoordinator`·`SelfRescueState` | `MaidUnloadRegistry` 注册 |
AI 权限 | `AiControlGate` | 管线 `onCleanup` (**键删除闭环**) |
农田 lastHarvest | **管线侧** (`FarmExecute`) | 传入 `FarmRegionContainers` 并按其返回清理 |
**纪律**: 服务**自带清理注册** (static 状态必须挂 `MaidUnloadRegistry`/暴露 reset), 否则 = 内存泄漏 (参 `task/data/README.md` 的缓存纪律表)。

### 3. io 原语 vs 服务 (判据复习)
```
跨 tick?      → vanilla/execute (协调器) 或 task/service (若含业务语义)
有业务语义?    → task/service        (例: FurnaceService 决定"该收产物还是加料")
纯读/纯写?     → vanilla/input|output (例: VanillaInputRegistry 读背包)
```

---

## 三、已知陷阱 (改前必读, 每条附事故)

| # | 陷阱 | 规则 |
|---|---|---|
**A** | **validate 与运行时不一致** | `FurnaceService.validateSmelt` 支持"空 target = 烧任何可烧的", 而 `resolveSmeltIngredient` 遇空 target 直接 `return ""` ⇒ **女仆走到炉子前站着不烧** (用户实机)。⇒ 改校验分支必须同步改执行分支 (反之亦然)。|
**B** | **服务里读配置要 `cfgOrCreate` 语义** | 读用 `pipelineConfig(maid)` (已改走 `cfgOrCreate`, 写才落盘); **写配置的服务/屏幕**必须确认拿到落盘引用, 否则静默丢 (钟间隔事故, 见 `task/gui/README.md`)。|
**C** | **static 状态必须可清** | 池/缓存/协调器挂 `MaidUnloadRegistry` 或有 `reset*` 并被入口调用; 新增 static Map 前先看 `task/data/README.md` 的缓存纪律 (per-maid 键 = UUID; 区块键 = `clearDimension`)。|
**D** | **服务不做 tick 决策** | 节拍/相位/门在 `task/pipeline`; 服务里出现 `gameTime % N` 节流应改为"调用方按节拍调用, 服务内部只做冷却判定 (如 `ThrottleUtil`)"。|
**E** | **调用方统计会骗人** | `_audit/extractServiceFacts.js` 的 "0 调用方" 可能是**类型引用/跨行全限定** (本轮实测 2 例都不是死代码) ⇒ 删任何类前用**全仓 grep (含类型位置)**复核。|

---

## 四、新增服务 checklist

1. **判定层级**: 有业务语义 + 无 tick 相位 → 放这里; 纯读/写 → `vanilla/input|output`; 相位机 → `task/pipeline`。
2. **无状态优先**: 需要 per-maid 状态时, 要么放管线 (传入), 要么 static Map + `MaidUnloadRegistry` 注册。
3. **输入/输出显式**: 参数传入 (世界/女仆/目标), 返回结果/布尔, 不读全局单例 (除配置/注册表)。
4. **补本 README 明细行** (输入/输出/调用方) + 若含 public API 则补单测 (纯函数优先, 参 `PipelineStepsGuardTest` 风格)。
5. **陷阱自查**: §三 A–E 逐条过一遍 (尤其 validate/执行一致性 + 状态清理)。
