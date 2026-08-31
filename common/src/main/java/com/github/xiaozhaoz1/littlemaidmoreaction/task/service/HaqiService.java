package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.combat.CombatOutput;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/**
 * 哈气执行服务 (v79.61x 架构审计 B 抽取) — 挥击/音效行为细节 (原 HaqiPipeline 内联)。
 * 行为零变化 (逐行搬移); 触发/状态机留管线, 执行细节进服务。
 */
public final class HaqiService {

    private HaqiService() {}

    /** 目标类型: 对主人 (魔法伤害绕护甲) — HaqiTrigger/HaqiPipeline 共用词汇 */
    public static final String TARGET_OWNER = "owner";

    /** 音频清单 (注册于 LmaSounds) */
    public static final String[] SOUND_NAMES = {
            "ha_1", "ha_2", "ha_3", "ha_4", "ha_5",
            "laowu_1", "laowu_2", "laowu_3", "laowu_4", "laowu_5"};

    /** 音频时长 (tick, ogg granule 实证: ha 22-45t / laowu 91-256t) — 总看着时长 = 基础 + 音频 */
    public static final Map<String, Integer> SOUND_TICKS = Map.of(
            "ha_1", 37, "ha_2", 45, "ha_3", 23, "ha_4", 22, "ha_5", 22,
            "laowu_1", 91, "laowu_2", 109, "laowu_3", 256, "laowu_4", 91, "laowu_5", 100);

    /**
     * 挥击 — 真实攻击链 (挥击动画 + 伤害) 但伤害固定配置值 (默认 1 点血, 不致命)。
     * TLM 实证: 女仆打女仆不会引发反击 (DefaultMonsterType FRIENDLY 排除 TamableAnimal, canAttack 恒 false)。
     * 目标类型分流: 对主人用魔法伤害 (绕护甲, 掉血可见), 主人不反击 (玩家无自动反击); 对女仆 mobAttack 真实攻击链。
     */
    public static void doHit(EntityMaid maid, LivingEntity target, String targetType) {
        maid.swing(InteractionHand.MAIN_HAND);
        // 对主人用魔法伤害 (绕护甲) — mobAttack 1.0 伤害被满甲玩家减到 0.2, 肉眼无感 (用户实测"没效果");
        // 魔法伤害 LivingEntity.hurt 不走护甲减伤 (isMagic → 跳过 getDamageAfterArmorAbsorb), 掉血可见。
        // 对女仆保持 mobAttack 真实攻击链不动。
        if (TARGET_OWNER.equals(targetType)) {
            CombatOutput.magicDamage(target, maid, PassiveTaskConfig.HAQI_HIT_DAMAGE_TO_OWNER.get().floatValue());
        } else {
            CombatOutput.damage(target, maid, PassiveTaskConfig.HAQI_HIT_DAMAGE.get().floatValue());
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.SoundOutput.playEntity(
                maid, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.0F);
    }

    /** 播放音频 (音量走配置) — v79.6x: 注册表解析收编 vanilla SoundOutput (B5) */
    public static void playSound(EntityMaid maid, String name) {
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.SoundOutput.playModSound(
                maid, LittleMaidMoreAction.MOD_ID, name,
                PassiveTaskConfig.HAQI_VOLUME.get().floatValue());
    }
}
