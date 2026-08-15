package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 网络包注册清单 (批次 A — 共享包清单单一事实源; v79.51 前: forge ID 硬编码 14 条手写 +
 * neoforge 16 条字符串 TYPE 手写, 仅注释对应 — R-02/03/04)。
 *
 * <p><strong>加新包步骤</strong>: ① 此处登记一条 (ID 从 16 起分配, 空洞不回收) → ② 双平台驱动
 * 注册 map 各加一行 (forge {@code ForgePacketRegistrar} / neoforge {@code NeoNetworkHandler})
 * → ③ 清单测试 {@code NetworkPacketManifestTest} 覆盖。漏 ② 由驱动启动时
 * {@link #validatePlatformNames} fail-fast 兜底。</p>
 *
 * <p><strong>ID 空洞 1/4</strong>: 历史已删包, 不回收复用 — 防旧客户端残留包错配到新包
 * (R-04 文档化)。</p>
 *
 * <p>neoforge 独有 2 项 (numen_companions / maid_voice) 在 stonecutter {@code !1.20.1} 分支 —
 * Numen 兼容模块仅 1.21.1 (COMMON.md §10 隔离对), forge 节点编译时剥离, 无类引用。</p>
 *
 * <p><strong>数组初始化器而非 List.of 参数列表</strong> (2026-08-11c 编译实证): stonecutter
 * 条件块注释化后条目尾随逗号残留 — List.of 参数列表禁尾随逗号 (语法错), 数组初始化器允许 —
 * 故 DEFS_RAW 用数组, DEFS = List.of(DEFS_RAW) 供清单消费。</p>
 */
public final class PacketRegistry {

    /**
     * ConfigSyncPacket 同 class 双注册 (R-02/R-05): forge ID 7=C2S / 8=S2C ↔ neoforge
     * TYPE config_sync / config_sync_s2c — 清单拆两条, 驱动循环零特判, 双向自然成立。
     */
    /** 原始清单 — 数组初始化器 (允许尾随逗号, 见类注释) */
    private static final PacketDef[] DEFS_RAW = {
            new PacketDef(0, "anim_sync", LmaAnimSyncMessage.class, PacketDef.Direction.S2C, false),
            new PacketDef(2, "interact_trigger", InteractTriggerPacket.class, PacketDef.Direction.C2S, false),
            new PacketDef(3, "task_config_action", TaskConfigActionPacket.class, PacketDef.Direction.C2S, false),
            new PacketDef(5, "request_task_config", RequestTaskConfigPacket.class, PacketDef.Direction.C2S, false),
            new PacketDef(6, "reply_task_config", ReplyTaskConfigPacket.class, PacketDef.Direction.S2C, false),
            new PacketDef(7, "config_sync", ConfigSyncPacket.class, PacketDef.Direction.C2S, false),
            new PacketDef(8, "config_sync_s2c", ConfigSyncPacket.class, PacketDef.Direction.S2C, false),
            new PacketDef(9, "anim_file_sync", AnimFileSyncPacket.class, PacketDef.Direction.S2C, false),
            new PacketDef(10, "haqi_owner_voice", HaqiOwnerVoicePacket.class, PacketDef.Direction.S2C, false),
            new PacketDef(11, "maid_chat_bubble", MaidChatBubblePacket.class, PacketDef.Direction.S2C, false),
            new PacketDef(12, "maid_list_query", MaidListQueryPacket.class, PacketDef.Direction.C2S, false),
            new PacketDef(13, "maid_list_response", MaidListResponsePacket.class, PacketDef.Direction.S2C, false),
            new PacketDef(14, "maid_codex_screen", MaidCodexScreenPacket.class, PacketDef.Direction.S2C, false),
            new PacketDef(15, "maid_env_sense_toggle", MaidEnvSenseTogglePacket.class, PacketDef.Direction.C2S, false),
            //? if !1.20.1 {
            new PacketDef(-1, "numen_companions", NumenCompanionSyncPayload.class, PacketDef.Direction.S2C, true),
            new PacketDef(-1, "maid_voice", LmaMaidVoicePayload.class, PacketDef.Direction.S2C, true)
            //?}
    };

    /** 清单消费视图 (不可变 List) — 驱动循环/校验/测试统一入口 */
    public static final List<PacketDef> DEFS = List.of(DEFS_RAW);

    /**
     * 平台注册面校验 (R-03 漂移防线) — 驱动注册循环前调用; name 集合与清单不等 = 新包
     * 漏接线/幽灵条目, fail-fast。forge 节点视图 14 项 (neoOnly 剥离) → 传 forge map 键;
     * neoforge 视图 16 项 → 传 neoforge map 键。
     */
    public static void validatePlatformNames(Set<String> registeredNames, String platform) {
        Set<String> expected = DEFS.stream().map(PacketDef::name).collect(Collectors.toSet());
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(registeredNames);
        Set<String> phantom = new HashSet<>(registeredNames);
        phantom.removeAll(expected);
        if (!missing.isEmpty() || !phantom.isEmpty()) {
            throw new IllegalStateException("[LMA] 网络注册表漂移 (" + platform + "): 缺注册 " + missing
                    + ", 幽灵条目 " + phantom);
        }
    }

    private PacketRegistry() {
    }
}
