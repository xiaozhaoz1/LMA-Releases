package com.github.xiaozhaoz1.littlemaidmoreaction.defense.client;
import com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlockEntity;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlockEntity;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;

//? if 1.20.1 {
import net.minecraftforge.fml.ModList;
//?} else {
import net.neoforged.fml.ModList;
//?}

import java.util.HashMap;
import java.util.Map;

/**
 * 防御塔手办动作显示 — **YSM 通道**客户端驱动 (v79.65, 用户裁定: 瞄准持续 + 开火触发)。
 *
 * <p><b>两条通道分工</b>:
 * <ol>
 *   <li><b>原生 gecko 模型</b>: 塔 BE 把动画键写进手办 NBT (getExtraData) ⇒ 渲染时
 *       {@code entity.load(data)} 灌进"渲染用假女仆"的 PersistentData ⇒ LMA 既有
 *       {@code LmaMagicCastingProvider} 每帧读 {@code lma_anim_mode/name/seq} 直接播 {@code iss:*} —
 *       **本类不参与** (零重复驱动)。</li>
 *   <li><b>YSM 模型</b> (isYsmModel): YSM 自管渲染, 不经过 gecko 通道 ⇒ 需要本类把同一份键
 *       翻译成 YSM 轮盘调用 {@code maid.playRouletteAnim(name)} / {@code stopRouletteAnim()}。</li>
 * </ol>
 *
 * <p><b>为什么这样做</b>: 手办没有真实女仆实体 (无法走服务端 SyncYsmMaidDataMessage), 只能在客户端对
 * {@link EntityCacheUtil#STATUE_CACHE} 里的渲染用假女仆直接施加动画 —— 该做法由开源模组
 * TouhouLittleMaidStatueAnimation 实证 (其 StatueAnimationApplier 同款路径)。
 *
 * <p><b>与 StatueAnimation 模组的避让 (用户裁定: 检测到对方在播 ⇒ 让位)</b>:
 * <ul>
 *   <li>只读**我们自己的键**驱动, 绝不写对方状态键 {@code YsmRouletteAnim}/{@code StatueRoulettePlaying} ✓</li>
 *   <li>只处理 {@link DefenseGarageKitBlockEntity} (防御塔) — TLM 原生雕像与普通手办不碰 ✓</li>
 *   <li>若对方正在驱动该手办 ({@code StatueRoulettePlaying == true}) ⇒ 本拍**跳过**, 控制权让给对方 ✓</li>
 * </ul>
 *
 * <p><b>seq 感知</b>: 开火动作名重复 (每发都是 {@code iss:instant_projectile}) ⇒ 只比名字不会重播 ✗,
 * 因此按 {@code lma_anim_seq} 变化触发 (与 AnimExecute / provider 同一契约 ✓)。
 */
public final class TowerStatueAnimationDriver {

    private TowerStatueAnimationDriver() {}

    /** 参考模组(StatueAnimation)的"正在播"标志键 — 仅**读**用于让位 */
    private static final String THEIR_PLAYING = "StatueRoulettePlaying";

    /** per-手办 上次施加记录 (key = 方块位置 asLong) — 防每帧重发 */
    private record Applied(String anim, int seq, boolean playing) {}

    private static final Map<Long, Applied> LAST = new HashMap<>();

    /** 客户端每 tick 调用 (两端入口注册) — 零开销: 只遍历"正在被渲染的"手办缓存 */
    public static void onClientTick() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            LAST.clear();
            return;
        }
        if (!ModList.get().isLoaded("yes_steve_model")) {
            return;   // 未装 YSM ⇒ 无轮盘通道; 原生通道不受影响
        }
        try {
            var cache = EntityCacheUtil.STATUE_CACHE.asMap();
            for (Map.Entry<Long, EntityMaid> e : cache.entrySet()) {
                EntityMaid fakeMaid = e.getValue();
                if (fakeMaid == null || !fakeMaid.isYsmModel()) continue;   // 原生模型走 ISS 通道
                // 用户裁定: iss 动画文件**直接在游戏目录里找** ⇒ 文件夹形态模型可先验可用性,
                //   没有 iss 就不发无效请求 (并打一行说明: 该包需改 fire_anim/aim_anim 名) ✓
                YsmAnimAvailability.Avail avail = YsmAnimAvailability.resolve(fakeMaid.getYsmModelId());
                YsmAnimAvailability.logOnce(fakeMaid.getYsmModelId(), avail);
                if (avail == YsmAnimAvailability.Avail.MISSING) continue;
                var be = mc.level.getBlockEntity(net.minecraft.core.BlockPos.of(e.getKey()));
                if (!(be instanceof DefenseGarageKitBlockEntity tower)) continue;   // 只认防御塔

                long key = e.getKey();
                var data = tower.getExtraData();
                if (data.getBoolean(THEIR_PLAYING)) {
                    // 让位: 参考模组正在驱动该手办 ⇒ 本拍不插手 (并清记录, 等它停了再接管)
                    Applied last = LAST.remove(key);
                    if (last != null && last.playing()) {
                        safeStop(fakeMaid);
                    }
                    continue;
                }

                // 动画键在**实体持久数据子标签**里 (ForgeData/NeoForgeData) — 与原生通道同源 ✓
                var animPd = tower.animData();
                String mode = animPd.getString(TaskKeys.ANIM_MODE);
                String anim = animPd.getString(TaskKeys.ANIM_NAME);
                int seq = animPd.getInt(TaskKeys.ANIM_SEQ);
                boolean want = ("INSTANT".equals(mode) || "FULL".equals(mode)) && !anim.isEmpty();

                Applied last = LAST.get(key);
                if (!want) {
                    if (last != null && last.playing()) {
                        safeStop(fakeMaid);
                        LAST.put(key, new Applied(null, seq, false));
                    }
                    continue;
                }
                if (last != null && last.playing() && anim.equals(last.anim()) && seq == last.seq()
                        && "FULL".equals(mode)) {
                    continue;   // 瞄准循环: 同一请求不重发 (YSM 自己循环)
                }
                // ★ 2026-09-20 实机定因 + 用户纠正: YSM 可播名来自**模型 animations/ 下全部动画文件**
                //   (`iss.animation.json` 的键就叫 `iss:charge_arrow` ⇒ iss:* **可播** ✓); ysm.json 的
                //   extra_animation 只是"轮盘 UI 显示项" ⇒ 不能只按它判断 (上一版按它查 = 误拦合法名 ✗)
                var playable = YsmAnimAvailability.playableNames(fakeMaid.getYsmModelId());
                if (!playable.isEmpty() && !playable.contains(anim)) {
                    YsmAnimAvailability.logUnplayable(fakeMaid.getYsmModelId(), anim, playable);
                    LAST.put(key, new Applied(anim, seq, false));
                    continue;
                }
                fakeMaid.playRouletteAnim(anim);
                LAST.put(key, new Applied(anim, seq, true));
            }
            // 缓存里消失的 (区块卸载/过期) 清记录
            LAST.keySet().removeIf(k -> !cache.containsKey(k));
        } catch (Throwable t) {
            // fail-soft: YSM/TLM 版本差异或模型无此动画 ⇒ 不播, 绝不影响塔功能 (一次性日志)
            if (!WARNED) {
                WARNED = true;
                LittleMaidMoreAction.LOGGER.warn("[LMA/TowerAnim] YSM 通道异常已忽略 (fail-soft): {}", t.toString());
            }
        }
    }

    private static boolean WARNED = false;

    private static void safeStop(EntityMaid maid) {
        try {
            maid.stopRouletteAnim();
        } catch (Throwable ignored) {
            // fail-soft
        }
    }

    /** 世界切换/断开: 清记录 (由入口在 LoggingOut 时调用) */
    public static void clearCache() {
        LAST.clear();
        WARNED = false;
    }
}
