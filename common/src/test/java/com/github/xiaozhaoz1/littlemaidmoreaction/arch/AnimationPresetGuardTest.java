package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationPresetNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动画预设守护 (v79.70 — 错题 #356: 外部玩家加载崩溃 + 预设精简)。
 *
 * <p>机械拦两类真实事故:
 * <ol>
 *   <li><b>关键帧非升序</b> — GeckoLib 4 解析 <b>MAP 形式</b>动画时按**文件中的键序**建关键帧表,
 *       首键跑到最前 (实证: `dodge.animation.json` 的 `animation.flash1` 是 `'1'` → `'-0.03'` → `0.3 …`)
 *       会被判 `Invalid keyframe data - multiple starting keyframes?` ⇒ **加载即崩溃** ✗
 *       (同文件的 `flash2` 正常升序; 全仓 1100+ 通道仅这 13 处异常 ✓)</li>
 *   <li><b>清单漂移</b> — 随包预设 ({@code AnimationPresetNames.SHIPPED}) 必须与 resources 目录实际文件一致;
 *       废弃预设 ({@code .OBSOLETE}) 不得再随包 (老存档残留由 {@code migrateObsolete} 挪去 `removed/`) ✓</li>
 * </ol>
 *
 * <p>另断言 DEBUG 自检表 ({@code AnimationDurationManager.FALLBACK_ANIMATIONS}) 的键都在随包动画里。
 *
 * <p>⚠ 本测试**刻意只用** {@code api.AnimationPresetNames} (零 MC/FML 依赖) — {@code storage.StartupLoader}
 * 的静态初始化需要 FML (`FMLPaths.CONFIGDIR`) ⇒ 在纯 JUnit 下 `<clinit>` 会抛 ExceptionInInitializerError ✗
 */
class AnimationPresetGuardTest {

    private static Path repoRoot() {
        Path p = ArchSource.findMainRoot();                 // <repo>/common/src/main/java
        for (int i = 0; i < 8 && p != null; i++) {
            if (Files.isRegularFile(p.resolve("docs").resolve("ARCHITECTURE.md"))) return p;
            p = p.getParent();
        }
        return null;
    }

    private static Path animDir() {
        Path repo = repoRoot();
        assertTrue(repo != null, "仓库根定位失败 (docs/ARCHITECTURE.md 未找到) — 本守护失效 ✗");
        return repo.resolve("common/src/main/resources/assets/littlemaidmoreaction/animations");
    }

    private static List<Path> shippedAnimationFiles() throws IOException {
        Path dir = animDir();
        assertTrue(Files.isDirectory(dir), "动画目录不存在: " + dir);
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".animation.json"))
                    .sorted()
                    .forEach(out::add);
        }
        return out;
    }

    /** 一个通道的关键帧时间 (对象键形式 或 数组 time 字段形式) */
    private static List<Double> keyframeTimes(JsonElement channel) {
        List<Double> times = new ArrayList<>();
        if (channel == null) return times;
        if (channel.isJsonArray()) {
            for (JsonElement e : channel.getAsJsonArray()) {
                if (e.isJsonObject() && e.getAsJsonObject().has("time")) {
                    times.add(e.getAsJsonObject().get("time").getAsDouble());
                }
            }
        } else if (channel.isJsonObject()) {
            for (String k : channel.getAsJsonObject().keySet()) {
                try {
                    times.add(Double.parseDouble(k));
                } catch (NumberFormatException ignored) {
                    // 非时间键 — 跳过
                }
            }
        }
        return times;
    }

    @Test
    @DisplayName("随包动画关键帧必须升序 (MAP 形式按键序解析 — 首键异常会崩 GeckoLib)")
    void keyframesAreAscending() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path file : shippedAnimationFiles()) {
            JsonObject anims = JsonParser.parseString(Files.readString(file))
                    .getAsJsonObject().getAsJsonObject("animations");
            if (anims == null) {
                bad.add(file.getFileName() + ": 缺 animations 段");
                continue;
            }
            for (String animName : anims.keySet()) {
                JsonObject anim = anims.getAsJsonObject(animName);
                JsonObject bones = anim.has("bones") ? anim.getAsJsonObject("bones") : null;
                if (bones == null) continue;
                for (String bone : bones.keySet()) {
                    JsonObject chans = bones.getAsJsonObject(bone);
                    for (String chan : chans.keySet()) {
                        List<Double> times = keyframeTimes(chans.get(chan));
                        for (int i = 1; i < times.size(); i++) {
                            if (times.get(i) <= times.get(i - 1)) {
                                bad.add(file.getFileName() + " → " + animName + " → " + bone + "." + chan
                                        + ": 第 " + (i + 1) + " 个关键帧 " + times.get(i)
                                        + " 不大于前一个 " + times.get(i - 1) + " (非升序 ⇒ 会崩)");
                                break;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "动画关键帧非升序 ✗ (GeckoLib 报 'multiple starting keyframes' 的根因):\n  "
                + String.join("\n  ", bad));
    }

    @Test
    @DisplayName("随包预设清单 == resources 实际文件; 废弃预设不得再随包")
    void presetListMatchesResources() throws IOException {
        Set<String> onDisk = new LinkedHashSet<>();
        for (Path p : shippedAnimationFiles()) onDisk.add(p.getFileName().toString());

        assertEquals(new LinkedHashSet<>(AnimationPresetNames.SHIPPED), onDisk,
                "AnimationPresetNames.SHIPPED 与 resources 目录不一致 ✗ (复制/扫描会漂移)");

        // (v79.70 起: config 目录由 AnimationPresetNames.syncConfigDir 对照 JAR 直接清理 — 见下一个测试)
    }

    @Test
    @DisplayName("DEBUG 自检表的动画键必须都在随包动画里 (防删文件忘改表)")
    void fallbackAnimationKeysAreShipped() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (Path file : shippedAnimationFiles()) {
            JsonObject anims = JsonParser.parseString(Files.readString(file))
                    .getAsJsonObject().getAsJsonObject("animations");
            if (anims != null) keys.addAll(anims.keySet());
        }
        for (String[] fb : com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationDurationManager.FALLBACK_ANIMATIONS) {
            assertTrue(keys.contains(fb[0]), "自检表动画 '" + fb[0] + "' (" + fb[1] + ") 不在随包动画里 ✗ — 表与文件已漂移");
        }
    }

    @Test
    @DisplayName("config 目录对照 JAR 清理: 不在 JAR 里的 *.animation.json 一律删除 (幂等)")
    void configDirIsSyncedWithJarPresets(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("animations");
        Files.createDirectories(dir);
        // 老玩家目录形态: 2 个在用 + 6 个已废弃 (JAR 里已无) + 1 个手工放入 — 且只该删 *.animation.json
        for (String f : AnimationPresetNames.SHIPPED) Files.writeString(dir.resolve(f), "{}");
        for (String f : List.of("execution.animation.json", "dodge.animation.json", "taunt.animation.json",
                "parry.animation.json", "man.animation.json", "ysm_slashblade.animation.json", "手工放的-old.animation.json")) {
            Files.writeString(dir.resolve(f), "{}");
        }
        Files.writeString(dir.resolve("notes.txt"), "不该被动");

        List<String> removed = AnimationPresetNames.syncConfigDir(dir);
        assertEquals(7, removed.size(), "应删除 7 个不在 JAR 内的动画文件, 实删: " + removed);

        for (String f : AnimationPresetNames.SHIPPED) {
            assertTrue(Files.exists(dir.resolve(f)), "在用预设被误删 ✗: " + f);
        }
        assertTrue(Files.exists(dir.resolve("notes.txt")), "非动画文件不该被删 ✗");
        assertTrue(AnimationPresetNames.syncConfigDir(dir).isEmpty(), "同步必须幂等 (第二次应为空)");
    }
}
