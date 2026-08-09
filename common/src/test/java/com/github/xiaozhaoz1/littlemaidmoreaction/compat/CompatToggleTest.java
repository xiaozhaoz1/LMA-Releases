package com.github.xiaozhaoz1.littlemaidmoreaction.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompatToggle 纯 JVM 测试 — package-private 钩子 (loadFrom/saveTo/serialize) 零 FML 依赖。
 *
 * <p>★ 勿"简化"设计 (测试承重):
 * <ul>
 *   <li>类加载安全依赖 {@code load()} 的 Throwable 双 catch — 纯 JVM 无 FML,
 *       {@code LittleMaidMoreAction.CONFIG_DIR} 触发 clinit 失败, 静默降级全启用</li>
 *   <li>文件路径是方法非 static 字段 — 防 <clinit> ExceptionInInitializerError 永久毒化类</li>
 * </ul>
 *
 * <p>不测 (FML 绑定, 与 TaskToggle 同理由): setModuleEnabled/save 直调 (写 CONFIG_DIR);
 * 构造期门控 (需真实 mod 加载 — 编译 + 手动验证)。
 */
class CompatToggleTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("缺失文件 = 全启用 (向后兼容)")
    void loadFrom_missingFile_allEnabled() throws IOException {
        CompatToggle.loadFrom(tmp.resolve("absent.json"));
        assertTrue(CompatToggle.isModuleEnabled("create"));
        assertTrue(CompatToggle.isModuleEnabled("numen"));
        assertTrue(CompatToggle.isModuleEnabled("createbigcannons"));
        assertTrue(CompatToggle.disabledModules().isEmpty());
    }

    @Test
    @DisplayName("解析 disabled 列表")
    void loadFrom_disabledList_parses() throws IOException {
        Path f = tmp.resolve("c.json");
        Files.writeString(f, "{\"disabled\":[\"create\",\"numen\"]}");
        CompatToggle.loadFrom(f);
        assertFalse(CompatToggle.isModuleEnabled("create"));
        assertFalse(CompatToggle.isModuleEnabled("numen"));
        assertTrue(CompatToggle.isModuleEnabled("createbigcannons"));
        assertEquals(Set.of("create", "numen"), CompatToggle.disabledModules());
    }

    @Test
    @DisplayName("saveTo + loadFrom roundtrip")
    void saveTo_loadFrom_roundtrip() throws IOException {
        Path f = tmp.resolve("rt.json");
        // 构造状态: serialize 写文件再 loadFrom — 状态从文件重建 (setModuleEnabled 直调 FML 绑定不可测)
        Files.writeString(f, CompatToggle.serialize(Set.of("create", "createbigcannons")));
        CompatToggle.loadFrom(f);
        assertFalse(CompatToggle.isModuleEnabled("create"));
        assertFalse(CompatToggle.isModuleEnabled("createbigcannons"));
        assertTrue(CompatToggle.isModuleEnabled("numen"));
        // 再存回 — 序列化幂等
        Path f2 = tmp.resolve("rt2.json");
        CompatToggle.saveTo(f2);
        CompatToggle.loadFrom(f2);
        assertEquals(Set.of("create", "createbigcannons"), CompatToggle.disabledModules());
    }

    @Test
    @DisplayName("畸形 JSON 不抛且状态不变")
    void loadFrom_malformed_noThrow() throws IOException {
        Path f = tmp.resolve("bad.json");
        Files.writeString(f, "{\"disabled\":[\"create\"");
        CompatToggle.loadFrom(f);   // 无 ] → 解析中止, 不抛
        assertTrue(CompatToggle.disabledModules().isEmpty());
        Files.writeString(f, "garbage not json");
        CompatToggle.loadFrom(f);   // 无 disabled 键 → 空
        assertTrue(CompatToggle.disabledModules().isEmpty());
    }

    @Test
    @DisplayName("未知 id 惰性存储, 不影响已知模块")
    void loadFrom_unknownId_inert() throws IOException {
        Path f = tmp.resolve("u.json");
        Files.writeString(f, "{\"disabled\":[\"slashblade\"]}");
        CompatToggle.loadFrom(f);
        assertFalse(CompatToggle.isModuleEnabled("slashblade"));
        assertTrue(CompatToggle.isModuleEnabled("create"));
        assertTrue(CompatToggle.disabledModules().contains("slashblade"));
    }
}
