# init — 注册与初始化 (双平台入口的公共部分)

**作用**: 把 LMA 的方块/物品/方块实体/配方序列化器/音效/创造栏/能力 注册进游戏; 统一入口 `LmaRegistrar`。
**依赖方向**: 原版注册 API + config + api; 被**平台入口** (`LmaForgeEntry` / `LmaNeoForgeEntry` 构造器) 调用。

## 一、组成 (10 类)
| 类 | 注册内容 |
|---|---|
`LmaRegistrar` | **统一注册入口** (平台入口调它; 内部按序调下面各组) |
`LmaBlocks` | 方块 (部分**门控**: 依赖 compat 时可能为 null, 用前必须判) |
`LmaItems` | 物品 |
`LmaBlockEntityTypes` | 方块实体类型 |
`LmaRecipeSerializers` | 配方序列化器 |
`LmaSounds` | 音效 |
`LmaCreativeTab` | 创造模式物品栏 (依赖 items ⇒ 注册顺序在最后) |
`LmaCapabilities` | 能力 (forge capability / neoforge 对应物) |
`TlmVersion` | **TLM 版本探测** (`get/isAtLeast/isV151…`) + `MOD_ID`; 被 init 与少数上层使用 |
`MaidCodexItem` | 图鉴物品 (开屏发包) |

## 二、连接链
```
平台入口构造器 (forge: LmaForgeEntry / neoforge: LmaNeoForgeEntry)
  → LmaRegistrar.register(...)  → 各 DeferredRegister/Registry 注册
  → commonSetup (平台 MOD 事件): **LmaMenus.* 注入 supplier** (存 holder 本身, 值在使用点 `.get()` 解析) + 网络包注册 (PacketRegistry.DEFS)
```
**顺序敏感**: 创造栏依赖物品、能力依赖方块实体 ⇒ 注册顺序 = 上表顺序; 平台事件 (commonSetup) 晚于类加载。

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **门控注册项可能为 null** | 依赖 compat mod 的注册 (如发电皮带方块) 在缺该 mod 时**不注册** ⇒ 用前必须 `if (X == null) return;` (先例: `CreateCompatClient.registerRenderers` 的判空)。|
**B** | **双平台分支** | 注册 API 两端不同 (DeferredRegister/RegistryObject vs Supplier/DeferredHolder) ⇒ 新注册项**双分支**写, 双节点编译验证。|
**C** | **菜单类型: 只存 supplier, 不缓存值** | `LmaMenus.*` 存的是 **supplier** (`holder::get`), 值在**使用点**解析 ⇒ **与时序无关** (v79.63 前的"在 commonSetup 缓存值"曾致装配屏 `register(null)` 静默失败)。★ **禁止**把 `.get()` 的结果再存进任何静态字段 (官方注册规范)。守护: `MenuScreenPairingGuardTest`。|
**D** | **`TlmVersion.MOD_ID` 别跨层借用** | 该常量仅供 init 内部; vanilla 层要用 TLM 命名空间请在**本类内定义常量** (本会话已把 `MaidAttrRegistry` 改为类内 NS, 断掉 vanilla→init 越层)。|
**E** | **初始化顺序 = 契约** | 改动注册顺序前先想清依赖 (创造栏/能力/兼容门控); 注册期间不要读 config 之外的世界状态。|

---

## 四、★ 生命周期时序表 (v79.63 — 采纳 ③; 事故 #310 的规矩落地)

> **一句话**: **注册对象用 holder/supplier, 在"使用点"取; 绝不把 `.get()` 值缓存进静态字段, 也绝不在"早期阶段"读"晚期阶段"写入的状态。**

| 阶段 | 事件 (mod bus) | 此时**可以**做什么 | 此时**不可以**做什么 |
|---|---|---|---|
1. 类加载/静态初始化 | — | `DeferredRegister.create(...)` + `register("name", …)` 建 **holder** | 调 `holder.get()` (此时注册尚未发生) ✗ |
2. 注册 | `RegisterEvent` (DeferredRegister 内部消费) | 注册方块/物品/方块实体/菜单类型/音效… | 依赖世界/配置/其它 mod 的注册结果 ✗ |
3. 共通 setup | `FMLCommonSetupEvent` (用 `enqueueWork` 排到主线程) | 跨注册项的**装配** (如把 holder 存进 supplier 静态表) · 网络包注册 | 读客户端类/屏 ✗ (服务端也会跑) |
4. 客户端 setup | `FMLClientSetupEvent` / **`RegisterMenuScreensEvent`** | **屏注册** (直取注册器 `X_MENU.get()`) · 渲染器注册 · 资源重载监听 | 读"阶段 3 注入的静态**值**" ✗ (顺序不保证; 必须存 supplier) |
5. 运行期 | game bus (`ServerTickEvent`/…), 各类世界事件 | 业务 (任务/管线/服务), 菜单构造时 `LmaMenus.X.get()` | 在 tick 里做重活 (交 GMPM 节拍) ✗ |

**两条硬规则 (违反即事故)**:
1. **不缓存注册值**: 需要跨类共享时, 共享的是 **holder/supplier** (如 `LmaMenus` 的 10 个字段), 使用点才 `.get()`。
   ⇒ 反例 = v79.63 前 `LmaMenus` 存 `MenuType` 值 ⇒ 客户端屏注册读到 null ⇒ `register(null, …)` **静默** ⇒ "界面消失" (#310)。
2. **门控/失败分支必须留痕**: "因为条件不成立所以不注册" ⇒ **必须 WARN** 并打印判定依据; 静默 = 用户看到功能凭空消失。

**守护**: `MenuScreenPairingGuardTest` (菜单↔屏配对 + 屏注册不得经 `LmaMenus` + `LmaMenus` 字段必须是 supplier) · `ModIdLiteralGuardTest` (modId 单一来源)。
