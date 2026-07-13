package littlemaidmoreaction.littlemaidmoreaction.impl.action.visual;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static littlemaidmoreaction.littlemaidmoreaction.engine.EngineUtils.*;

/**
 * 播放音效 — v9.0: 模组名随机 / 编号系列 / 逗号多选。
 *
 * <p>音效ID支持格式：</p>
 * <ul>
 *   <li>{@code modid:sound} — 精确匹配</li>
 *   <li>{@code modid:} — 从该模组所有已注册音效随机选取</li>
 *   <li>{@code a, b, c} — 逗号分隔多选，随机选一</li>
 *   <li>精确ID未找到 → 尝试 {@code modid:sound1, sound2, ...} 编号系列</li>
 * </ul>
 */
@RuleAction
public final class PlaySoundAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("sound_id", "音效ID", "minecraft:entity.player.attack.crit"),
        new TypedParam.DoubleParam("volume", "音量", 1.0),
        new TypedParam.DoubleParam("pitch", "音调", 1.0),
        new TypedParam.SelectParam("at", "播放位置", "self",
            List.of("self", "target", "owner"))
    );
    @Override public String id() { return "play_sound"; }
    @Override public String displayName() { return "播放音效"; }
    @Override public ActionCategory category() { return ActionCategory.VISUAL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> params) {
        String raw = params.getOrDefault("sound_id", "minecraft:entity.player.attack.crit");
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 1. 逗号分隔 → 随机选一
        String picked = pickComma(raw, rng);
        if (picked == null || picked.isEmpty()) return;

        // 2. 解析 → 模组名随机 / 精确匹配 / 编号系列
        String resolved = resolveSound(picked, rng);
        if (resolved == null) {
            LittleMaidMoreAction.LOGGER.warn("[PlaySound] 未找到音效: {}", picked);
            return;
        }

        ResourceLocation rl = ResourceLocation.tryParse(resolved);
        if (rl == null) return;
        SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (se == null) {
            LittleMaidMoreAction.LOGGER.warn("[PlaySound] 音效未注册: {}", resolved);
            return;
        }

        String at = params.getOrDefault("at", "self");
        net.minecraft.world.entity.Entity e = switch (at) {
            case "target" -> ctx.target();
            case "owner"  -> ctx.maid().getOwner();
            default       -> ctx.maid();
        };
        if (e == null) e = ctx.maid();
        float vol = parseFloat(params.getOrDefault("volume", "1.0"), 1.0f);
        float pit = parseFloat(params.getOrDefault("pitch", "1.0"), 1.0f);
        ctx.maid().level().playSound(null, e.getX(), e.getY(), e.getZ(),
            se, SoundSource.PLAYERS, vol, pit);
    }

    // ─── 解析链 ────────────────────────────────────────────────

    /** 逗号分隔多选 → 随机选一 */
    private static String pickComma(String raw, ThreadLocalRandom rng) {
        if (!raw.contains(",")) return raw.trim();
        String[] parts = raw.split(",");
        List<String> valid = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) valid.add(t);
        }
        if (valid.isEmpty()) return raw;
        return valid.get(rng.nextInt(valid.size()));
    }

    /** 解析音效ID: 模组名随机 / 精确匹配 / 编号系列 */
    private static String resolveSound(String id, ThreadLocalRandom rng) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;

        // "modid:" → 从该模组所有已注册音效随机选取
        if (rl.getPath().isEmpty()) {
            return pickFromMod(rl.getNamespace(), rng);
        }

        // 精确匹配存在 → 直接返回
        if (ForgeRegistries.SOUND_EVENTS.containsKey(rl)) return id;

        // 尝试编号系列: modid:name → modid:name1, name2, ...
        return pickFromSeries(rl, rng);
    }

    /** 从模组命名空间下所有已注册音效随机选取 */
    private static String pickFromMod(String namespace, ThreadLocalRandom rng) {
        List<String> sounds = ForgeRegistries.SOUND_EVENTS.getEntries().stream()
            .filter(e -> e.getKey().location().getNamespace().equals(namespace))
            .map(e -> e.getKey().location().toString())
            .toList();
        if (sounds.isEmpty()) return null;
        return sounds.get(rng.nextInt(sounds.size()));
    }

    /** 编号系列: modid:name1, modid:name2, ... 收集并随机选一 */
    private static String pickFromSeries(ResourceLocation base, ThreadLocalRandom rng) {
        String ns = base.getNamespace();
        String path = base.getPath();
        List<String> found = new ArrayList<>();
        for (int i = 1; i <= 99; i++) { // 最多扫 99 个编号，遇第一个缺失 break
            ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(ns, path + i);
            if (ForgeRegistries.SOUND_EVENTS.containsKey(candidate)) {
                found.add(candidate.toString());
            } else {
                break;
            }
        }
        if (found.isEmpty()) return null;
        if (found.size() == 1) return found.get(0);
        return found.get(rng.nextInt(found.size()));
    }
}
