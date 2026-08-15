# api — 外部门面层

**作用**: 外部 mod 只 import 一个类 (LMAT) 就接入任务系统 — 注册/触发/查询/取消/配置全在这; 其余门面 (SenseApi/PathingApi/MaidChatBubbleApi) 同类。

**判据 (什么进这里)**: 门面 = 委托, 无业务; 业务永远在下层。

**依赖方向**: 可依赖全部下层; 被外部 mod 依赖 (扩展面 = 契约, 改动即破坏)。

**代表**: LMAT (任务统一入口) / SenseApi / PathingApi / MaidChatBubbleApi / MaidEmojiApi / TaskResult

**规划注意 (体检发现)**: 本包不止门面 — 还混了域模型 (RuleContext/InventoryReaderProvider) 与动画域 (AnimationDurationManager/AnimationResourceRegistrar)。新增类先问: 是门面还是域模型? 域模型该去对应领域包。

**修改注意**:
1. 一句话注册语义 = 注册动作一次调用 (LMAT.register), 不是整个任务一行
2. 迟注册钩子保证任务栏可见 (注册顺序无关; TLM 冻结后 fail-soft)
3. 命名约定: 小写下划线 + mod 前缀 (净化撞 uid 陷阱)
4. 门面方法只加不减 (外部 mod 契约 — ADR-C7 同款裁定)

**子包索引** (无独立 README, 父层总览):
- `context/RuleContext` — 域模型 (规划待归位) / `input/InventoryReaderProvider` — 域模型 (规划待归位)
- `navigation/` — NavigationMemory/NavigationUtil / `pathing/PathingApi` — 寻路门面 / `sense/SenseApi` — 扫描门面
- `nbt/NbtCodecs` — NBT 编解码契约 / `output/ItemOutputProvider` — 输出注册
