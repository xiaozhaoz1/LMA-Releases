# vanilla/fakeplayer — 假人交互层

**作用**: 用假玩家执行世界交互 (放置/右键/破坏)——女仆不能做的玩家动作经假人完成。

**判据**: 假人执行的一次性世界操作 (io 级); 假人生命周期管理。

**代表**: FakePlayerManager (池) / FakePlayerInteract.placeBlock (纯块放置链) / LmaFakePlayer / LmaPlayerSimulator

**修改注意**: 假人残留 = 实体泄漏 (stale 覆盖修复先例); 放置链行为零变化 (仿 maid_useful_task placeBlock)。
