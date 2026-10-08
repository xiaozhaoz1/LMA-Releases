package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置屏**通用动作 payload 类型**守护 (v79.64.4 用户实测「保存后 Y 变 0」的直接教训)。
 *
 * <p><b>踩过的坑</b>: {@code VoidExcavationConfigScreen} 保存最低高度时**绕过基类助手**手写
 * {@code makePayload(...)} — 用 {@code putString("value", val)} 配 {@code ACTION_SET_INT},
 * 而服务端是 {@code cfg.putInt(key, payload.getInt("value"))} ⇒ 对 StringTag 取 int **得 0** ✗
 * ⇒ 用户设的 Y 被存成 0 (= 挖到世界底部, 覆盖"自动检测基岩层")，且**屏上回读也变 0** ✓ 现象完全吻合。
 *
 * <p><b>契约</b> (task/gui/README.md §二): 通用动作必须经基类助手
 * ({@code sendSetInt} / {@code sendRemove} / {@code sendToggle} / {@code sendSetString} / {@code sendSetList})
 * 发送 — 它们保证 payload 字段类型与服务端 {@code handleConfigAction} 的解析一致 ✓。
 * 自定义任务动作 (常量 ≥16, 定义在各自管线) 不受本守护限制 ✓。
 */
class GuiActionPayloadGuardTest {

    /** 通用动作常量名 (TaskConfigurable) — 只允许基类 LmaTaskConfigScreen 引用 */
    private static final List<String> GENERIC_ACTIONS = List.of(
            "TaskConfigurable.ACTION_TOGGLE",
            "TaskConfigurable.ACTION_SET_INT",
            "TaskConfigurable.ACTION_REMOVE",
            "TaskConfigurable.ACTION_SET_LIST",
            "TaskConfigurable.ACTION_SET_STRING");

    @Test
    @DisplayName("通用动作必须经基类助手发送 (禁屏内手写 payload — 类型易错)")
    void genericActionsOnlyViaBaseHelpers() throws IOException {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(Files.isDirectory(root), "源码树不可见 (打包环境) — 跳过");

        List<String> bad = new ArrayList<>();
        Path guiDir = root.resolve("task").resolve("gui");
        try (Stream<Path> files = Files.list(guiDir)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith("Screen.java")).toList()) {
                String name = p.getFileName().toString();
                if (name.equals("LmaTaskConfigScreen.java")) continue;   // 基类 = 助手的唯一定义处 ✓
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) continue;
                    for (String act : GENERIC_ACTIONS) {
                        if (line.contains(act)) {
                            bad.add(name + ":" + (i + 1) + " 直接引用 " + act
                                    + " — 应改用基类助手 (sendSetInt/sendRemove/sendToggle/sendSetString/sendSetList)");
                        }
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "通用动作必须经基类助手发送 (payload 类型契约, 见 task/gui/README.md §二): " + bad);
    }
}
