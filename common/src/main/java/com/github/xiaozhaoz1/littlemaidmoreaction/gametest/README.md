# gametest — 游戏内集成测试 (94 用例)

**作用**: 在真实服务器环境跑端到端用例 (任务/方块/实体/GUI 服务端对偶)。**唯一文件 `LmaGameTests` (4260 行)**, 用例方法名 `lmaXxx`。
**依赖方向**: 可以调任何层 (它是测试); 被 `:forge:1.20.1:runGameTestServer` 驱动。

## 一、运行方式
```bash
rm -rf forge/versions/1.20.1/run/gametest          # 清世界 (必须! 否则残留状态污染)
./gradlew :forge:1.20.1:runGameTestServer          # 默认 93 用例
./gradlew -PcompatRuntime=true :forge:1.20.1:runGameTestServer   # 带 Create/CBC (compat 任务才真跑)
```
**判定**: 只看日志 `GAME TESTS COMPLETE` / `All N required tests passed` —— ⚠ **mod 加载失败时 Gradle 仍可能报 BUILD SUCCESSFUL** (假绿形态之一)。

## 二、用例分布与覆盖
| 域 | 代表用例 | 备注 |
|---|---|---|
任务生命周期/通用 | `lmaTaskLifecycle` 等 | 提交→tick→完成 |
移动/工作站 | `lmaFurnaceNavigate`(真导航) · `lmaCampfireCook` · `lmaBellRing`? | **移动类必须真导航** (见下陷阱) |
农田/挖空 | `lmaFarm*` · `lmaVoidExcavation*` | 区域/池 |
防御塔 | `lmaDefenseTower*` (7 条) | **各自独立 batch + 固定清单清场** |
界面 (服务端对偶) | `lmaConfigMenuMatrix` · `lmaDefenseTowerMenuWrite` · `lmaMaidAssemblyWriteRun` | 菜单构造/写入/产出 |
compat | `lmaMaidAssembly*` · `lmaCrank/Press/Mix/Power/RunningBelt` · `lmaCannonLoad` | 需 `-PcompatRuntime=true` 才真跑 |

## 三、血泪陷阱 (每条都是本会话实测踩出来的)
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **模板是 16³, 边界无地面** | 目标放 x/z > 15 **会掉下去** ⇒ 目标必须在模板内 (塔测试实测: 越界目标 19/20 箭脱靶)。|
**B** | **移动类任务: 目标必须 >10 格** | 否则测不到"走过去" (用户铁律); 且**禁止注入 `TARGET_POS`** —— 注入 = 女仆"已在目标旁", 搜索+导航整段没测 (既有 furnace/campfire 测试就这样 ⇒ 96 用例全绿而游戏是坏的)。|
**C** | **塔测试: 独立 batch + 固定清单清场** | ① 同 batch 内用例**并发**, 塔会互射对方目标 ② 塔方块**不自动消失**, 残留塔继续射击后续用例 ⇒ 两者都要治 (清场**按已知坐标**, **禁止 ±64 逐格扫描** = 81 万次/调用拖垮服务器)。|
**D** | **靶子不能共用** | 多塔共用靶子时, **不耗箭的一方(御币弹幕)先把目标杀光** ⇒ 别的塔 `damage=0` 看似"坏了" ⇒ 每塔配**对齐同 z 的专用靶**。|
**E** | **夹具校验要区分"夹具坏"与"被测对象坏"** | 失败信息里带 `[夹具: 目标存活=N/M]`; 被本方塔打死**属正常** (证明塔在工作), 不能当失败条件。|
**F** | **compat 用例可能是"跳过式通过"** | 无 Create/CBC 时任务不注册 ⇒ 用例直接 `succeed()` ⇒ **假绿** ⇒ 真跑要 `-PcompatRuntime=true`; 基线表已标注。|
**G** | **共享世界 = 状态会串** | 每轮挂的用例可能不同 ⇒ **判定回归看"失败集合是否稳定"**, 单轮失败不等于回归 (用 `runGameTestServer` 多轮采样)。|
**H** | **客户端渲染测不到** | gametest 是服务端 ⇒ 渲染只能静态审查 + `[Screen]` 生命周期日志 (参 `screen/README.md` 陷阱 E)。|

## 四、新增用例 checklist
1. **先判能不能测**: 服务端可观测的才写 (渲染/鼠标拖拽测不到, 写"服务端对偶")。
2. **靶子专用 + 目标 >10 格 + 不注入内部记忆**。
3. **要并发隔离时**给独立 `batch`; 结束时清理自己造的东西 (实体/方块)。
4. **失败信息要能自证**: 打印关键状态 (数量/存活/距离), 让人一眼分清夹具/被测对象。
5. **跑两轮**确认不是偶发。
