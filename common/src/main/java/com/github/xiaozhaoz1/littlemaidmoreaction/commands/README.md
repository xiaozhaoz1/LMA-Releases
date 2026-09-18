# commands — 命令 (`/lma`)

**作用**: LMA 的**命令树**: 任务查询/切换、区域管理、调试工具。
**依赖方向**: 允许调 `task/runtime` (Dispatcher) · `task/data` · `storage`; 是**跨层入口** (命令层被用户直接触发)。

## 一、类明细 (1 类)
| 类 | 行数 | 职责 |
|---|---|---|
`LmaCommand` | 265 | 命令注册 + 全部子命令 (任务/区域/调试; 内部读 `FlowTaskData`/`TaskRegistry`/`FarmRegionStorage` 等) |

## 二、连接链
```
玩家/控制台 → /lma ... → LmaCommand (解析)
   ├─ 任务类: TaskDispatcher.submit/cancel · TaskRegistry 查询
   ├─ 区域类: storage/FarmRegionStorage 增删改查
   └─ 调试类: 输出状态 (如 pipeline.steps() 展示)
```

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **权限与侧别** | 命令可能被**命令方块/控制台**执行 ⇒ 涉及玩家上下文的操作要先判 `getPlayerOrException`/权限等级; 不要假设"一定有人"。|
**B** | **命令是跨层入口** | 它是少数被用户直接触发的出入口 ⇒ 改命令行为等于改用户可见契约; 破坏性改动 (改名/参数) 要记 changelog。|
**C** | **输出走 Component** | 回显用 `Component.translatable/literal` (支持命令反馈格式), 不要裸字符串文本 (影响客户端渲染与 i18n)。|
**D** | **别在命令里做重活** | 大批量操作应交给 service/任务系统 (有节拍与看门狗); 命令只做解析 + 提交。|
