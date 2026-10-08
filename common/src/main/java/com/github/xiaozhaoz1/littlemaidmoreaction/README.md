# 根包 (com.github.xiaozhaoz1.littlemaidmoreaction)

**作用**: 模组**入口级别**的顶层类: 主类 (注册/配置/生命周期/日志/常量) + 菜单静态字段 + 网络注册入口。
**依赖方向**: 作为最顶层被各包引用 (Java 包根); 本身只做"装配", 不放业务逻辑。

## 一、类明细 (3 类)
| 类 | 行数 | 职责 |
|---|---|---|
`LittleMaidMoreAction` | 216 | **主类**: `MOD_ID`/`LOGGER`/`CONFIG_DIR` 常量 · `MENU_TYPES` 等 DeferredRegister · `commonSetup` (注入 `LmaMenus.*` · 注册包) · `resetPool()` 等全局清理入口 · ServerStarting 钩子 |
`LmaMenus` | 41 | **菜单类型 supplier 静态表** (`MAID_ASSEMBLY_MENU` / 各 ConfigMenu…): 由平台入口注入 **supplier** (`RegistryObject::get`); 使用点写 `.get()` ⇒ **不缓存值=无时序依赖** (菜单类型不能硬编码) |
`LmaNetwork` | 71 | **网络注册入口** (消费 `network/PacketRegistry.DEFS`, 双平台分支) |

## 二、连接链
```
平台入口 (forge: LmaForgeEntry / neoforge: LmaNeoForgeEntry)
   → LittleMaidMoreAction (注册 MENU_TYPES 等) · LmaNetwork (注册包) · init/LmaRegistrar (方块/物品…)
   → commonSetup: 注入 LmaMenus.* **supplier** (存 holder, 值在使用点解析 ⇒ 不再有"必须早于屏注册"的约束) + 网络包注册
运行期全局入口: LittleMaidMoreAction.resetPool() (服务器退出清理) 等
```

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **`MOD_ID` 是单一来源** | 命名空间字符串统一用 `LittleMaidMoreAction.MOD_ID`; 别处不要再写字面量 (TLM 的是 `touhou_little_maid`, 两者别混)。|
**B** | **`LmaMenus.*` 只能 `.get()`, 不许缓存** | 表里存 **supplier** (值在使用点解析) ⇒ 时序无关; 但**禁止**把 `.get()` 结果存进静态 (那会退回 v79.63 前的 `register(null)` 静默失败, 曾致"装配界面打不开")。|
**C** | **主类不做业务** | 只做装配/常量/生命周期回调; 业务去 task/*, 世界操作去 vanilla/*。|
**D** | **全局清理入口要与持有者同步** | `resetPool()` 这类入口必须与状态持有者 (`VoidExcavationPool`) 的 `reset*` 对齐; 新增 static 池时补入口调用 (参 `task/data/README.md` 缓存纪律)。|
