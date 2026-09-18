# bauble — 饰品与牛奶 (WildKitsuneMilk 子包)

**作用**: 女仆**饰品**接入 (`BaubleApi`) + **野狐牛奶**系列物品/交互 (WildKitsuneMilk 子包)。
**依赖方向**: 原版 + TLM + config; 注册入口在 `init/LmaRegistrar` (经门控)。

## 一、类明细 (10 类)
### 顶层
| 类 | 行数 | 职责 |
|---|---|---|
`BaubleApi` | 40 | **饰品注册入口** (把物品挂进女仆饰品槽) |
### `WildKitsuneMilk/` (9)
| 类 | 行数 | 职责 |
|---|---|---|
`KitsuneMilkInteract` | 164 | **交互核心**: 对野狐/女仆使用牛奶 → 转化/效果 (含 `InteractMaidEvent` 订阅) |
`WildKitsuneMilkConfig` | 162 | 该子系统配置 (概率/时长/白名单) |
`WildMilkItem` | 95 | 野牛奶物品 (使用逻辑) |
`WildMilkBauble` | 83 | 野牛奶作为**饰品**的形态 |
`TamedMilkBucketItem` | 74 | 驯化牛奶桶 (成品) |
`KitsuneMilkItems` | 51 | 物品注册 |
`TamedMilkBauble` | 31 | 驯化奶饰品 |
`KitsuneMilkBaubleRegistry` | 22 | 饰品注册表 |
`MilkKind` | 6 | 奶种类枚举 (纯数据) |
### `token/` (3, v79.72 用户需求)
| 类 | 行数 | 职责 |
|---|---|---|
`TokenItem` | 42 | **可食用物品**: +2 饱食度 · 回 5 血 · 30s 缓慢恢复 I (双平台 `FoodProperties` 分支; 食用三路径的公共结算 `applyEffects`) |
`TokenBauble` | 43 | **饰品形态**: 佩戴时 `onTick` 周期补「生命恢复 I」(摘下即清) |
`TokenBaubleRegistry` | 25 | 饰品绑定入口 (经 `api/LittleMaidMoreActionExtension#bindMaidBauble`) |
`TokenFeedHandler` | 56 | **右键喂女仆** (TLM `InteractMaidEvent`): 消耗 1 个 ⇒ 调 `TokenItem.applyEffects` (数值只写一处 ✓) |

**token/ 陷阱** (踩过的):
| # | 陷阱 | 规则 |
|---|---|---|
**a** | **javadoc 里别写 `**加粗**/斜杠`** ✗ | `**` + `/` 连起来 = `*/` ⇒ **提前闭合注释** ⇒ 编译报"非法字符 ✓/②/⇒" (本次实录) ✓ |
**b** | **饰品 bind 双平台传参不同** ✗ | forge 传 `RegistryObject` ✓ / neoforge 必须 `.get()` 传解析后的 `Item` (否则报 "对于 bind(Supplier…)" — 本次实录) ✓ |
**c** | **效果别用无限时长** ✗ | `INFINITE_DURATION` 会让效果在**摘下后永久留存** ⇒ 用"短时长 + 周期补" (时长 60t / 每 40t 补 ✓) ✓ |

## 二、连接链
```
init/LmaRegistrar → KitsuneMilkItems (注册物品) → BaubleApi/KitsuneMilkBaubleRegistry (挂饰品槽)
交互: 玩家对野狐/女仆用奶 → KitsuneMilkInteract (事件订阅) → 转化/效果 (读 WildKitsuneMilkConfig)
```

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **配置读法** | 概率/时长等走 `config/*` 全局配置 (非 per-maid); 与 per-task 配置 (`pipelineConfig`) 不是一套。|
**B** | **物品/饰品注册顺序** | 物品先注册, 饰品注册后引用它 ⇒ 顺序错 = 饰品为 null (参 `init/README.md` §三-A 门控项 null 同族)。|
**C** | **交互事件自动注册** | `KitsuneMilkInteract` 走 `InteractMaidEvent` 订阅 ⇒ **静态扫描"零引用"不等于死代码** (参 `event/README.md` 陷阱 A)。|
**D** | **效果要可重放安全** | 转化类效果可能因重连/重复触发二次执行 ⇒ 判状态后再改 (幂等)。|
