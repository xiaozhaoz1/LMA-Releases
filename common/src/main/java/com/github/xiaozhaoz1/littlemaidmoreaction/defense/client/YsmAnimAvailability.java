package com.github.xiaozhaoz1.littlemaidmoreaction.defense.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

//? if 1.20.1 {
import net.minecraftforge.fml.loading.FMLPaths;
//?} else {
import net.neoforged.fml.loading.FMLPaths;
//?}

/**
 * YSM 动画可用性解析 (v79.65) — **与 {@code YsmAnimInjector} 同一套地址**, 在模型包的
 * {@code animations/} 目录里**直接查 {@code iss.animation.json}** (用户裁定 ✓)。
 *
 * <p><b>为什么这样最对</b>: LMA 的 YSM 注入器 (YsmAnimInjector) 已经在遍历
 * {@code FMLPaths.CONFIGDIR/yes_steve_model/builtin} 下所有"含 animations/ 的模型包"并往该目录写
 * {@code extra.animation.json} —— 而 {@code iss.animation.json} **就在同一个文件夹里** ⇒ 用同一套
 * 寻址 (同一根目录 + 同一层递归 + 同一相对路径即模型 id) 即可直接判断 iss 动画有无 ✓
 * (用户 2026-09-19 两次纠正: iss 不走 ysm.json 声明, 文件就在注入器那个地址旁边 ⇒ 直接看 ✓)
 *
 * <p><b>模型 id 约定</b>: YSM 女仆的 {@code getYsmModelId()} 形如 {@code wine_fox/01_taisho_maid}
 * (相对 {@code builtin/} 的路径 — 与 YsmOutput 内置预设表一致) ⇒ 本类按该相对路径查表。
 *
 * <p><b>覆盖范围</b>: builtin 文件夹形态 (= 注入器能覆盖的全部) ✓;
 * {@code custom/*.ysm} 加密包 (实测非 zip: UTF-8 BOM + 密文) ⇒ {@link Avail#UNKNOWN},
 * 调用方照常尝试 (YSM 侧无副作用) ✓。
 */
public final class YsmAnimAvailability {

    private YsmAnimAvailability() {}

    public enum Avail {
        /** 同一 animations/ 目录里确实有 iss.animation.json ⇒ iss:* 名可用 */
        PRESENT,
        /** 模型包在 builtin 里但**没有** iss 文件 ⇒ iss:* 一定播不出来 */
        MISSING,
        /** 无法判断 (custom 加密包/目录未就绪/IO 异常) ⇒ 照常尝试, YSM 兜底 */
        UNKNOWN
    }

    /** 模型 id (相对 builtin, 用 '/' 分隔) → 是否有 iss 动画文件; null = 索引未建 */
    private static Map<String, Boolean> INDEX = null;
    private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();

    /** 解析某 YSM 模型 id 是否带 iss 动画 */
    public static Avail resolve(String modelId) {
        if (modelId == null || modelId.isEmpty()) return Avail.UNKNOWN;
        Map<String, Boolean> idx = index();
        if (idx == null) return Avail.UNKNOWN;
        String key = modelId.replace('\\', '/');
        Boolean hasIss = idx.get(key);
        if (hasIss == null) return Avail.UNKNOWN;   // 不在 builtin (custom 包/未知) ⇒ 交 YSM 兜底
        return hasIss ? Avail.PRESENT : Avail.MISSING;
    }

    /** 首次解析时打一行 (用户可见: 为什么这个手办不动) */
    public static void logOnce(String modelId, Avail avail) {
        if (avail != Avail.MISSING || !LOGGED.add(modelId)) return;
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.info(
                "[LMA/TowerAnim] YSM 模型 {} 的 animations/ 目录里没有 iss.animation.json ⇒ 塔动作跳过 "
                        + "(该包如需动作, 请把配置 fire_anim/aim_anim 改成包内实际动画名)", modelId);
    }

    /** 建立/取索引 — 与注入器同根同层 (builtin, 深度 3, 只认含 animations/ 的目录) */
    private static Map<String, Boolean> index() {
        if (INDEX != null) return INDEX;
        try {
            Path builtin = FMLPaths.CONFIGDIR.get().resolve("yes_steve_model").resolve("builtin");
            if (!Files.isDirectory(builtin)) {
                // 目录未就绪 (YSM 解压竞态) ⇒ 不缓存, 下次再试
                return null;
            }
            Map<String, Boolean> map = new HashMap<>();
            try (Stream<Path> stream = Files.walk(builtin, 3)) {
                stream.filter(Files::isDirectory)
                        .filter(p -> Files.isDirectory(p.resolve("animations")))
                        .forEach(p -> {
                            String id = builtin.relativize(p).toString().replace('\\', '/');
                            map.put(id, Files.isRegularFile(p.resolve("animations").resolve("iss.animation.json")));
                        });
            }
            INDEX = map;
            return INDEX;
        } catch (IOException | RuntimeException e) {
            return null;   // fail-soft: 无法判断 ⇒ 交 YSM 兜底
        }
    }

    /** 世界切换/重载/断开: 清索引 (模型包可能被 YSM 重装) */
    public static void clearCache() {
        INDEX = null;
        LOGGED.clear();
        PLAYABLE.clear();
        UNPLAYABLE_LOGGED.clear();
    }

    // ── 可播动画名表 (YSM 手办实际可播的集合) ─────────────────────────────────────
    //
    // 关键机制 (2026-09-20 用户两次纠正 + 实机文件实证):
    //   ① YSM 的动画名来自**模型 animations/ 目录下全部 *.animation.json** —
    //      `iss.animation.json` 里的键就叫 `iss:charge_arrow` (前缀写在键名里), 同理 `tac:*` / `carryon:*` ✓
    //      ⇒ **`iss:*` 是可播的** (`ysm.json` 的 `extra_animation` 只管"轮盘 UI 显示哪些", **不是**可播集合 ✗)
    //   ② 该目录与 `iss.animation.json` 同处一处 ⇒ **未加密, 直接读** ✓ (builtin 文件夹形态)
    //   ⇒ 本类把"该模型全部动画名"索引出来, 供调用方判断"这名字能不能播" ✓

    private static final Map<String, java.util.List<String>> PLAYABLE = new HashMap<>();
    private static final java.util.Set<String> UNPLAYABLE_LOGGED = new java.util.HashSet<>();

    /** 取某模型**全部可播动画名** (空表 = 未知/读不到 ⇒ 调用方照常尝试) */
    public static java.util.List<String> playableNames(String modelId) {
        if (modelId == null || modelId.isEmpty()) return java.util.List.of();
        var cached = PLAYABLE.get(modelId);
        if (cached != null) return cached;
        java.util.List<String> names = new java.util.ArrayList<>();
        try {
            Path root = FMLPaths.CONFIGDIR.get().resolve("yes_steve_model").resolve("builtin");
            Path modelDir = root.resolve(modelId.replace('\\', '/'));
            Path animDir = modelDir.resolve("animations");
            if (Files.isDirectory(animDir)) {
                try (Stream<Path> files = Files.list(animDir)) {
                    for (Path p : files.toList()) {
                        if (!p.getFileName().toString().endsWith(".animation.json")) continue;
                        try (var reader = Files.newBufferedReader(p)) {
                            var obj = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                            var anims = obj.getAsJsonObject("animations");
                            if (anims == null) continue;
                            for (var e : anims.entrySet()) {
                                if (!names.contains(e.getKey())) names.add(e.getKey());
                            }
                        } catch (Throwable ignored) {
                            // 单文件失败 ⇒ 跳过
                        }
                    }
                }
            }
            // 轮盘表条目一并并入 (个别包把名直接写在 ysm.json extra_animation 里)
            Path ysmJson = modelDir.resolve("ysm.json");
            if (Files.isRegularFile(ysmJson)) {
                try (var reader = Files.newBufferedReader(ysmJson)) {
                    var obj = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    var props = obj.getAsJsonObject("properties");
                    var extra = props != null ? props.getAsJsonObject("extra_animation") : null;
                    if (extra != null) {
                        for (var e : extra.entrySet()) {
                            if (!names.contains(e.getKey())) names.add(e.getKey());
                        }
                    }
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
        } catch (Throwable ignored) {
            // fail-soft
        }
        var result = java.util.List.copyOf(names);
        PLAYABLE.put(modelId, result);
        return result;
    }

    /** 请求名不在可播表里时打一行 (列可用名; 每模型一次) */
    public static void logUnplayable(String modelId, String requested, java.util.List<String> available) {
        if (!UNPLAYABLE_LOGGED.add(modelId)) return;
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.info(
                "[LMA/TowerAnim] YSM 模型 {} 没有动画 {} — 可播名(前 12): {}",
                modelId, requested, available.size() > 12 ? available.subList(0, 12) + " …" : available);
    }

    // ── "该模型是否自带弓" 判定 (2026-09-20 用户裁定: 不同模型弓的来源不同) ─────────────
    //
    // 实测: `05_magical` 的 use_mainhand:bow 只动手臂骨 (⇒ 弓必须由**物品**渲染);
    //       而 `21_saint`(圣女酒狐) / `22_elf` 的同名动画动了 **`ysmGlow_MagicBow`** 这类**模型自带弓骨**
    //       (⇒ 模型自己画弓; 再塞原版弓物品就**多一把** ✗ 用户原话: "圣女酒狐用自己生成的弓来拉")。
    // ⇒ 自动判定: 该动画骨骼名含 bow/arrow/魔法弓 ⇒ **模型自带弓** ⇒ 不画原版武器 ✓
    private static final Map<String, Boolean> OWN_BOW = new HashMap<>();

    /** 模型在给定动画里是否自带弓 (骨骼名含 bow/arrow); 读不到 ⇒ false (保守: 画物品, 与 TLM 原版一致) */
    public static boolean hasOwnBowInAnimation(String modelId, String animName) {
        if (modelId == null || modelId.isEmpty() || animName == null || animName.isEmpty()) return false;
        String key = modelId + "#" + animName;
        Boolean cached = OWN_BOW.get(key);
        if (cached != null) return cached;
        boolean result = false;
        try {
            Path animDir = FMLPaths.CONFIGDIR.get().resolve("yes_steve_model").resolve("builtin")
                    .resolve(modelId.replace('\\', '/')).resolve("animations");
            if (Files.isDirectory(animDir)) {
                try (Stream<Path> files = Files.list(animDir)) {
                    for (Path p : files.toList()) {
                        if (!p.getFileName().toString().endsWith(".animation.json")) continue;
                        try (var reader = Files.newBufferedReader(p)) {
                            var obj = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                            var anims = obj.getAsJsonObject("animations");
                            var anim = anims != null ? anims.getAsJsonObject(animName) : null;
                            var bones = anim != null ? anim.getAsJsonObject("bones") : null;
                            if (bones == null) continue;
                            for (var e : bones.entrySet()) {
                                if (e.getKey().matches("(?i).*(bow|arrow|魔法弓).*")) {
                                    result = true;
                                    break;
                                }
                            }
                        } catch (Throwable ignored) {
                            // 单文件失败 ⇒ 跳过
                        }
                        if (result) break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // fail-soft
        }
        OWN_BOW.put(key, result);
        return result;
    }

    /** 清自带弓判定缓存 (世界切换/重载) */
    public static void clearBowCache() {
        OWN_BOW.clear();
    }
}
