/**
 * 客户端专用屏（Screen / GUI）。
 *
 * <p><b>当前状态</b>：本包位于 common，类未标注 {@code @OnlyIn(CLIENT)}，
 * 因此服务端可加载本包。安全性依赖“无启动期路径引用本包”这一事实。
 *
 * <p><b>机制（2026-09-21 联网核实）</b>：dist-cleaner 拦的是“**在服务端加载/解析到** {@code @OnlyIn(CLIENT)} 的 MC 类”，
 * 不是“源码里出现就炸” ✗ ⇒ **未被加载的引用路径是安全的** ✓。NeoForge {@code Dist} 文档原话:
 * “to prevent <b>classloading</b> errors, it is important to ensure that OnlyIn elements are only <b>loaded</b>
 * if their designated dist is the same as the executing dist”。
 * 旁证：Forge 论坛同类事故帖中，发帖人自称“已用包保证只在客户端开屏”仍崩 ✓ —— 因为**加载**发生在别处 ✓。
 *
 * <p><b>守护</b>：{@code ArchGuardTest.mainClass_mustNotReferenceMcClientClasses}
 * 锁定 Mod 主类不得引用 {@code net.minecraft.client.*}（= 错题 #168 唯一实证现场: 主类方法在启动/构造期即被执行,
 * 引用会被**立即解析** ✗）。另: 屏的注册已收敛到 {@code client/GuiScreenBindings} 表 + 两平台循环，
 * 由 {@code MenuScreenPairingGuardTest} 守护 ✓。
 *
 * <p><b>已知边界</b>：若未来出现第二个“启动期加载 + 引用本包”的类，本条规则会漏，需同步扩展。
 *
 * <p><b>为什么不给本包 30 个类逐个加 {@code @OnlyIn}（刻意的选择 ✓）</b>：
 * ① 它不是触发机制 ✗（见上 ✓）⇒ 30 类 × 8 行双平台 import 块 = 约 240 行 churn + 零功能收益 ✗；
 * ② 上游正在讨论“对 mod 自用 {@code @OnlyIn} 发警告甚至禁止”（neoforged/NeoForge#569）⇒ 方向相反 ✗；
 * ③ 若要真正的客户端边界，应走注入门面（先例 {@code api/MaidCodexScreenOpener} ✓）而非注解 ✗。
 *
 * <p><b>参考</b>：NeoForge {@code Dist} 文档 · Forge 论坛 “Attempted to load Screen for invalid dist DEDICATED_SERVER” ·
 * neoforged/NeoForge#569 · 本仓 `docs/lessons-learned.md` 错题 #168 与 #2026-09-21d ✓。
 */
package com.github.xiaozhaoz1.littlemaidmoreaction.screen;
