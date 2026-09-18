package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网络包注册清单一致性测试 (批次 A4) — {@link PacketRegistry#DEFS} 不变量 + 双平台注册面对齐。
 *
 * <p>测试跑在 forge 节点 → DEFS 为 1.20.1 视图 (21 项, 2 个 neoOnly 项被 stonecutter 剥离)。
 * 清单 ↔ 驱动注册 map 的等价断言: 运行时由 {@link PacketRegistry#validatePlatformNames} 在
 * forge commonSetup / neoforge RegisterPayloadHandlersEvent 时 fail-fast (本测试引
 * LittleMaidMoreAction 会触发 FMLPaths 类加载, 禁 — 单测铁律); 本测试只验清单侧 + 校验器自身。</p>
 *
 * <p>纯 JVM 铁律: 只读静态清单; PacketRegistry 类加载仅解析包类字面量
 * (NetworkCodecRoundTripTest 先例实证安全 — 包类静态字段零 MC 耦合)。</p>
 */
class NetworkPacketManifestTest {

    /** forge 可见 ID 表 (R-04: 空洞 1/20 = 历史已删包, 不回收; id 4 已回收给 patpat_reaction — v79.61x; 16-19 = 作物区域 v79.62; 21/22 = 五子棋开关 v79.62.3; 20 = smithing 锻造任务整体删除 — v79.63 用户裁定) */
    private static final Set<Integer> FORGE_IDS = Set.of(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22);

    /** forge 可见 name 表 — 与 ForgePacketRegistrar.REGISTRATIONS keySet 必须全等 (运行时校验兜底) */
    private static final Set<String> FORGE_NAMES = Set.of(
            "anim_sync", "interact_trigger", "task_config_action", "patpat_reaction",
            "request_task_config",
            "reply_task_config", "config_sync", "config_sync_s2c", "anim_file_sync",
            "haqi_owner_voice", "maid_chat_bubble", "maid_list_query", "maid_list_response",
            "maid_codex_screen", "maid_env_sense_toggle", "farm_region_edit", "farm_region_sync",
            "farm_container_bind", "farm_region_bind",
            "maid_gomoku_toggle", "maid_gomoku_state");

    /** forge 视图条目 (neoOnly 在 1.20.1 视图不存在 — 全项可见) */
    private static List<PacketDef> forgeDefs() {
        return PacketRegistry.DEFS.stream().filter(PacketDef::forgeVisible).collect(Collectors.toList());
    }

    @Test
    @DisplayName("清单 ID 无重复 (forge 视图; neoOnly 项无 forge ID 用 -1 不参与)")
    void ids_unique() {
        Set<Integer> ids = forgeDefs().stream().map(PacketDef::id).collect(Collectors.toSet());
        assertEquals(forgeDefs().size(), ids.size(), "ID 重复 = 注册冲突");
    }

    @Test
    @DisplayName("清单 name 无重复 (全视图 — 驱动注册 map 键必须唯一)")
    void names_unique() {
        Set<String> names = PacketRegistry.DEFS.stream().map(PacketDef::name).collect(Collectors.toSet());
        assertEquals(PacketRegistry.DEFS.size(), names.size(), "name 重复 = 驱动 map 键冲突");
    }

    @Test
    @DisplayName("forge 可见 = 21 项 (双平台包类 + ConfigSync 双条目 + 作物区域 4 + 五子棋开关 2)")
    void forgeVisible_count() {
        assertEquals(21, forgeDefs().size());
    }

    @Test
    @DisplayName("forge ID 表精确匹配 (空洞 1/20 文档化, 不回收)")
    void forge_ids_exact() {
        Set<Integer> actual = forgeDefs().stream().map(PacketDef::id).collect(Collectors.toSet());
        assertEquals(FORGE_IDS, actual);
    }

    @Test
    @DisplayName("ConfigSync 双条目: id 7 C2S + id 8 S2C (同 class 双 ID 语义显式化 — R-02)")
    void configSync_twoEntries() {
        List<PacketDef> configSync = PacketRegistry.DEFS.stream()
                .filter(d -> d.type() == ConfigSyncPacket.class)
                .collect(Collectors.toList());
        assertEquals(2, configSync.size(), "ConfigSync 必须恰 2 条 (7 C2S / 8 S2C)");
        assertTrue(configSync.stream().anyMatch(d -> d.id() == 7 && d.direction() == PacketDef.Direction.C2S));
        assertTrue(configSync.stream().anyMatch(d -> d.id() == 8 && d.direction() == PacketDef.Direction.S2C));
    }

    @Test
    @DisplayName("方向计数: 11 C2S / 10 S2C (forge 视图; -smithing_craft C2S v79.63)")
    void direction_counts() {
        assertEquals(11, forgeDefs().stream().filter(d -> d.direction() == PacketDef.Direction.C2S).count());
        assertEquals(10, forgeDefs().stream().filter(d -> d.direction() == PacketDef.Direction.S2C).count());
    }

    @Test
    @DisplayName("每项 type 非空 (驱动注册按 type 分发)")
    void type_notNull() {
        assertTrue(PacketRegistry.DEFS.stream().allMatch(d -> d.type() != null));
    }

    @Test
    @DisplayName("validatePlatformNames 与预期 forge 名集一致 (零漂移)")
    void validate_ok() {
        assertDoesNotThrow(() -> PacketRegistry.validatePlatformNames(FORGE_NAMES, "forge-test"));
    }

    @Test
    @DisplayName("validatePlatformNames 抓缺注册 (删 1 名 → 抛)")
    void validate_missing_throws() {
        Set<String> partial = FORGE_NAMES.stream()
                .filter(n -> !n.equals("anim_sync"))
                .collect(Collectors.toSet());
        assertThrows(IllegalStateException.class,
                () -> PacketRegistry.validatePlatformNames(partial, "test"));
    }

    @Test
    @DisplayName("validatePlatformNames 抓幽灵条目 (多 1 名 → 抛)")
    void validate_phantom_throws() {
        Set<String> phantom = new HashSet<>(FORGE_NAMES);
        phantom.add("ghost_packet");
        assertThrows(IllegalStateException.class,
                () -> PacketRegistry.validatePlatformNames(phantom, "test"));
    }
}
