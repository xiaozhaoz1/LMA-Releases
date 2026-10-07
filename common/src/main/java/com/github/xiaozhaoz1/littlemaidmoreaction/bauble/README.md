# bauble — 饰品与牛奶 (WildKitsuneMilk 子包)

**作用**: 女仆**饰品**接入 (`BaubleApi`) + **饰品家族**子包 (`WildKitsuneMilk/` 野狐牛奶 · `token/` Token 系列)。
**依赖方向**: 原版 + TLM + config; 注册入口在 `init/LmaRegistrar` (经门控)。

> **⭐ 家族归属判据 (2026-09-21 用户裁定)**: **饰品家族归本包**，判据 = **家族形态**——
> 「饰品 (`IMaidBauble`) + 绑定注册 + 物品 + 交互事件」成套者 ⇒ `bauble/<名>/` ✓
> （两例: `WildKitsuneMilk/` 9 类 ✓ · `token/` 6 类 ✓ —— 二者**结构同形**:
> 饰品/注册 83+22 与 49+31 · 物品 95 与 174 · 交互事件 164 与 108 ✓）。
> **驱动方式不是归属理由** ✗: 家族的**周期性行为**另设**触发口** —— 由 `task/pipeline/sense/<X>Trigger`
> 驱动（同 `SelfRescueTrigger` / `HaqiTrigger` 形态 ✓），**引擎不点名家族** ✓
> （实例: `token/` 的 6000t 偷吃由 `task/pipeline/sense/TokenStealTrigger` 驱动 ✓，
> 2026-09-21 由 `task/service/token/` **回迁本包** ✓）。
> ⇒ 一句话: **看家族形态（饰品成套），不看它被谁驱动** ✓。

## 一、类明细 (16 类 = 顶层 1 + WildKitsuneMilk 9 + token 6)
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
### `token/` (6) — **2026-09-21 回迁** (用户裁定: 饰品家族归本包 ✓)
| 类 | 行数 | 职责 |
|---|---|---|
`TokenStealService` | 229 | **偷吃 Token**: 每 6000t 掷 5% 把主人身上整组 Token 搬给女仆 (原子: 先 `simulate` 全量校验再真搬 ✓; 冷却自持) |
`TokenItem` | 174 | Token 物品 (可食用: +2 饱食 · 回 5 血 · 30s 缓慢恢复 I) |
`TokenFeedHandler` | 108 | 右键**喂女仆** (`InteractMaidEvent` 订阅 ✓ = 交互事件驱动, 与奶家族同形 ✓) |
`TokenStealMath` | 55 | 偷吃判定**纯函数** (冷却/概率/8 格距离门控语义, 由 `TokenStealMathTest` 锁定 ✓) |
`TokenBauble` | 49 | Token 作为**饰品**的形态 (`IMaidBauble`: 佩戴时周期补生命恢复 I) |
`TokenBaubleRegistry` | 31 | 饰品绑定入口 (经 `api/LittleMaidMoreActionExtension#bindMaidBauble`) |
> **驱动**: 6000t 周期行为由 `task/pipeline/sense/TokenStealTrigger` 驱动 ✓（引擎不点名家族 ✓；
> 与 `SelfRescueTrigger`/`HaqiTrigger` 同款触发口形态 ✓）。

**token/ 陷阱** (踩过的):
| # | 陷阱 | 规则 |
|---|---|---|
**a** | **javadoc 里别写 `**加粗**/斜杠`** ✗ | `**` + `/` 连起来 = `*/` ⇒ **提前闭合注释** ⇒ 编译报"非法字符 ✓/②/⇒" (本次实录) ✓ |
**b** | **饰品 bind 双平台传参不同** ✗ | forge 传 `RegistryObject` ✓ / neoforge 必须 `.get()` 传解析后的 `Item` (否则报 "对于 bind(Supplier…)" — 本次实录) ✓ |
**d** | **偷取状态别落盘** | 上次判定 tick 存 per-maid **PD** (`MaidData.pl`, 瞬态 ✓) — 6 分钟量级行为, 卸载后重新起算可接受 ✓ (落盘会引入迁移负担 ✗) |
**e** | **主人类型用 `Player` 而非 `ServerPlayer`** | 生产主人必为 `ServerPlayer` ✓, 但 gametest 的 `helper.makeMockPlayer(...)` 返回**匿名 `Player` 子类** ✗ ⇒ 用 `instanceof ServerPlayer` 会让用例永远测不到该路径 ⇒ 统一按 `Player` 判定 ✓ |
**f** | **搬运必须"先模拟后执行"** | 直接 insert 再回滚会**掉物品** ✗ ⇒ 先 `insertItem(..., simulate=true)` 全量校验, 通过后再真搬 ✓ (用户裁定: 放不下就一点不拿 ✓) |
**g** | **食用复用 TLM 原生 `maid.eat`** | 手动播音效/改饱食度 = 走偏门 ✗; `eat` 对非玩家实体不一定扣减 ⇒ 按"没扣就手动扣 1" ✓ (与 `TokenItem.feedOnce` 同源口径) |
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
