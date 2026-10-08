# resource — 动态资源 (动画定义与生成)

**作用**: LMA 的**动态资源**面: 动画定义 (纯数据) 与动态生成 (运行时产出资源)。
**依赖方向**: 原版资源 API + api; 客户端/服务端按需 (资源重载走客户端)。

## 一、类明细 (2 类)
| 类 | 行数 | 职责 |
|---|---|---|
`DynamicAnimationResources` | 166 | **动态动画资源生成** (把定义转成资源/合并到资源包; 重载时刷新) |
`LmaAnimationDef` | 36 | **动画定义 (纯数据)**: id/时长/资源路径 — 可 JVM 测 |

## 二、连接链
```
定义: LmaAnimationDef (数据) → 注册: api/AnimationResourceRegistrar → 时长表: api/AnimationDurationManager
运行: DynamicAnimationResources 在资源重载时生成/合并 (客户端)
  ↳ YSM 侧另有 compat/ysm/YsmReloadListener (热合并 ISS 动画) — 两者都挂在"资源重载"钩子, 改动要一起看
```

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **重载钩子有多处** | 本包 + `compat/ysm/YsmReloadListener` + `AnimationResourceRegistrar` 都参与资源重载 ⇒ 改一处要检查另两处 (避免重复/覆盖)。|
**B** | **定义与资源分开** | `LmaAnimationDef` 是**纯数据** (可测); 生成/IO 逻辑留 `DynamicAnimationResources` — 不要把 IO 混进数据类。|
**C** | **失败要可降级** | 资源缺失/格式错时跳过并留 WARN, 不要让资源重载整体失败 (会连累客户端启动)。|
